package dev.swivel.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.swivel.config.SwivelConfig;
import net.minecraft.client.KeyMapping;

import java.nio.file.Path;

public final class SwivelClient {
    public static final String MOD_ID = "swivel";

    private static SwivelConfig config = new SwivelConfig();
    private static Path configPath;
    private static KeyMapping toggleKey;
    private static KeyMapping recenterKey;
    private static KeyMapping freeLookKey;
    private static KeyMapping zoomInKey;
    private static KeyMapping zoomOutKey;
    private static KeyMapping swapShoulderKey;
    private static KeyMapping faceCameraKey;

    private SwivelClient() {
    }

    public static void init(Path configDir) {
        configPath = configDir.resolve(SwivelConfig.FILE_NAME);
        config = SwivelConfig.load(configPath);
    }

    public static KeyMapping createToggleKey() {
        return KeyFactory.create("key.swivel.toggle", InputConstants.UNKNOWN.getValue());
    }

    public static KeyMapping createRecenterKey() {
        return KeyFactory.create("key.swivel.recenter", InputConstants.UNKNOWN.getValue());
    }

    public static KeyMapping createFreeLookKey() {
        return KeyFactory.create("key.swivel.free_look", InputConstants.KEY_LALT);
    }

    public static KeyMapping createZoomInKey() {
        return KeyFactory.create("key.swivel.zoom_in", InputConstants.UNKNOWN.getValue());
    }

    public static KeyMapping createZoomOutKey() {
        return KeyFactory.create("key.swivel.zoom_out", InputConstants.UNKNOWN.getValue());
    }

    public static KeyMapping createSwapShoulderKey() {
        return KeyFactory.create("key.swivel.swap_shoulder", InputConstants.UNKNOWN.getValue());
    }

    public static KeyMapping createFaceCameraKey() {
        return KeyFactory.create("key.swivel.face_camera", InputConstants.UNKNOWN.getValue());
    }

    public static void setKeys(KeyMapping toggle, KeyMapping recenter, KeyMapping freeLook, KeyMapping zoomIn,
                               KeyMapping zoomOut, KeyMapping swapShoulder, KeyMapping faceCamera) {
        toggleKey = toggle;
        recenterKey = recenter;
        freeLookKey = freeLook;
        zoomInKey = zoomIn;
        zoomOutKey = zoomOut;
        swapShoulderKey = swapShoulder;
        faceCameraKey = faceCamera;
        ControlifyBindings.register(freeLook, recenter, toggle, zoomIn, zoomOut, swapShoulder, faceCamera);
    }

    public static KeyMapping toggleKey() {
        return toggleKey;
    }

    public static KeyMapping recenterKey() {
        return recenterKey;
    }

    public static KeyMapping freeLookKey() {
        return freeLookKey;
    }

    public static KeyMapping zoomInKey() {
        return zoomInKey;
    }

    public static KeyMapping zoomOutKey() {
        return zoomOutKey;
    }

    public static KeyMapping swapShoulderKey() {
        return swapShoulderKey;
    }

    public static KeyMapping faceCameraKey() {
        return faceCameraKey;
    }

    public static SwivelConfig config() {
        return config;
    }

    public static Path configPath() {
        return configPath;
    }

    public static void saveConfig() {
        config.saveQuietly(configPath);
    }
}
