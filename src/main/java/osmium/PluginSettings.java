package osmium;

import java.nio.file.Path;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import osmium.util.Names;

public record PluginSettings(
    String namespace,
    Material baseItem,
    int customModelDataStart,
    int packFormat,
    Path blueprintsFolder,
    Path resourcePackFolder,
    boolean autoGeneratePack,
    int interpolationDuration,
    int teleportDuration,
    float viewRange,
    boolean shadowsEnabled,
    float shadowRadius,
    float shadowStrength,
    boolean brightnessOverride,
    int brightnessBlock,
    int brightnessSky,
    double renderScale,
    boolean groundAlign) {
  private static final String DEFAULT_NAMESPACE = "osmium";
  private static final String DEFAULT_BASE_ITEM = "PAPER";
  private static final String DEFAULT_BLUEPRINTS_FOLDER = "blueprints";
  private static final String DEFAULT_RESOURCE_PACK_FOLDER = "resource_pack";

  private static final int DEFAULT_CUSTOM_MODEL_DATA_START = 100_000;
  private static final int DEFAULT_PACK_FORMAT = 84;
  private static final int DEFAULT_INTERPOLATION_DURATION = 3;
  private static final int DEFAULT_TELEPORT_DURATION = 1;
  private static final int DEFAULT_BRIGHTNESS = 15;

  private static final double DEFAULT_RENDER_SCALE = 1.0;
  private static final double DEFAULT_SHADOW_RADIUS = 0.0;
  private static final double DEFAULT_SHADOW_STRENGTH = 0.75;
  private static final double DEFAULT_VIEW_RANGE = 64.0;
  private static final double MIN_RENDER_SCALE = 0.0001;

  private static final int MIN_BRIGHTNESS = 0;
  private static final int MAX_BRIGHTNESS = 15;

  public static PluginSettings load(OsmiumPlugin plugin) {
    FileConfiguration config = plugin.getConfig();
    Path dataFolder = plugin.getDataFolder().toPath();

    return new PluginSettings(
        namespace(config),
        baseItem(config),
        customModelDataStart(config),
        packFormat(config),
        configuredPath(dataFolder, config, "blueprints-folder", DEFAULT_BLUEPRINTS_FOLDER),
        configuredPath(dataFolder, config, "resource-pack-folder", DEFAULT_RESOURCE_PACK_FOLDER),
        config.getBoolean("auto-generate-pack-on-reload", true),
        nonNegativeInt(config, "render.interpolation-duration", DEFAULT_INTERPOLATION_DURATION),
        nonNegativeInt(config, "render.teleport-duration", DEFAULT_TELEPORT_DURATION),
        positiveFloat(config),
        config.getBoolean("render.shadow-enabled", true),
        nonNegativeFloat(config, "render.shadow-radius", DEFAULT_SHADOW_RADIUS),
        shadowStrength(config),
        config.getBoolean("render.brightness-override", false),
        brightness(config, "render.brightness-block"),
        brightness(config, "render.brightness-sky"),
        renderScale(config),
        config.getBoolean("render.ground-align", true));
  }

  private static String namespace(FileConfiguration config) {
    return Names.namespace(config.getString("namespace", DEFAULT_NAMESPACE));
  }

  private static Material baseItem(FileConfiguration config) {
    Material material = Material.matchMaterial(config.getString("base-item", DEFAULT_BASE_ITEM));

    if (material == null || !material.isItem()) {
      return Material.PAPER;
    }

    return material;
  }

  private static int customModelDataStart(FileConfiguration config) {
    return Math.max(1, config.getInt("custom-model-data-start", DEFAULT_CUSTOM_MODEL_DATA_START));
  }

  private static int packFormat(FileConfiguration config) {
    return Math.max(1, config.getInt("pack-format", DEFAULT_PACK_FORMAT));
  }

  private static Path configuredPath(
      Path dataFolder, FileConfiguration config, String key, String fallback) {
    return dataFolder.resolve(config.getString(key, fallback));
  }

  private static int nonNegativeInt(FileConfiguration config, String key, int fallback) {
    return Math.max(0, config.getInt(key, fallback));
  }

  private static float positiveFloat(FileConfiguration config) {
    return (float)
        Math.max(1.0, config.getDouble("render.view-range", PluginSettings.DEFAULT_VIEW_RANGE));
  }

  private static float nonNegativeFloat(FileConfiguration config, String key, double fallback) {
    return (float) Math.max(0.0, config.getDouble(key, fallback));
  }

  private static float shadowStrength(FileConfiguration config) {
    double configured = config.getDouble("render.shadow-strength", DEFAULT_SHADOW_STRENGTH);
    if (!config.contains("render.shadow-enabled") && configured == 0.0) {
      configured = DEFAULT_SHADOW_STRENGTH;
    }
    return (float) Math.clamp(configured, 0.0, 1.0);
  }

  private static int brightness(FileConfiguration config, String key) {
    return clamp(config.getInt(key, DEFAULT_BRIGHTNESS));
  }

  private static double renderScale(FileConfiguration config) {
    return Math.max(MIN_RENDER_SCALE, config.getDouble("render.scale", DEFAULT_RENDER_SCALE));
  }

  private static int clamp(int value) {
    return Math.clamp(value, PluginSettings.MIN_BRIGHTNESS, PluginSettings.MAX_BRIGHTNESS);
  }
}
