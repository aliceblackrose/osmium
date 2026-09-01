package osmium.network;

import com.mojang.math.Transformation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Display;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Direct vanilla-packet transport for sub-tick display animation updates.
 *
 * <p>No protocol abstraction is used here. Bukkit/Paper remains responsible for entity lifecycle
 * and tracking; this class only serializes display transformation metadata and sends it through the
 * player's vanilla server connection.
 */
public final class NmsAnimationPacketTransport {
  private static final float COMPONENT_EPSILON = 1.0E-6F;
  private static final int IMMEDIATE_INTERPOLATION_START_DELTA_TICKS = -1;
  private static final Accessors ACCESSORS = Accessors.discover();

  private NmsAnimationPacketTransport() {}

  /**
   * Captures the connections currently tracking a display. Call this from the server thread only.
   */
  public static ViewerSnapshot snapshotViewers(ItemDisplay display, Set<UUID> hiddenPlayers) {
    Set<UUID> ids = new HashSet<>();
    List<ServerGamePacketListenerImpl> connections = new ArrayList<>();

    for (Player player : display.getTrackedBy()) {
      UUID playerId = player.getUniqueId();
      if (hiddenPlayers.contains(playerId)) {
        continue;
      }

      ids.add(playerId);
      connections.add(((CraftPlayer) player).getHandle().connection);
    }

    return new ViewerSnapshot(Set.copyOf(ids), List.copyOf(connections));
  }

  public static Batch batch() {
    return new Batch();
  }

  public record ViewerSnapshot(
      Set<UUID> playerIds, List<ServerGamePacketListenerImpl> connections) {
    public static final ViewerSnapshot EMPTY = new ViewerSnapshot(Set.of(), List.of());

    public ViewerSnapshot {
      playerIds = Set.copyOf(playerIds);
      connections = List.copyOf(connections);
    }
  }

  /** A per-render-pass bundle of vanilla entity-data packets. */
  public static final class Batch {
    private final List<Packet<? super ClientGamePacketListener>> packets = new ArrayList<>();

    private Batch() {}

    public void add(
        int entityId,
        Matrix4f transform,
        int interpolationDurationTicks,
        boolean force,
        TransformState state) {
      ClientboundSetEntityDataPacket packet =
          state.createPacket(entityId, transform, interpolationDurationTicks, force);
      if (packet != null) {
        packets.add(packet);
      }
    }

    public boolean isEmpty() {
      return packets.isEmpty();
    }

    public void send(ViewerSnapshot viewers) {
      if (packets.isEmpty() || viewers.connections().isEmpty()) {
        return;
      }

      ClientboundBundlePacket bundle = new ClientboundBundlePacket(packets);
      for (ServerGamePacketListenerImpl connection : viewers.connections()) {
        connection.send(bundle);
      }
    }
  }

  /**
   * Packet-side transformation cache. This is intentionally independent from the server entity's
   * SynchedEntityData so the 25 ms renderer never mutates an NMS/Bukkit entity off-thread.
   */
  public static final class TransformState {
    private final Vector3f translation = new Vector3f();
    private final Vector3f scale = new Vector3f(1.0F, 1.0F, 1.0F);
    private final Quaternionf leftRotation = new Quaternionf();
    private final Quaternionf rightRotation = new Quaternionf();
    private boolean initialized;

    private ClientboundSetEntityDataPacket createPacket(
        int entityId, Matrix4f matrix, int interpolationDurationTicks, boolean force) {
      Transformation transformation = new Transformation(new Matrix4f(matrix));
      Vector3f nextTranslation = new Vector3f(transformation.translation());
      Vector3f nextScale = new Vector3f(transformation.scale());
      Quaternionf nextLeft = new Quaternionf(transformation.leftRotation()).normalize();
      Quaternionf nextRight = new Quaternionf(transformation.rightRotation()).normalize();

      if (initialized) {
        keepSameHemisphere(nextLeft, leftRotation);
        keepSameHemisphere(nextRight, rightRotation);
      } else {
        canonicalize(nextLeft);
        canonicalize(nextRight);
      }

      boolean translationChanged = !initialized || !similar(translation, nextTranslation);
      boolean scaleChanged = !initialized || !similar(scale, nextScale);
      boolean leftChanged = !initialized || !similar(leftRotation, nextLeft);
      boolean rightChanged = !initialized || !similar(rightRotation, nextRight);

      if (!force && !translationChanged && !scaleChanged && !leftChanged && !rightChanged) {
        return null;
      }

      List<SynchedEntityData.DataValue<?>> values = new ArrayList<>(6);
      values.add(
          SynchedEntityData.DataValue.create(
              ACCESSORS.interpolationDelay(),
              interpolationStartDeltaTicks(interpolationDurationTicks)));
      values.add(
          SynchedEntityData.DataValue.create(
              ACCESSORS.interpolationDuration(), Math.max(0, interpolationDurationTicks)));

      if (force || translationChanged) {
        values.add(SynchedEntityData.DataValue.create(ACCESSORS.translation(), nextTranslation));
      }
      if (force || scaleChanged) {
        values.add(SynchedEntityData.DataValue.create(ACCESSORS.scale(), nextScale));
      }
      if (force || leftChanged) {
        values.add(SynchedEntityData.DataValue.create(ACCESSORS.leftRotation(), nextLeft));
      }
      if (force || rightChanged) {
        values.add(SynchedEntityData.DataValue.create(ACCESSORS.rightRotation(), nextRight));
      }

      translation.set(nextTranslation);
      scale.set(nextScale);
      leftRotation.set(nextLeft);
      rightRotation.set(nextRight);
      initialized = true;

      return new ClientboundSetEntityDataPacket(entityId, values);
    }
  }

  static int interpolationStartDeltaTicks(int interpolationDurationTicks) {
    return interpolationDurationTicks > 0 ? IMMEDIATE_INTERPOLATION_START_DELTA_TICKS : 0;
  }

  static void keepSameHemisphere(Quaternionf quaternion, Quaternionf previous) {
    if (quaternion.dot(previous) < 0.0F) {
      quaternion.mul(-1.0F);
    }
  }

  private static void canonicalize(Quaternionf quaternion) {
    if (quaternion.w < 0.0F
        || (quaternion.w == 0.0F && quaternion.x < 0.0F)
        || (quaternion.w == 0.0F && quaternion.x == 0.0F && quaternion.y < 0.0F)
        || (quaternion.w == 0.0F
            && quaternion.x == 0.0F
            && quaternion.y == 0.0F
            && quaternion.z < 0.0F)) {
      quaternion.mul(-1.0F);
    }
  }

  private static boolean similar(Vector3f first, Vector3f second) {
    return Math.abs(first.x - second.x) <= COMPONENT_EPSILON
        && Math.abs(first.y - second.y) <= COMPONENT_EPSILON
        && Math.abs(first.z - second.z) <= COMPONENT_EPSILON;
  }

  private static boolean similar(Quaternionf first, Quaternionf second) {
    return Math.abs(first.x - second.x) <= COMPONENT_EPSILON
        && Math.abs(first.y - second.y) <= COMPONENT_EPSILON
        && Math.abs(first.z - second.z) <= COMPONENT_EPSILON
        && Math.abs(first.w - second.w) <= COMPONENT_EPSILON;
  }

  private record Accessors(
      EntityDataAccessor<Integer> interpolationDelay,
      EntityDataAccessor<Integer> interpolationDuration,
      EntityDataAccessor<Vector3f> translation,
      EntityDataAccessor<Vector3f> scale,
      EntityDataAccessor<Quaternionf> leftRotation,
      EntityDataAccessor<Quaternionf> rightRotation) {
    private static Accessors discover() {
      List<EntityDataAccessor<?>> displayAccessors =
          Arrays.stream(Display.class.getDeclaredFields())
              .filter(field -> EntityDataAccessor.class.isAssignableFrom(field.getType()))
              .map(Accessors::readAccessor)
              .sorted(Comparator.comparingInt(EntityDataAccessor::id))
              .toList();

      if (displayAccessors.size() < 7) {
        throw new IllegalStateException(
            "Unsupported Paper display metadata layout: expected at least 7 accessors, found "
                + displayAccessors.size());
      }

      return new Accessors(
          cast(displayAccessors.get(0)),
          cast(displayAccessors.get(1)),
          cast(displayAccessors.get(3)),
          cast(displayAccessors.get(4)),
          cast(displayAccessors.get(5)),
          cast(displayAccessors.get(6)));
    }

    private static EntityDataAccessor<?> readAccessor(Field field) {
      try {
        field.setAccessible(true);
        return (EntityDataAccessor<?>) field.get(null);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException(
            "Unable to resolve display entity-data accessor", exception);
      }
    }

    @SuppressWarnings("unchecked")
    private static <T> EntityDataAccessor<T> cast(EntityDataAccessor<?> accessor) {
      return (EntityDataAccessor<T>) accessor;
    }
  }
}
