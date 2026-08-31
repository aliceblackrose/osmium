package osmium.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime-ready animation produced from authored Blockbench curves.
 *
 * <p>The runtime never searches keyframes or evaluates splines. Every bone is sampled onto one shared
 * frame timeline during compilation, matching the precomputed-frame architecture used by modern
 * display-entity model engines.
 */
public record CompiledAnimation(
    String name, double length, AnimationLoopMode loopMode, List<Frame> frames) {
  public CompiledAnimation {
    loopMode = loopMode == null ? AnimationLoopMode.ONCE : loopMode;
    frames = List.copyOf(frames);
  }

  public boolean loop() {
    return loopMode == AnimationLoopMode.LOOP;
  }

  public boolean hold() {
    return loopMode == AnimationLoopMode.HOLD;
  }

  public record Frame(
      double time,
      int durationTicks,
      boolean skipInterpolation,
      Map<String, BoneTimeline.Sample> poses) {
    public Frame {
      durationTicks = Math.max(0, durationTicks);
      poses = Collections.unmodifiableMap(new LinkedHashMap<>(poses));
    }

    public BoneTimeline.Sample pose(String boneName) {
      return poses.get(boneName);
    }
  }
}
