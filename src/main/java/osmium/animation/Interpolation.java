package osmium.animation;

import java.util.Locale;

public enum Interpolation {
  LINEAR,
  STEP,
  CATMULL_ROM,
  BEZIER,
  /** Legacy Osmium smoothstep mode retained for source compatibility. */
  SMOOTH;

  public static Interpolation parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return LINEAR;
    }

    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "step", "constant" -> STEP;
      case "catmullrom", "catmull_rom", "catmull-rom", "smooth" -> CATMULL_ROM;
      case "bezier", "bézier" -> BEZIER;
      default -> LINEAR;
    };
  }
}
