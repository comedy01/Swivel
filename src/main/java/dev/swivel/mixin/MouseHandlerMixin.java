package dev.swivel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @WrapOperation(
            method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void swivel$turn(LocalPlayer player, double xo, double yo, Operation<Void> original) {
        if (!SwivelRuntime.turnCamera(xo, yo)) {
            original.call(player, xo, yo);
        }
    }
}
