package osmium.animation;

import java.util.Locale;

/** Playback mode used by Blockbench animations. */
public enum AnimationLoopMode {
  ONCE,
  LOOP,
  HOLD;

  public static AnimationLoopMode parse(String value) {
    if (value == null || value.isBlank()) {
      return ONCE;
    }

    return switch (value.toLowerCase(Locale.ROOT)) {
      case "true", "loop" -> LOOP;
      case "hold" -> HOLD;
      default -> ONCE;
    };
  }
}
