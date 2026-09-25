package dev.swivel.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OrbitCameraTest {
    private static final float EPSILON = 1.0E-3F;

    @Test
    void startsLookingWhereThePlayerLooks() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(123.0F, 20.0F);
        assertTrue(orbit.active());
        assertEquals(123.0F, orbit.yaw(), EPSILON);
        assertEquals(20.0F, orbit.pitch(), EPSILON);
    }

    @Test
    void mouseTurnsLikeFirstPerson() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(0.0F, 0.0F);
        orbit.turn(100.0, -50.0);
        assertEquals(100.0F * OrbitCamera.MOUSE_SCALE, orbit.yaw(), EPSILON);
        assertEquals(-50.0F * OrbitCamera.MOUSE_SCALE, orbit.pitch(), EPSILON);
    }

    @Test
    void pitchStopsAtStraightUpAndDown() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(0.0F, 80.0F);
        orbit.turn(0.0, 1000.0);
        assertEquals(90.0F, orbit.pitch(), EPSILON);
        orbit.turn(0.0, -5000.0);
        assertEquals(-90.0F, orbit.pitch(), EPSILON);
    }

    @Test
    void recenterSwingsBehindThePlayer() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(0.0F, 0.0F);
        orbit.turn(1000.0, 0.0);
        orbit.recenter(0.0F);
        for (int frame = 0; frame < 120; frame++) {
            orbit.advance(1.0 / 60.0);
        }
        assertFalse(orbit.recentering());
        assertEquals(0.0F, OrbitMath.wrap(orbit.yaw()), EPSILON);
    }

    @Test
    void returnToRestoresPitchToo() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(30.0F, 10.0F);
        orbit.turn(-800.0, 200.0);
        orbit.returnTo(30.0F, 10.0F);
        assertEquals(30.0F, orbit.recenterYaw(), EPSILON);
        for (int frame = 0; frame < 120; frame++) {
            orbit.advance(1.0 / 60.0);
        }
        assertFalse(orbit.recentering());
        assertEquals(30.0F, OrbitMath.wrap(orbit.yaw()), EPSILON);
        assertEquals(10.0F, orbit.pitch(), EPSILON);
    }

    @Test
    void recenterLeavesPitchAlone() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(0.0F, 0.0F);
        orbit.turn(600.0, 150.0);
        float pitch = orbit.pitch();
        orbit.recenter(0.0F);
        for (int frame = 0; frame < 120; frame++) {
            orbit.advance(1.0 / 60.0);
        }
        assertEquals(pitch, orbit.pitch(), EPSILON);
    }

    @Test
    void stopRecenteringHaltsTheSwing() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(0.0F, 0.0F);
        orbit.turn(600.0, 0.0);
        orbit.recenter(0.0F);
        orbit.stopRecentering();
        float yaw = orbit.yaw();
        orbit.advance(1.0 / 60.0);
        assertEquals(yaw, orbit.yaw(), EPSILON);
    }

    @Test
    void movingTheMouseCancelsRecentering() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.activate(0.0F, 0.0F);
        orbit.turn(1000.0, 0.0);
        orbit.recenter(0.0F);
        orbit.turn(50.0, 0.0);
        assertFalse(orbit.recentering());
    }

    @Test
    void wallsPullTheCameraInAtOnce() {
        OrbitCamera orbit = new OrbitCamera();
        assertEquals(4.0, orbit.smoothDistance(4.0, 0.016, true), 1.0E-9);
        assertEquals(1.5, orbit.smoothDistance(1.5, 0.016, true), 1.0E-9);
    }

    @Test
    void theCameraGlidesBackOut() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.smoothDistance(1.0, 0.016, true);
        double next = orbit.smoothDistance(4.0, 0.016, true);
        assertTrue(next > 1.0 && next < 4.0, "should ease, got " + next);
        for (int frame = 0; frame < 120; frame++) {
            next = orbit.smoothDistance(4.0, 1.0 / 60.0, true);
        }
        assertEquals(4.0, next, 1.0E-3);
    }

    @Test
    void smoothingCanBeTurnedOff() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.smoothDistance(1.0, 0.016, false);
        assertEquals(4.0, orbit.smoothDistance(4.0, 0.016, false), 1.0E-9);
    }

    @Test
    void resetForgetsTheSmoothedDistance() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.smoothDistance(1.0, 0.016, true);
        orbit.resetDistance();
        assertEquals(4.0, orbit.smoothDistance(4.0, 0.016, true), 1.0E-9);
    }
}
