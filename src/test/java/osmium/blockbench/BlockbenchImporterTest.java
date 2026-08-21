package osmium.blockbench;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import osmium.model.ModelBlueprint;

final class BlockbenchImporterTest {
  @TempDir Path tempDirectory;

  @Test
  void fallbackTextureUsesConfiguredNamespace() throws Exception {
    Path modelFile = tempDirectory.resolve("dragon.bbmodel");
    Files.writeString(modelFile, "{\"elements\":[],\"outliner\":[],\"animations\":[]}");

    BlockbenchImporter importer = new BlockbenchImporter(Logger.getAnonymousLogger(), "My Pack");
    ModelBlueprint model = importer.importFile(modelFile);

    assertEquals("my_pack:item/dragon/fallback", model.texture("0").modelPath());
  }
}
