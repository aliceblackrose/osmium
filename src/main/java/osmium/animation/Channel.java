package osmium.animation;

import java.util.ArrayList;
import java.util.List;
import osmium.math.Vec3;

public final class Channel {
  private static final double MIN_SEGMENT_DURATION = 1.0E-9;
  private static final int BEZIER_SOLVER_ITERATIONS = 24;

  private final Vec3 fallback;
  private final List<Keyframe> frames = new ArrayList<>();

  public Channel(Vec3 fallback) {
    this.fallback = fallback;
  }

  public void add(Keyframe frame) {
    frames.add(insertionIndex(frame), frame);
  }

  public Vec3 sample(double time) {
    return sample(time, false, 0);
  }

  /** Samples this channel using Blockbench's loop-aware neighboring-keyframe semantics. */
  public Vec3 sample(double time, boolean loop, double animationLength) {
    if (frames.isEmpty()) {
      return fallback;
    }

    if (frames.size() == 1) {
      Keyframe frame = frames.getFirst();
      return time <= frame.time() ? frame.pre() : frame.post();
    }

    if (loop && animationLength > MIN_SEGMENT_DURATION) {
      double normalizedTime = time % animationLength;
      if (normalizedTime < 0) {
        normalizedTime += animationLength;
      }
      return sampleLooping(normalizedTime, animationLength);
    }

    return sampleOnce(time);
  }

  private Vec3 sampleOnce(double time) {
    Keyframe firstFrame = frames.getFirst();
    if (time <= firstFrame.time()) {
      return firstFrame.pre();
    }

    for (int index = 1; index < frames.size(); index++) {
      Keyframe nextFrame = frames.get(index);
      if (time <= nextFrame.time()) {
        return sampleBetween(index - 1, index, time, frames.get(index - 1).time(), nextFrame.time(), false);
      }
    }

    return frames.getLast().post();
  }

  private Vec3 sampleLooping(double time, double animationLength) {
    Keyframe firstFrame = frames.getFirst();
    Keyframe lastFrame = frames.getLast();

    if (time == firstFrame.time()) {
      return firstFrame.pre();
    }

    if (time < firstFrame.time()) {
      return sampleBetween(
          frames.size() - 1,
          0,
          time,
          lastFrame.time() - animationLength,
          firstFrame.time(),
          true);
    }

    for (int index = 1; index < frames.size(); index++) {
      Keyframe nextFrame = frames.get(index);
      if (time <= nextFrame.time()) {
        return sampleBetween(
            index - 1,
            index,
            time,
            frames.get(index - 1).time(),
            nextFrame.time(),
            true);
      }
    }

    return sampleBetween(
        frames.size() - 1,
        0,
        time,
        lastFrame.time(),
        firstFrame.time() + animationLength,
        true);
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

  private Vec3 sampleBetween(
      int previousIndex,
      int nextIndex,
      double time,
      double previousTime,
      double nextTime,
      boolean loop) {
    Keyframe previousFrame = frames.get(previousIndex);
    Keyframe nextFrame = frames.get(nextIndex);
    double amount = normalizedAmount(previousTime, nextTime, time);
    Interpolation interpolation = interpolation(previousFrame, nextFrame);

    return switch (interpolation) {
      case CATMULL_ROM -> sampleCatmullRom(previousIndex, nextIndex, amount, loop);
      case BEZIER -> sampleBezier(previousFrame, nextFrame, time, previousTime, nextTime);
      case SMOOTH -> Vec3.lerp(previousFrame.post(), nextFrame.pre(), smoothstep(amount));
      case LINEAR -> Vec3.lerp(previousFrame.post(), nextFrame.pre(), amount);
      case STEP -> previousFrame.post();
    };
  }

  private static Interpolation interpolation(Keyframe previousFrame, Keyframe nextFrame) {
    Interpolation previous = previousFrame.interpolation();
    Interpolation next = nextFrame.interpolation();

    if (previous == Interpolation.SMOOTH) {
      return Interpolation.SMOOTH;
    }

    if (previous == Interpolation.LINEAR
        && (next == Interpolation.LINEAR || next == Interpolation.STEP)) {
      return Interpolation.LINEAR;
    }

    if (previous == Interpolation.CATMULL_ROM || next == Interpolation.CATMULL_ROM) {
      return Interpolation.CATMULL_ROM;
    }

    if (previous == Interpolation.BEZIER || next == Interpolation.BEZIER) {
      return Interpolation.BEZIER;
    }

    return previous == Interpolation.STEP ? Interpolation.STEP : Interpolation.LINEAR;
  }

  private Vec3 sampleCatmullRom(
      int previousIndex, int nextIndex, double amount, boolean loop) {
    Vec3 p1 = frames.get(previousIndex).post();
    Vec3 p2 = frames.get(nextIndex).pre();
    Vec3 p0 = neighboringValue(previousIndex - 1, p1, true, loop);
    Vec3 p3 = neighboringValue(nextIndex + 1, p2, false, loop);

    return new Vec3(
        catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), amount),
        catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), amount),
        catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), amount));
  }

  private Vec3 neighboringValue(int index, Vec3 fallbackValue, boolean before, boolean loop) {
    if (index >= 0 && index < frames.size()) {
      return before ? frames.get(index).post() : frames.get(index).pre();
    }

    if (!loop || frames.size() < 3) {
      return fallbackValue;
    }

    int wrappedIndex = Math.floorMod(index, frames.size());
    return before ? frames.get(wrappedIndex).post() : frames.get(wrappedIndex).pre();
  }

  private static double catmullRom(double p0, double p1, double p2, double p3, double amount) {
    double amountSquared = amount * amount;
    double amountCubed = amountSquared * amount;
    return 0.5
        * ((2 * p1)
            + (-p0 + p2) * amount
            + (2 * p0 - 5 * p1 + 4 * p2 - p3) * amountSquared
            + (-p0 + 3 * p1 - 3 * p2 + p3) * amountCubed);
  }

  private static Vec3 sampleBezier(
      Keyframe previousFrame,
      Keyframe nextFrame,
      double time,
      double previousTime,
      double nextTime) {
    return new Vec3(
        sampleBezierAxis(previousFrame, nextFrame, time, previousTime, nextTime, Axis.X),
        sampleBezierAxis(previousFrame, nextFrame, time, previousTime, nextTime, Axis.Y),
        sampleBezierAxis(previousFrame, nextFrame, time, previousTime, nextTime, Axis.Z));
  }

  private static double sampleBezierAxis(
      Keyframe previousFrame,
      Keyframe nextFrame,
      double time,
      double previousTime,
      double nextTime,
      Axis axis) {
    double timeGap = Math.max(nextTime - previousTime, MIN_SEGMENT_DURATION);

    double startValue = axis.value(previousFrame.post());
    double endValue = axis.value(nextFrame.pre());
    double outgoingTime = Math.clamp(axis.value(previousFrame.bezierRightTime()), 0.0, timeGap);
    double incomingTime = Math.clamp(axis.value(nextFrame.bezierLeftTime()), -timeGap, 0.0);

    double p0x = previousTime;
    double p1x = previousTime + outgoingTime;
    double p2x = nextTime + incomingTime;
    double p3x = nextTime;

    double p0y = startValue;
    double p1y = startValue + axis.value(previousFrame.bezierRightValue());
    double p2y = endValue + axis.value(nextFrame.bezierLeftValue());
    double p3y = endValue;

    double low = 0.0;
    double high = 1.0;
    for (int iteration = 0; iteration < BEZIER_SOLVER_ITERATIONS; iteration++) {
      double middle = (low + high) * 0.5;
      double sampledTime = cubicBezier(p0x, p1x, p2x, p3x, middle);
      if (sampledTime < time) {
        low = middle;
      } else {
        high = middle;
      }
    }

    return cubicBezier(p0y, p1y, p2y, p3y, (low + high) * 0.5);
  }

  private static double cubicBezier(double p0, double p1, double p2, double p3, double amount) {
    double inverse = 1.0 - amount;
    return inverse * inverse * inverse * p0
        + 3.0 * inverse * inverse * amount * p1
        + 3.0 * inverse * amount * amount * p2
        + amount * amount * amount * p3;
  }

  private static double normalizedAmount(double previousTime, double nextTime, double time) {
    return clamp01((time - previousTime) / Math.max(nextTime - previousTime, MIN_SEGMENT_DURATION));
  }

  private static double smoothstep(double amount) {
    return amount * amount * (3 - 2 * amount);
  }

  private static double clamp01(double value) {
    return Math.clamp(value, 0, 1);
  }

  private enum Axis {
    X {
      @Override
      double value(Vec3 vector) {
        return vector.x();
      }
    },
    Y {
      @Override
      double value(Vec3 vector) {
        return vector.y();
      }
    },
    Z {
      @Override
      double value(Vec3 vector) {
        return vector.z();
      }
    };

    abstract double value(Vec3 vector);
  }
}
