package osmium.animation;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import osmium.model.Bone;

/** Shares immutable compiled animation streams between runtime instances using the same skeleton. */
public final class AnimationCompilationCache {
  private final Map<Bone, Map<Integer, IdentityHashMap<Animation, CompiledAnimation>>> byRoot =
      new WeakHashMap<>();

  public synchronized CompiledAnimation get(
      Animation animation, Bone rootBone, int interpolationDurationTicks) {
    int interpolationTicks = Math.max(0, interpolationDurationTicks);
    Map<Integer, IdentityHashMap<Animation, CompiledAnimation>> byInterpolation =
        byRoot.computeIfAbsent(rootBone, ignored -> new HashMap<>());
    IdentityHashMap<Animation, CompiledAnimation> animations =
        byInterpolation.computeIfAbsent(interpolationTicks, ignored -> new IdentityHashMap<>());
    return animations.computeIfAbsent(
        animation, value -> AnimationCompiler.compile(value, rootBone, interpolationTicks));
  }

  public synchronized void clear() {
    byRoot.clear();
  }
}
