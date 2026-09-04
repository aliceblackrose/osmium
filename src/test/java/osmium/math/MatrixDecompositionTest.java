package osmium.math;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class MatrixDecompositionTest {
  private static final float EPSILON = 1.0E-5F;

  @Test
  void directTrsExtractsSimpleTransformWithoutGenericDecomposition() {
    Matrix4f matrix =
        new Matrix4f()
            .translate(2.0F, -3.0F, 4.0F)
            .rotateY((float) Math.toRadians(35.0D))
            .scale(1.5F, 2.0F, 0.75F);

    assertTrue(MatrixDecomposition.canUseDirectTrs(matrix));

    Vector3f translation = new Vector3f();
    Quaternionf rotation = new Quaternionf();
    Vector3f scale = new Vector3f();
    MatrixDecomposition.directTrs(matrix, translation, rotation, scale);

    assertEquals(2.0F, translation.x, EPSILON);
    assertEquals(-3.0F, translation.y, EPSILON);
    assertEquals(4.0F, translation.z, EPSILON);
    assertEquals(1.5F, scale.x, EPSILON);
    assertEquals(2.0F, scale.y, EPSILON);
    assertEquals(0.75F, scale.z, EPSILON);

    Vector3f expectedForward = new Vector3f(0.0F, 0.0F, 1.0F);
    Vector3f actualForward = new Vector3f(0.0F, 0.0F, 1.0F);
    new Quaternionf().rotateY((float) Math.toRadians(35.0D)).transform(expectedForward);
    rotation.transform(actualForward);
    assertEquals(expectedForward.x, actualForward.x, EPSILON);
    assertEquals(expectedForward.y, actualForward.y, EPSILON);
    assertEquals(expectedForward.z, actualForward.z, EPSILON);
  }

  @Test
  void shearRequiresGenericDecomposition() {
    Matrix4f matrix = new Matrix4f();
    matrix.m10(0.25F);

    assertFalse(MatrixDecomposition.canUseDirectTrs(matrix));
  }
}
