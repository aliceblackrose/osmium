package osmium.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import osmium.animation.AnimationCompilationCache;
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
import osmium.network.NmsAnimationPacketTransport;
import osmium.util.Names;

/** Runtime instance of a generated model rendered with Bukkit display entities. */
public final class RuntimeModel {
  private static final String IDLE_ANIMATION_NAME = "idle";
  private static final String WALK_ANIMATION_NAME = "walk";
  private static final double MOVEMENT_THRESHOLD_SQUARED = 0.00005D;
  private static final double ROOT_POSITION_EPSILON_SQUARED = 1.0E-10D;
  private static final double LOOPING_ACTION_SECONDS = 1.25D;
  private static final double ACTION_PADDING_SECONDS = 0.05D;
  private static final float MINIMUM_HITBOX_SIZE = 0.1F;
  private static final float MINIMUM_RENDER_SCALE_COMPONENT = 1.0E-4F;
  private static final float MINIMUM_AUTO_SHADOW_RADIUS = 0.25F;
  private static final float HITBOX_SIZE_EPSILON = 1.0E-5F;
  private static final int LIGHTING_UPDATE_INTERVAL_TICKS = 2;
  private static final int LIGHTING_FULL_REFRESH_UPDATES = 10;
  private static final int VIEWER_UPDATE_INTERVAL_TICKS = 4;

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
  private final Location liveOriginScratch;
  private final float staticYawRadians;
  private final LivingEntity baseEntity;
  private final ArrayList<PartRenderCache> parts;
  private final ArrayList<HitboxRenderCache> hitboxes;
  private final IdentityHashMap<Bone, BoneRenderCache> boneCaches;
  private final Matrix4f rootTransform = new Matrix4f();
  private final LightSampleCache lightSampleCache;
  private final Optional<Animation> idleAnimation;
  private final Optional<Animation> walkAnimation;
  private final Optional<Animation> talkAnimation;
  private final Optional<Animation> attackAnimation;
  private final Optional<Animation> hurtAnimation;
  private final Optional<Animation> deathAnimation;
  private final AnimationState animationState = new AnimationState();
  private final Object animationLock = new Object();
  private final Set<UUID> hiddenPlayers = new HashSet<>();

  private volatile boolean removed;
  private volatile boolean frozen;
  private volatile float currentYawRadians;

  private NmsAnimationPacketTransport.ViewerSnapshot animationViewers =
      NmsAnimationPacketTransport.ViewerSnapshot.EMPTY;
  private long viewerGeneration;
  private boolean manualAnimation;
  private boolean deathAnimationStarted;
  private long actionEndsAtNanos;
  private float lastRenderedYawRadians = Float.NaN;
  private long sentViewerGeneration = -1L;
  private long poseGeneration;
  private long rootGeneration;
  private long lastHitboxPoseGeneration = -1L;
  private long lastHitboxRootGeneration = -1L;
  private long lastLightingPoseGeneration = -1L;
  private long lastLightingRootGeneration = -1L;
  private int lightingTicksUntilUpdate;
  private int lightingUpdatesSinceFullRefresh;
  private int viewerTicksUntilUpdate;
  private World lastRootWorld;
  private double lastRootX = Double.NaN;
  private double lastRootY = Double.NaN;
  private double lastRootZ = Double.NaN;

  public RuntimeModel(
      int id,
      Plugin plugin,
      NamespacedKey runtimeModelKey,
      PluginSettings settings,
      ModelBlueprint blueprint,
      Location location,
      String initialAnimation,
      LivingEntity baseEntity) {
    this(
        id,
        plugin,
        runtimeModelKey,
        settings,
        blueprint,
        location,
        initialAnimation,
        baseEntity,
        new AnimationCompilationCache());
  }

  RuntimeModel(
      int id,
      Plugin plugin,
      NamespacedKey runtimeModelKey,
      PluginSettings settings,
      ModelBlueprint blueprint,
      Location location,
      String initialAnimation,
      LivingEntity baseEntity,
      AnimationCompilationCache animationCompilationCache) {
    this.id = id;
    this.plugin = plugin;
    this.runtimeModelKey = runtimeModelKey;
    this.settings = settings;
    this.blueprint = blueprint;
    this.staticOrigin = normalizedOrigin(location);
    this.liveOriginScratch = staticOrigin.clone();
    this.staticYawRadians = yawRadians(location);
    this.currentYawRadians = staticYawRadians;
    this.baseEntity = baseEntity;
    this.parts = new ArrayList<>(blueprint.parts().size());
    this.hitboxes = new ArrayList<>(blueprint.hitboxes().size());
    this.boneCaches = new IdentityHashMap<>(Math.max(blueprint.bones().size(), 1));
    this.lightSampleCache = new LightSampleCache(Math.max(blueprint.parts().size(), 1));
    this.idleAnimation = firstAnimation(IDLE_ANIMATIONS);
    this.walkAnimation = firstAnimation(WALK_ANIMATIONS);
    this.talkAnimation = firstAnimation(TALK_ANIMATIONS);
    this.attackAnimation = firstAnimation(ATTACK_ANIMATIONS);
    this.hurtAnimation = firstAnimation(HURT_ANIMATIONS);
    this.deathAnimation = firstAnimation(DEATH_ANIMATIONS);
    this.lightingTicksUntilUpdate = Math.floorMod(id, LIGHTING_UPDATE_INTERVAL_TICKS);
    this.lightingUpdatesSinceFullRefresh = Math.floorMod(id, LIGHTING_FULL_REFRESH_UPDATES);
    this.viewerTicksUntilUpdate = Math.floorMod(id, VIEWER_UPDATE_INTERVAL_TICKS);

    initializeBoneCaches(blueprint.root());
    animationState.configure(
        blueprint.root(), settings.interpolationDuration(), animationCompilationCache);
    setupBaseEntity();
    spawnParts();
    spawnHitboxes();
    playInitialAnimation(initialAnimation);
    initializeServerPose();
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
    synchronized (animationLock) {
      manualAnimation = baseEntity == null;
      frozen = false;
      actionEndsAtNanos =
          baseEntity == null ? 0 : System.nanoTime() + actionDurationNanos(requestedAnimation);
      animationState.play(requestedAnimation);
    }
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

  /** Main-server-thread tick for entity lifecycle, movement, tracking, hitboxes, and lighting. */
  public void tick() {
    if (removed) {
      return;
    }

    if (baseEntity == null && frozen) {
      updateFrozenVisuals();
      return;
    }

    long nowNanos = System.nanoTime();
    if (baseEntity != null && (!baseEntity.isValid() || baseEntity.isDead())) {
      if (!deathAnimationStarted) {
        playDeath();
      }

      boolean animationStillRunning;
      synchronized (animationLock) {
        animationStillRunning =
            deathAnimationStarted
                && actionEndsAtNanos > 0
                && nowNanos < actionEndsAtNanos
                && !animationState.complete();
      }

      if (animationStillRunning) {
        updateMainThreadVisuals();
        return;
      }

      remove();
      return;
    }

    updateAnimationController(nowNanos);
    updateMainThreadVisuals();
  }

  /**
   * 25 ms packet-render tick. This method must not call Bukkit APIs; it only consumes main-thread
   * snapshots, computes local matrices, and sends vanilla packets through cached player
   * connections.
   */
  public void animationTick() {
    if (removed) {
      return;
    }

    synchronized (animationLock) {
      if (removed) {
        return;
      }

      long currentViewerGeneration = viewerGeneration;
      boolean viewersChanged = currentViewerGeneration != sentViewerGeneration;
      boolean hasViewers = animationViewers.hasViewers();

      if (baseEntity == null && frozen) {
        if (viewersChanged && hasViewers) {
          sendAnimationTransforms(animationState.interpolationDurationTicks(), true);
        }
        sentViewerGeneration = currentViewerGeneration;
        return;
      }

      // Purely visual models can advance their playback cursor without evaluating matrices while
      // nobody is watching. Models with hitboxes still evaluate poses so gameplay remains correct.
      if (!hasViewers && hitboxes.isEmpty()) {
        sentViewerGeneration = currentViewerGeneration;
        advanceAnimation();
        return;
      }

      float yawRadians = currentYawRadians;
      boolean yawChanged = Float.compare(yawRadians, lastRenderedYawRadians) != 0;
      boolean poseDirty = animationState.dirty() || yawChanged;
      CompiledAnimation.Frame animationFrame = null;

      if (poseDirty) {
        animationFrame = animationState.frame();
        boolean poseChanged =
            applyBoneTransform(
                blueprint.root(), updateRootTransform(yawRadians), animationFrame, yawChanged);
        if (poseChanged) {
          poseGeneration++;
        }
        lastRenderedYawRadians = yawRadians;
        animationState.markRendered();
      }

      if (hasViewers && (poseDirty || viewersChanged)) {
        int interpolationDuration = animationState.interpolationDurationTicks();
        if (animationFrame == null) {
          animationFrame = animationState.frame();
        }
        if (animationFrame != null && animationFrame.skipInterpolation()) {
          interpolationDuration = 0;
        }
        sendAnimationTransforms(interpolationDuration, viewersChanged);
      }

      sentViewerGeneration = currentViewerGeneration;
      advanceAnimation();
    }
  }

  public void setVisible(Player player, boolean visible) {
    UUID playerId = player.getUniqueId();
    if (visible) {
      hiddenPlayers.remove(playerId);
      showParts(player);
      showHitboxes(player);
    } else {
      hiddenPlayers.add(playerId);
      hideParts(player);
      hideHitboxes(player);
    }

    refreshAnimationViewers();
    viewerTicksUntilUpdate = VIEWER_UPDATE_INTERVAL_TICKS - 1;
  }

  public void remove() {
    synchronized (animationLock) {
      if (removed) {
        return;
      }
      removed = true;
      animationViewers = NmsAnimationPacketTransport.ViewerSnapshot.EMPTY;
      viewerGeneration++;
    }

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

    Location origin = baseEntity.getLocation(liveOriginScratch);
    origin.setYaw(0);
    origin.setPitch(0);
    return origin;
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
      boolean shadowCarrier = parts.isEmpty();
      ItemDisplay display =
          world.spawn(
              spawnOrigin, ItemDisplay.class, entity -> setupPart(entity, part, shadowCarrier));
      parts.add(
          new PartRenderCache(
              new RuntimePart(part, display),
              display.getEntityId(),
              boneCaches.get(part.bone()),
              localTransform(part.bone(), part.cube())));
    }
  }

  private void setupPart(ItemDisplay display, RenderPart part, boolean shadowCarrier) {
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
    if (shadowCarrier && settings.shadowsEnabled()) {
      display.setShadowRadius(modelShadowRadius());
      display.setShadowStrength(settings.shadowStrength());
    } else {
      display.setShadowRadius(0.0F);
      display.setShadowStrength(0.0F);
    }
    if (settings.brightnessOverride()) {
      display.setBrightness(
          new Display.Brightness(settings.brightnessBlock(), settings.brightnessSky()));
    } else {
      display.setBrightness(null);
    }
    display.getPersistentDataContainer().set(runtimeModelKey, PersistentDataType.INTEGER, id);
  }

  private float modelShadowRadius() {
    if (settings.shadowRadius() > 0.0F) {
      return settings.shadowRadius();
    }

    Vec3 modelSize = blueprint.modelSizeBlocks();
    double footprint = Math.max(modelSize.x(), modelSize.z()) * settings.renderScale();
    return (float) Math.max(MINIMUM_AUTO_SHADOW_RADIUS, footprint * 0.5D);
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
              Transforms.bbLocalToMc(hitbox.cube().signedSize()).abs(),
              spawnOrigin));
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

  private void initializeServerPose() {
    float initialYawRadians = staticYawRadians;
    if (baseEntity != null) {
      initialYawRadians = yawRadians(baseEntity.getLocation(liveOriginScratch));
    }
    currentYawRadians = initialYawRadians;

    synchronized (animationLock) {
      CompiledAnimation.Frame frame = animationState.frame();
      if (applyBoneTransform(
          blueprint.root(), updateRootTransform(initialYawRadians), frame, true)) {
        poseGeneration++;
      }
      for (PartRenderCache cache : parts) {
        Matrix4f transform =
            cache.transform.set(cache.bone.transform).mul(cache.localTransform);
        applyInitialDisplayTransform(cache.runtime.display(), transform);
      }
      lastRenderedYawRadians = initialYawRadians;
    }
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
    if (manualAnimation || animation.isEmpty()) {
      return false;
    }

    Animation action = animation.get();
    synchronized (animationLock) {
      playIfChangedLocked(action);
      actionEndsAtNanos = System.nanoTime() + actionDurationNanos(action);
    }
    return true;
  }

  private void updateAnimationController(long nowNanos) {
    if (baseEntity == null || manualAnimation) {
      return;
    }

    synchronized (animationLock) {
      if (actionEndsAtNanos > 0) {
        if (nowNanos < actionEndsAtNanos && !animationState.complete()) {
          return;
        }
        actionEndsAtNanos = 0;
      }

      if (moving()) {
        walkAnimation.ifPresent(this::playIfChangedLocked);
      } else {
        idleAnimation.ifPresent(this::playIfChangedLocked);
      }
    }
  }

  private void playIfChangedLocked(Animation animation) {
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

  private void advanceAnimation() {
    animationState.advance();
    if (baseEntity == null && (animationState.animation() == null || animationState.complete())) {
      frozen = true;
    }
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

  private void updateFrozenVisuals() {
    if (viewerUpdateDue()) {
      refreshAnimationViewers();
    }

    World world = staticOrigin.getWorld();
    if (world == null) {
      return;
    }

    synchronized (animationLock) {
      updateScheduledLighting(world, staticOrigin);
      updateHitboxesIfDirty(world, staticOrigin);
    }
  }

  private void updateMainThreadVisuals() {
    Location currentLocation;
    float yawRadians;

    if (baseEntity == null) {
      currentLocation = staticOrigin;
      yawRadians = staticYawRadians;
    } else {
      currentLocation = baseEntity.getLocation(liveOriginScratch);
      yawRadians = yawRadians(currentLocation);
      currentLocation.setYaw(0);
      currentLocation.setPitch(0);
    }

    World world = currentLocation.getWorld();
    if (world == null) {
      return;
    }

    currentYawRadians = yawRadians;
    boolean rootMoved = updateRootState(currentLocation);
    if (rootMoved) {
      teleportParts(currentLocation);
    }
    if (viewerUpdateDue()) {
      refreshAnimationViewers();
    }

    synchronized (animationLock) {
      updateScheduledLighting(world, currentLocation);
      updateHitboxesIfDirty(world, currentLocation);
    }
  }

  private boolean updateRootState(Location rootLocation) {
    World world = rootLocation.getWorld();
    double x = rootLocation.getX();
    double y = rootLocation.getY();
    double z = rootLocation.getZ();

    boolean changed = lastRootWorld != world || Double.isNaN(lastRootX);
    if (!changed) {
      double dx = x - lastRootX;
      double dy = y - lastRootY;
      double dz = z - lastRootZ;
      changed = dx * dx + dy * dy + dz * dz > ROOT_POSITION_EPSILON_SQUARED;
    }

    if (changed) {
      lastRootWorld = world;
      lastRootX = x;
      lastRootY = y;
      lastRootZ = z;
      rootGeneration++;
    }
    return changed;
  }

  private boolean lightingUpdateDue() {
    if (lightingTicksUntilUpdate > 0) {
      lightingTicksUntilUpdate--;
      return false;
    }
    lightingTicksUntilUpdate = LIGHTING_UPDATE_INTERVAL_TICKS - 1;
    return true;
  }

  private boolean viewerUpdateDue() {
    if (viewerTicksUntilUpdate > 0) {
      viewerTicksUntilUpdate--;
      return false;
    }
    viewerTicksUntilUpdate = VIEWER_UPDATE_INTERVAL_TICKS - 1;
    return true;
  }

  private boolean fullLightingRefreshDue() {
    lightingUpdatesSinceFullRefresh++;
    if (lightingUpdatesSinceFullRefresh < LIGHTING_FULL_REFRESH_UPDATES) {
      return false;
    }
    lightingUpdatesSinceFullRefresh = 0;
    return true;
  }

  private void updateScheduledLighting(World world, Location rootLocation) {
    if (settings.brightnessOverride() || !lightingUpdateDue()) {
      return;
    }

    boolean forceRefresh = fullLightingRefreshDue();
    if (!forceRefresh
        && lastLightingPoseGeneration == poseGeneration
        && lastLightingRootGeneration == rootGeneration) {
      return;
    }

    updatePartLighting(world, rootLocation, forceRefresh);
    lastLightingPoseGeneration = poseGeneration;
    lastLightingRootGeneration = rootGeneration;
  }

  private void updatePartLighting(World world, Location rootLocation, boolean forceRefresh) {
    lightSampleCache.beginPass();
    double rootX = rootLocation.getX();
    double rootY = rootLocation.getY();
    double rootZ = rootLocation.getZ();

    for (PartRenderCache cache : parts) {
      PartLightState state = cache.lightState;
      long boneGeneration = cache.bone.generation;
      if (!forceRefresh
          && state.boneGeneration == boneGeneration
          && state.rootGeneration == rootGeneration) {
        continue;
      }

      Matrix4f transform = cache.transform.set(cache.bone.transform).mul(cache.localTransform);
      int blockX = (int) Math.floor(rootX + transform.m30());
      int blockY =
          Math.clamp(
              (int) Math.floor(rootY + transform.m31()),
              world.getMinHeight(),
              world.getMaxHeight() - 1);
      int blockZ = (int) Math.floor(rootZ + transform.m32());

      state.boneGeneration = boneGeneration;
      state.rootGeneration = rootGeneration;
      if (!forceRefresh && state.matchesPosition(blockX, blockY, blockZ)) {
        continue;
      }
      state.setPosition(blockX, blockY, blockZ);

      if (!world.isChunkLoaded(blockX >> 4, blockZ >> 4)) {
        continue;
      }

      long key = LightSampleCache.blockKey(blockX, blockY, blockZ);
      int packedLight = lightSampleCache.get(key);
      if (packedLight < 0) {
        Block lightBlock = world.getBlockAt(blockX, blockY, blockZ);
        packedLight =
            LightSampleCache.packLight(
                lightBlock.getLightFromBlocks(), lightBlock.getLightFromSky());
        lightSampleCache.put(key, packedLight);
      }

      int blockLight = LightSampleCache.blockLight(packedLight);
      int skyLight = LightSampleCache.skyLight(packedLight);
      if (state.matchesLight(blockLight, skyLight)) {
        continue;
      }

      cache.runtime.display().setBrightness(new Display.Brightness(blockLight, skyLight));
      state.setLight(blockLight, skyLight);
    }
  }

  private void refreshAnimationViewers() {
    if (parts.isEmpty()) {
      return;
    }

    NmsAnimationPacketTransport.ViewerSnapshot next =
        NmsAnimationPacketTransport.snapshotViewers(parts.getFirst().runtime.display(), hiddenPlayers);
    synchronized (animationLock) {
      if (!next.playerIds().equals(animationViewers.playerIds())) {
        viewerGeneration++;
      }
      animationViewers = next;
    }
  }

  private Matrix4f updateRootTransform(float yawRadians) {
    rootTransform.identity().rotateY(yawRadians).scale((float) settings.renderScale());
    if (settings.groundAlign()) {
      rootTransform.translate(0, (float) -blueprint.minY(), 0);
    }
    return rootTransform;
  }

  private boolean applyBoneTransform(
      Bone bone,
      Matrix4f parentTransform,
      CompiledAnimation.Frame animationFrame,
      boolean parentDirty) {
    BoneRenderCache cache = boneCaches.get(bone);
    BoneTimeline.Sample animationSample = sample(animationFrame, bone);
    boolean localDirty = parentDirty || !animationSample.equals(cache.lastSample);

    if (localDirty) {
      updateBoneTransform(cache, parentTransform, animationSample);
      cache.lastSample = animationSample;
      cache.generation++;
    }

    boolean changed = localDirty;
    for (Bone child : bone.children()) {
      changed |= applyBoneTransform(child, cache.transform, animationFrame, localDirty);
    }
    return changed;
  }

  private static void updateBoneTransform(
      BoneRenderCache cache,
      Matrix4f parentTransform,
      BoneTimeline.Sample animationSample) {
    Vec3 animationPosition = animationSample.position();
    Vec3 scale = animationSample.scale();

    cache
        .transform
        .set(parentTransform)
        .translate(
            (float) (cache.localPosition.x() - animationPosition.x() * Transforms.UNIT),
            (float) (cache.localPosition.y() + animationPosition.y() * Transforms.UNIT),
            (float) (cache.localPosition.z() - animationPosition.z() * Transforms.UNIT))
        .rotate(cache.localRotation)
        .rotate(Transforms.animationRotation(animationSample.rotation(), cache.animationRotation))
        .scale(stableScale(scale.x()), stableScale(scale.y()), stableScale(scale.z()));
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

  private void sendAnimationTransforms(int interpolationDuration, boolean force) {
    NmsAnimationPacketTransport.Batch batch = NmsAnimationPacketTransport.batch();
    for (PartRenderCache cache : parts) {
      long boneGeneration = cache.bone.generation;
      if (!force && cache.packetBoneGeneration == boneGeneration) {
        continue;
      }

      Matrix4f transform = cache.transform.set(cache.bone.transform).mul(cache.localTransform);
      batch.add(cache.entityId, transform, interpolationDuration, force, cache.packetState);
      cache.packetBoneGeneration = boneGeneration;
    }
    batch.send(animationViewers);
  }

  private void teleportParts(Location rootLocation) {
    for (PartRenderCache cache : parts) {
      cache.runtime.display().teleport(rootLocation);
    }
  }

  private void updateHitboxesIfDirty(World world, Location rootLocation) {
    if (lastHitboxPoseGeneration == poseGeneration
        && lastHitboxRootGeneration == rootGeneration) {
      return;
    }
    updateHitboxes(world, rootLocation);
    lastHitboxPoseGeneration = poseGeneration;
    lastHitboxRootGeneration = rootGeneration;
  }

  private void updateHitboxes(World world, Location rootLocation) {
    double rootX = rootLocation.getX();
    double rootY = rootLocation.getY();
    double rootZ = rootLocation.getZ();

    for (HitboxRenderCache cache : hitboxes) {
      HitboxRenderState state = cache.state;
      long boneGeneration = cache.bone.generation;
      if (state.boneGeneration == boneGeneration && state.rootGeneration == rootGeneration) {
        continue;
      }

      Matrix4f transform = cache.transform.set(cache.bone.transform).mul(cache.localTransform);
      transform.getScale(state.scaleScratch);
      Vec3 size = cache.size;

      float width =
          Math.max(
              MINIMUM_HITBOX_SIZE,
              (float)
                  Math.max(
                      Math.abs(size.x() * state.scaleScratch.x),
                      Math.abs(size.z() * state.scaleScratch.z)));
      float height =
          Math.max(
              MINIMUM_HITBOX_SIZE,
              (float) Math.abs(size.y() * state.scaleScratch.y));

      Interaction interaction = cache.runtime.interaction();
      if (Math.abs(width - state.width) > HITBOX_SIZE_EPSILON) {
        interaction.setInteractionWidth(width);
        state.width = width;
      }
      if (Math.abs(height - state.height) > HITBOX_SIZE_EPSILON) {
        interaction.setInteractionHeight(height);
        state.height = height;
      }

      Location target = state.locationScratch;
      target.setWorld(world);
      target.setX(rootX + transform.m30());
      target.setY(rootY + transform.m31() - height * 0.5D);
      target.setZ(rootZ + transform.m32());
      target.setYaw(0.0F);
      target.setPitch(0.0F);
      interaction.teleport(target);

      state.boneGeneration = boneGeneration;
      state.rootGeneration = rootGeneration;
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

  private static void applyInitialDisplayTransform(ItemDisplay display, Matrix4f transform) {
    display.setInterpolationDuration(0);
    if (DisplayTransform.canUseDirectTrs(transform)) {
      display.setTransformation(DisplayTransform.directTrs(transform));
    } else {
      display.setTransformationMatrix(transform);
    }
    display.setInterpolationDelay(0);
  }

  private void showParts(Player player) {
    for (PartRenderCache part : parts) {
      player.showEntity(plugin, part.runtime.display());
    }
  }

  private void hideParts(Player player) {
    for (PartRenderCache part : parts) {
      player.hideEntity(plugin, part.runtime.display());
    }
  }

  private void showHitboxes(Player player) {
    for (HitboxRenderCache hitbox : hitboxes) {
      player.showEntity(plugin, hitbox.runtime.interaction());
    }
  }

  private void hideHitboxes(Player player) {
    for (HitboxRenderCache hitbox : hitboxes) {
      player.hideEntity(plugin, hitbox.runtime.interaction());
    }
  }

  private void removeParts() {
    for (PartRenderCache part : parts) {
      if (!part.runtime.display().isDead()) {
        part.runtime.display().remove();
      }
    }
    parts.clear();
  }

  private void removeHitboxes() {
    for (HitboxRenderCache hitbox : hitboxes) {
      if (!hitbox.runtime.interaction().isDead()) {
        hitbox.runtime.interaction().remove();
      }
    }
    hitboxes.clear();
  }

  private void removeBaseEntity() {
    if (baseEntity != null && !baseEntity.isDead()) {
      baseEntity.remove();
    }
  }

  private static final class BoneRenderCache {
    private final Matrix4f transform = new Matrix4f();
    private final Quaternionf localRotation;
    private final Quaternionf animationRotation = new Quaternionf();
    private final Vec3 localPosition;
    private BoneTimeline.Sample lastSample;
    private long generation;

    private BoneRenderCache(Bone bone, Vec3 overlayOffset) {
      localRotation = bone.localRotation();
      localPosition = bone.localPosition().add(overlayOffset);
    }
  }

  private static final class PartLightState {
    private int x = Integer.MIN_VALUE;
    private int y = Integer.MIN_VALUE;
    private int z = Integer.MIN_VALUE;
    private int block = -1;
    private int sky = -1;
    private long boneGeneration = -1L;
    private long rootGeneration = -1L;

    private boolean matchesPosition(int x, int y, int z) {
      return this.x == x && this.y == y && this.z == z;
    }

    private void setPosition(int x, int y, int z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }

    private boolean matchesLight(int block, int sky) {
      return this.block == block && this.sky == sky;
    }

    private void setLight(int block, int sky) {
      this.block = block;
      this.sky = sky;
    }
  }

  private static final class PartRenderCache {
    private final RuntimePart runtime;
    private final int entityId;
    private final BoneRenderCache bone;
    private final Matrix4f localTransform;
    private final Matrix4f transform = new Matrix4f();
    private final PartLightState lightState = new PartLightState();
    private final NmsAnimationPacketTransport.TransformState packetState =
        new NmsAnimationPacketTransport.TransformState();
    private long packetBoneGeneration = -1L;

    private PartRenderCache(
        RuntimePart runtime, int entityId, BoneRenderCache bone, Matrix4f localTransform) {
      this.runtime = runtime;
      this.entityId = entityId;
      this.bone = bone;
      this.localTransform = localTransform;
    }
  }

  private static final class HitboxRenderState {
    private final Vector3f scaleScratch = new Vector3f();
    private final Location locationScratch;
    private long boneGeneration = -1L;
    private long rootGeneration = -1L;
    private float width = Float.NaN;
    private float height = Float.NaN;

    private HitboxRenderState(Location location) {
      locationScratch = location.clone();
    }
  }

  private static final class HitboxRenderCache {
    private final RuntimeHitbox runtime;
    private final BoneRenderCache bone;
    private final Matrix4f localTransform;
    private final Matrix4f transform = new Matrix4f();
    private final Vec3 size;
    private final HitboxRenderState state;

    private HitboxRenderCache(
        RuntimeHitbox runtime,
        BoneRenderCache bone,
        Matrix4f localTransform,
        Vec3 size,
        Location location) {
      this.runtime = runtime;
      this.bone = bone;
      this.localTransform = localTransform;
      this.size = size;
      this.state = new HitboxRenderState(location);
    }
  }
}
