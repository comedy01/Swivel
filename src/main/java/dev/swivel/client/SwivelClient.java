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

    public static void setKeys(KeyMapping toggle, KeyMapping recenter, KeyMapping freeLook) {
        toggleKey = toggle;
        recenterKey = recenter;
        freeLookKey = freeLook;
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
