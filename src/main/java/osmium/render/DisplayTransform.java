package osmium.render;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Chooses the stable display representation for a local model transform. */
final class DisplayTransform {
  private static final float SHEAR_EPSILON = 1.0E-4F;
  private static final float MINIMUM_BASIS_LENGTH_SQUARED = 1.0E-12F;

  private DisplayTransform() {}

  /**
   * Pure translation/rotation/positive-scale matrices should bypass Minecraft's generic SVD matrix
   * decomposition. SVD is only necessary when inherited non-uniform scale actually introduces
   * shear.
   */
  static boolean canUseDirectTrs(Matrix4f matrix) {
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

    return determinant3x3(matrix) > 0;
  }

  static Transformation directTrs(Matrix4f matrix) {
    Vector3f translation = matrix.getTranslation(new Vector3f());
    Quaternionf rotation = directRotation(matrix);
    canonicalize(rotation);
    Vector3f scale = matrix.getScale(new Vector3f());
    return new Transformation(translation, rotation, scale, new Quaternionf());
  }

  static Transformation directTrs(Matrix4f matrix, Quaternionf previousRotation) {
    Vector3f translation = matrix.getTranslation(new Vector3f());
    Quaternionf rotation = directRotation(matrix);
    if (previousRotation == null) {
      canonicalize(rotation);
    } else {
      keepSameHemisphere(rotation, previousRotation);
      previousRotation.set(rotation);
    }
    Vector3f scale = matrix.getScale(new Vector3f());
    return new Transformation(translation, rotation, scale, new Quaternionf());
  }

  private static Quaternionf directRotation(Matrix4f matrix) {
    return matrix.getUnnormalizedRotation(new Quaternionf()).normalize();
  }

  private static void canonicalize(Quaternionf rotation) {
    if (rotation.w < 0
        || (rotation.w == 0
            && (rotation.x < 0
                || (rotation.x == 0 && (rotation.y < 0 || (rotation.y == 0 && rotation.z < 0)))))) {
      rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
    }
  }

  private static void keepSameHemisphere(Quaternionf rotation, Quaternionf previousRotation) {
    float dot =
        rotation.x * previousRotation.x
            + rotation.y * previousRotation.y
            + rotation.z * previousRotation.z
            + rotation.w * previousRotation.w;
    if (dot < 0) {
      rotation.set(-rotation.x, -rotation.y, -rotation.z, -rotation.w);
    }
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
