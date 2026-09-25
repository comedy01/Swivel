package dev.swivel.camera;

public final class MoveFrame {
    private static final float HEADING_CHANGE = 1.0F;

    private boolean held;
    private float heldYaw;
    private boolean locked;
    private float lockedYaw;
    private float lockedHeading;

    public void hold(float yaw) {
        held = true;
        heldYaw = yaw;
    }

    public boolean held() {
        return held;
    }

    public void release() {
        held = false;
    }

    public float yaw(float free, float heading, boolean swivel) {
        if (held) {
            locked = swivel;
            lockedYaw = heldYaw;
            lockedHeading = heading;
            return heldYaw;
        }
        if (!swivel) {
            locked = false;
            return free;
        }
        if (!locked || Math.abs(OrbitMath.wrap(heading - lockedHeading)) > HEADING_CHANGE) {
            locked = true;
            lockedYaw = free;
            lockedHeading = heading;
        }
        return lockedYaw;
    }

    public float current(float free) {
        if (held) {
            return heldYaw;
        }
        return locked ? lockedYaw : free;
    }

    public void stop() {
        locked = false;
    }

    public void reset() {
        held = false;
        locked = false;
    }
}
