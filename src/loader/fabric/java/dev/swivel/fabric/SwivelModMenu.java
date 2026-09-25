package dev.swivel.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.swivel.client.gui.SwivelSettingsScreen;
import net.minecraft.client.Minecraft;

public final class SwivelModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SwivelSettingsScreen(parent, Minecraft.getInstance().options);
    }
}
