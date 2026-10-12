package net.modder.psychosis.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** JSON-backed client preferences. Invalid values are clamped before they are used. */
public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("psychosis.json");
    private static ModConfig instance = new ModConfig();

    public int maxActiveMarkers = 6;
    public double renderDistance = 32.0D;
    public boolean enablePlayerTargeting = true;
    public boolean enableMobTargeting = true;
    public boolean enableSkyMarkers = true;
    public int phraseRotationInterval = 400;
    public float textSize = 1.35F;
    public boolean enableJitter = true;
    public float jitterIntensity = 1.5F;
    /** RGB, not ARGB. */
    public int markerColor = 0xFF0000;
    public float maxAlpha = 0.85F;

    private ModConfig() { }

    public static ModConfig get() {
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                ModConfig read = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), ModConfig.class);
                if (read != null) instance = read;
            }
            instance.validate();
            instance.save();
        } catch (IOException | RuntimeException ignored) {
            instance = new ModConfig();
        }
    }

    public void save() throws IOException {
        validate();
        Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
    }

    private void validate() {
        maxActiveMarkers = clamp(maxActiveMarkers, 1, 32);
        renderDistance = clamp(renderDistance, 8.0D, 64.0D);
        phraseRotationInterval = clamp(phraseRotationInterval, 20, 12_000);
        textSize = clamp(textSize, 0.5F, 3.0F);
        jitterIntensity = clamp(jitterIntensity, 0.0F, 8.0F);
        markerColor &= 0xFFFFFF;
        maxAlpha = clamp(maxAlpha, 0.05F, 1.0F);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
