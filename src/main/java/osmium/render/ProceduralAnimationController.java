package osmium.render;

import java.util.IdentityHashMap;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import osmium.PluginSettings;
import osmium.animation.BoneTimeline;
import osmium.animation.ProceduralAnimation;
import osmium.animation.ProceduralBonePreset;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.ModelBlueprint;

/** Stateful runtime inputs for additive procedural animation. */
final class ProceduralAnimationController {
  private static final double NANOS_PER_SECOND = 1_000_000_000.0;
  private static final double IDLE_VARIANT_RATE = 7.5;
  private static final long TRACKING_REFRESH_NANOS = 250_000_000L;

  private final int id;
  private final PluginSettings settings;
  private final ModelBlueprint blueprint;
  private final LivingEntity baseEntity;
  private final long createdAtNanos = System.nanoTime();
  private final double seed;
  private final IdentityHashMap<Bone, SpringState> springs = new IdentityHashMap<>();

  private ProceduralAnimation.Context context = ProceduralAnimation.Context.EMPTY;
  private boolean enabled = true;
  private long lastFlinchAtNanos = Long.MIN_VALUE;
  private double flinchDirection = 1.0;
  private double previousHorizontalSpeed;
  private double previousYawDegrees;
  private double smoothedTurnRateDegrees;
  private double smoothedAcceleration;
  private boolean previousMotionCaptured;
  private Player trackingTarget;
  private long nextTrackingSearchAtNanos;

  ProceduralAnimationController(
      int id,
      PluginSettings settings,
      ModelBlueprint blueprint,
      Location initialLocation,
      LivingEntity baseEntity) {
    this.id = id;
    this.settings = settings;
    this.blueprint = blueprint;
    this.baseEntity = baseEntity;
    this.seed = stableSeed(id, initialLocation);

    for (Bone bone : blueprint.bones()) {
      if (ProceduralAnimation.isSpringBone(bone.name())) {
        springs.put(bone, new SpringState());
      }
    }
  }

  boolean enabled() {
    return enabled;
  }

  void setEnabled(boolean enabled) {
    this.enabled = enabled;
    if (!enabled) {
      context = ProceduralAnimation.Context.EMPTY;
    }
  }

  boolean active() {
    return enabled && settings.proceduralAnimationEnabled();
  }

  void update(long nowNanos, Location modelLocation, double modelYawDegrees) {
    if (!active()) {
      context = ProceduralAnimation.Context.EMPTY;
      return;
    }

    double horizontalSpeed = horizontalSpeed();
    Kinematics kinematics = updateKinematics(horizontalSpeed, modelYawDegrees);
    LookAngles lookAngles = lookAngles(nowNanos, modelLocation, modelYawDegrees);
    double ageSeconds = ageSeconds(nowNanos);
    IdleVariant idleVariant = idleVariant(ageSeconds, horizontalSpeed);

    context =
        new ProceduralAnimation.Context(
            ageSeconds,
            horizontalSpeed,
            kinematics.turnRateDegrees(),
            kinematics.acceleration(),
            lookAngles.yaw(),
            lookAngles.pitch(),
            blinkAmount(ageSeconds),
            idleVariant.variant(),
            idleVariant.amount(),
            idleVariant.wave(),
            flinchAmount(nowNanos),
            flinchDirection);
  }

  BoneTimeline.Sample sample(Bone bone) {
    if (!active()) {
      return ProceduralAnimation.emptySample();
    }

    return ProceduralAnimation.sample(bone, context, springSample(bone), settings);
  }

  void flinch(long nowNanos) {
    if (!active() || !settings.proceduralHitFlinch()) {
      return;
    }

    lastFlinchAtNanos = nowNanos;
    flinchDirection = stableUnit(ageSeconds(nowNanos) * 19.19 + id * 73.73) >= 0.5 ? 1.0 : -1.0;
  }

  private ProceduralAnimation.BoneSpring springSample(Bone bone) {
    if (!settings.proceduralSpringBones()) {
      return ProceduralAnimation.BoneSpring.ZERO;
    }

    SpringState state = springs.get(bone);
    if (state == null) {
      return ProceduralAnimation.BoneSpring.ZERO;
    }

    ProceduralBonePreset preset = settings.proceduralBonePreset(bone.name());
    Vec3 target = ProceduralAnimation.springRotationTarget(bone, context, settings, preset);
    Vec3 rotation =
        state.update(
            target,
            preset.effectiveSpringStiffness(settings.proceduralSpringStiffness()),
            preset.effectiveSpringDamping(settings.proceduralSpringDamping()));
    return new ProceduralAnimation.BoneSpring(Vec3.ZERO, rotation);
  }

  private Kinematics updateKinematics(double horizontalSpeed, double yawDegrees) {
    if (!previousMotionCaptured) {
      previousHorizontalSpeed = horizontalSpeed;
      previousYawDegrees = yawDegrees;
      previousMotionCaptured = true;
      return new Kinematics(0, 0);
    }

    double rawTurnRateDegrees = wrapDegrees(yawDegrees - previousYawDegrees);
    double rawAcceleration = horizontalSpeed - previousHorizontalSpeed;
    previousHorizontalSpeed = horizontalSpeed;
    previousYawDegrees = yawDegrees;

    smoothedTurnRateDegrees = smoothedTurnRateDegrees * 0.68 + rawTurnRateDegrees * 0.32;
    smoothedAcceleration = smoothedAcceleration * 0.68 + rawAcceleration * 0.32;
    return new Kinematics(smoothedTurnRateDegrees, smoothedAcceleration);
  }

  private double horizontalSpeed() {
    if (baseEntity == null) {
      return 0;
    }

    Vector velocity = baseEntity.getVelocity();
    return Math.hypot(velocity.getX(), velocity.getZ());
  }

  private double blinkAmount(double ageSeconds) {
    if (!settings.proceduralBlinking()) {
      return 0;
    }

    double interval = settings.proceduralBlinkInterval();
    double duration = settings.proceduralBlinkDuration();
    double phase = positiveModulo(ageSeconds + seed * interval, interval);
    if (phase > duration) {
      return 0;
    }

    return Math.sin((phase / duration) * Math.PI);
  }

  private IdleVariant idleVariant(double ageSeconds, double horizontalSpeed) {
    if (!settings.proceduralIdleVariants() || horizontalSpeed > 0.025) {
      return IdleVariant.NONE;
    }

    double cycleSeconds = 5.8;
    double activeSeconds = 1.65;
    double shiftedAge = ageSeconds + seed * cycleSeconds;
    double localTime = positiveModulo(shiftedAge, cycleSeconds);
    if (localTime > activeSeconds) {
      return IdleVariant.NONE;
    }

    int cycle = (int) Math.floor(shiftedAge / cycleSeconds);
    int variant = (int) Math.floor(stableUnit(cycle + id * 31.0) * 4.0);
    double progress = localTime / activeSeconds;
    return new IdleVariant(
        variant, Math.sin(progress * Math.PI), Math.sin(progress * Math.PI * IDLE_VARIANT_RATE));
  }

  private double flinchAmount(long nowNanos) {
    if (!settings.proceduralHitFlinch() || lastFlinchAtNanos == Long.MIN_VALUE) {
      return 0;
    }

    double elapsedSeconds = (nowNanos - lastFlinchAtNanos) / NANOS_PER_SECOND;
    double duration = settings.proceduralHitFlinchDuration();
    if (elapsedSeconds < 0 || elapsedSeconds > duration) {
      return 0;
    }

    double progress = elapsedSeconds / duration;
    return Math.sin(progress * Math.PI) * Math.pow(1.0 - progress, 0.65);
  }

  private LookAngles lookAngles(long nowNanos, Location modelLocation, double modelYawDegrees) {
    if (!settings.proceduralHeadTracking() && !settings.proceduralEyeTracking()) {
      return LookAngles.ZERO;
    }

    World world = modelLocation.getWorld();
    if (world == null) {
      return LookAngles.ZERO;
    }

    Location source = trackingSource(modelLocation);
    Player target = trackingTarget(nowNanos, world, source);
    if (target == null) {
      return LookAngles.ZERO;
    }

    Location targetLocation = target.getEyeLocation();
    double deltaX = targetLocation.getX() - source.getX();
    double deltaY = targetLocation.getY() - source.getY();
    double deltaZ = targetLocation.getZ() - source.getZ();
    double horizontalDistance = Math.max(0.0001, Math.hypot(deltaX, deltaZ));
    double targetYaw = Math.toDegrees(Math.atan2(-deltaX, deltaZ));
    double targetPitch = -Math.toDegrees(Math.atan2(deltaY, horizontalDistance));

    return new LookAngles(
        Math.clamp(
            wrapDegrees(targetYaw - modelYawDegrees),
            -settings.proceduralHeadTrackingMaxYaw(),
            settings.proceduralHeadTrackingMaxYaw()),
        Math.clamp(
            targetPitch,
            -settings.proceduralHeadTrackingMaxPitch(),
            settings.proceduralHeadTrackingMaxPitch()));
  }

  private Player trackingTarget(long nowNanos, World world, Location source) {
    double range = settings.proceduralHeadTrackingRange();
    double maximumDistanceSquared = range * range;
    boolean currentTargetValid =
        trackingTarget != null
            && trackingTarget.isValid()
            && !trackingTarget.isDead()
            && trackingTarget.getGameMode() != GameMode.SPECTATOR
            && trackingTarget.getWorld() == world
            && source.distanceSquared(trackingTarget.getEyeLocation()) <= maximumDistanceSquared;

    if (currentTargetValid && nowNanos < nextTrackingSearchAtNanos) {
      return trackingTarget;
    }

    nextTrackingSearchAtNanos = nowNanos + TRACKING_REFRESH_NANOS;
    Player closest = null;
    double closestDistanceSquared = maximumDistanceSquared;

    for (Player player : world.getPlayers()) {
      if (!player.isValid() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
        continue;
      }

      double distanceSquared = source.distanceSquared(player.getEyeLocation());
      if (distanceSquared <= closestDistanceSquared) {
        closest = player;
        closestDistanceSquared = distanceSquared;
      }
    }

    trackingTarget = closest;
    return closest;
  }

  private Location trackingSource(Location modelLocation) {
    Location source = modelLocation.clone();
    if (baseEntity != null) {
      source.add(0, baseEntity.getEyeHeight(), 0);
      return source;
    }

    source.add(0, Math.max(1.0, blueprint.modelSizeBlocks().y() * 0.75), 0);
    return source;
  }

  private double ageSeconds(long nowNanos) {
    return (nowNanos - createdAtNanos) / NANOS_PER_SECOND;
  }

  private static double stableSeed(int id, Location location) {
    double value =
        id * 37.719 + location.getX() * 3.13 + location.getY() * 5.17 + location.getZ() * 7.19;
    return stableUnit(value);
  }

  private static double stableUnit(double value) {
    double sine = Math.sin(value * 12.9898) * 43_758.5453;
    return sine - Math.floor(sine);
  }

  private static double positiveModulo(double value, double divisor) {
    double result = value % divisor;
    return result < 0 ? result + divisor : result;
  }

  private static double wrapDegrees(double degrees) {
    double wrapped = degrees % 360.0;
    if (wrapped >= 180.0) {
      wrapped -= 360.0;
    } else if (wrapped < -180.0) {
      wrapped += 360.0;
    }

    return wrapped;
  }

  private record Kinematics(double turnRateDegrees, double acceleration) {}

  private record IdleVariant(int variant, double amount, double wave) {
    private static final IdleVariant NONE = new IdleVariant(-1, 0, 0);
  }

  private record LookAngles(double yaw, double pitch) {
    private static final LookAngles ZERO = new LookAngles(0, 0);
  }

  private static final class SpringState {
    private Vec3 value = Vec3.ZERO;
    private Vec3 velocity = Vec3.ZERO;

    private Vec3 update(Vec3 target, double stiffness, double damping) {
      double safeStiffness = Math.max(0, stiffness);
      double safeDamping = Math.clamp(damping, 0, 0.98);
      velocity = velocity.add(target.subtract(value).multiply(safeStiffness)).multiply(safeDamping);
      value = value.add(velocity);
      return value;
    }
  }
}
