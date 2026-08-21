package osmium.animation;

import osmium.math.Vec3;

public record Keyframe(
    double time,
    Vec3 pre,
    Vec3 post,
    Interpolation interpolation,
    Vec3 bezierLeftTime,
    Vec3 bezierRightTime,
    Vec3 bezierLeftValue,
    Vec3 bezierRightValue) {
  public Keyframe {
    bezierLeftTime = defaultVector(bezierLeftTime);
    bezierRightTime = defaultVector(bezierRightTime);
    bezierLeftValue = defaultVector(bezierLeftValue);
    bezierRightValue = defaultVector(bezierRightValue);
  }

  public Keyframe(double time, Vec3 pre, Vec3 post, Interpolation interpolation) {
    this(time, pre, post, interpolation, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO);
  }

  public Keyframe(double time, Vec3 value, Interpolation interpolation) {
    this(time, value, value, interpolation);
  }

  private static Vec3 defaultVector(Vec3 value) {
    return value == null ? Vec3.ZERO : value;
  }
}
