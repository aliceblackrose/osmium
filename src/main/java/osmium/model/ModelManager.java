package osmium.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;
import osmium.PluginSettings;
import osmium.blockbench.BlockbenchImporter;

public final class ModelManager {
  private final Logger logger;
  private final Map<String, ModelBlueprint> models = new LinkedHashMap<>();

  public ModelManager(Logger logger) {
    this.logger = logger;
  }

  public void reload(PluginSettings s) throws IOException {
    Files.createDirectories(s.blueprintsFolder());
    Files.createDirectories(s.modelsFolder());
    models.clear();
    BlockbenchImporter i = new BlockbenchImporter(logger);
    load(i, s.blueprintsFolder());
    if (!s.modelsFolder().equals(s.blueprintsFolder())) load(i, s.modelsFolder());
  }

  private void load(BlockbenchImporter i, Path folder) throws IOException {
    if (!Files.isDirectory(folder)) return;
    try (Stream<Path> st = Files.walk(folder)) {
      for (Path p :
          st.filter(Files::isRegularFile)
              .filter(x -> x.getFileName().toString().endsWith(".bbmodel"))
              .sorted()
              .toList()) {
        try {
          ModelBlueprint m = i.importFile(p);
          models.put(m.id(), m);
          logger.info(
              "Loaded "
                  + m.id()
                  + " bones="
                  + m.bones().size()
                  + " cubes="
                  + m.cubes().size()
                  + " hitboxes="
                  + m.hitboxes().size()
                  + " animations="
                  + m.animations().size());
        } catch (Exception e) {
          logger.warning("Failed to load " + p + ": " + e.getMessage());
          e.printStackTrace();
        }
      }
    }
  }

  public Optional<ModelBlueprint> model(String id) {
    return Optional.ofNullable(models.get(id));
  }

  public Collection<ModelBlueprint> models() {
    return Collections.unmodifiableCollection(models.values());
  }

  public boolean empty() {
    return models.isEmpty();
  }
}
