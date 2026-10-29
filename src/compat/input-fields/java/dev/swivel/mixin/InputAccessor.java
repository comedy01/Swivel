package dev.swivel.mixin;

import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Input.class)
public interface InputAccessor {
    @Accessor("leftImpulse")
    float swivel$leftImpulse();
}
