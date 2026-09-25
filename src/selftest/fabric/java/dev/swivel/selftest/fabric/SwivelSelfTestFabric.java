package dev.swivel.selftest.fabric;

import dev.swivel.selftest.Showcase;
import dev.swivel.selftest.SwivelSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class SwivelSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (Showcase.requested()) {
            Showcase.start();
            return;
        }
        SwivelSelfTest test = new SwivelSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
