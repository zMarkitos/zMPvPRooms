package dev.zm.pvprooms.utils;

import dev.zm.pvprooms.hooks.worldguard.WorldGuardHook;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArenaBoundaryScanner {

    private ArenaBoundaryScanner() {
    }

    public static List<BlockPoint> detectEntranceGaps(World world, WorldGuardHook.RegionBounds bounds) {
        Map<String, BlockPoint> points = new LinkedHashMap<>();
        if (world == null || bounds == null) {
            return new ArrayList<>();
        }

        int minX = bounds.getMinX();
        int maxX = bounds.getMaxX();
        int minY = bounds.getMinY();
        int maxY = bounds.getMaxY();
        int minZ = bounds.getMinZ();
        int maxZ = bounds.getMaxZ();

        // Close all free blocks on the region border (all six faces).
        // This is stricter and avoids missing side/top/bottom holes.
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                addIfBoundaryGap(points, world, minX, y, z);
                addIfBoundaryGap(points, world, maxX, y, z);
            }
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                addIfBoundaryGap(points, world, x, y, minZ);
                addIfBoundaryGap(points, world, x, y, maxZ);
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                addIfBoundaryGap(points, world, x, minY, z);
                addIfBoundaryGap(points, world, x, maxY, z);
            }
        }

        return new ArrayList<>(points.values());
    }

    private static void addIfBoundaryGap(Map<String, BlockPoint> result, World world, int x, int y, int z) {
        Block edge = world.getBlockAt(x, y, z);
        if (!isGapCandidate(edge)) {
            return;
        }

        String key = x + ":" + y + ":" + z;
        result.putIfAbsent(key, new BlockPoint(x, y, z));
    }

    public static boolean isGapCandidate(Material material) {
        return material == Material.AIR
                || material == Material.CAVE_AIR
                || material == Material.VOID_AIR
                || material == Material.WATER
                || material == Material.LAVA
                || !material.isSolid();
    }

    public static boolean isGapCandidate(Block block) {
        if (block == null) {
            return false;
        }
        Material material = block.getType();
        return isGapCandidate(material) || block.isPassable();
    }

    public static class BlockPoint {
        private final int x;
        private final int y;
        private final int z;

        public BlockPoint(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getZ() {
            return z;
        }
    }
}
