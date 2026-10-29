package dev.swivel.mixin;

import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Steers whatever input the player has (keyboard, or a controller mod's own input), right after it ticks.
@Mixin(LocalPlayer.class)
abstract class PlayerInputMixin {
    @Inject(
            method = "aiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", ordinal = 0,
                    shift = At.Shift.AFTER))
    private void swivel$steer(CallbackInfo ci) {
        ClientInput input = ((LocalPlayer) (Object) this).input;
        InputAccessor access = (InputAccessor) input;
        Vec2 move = access.swivel$moveVector();
        SwivelRuntime.Steer steer = SwivelRuntime.steer(input, move.x, move.y);
        if (steer == null) {
            return;
        }
        access.swivel$setMoveVector(new Vec2(steer.left(), steer.forward()));
        Input keys = input.keyPresses;
        input.keyPresses = new Input(steer.forwardKey(), steer.backwardKey(), steer.leftKey(), steer.rightKey(),
                keys.jump(), keys.shift(), keys.sprint());
    }
}
