package dev.swivel.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Minecraft.class)
abstract class PickMixin {
    @WrapMethod(method = "pick")
    private void swivel$pick(float partialTicks, Operation<Void> original) {
        SwivelRuntime.withAim(partialTicks, () -> original.call(partialTicks));
    }
}
