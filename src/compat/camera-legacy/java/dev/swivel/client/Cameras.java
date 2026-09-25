package dev.swivel.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

final class Cameras {
    private Cameras() {
    }

    static Camera main(Minecraft mc) {
        return mc.gameRenderer.getMainCamera();
    }

    static Vec3 position(Camera camera) {
        return camera.getPosition();
    }

    static Vec3 forward(Camera camera) {
        Vector3f forward = camera.getLookVector();
        return new Vec3(forward.x(), forward.y(), forward.z());
    }
}
