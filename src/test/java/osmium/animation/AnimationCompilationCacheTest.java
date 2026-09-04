package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.math.Vec3;
import osmium.model.Bone;

final class AnimationCompilationCacheTest {
  @Test
  void sharesCompiledStreamsForSameSkeletonAndInterpolationSettings() {
    Bone root = new Bone("root", "root", "root", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    Animation animation = new Animation("idle", 0.2D, AnimationLoopMode.LOOP, Map.of());
    AnimationCompilationCache cache = new AnimationCompilationCache();

    CompiledAnimation first = cache.get(animation, root, 2);
    CompiledAnimation second = cache.get(animation, root, 2);
    CompiledAnimation differentInterpolation = cache.get(animation, root, 3);

    assertSame(first, second);
    assertNotSame(first, differentInterpolation);
  }
}
