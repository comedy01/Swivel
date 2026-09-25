package dev.swivel.selftest.neoforge;

import dev.swivel.selftest.SwivelSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "swivel_selftest", dist = Dist.CLIENT)
public final class SwivelSelfTestNeoForge {
    public SwivelSelfTestNeoForge() {
        SwivelSelfTest test = new SwivelSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
