package osmium.pack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResourcePackCompilerTest {
  @TempDir Path tempDirectory;

  @Test
  void compileLeavesOnlyPackZipInOutputFolder() throws Exception {
    Path outputFolder = tempDirectory.resolve("resource-pack");
    Files.createDirectories(outputFolder.resolve("assets/osmium"));
    Files.writeString(outputFolder.resolve("generated_at.txt"), "stale");
    Files.writeString(outputFolder.resolve("modelengine_like_manifest.json"), "stale");
    Files.writeString(outputFolder.resolve("pack.mcmeta"), "stale");
    Files.writeString(outputFolder.resolve("pack-deadbeef.zip"), "stale");

    ResourcePackCompiler compiler =
        new ResourcePackCompiler(
            Logger.getAnonymousLogger(), outputFolder, "osmium", 100_000, Material.PAPER, 84);

    Path packPath = compiler.compile(List.of());

    assertEquals(outputFolder.resolve("pack.zip"), packPath);
    try (Stream<Path> paths = Files.list(outputFolder)) {
      assertEquals(List.of(packPath), paths.toList());
    }

    try (ZipFile zipFile = new ZipFile(packPath.toFile())) {
      assertNotNull(zipFile.getEntry("pack.mcmeta"));
    }
  }
}
