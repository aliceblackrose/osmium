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
}
