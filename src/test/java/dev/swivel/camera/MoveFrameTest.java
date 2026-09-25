package dev.swivel.camera;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MoveFrameTest {
    private static final float EPSILON = 1.0E-3F;
    private static final float FORWARD = 0.0F;
    private static final float FORWARD_LEFT = -45.0F;

    @Test
    void followsTheCameraByDefault() {
        MoveFrame frame = new MoveFrame();
        assertEquals(10.0F, frame.yaw(10.0F, FORWARD, false), EPSILON);
        assertEquals(80.0F, frame.yaw(80.0F, FORWARD, false), EPSILON);
    }

    @Test
    void swivelKeepsTheDirectionWhileTheCameraSwings() {
        MoveFrame frame = new MoveFrame();
        assertEquals(10.0F, frame.yaw(10.0F, FORWARD, true), EPSILON);
        assertEquals(10.0F, frame.yaw(150.0F, FORWARD, true), EPSILON);
        assertEquals(10.0F, frame.yaw(-120.0F, FORWARD, true), EPSILON);
    }

    @Test
    void anotherKeyPicksUpTheCamera() {
        MoveFrame frame = new MoveFrame();
        frame.yaw(10.0F, FORWARD, true);
        assertEquals(150.0F, frame.yaw(150.0F, FORWARD_LEFT, true), EPSILON);
        assertEquals(170.0F, frame.yaw(170.0F, FORWARD, true), EPSILON);
    }

    @Test
    void stoppingForgetsTheDirection() {
        MoveFrame frame = new MoveFrame();
        frame.yaw(10.0F, FORWARD, true);
        frame.stop();
        assertEquals(90.0F, frame.yaw(90.0F, FORWARD, true), EPSILON);
    }

    @Test
    void holdingKeepsTheDirectionThroughKeyChanges() {
        MoveFrame frame = new MoveFrame();
        frame.hold(frame.current(20.0F));
        assertTrue(frame.held());
        assertEquals(20.0F, frame.yaw(100.0F, FORWARD, false), EPSILON);
        assertEquals(20.0F, frame.yaw(140.0F, FORWARD_LEFT, false), EPSILON);
        frame.release();
        assertFalse(frame.held());
        assertEquals(140.0F, frame.yaw(140.0F, FORWARD_LEFT, false), EPSILON);
    }

    @Test
    void releasingTheHoldLeavesASwivelRunGoingTheSameWay() {
        MoveFrame frame = new MoveFrame();
        frame.yaw(10.0F, FORWARD, true);
        frame.hold(frame.current(90.0F));
        assertEquals(10.0F, frame.yaw(200.0F, FORWARD, true), EPSILON);
        frame.release();
        assertEquals(10.0F, frame.yaw(200.0F, FORWARD, true), EPSILON);
    }

    @Test
    void resetDropsBothHoldAndLock() {
        MoveFrame frame = new MoveFrame();
        frame.yaw(10.0F, FORWARD, true);
        frame.hold(10.0F);
        frame.reset();
        assertFalse(frame.held());
        assertEquals(60.0F, frame.current(60.0F), EPSILON);
    }
}
