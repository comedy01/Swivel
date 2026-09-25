package dev.swivel.camera;

public final class FreeLook {
    private static final double RETURN_HALF_LIFE = 0.06;
    private static final float RETURN_SNAP = 0.5F;

    private boolean looking;
    private boolean returning;
    private float yaw;
    private float pitch;

    public boolean active() {
        return looking || returning;
    }

    public boolean looking() {
        return looking;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public void start(float viewYaw, float viewPitch) {
        if (!active()) {
            yaw = viewYaw;
            pitch = OrbitMath.clampPitch(viewPitch);
        }
        looking = true;
        returning = false;
    }

    public void turn(double xo, double yo) {
        yaw += (float) xo * OrbitCamera.MOUSE_SCALE;
        pitch = OrbitMath.clampPitch(pitch + (float) yo * OrbitCamera.MOUSE_SCALE);
    }

    public void release() {
        if (looking) {
            looking = false;
            returning = true;
        }
    }

    public void stop() {
        looking = false;
        returning = false;
    }

    public void advance(double seconds, float targetYaw, float targetPitch) {
        if (!returning) {
            return;
        }
        float yawGap = OrbitMath.wrap(targetYaw - yaw);
        float pitchGap = targetPitch - pitch;
        if (Math.abs(yawGap) < RETURN_SNAP && Math.abs(pitchGap) < RETURN_SNAP) {
            returning = false;
            return;
        }
        float share = (float) OrbitMath.ease(seconds, RETURN_HALF_LIFE);
        yaw += yawGap * share;
        pitch += pitchGap * share;
    }
}
