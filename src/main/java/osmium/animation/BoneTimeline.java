package osmium.animation;

import osmium.math.Vec3;

public final class BoneTimeline {
  private final Channel position = new Channel(Vec3.ZERO),
      rotation = new Channel(Vec3.ZERO),
      scale = new Channel(Vec3.ONE);

  public Channel position() {
    return position;
  }

  public Channel rotation() {
    return rotation;
  }

  public Channel scale() {
    return scale;
  }

  public Sample sample(double t) {
    return sample(t, false, 0);
  }

  public Sample sample(double t, boolean loop, double animationLength) {
    return new Sample(
        position.sample(t, loop, animationLength),
        rotation.sample(t, loop, animationLength),
        scale.sample(t, loop, animationLength));
  }

  public record Sample(Vec3 position, Vec3 rotation, Vec3 scale) {}
}
