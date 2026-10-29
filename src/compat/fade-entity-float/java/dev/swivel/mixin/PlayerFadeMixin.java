package dev.swivel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
abstract class PlayerFadeMixin {
    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/world/entity/LivingEntity;)Z"))
    private boolean swivel$fadeBody(LivingEntityRenderer<?, ?> renderer, LivingEntity entity,
                                    Operation<Boolean> original) {
        return !SwivelRuntime.fadeBody(SwivelRuntime.playerAlpha(entity)) && original.call(renderer, entity);
    }

    @ModifyExpressionValue(method = "render", at = @At(value = "CONSTANT", args = "floatValue=0.15F"))
    private float swivel$fadeAlpha(float original, @Local(argsOnly = true) LivingEntity entity) {
        float alpha = SwivelRuntime.playerAlpha(entity);
        return SwivelRuntime.fadeBody(alpha) ? SwivelRuntime.fadeAlpha(alpha) : original;
    }

    @WrapOperation(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSpectator()Z"))
    private boolean swivel$fadeLayers(LivingEntity entity, Operation<Boolean> original) {
        return SwivelRuntime.fadeLayers(SwivelRuntime.playerAlpha(entity)) || original.call(entity);
    }
}
