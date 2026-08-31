package osmium.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

final class DisplayTransformTest {
  @Test
  void rigidRotationAndUniformScaleUseDirectTrsPath() {
    Matrix4f matrix =
        new Matrix4f().translate(1.25F, -2.5F, 0.75F).rotateXYZ(0.4F, -1.1F, 0.2F).scale(1.5F);

    assertTrue(DisplayTransform.canUseDirectTrs(matrix));

    Transformation transformation = DisplayTransform.directTrs(matrix);
    assertEquals(1.25F, transformation.getTranslation().x, 1.0E-5F);
    assertEquals(-2.5F, transformation.getTranslation().y, 1.0E-5F);
    assertEquals(0.75F, transformation.getTranslation().z, 1.0E-5F);
    assertEquals(1.5F, transformation.getScale().x, 1.0E-5F);
    assertEquals(1.5F, transformation.getScale().y, 1.0E-5F);
    assertEquals(1.5F, transformation.getScale().z, 1.0E-5F);
  }

  @Test
  void directQuaternionUsesDeterministicHemisphere() {
    Transformation transformation =
        DisplayTransform.directTrs(new Matrix4f().rotateXYZ(2.8F, -1.4F, 2.2F));

    Quaternionf rotation = transformation.getLeftRotation();
    assertTrue(rotation.w > 0 || rotation.w == 0 && firstNonZeroComponentIsPositive(rotation));
  }

  @Test
  void directQuaternionCanFollowPreviousHemisphere() {
    Matrix4f matrix = new Matrix4f().rotateXYZ(0.6F, -1.2F, 0.4F);
    Quaternionf current = DisplayTransform.directTrs(matrix).getLeftRotation();
    Quaternionf previous = new Quaternionf(-current.x, -current.y, -current.z, -current.w);
    Quaternionf requestedHemisphere = new Quaternionf(previous);

    Quaternionf stabilized = DisplayTransform.directTrs(matrix, previous).getLeftRotation();

    assertTrue(dot(stabilized, requestedHemisphere) >= 0);
    assertEquals(stabilized.x, previous.x, 1.0E-6F);
    assertEquals(stabilized.y, previous.y, 1.0E-6F);
    assertEquals(stabilized.z, previous.z, 1.0E-6F);
    assertEquals(stabilized.w, previous.w, 1.0E-6F);
  }

  @Test
  void inheritedNonUniformScaleAndChildRotationKeepMatrixPath() {
    Matrix4f sheared = new Matrix4f().scale(2.0F, 1.0F, 0.5F).rotateY(0.7F);

    assertFalse(DisplayTransform.canUseDirectTrs(sheared));
  }

  @Test
  void reflectedTransformsKeepMatrixPath() {
    Matrix4f reflected = new Matrix4f().scale(-1.0F, 1.0F, 1.0F);

    assertFalse(DisplayTransform.canUseDirectTrs(reflected));
  }

  private static boolean firstNonZeroComponentIsPositive(Quaternionf rotation) {
    if (rotation.x != 0) {
      return rotation.x > 0;
    }
    if (rotation.y != 0) {
      return rotation.y > 0;
    }
    return rotation.z >= 0;
  }

  private static float dot(Quaternionf first, Quaternionf second) {
    return first.x * second.x + first.y * second.y + first.z * second.z + first.w * second.w;
  }
}
