package osmium.render;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import osmium.math.TransformDecomposition;

/** Chooses the stable display representation for a local model transform. */
final class DisplayTransform {
  private DisplayTransform() {}

  /**
   * Pure translation/rotation/positive-scale matrices should bypass Minecraft's generic SVD matrix
   * decomposition. SVD is only necessary when inherited non-uniform scale actually introduces
   * shear.
   */
  static boolean canUseDirectTrs(Matrix4f matrix) {
    return TransformDecomposition.canUseDirectTrs(matrix);
  }

  static Transformation directTrs(Matrix4f matrix) {
    TransformDecomposition.Components components = TransformDecomposition.direct(matrix);
    return new Transformation(
        components.translation(),
        components.leftRotation(),
        components.scale(),
        components.rightRotation());
  }
}
