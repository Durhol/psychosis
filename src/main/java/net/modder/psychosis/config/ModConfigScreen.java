package net.modder.psychosis.config;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * In-game configuration screen accessible from Mod Menu or standalone.
 */
public final class ModConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config = ModConfig.get();

    public enum ColorPreset {
        BLOOD_RED("Blood Red", 0xFF0000),
        CRIMSON("Crimson", 0xDC143C),
        CREEPY_VIOLET("Creepy Violet", 0x9900FF),
        COLD_CYAN("Cold Cyan", 0x00E5FF),
        TOXIC_GREEN("Toxic Green", 0x00FF33),
        WARNING_YELLOW("Warning Yellow", 0xFFCC00),
        GHOST_WHITE("Ghost White", 0xFFFFFF);

        public final String label;
        public final int rgb;

        ColorPreset(String label, int rgb) {
            this.label = label;
            this.rgb = rgb;
        }

        public static ColorPreset fromRgb(int rgb) {
            for (ColorPreset p : values()) {
                if (p.rgb == rgb) return p;
            }
            return BLOOD_RED;
        }
    }

    public ModConfigScreen(Screen parent) {
        super(Component.literal("Psychosis - Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int colW = 155;
        int rowH = 24;
        int startY = 36;
        int leftX = this.width / 2 - colW - 5;
        int rightX = this.width / 2 + 5;

        // Row 1: Max Markers & Render Distance
        this.addRenderableWidget(new SimpleSlider(
                leftX, startY, colW, 20,
                (config.maxActiveMarkers - 1) / 15.0D,
                val -> Component.literal("Max Markers: " + (1 + (int) Math.round(val * 15.0D))),
                val -> config.maxActiveMarkers = 1 + (int) Math.round(val * 15.0D)
        ));

        this.addRenderableWidget(new SimpleSlider(
                rightX, startY, colW, 20,
                (config.renderDistance - 8.0D) / 56.0D,
                val -> Component.literal("Distance: " + (int) Math.round(8.0D + val * 56.0D) + "m"),
                val -> config.renderDistance = Math.round(8.0D + val * 56.0D)
        ));

        // Row 2: Shaking / Jitter Toggle & Jitter Intensity
        this.addRenderableWidget(CycleButton.onOffBuilder(config.enableJitter)
                .create(leftX, startY + rowH, colW, 20, Component.literal("Trembling"),
                        (btn, val) -> config.enableJitter = val));

        this.addRenderableWidget(new SimpleSlider(
                rightX, startY + rowH, colW, 20,
                (config.jitterIntensity - 0.5D) / 3.5D,
                val -> Component.literal("Shake: " + String.format("%.1fx", 0.5D + val * 3.5D)),
                val -> config.jitterIntensity = (float) (0.5D + val * 3.5D)
        ));

        // Row 3: Target Players & Target Mobs
        this.addRenderableWidget(CycleButton.onOffBuilder(config.enablePlayerTargeting)
                .create(leftX, startY + rowH * 2, colW, 20, Component.literal("Track Players"),
                        (btn, val) -> config.enablePlayerTargeting = val));

        this.addRenderableWidget(CycleButton.onOffBuilder(config.enableMobTargeting)
                .create(rightX, startY + rowH * 2, colW, 20, Component.literal("Track Mobs"),
                        (btn, val) -> config.enableMobTargeting = val));

        // Row 4: Target Sky & Marker Color
        this.addRenderableWidget(CycleButton.onOffBuilder(config.enableSkyMarkers)
                .create(leftX, startY + rowH * 3, colW, 20, Component.literal("Track Sky"),
                        (btn, val) -> config.enableSkyMarkers = val));

        ColorPreset initialPreset = ColorPreset.fromRgb(config.markerColor);
        this.addRenderableWidget(CycleButton.builder((ColorPreset p) -> Component.literal(p.label), initialPreset)
                .withValues(List.of(ColorPreset.values()))
                .create(rightX, startY + rowH * 3, colW, 20, Component.literal("Color"),
                        (btn, preset) -> config.markerColor = preset.rgb));

        // Row 5: Text Size
        this.addRenderableWidget(new SimpleSlider(
                this.width / 2 - 100, startY + rowH * 4, 200, 20,
                (config.textSize - 0.8D) / 1.6D,
                val -> Component.literal("Text Scale: " + String.format("%.1fx", 0.8D + val * 1.6D)),
                val -> config.textSize = (float) (0.8D + val * 1.6D)
        ));

        // Done button
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> this.onClose())
                .bounds(this.width / 2 - 100, startY + rowH * 5 + 10, 200, 20)
                .build());
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        super.render(drawContext, mouseX, mouseY, delta);

        // Header Title
        drawContext.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFF3333);

        // Author & Contact footer
        drawContext.drawCenteredString(this.font,
                Component.literal("Author: Durhol  |  TG: @tyxap or @Durhol"),
                this.width / 2, this.height - 18, 0xFFAAAAAA);
    }

    @Override
    public void onClose() {
        try {
            config.save();
        } catch (IOException ignored) {
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private static final class SimpleSlider extends AbstractSliderButton {
        private final Consumer<Double> onApply;
        private final Function<Double, Component> messageProvider;

        SimpleSlider(int x, int y, int width, int height, double initial,
                     Function<Double, Component> messageProvider, Consumer<Double> onApply) {
            super(x, y, width, height, Component.empty(), Math.max(0.0D, Math.min(1.0D, initial)));
            this.messageProvider = messageProvider;
            this.onApply = onApply;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(messageProvider.apply(this.value));
        }

        @Override
        protected void applyValue() {
            onApply.accept(this.value);
        }
    }
}
