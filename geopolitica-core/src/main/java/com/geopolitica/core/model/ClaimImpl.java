package com.geopolitica.core.model;

import com.geopolitica.api.claim.Claim;
import com.geopolitica.api.claim.ClaimPermission;
import com.geopolitica.api.claim.TrustLevel;
import com.geopolitica.api.town.Town;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public class ClaimImpl implements Claim {

    private final TownImpl town;
    private final String worldName;
    private final int chunkX;
    private final int chunkZ;
    private String plotName = "";
    private final Map<TrustLevel, Set<ClaimPermission>> permissions = new EnumMap<>(TrustLevel.class);

    public ClaimImpl(TownImpl town, String worldName, int chunkX, int chunkZ) {
        this.town = town;
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    @Override
    public Town getTown() {
        return town;
    }

    public TownImpl getTownImpl() {
        return town;
    }

    @Override
    public String getWorldName() {
        return worldName;
    }

    @Override
    public int getChunkX() {
        return chunkX;
    }

    @Override
    public int getChunkZ() {
        return chunkZ;
    }

    @Override
    public String getPlotName() {
        return plotName;
    }

    public void setPlotName(String plotName) {
        this.plotName = plotName;
    }

    @Override
    public boolean isPermissionSet(TrustLevel level, ClaimPermission permission) {
        Set<ClaimPermission> set = permissions.get(level);
        return set != null && set.contains(permission);
    }

    @Override
    public void setPermission(TrustLevel level, ClaimPermission permission, boolean allowed) {
        Set<ClaimPermission> set = permissions.computeIfAbsent(level, k -> EnumSet.noneOf(ClaimPermission.class));
        if (allowed) {
            set.add(permission);
        } else {
            set.remove(permission);
        }
    }
}
