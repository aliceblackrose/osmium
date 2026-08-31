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
   * Minimum final thickness for renderable geometry, in Blockbench model units.
   *
   * <p>Exactly-flat planes can share the same depth as nearby surfaces and z-fight. Expanding only
   * the flat axis keeps adjacent eye/overlay rectangles from growing into each other on their X/Y
   * axes while still giving the depth buffer a stable surface.
   */
  private static final double MIN_RENDER_THICKNESS = 1.0 / 64.0;

  public Cube {
    faces = new LinkedHashMap<>(faces);
    if (visible && !faces.isEmpty()) {
      Vec3 padding = minimumAxisPadding(from, to, inflate);
      from = new Vec3(from.x() - padding.x(), from.y() - padding.y(), from.z() - padding.z());
      to = new Vec3(to.x() + padding.x(), to.y() + padding.y(), to.z() + padding.z());
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

  private static Vec3 minimumAxisPadding(Vec3 from, Vec3 to, double inflation) {
    Vec3 size = to.subtract(from);
    return new Vec3(
        minimumAxisPadding(size.x(), inflation),
        minimumAxisPadding(size.y(), inflation),
        minimumAxisPadding(size.z(), inflation));
  }

  private static double minimumAxisPadding(double size, double inflation) {
    double finalThickness = Math.abs(size) + Math.max(0, inflation) * 2;
    if (finalThickness >= MIN_RENDER_THICKNESS) {
      return 0;
    }

    double padding = (MIN_RENDER_THICKNESS - finalThickness) * 0.5;
    return size < 0 ? -padding : padding;
  }
}
