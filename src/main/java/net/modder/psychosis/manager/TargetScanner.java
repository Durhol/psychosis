package net.modder.psychosis.manager;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.modder.psychosis.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Throttled ray-sampler that finds doors, windows, houses/structures, mobs, players, and sky.
 * Clusters blocks by proximity into unified targets (e.g. an entire house) instead of
 * fragmenting them across arbitrary grid boundaries.
 */
public final class TargetScanner {
    private static final int SCAN_INTERVAL_TICKS = 15;
    private static final int CAMERA_RAYS_PER_SCAN = 192;
    private final MarkerManager markers;
    private long ticks;

    public TargetScanner(MarkerManager markers) {
        this.markers = markers;
    }

    public void tick(Minecraft client) {
        ticks++;
        if (client.level == null || client.player == null || client.screen != null) return;

        markers.advance(ticks);
        if (ticks % SCAN_INTERVAL_TICKS != 0) return;

        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        Camera camera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.position();
        ModConfig config = ModConfig.get();

        scanRayHits(level, player, camera, cameraPos, config);
        if (config.enablePlayerTargeting) {
            scanPlayers(level, player, cameraPos, cameraForward(camera), config.renderDistance);
        }
        if (config.enableMobTargeting) {
            scanMobs(level, player, cameraPos, cameraForward(camera), config.renderDistance);
        }
        markers.endScan(ticks);
    }

    private void scanRayHits(ClientLevel level, LocalPlayer player, Camera camera, Vec3 origin, ModConfig config) {
        Vec3 forward = cameraForward(camera).normalize();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        if (right.lengthSqr() < 0.0001D) right = new Vec3(1.0D, 0.0D, 0.0D);
        right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        double halfFovRadians = Math.toRadians((Minecraft.getInstance().options.fov().get() * 0.5D) + 6.0D);
        double tangent = Math.tan(halfFovRadians);
        double distance = config.renderDistance;

        List<Cluster> clusters = new ArrayList<>();
        Vec3 bestSkyPoint = null;
        double bestSkyAlign = 0.0D;

        for (int index = 0; index < CAMERA_RAYS_PER_SCAN; index++) {
            double x = radicalInverse(index + (int) (ticks / SCAN_INTERVAL_TICKS), 2) * 2.0D - 1.0D;
            double y = radicalInverse(index + 37, 3) * 2.0D - 1.0D;
            Vec3 ray = forward.add(right.scale(x * tangent)).add(up.scale(y * tangent * 0.75D)).normalize();
            BlockHitResult hit = level.clip(new ClipContext(origin, origin.add(ray.scale(distance)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

            if (hit.getType() != HitResult.Type.BLOCK) {
                // Sky point candidate (looking up into the open sky)
                if (config.enableSkyMarkers && ray.y > 0.28D && forward.y > 0.12D) {
                    double align = forward.dot(ray);
                    if (align > bestSkyAlign) {
                        bestSkyAlign = align;
                        bestSkyPoint = origin.add(ray.scale(Math.min(distance * 0.72D, 26.0D)));
                    }
                }
                continue;
            }

            BlockPos pos = hit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            MarkerCategory category = classify(level, pos, state, origin, ray);
            if (category == null) continue;

            Vec3 target = hit.getLocation();
            if (!inFov(origin, forward, target)) continue;
            mergeIntoClusters(clusters, pos, category, target);
        }

        for (Cluster cluster : clusters) {
            markers.observe(cluster.key, cluster.category, cluster.bounds, cluster.target, ticks);
        }

        if (bestSkyPoint != null && inFov(origin, forward, bestSkyPoint)) {
            int yawBucket = (int) Math.floor((Math.atan2(forward.x, forward.z) + Math.PI) / 0.45D);
            int pitchBucket = (int) Math.floor(forward.y * 8.0D);
            AABB skyBox = new AABB(bestSkyPoint, bestSkyPoint).inflate(1.2D);
            markers.observe("sky:" + yawBucket + ":" + pitchBucket, MarkerCategory.SKYLINE, skyBox, bestSkyPoint, ticks);
        }
    }

    private static void mergeIntoClusters(List<Cluster> clusters, BlockPos pos, MarkerCategory category, Vec3 target) {
        double maxMergeDist = (category == MarkerCategory.DOOR) ? 2.5D :
                (category == MarkerCategory.WINDOW) ? 3.5D : 10.0D;

        Cluster best = null;
        for (Cluster c : clusters) {
            if (c.category == category && c.distanceTo(pos) <= maxMergeDist) {
                // Keep max structure cluster size reasonable (e.g. 28x20x28) so separate houses don't merge
                if (category == MarkerCategory.STRUCTURE && c.wouldBeTooBig(pos, 28.0D)) {
                    continue;
                }
                best = c;
                break;
            }
        }

        if (best == null) {
            String key = category.name().toLowerCase() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
            clusters.add(new Cluster(key, category, new AABB(pos), target));
        } else {
            best.add(pos, target);
        }
    }

    private static MarkerCategory classify(ClientLevel level, BlockPos pos, BlockState state, Vec3 cameraPos, Vec3 ray) {
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof FenceGateBlock || state.getBlock() instanceof TrapDoorBlock) {
            return MarkerCategory.DOOR;
        }
        if (state.getBlock() instanceof TransparentBlock || state.getBlock() instanceof StainedGlassPaneBlock) {
            return MarkerCategory.WINDOW;
        }
        if (state.getCollisionShape(level, pos).isEmpty()) return null;

        // Check if block is building material (part of a house/structure)
        if (isStructureBlock(state)) {
            return MarkerCategory.STRUCTURE;
        }

        // Only natural mountain cliffs high above camera qualify for skyline
        boolean openAbove = level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
        double heightAboveCamera = pos.getY() + 1.0D - cameraPos.y;
        if (openAbove && heightAboveCamera >= 9.0D && ray.y > 0.35D) {
            return MarkerCategory.SKYLINE;
        }

        return null;
    }

    private static boolean isStructureBlock(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("plank") || path.contains("log") || path.contains("wood")
                || path.contains("cobble") || path.contains("stone_brick") || path.contains("brick")
                || path.contains("stair") || path.contains("slab") || path.contains("fence")
                || path.contains("wall") || path.contains("terracotta") || path.contains("concrete")
                || path.contains("wool") || path.contains("door") || path.contains("glass")
                || path.contains("bookshelf") || path.contains("furnace") || path.contains("crafting")
                || path.contains("hay_block") || path.contains("torch") || path.contains("lantern")
                || path.contains("carpet") || path.contains("bed") || path.contains("chest")
                || path.contains("iron_bars");
    }

    private void scanPlayers(ClientLevel level, LocalPlayer self, Vec3 origin, Vec3 forward, double distance) {
        double maxDistanceSq = distance * distance;
        for (Player other : level.players()) {
            if (other == self || other.isSpectator() || !other.isAlive()) continue;
            AABB box = other.getBoundingBox();
            Vec3 target = new Vec3(box.getCenter().x, box.maxY - 0.15D, box.getCenter().z);
            if (target.distanceToSqr(origin) > maxDistanceSq || !inFov(origin, forward, target)) continue;
            if (!entityVisible(level, self, origin, box)) continue;
            markers.observe("player:" + other.getUUID(), MarkerCategory.PLAYER, box, target, ticks);
        }
    }

    private void scanMobs(ClientLevel level, LocalPlayer self, Vec3 origin, Vec3 forward, double distance) {
        double maxDistanceSq = distance * distance;
        AABB search = new AABB(origin, origin).inflate(distance);
        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> entity.isAlive() && !(entity instanceof Player))) {
            AABB box = mob.getBoundingBox();
            Vec3 target = new Vec3(box.getCenter().x, box.maxY - 0.05D, box.getCenter().z);
            if (target.distanceToSqr(origin) > maxDistanceSq || !inFov(origin, forward, target)) continue;
            if (!entityVisible(level, self, origin, box)) continue;
            markers.observe("mob:" + mob.getUUID(), MarkerCategory.MOB, box, target, ticks);
        }
    }

    private static boolean entityVisible(ClientLevel level, Player viewer, Vec3 from, AABB box) {
        Vec3 center = box.getCenter();
        Vec3 head = new Vec3(center.x, box.maxY - 0.05D, center.z);
        return rayReachesEntity(level, viewer, from, head, box)
                || rayReachesEntity(level, viewer, from, center, box);
    }

    private static boolean rayReachesEntity(ClientLevel level, Player viewer, Vec3 from, Vec3 to, AABB box) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        double boxDistance = distanceToAabb(from, box);
        if (hit.getType() == HitResult.Type.MISS) return true;
        return hit.getLocation().distanceTo(from) >= boxDistance - 0.2D;
    }

    private static double distanceToAabb(Vec3 point, AABB box) {
        double x = Math.max(box.minX - point.x, Math.max(0.0D, point.x - box.maxX));
        double y = Math.max(box.minY - point.y, Math.max(0.0D, point.y - box.maxY));
        double z = Math.max(box.minZ - point.z, Math.max(0.0D, point.z - box.maxZ));
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static boolean inFov(Vec3 origin, Vec3 forward, Vec3 target) {
        Vec3 direction = target.subtract(origin);
        if (direction.lengthSqr() < 0.0001D) return true;
        double fov = Minecraft.getInstance().options.fov().get();
        double threshold = Math.cos(Math.toRadians((fov * 0.5D) + 8.0D));
        return forward.normalize().dot(direction.normalize()) >= threshold;
    }

    private static double radicalInverse(int value, int base) {
        double reversed = 0.0D;
        double factor = 1.0D / base;
        while (value > 0) {
            reversed += (value % base) * factor;
            value /= base;
            factor /= base;
        }
        return reversed;
    }

    private static Vec3 cameraForward(Camera camera) {
        return new Vec3(camera.forwardVector().x(), camera.forwardVector().y(), camera.forwardVector().z());
    }

    private static final class Cluster {
        final String key;
        final MarkerCategory category;
        AABB bounds;
        Vec3 target;

        Cluster(String key, MarkerCategory category, AABB bounds, Vec3 target) {
            this.key = key;
            this.category = category;
            this.bounds = bounds;
            this.target = target;
        }

        double distanceTo(BlockPos pos) {
            double dx = Math.max(0.0D, Math.max(bounds.minX - (pos.getX() + 1), pos.getX() - bounds.maxX));
            double dy = Math.max(0.0D, Math.max(bounds.minY - (pos.getY() + 1), pos.getY() - bounds.maxY));
            double dz = Math.max(0.0D, Math.max(bounds.minZ - (pos.getZ() + 1), pos.getZ() - bounds.maxZ));
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        boolean wouldBeTooBig(BlockPos pos, double maxDim) {
            double newMinX = Math.min(bounds.minX, pos.getX());
            double newMaxX = Math.max(bounds.maxX, pos.getX() + 1);
            double newMinZ = Math.min(bounds.minZ, pos.getZ());
            double newMaxZ = Math.max(bounds.maxZ, pos.getZ() + 1);
            return (newMaxX - newMinX) > maxDim || (newMaxZ - newMinZ) > maxDim;
        }

        void add(BlockPos pos, Vec3 hitTarget) {
            bounds = new AABB(
                    Math.min(bounds.minX, pos.getX()),
                    Math.min(bounds.minY, pos.getY()),
                    Math.min(bounds.minZ, pos.getZ()),
                    Math.max(bounds.maxX, pos.getX() + 1),
                    Math.max(bounds.maxY, pos.getY() + 1),
                    Math.max(bounds.maxZ, pos.getZ() + 1)
            );
            target = bounds.getCenter();
        }
    }
}
