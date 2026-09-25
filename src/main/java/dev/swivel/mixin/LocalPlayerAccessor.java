package dev.swivel.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("yRotLast")
    float swivel$yRotLast();

    @Accessor("yRotLast")
    void swivel$setYRotLast(float value);

    @Accessor("xRotLast")
    float swivel$xRotLast();

    @Accessor("xRotLast")
    void swivel$setXRotLast(float value);
}
