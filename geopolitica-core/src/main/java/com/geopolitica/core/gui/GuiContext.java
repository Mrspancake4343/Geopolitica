package com.geopolitica.core.gui;

import com.geopolitica.core.economy.EconomyHook;
import com.geopolitica.core.service.ClaimServiceImpl;
import com.geopolitica.core.service.NationServiceImpl;
import com.geopolitica.core.service.TownServiceImpl;

/** Bundles the services every menu needs, so menu constructors don't have to thread five separate params through. */
public record GuiContext(
        TownServiceImpl townService,
        ClaimServiceImpl claimService,
        NationServiceImpl nationService,
        EconomyHook economyHook,
        GuiListener guiListener
) {
}
