package osmium.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class TransformDecompositionTest {
  private static final float EPSILON = 1.0E-5F;

  @Test
  void nearZeroVillagerEyeScaleKeepsDirectTrsPath() {
    Matrix4f matrix =
        new Matrix4f()
            .rotate(Transforms.animationRotation(new Vec3(-133.35, -79.34, 133.76)))
            .translate(0.125F, 0.21875F, 0.25351563F)
            .scale(1.0F, 1.0E-4F, 1.0F);

    assertTrue(TransformDecomposition.canUseDirectTrs(matrix));

    TransformDecomposition.Components components = TransformDecomposition.direct(matrix);
    assertEquals(1.0F, components.scale().x, EPSILON);
    assertEquals(1.0E-4F, components.scale().y, 1.0E-7F);
    assertEquals(1.0F, components.scale().z, EPSILON);
    assertEquals(0.0F, components.rightRotation().x, EPSILON);
    assertEquals(0.0F, components.rightRotation().y, EPSILON);
    assertEquals(0.0F, components.rightRotation().z, EPSILON);
    assertEquals(1.0F, components.rightRotation().w, EPSILON);

    Matrix4f reconstructed =
        new Matrix4f()
            .translate(
                components.translation().x, components.translation().y, components.translation().z)
            .rotate(components.leftRotation())
            .scale(components.scale().x, components.scale().y, components.scale().z);

    assertMatrixEquals(matrix, reconstructed);
  }

  @Test
  void inheritedNonUniformScaleWithChildRotationStillRequiresSvd() {
    Matrix4f sheared = new Matrix4f().scale(2.0F, 1.0F, 0.5F).rotateY(0.7F);

    assertFalse(TransformDecomposition.canUseDirectTrs(sheared));
  }

  private static void assertMatrixEquals(Matrix4f expected, Matrix4f actual) {
    assertEquals(expected.m00(), actual.m00(), EPSILON);
    assertEquals(expected.m01(), actual.m01(), EPSILON);
    assertEquals(expected.m02(), actual.m02(), EPSILON);
    assertEquals(expected.m03(), actual.m03(), EPSILON);
    assertEquals(expected.m10(), actual.m10(), EPSILON);
    assertEquals(expected.m11(), actual.m11(), EPSILON);
    assertEquals(expected.m12(), actual.m12(), EPSILON);
    assertEquals(expected.m13(), actual.m13(), EPSILON);
    assertEquals(expected.m20(), actual.m20(), EPSILON);
    assertEquals(expected.m21(), actual.m21(), EPSILON);
    assertEquals(expected.m22(), actual.m22(), EPSILON);
    assertEquals(expected.m23(), actual.m23(), EPSILON);
    assertEquals(expected.m30(), actual.m30(), EPSILON);
    assertEquals(expected.m31(), actual.m31(), EPSILON);
    assertEquals(expected.m32(), actual.m32(), EPSILON);
    assertEquals(expected.m33(), actual.m33(), EPSILON);
  }
}
