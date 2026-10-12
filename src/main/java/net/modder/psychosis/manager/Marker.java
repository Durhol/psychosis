package net.modder.psychosis.manager;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Mutable on the client tick thread and copied to immutable render snapshots. */
final class Marker {
    final String key;
    final MarkerCategory category;
    AABB bounds;
    Vec3 target;
    String phrase;
    long firstSeenTick;
    long lastSeenTick;
    long lastPhraseTick;

    Marker(String key, MarkerCategory category, AABB bounds, Vec3 target, String phrase, long tick) {
        this.key = key;
        this.category = category;
        this.bounds = bounds;
        this.target = target;
        this.phrase = phrase;
        this.firstSeenTick = tick;
        this.lastSeenTick = tick;
        this.lastPhraseTick = tick;
    }
}
