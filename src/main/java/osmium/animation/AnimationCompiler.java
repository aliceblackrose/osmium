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
 * <p>This deliberately follows the architecture used by BetterModel: curve evaluation is a load-time
 * concern, every animated bone shares the same frame boundaries, step transitions get an explicit
 * pre-step frame, and large hierarchical rotations are subdivided before Minecraft interpolates them.
 */
public final class AnimationCompiler {
  public static final double MINECRAFT_TICK_SECONDS = 0.05D;

  private static final double MAX_ROTATION_STEP_DEGREES = 90.0D;
  private static final double TIME_SCALE = 1_000_000.0D;
  private static final double EPSILON = 1.0E-6D;

  private AnimationCompiler() {}

  public static CompiledAnimation compile(
      Animation animation, Bone rootBone, int interpolationDurationTicks) {
    TreeSet<Double> frameTimes = new TreeSet<>();
    Set<Long> stepTargets = new HashSet<>();

    addTime(frameTimes, 0.0D, animation.length());
    addTime(frameTimes, animation.length(), animation.length());
    collectAuthoredTimes(animation, frameTimes);
    insertInterpolationFrames(frameTimes, interpolationDurationTicks, animation.length());
    insertStepFrames(animation, frameTimes, stepTargets);
    insertRotationFrames(animation, rootBone, frameTimes);

    List<Double> times = new ArrayList<>(frameTimes);
    List<CompiledAnimation.Frame> frames = new ArrayList<>(times.size());
    double previousTime = 0.0D;

    for (int index = 0; index < times.size(); index++) {
      double time = times.get(index);
      Map<String, BoneTimeline.Sample> poses = new LinkedHashMap<>();
      for (Map.Entry<String, BoneTimeline> entry : animation.timelines().entrySet()) {
        poses.put(entry.getKey(), entry.getValue().sample(time, false, animation.length()));
      }

      int durationTicks = index == 0 ? 0 : ticksBetween(previousTime, time);
      frames.add(
          new CompiledAnimation.Frame(
              time, durationTicks, stepTargets.contains(timeKey(time)), poses));
      previousTime = time;
    }

    return new CompiledAnimation(animation.name(), animation.length(), animation.loopMode(), frames);
  }

  private static void collectAuthoredTimes(Animation animation, TreeSet<Double> frameTimes) {
    for (BoneTimeline timeline : animation.timelines().values()) {
      collectChannelTimes(timeline.position(), animation.length(), frameTimes);
      collectChannelTimes(timeline.rotation(), animation.length(), frameTimes);
      collectChannelTimes(timeline.scale(), animation.length(), frameTimes);
    }
  }

  private static void collectChannelTimes(
      Channel channel, double animationLength, TreeSet<Double> frameTimes) {
    for (Keyframe frame : channel.frames()) {
      addTime(frameTimes, frame.time(), animationLength);
    }
  }

  private static void insertInterpolationFrames(
      TreeSet<Double> frameTimes, int interpolationDurationTicks, double animationLength) {
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
        addTime(frameTimes, time, animationLength);
      }
    }
  }

  private static void insertStepFrames(
      Animation animation, TreeSet<Double> frameTimes, Set<Long> stepTargets) {
    for (BoneTimeline timeline : animation.timelines().values()) {
      insertStepFrames(timeline.position(), animation.length(), frameTimes, stepTargets);
      insertStepFrames(timeline.rotation(), animation.length(), frameTimes, stepTargets);
      insertStepFrames(timeline.scale(), animation.length(), frameTimes, stepTargets);
    }
  }

  private static void insertStepFrames(
      Channel channel,
      double animationLength,
      TreeSet<Double> frameTimes,
      Set<Long> stepTargets) {
    List<Keyframe> frames = channel.frames();
    for (int index = 1; index < frames.size(); index++) {
      Keyframe previous = frames.get(index - 1);
      Keyframe next = frames.get(index);
      if (previous.interpolation() != Interpolation.STEP) {
        continue;
      }

      double targetTime = clampedTime(next.time(), animationLength);
      stepTargets.add(timeKey(targetTime));

      double holdTime = targetTime - MINECRAFT_TICK_SECONDS;
      if (holdTime + EPSILON >= previous.time()) {
        addTime(frameTimes, holdTime, animationLength);
      }
    }
  }

  private static void insertRotationFrames(
      Animation animation, Bone rootBone, TreeSet<Double> frameTimes) {
    List<Double> baseTimes = new ArrayList<>(frameTimes);
    for (int index = 1; index < baseTimes.size(); index++) {
      double previous = baseTimes.get(index - 1);
      double next = baseTimes.get(index);
      double maximumRotation = maximumHierarchicalRotation(animation, rootBone, previous, next);
      int subdivisions = (int) Math.ceil(maximumRotation / MAX_ROTATION_STEP_DEGREES);
      if (subdivisions < 2) {
        continue;
      }

      double interval = Math.max((next - previous) / subdivisions, MINECRAFT_TICK_SECONDS);
      for (int step = 1; step < subdivisions; step++) {
        double time = previous + interval * step;
        if (next - time < MINECRAFT_TICK_SECONDS - EPSILON) {
          break;
        }
        addTime(frameTimes, time, animation.length());
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
      localDelta =
          timeline.rotation().sample(nextTime).subtract(timeline.rotation().sample(previousTime));
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
    return Math.sqrt(
        vector.x() * vector.x() + vector.y() * vector.y() + vector.z() * vector.z());
  }

  private static int ticksBetween(double previous, double next) {
    return Math.max(1, (int) Math.round((next - previous) / MINECRAFT_TICK_SECONDS));
  }

  private static void addTime(TreeSet<Double> times, double time, double animationLength) {
    times.add(clampedTime(time, animationLength));
  }

  private static double clampedTime(double time, double animationLength) {
    double clamped = Math.clamp(time, 0.0D, animationLength);
    return Math.rint(clamped * TIME_SCALE) / TIME_SCALE;
  }

  private static long timeKey(double time) {
    return Math.round(time * TIME_SCALE);
  }
}
