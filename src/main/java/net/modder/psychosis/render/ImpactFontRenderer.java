package net.modder.psychosis.render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders bold, creepy Impact-font text badges onto dynamic textures for HUD rendering.
 * Caches textures per phrase to avoid overhead.
 */
public final class ImpactFontRenderer {
    public record TextTexture(Identifier id, int width, int height) {}

    private final Map<String, TextTexture> cache = new HashMap<>();
    private final Font font;

    public ImpactFontRenderer() {
        Font chosen;
        try {
            chosen = new Font("Impact", Font.BOLD, 36);
            if (!"Impact".equalsIgnoreCase(chosen.getFamily())) {
                chosen = new Font(Font.SANS_SERIF, Font.BOLD, 36);
            }
        } catch (Throwable t) {
            chosen = new Font(Font.SANS_SERIF, Font.BOLD, 36);
        }
        this.font = chosen;
    }

    public TextTexture getOrCreate(String text, int hexColor) {
        String key = text + ":" + hexColor;
        TextTexture cached = cache.get(key);
        if (cached != null) return cached;

        // Measure text
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gDummy = dummy.createGraphics();
        gDummy.setFont(font);
        FontMetrics metrics = gDummy.getFontMetrics();
        int textW = metrics.stringWidth(text);
        int textH = metrics.getHeight();
        int ascent = metrics.getAscent();
        gDummy.dispose();

        int pad = 8;
        int imgW = Math.max(1, textW + pad * 2);
        int imgH = Math.max(1, textH + pad * 2);

        BufferedImage image = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setFont(font);

        int red = (hexColor >> 16) & 0xFF;
        int green = (hexColor >> 8) & 0xFF;
        int blue = hexColor & 0xFF;

        // Dark outline for readability on all backgrounds
        Color shadowColor = new Color(30, 0, 0, 220);
        g.setColor(shadowColor);
        int drawX = pad;
        int drawY = pad + ascent;
        int[] ox = {-2, 0, 2, -2, 2, -1, 1, 0, 0};
        int[] oy = {0, -2, 0, 2, 2, -1, 1, 2, -2};
        for (int i = 0; i < ox.length; i++) {
            g.drawString(text, drawX + ox[i], drawY + oy[i]);
        }

        // Vibrant main text in configured color (default crimson red)
        g.setColor(new Color(red, green, blue, 255));
        g.drawString(text, drawX, drawY);
        g.dispose();

        // Convert to Minecraft NativeImage (ABGR format)
        NativeImage nativeImage = new NativeImage(imgW, imgH, false);
        for (int y = 0; y < imgH; y++) {
            for (int x = 0; x < imgW; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int gr = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int abgr = (a << 24) | (b << 16) | (gr << 8) | r;
                nativeImage.setPixelABGR(x, y, abgr);
            }
        }

        String safeKey = "tex_" + Math.abs(key.hashCode());
        Identifier id = Identifier.fromNamespaceAndPath("psychosis", safeKey);
        DynamicTexture dynTex = new DynamicTexture(() -> "psychosis " + text, nativeImage);
        dynTex.upload();
        Minecraft.getInstance().getTextureManager().register(id, dynTex);

        TextTexture result = new TextTexture(id, imgW, imgH);
        cache.put(key, result);
        return result;
    }
}
