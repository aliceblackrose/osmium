package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnimationTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void loopingAnimationWrapsElapsedTime() {
    Animation animation = new Animation("idle", 2.0, true, Map.of());

    assertEquals(0.5, animation.normalize(4.5), EPSILON);
  }

  @Test
  void nonLoopingAnimationClampsAtEnd() {
    Animation animation = new Animation("attack", 1.5, false, Map.of());

    assertEquals(1.5, animation.normalize(10.0), EPSILON);
  }

  @Test
  void animationLengthHasSafeMinimum() {
    Animation animation = new Animation("tiny", 0.0, false, Map.of());

    assertEquals(0.05, animation.length(), EPSILON);
  }
}
