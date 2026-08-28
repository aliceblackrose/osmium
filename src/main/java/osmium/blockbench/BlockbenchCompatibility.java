package osmium.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import osmium.util.Jsons;

/** Applies the compatibility migrations Blockbench performs when opening older bbmodel files. */
final class BlockbenchCompatibility {
  private static final int FORMAT_3_2_MAJOR = 3;
  private static final int FORMAT_3_2_MINOR = 2;
  private static final int FORMAT_5_0_MAJOR = 5;
  private static final int FORMAT_5_0_MINOR = 0;

  private BlockbenchCompatibility() {}

  static void normalize(JsonObject root) {
    inlineSeparateGroupMetadata(root);

    String version = formatVersion(root);
    if (version.isBlank()) {
      return;
    }

    if (olderThan(version, FORMAT_3_2_MAJOR, FORMAT_3_2_MINOR)) {
      normalizeLegacyOutlinerRotations(Jsons.array(root, "outliner"));
    }

    if (olderThan(version, FORMAT_5_0_MAJOR, FORMAT_5_0_MINOR)) {
      normalizeLegacyAnimationCoordinates(Jsons.array(root, "animations"));
    }
  }

  private static void inlineSeparateGroupMetadata(JsonObject root) {
    Map<String, JsonObject> groupsByUuid = new LinkedHashMap<>();

    for (JsonElement groupElement : Jsons.array(root, "groups")) {
      if (!groupElement.isJsonObject()) {
        continue;
      }

      JsonObject group = groupElement.getAsJsonObject();
      String uuid = Jsons.string(group, "uuid", "");
      if (!uuid.isBlank()) {
        groupsByUuid.put(uuid, group);
      }
    }

    if (groupsByUuid.isEmpty()) {
      return;
    }

    inlineOutlinerGroups(Jsons.array(root, "outliner"), groupsByUuid);
  }

  private static void inlineOutlinerGroups(
      JsonArray children, Map<String, JsonObject> groupsByUuid) {
    for (JsonElement child : children) {
      if (!child.isJsonObject()) {
        continue;
      }

      JsonObject outlinerGroup = child.getAsJsonObject();
      String uuid = Jsons.string(outlinerGroup, "uuid", "");
      JsonObject group = groupsByUuid.get(uuid);

      if (group != null) {
        for (Map.Entry<String, JsonElement> entry : group.entrySet()) {
          if (!entry.getKey().equals("children")) {
            outlinerGroup.add(entry.getKey(), entry.getValue().deepCopy());
          }
        }
      }

      inlineOutlinerGroups(Jsons.array(outlinerGroup, "children"), groupsByUuid);
    }
  }

  private static String formatVersion(JsonObject root) {
    JsonObject meta = Jsons.object(root, "meta");
    String formatVersion = Jsons.string(meta, "format_version", "");
    return formatVersion.isBlank() ? Jsons.string(meta, "format", "") : formatVersion;
  }

  private static boolean olderThan(String version, int targetMajor, int targetMinor) {
    String[] parts = version.split("\\.", -1);
    int major = parts.length > 0 ? versionPart(parts[0]) : 0;
    int minor = parts.length > 1 ? versionPart(parts[1]) : 0;
    return major < targetMajor || (major == targetMajor && minor < targetMinor);
  }

  private static int versionPart(String value) {
    int end = 0;
    while (end < value.length() && Character.isDigit(value.charAt(end))) {
      end++;
    }

    if (end == 0) {
      return 0;
    }

    try {
      return Integer.parseInt(value.substring(0, end));
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private static void normalizeLegacyOutlinerRotations(JsonArray children) {
    for (JsonElement child : children) {
      if (!child.isJsonObject()) {
        continue;
      }

      JsonObject group = child.getAsJsonObject();
      negateArrayCoordinate(group.get("rotation"), 2);
      normalizeLegacyOutlinerRotations(Jsons.array(group, "children"));
    }
  }

  private static void normalizeLegacyAnimationCoordinates(JsonArray animations) {
    for (JsonElement animationElement : animations) {
      if (!animationElement.isJsonObject()) {
        continue;
      }

      JsonObject animators = Jsons.object(animationElement.getAsJsonObject(), "animators");
      for (Map.Entry<String, JsonElement> entry : animators.entrySet()) {
        if (!entry.getValue().isJsonObject()) {
          continue;
        }

        for (JsonElement keyframeElement :
            Jsons.array(entry.getValue().getAsJsonObject(), "keyframes")) {
          if (keyframeElement.isJsonObject()) {
            normalizeLegacyKeyframe(keyframeElement.getAsJsonObject());
          }
        }
      }
    }
  }

  private static void normalizeLegacyKeyframe(JsonObject keyframe) {
    String channel = Jsons.string(keyframe, "channel", "").toLowerCase(Locale.ROOT);
    boolean positionOrRotation = channel.equals("position") || channel.equals("rotation");
    if (!positionOrRotation) {
      return;
    }

    for (JsonElement dataPointElement : Jsons.array(keyframe, "data_points")) {
      if (!dataPointElement.isJsonObject()) {
        continue;
      }

      JsonObject dataPoint = dataPointElement.getAsJsonObject();
      negateObjectCoordinate(dataPoint, "x");
      if (channel.equals("rotation")) {
        negateObjectCoordinate(dataPoint, "y");
      }
    }

    if (!"bezier".equalsIgnoreCase(Jsons.string(keyframe, "interpolation", ""))) {
      return;
    }

    negateBezierValues(keyframe, "bezier_left_value", channel.equals("rotation"));
    negateBezierValues(keyframe, "bezier_right_value", channel.equals("rotation"));
  }

  private static void negateBezierValues(JsonObject keyframe, String key, boolean negateY) {
    JsonElement values = keyframe.get(key);
    negateArrayCoordinate(values, 0);
    if (negateY) {
      negateArrayCoordinate(values, 1);
    }
  }

  private static void negateObjectCoordinate(JsonObject object, String key) {
    JsonElement value = object.get(key);
    Double number = numericValue(value);
    if (number != null) {
      object.addProperty(key, -number);
    }
  }

  private static void negateArrayCoordinate(JsonElement element, int index) {
    if (element == null || !element.isJsonArray()) {
      return;
    }

    JsonArray array = element.getAsJsonArray();
    if (array.size() <= index) {
      return;
    }

    Double number = numericValue(array.get(index));
    if (number != null) {
      array.set(index, new com.google.gson.JsonPrimitive(-number));
    }
  }

  private static Double numericValue(JsonElement element) {
    if (element == null || element.isJsonNull()) {
      return null;
    }

    try {
      return element.getAsDouble();
    } catch (IllegalStateException
        | NumberFormatException
        | UnsupportedOperationException
        | ClassCastException exception) {
      return null;
    }
  }
}
