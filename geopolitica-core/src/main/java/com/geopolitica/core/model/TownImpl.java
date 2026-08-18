package com.geopolitica.core.model;

import com.geopolitica.api.claim.Claim;
import com.geopolitica.api.nation.Nation;
import com.geopolitica.api.state.State;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;
import com.geopolitica.api.town.TownRank;
import org.bukkit.Location;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TownImpl implements Town {

    public static final String OWNER_RANK_NAME = "Owner";
    public static final String DEFAULT_RANK_NAME = "Resident";

    private final UUID uniqueId;
    private String name;
    private ResidentImpl owner;
    private final Map<UUID, ResidentImpl> residents = new LinkedHashMap<>();
    private final Map<String, TownRankImpl> ranks = new LinkedHashMap<>();
    private final Set<ClaimImpl> claims = new LinkedHashSet<>();

    private double bankBalance;
    private String description = "";
    private Color mapColor = Color.CYAN;
    private Location home;
    private boolean open;
    private boolean pvpEnabled;
    private boolean frozen;
    private int maxClaims;

    private StateImpl state;
    private NationImpl directNation;

    public TownImpl(UUID uniqueId, String name, ResidentImpl owner, int maxClaims) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.owner = owner;
        this.maxClaims = maxClaims;

        TownRankImpl ownerRank = new TownRankImpl(OWNER_RANK_NAME);
        ownerRank.setOwnerRank(true);
        ranks.put(key(OWNER_RANK_NAME), ownerRank);

        TownRankImpl defaultRank = new TownRankImpl(DEFAULT_RANK_NAME);
        defaultRank.setDefaultRank(true);
        ranks.put(key(DEFAULT_RANK_NAME), defaultRank);

        residents.put(owner.getUniqueId(), owner);
        owner.setTown(this);
        owner.setRank(ownerRank);
    }

    /** Bare constructor used only when rehydrating a town from storage; see {@link #rehydrate}. */
    private TownImpl(UUID uniqueId, String name, int maxClaims) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.maxClaims = maxClaims;
    }

    /**
     * Creates an empty town shell with no ranks, residents, owner or claims,
     * to be populated by the data store loader via {@link #restoreRank},
     * {@link #restoreResident} and {@link #restoreOwner}. Not for normal
     * town-creation use; see the public constructor for that.
     */
    public static TownImpl rehydrate(UUID uniqueId, String name, int maxClaims) {
        return new TownImpl(uniqueId, name, maxClaims);
    }

    public void restoreRank(TownRankImpl rank) {
        ranks.put(key(rank.getName()), rank);
    }

    public void restoreResident(ResidentImpl resident, TownRankImpl rank) {
        residents.put(resident.getUniqueId(), resident);
        resident.setTown(this);
        resident.setRank(rank);
    }

    public void restoreOwner(ResidentImpl owner) {
        this.owner = owner;
    }

    private static String key(String rankName) {
        return rankName.toLowerCase(Locale.ROOT);
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Resident getOwner() {
        return owner;
    }

    public ResidentImpl getOwnerImpl() {
        return owner;
    }

    public void setOwner(ResidentImpl owner) {
        this.owner = owner;
    }

    @Override
    public Collection<Resident> getResidents() {
        return Collections.unmodifiableCollection(residents.values());
    }

    public Collection<ResidentImpl> getResidentImpls() {
        return residents.values();
    }

    public void addResident(ResidentImpl resident) {
        residents.put(resident.getUniqueId(), resident);
        resident.setTown(this);
        resident.setRank(getDefaultRankImpl());
    }

    public void removeResident(ResidentImpl resident) {
        residents.remove(resident.getUniqueId());
        resident.setTown(null);
        resident.setRank(null);
    }

    @Override
    public Collection<TownRank> getRanks() {
        return Collections.unmodifiableCollection(ranks.values());
    }

    @Override
    public Optional<TownRank> getRank(String rankName) {
        return Optional.ofNullable(ranks.get(key(rankName)));
    }

    public TownRankImpl getRankImpl(String rankName) {
        return ranks.get(key(rankName));
    }

    public TownRankImpl getDefaultRankImpl() {
        return ranks.values().stream().filter(TownRankImpl::isDefaultRank).findFirst().orElse(null);
    }

    @Override
    public TownRank createRank(String rankName) {
        if (ranks.containsKey(key(rankName))) {
            throw new IllegalArgumentException("A rank named '" + rankName + "' already exists");
        }
        TownRankImpl rank = new TownRankImpl(rankName);
        ranks.put(key(rankName), rank);
        return rank;
    }

    @Override
    public void deleteRank(String rankName) {
        TownRankImpl rank = ranks.get(key(rankName));
        if (rank == null) {
            throw new IllegalArgumentException("No rank named '" + rankName + "' exists");
        }
        if (rank.isOwnerRank() || rank.isDefaultRank()) {
            throw new IllegalStateException("The owner and default ranks cannot be deleted");
        }
        TownRankImpl fallback = getDefaultRankImpl();
        for (ResidentImpl resident : residents.values()) {
            if (resident.getRankImpl() == rank) {
                resident.setRank(fallback);
            }
        }
        ranks.remove(key(rankName));
    }

    @Override
    public Optional<State> getState() {
        return Optional.ofNullable(state);
    }

    public StateImpl getStateImpl() {
        return state;
    }

    public void setStateImpl(StateImpl state) {
        this.state = state;
    }

    @Override
    public Optional<Nation> getNation() {
        if (state != null) {
            return Optional.of(state.getNation());
        }
        return Optional.ofNullable(directNation);
    }

    public NationImpl getDirectNationImpl() {
        return directNation;
    }

    public void setDirectNationImpl(NationImpl nation) {
        this.directNation = nation;
    }

    @Override
    public Collection<Claim> getClaims() {
        return Collections.unmodifiableCollection(new ArrayList<>(claims));
    }

    public Set<ClaimImpl> getClaimImpls() {
        return claims;
    }

    public void addClaim(ClaimImpl claim) {
        claims.add(claim);
    }

    public void removeClaim(ClaimImpl claim) {
        claims.remove(claim);
    }

    @Override
    public int getClaimCount() {
        return claims.size();
    }

    @Override
    public int getMaxClaims() {
        return maxClaims;
    }

    public void setMaxClaims(int maxClaims) {
        this.maxClaims = maxClaims;
    }

    @Override
    public double getBankBalance() {
        return bankBalance;
    }

    @Override
    public void depositBank(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        bankBalance += amount;
    }

    @Override
    public boolean withdrawBank(double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (amount > bankBalance) {
            return false;
        }
        bankBalance -= amount;
        return true;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    @Override
    public Color getMapColor() {
        return mapColor;
    }

    @Override
    public void setMapColor(Color color) {
        this.mapColor = color;
    }

    @Override
    public Optional<Location> getHomeLocation() {
        return Optional.ofNullable(home == null ? null : home.clone());
    }

    @Override
    public void setHomeLocation(Location location) {
        this.home = location == null ? null : location.clone();
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public boolean isPvpEnabled() {
        return pvpEnabled;
    }

    @Override
    public void setPvpEnabled(boolean enabled) {
        this.pvpEnabled = enabled;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    public List<TownRankImpl> getRankImpls() {
        return new ArrayList<>(ranks.values());
    }
}
