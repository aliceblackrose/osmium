package osmium.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class NamesTest {
  @Test
  void keyNormalizesUnsafeCharacters() {
    assertEquals("my_model.v2", Names.key("  My Model.V2!  "));
  }

  @Test
  void namespaceDoesNotRetainPathSeparators() {
    assertEquals("my_pack_models", Names.namespace("My Pack/Models"));
  }

  @Test
  void stemHandlesWindowsAndUnixPaths() {
    assertEquals("dragon", Names.stem("models/boss/dragon.bbmodel"));
    assertEquals("dragon", Names.stem("models\\boss\\dragon.bbmodel"));
  }
}
