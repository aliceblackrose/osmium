package osmium.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record Animation(
    String name, double length, AnimationLoopMode loopMode, Map<String, BoneTimeline> timelines) {
  private static final double MIN_LENGTH_SECONDS = 0.05;

  public Animation {
    length = Math.max(length, MIN_LENGTH_SECONDS);
    loopMode = loopMode == null ? AnimationLoopMode.ONCE : loopMode;
    timelines = Collections.unmodifiableMap(new LinkedHashMap<>(timelines));
  }

  /**
   * Compatibility constructor for callers that only distinguish looping and one-shot animations.
   */
  public Animation(String name, double length, boolean loop, Map<String, BoneTimeline> timelines) {
    this(name, length, loop ? AnimationLoopMode.LOOP : AnimationLoopMode.ONCE, timelines);
  }

  public boolean loop() {
    return loopMode == AnimationLoopMode.LOOP;
  }

  public boolean hold() {
    return loopMode == AnimationLoopMode.HOLD;
  }

  public double normalize(double elapsedSeconds) {
    if (loop()) {
      return elapsedSeconds % length;
    }

    return Math.min(elapsedSeconds, length);
  }
}
