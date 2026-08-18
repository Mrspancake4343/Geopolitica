package com.geopolitica.core.service;

import com.geopolitica.api.events.ResidentJoinTownEvent;
import com.geopolitica.api.events.ResidentLeaveTownEvent;
import com.geopolitica.api.events.TownCreateEvent;
import com.geopolitica.api.events.TownDisbandEvent;
import com.geopolitica.api.service.TownService;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;
import com.geopolitica.core.config.ConfigManager;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.model.TownRankImpl;
import com.geopolitica.core.storage.DataStore;
import com.geopolitica.core.storage.LoadResult;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class TownServiceImpl implements TownService {

    private final JavaPlugin plugin;
    private final DataStore dataStore;
    private final ConfigManager configManager;

    private final Map<UUID, TownImpl> towns = new LinkedHashMap<>();
    private final Map<String, UUID> townNameIndex = new LinkedHashMap<>();
    private final Map<UUID, ResidentImpl> residents = new LinkedHashMap<>();

    private ClaimServiceImpl claimService;

    public TownServiceImpl(JavaPlugin plugin, DataStore dataStore, ConfigManager configManager, LoadResult loaded) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.configManager = configManager;

        this.towns.putAll(loaded.towns());
        this.residents.putAll(loaded.residents());
        for (TownImpl town : towns.values()) {
            townNameIndex.put(key(town.getName()), town.getUniqueId());
        }
    }

    /** Wired in after construction since {@link ClaimServiceImpl} also depends on this service. */
    public void setClaimService(ClaimServiceImpl claimService) {
        this.claimService = claimService;
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private void persistAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    // --- TownService ---------------------------------------------------

    @Override
    public Optional<Town> getTown(String name) {
        UUID id = townNameIndex.get(key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(towns.get(id));
    }

    @Override
    public Optional<Town> getTown(UUID townId) {
        return Optional.ofNullable(towns.get(townId));
    }

    @Override
    public Collection<Town> getTowns() {
        return Collections.unmodifiableCollection(new LinkedHashMap<UUID, Town>(towns).values());
    }

    public Optional<TownImpl> getTownImpl(String name) {
        UUID id = townNameIndex.get(key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(towns.get(id));
    }

    @Override
    public boolean isNameTaken(String name) {
        return townNameIndex.containsKey(key(name));
    }

    @Override
    public Town createTown(String name, Resident ownerResident) {
        if (name == null || name.length() < configManager.getMinNameLength() || name.length() > configManager.getMaxNameLength()) {
            throw new IllegalArgumentException("Town names must be between " + configManager.getMinNameLength()
                    + " and " + configManager.getMaxNameLength() + " characters");
        }
        if (isNameTaken(name)) {
            throw new IllegalArgumentException("A town named '" + name + "' already exists");
        }
        ResidentImpl owner = requireImpl(ownerResident);
        if (owner.getTownImpl() != null) {
            throw new IllegalStateException(owner.getName() + " already belongs to a town");
        }

        UUID id = UUID.randomUUID();
        TownImpl town = new TownImpl(id, name, owner, configManager.getDefaultMaxClaims());

        TownCreateEvent event = new TownCreateEvent(town, owner);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            owner.setTown(null);
            owner.setRank(null);
            throw new IllegalStateException("Town creation was cancelled by another plugin");
        }

        towns.put(id, town);
        townNameIndex.put(key(name), id);
        persistAsync(() -> {
            dataStore.saveTown(town);
            dataStore.saveResident(owner);
        });
        return town;
    }

    @Override
    public void disbandTown(Town townHandle) {
        TownImpl town = requireImpl(townHandle);

        if (town.getStateImpl() != null && town.getStateImpl().getCapitalImpl() == town) {
            throw new IllegalStateException("This town is a state's capital; disband the state first");
        }
        if (town.getDirectNationImpl() != null && town.getDirectNationImpl().getCapitalImpl() == town) {
            throw new IllegalStateException("This town is a nation's capital; disband the nation first");
        }

        TownDisbandEvent event = new TownDisbandEvent(town);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        for (ResidentImpl resident : town.getResidentImpls().toArray(new ResidentImpl[0])) {
            resident.setTown(null);
            resident.setRank(null);
            persistAsync(() -> dataStore.saveResident(resident));
        }
        if (claimService != null) {
            claimService.purgeClaims(town);
        }
        if (town.getStateImpl() != null) {
            town.getStateImpl().getTownImpls().remove(town);
        }
        if (town.getDirectNationImpl() != null) {
            town.getDirectNationImpl().getDirectTownImpls().remove(town);
        }

        towns.remove(town.getUniqueId());
        townNameIndex.remove(key(town.getName()));
        persistAsync(() -> dataStore.deleteTown(town.getUniqueId()));
    }

    // --- Membership (implementation-level helpers used by commands) -----

    public void joinTown(TownImpl town, ResidentImpl resident) {
        if (resident.getTownImpl() != null) {
            throw new IllegalStateException(resident.getName() + " already belongs to a town");
        }
        ResidentJoinTownEvent event = new ResidentJoinTownEvent(resident, town);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        town.addResident(resident);
        persistAsync(() -> dataStore.saveResident(resident));
    }

    public void leaveTown(ResidentImpl resident, ResidentLeaveTownEvent.Cause cause) {
        TownImpl town = resident.getTownImpl();
        if (town == null) {
            throw new IllegalStateException(resident.getName() + " does not belong to a town");
        }
        if (town.getOwner().getUniqueId().equals(resident.getUniqueId())) {
            throw new IllegalStateException("The town owner cannot leave or be kicked; disband the town or transfer ownership instead");
        }
        ResidentLeaveTownEvent event = new ResidentLeaveTownEvent(resident, town, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        town.removeResident(resident);
        persistAsync(() -> dataStore.saveResident(resident));
    }

    public void assignRank(TownImpl town, ResidentImpl resident, TownRankImpl rank) {
        if (resident.getTownImpl() != town) {
            throw new IllegalStateException(resident.getName() + " is not a resident of " + town.getName());
        }
        resident.setRank(rank);
        persistAsync(() -> dataStore.saveResident(resident));
    }

    public void saveTown(TownImpl town) {
        persistAsync(() -> dataStore.saveTown(town));
    }

    // --- Residents -------------------------------------------------------

    @Override
    public Resident getResident(UUID playerId) {
        return getResidentImpl(playerId);
    }

    public ResidentImpl getResidentImpl(UUID playerId) {
        ResidentImpl resident = residents.get(playerId);
        if (resident == null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerId);
            resident = new ResidentImpl(playerId, offlinePlayer.getName() != null ? offlinePlayer.getName() : playerId.toString());
            residents.put(playerId, resident);
            ResidentImpl toSave = resident;
            persistAsync(() -> dataStore.saveResident(toSave));
        }
        return resident;
    }

    /**
     * Records or updates a player's resident record. Called on join so that
     * name changes and last-seen timestamps stay current even for players
     * who never interact with towns.
     */
    public ResidentImpl trackPlayer(UUID playerId, String name) {
        ResidentImpl resident = residents.get(playerId);
        if (resident == null) {
            resident = new ResidentImpl(playerId, name);
            residents.put(playerId, resident);
        } else {
            resident.setName(name);
        }
        resident.setLastSeen(System.currentTimeMillis());
        ResidentImpl toSave = resident;
        persistAsync(() -> dataStore.saveResident(toSave));
        return resident;
    }

    @Override
    public Optional<Resident> getResident(String name) {
        return residents.values().stream()
                .filter(r -> r.getName().equalsIgnoreCase(name))
                .map(r -> (Resident) r)
                .findFirst();
    }

    @Override
    public Collection<Resident> getResidents() {
        return Collections.unmodifiableCollection(new LinkedHashMap<UUID, Resident>(residents).values());
    }

    private ResidentImpl requireImpl(Resident resident) {
        if (!(resident instanceof ResidentImpl impl)) {
            throw new IllegalArgumentException("Unknown Resident implementation: " + resident.getClass());
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
