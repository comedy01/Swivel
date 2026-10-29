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

    @Test
    void zoomStartsAtTheWantedDistance() {
        OrbitCamera orbit = new OrbitCamera();
        assertEquals(6.0, orbit.smoothZoom(6.0, 0.016), 1.0E-9);
    }

    @Test
    void zoomGlidesBothWays() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.smoothZoom(4.0, 0.016);
        double closer = orbit.smoothZoom(2.0, 0.016);
        assertTrue(closer < 4.0 && closer > 2.0, "should ease in, got " + closer);
        double further = orbit.smoothZoom(8.0, 0.016);
        assertTrue(further > closer && further < 8.0, "should ease out, got " + further);
        for (int frame = 0; frame < 120; frame++) {
            further = orbit.smoothZoom(8.0, 1.0 / 60.0);
        }
        assertEquals(8.0, further, 1.0E-3);
    }

    @Test
    void resetForgetsTheZoom() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.smoothZoom(2.0, 0.016);
        orbit.resetDistance();
        assertEquals(8.0, orbit.smoothZoom(8.0, 0.016), 1.0E-9);
    }

    @Test
    void theShoulderOffsetGlidesInAndOut() {
        OrbitCamera orbit = new OrbitCamera();
        double side = orbit.smoothShoulder(0.6, 1.0 / 60.0);
        assertTrue(side > 0.0 && side < 0.6, "should ease, got " + side);
        for (int frame = 0; frame < 60; frame++) {
            side = orbit.smoothShoulder(0.6, 1.0 / 60.0);
        }
        assertEquals(0.6, side, 1.0E-9);
        for (int frame = 0; frame < 60; frame++) {
            side = orbit.smoothShoulder(0.0, 1.0 / 60.0);
        }
        assertEquals(0.0, side, 1.0E-9);
    }

    @Test
    void resetDropsTheShoulderOffset() {
        OrbitCamera orbit = new OrbitCamera();
        orbit.smoothShoulder(0.6, 1.0);
        orbit.resetDistance();
        assertEquals(0.0, orbit.shoulder(), 1.0E-9);
    }
}
