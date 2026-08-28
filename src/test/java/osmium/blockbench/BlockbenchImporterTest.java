package osmium.blockbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import osmium.animation.AnimationLoopMode;
import osmium.animation.BoneTimeline;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.ModelBlueprint;

final class BlockbenchImporterTest {
  private static final double EPSILON = 1.0E-5;

  @TempDir Path tempDirectory;

  @Test
  void fallbackTextureUsesConfiguredNamespace() throws Exception {
    Path modelFile = tempDirectory.resolve("dragon.bbmodel");
    Files.writeString(modelFile, "{\"elements\":[],\"outliner\":[],\"animations\":[]}");

    BlockbenchImporter importer = new BlockbenchImporter(Logger.getAnonymousLogger(), "My Pack");
    ModelBlueprint model = importer.importFile(modelFile);

    assertEquals("my_pack:item/dragon/fallback", model.texture("0").modelPath());
  }

  @Test
  void bezierHandleDataIsPreservedDuringImport() throws Exception {
    Path modelFile = tempDirectory.resolve("curve.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "elements": [],
          "outliner": [],
          "animations": [
            {
              "name": "curve",
              "length": 1.0,
              "loop": "once",
              "animators": {
                "root": {
                  "name": "root",
                  "keyframes": [
                    {
                      "channel": "position",
                      "time": 0.0,
                      "interpolation": "bezier",
                      "data_points": [{"x": 0, "y": 0, "z": 0}],
                      "bezier_right_time": [0.25, 0, 0],
                      "bezier_right_value": [10, 0, 0]
                    },
                    {
                      "channel": "position",
                      "time": 1.0,
                      "interpolation": "linear",
                      "data_points": [{"x": 10, "y": 0, "z": 0}],
                      "bezier_left_time": [-0.25, 0, 0],
                      "bezier_left_value": [0, 0, 0]
                    }
                  ]
                }
              }
            }
          ]
        }
        """);

    BlockbenchImporter importer = new BlockbenchImporter(Logger.getAnonymousLogger(), "osmium");
    ModelBlueprint model = importer.importFile(modelFile);

    double sample =
        model.animation("curve").orElseThrow().timelines().get("root").position().sample(0.5).x();
    assertEquals(8.75, sample, EPSILON);
  }

  @Test
  void missingLoopDefaultsToOnceAndHoldRemainsDistinct() throws Exception {
    Path modelFile = tempDirectory.resolve("loops.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "elements": [],
          "outliner": [],
          "animations": [
            {"name": "attack", "length": 1.0, "animators": {}},
            {"name": "pose", "length": 1.0, "loop": "hold", "animators": {}},
            {"name": "idle", "length": 1.0, "loop": "loop", "animators": {}}
          ]
        }
        """);

    ModelBlueprint model = importer().importFile(modelFile);

    assertEquals(AnimationLoopMode.ONCE, model.animation("attack").orElseThrow().loopMode());
    assertEquals(AnimationLoopMode.HOLD, model.animation("pose").orElseThrow().loopMode());
    assertEquals(AnimationLoopMode.LOOP, model.animation("idle").orElseThrow().loopMode());
  }

  @Test
  void texturelessInvisibleHitboxCubeIsPreserved() throws Exception {
    Path modelFile = tempDirectory.resolve("hitbox.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "elements": [
            {
              "uuid": "hitbox-cube",
              "name": "collision",
              "visibility": false,
              "from": [-4, 0, -4],
              "to": [4, 16, 4],
              "origin": [0, 0, 0],
              "rotation": [0, 0, 0],
              "faces": {}
            }
          ],
          "outliner": [
            {
              "name": "hitbox",
              "uuid": "hitbox-bone",
              "origin": [0, 0, 0],
              "rotation": [0, 0, 0],
              "children": ["hitbox-cube"]
            }
          ],
          "animations": []
        }
        """);

    ModelBlueprint model = importer().importFile(modelFile);
    Cube cube = model.cube("hitbox-cube").orElseThrow();

    assertFalse(cube.visible());
    assertTrue(cube.faces().isEmpty());
    assertEquals(1, model.hitboxes().size());
    assertEquals("hitbox-cube", model.hitboxes().getFirst().cube().uuid());
  }

  @Test
  void preFiveAnimationCoordinatesUseCurrentBlockbenchConvention() throws Exception {
    Path modelFile = tempDirectory.resolve("legacy-animation.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "meta": {"format_version": "4.10"},
          "elements": [],
          "outliner": [],
          "animations": [
            {
              "name": "legacy",
              "length": 1.0,
              "loop": "once",
              "animators": {
                "root": {
                  "name": "root",
                  "keyframes": [
                    {
                      "channel": "position",
                      "time": 0.0,
                      "data_points": [{"x": 4, "y": 5, "z": 6}]
                    },
                    {
                      "channel": "rotation",
                      "time": 0.0,
                      "data_points": [{"x": 10, "y": 20, "z": 30}]
                    }
                  ]
                }
              }
            }
          ]
        }
        """);

    BoneTimeline timeline =
        importer().importFile(modelFile).animation("legacy").orElseThrow().timelines().get("root");

    assertEquals(-4, timeline.position().sample(0).x(), EPSILON);
    assertEquals(5, timeline.position().sample(0).y(), EPSILON);
    assertEquals(6, timeline.position().sample(0).z(), EPSILON);
    assertEquals(-10, timeline.rotation().sample(0).x(), EPSILON);
    assertEquals(-20, timeline.rotation().sample(0).y(), EPSILON);
    assertEquals(30, timeline.rotation().sample(0).z(), EPSILON);
  }

  @Test
  void preThreeTwoOutlinerRotationIsMigrated() throws Exception {
    Path modelFile = tempDirectory.resolve("legacy-outliner.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "meta": {"format_version": "3.1"},
          "elements": [],
          "outliner": [
            {
              "name": "arm",
              "uuid": "arm-bone",
              "origin": [0, 0, 0],
              "rotation": [10, 20, 30],
              "children": []
            }
          ],
          "animations": []
        }
        """);

    ModelBlueprint model = importer().importFile(modelFile);

    assertEquals(-30, model.bone("arm").orElseThrow().rotationDegrees().z(), EPSILON);
  }

  @Test
  void blockbenchFiveSeparateGroupsRestoreAnimatedBonePivots() throws Exception {
    Path modelFile = tempDirectory.resolve("blockbench-five-groups.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "meta": {"format_version": "5.0", "model_format": "free"},
          "elements": [
            {
              "uuid": "torso-cube",
              "name": "redstone_golem_torso",
              "from": [-20, 28, -10],
              "to": [20, 60, 10],
              "origin": [0, 31, -9],
              "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": 0}
              }
            }
          ],
          "groups": [
            {
              "uuid": "hip-bone",
              "name": "redstone_golem_hip",
              "origin": [0, 24, 0],
              "rotation": [0, 0, 0],
              "visibility": true,
              "children": []
            },
            {
              "uuid": "torso-bone",
              "name": "redstone_golem_torso",
              "origin": [0, 30, 0],
              "rotation": [0, 0, 0],
              "visibility": true,
              "children": []
            }
          ],
          "outliner": [
            {
              "uuid": "hip-bone",
              "isOpen": true,
              "children": [
                {
                  "uuid": "torso-bone",
                  "isOpen": true,
                  "children": ["torso-cube"]
                }
              ]
            }
          ],
          "animations": [
            {
              "name": "redstone_golem_idle_animation_1",
              "length": 1.0,
              "loop": "loop",
              "animators": {
                "torso-bone": {
                  "name": "redstone_golem_torso",
                  "type": "bone",
                  "keyframes": [
                    {
                      "channel": "rotation",
                      "time": 0.0,
                      "interpolation": "linear",
                      "data_points": [{"x": "10", "y": "20", "z": "30"}]
                    }
                  ]
                }
              }
            }
          ]
        }
        """);

    ModelBlueprint model = importer().importFile(modelFile);
    Bone hip = model.bone("redstone_golem_hip").orElseThrow();
    Bone torso = model.bone("redstone_golem_torso").orElseThrow();
    BoneTimeline torsoTimeline =
        model
            .animation("redstone_golem_idle_animation_1")
            .orElseThrow()
            .timelines()
            .get("redstone_golem_torso");

    assertEquals(24, hip.origin().y(), EPSILON);
    assertEquals(30, torso.origin().y(), EPSILON);
    assertEquals(6.0 / 16.0, torso.localPosition().y(), EPSILON);
    assertEquals("torso-cube", torso.cubeIds().getFirst());
    assertEquals("redstone_golem_torso", model.boneByUuid("torso-bone").orElseThrow().name());
    assertEquals(10, torsoTimeline.rotation().sample(0).x(), EPSILON);
    assertEquals(20, torsoTimeline.rotation().sample(0).y(), EPSILON);
    assertEquals(30, torsoTimeline.rotation().sample(0).z(), EPSILON);
    assertEquals(
        "redstone_golem_idle_animation_1", model.animation("idle").orElseThrow().name());
  }

  private static BlockbenchImporter importer() {
    return new BlockbenchImporter(Logger.getAnonymousLogger(), "osmium");
  }
}
