package dev.swivel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
abstract class CrosshairMixin {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z"))
    private boolean swivel$crosshair(boolean firstPerson) {
        return SwivelRuntime.crosshair(firstPerson);
    }
}
