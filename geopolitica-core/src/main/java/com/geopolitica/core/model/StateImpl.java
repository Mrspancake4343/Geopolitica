package com.geopolitica.core.model;

import com.geopolitica.api.nation.Nation;
import com.geopolitica.api.state.State;
import com.geopolitica.api.town.Resident;
import com.geopolitica.api.town.Town;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class StateImpl implements State {

    private final UUID uniqueId;
    private String name;
    private final NationImpl nation;
    private TownImpl capital;
    private ResidentImpl leader;
    private final Set<TownImpl> towns = new LinkedHashSet<>();

    private double bankBalance;
    private String description = "";
    private Color mapColor = Color.ORANGE;
    private boolean open;
    private boolean frozen;

    public StateImpl(UUID uniqueId, String name, NationImpl nation, TownImpl capital) {
        this.uniqueId = uniqueId;
        this.name = name;
        this.nation = nation;
        this.capital = capital;
        this.leader = capital.getOwnerImpl();
        this.towns.add(capital);
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
    public Nation getNation() {
        return nation;
    }

    public NationImpl getNationImpl() {
        return nation;
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
        return leader;
    }

    @Override
    public void setLeader(Resident leader) {
        if (!(leader instanceof ResidentImpl impl)) {
            throw new IllegalArgumentException("Unknown Resident implementation: " + leader.getClass());
        }
        this.leader = impl;
    }

    public ResidentImpl getLeaderImpl() {
        return leader;
    }

    /** Used only when rehydrating a state from storage, where the leader may not be the capital's owner. */
    public void restoreLeader(ResidentImpl leader) {
        this.leader = leader;
    }

    @Override
    public Collection<Town> getTowns() {
        return Collections.unmodifiableCollection(new ArrayList<>(towns));
    }

    public Set<TownImpl> getTownImpls() {
        return towns;
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
}
