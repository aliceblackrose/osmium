package osmium.animation;

public final class AnimationState {
  private static final double NANOS_PER_SECOND = 1_000_000_000.0;

  private Animation animation;
  private long startedAtNanos;

  public void play(Animation animation) {
    this.animation = animation;
    this.startedAtNanos = System.nanoTime();
  }

  public void stop() {
    animation = null;
    startedAtNanos = 0;
  }

  public Animation animation() {
    return animation;
  }

  public boolean playing(String name) {
    return animation != null && animation.name().equals(name);
  }

  public boolean complete() {
    Animation currentAnimation = animation;
    return currentAnimation != null
        && !currentAnimation.loop()
        && elapsedSeconds() >= currentAnimation.length();
  }

  public double time() {
    Animation currentAnimation = animation;
    if (currentAnimation == null) {
      return 0;
    }

    return currentAnimation.normalize(elapsedSeconds());
  }

  public double elapsedSeconds() {
    if (animation == null) {
      return 0;
    }

    return (System.nanoTime() - startedAtNanos) / NANOS_PER_SECOND;
  }
}
