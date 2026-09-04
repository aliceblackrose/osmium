# Osmium

Osmium is a clean-room, ModelEngine-style Blockbench renderer for Paper Minecraft servers.

It loads `.bbmodel` files, converts model parts into resource-pack item models, and renders them in-game with Bukkit display entities. Osmium is built for modern Paper servers and is designed for custom models, animated runtime entities, generated resource packs, and model hitboxes.

## Features

- Load Blockbench `.bbmodel` files
- Generate Minecraft resource packs automatically
- Spawn runtime models in-game
- Attach models to living mobs
- Play model animations
- Generate item model data from model parts
- Support model hitboxes through interaction entities
- Use native display shadows with automatic model-footprint sizing
- Debug loaded models, textures, UVs, cubes, and animations
- Rolling `latest` GitHub release builds

## Requirements

- Java 25
- Paper 26.1
- Gradle wrapper included for building from source

## Installation

1. Download the latest Osmium JAR from the repository Releases page.
2. Stop your Paper server.
3. Put the JAR into your server's `plugins/` folder.
4. Start the server once so Osmium can generate its folders and config.
5. Place your `.bbmodel` files into:

   ```txt
   plugins/Osmium/blueprints/
   ```

6. Run:

   ```txt
   /om reload
   /om pack
   ```

7. Apply the generated resource pack from:

   ```txt
   plugins/Osmium/resource_pack/
   ```

The generated pack will include a normal `pack.zip` and a versioned `pack-<hash>.zip`.

## Commands

Osmium commands require `osmium.admin` or the legacy `modelenginelike.admin` permission.

| Command | Description |
| --- | --- |
| `/om reload` | Reloads config and models, then removes active runtime models |
| `/om pack` | Generates the resource pack |
| `/om list` | Lists loaded models |
| `/om spawn <model> [animation]` | Spawns a standalone runtime model at your location |
| `/om spawnmob <entity_type> <model> [animation]` | Spawns a living mob with an Osmium model attached |
| `/om play <runtime_id> <animation>` | Plays an animation on a spawned runtime model |
| `/om remove <runtime_id>` | Removes a specific runtime model |
| `/om remove all` | Removes all active runtime models |
| `/om debug <model>` | Shows model debug info, including cubes, parts, hitboxes, textures, and UV data |

## Configuration

Default config:

```yml
namespace: osmium
base-item: PAPER
custom-model-data-start: 100000
pack-format: 84

blueprints-folder: blueprints
resource-pack-folder: resource_pack

auto-generate-pack-on-reload: true

render:
  interpolation-duration: 1
  teleport-duration: 1
  view-range: 64.0
  shadow-enabled: true
  shadow-radius: 0.0
  shadow-strength: 0.75
  brightness-override: false
  brightness-block: 15
  brightness-sky: 15
  scale: 1.0
  ground-align: true
```

### Important options

| Option | Description |
| --- | --- |
| `namespace` | Namespace used for generated assets |
| `base-item` | Minecraft item used as the model carrier |
| `custom-model-data-start` | First custom model data value assigned to generated model parts |
| `pack-format` | Resource-pack format written to `pack.mcmeta` |
| `blueprints-folder` | Folder scanned for `.bbmodel` files |
| `resource-pack-folder` | Folder where generated resource pack files are written |
| `auto-generate-pack-on-reload` | Automatically regenerates the pack when Osmium reloads |
| `render.view-range` | Display entity view range |
| `render.shadow-enabled` | Enables one native display shadow per model |
| `render.shadow-radius` | Shadow radius; `0.0` automatically sizes it from the model's X/Z footprint and render scale |
| `render.shadow-strength` | Native shadow opacity/strength from `0.0` to `1.0` |
| `render.brightness-override` | When `false`, use vanilla world lighting; when `true`, force the block/sky brightness values below |
| `render.brightness-block` | Fixed block-light override used when `render.brightness-override` is enabled |
| `render.brightness-sky` | Fixed sky-light override used when `render.brightness-override` is enabled |
| `render.scale` | Runtime render scale |
| `render.ground-align` | Aligns rendered models to the ground |

Osmium emits the shadow from a single root-anchored display instead of every model part. This avoids stacking many identical shadows at the same entity anchor. Native Minecraft display shadows are soft entity shadows; they are not geometry-projected or ray-traced shadows.

## Model workflow

1. Create or export a `.bbmodel` file from Blockbench.
2. Copy it into:

   ```txt
   plugins/Osmium/blueprints/
   ```

3. Reload Osmium:

   ```txt
   /om reload
   ```

4. Generate the resource pack:

   ```txt
   /om pack
   ```

5. Apply the generated resource pack to your client/server.
6. List loaded models:

   ```txt
   /om list
   ```

7. Spawn a model:

   ```txt
   /om spawn <model>
   ```

8. Spawn a mob with a model:

   ```txt
   /om spawnmob zombie <model>
   ```

## Animations

Osmium can play animations from loaded models.

```txt
/om play <runtime_id> <animation>
```

When spawning a model, you can also provide the starting animation:

```txt
/om spawn <model> idle
/om spawnmob zombie <model> walk
```

For mob-attached models, Osmium attempts to use common animation names such as:

- `idle`, `stand`, `standing`
- `walk`, `walking`, `move`, `moving`, `run`, `running`
- `talk`, `talking`, `speak`, `speaking`, `interact`
- `attack`, `attacking`, `bite`, `melee`, `shoot`
- `hurt`, `damaged`, `damage`, `hit`
- `death`, `die`, `dying`

## Building from source

Clone the repository:

```bash
git clone https://github.com/aliceblackrose/osmium.git
cd osmium
```

Build with Gradle:

```bash
./gradlew clean build
```

The compiled JAR will be in:

```txt
build/libs/
```

On Windows, use:

```bat
gradlew.bat clean build
```

## Using Osmium as a dependency

Osmium is published through JitPack. Add JitPack to your repositories:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://jitpack.io")
    }
}
```

Then depend on a Git tag or commit:

```kotlin
dependencies {
    compileOnly("com.github.aliceblackrose:osmium:<tag>")
}
```

For the latest `master` snapshot:

```kotlin
dependencies {
    compileOnly("com.github.aliceblackrose:osmium:master-SNAPSHOT")
}
```

Use `compileOnly` when Osmium is installed separately as a Paper plugin at runtime.

## Development notes

Osmium uses:

- Java 25
- Gradle Kotlin DSL
- Paper API
- Bukkit display entities
- Item display entities for visual model parts
- Interaction entities for model hitboxes
- Generated resource-pack item models and custom model data

### Architecture roadmap

The planned architecture refactor is documented in:

- [`docs/ROADMAP.md`](docs/ROADMAP.md) - phased implementation roadmap, acceptance criteria, PR sequence, and risk register.
- [`docs/ARCHITECTURE_PLAN.md`](docs/ARCHITECTURE_PLAN.md) - proposed model/compiler/runtime/API/threading architecture and migration strategy.
- [`docs/ANIMATION_ENGINE.md`](docs/ANIMATION_ENGINE.md) - current compiled animation and 40 Hz packet-rendering design.

The refactor prioritizes an immutable compiled-model pipeline, decomposition of `RuntimeModel`, and a stable `osmium.api` surface before larger feature expansion.

## Permissions

```yml
osmium.admin:
  default: op

modelenginelike.admin:
  default: op
```

`modelenginelike.admin` is kept as a legacy permission alias.

## Status

Osmium is in active development. Expect changes to model loading, resource-pack generation, runtime rendering, commands, and configuration as the project evolves.

## License

Osmium is licensed under the GNU General Public License v3.0 (GPL-3.0). See [`LICENSE`](LICENSE) for the full license text.
