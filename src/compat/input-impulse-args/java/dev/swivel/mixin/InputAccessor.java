package dev.swivel.mixin;

import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface InputAccessor {
    @Accessor("leftImpulse")
    float swivel$leftImpulse();
}
