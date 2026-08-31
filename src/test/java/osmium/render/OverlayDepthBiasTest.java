package osmium.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.Face;
import osmium.model.ModelBlueprint;

final class OverlayDepthBiasTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void eyePlaneNearParentSurfaceGetsOutwardBias() {
    Bone root = bone("root", Vec3.ZERO, Vec3.ZERO);
    Bone head = bone("head", new Vec3(24, 24, 0), Vec3.ZERO);
    Bone eye = bone("eye", new Vec3(22, 27.5, -4.025), head.origin());
    root.addChild(head);
    head.addChild(eye);

    Cube headCube = cube("head-cube", new Vec3(20, 24, -4), new Vec3(28, 34, 4));
    Cube eyePlane = cube("eye-plane", new Vec3(21, 27, -4.025), new Vec3(23, 28, -4.025));
    head.addCube(headCube.uuid());
    eye.addCube(eyePlane.uuid());

    ModelBlueprint blueprint = blueprint(root, head, eye, headCube, eyePlane);
    Vec3 offset = OverlayDepthBias.minecraftOffset(blueprint, eye);

    assertEquals(0.0, offset.x(), EPSILON);
    assertEquals(0.0, offset.y(), EPSILON);
    assertEquals(1.0 / 512.0, offset.z(), EPSILON);
  }

  @Test
  void internalFlatPlaneIsNotMistakenForSurfaceOverlay() {
    Bone root = bone("root", Vec3.ZERO, Vec3.ZERO);
    Bone head = bone("head", new Vec3(24, 24, 0), Vec3.ZERO);
    Bone tongue = bone("tongue", new Vec3(24, 25.25, -2), head.origin());
    root.addChild(head);
    head.addChild(tongue);

    Cube headCube = cube("head-cube", new Vec3(20, 24, -4), new Vec3(28, 34, 4));
    Cube tonguePlane = cube("tongue-plane", new Vec3(23, 25.25, -4), new Vec3(25, 25.25, 0));
    head.addCube(headCube.uuid());
    tongue.addCube(tonguePlane.uuid());

    ModelBlueprint blueprint = blueprint(root, head, tongue, headCube, tonguePlane);

    assertEquals(Vec3.ZERO, OverlayDepthBias.minecraftOffset(blueprint, tongue));
  }

  private static Bone bone(String name, Vec3 origin, Vec3 parentOrigin) {
    return new Bone(name, name, name + "-uuid", origin, parentOrigin, Vec3.ZERO, true);
  }

  private static Cube cube(String id, Vec3 from, Vec3 to) {
    return new Cube(
        id,
        id,
        from,
        to,
        from.add(to).divide(2),
        Vec3.ZERO,
        0,
        Map.of("north", new Face("north", 0, 0, 1, 1, 0, "0")));
  }

  private static ModelBlueprint blueprint(
      Bone root, Bone parent, Bone child, Cube parentCube, Cube childCube) {
    return new ModelBlueprint(
        "test",
        Path.of("test.bbmodel"),
        root,
        Map.of(root.name(), root, parent.name(), parent, child.name(), child),
        Map.of(root.uuid(), root, parent.uuid(), parent, child.uuid(), child),
        Map.of(parentCube.uuid(), parentCube, childCube.uuid(), childCube),
        Map.of(),
        Map.of());
  }
}
