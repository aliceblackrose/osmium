package osmium.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.math.Vec3;

final class PartPlacementTest {
  @Test
  void unrotatedCubeCanBakeCenterOffsetIntoItemModel() {
    Bone bone =
        new Bone(
            "bed",
            "bed",
            "bed-bone",
            new Vec3(0, 16.2059906, 2.4364587),
            Vec3.ZERO,
            Vec3.ZERO,
            true);
    Cube cube =
        new Cube(
            "bed-cube",
            "bed",
            new Vec3(-6.5, 29.6576573, 2.4762918),
            new Vec3(6.5, 31.6576573, 4.4762918),
            Vec3.ZERO,
            Vec3.ZERO,
            0,
            Map.of());

    assertTrue(PartPlacement.canBakeIntoItemModel(cube));

    Vec3 translation = PartPlacement.itemModelTranslation(bone, cube);
    assertEquals(0.0, translation.x(), 1.0E-9);
    assertEquals(14.4516667, translation.y(), 1.0E-7);
    assertEquals(-1.0398331, translation.z(), 1.0E-7);
  }

  @Test
  void rotatedCubeKeepsRuntimePlacementFallback() {
    Cube cube =
        new Cube(
            "rotated",
            "rotated",
            Vec3.ZERO,
            Vec3.ONE,
            Vec3.ZERO,
            new Vec3(0, 22.5, 0),
            0,
            Map.of());

    assertFalse(PartPlacement.canBakeIntoItemModel(cube));
  }
}
