package osmium.render;

import osmium.math.Transforms;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.ModelBlueprint;

/** Finds a tiny outward offset for authored overlays that sit on a parent bone surface. */
final class OverlayDepthBias {
  private static final double FLAT_AXIS_EPSILON = 1.0E-6;
  private static final double MAX_SURFACE_DISTANCE = 0.25;
  private static final double MAX_EMBEDDED_DEPTH = 2.0;
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
        closest =
            closest(closest, surfaceCandidate(blueprint, parent, overlay.center().x(), Axis.X));
      } else {
        closest = closest(closest, embeddedSurfaceCandidate(blueprint, parent, overlay, Axis.X));
      }
      if (Math.abs(size.y()) <= FLAT_AXIS_EPSILON) {
        closest =
            closest(closest, surfaceCandidate(blueprint, parent, overlay.center().y(), Axis.Y));
      } else {
        closest = closest(closest, embeddedSurfaceCandidate(blueprint, parent, overlay, Axis.Y));
      }
      if (Math.abs(size.z()) <= FLAT_AXIS_EPSILON) {
        closest =
            closest(closest, surfaceCandidate(blueprint, parent, overlay.center().z(), Axis.Z));
      } else {
        closest = closest(closest, embeddedSurfaceCandidate(blueprint, parent, overlay, Axis.Z));
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

  private static Candidate embeddedSurfaceCandidate(
      ModelBlueprint blueprint, Bone parent, Cube overlay, Axis axis) {
    double overlayFrom = axis.value(overlay.from());
    double overlayTo = axis.value(overlay.to());
    double overlayInflation = Math.max(0, overlay.inflate());
    double overlayMinimum = Math.min(overlayFrom, overlayTo) - overlayInflation;
    double overlayMaximum = Math.max(overlayFrom, overlayTo) + overlayInflation;

    Candidate closest = null;
    for (String cubeId : parent.cubeIds()) {
      Cube support = blueprint.cube(cubeId).orElse(null);
      if (support == null || !support.renderable()) {
        continue;
      }

      double supportFrom = axis.value(support.from());
      double supportTo = axis.value(support.to());
      double supportInflation = Math.max(0, support.inflate());
      double supportMinimum = Math.min(supportFrom, supportTo) - supportInflation;
      double supportMaximum = Math.max(supportFrom, supportTo) + supportInflation;

      double minimumGap = supportMinimum - overlayMinimum;
      double minimumPenetration = overlayMaximum - supportMinimum;
      if (minimumGap >= 0
          && minimumGap <= MAX_SURFACE_DISTANCE
          && minimumPenetration > FLAT_AXIS_EPSILON
          && minimumPenetration <= MAX_EMBEDDED_DEPTH) {
        closest = closest(closest, new Candidate(minimumGap, axis.offset(-DEPTH_BIAS)));
      }

      double maximumGap = overlayMaximum - supportMaximum;
      double maximumPenetration = supportMaximum - overlayMinimum;
      if (maximumGap >= 0
          && maximumGap <= MAX_SURFACE_DISTANCE
          && maximumPenetration > FLAT_AXIS_EPSILON
          && maximumPenetration <= MAX_EMBEDDED_DEPTH) {
        closest = closest(closest, new Candidate(maximumGap, axis.offset(DEPTH_BIAS)));
      }
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
