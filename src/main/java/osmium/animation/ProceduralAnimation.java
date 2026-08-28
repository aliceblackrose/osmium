package osmium.animation;

import java.util.Locale;
import java.util.Objects;
import osmium.PluginSettings;
import osmium.math.Vec3;
import osmium.model.Bone;

/** Additive runtime animation generated from entity state instead of Blockbench keyframes. */
public final class ProceduralAnimation {
  private static final double TAU = Math.PI * 2.0;
  private static final double IDLE_BREATH_RATE = 0.75;
  private static final double WALK_CYCLE_RATE = 6.0;
  private static final double BODY_BOB_UNITS = 0.22;
  private static final double BODY_SWAY_DEGREES = 1.75;
  private static final double HEAD_IDLE_DEGREES = 1.5;
  private static final double LIMB_SWING_DEGREES = 22.0;
  private static final double LIMB_SIDE_SWAY_DEGREES = 3.0;
  private static final double MAX_EXPECTED_SPEED_BLOCKS = 0.24;
  private static final double ACCELERATION_TO_DEGREES = 150.0;
  private static final double EYE_TRACKING_STRENGTH = 0.45;
  private static final double HEAD_TRACKING_STRENGTH = 1.0;
  private static final double BLINK_EYE_SCALE = 0.12;

  private ProceduralAnimation() {}

  public static BoneTimeline.Sample sample(
      Bone bone, Context context, BoneSpring spring, PluginSettings settings) {
    if (!settings.proceduralAnimationEnabled()) {
      return emptySample();
    }

    String name = bone.name().toLowerCase(Locale.ROOT);
    ProceduralBonePreset preset = settings.proceduralBonePreset(name);
    if (!preset.enabled()) {
      return emptySample();
    }

    double strength = settings.proceduralAnimationStrength();
    double speed = settings.proceduralAnimationSpeed();
    double motion = motionAmount(context.horizontalSpeed());
    Vec3 position = Vec3.ZERO;
    Vec3 rotation = Vec3.ZERO;
    Vec3 scale = Vec3.ONE;

    if (settings.proceduralIdleBreathing()) {
      double breath = Math.sin(context.ageSeconds() * speed * IDLE_BREATH_RATE * TAU) * strength;
      breath *= preset.idleStrength();
      if (isBody(name) || isRoot(name)) {
        position = position.add(new Vec3(0, BODY_BOB_UNITS * breath * (1.0 - motion * 0.35), 0));
        rotation = rotation.add(new Vec3(0, 0, BODY_SWAY_DEGREES * breath * 0.35));
      } else if (isHead(name)) {
        rotation = rotation.add(new Vec3(HEAD_IDLE_DEGREES * breath, 0, 0));
      }
    }

    if (settings.proceduralIdleVariants() && context.idleVariantAmount() > 0) {
      IdleVariantSample variantSample = idleVariantSample(name, context, strength, preset);
      position = position.add(variantSample.position());
      rotation = rotation.add(variantSample.rotation());
    }

    if (settings.proceduralWalkCycle() && motion > 0) {
      double phase = context.ageSeconds() * speed * WALK_CYCLE_RATE;
      double walkStrength = strength * preset.walkStrength();
      double stride = Math.sin(phase) * motion * walkStrength;
      double counterStride = Math.sin(phase + Math.PI) * motion * walkStrength;
      double bob = Math.abs(Math.sin(phase)) * motion * walkStrength;

      if (isRoot(name) || isBody(name)) {
        position = position.add(new Vec3(0, BODY_BOB_UNITS * 0.75 * bob, 0));
        rotation = rotation.add(new Vec3(0, 0, BODY_SWAY_DEGREES * stride));
      } else if (isLeftLeg(name) || isRightArm(name)) {
        rotation =
            rotation.add(new Vec3(LIMB_SWING_DEGREES * stride, 0, LIMB_SIDE_SWAY_DEGREES * stride));
      } else if (isRightLeg(name) || isLeftArm(name)) {
        rotation =
            rotation.add(
                new Vec3(
                    LIMB_SWING_DEGREES * counterStride, 0, LIMB_SIDE_SWAY_DEGREES * counterStride));
      } else if (isHead(name)) {
        rotation = rotation.add(new Vec3(HEAD_IDLE_DEGREES * 0.5 * bob, 0, 0));
      }
    }

    if (settings.proceduralTurnLean() && (isRoot(name) || isBody(name))) {
      double lean =
          clamp(
              -context.turnRateDegrees()
                  * settings.proceduralTurnLeanStrength()
                  * preset.turnLeanStrength(),
              -settings.proceduralTurnLeanMaxDegrees(),
              settings.proceduralTurnLeanMaxDegrees());
      rotation = rotation.add(new Vec3(0, 0, lean * strength));
    }

    if (settings.proceduralMovementLean() && (isRoot(name) || isBody(name))) {
      double lean =
          clamp(
              -context.acceleration()
                  * ACCELERATION_TO_DEGREES
                  * settings.proceduralMovementLeanStrength()
                  * preset.movementLeanStrength(),
              -settings.proceduralMovementLeanMaxDegrees(),
              settings.proceduralMovementLeanMaxDegrees());
      rotation = rotation.add(new Vec3(lean * strength, 0, 0));
    }

    if (settings.proceduralHeadTracking() && isHead(name)) {
      rotation =
          rotation.add(
              new Vec3(context.lookPitchDegrees(), context.lookYawDegrees(), 0)
                  .multiply(strength * HEAD_TRACKING_STRENGTH * preset.trackingStrength()));
    }

    if (settings.proceduralEyeTracking() && isEye(name)) {
      rotation =
          rotation.add(
              new Vec3(context.lookPitchDegrees(), context.lookYawDegrees(), 0)
                  .multiply(strength * EYE_TRACKING_STRENGTH * preset.trackingStrength()));
    }

    if (settings.proceduralBlinking() && context.blinkAmount() > 0) {
      double blink = context.blinkAmount() * strength * preset.blinkStrength();
      if (isEye(name)) {
        double scaleY = Math.max(BLINK_EYE_SCALE, 1.0 - blink * (1.0 - BLINK_EYE_SCALE));
        scale = scale.multiply(new Vec3(1, scaleY, 1));
      } else if (isEyelid(name)) {
        rotation = rotation.add(new Vec3(32.0 * blink, 0, 0));
      }
    }

    if (settings.proceduralSpringBones() && !Objects.equals(spring, BoneSpring.ZERO)) {
      position = position.add(spring.position());
      rotation = rotation.add(spring.rotation().multiply(strength * preset.springStrength()));
    }

    if (settings.proceduralHitFlinch() && context.flinchAmount() > 0) {
      double flinch =
          context.flinchAmount()
              * strength
              * settings.proceduralHitFlinchStrength()
              * preset.flinchStrength();
      double side = context.flinchDirection();
      if (isRoot(name) || isBody(name)) {
        rotation = rotation.add(new Vec3(-7.0 * flinch, 0, 4.0 * side * flinch));
      } else if (isHead(name)) {
        rotation = rotation.add(new Vec3(9.0 * flinch, 3.0 * side * flinch, -5.0 * side * flinch));
      } else if (isLeftArm(name) || isRightArm(name)) {
        rotation = rotation.add(new Vec3(-5.0 * flinch, 0, side * 3.0 * flinch));
      }
    }

    return new BoneTimeline.Sample(position, rotation, scale);
  }

  public static Vec3 springRotationTarget(
      Bone bone, Context context, PluginSettings settings, ProceduralBonePreset preset) {
    String name = bone.name().toLowerCase(Locale.ROOT);
    if (!settings.proceduralSpringBones() || !preset.enabled() || !isSpringBone(name)) {
      return Vec3.ZERO;
    }

    double motion = motionAmount(context.horizontalSpeed());
    double phase = stablePhase(name);
    double idleSway =
        Math.sin(context.ageSeconds() * settings.proceduralAnimationSpeed() * 2.15 + phase)
            * (1.0 - motion * 0.45);
    double turn = clamp(context.turnRateDegrees(), -18.0, 18.0);
    double acceleration = clamp(context.acceleration() * ACCELERATION_TO_DEGREES, -12.0, 12.0);
    double typeStrength = springTypeStrength(name);
    double strength = preset.springStrength() * typeStrength;

    return new Vec3(
        (-acceleration * 0.38 + idleSway * 2.0) * strength,
        (-turn * 1.25 + idleSway * 2.75) * strength,
        (-turn * 0.34 + idleSway * 1.1) * strength);
  }

  public static boolean isSpringBone(String name) {
    return containsAny(
        name.toLowerCase(Locale.ROOT),
        "tail",
        "ear",
        "hair",
        "bang",
        "braid",
        "cloth",
        "cape",
        "wing",
        "antenna",
        "tendril",
        "feather",
        "skirt",
        "ribbon");
  }

  public static BoneTimeline.Sample emptySample() {
    return new BoneTimeline.Sample(Vec3.ZERO, Vec3.ZERO, Vec3.ONE);
  }

  private static IdleVariantSample idleVariantSample(
      String name, Context context, double strength, ProceduralBonePreset preset) {
    double amount = context.idleVariantAmount() * strength * preset.idleStrength();
    double wave = context.idleVariantWave();
    Vec3 position = Vec3.ZERO;
    Vec3 rotation = Vec3.ZERO;

    switch (context.idleVariant()) {
      case 0 -> {
        if (isHead(name)) {
          rotation = rotation.add(new Vec3(-2.0 * amount, 10.0 * amount, 0));
        } else if (isEye(name)) {
          rotation = rotation.add(new Vec3(0, 6.0 * amount, 0));
        }
      }
      case 1 -> {
        if (isRoot(name) || isBody(name)) {
          position = position.add(new Vec3(0.12 * amount, 0, 0));
          rotation = rotation.add(new Vec3(0, 0, 3.5 * amount));
        } else if (isHead(name)) {
          rotation = rotation.add(new Vec3(1.5 * amount, -4.0 * amount, -1.5 * amount));
        }
      }
      case 2 -> {
        if (isRoot(name) || isBody(name)) {
          position = position.add(new Vec3(0, 0.3 * amount, 0));
          rotation = rotation.add(new Vec3(-2.0 * amount, 0, 0));
        } else if (isLeftArm(name) || isRightArm(name)) {
          rotation = rotation.add(new Vec3(5.0 * amount, 0, 3.0 * amount));
        } else if (isHead(name)) {
          rotation = rotation.add(new Vec3(-3.0 * amount, 0, 0));
        }
      }
      case 3 -> {
        if (isEar(name) || isTail(name) || isHair(name)) {
          rotation = rotation.add(new Vec3(0, wave * 6.0 * amount, wave * 8.0 * amount));
        } else if (isHead(name)) {
          rotation = rotation.add(new Vec3(wave * 1.2 * amount, 0, 0));
        }
      }
      default -> {
        // No variant selected.
      }
    }

    return new IdleVariantSample(position, rotation);
  }

  private static double motionAmount(double horizontalSpeed) {
    return clamp(horizontalSpeed / MAX_EXPECTED_SPEED_BLOCKS, 0.0, 1.0);
  }

  private static double stablePhase(String name) {
    int hash = name.hashCode();
    double normalized = Math.abs(hash % 10_000) / 10_000.0;
    return normalized * TAU;
  }

  private static double springTypeStrength(String name) {
    if (isTail(name)) {
      return 1.0;
    }

    if (isHair(name) || name.contains("cloth") || name.contains("cape") || name.contains("skirt")) {
      return 0.65;
    }

    if (isEar(name) || name.contains("feather") || name.contains("ribbon")) {
      return 0.45;
    }

    return 0.55;
  }

  private static boolean isRoot(String name) {
    return name.equals("root") || name.equals("body_root") || name.equals("model_root");
  }

  private static boolean isBody(String name) {
    return containsAny(name, "body", "torso", "chest", "waist", "pelvis", "spine");
  }

  private static boolean isHead(String name) {
    return containsAny(name, "head", "neck", "skull", "face");
  }

  private static boolean isEye(String name) {
    return containsAny(name, "eye", "pupil", "iris", "eyeball") && !isEyelid(name);
  }

  private static boolean isEyelid(String name) {
    return containsAny(name, "eyelid", "lid", "blink");
  }

  private static boolean isEar(String name) {
    return containsAny(name, "ear");
  }

  private static boolean isTail(String name) {
    return containsAny(name, "tail");
  }

  private static boolean isHair(String name) {
    return containsAny(name, "hair", "bang", "braid");
  }

  private static boolean isLeftArm(String name) {
    return containsAny(name, "left_arm", "leftarm", "l_arm", "arm_l", "front_left", "front_l");
  }

  private static boolean isRightArm(String name) {
    return containsAny(name, "right_arm", "rightarm", "r_arm", "arm_r", "front_right", "front_r");
  }

  private static boolean isLeftLeg(String name) {
    return containsAny(name, "left_leg", "leftleg", "l_leg", "leg_l", "back_left", "back_l");
  }

  private static boolean isRightLeg(String name) {
    return containsAny(name, "right_leg", "rightleg", "r_leg", "leg_r", "back_right", "back_r");
  }

  private static boolean containsAny(String value, String... fragments) {
    for (String fragment : fragments) {
      if (value.contains(fragment)) {
        return true;
      }
    }

    return false;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  public record Context(
      double ageSeconds,
      double horizontalSpeed,
      double turnRateDegrees,
      double acceleration,
      double lookYawDegrees,
      double lookPitchDegrees,
      double blinkAmount,
      int idleVariant,
      double idleVariantAmount,
      double idleVariantWave,
      double flinchAmount,
      double flinchDirection) {
    public static final Context EMPTY = new Context(0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 1);
  }

  public record BoneSpring(Vec3 position, Vec3 rotation) {
    public static final BoneSpring ZERO = new BoneSpring(Vec3.ZERO, Vec3.ZERO);
  }

  private record IdleVariantSample(Vec3 position, Vec3 rotation) {}
}
