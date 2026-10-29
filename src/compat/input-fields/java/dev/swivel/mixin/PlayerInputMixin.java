package dev.swivel.mixin;

import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class PlayerInputMixin {
    @Inject(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", ordinal = 0,
                    shift = At.Shift.AFTER))
    private void swivel$steer(CallbackInfo ci) {
        Input input = ((LocalPlayer) (Object) this).input;
        SwivelRuntime.Steer steer = SwivelRuntime.steer(input, input.leftImpulse, input.forwardImpulse);
        if (steer == null) {
            return;
        }
        input.leftImpulse = steer.left();
        input.forwardImpulse = steer.forward();
        input.up = steer.forwardKey();
        input.down = steer.backwardKey();
        input.left = steer.leftKey();
        input.right = steer.rightKey();
    }
}
