package osmium.pack;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Comparator;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.bukkit.Material;
import osmium.model.ModelBlueprint;

public final class ResourcePackCompiler {
  private static final String GENERATED_PACK_NAME = "pack.zip";
  private static final String PENDING_PACK_NAME = ".pack.zip.tmp";

  private final Logger logger;
  private final Path outputFolder;
  private final String namespace;
  private final int customModelDataStart;
  private final Material baseItem;
  private final int packFormat;

  public ResourcePackCompiler(
      Logger logger,
      Path outputFolder,
      String namespace,
      int customModelDataStart,
      Material baseItem,
      int packFormat) {
    this.logger = logger;
    this.outputFolder = outputFolder;
    this.namespace = namespace;
    this.customModelDataStart = customModelDataStart;
    this.baseItem = baseItem;
    this.packFormat = packFormat;
  }

  /** Compiles and publishes exactly one resource-pack artifact: {@code pack.zip}. */
  public Path compile(Collection<ModelBlueprint> models) throws IOException {
    Files.createDirectories(outputFolder);
    Path stagingFolder = Files.createTempDirectory("osmium-resource-pack-");

    try {
      Path stagedPack =
          new ResourcePackGenerator(
                  logger,
                  stagingFolder,
                  namespace,
                  customModelDataStart,
                  baseItem,
                  packFormat)
              .generate(models);

      Path packPath = outputFolder.resolve(GENERATED_PACK_NAME);
      Path pendingPack = outputFolder.resolve(PENDING_PACK_NAME);
      Files.copy(stagedPack, pendingPack, StandardCopyOption.REPLACE_EXISTING);
      publishPendingPack(pendingPack, packPath);
      deleteOutputExcept(packPath);
      return packPath;
    } finally {
      deleteRecursively(stagingFolder);
    }
  }

  private static void publishPendingPack(Path pendingPack, Path packPath) throws IOException {
    try {
      Files.move(
          pendingPack,
          packPath,
          StandardCopyOption.ATOMIC_MOVE,
          StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException exception) {
      Files.move(pendingPack, packPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private void deleteOutputExcept(Path packPath) throws IOException {
    try (Stream<Path> paths = Files.list(outputFolder)) {
      for (Path path : paths.filter(path -> !path.equals(packPath)).toList()) {
        deleteRecursively(path);
      }
    }
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(path)) {
      for (Path currentPath : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(currentPath);
      }
    }
  }
}
