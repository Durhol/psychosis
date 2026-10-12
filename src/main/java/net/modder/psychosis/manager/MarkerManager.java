package net.modder.psychosis.manager;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.modder.psychosis.config.ModConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Owns marker lifetime. Scanner calls observe; the renderer receives deduplicated snapshots. */
public final class MarkerManager {
    private static final long LOST_TARGET_TTL_TICKS = 40L;
    private static final double MIN_MARKER_SPACING_SQ = 6.5D * 6.5D; // Keep at least 6.5 blocks between markers
    private final Map<String, Marker> active = new HashMap<>();
    private final Random random = new Random();
    private long currentTick;

    public void observe(String key, MarkerCategory category, AABB bounds, Vec3 target, long tick) {
        Marker marker = active.get(key);
        if (marker == null || marker.category != category) {
            marker = new Marker(key, category, bounds, target, nextPhrase(category), tick);
            active.put(key, marker);
        } else {
            marker.bounds = bounds;
            marker.target = target;
            marker.lastSeenTick = tick;
        }
    }

    public void advance(long tick) {
        currentTick = tick;
    }

    public void endScan(long tick) {
        currentTick = tick;
        ModConfig config = ModConfig.get();
        active.values().removeIf(marker -> tick - marker.lastSeenTick > LOST_TARGET_TTL_TICKS);
        for (Marker marker : active.values()) {
            if (tick - marker.lastPhraseTick >= config.phraseRotationInterval) {
                marker.phrase = nextPhrase(marker.category);
                marker.lastPhraseTick = tick;
            }
        }
    }

    public long currentTick() {
        return currentTick;
    }

    public List<RenderMarker> snapshot(Vec3 cameraPosition, long tick) {
        double maxDistanceSq = Math.pow(ModConfig.get().renderDistance, 2);
        // Sort by category priority first, then by distance to player
        List<Marker> eligible = active.values().stream()
                .filter(marker -> tick - marker.lastSeenTick <= LOST_TARGET_TTL_TICKS)
                .filter(marker -> marker.target.distanceToSqr(cameraPosition) <= maxDistanceSq)
                .sorted(Comparator.comparingInt((Marker m) -> -priority(m.category))
                        .thenComparingDouble(m -> m.target.distanceToSqr(cameraPosition)))
                .toList();

        int budget = ModConfig.get().maxActiveMarkers;
        List<Marker> chosen = new ArrayList<>();

        for (Marker marker : eligible) {
            if (chosen.size() >= budget) break;

            // Spatial separation check: do not place multiple markers on the same house or spot
            boolean tooClose = false;
            for (Marker other : chosen) {
                if (marker.target.distanceToSqr(other.target) < MIN_MARKER_SPACING_SQ) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                chosen.add(marker);
            }
        }

        return chosen.stream()
                .map(marker -> new RenderMarker(marker.key, marker.category, marker.bounds, marker.target,
                        marker.phrase, marker.firstSeenTick, marker.lastSeenTick))
                .toList();
    }

    private static int priority(MarkerCategory category) {
        return switch (category) {
            case PLAYER -> 6;
            case MOB -> 5;
            case DOOR -> 4;
            case WINDOW -> 3;
            case STRUCTURE -> 2;
            case SKYLINE -> 1;
        };
    }

    private String nextPhrase(MarkerCategory category) {
        Set<String> visible = new HashSet<>();
        for (Marker marker : active.values()) visible.add(marker.phrase);
        return PhraseManager.next(category, random, visible);
    }
}
