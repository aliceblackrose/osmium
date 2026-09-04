package osmium.render;

import java.util.Arrays;

/** Reusable allocation-free cache for light samples within one model lighting pass. */
final class LightSampleCache {
  private final long[] keys;
  private final int[] values;
  private final int[] generations;
  private final int mask;
  private int generation = 1;

  LightSampleCache(int expectedEntries) {
    int capacity = 4;
    int target = Math.max(4, expectedEntries * 2);
    while (capacity < target) {
      capacity <<= 1;
    }

    keys = new long[capacity];
    values = new int[capacity];
    generations = new int[capacity];
    mask = capacity - 1;
  }

  void beginPass() {
    if (generation == Integer.MAX_VALUE) {
      Arrays.fill(generations, 0);
      generation = 1;
      return;
    }
    generation++;
  }

  int get(long key) {
    int slot = slot(key);
    while (generations[slot] == generation) {
      if (keys[slot] == key) {
        return values[slot];
      }
      slot = (slot + 1) & mask;
    }
    return -1;
  }

  void put(long key, int value) {
    int slot = slot(key);
    while (generations[slot] == generation && keys[slot] != key) {
      slot = (slot + 1) & mask;
    }
    generations[slot] = generation;
    keys[slot] = key;
    values[slot] = value;
  }

  static long blockKey(int x, int y, int z) {
    return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
  }

  static int packLight(int block, int sky) {
    return (block & 0xF) | (sky & 0xF) << 4;
  }

  static int blockLight(int packed) {
    return packed & 0xF;
  }

  static int skyLight(int packed) {
    return packed >>> 4 & 0xF;
  }

  private int slot(long key) {
    long mixed = key;
    mixed ^= mixed >>> 33;
    mixed *= 0xff51afd7ed558ccdL;
    mixed ^= mixed >>> 33;
    mixed *= 0xc4ceb9fe1a85ec53L;
    mixed ^= mixed >>> 33;
    return (int) mixed & mask;
  }
}
