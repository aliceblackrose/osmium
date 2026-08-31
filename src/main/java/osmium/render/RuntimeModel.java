package osmium.render;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import osmium.PluginSettings;
import osmium.animation.Animation;
import osmium.animation.AnimationState;
import osmium.animation.BoneTimeline;
import osmium.animation.CompiledAnimation;
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
  private static final float MINIMUM_RENDER_SCALE_COMPONENT = 1.0E-4F;

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
  private final ArrayList<PartRenderCache> parts;
  private final ArrayList<HitboxRenderCache> hitboxes;
  private final IdentityHashMap<Bone, BoneRenderCache> boneCaches;
  private final Matrix4f rootTransform = new Matrix4f();
  private final Optional<Animation> idleAnimation;
  private final Optional<Animation> walkAnimation;
  private final Optional<Animation> talkAnimation;
  private final Optional<Animation> attackAnimation;
  private final Optional<Animation> hurtAnimation;
  private final Optional<Animation> deathAnimation;
  private final AnimationState animationState = new AnimationState();

  private boolean removed;
  private boolean manualAnimation;
  private boolean deathAnimationStarted;
  private boolean frozen;
  private long actionEndsAtNanos;
  private float lastRenderedYawRadians = Float.NaN;

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
    this.boneCaches = new IdentityHashMap<>(Math.max(blueprint.bones().size(), 1));
    this.idleAnimation = firstAnimation(IDLE_ANIMATIONS);
    this.walkAnimation = firstAnimation(WALK_ANIMATIONS);
    this.talkAnimation = firstAnimation(TALK_ANIMATIONS);
    this.attackAnimation = firstAnimation(ATTACK_ANIMATIONS);
    this.hurtAnimation = firstAnimation(HURT_ANIMATIONS);
    this.deathAnimation = firstAnimation(DEATH_ANIMATIONS);

    initializeBoneCaches(blueprint.root());
    animationState.configure(blueprint.root(), settings.interpolationDuration());
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
    frozen = false;
    actionEndsAtNanos =
        baseEntity == null ? 0 : System.nanoTime() + actionDurationNanos(requestedAnimation);
    animationState.play(requestedAnimation);
    return true;
  }

  public boolean playTalk() {
    return playAction(talkAnimation);
  }

  public boolean playAttack() {
    return playAction(attackAnimation);
  }

  public boolean playHurt() {
    return playAction(hurtAnimation);
  }

  public boolean playDeath() {
    boolean played = playAction(deathAnimation);
    if (played) {
      deathAnimationStarted = true;
    }

    return played;
  }

  public void tick() {
    if (removed || (baseEntity == null && frozen)) {
      return;
    }

    long nowNanos = System.nanoTime();

    if (baseEntity != null && (!baseEntity.isValid() || baseEntity.isDead())) {
      if (!deathAnimationStarted) {
        playDeath();
      }

      if (deathAnimationStarted
          && actionEndsAtNanos > 0
          && nowNanos < actionEndsAtNanos
          && !animationState.complete()) {
        updateVisuals();
        animationState.advance();
        return;
      }

      remove();
      return;
    }

    updateAnimationController(nowNanos);
    updateVisuals();

    if (baseEntity == null && (animationState.animation() == null || animationState.complete())) {
      frozen = true;
    } else {
      animationState.advance();
    }
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
    Location spawnOrigin = origin();
    World world = spawnOrigin.getWorld();
    if (world == null) {
      return;
    }

    for (RenderPart part : blueprint.parts()) {
      ItemDisplay display =
          world.spawn(spawnOrigin, ItemDisplay.class, entity -> setupPart(entity, part));
      parts.add(
          new PartRenderCache(
              new RuntimePart(part, display),
              boneCaches.get(part.bone()),
              localTransform(part.bone(), part.cube()),
              new Matrix4f()));
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
    Location spawnOrigin = origin();
    World world = spawnOrigin.getWorld();
    if (world == null) {
      return;
    }

    for (HitboxPart hitbox : blueprint.hitboxes()) {
      Interaction interaction = world.spawn(spawnOrigin, Interaction.class, this::setupHitbox);
      hitboxes.add(
          new HitboxRenderCache(
              new RuntimeHitbox(hitbox, interaction),
              boneCaches.get(hitbox.bone()),
              localTransform(hitbox.bone(), hitbox.cube()),
              new Matrix4f(),
              Transforms.bbLocalToMc(hitbox.cube().signedSize()).abs()));
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

    idleAnimation
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

  private boolean playAction(Optional<Animation> animation) {
    if (manualAnimation) {
      return false;
    }

    if (animation.isEmpty()) {
      return false;
    }

    Animation action = animation.get();
    playIfChanged(action);
    actionEndsAtNanos = System.nanoTime() + actionDurationNanos(action);
    return true;
  }

  private void updateAnimationController(long nowNanos) {
    if (baseEntity == null || manualAnimation) {
      return;
    }

    if (actionEndsAtNanos > 0) {
      if (nowNanos < actionEndsAtNanos && !animationState.complete()) {
        return;
      }

      actionEndsAtNanos = 0;
    }

    if (moving()) {
      playLocomotion(walkAnimation);
      return;
    }

    playLocomotion(idleAnimation);
  }

  private void playLocomotion(Optional<Animation> animation) {
    animation.ifPresent(this::playIfChanged);
  }

  private void playIfChanged(Animation animation) {
    if (!animationState.playing(animation.name())) {
      frozen = false;
      animationState.play(animation);
    }
  }

  private boolean moving() {
    Vector velocity = baseEntity.getVelocity();
    double x = velocity.getX();
    double z = velocity.getZ();
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

  private void initializeBoneCaches(Bone bone) {
    boneCaches.put(
        bone, new BoneRenderCache(bone, OverlayDepthBias.minecraftOffset(blueprint, bone)));

    for (Bone child : bone.children()) {
      initializeBoneCaches(child);
    }
  }

  private void updateVisuals() {
    Location currentLocation;
    float currentYawRadians;

    if (baseEntity == null) {
      currentLocation = staticOrigin;
      currentYawRadians = staticYawRadians;
    } else {
      currentLocation = baseEntity.getLocation();
      currentYawRadians = yawRadians(currentLocation);
      currentLocation.setYaw(0);
      currentLocation.setPitch(0);
    }

    World world = currentLocation.getWorld();
    if (world == null) {
      return;
    }

    boolean yawChanged = Float.compare(currentYawRadians, lastRenderedYawRadians) != 0;
    boolean transformDirty = animationState.dirty() || yawChanged;
    if (transformDirty) {
      CompiledAnimation.Frame animationFrame = animationState.frame();
      applyBoneTransform(blueprint.root(), updateRootTransform(currentYawRadians), animationFrame);

      int interpolationDuration = animationState.interpolationDurationTicks();
      if (animationFrame != null && animationFrame.skipInterpolation()) {
        interpolationDuration = 0;
      }

      updateParts(currentLocation, interpolationDuration);
      lastRenderedYawRadians = currentYawRadians;
      animationState.markRendered();
    } else {
      teleportParts(currentLocation);
    }

    updateHitboxes(world, currentLocation);
  }

  private Matrix4f updateRootTransform(float yawRadians) {
    rootTransform.identity().rotateY(yawRadians).scale((float) settings.renderScale());

    if (settings.groundAlign()) {
      rootTransform.translate(0, (float) -blueprint.minY(), 0);
    }

    return rootTransform;
  }

  private void applyBoneTransform(
      Bone bone, Matrix4f parentTransform, CompiledAnimation.Frame animationFrame) {
    BoneRenderCache cache = boneCaches.get(bone);
    Matrix4f boneTransform =
        updateBoneTransform(cache, bone, parentTransform, sample(animationFrame, bone));

    for (Bone child : bone.children()) {
      applyBoneTransform(child, boneTransform, animationFrame);
    }
  }

  private static Matrix4f updateBoneTransform(
      BoneRenderCache cache,
      Bone bone,
      Matrix4f parentTransform,
      BoneTimeline.Sample animationSample) {
    Vec3 localPosition = bone.localPosition().add(cache.overlayOffset());
    Vec3 animationPosition = Transforms.animationPosition(animationSample.position());

    return cache
        .transform()
        .set(parentTransform)
        .translate(
            (float) (localPosition.x() + animationPosition.x()),
            (float) (localPosition.y() + animationPosition.y()),
            (float) (localPosition.z() + animationPosition.z()))
        .rotate(cache.localRotation())
        .rotate(Transforms.animationRotation(animationSample.rotation(), cache.animationRotation()))
        .scale(
            stableScale(animationSample.scale().x()),
            stableScale(animationSample.scale().y()),
            stableScale(animationSample.scale().z()));
  }

  private static float stableScale(double value) {
    return Math.abs(value) < MINIMUM_RENDER_SCALE_COMPONENT
        ? MINIMUM_RENDER_SCALE_COMPONENT
        : (float) value;
  }

  private static BoneTimeline.Sample sample(CompiledAnimation.Frame animationFrame, Bone bone) {
    if (animationFrame == null) {
      return DEFAULT_SAMPLE;
    }

    BoneTimeline.Sample sample = animationFrame.pose(bone.name());
    return sample == null ? DEFAULT_SAMPLE : sample;
  }

  private void updateParts(Location rootLocation, int interpolationDuration) {
    for (PartRenderCache cache : parts) {
      Matrix4f transform =
          cache.transform().set(cache.bone().transform()).mul(cache.localTransform());
      applyDisplayTransform(rootLocation, cache.runtime().display(), transform, interpolationDuration);
    }
  }

  private void teleportParts(Location rootLocation) {
    for (PartRenderCache cache : parts) {
      cache.runtime().display().teleport(rootLocation);
    }
  }

  private void updateHitboxes(World world, Location rootLocation) {
    for (HitboxRenderCache cache : hitboxes) {
      Matrix4f transform =
          cache.transform().set(cache.bone().transform()).mul(cache.localTransform());
      Vector3f position = transform.getTranslation(new Vector3f());
      Vector3f scale = transform.getScale(new Vector3f());
      Vec3 size = cache.size();

      float width =
          Math.max(
              MINIMUM_HITBOX_SIZE,
              (float) Math.max(Math.abs(size.x() * scale.x), Math.abs(size.z() * scale.z)));
      float height = Math.max(MINIMUM_HITBOX_SIZE, (float) Math.abs(size.y() * scale.y));

      Interaction interaction = cache.runtime().interaction();
      interaction.setInteractionWidth(width);
      interaction.setInteractionHeight(height);
      interaction.teleport(
          new Location(
              world,
              rootLocation.getX() + position.x,
              rootLocation.getY() + position.y - height * 0.5D,
              rootLocation.getZ() + position.z,
              0,
              0));
    }
  }

  private static Matrix4f localTransform(Bone bone, Cube cube) {
    Quaternionf cubeRotation = Transforms.staticRotation(cube.rotation());
    return new Matrix4f()
        .translate(partCenter(bone, cube, cubeRotation))
        .rotate(cubeRotation)
        .rotate(Transforms.axisConversionRotation());
  }

  private static Vector3f partCenter(Bone bone, Cube cube, Quaternionf cubeRotation) {
    Vec3 pivot = Transforms.bbLocalToMc(cube.origin().subtract(bone.origin()));
    Vec3 offsetFromPivot = Transforms.bbLocalToMc(cube.center().subtract(cube.origin()));

    Vector3f center = offsetFromPivot.toVector3f();
    cubeRotation.transform(center);
    center.add(pivot.toVector3f());

    return center;
  }

  private static void applyDisplayTransform(
      Location rootLocation, ItemDisplay display, Matrix4f transform, int interpolationDuration) {
    display.teleport(rootLocation);
    display.setInterpolationDuration(Math.max(0, interpolationDuration));
    if (DisplayTransform.canUseDirectTrs(transform)) {
      display.setTransformation(DisplayTransform.directTrs(transform));
    } else {
      display.setTransformationMatrix(transform);
    }
    display.setInterpolationDelay(0);
  }

  private void showParts(Player player) {
    for (PartRenderCache part : parts) {
      player.showEntity(plugin, part.runtime().display());
    }
  }

  private void hideParts(Player player) {
    for (PartRenderCache part : parts) {
      player.hideEntity(plugin, part.runtime().display());
    }
  }

  private void showHitboxes(Player player) {
    for (HitboxRenderCache hitbox : hitboxes) {
      player.showEntity(plugin, hitbox.runtime().interaction());
    }
  }

  private void hideHitboxes(Player player) {
    for (HitboxRenderCache hitbox : hitboxes) {
      player.hideEntity(plugin, hitbox.runtime().interaction());
    }
  }

  private void removeParts() {
    for (PartRenderCache part : parts) {
      if (!part.runtime().display().isDead()) {
        part.runtime().display().remove();
      }
    }

    parts.clear();
  }

  private void removeHitboxes() {
    for (HitboxRenderCache hitbox : hitboxes) {
      if (!hitbox.runtime().interaction().isDead()) {
        hitbox.runtime().interaction().remove();
      }
    }

    hitboxes.clear();
  }

  private void removeBaseEntity() {
    if (baseEntity != null && !baseEntity.isDead()) {
      baseEntity.remove();
    }
  }

  private record BoneRenderCache(
      Matrix4f transform,
      Quaternionf localRotation,
      Quaternionf animationRotation,
      Vec3 overlayOffset) {
    private BoneRenderCache(Bone bone, Vec3 overlayOffset) {
      this(new Matrix4f(), bone.localRotation(), new Quaternionf(), overlayOffset);
    }
  }

  private record PartRenderCache(
      RuntimePart runtime, BoneRenderCache bone, Matrix4f localTransform, Matrix4f transform) {}

  private record HitboxRenderCache(
      RuntimeHitbox runtime,
      BoneRenderCache bone,
      Matrix4f localTransform,
      Matrix4f transform,
      Vec3 size) {}
}
