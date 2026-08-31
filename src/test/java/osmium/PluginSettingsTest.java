package osmium;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class PluginSettingsTest {
  @Test
  void legacyInterpolationDefaultMigratesToSmoothWindow() {
    assertEquals(3, PluginSettings.normalizeInterpolationDuration(1));
  }

  @Test
  void explicitInterpolationChoicesRemainUnchanged() {
    assertEquals(0, PluginSettings.normalizeInterpolationDuration(0));
    assertEquals(2, PluginSettings.normalizeInterpolationDuration(2));
    assertEquals(4, PluginSettings.normalizeInterpolationDuration(4));
  }

  @Test
  void negativeInterpolationDurationIsDisabled() {
    assertEquals(0, PluginSettings.normalizeInterpolationDuration(-1));
  }
}
