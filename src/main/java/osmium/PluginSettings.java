package osmium;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import osmium.animation.ProceduralBonePreset;
import osmium.util.Names;

public record PluginSettings(
    String namespace,
    Material baseItem,
    int customModelDataStart,
    int packFormat,
    Path blueprintsFolder,
    Path modelsFolder,
    Path resourcePackFolder,
    boolean autoGeneratePack,
    int interpolationDuration,
    int teleportDuration,
    float viewRange,
    float shadowRadius,
    float shadowStrength,
    int brightnessBlock,
    int brightnessSky,
    double renderScale,
    boolean groundAlign,
    boolean proceduralAnimationEnabled,
    double proceduralAnimationStrength,
    double proceduralAnimationSpeed,
    boolean proceduralIdleBreathing,
    boolean proceduralWalkCycle,
    boolean proceduralHeadTracking,
    boolean proceduralEyeTracking,
    boolean proceduralBlinking,
    boolean proceduralTurnLean,
    boolean proceduralMovementLean,
    boolean proceduralSpringBones,
    boolean proceduralIdleVariants,
    boolean proceduralHitFlinch,
    double proceduralHeadTrackingRange,
    double proceduralHeadTrackingMaxYaw,
    double proceduralHeadTrackingMaxPitch,
    double proceduralBlinkInterval,
    double proceduralBlinkDuration,
    double proceduralTurnLeanStrength,
    double proceduralTurnLeanMaxDegrees,
    double proceduralMovementLeanStrength,
    double proceduralMovementLeanMaxDegrees,
    double proceduralSpringStiffness,
    double proceduralSpringDamping,
    double proceduralHitFlinchStrength,
    double proceduralHitFlinchDuration,
    List<ProceduralBonePreset> proceduralBonePresets) {
  private static final String DEFAULT_NAMESPACE = "osmium";
  private static final String DEFAULT_BASE_ITEM = "PAPER";
  private static final String DEFAULT_BLUEPRINTS_FOLDER = "blueprints";
  private static final String DEFAULT_MODELS_FOLDER = "models";
  private static final String DEFAULT_RESOURCE_PACK_FOLDER = "resource_pack";

  private static final int DEFAULT_CUSTOM_MODEL_DATA_START = 100_000;
  private static final int DEFAULT_PACK_FORMAT = 84;
  private static final int DEFAULT_INTERPOLATION_DURATION = 1;
  private static final int DEFAULT_TELEPORT_DURATION = 1;
  private static final int DEFAULT_BRIGHTNESS = 15;

  private static final double DEFAULT_RENDER_SCALE = 1.0;
  private static final double DEFAULT_SHADOW_RADIUS = 0.0;
  private static final double DEFAULT_SHADOW_STRENGTH = 0.0;
  private static final double DEFAULT_VIEW_RANGE = 64.0;
  private static final double DEFAULT_PROCEDURAL_ANIMATION_STRENGTH = 1.0;
  private static final double DEFAULT_PROCEDURAL_ANIMATION_SPEED = 1.0;
  private static final double DEFAULT_PROCEDURAL_HEAD_TRACKING_RANGE = 12.0;
  private static final double DEFAULT_PROCEDURAL_HEAD_TRACKING_MAX_YAW = 35.0;
  private static final double DEFAULT_PROCEDURAL_HEAD_TRACKING_MAX_PITCH = 25.0;
  private static final double DEFAULT_PROCEDURAL_BLINK_INTERVAL = 4.25;
  private static final double DEFAULT_PROCEDURAL_BLINK_DURATION = 0.16;
  private static final double DEFAULT_PROCEDURAL_TURN_LEAN_STRENGTH = 0.65;
  private static final double DEFAULT_PROCEDURAL_TURN_LEAN_MAX_DEGREES = 9.0;
  private static final double DEFAULT_PROCEDURAL_MOVEMENT_LEAN_STRENGTH = 0.75;
  private static final double DEFAULT_PROCEDURAL_MOVEMENT_LEAN_MAX_DEGREES = 8.0;
  private static final double DEFAULT_PROCEDURAL_SPRING_STIFFNESS = 0.22;
  private static final double DEFAULT_PROCEDURAL_SPRING_DAMPING = 0.72;
  private static final double DEFAULT_PROCEDURAL_HIT_FLINCH_STRENGTH = 1.0;
  private static final double DEFAULT_PROCEDURAL_HIT_FLINCH_DURATION = 0.34;
  private static final double MIN_RENDER_SCALE = 0.0001;
  private static final double MIN_BLINK_INTERVAL = 0.4;
  private static final double MIN_BLINK_DURATION = 0.03;

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
        configuredPath(dataFolder, config, "models-folder", DEFAULT_MODELS_FOLDER),
        configuredPath(dataFolder, config, "resource-pack-folder", DEFAULT_RESOURCE_PACK_FOLDER),
        config.getBoolean("auto-generate-pack-on-reload", true),
        nonNegativeInt(config, "render.interpolation-duration", DEFAULT_INTERPOLATION_DURATION),
        nonNegativeInt(config, "render.teleport-duration", DEFAULT_TELEPORT_DURATION),
        positiveFloat(config),
        nonNegativeFloat(config, "render.shadow-radius", DEFAULT_SHADOW_RADIUS),
        nonNegativeFloat(config, "render.shadow-strength", DEFAULT_SHADOW_STRENGTH),
        brightness(config, "render.brightness-block"),
        brightness(config, "render.brightness-sky"),
        renderScale(config),
        config.getBoolean("render.ground-align", true),
        config.getBoolean("render.procedural-animation.enabled", true),
        positiveDouble(
            config, "render.procedural-animation.strength", DEFAULT_PROCEDURAL_ANIMATION_STRENGTH),
        positiveDouble(
            config, "render.procedural-animation.speed", DEFAULT_PROCEDURAL_ANIMATION_SPEED),
        config.getBoolean("render.procedural-animation.idle-breathing", true),
        config.getBoolean("render.procedural-animation.walk-cycle", true),
        config.getBoolean("render.procedural-animation.head-tracking", true),
        config.getBoolean("render.procedural-animation.eye-tracking", true),
        config.getBoolean("render.procedural-animation.blinking", true),
        config.getBoolean("render.procedural-animation.turn-lean", true),
        config.getBoolean("render.procedural-animation.movement-lean", true),
        config.getBoolean("render.procedural-animation.spring-bones", true),
        config.getBoolean("render.procedural-animation.idle-variants", true),
        config.getBoolean("render.procedural-animation.hit-flinch", true),
        positiveDouble(
            config,
            "render.procedural-animation.head-tracking-range",
            DEFAULT_PROCEDURAL_HEAD_TRACKING_RANGE),
        positiveDouble(
            config,
            "render.procedural-animation.head-tracking-max-yaw",
            DEFAULT_PROCEDURAL_HEAD_TRACKING_MAX_YAW),
        positiveDouble(
            config,
            "render.procedural-animation.head-tracking-max-pitch",
            DEFAULT_PROCEDURAL_HEAD_TRACKING_MAX_PITCH),
        lowerBoundedDouble(
            config,
            "render.procedural-animation.blink-interval",
            DEFAULT_PROCEDURAL_BLINK_INTERVAL,
            MIN_BLINK_INTERVAL),
        lowerBoundedDouble(
            config,
            "render.procedural-animation.blink-duration",
            DEFAULT_PROCEDURAL_BLINK_DURATION,
            MIN_BLINK_DURATION),
        positiveDouble(
            config,
            "render.procedural-animation.turn-lean-strength",
            DEFAULT_PROCEDURAL_TURN_LEAN_STRENGTH),
        positiveDouble(
            config,
            "render.procedural-animation.turn-lean-max-degrees",
            DEFAULT_PROCEDURAL_TURN_LEAN_MAX_DEGREES),
        positiveDouble(
            config,
            "render.procedural-animation.movement-lean-strength",
            DEFAULT_PROCEDURAL_MOVEMENT_LEAN_STRENGTH),
        positiveDouble(
            config,
            "render.procedural-animation.movement-lean-max-degrees",
            DEFAULT_PROCEDURAL_MOVEMENT_LEAN_MAX_DEGREES),
        positiveDouble(
            config,
            "render.procedural-animation.spring-stiffness",
            DEFAULT_PROCEDURAL_SPRING_STIFFNESS),
        clampedDouble(
            config,
            "render.procedural-animation.spring-damping",
            DEFAULT_PROCEDURAL_SPRING_DAMPING,
            0.0,
            0.98),
        positiveDouble(
            config,
            "render.procedural-animation.hit-flinch-strength",
            DEFAULT_PROCEDURAL_HIT_FLINCH_STRENGTH),
        lowerBoundedDouble(
            config,
            "render.procedural-animation.hit-flinch-duration",
            DEFAULT_PROCEDURAL_HIT_FLINCH_DURATION,
            0.05),
        proceduralBonePresets(config));
  }

  public ProceduralBonePreset proceduralBonePreset(String boneName) {
    for (ProceduralBonePreset preset : proceduralBonePresets) {
      if (preset.matches(boneName)) {
        return preset;
      }
    }

    return ProceduralBonePreset.DEFAULT;
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

  private static int brightness(FileConfiguration config, String key) {
    return clamp(config.getInt(key, DEFAULT_BRIGHTNESS));
  }

  private static double renderScale(FileConfiguration config) {
    return Math.max(MIN_RENDER_SCALE, config.getDouble("render.scale", DEFAULT_RENDER_SCALE));
  }

  private static double positiveDouble(ConfigurationSection config, String key, double fallback) {
    return Math.max(0.0, config.getDouble(key, fallback));
  }

  private static double lowerBoundedDouble(
      ConfigurationSection config, String key, double fallback, double lowerBound) {
    return Math.max(lowerBound, config.getDouble(key, fallback));
  }

  private static double clampedDouble(
      ConfigurationSection config, String key, double fallback, double min, double max) {
    return Math.max(min, Math.min(max, config.getDouble(key, fallback)));
  }

  private static int clamp(int value) {
    return Math.clamp(value, PluginSettings.MIN_BRIGHTNESS, PluginSettings.MAX_BRIGHTNESS);
  }

  private static List<ProceduralBonePreset> proceduralBonePresets(FileConfiguration config) {
    ConfigurationSection section =
        config.getConfigurationSection("render.procedural-animation.bone-presets");
    if (section == null) {
      return List.of();
    }

    List<ProceduralBonePreset> presets = new ArrayList<>();
    for (String id : section.getKeys(false)) {
      ConfigurationSection preset = section.getConfigurationSection(id);
      if (preset == null) {
        continue;
      }

      presets.add(
          new ProceduralBonePreset(
              id,
              preset.getString("match", id),
              preset.getBoolean("enabled", true),
              positiveDouble(preset, "idle-strength", 1.0),
              positiveDouble(preset, "walk-strength", 1.0),
              positiveDouble(preset, "tracking-strength", 1.0),
              positiveDouble(preset, "blink-strength", 1.0),
              positiveDouble(preset, "spring-strength", 1.0),
              positiveDouble(preset, "turn-lean-strength", 1.0),
              positiveDouble(preset, "movement-lean-strength", 1.0),
              positiveDouble(preset, "flinch-strength", 1.0),
              preset.getDouble("spring-stiffness", -1.0),
              preset.getDouble("spring-damping", -1.0)));
    }

    return List.copyOf(presets);
  }
}
