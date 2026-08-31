package osmium.animation;

import java.util.List;

/** Playback state for a precompiled animation frame stream. */
public final class AnimationState {
  private static final double FRAME_TIME_EPSILON = 1.0E-6D;

  private CompiledAnimation animation;
  private int frameIndex;
  private int ticksUntilNext;
  private int interpolationDurationTicks;
  private boolean complete;
  private boolean dirty;

  public void play(CompiledAnimation animation) {
    this.animation = animation;
    frameIndex = 0;
    interpolationDurationTicks = 0;
    complete = animation.frames().isEmpty();
    dirty = !complete;
    ticksUntilNext = complete ? 0 : durationToNextFrame();
  }

  public void stop() {
    animation = null;
    frameIndex = 0;
    ticksUntilNext = 0;
    interpolationDurationTicks = 0;
    complete = false;
    dirty = false;
  }

  public CompiledAnimation animation() {
    return animation;
  }

  public boolean playing(String name) {
    return animation != null && animation.name().equals(name);
  }

  public boolean complete() {
    return animation != null && complete;
  }

  public CompiledAnimation.Frame frame() {
    if (animation == null || animation.frames().isEmpty()) {
      return null;
    }
    return animation.frames().get(frameIndex);
  }

  /** Duration Minecraft should use when interpolating from the previous pose to this frame. */
  public int interpolationDurationTicks() {
    return interpolationDurationTicks;
  }

  /** True when a new local pose needs to be transmitted to display entities. */
  public boolean dirty() {
    return dirty;
  }

  public void markRendered() {
    dirty = false;
  }

  /** Advances one Minecraft server tick through the compiled frame stream. */
  public void advance() {
    if (animation == null || complete || animation.frames().isEmpty()) {
      return;
    }

    List<CompiledAnimation.Frame> frames = animation.frames();
    int lastIndex = frames.size() - 1;
    if (frameIndex >= lastIndex) {
      if (animation.loop()) {
        interpolationDurationTicks = 1;
        frameIndex = 0;
        dirty = true;
        ticksUntilNext = durationToNextFrame();
      } else {
        complete = true;
      }
      return;
    }

    if (ticksUntilNext > 1) {
      ticksUntilNext--;
      return;
    }

    int nextIndex = frameIndex + 1;
    if (animation.loop()
        && nextIndex == lastIndex
        && Math.abs(frames.get(lastIndex).time() - animation.length()) <= FRAME_TIME_EPSILON) {
      // The terminal length frame is a compile-time seam marker. Its duration is the duration of the
      // transition from the last visible frame back to frame zero.
      interpolationDurationTicks = Math.max(1, frames.get(lastIndex).durationTicks());
      frameIndex = 0;
    } else {
      interpolationDurationTicks = Math.max(0, frames.get(nextIndex).durationTicks());
      frameIndex = nextIndex;
    }

    dirty = true;
    ticksUntilNext = durationToNextFrame();
  }

  private int durationToNextFrame() {
    if (animation == null || animation.frames().isEmpty()) {
      return 0;
    }

    List<CompiledAnimation.Frame> frames = animation.frames();
    int nextIndex = frameIndex + 1;
    if (nextIndex < frames.size()) {
      return Math.max(1, frames.get(nextIndex).durationTicks());
    }

    return animation.loop() ? 1 : 0;
  }
}
