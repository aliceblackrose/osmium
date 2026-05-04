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
    Map<String, Face> faces) {
  public Cube {
    faces = new LinkedHashMap<>(faces);
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
}
