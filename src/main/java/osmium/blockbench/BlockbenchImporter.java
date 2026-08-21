package osmium.blockbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import osmium.animation.Animation;
import osmium.animation.BoneTimeline;
import osmium.animation.Interpolation;
import osmium.animation.Keyframe;
import osmium.math.Vec3;
import osmium.model.Bone;
import osmium.model.Cube;
import osmium.model.Face;
import osmium.model.ModelBlueprint;
import osmium.model.TextureAsset;
import osmium.util.Jsons;
import osmium.util.Names;

public final class BlockbenchImporter {
  private static final String DEFAULT_MODEL_NAMESPACE = "osmium";
  private static final String DEFAULT_ROOT_BONE_ID = "root";
  private static final String DEFAULT_TEXTURE_NAME = "fallback";
  private static final String HITBOX_NAME_PART = "hitbox";
  private static final String TEXTURE_MODEL_DIRECTORY = "item";

  private static final int DEFAULT_TEXTURE_HEIGHT = 16;
  private static final int DEFAULT_TEXTURE_WIDTH = 16;
  private static final double DEFAULT_ANIMATION_LENGTH = 0.05;

  private static final String[] DIRECTIONS = {"north", "east", "south", "west", "up", "down"};

  private final Logger logger;
  private final String namespace;

  public BlockbenchImporter(Logger logger) {
    this(logger, DEFAULT_MODEL_NAMESPACE);
  }

  public BlockbenchImporter(Logger logger, String namespace) {
    this.logger = logger;
    this.namespace = Names.namespace(namespace);
  }

  public ModelBlueprint importFile(Path file) throws IOException {
    JsonObject root =
        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

    String modelId = Names.key(Names.stem(file.getFileName().toString()));
    Map<String, TextureAsset> textures = readTextures(modelId, file, root);
    Map<String, Cube> cubes = readCubes(root);

    Map<String, Bone> bonesByName = new LinkedHashMap<>();
    Map<String, Bone> bonesByUuid = new LinkedHashMap<>();
    Map<String, String> animationBoneNames = new LinkedHashMap<>();

    Bone rootBone = readOutliner(root, cubes, bonesByName, bonesByUuid, animationBoneNames);
    Map<String, Animation> animations = readAnimations(root, animationBoneNames);

    return new ModelBlueprint(
        modelId, file, rootBone, bonesByName, bonesByUuid, cubes, textures, animations);
  }

  private static void registerTexture(
      Map<String, TextureAsset> textures, TextureAsset texture, int index) {
    textures.put(String.valueOf(index), texture);
    textures.putIfAbsent(texture.variable(), texture);
    textures.putIfAbsent(texture.id(), texture);
    textures.putIfAbsent(texture.name(), texture);
    textures.putIfAbsent(Names.stem(texture.name()), texture);
  }

  private byte[] decodeEmbeddedTexture(String source) {
    if (source == null || source.isBlank()) {
      return null;
    }

    int dataSeparatorIndex = source.indexOf(',');
    String base64 = dataSeparatorIndex >= 0 ? source.substring(dataSeparatorIndex + 1) : source;

    try {
      return Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException exception) {
      logger.log(Level.FINE, "Ignoring invalid embedded texture data.", exception);
      return null;
    }
  }

  private static Path resolveTexturePath(
      Path modelFile, String pathValue, String relativePathValue) {
    String rawPath = firstNonBlank(pathValue, relativePathValue);
    if (rawPath.isBlank() || rawPath.startsWith("data:")) {
      return null;
    }

    Path path = Path.of(rawPath);
    if (!path.isAbsolute() && modelFile.getParent() != null) {
      path = modelFile.getParent().resolve(path).normalize();
    }

    return Files.isRegularFile(path) ? path : null;
  }

  private static Map<String, Cube> readCubes(JsonObject root) {
    Map<String, Cube> cubes = new LinkedHashMap<>();
    JsonArray elements = Jsons.array(root, "elements");

    for (int index = 0; index < elements.size(); index++) {
      JsonElement element = elements.get(index);
      if (!element.isJsonObject()) {
        continue;
      }

      JsonObject cubeJson = element.getAsJsonObject();
      if (!Jsons.bool(cubeJson, "visibility", true)) {
        continue;
      }

      Map<String, Face> faces = readFaces(cubeJson);
      if (faces.isEmpty()) {
        continue;
      }

      String uuid = firstNonBlank(Jsons.string(cubeJson, "uuid", ""), UUID.randomUUID().toString());
      String name = firstNonBlank(Jsons.string(cubeJson, "name", ""), "cube_" + index);

      cubes.put(
          uuid,
          new Cube(
              uuid,
              name,
              Vec3.fromArray(cubeJson.get("from")),
              Vec3.fromArray(cubeJson.get("to")),
              Vec3.fromArray(cubeJson.get("origin")),
              Vec3.fromArray(cubeJson.get("rotation")),
              Jsons.dbl(cubeJson, "inflate", 0),
              faces));
    }

    return cubes;
  }

  private static Map<String, Face> readFaces(JsonObject cubeJson) {
    Map<String, Face> faces = new LinkedHashMap<>();
    JsonObject facesJson = Jsons.object(cubeJson, "faces");

    for (String direction : DIRECTIONS) {
      JsonElement faceElement = facesJson.get(direction);
      if (faceElement == null || !faceElement.isJsonObject()) {
        continue;
      }

      Face face = readFace(direction, faceElement.getAsJsonObject());
      if (face != null && !face.empty()) {
        faces.put(direction, face);
      }
    }

    return faces;
  }

  private static Face readFace(String direction, JsonObject faceJson) {
    String textureKey = readTextureKey(faceJson);
    if (textureKey.isBlank()) {
      return null;
    }

    JsonArray uv = Jsons.array(faceJson, "uv");
    if (uv.size() < 4) {
      return null;
    }

    return new Face(
        direction,
        readDouble(uv, 0),
        readDouble(uv, 1),
        readDouble(uv, 2),
        readDouble(uv, 3),
        Jsons.integer(faceJson, "rotation", 0),
        textureKey);
  }

  private static String readTextureKey(JsonObject faceJson) {
    JsonElement textureElement = faceJson.get("texture");
    if (textureElement == null || textureElement.isJsonNull()) {
      return "";
    }

    String textureKey;
    try {
      textureKey = textureElement.getAsString();
    } catch (IllegalStateException | UnsupportedOperationException | ClassCastException exception) {
      return "";
    }

    if (textureKey == null || textureKey.isBlank() || "null".equalsIgnoreCase(textureKey)) {
      return "";
    }

    return textureKey.startsWith("#") ? textureKey.substring(1) : textureKey;
  }

  private static double readDouble(JsonArray array, int index) {
    if (array.size() <= index) {
      return 0;
    }

    try {
      return array.get(index).getAsDouble();
    } catch (IllegalStateException
        | NumberFormatException
        | UnsupportedOperationException
        | ClassCastException exception) {
      return 0;
    }
  }

  private static String uniqueBoneName(String name, Map<String, Bone> bonesByName) {
    if (!bonesByName.containsKey(name)) {
      return name;
    }

    int suffix = 2;
    while (bonesByName.containsKey(name + "_" + suffix)) {
      suffix++;
    }

    return name + "_" + suffix;
  }

  private static boolean isCubeAssigned(Bone bone, String cubeId) {
    if (bone.cubeIds().contains(cubeId)) {
      return true;
    }

    for (Bone child : bone.children()) {
      if (isCubeAssigned(child, cubeId)) {
        return true;
      }
    }

    return false;
  }

  private static boolean loops(JsonObject animationJson) {
    JsonElement loopElement = animationJson.get("loop");
    if (loopElement == null || loopElement.isJsonNull()) {
      return true;
    }

    String value = loopElement.getAsString().toLowerCase(Locale.ROOT);
    return value.equals("true") || value.equals("loop") || value.equals("hold");
  }

  private static Vec3 keyframePoint(JsonObject keyframeJson, String channel, int pointIndex) {
    JsonArray dataPoints = Jsons.array(keyframeJson, "data_points");
    Vec3 fallback = channel.equals("scale") ? Vec3.ONE : Vec3.ZERO;

    if (dataPoints.size() > pointIndex && dataPoints.get(pointIndex).isJsonObject()) {
      return Vec3.fromObject(dataPoints.get(pointIndex).getAsJsonObject(), fallback);
    }

    if (pointIndex == 0
        && keyframeJson.get("vector") != null
        && keyframeJson.get("vector").isJsonArray()) {
      return Vec3.fromArray(keyframeJson.get("vector"));
    }

    return pointIndex == 0 ? fallback : null;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }

    return "";
  }

  private Map<String, TextureAsset> readTextures(String modelId, Path file, JsonObject root) {
    Map<String, TextureAsset> textures = new LinkedHashMap<>();
    JsonObject resolution = Jsons.object(root, "resolution");
    int defaultWidth = Jsons.integer(resolution, "width", DEFAULT_TEXTURE_WIDTH);
    int defaultHeight = Jsons.integer(resolution, "height", DEFAULT_TEXTURE_HEIGHT);

    JsonArray textureArray = Jsons.array(root, "textures");
    for (int index = 0; index < textureArray.size(); index++) {
      JsonElement element = textureArray.get(index);
      if (!element.isJsonObject()) {
        continue;
      }

      TextureAsset texture =
          readTexture(modelId, file, element.getAsJsonObject(), index, defaultWidth, defaultHeight);
      registerTexture(textures, texture, index);
    }

    if (textures.isEmpty()) {
      registerTexture(textures, fallbackTexture(modelId), 0);
    }

    return textures;
  }

  private TextureAsset readTexture(
      String modelId,
      Path file,
      JsonObject textureJson,
      int index,
      int defaultWidth,
      int defaultHeight) {
    String name = firstNonBlank(Jsons.string(textureJson, "name", ""), "texture_" + index);
    String id = firstNonBlank(Jsons.string(textureJson, "id", ""), String.valueOf(index));

    int frameWidth =
        Jsons.integer(textureJson, "uv_width", Jsons.integer(textureJson, "width", defaultWidth));
    int frameHeight =
        Jsons.integer(
            textureJson, "uv_height", Jsons.integer(textureJson, "height", defaultHeight));
    int imageWidth = Jsons.integer(textureJson, "width", frameWidth);
    int imageHeight = Jsons.integer(textureJson, "height", frameHeight);

    return new TextureAsset(
        id,
        name,
        String.valueOf(index),
        textureModelPath(modelId, name),
        decodeEmbeddedTexture(Jsons.string(textureJson, "source", "")),
        resolveTexturePath(
            file,
            Jsons.string(textureJson, "path", ""),
            Jsons.string(textureJson, "relative_path", "")),
        Math.max(1, frameWidth),
        Math.max(1, frameHeight),
        Math.max(1, imageWidth),
        Math.max(1, imageHeight),
        Jsons.integer(textureJson, "frame_time", 1),
        Jsons.bool(textureJson, "frame_interpolate", false),
        Jsons.string(textureJson, "frame_order", ""));
  }

  private TextureAsset fallbackTexture(String modelId) {
    return new TextureAsset(
        "0",
        DEFAULT_TEXTURE_NAME,
        "0",
        namespace + ":" + TEXTURE_MODEL_DIRECTORY + "/" + modelId + "/fallback",
        null,
        null,
        DEFAULT_TEXTURE_WIDTH,
        DEFAULT_TEXTURE_HEIGHT,
        DEFAULT_TEXTURE_WIDTH,
        DEFAULT_TEXTURE_HEIGHT,
        1,
        false,
        "");
  }

  private String textureModelPath(String modelId, String textureName) {
    return namespace
        + ":"
        + TEXTURE_MODEL_DIRECTORY
        + "/"
        + modelId
        + "/"
        + Names.key(Names.stem(textureName));
  }

  private Bone readOutliner(
      JsonObject root,
      Map<String, Cube> cubes,
      Map<String, Bone> bonesByName,
      Map<String, Bone> bonesByUuid,
      Map<String, String> animationBoneNames) {
    Bone rootBone =
        new Bone(
            DEFAULT_ROOT_BONE_ID,
            DEFAULT_ROOT_BONE_ID,
            DEFAULT_ROOT_BONE_ID,
            Vec3.ZERO,
            Vec3.ZERO,
            Vec3.ZERO,
            true);

    bonesByName.put(DEFAULT_ROOT_BONE_ID, rootBone);
    bonesByUuid.put(DEFAULT_ROOT_BONE_ID, rootBone);

    for (JsonElement element : Jsons.array(root, "outliner")) {
      readOutlinerChild(element, rootBone, cubes, bonesByName, bonesByUuid, animationBoneNames);
    }

    for (String cubeId : cubes.keySet()) {
      if (!isCubeAssigned(rootBone, cubeId)) {
        rootBone.addCube(cubeId);
      }
    }

    return rootBone;
  }

  private void readOutlinerChild(
      JsonElement element,
      Bone parent,
      Map<String, Cube> cubes,
      Map<String, Bone> bonesByName,
      Map<String, Bone> bonesByUuid,
      Map<String, String> animationBoneNames) {
    if (element == null) {
      return;
    }

    if (element.isJsonPrimitive()) {
      addCubeReference(element, parent, cubes);
      return;
    }

    if (!element.isJsonObject()) {
      return;
    }

    readBone(
        element.getAsJsonObject(), parent, cubes, bonesByName, bonesByUuid, animationBoneNames);
  }

  private static void addCubeReference(JsonElement element, Bone parent, Map<String, Cube> cubes) {
    String cubeId = element.getAsString();
    if (cubes.containsKey(cubeId)) {
      parent.addCube(cubeId);
    }
  }

  private void readBone(
      JsonObject boneJson,
      Bone parent,
      Map<String, Cube> cubes,
      Map<String, Bone> bonesByName,
      Map<String, Bone> bonesByUuid,
      Map<String, String> animationBoneNames) {
    String rawName =
        firstNonBlank(
            Jsons.string(boneJson, "name", ""), Jsons.string(boneJson, "uuid", ""), "bone");
    String uuid = firstNonBlank(Jsons.string(boneJson, "uuid", ""), UUID.randomUUID().toString());

    Bone bone =
        new Bone(
            uniqueBoneName(Names.key(rawName), bonesByName),
            rawName,
            uuid,
            Vec3.fromArray(boneJson.get("origin")),
            parent.origin(),
            Vec3.fromArray(boneJson.get("rotation")),
            isBoneVisible(boneJson, rawName));

    parent.addChild(bone);
    bonesByName.put(bone.name(), bone);
    bonesByUuid.put(uuid, bone);
    registerAnimationBoneNames(animationBoneNames, uuid, rawName, bone.name());

    for (JsonElement child : Jsons.array(boneJson, "children")) {
      readOutlinerChild(child, bone, cubes, bonesByName, bonesByUuid, animationBoneNames);
    }
  }

  private static boolean isBoneVisible(JsonObject boneJson, String rawName) {
    return Jsons.bool(boneJson, "visibility", true)
        && !rawName.toLowerCase(Locale.ROOT).contains(HITBOX_NAME_PART);
  }

  private static void registerAnimationBoneNames(
      Map<String, String> animationBoneNames, String uuid, String rawName, String boneName) {
    animationBoneNames.put(uuid, boneName);
    animationBoneNames.putIfAbsent(rawName, boneName);
    animationBoneNames.putIfAbsent(Names.key(rawName), boneName);
  }

  private Map<String, Animation> readAnimations(
      JsonObject root, Map<String, String> animationBoneNames) {
    Map<String, Animation> animations = new LinkedHashMap<>();
    JsonArray animationArray = Jsons.array(root, "animations");

    for (int index = 0; index < animationArray.size(); index++) {
      JsonElement element = animationArray.get(index);
      if (!element.isJsonObject()) {
        continue;
      }

      JsonObject animationJson = element.getAsJsonObject();
      String name =
          Names.key(firstNonBlank(Jsons.string(animationJson, "name", ""), "animation_" + index));

      animations.put(
          name,
          new Animation(
              name,
              Jsons.dbl(animationJson, "length", DEFAULT_ANIMATION_LENGTH),
              loops(animationJson),
              readTimelines(animationJson, animationBoneNames)));
    }

    return animations;
  }

  private Map<String, BoneTimeline> readTimelines(
      JsonObject animationJson, Map<String, String> animationBoneNames) {
    Map<String, BoneTimeline> timelines = new LinkedHashMap<>();

    for (Map.Entry<String, JsonElement> entry :
        Jsons.object(animationJson, "animators").entrySet()) {
      if (!entry.getValue().isJsonObject()) {
        continue;
      }

      JsonObject animatorJson = entry.getValue().getAsJsonObject();
      String boneName = animationBoneName(entry.getKey(), animatorJson, animationBoneNames);
      BoneTimeline timeline = readTimeline(animatorJson);

      timelines.put(boneName, timeline);
    }

    return timelines;
  }

  private BoneTimeline readTimeline(JsonObject animatorJson) {
    BoneTimeline timeline = new BoneTimeline();

    for (JsonElement keyframe : Jsons.array(animatorJson, "keyframes")) {
      if (keyframe.isJsonObject()) {
        readKeyframe(timeline, keyframe.getAsJsonObject());
      }
    }

    return timeline;
  }

  private static String animationBoneName(
      String animatorKey, JsonObject animatorJson, Map<String, String> animationBoneNames) {
    String animatorName = Jsons.string(animatorJson, "name", "");

    return firstNonBlank(
        animationBoneNames.get(animatorKey),
        animationBoneNames.get(animatorName),
        animationBoneNames.get(Names.key(animatorName)),
        Names.key(Jsons.string(animatorJson, "name", animatorKey)));
  }

  private void readKeyframe(BoneTimeline timeline, JsonObject keyframeJson) {
    String channel = Jsons.string(keyframeJson, "channel", "").toLowerCase(Locale.ROOT);
    if (channel.isBlank() || channel.equals("timeline")) {
      return;
    }

    double time = Jsons.dbl(keyframeJson, "time", 0);
    Interpolation interpolation =
        Interpolation.parse(Jsons.string(keyframeJson, "interpolation", "linear"));
    Vec3 start = keyframePoint(keyframeJson, channel, 0);
    Vec3 end = keyframePoint(keyframeJson, channel, 1);
    Keyframe keyframe = new Keyframe(time, start, end == null ? start : end, interpolation);

    switch (channel) {
      case "position", "translation", "move" -> timeline.position().add(keyframe);
      case "rotation", "rotate" -> timeline.rotation().add(keyframe);
      case "scale" -> timeline.scale().add(keyframe);
      default -> logger.fine("Unsupported channel " + channel);
    }
  }
}
