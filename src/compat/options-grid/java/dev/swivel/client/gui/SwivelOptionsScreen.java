package dev.swivel.client.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public abstract class SwivelOptionsScreen extends OptionsSubScreen {
    private int rows;
    private int openRow = -1;

    protected SwivelOptionsScreen(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    protected abstract void addOptions();

    @Override
    protected void init() {
        rows = 0;
        openRow = -1;
        addOptions();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20)
                .build());
    }

    protected void addHeader(Component text) {
    }

    protected void addRow(AbstractWidget left, AbstractWidget right) {
        if (right == null && openRow >= 0) {
            left.setPosition(width / 2 + 5, rowY(openRow));
            addRenderableWidget(left);
            openRow = -1;
            return;
        }
        int y = rowY(rows);
        left.setPosition(width / 2 - 155, y);
        addRenderableWidget(left);
        if (right != null) {
            right.setPosition(width / 2 + 5, y);
            addRenderableWidget(right);
        } else {
            openRow = rows;
        }
        rows++;
    }

    private int rowY(int row) {
        return height / 6 - 12 + row * 24;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);
    }
}
