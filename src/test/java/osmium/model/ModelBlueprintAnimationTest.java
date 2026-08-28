package osmium.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import osmium.animation.Animation;
import osmium.animation.AnimationLoopMode;
import osmium.math.Vec3;

final class ModelBlueprintAnimationTest {
  @Test
  void semanticAliasesMatchTokensInsideExportedAnimationNames() {
    Bone root = new Bone("root", "root", "root", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    Animation idle =
        new Animation("redstone_golem_idle_animation_1", 5.0, AnimationLoopMode.LOOP, Map.of());
    Animation walk =
        new Animation("redstone_golem_walk_animation_1", 2.25, AnimationLoopMode.LOOP, Map.of());
    ModelBlueprint model =
        new ModelBlueprint(
            "golem",
            Path.of("golem.bbmodel"),
            root,
            Map.of("root", root),
            Map.of("root", root),
            Map.of(),
            Map.of(),
            Map.of(idle.name(), idle, walk.name(), walk));

    assertEquals(idle, model.animation("idle").orElseThrow());
    assertEquals(walk, model.animation("walk").orElseThrow());
    assertTrue(model.animation("attack").isEmpty());
  }
}
