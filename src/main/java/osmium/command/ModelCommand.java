package osmium.command;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import osmium.OsmiumPlugin;
import osmium.model.Cube;
import osmium.model.Face;
import osmium.model.ModelBlueprint;
import osmium.model.TextureAsset;
import osmium.render.RuntimeModel;

public final class ModelCommand implements CommandExecutor, TabCompleter {
  private static final String ADMIN_PERMISSION = "osmium.admin";
  private static final String LEGACY_ADMIN_PERMISSION = "modelenginelike.admin";
  private static final String DEFAULT_ANIMATION = "idle";

  private static final double MODEL_UV_UNITS = 16.0;
  private static final double UV_ROUNDING_SCALE = 100_000.0;

  private static final List<String> SUBCOMMANDS =
      List.of("reload", "pack", "list", "spawn", "spawnmob", "play", "remove", "debug");

  private final OsmiumPlugin plugin;

  public ModelCommand(OsmiumPlugin plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean onCommand(
      CommandSender sender, Command command, String label, String[] arguments) {
    if (!hasAdminPermission(sender)) {
      sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
      return true;
    }

    if (arguments.length == 0) {
      sendHelp(sender, label);
      return true;
    }

    String subcommand = arguments[0].toLowerCase(Locale.ROOT);
    switch (subcommand) {
      case "reload" -> reload(sender);
      case "pack" -> pack(sender);
      case "list" -> list(sender);
      case "spawn" -> spawn(sender, arguments);
      case "spawnmob" -> spawnMob(sender, arguments);
      case "play" -> play(sender, arguments);
      case "remove" -> remove(sender, arguments);
      case "debug" -> debug(sender, arguments);
      default -> sendHelp(sender, label);
    }

    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] arguments) {
    if (!hasAdminPermission(sender)) {
      return List.of();
    }

    if (arguments.length == 1) {
      return matching(arguments[0], SUBCOMMANDS);
    }

    if (arguments.length == 2 && arguments[0].equalsIgnoreCase("spawnmob")) {
      return matching(arguments[1], livingEntityTypeIds());
    }

    if (arguments.length == 3 && arguments[0].equalsIgnoreCase("spawnmob")) {
      return matching(arguments[2], loadedModelIds());
    }

    if (arguments.length == 2 && isModelNameCommand(arguments[0])) {
      return matching(arguments[1], loadedModelIds());
    }

    if (arguments.length == 2 && isRuntimeIdCommand(arguments[0])) {
      return matching(arguments[1], runtimeModelIds());
    }

    return List.of();
  }

  private static Component text(String message, NamedTextColor color) {
    return Component.text(message, color);
  }

  private static double scaledUv(double value, int textureSize) {
    double safeTextureSize = Math.max(textureSize, 1);
    double scaled = value / safeTextureSize * MODEL_UV_UNITS;
    return Math.rint(scaled * UV_ROUNDING_SCALE) / UV_ROUNDING_SCALE;
  }

  private static Optional<Integer> parseInteger(String value) {
    try {
      return Optional.of(Integer.parseInt(value));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private static List<String> matching(String prefix, List<String> values) {
    String lowercasePrefix = prefix.toLowerCase(Locale.ROOT);

    return values.stream()
        .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lowercasePrefix))
        .sorted()
        .toList();
  }

  private static boolean isNumericKey(String value) {
    if (value.isEmpty()) {
      return false;
    }

    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (character < '0' || character > '9') {
        return false;
      }
    }

    return true;
  }

  private static boolean isModelNameCommand(String command) {
    return command.equalsIgnoreCase("spawn") || command.equalsIgnoreCase("debug");
  }

  private static Optional<EntityType> parseEntityType(String value) {
    String normalized = value.toUpperCase(Locale.ROOT).replace('-', '_');
    try {
      return Optional.of(EntityType.valueOf(normalized));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static boolean isLivingEntityType(EntityType entityType) {
    Class<? extends Entity> entityClass = entityType.getEntityClass();
    return entityType.isSpawnable()
        && entityClass != null
        && LivingEntity.class.isAssignableFrom(entityClass);
  }

  private static boolean isRuntimeIdCommand(String command) {
    return command.equalsIgnoreCase("play") || command.equalsIgnoreCase("remove");
  }

  private static boolean hasAdminPermission(CommandSender sender) {
    return sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(LEGACY_ADMIN_PERMISSION);
  }

  private void reload(CommandSender sender) {
    plugin.reloadEverything(true);
    sender.sendMessage(
        text(
            "Reloaded "
                + plugin.modelManager().models().size()
                + " model(s). Active models removed.",
            NamedTextColor.GREEN));
  }

  private void pack(CommandSender sender) {
    try {
      Path packPath = plugin.generatePack();
      sender.sendMessage(
          text("Generated pack: " + packPath.toAbsolutePath(), NamedTextColor.GREEN));
    } catch (Exception exception) {
      sender.sendMessage(text("Pack failed: " + exception.getMessage(), NamedTextColor.RED));
      plugin.getLogger().log(Level.SEVERE, "Failed to generate resource pack.", exception);
    }
  }

  private void list(CommandSender sender) {
    if (plugin.modelManager().empty()) {
      sender.sendMessage(text("No models loaded.", NamedTextColor.YELLOW));
      return;
    }

    for (ModelBlueprint model : plugin.modelManager().models()) {
      sender.sendMessage(modelSummary(model));
    }
  }

  private Component modelSummary(ModelBlueprint model) {
    return Component.text("- " + model.id(), NamedTextColor.YELLOW)
        .append(
            Component.text(
                " bones="
                    + model.bones().size()
                    + " cubes="
                    + model.cubes().size()
                    + " parts="
                    + model.parts().size()
                    + " hitboxes="
                    + model.hitboxes().size()
                    + " animations="
                    + model.animations().keySet(),
                NamedTextColor.GRAY));
  }

  private void spawn(CommandSender sender, String[] arguments) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(text("Only players.", NamedTextColor.RED));
      return;
    }

    if (arguments.length < 2) {
      sender.sendMessage(text("/om spawn <model> [animation]", NamedTextColor.RED));
      return;
    }

    Optional<ModelBlueprint> model = findModel(arguments[1]);
    if (model.isEmpty()) {
      sender.sendMessage(text("Unknown model: " + arguments[1], NamedTextColor.RED));
      return;
    }

    ModelBlueprint blueprint = model.get();
    if (blueprint.parts().isEmpty()) {
      sender.sendMessage(text("No render parts. Run /om reload and /om pack.", NamedTextColor.RED));
      return;
    }

    String animation = arguments.length >= 3 ? normalize(arguments[2]) : DEFAULT_ANIMATION;
    RuntimeModel runtimeModel =
        plugin
            .runtimeRegistry()
            .spawn(plugin.settings(), blueprint, player.getLocation(), animation);

    sender.sendMessage(
        text(
            "Spawned #"
                + runtimeModel.runtimeId()
                + " "
                + blueprint.id()
                + " parts="
                + blueprint.parts().size()
                + " hitboxes="
                + blueprint.hitboxes().size(),
            NamedTextColor.GREEN));
  }

  private void spawnMob(CommandSender sender, String[] arguments) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(text("Only players.", NamedTextColor.RED));
      return;
    }

    if (arguments.length < 3) {
      sender.sendMessage(
          text("/om spawnmob <entity-type> <model> [animation]", NamedTextColor.RED));
      return;
    }

    Optional<EntityType> parsedType = parseEntityType(arguments[1]);
    if (parsedType.isEmpty() || !isLivingEntityType(parsedType.get())) {
      sender.sendMessage(text("Unknown living entity type: " + arguments[1], NamedTextColor.RED));
      return;
    }

    Optional<ModelBlueprint> model = findModel(arguments[2]);
    if (model.isEmpty()) {
      sender.sendMessage(text("Unknown model: " + arguments[2], NamedTextColor.RED));
      return;
    }

    ModelBlueprint blueprint = model.get();
    if (blueprint.parts().isEmpty()) {
      sender.sendMessage(text("No render parts. Run /om reload and /om pack.", NamedTextColor.RED));
      return;
    }

    Entity entity = player.getWorld().spawnEntity(player.getLocation(), parsedType.get());
    if (!(entity instanceof LivingEntity baseEntity)) {
      entity.remove();
      sender.sendMessage(text("Entity type is not a LivingEntity.", NamedTextColor.RED));
      return;
    }

    String animation = arguments.length >= 4 ? normalize(arguments[3]) : DEFAULT_ANIMATION;
    RuntimeModel runtimeModel =
        plugin
            .runtimeRegistry()
            .spawn(plugin.settings(), blueprint, player.getLocation(), animation, baseEntity);

    sender.sendMessage(
        text(
            "Spawned mob #"
                + runtimeModel.runtimeId()
                + " "
                + parsedType.get().name().toLowerCase(Locale.ROOT)
                + " model="
                + blueprint.id()
                + " parts="
                + blueprint.parts().size()
                + " hitboxes="
                + blueprint.hitboxes().size(),
            NamedTextColor.GREEN));
  }

  private void play(CommandSender sender, String[] arguments) {
    if (arguments.length < 3) {
      sender.sendMessage(text("/om play <id> <animation>", NamedTextColor.RED));
      return;
    }

    Optional<RuntimeModel> model = runtimeModel(arguments[1]);
    if (model.isEmpty()) {
      sender.sendMessage(text("Unknown runtime id.", NamedTextColor.RED));
      return;
    }

    RuntimeModel runtimeModel = model.get();
    boolean playing = runtimeModel.play(normalize(arguments[2]));
    if (playing) {
      sender.sendMessage(text("Playing.", NamedTextColor.GREEN));
      return;
    }

    sender.sendMessage(
        text(
            "Animation missing: " + runtimeModel.blueprint().animations().keySet(),
            NamedTextColor.RED));
  }

  private void remove(CommandSender sender, String[] arguments) {
    if (arguments.length < 2) {
      sender.sendMessage(text("/om remove <id|all>", NamedTextColor.RED));
      return;
    }

    if (arguments[1].equalsIgnoreCase("all")) {
      plugin.runtimeRegistry().removeAll();
      sender.sendMessage(text("Removed all.", NamedTextColor.GREEN));
      return;
    }

    Optional<Integer> id = parseInteger(arguments[1]);
    if (id.isPresent() && plugin.runtimeRegistry().remove(id.get())) {
      sender.sendMessage(text("Removed.", NamedTextColor.GREEN));
      return;
    }

    sender.sendMessage(text("Unknown id.", NamedTextColor.RED));
  }

  private void debug(CommandSender sender, String[] arguments) {
    if (arguments.length < 2) {
      sender.sendMessage(text("/om debug <model>", NamedTextColor.RED));
      return;
    }

    Optional<ModelBlueprint> model = findModel(arguments[1]);
    if (model.isEmpty()) {
      sender.sendMessage(text("Unknown model.", NamedTextColor.RED));
      return;
    }

    ModelBlueprint blueprint = model.get();
    sender.sendMessage(
        text(
            "Debug " + blueprint.id() + " source=" + blueprint.source().getFileName(),
            NamedTextColor.GOLD));
    sender.sendMessage(debugSummary(blueprint));
    sendTextureDebug(sender, blueprint);
    sendFirstFaceDebug(sender, blueprint);
  }

  private Component debugSummary(ModelBlueprint model) {
    return text(
        "cubes="
            + model.cubes().size()
            + " parts="
            + model.parts().size()
            + " hitboxes="
            + model.hitboxes().size()
            + " animations="
            + model.animations().keySet(),
        NamedTextColor.YELLOW);
  }

  private void sendTextureDebug(CommandSender sender, ModelBlueprint model) {
    for (Map.Entry<String, TextureAsset> entry : model.textures().entrySet()) {
      if (!isNumericKey(entry.getKey())) {
        continue;
      }

      TextureAsset texture = entry.getValue();
      sender.sendMessage(
          Component.text("texture #" + entry.getKey(), NamedTextColor.AQUA)
              .append(
                  Component.text(
                      " "
                          + texture.name()
                          + " uv="
                          + texture.frameWidth()
                          + "x"
                          + texture.frameHeight()
                          + " image="
                          + texture.imageWidth()
                          + "x"
                          + texture.imageHeight(),
                      NamedTextColor.GRAY)));
    }
  }

  private void sendFirstFaceDebug(CommandSender sender, ModelBlueprint model) {
    for (Cube cube : model.cubes().values()) {
      for (Face face : cube.faces().values()) {
        sender.sendMessage(sampleFaceDebugMessage(model, cube, face));
        return;
      }
    }
  }

  private Component sampleFaceDebugMessage(ModelBlueprint model, Cube cube, Face face) {
    TextureAsset texture = model.texture(face.textureKey());

    return Component.text("sample face", NamedTextColor.AQUA)
        .append(
            Component.text(
                " cube="
                    + cube.name()
                    + " reversed="
                    + cube.reversed()
                    + " rawUV=["
                    + face.u1()
                    + ","
                    + face.v1()
                    + ","
                    + face.u2()
                    + ","
                    + face.v2()
                    + "] writtenUV=["
                    + scaledUv(face.u1(), texture.frameWidth())
                    + ","
                    + scaledUv(face.v1(), texture.frameHeight())
                    + ","
                    + scaledUv(face.u2(), texture.frameWidth())
                    + ","
                    + scaledUv(face.v2(), texture.frameHeight())
                    + "]",
                NamedTextColor.GRAY));
  }

  private void sendHelp(CommandSender sender, String label) {
    sender.sendMessage(
        text(
            "/" + label + " reload|pack|list|spawn|spawnmob|play|remove|debug",
            NamedTextColor.GOLD));
  }

  private Optional<ModelBlueprint> findModel(String modelId) {
    return plugin.modelManager().model(normalize(modelId));
  }

  private Optional<RuntimeModel> runtimeModel(String rawId) {
    return parseInteger(rawId).flatMap(id -> plugin.runtimeRegistry().model(id));
  }

  private List<String> loadedModelIds() {
    return plugin.modelManager().models().stream().map(ModelBlueprint::id).toList();
  }

  private List<String> livingEntityTypeIds() {
    return java.util.Arrays.stream(EntityType.values())
        .filter(ModelCommand::isLivingEntityType)
        .map(entityType -> entityType.name().toLowerCase(Locale.ROOT))
        .toList();
  }

  private List<String> runtimeModelIds() {
    return plugin.runtimeRegistry().models().stream()
        .map(model -> String.valueOf(model.runtimeId()))
        .toList();
  }

  private static String normalize(String value) {
    return value.toLowerCase(Locale.ROOT);
  }
}
