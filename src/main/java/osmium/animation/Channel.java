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
    if (frames.isEmpty()) {
      return fallback;
    }

    Keyframe firstFrame = frames.getFirst();
    if (time <= firstFrame.time()) {
      return firstFrame.pre();
    }

    for (int index = 1; index < frames.size(); index++) {
      Keyframe nextFrame = frames.get(index);
      if (time <= nextFrame.time()) {
        return sampleBetween(index - 1, index, time);
      }
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

  private Vec3 sampleBetween(int previousIndex, int nextIndex, double time) {
    Keyframe previousFrame = frames.get(previousIndex);
    Keyframe nextFrame = frames.get(nextIndex);

    if (previousFrame.interpolation() == Interpolation.STEP) {
      return previousFrame.post();
    }

    double amount = normalizedAmount(previousFrame, nextFrame, time);

    return switch (previousFrame.interpolation()) {
      case CATMULL_ROM -> sampleCatmullRom(previousIndex, nextIndex, amount);
      case BEZIER -> sampleBezier(previousFrame, nextFrame, time);
      case SMOOTH -> Vec3.lerp(previousFrame.post(), nextFrame.pre(), smoothstep(amount));
      case LINEAR, STEP -> Vec3.lerp(previousFrame.post(), nextFrame.pre(), amount);
    };
  }

  private Vec3 sampleCatmullRom(int previousIndex, int nextIndex, double amount) {
    Vec3 p1 = frames.get(previousIndex).post();
    Vec3 p2 = frames.get(nextIndex).pre();
    Vec3 p0 = previousIndex > 0 ? frames.get(previousIndex - 1).post() : p1;
    Vec3 p3 = nextIndex + 1 < frames.size() ? frames.get(nextIndex + 1).pre() : p2;

    return new Vec3(
        catmullRom(p0.x(), p1.x(), p2.x(), p3.x(), amount),
        catmullRom(p0.y(), p1.y(), p2.y(), p3.y(), amount),
        catmullRom(p0.z(), p1.z(), p2.z(), p3.z(), amount));
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

  private static Vec3 sampleBezier(Keyframe previousFrame, Keyframe nextFrame, double time) {
    return new Vec3(
        sampleBezierAxis(previousFrame, nextFrame, time, Axis.X),
        sampleBezierAxis(previousFrame, nextFrame, time, Axis.Y),
        sampleBezierAxis(previousFrame, nextFrame, time, Axis.Z));
  }

  private static double sampleBezierAxis(
      Keyframe previousFrame, Keyframe nextFrame, double time, Axis axis) {
    double startTime = previousFrame.time();
    double endTime = nextFrame.time();
    double timeGap = Math.max(endTime - startTime, MIN_SEGMENT_DURATION);

    double startValue = axis.value(previousFrame.post());
    double endValue = axis.value(nextFrame.pre());
    double outgoingTime =
        Math.clamp(axis.value(previousFrame.bezierRightTime()), 0.0, timeGap);
    double incomingTime =
        Math.clamp(axis.value(nextFrame.bezierLeftTime()), -timeGap, 0.0);

    double p0x = startTime;
    double p1x = startTime + outgoingTime;
    double p2x = endTime + incomingTime;
    double p3x = endTime;

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

  private static double normalizedAmount(Keyframe previousFrame, Keyframe nextFrame, double time) {
    return clamp01(
        (time - previousFrame.time())
            / Math.max(nextFrame.time() - previousFrame.time(), MIN_SEGMENT_DURATION));
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
