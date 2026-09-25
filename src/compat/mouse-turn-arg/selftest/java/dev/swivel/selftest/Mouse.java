package dev.swivel.selftest;

import dev.swivel.selftest.mixin.MouseHandlerAccess;
import net.minecraft.client.Minecraft;

final class Mouse {
    private Mouse() {
    }

    static void move(Minecraft mc, double dx, double dy) {
        MouseHandlerAccess mouse = (MouseHandlerAccess) mc.mouseHandler;
        mouse.swivel$setAccumulatedDX(dx);
        mouse.swivel$setAccumulatedDY(dy);
        mouse.swivel$turnPlayer(0.0);
        mouse.swivel$setAccumulatedDX(0.0);
        mouse.swivel$setAccumulatedDY(0.0);
    }
}
