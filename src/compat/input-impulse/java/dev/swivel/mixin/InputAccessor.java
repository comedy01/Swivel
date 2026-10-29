package dev.swivel.mixin;

import net.minecraft.client.player.ClientInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// The impulses are public on this version; the accessor only keeps the mixin list the same on every version.
@Mixin(ClientInput.class)
public interface InputAccessor {
    @Accessor("leftImpulse")
    float swivel$leftImpulse();
}
