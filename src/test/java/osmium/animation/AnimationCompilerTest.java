package osmium.animation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.math.Vec3;
import osmium.model.Bone;

final class AnimationCompilerTest {
  private static final double EPSILON = 1.0E-6;

  @Test
  void stepBoundaryGetsHoldFrameAndNonInterpolatedTarget() {
    Bone root = bone("root", Vec3.ZERO);
    BoneTimeline timeline = new BoneTimeline();
    timeline.position().add(new Keyframe(0.0, Vec3.ZERO, Interpolation.STEP));
    timeline.position().add(new Keyframe(0.5, new Vec3(10, 0, 0), Interpolation.LINEAR));

    Animation animation =
        new Animation("step", 0.5, AnimationLoopMode.ONCE, Map.of(root.name(), timeline));
    CompiledAnimation compiled = AnimationCompiler.compile(animation, root, 5);

    CompiledAnimation.Frame hold = frameAt(compiled, 0.45);
    CompiledAnimation.Frame target = frameAt(compiled, 0.50);

    assertEquals(0.0, hold.pose(root.name()).position().x(), EPSILON);
    assertEquals(10.0, target.pose(root.name()).position().x(), EPSILON);
    assertFalse(hold.skipInterpolation());
    assertTrue(target.skipInterpolation());
  }

  @Test
  void hierarchicalLargeRotationUsesEveryAvailableServerFrame() {
    Bone root = bone("root", Vec3.ZERO);
    Bone parent = bone("parent", Vec3.ZERO);
    Bone child = bone("child", Vec3.ZERO);
    root.addChild(parent);
    parent.addChild(child);

    BoneTimeline parentTimeline = rotationTimeline(100);
    BoneTimeline childTimeline = rotationTimeline(100);
    Animation animation =
        new Animation(
            "large_rotation",
            0.2,
            AnimationLoopMode.ONCE,
            Map.of(parent.name(), parentTimeline, child.name(), childTimeline));

    CompiledAnimation compiled = AnimationCompiler.compile(animation, root, 0);
    List<Double> times = compiled.frames().stream().map(CompiledAnimation.Frame::time).toList();

    assertEquals(List.of(0.0, 0.05, 0.10, 0.15, 0.20), times);
  }

  @Test
  void authoredTimesAreQuantizedToMinecraftTransportCadence() {
    Bone root = bone("root", Vec3.ZERO);
    BoneTimeline timeline = new BoneTimeline();
    timeline.position().add(new Keyframe(0.041667, new Vec3(1, 0, 0), Interpolation.LINEAR));
    timeline.position().add(new Keyframe(0.083333, new Vec3(2, 0, 0), Interpolation.LINEAR));

    Animation animation =
        new Animation("quantized", 0.2, AnimationLoopMode.ONCE, Map.of(root.name(), timeline));
    CompiledAnimation compiled = AnimationCompiler.compile(animation, root, 0);
    List<Double> times = compiled.frames().stream().map(CompiledAnimation.Frame::time).toList();

    assertTrue(times.contains(0.05));
    assertTrue(times.contains(0.10));
  }

  @Test
  void everyCompiledFrameCarriesEveryAnimatedBonePose() {
    Bone root = bone("root", Vec3.ZERO);
    Bone child = bone("child", Vec3.ZERO);
    root.addChild(child);

    BoneTimeline rootTimeline = new BoneTimeline();
    rootTimeline.position().add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    rootTimeline.position().add(new Keyframe(0.2, new Vec3(2, 0, 0), Interpolation.LINEAR));
    BoneTimeline childTimeline = rotationTimeline(45);

    Animation animation =
        new Animation(
            "shared",
            0.2,
            AnimationLoopMode.ONCE,
            Map.of(root.name(), rootTimeline, child.name(), childTimeline));
    CompiledAnimation compiled = AnimationCompiler.compile(animation, root, 2);

    for (CompiledAnimation.Frame frame : compiled.frames()) {
      assertNotNull(frame.pose(root.name()));
      assertNotNull(frame.pose(child.name()));
    }
  }

  private static BoneTimeline rotationTimeline(double endDegrees) {
    BoneTimeline timeline = new BoneTimeline();
    timeline.rotation().add(new Keyframe(0.0, Vec3.ZERO, Interpolation.LINEAR));
    timeline
        .rotation()
        .add(new Keyframe(0.2, new Vec3(endDegrees, 0, 0), Interpolation.LINEAR));
    return timeline;
  }

  private static Bone bone(String name, Vec3 origin) {
    return new Bone(name, name, name, origin, Vec3.ZERO, Vec3.ZERO, true);
  }

  private static CompiledAnimation.Frame frameAt(CompiledAnimation animation, double time) {
    return animation.frames().stream()
        .filter(frame -> Math.abs(frame.time() - time) <= EPSILON)
        .findFirst()
        .orElseThrow();
  }
}
