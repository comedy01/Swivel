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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Locale;

public final class SwivelRuntime {
    private static final int ACTION_HOLD_TICKS = 8;
    private static final float FLIGHT_FRACTION = 0.5F;
    private static final float FLIGHT_MIN_STEP = 4.0F;
    private static final float LEVEL_PITCH_RATE = 0.3F;
    private static final float KEY_THRESHOLD = 0.1F;
    private static final double MAX_FRAME_SECONDS = 0.25;
    private static final double CLEARANCE = 0.2;
    private static final double CLEAR_STEP = 0.05;
    private static final double AIM_DISTANCE = 96.0;
    private static final int ZOOM_REPEAT_DELAY = 6;
    private static final int ZOOM_REPEAT_EVERY = 2;
    private static final double SHOULDER_OFFSET = 0.6;
    private static final double FADE_START = 1.6;
    private static final double FADE_END = 0.6;
    private static final float MIN_ALPHA = 0.2F;
    private static final float LAYER_ALPHA = 0.6F;

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
    private static int zoomHeldTicks;
    private static boolean wasScoping;
    private static CameraType spyglassReturn;
    private static boolean aimCamera;
    private static double cameraDistance = -1.0;
    private static WeakReference<Object> fadedState = new WeakReference<>(null);
    private static float fadedAlpha = 1.0F;
    private static long fadedFrames;

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

    public static Vec3 cameraPosition() {
        return Cameras.position(Cameras.main(Minecraft.getInstance()));
    }

    public static Vec3 cameraForward() {
        return Cameras.forward(Cameras.main(Minecraft.getInstance()));
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
            wasScoping = false;
            spyglassReturn = null;
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
            cameraDistance = -1.0;
        }
        updateFreeLook(mc);
        aimCamera = ORBIT.active() && SwivelClient.config().aimCamera() != SwivelConfig.AimCamera.OFF
                && aimingWeapon(player);
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
        return (float) clearReach(level, eye, up, wanted);
    }

    public static float cameraSide(Camera camera, BlockGetter level, Entity entity, Vec3 from, Vec3 up) {
        double wanted = aimCamera ? SwivelClient.config().aimCamera().side() * SHOULDER_OFFSET : 0.0;
        double side = ORBIT.smoothShoulder(wanted, frameSeconds);
        if (!ORBIT.active() || level == null || entity == null || side == 0.0) {
            return 0.0F;
        }
        Vec3 right = Cameras.forward(camera).cross(up).normalize().scale(Math.signum(side));
        return (float) Math.copySign(clearReach(level, from, right, Math.abs(side)), side);
    }

    // How far the camera can slide from 'from' along 'direction' before a box around it touches a block. Vanilla
    // starts its zoom check from the eyes, which always have about this much room, and its rays begin inside the
    // ceiling (and let the camera through) when the start point is any closer.
    private static double clearReach(BlockGetter level, Vec3 from, Vec3 direction, double wanted) {
        double reach = 0.0;
        while (reach < wanted) {
            double next = Math.min(wanted, reach + CLEAR_STEP);
            if (blocked(level, from.add(direction.scale(next)))) {
                break;
            }
            reach = next;
        }
        return reach;
    }

    private static boolean blocked(BlockGetter level, Vec3 point) {
        AABB box = new AABB(point, point).inflate(CLEARANCE);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            AABB local = box.move(-pos.getX(), -pos.getY(), -pos.getZ());
            for (AABB shape : level.getBlockState(pos).getCollisionShape(level, pos).toAabbs()) {
                if (shape.intersects(local)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean aimCamera() {
        return aimCamera;
    }

    public static float cameraDistance(float vanilla, Collision collision) {
        SwivelConfig config = SwivelClient.config();
        if (!config.enabled()) {
            float allowed = collision.allowed(vanilla);
            cameraDistance = allowed;
            return allowed;
        }
        double zoom = ORBIT.smoothZoom(aimCamera ? config.aimDistance() : config.distance(), frameSeconds);
        float wanted = (float) (vanilla * zoom / SwivelConfig.DEFAULT_DISTANCE);
        float allowed = collision.allowed(wanted);
        cameraDistance = ORBIT.smoothDistance(allowed, frameSeconds, config.smoothCollisions());
        return (float) cameraDistance;
    }

    public static float playerAlpha(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (!SwivelClient.config().fadePlayer() || entity == null || entity != mc.player
                || mc.getCameraEntity() != entity || mc.options.getCameraType().isFirstPerson()
                || cameraDistance < 0.0 || cameraDistance >= FADE_START) {
            return 1.0F;
        }
        double share = Math.max(0.0, (cameraDistance - FADE_END) / (FADE_START - FADE_END));
        return (float) (MIN_ALPHA + (1.0 - MIN_ALPHA) * share);
    }

    public static void markFade(Object state, Entity entity) {
        float alpha = playerAlpha(entity);
        if (alpha < 1.0F) {
            fadedState = new WeakReference<>(state);
            fadedAlpha = alpha;
        } else if (fadedState.get() == state) {
            fadedState.clear();
        }
    }

    public static float stateAlpha(Object state) {
        return state != null && fadedState.get() == state ? fadedAlpha : 1.0F;
    }

    public static boolean fadeBody(float alpha) {
        return alpha < 1.0F;
    }

    public static float fadeAlpha(float alpha) {
        fadedFrames++;
        return alpha;
    }

    public static int fadeColor(float alpha) {
        fadedFrames++;
        return Math.round(alpha * 255.0F) << 24 | 0xFFFFFF;
    }

    public static boolean fadeLayers(float alpha) {
        return alpha < LAYER_ALPHA;
    }

    public static long fadedFrames() {
        return fadedFrames;
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

        KeyMapping swap = SwivelClient.swapShoulderKey();
        while (swap != null && swap.consumeClick()) {
            swapShoulder(player);
        }

        KeyMapping face = SwivelClient.faceCameraKey();
        while (face != null && face.consumeClick()) {
            if (ORBIT.active() && player != null) {
                faceCamera(player);
            }
        }

        updateZoom(player);
        updateSpyglass(mc, player);

        if (!ORBIT.active() || player == null) {
            return;
        }
        if (player.isUsingItem() && !mc.options.keyUse.isDown()) {
            faceAim(mc, player, true);
        } else if (aiming(mc, player)) {
            faceAim(mc, player, false);
        }
    }

    public static void faceCamera(LocalPlayer player) {
        float yaw = OrbitMath.near(player.getYRot(), freeYaw());
        player.setYRot(yaw);
        player.yRotO = yaw;
        player.setYHeadRot(yaw);
        player.setYBodyRot(yaw);
    }

    private static void swapShoulder(LocalPlayer player) {
        SwivelConfig config = SwivelClient.config();
        SwivelConfig.AimCamera mode = switch (config.aimCamera()) {
            case RIGHT_SHOULDER -> SwivelConfig.AimCamera.LEFT_SHOULDER;
            case LEFT_SHOULDER -> SwivelConfig.AimCamera.RIGHT_SHOULDER;
            default -> null;
        };
        if (mode == null) {
            return;
        }
        config.setAimCamera(mode);
        SwivelClient.saveConfig();
        if (player != null) {
            Messages.overlay(player, Component.translatable("swivel.message.aim_camera",
                    Component.translatable("swivel.options.aim_camera." + mode.key())));
        }
    }

    private static void updateZoom(LocalPlayer player) {
        KeyMapping in = SwivelClient.zoomInKey();
        KeyMapping out = SwivelClient.zoomOutKey();
        int steps = 0;
        while (in != null && in.consumeClick()) {
            steps--;
        }
        while (out != null && out.consumeClick()) {
            steps++;
        }

        boolean inDown = in != null && in.isDown();
        boolean outDown = out != null && out.isDown();
        if (inDown != outDown) {
            zoomHeldTicks++;
            if (zoomHeldTicks > ZOOM_REPEAT_DELAY && (zoomHeldTicks - ZOOM_REPEAT_DELAY) % ZOOM_REPEAT_EVERY == 0) {
                steps += inDown ? -1 : 1;
            }
        } else {
            zoomHeldTicks = 0;
        }
        if (steps == 0) {
            return;
        }

        SwivelConfig config = SwivelClient.config();
        double before = config.distance();
        config.setDistance(before + steps * SwivelConfig.DISTANCE_STEP);
        if (config.distance() != before) {
            SwivelClient.saveConfig();
        }
        if (player != null) {
            Messages.overlay(player, Component.translatable("swivel.message.distance",
                    String.format(Locale.ROOT, "%.1f", config.distance())));
        }
    }

    private static void updateSpyglass(Minecraft mc, LocalPlayer player) {
        boolean scoping = player != null && mc.getCameraEntity() == player && player.isScoping();
        if (scoping && !wasScoping && SwivelClient.config().spyglassFirstPerson()
                && !mc.options.getCameraType().isFirstPerson()) {
            spyglassReturn = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        } else if (!scoping && spyglassReturn != null) {
            if (mc.options.getCameraType().isFirstPerson()) {
                mc.options.setCameraType(spyglassReturn);
            }
            spyglassReturn = null;
        }
        wasScoping = scoping;
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
                || aimCamera && aimingWeapon(player)
                || mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown()
                || actionTicks > 0;
    }

    private static boolean aimingWeapon(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        if (player.isUsingItem()) {
            ItemStack used = player.getUseItem();
            return used.getItem() instanceof ProjectileWeaponItem || used.getItem() instanceof TridentItem;
        }
        return loadedCrossbow(player.getMainHandItem()) || loadedCrossbow(player.getOffhandItem());
    }

    private static boolean loadedCrossbow(ItemStack stack) {
        return stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack);
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
