package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import osmium.math.Vec3;

final class ChannelExactKeyframeTest {
  @Test
  void exactStepTargetUsesIncomingKeyframeValue() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.STEP));
    channel.add(new Keyframe(0.5, new Vec3(10, 0, 0), Interpolation.LINEAR));

    assertEquals(0.0, channel.sample(0.45).x(), 1.0E-9);
    assertEquals(10.0, channel.sample(0.50).x(), 1.0E-9);
  }

  @Test
  void duplicateTimesUseFirstIncomingAndLastOutgoingValue() {
    Channel channel = new Channel(Vec3.ZERO);
    channel.add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    channel.add(new Keyframe(1.0, new Vec3(10, 0, 0), new Vec3(20, 0, 0), Interpolation.LINEAR));
    channel.add(new Keyframe(1.0, new Vec3(30, 0, 0), new Vec3(40, 0, 0), Interpolation.LINEAR));
    channel.add(new Keyframe(2.0, new Vec3(50, 0, 0), Interpolation.LINEAR));

    for (boolean loop : new boolean[] {false, true}) {
      assertEquals(10.0, channel.sample(1.0, loop, 3.0).x(), 1.0E-9);
      assertEquals(10.0, channel.sample(1.0 + 0.5E-9, loop, 3.0).x(), 1.0E-9);
      assertEquals(45.0, channel.sample(1.5, loop, 3.0).x(), 1.0E-9);
    }
  }

  @Test
  void largeChannelFindsEverySegmentAndExactBoundary() {
    Channel channel = new Channel(Vec3.ZERO);
    for (int index = 1023; index >= 0; index--) {
      channel.add(new Keyframe(index, new Vec3(index, 2 * index, -index), Interpolation.LINEAR));
    }

    assertEquals(1024, channel.frameCount());
    for (int index = 0; index < 1023; index++) {
      assertEquals(index, channel.sample(index).x(), 1.0E-9);
      assertEquals(index + 0.5, channel.sample(index + 0.5).x(), 1.0E-9);
      assertEquals(index + 0.5, channel.sample(index + 0.5, true, 1024).x(), 1.0E-9);
    }
    assertEquals(0.0, channel.sample(-1).x(), 1.0E-9);
    assertEquals(1023.0, channel.sample(1024).x(), 1.0E-9);
  }
}
