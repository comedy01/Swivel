package dev.swivel.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
abstract class PlayerFadeMixin {
    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("RETURN"))
    private void swivel$markFade(LivingEntity entity, LivingEntityRenderState state, float partialTicks,
                                 CallbackInfo ci) {
        SwivelRuntime.markFade(state, entity);
    }

    @WrapOperation(
            method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private boolean swivel$fadeBody(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
                                    Operation<Boolean> original) {
        return !SwivelRuntime.fadeBody(SwivelRuntime.stateAlpha(state)) && original.call(renderer, state);
    }

    @ModifyExpressionValue(method = "submit", at = @At(value = "CONSTANT", args = "intValue=654311423"))
    private int swivel$fadeAlpha(int original, @Local(argsOnly = true) LivingEntityRenderState state) {
        float alpha = SwivelRuntime.stateAlpha(state);
        return SwivelRuntime.fadeBody(alpha) ? SwivelRuntime.fadeColor(alpha) : original;
    }

    @WrapOperation(
            method = "submit",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;shouldRenderLayers(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z"))
    private boolean swivel$fadeLayers(LivingEntityRenderer<?, ?, ?> renderer, LivingEntityRenderState state,
                                      Operation<Boolean> original) {
        return !SwivelRuntime.fadeLayers(SwivelRuntime.stateAlpha(state)) && original.call(renderer, state);
    }
}
