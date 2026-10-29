package dev.swivel.client.gui;

import dev.swivel.client.SwivelClient;
import dev.swivel.config.SwivelConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

public final class SwivelSettingsScreen extends SwivelOptionsScreen {
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    public SwivelSettingsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, Component.translatable("swivel.options.title"));
    }

    @Override
    protected void addOptions() {
        SwivelConfig config = SwivelClient.config();

        addHeader(Component.translatable("swivel.options.section.third_person"));

        addRow(
                toggle("swivel.options.enabled", config::enabled, config::setEnabled),
                new StepSlider("swivel.options.turn_speed", SwivelConfig.MIN_TURN_SPEED, SwivelConfig.MAX_TURN_SPEED,
                        1, config.turnSpeed(), value -> Integer.toString((int) value),
                        value -> config.setTurnSpeed((int) value)));

        addRow(
                new StepSlider("swivel.options.distance", SwivelConfig.MIN_DISTANCE, SwivelConfig.MAX_DISTANCE,
                        SwivelConfig.DISTANCE_STEP, config.distance(),
                        value -> String.format(Locale.ROOT, "%.1f", value), config::setDistance),
                toggle("swivel.options.smooth_collisions", config::smoothCollisions, config::setSmoothCollisions));

        addRow(
                toggle("swivel.options.raise_camera", config::raiseCamera, config::setRaiseCamera),
                new StepSlider("swivel.options.height", SwivelConfig.MIN_HEIGHT, SwivelConfig.MAX_HEIGHT,
                        SwivelConfig.HEIGHT_STEP, config.height(),
                        value -> String.format(Locale.ROOT, "%.1f", value), config::setHeight));

        addRow(
                toggle("swivel.options.strafe", config::strafe, config::setStrafe),
                toggle("swivel.options.swivel_running", config::swivelWhileRunning, config::setSwivelWhileRunning));

        addRow(
                toggle("swivel.options.crosshair", config::crosshair, config::setCrosshair),
                toggle("swivel.options.align_on_exit", config::alignOnExit, config::setAlignOnExit));

        addRow(toggle("swivel.options.fade_player", config::fadePlayer, config::setFadePlayer), null);

        addHeader(Component.translatable("swivel.options.section.aiming"));

        addRow(aimCameraButton(config),
                toggle("swivel.options.spyglass", config::spyglassFirstPerson, config::setSpyglassFirstPerson));

        addHeader(freeLookHeader());

        addRow(toggle("swivel.options.free_look_crosshair", config::hideCrosshairInFreeLook,
                config::setHideCrosshairInFreeLook), null);

        addRow(keyBindsButton(), resetButton(config));
    }

    private static Component freeLookHeader() {
        KeyMapping key = SwivelClient.freeLookKey();
        if (key == null || key.isUnbound()) {
            return Component.translatable("swivel.options.section.free_look.unbound");
        }
        return Component.translatable("swivel.options.section.free_look", key.getTranslatedKeyMessage());
    }

    @Override
    public void removed() {
        super.removed();
        SwivelClient.saveConfig();
    }

    private AbstractWidget keyBindsButton() {
        return Button.builder(Component.translatable("swivel.options.keys"),
                        button -> ScreenOpener.open(minecraft, KeyBinds.screen(this, options)))
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("swivel.options.keys.tooltip")))
                .build();
    }

    private AbstractWidget resetButton(SwivelConfig config) {
        return Button.builder(Component.translatable("swivel.options.reset"), button -> {
                    config.resetToDefaults();
                    ScreenOpener.open(minecraft, new SwivelSettingsScreen(lastScreen, options));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("swivel.options.reset.tooltip")))
                .build();
    }

    private static AbstractWidget aimCameraButton(SwivelConfig config) {
        return Button.builder(aimCameraLabel(config.aimCamera()), button -> {
                    config.setAimCamera(config.aimCamera().next());
                    button.setMessage(aimCameraLabel(config.aimCamera()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable("swivel.options.aim_camera.tooltip")))
                .build();
    }

    private static Component aimCameraLabel(SwivelConfig.AimCamera mode) {
        return Component.translatable("options.generic_value", Component.translatable("swivel.options.aim_camera"),
                Component.translatable("swivel.options.aim_camera." + mode.key()));
    }

    private static AbstractWidget toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
        return Button.builder(toggleLabel(key, getter.getAsBoolean()), button -> {
                    setter.accept(!getter.getAsBoolean());
                    button.setMessage(toggleLabel(key, getter.getAsBoolean()));
                })
                .width(WIDTH)
                .tooltip(Tooltip.create(Component.translatable(key + ".tooltip")))
                .build();
    }

    private static Component toggleLabel(String key, boolean on) {
        Component state = Component.translatable(on ? "options.on" : "options.off");
        return Component.translatable("options.generic_value", Component.translatable(key), state);
    }

    private static final class StepSlider extends AbstractSliderButton {
        private final String captionKey;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<String> format;
        private final DoubleConsumer onChange;

        StepSlider(
                String captionKey,
                double min,
                double max,
                double step,
                double initial,
                DoubleFunction<String> format,
                DoubleConsumer onChange) {
            super(0, 0, WIDTH, HEIGHT, Component.empty(), 0.0);
            this.captionKey = captionKey;
            this.min = min;
            this.max = max;
            this.step = step;
            this.format = format;
            this.onChange = onChange;
            this.value = (snap(initial) - min) / (max - min);
            setTooltip(Tooltip.create(Component.translatable(captionKey + ".tooltip")));
            updateMessage();
        }

        private double snap(double raw) {
            double clamped = Math.max(min, Math.min(max, raw));
            double snapped = min + Math.round((clamped - min) / step) * step;
            return Math.max(min, Math.min(max, snapped));
        }

        private double current() {
            return snap(min + value * (max - min));
        }

        @Override
        protected void updateMessage() {
            Component shown = Component.literal(format.apply(current()));
            setMessage(Component.translatable("options.generic_value", Component.translatable(captionKey), shown));
        }

        @Override
        protected void applyValue() {
            double snapped = current();
            value = (snapped - min) / (max - min);
            onChange.accept(snapped);
        }
    }
}
