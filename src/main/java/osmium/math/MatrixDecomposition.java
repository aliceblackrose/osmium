package osmium.math;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Fast-path helpers for matrices that can be represented as direct translation/rotation/scale. */
public final class MatrixDecomposition {
  private static final float SHEAR_EPSILON = 1.0E-4F;
  private static final float MINIMUM_BASIS_LENGTH_SQUARED = 1.0E-12F;

  private MatrixDecomposition() {}

  /**
   * Returns true when the matrix has non-degenerate orthogonal basis vectors and a positive
   * determinant, so generic SVD decomposition is unnecessary.
   */
  public static boolean canUseDirectTrs(Matrix4f matrix) {
    float xLengthSquared =
        matrix.m00() * matrix.m00() + matrix.m01() * matrix.m01() + matrix.m02() * matrix.m02();
    float yLengthSquared =
        matrix.m10() * matrix.m10() + matrix.m11() * matrix.m11() + matrix.m12() * matrix.m12();
    float zLengthSquared =
        matrix.m20() * matrix.m20() + matrix.m21() * matrix.m21() + matrix.m22() * matrix.m22();

    if (xLengthSquared <= MINIMUM_BASIS_LENGTH_SQUARED
        || yLengthSquared <= MINIMUM_BASIS_LENGTH_SQUARED
        || zLengthSquared <= MINIMUM_BASIS_LENGTH_SQUARED) {
      return false;
    }

    float xy =
        matrix.m00() * matrix.m10() + matrix.m01() * matrix.m11() + matrix.m02() * matrix.m12();
    float xz =
        matrix.m00() * matrix.m20() + matrix.m01() * matrix.m21() + matrix.m02() * matrix.m22();
    float yz =
        matrix.m10() * matrix.m20() + matrix.m11() * matrix.m21() + matrix.m12() * matrix.m22();

    return approximatelyOrthogonal(xy, xLengthSquared, yLengthSquared)
        && approximatelyOrthogonal(xz, xLengthSquared, zLengthSquared)
        && approximatelyOrthogonal(yz, yLengthSquared, zLengthSquared)
        && determinant3x3(matrix) > 0.0F;
  }

  /** Writes a direct TRS decomposition into reusable destination objects. */
  public static void directTrs(
      Matrix4f matrix, Vector3f translation, Quaternionf rotation, Vector3f scale) {
    translation.set(matrix.m30(), matrix.m31(), matrix.m32());
    matrix.getUnnormalizedRotation(rotation).normalize();
    matrix.getScale(scale);
  }

  private static boolean approximatelyOrthogonal(
      float dot, float firstLengthSquared, float secondLengthSquared) {
    double maximumDot =
        SHEAR_EPSILON * Math.sqrt((double) firstLengthSquared * secondLengthSquared);
    return Math.abs(dot) <= maximumDot;
  }

  private static float determinant3x3(Matrix4f matrix) {
    return matrix.m00() * (matrix.m11() * matrix.m22() - matrix.m21() * matrix.m12())
        - matrix.m10() * (matrix.m01() * matrix.m22() - matrix.m21() * matrix.m02())
        + matrix.m20() * (matrix.m01() * matrix.m12() - matrix.m11() * matrix.m02());
  }
}
