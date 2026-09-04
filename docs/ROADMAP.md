# Osmium architecture roadmap

Status: planning

This roadmap describes the refactor that turns Osmium from a Paper plugin with an embedded renderer into a model-rendering engine with a stable Paper integration layer. The work is intentionally split into independently mergeable phases so the plugin remains usable throughout the migration.

The first three priorities are:

1. introduce an immutable compiled-model pipeline,
2. decompose `RuntimeModel`, and
3. define a stable public API.

Everything after those phases builds on those boundaries.

## Goals

- Keep authored Blockbench data separate from generated resource-pack/runtime data.
- Make model data immutable after loading/compilation.
- Reduce the responsibility and size of `RuntimeModel`.
- Define explicit server-thread versus packet-render-thread ownership.
- Isolate Paper/NMS version-sensitive code.
- Expose a small, stable API for other plugins using Osmium through JitPack.
- Make reloads transactional so failed imports or pack generation do not partially mutate active state.
- Make commands, runtime controllers, renderers, and transports replaceable/testable behind interfaces.
- Preserve the current 40 Hz compiled animation transport unless a phase explicitly changes it.
- Keep every phase reviewable and testable without requiring a big-bang rewrite.

## Non-goals for the first refactor

The first architecture pass should not simultaneously add major user-facing features. In particular, the following should wait until the new boundaries exist:

- animation layers/blending,
- procedural animation,
- per-player skins or model variants,
- mounts/passengers,
- a complete custom mob AI system,
- Folia support,
- protocol abstraction libraries,
- a new model format.

Those become easier once the runtime, renderer, controller, and API layers are separated.

## Design rules

### Immutable data crosses subsystem boundaries

`ModelBlueprint`, compiled animation data, and the future `CompiledModel` should be immutable snapshots. Runtime state belongs to runtime objects, not model-definition objects.

### Filesystem work must not create runtime state

Resource-pack generation should consume compiled model definitions. It must not call mutators such as `ModelBlueprint.clearParts()` or `ModelBlueprint.addPart()` to prepare models for runtime spawning.

### Bukkit/Paper entity access stays on the owning server thread

The 25 ms packet renderer may evaluate already-snapshotted model state and send packets through captured connections, but it must not call Bukkit APIs or mutate Bukkit/NMS entities off-thread.

### Version-sensitive code has one boundary

Direct NMS packet implementation details should be concentrated under an internal transport package rather than leaking into runtime/model classes.

### Public API is smaller than the implementation

External plugins should depend on interfaces and immutable API value types. They should not need to know about render caches, NMS packet types, internal model managers, or compiler internals.

## Phase 0 - Baseline and guardrails

Purpose: establish tests and invariants before structural changes.

### Tasks

- [ ] Document current package ownership and thread ownership.
- [ ] Add a regression test proving the current resource-pack output for representative fixtures.
- [ ] Add tests for standalone runtime lifecycle where practical.
- [ ] Add tests for runtime registry removal/snapshot behavior.
- [ ] Add animation-controller transition tests for idle/walk/action/death semantics.
- [ ] Add tests around viewer generation and packet transform-state invalidation where they can be tested without a live server.
- [ ] Make Java/config defaults agree, including interpolation defaults.
- [ ] Record representative performance numbers for model import, pack compile, animation compile, and a high-part-count runtime model.

### Exit criteria

- Existing behavior is covered well enough that architecture changes can be distinguished from rendering regressions.
- CI remains green with Spotless, Error Prone, tests, and Java 25.

## Phase 1 - Immutable compiled-model pipeline

Purpose: separate authored/imported model data from render/resource-pack compilation output.

### Proposed data flow

```text
.bbmodel
   |
   v
BlockbenchImporter
   |
   v
ModelBlueprint          authored/imported model, immutable
   |
   v
ModelCompiler
   |
   v
CompiledModel           runtime/resource-pack-ready, immutable
   |                  |
   |                  +--> ResourcePackGenerator
   |
   +--> RuntimeModelFactory
```

### Proposed types

```java
public record CompiledModel(
    ModelBlueprint blueprint,
    List<RenderPart> renderParts,
    List<HitboxPart> hitboxes
) {}
```

The exact shape can change during implementation. The important invariant is that resource-pack generation no longer mutates a `ModelBlueprint`.

A `ModelCompiler` should be responsible for assigning the stable render-part definitions needed by both the resource pack and the runtime renderer. Custom-model-data allocation may either remain pack-level or become part of a compilation context, but it must be deterministic.

### Tasks

- [ ] Introduce `CompiledModel`.
- [ ] Introduce `ModelCompiler`.
- [ ] Move render-part construction out of `ResourcePackGenerator.writeModelParts()`.
- [ ] Remove mutable `parts` state from `ModelBlueprint`.
- [ ] Remove `ModelBlueprint.clearParts()` and `ModelBlueprint.addPart()`.
- [ ] Decide whether hitbox extraction belongs in `ModelBlueprint` or `CompiledModel`; prefer the compiled layer if hitboxes are runtime concerns.
- [ ] Make `ResourcePackGenerator` consume compiled models.
- [ ] Make runtime spawning consume compiled models.
- [ ] Keep deterministic custom model data/model key generation.
- [ ] Add equality/snapshot tests proving repeated compiles are deterministic.
- [ ] Add a test proving pack generation does not mutate source model state.

### Compatibility strategy

During migration, `ModelManager` may temporarily expose both imported and compiled views. Avoid a permanent dual-registry design; the steady state should have one authoritative snapshot used for runtime spawning.

### Exit criteria

- No pack-generation path mutates `ModelBlueprint`.
- A model can be compiled once and used by both resource-pack generation and runtime spawning.
- Re-running pack generation does not change in-memory model definitions.
- Current generated packs and runtime models remain behaviorally equivalent.

## Phase 2 - Decompose `RuntimeModel`

Purpose: turn `RuntimeModel` into an orchestrator rather than the owner of every runtime subsystem.

### Target responsibility split

```text
RuntimeModel
  |- ModelAnimationController
  |- ModelPoseRenderer
  |- ViewerTracker
  |- HitboxController
  |- BaseEntityController
  `- RuntimeVisibility
```

Possible package layout:

```text
osmium.runtime
  RuntimeModel
  RuntimeModelFactory
  RuntimeModelRegistry

osmium.runtime.animation
  ModelAnimationController
  DefaultMobAnimationController
  AnimationPlayer

osmium.runtime.render
  ModelPoseRenderer
  BonePoseEvaluator
  PartInstance
  BoneRenderState

osmium.runtime.hitbox
  HitboxController
  HitboxInstance

osmium.runtime.tracking
  ViewerTracker

osmium.runtime.entity
  BaseEntityController
```

Package names are provisional. Prefer clear ownership over preserving the existing `render` package shape.

### `RuntimeModel` should retain

- runtime identity,
- high-level lifecycle (`tick`, `remove`),
- access to the compiled model,
- delegation to animation/render/hitbox/viewer components,
- small public operations such as play/remove/visibility until the public API lands.

### `RuntimeModel` should lose

- hardcoded animation alias tables,
- direct ItemDisplay spawn/setup loops,
- hitbox transform math,
- viewer snapshot construction,
- packet batching implementation,
- recursive bone transform implementation,
- mob movement/action state-machine logic.

### Tasks

- [ ] Extract immutable render-layout data from per-instance render state.
- [ ] Extract bone pose evaluation.
- [ ] Extract part spawning/removal/teleport/display configuration.
- [ ] Extract hitbox spawning/updating/removal.
- [ ] Extract viewer tracking and hidden-player state.
- [ ] Extract default mob animation semantics.
- [ ] Keep the 20 TPS/40 Hz thread split explicit in component APIs.
- [ ] Replace broad synchronized regions with ownership/snapshot boundaries where possible.
- [ ] Add unit tests for extracted pure transform/controller classes.

### Exit criteria

- `RuntimeModel` is a small lifecycle/orchestration class.
- Pose evaluation can be tested without spawning Bukkit entities.
- Default mob animation selection can be tested without packet transport.
- Hitbox math can be tested separately from entity lifecycle.

## Phase 3 - Public API

Purpose: make the JitPack dependency useful without exposing Osmium internals as the de facto API.

### Proposed API surface

```text
osmium.api
  Osmium
  ModelRegistry
  ModelSpawner
  ModelDefinition
  ModelInstance
  SpawnOptions
  AnimationHandle / animation operations
```

Example direction:

```java
public interface Osmium {
    ModelRegistry models();
    ModelSpawner spawner();
}

public interface ModelSpawner {
    ModelInstance spawn(String modelId, Location location, SpawnOptions options);
}

public interface ModelInstance {
    int runtimeId();
    String modelId();
    boolean playAnimation(String name);
    void setVisible(Player player, boolean visible);
    void remove();
}
```

The API should prefer stable identifiers/value objects over returning internal `RuntimeModel`, `ModelBlueprint`, `AnimationState`, or NMS types.

### Service discovery

Prefer publishing the API through Paper/Bukkit's service mechanism or another explicit accessor rather than requiring consumers to cast `PluginManager#getPlugin("Osmium")` to the implementation class.

### Versioning policy

- Treat `osmium.api` as compatibility-sensitive.
- Treat `osmium.internal` or non-API packages as unstable.
- Document semantic-version expectations before a stable release.
- Avoid putting Paper/NMS implementation classes in public signatures.

### Tasks

- [ ] Define minimum API use cases from external plugins.
- [ ] Add `osmium.api` interfaces/value types.
- [ ] Register an Osmium service implementation on plugin enable.
- [ ] Add a small integration example in docs.
- [ ] Keep implementation classes out of API return types.
- [ ] Document threading requirements for API calls.
- [ ] Add API compatibility tests where practical.

### Exit criteria

An external plugin can load a model, spawn an instance, play an animation, change visibility, query basic model metadata, and remove the instance without importing internal implementation packages.

## Phase 4 - NMS transport boundary

Purpose: make Paper-version-sensitive packet code explicit and replaceable.

### Target structure

```text
osmium.network
  AnimationPacketTransport
  ViewerSnapshot
  TransformUpdate

osmium.network.nms
  NmsAnimationPacketTransport
  DisplayMetadataAccessors
```

### Tasks

- [ ] Define a transport interface that contains no CraftBukkit/NMS types.
- [ ] Move reflection-based display metadata discovery into one compatibility component.
- [ ] Perform transport compatibility validation during startup.
- [ ] Emit a clear unsupported-Paper error with expected/observed metadata information.
- [ ] Keep packet-side transform caches independent of Bukkit entity metadata.
- [ ] Add focused tests for quaternion hemisphere preservation and transform-change filtering.

### Exit criteria

Runtime/render code depends only on the transport abstraction. Updating to a new Paper version should usually require changes in the NMS implementation package rather than throughout the renderer.

## Phase 5 - Transactional asynchronous reloads

Purpose: remove expensive file/model/pack work from the main server thread and prevent partial state mutation.

### Proposed flow

```text
async executor
  scan files
  -> import models
  -> compile models
  -> generate staged pack
  -> build ModelSnapshot

server thread
  atomic snapshot swap
  -> optional runtime cleanup/migration
  -> publish new pack metadata
```

### Tasks

- [ ] Introduce immutable `ModelSnapshot`.
- [ ] Make `ModelManager` atomically replace snapshots only after full success.
- [ ] Stage pack generation before publishing `pack.zip`.
- [ ] Return structured reload results instead of only throwing `IOException`.
- [ ] Preserve previous models/pack on failure.
- [ ] Prevent overlapping reloads or define cancellation/coalescing semantics.
- [ ] Provide progress/result feedback to commands/API callers.

### Exit criteria

A malformed `.bbmodel` or failed pack write cannot leave Osmium in a half-reloaded state, and heavy import/pack work does not block the main server thread.

## Phase 6 - Command system cleanup

Purpose: make command parsing/validation a thin adapter over the runtime API.

### Tasks

- [ ] Move command behavior to service/API operations rather than accessing implementation managers directly.
- [ ] Migrate to Paper's Brigadier command API.
- [ ] Introduce typed model/runtime/entity arguments.
- [ ] Move debug formatting out of the command parser.
- [ ] Add proper animation suggestions based on selected models/instances.
- [ ] Preserve existing `/om` behavior during migration where practical.

### Exit criteria

The command layer contains minimal business logic and mostly validates arguments, calls services, and formats results.

## Phase 7 - Scheduler portability and performance

Purpose: optimize only after subsystem boundaries make profiling meaningful.

### Tasks

- [ ] Evaluate Folia compatibility and entity/region scheduler requirements.
- [ ] Avoid unconditional work for models with no viewers.
- [ ] Consider distance/viewer-driven animation suspension.
- [ ] Benchmark packet batching for high-part-count models and many viewers.
- [ ] Measure allocation pressure in matrix/pose evaluation.
- [ ] Consider pooled/reused frame output only where benchmarks show value.
- [ ] Add configurable animation-render cadence only if it does not compromise authored motion semantics.

### Exit criteria

Performance changes are supported by measurements and do not weaken the thread-safety contract.

## Phase 8 - Feature expansion enabled by the architecture

Once the foundation is stable, the following become realistic without bloating `RuntimeModel` again:

- animation layers and priorities,
- transitions/crossfades,
- procedural/look-at bones,
- per-player render variants,
- custom animation controllers,
- attachable accessories,
- mounts/passengers,
- custom mob behavior integration,
- persistence/respawn policies,
- richer hitbox/event APIs.

Each feature should attach to an existing subsystem rather than re-centralizing responsibilities.

## Dependency order

```text
Phase 0  Baseline
   |
   v
Phase 1  CompiledModel
   |
   v
Phase 2  Runtime decomposition
   |
   +------------------+
   v                  v
Phase 3  Public API   Phase 4  NMS boundary
   |                  |
   +--------+---------+
            v
Phase 5  Transactional reload
            |
            v
Phase 6  Commands
            |
            v
Phase 7  Portability/performance
            |
            v
Phase 8  Features
```

Phase 4 can overlap with Phase 3 after the runtime decomposition establishes the dependency direction.

## Recommended pull-request sequence

Keep each implementation PR narrow enough to review and revert independently.

1. `test: establish architecture regression coverage`
2. `refactor: introduce immutable compiled model`
3. `refactor: make pack generator consume compiled models`
4. `refactor: make runtime spawning consume compiled models`
5. `refactor: extract bone pose evaluator`
6. `refactor: extract display renderer`
7. `refactor: extract hitbox controller`
8. `refactor: extract viewer tracker`
9. `refactor: extract mob animation controller`
10. `feat: add public Osmium API`
11. `refactor: isolate NMS animation transport`
12. `refactor: transactional model snapshots and reload`
13. `refactor: migrate commands to API/Brigadier`
14. `perf: runtime profiling and targeted optimizations`

Avoid combining Phase 1 and the entire `RuntimeModel` split into one PR.

## Risk register

### Resource-pack/runtime identity drift

Risk: a compiler refactor may assign a different custom-model-data value or item model key than the runtime expects.

Mitigation: deterministic compilation and golden-output pack tests.

### Thread-ownership regression

Risk: extracted components may accidentally call Bukkit from the 25 ms animation thread.

Mitigation: explicit `serverTick`/`renderTick` APIs, immutable snapshots, comments/tests around thread ownership, and no Bukkit objects in pure pose-evaluation APIs where avoidable.

### Public API stabilization too early

Risk: freezing interfaces before the runtime decomposition is understood can create awkward compatibility commitments.

Mitigation: finish the core Phase 2 boundaries before declaring the initial API stable.

### NMS compatibility failure

Risk: metadata accessor order changes on a future Paper build.

Mitigation: startup capability check and one isolated compatibility implementation.

### Big-bang refactor

Risk: too many simultaneous file moves and behavior changes make rendering regressions hard to identify.

Mitigation: sequence the work as small behavior-preserving PRs with tests between them.

## Definition of done for the architecture project

The architecture refactor is complete when:

- `ModelBlueprint` is immutable and pack generation cannot mutate it.
- runtime spawning consumes an immutable compiled model definition.
- `RuntimeModel` primarily coordinates smaller runtime components.
- pose evaluation, animation selection, viewer tracking, hitboxes, and packet transport have explicit owners.
- the 20 TPS server-thread and 40 Hz packet-render-thread contract is documented and enforced by APIs.
- external plugins can use `osmium.api` without importing implementation or NMS packages.
- NMS compatibility code is isolated.
- reloads are transactional.
- commands use the same services/API as other callers rather than containing core logic.
- CI covers the major compiler/runtime boundaries.

At that point Osmium has a foundation suitable for larger rendering and custom-entity features without returning to a monolithic runtime class.
