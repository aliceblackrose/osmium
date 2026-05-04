package osmium.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.joml.Quaternionf;
import osmium.math.Transforms;
import osmium.math.Vec3;

public final class Bone {
  private final String name, rawName, uuid;
  private final Vec3 origin, localPosition, rotationDegrees;
  private final Quaternionf localRotation;
  private final boolean visible;
  private final boolean hitbox;
  private final List<String> cubeIds = new ArrayList<>();
  private final List<Bone> children = new ArrayList<>();
  private Bone parent;

  public Bone(
      String name,
      String rawName,
      String uuid,
      Vec3 origin,
      Vec3 parentOrigin,
      Vec3 rotationDegrees,
      boolean visible) {
    this.name = name;
    this.rawName = rawName;
    this.uuid = uuid;
    this.origin = origin;
    this.rotationDegrees = rotationDegrees;
    this.visible = visible;
    this.hitbox = rawName.toLowerCase(Locale.ROOT).contains("hitbox");
    this.localPosition = Transforms.bbLocalToMc(origin.subtract(parentOrigin));
    this.localRotation = Transforms.staticRotation(rotationDegrees);
  }

  public String name() {
    return name;
  }

  public String rawName() {
    return rawName;
  }

  public String uuid() {
    return uuid;
  }

  public Vec3 origin() {
    return origin;
  }

  public Vec3 localPosition() {
    return localPosition;
  }

  public Vec3 rotationDegrees() {
    return rotationDegrees;
  }

  public Quaternionf localRotation() {
    return new Quaternionf(localRotation);
  }

  public boolean visible() {
    return visible;
  }

  public boolean hitbox() {
    return hitbox;
  }

  public Bone parent() {
    return parent;
  }

  public void setParent(Bone parent) {
    this.parent = parent;
  }

  public void addCube(String id) {
    cubeIds.add(id);
  }

  public void addChild(Bone child) {
    child.setParent(this);
    children.add(child);
  }

  public List<String> cubeIds() {
    return Collections.unmodifiableList(cubeIds);
  }

  public List<Bone> children() {
    return Collections.unmodifiableList(children);
  }
}
