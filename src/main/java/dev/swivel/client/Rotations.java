package dev.swivel.client;

import dev.swivel.mixin.LocalPlayerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;

final class Rotations {
    private Rotations() {
    }

    static void announce(Minecraft mc, LocalPlayer player) {
        LocalPlayerAccessor sent = (LocalPlayerAccessor) player;
        if (sent.swivel$yRotLast() == player.getYRot() && sent.swivel$xRotLast() == player.getXRot()) {
            return;
        }
        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            return;
        }
        connection.send(RotationPacket.create(player));
        sent.swivel$setYRotLast(player.getYRot());
        sent.swivel$setXRotLast(player.getXRot());
    }
}
