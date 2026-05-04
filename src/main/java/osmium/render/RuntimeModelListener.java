package osmium.render;

import java.util.Optional;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import osmium.OsmiumPlugin;

public final class RuntimeModelListener implements Listener {
  private final OsmiumPlugin plugin;

  public RuntimeModelListener(OsmiumPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(ignoreCancelled = true)
  public void onModelInteract(PlayerInteractEntityEvent event) {
    handleModelInteract(event);
  }

  @EventHandler(ignoreCancelled = true)
  public void onModelInteractAt(PlayerInteractAtEntityEvent event) {
    handleModelInteract(event);
  }

  private void handleModelInteract(PlayerInteractEntityEvent event) {
    Optional<RuntimeModel> model = model(event.getRightClicked());
    if (model.isEmpty()) {
      return;
    }

    event.setCancelled(true);
    model.get().playTalk();
  }

  @EventHandler(ignoreCancelled = true)
  public void onModelDamage(EntityDamageEvent event) {
    if (event.getEntity() instanceof Interaction) {
      return;
    }

    model(event.getEntity()).ifPresent(RuntimeModel::playHurt);
  }

  @EventHandler(ignoreCancelled = true)
  public void onModelDamageByEntity(EntityDamageByEntityEvent event) {
    Optional<RuntimeModel> attackingModel = model(damageSource(event.getDamager()));
    attackingModel.ifPresent(RuntimeModel::playAttack);

    if (!(event.getEntity() instanceof Interaction interaction)) {
      return;
    }

    Optional<RuntimeModel> damagedModel = model(interaction);
    if (damagedModel.isEmpty()) {
      return;
    }

    LivingEntity baseEntity = damagedModel.get().baseEntity();
    if (baseEntity == null || !baseEntity.isValid() || baseEntity.isDead()) {
      event.setCancelled(true);
      return;
    }

    event.setCancelled(true);
    damagedModel.get().playHurt();

    Entity source = damageSource(event.getDamager());
    if (source == null) {
      baseEntity.damage(event.getDamage());
      return;
    }

    baseEntity.damage(event.getDamage(), source);
  }

  @EventHandler(ignoreCancelled = true)
  public void onModelDeath(EntityDeathEvent event) {
    model(event.getEntity()).ifPresent(RuntimeModel::playDeath);
  }

  private Optional<RuntimeModel> model(Entity entity) {
    if (entity == null) {
      return Optional.empty();
    }

    Integer id = persistentModelId(entity.getPersistentDataContainer(), plugin.runtimeModelKey());
    if (id == null) {
      return Optional.empty();
    }

    return plugin.runtimeRegistry().model(id);
  }

  private static Integer persistentModelId(
      PersistentDataContainer container, NamespacedKey runtimeModelKey) {
    return container.get(runtimeModelKey, PersistentDataType.INTEGER);
  }

  private static Entity damageSource(Entity damager) {
    if (!(damager instanceof Projectile projectile)) {
      return damager;
    }

    ProjectileSource shooter = projectile.getShooter();
    return shooter instanceof Entity entity ? entity : damager;
  }
}
