package osmium.animation;

import osmium.math.Vec3;

public final class BoneTimeline {
  private final Channel position = new Channel(Vec3.ZERO),
      rotation = new Channel(Vec3.ZERO),
      scale = new Channel(Vec3.ONE);

  private boolean looping;

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
    return sample(t, looping);
  }

  public Sample sample(double t, boolean loop) {
    return new Sample(position.sample(t, loop), rotation.sample(t, loop), scale.sample(t, loop));
  }

  void setLooping(boolean looping) {
    this.looping = looping;
  }

  public record Sample(Vec3 position, Vec3 rotation, Vec3 scale) {}
}
