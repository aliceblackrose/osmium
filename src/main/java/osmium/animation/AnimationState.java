package osmium.animation;

import java.util.IdentityHashMap;
import java.util.List;
import osmium.model.Bone;

/** Playback state backed by cached, precompiled animation frame streams. */
public final class AnimationState {
  private static final double FRAME_TIME_EPSILON = 1.0E-6D;

  private final IdentityHashMap<Animation, CompiledAnimation> compiledAnimations =
      new IdentityHashMap<>();

  private Bone rootBone;
  private int compileInterpolationTicks = 1;
  private AnimationCompilationCache sharedCompilationCache;
  private Animation animation;
  private CompiledAnimation compiledAnimation;
  private int frameIndex;
  private int stepsUntilNext;
  private int interpolationDurationTicks;
  private boolean complete;
  private boolean dirty;

  /** Configures the skeleton and maximum client interpolation interval used during compilation. */
  public void configure(Bone rootBone, int interpolationDurationTicks) {
    configure(rootBone, interpolationDurationTicks, null);
  }

  /**
   * Configures playback using an optional cache shared by runtime instances of the same model.
   */
  public void configure(
      Bone rootBone,
      int interpolationDurationTicks,
      AnimationCompilationCache sharedCompilationCache) {
    this.rootBone = rootBone;
    compileInterpolationTicks = Math.max(0, interpolationDurationTicks);
    this.sharedCompilationCache = sharedCompilationCache;
    compiledAnimations.clear();
    stop();
  }

  public void play(Animation animation) {
    if (rootBone == null) {
      throw new IllegalStateException("AnimationState must be configured before playback.");
    }

    this.animation = animation;
    compiledAnimation = compiled(animation);
    frameIndex = 0;
    interpolationDurationTicks = 0;
    complete = compiledAnimation.frames().isEmpty();
    dirty = !complete;
    stepsUntilNext = complete ? 0 : durationToNextFrame();
  }

  public void stop() {
    animation = null;
    compiledAnimation = null;
    frameIndex = 0;
    stepsUntilNext = 0;
    interpolationDurationTicks = 0;
    complete = false;
    dirty = false;
  }

  public Animation animation() {
    return animation;
  }

  public CompiledAnimation compiledAnimation() {
    return compiledAnimation;
  }

  public boolean playing(String name) {
    return animation != null && animation.name().equals(name);
  }

  public boolean complete() {
    return animation != null && complete;
  }

  public CompiledAnimation.Frame frame() {
    if (compiledAnimation == null || compiledAnimation.frames().isEmpty()) {
      return null;
    }
    return compiledAnimation.frames().get(frameIndex);
  }

  public double time() {
    CompiledAnimation.Frame currentFrame = frame();
    return currentFrame == null ? 0.0D : currentFrame.time();
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

  /** Advances one 25 ms packet-renderer step through the compiled frame stream. */
  public void advance() {
    if (compiledAnimation == null || complete || compiledAnimation.frames().isEmpty()) {
      return;
    }

    List<CompiledAnimation.Frame> frames = compiledAnimation.frames();
    int lastIndex = frames.size() - 1;
    if (frameIndex >= lastIndex) {
      if (compiledAnimation.loop()) {
        interpolationDurationTicks = 1;
        frameIndex = 0;
        dirty = true;
        stepsUntilNext = durationToNextFrame();
      } else {
        complete = true;
      }
      return;
    }

    if (stepsUntilNext > 1) {
      stepsUntilNext--;
      return;
    }

    int nextIndex = frameIndex + 1;
    if (compiledAnimation.loop()
        && nextIndex == lastIndex
        && Math.abs(frames.get(lastIndex).time() - compiledAnimation.length())
            <= FRAME_TIME_EPSILON) {
      CompiledAnimation.Frame seamFrame = frames.get(lastIndex);
      interpolationDurationTicks =
          seamFrame.skipInterpolation()
              ? 0
              : Math.max(1, AnimationCompiler.clientInterpolationTicks(seamFrame.durationSteps()));
      frameIndex = 0;
    } else {
      interpolationDurationTicks =
          AnimationCompiler.clientInterpolationTicks(frames.get(nextIndex).durationSteps());
      frameIndex = nextIndex;
    }

    dirty = true;
    stepsUntilNext = durationToNextFrame();
  }

  private CompiledAnimation compiled(Animation animation) {
    if (sharedCompilationCache != null) {
      return sharedCompilationCache.get(animation, rootBone, compileInterpolationTicks);
    }
    return compiledAnimations.computeIfAbsent(
        animation,
        value -> AnimationCompiler.compile(value, rootBone, compileInterpolationTicks));
  }

  private int durationToNextFrame() {
    if (compiledAnimation == null || compiledAnimation.frames().isEmpty()) {
      return 0;
    }

    List<CompiledAnimation.Frame> frames = compiledAnimation.frames();
    int nextIndex = frameIndex + 1;
    if (nextIndex < frames.size()) {
      return Math.max(1, frames.get(nextIndex).durationSteps());
    }

    return compiledAnimation.loop() ? 1 : 0;
  }
}
