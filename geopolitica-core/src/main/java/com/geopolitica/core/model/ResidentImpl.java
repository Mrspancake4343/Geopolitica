package com.geopolitica.core.model;

import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;
import com.geopolitica.api.town.TownPermission;
import com.geopolitica.api.town.TownRank;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Optional;
import java.util.UUID;

public class ResidentImpl implements Resident {

    private final UUID uniqueId;
    private String name;
    private TownImpl town;
    private TownRankImpl rank;
    private long lastSeen;

    public ResidentImpl(UUID uniqueId, String name) {
        this.uniqueId = uniqueId;
        this.name = name;
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public OfflinePlayer getOfflinePlayer() {
        return Bukkit.getOfflinePlayer(uniqueId);
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Optional<Town> getTown() {
        return Optional.ofNullable(town);
    }

    public TownImpl getTownImpl() {
        return town;
    }

    public void setTown(TownImpl town) {
        this.town = town;
    }

    @Override
    public Optional<TownRank> getRank() {
        return Optional.ofNullable(rank);
    }

    public TownRankImpl getRankImpl() {
        return rank;
    }

    public void setRank(TownRankImpl rank) {
        this.rank = rank;
    }

    @Override
    public boolean hasPermission(TownPermission permission) {
        return rank != null && rank.hasPermission(permission);
    }

    @Override
    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }
}
