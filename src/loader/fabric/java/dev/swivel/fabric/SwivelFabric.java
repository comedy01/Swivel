package dev.swivel.fabric;

import dev.swivel.client.KeyRegistrar;
import dev.swivel.client.SwivelClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class SwivelFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        SwivelClient.init(FabricLoader.getInstance().getConfigDir());
        SwivelClient.setKeys(
                KeyRegistrar.register(SwivelClient.createToggleKey()),
                KeyRegistrar.register(SwivelClient.createRecenterKey()),
                KeyRegistrar.register(SwivelClient.createFreeLookKey()));
    }
}
