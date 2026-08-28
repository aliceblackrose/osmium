package osmium.pack;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.Face;
import osmium.model.ModelBlueprint;
import osmium.model.TextureAsset;

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

  @Test
  void zeroThicknessEyePlaneRemainsFlatAndUsesTextureUvResolution() throws Exception {
    Bone eye = new Bone("eye", "eye", "eye-bone", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    Cube plane =
        new Cube(
            "eye-plane",
            "eye",
            new Vec3(-8.025, 18.2, -9.5),
            new Vec3(-8.025, 19.8, -7.9),
            new Vec3(-4, 19, -6.7),
            new Vec3(0, -15, 0),
            0,
            Map.of(
                "east", new Face("east", 48.1, 61.1, 48.9, 61.9, 0, "0"),
                "south", new Face("south", 0.075, 62.1, -0.1, 63.6, 0, "0")));
    eye.addCube(plane.uuid());

    TextureAsset texture =
        new TextureAsset(
            "0",
            "monstruos_bug.png",
            "0",
            "osmium:item/armored_grub/monstruos_bug",
            null,
            null,
            128,
            128,
            128,
            128,
            1,
            false,
            "");
    ModelBlueprint model =
        new ModelBlueprint(
            "armored_grub",
            Path.of("armored_grub.bbmodel"),
            eye,
            Map.of(eye.name(), eye),
            Map.of(eye.uuid(), eye),
            Map.of(plane.uuid(), plane),
            Map.of("0", texture),
            Map.of());

    ResourcePackGenerator generator =
        new ResourcePackGenerator(
            Logger.getAnonymousLogger(), tempDirectory, "osmium", 100_000, Material.PAPER, 84);
    generator.generate(List.of(model));

    Path generatedModel =
        tempDirectory
            .resolve("assets")
            .resolve("osmium")
            .resolve("models")
            .resolve("item")
            .resolve("armored_grub")
            .resolve("eye_eye_0.json");
    JsonObject element =
        JsonParser.parseString(Files.readString(generatedModel))
            .getAsJsonObject()
            .getAsJsonArray("elements")
            .get(0)
            .getAsJsonObject();

    assertEquals(8.0, element.getAsJsonArray("from").get(0).getAsDouble(), 1.0E-9);
    assertEquals(8.0, element.getAsJsonArray("to").get(0).getAsDouble(), 1.0E-9);

    JsonObject east = element.getAsJsonObject("faces").getAsJsonObject("east");
    assertEquals(48.1 / 128.0 * 16.0, east.getAsJsonArray("uv").get(0).getAsDouble(), 1.0E-9);
    assertEquals(61.1 / 128.0 * 16.0, east.getAsJsonArray("uv").get(1).getAsDouble(), 1.0E-9);
  }
}
