package osmium.model;

import java.util.LinkedHashMap;
import java.util.Map;
import osmium.math.Vec3;

public record Cube(
    String uuid,
    String name,
    Vec3 from,
    Vec3 to,
    Vec3 origin,
    Vec3 rotation,
    double inflate,
    boolean visible,
    Map<String, Face> faces) {
  /**
   * Minimum generated thickness for renderable geometry, in Blockbench model units.
   *
   * <p>Exactly-flat and extremely thin planes can occupy effectively the same depth as the surface
   * below them, which causes the client depth buffer to alternate which face wins. 1/64 of a model
   * unit is only 1/1024 of a block, so this gives overlays such as eyes stable depth without making
   * them visibly thick.
   */
  private static final double MIN_RENDER_THICKNESS = 1.0 / 64.0;

  public Cube {
    faces = new LinkedHashMap<>(faces);
    if (visible && !faces.isEmpty()) {
      inflate = Math.max(inflate, minimumDepthInflation(from, to));
    }
  }

  /** Compatibility constructor for programmatic cubes that should be rendered. */
  public Cube(
      String uuid,
      String name,
      Vec3 from,
      Vec3 to,
      Vec3 origin,
      Vec3 rotation,
      double inflate,
      Map<String, Face> faces) {
    this(uuid, name, from, to, origin, rotation, inflate, true, faces);
  }

  public Vec3 center() {
    return new Vec3((from.x() + to.x()) / 2, (from.y() + to.y()) / 2, (from.z() + to.z()) / 2);
  }

  public Vec3 signedSize() {
    return to.subtract(from);
  }

  public boolean reversed() {
    return signedSize().x() < 0 || signedSize().y() < 0 || signedSize().z() < 0;
  }

  public boolean renderable() {
    return visible && !faces.isEmpty();
  }

  private static double minimumDepthInflation(Vec3 from, Vec3 to) {
    Vec3 size = to.subtract(from);
    double smallestAxis =
        Math.min(Math.abs(size.x()), Math.min(Math.abs(size.y()), Math.abs(size.z())));
    if (smallestAxis >= MIN_RENDER_THICKNESS) {
      return 0;
    }

    return (MIN_RENDER_THICKNESS - smallestAxis) * 0.5;
  }
}
