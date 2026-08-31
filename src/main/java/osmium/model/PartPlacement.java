package osmium.model;

import osmium.math.Vec3;

/** Static cube placement that can be baked into an item's FIXED display transform. */
public final class PartPlacement {
  private static final double ROTATION_EPSILON = 1.0E-9;

  private PartPlacement() {}

  /**
   * Unrotated cubes can keep their display entity exactly on the animated bone pivot. Their static
   * center offset is baked into the item model instead, so the client rotates the offset around the
   * pivot continuously rather than linearly interpolating a re-computed cube-center translation.
   */
  public static boolean canBakeIntoItemModel(Cube cube) {
    Vec3 rotation = cube.rotation();
    return Math.abs(rotation.x()) <= ROTATION_EPSILON
        && Math.abs(rotation.y()) <= ROTATION_EPSILON
        && Math.abs(rotation.z()) <= ROTATION_EPSILON;
  }

  /** Returns the Minecraft item-model translation in model units (1 unit = 1/16 block). */
  public static Vec3 itemModelTranslation(Bone bone, Cube cube) {
    Vec3 offset = cube.center().subtract(bone.origin());
    return new Vec3(-offset.x(), offset.y(), -offset.z());
  }
}
