package dev.swivel.mixin;

import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("TAIL"))
    private void swivel$steer(boolean slowDown, float slowDownAmount, CallbackInfo ci) {
        SwivelRuntime.Steer steer = SwivelRuntime.steer(this, leftImpulse, forwardImpulse);
        if (steer == null) {
            return;
        }
        leftImpulse = steer.left();
        forwardImpulse = steer.forward();
        up = steer.forwardKey();
        down = steer.backwardKey();
        left = steer.leftKey();
        right = steer.rightKey();
    }
}
