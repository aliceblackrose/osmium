package osmium;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import osmium.command.ModelCommand;
import osmium.model.ModelManager;
import osmium.pack.ResourcePackGenerator;
import osmium.render.RuntimeModelListener;
import osmium.render.RuntimeModelRegistry;

public final class OsmiumPlugin extends JavaPlugin {
  private PluginSettings settings;
  private ModelManager modelManager;
  private RuntimeModelRegistry runtimeRegistry;
  private NamespacedKey runtimeModelKey;
  private BukkitTask tickTask;

  @Override
  public void onEnable() {
    saveDefaultConfig();

    settings = PluginSettings.load(this);
    modelManager = new ModelManager(getLogger());
    runtimeModelKey = new NamespacedKey(this, "runtime_model_id");
    runtimeRegistry = new RuntimeModelRegistry(this, runtimeModelKey);

    createDataFolders();
    reloadEverything(false);
    registerCommands();
    registerListeners();
    startTickTask();
  }

  @Override
  public void onDisable() {
    stopTickTask();

    if (runtimeRegistry != null) {
      runtimeRegistry.removeAll();
    }
  }

  public void reloadEverything(boolean removeActiveModels) {
    reloadConfig();
    settings = PluginSettings.load(this);
    createDataFolders();

    try {
      modelManager.reload(settings);

      if (settings.autoGeneratePack()) {
        generatePack();
      }

      if (removeActiveModels && runtimeRegistry != null) {
        runtimeRegistry.removeAll();
      }
    } catch (IOException exception) {
      getLogger().log(Level.SEVERE, "Reload failed; keeping active runtime models.", exception);
    }
  }

  public Path generatePack() throws IOException {
    return new ResourcePackGenerator(
            getLogger(),
            settings.resourcePackFolder(),
            settings.namespace(),
            settings.customModelDataStart(),
            settings.baseItem(),
            settings.packFormat())
        .generate(modelManager.models());
  }

  public PluginSettings settings() {
    return settings;
  }

  public ModelManager modelManager() {
    return modelManager;
  }

  public RuntimeModelRegistry runtimeRegistry() {
    return runtimeRegistry;
  }

  public NamespacedKey runtimeModelKey() {
    return runtimeModelKey;
  }

  private void registerCommands() {
    PluginCommand command = getCommand("om");
    if (command == null) {
      getLogger().warning("Command 'om' is not registered in plugin.yml.");
      return;
    }

    ModelCommand modelCommand = new ModelCommand(this);
    command.setExecutor(modelCommand);
    command.setTabCompleter(modelCommand);
  }

  private void registerListeners() {
    getServer().getPluginManager().registerEvents(new RuntimeModelListener(this), this);
  }

  private void startTickTask() {
    tickTask = getServer().getScheduler().runTaskTimer(this, runtimeRegistry::tick, 1L, 1L);
  }

  private void stopTickTask() {
    if (tickTask == null) {
      return;
    }

    tickTask.cancel();
    tickTask = null;
  }

  private void createDataFolders() {
    try {
      Files.createDirectories(settings.blueprintsFolder());
      Files.createDirectories(settings.modelsFolder());
      Files.createDirectories(settings.resourcePackFolder());
    } catch (IOException exception) {
      getLogger().log(Level.WARNING, "Folder creation failed.", exception);
    }
  }
}
