package dev.swivel.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.swivel.client.SwivelRuntime;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    private BlockGetter level;

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void move(float forwards, float up, float right);

    @Inject(method = "setup", at = @At("HEAD"))
    private void swivel$state(BlockGetter area, Entity focused, boolean detached, boolean mirrored, float partialTicks,
                              CallbackInfo ci) {
        SwivelRuntime.updateState(partialTicks);
    }

    @WrapOperation(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float swivel$yaw(Entity target, float partialTicks, Operation<Float> original) {
        return SwivelRuntime.cameraYaw(target, original.call(target, partialTicks));
    }

    @WrapOperation(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float swivel$pitch(Entity target, float partialTicks, Operation<Float> original) {
        return SwivelRuntime.cameraPitch(target, original.call(target, partialTicks));
    }

    @WrapOperation(
            method = "setup",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getMaxZoom(F)F"))
    private float swivel$distance(Camera camera, float distance, Operation<Float> original) {
        Vector3f up = camera.getUpVector();
        float lift = SwivelRuntime.cameraLift(level, entity, camera.getPosition(), new Vec3(up.x(), up.y(), up.z()));
        if (lift > 0.0F) {
            move(0.0F, lift, 0.0F);
        }
        return SwivelRuntime.cameraDistance(distance, wanted -> original.call(camera, wanted));
    }
}
