package net.modder.psychosis.render;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.modder.psychosis.config.ModConfig;
import net.modder.psychosis.manager.MarkerManager;
import net.modder.psychosis.manager.RenderMarker;
import org.joml.Vector3fc;

import java.util.Random;

/**
 * Renders shaky 2D analogue horror frames, angled pointer arrows, and trembling
 * Impact-font text on the HUD, perfectly projected from 3D world targets.
 */
public final class WorldOverlayRenderer {
    private static final long FADE_TICKS = 10L;
    private final MarkerManager markers;
    private final ImpactFontRenderer fontRenderer = new ImpactFontRenderer();
    private final Random random = new Random();

    public WorldOverlayRenderer(MarkerManager markers) {
        this.markers = markers;
    }

    public void onHudRender(GuiGraphics drawContext, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (mc.screen != null) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        long now = markers.currentTick();
        ModConfig config = ModConfig.get();

        int guiWidth = drawContext.guiWidth();
        int guiHeight = drawContext.guiHeight();
        double fov = mc.options.fov().get();

        long frameSeed = System.nanoTime();

        for (RenderMarker marker : markers.snapshot(cameraPos, now)) {
            double distance = marker.target().distanceTo(cameraPos);
            float alpha = calculateAlpha(marker, now, distance, config);
            if (alpha <= 0.02F) continue;

            // Strict Line of Sight check in real-time
            if (!hasLineOfSight(mc, cameraPos, marker.target())) continue;

            // Project 3D bounds to 2D screen frame
            ScreenRect rect = projectBoundsToScreen(marker.bounds(), camera, guiWidth, guiHeight, fov);
            if (rect == null) continue;

            // Discard if completely off screen
            if (rect.maxX < -20 || rect.minX > guiWidth + 20 || rect.maxY < -20 || rect.minY > guiHeight + 20) {
                continue;
            }

            int colorARGB = ((int) (alpha * 255.0F) << 24) | (config.markerColor & 0x00FFFFFF);

            // 1. Draw 2D Shaky Frame (Рамка) around the target
            drawShakyFrame(drawContext, rect, colorARGB, frameSeed, marker.key(), config);

            // 2. Prepare Impact font text
            ImpactFontRenderer.TextTexture textTex = fontRenderer.getOrCreate(marker.phrase(), config.markerColor);

            // 3. Position text slightly higher and to the side
            boolean placeRight = (rect.maxX + textTex.width() + 40 <= guiWidth);
            float textX = placeRight ? (rect.maxX + 26.0F) : (rect.minX - textTex.width() - 26.0F);
            float textY = Math.max(16.0F, rect.minY - 24.0F);

            // Text Jitter (trembling effect)
            float textJitterX = 0.0F;
            float textJitterY = 0.0F;
            if (config.enableJitter && config.jitterIntensity > 0.0F) {
                random.setSeed(frameSeed + marker.key().hashCode());
                textJitterX = (random.nextFloat() - 0.5F) * config.jitterIntensity * 3.5F;
                textJitterY = (random.nextFloat() - 0.5F) * config.jitterIntensity * 3.5F;
            }

            // 4. Draw Angled Pointer Arrow from text pointing towards the frame
            float arrowStartX = placeRight ? (textX - 6.0F + textJitterX) : (textX + textTex.width() + 6.0F + textJitterX);
            float arrowStartY = textY + textTex.height() * 0.5F + textJitterY;
            float arrowEndX = placeRight ? (rect.maxX + 3.0F) : (rect.minX - 3.0F);
            float arrowEndY = rect.minY + Math.min(30.0F, (rect.maxY - rect.minY) * 0.25F);

            drawAngledArrow(drawContext, arrowStartX, arrowStartY, arrowEndX, arrowEndY, colorARGB, frameSeed, config);

            // 5. Draw Impact Font Text Badge
            drawContext.blit(RenderPipelines.GUI_TEXTURED, textTex.id(),
                    (int) (textX + textJitterX), (int) (textY + textJitterY),
                    0.0F, 0.0F, textTex.width(), textTex.height(),
                    textTex.width(), textTex.height(), colorARGB);
        }
    }

    /**
     * Draws a shaky, trembling 2D rectangular frame around the object.
     */
    private void drawShakyFrame(GuiGraphics drawContext, ScreenRect r, int colorARGB,
                                long frameSeed, String key, ModConfig config) {
        float jx1 = 0, jy1 = 0, jx2 = 0, jy2 = 0;
        if (config.enableJitter && config.jitterIntensity > 0.0F) {
            random.setSeed(frameSeed * 17L + key.hashCode());
            jx1 = (random.nextFloat() - 0.5F) * config.jitterIntensity * 2.0F;
            jy1 = (random.nextFloat() - 0.5F) * config.jitterIntensity * 2.0F;
            jx2 = (random.nextFloat() - 0.5F) * config.jitterIntensity * 2.0F;
            jy2 = (random.nextFloat() - 0.5F) * config.jitterIntensity * 2.0F;
        }

        int x1 = (int) (r.minX + jx1);
        int y1 = (int) (r.minY + jy1);
        int x2 = (int) (r.maxX + jx2);
        int y2 = (int) (r.maxY + jy2);
        int thickness = 2;

        // Top edge
        drawContext.fill(x1, y1, x2, y1 + thickness, colorARGB);
        // Bottom edge
        drawContext.fill(x1, y2 - thickness, x2, y2, colorARGB);
        // Left edge
        drawContext.fill(x1, y1, x1 + thickness, y2, colorARGB);
        // Right edge
        drawContext.fill(x2 - thickness, y1, x2, y2, colorARGB);
    }

    /**
     * Draws an angled red pointer arrow with an arrowhead pointing down/diagonally at the target.
     */
    private void drawAngledArrow(GuiGraphics drawContext, float x1, float y1, float x2, float y2,
                                 int colorARGB, long frameSeed, ModConfig config) {
        float jx = 0, jy = 0;
        if (config.enableJitter && config.jitterIntensity > 0.0F) {
            random.setSeed(frameSeed * 37L + (long)(x1 + y1));
            jx = (random.nextFloat() - 0.5F) * config.jitterIntensity * 1.8F;
            jy = (random.nextFloat() - 0.5F) * config.jitterIntensity * 1.8F;
        }
        x1 += jx; y1 += jy;
        x2 += jx; y2 += jy;

        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length < 4.0F) return;

        double angle = Math.atan2(dy, dx);

        // Main arrow shaft
        drawLine(drawContext, (int) x1, (int) y1, (int) x2, (int) y2, colorARGB);

        // Arrowhead fin 1
        double finAngle1 = angle + 2.5D;
        float fx1 = x2 + (float) (Math.cos(finAngle1) * 11.0D);
        float fy1 = y2 + (float) (Math.sin(finAngle1) * 11.0D);
        drawLine(drawContext, (int) x2, (int) y2, (int) fx1, (int) fy1, colorARGB);

        // Arrowhead fin 2
        double finAngle2 = angle - 2.5D;
        float fx2 = x2 + (float) (Math.cos(finAngle2) * 11.0D);
        float fy2 = y2 + (float) (Math.sin(finAngle2) * 11.0D);
        drawLine(drawContext, (int) x2, (int) y2, (int) fx2, (int) fy2, colorARGB);
    }

    private static void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            g.fill(x0, y0, x0 + 2, y0 + 2, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private static boolean hasLineOfSight(Minecraft mc, Vec3 cameraPos, Vec3 target) {
        BlockHitResult hit = mc.level.clip(new ClipContext(
                cameraPos, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player
        ));
        if (hit.getType() == HitResult.Type.MISS) return true;
        double hitDist = hit.getLocation().distanceTo(cameraPos);
        double targetDist = target.distanceTo(cameraPos);
        return hitDist >= targetDist - 0.75D;
    }

    /**
     * Projects a 3D AABB box onto the 2D camera viewport to compute its exact screen-space 2D frame.
     */
    private static ScreenRect projectBoundsToScreen(AABB box, Camera camera, int guiWidth, int guiHeight, double fov) {
        Vec3 camPos = camera.position();
        Vec3 center = box.getCenter();
        Vec3 relCenter = center.subtract(camPos);

        Vector3fc fwd = camera.forwardVector();
        double centerFwdDot = relCenter.x * fwd.x() + relCenter.y * fwd.y() + relCenter.z * fwd.z();
        if (centerFwdDot <= 0.2D) return null; // Behind camera

        // Compute camera plane basis vectors
        Vector3fc left = camera.leftVector();
        Vec3 rightVec = new Vec3(-left.x(), -left.y(), -left.z()).normalize();
        Vec3 upVec = new Vec3(camera.upVector().x(), camera.upVector().y(), camera.upVector().z()).normalize();

        double boxWidth = Math.max(0.8D, Math.max(box.maxX - box.minX, box.maxZ - box.minZ));
        double boxHeight = Math.max(0.8D, box.maxY - box.minY);

        Vec3 pLeft = center.add(rightVec.scale(-boxWidth * 0.5D));
        Vec3 pRight = center.add(rightVec.scale(boxWidth * 0.5D));
        Vec3 pTop = center.add(upVec.scale(boxHeight * 0.5D));
        Vec3 pBottom = center.add(upVec.scale(-boxHeight * 0.5D));

        Vec2f sLeft = projectPoint(pLeft, camera, guiWidth, guiHeight, fov);
        Vec2f sRight = projectPoint(pRight, camera, guiWidth, guiHeight, fov);
        Vec2f sTop = projectPoint(pTop, camera, guiWidth, guiHeight, fov);
        Vec2f sBottom = projectPoint(pBottom, camera, guiWidth, guiHeight, fov);

        if (sLeft == null || sRight == null || sTop == null || sBottom == null) return null;

        float minX = Math.min(sLeft.x, sRight.x);
        float maxX = Math.max(sLeft.x, sRight.x);
        float minY = Math.min(sTop.y, sBottom.y);
        float maxY = Math.max(sTop.y, sBottom.y);

        // Ensure minimum frame dimensions so distant targets don't collapse to a point
        float minSize = 20.0F;
        if (maxX - minX < minSize) {
            float mid = (minX + maxX) * 0.5F;
            minX = mid - minSize * 0.5F;
            maxX = mid + minSize * 0.5F;
        }
        if (maxY - minY < minSize) {
            float mid = (minY + maxY) * 0.5F;
            minY = mid - minSize * 0.5F;
            maxY = mid + minSize * 0.5F;
        }

        return new ScreenRect(minX, minY, maxX, maxY);
    }

    private static Vec2f projectPoint(Vec3 point, Camera camera, int guiWidth, int guiHeight, double fov) {
        Vec3 rel = point.subtract(camera.position());
        Vector3fc fwd = camera.forwardVector();
        double fwdDot = rel.x * fwd.x() + rel.y * fwd.y() + rel.z * fwd.z();
        if (fwdDot <= 0.05D) return null;

        Vector3fc left = camera.leftVector();
        double rightDot = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
        Vector3fc up = camera.upVector();
        double upDot = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();

        double tanHalfFov = Math.tan(Math.toRadians(fov * 0.5D));
        double aspect = (double) guiWidth / (double) guiHeight;

        double ndcX = rightDot / (fwdDot * tanHalfFov * aspect);
        double ndcY = upDot / (fwdDot * tanHalfFov);

        float screenX = (float) ((ndcX + 1.0D) * 0.5D * guiWidth);
        float screenY = (float) ((1.0D - ndcY) * 0.5D * guiHeight);

        return new Vec2f(screenX, screenY);
    }

    private static float calculateAlpha(RenderMarker marker, long now, double distance, ModConfig config) {
        float appear = Math.min(1.0F, (now - marker.firstSeenTick()) / (float) FADE_TICKS);
        float disappear = Math.min(1.0F, (40L - Math.max(0L, now - marker.lastSeenTick())) / (float) FADE_TICKS);
        double fadeStart = config.renderDistance * 0.80D;
        float distanceFade = distance <= fadeStart ? 1.0F
                : (float) Math.max(0.0D, (config.renderDistance - distance) / (config.renderDistance - fadeStart));
        return config.maxAlpha * appear * disappear * distanceFade;
    }

    private record ScreenRect(float minX, float minY, float maxX, float maxY) {}
    private record Vec2f(float x, float y) {}
}
