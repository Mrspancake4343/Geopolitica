package com.geopolitica.core.model;

import com.geopolitica.api.town.TownPermission;
import com.geopolitica.api.town.TownRank;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class TownRankImpl implements TownRank {

    private String name;
    private final Set<TownPermission> permissions = EnumSet.noneOf(TownPermission.class);
    private boolean ownerRank;
    private boolean defaultRank;

    public TownRankImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public Set<TownPermission> getPermissions() {
        if (ownerRank) {
            return Collections.unmodifiableSet(EnumSet.allOf(TownPermission.class));
        }
        return Collections.unmodifiableSet(permissions);
    }

    @Override
    public boolean hasPermission(TownPermission permission) {
        return ownerRank || permissions.contains(permission);
    }

    @Override
    public void setPermission(TownPermission permission, boolean granted) {
        if (ownerRank) {
            return;
        }
        if (granted) {
            permissions.add(permission);
        } else {
            permissions.remove(permission);
        }
    }

    @Override
    public boolean isOwnerRank() {
        return ownerRank;
    }

    public void setOwnerRank(boolean ownerRank) {
        this.ownerRank = ownerRank;
    }

    @Override
    public boolean isDefaultRank() {
        return defaultRank;
    }

    public void setDefaultRank(boolean defaultRank) {
        this.defaultRank = defaultRank;
    }
}
