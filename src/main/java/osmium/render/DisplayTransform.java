package osmium.render;

import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import osmium.math.MatrixDecomposition;

/** Chooses the stable display representation for a local model transform. */
final class DisplayTransform {
  private DisplayTransform() {}

  /**
   * Pure translation/rotation/positive-scale matrices should bypass Minecraft's generic SVD matrix
   * decomposition. SVD is only necessary when inherited non-uniform scale actually introduces
   * shear.
   */
  static boolean canUseDirectTrs(Matrix4f matrix) {
    return MatrixDecomposition.canUseDirectTrs(matrix);
  }

  static Transformation directTrs(Matrix4f matrix) {
    Vector3f translation = new Vector3f();
    Quaternionf rotation = new Quaternionf();
    Vector3f scale = new Vector3f();
    MatrixDecomposition.directTrs(matrix, translation, rotation, scale);
    return new Transformation(translation, rotation, scale, new Quaternionf());
  }
}
