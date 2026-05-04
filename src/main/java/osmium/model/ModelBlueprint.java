package osmium.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import osmium.animation.Animation;
import osmium.math.Transforms;
import osmium.math.Vec3;
import osmium.util.Names;

public final class ModelBlueprint {
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
    this.minY = computeMinY();
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
    return Optional.ofNullable(animations.get(name));
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

  private double computeMinY() {
    double minimumY = 0;
    boolean foundCube = false;

    for (Cube cube : cubes.values()) {
      double fromY = Transforms.bbLocalToMc(cube.from()).y();
      double toY = Transforms.bbLocalToMc(cube.to()).y();
      double cubeMinimumY = Math.min(fromY, toY);

      minimumY = foundCube ? Math.min(minimumY, cubeMinimumY) : cubeMinimumY;
      foundCube = true;
    }

    return minimumY;
  }

  public Vec3 modelSizeBlocks() {
    Vec3 minimum = null;
    Vec3 maximum = null;

    for (Cube cube : cubes.values()) {
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

    return minimum == null ? Vec3.ZERO : maximum.subtract(minimum);
  }
}
