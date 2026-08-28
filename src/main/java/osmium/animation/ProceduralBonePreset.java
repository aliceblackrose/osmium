package osmium.animation;

import java.util.Locale;

/** Per-bone multipliers for additive procedural animation. */
public record ProceduralBonePreset(
    String id,
    String match,
    boolean enabled,
    double idleStrength,
    double walkStrength,
    double trackingStrength,
    double blinkStrength,
    double springStrength,
    double turnLeanStrength,
    double movementLeanStrength,
    double flinchStrength,
    double springStiffness,
    double springDamping) {
  public static final ProceduralBonePreset DEFAULT =
      new ProceduralBonePreset("default", "", true, 1, 1, 1, 1, 1, 1, 1, 1, -1, -1);

  public boolean matches(String boneName) {
    if (match == null || match.isBlank()) {
      return false;
    }

    String lowerBoneName = boneName.toLowerCase(Locale.ROOT);
    for (String fragment : match.toLowerCase(Locale.ROOT).split("[,|;]")) {
      String trimmed = fragment.trim();
      if (!trimmed.isEmpty() && lowerBoneName.contains(trimmed)) {
        return true;
      }
    }

    return false;
  }

  public double effectiveSpringStiffness(double fallback) {
    return springStiffness > 0 ? springStiffness : fallback;
  }

  public double effectiveSpringDamping(double fallback) {
    return springDamping > 0 ? springDamping : fallback;
  }
}
