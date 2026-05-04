package osmium.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record Animation(
    String name, double length, boolean loop, Map<String, BoneTimeline> timelines) {
  private static final double MIN_LENGTH_SECONDS = 0.05;

  public Animation {
    length = Math.max(length, MIN_LENGTH_SECONDS);
    timelines = Collections.unmodifiableMap(new LinkedHashMap<>(timelines));
  }

  public double normalize(double elapsedSeconds) {
    if (loop) {
      return elapsedSeconds % length;
    }

    return Math.min(elapsedSeconds, length);
  }
}
