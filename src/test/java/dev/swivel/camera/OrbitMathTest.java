package dev.swivel.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrbitMathTest {
    private static final float EPSILON = 1.0E-3F;

    @Test
    void wrapKeepsAnglesBetweenMinusAndPlusHalfTurn() {
        assertEquals(0.0F, OrbitMath.wrap(360.0F), EPSILON);
        assertEquals(-90.0F, OrbitMath.wrap(270.0F), EPSILON);
        assertEquals(90.0F, OrbitMath.wrap(-270.0F), EPSILON);
        assertEquals(-180.0F, OrbitMath.wrap(180.0F), EPSILON);
        assertEquals(10.0F, OrbitMath.wrap(730.0F), EPSILON);
    }

    @Test
    void approachTakesTheShorterWayRound() {
        float next = OrbitMath.approach(170.0F, -170.0F, 0.5F, 0.0F);
        assertEquals(180.0F, next, EPSILON);
    }

    @Test
    void approachNeverOvershoots() {
        assertEquals(95.0F, OrbitMath.approach(90.0F, 95.0F, 0.5F, 10.0F), EPSILON);
        assertEquals(90.0F, OrbitMath.approach(90.0F, 90.0F, 0.5F, 10.0F), EPSILON);
    }

    @Test
    void approachMovesAtLeastTheMinimumStep() {
        assertEquals(10.0F, OrbitMath.approach(0.0F, 100.0F, 0.01F, 10.0F), EPSILON);
    }

    @Test
    void fullFractionTurnsInstantly() {
        assertEquals(-135.0F, OrbitMath.approach(0.0F, -135.0F, 1.0F, 0.0F), EPSILON);
    }

    @Test
    void approachKeepsTheAngleContinuous() {
        float next = OrbitMath.approach(725.0F, 0.0F, 1.0F, 0.0F);
        assertEquals(720.0F, next, EPSILON);
    }

    @Test
    void nearWritesTheTargetCloseToTheReference() {
        assertEquals(700.0F, OrbitMath.near(710.0F, -20.0F), EPSILON);
        assertEquals(-190.0F, OrbitMath.near(-170.0F, 170.0F), EPSILON);
    }

    @Test
    void inputYawMatchesMinecraftKeys() {
        assertEquals(0.0F, OrbitMath.inputYaw(0.0F, 1.0F), EPSILON);
        assertEquals(-90.0F, OrbitMath.inputYaw(1.0F, 0.0F), EPSILON);
        assertEquals(90.0F, OrbitMath.inputYaw(-1.0F, 0.0F), EPSILON);
        assertEquals(180.0F, Math.abs(OrbitMath.inputYaw(0.0F, -1.0F)), EPSILON);
        assertEquals(-45.0F, OrbitMath.inputYaw(1.0F, 1.0F), EPSILON);
    }

    @Test
    void leftAndForwardUndoInputYaw() {
        for (float left : new float[] {-1.0F, 0.0F, 0.6F, 1.0F}) {
            for (float forward : new float[] {-1.0F, 0.0F, 0.8F, 1.0F}) {
                float strength = (float) Math.sqrt(left * left + forward * forward);
                if (strength == 0.0F) {
                    continue;
                }
                float yaw = OrbitMath.inputYaw(left, forward);
                assertEquals(left, OrbitMath.left(yaw, strength), EPSILON, "left for " + left + "," + forward);
                assertEquals(forward, OrbitMath.forward(yaw, strength), EPSILON, "forward for " + left + "," + forward);
            }
        }
    }

    @Test
    void yawToMatchesMinecraftLookDirection() {
        assertEquals(0.0F, OrbitMath.yawTo(0.0, 1.0), EPSILON);
        assertEquals(90.0F, OrbitMath.yawTo(-1.0, 0.0), EPSILON);
        assertEquals(-90.0F, OrbitMath.yawTo(1.0, 0.0), EPSILON);
        assertEquals(180.0F, Math.abs(OrbitMath.yawTo(0.0, -1.0)), EPSILON);
    }

    @Test
    void pitchIsPositiveWhenLookingDown() {
        assertEquals(45.0F, OrbitMath.pitchTo(1.0, -1.0, 0.0), EPSILON);
        assertEquals(-90.0F, OrbitMath.pitchTo(0.0, 5.0, 0.0), EPSILON);
        assertEquals(0.0F, OrbitMath.pitchTo(3.0, 0.0, 4.0), EPSILON);
    }

    @Test
    void easeIsFrameRateIndependent() {
        double oneStep = OrbitMath.ease(0.1, 0.05);
        double twoSteps = 1.0 - (1.0 - OrbitMath.ease(0.05, 0.05)) * (1.0 - OrbitMath.ease(0.05, 0.05));
        assertEquals(oneStep, twoSteps, 1.0E-9);
        assertEquals(0.5, OrbitMath.ease(0.05, 0.05), 1.0E-9);
        assertEquals(0.0, OrbitMath.ease(0.0, 0.05), 1.0E-9);
        assertTrue(OrbitMath.ease(10.0, 0.05) > 0.999);
    }
}
