package dev.swivel.selftest.mixin;

import dev.swivel.selftest.Showcase;
import dev.swivel.selftest.SwivelSelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void swivelSelftest$frame(boolean advanceGameTime, CallbackInfo ci) {
        SwivelSelfTest.frame();
        Showcase.frame();
    }
}
