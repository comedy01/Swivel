package dev.swivel.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SwivelConfigTest {
    @TempDir
    Path dir;

    @Test
    void missingFileIsCreatedWithDefaults() {
        Path file = dir.resolve(SwivelConfig.FILE_NAME);
        SwivelConfig config = SwivelConfig.load(file);
        assertTrue(Files.isRegularFile(file));
        assertTrue(config.enabled());
        assertEquals(SwivelConfig.DEFAULT_TURN_SPEED, config.turnSpeed());
        assertEquals(SwivelConfig.DEFAULT_DISTANCE, config.distance(), 1.0E-9);
        assertEquals(SwivelConfig.DEFAULT_HEIGHT, config.height(), 1.0E-9);
        assertTrue(config.raiseCamera());
        assertFalse(config.strafe());
        assertFalse(config.swivelWhileRunning());
        assertTrue(config.smoothCollisions());
        assertTrue(config.crosshair());
        assertTrue(config.alignOnExit());
        assertTrue(config.hideCrosshairInFreeLook());
    }

    @Test
    void savedSettingsComeBack() throws IOException {
        Path file = dir.resolve(SwivelConfig.FILE_NAME);
        SwivelConfig config = new SwivelConfig();
        config.setEnabled(false);
        config.setTurnSpeed(3);
        config.setDistance(7.5);
        config.setHeight(0.8);
        config.setRaiseCamera(false);
        config.setStrafe(true);
        config.setSwivelWhileRunning(true);
        config.setSmoothCollisions(false);
        config.setCrosshair(false);
        config.setAlignOnExit(false);
        config.setHideCrosshairInFreeLook(false);
        config.save(file);

        SwivelConfig loaded = SwivelConfig.load(file);
        assertFalse(loaded.enabled());
        assertEquals(3, loaded.turnSpeed());
        assertEquals(7.5, loaded.distance(), 1.0E-9);
        assertEquals(0.8, loaded.height(), 1.0E-9);
        assertFalse(loaded.raiseCamera());
        assertTrue(loaded.strafe());
        assertTrue(loaded.swivelWhileRunning());
        assertFalse(loaded.smoothCollisions());
        assertFalse(loaded.crosshair());
        assertFalse(loaded.alignOnExit());
        assertFalse(loaded.hideCrosshairInFreeLook());
    }

    @Test
    void outOfRangeValuesAreClamped() throws IOException {
        Path file = dir.resolve(SwivelConfig.FILE_NAME);
        Files.writeString(file, "{\"turnSpeed\": 99, \"distance\": 100.3, \"height\": -2}", StandardCharsets.UTF_8);
        SwivelConfig config = SwivelConfig.load(file);
        assertEquals(SwivelConfig.MAX_TURN_SPEED, config.turnSpeed());
        assertEquals(SwivelConfig.MAX_DISTANCE, config.distance(), 1.0E-9);
        assertEquals(SwivelConfig.MIN_HEIGHT, config.height(), 1.0E-9);

        config.setTurnSpeed(-4);
        config.setDistance(0.1);
        assertEquals(SwivelConfig.MIN_TURN_SPEED, config.turnSpeed());
        assertEquals(SwivelConfig.MIN_DISTANCE, config.distance(), 1.0E-9);
    }

    @Test
    void olderFilesGetDefaultsForNewSettings() throws IOException {
        Path file = dir.resolve(SwivelConfig.FILE_NAME);
        Files.writeString(file, "{\"enabled\": true, \"height\": 0.7}", StandardCharsets.UTF_8);
        SwivelConfig config = SwivelConfig.load(file);
        assertTrue(config.raiseCamera());
        assertFalse(config.swivelWhileRunning());
        assertTrue(config.hideCrosshairInFreeLook());
        assertEquals(0.7, config.height(), 1.0E-9);
    }

    @Test
    void distanceSnapsToHalfBlocks() {
        SwivelConfig config = new SwivelConfig();
        config.setDistance(5.3);
        assertEquals(5.5, config.distance(), 1.0E-9);
        config.setDistance(Double.NaN);
        assertEquals(SwivelConfig.DEFAULT_DISTANCE, config.distance(), 1.0E-9);
    }

    @Test
    void brokenFileIsMovedAsideAndReplaced() throws IOException {
        Path file = dir.resolve(SwivelConfig.FILE_NAME);
        Files.writeString(file, "{ not json", StandardCharsets.UTF_8);
        SwivelConfig config = SwivelConfig.load(file);
        assertTrue(config.enabled());
        assertTrue(Files.isRegularFile(dir.resolve(SwivelConfig.FILE_NAME + ".broken")));
        assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("\"enabled\": true"));
    }

    @Test
    void topTurnSpeedTurnsInstantly() {
        SwivelConfig config = new SwivelConfig();
        config.setTurnSpeed(SwivelConfig.MAX_TURN_SPEED);
        assertEquals(1.0F, config.turnFraction(), 1.0E-6F);
    }

    @Test
    void resetRestoresEverything() {
        SwivelConfig config = new SwivelConfig();
        config.setEnabled(false);
        config.setTurnSpeed(1);
        config.setDistance(12.0);
        config.setHeight(1.5);
        config.setRaiseCamera(false);
        config.setStrafe(true);
        config.setSwivelWhileRunning(true);
        config.setSmoothCollisions(false);
        config.setCrosshair(false);
        config.setAlignOnExit(false);
        config.setHideCrosshairInFreeLook(false);
        config.resetToDefaults();
        assertTrue(config.enabled());
        assertEquals(SwivelConfig.DEFAULT_TURN_SPEED, config.turnSpeed());
        assertEquals(SwivelConfig.DEFAULT_DISTANCE, config.distance(), 1.0E-9);
        assertEquals(SwivelConfig.DEFAULT_HEIGHT, config.height(), 1.0E-9);
        assertTrue(config.raiseCamera());
        assertFalse(config.strafe());
        assertFalse(config.swivelWhileRunning());
        assertTrue(config.smoothCollisions());
        assertTrue(config.crosshair());
        assertTrue(config.alignOnExit());
        assertTrue(config.hideCrosshairInFreeLook());
    }
}
