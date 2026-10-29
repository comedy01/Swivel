package dev.swivel.camera;

public final class OrbitCamera {
    static final float MOUSE_SCALE = 0.15F;
    private static final double RECENTER_HALF_LIFE = 0.06;
    private static final double ZOOM_OUT_HALF_LIFE = 0.12;
    private static final double ZOOM_HALF_LIFE = 0.08;
    private static final double SHOULDER_HALF_LIFE = 0.07;
    private static final float RECENTER_SNAP = 0.5F;
    private static final double RECENTER_CANCEL = 1.0;

    private boolean active;
    private float yaw;
    private float pitch;
    private boolean recentering;
    private float recenterYaw;
    private boolean recenterPitch;
    private float recenterPitchTarget;
    private double distance = -1.0;
    private double zoom = -1.0;
    private double shoulder;

    public boolean active() {
        return active;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public boolean recentering() {
        return recentering;
    }

    public float recenterYaw() {
        return recenterYaw;
    }

    public void activate(float playerYaw, float playerPitch) {
        active = true;
        yaw = playerYaw;
        pitch = OrbitMath.clampPitch(playerPitch);
        recentering = false;
    }

    public void deactivate() {
        active = false;
        recentering = false;
    }

    public void turn(double xo, double yo) {
        yaw += (float) xo * MOUSE_SCALE;
        pitch = OrbitMath.clampPitch(pitch + (float) yo * MOUSE_SCALE);
        if (Math.abs(xo) > RECENTER_CANCEL || (recenterPitch && Math.abs(yo) > RECENTER_CANCEL)) {
            recentering = false;
        }
    }

    public void recenter(float bodyYaw) {
        recentering = true;
        recenterYaw = bodyYaw;
        recenterPitch = false;
    }

    public void returnTo(float targetYaw, float targetPitch) {
        recentering = true;
        recenterYaw = targetYaw;
        recenterPitch = true;
        recenterPitchTarget = OrbitMath.clampPitch(targetPitch);
    }

    public void stopRecentering() {
        recentering = false;
    }

    public void advance(double seconds) {
        if (!recentering) {
            return;
        }
        float gap = OrbitMath.wrap(recenterYaw - yaw);
        float pitchGap = recenterPitch ? recenterPitchTarget - pitch : 0.0F;
        if (Math.abs(gap) < RECENTER_SNAP && Math.abs(pitchGap) < RECENTER_SNAP) {
            yaw += gap;
            pitch += pitchGap;
            recentering = false;
        } else {
            float share = (float) OrbitMath.ease(seconds, RECENTER_HALF_LIFE);
            yaw += gap * share;
            pitch += pitchGap * share;
        }
    }

    public double smoothZoom(double wanted, double seconds) {
        if (zoom < 0.0) {
            zoom = wanted;
        } else {
            zoom += (wanted - zoom) * OrbitMath.ease(seconds, ZOOM_HALF_LIFE);
        }
        return zoom;
    }

    public double smoothDistance(double allowed, double seconds, boolean smooth) {
        if (!smooth || distance < 0.0 || allowed < distance) {
            distance = allowed;
        } else {
            distance += (allowed - distance) * OrbitMath.ease(seconds, ZOOM_OUT_HALF_LIFE);
        }
        return distance;
    }

    public double smoothShoulder(double wanted, double seconds) {
        shoulder += (wanted - shoulder) * OrbitMath.ease(seconds, SHOULDER_HALF_LIFE);
        if (Math.abs(wanted - shoulder) < 1.0E-3) {
            shoulder = wanted;
        }
        return shoulder;
    }

    public double shoulder() {
        return shoulder;
    }

    public double distance() {
        return distance;
    }

    public void resetDistance() {
        distance = -1.0;
        zoom = -1.0;
        shoulder = 0.0;
    }
}
