package dev.swivel.client;

import dev.swivel.camera.FreeLook;
import dev.swivel.camera.MoveFrame;
import dev.swivel.camera.OrbitCamera;
import dev.swivel.camera.OrbitMath;
import dev.swivel.config.SwivelConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;

public final class SwivelRuntime {
    private static final int ACTION_HOLD_TICKS = 8;
    private static final float FLIGHT_FRACTION = 0.5F;
    private static final float FLIGHT_MIN_STEP = 4.0F;
    private static final float LEVEL_PITCH_RATE = 0.3F;
    private static final float KEY_THRESHOLD = 0.1F;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double LIFT_MARGIN = 0.1;
    private static final double AIM_DISTANCE = 96.0;

    private static final OrbitCamera ORBIT = new OrbitCamera();
    private static final FreeLook FREE_LOOK = new FreeLook();
    private static final MoveFrame MOVE = new MoveFrame();

    private static WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
    private static CameraType lastCameraType;
    private static float heldViewYaw;
    private static float heldViewPitch;
    private static long lastFrame;
    private static double frameSeconds;
    private static int actionTicks;
    private static long crosshairFrames;
    private static LocalPlayer aimed;
    private static float savedYRot;
    private static float savedXRot;
    private static float savedYRotO;
    private static float savedXRotO;

    private SwivelRuntime() {
    }

    public record Steer(float left, float forward, boolean forwardKey, boolean backwardKey, boolean leftKey,
                        boolean rightKey) {
    }

    public static OrbitCamera orbit() {
        return ORBIT;
    }

    public static FreeLook freeLook() {
        return FREE_LOOK;
    }

    public static boolean active() {
        return ORBIT.active();
    }

    public static long crosshairFrames() {
        return crosshairFrames;
    }

    public static void updateState(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        ClientLevel level = mc.level;
        if (lastLevel.get() != level) {
            lastLevel = new WeakReference<>(level);
            ORBIT.deactivate();
            ORBIT.resetDistance();
            FREE_LOOK.stop();
            MOVE.reset();
            actionTicks = 0;
        }

        long now = System.nanoTime();
        frameSeconds = lastFrame == 0L ? 0.0 : Math.min(MAX_FRAME_SECONDS, (now - lastFrame) / 1.0E9);
        lastFrame = now;

        boolean wanted = wanted(mc);
        if (wanted && !ORBIT.active()) {
            ORBIT.activate(player.getYRot(), player.getXRot());
            MOVE.reset();
        } else if (!wanted && ORBIT.active()) {
            ORBIT.deactivate();
            MOVE.reset();
            if (player != null && SwivelClient.config().alignOnExit()
                    && mc.options.getCameraType().isFirstPerson() && mc.getCameraEntity() == player) {
                snap(player, ORBIT.yaw(), ORBIT.pitch());
            }
        }
        if (mc.options.getCameraType().isFirstPerson()) {
            ORBIT.resetDistance();
        }
        updateFreeLook(mc);
        ORBIT.advance(frameSeconds);
        if (player != null) {
            FREE_LOOK.advance(frameSeconds, player.getViewYRot(partialTicks), player.getViewXRot(partialTicks));
        }
    }

    private static boolean wanted(Minecraft mc) {
        LocalPlayer player = mc.player;
        return SwivelClient.config().enabled()
                && player != null
                && mc.level != null
                && mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                && mc.getCameraEntity() == player
                && !player.isSleeping();
    }

    private static void updateFreeLook(Minecraft mc) {
        LocalPlayer player = mc.player;
        KeyMapping key = SwivelClient.freeLookKey();
        boolean own = player != null && mc.getCameraEntity() == player;
        boolean held = own && key != null && key.isDown();

        CameraType cameraType = mc.options.getCameraType();
        if (cameraType != lastCameraType || !own) {
            lastCameraType = cameraType;
            FREE_LOOK.stop();
        }

        if (ORBIT.active()) {
            FREE_LOOK.stop();
            if (held && !MOVE.held()) {
                MOVE.hold(MOVE.current(freeYaw()));
                heldViewYaw = ORBIT.yaw();
                heldViewPitch = ORBIT.pitch();
                ORBIT.stopRecentering();
            } else if (!held && MOVE.held()) {
                MOVE.release();
                ORBIT.returnTo(heldViewYaw, heldViewPitch);
            }
            return;
        }

        if (held && !FREE_LOOK.looking()) {
            FREE_LOOK.start(player.getYRot(), player.getXRot());
        } else if (!held && FREE_LOOK.looking()) {
            FREE_LOOK.release();
        }
    }

    private static float freeYaw() {
        return ORBIT.recentering() ? ORBIT.recenterYaw() : ORBIT.yaw();
    }

    public static float cameraYaw(Entity entity, float vanilla) {
        if (entity != Minecraft.getInstance().player) {
            return vanilla;
        }
        if (ORBIT.active()) {
            return ORBIT.yaw();
        }
        return FREE_LOOK.active() ? FREE_LOOK.yaw() : vanilla;
    }

    public static float cameraPitch(Entity entity, float vanilla) {
        if (entity != Minecraft.getInstance().player) {
            return vanilla;
        }
        if (ORBIT.active()) {
            return ORBIT.pitch();
        }
        return FREE_LOOK.active() ? FREE_LOOK.pitch() : vanilla;
    }

    public interface Collision {
        float allowed(float distance);
    }

    public static float cameraLift(BlockGetter level, Entity entity, Vec3 eye, Vec3 up) {
        SwivelConfig config = SwivelClient.config();
        if (!ORBIT.active() || level == null || entity == null || !config.raiseCamera()) {
            return 0.0F;
        }
        double wanted = config.height();
        if (wanted <= 0.0) {
            return 0.0F;
        }
        Vec3 top = eye.add(up.scale(wanted + LIFT_MARGIN));
        HitResult hit = level.clip(new ClipContext(eye, top, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) {
            return (float) wanted;
        }
        return (float) Math.max(0.0, Math.min(wanted, hit.getLocation().distanceTo(eye) - LIFT_MARGIN));
    }

    public static float cameraDistance(float vanilla, Collision collision) {
        SwivelConfig config = SwivelClient.config();
        if (!config.enabled()) {
            return collision.allowed(vanilla);
        }
        float wanted = (float) (vanilla * config.distance() / SwivelConfig.DEFAULT_DISTANCE);
        float allowed = collision.allowed(wanted);
        return (float) ORBIT.smoothDistance(allowed, frameSeconds, config.smoothCollisions());
    }

    public static boolean turnCamera(double xo, double yo) {
        updateFreeLook(Minecraft.getInstance());
        if (ORBIT.active()) {
            ORBIT.turn(xo, yo);
            return true;
        }
        if (FREE_LOOK.looking()) {
            FREE_LOOK.turn(xo, yo);
            return true;
        }
        return false;
    }

    public static boolean crosshair(boolean vanilla) {
        if (FREE_LOOK.active() && SwivelClient.config().hideCrosshairInFreeLook()) {
            return false;
        }
        if (vanilla) {
            return true;
        }
        if (ORBIT.active() && SwivelClient.config().crosshair()) {
            crosshairFrames++;
            return true;
        }
        return false;
    }

    public static void withAim(float partialTicks, Runnable pick) {
        beginAim(partialTicks);
        try {
            pick.run();
        } finally {
            endAim();
        }
    }

    public static void beginAim(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        aimed = null;
        if (!ORBIT.active() || player == null || mc.level == null || mc.getCameraEntity() != player) {
            return;
        }
        savedYRot = player.getYRot();
        savedXRot = player.getXRot();
        savedYRotO = player.yRotO;
        savedXRotO = player.xRotO;
        float[] aim = aimRotation(mc, player, partialTicks);
        float yaw = OrbitMath.near(savedYRot, aim[0]);
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setXRot(aim[1]);
        player.xRotO = player.getXRot();
        aimed = player;
    }

    public static void endAim() {
        LocalPlayer player = aimed;
        if (player == null) {
            return;
        }
        aimed = null;
        player.setYRot(savedYRot);
        player.yRotO = savedYRotO;
        player.setXRot(savedXRot);
        player.xRotO = savedXRotO;
    }

    public static void beforeKeybinds() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (actionTicks > 0) {
            actionTicks--;
        }

        KeyMapping toggle = SwivelClient.toggleKey();
        while (toggle != null && toggle.consumeClick()) {
            SwivelConfig config = SwivelClient.config();
            config.setEnabled(!config.enabled());
            SwivelClient.saveConfig();
            if (player != null) {
                Messages.overlay(player, Component.translatable(
                        config.enabled() ? "swivel.message.on" : "swivel.message.off"));
            }
        }

        KeyMapping recenter = SwivelClient.recenterKey();
        while (recenter != null && recenter.consumeClick()) {
            if (ORBIT.active() && player != null) {
                ORBIT.recenter(player.getYRot());
            }
        }

        if (!ORBIT.active() || player == null) {
            return;
        }
        if (player.isUsingItem() && !mc.options.keyUse.isDown()) {
            faceAim(mc, player, true);
        } else if (aiming(mc, player)) {
            faceAim(mc, player, false);
        }
    }

    public static void beforeAction() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ORBIT.active() || player == null || mc.level == null) {
            return;
        }
        faceAim(mc, player, true);
        actionTicks = ACTION_HOLD_TICKS;
    }

    public static Steer steer(Object input, float left, float forward) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!ORBIT.active() || player == null || player.input != input) {
            return null;
        }
        Entity vehicle = player.getVehicle();
        boolean mounted = vehicle instanceof LivingEntity;
        if (vehicle != null && !mounted) {
            MOVE.stop();
            return null;
        }
        float viewPitch = MOVE.held() ? heldViewPitch : ORBIT.pitch();
        if (player.isFallFlying()) {
            MOVE.stop();
            float course = MOVE.current(freeYaw());
            player.setYRot(OrbitMath.approach(player.getYRot(), course, FLIGHT_FRACTION, FLIGHT_MIN_STEP));
            player.setXRot(player.getXRot() + (viewPitch - player.getXRot()) * FLIGHT_FRACTION);
            return null;
        }

        SwivelConfig config = SwivelClient.config();
        float strength = Math.min(1.0F, (float) Math.sqrt(left * left + forward * forward));
        if (strength < 1.0E-4F) {
            MOVE.stop();
            return null;
        }
        boolean swimming = player.isInWater() || player.isSwimming();
        float heading = OrbitMath.inputYaw(left, forward);
        float base = MOVE.yaw(freeYaw(), heading, config.swivelWhileRunning() && !swimming);
        float wanted = base + heading;

        if (!aiming(mc, player)) {
            float facing = config.strafe() ? base : wanted;
            player.setYRot(OrbitMath.approach(player.getYRot(), facing, config.turnFraction(), config.turnMinStep()));
            float pitchTarget = mounted || swimming
                    ? viewPitch * (float) Math.cos(Math.toRadians(heading))
                    : 0.0F;
            player.setXRot(player.getXRot() + (pitchTarget - player.getXRot()) * LEVEL_PITCH_RATE);
        }

        if (mounted) {
            return new Steer(0.0F, strength, true, false, false, false);
        }

        float relative = OrbitMath.wrap(wanted - player.getYRot());
        float newLeft = OrbitMath.left(relative, strength);
        float newForward = OrbitMath.forward(relative, strength);
        return new Steer(newLeft, newForward,
                newForward > KEY_THRESHOLD, newForward < -KEY_THRESHOLD,
                newLeft > KEY_THRESHOLD, newLeft < -KEY_THRESHOLD);
    }

    private static boolean aiming(Minecraft mc, LocalPlayer player) {
        return player.isUsingItem()
                || mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown()
                || actionTicks > 0;
    }

    private static void faceAim(Minecraft mc, LocalPlayer player, boolean announce) {
        float[] aim = aimRotation(mc, player, 1.0F);
        float yaw = OrbitMath.near(player.getYRot(), aim[0]);
        player.setYRot(yaw);
        player.setXRot(aim[1]);
        player.yHeadRot = yaw;
        if (announce) {
            Rotations.announce(mc, player);
        }
    }

    private static float[] aimRotation(Minecraft mc, LocalPlayer player, float partialTicks) {
        Camera camera = Cameras.main(mc);
        Vec3 from = Cameras.position(camera);
        Vec3 look = Cameras.forward(camera);
        Vec3 eye = player.getEyePosition(partialTicks);

        Vec3 far = from.add(look.scale(AIM_DISTANCE));
        HitResult block = mc.level.clip(
                new ClipContext(from, far, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 target = block.getType() == HitResult.Type.MISS ? far : block.getLocation();

        double limit = from.distanceToSqr(target);
        double reach = Math.max(0.0, eye.subtract(from).dot(look)) + Reach.of(player) + 1.0;
        Vec3 end = from.add(look.scale(Math.min(reach, Math.sqrt(limit))));
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(
                player, from, end, new AABB(from, end).inflate(1.0), other -> targetable(player, other), limit);
        if (entity != null) {
            target = entity.getLocation();
        }

        Vec3 delta = target.subtract(eye);
        return new float[] {OrbitMath.yawTo(delta.x, delta.z), OrbitMath.pitchTo(delta.x, delta.y, delta.z)};
    }

    private static boolean targetable(LocalPlayer player, Entity other) {
        return !other.isSpectator() && other.isPickable() && other.getRootVehicle() != player.getRootVehicle();
    }

    private static void snap(LocalPlayer player, float yaw, float pitch) {
        float near = OrbitMath.near(player.getYRot(), yaw);
        player.setYRot(near);
        player.yRotO = near;
        player.setXRot(pitch);
        player.xRotO = player.getXRot();
    }
}
