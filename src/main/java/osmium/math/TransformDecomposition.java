package osmium.math;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Stable display-transform decomposition for matrices that do not require SVD. */
public final class TransformDecomposition {
  private static final float SHEAR_EPSILON = 1.0E-4F;
  private static final float MINIMUM_BASIS_LENGTH_SQUARED = 1.0E-12F;

  private TransformDecomposition() {}

  /**
   * Returns whether a matrix is representable as translation + rotation + positive scale without
   * shear. These matrices should bypass Minecraft's generic SVD decomposition, especially when one
   * scale component is very small during authored blink/squash animation.
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

    if (!approximatelyOrthogonal(xy, xLengthSquared, yLengthSquared)
        || !approximatelyOrthogonal(xz, xLengthSquared, zLengthSquared)
        || !approximatelyOrthogonal(yz, yLengthSquared, zLengthSquared)) {
      return false;
    }

    return determinant3x3(matrix) > 0.0F;
  }

  /**
   * Extracts a stable direct-TRS representation. Call only when {@link #canUseDirectTrs} is true.
   */
  public static Components direct(Matrix4f matrix) {
    return new Components(
        matrix.getTranslation(new Vector3f()),
        matrix.getUnnormalizedRotation(new Quaternionf()).normalize(),
        matrix.getScale(new Vector3f()),
        new Quaternionf());
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

  public record Components(
      Vector3f translation, Quaternionf leftRotation, Vector3f scale, Quaternionf rightRotation) {
    public Components {
      translation = new Vector3f(translation);
      leftRotation = new Quaternionf(leftRotation);
      scale = new Vector3f(scale);
      rightRotation = new Quaternionf(rightRotation);
    }
  }
}
