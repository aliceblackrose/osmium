package osmium.animation;

import java.util.ArrayList;
import java.util.List;
import osmium.math.Vec3;

public final class Channel {
  private static final double MIN_SEGMENT_DURATION = 1.0E-9;

  private final Vec3 fallback;
  private final List<Keyframe> frames = new ArrayList<>();

  public Channel(Vec3 fallback) {
    this.fallback = fallback;
  }

  public void add(Keyframe frame) {
    frames.add(insertionIndex(frame), frame);
  }

  public Vec3 sample(double time) {
    if (frames.isEmpty()) {
      return fallback;
    }

    Keyframe firstFrame = frames.getFirst();
    if (time <= firstFrame.time()) {
      return firstFrame.pre();
    }

    Keyframe previousFrame = firstFrame;
    for (int index = 1; index < frames.size(); index++) {
      Keyframe nextFrame = frames.get(index);
      if (time <= nextFrame.time()) {
        return sampleBetween(previousFrame, nextFrame, time);
      }

      previousFrame = nextFrame;
    }

    return frames.getLast().post();
  }

  private int insertionIndex(Keyframe frame) {
    int low = 0;
    int high = frames.size();

    while (low < high) {
      int middle = (low + high) >>> 1;
      if (frames.get(middle).time() <= frame.time()) {
        low = middle + 1;
      } else {
        high = middle;
      }
    }

    return low;
  }

  private static Vec3 sampleBetween(Keyframe previousFrame, Keyframe nextFrame, double time) {
    if (previousFrame.interpolation() == Interpolation.STEP) {
      return previousFrame.post();
    }

    double amount =
        (time - previousFrame.time())
            / Math.max(nextFrame.time() - previousFrame.time(), MIN_SEGMENT_DURATION);

    if (previousFrame.interpolation() == Interpolation.SMOOTH) {
      amount = smoothstep(amount);
    }

    return Vec3.lerp(previousFrame.post(), nextFrame.pre(), clamp01(amount));
  }

  private static double smoothstep(double amount) {
    return amount * amount * (3 - 2 * amount);
  }

  private static double clamp01(double value) {
    return Math.clamp(value, 0, 1);
  }
}
