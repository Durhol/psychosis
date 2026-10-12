package net.modder.psychosis.manager;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Thread-safe render payload: contains no live world or entity references. */
public record RenderMarker(String key, MarkerCategory category, AABB bounds, Vec3 target,
                           String phrase, long firstSeenTick, long lastSeenTick) { }
