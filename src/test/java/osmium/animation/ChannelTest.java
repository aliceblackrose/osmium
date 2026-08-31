package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import osmium.math.Vec3;

final class ChannelTest {
  private static final double EPSILON = 1.0E-5;

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
    channel.add(new Keyframe(0.0, new Vec3(1, 2, 3), new Vec3(4, 5, 6), Interpolation.STEP));
    channel.add(new Keyframe(1.0, new Vec3(10, 20, 30), Interpolation.CATMULL_ROM));

    assertVec3(new Vec3(4, 5, 6), channel.sample(0.75));
  }

  @Test
  void legacySmoothInterpolationUsesSmoothstepCurve() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.SMOOTH));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.LINEAR));

    assertEquals(1.5625, channel.sample(0.25).x(), EPSILON);
  }

  @Test
  void catmullRomUsesNeighboringKeyframes() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.CATMULL_ROM));
    channel.add(new Keyframe(2.0, Vec3.ZERO, Interpolation.LINEAR));
    channel.add(new Keyframe(3.0, Vec3.ZERO, Interpolation.LINEAR));

    assertEquals(5.625, channel.sample(1.5).x(), EPSILON);
  }

  @Test
  void incomingCatmullRomControlsPreviousSegment() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.CATMULL_ROM));
    channel.add(new Keyframe(2.0, Vec3.ZERO, Interpolation.LINEAR));

    assertEquals(5.625, channel.sample(0.5).x(), EPSILON);
  }

  @Test
  void loopingChannelInterpolatesAcrossAnimationSeam() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.5, Vec3.ZERO, Interpolation.LINEAR));
    channel.add(new Keyframe(1.5, new Vec3(10, 0, 0), Interpolation.LINEAR));

    assertEquals(7.5, channel.sample(1.75, true, 2.0).x(), EPSILON);
    assertEquals(2.5, channel.sample(0.25, true, 2.0).x(), EPSILON);
  }

  @Test
  void loopingCatmullRomUsesBlockbenchBoundaryControlPoints() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.CATMULL_ROM));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), Interpolation.CATMULL_ROM));
    channel.add(new Keyframe(2.0, new Vec3(20, 0, 0), Interpolation.CATMULL_ROM));
    channel.add(new Keyframe(3.0, new Vec3(100, 0, 0), Interpolation.CATMULL_ROM));

    assertEquals(3.125, channel.sample(0.5, true, 4.0).x(), EPSILON);
  }

  @Test
  void bezierUsesPerAxisTimeAndValueHandles() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(
        new Keyframe(
            0.0,
            Vec3.ZERO,
            Vec3.ZERO,
            Interpolation.BEZIER,
            Vec3.ZERO,
            new Vec3(0.25, 0, 0),
            Vec3.ZERO,
            new Vec3(10, 0, 0)));
    channel.add(
        new Keyframe(
            1.0,
            new Vec3(10, 0, 0),
            new Vec3(10, 0, 0),
            Interpolation.LINEAR,
            new Vec3(-0.25, 0, 0),
            Vec3.ZERO,
            Vec3.ZERO,
            Vec3.ZERO));

    assertEquals(8.75, channel.sample(0.5).x(), EPSILON);
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
