package dev.swivel.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class SwivelConfig {
    public static final String FILE_NAME = "swivel.json";
    public static final boolean DEFAULT_ENABLED = true;
    public static final int MIN_TURN_SPEED = 1;
    public static final int MAX_TURN_SPEED = 10;
    public static final int DEFAULT_TURN_SPEED = 6;
    public static final double MIN_DISTANCE = 2.0;
    public static final double MAX_DISTANCE = 12.0;
    public static final double DISTANCE_STEP = 0.5;
    public static final double DEFAULT_DISTANCE = 4.0;
    public static final boolean DEFAULT_RAISE_CAMERA = true;
    public static final double MIN_HEIGHT = 0.0;
    public static final double MAX_HEIGHT = 1.5;
    public static final double HEIGHT_STEP = 0.1;
    public static final double DEFAULT_HEIGHT = 0.5;
    public static final boolean DEFAULT_STRAFE = false;
    public static final boolean DEFAULT_SWIVEL_WHILE_RUNNING = false;
    public static final boolean DEFAULT_SMOOTH_COLLISIONS = true;
    public static final boolean DEFAULT_CROSSHAIR = true;
    public static final boolean DEFAULT_ALIGN_ON_EXIT = true;
    public static final boolean DEFAULT_HIDE_CROSSHAIR_IN_FREE_LOOK = true;

    private static final Logger LOGGER = LoggerFactory.getLogger("swivel");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("enabled")
    private boolean enabled = DEFAULT_ENABLED;

    @SerializedName("turnSpeed")
    private int turnSpeed = DEFAULT_TURN_SPEED;

    @SerializedName("distance")
    private double distance = DEFAULT_DISTANCE;

    @SerializedName("raiseCamera")
    private boolean raiseCamera = DEFAULT_RAISE_CAMERA;

    @SerializedName("height")
    private double height = DEFAULT_HEIGHT;

    @SerializedName("strafe")
    private boolean strafe = DEFAULT_STRAFE;

    @SerializedName("swivelWhileRunning")
    private boolean swivelWhileRunning = DEFAULT_SWIVEL_WHILE_RUNNING;

    @SerializedName("smoothCollisions")
    private boolean smoothCollisions = DEFAULT_SMOOTH_COLLISIONS;

    @SerializedName("crosshair")
    private boolean crosshair = DEFAULT_CROSSHAIR;

    @SerializedName("alignOnExit")
    private boolean alignOnExit = DEFAULT_ALIGN_ON_EXIT;

    @SerializedName("hideCrosshairInFreeLook")
    private boolean hideCrosshairInFreeLook = DEFAULT_HIDE_CROSSHAIR_IN_FREE_LOOK;

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        enabled = value;
    }

    public int turnSpeed() {
        return turnSpeed;
    }

    public void setTurnSpeed(int value) {
        turnSpeed = Math.max(MIN_TURN_SPEED, Math.min(MAX_TURN_SPEED, value));
    }

    public float turnFraction() {
        return turnSpeed / (float) MAX_TURN_SPEED;
    }

    public float turnMinStep() {
        return 3.0F + turnSpeed * 2.0F;
    }

    public double distance() {
        return distance;
    }

    public void setDistance(double value) {
        double clamped = Double.isNaN(value) ? DEFAULT_DISTANCE : Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, value));
        distance = Math.round(clamped / DISTANCE_STEP) * DISTANCE_STEP;
    }

    public boolean raiseCamera() {
        return raiseCamera;
    }

    public void setRaiseCamera(boolean value) {
        raiseCamera = value;
    }

    public double height() {
        return height;
    }

    public void setHeight(double value) {
        double clamped = Double.isNaN(value) ? DEFAULT_HEIGHT : Math.max(MIN_HEIGHT, Math.min(MAX_HEIGHT, value));
        height = Math.round(clamped / HEIGHT_STEP) * HEIGHT_STEP;
    }

    public boolean strafe() {
        return strafe;
    }

    public void setStrafe(boolean value) {
        strafe = value;
    }

    public boolean swivelWhileRunning() {
        return swivelWhileRunning;
    }

    public void setSwivelWhileRunning(boolean value) {
        swivelWhileRunning = value;
    }

    public boolean smoothCollisions() {
        return smoothCollisions;
    }

    public void setSmoothCollisions(boolean value) {
        smoothCollisions = value;
    }

    public boolean crosshair() {
        return crosshair;
    }

    public void setCrosshair(boolean value) {
        crosshair = value;
    }

    public boolean alignOnExit() {
        return alignOnExit;
    }

    public void setAlignOnExit(boolean value) {
        alignOnExit = value;
    }

    public boolean hideCrosshairInFreeLook() {
        return hideCrosshairInFreeLook;
    }

    public void setHideCrosshairInFreeLook(boolean value) {
        hideCrosshairInFreeLook = value;
    }

    public void resetToDefaults() {
        enabled = DEFAULT_ENABLED;
        turnSpeed = DEFAULT_TURN_SPEED;
        distance = DEFAULT_DISTANCE;
        raiseCamera = DEFAULT_RAISE_CAMERA;
        height = DEFAULT_HEIGHT;
        strafe = DEFAULT_STRAFE;
        swivelWhileRunning = DEFAULT_SWIVEL_WHILE_RUNNING;
        smoothCollisions = DEFAULT_SMOOTH_COLLISIONS;
        crosshair = DEFAULT_CROSSHAIR;
        alignOnExit = DEFAULT_ALIGN_ON_EXIT;
        hideCrosshairInFreeLook = DEFAULT_HIDE_CROSSHAIR_IN_FREE_LOOK;
    }

    private void sanitize() {
        setTurnSpeed(turnSpeed);
        setDistance(distance);
        setHeight(height);
    }

    public static SwivelConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            SwivelConfig fresh = new SwivelConfig();
            fresh.saveQuietly(file);
            return fresh;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            SwivelConfig loaded = GSON.fromJson(reader, SwivelConfig.class);
            if (loaded == null) {
                throw new JsonParseException("config file is empty");
            }
            loaded.sanitize();
            return loaded;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not read {}; using defaults. {}", file, e.toString());
            moveAside(file);
            SwivelConfig fresh = new SwivelConfig();
            fresh.saveQuietly(file);
            return fresh;
        }
    }

    public void save(Path file) throws IOException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = absolute.resolveSibling(absolute.getFileName() + ".tmp");
        Files.writeString(temp, GSON.toJson(this) + System.lineSeparator(), StandardCharsets.UTF_8);
        try {
            Files.move(temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void saveQuietly(Path file) {
        try {
            save(file);
        } catch (IOException e) {
            LOGGER.warn("Could not save {}: {}", file, e.toString());
        }
    }

    private static void moveAside(Path file) {
        try {
            Files.move(
                    file,
                    file.resolveSibling(file.getFileName() + ".broken"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Could not back up unreadable config {}: {}", file, e.toString());
        }
    }
}
