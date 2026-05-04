package osmium.animation;

import osmium.math.Vec3;

public record Keyframe(double time, Vec3 pre, Vec3 post, Interpolation interpolation) {
  public Keyframe(double t, Vec3 v, Interpolation i) {
    this(t, v, v, i);
  }
}
