package dev.swivel.forge;

import dev.swivel.client.SwivelClient;
import dev.swivel.client.gui.SwivelSettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

@Mod(SwivelClient.MOD_ID)
public final class SwivelForge {
    public SwivelForge() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        SwivelClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping toggle = SwivelClient.createToggleKey();
        KeyMapping recenter = SwivelClient.createRecenterKey();
        KeyMapping freeLook = SwivelClient.createFreeLookKey();
        KeyMapping zoomIn = SwivelClient.createZoomInKey();
        KeyMapping zoomOut = SwivelClient.createZoomOutKey();
        KeyMapping swapShoulder = SwivelClient.createSwapShoulderKey();
        KeyMapping faceCamera = SwivelClient.createFaceCameraKey();
        SwivelClient.setKeys(toggle, recenter, freeLook, zoomIn, zoomOut, swapShoulder, faceCamera);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(toggle);
            event.register(recenter);
            event.register(freeLook);
            event.register(zoomIn);
            event.register(zoomOut);
            event.register(swapShoulder);
            event.register(faceCamera);
        });

        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new SwivelSettingsScreen(parent, client.options)));
    }
}
