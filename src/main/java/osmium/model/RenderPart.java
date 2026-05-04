package osmium.model;

public record RenderPart(
    String id, String itemModelKey, String modelPath, int customModelData, Bone bone, Cube cube) {}
