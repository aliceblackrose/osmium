package osmium.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ResourcePackGeneratorTest {
  @TempDir Path tempDirectory;

  @Test
  void configuredPackFormatIsWrittenToMetadata() throws Exception {
    ResourcePackGenerator generator =
        new ResourcePackGenerator(
            Logger.getAnonymousLogger(), tempDirectory, "osmium", 100_000, Material.PAPER, 99);

    generator.generate(List.of());

    String metadata = Files.readString(tempDirectory.resolve("pack.mcmeta"));
    assertTrue(metadata.contains("\"min_format\": 99"));
    assertTrue(metadata.contains("\"max_format\": 99"));
  }

  @Test
  void identicalInputsProduceIdenticalPackBytesAndHash() throws Exception {
    ResourcePackGenerator generator =
        new ResourcePackGenerator(
            Logger.getAnonymousLogger(), tempDirectory, "osmium", 100_000, Material.PAPER, 84);

    Path firstVersionedPack = generator.generate(List.of());
    byte[] firstPackBytes = Files.readAllBytes(tempDirectory.resolve("pack.zip"));

    Path secondVersionedPack = generator.generate(List.of());
    byte[] secondPackBytes = Files.readAllBytes(tempDirectory.resolve("pack.zip"));

    assertEquals(firstVersionedPack.getFileName(), secondVersionedPack.getFileName());
    assertArrayEquals(firstPackBytes, secondPackBytes);
  }
}
