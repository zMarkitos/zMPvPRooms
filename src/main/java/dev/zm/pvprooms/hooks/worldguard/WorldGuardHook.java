package dev.zm.pvprooms.hooks.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Locale;
import java.util.Optional;

public class WorldGuardHook {

    public Optional<RegionBounds> getRegionBounds(String worldName, String regionId) {
        if (worldName == null || regionId == null || regionId.isEmpty()) {
            return Optional.empty();
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager manager = container.get(BukkitAdapter.adapt(world));
        if (manager == null) {
            return Optional.empty();
        }

        ProtectedRegion region = manager.getRegion(regionId);
        if (region == null) {
            region = manager.getRegion(regionId.toLowerCase(Locale.ROOT));
        }
        if (region == null) {
            return Optional.empty();
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        return Optional.of(new RegionBounds(
                world.getName(),
                min.getBlockX(),
                min.getBlockY(),
                min.getBlockZ(),
                max.getBlockX(),
                max.getBlockY(),
                max.getBlockZ()));
    }

    public String normalizeRegionId(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).trim();
    }

    public static class RegionBounds {
        private final String worldName;
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;

        public RegionBounds(String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            this.worldName = worldName;
            this.minX = Math.min(minX, maxX);
            this.minY = Math.min(minY, maxY);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxY = Math.max(minY, maxY);
            this.maxZ = Math.max(minZ, maxZ);
        }

        public String getWorldName() {
            return worldName;
        }

        public int getMinX() {
            return minX;
        }

        public int getMinY() {
            return minY;
        }

        public int getMinZ() {
            return minZ;
        }

        public int getMaxX() {
            return maxX;
        }

        public int getMaxY() {
            return maxY;
        }

        public int getMaxZ() {
            return maxZ;
        }

        public Location toMinLocation(World world) {
            return new Location(world, minX, minY, minZ);
        }

        public Location toMaxLocation(World world) {
            return new Location(world, maxX, maxY, maxZ);
        }
    }
}
