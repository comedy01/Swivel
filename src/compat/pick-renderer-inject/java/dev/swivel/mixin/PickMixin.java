package dev.swivel.mixin;

import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class PickMixin {
    @Inject(method = "pick", at = @At("HEAD"))
    private void swivel$aim(float partialTicks, CallbackInfo ci) {
        SwivelRuntime.beginAim(partialTicks);
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void swivel$restore(float partialTicks, CallbackInfo ci) {
        SwivelRuntime.endAim();
    }
}
