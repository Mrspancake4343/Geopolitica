package com.geopolitica.core.storage;

import com.geopolitica.api.nation.DiplomaticStatus;
import com.geopolitica.core.model.ClaimImpl;
import com.geopolitica.core.model.NationImpl;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.StateImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.util.ChunkPos;

import java.util.Map;
import java.util.UUID;

/** Fully reconstructed object graph produced by {@link DataStore#loadAll()}. */
public record LoadResult(
        Map<UUID, TownImpl> towns,
        Map<UUID, ResidentImpl> residents,
        Map<ChunkPos, ClaimImpl> claims,
        Map<UUID, NationImpl> nations,
        Map<UUID, StateImpl> states,
        Map<UUID, Map<UUID, DiplomaticStatus>> nationRelations
) {
}
