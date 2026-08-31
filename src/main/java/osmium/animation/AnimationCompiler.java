package osmium.animation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import osmium.math.Vec3;
import osmium.model.Bone;

/**
 * Compiles authored Blockbench channels into a common runtime frame stream.
 *
 * <p>This deliberately follows the architecture used by BetterModel: curve evaluation is a
 * pre-playback concern, every animated bone shares the same frame boundaries, step transitions get
 * an explicit pre-step frame, and large hierarchical rotations are subdivided before Minecraft
 * interpolates them. Because Osmium currently uses Bukkit's 20 TPS entity API rather than a 25 ms
 * packet tracker, compiled times are quantized to Minecraft server ticks.
 */
public final class AnimationCompiler {
  public static final double MINECRAFT_TICK_SECONDS = 0.05D;

  private static final double MAX_ROTATION_STEP_DEGREES = 90.0D;
  private static final double TIME_SCALE = 1_000_000.0D;
  private static final double EPSILON = 1.0E-6D;

  private AnimationCompiler() {}

  public static CompiledAnimation compile(
      Animation animation, Bone rootBone, int interpolationDurationTicks) {
    double runtimeLength = quantizeTime(Math.max(animation.length(), MINECRAFT_TICK_SECONDS));
    TreeSet<Double> frameTimes = new TreeSet<>();
    Set<Long> stepTargets = new HashSet<>();

    addTime(frameTimes, 0.0D, runtimeLength);
    addTime(frameTimes, runtimeLength, runtimeLength);
    collectAuthoredTimes(animation, runtimeLength, frameTimes);
    insertInterpolationFrames(frameTimes, interpolationDurationTicks, runtimeLength);
    insertStepFrames(animation, runtimeLength, frameTimes, stepTargets);
    insertRotationFrames(animation, rootBone, runtimeLength, frameTimes);

    List<Double> times = new ArrayList<>(frameTimes);
    List<CompiledAnimation.Frame> frames = new ArrayList<>(times.size());
    double previousTime = 0.0D;

    for (int index = 0; index < times.size(); index++) {
      double time = times.get(index);
      Map<String, BoneTimeline.Sample> poses = new LinkedHashMap<>();
      for (Map.Entry<String, BoneTimeline> entry : animation.timelines().entrySet()) {
        poses.put(
            entry.getKey(),
            entry.getValue().sample(time, animation.loop(), animation.length()));
      }

      int durationTicks = index == 0 ? 0 : ticksBetween(previousTime, time);
      frames.add(
          new CompiledAnimation.Frame(
              time, durationTicks, stepTargets.contains(timeKey(time)), poses));
      previousTime = time;
    }

    return new CompiledAnimation(animation.name(), runtimeLength, animation.loopMode(), frames);
  }

  private static void collectAuthoredTimes(
      Animation animation, double runtimeLength, Set<Double> frameTimes) {
    for (BoneTimeline timeline : animation.timelines().values()) {
      collectChannelTimes(timeline.position(), runtimeLength, frameTimes);
      collectChannelTimes(timeline.rotation(), runtimeLength, frameTimes);
      collectChannelTimes(timeline.scale(), runtimeLength, frameTimes);
    }
  }

  private static void collectChannelTimes(
      Channel channel, double runtimeLength, Set<Double> frameTimes) {
    for (Keyframe frame : channel.frames()) {
      addTime(frameTimes, frame.time(), runtimeLength);
    }
  }

  private static void insertInterpolationFrames(
      Set<Double> frameTimes, int interpolationDurationTicks, double runtimeLength) {
    if (interpolationDurationTicks <= 0) {
      return;
    }

    double frameSeconds = interpolationDurationTicks * MINECRAFT_TICK_SECONDS;
    List<Double> authored = new ArrayList<>(frameTimes);
    for (int index = 1; index < authored.size(); index++) {
      double first = authored.get(index - 1);
      double second = authored.get(index);
      for (double time = first + frameSeconds;
          time < second - MINECRAFT_TICK_SECONDS + EPSILON;
          time += frameSeconds) {
        addTime(frameTimes, time, runtimeLength);
      }
    }
  }

  private static void insertStepFrames(
      Animation animation,
      double runtimeLength,
      Set<Double> frameTimes,
      Set<Long> stepTargets) {
    for (BoneTimeline timeline : animation.timelines().values()) {
      insertStepFrames(timeline.position(), runtimeLength, frameTimes, stepTargets);
      insertStepFrames(timeline.rotation(), runtimeLength, frameTimes, stepTargets);
      insertStepFrames(timeline.scale(), runtimeLength, frameTimes, stepTargets);
    }
  }

  private static void insertStepFrames(
      Channel channel, double runtimeLength, Set<Double> frameTimes, Set<Long> stepTargets) {
    List<Keyframe> frames = channel.frames();
    for (int index = 1; index < frames.size(); index++) {
      Keyframe previous = frames.get(index - 1);
      Keyframe next = frames.get(index);
      if (previous.interpolation() != Interpolation.STEP) {
        continue;
      }

      double targetTime = clampedTime(next.time(), runtimeLength);
      stepTargets.add(timeKey(targetTime));

      double holdTime = targetTime - MINECRAFT_TICK_SECONDS;
      if (holdTime + EPSILON >= previous.time()) {
        addTime(frameTimes, holdTime, runtimeLength);
      }
    }
  }

  private static void insertRotationFrames(
      Animation animation, Bone rootBone, double runtimeLength, Set<Double> frameTimes) {
    List<Double> baseTimes = new ArrayList<>(frameTimes);
    for (int index = 1; index < baseTimes.size(); index++) {
      double previous = baseTimes.get(index - 1);
      double next = baseTimes.get(index);
      double maximumRotation = maximumHierarchicalRotation(animation, rootBone, previous, next);
      if (maximumRotation <= MAX_ROTATION_STEP_DEGREES) {
        continue;
      }

      // BetterModel subdivides high angular deltas. Osmium cannot safely send Bukkit display
      // updates faster than a server tick, so use every transport slot available inside the risky
      // interval.
      for (double time = previous + MINECRAFT_TICK_SECONDS;
          time < next - EPSILON;
          time += MINECRAFT_TICK_SECONDS) {
        addTime(frameTimes, time, runtimeLength);
      }
    }
  }

  private static double maximumHierarchicalRotation(
      Animation animation, Bone rootBone, double previousTime, double nextTime) {
    return maximumHierarchicalRotation(
        animation, rootBone, previousTime, nextTime, Vec3.ZERO, 0.0D);
  }

  private static double maximumHierarchicalRotation(
      Animation animation,
      Bone bone,
      double previousTime,
      double nextTime,
      Vec3 parentDelta,
      double maximum) {
    BoneTimeline timeline = animation.timelines().get(bone.name());
    Vec3 localDelta = Vec3.ZERO;
    if (timeline != null && timeline.rotation().frames().size() >= 2) {
      Vec3 previousRotation =
          timeline.rotation().sample(previousTime, animation.loop(), animation.length());
      Vec3 nextRotation =
          timeline.rotation().sample(nextTime, animation.loop(), animation.length());
      localDelta = nextRotation.subtract(previousRotation);
    }

    Vec3 accumulated = parentDelta.add(localDelta);
    maximum = Math.max(maximum, vectorLength(accumulated));
    for (Bone child : bone.children()) {
      maximum =
          Math.max(
              maximum,
              maximumHierarchicalRotation(
                  animation, child, previousTime, nextTime, accumulated, maximum));
    }
    return maximum;
  }

  private static double vectorLength(Vec3 vector) {
    return Math.sqrt(vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
  }

  private static int ticksBetween(double previous, double next) {
    return Math.max(1, (int) Math.round((next - previous) / MINECRAFT_TICK_SECONDS));
  }

  private static void addTime(Set<Double> times, double time, double runtimeLength) {
    times.add(clampedTime(time, runtimeLength));
  }

  private static double clampedTime(double time, double runtimeLength) {
    return Math.clamp(quantizeTime(time), 0.0D, runtimeLength);
  }

  private static double quantizeTime(double time) {
    double ticks = Math.round(time / MINECRAFT_TICK_SECONDS);
    return Math.rint(ticks * MINECRAFT_TICK_SECONDS * TIME_SCALE) / TIME_SCALE;
  }

  private static long timeKey(double time) {
    return Math.round(time * TIME_SCALE);
  }
}
