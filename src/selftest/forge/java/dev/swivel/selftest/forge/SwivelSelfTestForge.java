package dev.swivel.selftest.forge;

import dev.swivel.selftest.SwivelSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("swivel_selftest")
public final class SwivelSelfTestForge {
    public SwivelSelfTestForge() {
        SwivelSelfTest test = new SwivelSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
