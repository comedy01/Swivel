package dev.swivel.mixin;

import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick", at = @At("TAIL"))
    private void swivel$steer(boolean slowDown, float slowDownAmount, CallbackInfo ci) {
        SwivelRuntime.Steer steer = SwivelRuntime.steer(this, leftImpulse, forwardImpulse);
        if (steer == null) {
            return;
        }
        leftImpulse = steer.left();
        forwardImpulse = steer.forward();
        keyPresses = new Input(steer.forwardKey(), steer.backwardKey(), steer.leftKey(), steer.rightKey(),
                keyPresses.jump(), keyPresses.shift(), keyPresses.sprint());
    }
}
