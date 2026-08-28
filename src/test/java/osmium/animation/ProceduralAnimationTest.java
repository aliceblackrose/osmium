package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ProceduralAnimationTest {
  @Test
  void secondaryMotionBonesAreDetectedByCommonNames() {
    assertTrue(ProceduralAnimation.isSpringBone("tail_tip"));
    assertTrue(ProceduralAnimation.isSpringBone("wing_left"));
    assertTrue(ProceduralAnimation.isSpringBone("antenna_right"));
    assertTrue(ProceduralAnimation.isSpringBone("cape_back"));
    assertFalse(ProceduralAnimation.isSpringBone("head"));
  }

  @Test
  void bonePresetsMatchCaseInsensitiveFragments() {
    ProceduralBonePreset preset =
        new ProceduralBonePreset("secondary", "tail, wing | antenna", true, 1, 1, 1, 1, 1, 1, 1, 1, 0.3, 0.7);

    assertTrue(preset.matches("Tail_Tip"));
    assertTrue(preset.matches("left_wing"));
    assertTrue(preset.matches("ANTENNA_RIGHT"));
    assertFalse(preset.matches("head"));
  }
}
