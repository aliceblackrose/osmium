package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import osmium.math.Vec3;

final class BoneTimelineTest {
  private static final double EPSILON = 1.0E-9;

  @Test
  void runtimeSampleConvertsBlockbenchXToMinecraftBasis() {
    BoneTimeline timeline = new BoneTimeline();
    timeline.position().add(new Keyframe(0.0, new Vec3(4, 5, 6), Interpolation.LINEAR));

    BoneTimeline.Sample sample = timeline.sample(0.0);

    assertEquals(-4.0, sample.position().x(), EPSILON);
    assertEquals(5.0, sample.position().y(), EPSILON);
    assertEquals(6.0, sample.position().z(), EPSILON);
  }
}
