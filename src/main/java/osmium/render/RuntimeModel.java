package osmium.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import osmium.PluginSettings;
import osmium.animation.Animation;
import osmium.animation.AnimationState;
import osmium.animation.BoneTimeline;
import osmium.math.Transforms;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.HitboxPart;
import osmium.model.ModelBlueprint;
import osmium.model.RenderPart;
import osmium.util.Names;

/** Runtime instance of a generated model rendered with Bukkit display entities. */
public final class RuntimeModel {
  private static final String IDLE_ANIMATION_NAME = "idle";
  private static final String WALK_ANIMATION_NAME = "walk";
  private static final double MOVEMENT_THRESHOLD_SQUARED = 0.00005D;
  private static final double LOOPING_ACTION_SECONDS = 1.25D;
  private static final double ACTION_PADDING_SECONDS = 0.05D;
  private static final float MINIMUM_HITBOX_SIZE = 0.1F;

  private static final String[] IDLE_ANIMATIONS = {IDLE_ANIMATION_NAME, "stand", "standing"};
  private static final String[] WALK_ANIMATIONS = {
    WALK_ANIMATION_NAME, "walking", "move", "moving", "run", "running"
  };
  private static final String[] TALK_ANIMATIONS = {
    "talk", "talking", "speak", "speaking", "interact", "interaction"
  };
  private static final String[] ATTACK_ANIMATIONS = {
    "attack", "attacking", "bite", "melee", "shoot"
  };
  private static final String[] HURT_ANIMATIONS = {"hurt", "damaged", "damage", "hit"};
  private static final String[] DEATH_ANIMATIONS = {"death", "die", "dying"};

  private static final BoneTimeline.Sample DEFAULT_SAMPLE =
      new BoneTimeline.Sample(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);

  private final int id;
  private final Plugin plugin;
  private final NamespacedKey runtimeModelKey;
  private final PluginSettings settings;
  private final ModelBlueprint blueprint;
  private final Location staticOrigin;
  private final float staticYawRadians;
  private final LivingEntity baseEntity;
  private final ArrayList<RuntimePart> parts;
  private final ArrayList<RuntimeHitbox> hitboxes;
  private final AnimationState animationState = new AnimationState();

  private boolean removed;
  private boolean manualAnimation;
  private boolean deathAnimationStarted;
  private long actionEndsAtNanos;

  public RuntimeModel(
      int id,
      Plugin plugin,
      NamespacedKey runtimeModelKey,
      PluginSettings settings,
      ModelBlueprint blueprint,
      Location location,
      String initialAnimation,
      LivingEntity baseEntity) {
    this.id = id;
    this.plugin = plugin;
    this.runtimeModelKey = runtimeModelKey;
    this.settings = settings;
    this.blueprint = blueprint;
    this.staticOrigin = normalizedOrigin(location);
    this.staticYawRadians = yawRadians(location);
    this.baseEntity = baseEntity;
    this.parts = new ArrayList<>(blueprint.parts().size());
    this.hitboxes = new ArrayList<>(blueprint.hitboxes().size());

    setupBaseEntity();
    spawnParts();
    spawnHitboxes();
    playInitialAnimation(initialAnimation);
    tick();
  }

  public int runtimeId() {
    return id;
  }

  public ModelBlueprint blueprint() {
    return blueprint;
  }

  public LivingEntity baseEntity() {
    return baseEntity;
  }

  public boolean removed() {
    return removed;
  }

  public boolean play(String name) {
    Optional<Animation> animation = animation(name);
    if (animation.isEmpty()) {
      return false;
    }

    Animation requestedAnimation = animation.get();
    manualAnimation = baseEntity == null;
    actionEndsAtNanos =
        baseEntity == null ? 0 : System.nanoTime() + actionDurationNanos(requestedAnimation);
    playIfChanged(requestedAnimation);
    return true;
  }

  public boolean playTalk() {
    return playAction(TALK_ANIMATIONS);
  }

  public boolean playAttack() {
    return playAction(ATTACK_ANIMATIONS);
  }

  public boolean playHurt() {
    return playAction(HURT_ANIMATIONS);
  }

  public boolean playDeath() {
    boolean played = playAction(DEATH_ANIMATIONS);
    if (played) {
      deathAnimationStarted = true;
    }

    return played;
  }

  public void tick() {
    if (removed) {
      return;
    }

    if (baseEntity != null && (!baseEntity.isValid() || baseEntity.isDead())) {
      if (!deathAnimationStarted) {
        playDeath();
      }

      if (deathAnimationStarted
          && actionEndsAtNanos > 0
          && System.nanoTime() < actionEndsAtNanos
          && !animationState.complete()) {
        updateVisuals();
        return;
      }

      remove();
      return;
    }

    updateAnimationController();
    updateVisuals();
  }

  public void setVisible(Player player, boolean visible) {
    if (visible) {
      showParts(player);
      showHitboxes(player);
      return;
    }

    hideParts(player);
    hideHitboxes(player);
  }

  public void remove() {
    if (removed) {
      return;
    }

    removed = true;
    removeParts();
    removeHitboxes();
    removeBaseEntity();
  }

  private static Location normalizedOrigin(Location location) {
    Location origin = location.clone();
    origin.setYaw(0);
    origin.setPitch(0);
    return origin;
  }

  private static float yawRadians(Location location) {
    return (float) Math.toRadians(-location.getYaw());
  }

  private Location origin() {
    if (baseEntity == null) {
      return staticOrigin;
    }

    return normalizedOrigin(baseEntity.getLocation());
  }

  private float yawRadians() {
    return baseEntity == null ? staticYawRadians : yawRadians(baseEntity.getLocation());
  }

  private void setupBaseEntity() {
    if (baseEntity == null) {
      return;
    }

    baseEntity.setPersistent(false);
    baseEntity.setRemoveWhenFarAway(false);
    baseEntity.setInvisible(true);
    baseEntity.setSilent(true);
    baseEntity.setAI(true);
    baseEntity.getPersistentDataContainer().set(runtimeModelKey, PersistentDataType.INTEGER, id);

    if (baseEntity instanceof Mob mob) {
      mob.setAware(true);
    }
  }

  private void spawnParts() {
    World world = origin().getWorld();
    if (world == null) {
      return;
    }

    for (RenderPart part : blueprint.parts()) {
      ItemDisplay display =
          world.spawn(origin(), ItemDisplay.class, entity -> setupPart(entity, part));
      parts.add(new RuntimePart(part, display));
    }
  }

  private void setupPart(ItemDisplay display, RenderPart part) {
    display.setPersistent(false);
    display.setGravity(false);
    display.setInvulnerable(true);
    display.setSilent(true);
    display.setItemStack(
        ItemStacks.displayItem(settings.baseItem(), part.itemModelKey(), part.customModelData()));
    display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
    display.setBillboard(Display.Billboard.FIXED);
    display.setInterpolationDuration(settings.interpolationDuration());
    display.setTeleportDuration(settings.teleportDuration());
    display.setViewRange(settings.viewRange());
    display.setShadowRadius(settings.shadowRadius());
    display.setShadowStrength(settings.shadowStrength());
    display.setBrightness(
        new Display.Brightness(settings.brightnessBlock(), settings.brightnessSky()));
    display.getPersistentDataContainer().set(runtimeModelKey, PersistentDataType.INTEGER, id);
  }

  private void spawnHitboxes() {
    World world = origin().getWorld();
    if (world == null) {
      return;
    }

    for (HitboxPart hitbox : blueprint.hitboxes()) {
      Interaction interaction = world.spawn(origin(), Interaction.class, this::setupHitbox);
      hitboxes.add(new RuntimeHitbox(hitbox, interaction));
    }
  }

  private void setupHitbox(Interaction interaction) {
    interaction.setPersistent(false);
    interaction.setGravity(false);
    interaction.setInvulnerable(false);
    interaction.setSilent(true);
    interaction.setResponsive(true);
    interaction.setInteractionWidth(MINIMUM_HITBOX_SIZE);
    interaction.setInteractionHeight(MINIMUM_HITBOX_SIZE);
    interaction.getPersistentDataContainer().set(runtimeModelKey, PersistentDataType.INTEGER, id);
  }

  private void playInitialAnimation(String initialAnimation) {
    if (initialAnimation != null && !initialAnimation.isBlank()) {
      Optional<Animation> requestedAnimation = animation(initialAnimation);
      if (requestedAnimation.isPresent()) {
        animationState.play(requestedAnimation.get());
        return;
      }
    }

    firstAnimation(IDLE_ANIMATIONS)
        .or(() -> blueprint.animations().values().stream().findFirst())
        .ifPresent(animationState::play);
  }

  private Optional<Animation> animation(String name) {
    return blueprint.animation(normalizeAnimationName(name));
  }

  private Optional<Animation> firstAnimation(String... names) {
    for (String name : names) {
      Optional<Animation> animation = animation(name);
      if (animation.isPresent()) {
        return animation;
      }
    }

    return Optional.empty();
  }

  private boolean playAction(String... names) {
    if (manualAnimation) {
      return false;
    }

    Optional<Animation> animation = firstAnimation(names);
    if (animation.isEmpty()) {
      return false;
    }

    Animation action = animation.get();
    playIfChanged(action);
    actionEndsAtNanos = System.nanoTime() + actionDurationNanos(action);
    return true;
  }

  private void updateAnimationController() {
    if (baseEntity == null || manualAnimation) {
      return;
    }

    if (actionEndsAtNanos > 0) {
      if (System.nanoTime() < actionEndsAtNanos && !animationState.complete()) {
        return;
      }

      actionEndsAtNanos = 0;
    }

    if (moving()) {
      playLocomotion(WALK_ANIMATIONS);
      return;
    }

    playLocomotion(IDLE_ANIMATIONS);
  }

  private void playLocomotion(String... names) {
    firstAnimation(names).ifPresent(this::playIfChanged);
  }

  private void playIfChanged(Animation animation) {
    if (!animationState.playing(animation.name())) {
      animationState.play(animation);
    }
  }

  private boolean moving() {
    double x = baseEntity.getVelocity().getX();
    double z = baseEntity.getVelocity().getZ();
    return x * x + z * z > MOVEMENT_THRESHOLD_SQUARED;
  }

  private static long actionDurationNanos(Animation animation) {
    double seconds =
        animation.loop() ? LOOPING_ACTION_SECONDS : animation.length() + ACTION_PADDING_SECONDS;
    return (long) (seconds * 1_000_000_000L);
  }

  private static String normalizeAnimationName(String name) {
    return Names.key(name);
  }

  private void updateVisuals() {
    Map<String, Matrix4f> boneTransforms = computeBoneTransforms();
    updateParts(boneTransforms);
    updateHitboxes(boneTransforms);
  }

  private Map<String, Matrix4f> computeBoneTransforms() {
    Map<String, Matrix4f> boneTransforms = new HashMap<>();
    applyBoneTransform(blueprint.root(), rootTransform(), boneTransforms);
    return boneTransforms;
  }

  private Matrix4f rootTransform() {
    Location root = origin();
    Matrix4f transform =
        new Matrix4f()
            .translation((float) root.getX(), (float) root.getY(), (float) root.getZ())
            .rotateY(yawRadians())
            .scale((float) settings.renderScale());

    if (settings.groundAlign()) {
      transform.translate(0, (float) -blueprint.minY(), 0);
    }

    return transform;
  }

  private void applyBoneTransform(
      Bone bone, Matrix4f parentTransform, Map<String, Matrix4f> boneTransforms) {
    Matrix4f boneTransform = boneTransform(bone, parentTransform, sample(bone));
    boneTransforms.put(bone.name(), boneTransform);

    for (Bone child : bone.children()) {
      applyBoneTransform(child, boneTransform, boneTransforms);
    }
  }

  private Matrix4f boneTransform(
      Bone bone, Matrix4f parentTransform, BoneTimeline.Sample animationSample) {
    Vec3 animatedPosition =
        bone.localPosition().add(Transforms.animationPosition(animationSample.position()));

    return new Matrix4f(parentTransform)
        .translate(animatedPosition.toVector3f())
        .rotate(bone.localRotation())
        .rotate(Transforms.animationRotation(animationSample.rotation()))
        .scale(
            (float) animationSample.scale().x(),
            (float) animationSample.scale().y(),
            (float) animationSample.scale().z());
  }

  private BoneTimeline.Sample sample(Bone bone) {
    Animation animation = animationState.animation();
    if (animation == null) {
      return DEFAULT_SAMPLE;
    }

    BoneTimeline timeline = animation.timelines().get(bone.name());
    if (timeline == null) {
      return DEFAULT_SAMPLE;
    }

    return timeline.sample(animationState.time());
  }

  private void updateParts(Map<String, Matrix4f> boneTransforms) {
    for (RuntimePart runtimePart : parts) {
      RenderPart part = runtimePart.blueprint();
      Matrix4f boneTransform = boneTransforms.get(part.bone().name());

      if (boneTransform == null) {
        continue;
      }

      updatePart(runtimePart, part, boneTransform);
    }
  }

  private void updatePart(RuntimePart runtimePart, RenderPart part, Matrix4f boneTransform) {
    Quaternionf cubeRotation = Transforms.staticRotation(part.cube().rotation());
    Vector3f center = partCenter(part.bone(), part.cube(), cubeRotation);
    Matrix4f transform = new Matrix4f(boneTransform).translate(center).rotate(cubeRotation);

    applyDisplayTransform(runtimePart.display(), transform);
  }

  private void updateHitboxes(Map<String, Matrix4f> boneTransforms) {
    for (RuntimeHitbox runtimeHitbox : hitboxes) {
      HitboxPart part = runtimeHitbox.blueprint();
      Matrix4f boneTransform = boneTransforms.get(part.bone().name());

      if (boneTransform == null) {
        continue;
      }

      updateHitbox(runtimeHitbox, part, boneTransform);
    }
  }

  private void updateHitbox(RuntimeHitbox runtimeHitbox, HitboxPart part, Matrix4f boneTransform) {
    Quaternionf cubeRotation = Transforms.staticRotation(part.cube().rotation());
    Vector3f center = partCenter(part.bone(), part.cube(), cubeRotation);
    Matrix4f transform = new Matrix4f(boneTransform).translate(center).rotate(cubeRotation);
    Vector3f position = transform.getTranslation(new Vector3f());
    Vector3f scale = transform.getScale(new Vector3f());
    Vec3 size = Transforms.bbLocalToMc(part.cube().signedSize()).abs();

    float width =
        Math.max(
            MINIMUM_HITBOX_SIZE,
            (float) Math.max(Math.abs(size.x() * scale.x), Math.abs(size.z() * scale.z)));
    float height = Math.max(MINIMUM_HITBOX_SIZE, (float) Math.abs(size.y() * scale.y));

    Interaction interaction = runtimeHitbox.interaction();
    interaction.setInteractionWidth(width);
    interaction.setInteractionHeight(height);
    interaction.teleport(
        new Location(
            origin().getWorld(), position.x, position.y - height * 0.5D, position.z, 0, 0));
  }

  private Vector3f partCenter(Bone bone, Cube cube, Quaternionf cubeRotation) {
    Vec3 pivot = Transforms.bbLocalToMc(cube.origin().subtract(bone.origin()));
    Vec3 offsetFromPivot = Transforms.bbLocalToMc(cube.center().subtract(cube.origin()));

    Vector3f center = offsetFromPivot.toVector3f();
    cubeRotation.transform(center);
    center.add(pivot.toVector3f());

    return center;
  }

  private void applyDisplayTransform(ItemDisplay display, Matrix4f transform) {
    Vector3f position = transform.getTranslation(new Vector3f());
    Quaternionf rotation = transform.getNormalizedRotation(new Quaternionf());
    Vector3f scale = transform.getScale(new Vector3f());

    display.teleport(new Location(origin().getWorld(), position.x, position.y, position.z, 0, 0));
    display.setTransformation(
        new Transformation(new Vector3f(), rotation, scale, new Quaternionf()));
  }

  private void showParts(Player player) {
    for (RuntimePart part : parts) {
      player.showEntity(plugin, part.display());
    }
  }

  private void hideParts(Player player) {
    for (RuntimePart part : parts) {
      player.hideEntity(plugin, part.display());
    }
  }

  private void showHitboxes(Player player) {
    for (RuntimeHitbox hitbox : hitboxes) {
      player.showEntity(plugin, hitbox.interaction());
    }
  }

  private void hideHitboxes(Player player) {
    for (RuntimeHitbox hitbox : hitboxes) {
      player.hideEntity(plugin, hitbox.interaction());
    }
  }

  private void removeParts() {
    for (RuntimePart part : parts) {
      if (!part.display().isDead()) {
        part.display().remove();
      }
    }

    parts.clear();
  }

  private void removeHitboxes() {
    for (RuntimeHitbox hitbox : hitboxes) {
      if (!hitbox.interaction().isDead()) {
        hitbox.interaction().remove();
      }
    }

    hitboxes.clear();
  }

  private void removeBaseEntity() {
    if (baseEntity != null && !baseEntity.isDead()) {
      baseEntity.remove();
    }
  }
}
