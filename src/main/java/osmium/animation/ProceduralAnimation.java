package osmium.animation;

import java.util.Locale;
import java.util.Objects;
import osmium.PluginSettings;
import osmium.math.Vec3;
import osmium.model.Bone;

/** Primary runtime pose generation driven by entity state instead of locomotion keyframes. */
public final class ProceduralAnimation {
  private static final double TAU = Math.PI * 2.0;
  private static final double IDLE_BREATH_RATE = 0.75;
  private static final double BODY_BOB_UNITS = 0.34;
  private static final double BODY_SWAY_DEGREES = 2.4;
  private static final double BODY_TWIST_DEGREES = 2.0;
  private static final double HEAD_IDLE_DEGREES = 1.6;
  private static final double LEG_SWING_DEGREES = 27.0;
  private static final double ARM_SWING_DEGREES = 21.0;
  private static final double KNEE_BEND_DEGREES = 22.0;
  private static final double FOOT_FLEX_DEGREES = 9.0;
  private static final double LIMB_SIDE_SWAY_DEGREES = 2.5;
  private static final double ACCELERATION_TO_DEGREES = 150.0;
  private static final double EYE_TRACKING_STRENGTH = 0.45;
  private static final double HEAD_TRACKING_STRENGTH = 1.0;
  private static final double BLINK_EYE_SCALE = 0.12;

  private ProceduralAnimation() {}

  /** Returns the complete procedural delta pose for a bone relative to its bind pose. */
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
    double motion = context.motionAmount();
    double idle = 1.0 - motion;
    Vec3 position = Vec3.ZERO;
    Vec3 rotation = Vec3.ZERO;
    Vec3 scale = Vec3.ONE;

    if (settings.proceduralIdleBreathing()) {
      double breath =
          Math.sin(context.ageSeconds() * speed * IDLE_BREATH_RATE * TAU)
              * strength
              * preset.idleStrength()
              * (0.25 + idle * 0.75);
      if (isBody(name) || isRoot(name)) {
        position = position.add(new Vec3(0, BODY_BOB_UNITS * 0.45 * breath, 0));
        rotation = rotation.add(new Vec3(0, 0, BODY_SWAY_DEGREES * 0.22 * breath));
      } else if (isHead(name)) {
        rotation = rotation.add(new Vec3(HEAD_IDLE_DEGREES * breath, 0, 0));
      }
    }

    if (settings.proceduralIdleVariants() && context.idleVariantAmount() > 0) {
      IdleVariantSample variantSample = idleVariantSample(name, context, strength, preset);
      position = position.add(variantSample.position());
      rotation = rotation.add(variantSample.rotation());
    }

    if (settings.proceduralWalkCycle() && motion > 0.001) {
      BoneTimeline.Sample locomotion =
          context.grounded()
              ? groundedLocomotion(name, context, strength, preset)
              : airborneLocomotion(name, context, strength, preset);
      position = position.add(locomotion.position());
      rotation = rotation.add(locomotion.rotation());
      scale = scale.multiply(locomotion.scale());
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

  private static BoneTimeline.Sample groundedLocomotion(
      String name, Context context, double strength, ProceduralBonePreset preset) {
    double phase = context.gaitPhase();
    double motion = context.motionAmount();
    double walkStrength = strength * preset.walkStrength() * motion;
    double stride = Math.sin(phase);
    double oppositeStride = -stride;
    double doublePhase = Math.sin(phase * 2.0);
    double verticalCycle = -Math.cos(phase * 2.0);
    double leftPlant = Math.max(0, -stride);
    double rightPlant = Math.max(0, stride);
    Vec3 position = Vec3.ZERO;
    Vec3 rotation = Vec3.ZERO;

    if (isRoot(name)) {
      position = position.add(new Vec3(0, BODY_BOB_UNITS * 0.55 * verticalCycle * walkStrength, 0));
      rotation =
          rotation.add(
              new Vec3(
                  0,
                  BODY_TWIST_DEGREES * doublePhase * walkStrength,
                  BODY_SWAY_DEGREES * stride * walkStrength));
    } else if (isBody(name)) {
      position = position.add(new Vec3(0, BODY_BOB_UNITS * 0.35 * verticalCycle * walkStrength, 0));
      rotation =
          rotation.add(
              new Vec3(
                  1.5 * motion * walkStrength,
                  -BODY_TWIST_DEGREES * 0.7 * doublePhase * walkStrength,
                  BODY_SWAY_DEGREES * 0.55 * stride * walkStrength));
    } else if (isLeftLowerLeg(name)) {
      rotation = rotation.add(new Vec3(KNEE_BEND_DEGREES * leftPlant * walkStrength, 0, 0));
    } else if (isRightLowerLeg(name)) {
      rotation = rotation.add(new Vec3(KNEE_BEND_DEGREES * rightPlant * walkStrength, 0, 0));
    } else if (isLeftFoot(name)) {
      rotation = rotation.add(new Vec3(-FOOT_FLEX_DEGREES * stride * walkStrength, 0, 0));
    } else if (isRightFoot(name)) {
      rotation = rotation.add(new Vec3(-FOOT_FLEX_DEGREES * oppositeStride * walkStrength, 0, 0));
    } else if (isLeftLeg(name)) {
      rotation =
          rotation.add(
              new Vec3(
                  LEG_SWING_DEGREES * stride * walkStrength,
                  0,
                  LIMB_SIDE_SWAY_DEGREES * stride * walkStrength));
    } else if (isRightLeg(name)) {
      rotation =
          rotation.add(
              new Vec3(
                  LEG_SWING_DEGREES * oppositeStride * walkStrength,
                  0,
                  LIMB_SIDE_SWAY_DEGREES * oppositeStride * walkStrength));
    } else if (isLeftForearm(name)) {
      rotation = rotation.add(new Vec3(4.0 * rightPlant * walkStrength, 0, 0));
    } else if (isRightForearm(name)) {
      rotation = rotation.add(new Vec3(4.0 * leftPlant * walkStrength, 0, 0));
    } else if (isLeftArm(name)) {
      rotation =
          rotation.add(
              new Vec3(
                  ARM_SWING_DEGREES * oppositeStride * walkStrength,
                  0,
                  LIMB_SIDE_SWAY_DEGREES * 0.7 * oppositeStride * walkStrength));
    } else if (isRightArm(name)) {
      rotation =
          rotation.add(
              new Vec3(
                  ARM_SWING_DEGREES * stride * walkStrength,
                  0,
                  LIMB_SIDE_SWAY_DEGREES * 0.7 * stride * walkStrength));
    } else if (isHead(name)) {
      rotation =
          rotation.add(
              new Vec3(
                  -HEAD_IDLE_DEGREES * 0.45 * verticalCycle * walkStrength,
                  -BODY_TWIST_DEGREES * 0.35 * doublePhase * walkStrength,
                  0));
    }

    return new BoneTimeline.Sample(position, rotation, Vec3.ONE);
  }

  private static BoneTimeline.Sample airborneLocomotion(
      String name, Context context, double strength, ProceduralBonePreset preset) {
    double amount = context.motionAmount() * strength * preset.walkStrength();
    double vertical = clamp(context.verticalSpeed(), -0.7, 0.7);
    Vec3 rotation = Vec3.ZERO;

    if (isRoot(name) || isBody(name)) {
      rotation = rotation.add(new Vec3(clamp(-vertical * 16.0, -9.0, 9.0) * amount, 0, 0));
    } else if (isLeftLeg(name) || isRightLeg(name)) {
      rotation = rotation.add(new Vec3(-9.0 * amount, 0, 0));
    } else if (isLeftLowerLeg(name) || isRightLowerLeg(name)) {
      rotation = rotation.add(new Vec3(16.0 * amount, 0, 0));
    } else if (isLeftArm(name) || isRightArm(name)) {
      rotation = rotation.add(new Vec3(7.0 * amount, 0, 0));
    } else if (isHead(name)) {
      rotation = rotation.add(new Vec3(clamp(vertical * 7.0, -4.0, 4.0) * amount, 0, 0));
    }

    return new BoneTimeline.Sample(Vec3.ZERO, rotation, Vec3.ONE);
  }

  public static Vec3 springRotationTarget(
      Bone bone, Context context, PluginSettings settings, ProceduralBonePreset preset) {
    String name = bone.name().toLowerCase(Locale.ROOT);
    if (!settings.proceduralSpringBones() || !preset.enabled() || !isSpringBone(name)) {
      return Vec3.ZERO;
    }

    double motion = context.motionAmount();
    double phase = stablePhase(name);
    double idleSway =
        Math.sin(context.ageSeconds() * settings.proceduralAnimationSpeed() * 2.15 + phase)
            * (1.0 - motion * 0.45);
    double turn = clamp(context.turnRateDegrees(), -18.0, 18.0);
    double acceleration = clamp(context.acceleration() * ACCELERATION_TO_DEGREES, -12.0, 12.0);
    double vertical = clamp(context.verticalSpeed() * 18.0, -8.0, 8.0);
    double typeStrength = springTypeStrength(name);
    double strength = preset.springStrength() * typeStrength;

    return new Vec3(
        (-acceleration * 0.38 - vertical * 0.25 + idleSway * 2.0) * strength,
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
    double amount =
        context.idleVariantAmount()
            * strength
            * preset.idleStrength()
            * (1.0 - context.motionAmount());
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
    return containsAny(name, "body", "torso", "chest", "waist", "pelvis", "spine", "hip");
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

  private static boolean isLeftForearm(String name) {
    return containsAny(name, "left_forearm", "leftforearm", "forearm_left", "forearm_l");
  }

  private static boolean isRightForearm(String name) {
    return containsAny(name, "right_forearm", "rightforearm", "forearm_right", "forearm_r");
  }

  private static boolean isLeftArm(String name) {
    return containsAny(name, "left_arm", "leftarm", "l_arm", "arm_l", "front_left", "front_l");
  }

  private static boolean isRightArm(String name) {
    return containsAny(name, "right_arm", "rightarm", "r_arm", "arm_r", "front_right", "front_r");
  }

  private static boolean isLeftLowerLeg(String name) {
    return containsAny(name, "left_shin", "left_calf", "shin_left", "calf_left", "lower_leg_left");
  }

  private static boolean isRightLowerLeg(String name) {
    return containsAny(name, "right_shin", "right_calf", "shin_right", "calf_right", "lower_leg_right");
  }

  private static boolean isLeftFoot(String name) {
    return containsAny(name, "left_foot", "leftfoot", "foot_left", "foot_l");
  }

  private static boolean isRightFoot(String name) {
    return containsAny(name, "right_foot", "rightfoot", "foot_right", "foot_r");
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
      double motionAmount,
      double gaitPhase,
      double verticalSpeed,
      boolean grounded,
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
    public static final Context EMPTY =
        new Context(0, 0, 0, 0, 0, true, 0, 0, 0, 0, 0, -1, 0, 0, 0, 1);
  }

  public record BoneSpring(Vec3 position, Vec3 rotation) {
    public static final BoneSpring ZERO = new BoneSpring(Vec3.ZERO, Vec3.ZERO);
  }

  private record IdleVariantSample(Vec3 position, Vec3 rotation) {}
}
