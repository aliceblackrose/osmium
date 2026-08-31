package osmium.render;

import osmium.math.Transforms;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.ModelBlueprint;

/** Finds a tiny outward offset for authored flat overlays that sit on a parent bone surface. */
final class OverlayDepthBias {
  private static final double FLAT_AXIS_EPSILON = 1.0E-6;
  private static final double MAX_SURFACE_DISTANCE = 0.25;
  private static final double DEPTH_BIAS = 1.0 / 32.0;

  private OverlayDepthBias() {}

  static Vec3 minecraftOffset(ModelBlueprint blueprint, Bone bone) {
    Bone parent = bone.parent();
    if (parent == null) {
      return Vec3.ZERO;
    }

    Candidate closest = null;
    for (String cubeId : bone.cubeIds()) {
      Cube overlay = blueprint.cube(cubeId).orElse(null);
      if (overlay == null || !overlay.renderable()) {
        continue;
      }

      Vec3 size = overlay.signedSize();
      if (Math.abs(size.x()) <= FLAT_AXIS_EPSILON) {
        closest = closest(closest, surfaceCandidate(blueprint, parent, overlay.center().x(), Axis.X));
      }
      if (Math.abs(size.y()) <= FLAT_AXIS_EPSILON) {
        closest = closest(closest, surfaceCandidate(blueprint, parent, overlay.center().y(), Axis.Y));
      }
      if (Math.abs(size.z()) <= FLAT_AXIS_EPSILON) {
        closest = closest(closest, surfaceCandidate(blueprint, parent, overlay.center().z(), Axis.Z));
      }
    }

    return closest == null ? Vec3.ZERO : Transforms.bbLocalToMc(closest.blockbenchOffset());
  }

  private static Candidate surfaceCandidate(
      ModelBlueprint blueprint, Bone parent, double planeCoordinate, Axis axis) {
    Candidate closest = null;

    for (String cubeId : parent.cubeIds()) {
      Cube support = blueprint.cube(cubeId).orElse(null);
      if (support == null || !support.renderable()) {
        continue;
      }

      double from = axis.value(support.from());
      double to = axis.value(support.to());
      double inflation = Math.max(0, support.inflate());
      double minimum = Math.min(from, to) - inflation;
      double maximum = Math.max(from, to) + inflation;

      closest = closest(closest, candidate(planeCoordinate, minimum, axis, -1));
      closest = closest(closest, candidate(planeCoordinate, maximum, axis, 1));
    }

    return closest;
  }

  private static Candidate candidate(
      double planeCoordinate, double surfaceCoordinate, Axis axis, int direction) {
    double distance = Math.abs(planeCoordinate - surfaceCoordinate);
    if (distance > MAX_SURFACE_DISTANCE) {
      return null;
    }

    return new Candidate(distance, axis.offset(direction * DEPTH_BIAS));
  }

  private static Candidate closest(Candidate first, Candidate second) {
    if (second == null) {
      return first;
    }
    if (first == null || second.distance() < first.distance()) {
      return second;
    }
    return first;
  }

  private record Candidate(double distance, Vec3 blockbenchOffset) {}

  private enum Axis {
    X {
      @Override
      double value(Vec3 vector) {
        return vector.x();
      }

      @Override
      Vec3 offset(double amount) {
        return new Vec3(amount, 0, 0);
      }
    },
    Y {
      @Override
      double value(Vec3 vector) {
        return vector.y();
      }

      @Override
      Vec3 offset(double amount) {
        return new Vec3(0, amount, 0);
      }
    },
    Z {
      @Override
      double value(Vec3 vector) {
        return vector.z();
      }

      @Override
      Vec3 offset(double amount) {
        return new Vec3(0, 0, amount);
      }
    };

    abstract double value(Vec3 vector);

    abstract Vec3 offset(double amount);
  }
}
