package dev.swivel.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface InputAccessor {
    @Accessor("moveVector")
    Vec2 swivel$moveVector();

    @Accessor("moveVector")
    void swivel$setMoveVector(Vec2 value);
}
