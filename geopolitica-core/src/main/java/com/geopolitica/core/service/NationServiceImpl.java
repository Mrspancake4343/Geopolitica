package com.geopolitica.core.service;

import com.geopolitica.api.events.NationCreateEvent;
import com.geopolitica.api.events.NationDisbandEvent;
import com.geopolitica.api.events.NationRelationChangeEvent;
import com.geopolitica.api.events.StateCreateEvent;
import com.geopolitica.api.events.StateDisbandEvent;
import com.geopolitica.api.events.StateSecedeEvent;
import com.geopolitica.api.events.TownNationChangeEvent;
import com.geopolitica.api.events.TownStateChangeEvent;
import com.geopolitica.api.nation.DiplomaticStatus;
import com.geopolitica.api.nation.Nation;
import com.geopolitica.api.service.NationService;
import com.geopolitica.api.state.State;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;
import com.geopolitica.core.config.ConfigManager;
import com.geopolitica.core.model.NationImpl;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.StateImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.storage.DataStore;
import com.geopolitica.core.storage.LoadResult;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class NationServiceImpl implements NationService {

    private final JavaPlugin plugin;
    private final DataStore dataStore;
    private final ConfigManager configManager;

    private final Map<UUID, NationImpl> nations = new LinkedHashMap<>();
    private final Map<String, UUID> nationNameIndex = new LinkedHashMap<>();
    private final Map<UUID, StateImpl> states = new LinkedHashMap<>();
    private final Map<String, UUID> stateNameIndex = new LinkedHashMap<>();
    private final Map<UUID, Map<UUID, DiplomaticStatus>> relations = new LinkedHashMap<>();

    /** From-nation -> set of nations it has requested an alliance with. In-memory only; lost on restart. */
    private final Map<UUID, Set<UUID>> pendingAllianceRequests = new LinkedHashMap<>();

    public NationServiceImpl(JavaPlugin plugin, DataStore dataStore, ConfigManager configManager, LoadResult loaded) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.configManager = configManager;

        this.nations.putAll(loaded.nations());
        for (NationImpl nation : nations.values()) {
            nation.setService(this);
            nationNameIndex.put(key(nation.getName()), nation.getUniqueId());
        }
        this.states.putAll(loaded.states());
        for (StateImpl state : states.values()) {
            stateNameIndex.put(key(state.getName()), state.getUniqueId());
        }
        for (Map.Entry<UUID, Map<UUID, DiplomaticStatus>> entry : loaded.nationRelations().entrySet()) {
            relations.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private void persistAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    // --- Nations -----------------------------------------------------

    @Override
    public Optional<Nation> getNation(String name) {
        UUID id = nationNameIndex.get(key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(nations.get(id));
    }

    @Override
    public Optional<Nation> getNation(UUID nationId) {
        return Optional.ofNullable(nations.get(nationId));
    }

    public Optional<NationImpl> getNationImpl(String name) {
        UUID id = nationNameIndex.get(key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(nations.get(id));
    }

    @Override
    public Collection<Nation> getNations() {
        return Collections.unmodifiableCollection(new LinkedHashMap<UUID, Nation>(nations).values());
    }

    @Override
    public boolean isNationNameTaken(String name) {
        return nationNameIndex.containsKey(key(name));
    }

    @Override
    public Nation createNation(String name, Town capitalHandle) {
        validateName(name);
        if (isNationNameTaken(name)) {
            throw new IllegalArgumentException("A nation named '" + name + "' already exists");
        }
        TownImpl capital = requireTownImpl(capitalHandle);
        if (capital.getStateImpl() != null || capital.getDirectNationImpl() != null) {
            throw new IllegalStateException(capital.getName() + " already belongs to a nation or state");
        }

        UUID id = UUID.randomUUID();
        NationImpl nation = new NationImpl(id, name, capital);
        nation.setService(this);

        NationCreateEvent event = new NationCreateEvent(nation, capital);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            throw new IllegalStateException("Nation creation was cancelled by another plugin");
        }

        capital.setDirectNationImpl(nation);
        nations.put(id, nation);
        nationNameIndex.put(key(name), id);
        persistAsync(() -> {
            dataStore.saveNation(nation);
            dataStore.saveTown(capital);
        });
        return nation;
    }

    @Override
    public void disbandNation(Nation nationHandle) {
        NationImpl nation = requireNationImpl(nationHandle);

        NationDisbandEvent event = new NationDisbandEvent(nation);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        for (StateImpl state : new ArrayList<>(nation.getStateImpls())) {
            for (TownImpl town : new ArrayList<>(state.getTownImpls())) {
                town.setStateImpl(null);
                persistAsync(() -> dataStore.saveTown(town));
            }
            states.remove(state.getUniqueId());
            stateNameIndex.remove(key(state.getName()));
        }
        for (TownImpl town : new ArrayList<>(nation.getDirectTownImpls())) {
            town.setDirectNationImpl(null);
            persistAsync(() -> dataStore.saveTown(town));
        }

        relations.remove(nation.getUniqueId());
        for (Map<UUID, DiplomaticStatus> byOther : relations.values()) {
            byOther.remove(nation.getUniqueId());
        }
        pendingAllianceRequests.remove(nation.getUniqueId());
        for (Set<UUID> requests : pendingAllianceRequests.values()) {
            requests.remove(nation.getUniqueId());
        }

        nations.remove(nation.getUniqueId());
        nationNameIndex.remove(key(nation.getName()));
        persistAsync(() -> dataStore.deleteNation(nation.getUniqueId()));
    }

    public void saveNation(NationImpl nation) {
        persistAsync(() -> dataStore.saveNation(nation));
    }

    // --- States ------------------------------------------------------

    @Override
    public Optional<State> getState(String name) {
        UUID id = stateNameIndex.get(key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(states.get(id));
    }

    @Override
    public Optional<State> getState(UUID stateId) {
        return Optional.ofNullable(states.get(stateId));
    }

    public Optional<StateImpl> getStateImpl(String name) {
        UUID id = stateNameIndex.get(key(name));
        return id == null ? Optional.empty() : Optional.ofNullable(states.get(id));
    }

    @Override
    public Collection<State> getStates() {
        return Collections.unmodifiableCollection(new LinkedHashMap<UUID, State>(states).values());
    }

    @Override
    public boolean isStateNameTaken(String name) {
        return stateNameIndex.containsKey(key(name));
    }

    @Override
    public State createState(String name, Nation nationHandle, Town capitalHandle) {
        validateName(name);
        if (isStateNameTaken(name)) {
            throw new IllegalArgumentException("A state named '" + name + "' already exists");
        }
        NationImpl nation = requireNationImpl(nationHandle);
        TownImpl capital = requireTownImpl(capitalHandle);
        if (capital.getDirectNationImpl() != nation) {
            throw new IllegalStateException(capital.getName() + " must be a direct member of " + nation.getName() + " to found a state");
        }

        UUID id = UUID.randomUUID();
        StateImpl state = new StateImpl(id, name, nation, capital);

        StateCreateEvent event = new StateCreateEvent(state, nation, capital);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            throw new IllegalStateException("State creation was cancelled by another plugin");
        }

        nation.getDirectTownImpls().remove(capital);
        nation.getStateImpls().add(state);
        capital.setDirectNationImpl(null);
        capital.setStateImpl(state);

        states.put(id, state);
        stateNameIndex.put(key(name), id);
        persistAsync(() -> {
            dataStore.saveState(state);
            dataStore.saveTown(capital);
        });
        return state;
    }

    @Override
    public void disbandState(State stateHandle) {
        StateImpl state = requireStateImpl(stateHandle);

        StateDisbandEvent event = new StateDisbandEvent(state);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        NationImpl nation = state.getNationImpl();
        for (TownImpl town : new ArrayList<>(state.getTownImpls())) {
            town.setStateImpl(null);
            town.setDirectNationImpl(nation);
            nation.getDirectTownImpls().add(town);
            persistAsync(() -> dataStore.saveTown(town));
        }
        nation.getStateImpls().remove(state);

        states.remove(state.getUniqueId());
        stateNameIndex.remove(key(state.getName()));
        persistAsync(() -> dataStore.deleteState(state.getUniqueId()));
    }

    public void saveState(StateImpl state) {
        persistAsync(() -> dataStore.saveState(state));
    }

    @Override
    public Nation secedeState(State stateHandle, String newNationName) {
        StateImpl state = requireStateImpl(stateHandle);
        validateName(newNationName);
        if (isNationNameTaken(newNationName)) {
            throw new IllegalArgumentException("A nation named '" + newNationName + "' already exists");
        }
        NationImpl oldNation = state.getNationImpl();
        TownImpl capital = state.getCapitalImpl();

        UUID id = UUID.randomUUID();
        NationImpl newNation = new NationImpl(id, newNationName, capital);
        newNation.setService(this);

        StateSecedeEvent event = new StateSecedeEvent(state, oldNation, newNationName);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            throw new IllegalStateException("Secession was cancelled by another plugin");
        }

        oldNation.getStateImpls().remove(state);
        states.remove(state.getUniqueId());
        stateNameIndex.remove(key(state.getName()));

        for (TownImpl town : new ArrayList<>(state.getTownImpls())) {
            town.setStateImpl(null);
            town.setDirectNationImpl(newNation);
            newNation.getDirectTownImpls().add(town);
            persistAsync(() -> dataStore.saveTown(town));
        }

        nations.put(id, newNation);
        nationNameIndex.put(key(newNationName), id);
        persistAsync(() -> {
            dataStore.saveNation(newNation);
            dataStore.deleteState(state.getUniqueId());
        });
        return newNation;
    }

    @Override
    public void setStateCapital(State stateHandle, Town newCapitalHandle) {
        StateImpl state = requireStateImpl(stateHandle);
        TownImpl newCapital = requireTownImpl(newCapitalHandle);
        if (!state.getTownImpls().contains(newCapital)) {
            throw new IllegalArgumentException(newCapital.getName() + " is not a member of " + state.getName());
        }
        state.setCapital(newCapital);
        persistAsync(() -> dataStore.saveState(state));
    }

    @Override
    public void setStateLeader(State stateHandle, Resident leaderHandle) {
        StateImpl state = requireStateImpl(stateHandle);
        ResidentImpl leader = requireResidentImpl(leaderHandle);
        if (leader.getTownImpl() == null || !state.getTownImpls().contains(leader.getTownImpl())) {
            throw new IllegalArgumentException(leader.getName() + " is not a resident of a member town of " + state.getName());
        }
        state.setLeader(leader);
        persistAsync(() -> dataStore.saveState(state));
    }

    @Override
    public void kickTownFromState(State stateHandle, Town townHandle) {
        StateImpl state = requireStateImpl(stateHandle);
        TownImpl town = requireTownImpl(townHandle);
        if (!state.getTownImpls().contains(town)) {
            throw new IllegalArgumentException(town.getName() + " is not a member of " + state.getName());
        }
        if (state.getCapitalImpl() == town) {
            throw new IllegalStateException("The capital cannot be kicked; relocate the capital or disband the state instead");
        }

        TownStateChangeEvent event = new TownStateChangeEvent(town, state, null);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        state.getTownImpls().remove(town);
        town.setStateImpl(null);
        resetLeaderIfOrphaned(state, town);
        persistAsync(() -> dataStore.saveTown(town));
    }

    @Override
    public void collectStateTax(State stateHandle, Town townHandle, double amount) {
        StateImpl state = requireStateImpl(stateHandle);
        TownImpl town = requireTownImpl(townHandle);
        if (!state.getTownImpls().contains(town)) {
            throw new IllegalArgumentException(town.getName() + " is not a member of " + state.getName());
        }
        if (!town.withdrawBank(amount)) {
            throw new IllegalStateException(town.getName() + "'s bank cannot afford this tax (needs " + amount + ")");
        }
        state.depositBank(amount);
        persistAsync(() -> {
            dataStore.saveTown(town);
            dataStore.saveState(state);
        });
    }

    // --- Membership ----------------------------------------------------

    @Override
    public void joinNation(Town townHandle, Nation nationHandle) {
        TownImpl town = requireTownImpl(townHandle);
        NationImpl nation = requireNationImpl(nationHandle);
        if (town.getStateImpl() != null || town.getDirectNationImpl() != null) {
            throw new IllegalStateException(town.getName() + " already belongs to a nation or state");
        }
        if (nation.isFrozen()) {
            throw new IllegalStateException(nation.getName() + " is frozen by an administrator");
        }
        if (!nation.isOpen()) {
            throw new IllegalStateException(nation.getName() + " is invite-only");
        }

        TownNationChangeEvent event = new TownNationChangeEvent(town, null, nation);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        town.setDirectNationImpl(nation);
        nation.getDirectTownImpls().add(town);
        persistAsync(() -> dataStore.saveTown(town));
    }

    @Override
    public void leaveNation(Town townHandle) {
        TownImpl town = requireTownImpl(townHandle);
        NationImpl nation = town.getDirectNationImpl();
        if (nation == null) {
            return;
        }
        if (nation.getCapitalImpl() == town) {
            throw new IllegalStateException("The capital cannot leave its nation; disband the nation instead");
        }

        TownNationChangeEvent event = new TownNationChangeEvent(town, nation, null);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        nation.getDirectTownImpls().remove(town);
        town.setDirectNationImpl(null);
        persistAsync(() -> dataStore.saveTown(town));
    }

    @Override
    public void joinState(Town townHandle, State stateHandle) {
        TownImpl town = requireTownImpl(townHandle);
        StateImpl state = requireStateImpl(stateHandle);
        if (town.getStateImpl() != null || town.getDirectNationImpl() != null) {
            throw new IllegalStateException(town.getName() + " already belongs to a nation or state");
        }
        if (state.isFrozen()) {
            throw new IllegalStateException(state.getName() + " is frozen by an administrator");
        }
        if (!state.isOpen()) {
            throw new IllegalStateException(state.getName() + " is invite-only");
        }

        TownStateChangeEvent event = new TownStateChangeEvent(town, null, state);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        town.setStateImpl(state);
        state.getTownImpls().add(town);
        persistAsync(() -> dataStore.saveTown(town));
    }

    @Override
    public void leaveState(Town townHandle) {
        TownImpl town = requireTownImpl(townHandle);
        StateImpl state = town.getStateImpl();
        if (state == null) {
            return;
        }
        if (state.getCapitalImpl() == town) {
            throw new IllegalStateException("The capital cannot leave its state; disband the state instead");
        }

        TownStateChangeEvent event = new TownStateChangeEvent(town, state, null);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        state.getTownImpls().remove(town);
        town.setStateImpl(null);
        resetLeaderIfOrphaned(state, town);
        persistAsync(() -> dataStore.saveTown(town));
    }

    /** If the state's leader belonged to a town that just left/was kicked, fall back to the capital's owner. */
    private void resetLeaderIfOrphaned(StateImpl state, TownImpl departedTown) {
        if (state.getLeaderImpl() != null && state.getLeaderImpl().getTownImpl() == departedTown) {
            state.setLeader(state.getCapitalImpl().getOwnerImpl());
            persistAsync(() -> dataStore.saveState(state));
        }
    }

    // --- Diplomacy -------------------------------------------------------

    @Override
    public DiplomaticStatus getRelation(Nation aHandle, Nation bHandle) {
        if (aHandle.getUniqueId().equals(bHandle.getUniqueId())) {
            return DiplomaticStatus.NEUTRAL;
        }
        return relations.getOrDefault(aHandle.getUniqueId(), Collections.emptyMap())
                .getOrDefault(bHandle.getUniqueId(), DiplomaticStatus.NEUTRAL);
    }

    @Override
    public void declareWar(Nation aHandle, Nation bHandle) {
        NationImpl a = requireNationImpl(aHandle);
        NationImpl b = requireNationImpl(bHandle);
        if (a == b) {
            throw new IllegalArgumentException("A nation cannot declare war on itself");
        }
        setRelation(a, b, DiplomaticStatus.ENEMY);
    }

    @Override
    public void declarePeace(Nation aHandle, Nation bHandle) {
        NationImpl a = requireNationImpl(aHandle);
        NationImpl b = requireNationImpl(bHandle);
        if (getRelation(a, b) != DiplomaticStatus.ENEMY) {
            return;
        }
        setRelation(a, b, DiplomaticStatus.NEUTRAL);
    }

    @Override
    public void requestAlliance(Nation aHandle, Nation bHandle) {
        NationImpl a = requireNationImpl(aHandle);
        NationImpl b = requireNationImpl(bHandle);
        if (a == b || getRelation(a, b) == DiplomaticStatus.ALLY) {
            return;
        }
        Set<UUID> bToA = pendingAllianceRequests.getOrDefault(b.getUniqueId(), Collections.emptySet());
        if (bToA.contains(a.getUniqueId())) {
            pendingAllianceRequests.get(b.getUniqueId()).remove(a.getUniqueId());
            Set<UUID> aRequests = pendingAllianceRequests.get(a.getUniqueId());
            if (aRequests != null) {
                aRequests.remove(b.getUniqueId());
            }
            setRelation(a, b, DiplomaticStatus.ALLY);
        } else {
            pendingAllianceRequests.computeIfAbsent(a.getUniqueId(), k -> new LinkedHashSet<>()).add(b.getUniqueId());
        }
    }

    @Override
    public void breakAlliance(Nation aHandle, Nation bHandle) {
        NationImpl a = requireNationImpl(aHandle);
        NationImpl b = requireNationImpl(bHandle);
        if (getRelation(a, b) != DiplomaticStatus.ALLY) {
            return;
        }
        setRelation(a, b, DiplomaticStatus.NEUTRAL);
    }

    private void setRelation(NationImpl a, NationImpl b, DiplomaticStatus status) {
        DiplomaticStatus old = getRelation(a, b);
        if (old == status) {
            return;
        }
        NationRelationChangeEvent event = new NationRelationChangeEvent(a, b, old, status);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        relations.computeIfAbsent(a.getUniqueId(), k -> new LinkedHashMap<>()).put(b.getUniqueId(), status);
        relations.computeIfAbsent(b.getUniqueId(), k -> new LinkedHashMap<>()).put(a.getUniqueId(), status);

        Set<UUID> aRequests = pendingAllianceRequests.get(a.getUniqueId());
        if (aRequests != null) {
            aRequests.remove(b.getUniqueId());
        }
        Set<UUID> bRequests = pendingAllianceRequests.get(b.getUniqueId());
        if (bRequests != null) {
            bRequests.remove(a.getUniqueId());
        }

        persistAsync(() -> dataStore.saveNationRelation(a.getUniqueId(), b.getUniqueId(), status));
    }

    // --- Helpers -----------------------------------------------------

    private void validateName(String name) {
        if (name == null || name.length() < configManager.getMinNameLength() || name.length() > configManager.getMaxNameLength()) {
            throw new IllegalArgumentException("Names must be between " + configManager.getMinNameLength()
                    + " and " + configManager.getMaxNameLength() + " characters");
        }
    }

    private TownImpl requireTownImpl(Town town) {
        if (!(town instanceof TownImpl impl)) {
            throw new IllegalArgumentException("Unknown Town implementation: " + town.getClass());
        }
        return impl;
    }

    private NationImpl requireNationImpl(Nation nation) {
        if (!(nation instanceof NationImpl impl)) {
            throw new IllegalArgumentException("Unknown Nation implementation: " + nation.getClass());
        }
        return impl;
    }

    private StateImpl requireStateImpl(State state) {
        if (!(state instanceof StateImpl impl)) {
            throw new IllegalArgumentException("Unknown State implementation: " + state.getClass());
        }
        return impl;
    }

    private ResidentImpl requireResidentImpl(Resident resident) {
        if (!(resident instanceof ResidentImpl impl)) {
            throw new IllegalArgumentException("Unknown Resident implementation: " + resident.getClass());
        }
        return impl;
    }
}
