package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnimationStateTest {
  @Test
  void playbackHonorsCompiledFrameDurations() {
    CompiledAnimation animation =
        new CompiledAnimation(
            "once",
            0.15,
            AnimationLoopMode.ONCE,
            List.of(frame(0.0, 0), frame(0.10, 2), frame(0.15, 1)));
    AnimationState state = new AnimationState();
    state.play(animation);

    assertEquals(0.0, state.frame().time());
    assertTrue(state.dirty());
    assertFalse(state.complete());

    state.markRendered();
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertFalse(state.dirty());

    state.advance();
    assertEquals(0.10, state.frame().time());
    assertTrue(state.dirty());

    state.markRendered();
    state.advance();
    assertEquals(0.15, state.frame().time());
    assertFalse(state.complete());

    state.markRendered();
    state.advance();
    assertTrue(state.complete());
    assertEquals(0.15, state.frame().time());
  }

  @Test
  void loopingPlaybackUsesTerminalLengthFrameOnlyAsSeamMarker() {
    CompiledAnimation animation =
        new CompiledAnimation(
            "loop",
            0.10,
            AnimationLoopMode.LOOP,
            List.of(frame(0.0, 0), frame(0.05, 1), frame(0.10, 1)));
    AnimationState state = new AnimationState();
    state.play(animation);

    state.markRendered();
    state.advance();
    assertEquals(0.05, state.frame().time());

    state.markRendered();
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertFalse(state.complete());
  }

  private static CompiledAnimation.Frame frame(double time, int durationTicks) {
    return new CompiledAnimation.Frame(time, durationTicks, false, Map.of());
  }
}
