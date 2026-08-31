package osmium.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
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
  void inheritedNonUniformScaleAndChildRotationKeepMatrixPath() {
    Matrix4f sheared = new Matrix4f().scale(2.0F, 1.0F, 0.5F).rotateY(0.7F);

    assertFalse(DisplayTransform.canUseDirectTrs(sheared));
  }

  @Test
  void reflectedTransformsKeepMatrixPath() {
    Matrix4f reflected = new Matrix4f().scale(-1.0F, 1.0F, 1.0F);

    assertFalse(DisplayTransform.canUseDirectTrs(reflected));
  }
}
