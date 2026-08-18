package com.geopolitica.core.service;

import com.geopolitica.api.claim.Claim;
import com.geopolitica.api.claim.ClaimPermission;
import com.geopolitica.api.claim.TrustLevel;
import com.geopolitica.api.events.ClaimCreateEvent;
import com.geopolitica.api.events.ClaimRemoveEvent;
import com.geopolitica.api.service.ClaimService;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;
import com.geopolitica.core.config.ConfigManager;
import com.geopolitica.core.model.ClaimImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.storage.DataStore;
import com.geopolitica.core.storage.LoadResult;
import com.geopolitica.core.util.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class ClaimServiceImpl implements ClaimService {

    private final JavaPlugin plugin;
    private final DataStore dataStore;
    private final ConfigManager configManager;
    private final Map<ChunkPos, ClaimImpl> claims = new LinkedHashMap<>();

    public ClaimServiceImpl(JavaPlugin plugin, DataStore dataStore, ConfigManager configManager, LoadResult loaded) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.configManager = configManager;
        this.claims.putAll(loaded.claims());
    }

    private void persistAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public Optional<Claim> getClaim(Location location) {
        return getClaim(location.getWorld().getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public Optional<Claim> getClaim(Chunk chunk) {
        return getClaim(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
    }

    @Override
    public Optional<Claim> getClaim(String worldName, int chunkX, int chunkZ) {
        return Optional.ofNullable(claims.get(new ChunkPos(worldName, chunkX, chunkZ)));
    }

    @Override
    public boolean isClaimed(Chunk chunk) {
        return claims.containsKey(ChunkPos.of(chunk));
    }

    @Override
    public Collection<Claim> getClaims(Town town) {
        return town.getClaims();
    }

    @Override
    public Claim claim(Town townHandle, Chunk chunk) {
        TownImpl town = requireImpl(townHandle);
        if (town.isFrozen()) {
            throw new IllegalStateException(town.getName() + " is frozen by an administrator and cannot claim land");
        }

        ChunkPos pos = ChunkPos.of(chunk);
        if (claims.containsKey(pos)) {
            throw new IllegalStateException("That chunk is already claimed");
        }

        int maxClaims = town.getMaxClaims();
        if (maxClaims >= 0 && town.getClaimCount() >= maxClaims) {
            throw new IllegalStateException(town.getName() + " has reached its claim limit (" + maxClaims + ")");
        }

        if (configManager.isRequireAdjacentClaims() && town.getClaimCount() > 0 && !hasAdjacentOwnClaim(town, pos)) {
            throw new IllegalStateException("New claims must be adjacent to a chunk your town already owns");
        }

        if (violatesMinDistance(town, pos)) {
            throw new IllegalStateException("That chunk is too close to another town's territory");
        }

        double cost = town.getClaimCount() >= configManager.getFreeClaims() ? configManager.getCostPerClaim() : 0.0;
        if (cost > 0 && !town.withdrawBank(cost)) {
            throw new IllegalStateException(town.getName() + "'s bank cannot afford this claim (needs " + cost + ")");
        }

        ClaimImpl claim = new ClaimImpl(town, pos.world(), pos.x(), pos.z());
        applyDefaultPermissions(claim);

        ClaimCreateEvent event = new ClaimCreateEvent(claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            if (cost > 0) {
                town.depositBank(cost);
            }
            throw new IllegalStateException("Claiming was cancelled by another plugin");
        }

        claims.put(pos, claim);
        town.addClaim(claim);
        persistAsync(() -> {
            dataStore.saveClaim(claim);
            if (cost > 0) {
                dataStore.saveTown(town);
            }
        });
        return claim;
    }

    @Override
    public void unclaim(Claim claimHandle) {
        ClaimImpl claim = requireImpl(claimHandle);

        ClaimRemoveEvent event = new ClaimRemoveEvent(claim);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        TownImpl town = claim.getTownImpl();
        ChunkPos pos = new ChunkPos(claim.getWorldName(), claim.getChunkX(), claim.getChunkZ());

        double refund = configManager.getRefundPerClaim();
        if (refund > 0) {
            town.depositBank(refund);
        }

        town.removeClaim(claim);
        claims.remove(pos);
        persistAsync(() -> {
            dataStore.deleteClaim(pos.world(), pos.x(), pos.z());
            if (refund > 0) {
                dataStore.saveTown(town);
            }
        });
    }

    @Override
    public boolean hasPermission(Player player, Location location, ClaimPermission permission) {
        if (configManager.isAdminBypassEnabled() && player.hasPermission("geopolitica.town.bypass")) {
            return true;
        }
        Optional<Claim> claimOpt = getClaim(location);
        if (claimOpt.isEmpty()) {
            return true;
        }
        Claim claim = claimOpt.get();

        Resident resident = claim.getTown().getResidents().stream()
                .filter(r -> r.getUniqueId().equals(player.getUniqueId()))
                .findFirst()
                .orElse(null);
        boolean isResident = resident != null;
        if (isResident && resident.hasPermission(com.geopolitica.api.town.TownPermission.MANAGE_PLOT_PERMISSIONS)) {
            return true;
        }

        TrustLevel level = isResident ? TrustLevel.RESIDENT : TrustLevel.OUTSIDER;
        return claim.isPermissionSet(level, permission);
    }

    // --- Implementation-level helpers -------------------------------------

    /** Removes every claim belonging to a town without firing per-claim events; used only during disband. */
    public void purgeClaims(TownImpl town) {
        for (ClaimImpl claim : town.getClaimImpls()) {
            claims.remove(new ChunkPos(claim.getWorldName(), claim.getChunkX(), claim.getChunkZ()));
        }
        town.getClaimImpls().clear();
    }

    private void applyDefaultPermissions(ClaimImpl claim) {
        for (TrustLevel level : TrustLevel.values()) {
            for (ClaimPermission permission : ClaimPermission.values()) {
                if (configManager.getDefaultClaimPermission(level.name(), permission.name())) {
                    claim.setPermission(level, permission, true);
                }
            }
        }
    }

    private boolean hasAdjacentOwnClaim(TownImpl town, ChunkPos pos) {
        for (ClaimImpl claim : town.getClaimImpls()) {
            if (new ChunkPos(claim.getWorldName(), claim.getChunkX(), claim.getChunkZ()).isAdjacent(pos)) {
                return true;
            }
        }
        return false;
    }

    private boolean violatesMinDistance(TownImpl town, ChunkPos pos) {
        int minDist = configManager.getMinDistanceBetweenTowns();
        if (minDist <= 0) {
            return false;
        }
        for (int dx = -minDist; dx <= minDist; dx++) {
            for (int dz = -minDist; dz <= minDist; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                ClaimImpl neighbor = claims.get(new ChunkPos(pos.world(), pos.x() + dx, pos.z() + dz));
                if (neighbor != null && neighbor.getTownImpl() != town) {
                    return true;
                }
            }
        }
        return false;
    }

    private ClaimImpl requireImpl(Claim claim) {
        if (!(claim instanceof ClaimImpl impl)) {
            throw new IllegalArgumentException("Unknown Claim implementation: " + claim.getClass());
        }
        return impl;
    }

    private TownImpl requireImpl(Town town) {
        if (!(town instanceof TownImpl impl)) {
            throw new IllegalArgumentException("Unknown Town implementation: " + town.getClass());
        }
        return impl;
    }
}
