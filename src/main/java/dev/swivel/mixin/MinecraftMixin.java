package dev.swivel.mixin;

import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void swivel$keybinds(CallbackInfo ci) {
        SwivelRuntime.beforeKeybinds();
    }

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void swivel$attack(CallbackInfoReturnable<Boolean> cir) {
        SwivelRuntime.beforeAction();
    }

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void swivel$use(CallbackInfo ci) {
        SwivelRuntime.beforeAction();
    }
}
