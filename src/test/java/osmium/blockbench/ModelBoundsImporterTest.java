package osmium.blockbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import osmium.model.ModelBlueprint;

final class ModelBoundsImporterTest {
  private static final double EPSILON = 1.0E-9;

  @TempDir Path tempDirectory;

  @Test
  void invisibleHitboxGeometryDoesNotAffectGroundAlignmentBounds() throws Exception {
    Path modelFile = tempDirectory.resolve("bounds.bbmodel");
    Files.writeString(
        modelFile,
        """
        {
          "elements": [
            {
              "uuid": "body-cube",
              "name": "body",
              "from": [-8, 0, -8],
              "to": [8, 16, 8],
              "origin": [0, 0, 0],
              "rotation": [0, 0, 0],
              "faces": {
                "north": {"uv": [0, 0, 16, 16], "texture": 0}
              }
            },
            {
              "uuid": "hitbox-cube",
              "name": "collision",
              "visibility": false,
              "from": [-16, -32, -16],
              "to": [16, 32, 16],
              "origin": [0, 0, 0],
              "rotation": [0, 0, 0],
              "faces": {}
            }
          ],
          "outliner": [
            {
              "name": "body",
              "uuid": "body-bone",
              "origin": [0, 0, 0],
              "rotation": [0, 0, 0],
              "children": ["body-cube"]
            },
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

    ModelBlueprint model =
        new BlockbenchImporter(Logger.getAnonymousLogger(), "osmium").importFile(modelFile);

    assertEquals(0.0, model.minY(), EPSILON);
    assertEquals(1.0, model.modelSizeBlocks().y(), EPSILON);
  }
}
