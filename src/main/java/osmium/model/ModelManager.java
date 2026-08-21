package osmium.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import osmium.PluginSettings;
import osmium.blockbench.BlockbenchImporter;

public final class ModelManager {
  private final Logger logger;
  private volatile Map<String, ModelBlueprint> models = Map.of();

  public ModelManager(Logger logger) {
    this.logger = logger;
  }

  public void reload(PluginSettings settings) throws IOException {
    Files.createDirectories(settings.blueprintsFolder());
    Files.createDirectories(settings.modelsFolder());

    Map<String, ModelBlueprint> nextModels = new LinkedHashMap<>();
    List<String> failures = new ArrayList<>();
    BlockbenchImporter importer = new BlockbenchImporter(logger, settings.namespace());

    load(importer, settings.blueprintsFolder(), nextModels, failures);
    if (!settings.modelsFolder().equals(settings.blueprintsFolder())) {
      load(importer, settings.modelsFolder(), nextModels, failures);
    }

    if (!failures.isEmpty()) {
      throw new IOException(
          "Model reload aborted; " + failures.size() + " model(s) failed: " + String.join(", ", failures));
    }

    models = Collections.unmodifiableMap(new LinkedHashMap<>(nextModels));
  }

  private void load(
      BlockbenchImporter importer,
      Path folder,
      Map<String, ModelBlueprint> target,
      List<String> failures)
      throws IOException {
    if (!Files.isDirectory(folder)) {
      return;
    }

    try (Stream<Path> paths = Files.walk(folder)) {
      for (Path path :
          paths
              .filter(Files::isRegularFile)
              .filter(candidate -> candidate.getFileName().toString().endsWith(".bbmodel"))
              .sorted()
              .toList()) {
        try {
          ModelBlueprint model = importer.importFile(path);
          ModelBlueprint existing = target.putIfAbsent(model.id(), model);
          if (existing != null) {
            String message =
                "Duplicate model id '"
                    + model.id()
                    + "' from "
                    + path
                    + " conflicts with "
                    + existing.source();
            logger.warning(message);
            failures.add(path.toString());
            continue;
          }

          logger.info(
              "Loaded "
                  + model.id()
                  + " bones="
                  + model.bones().size()
                  + " cubes="
                  + model.cubes().size()
                  + " hitboxes="
                  + model.hitboxes().size()
                  + " animations="
                  + model.animations().size());
        } catch (Exception exception) {
          logger.log(Level.WARNING, "Failed to load " + path, exception);
          failures.add(path.toString());
        }
      }
    }
  }

  public Optional<ModelBlueprint> model(String id) {
    return Optional.ofNullable(models.get(id));
  }

  public Collection<ModelBlueprint> models() {
    return models.values();
  }

  public boolean empty() {
    return models.isEmpty();
  }
}
