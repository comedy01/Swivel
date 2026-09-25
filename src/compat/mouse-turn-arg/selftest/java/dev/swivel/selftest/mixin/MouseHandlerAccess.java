package dev.swivel.selftest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccess {
    @Accessor("accumulatedDX")
    void swivel$setAccumulatedDX(double value);

    @Accessor("accumulatedDY")
    void swivel$setAccumulatedDY(double value);

    @Invoker("turnPlayer")
    void swivel$turnPlayer(double mousea);
}
