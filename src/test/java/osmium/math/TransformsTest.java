package osmium.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

final class TransformsTest {
  private static final double EPSILON = 1.0E-6;

  @Test
  void animationPositionUsesSameBasisAsStaticPosition() {
    Vec3 position = new Vec3(16, 8, -4);

    assertEquals(Transforms.bbLocalToMc(position), Transforms.animationPosition(position));
  }

  @Test
  void animationRotationUsesCurrentBlockbenchBasis() {
    Vec3 rotation = new Vec3(10, 20, 30);
    Quaternionf expected = Transforms.staticRotation(rotation);
    Quaternionf actual = Transforms.animationRotation(rotation);

    assertEquals(expected.x, actual.x, EPSILON);
    assertEquals(expected.y, actual.y, EPSILON);
    assertEquals(expected.z, actual.z, EPSILON);
    assertEquals(expected.w, actual.w, EPSILON);
  }

  @Test
  void axisConversionRotatesLocalModelGeometryIntoRuntimeBasis() {
    Vector3f position = new Vector3f(1, 2, 3);

    Transforms.axisConversionRotation().transform(position);

    assertEquals(-1, position.x, EPSILON);
    assertEquals(2, position.y, EPSILON);
    assertEquals(-3, position.z, EPSILON);
  }
}
