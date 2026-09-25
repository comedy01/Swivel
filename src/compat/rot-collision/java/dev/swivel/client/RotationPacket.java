package dev.swivel.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

final class RotationPacket {
    private RotationPacket() {
    }

    static Packet<?> create(LocalPlayer player) {
        return new ServerboundMovePlayerPacket.Rot(
                player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision);
    }
}
