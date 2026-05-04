package osmium.math;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;

/** Immutable three-dimensional vector used for model, animation, and transform math. */
public record Vec3(double x, double y, double z) {
  public static final Vec3 ZERO = new Vec3(0, 0, 0);
  public static final Vec3 ONE = new Vec3(1, 1, 1);

  private static final int X_INDEX = 0;
  private static final int Y_INDEX = 1;
  private static final int Z_INDEX = 2;

  public static Vec3 fromArray(JsonElement element) {
    if (element == null || !element.isJsonArray()) {
      return ZERO;
    }

    JsonArray array = element.getAsJsonArray();
    return new Vec3(
        readArrayDouble(array, X_INDEX),
        readArrayDouble(array, Y_INDEX),
        readArrayDouble(array, Z_INDEX));
  }

  public static Vec3 fromObject(JsonObject object, Vec3 fallback) {
    if (object == null) {
      return fallback;
    }

    return new Vec3(
        readObjectDouble(object, "x", fallback.x()),
        readObjectDouble(object, "y", fallback.y()),
        readObjectDouble(object, "z", fallback.z()));
  }

  public static Vec3 lerp(Vec3 start, Vec3 end, double amount) {
    return new Vec3(
        start.x + (end.x - start.x) * amount,
        start.y + (end.y - start.y) * amount,
        start.z + (end.z - start.z) * amount);
  }

  private static double readArrayDouble(JsonArray array, int index) {
    if (array.size() <= index) {
      return 0;
    }

    return readDouble(array.get(index), 0);
  }

  private static double readObjectDouble(JsonObject object, String key, double fallback) {
    return readDouble(object.get(key), fallback);
  }

  private static double readDouble(JsonElement element, double fallback) {
    if (element == null || element.isJsonNull()) {
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

  public Vec3 add(Vec3 other) {
    return new Vec3(x + other.x, y + other.y, z + other.z);
  }

  public Vec3 subtract(Vec3 other) {
    return new Vec3(x - other.x, y - other.y, z - other.z);
  }

  public Vec3 multiply(double scalar) {
    return new Vec3(x * scalar, y * scalar, z * scalar);
  }

  public Vec3 multiply(Vec3 other) {
    return new Vec3(x * other.x, y * other.y, z * other.z);
  }

  public Vec3 abs() {
    return new Vec3(Math.abs(x), Math.abs(y), Math.abs(z));
  }

  public Vec3 divide(double scalar) {
    if (scalar == 0) {
      return ZERO;
    }

    return new Vec3(x / scalar, y / scalar, z / scalar);
  }

  public Vector3f toVector3f() {
    return new Vector3f((float) x, (float) y, (float) z);
  }

  @Override
  public @NonNull String toString() {
    return "[" + x + ", " + y + ", " + z + "]";
  }
}
