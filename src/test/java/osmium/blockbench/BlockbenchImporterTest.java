package osmium.blockbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
}
