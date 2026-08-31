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
  void zeroThicknessEyePlaneOnlyExpandsItsFlatAxis() throws Exception {
    Bone eye = new Bone("eye", "eye", "eye-bone", Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, true);
    Cube plane =
        new Cube(
            "eye-plane",
            "eye",
            new Vec3(21, 27, -4.025),
            new Vec3(23, 28, -4.025),
            new Vec3(22, 26, -3.025),
            Vec3.ZERO,
            0,
            Map.of("north", new Face("north", 0, 3, 2, 4, 0, "0")));
    eye.addCube(plane.uuid());

    TextureAsset texture =
        new TextureAsset(
            "0",
            "bedwars_npc.png",
            "0",
            "osmium:item/bedwars_npc/bedwars_npc",
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
            "bedwars_npc",
            Path.of("bedwars_npc.bbmodel"),
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
            .resolve("bedwars_npc")
            .resolve("eye_eye_0.json");
    JsonObject element =
        JsonParser.parseString(Files.readString(generatedModel))
            .getAsJsonObject()
            .getAsJsonArray("elements")
            .get(0)
            .getAsJsonObject();

    double fromX = element.getAsJsonArray("from").get(0).getAsDouble();
    double fromY = element.getAsJsonArray("from").get(1).getAsDouble();
    double fromZ = element.getAsJsonArray("from").get(2).getAsDouble();
    double toX = element.getAsJsonArray("to").get(0).getAsDouble();
    double toY = element.getAsJsonArray("to").get(1).getAsDouble();
    double toZ = element.getAsJsonArray("to").get(2).getAsDouble();

    assertEquals(2.0, toX - fromX, 1.0E-9);
    assertEquals(1.0, toY - fromY, 1.0E-9);
    assertEquals(1.0 / 64.0, toZ - fromZ, 2.0E-6);
    assertEquals(8.0, (fromX + toX) * 0.5, 1.0E-9);
    assertEquals(8.0, (fromY + toY) * 0.5, 1.0E-9);
    assertEquals(8.0, (fromZ + toZ) * 0.5, 1.0E-9);

    JsonObject north = element.getAsJsonObject("faces").getAsJsonObject("north");
    assertEquals(0.0, north.getAsJsonArray("uv").get(0).getAsDouble(), 1.0E-9);
    assertEquals(3.0 / 128.0 * 16.0, north.getAsJsonArray("uv").get(1).getAsDouble(), 1.0E-9);
  }
}
