package dev.swivel.selftest;

import com.mojang.datafixers.util.Pair;
import dev.swivel.camera.FreeLook;
import dev.swivel.camera.OrbitCamera;
import dev.swivel.camera.OrbitMath;
import dev.swivel.client.SwivelClient;
import dev.swivel.client.SwivelRuntime;
import dev.swivel.client.gui.SwivelSettingsScreen;
import dev.swivel.config.SwivelConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

public final class Showcase {
    private static final String WORLD = "swivel-showcase";
    private static final double MOUSE_PER_DEGREE = 1.0 / 0.15;
    private static final long SETTLE_NANOS = 3_000_000_000L;
    private static Showcase instance;

    private final Path out;
    private final Recorder recorder;
    private final long seed;
    private final List<Action> actions = new ArrayList<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int frame;
    private long actionStart;
    private Path pendingStill;

    private interface Action {
        boolean run(int frame);
    }

    private Showcase(Path out, String ffmpeg, long seed) {
        this.out = out;
        this.recorder = new Recorder(ffmpeg);
        this.seed = seed;
    }

    public static boolean requested() {
        return System.getProperty("swivel.showcase") != null;
    }

    public static void start() {
        String ffmpeg = System.getProperty("swivel.ffmpeg", "ffmpeg");
        long seed = Long.parseLong(System.getProperty("swivel.seed", "20260925"));
        instance = new Showcase(Path.of(System.getProperty("swivel.showcase")), ffmpeg, seed);
    }

    public static long nanos() {
        return Clock.nanos();
    }

    public static void frame() {
        if (instance != null) {
            instance.onFrame();
        }
    }

    private void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 120) {
                started = true;
                setUpOptions(mc);
                plan(mc);
                log("creating world with seed " + seed);
                deleteWorld(mc);
                Worlds.create(mc, WORLD, false, seed);
            }
            return;
        }
        Clock.step();
        mc.gui.toastManager().clear();
        try {
            while (index < actions.size()) {
                if (frame == 0) {
                    actionStart = System.nanoTime();
                }
                if (!actions.get(index).run(frame++)) {
                    return;
                }
                index++;
                frame = 0;
            }
        } catch (Throwable e) {
            log("FAILED: " + e);
            e.printStackTrace();
        }
        finish(mc);
    }

    private void finish(Minecraft mc) {
        finished = true;
        Clock.release();
        release(mc);
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(90_000L);
            } catch (InterruptedException e) {
                return;
            }
            log("the game did not close, forcing it");
            Runtime.getRuntime().halt(1);
        }, "swivel-showcase-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        SwivelClient.config().resetToDefaults();
        SwivelClient.saveConfig();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        log("showcase done");
        mc.stop();
    }

    private static void setUpOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.renderDistance().set(16);
        mc.options.bobView().set(true);
    }

    private void once(Runnable action) {
        actions.add(f -> {
            action.run();
            return true;
        });
    }

    private void waitFrames(int frames) {
        actions.add(f -> f >= frames);
    }

    private void until(BooleanSupplier ready) {
        actions.add(f -> ready.getAsBoolean());
    }

    private void settle(Minecraft mc) {
        settle(mc, () -> {
        });
    }

    private void settle(Minecraft mc, Runnable hold) {
        actions.add(f -> {
            hold.run();
            return f >= 90 && System.nanoTime() - actionStart > SETTLE_NANOS
                    && (mc.levelRenderer.hasRenderedAllSections() || System.nanoTime() - actionStart > 20 * SETTLE_NANOS);
        });
    }

    private void record(String name, int frames, IntConsumer script) {
        actions.add(f -> {
            if (f == 0) {
                log("recording " + name);
                recorder.start(out.resolve("clips").resolve(name + ".mp4"));
            } else {
                Path still = pendingStill;
                pendingStill = null;
                recorder.capture(still);
            }
            if (f < frames) {
                script.accept(f);
                return false;
            }
            return true;
        });
        until(recorder::drained);
        once(recorder::stop);
    }

    private void shot(String name) {
        pendingStill = out.resolve("stills").resolve(name + ".png");
    }

    private void still(String name) {
        once(() -> Recorder.still(out.resolve("stills").resolve(name + ".png")));
        waitFrames(3);
    }

    private static float cameraYaw(Minecraft mc) {
        FreeLook look = SwivelRuntime.freeLook();
        if (SwivelRuntime.active()) {
            return SwivelRuntime.orbit().yaw();
        }
        return look.looking() ? look.yaw() : mc.player.getYRot();
    }

    private static float cameraPitch(Minecraft mc) {
        FreeLook look = SwivelRuntime.freeLook();
        if (SwivelRuntime.active()) {
            return SwivelRuntime.orbit().pitch();
        }
        return look.looking() ? look.pitch() : mc.player.getXRot();
    }

    static void aim(Minecraft mc, float yaw, float pitch) {
        double dx = OrbitMath.wrap(yaw - cameraYaw(mc));
        double dy = pitch - cameraPitch(mc);
        Mouse.move(mc, dx * MOUSE_PER_DEGREE, dy * MOUSE_PER_DEGREE);
    }

    static void turn(Minecraft mc, double yaw, double pitch) {
        Mouse.move(mc, yaw * MOUSE_PER_DEGREE, pitch * MOUSE_PER_DEGREE);
    }

    static double smooth(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        return t * t * (3.0 - 2.0 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static void release(Minecraft mc) {
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keySprint.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        SwivelClient.freeLookKey().setDown(false);
    }

    static void run(Minecraft mc, String command) {
        SwivelSelfTest.run(mc, command);
    }

    private void locate(Minecraft mc, String label, ResourceKey<Biome> biome, AtomicReference<BlockPos> into) {
        once(() -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ServerLevel level = server.overworld();
                BlockPos found;
                if (biome == null) {
                    found = level.findNearestMapStructure(StructureTags.VILLAGE, BlockPos.ZERO, 100, false);
                } else {
                    Pair<BlockPos, Holder<Biome>> pair = level.findClosestBiome3d(h -> h.is(biome), BlockPos.ZERO,
                            6400, 32, 64);
                    found = pair == null ? null : pair.getFirst();
                }
                if (found == null) {
                    log("no " + label + " found");
                    into.set(BlockPos.ZERO);
                    return;
                }
                into.set(surface(level, found.getX(), found.getZ()));
                log(label + " at " + into.get().toShortString());
            });
        });
        until(() -> into.get() != null);
    }

    static BlockPos surface(ServerLevel level, int x, int z) {
        int y = level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
        return new BlockPos(x, y + 1, z);
    }

    private static void teleport(Minecraft mc, Supplier<BlockPos> at, float yaw) {
        BlockPos pos = at.get();
        run(mc, String.format(Locale.ROOT, "tp @p %.1f %d %.1f %.1f 0", pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                yaw));
    }

    private void plan(Minecraft mc) {
        SwivelConfig config = SwivelClient.config();
        OrbitCamera orbit = SwivelRuntime.orbit();

        until(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null);
        once(() -> {
            log("world loaded");
            Clock.fix();
            config.resetToDefaults();
            run(mc, "time set 1500");
            run(mc, "weather clear 1000000");
            run(mc, "gamerule doDaylightCycle false");
            run(mc, "gamerule advance_time false");
            run(mc, "gamerule doMobSpawning false");
            run(mc, "gamerule spawn_mobs false");
        });
        if (System.getProperty("swivel.scout") != null) {
            scout(mc);
        } else {
            film(mc, config);
        }
    }

    private void scout(Minecraft mc) {
        List<Pair<String, ResourceKey<Biome>>> places = List.of(
                Pair.of("cherry", Biomes.CHERRY_GROVE),
                Pair.of("meadow", Biomes.MEADOW),
                Pair.of("sunflower", Biomes.SUNFLOWER_PLAINS),
                Pair.of("village", null));
        for (Pair<String, ResourceKey<Biome>> place : places) {
            AtomicReference<BlockPos> pos = new AtomicReference<>();
            locate(mc, place.getFirst(), place.getSecond(), pos);
            once(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
            once(() -> teleport(mc, pos::get, 0.0F));
            waitFrames(5);
            once(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            settle(mc);
            for (int i = 0; i < 4; i++) {
                float yaw = i * 90.0F;
                once(() -> aim(mc, yaw, 15.0F));
                waitFrames(10);
                still("scout-" + place.getFirst() + "-" + i);
            }
        }
    }

    private final AtomicReference<BlockPos> site = new AtomicReference<>();

    private static boolean wanted(String scene) {
        String only = System.getProperty("swivel.scenes");
        return only == null || List.of(only.split(",")).contains(scene);
    }

    private Vec3 at(double dx, double dy, double dz) {
        BlockPos c = site.get();
        return new Vec3(c.getX() + dx + 0.5, c.getY() + dy, c.getZ() + dz + 0.5);
    }

    private static void cmd(Minecraft mc, String format, Object... args) {
        run(mc, String.format(Locale.ROOT, format, args));
    }

    private static void fill(Minecraft mc, int x1, int y1, int z1, int x2, int y2, int z2, String block) {
        int layers = Math.max(1, 32768 / ((Math.abs(x2 - x1) + 1) * (Math.abs(z2 - z1) + 1)));
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y += layers) {
            cmd(mc, "fill %d %d %d %d %d %d %s", x1, y, z1, x2, Math.min(y + layers - 1, Math.max(y1, y2)), z2, block);
        }
    }

    private void buildSet(Minecraft mc) {
        BlockPos c = site.get();
        int x = c.getX();
        int z = c.getZ();
        int ground = c.getY() - 1;
        fill(mc, x - 22, ground + 1, z - 34, x + 22, ground + 40, z + 34, "minecraft:air");
        fill(mc, x - 22, ground - 8, z - 34, x + 22, ground - 1, z + 34, "minecraft:dirt");
        fill(mc, x - 22, ground, z - 34, x + 22, ground, z + 34, "minecraft:grass_block");
        int[][] trees = {{-19, -31}, {19, -31}, {19, 21}, {-20, -8}, {18, 33}, {-19, 33}, {20, -14}};
        for (int[] tree : trees) {
            cmd(mc, "place feature minecraft:cherry %d %d %d", x + tree[0], ground + 1, z + tree[1]);
        }
        int[][] petals = {{-4, -20}, {4, 18}, {-15, 20}, {14, -22}, {1, 1}, {-12, -2}, {6, -28}, {-8, 24}};
        for (int[] patch : petals) {
            cmd(mc, "place feature minecraft:flower_cherry %d %d %d", x + patch[0], ground + 1, z + patch[1]);
        }
        java.util.Random random = new java.util.Random(seed);
        String[] flowers = {"pink_tulip", "allium", "oxeye_daisy", "lily_of_the_valley", "cornflower"};
        String[] facings = {"north", "south", "east", "west"};
        for (int dx = -21; dx <= 21; dx++) {
            for (int dz = -33; dz <= 33; dz++) {
                double roll = random.nextDouble();
                String block;
                if (roll < 0.13) {
                    block = "pink_petals[flower_amount=" + (1 + random.nextInt(4)) + ",facing="
                            + facings[random.nextInt(4)] + "]";
                } else if (roll < 0.21) {
                    block = "short_grass";
                } else if (roll < 0.225) {
                    block = flowers[random.nextInt(flowers.length)];
                } else {
                    continue;
                }
                cmd(mc, "setblock %d %d %d minecraft:%s", x + dx, ground + 1, z + dz, block);
            }
        }
        fill(mc, x + 7, ground + 1, z - 6, x + 11, ground + 4, z + 6, "minecraft:stone_bricks");
        fill(mc, x + 8, ground + 1, z - 6, x + 10, ground + 3, z + 6, "minecraft:air");
        fill(mc, x - 12, ground + 1, z + 10, x - 6, ground + 4, z + 10, "minecraft:cherry_planks");
        for (int tx : new int[] {-8, -3, 2}) {
            cmd(mc, "setblock %d %d %d minecraft:hay_block", x + tx, ground + 1, z + 30);
            cmd(mc, "setblock %d %d %d minecraft:target", x + tx, ground + 2, z + 30);
        }
        run(mc, "kill @e[type=minecraft:item]");
        run(mc, "clear @p");
        String[] hotbar = {"diamond_pickaxe", "bow", "cherry_planks 64", "firework_rocket 64", "lantern 16",
                "spyglass", "cherry_sapling 16", "torch 64", "cake"};
        for (int i = 0; i < hotbar.length; i++) {
            cmd(mc, "item replace entity @p hotbar.%d with minecraft:%s", i, hotbar[i]);
        }
    }

    private static void select(Minecraft mc, int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
    }

    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        aim(mc, yaw, pitch);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    private void scene(Minecraft mc, SwivelConfig config, double dx, double dy, double dz, float yaw, boolean third,
                       Runnable setup, Runnable hold) {
        once(() -> {
            release(mc);
            Screens.open(mc, null);
            config.resetToDefaults();
            mc.player.getAbilities().flying = false;
            mc.player.onUpdateAbilities();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            Vec3 p = at(dx, dy, dz);
            cmd(mc, "tp @p %.2f %.2f %.2f %.1f 0", p.x, p.y, p.z, yaw);
            run(mc, "kill @e[type=minecraft:arrow]");
        });
        waitFrames(15);
        once(() -> {
            setup.run();
            if (third) {
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            }
        });
        waitFrames(3);
        settle(mc, hold);
    }

    private void film(Minecraft mc, SwivelConfig config) {
        locate(mc, "cherry grove", Biomes.CHERRY_GROVE, site);
        once(() -> {
            BlockPos c = site.get();
            cmd(mc, "forceload add %d %d %d %d", c.getX() - 40, c.getZ() - 50, c.getX() + 40, c.getZ() + 50);
            cmd(mc, "tp @p %d %d %d", c.getX(), c.getY() + 45, c.getZ());
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
        settle(mc);
        once(() -> buildSet(mc));
        waitFrames(60);

        if (wanted("intro")) {
            scene(mc, config, 0, 0, -8, 0.0F, true, () -> select(mc, 4), () -> aim(mc, 200.0F, 18.0F));
            record("01-intro", 540, f -> {
                double t = smooth(f / 540.0);
                aim(mc, (float) (200.0 + 360.0 * t), (float) lerp(18.0, 10.0, t));
                if (f == 150) {
                    shot("orbit-1");
                } else if (f == 380) {
                    shot("orbit-2");
                }
            });
        }

        if (wanted("walk")) {
            scene(mc, config, -6, 0, -26, 0.0F, true, () -> select(mc, 4), () -> aim(mc, 0.0F, 14.0F));
            record("02-walk", 480, f -> {
                mc.options.keyUp.setDown((f >= 130 && f < 250) || (f >= 350 && f < 470));
                double yaw;
                if (f < 30) {
                    yaw = 0.0;
                } else if (f < 110) {
                    yaw = lerp(0.0, -120.0, smooth((f - 30) / 80.0));
                } else if (f < 260) {
                    yaw = -120.0;
                } else if (f < 340) {
                    yaw = lerp(-120.0, 20.0, smooth((f - 260) / 80.0));
                } else {
                    yaw = 20.0;
                }
                aim(mc, (float) yaw, 14.0F);
                if (f == 115) {
                    shot("walk-side");
                } else if (f == 165) {
                    shot("walk-turn");
                }
            });
        }

        if (wanted("running")) {
            scene(mc, config, -2, 0, -30, 0.0F, true, () -> {
                config.setSwivelWhileRunning(true);
                select(mc, 0);
            }, () -> aim(mc, 0.0F, 10.0F));
            record("03-swivel-running", 420, f -> {
                boolean running = f >= 15 && f < 410;
                mc.options.keyUp.setDown(running);
                mc.options.keySprint.setDown(running);
                double yaw;
                if (f < 80) {
                    yaw = 0.0;
                } else if (f < 200) {
                    yaw = lerp(0.0, 180.0, smooth((f - 80) / 120.0));
                } else if (f < 300) {
                    yaw = 180.0;
                } else {
                    yaw = lerp(180.0, 360.0, smooth((f - 300) / 100.0));
                }
                aim(mc, (float) yaw, (float) (f >= 150 && f < 320 ? 6.0 : 10.0));
                if (f == 250) {
                    shot("swivel-running");
                }
            });
        }

        if (wanted("build")) {
            scene(mc, config, -9, 0, 7, 90.0F, true, () -> select(mc, 0),
                    () -> lookAt(mc, at(-11, 1.5, 10)));
            record("04-mine-build", 480, f -> {
                if (f < 210) {
                    mc.options.keyAttack.setDown(f >= 30 && f < 200);
                    double t = smooth((f - 30) / 160.0);
                    lookAt(mc, at(lerp(-11.0, -7.0, t), 1.5, 10));
                    if (f == 115) {
                        shot("mine");
                    }
                } else {
                    if (f == 210) {
                        select(mc, 2);
                    }
                    int hole = Math.max(0, Math.min(4, (f - 230) / 45));
                    int local = (f - 230) - hole * 45;
                    Vec3 from = at(-7 - Math.max(0, hole - 1), 1.02, 10);
                    Vec3 to = at(-7 - hole, 1.02, 10);
                    lookAt(mc, f < 230 ? lerp(at(-7, 1.5, 10), at(-7, 1.02, 10), smooth((f - 210) / 20.0))
                            : lerp(from, to, smooth(hole == 0 ? 1.0 : local / 25.0)));
                    mc.options.keyUse.setDown(f >= 230 && local >= 30 && local < 38 && f < 460);
                    if (f == 400) {
                        shot("build");
                    }
                }
            });
        }

        if (wanted("archery")) {
            scene(mc, config, 0, 0, 12, 90.0F, true, () -> {
                config.setHeight(1.0);
                config.setDistance(5.0);
                select(mc, 1);
            }, () -> lookAt(mc, at(-8, 2.1, 30)));
            record("05-archery", 420, f -> {
                Vec3[] targets = {at(-8, 2.1, 30), at(-3, 2.1, 30), at(2, 2.1, 30)};
                int i = Math.min(2, Math.max(0, (f - 20) / 120));
                int local = f - 20 - i * 120;
                Vec3 from = targets[Math.max(0, i - 1)];
                lookAt(mc, lerp(from, targets[i], smooth(local / 30.0)));
                mc.options.keyUse.setDown(local >= 30 && local < 100);
                if (i == 1 && local == 104) {
                    shot("archery");
                }
            });
        }

        if (wanted("distance")) {
            scene(mc, config, 0, 0, -12, 0.0F, true, () -> {
                config.setDistance(2.0);
                select(mc, 4);
            }, () -> aim(mc, 200.0F, 12.0F));
            record("06-distance", 480, f -> {
                double t = f / 480.0;
                aim(mc, (float) lerp(200.0, 380.0, smooth(t)), (float) lerp(12.0, 22.0, smooth(t)));
                if (f >= 60 && (f - 60) % 12 == 0) {
                    config.setDistance(Math.min(SwivelConfig.MAX_DISTANCE, config.distance() + 0.5));
                }
                if (f == 40) {
                    shot("distance-near");
                } else if (f == 440) {
                    shot("distance-far");
                }
            });
        }

        if (wanted("tunnel")) {
            scene(mc, config, 9, 0, -18, 0.0F, true, () -> {
                config.setDistance(6.0);
                config.setSwivelWhileRunning(true);
                select(mc, 4);
            }, () -> aim(mc, 0.0F, 8.0F));
            record("07-tunnel", 480, f -> {
                mc.options.keyUp.setDown(f >= 20);
                double yaw;
                if (f < 200) {
                    yaw = 0.0;
                } else if (f < 250) {
                    yaw = lerp(0.0, 55.0, smooth((f - 200) / 50.0));
                } else if (f < 290) {
                    yaw = 55.0;
                } else if (f < 350) {
                    yaw = lerp(55.0, -55.0, smooth((f - 290) / 60.0));
                } else if (f < 380) {
                    yaw = -55.0;
                } else {
                    yaw = lerp(-55.0, 0.0, smooth((f - 380) / 60.0));
                }
                aim(mc, (float) yaw, 8.0F);
                if (f == 150) {
                    shot("tunnel-outside");
                } else if (f == 275) {
                    shot("tunnel-inside");
                }
            });
        }

        if (wanted("horse")) {
            scene(mc, config, 9, 0, -18, 0.0F, true, () -> {
                Vec3 p = at(9, 0, -18);
                cmd(mc, "summon minecraft:horse %.2f %.2f %.2f {Tame:1b,Variant:2,Rotation:[0f,0f],"
                        + "equipment:{saddle:{id:\"minecraft:saddle\",count:1}},"
                        + "attributes:[{id:\"minecraft:movement_speed\",base:0.18d}]}", p.x, p.y, p.z);
                run(mc, "ride @p mount @e[type=minecraft:horse,limit=1,sort=nearest]");
                config.setDistance(7.0);
                select(mc, 4);
            }, () -> aim(mc, 0.0F, 24.0F));
            waitFrames(200);
            record("08-horse", 420, f -> {
                mc.options.keyUp.setDown(f >= 20 && f < 410);
                aim(mc, (float) lerp(0.0, 330.0, Math.max(0.0, Math.min(1.0, (f - 40) / 360.0))), 24.0F);
                if (f == 250) {
                    shot("horse");
                }
            });
            once(() -> {
                release(mc);
                run(mc, "ride @p dismount");
                run(mc, "kill @e[type=minecraft:horse]");
            });
            waitFrames(10);
        }

        if (wanted("elytra")) {
            scene(mc, config, 0, 40, -30, 0.0F, true, () -> {
                run(mc, "item replace entity @p armor.chest with minecraft:elytra");
                select(mc, 3);
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }, () -> aim(mc, 0.0F, 15.0F));
            record("09-elytra", 450, f -> {
                if (f == 5) {
                    mc.player.getAbilities().flying = false;
                    mc.player.onUpdateAbilities();
                }
                mc.options.keyJump.setDown(f >= 20 && f < 24);
                mc.options.keyUse.setDown((f >= 45 && f < 49) || (f >= 250 && f < 254));
                double yaw = f < 100 ? 0.0 : lerp(0.0, 140.0, smooth((f - 100) / 340.0));
                double pitch = f < 60 ? 15.0 : lerp(15.0, -2.0, smooth((f - 60) / 60.0));
                aim(mc, (float) yaw, (float) pitch);
                if (f == 130) {
                    shot("elytra-1");
                } else if (f == 330) {
                    shot("elytra-2");
                }
            });
            once(() -> run(mc, "item replace entity @p armor.chest with minecraft:air"));
        }

        if (wanted("freelook")) {
            scene(mc, config, -12, 0, -28, 0.0F, false, () -> select(mc, 4), () -> aim(mc, 0.0F, 5.0F));
            record("10-free-look", 480, f -> {
                mc.options.keyUp.setDown(f >= 20 && f < 470);
                SwivelClient.freeLookKey().setDown(f >= 90 && f < 340);
                if (f >= 90 && f < 340) {
                    double yaw;
                    double pitch;
                    if (f < 150) {
                        yaw = lerp(0.0, -100.0, smooth((f - 90) / 60.0));
                        pitch = lerp(5.0, 0.0, smooth((f - 90) / 60.0));
                    } else if (f < 230) {
                        yaw = -100.0;
                        pitch = 0.0;
                    } else {
                        yaw = lerp(-100.0, 70.0, smooth((f - 230) / 80.0));
                        pitch = lerp(0.0, -12.0, smooth((f - 230) / 80.0));
                    }
                    aim(mc, (float) yaw, (float) pitch);
                }
                if (f == 200) {
                    shot("free-look");
                }
            });
        }

        if (wanted("settings")) {
            scene(mc, config, 0, 0, -12, 0.0F, true, () -> select(mc, 4), () -> aim(mc, 160.0F, 15.0F));
            once(() -> Screens.open(mc, new SwivelSettingsScreen(null, mc.options)));
            waitFrames(20);
            record("11-settings", 270, f -> {
                if (f == 60) {
                    shot("settings");
                } else if (f == 100) {
                    SwivelSelfTest.click(mc, SwivelSelfTest.find(Screens.current(mc), "swivel.options.swivel_running"));
                } else if (f == 170) {
                    SwivelSelfTest.click(mc, SwivelSelfTest.find(Screens.current(mc), "swivel.options.strafe"));
                }
            });
            once(() -> Screens.open(mc, null));
        }

        if (wanted("sunset")) {
            once(() -> run(mc, "time set 12300"));
            scene(mc, config, -4, 0, -16, 0.0F, true, () -> select(mc, 4), () -> aim(mc, 0.0F, 6.0F));
            record("12-sunset", 480, f -> {
                double t = smooth(f / 480.0);
                aim(mc, (float) lerp(0.0, 100.0, t), (float) lerp(6.0, 12.0, t));
                if (f >= 30 && (f - 30) % 30 == 0) {
                    config.setDistance(Math.min(8.0, config.distance() + 0.5));
                }
                if (f == 260) {
                    shot("sunset");
                }
            });
            once(() -> run(mc, "time set 1500"));
        }
    }

    private static void deleteWorld(Minecraft mc) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (java.nio.file.Files.exists(dir)) {
            try (var paths = java.nio.file.Files.walk(dir)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    java.nio.file.Files.delete(path);
                }
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
    }

    static void log(String message) {
        System.out.println("[swivel-showcase] " + message);
    }
}
