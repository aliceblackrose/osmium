package osmium.animation;

import java.util.Locale;

public enum Interpolation {
  LINEAR,
  STEP,
  SMOOTH;

  public static Interpolation parse(String raw) {
    if (raw == null || raw.isBlank()) {
      return LINEAR;
    }

    return switch (raw.toLowerCase(Locale.ROOT)) {
      case "step", "constant" -> STEP;
      case "catmullrom", "bezier" -> SMOOTH;
      default -> LINEAR;
    };
  }
}
