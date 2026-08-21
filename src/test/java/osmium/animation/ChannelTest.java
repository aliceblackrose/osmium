package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import osmium.math.Vec3;

final class ChannelTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void linearlyInterpolatesBetweenFrames() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, new Vec3(0, 0, 0), Interpolation.LINEAR));
    channel.add(new Keyframe(1.0, new Vec3(10, 20, 30), Interpolation.LINEAR));

    Vec3 sample = channel.sample(0.5);

    assertVec3(new Vec3(5, 10, 15), sample);
  }

  @Test
  void stepInterpolationHoldsPreviousPostValue() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(
        new Keyframe(
            0.0, new Vec3(1, 2, 3), new Vec3(4, 5, 6), Interpolation.STEP));
    channel.add(new Keyframe(1.0, new Vec3(10, 20, 30), Interpolation.LINEAR));

    assertVec3(new Vec3(4, 5, 6), channel.sample(0.75));
  }

  @Test
  void smoothInterpolationUsesSmoothstepCurve() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.SMOOTH));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.LINEAR));

    assertEquals(1.5625, channel.sample(0.25).x(), EPSILON);
  }

  @Test
  void framesMayBeAddedOutOfOrder() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(2.0, new Vec3(20, 0, 0), Interpolation.LINEAR));
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.LINEAR));

    assertEquals(15.0, channel.sample(1.5).x(), EPSILON);
  }

  private static void assertVec3(Vec3 expected, Vec3 actual) {
    assertEquals(expected.x(), actual.x(), EPSILON);
    assertEquals(expected.y(), actual.y(), EPSILON);
    assertEquals(expected.z(), actual.z(), EPSILON);
  }
}
