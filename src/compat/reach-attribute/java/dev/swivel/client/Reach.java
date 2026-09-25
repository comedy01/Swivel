package dev.swivel.client;

import net.minecraft.client.player.LocalPlayer;

final class Reach {
    private Reach() {
    }

    static double of(LocalPlayer player) {
        return Math.max(player.blockInteractionRange(), player.entityInteractionRange());
    }
}
