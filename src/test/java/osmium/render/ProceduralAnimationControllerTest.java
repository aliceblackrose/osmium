package osmium.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ProceduralAnimationControllerTest {
  private static final double EPSILON = 1.0E-6;

  @Test
  void lookAngleSmoothingUsesShortestTurnAcrossWrapBoundary() {
    assertEquals(175.0, ProceduralAnimationController.smoothAngleDegrees(170, -170, 0.25), EPSILON);
  }

  @Test
  void scalarSmoothingEasesTowardTarget() {
    assertEquals(5.0, ProceduralAnimationController.smoothValue(0, 20, 0.25), EPSILON);
  }
}
