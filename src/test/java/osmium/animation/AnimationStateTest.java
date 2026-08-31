package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnimationStateTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void playbackAdvancesInExactServerTickSteps() {
    Animation animation = new Animation("once", 0.2, false, Map.of());
    AnimationState state = new AnimationState();
    state.play(animation);

    assertEquals(0.0, state.time(), EPSILON);
    assertFalse(state.complete());

    state.advance();
    assertEquals(0.05, state.time(), EPSILON);

    state.advance();
    state.advance();
    state.advance();
    assertEquals(0.2, state.time(), EPSILON);
    assertTrue(state.complete());
  }

  @Test
  void loopingPlaybackWrapsOnTheSameTickClock() {
    Animation animation = new Animation("loop", 0.1, true, Map.of());
    AnimationState state = new AnimationState();
    state.play(animation);

    state.advance();
    state.advance();
    state.advance();

    assertEquals(0.05, state.time(), EPSILON);
    assertFalse(state.complete());
  }
}
