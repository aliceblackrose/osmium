package osmium.model;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public final class TextureAsset {
  private final String id;
  private final String name;
  private final String variable;
  private final String modelPath;
  private final byte[] embeddedBytes;
  private final Path sourcePath;
  private final int frameWidth;
  private final int frameHeight;
  private final int imageWidth;
  private final int imageHeight;
  private final int frameTime;
  private final boolean frameInterpolate;
  private final String frameOrder;

  public TextureAsset(
      String id,
      String name,
      String variable,
      String modelPath,
      byte[] embeddedBytes,
      Path sourcePath,
      int frameWidth,
      int frameHeight,
      int imageWidth,
      int imageHeight,
      int frameTime,
      boolean frameInterpolate,
      String frameOrder) {
    this.id = id;
    this.name = name;
    this.variable = variable;
    this.modelPath = modelPath;
    this.embeddedBytes = copyBytes(embeddedBytes);
    this.sourcePath = sourcePath;
    this.frameWidth = frameWidth;
    this.frameHeight = frameHeight;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.frameTime = frameTime;
    this.frameInterpolate = frameInterpolate;
    this.frameOrder = frameOrder;
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public String variable() {
    return variable;
  }

  public String modelPath() {
    return modelPath;
  }

  public byte[] embeddedBytes() {
    return copyBytes(embeddedBytes);
  }

  public Optional<byte[]> embeddedBytesOptional() {
    return Optional.ofNullable(embeddedBytes());
  }

  public Path sourcePath() {
    return sourcePath;
  }

  public Optional<Path> sourcePathOptional() {
    return Optional.ofNullable(sourcePath);
  }

  public int frameWidth() {
    return frameWidth;
  }

  public int frameHeight() {
    return frameHeight;
  }

  public int imageWidth() {
    return imageWidth;
  }

  public int imageHeight() {
    return imageHeight;
  }

  public int frameTime() {
    return frameTime;
  }

  public boolean frameInterpolate() {
    return frameInterpolate;
  }

  public String frameOrder() {
    return frameOrder;
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }

    if (!(object instanceof TextureAsset other)) {
      return false;
    }

    return frameWidth == other.frameWidth
        && frameHeight == other.frameHeight
        && imageWidth == other.imageWidth
        && imageHeight == other.imageHeight
        && frameTime == other.frameTime
        && frameInterpolate == other.frameInterpolate
        && Objects.equals(id, other.id)
        && Objects.equals(name, other.name)
        && Objects.equals(variable, other.variable)
        && Objects.equals(modelPath, other.modelPath)
        && Arrays.equals(embeddedBytes, other.embeddedBytes)
        && Objects.equals(sourcePath, other.sourcePath)
        && Objects.equals(frameOrder, other.frameOrder);
  }

  @Override
  public int hashCode() {
    int result =
        Objects.hash(
            id,
            name,
            variable,
            modelPath,
            sourcePath,
            frameWidth,
            frameHeight,
            imageWidth,
            imageHeight,
            frameTime,
            frameInterpolate,
            frameOrder);
    result = 31 * result + Arrays.hashCode(embeddedBytes);
    return result;
  }

  @Override
  public String toString() {
    return "TextureAsset["
        + "id="
        + id
        + ", name="
        + name
        + ", variable="
        + variable
        + ", modelPath="
        + modelPath
        + ", sourcePath="
        + sourcePath
        + ", frameWidth="
        + frameWidth
        + ", frameHeight="
        + frameHeight
        + ", imageWidth="
        + imageWidth
        + ", imageHeight="
        + imageHeight
        + ", frameTime="
        + frameTime
        + ", frameInterpolate="
        + frameInterpolate
        + ", frameOrder="
        + frameOrder
        + ']';
  }

  private static byte[] copyBytes(byte[] bytes) {
    return bytes == null ? null : bytes.clone();
  }
}
