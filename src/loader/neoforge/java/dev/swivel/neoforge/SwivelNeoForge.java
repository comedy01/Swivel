package dev.swivel.neoforge;

import dev.swivel.client.SwivelClient;
import dev.swivel.client.gui.SwivelSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = SwivelClient.MOD_ID, dist = Dist.CLIENT)
public final class SwivelNeoForge {
    public SwivelNeoForge(IEventBus modBus, ModContainer container) {
        SwivelClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping toggle = SwivelClient.createToggleKey();
        KeyMapping recenter = SwivelClient.createRecenterKey();
        KeyMapping freeLook = SwivelClient.createFreeLookKey();
        SwivelClient.setKeys(toggle, recenter, freeLook);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
            event.register(toggle);
            event.register(recenter);
            event.register(freeLook);
        });

        container.registerExtensionPoint(
                IConfigScreenFactory.class,
                (modContainer, parent) -> new SwivelSettingsScreen(parent, Minecraft.getInstance().options));
    }
}
