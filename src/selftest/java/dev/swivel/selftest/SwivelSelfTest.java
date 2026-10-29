package dev.swivel.selftest;

import dev.swivel.camera.FreeLook;
import dev.swivel.camera.OrbitCamera;
import dev.swivel.camera.OrbitMath;
import dev.swivel.client.SwivelClient;
import dev.swivel.client.SwivelRuntime;
import dev.swivel.client.gui.SwivelSettingsScreen;
import dev.swivel.config.SwivelConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

public final class SwivelSelfTest {
    private static final String WORLD = "swivel-selftest";
    private static final int TIMEOUT = 2400;
    private static final double MOUSE_PER_DEGREE = 1.0 / 0.15;

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;

    private float bodyYaw;
    private float cameraYaw;
    private int patience;
    private Vec3 start;
    private long fadedFrames;
    private BlockPos tunnel;
    private BlockPos target;

    private record Step(int delay, BooleanSupplier ready, Runnable action) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > TIMEOUT) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        mc.options.keyUp.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        SwivelClient.freeLookKey().setDown(false);
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        SwivelConfig config = SwivelClient.config();
        config.resetToDefaults();
        SwivelClient.saveConfig();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.stop();
    }

    private void plan(Minecraft mc) {
        SwivelConfig config = SwivelClient.config();
        OrbitCamera orbit = SwivelRuntime.orbit();

        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        then(40, () -> {
            config.resetToDefaults();
            run(mc, "time set 6000");
            run(mc, "tp @p 0.5 -60 0.5 0 0");
        });
        then(20, () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        then(10, () -> {
            LocalPlayer player = mc.player;
            check(SwivelRuntime.active(), "the orbit camera did not switch on in third person");
            check(angle(orbit.yaw(), player.getYRot()) < 0.5F, "the camera did not start behind the player");
            bodyYaw = player.getYRot();
            Mouse.move(mc, 90.0 * MOUSE_PER_DEGREE, 0.0);
        });
        then(5, () -> {
            LocalPlayer player = mc.player;
            check(angle(orbit.yaw(), bodyYaw + 90.0F) < 1.0F,
                    "the mouse did not swing the camera: " + orbit.yaw());
            check(angle(player.getYRot(), bodyYaw) < 0.01F,
                    "the mouse turned the player instead of the camera: " + player.getYRot());
            screenshot(mc, "orbit-side");
            start = player.position();
            mc.options.keyUp.setDown(true);
        });
        then(20, () -> {
            mc.options.keyUp.setDown(false);
            LocalPlayer player = mc.player;
            Vec3 moved = player.position().subtract(start);
            check(moved.x < -1.5 && Math.abs(moved.z) < 0.3, "W did not walk the way the camera faces: " + moved);
            check(angle(player.getYRot(), 90.0F) < 5.0F,
                    "the body did not turn toward the way it walked: " + player.getYRot());
            screenshot(mc, "walked");
            run(mc, String.format(Locale.ROOT, "tp @p %.1f %.1f %.1f 0 0",
                    Mth.floor(player.getX()) + 0.5, player.getY(), Mth.floor(player.getZ()) + 0.5));
        });
        within(40, () -> angle(mc.player.getYRot(), 0.0F) < 0.5F, () -> {
            LocalPlayer player = mc.player;
            check(angle(player.getYRot(), 0.0F) < 0.5F, "teleport did not face the body along +Z: " + player.getYRot());
            target = BlockPos.containing(player.getX() - 3.0, player.getEyeY() + config.height(), player.getZ());
            run(mc, "setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:stone");
        });
        within(40, () -> !mc.level.getBlockState(target).isAir(), () -> {
            LocalPlayer player = mc.player;
            HitResult hit = mc.hitResult;
            check(hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                            && block.getBlockPos().equals(target),
                    "the crosshair did not target the block the camera looks at: " + describe(hit));
            check(angle(player.getYRot(), 0.0F) < 0.5F, "just aiming turned the body: " + player.getYRot());
            check(SwivelRuntime.crosshairFrames() > 0, "the crosshair was not drawn in third person");
            screenshot(mc, "target");
            mc.options.keyAttack.setDown(true);
        });
        then(10, () -> {
            mc.options.keyAttack.setDown(false);
            LocalPlayer player = mc.player;
            check(mc.level.getBlockState(target).isAir(), "holding attack did not break the targeted block");
            check(angle(player.getYRot(), 90.0F) < 3.0F,
                    "the body did not turn to the block it broke: " + player.getYRot());
            config.setDistance(8.0);
        });
        then(30, () -> {
            check(Math.abs(orbit.distance() - 8.0) < 0.1, "the camera did not move out to 8 blocks: " + orbit.distance());
            screenshot(mc, "far");
            LocalPlayer player = mc.player;
            int wall = Mth.floor(player.getX()) + 3;
            int y = Mth.floor(player.getY());
            int z = Mth.floor(player.getZ());
            run(mc, "fill " + wall + " " + (y - 1) + " " + (z - 3) + " " + wall + " " + (y + 4) + " " + (z + 3)
                    + " minecraft:stone");
        });
        within(40, () -> orbit.distance() < 3.2, () -> {
            check(orbit.distance() < 3.2, "the wall did not pull the camera in: " + orbit.distance());
            screenshot(mc, "wall");
            LocalPlayer player = mc.player;
            int wall = Mth.floor(player.getX()) + 3;
            int y = Mth.floor(player.getY());
            int z = Mth.floor(player.getZ());
            run(mc, "fill " + wall + " " + (y - 1) + " " + (z - 3) + " " + wall + " " + (y + 4) + " " + (z + 3)
                    + " minecraft:air");
        });
        then(2, () -> check(orbit.distance() < 7.5,
                "the camera popped straight back out instead of gliding: " + orbit.distance()));
        then(40, () -> {
            check(Math.abs(orbit.distance() - 8.0) < 0.2, "the camera did not glide back out: " + orbit.distance());
            check(SwivelRuntime.playerAlpha(mc.player) == 1.0F, "the player faded with the camera far away");
            LocalPlayer player = mc.player;
            int wall = Mth.floor(player.getX()) + 1;
            int y = Mth.floor(player.getY());
            int z = Mth.floor(player.getZ());
            run(mc, "fill " + wall + " " + (y - 1) + " " + (z - 3) + " " + wall + " " + (y + 4) + " " + (z + 3)
                    + " minecraft:stone");
        });
        within(40, () -> orbit.distance() < 1.0, () -> {
            fadedFrames = SwivelRuntime.fadedFrames();
        });
        then(5, () -> {
            float alpha = SwivelRuntime.playerAlpha(mc.player);
            check(orbit.distance() < 1.0, "the close wall did not pull the camera in: " + orbit.distance());
            check(alpha < 0.6F, "the player did not fade with the camera close: " + alpha);
            check(SwivelRuntime.fadedFrames() > fadedFrames, "the faded player was not drawn see-through");
            screenshot(mc, "faded");
            config.setFadePlayer(false);
        });
        then(3, () -> {
            check(SwivelRuntime.playerAlpha(mc.player) == 1.0F, "Fade When Close off still faded the player");
            fadedFrames = SwivelRuntime.fadedFrames();
        });
        then(5, () -> {
            check(SwivelRuntime.fadedFrames() == fadedFrames, "Fade When Close off still drew the player see-through");
            config.setFadePlayer(true);
            LocalPlayer player = mc.player;
            int wall = Mth.floor(player.getX()) + 1;
            int y = Mth.floor(player.getY());
            int z = Mth.floor(player.getZ());
            run(mc, "fill " + wall + " " + (y - 1) + " " + (z - 3) + " " + wall + " " + (y + 4) + " " + (z + 3)
                    + " minecraft:air");
        });
        then(40, () -> {
            check(SwivelRuntime.playerAlpha(mc.player) == 1.0F, "the player stayed faded after the wall was gone");
            config.setDistance(SwivelConfig.DEFAULT_DISTANCE);
            config.setEnabled(false);
        });
        then(5, () -> {
            check(!SwivelRuntime.active(), "the orbit camera stayed on after switching Swivel off");
            config.setEnabled(true);
        });
        then(5, () -> {
            check(SwivelRuntime.active(), "the orbit camera did not come back after switching Swivel on");
            Mouse.move(mc, 120.0 * MOUSE_PER_DEGREE, 10.0 * MOUSE_PER_DEGREE);
        });
        then(3, () -> {
            cameraYaw = orbit.yaw();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        });
        then(5, () -> {
            LocalPlayer player = mc.player;
            check(!SwivelRuntime.active(), "the orbit camera stayed on in first person");
            check(angle(player.getYRot(), cameraYaw) < 0.5F,
                    "first person did not keep the camera's view: " + player.getYRot() + " vs " + cameraYaw);
            Mouse.move(mc, 30.0 * MOUSE_PER_DEGREE, 0.0);
            bodyYaw = player.getYRot();
        });
        then(3, () -> {
            check(angle(mc.player.getYRot(), cameraYaw + 30.0F) < 1.0F,
                    "the mouse no longer turns the player in first person: " + mc.player.getYRot());
            run(mc, "give @p minecraft:bow");
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        then(10, () -> {
            check(SwivelRuntime.active(), "the orbit camera did not come back in third person");
            bodyYaw = mc.player.getYRot();
            Mouse.move(mc, 90.0 * MOUSE_PER_DEGREE, -10.0 * MOUSE_PER_DEGREE);
        });
        then(3, () -> {
            cameraYaw = orbit.yaw();
            check(angle(mc.player.getYRot(), bodyYaw) < 0.01F, "turning the camera turned the body");
            mc.options.keyUse.setDown(true);
        });
        then(20, () -> {
            check(mc.player.isUsingItem(), "holding use did not draw the bow");
            check(angle(mc.player.getYRot(), cameraYaw) < 2.0F,
                    "drawing the bow did not face the crosshair: " + mc.player.getYRot() + " vs " + cameraYaw);
            check(SwivelRuntime.aimCamera(), "drawing the bow did not switch on the aim camera");
            check(orbit.shoulder() > 0.5, "the aim camera did not move over the right shoulder: " + orbit.shoulder());
            check(orbit.distance() < config.aimDistance() + 0.2,
                    "the aim camera did not move closer: " + orbit.distance() + " vs " + config.aimDistance());
            screenshot(mc, "bow");
            mc.options.keyUse.setDown(false);
        });
        within(60, () -> arrow(mc) != null && arrow(mc).tickCount >= 3
                && arrow(mc).getDeltaMovement().horizontalDistanceSqr() > 0.01, () -> {
            Entity arrow = arrow(mc);
            check(arrow != null, "releasing the bow did not shoot an arrow");
            Vec3 flight = arrow.getDeltaMovement();
            float arrowYaw = OrbitMath.yawTo(flight.x, flight.z);
            check(angle(arrowYaw, cameraYaw) < 5.0F,
                    "the arrow did not fly where the camera aimed: " + arrowYaw + " vs " + cameraYaw);
            check(!SwivelRuntime.aimCamera(), "the aim camera stayed on after shooting");
            check(orbit.shoulder() < 0.3, "the camera did not leave the shoulder after shooting: " + orbit.shoulder());
            run(mc, "kill @e[type=minecraft:arrow]");
            config.setAimCamera(SwivelConfig.AimCamera.OFF);
            mc.options.keyUse.setDown(true);
        });
        then(20, () -> {
            check(mc.player.isUsingItem(), "holding use did not draw the bow again");
            check(!SwivelRuntime.aimCamera() && orbit.shoulder() == 0.0,
                    "Aim Camera Off still moved the camera: " + orbit.shoulder());
            check(Math.abs(orbit.distance() - config.distance()) < 0.2,
                    "Aim Camera Off still moved the camera closer: " + orbit.distance());
            mc.options.keyUse.setDown(false);
            config.setAimCamera(SwivelConfig.DEFAULT_AIM_CAMERA);
        });
        then(30, () -> {
            run(mc, "kill @e[type=minecraft:arrow]");
            run(mc, "clear @p");
        });
        planTunnel(mc, orbit);
        planFreeLook(mc);
        planSwivel(mc, config, orbit);
        planRaise(mc, config, orbit);
        planSettings(mc, config);
    }

    private void planTunnel(Minecraft mc, OrbitCamera orbit) {
        List<String> buried = new ArrayList<>();
        then(5, () -> {
            LocalPlayer player = mc.player;
            tunnel = player.blockPosition();
            run(mc, String.format(Locale.ROOT, "tp @p %.1f %d %.1f 0 0",
                    tunnel.getX() + 0.5, tunnel.getY(), tunnel.getZ() + 0.5));
            run(mc, "fill " + (tunnel.getX() - 1) + " " + (tunnel.getY() - 1) + " " + (tunnel.getZ() - 4) + " "
                    + (tunnel.getX() + 1) + " " + (tunnel.getY() + 2) + " " + (tunnel.getZ() + 4)
                    + " minecraft:stone hollow");
        });
        for (int yaw = 0; yaw < 360; yaw += 45) {
            for (int pitch = -60; pitch <= 80; pitch += 20) {
                float wantedYaw = yaw;
                float wantedPitch = pitch;
                then(2, () -> Mouse.move(mc, Mth.wrapDegrees(wantedYaw - orbit.yaw()) * MOUSE_PER_DEGREE,
                        (wantedPitch - orbit.pitch()) * MOUSE_PER_DEGREE));
                then(3, () -> {
                    Vec3 camera = SwivelRuntime.cameraPosition();
                    if (insideBlock(mc, camera)) {
                        buried.add(String.format(Locale.ROOT, "yaw %.0f pitch %.0f at %.2f %.2f %.2f",
                                orbit.yaw(), orbit.pitch(), camera.x, camera.y, camera.z));
                    }
                });
            }
        }
        then(1, () -> {
            screenshot(mc, "tunnel");
            run(mc, "fill " + (tunnel.getX() - 1) + " " + tunnel.getY() + " " + (tunnel.getZ() - 4) + " "
                    + (tunnel.getX() + 1) + " " + (tunnel.getY() + 2) + " " + (tunnel.getZ() + 4) + " minecraft:air");
            Mouse.move(mc, -orbit.pitch() * MOUSE_PER_DEGREE, 0.0);
            check(buried.isEmpty(), "the camera clipped into the tunnel walls " + buried.size() + " times: " + buried);
        });
        then(10, () -> {
        });
    }

    private static boolean insideBlock(Minecraft mc, Vec3 camera) {
        Vec3 forward = SwivelRuntime.cameraForward();
        Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 center = camera.add(forward.scale(0.05));
        for (int corner = 0; corner < 5; corner++) {
            Vec3 point = corner == 4 ? center : center.add(right.scale((corner & 1) == 0 ? -0.09 : 0.09))
                    .add(up.scale((corner & 2) == 0 ? -0.05 : 0.05));
            BlockPos pos = BlockPos.containing(point);
            for (AABB box : mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).toAabbs()) {
                if (box.contains(point.x - pos.getX(), point.y - pos.getY(), point.z - pos.getZ())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void planFreeLook(Minecraft mc) {
        KeyMapping freeLook = SwivelClient.freeLookKey();
        FreeLook look = SwivelRuntime.freeLook();

        then(5, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        when(() -> !SwivelRuntime.active(), () -> face(mc, 0.0F));
        within(40, () -> angle(mc.player.getYRot(), 0.0F) < 0.5F, () -> {
            check(angle(mc.player.getYRot(), 0.0F) < 0.5F, "teleport did not face the body along +Z: " + mc.player.getYRot());
            freeLook.setDown(true);
        });
        within(20, look::looking, () -> {
            check(look.looking(), "holding Free Look did not start free look in first person");
            Mouse.move(mc, 90.0 * MOUSE_PER_DEGREE, 20.0 * MOUSE_PER_DEGREE);
        });
        then(3, () -> {
            LocalPlayer player = mc.player;
            check(angle(player.getYRot(), 0.0F) < 0.01F && Math.abs(player.getXRot()) < 0.01F,
                    "free look turned the character: " + player.getYRot() + " / " + player.getXRot());
            check(angle(look.yaw(), 90.0F) < 1.0F && Math.abs(look.pitch() - 20.0F) < 1.0F,
                    "free look did not turn the view: " + look.yaw() + " / " + look.pitch());
            check(angle(SwivelRuntime.cameraYaw(player, player.getYRot()), 90.0F) < 1.0F,
                    "the camera did not follow the free look view");
            check(!SwivelRuntime.crosshair(true), "the crosshair stayed on while free looking");
            screenshot(mc, "free-look");
            start = player.position();
            mc.options.keyUp.setDown(true);
        });
        then(15, () -> {
            mc.options.keyUp.setDown(false);
            LocalPlayer player = mc.player;
            Vec3 moved = player.position().subtract(start);
            check(moved.z > 1.5 && Math.abs(moved.x) < 0.3,
                    "W during free look did not walk the way the character faces: " + moved);
            check(angle(player.getYRot(), 0.0F) < 0.01F, "walking during free look turned the character");
            freeLook.setDown(false);
        });
        then(1, () -> check(look.active() && !look.looking(), "letting go of Free Look did not glide the view back"));
        then(20, () -> {
            check(!look.active(), "the view did not come back to the character after Free Look");
            check(SwivelRuntime.crosshair(true), "the crosshair did not come back after Free Look");
            Mouse.move(mc, 30.0 * MOUSE_PER_DEGREE, 0.0);
        });
        then(3, () -> check(angle(mc.player.getYRot(), 30.0F) < 1.0F,
                "the mouse did not turn the character again after Free Look: " + mc.player.getYRot()));
    }

    private void planSwivel(Minecraft mc, SwivelConfig config, OrbitCamera orbit) {
        KeyMapping freeLook = SwivelClient.freeLookKey();

        then(5, () -> {
            config.setSwivelWhileRunning(true);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        then(10, () -> {
            LocalPlayer player = mc.player;
            check(SwivelRuntime.active(), "the orbit camera did not come back in third person");
            check(angle(orbit.yaw(), player.getYRot()) < 0.5F, "the camera did not start behind the player");
            bodyYaw = player.getYRot();
            start = player.position();
            mc.options.keyUp.setDown(true);
        });
        then(5, () -> Mouse.move(mc, 120.0 * MOUSE_PER_DEGREE, 0.0));
        then(15, () -> {
            LocalPlayer player = mc.player;
            check(angle(orbit.yaw(), bodyYaw + 120.0F) < 1.0F, "the mouse did not swivel the camera while running: "
                    + orbit.yaw());
            check(angle(player.getYRot(), bodyYaw) < 1.0F, "swiveling the camera turned the runner: " + player.getYRot());
            Vec3 moved = player.position().subtract(start);
            float course = OrbitMath.yawTo(moved.x, moved.z);
            check(moved.horizontalDistance() > 2.0 && angle(course, bodyYaw) < 5.0F,
                    "the character did not keep running the same way: " + moved);
            screenshot(mc, "swivel-running");
            mc.options.keyUp.setDown(false);
        });
        then(3, () -> mc.options.keyUp.setDown(true));
        then(15, () -> {
            mc.options.keyUp.setDown(false);
            check(angle(mc.player.getYRot(), orbit.yaw()) < 5.0F,
                    "running again did not head where the camera looks: " + mc.player.getYRot() + " vs " + orbit.yaw());
            config.setSwivelWhileRunning(false);
        });

        then(5, () -> {
            cameraYaw = orbit.yaw();
            freeLook.setDown(true);
        });
        then(2, () -> {
            start = mc.player.position();
            mc.options.keyUp.setDown(true);
            Mouse.move(mc, -100.0 * MOUSE_PER_DEGREE, 0.0);
        });
        then(15, () -> {
            LocalPlayer player = mc.player;
            check(angle(orbit.yaw(), cameraYaw - 100.0F) < 1.0F, "the camera did not swing while holding Free Look: "
                    + orbit.yaw());
            check(angle(player.getYRot(), cameraYaw) < 2.0F,
                    "holding Free Look in third person changed the running direction: " + player.getYRot());
            screenshot(mc, "free-look-third");
            freeLook.setDown(false);
        });
        then(1, () -> check(orbit.recentering() || angle(orbit.yaw(), cameraYaw) < 1.0F,
                "letting go of Free Look did not swing the camera back: " + orbit.yaw()));
        then(20, () -> {
            mc.options.keyUp.setDown(false);
            check(!orbit.recentering() && angle(orbit.yaw(), cameraYaw) < 1.0F,
                    "the camera did not swing back after Free Look: " + orbit.yaw() + " vs " + cameraYaw);
            check(angle(mc.player.getYRot(), cameraYaw) < 2.0F,
                    "the swing back turned the runner: " + mc.player.getYRot());
        });

        then(5, () -> {
            cameraYaw = mc.player.getYRot();
            Mouse.move(mc, 90.0 * MOUSE_PER_DEGREE, 0.0);
        });
        then(10, () -> {
            check(angle(mc.player.getYRot(), cameraYaw) < 2.0F,
                    "turning the camera while standing turned the character: " + mc.player.getYRot());
            SwivelRuntime.faceCamera(mc.player);
        });
        then(2, () -> check(angle(mc.player.getYRot(), orbit.yaw()) < 1.0F
                        && angle(mc.player.yBodyRot, orbit.yaw()) < 1.0F,
                "Face Camera did not turn the character to the camera: " + mc.player.getYRot() + " vs " + orbit.yaw()));
    }

    private void planRaise(Minecraft mc, SwivelConfig config, OrbitCamera orbit) {
        then(5, () -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        when(() -> !SwivelRuntime.active(), () -> face(mc, 90.0F));
        within(40, () -> angle(mc.player.getYRot(), 90.0F) < 0.5F,
                () -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        then(5, () -> {
            LocalPlayer player = mc.player;
            check(angle(orbit.yaw(), 90.0F) < 0.5F && Math.abs(orbit.pitch()) < 0.5F,
                    "the camera did not start level behind the player: " + orbit.yaw() + " / " + orbit.pitch());
            config.setRaiseCamera(false);
            check(SwivelRuntime.cameraLift(mc.level, player, player.getEyePosition(), new Vec3(0.0, 1.0, 0.0)) == 0.0F,
                    "Raise Camera off still lifts the camera");
            target = BlockPos.containing(player.getX() - 3.0, player.getEyeY() + config.height(), player.getZ());
            run(mc, "setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:stone");
        });
        within(40, () -> !mc.level.getBlockState(target).isAir(), () -> {
            check(!targets(mc.hitResult, target),
                    "with Raise Camera off the crosshair still looked from above the eyes: " + describe(mc.hitResult));
            screenshot(mc, "not-raised");
            config.setRaiseCamera(true);
        });
        then(5, () -> {
            check(targets(mc.hitResult, target),
                    "turning Raise Camera back on did not lift the camera: " + describe(mc.hitResult));
            run(mc, "setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:air");
        });
    }

    private static Entity arrow(Minecraft mc) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if ("minecraft:arrow".equals(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())))) {
                return entity;
            }
        }
        return null;
    }

    private static void face(Minecraft mc, float yaw) {
        LocalPlayer player = mc.player;
        run(mc, String.format(Locale.ROOT, "tp @p %.1f %.1f %.1f %.1f 0",
                Mth.floor(player.getX()) + 0.5, player.getY(), Mth.floor(player.getZ()) + 0.5, yaw));
    }

    private static boolean targets(HitResult hit, BlockPos pos) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && block.getBlockPos().equals(pos);
    }

    private void planSettings(Minecraft mc, SwivelConfig config) {
        then(5, () -> Screens.open(mc, new SwivelSettingsScreen(null, mc.options)));
        then(10, () -> {
            screenshot(mc, "settings");
            click(mc, find(Screens.current(mc), "swivel.options.strafe"));
        });
        then(3, () -> {
            check(config.strafe(), "the strafe button did not switch strafing on");
            click(mc, find(Screens.current(mc), "swivel.options.swivel_running"));
        });
        then(3, () -> {
            check(config.swivelWhileRunning(), "the Swivel While Running button did not switch it on");
            click(mc, find(Screens.current(mc), "swivel.options.raise_camera"));
        });
        then(3, () -> {
            check(!config.raiseCamera(), "the Raise Camera button did not switch it off");
            click(mc, find(Screens.current(mc), "swivel.options.fade_player"));
        });
        then(3, () -> {
            check(!config.fadePlayer(), "the Fade When Close button did not switch it off");
            scroll(Screens.current(mc), true);
        });
        then(3, () -> click(mc, find(Screens.current(mc), "swivel.options.aim_camera")));
        then(3, () -> check(config.aimCamera() == SwivelConfig.AimCamera.LEFT_SHOULDER,
                "the Aim Camera button did not switch to the left shoulder: " + config.aimCamera()));
        then(3, () -> click(mc, find(Screens.current(mc), "swivel.options.free_look_crosshair")));
        then(3, () -> {
            check(!config.hideCrosshairInFreeLook(), "the Hide Crosshair button did not switch it off");
            scroll(Screens.current(mc), false);
        });
        then(3, () -> clickRightEdge(mc, slider(Screens.current(mc), "Camera Distance")));
        then(3, () -> {
            check(config.distance() == SwivelConfig.MAX_DISTANCE, "the distance slider did not reach the maximum: "
                    + config.distance());
            scroll(Screens.current(mc), true);
        });
        then(3, () -> click(mc, find(Screens.current(mc), "swivel.options.reset")));
        then(5, () -> {
            Screen screen = Screens.current(mc);
            check(!config.strafe(), "reset did not switch strafing back off");
            check(!config.swivelWhileRunning(), "reset did not switch Swivel While Running back off");
            check(config.raiseCamera(), "reset did not switch Raise Camera back on");
            check(config.aimCamera() == SwivelConfig.DEFAULT_AIM_CAMERA, "reset did not restore the aim camera");
            check(config.fadePlayer(), "reset did not switch Fade When Close back on");
            check(config.hideCrosshairInFreeLook(), "reset did not switch Hide Crosshair back on");
            check(config.distance() == SwivelConfig.DEFAULT_DISTANCE, "reset did not restore the distance");
            check(count(screen, "Turn Speed") == 1, "reset left " + count(screen, "Turn Speed") + " turn sliders");
            clickRightEdge(mc, slider(screen, "Turn Speed"));
        });
        then(3, () -> {
            check(config.turnSpeed() == SwivelConfig.MAX_TURN_SPEED, "the turn slider after reset did not change the config");
            Screens.open(mc, null);
        });
        then(5, () -> {
            String saved = read(SwivelClient.configPath());
            check(saved.contains("\"turnSpeed\": 10"), "settings were not saved on close: " + saved);
        });
        then(10, () -> log("settings checks done"));
    }

    private static float angle(float a, float b) {
        return Math.abs(OrbitMath.wrap(a - b));
    }

    private static String describe(HitResult hit) {
        if (hit instanceof BlockHitResult block) {
            return hit.getType() + " at " + block.getBlockPos();
        }
        return String.valueOf(hit == null ? null : hit.getType());
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        Screenshot.grab(mc.gameDirectory, Screens.renderTarget(mc), message -> {
        });
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "sw-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void click(Minecraft mc, AbstractWidget widget) {
        visible(Screens.current(mc), widget);
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void clickRightEdge(Minecraft mc, AbstractWidget widget) {
        visible(Screens.current(mc), widget);
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() - 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static void scroll(Screen screen, boolean bottom) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                list.setScrollAmount(bottom ? Double.MAX_VALUE : 0.0);
            }
        }
    }

    private static void visible(Screen screen, AbstractWidget widget) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractSelectionList<?> list) {
                check(Lists.shows(list, widget),
                        widget.getMessage().getString() + " is scrolled out of view");
            }
        }
    }

    static AbstractWidget find(GuiEventListener node, String key) {
        if (node instanceof AbstractWidget widget && labelled(widget, key)) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Screen) {
            throw new AssertionError("no widget " + key + " on " + node);
        }
        return null;
    }

    private static boolean labelled(AbstractWidget widget, String key) {
        if (!(widget.getMessage().getContents() instanceof TranslatableContents contents)) {
            return false;
        }
        if (contents.getKey().equals(key)) {
            return true;
        }
        Object[] args = contents.getArgs();
        return args.length > 0
                && args[0] instanceof net.minecraft.network.chat.Component name
                && name.getContents() instanceof TranslatableContents inner
                && inner.getKey().equals(key);
    }

    private static AbstractSliderButton slider(GuiEventListener node, String caption) {
        AbstractSliderButton found = findSlider(node, caption);
        check(found != null, "no slider " + caption);
        return found;
    }

    private static AbstractSliderButton findSlider(GuiEventListener node, String caption) {
        if (node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption)) {
            return slider;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractSliderButton found = findSlider(child, caption);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int count(GuiEventListener node, String caption) {
        int total = node instanceof AbstractSliderButton slider && slider.getMessage().getString().startsWith(caption) ? 1 : 0;
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                total += count(child, caption);
            }
        }
        return total;
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[swivel-selftest] " + message);
    }
}
