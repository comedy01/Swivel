package dev.swivel.selftest.mixin;

import dev.swivel.selftest.Showcase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "dev.swivel.client.SwivelRuntime", remap = false)
abstract class SwivelRuntimeClockMixin {
    @Redirect(method = "updateState", at = @At(value = "INVOKE", target = "Ljava/lang/System;nanoTime()J"))
    private static long swivelSelftest$nanoTime() {
        return Showcase.nanos();
    }
}
