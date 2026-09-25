package dev.swivel.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

final class Cameras {
    private Cameras() {
    }

    static Camera main(Minecraft mc) {
        return mc.gameRenderer.getMainCamera();
    }

    static Vec3 position(Camera camera) {
        return camera.position();
    }

    static Vec3 forward(Camera camera) {
        Vector3fc forward = camera.forwardVector();
        return new Vec3(forward.x(), forward.y(), forward.z());
    }
}
