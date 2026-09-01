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
  private volatile RuntimeModel[] animationSnapshot = new RuntimeModel[0];

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
    refreshAnimationSnapshot();
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

    refreshAnimationSnapshot();
    model.remove();
    return true;
  }

  /** Main-thread Bukkit lifecycle tick. */
  public void tick() {
    Iterator<RuntimeModel> iterator = models.values().iterator();
    boolean changed = false;

    while (iterator.hasNext()) {
      RuntimeModel model = iterator.next();
      if (model.removed()) {
        iterator.remove();
        changed = true;
        continue;
      }

      model.tick();
      if (model.removed()) {
        iterator.remove();
        changed = true;
      }
    }

    if (changed) {
      refreshAnimationSnapshot();
    }
  }

  /** Packet-only 25 ms animation tick; never touches the mutable registry map. */
  public void animationTick() {
    for (RuntimeModel model : animationSnapshot) {
      model.animationTick();
    }
  }

  public void removeAll() {
    RuntimeModel[] snapshot = animationSnapshot;
    animationSnapshot = new RuntimeModel[0];

    for (RuntimeModel model : snapshot) {
      model.remove();
    }

    models.clear();
  }

  private void refreshAnimationSnapshot() {
    animationSnapshot = models.values().toArray(RuntimeModel[]::new);
  }
}
