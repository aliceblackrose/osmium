package osmium.model;

public record Face(
    String direction, double u1, double v1, double u2, double v2, int rotation, String textureKey) {
  public boolean empty() {
    return textureKey == null
        || textureKey.isBlank()
        || Math.abs(u1 - u2) < 1e-6
        || Math.abs(v1 - v2) < 1e-6;
  }
}
