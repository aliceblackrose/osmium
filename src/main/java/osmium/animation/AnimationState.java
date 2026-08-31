package osmium.animation;

/** Playback state advanced at the same 20 TPS cadence used to transmit display poses. */
public final class AnimationState {
  private static final double TICKS_PER_SECOND = 20.0;

  private Animation animation;
  private long elapsedTicks;

  public void play(Animation animation) {
    this.animation = animation;
    elapsedTicks = 0;
  }

  public void stop() {
    animation = null;
    elapsedTicks = 0;
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
    return animation == null ? 0 : elapsedTicks / TICKS_PER_SECOND;
  }

  /** Advances exactly one server animation frame (1/20 second). */
  public void advance() {
    if (animation != null) {
      elapsedTicks++;
    }
  }
}
