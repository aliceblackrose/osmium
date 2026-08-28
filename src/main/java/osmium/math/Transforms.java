package osmium.math;

import org.joml.Quaternionf;

public final class Transforms {
  public static final double UNIT = 1.0 / 16.0;

  private static final double DEGREES_TO_RADIANS = Math.PI / 180.0;

  private Transforms() {}

  public static Vec3 bbLocalToMc(Vec3 blockbenchPosition) {
    return new Vec3(
        -blockbenchPosition.x() * UNIT,
        blockbenchPosition.y() * UNIT,
        -blockbenchPosition.z() * UNIT);
  }

  public static Vec3 animationPosition(Vec3 blockbenchPosition) {
    return bbLocalToMc(blockbenchPosition);
  }

  public static Quaternionf staticRotation(Vec3 degrees) {
    return new Quaternionf()
        .rotateZYX(radians(-degrees.z()), radians(degrees.y()), radians(-degrees.x()));
  }

  public static Quaternionf animationRotation(Vec3 degrees) {
    return animationRotation(degrees, new Quaternionf());
  }

  public static Quaternionf animationRotation(Vec3 degrees, Quaternionf destination) {
    return destination
        .identity()
        .rotateZYX(radians(-degrees.z()), radians(degrees.y()), radians(-degrees.x()));
  }

  /**
   * Generated Minecraft item geometry is already centered in the display entity's local basis.
   * Applying the Blockbench world-space half-turn here would reverse directional face textures a
   * second time (most visibly the golem's head/eyes).
   */
  public static Quaternionf axisConversionRotation() {
    return new Quaternionf();
  }

  private static float radians(double degrees) {
    return (float) (degrees * DEGREES_TO_RADIANS);
  }
}
