package net.modder.psychosis;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.modder.psychosis.config.ModConfig;
import net.modder.psychosis.manager.MarkerManager;
import net.modder.psychosis.manager.TargetScanner;
import net.modder.psychosis.render.WorldOverlayRenderer;

public final class PsychosisClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ModConfig.load();
        MarkerManager markers = new MarkerManager();
        TargetScanner scanner = new TargetScanner(markers);
        WorldOverlayRenderer renderer = new WorldOverlayRenderer(markers);

        ClientTickEvents.END_CLIENT_TICK.register(scanner::tick);
        HudRenderCallback.EVENT.register(renderer::onHudRender);
    }
}
