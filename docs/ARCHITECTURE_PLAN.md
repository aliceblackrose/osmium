# Osmium target architecture plan

This document expands the roadmap into a proposed target architecture. It is intentionally more concrete than the roadmap, but package names and type names are still provisional until implementation proves the boundaries.

## Current architectural pressure points

Osmium already has useful package separation, but several runtime responsibilities converge in a small number of implementation classes:

- `RuntimeModel` owns lifecycle, Bukkit entity setup, animation semantics, pose evaluation, hitboxes, viewer tracking, visibility, and packet rendering.
- `ResourcePackGenerator` creates `RenderPart` objects as a side effect of writing the pack and mutates `ModelBlueprint` to publish them to runtime code.
- `ModelCommand` performs parsing and contains a meaningful amount of application logic.
- direct NMS display metadata handling is correctly isolated in one class today, but runtime code depends on that concrete implementation rather than an abstraction.
- reload performs import and optional pack generation synchronously before the runtime/model state boundary is fully explicit.

The target architecture should separate definition data, compiled data, runtime state, Paper integration, and NMS transport.

## Architectural layers

```text
+------------------------------------------------------------+
|                        Osmium API                          |
|  model lookup | spawn | model instances | animation ops   |
+-------------------------------+----------------------------+
                                |
+-------------------------------v----------------------------+
|                    Application services                    |
| model repository | reload | spawn service | pack service  |
+-----------+-------------------+----------------------------+
            |                   |
            |                   +-------------------------+
            v                                             v
+-------------------------+                    +-------------------------+
| Model/compiler domain   |                    | Runtime domain          |
| Blockbench import       |                    | lifecycle               |
| ModelBlueprint          |                    | animation controller    |
| ModelCompiler           |                    | pose evaluator          |
| CompiledModel           |                    | viewer/hitbox state     |
+------------+------------+                    +------------+------------+
             |                                              |
             v                                              v
+-------------------------+                    +-------------------------+
| Resource pack output    |                    | Paper rendering         |
| JSON/models/textures    |                    | displays/interactions   |
| deterministic pack zip  |                    | root teleports          |
+-------------------------+                    +------------+------------+
                                                          |
                                                          v
                                             +-------------------------+
                                             | Packet transport        |
                                             | interface + NMS impl    |
                                             +-------------------------+
```

Dependencies should point downward. The public API must not depend on internal NMS transport types.

## 1. Model definition layer

### `ModelBlueprint`

Represents imported authoring data.

It should contain data that originates from Blockbench or can be deterministically derived from that authored structure without runtime/resource-pack allocation.

Candidate contents:

- model id,
- source path,
- bone hierarchy,
- cubes,
- textures,
- authored animations,
- source-space/model-space bounds,
- semantic metadata that is genuinely model definition data.

It should not contain:

- Bukkit entities,
- packet state,
- viewer state,
- runtime IDs,
- generated custom model data allocations,
- mutable `RenderPart` collections populated by resource-pack generation.

Prefer defensive copies or immutable collections at construction time.

### Import boundary

`BlockbenchImporter` should remain responsible for converting the external `.bbmodel` format into Osmium domain objects. Compatibility normalization belongs directly before or during import.

The importer should not know how runtime displays are spawned or how a resource pack is laid out.

## 2. Compilation layer

### Why a separate compiler exists

Blockbench data is not yet the exact representation needed by Minecraft rendering. Osmium must derive stable render parts, model keys, and other runtime/resource-pack metadata.

That derivation should happen once in an explicit compiler stage instead of as a side effect of writing files.

### Proposed `CompiledModel`

One possible shape:

```java
public record CompiledModel(
    ModelBlueprint blueprint,
    List<CompiledRenderPart> renderParts,
    List<CompiledHitbox> hitboxes,
    Map<String, CompiledAnimation> animations,
    ModelBounds bounds
) {}
```

Whether animations live here or remain cached independently should be decided based on memory/performance behavior. The critical part is the separation between authored model state and runtime-ready render layout.

### `CompiledRenderPart`

A compiled render part should include stable data shared by every runtime instance, for example:

```java
public record CompiledRenderPart(
    String name,
    String itemModelKey,
    String modelPath,
    int customModelData,
    Bone bone,
    Cube cube,
    Matrix4f localTransform
) {}
```

The exact type should avoid mutable matrices if the object is exposed across threads; a constructor may precompute immutable numeric data and let runtime instances create their own mutable JOML working matrices.

### Compilation context

If custom model data must be globally unique across all loaded models, compilation should operate on a model set/snapshot rather than compiling models independently without context.

For example:

```java
public interface ModelCompiler {
    ModelSnapshot compile(Collection<ModelBlueprint> blueprints, CompileOptions options);
}
```

This can deterministically assign values in sorted model/part order.

### Determinism

Given identical inputs and settings, compilation should produce identical:

- custom model data,
- namespaced item keys,
- model paths,
- resource-pack JSON,
- manifest entries.

Determinism is important for cacheability, debugging, and avoiding client pack churn.

## 3. Model repository and snapshots

### `ModelSnapshot`

Model loading should eventually publish one immutable registry snapshot:

```java
public record ModelSnapshot(
    Map<String, CompiledModel> models,
    String contentHash
) {}
```

`ModelManager` or a renamed `ModelRepository` can hold:

```java
private volatile ModelSnapshot snapshot = ModelSnapshot.empty();
```

A reload builds the entire next snapshot separately. Only after import, compile, and required pack work succeeds does the server publish it.

This avoids partially replacing individual models while other work is still failing.

## 4. Runtime factory

Constructing a runtime model currently performs substantial work in the `RuntimeModel` constructor. Prefer a factory so construction can be validated and component assembly is not hidden in one large constructor.

```java
final class RuntimeModelFactory {
    RuntimeModel create(RuntimeSpawnRequest request) {
        // validate world/model
        // create render instances
        // create controller
        // create hitboxes
        // create viewer tracker
        // assemble RuntimeModel
    }
}
```

A spawn request can carry the immutable inputs:

```java
record RuntimeSpawnRequest(
    int runtimeId,
    CompiledModel model,
    Location origin,
    LivingEntity baseEntity,
    SpawnOptions options
) {}
```

The public API should not necessarily expose this internal request type.

## 5. Runtime model

### Target responsibility

`RuntimeModel` should represent one live model instance and coordinate its components.

A rough shape:

```java
final class RuntimeModel {
    private final int id;
    private final CompiledModel model;
    private final RuntimeRoot root;
    private final AnimationController animations;
    private final ModelRenderer renderer;
    private final HitboxController hitboxes;
    private final ViewerTracker viewers;

    void serverTick() {
        root.serverTick();
        animations.serverTick();
        viewers.serverTick();
        hitboxes.serverTick();
        renderer.serverTick();
    }

    void renderTick() {
        renderer.renderTick(animations.pose(), viewers.snapshot(), root.snapshot());
    }

    void remove() {
        // idempotent orchestration
    }
}
```

The exact tick ordering needs tests because animation decisions, root movement, viewer snapshots, and hitboxes depend on a coherent pose/root snapshot.

## 6. Root/entity ownership

Standalone models and mob-attached models currently share `RuntimeModel` with conditional behavior. That is workable if root transport is abstracted.

### Proposed `RuntimeRoot`

```java
interface RuntimeRoot {
    RootSnapshot snapshot();
    void serverTick();
    boolean alive();
    void remove();
}
```

Implementations:

- `StaticRuntimeRoot`
- `LivingEntityRuntimeRoot`

A `RootSnapshot` can contain data safe for the render thread:

```java
record RootSnapshot(
    double x,
    double y,
    double z,
    float yawRadians
) {}
```

This reduces `baseEntity == null` branching throughout runtime code.

## 7. Animation controller versus animation player

These are separate concepts.

### Animation player

Owns playback mechanics:

- active animation,
- compiled frame index,
- frame timing,
- dirty state,
- completion,
- explicit play/stop operations.

The existing `AnimationState` is close to this role and can evolve rather than being discarded.

### Animation controller

Chooses what should play based on high-level runtime state.

```java
interface AnimationController {
    void serverTick(AnimationContext context, AnimationPlayer player);
}
```

Default mob controller behavior can own:

- idle/walk selection,
- talk/attack/hurt/death action priority,
- action timeout policy,
- semantic alias resolution.

A standalone/manual controller can simply play requested animations.

This avoids mixing animation-selection policy into the renderer.

## 8. Semantic animation mapping

Today semantic aliases such as `idle`, `walk`, `attack`, and `death` are represented in more than one place.

Centralize them into one semantic mapping abstraction.

Possible API:

```java
public enum AnimationSemantic {
    IDLE,
    MOVE,
    TALK,
    ATTACK,
    HURT,
    DEATH
}
```

```java
interface AnimationResolver {
    Optional<Animation> resolve(CompiledModel model, AnimationSemantic semantic);
    Optional<Animation> resolve(CompiledModel model, String authoredName);
}
```

The default resolver can preserve current aliases and token matching.

Later, model metadata/config can override mappings without modifying controller logic.

## 9. Pose evaluation

Pose math should be a mostly pure subsystem.

### Inputs

- compiled bone hierarchy,
- animation frame/pose sample,
- root rotation/scale settings,
- ground alignment,
- optional overlay depth offsets.

### Outputs

A runtime pose snapshot containing transforms by bone/part.

Example direction:

```java
final class BonePoseEvaluator {
    void evaluate(
        CompiledModel model,
        AnimationPose animation,
        RootRenderState root,
        MutablePoseOutput output
    ) {}
}
```

Runtime may reuse mutable buffers internally for allocation efficiency, but the ownership must be explicit: only the render thread mutates render-thread pose buffers.

### Why extract it

This makes it possible to test:

- hierarchy transforms,
- scale inheritance,
- cube pivots,
- axis conversion,
- ground alignment,
- overlay depth bias,
- interpolation edge cases

without spawning a Minecraft world.

## 10. Display renderer

The display renderer should own visual ItemDisplay instances and their per-instance caches.

Responsibilities:

- spawn/configure part displays on the server thread,
- remove displays on the server thread,
- root teleports on the server thread,
- hold immutable entity IDs needed by packet transport,
- keep per-part packet transform state,
- build/send transform updates on the render thread.

It should not decide whether the model is walking or attacking.

A useful internal split may be:

```text
DisplayEntityController    Bukkit entity lifecycle
PacketPoseRenderer         render-thread transform packets
```

If that split makes ownership clearer, prefer two small classes over one renderer with both thread domains.

## 11. Viewer tracking

Viewer discovery is a server-thread concern because it originates from Bukkit/Paper entity tracking APIs.

`ViewerTracker` should own:

- hidden-player UUIDs,
- tracking-player discovery,
- viewer generation/version,
- immutable transport-ready viewer snapshot publication.

The render thread should only read the last published snapshot.

This can preserve the current generation counter approach while removing it from `RuntimeModel`.

## 12. Hitbox controller

Hitboxes are not rendering; they are runtime interaction state.

The hitbox controller should own:

- `Interaction` entity lifecycle,
- mapping compiled hitboxes to live entities,
- applying the current evaluated pose/root transform on the server thread,
- player visibility changes if hitboxes follow visual visibility,
- future hitbox event routing.

Pose math used for hitboxes should share transform results with the renderer where possible rather than independently reimplementing hierarchy calculations.

A future optimization is to publish a pose snapshot at 20 TPS for server hitboxes while rendering the same compiled animation at 40 Hz to clients.

## 13. Packet transport abstraction

The packet interface should describe what Osmium needs, not expose NMS classes.

Possible direction:

```java
interface AnimationPacketTransport {
    ViewerSnapshot snapshotViewers(ItemDisplay anchor, Set<UUID> hiddenPlayers);
    PacketBatch newBatch();
}
```

A stronger separation would keep `snapshotViewers` out of the transport because it requires Bukkit and place it in `ViewerTracker`.

Then transport could become:

```java
interface AnimationPacketTransport {
    void send(ViewerConnections viewers, List<TransformUpdate> updates);
}
```

Internal implementation types may still hold cached `ServerGamePacketListenerImpl` references in a snapshot, but those types should not escape the internal network package.

### Compatibility probe

At plugin startup:

1. discover expected display metadata accessors,
2. verify enough fields exist,
3. optionally verify expected accessor serializer/value categories,
4. construct the transport,
5. fail enable cleanly with a specific compatibility message if unsupported.

Do not defer an unsupported layout failure until the first animation render tick.

## 14. Thread model

The architecture should explicitly define two execution domains.

### Server thread / owning region thread

Allowed:

- Bukkit/Paper world/entity APIs,
- display spawning/removal/teleports,
- hitbox entities,
- base entity reads,
- tracked viewer discovery,
- command/API lifecycle actions,
- publishing immutable/safe snapshots.

### 25 ms packet render thread

Allowed:

- advance precompiled animation playback state if that state is owned exclusively here,
- evaluate matrices from immutable model data and published root state,
- compare cached transform components,
- construct packets,
- send through already-captured transport connection handles.

Not allowed:

- Bukkit entity/world method calls,
- Bukkit scheduler mutations,
- entity spawn/remove/teleport,
- querying tracking players,
- mutating authoritative entity metadata.

### Ownership decision for `AnimationState`

The current animation player is accessed by both server logic and render logic under a lock. During decomposition, choose one of two models:

#### Option A - render-thread-owned player

Server thread publishes animation commands (`play`, controller decision) into a small command mailbox. Render thread owns frame advancement and current pose.

Pros:
- less shared mutable playback state,
- fewer broad locks.

Cons:
- action completion/death lifecycle needs a render-to-server status snapshot.

#### Option B - synchronized player boundary

Keep the current lock-backed design but encapsulate it in `AnimationPlayer`.

Pros:
- lower migration risk.

Cons:
- shared state remains.

Recommended migration: start with Option B to preserve behavior, then evaluate Option A after decomposition and benchmarks.

## 15. Public API design

### API principles

- stable interfaces,
- immutable value types,
- no NMS/CraftBukkit types,
- no internal mutable collections,
- minimal threading surprises,
- async operations represented explicitly.

### Initial API use cases

External plugin authors should be able to:

1. check whether Osmium is available,
2. list/query loaded model definitions,
3. spawn a model at a location,
4. attach a model to a living entity,
5. play named animations,
6. trigger semantic animations where supported,
7. show/hide an instance for a player,
8. remove a runtime instance,
9. listen for runtime lifecycle or hitbox events later.

### Possible service

```java
public interface Osmium {
    ModelRegistry models();
    ModelSpawner spawner();
    RuntimeRegistry runtimes();
}
```

Avoid exposing the mutable internal registry directly. API registry methods should return API views/handles.

### Model handle

```java
public interface ModelDefinition {
    String id();
    Set<String> animations();
    int renderPartCount();
    int hitboxCount();
}
```

Do not expose internal `Bone`/`Cube` classes initially unless there is a demonstrated external use case.

### Runtime handle

```java
public interface ModelInstance {
    int id();
    ModelDefinition model();
    boolean playAnimation(String name);
    void setVisible(Player player, boolean visible);
    void remove();
    boolean removed();
}
```

If calls must run on a specific thread, either schedule internally or document/enforce the contract consistently. Prefer a forgiving API that marshals entity mutations onto the correct scheduler where practical.

## 16. Application services

Commands and public API should call the same application services.

Potential services:

```text
ModelReloadService
ResourcePackService
RuntimeSpawnService
RuntimeLookupService
AnimationService
```

This prevents commands from becoming a second internal API.

Example:

```java
final class RuntimeSpawnService {
    ModelInstance spawn(SpawnRequest request) {
        CompiledModel model = modelRepository.require(request.modelId());
        return runtimeRegistry.spawn(model, request);
    }
}
```

## 17. Reload architecture

A reload should be treated as a transaction.

### Build stage

Runs off the server thread where safe:

- scan directories,
- read files,
- parse JSON,
- decode/load textures,
- import Blockbench models,
- compile model snapshot,
- stage resource pack.

### Commit stage

Runs on the appropriate server thread:

- verify plugin is still enabled,
- atomically publish new model snapshot,
- decide policy for active runtime models,
- publish/swap generated pack artifact metadata,
- report result.

### Failure semantics

If any required build step fails:

- current model snapshot remains active,
- current resource pack remains intact,
- active runtime models remain intact unless the caller explicitly requested destructive behavior and the transaction reached commit,
- errors identify each failed model/path when possible.

## 18. Resource pack compiler

`ResourcePackCompiler` already stages pack generation and publishes `pack.zip` atomically where supported. Preserve that pattern.

The larger change is upstream: its input should be a compiled model snapshot, not mutable blueprints.

Potential signature:

```java
Path compile(ModelSnapshot snapshot) throws IOException;
```

The generator should be a pure-ish output step over that snapshot plus pack settings.

## 19. Configuration structure

As features grow, the flat `PluginSettings` record may become unwieldy. Do not split it prematurely, but plan for grouped records:

```java
record PluginSettings(
    ModelSettings models,
    PackSettings pack,
    RenderSettings render,
    AnimationSettings animation
) {}
```

This also reduces constructors such as `ResourcePackCompiler(logger, folder, namespace, customModelDataStart, baseItem, packFormat)` becoming longer over time.

Configuration loading should validate and log corrected/fallback values rather than silently clamping every invalid value when the error would help the server owner.

## 20. Error/result types

Infrastructure operations should move toward structured results.

Example:

```java
record ReloadResult(
    boolean success,
    int modelCount,
    List<ModelLoadFailure> failures,
    Path packPath
) {}
```

This allows commands, logs, and API callers to present appropriate output without parsing exception messages.

Use exceptions for unexpected failure/control boundaries, not as the sole application result model.

## 21. Testing strategy

### Pure unit tests

Target the parts extracted from Bukkit:

- model compiler determinism,
- custom model data allocation,
- semantic animation resolution,
- animation controller transitions,
- pose hierarchy math,
- cube/local transforms,
- hitbox size/position math,
- quaternion canonicalization/hemisphere behavior,
- transform dirty detection.

### Golden file tests

For representative `.bbmodel` fixtures:

- generated item model JSON,
- item definitions,
- UV conversion,
- texture metadata,
- manifests,
- pack structure/hash behavior where stable.

Golden tests should normalize timestamps/cache-buster data if those are intentionally non-deterministic.

### Integration-ish tests

Where Paper test harness support is practical:

- plugin enable/disable,
- registry lifecycle,
- model spawn/remove,
- config load validation.

Do not make the architecture dependent on having a full Minecraft server integration test for every pure transform rule.

## 22. Package visibility

Use Java visibility as part of the architecture.

- `osmium.api.*`: public and compatibility-sensitive.
- implementation packages: public only where Java/Paper requires it; otherwise package-private classes are preferred.
- `osmium.network.nms.*`: internal and explicitly version-sensitive.

A later multi-module build could physically split `osmium-api` and `osmium-plugin`, but that is not required for the first API iteration. Start with package boundaries; move to Gradle subprojects only when the API is stable enough to justify the extra publishing/build complexity.

## 23. Candidate future multi-module layout

Not an immediate requirement, but an eventual layout could be:

```text
osmium-api
  stable interfaces/value types

osmium-core
  importer/compiler/animation domain

osmium-paper
  JavaPlugin, Bukkit entities, commands, schedulers

osmium-nms-vX
  direct packet implementation if version splits become necessary
```

Do not introduce modules solely for aesthetics. Use them if they enforce a real dependency/version boundary.

## 24. Migration approach

The safest implementation strategy is strangler-style rather than rewrite-style.

### Step 1

Add new immutable compiler types while keeping existing runtime behavior.

### Step 2

Switch pack generation to the new compiled data.

### Step 3

Switch runtime spawning to the same compiled data.

### Step 4

Remove old `ModelBlueprint.parts` mutation APIs.

### Step 5

Extract one `RuntimeModel` responsibility at a time, preserving public behavior after each extraction.

### Step 6

Define the public API over the now-clear runtime services.

### Step 7

Move commands to those services/API.

This sequence minimizes the period where two unrelated architectures coexist.

## 25. Near-term implementation checklist

The first implementation branch after this planning branch should probably do only Phase 1.

Recommended concrete sequence:

1. add compiler tests based on current `ResourcePackGenerator` output,
2. add `CompiledModel`/compiled render-part type,
3. extract deterministic render-part creation from `ResourcePackGenerator`,
4. have `ResourcePackGenerator` consume the compiled parts,
5. have `RuntimeModelRegistry.spawn` consume the compiled model,
6. remove mutable render-part methods from `ModelBlueprint`,
7. ensure reload/pack/spawn behavior still passes current tests,
8. add documentation describing the new model pipeline.

Do not start the `RuntimeModel` split until this data boundary is stable.

## Success state

When the plan is implemented, the core flow should be understandable as:

```text
Blockbench file
  -> immutable authored blueprint
  -> deterministic immutable compiled model
  -> immutable repository snapshot
       -> resource pack output
       -> runtime model factory
            -> server-thread entity components
            -> animation/controller components
            -> render-thread pose + packet components
  -> stable public Osmium API above the implementation
```

That structure should make future model-engine features additive rather than forcing every feature into `RuntimeModel` or `ResourcePackGenerator`.
