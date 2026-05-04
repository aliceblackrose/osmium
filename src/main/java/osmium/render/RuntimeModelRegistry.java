package osmium.render;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.Plugin;
import osmium.PluginSettings;
import osmium.model.ModelBlueprint;

public final class RuntimeModelRegistry {
  private int nextId = 1;

  private final Plugin plugin;
  private final NamespacedKey runtimeModelKey;
  private final Map<Integer, RuntimeModel> models = new LinkedHashMap<>();

  public RuntimeModelRegistry(Plugin plugin, NamespacedKey runtimeModelKey) {
    this.plugin = plugin;
    this.runtimeModelKey = runtimeModelKey;
  }

  public RuntimeModel spawn(
      PluginSettings settings, ModelBlueprint blueprint, Location location, String animation) {
    return spawn(settings, blueprint, location, animation, null);
  }

  public RuntimeModel spawn(
      PluginSettings settings,
      ModelBlueprint blueprint,
      Location location,
      String animation,
      LivingEntity baseEntity) {
    int id = nextId++;
    RuntimeModel model =
        new RuntimeModel(
            id, plugin, runtimeModelKey, settings, blueprint, location, animation, baseEntity);
    models.put(id, model);
    return model;
  }

  public Optional<RuntimeModel> model(int id) {
    return Optional.ofNullable(models.get(id));
  }

  public Collection<RuntimeModel> models() {
    return Collections.unmodifiableCollection(models.values());
  }

  public boolean remove(int id) {
    RuntimeModel model = models.remove(id);
    if (model == null) {
      return false;
    }

    model.remove();
    return true;
  }

  public void tick() {
    Iterator<RuntimeModel> iterator = models.values().iterator();

    while (iterator.hasNext()) {
      RuntimeModel model = iterator.next();
      if (model.removed()) {
        iterator.remove();
        continue;
      }

      model.tick();
    }
  }

  public void removeAll() {
    for (RuntimeModel model : models.values()) {
      model.remove();
    }

    models.clear();
  }
}
