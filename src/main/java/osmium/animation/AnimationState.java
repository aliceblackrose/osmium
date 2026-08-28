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
    return complete(System.nanoTime());
  }

  public boolean complete(long nowNanos) {
    Animation currentAnimation = animation;
    return currentAnimation != null
        && !currentAnimation.loop()
        && elapsedSeconds(nowNanos) >= currentAnimation.length();
  }

  public double time() {
    return time(System.nanoTime());
  }

  public double time(long nowNanos) {
    Animation currentAnimation = animation;
    if (currentAnimation == null) {
      return 0;
    }

    return currentAnimation.normalize(elapsedSeconds(nowNanos));
  }

  public double elapsedSeconds() {
    return elapsedSeconds(System.nanoTime());
  }

  public double elapsedSeconds(long nowNanos) {
    if (animation == null) {
      return 0;
    }

    return (nowNanos - startedAtNanos) / NANOS_PER_SECOND;
  }
}
