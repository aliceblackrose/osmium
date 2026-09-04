package osmium;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import osmium.animation.AnimationCompiler;
import osmium.command.ModelCommand;
import osmium.model.ModelManager;
import osmium.pack.ResourcePackCompiler;
import osmium.render.RuntimeModelListener;
import osmium.render.RuntimeModelRegistry;

public final class OsmiumPlugin extends JavaPlugin {
  private PluginSettings settings;
  private ModelManager modelManager;
  private RuntimeModelRegistry runtimeRegistry;
  private NamespacedKey runtimeModelKey;
  private BukkitTask tickTask;
  private ScheduledExecutorService animationExecutor;
  private ScheduledFuture<?> animationTask;

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
    startAnimationRenderer();
  }

  @Override
  public void onDisable() {
    stopAnimationRenderer();
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
    return new ResourcePackCompiler(
            getLogger(),
            settings.resourcePackFolder(),
            settings.namespace(),
            settings.customModelDataStart(),
            settings.baseItem(),
            settings.packFormat())
        .compile(modelManager.models());
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

  private void startAnimationRenderer() {
    animationExecutor =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "Osmium Animation Renderer");
              thread.setDaemon(true);
              return thread;
            });

    animationTask =
        animationExecutor.scheduleAtFixedRate(
            this::runAnimationTick,
            AnimationCompiler.TRANSPORT_STEP_MILLIS,
            AnimationCompiler.TRANSPORT_STEP_MILLIS,
            TimeUnit.MILLISECONDS);
  }

  private void runAnimationTick() {
    try {
      runtimeRegistry.animationTick();
    } catch (Throwable throwable) {
      getLogger().log(Level.SEVERE, "Packet animation renderer tick failed.", throwable);
    }
  }

  private void stopAnimationRenderer() {
    if (animationTask != null) {
      animationTask.cancel(true);
      animationTask = null;
    }

    if (animationExecutor == null) {
      return;
    }

    animationExecutor.shutdownNow();
    animationExecutor = null;
  }

  private void createDataFolders() {
    try {
      Files.createDirectories(settings.blueprintsFolder());
      Files.createDirectories(settings.resourcePackFolder());
    } catch (IOException exception) {
      getLogger().log(Level.WARNING, "Folder creation failed.", exception);
    }
  }
}
