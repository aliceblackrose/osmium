# Compiled animation engine

Osmium's animation runtime follows the same high-level architecture used by modern display-model engines such as BetterModel: authored Blockbench curves are evaluated before playback into a shared timeline of runtime frames, and playback only advances prepared poses.

## Pipeline

1. Import Blockbench keyframes and preserve interpolation metadata.
2. Quantize authored times to the packet renderer's 25 ms transport cadence.
3. Build one shared frame timeline across every animated bone and channel.
4. Insert explicit hold frames immediately before `step` transitions.
5. Densify intervals whose accumulated parent/child rotation exceeds 90 degrees, using every 25 ms transport slot available.
6. Evaluate position, rotation, and scale curves once for each compiled frame.
7. Cache the compiled animation for subsequent plays on the runtime model.
8. During playback, send a new local display transformation only when the compiled target frame changes, model yaw changes, or a new viewer begins tracking the model.
9. Set the display interpolation duration from the actual compiled transition duration. Minecraft still expresses this duration in 50 ms client ticks, so 25 ms transport intervals round upward to the minimum one-tick interpolation duration. Step targets force interpolation duration to zero.
10. Root/entity motion remains a normal Bukkit teleport at 20 TPS so Bukkit entity lifecycle and world access stay on the server thread.

## Why the engine is compiled

The old engine searched keyframes and evaluated interpolation curves every server tick, then rewrote every display transformation every tick. That coupled animation evaluation, entity movement, and Minecraft's client interpolation into one feedback loop.

The compiled engine separates those responsibilities. Curve semantics belong to compilation; frame scheduling belongs to playback; entity translation belongs to root transport; and Minecraft receives discrete target poses with explicit durations.

## 40 Hz direct packet transport

The animation transport is intentionally split from Bukkit's 20 TPS entity tick. A dedicated renderer advances the compiled timeline every 25 ms and sends vanilla `ClientboundSetEntityDataPacket` updates directly through each tracking player's Minecraft connection. Per-model packets are bundled before transmission.

The packet renderer does not call Bukkit APIs and does not mutate the server-side `ItemDisplay` or its `SynchedEntityData`. The main server thread owns spawning, removal, teleports, hitboxes, animation-controller decisions, and viewer discovery. It publishes only cached yaw and tracking snapshots to the renderer. This keeps sub-tick rendering from turning Bukkit entity access into an unsafe asynchronous operation.

Each render part owns an independent packet-side transformation cache. Full matrices are decomposed with Minecraft's own `com.mojang.math.Transformation`, including right rotation when inherited scale introduces a non-trivial decomposition. Quaternion signs are kept on the same hemisphere as the previous packet so mathematically equivalent `q`/`-q` representations cannot create an interpolation flip.

When a viewer starts tracking a model, the renderer force-sends the complete current transformation state. This is necessary because the authoritative Bukkit entity intentionally keeps only an initial transformation baseline; subsequent animation poses exist only in viewer packets.

## Dependencies and version boundary

The runtime transport has no packet-library dependency: no ProtocolLib, PacketEvents, or equivalent abstraction is used. Osmium compiles against Paper's NMS classes through Paper's official `paperweight-userdev` build tooling and sends Mojang-mapped vanilla packets directly.

That makes the transport intentionally Paper-version-sensitive. The current branch targets Paper `26.1.2`; a future Minecraft/Paper protocol change may require adapting the NMS metadata layout or packet constructors even though the compiled animation engine itself remains unchanged.
