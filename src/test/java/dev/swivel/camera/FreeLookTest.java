package dev.swivel.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FreeLookTest {
    private static final float EPSILON = 1.0E-3F;

    @Test
    void startsWhereTheCharacterLooks() {
        FreeLook look = new FreeLook();
        look.start(45.0F, -10.0F);
        assertTrue(look.looking());
        assertEquals(45.0F, look.yaw(), EPSILON);
        assertEquals(-10.0F, look.pitch(), EPSILON);
    }

    @Test
    void mouseTurnsTheViewLikeFirstPerson() {
        FreeLook look = new FreeLook();
        look.start(0.0F, 0.0F);
        look.turn(200.0, 1000.0);
        assertEquals(200.0F * OrbitCamera.MOUSE_SCALE, look.yaw(), EPSILON);
        assertEquals(90.0F, look.pitch(), EPSILON);
    }

    @Test
    void releasingGlidesBackToTheCharacter() {
        FreeLook look = new FreeLook();
        look.start(0.0F, 0.0F);
        look.turn(1000.0, -200.0);
        look.release();
        assertTrue(look.active());
        assertFalse(look.looking());

        look.advance(1.0 / 60.0, 0.0F, 0.0F);
        assertTrue(Math.abs(look.yaw()) > 1.0F, "should ease, not jump: " + look.yaw());
        for (int frame = 0; frame < 120; frame++) {
            look.advance(1.0 / 60.0, 0.0F, 0.0F);
        }
        assertFalse(look.active());
        assertEquals(0.0F, OrbitMath.wrap(look.yaw()), 0.5F);
    }

    @Test
    void theGlideFollowsTheCharacterWhileItTurns() {
        FreeLook look = new FreeLook();
        look.start(0.0F, 0.0F);
        look.turn(600.0, 0.0);
        look.release();
        for (int frame = 0; frame < 120; frame++) {
            look.advance(1.0 / 60.0, 30.0F + frame * 0.1F, 5.0F);
        }
        assertFalse(look.active());
    }

    @Test
    void holdingAgainMidGlideCarriesOnFromThere() {
        FreeLook look = new FreeLook();
        look.start(0.0F, 0.0F);
        look.turn(600.0, 0.0);
        look.release();
        look.advance(1.0 / 60.0, 0.0F, 0.0F);
        float midway = look.yaw();
        look.start(0.0F, 0.0F);
        assertTrue(look.looking());
        assertEquals(midway, look.yaw(), EPSILON);
    }

    @Test
    void stopEndsAtOnce() {
        FreeLook look = new FreeLook();
        look.start(0.0F, 0.0F);
        look.turn(600.0, 0.0);
        look.stop();
        assertFalse(look.active());
    }
}
