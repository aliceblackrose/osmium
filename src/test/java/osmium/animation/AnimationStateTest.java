package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.math.Vec3;
import osmium.model.Bone;

final class AnimationStateTest {
  @Test
  void playbackHonorsCompiledFrameDurations() {
    Animation animation = new Animation("once", 0.15, AnimationLoopMode.ONCE, Map.of());
    AnimationState state = configuredState(2);
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
    assertEquals(2, state.interpolationDurationTicks());
    assertTrue(state.dirty());

    state.markRendered();
    state.advance();
    assertEquals(0.15, state.frame().time());
    assertEquals(1, state.interpolationDurationTicks());
    assertFalse(state.complete());

    state.markRendered();
    state.advance();
    assertTrue(state.complete());
    assertEquals(0.15, state.frame().time());
  }

  @Test
  void loopingPlaybackUsesTerminalLengthFrameOnlyAsSeamMarker() {
    Animation animation = new Animation("loop", 0.10, AnimationLoopMode.LOOP, Map.of());
    AnimationState state = configuredState(1);
    state.play(animation);

    state.markRendered();
    state.advance();
    assertEquals(0.05, state.frame().time());

    state.markRendered();
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertEquals(1, state.interpolationDurationTicks());
    assertFalse(state.complete());
  }

  private static AnimationState configuredState(int interpolationTicks) {
    Bone root = new Bone("root", "root", "root", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    AnimationState state = new AnimationState();
    state.configure(root, interpolationTicks);
    return state;
  }
}
