package dev.swivel.camera;

public final class OrbitMath {
    private OrbitMath() {
    }

    public static float wrap(float degrees) {
        float wrapped = degrees % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static float approach(float current, float target, float fraction, float minStep) {
        float gap = wrap(target - current);
        float step = Math.max(Math.abs(gap) * fraction, minStep);
        if (step >= Math.abs(gap)) {
            return current + gap;
        }
        return current + Math.copySign(step, gap);
    }

    public static float near(float reference, float target) {
        return reference + wrap(target - reference);
    }

    public static float inputYaw(float left, float forward) {
        return (float) Math.toDegrees(Math.atan2(-left, forward));
    }

    public static float left(float relativeYaw, float strength) {
        return (float) (-Math.sin(Math.toRadians(relativeYaw)) * strength);
    }

    public static float forward(float relativeYaw, float strength) {
        return (float) (Math.cos(Math.toRadians(relativeYaw)) * strength);
    }

    public static float yawTo(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    public static float pitchTo(double dx, double dy, double dz) {
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    public static float clampPitch(float pitch) {
        return Math.max(-90.0F, Math.min(90.0F, pitch));
    }

    public static double ease(double seconds, double halfLife) {
        if (seconds <= 0.0) {
            return 0.0;
        }
        return 1.0 - Math.pow(0.5, seconds / halfLife);
    }
}
