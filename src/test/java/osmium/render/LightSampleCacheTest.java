package osmium.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class LightSampleCacheTest {
  @Test
  void cacheDeduplicatesSamplesWithinPassAndResetsLogicallyBetweenPasses() {
    LightSampleCache cache = new LightSampleCache(8);
    long first = LightSampleCache.blockKey(10, 64, -4);
    long second = LightSampleCache.blockKey(11, 64, -4);

    cache.beginPass();
    assertEquals(-1, cache.get(first));
    cache.put(first, LightSampleCache.packLight(7, 12));
    cache.put(second, LightSampleCache.packLight(2, 15));

    assertEquals(7, LightSampleCache.blockLight(cache.get(first)));
    assertEquals(12, LightSampleCache.skyLight(cache.get(first)));
    assertEquals(2, LightSampleCache.blockLight(cache.get(second)));
    assertEquals(15, LightSampleCache.skyLight(cache.get(second)));

    cache.beginPass();
    assertEquals(-1, cache.get(first));
    assertEquals(-1, cache.get(second));
  }

  @Test
  void blockKeysPreserveSignedCoordinates() {
    long a = LightSampleCache.blockKey(-1, -64, -1);
    long b = LightSampleCache.blockKey(-1, -63, -1);
    long c = LightSampleCache.blockKey(1, -64, -1);

    org.junit.jupiter.api.Assertions.assertNotEquals(a, b);
    org.junit.jupiter.api.Assertions.assertNotEquals(a, c);
  }
}
