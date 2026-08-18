package com.geopolitica.core.storage;

import com.geopolitica.api.nation.DiplomaticStatus;
import com.geopolitica.core.model.ClaimImpl;
import com.geopolitica.core.model.NationImpl;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.StateImpl;
import com.geopolitica.core.model.TownImpl;

import java.util.UUID;

/**
 * Persistence boundary for Geopolitica's data. Implementations must be safe
 * to call from any thread; the service layer is responsible for dispatching
 * writes off the main server thread.
 */
public interface DataStore {

    void init() throws Exception;

    void close();

    LoadResult loadAll() throws Exception;

    /** Upserts a town's core fields plus its ranks and their permissions. */
    void saveTown(TownImpl town);

    void deleteTown(UUID townId);

    /** Upserts a resident, including its current town/rank assignment. */
    void saveResident(ResidentImpl resident);

    /** Upserts a claimed chunk plus its permission matrix. */
    void saveClaim(ClaimImpl claim);

    void deleteClaim(String worldName, int chunkX, int chunkZ);

    /** Upserts a nation's core fields. Does not touch its member towns/states; see {@link #saveTown}. */
    void saveNation(NationImpl nation);

    void deleteNation(UUID nationId);

    /** Upserts a state's core fields. Does not touch its member towns; see {@link #saveTown}. */
    void saveState(StateImpl state);

    void deleteState(UUID stateId);

    /** Upserts (or, for NEUTRAL, clears) the relation between two nations. */
    void saveNationRelation(UUID nationA, UUID nationB, DiplomaticStatus status);
}
