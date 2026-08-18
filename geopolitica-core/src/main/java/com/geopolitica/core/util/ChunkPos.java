package com.geopolitica.core.util;

import org.bukkit.Chunk;
import org.bukkit.Location;

/** Immutable key identifying a single chunk by world name + chunk coordinates. */
public record ChunkPos(String world, int x, int z) {

    public static ChunkPos of(Chunk chunk) {
        return new ChunkPos(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    public static ChunkPos of(Location location) {
        return new ChunkPos(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /** @return true if this chunk is orthogonally adjacent to (shares an edge with) the other chunk. */
    public boolean isAdjacent(ChunkPos other) {
        if (!world.equals(other.world)) {
            return false;
        }
        int dx = Math.abs(x - other.x);
        int dz = Math.abs(z - other.z);
        return (dx == 1 && dz == 0) || (dx == 0 && dz == 1);
    }
}
