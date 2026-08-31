# Compiled animation engine

Osmium's animation runtime follows the same high-level architecture used by modern display-model engines such as BetterModel: authored Blockbench curves are evaluated before playback into a shared timeline of runtime frames, and playback only advances prepared poses.

## Pipeline

1. Import Blockbench keyframes and preserve interpolation metadata.
2. Quantize authored times to Osmium's current 20 TPS Bukkit transport cadence.
3. Build one shared frame timeline across every animated bone and channel.
4. Insert explicit hold frames before `step` transitions.
5. Densify intervals whose accumulated parent/child rotation exceeds 90 degrees, using every server-tick transport slot available.
6. Evaluate position, rotation, and scale curves once for each compiled frame.
7. Cache the compiled animation for subsequent plays on the runtime model.
8. During playback, send a new local display transformation only when the compiled target frame changes (or model yaw changes).
9. Set the display interpolation duration from the actual compiled transition duration. Step targets force interpolation duration to zero.
10. Root/entity motion is teleported separately so ordinary movement does not restart local bone interpolation.

## Why the engine is compiled

The old engine searched keyframes and evaluated interpolation curves every server tick, then rewrote every display transformation every tick. That coupled animation evaluation, entity movement, and Minecraft's client interpolation into one feedback loop.

The compiled engine separates those responsibilities. Curve semantics belong to compilation; frame scheduling belongs to playback; entity translation belongs to root transport; and Minecraft receives discrete target poses with explicit durations.

## Transport resolution

BetterModel can run a packet tracker at 25 ms. Osmium currently uses Bukkit display entities on the Minecraft server tick, so its safe transport resolution is 50 ms. The compiler therefore quantizes animation targets to 20 TPS rather than creating undeliverable sub-tick targets. A future packet/NMS backend can lower the compiler quantum without changing authored animation semantics or playback architecture.
