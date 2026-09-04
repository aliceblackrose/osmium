package osmium.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import osmium.animation.Animation;
import osmium.math.Transforms;
import osmium.math.Vec3;
import osmium.util.Names;

public final class ModelBlueprint {
  private static final Set<String> SEMANTIC_ANIMATION_ALIASES =
      Set.of(
          "idle",
          "stand",
          "standing",
          "walk",
          "walking",
          "move",
          "moving",
          "run",
          "running",
          "talk",
          "talking",
          "speak",
          "speaking",
          "interact",
          "interaction",
          "attack",
          "attacking",
          "bite",
          "melee",
          "shoot",
          "hurt",
          "damaged",
          "damage",
          "hit",
          "death",
          "die",
          "dying");

  private final String id;
  private final Path source;
  private final Bone root;
  private final Map<String, Bone> bonesByName;
  private final Map<String, Bone> bonesByUuid;
  private final Map<String, Cube> cubes;
  private final Map<String, TextureAsset> textures;
  private final Map<String, Animation> animations;
  private final List<RenderPart> parts = new ArrayList<>();
  private final List<HitboxPart> hitboxes = new ArrayList<>();
  private final double minY;
  private final Vec3 modelSizeBlocks;

  public ModelBlueprint(
      String id,
      Path source,
      Bone root,
      Map<String, Bone> bonesByName,
      Map<String, Bone> bonesByUuid,
      Map<String, Cube> cubes,
      Map<String, TextureAsset> textures,
      Map<String, Animation> animations) {
    this.id = Names.key(id);
    this.source = source;
    this.root = root;
    this.bonesByName = new LinkedHashMap<>(bonesByName);
    this.bonesByUuid = new LinkedHashMap<>(bonesByUuid);
    this.cubes = new LinkedHashMap<>(cubes);
    this.textures = new LinkedHashMap<>(textures);
    this.animations = new LinkedHashMap<>(animations);
    ModelBounds bounds = computeBounds();
    this.minY = bounds.minY();
    this.modelSizeBlocks = bounds.size();
    collectHitboxes(root);
  }

  public String id() {
    return id;
  }

  public Path source() {
    return source;
  }

  public Bone root() {
    return root;
  }

  public Collection<Bone> bones() {
    return Collections.unmodifiableCollection(bonesByName.values());
  }

  public Optional<Bone> bone(String name) {
    return Optional.ofNullable(bonesByName.get(name));
  }

  public Optional<Bone> boneByUuid(String uuid) {
    return Optional.ofNullable(bonesByUuid.get(uuid));
  }

  public Map<String, Cube> cubes() {
    return Collections.unmodifiableMap(cubes);
  }

  public Optional<Cube> cube(String id) {
    return Optional.ofNullable(cubes.get(id));
  }

  public Map<String, TextureAsset> textures() {
    return Collections.unmodifiableMap(textures);
  }

  public Optional<Animation> animation(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }

    String normalizedName = Names.key(name);
    Animation exactAnimation = animations.get(normalizedName);
    if (exactAnimation != null) {
      return Optional.of(exactAnimation);
    }

    if (!SEMANTIC_ANIMATION_ALIASES.contains(normalizedName)) {
      return Optional.empty();
    }

    for (Map.Entry<String, Animation> entry : animations.entrySet()) {
      if (containsNameToken(entry.getKey(), normalizedName)) {
        return Optional.of(entry.getValue());
      }
    }

    return Optional.empty();
  }

  public Map<String, Animation> animations() {
    return Collections.unmodifiableMap(animations);
  }

  public List<RenderPart> parts() {
    return Collections.unmodifiableList(parts);
  }

  public List<HitboxPart> hitboxes() {
    return Collections.unmodifiableList(hitboxes);
  }

  public void clearParts() {
    parts.clear();
  }

  public void addPart(RenderPart part) {
    parts.add(part);
  }

  public double minY() {
    return minY;
  }

  public Vec3 modelSizeBlocks() {
    return modelSizeBlocks;
  }

  private void collectHitboxes(Bone bone) {
    if (bone.hitbox()) {
      int index = 0;
      for (String cubeId : bone.cubeIds()) {
        Cube cube = cubes.get(cubeId);
        if (cube == null) {
          continue;
        }

        hitboxes.add(new HitboxPart(bone.name() + "_" + index, bone, cube));
        index++;
      }
    }

    for (Bone child : bone.children()) {
      collectHitboxes(child);
    }
  }

  public TextureAsset texture(String key) {
    TextureAsset texture = textures.get(key);
    if (texture != null) {
      return texture;
    }

    texture = textures.get("0");
    if (texture != null) {
      return texture;
    }

    return textures.values().iterator().next();
  }

  private ModelBounds computeBounds() {
    Vec3 minimum = null;
    Vec3 maximum = null;

    for (Cube cube : renderableCubes()) {
      Vec3 from = Transforms.bbLocalToMc(cube.from());
      Vec3 to = Transforms.bbLocalToMc(cube.to());
      Vec3 cubeMinimum =
          new Vec3(
              Math.min(from.x(), to.x()), Math.min(from.y(), to.y()), Math.min(from.z(), to.z()));
      Vec3 cubeMaximum =
          new Vec3(
              Math.max(from.x(), to.x()), Math.max(from.y(), to.y()), Math.max(from.z(), to.z()));

      minimum =
          minimum == null
              ? cubeMinimum
              : new Vec3(
                  Math.min(minimum.x(), cubeMinimum.x()),
                  Math.min(minimum.y(), cubeMinimum.y()),
                  Math.min(minimum.z(), cubeMinimum.z()));
      maximum =
          maximum == null
              ? cubeMaximum
              : new Vec3(
                  Math.max(maximum.x(), cubeMaximum.x()),
                  Math.max(maximum.y(), cubeMaximum.y()),
                  Math.max(maximum.z(), cubeMaximum.z()));
    }

    if (minimum == null) {
      return new ModelBounds(0.0D, Vec3.ZERO);
    }
    return new ModelBounds(minimum.y(), maximum.subtract(minimum));
  }

  private List<Cube> renderableCubes() {
    List<Cube> renderable = new ArrayList<>();
    collectRenderableCubes(root, renderable);
    return renderable;
  }

  private void collectRenderableCubes(Bone bone, List<Cube> renderable) {
    if (bone.visible()) {
      for (String cubeId : bone.cubeIds()) {
        Cube cube = cubes.get(cubeId);
        if (cube != null && cube.renderable()) {
          renderable.add(cube);
        }
      }
    }

    for (Bone child : bone.children()) {
      collectRenderableCubes(child, renderable);
    }
  }

  private static boolean containsNameToken(String animationName, String token) {
    int offset = 0;
    while (offset < animationName.length()) {
      int separator = animationName.indexOf('_', offset);
      int end = separator < 0 ? animationName.length() : separator;
      if (animationName.regionMatches(offset, token, 0, token.length())
          && end - offset == token.length()) {
        return true;
      }

      if (separator < 0) {
        return false;
      }

      offset = separator + 1;
    }

    return false;
  }

  private record ModelBounds(double minY, Vec3 size) {}
}
