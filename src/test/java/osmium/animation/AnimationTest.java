package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.math.Vec3;

final class AnimationTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void loopingAnimationWrapsElapsedTime() {
    Animation animation = new Animation("idle", 2.0, AnimationLoopMode.LOOP, Map.of());

    assertEquals(0.5, animation.normalize(4.5), EPSILON);
    assertTrue(animation.loop());
    assertFalse(animation.hold());
  }

  @Test
  void onceAnimationClampsAtEnd() {
    Animation animation = new Animation("attack", 1.5, AnimationLoopMode.ONCE, Map.of());

    assertEquals(1.5, animation.normalize(10.0), EPSILON);
    assertFalse(animation.loop());
    assertFalse(animation.hold());
  }

  @Test
  void holdAnimationClampsAtEndWithoutLooping() {
    Animation animation = new Animation("pose", 1.5, AnimationLoopMode.HOLD, Map.of());

    assertEquals(1.5, animation.normalize(10.0), EPSILON);
    assertFalse(animation.loop());
    assertTrue(animation.hold());
  }

  @Test
  void animationLengthHasSafeMinimum() {
    Animation animation = new Animation("tiny", 0.0, AnimationLoopMode.ONCE, Map.of());

    assertEquals(0.05, animation.length(), EPSILON);
  }

  @Test
  void loopModeConfiguresTimelineCurveWrapping() {
    BoneTimeline timeline = new BoneTimeline();
    timeline.position().add(new Keyframe(0.0, Vec3.ZERO, Interpolation.CATMULL_ROM));
    timeline.position().add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.CATMULL_ROM));
    timeline.position().add(new Keyframe(2.0, new Vec3(20, 0, 0), Interpolation.CATMULL_ROM));
    timeline.position().add(new Keyframe(3.0, Vec3.ZERO, Interpolation.CATMULL_ROM));

    Animation animation =
        new Animation("idle", 3.0, AnimationLoopMode.LOOP, Map.of("root", timeline));

    assertEquals(3.125, animation.timelines().get("root").sample(0.5).position().x(), EPSILON);
  }

  @Test
  void loopModeParserMatchesBlockbenchValues() {
    assertEquals(AnimationLoopMode.ONCE, AnimationLoopMode.parse(null));
    assertEquals(AnimationLoopMode.ONCE, AnimationLoopMode.parse("once"));
    assertEquals(AnimationLoopMode.LOOP, AnimationLoopMode.parse("loop"));
    assertEquals(AnimationLoopMode.LOOP, AnimationLoopMode.parse("true"));
    assertEquals(AnimationLoopMode.HOLD, AnimationLoopMode.parse("hold"));
  }
}
