package osmium.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Small helpers for reading optional Gson values with safe defaults. */
public final class Jsons {
  private Jsons() {}

  public static JsonObject object(JsonObject object, String key) {
    JsonElement element = object.get(key);
    if (element == null || !element.isJsonObject()) {
      return new JsonObject();
    }

    return element.getAsJsonObject();
  }

  public static JsonArray array(JsonObject object, String key) {
    JsonElement element = object.get(key);
    if (element == null || !element.isJsonArray()) {
      return new JsonArray();
    }

    return element.getAsJsonArray();
  }

  public static String string(JsonObject object, String key, String fallback) {
    JsonElement element = value(object, key);
    if (element == null) {
      return fallback;
    }

    try {
      return element.getAsString();
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException exception) {
      return fallback;
    }
  }

  public static int integer(JsonObject object, String key, int fallback) {
    JsonElement element = value(object, key);
    if (element == null) {
      return fallback;
    }

    try {
      return element.getAsInt();
    } catch (IllegalStateException
        | NumberFormatException
        | UnsupportedOperationException
        | ClassCastException exception) {
      return fallback;
    }
  }

  public static double dbl(JsonObject object, String key, double fallback) {
    JsonElement element = value(object, key);
    if (element == null) {
      return fallback;
    }

    try {
      return element.getAsDouble();
    } catch (IllegalStateException
        | NumberFormatException
        | UnsupportedOperationException
        | ClassCastException exception) {
      return fallback;
    }
  }

  public static boolean bool(JsonObject object, String key, boolean fallback) {
    JsonElement element = value(object, key);
    if (element == null) {
      return fallback;
    }

    try {
      return element.getAsBoolean();
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException exception) {
      return fallback;
    }
  }

  private static JsonElement value(JsonObject object, String key) {
    JsonElement element = object.get(key);
    return element == null || element.isJsonNull() ? null : element;
  }
}
