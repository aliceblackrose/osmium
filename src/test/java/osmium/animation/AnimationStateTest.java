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
  void playbackHonorsCompiledFrameDurationsAtFortyHertz() {
    Animation animation = new Animation("once", 0.15, AnimationLoopMode.ONCE, Map.of());
    AnimationState state = configuredState(2);
    state.play(animation);

    assertEquals(0.0, state.frame().time());
    assertTrue(state.dirty());
    assertFalse(state.complete());

    state.markRendered();
    state.advance();
    state.advance();
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertFalse(state.dirty());

    state.advance();
    assertEquals(0.10, state.frame().time());
    assertEquals(2, state.interpolationDurationTicks());
    assertTrue(state.dirty());

    state.markRendered();
    state.advance();
    assertEquals(0.10, state.frame().time());
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
    assertEquals(0.0, state.frame().time());
    state.advance();
    assertEquals(0.05, state.frame().time());

    state.markRendered();
    state.advance();
    assertEquals(0.05, state.frame().time());
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertEquals(1, state.interpolationDurationTicks());
    assertFalse(state.complete());
  }

  @Test
  void loopingTwentyFiveMillisecondSeamKeepsOneTickBlend() {
    Bone root = new Bone("root", "root", "root", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    BoneTimeline timeline = new BoneTimeline();
    timeline.position().add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    timeline.position().add(new Keyframe(0.05, new Vec3(1, 0, 0), Interpolation.LINEAR));

    Animation animation =
        new Animation("short_seam", 0.075, AnimationLoopMode.LOOP, Map.of(root.name(), timeline));
    AnimationState state = new AnimationState();
    state.configure(root, 0);
    state.play(animation);

    state.markRendered();
    state.advance();
    assertEquals(0.0, state.frame().time());
    state.advance();
    assertEquals(0.05, state.frame().time());

    state.markRendered();
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertEquals(1, state.interpolationDurationTicks());
    assertTrue(state.dirty());
    assertFalse(state.complete());
  }

  @Test
  void changingAnimationsBlendsFromThePreviousRenderedPose() {
    Animation first = new Animation("first", 0.10, AnimationLoopMode.LOOP, Map.of());
    Animation second = new Animation("second", 0.025, AnimationLoopMode.ONCE, Map.of());
    AnimationState state = configuredState(3);
    state.play(first);
    state.markRendered();

    state.play(second);

    assertEquals(0.0, state.frame().time());
    assertEquals(1, state.interpolationDurationTicks());
    assertTrue(state.dirty());

    state.markRendered();
    state.advance();
    assertEquals(0.0, state.frame().time());
    assertFalse(state.dirty());

    state.advance();
    assertEquals(0.025, state.frame().time());
    assertEquals(0, state.interpolationDurationTicks());
    assertTrue(state.dirty());
  }

  private static AnimationState configuredState(int interpolationTicks) {
    Bone root = new Bone("root", "root", "root", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    AnimationState state = new AnimationState();
    state.configure(root, interpolationTicks);
    return state;
  }
}
