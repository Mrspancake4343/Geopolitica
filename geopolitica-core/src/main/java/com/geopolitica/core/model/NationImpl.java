package com.geopolitica.core.model;

import com.geopolitica.api.nation.DiplomaticStatus;
import com.geopolitica.api.nation.Nation;
import com.geopolitica.api.state.State;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;
import com.geopolitica.core.service.NationServiceImpl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class NationImpl implements Nation {

    private final UUID uniqueId;
    private String name;
    private TownImpl capital;
    private final Set<TownImpl> directTowns = new LinkedHashSet<>();
    private final Set<StateImpl> states = new LinkedHashSet<>();

    private double bankBalance;
    private String description = "";
    private Color mapColor = Color.BLUE;
    private boolean open;
    private boolean frozen;

    /** Wired in by {@link NationServiceImpl} after construction/rehydration, for relation lookups. */
    private NationServiceImpl service;

    public NationImpl(UUID uniqueId, String name, TownImpl capital) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.capital = capital;
        this.directTowns.add(capital);
    }

    public void setService(NationServiceImpl service) {
        this.service = service;
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
    public Town getCapital() {
        return capital;
    }

    public TownImpl getCapitalImpl() {
        return capital;
    }

    public void setCapital(TownImpl capital) {
        this.capital = capital;
    }

    @Override
    public Resident getLeader() {
        return capital.getOwner();
    }

    @Override
    public Collection<Town> getDirectTowns() {
        return Collections.unmodifiableCollection(new ArrayList<>(directTowns));
    }

    public Set<TownImpl> getDirectTownImpls() {
        return directTowns;
    }

    @Override
    public Collection<State> getStates() {
        return Collections.unmodifiableCollection(new ArrayList<>(states));
    }

    public Set<StateImpl> getStateImpls() {
        return states;
    }

    @Override
    public Collection<Town> getAllTowns() {
        List<Town> all = new ArrayList<>(directTowns);
        for (StateImpl state : states) {
            all.addAll(state.getTowns());
        }
        return all;
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
    public boolean isOpen() {
        return open;
    }

    @Override
    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    @Override
    public void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    @Override
    public DiplomaticStatus getRelation(Nation other) {
        return service.getRelation(this, other);
    }

    @Override
    public Collection<Nation> getAllies() {
        return service.getNations().stream()
                .filter(n -> n != this && service.getRelation(this, n) == DiplomaticStatus.ALLY)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<Nation> getEnemies() {
        return service.getNations().stream()
                .filter(n -> n != this && service.getRelation(this, n) == DiplomaticStatus.ENEMY)
                .collect(Collectors.toList());
    }
}
