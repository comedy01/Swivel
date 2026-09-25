package dev.swivel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;

final class Reach {
    private Reach() {
    }

    static double of(LocalPlayer player) {
        MultiPlayerGameMode mode = Minecraft.getInstance().gameMode;
        if (mode == null) {
            return 3.0;
        }
        return mode.hasFarPickRange() ? 6.0 : mode.getPickRange();
    }
}
