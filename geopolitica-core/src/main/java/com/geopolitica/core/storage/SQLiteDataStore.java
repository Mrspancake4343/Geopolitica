package com.geopolitica.core.storage;

import com.geopolitica.api.claim.ClaimPermission;
import com.geopolitica.api.claim.TrustLevel;
import com.geopolitica.api.nation.DiplomaticStatus;
import com.geopolitica.api.town.TownPermission;
import com.geopolitica.core.model.ClaimImpl;
import com.geopolitica.core.model.NationImpl;
import com.geopolitica.core.model.ResidentImpl;
import com.geopolitica.core.model.StateImpl;
import com.geopolitica.core.model.TownImpl;
import com.geopolitica.core.model.TownRankImpl;
import com.geopolitica.core.util.ChunkPos;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.awt.Color;
import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class SQLiteDataStore implements DataStore {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS towns (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE NOT NULL,
                owner_id TEXT NOT NULL,
                max_claims INTEGER NOT NULL,
                bank_balance REAL NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                map_color INTEGER NOT NULL DEFAULT -16711936,
                home_world TEXT,
                home_x REAL, home_y REAL, home_z REAL, home_yaw REAL, home_pitch REAL,
                open INTEGER NOT NULL DEFAULT 0,
                pvp INTEGER NOT NULL DEFAULT 0,
                frozen INTEGER NOT NULL DEFAULT 0
            )""",
            """
            CREATE TABLE IF NOT EXISTS town_ranks (
                town_id TEXT NOT NULL REFERENCES towns(id),
                name TEXT NOT NULL,
                is_owner_rank INTEGER NOT NULL DEFAULT 0,
                is_default_rank INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (town_id, name)
            )""",
            """
            CREATE TABLE IF NOT EXISTS rank_permissions (
                town_id TEXT NOT NULL,
                rank_name TEXT NOT NULL,
                permission TEXT NOT NULL,
                PRIMARY KEY (town_id, rank_name, permission)
            )""",
            """
            CREATE TABLE IF NOT EXISTS residents (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                town_id TEXT,
                rank_name TEXT,
                last_seen INTEGER NOT NULL DEFAULT 0
            )""",
            """
            CREATE TABLE IF NOT EXISTS claims (
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                town_id TEXT NOT NULL,
                plot_name TEXT NOT NULL DEFAULT '',
                PRIMARY KEY (world, chunk_x, chunk_z)
            )""",
            """
            CREATE TABLE IF NOT EXISTS claim_permissions (
                world TEXT NOT NULL,
                chunk_x INTEGER NOT NULL,
                chunk_z INTEGER NOT NULL,
                trust_level TEXT NOT NULL,
                permission TEXT NOT NULL,
                PRIMARY KEY (world, chunk_x, chunk_z, trust_level, permission)
            )""",
            """
            CREATE TABLE IF NOT EXISTS nations (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE NOT NULL,
                capital_town_id TEXT NOT NULL,
                bank_balance REAL NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                map_color INTEGER NOT NULL DEFAULT -16776961,
                open INTEGER NOT NULL DEFAULT 0,
                frozen INTEGER NOT NULL DEFAULT 0
            )""",
            """
            CREATE TABLE IF NOT EXISTS states (
                id TEXT PRIMARY KEY,
                name TEXT UNIQUE NOT NULL,
                nation_id TEXT NOT NULL,
                capital_town_id TEXT NOT NULL,
                leader_id TEXT,
                bank_balance REAL NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                map_color INTEGER NOT NULL DEFAULT -16776961,
                open INTEGER NOT NULL DEFAULT 0,
                frozen INTEGER NOT NULL DEFAULT 0
            )""",
            """
            CREATE TABLE IF NOT EXISTS nation_relations (
                nation_a TEXT NOT NULL,
                nation_b TEXT NOT NULL,
                status TEXT NOT NULL,
                PRIMARY KEY (nation_a, nation_b)
            )"""
    };

    private final JavaPlugin plugin;
    private HikariDataSource dataSource;

    public SQLiteDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() throws Exception {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File dbFile = new File(plugin.getDataFolder(), "geopolitica.db");

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setDriverClassName("org.sqlite.JDBC");
        hikariConfig.setMaximumPoolSize(1); // SQLite only supports one writer at a time
        hikariConfig.setPoolName("Geopolitica-SQLite");
        this.dataSource = new HikariDataSource(hikariConfig);

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                for (String ddl : SCHEMA) {
                    statement.execute(ddl);
                }
            }
            // Migration: these columns were added after the initial towns table shipped.
            ensureColumn(connection, "towns", "state_id", "TEXT");
            ensureColumn(connection, "towns", "direct_nation_id", "TEXT");
            // Migration: added after the initial states table shipped.
            ensureColumn(connection, "states", "leader_id", "TEXT");
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String type) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (rs.getString("name").equalsIgnoreCase(column)) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        }
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    public LoadResult loadAll() throws Exception {
        Map<UUID, ResidentImpl> residents = new LinkedHashMap<>();
        Map<UUID, TownImpl> towns = new LinkedHashMap<>();
        Map<ChunkPos, ClaimImpl> claims = new LinkedHashMap<>();

        Map<UUID, NationImpl> nations = new LinkedHashMap<>();
        Map<UUID, StateImpl> states = new LinkedHashMap<>();
        Map<UUID, Map<UUID, DiplomaticStatus>> relations = new LinkedHashMap<>();

        // Residents (town/rank linkage recorded but applied after towns exist)
        Map<UUID, UUID> residentTownIds = new LinkedHashMap<>();
        Map<UUID, String> residentRankNames = new LinkedHashMap<>();

        // Town hierarchy linkage recorded but applied after nations/states exist
        Map<UUID, UUID> townStateIds = new LinkedHashMap<>();
        Map<UUID, UUID> townDirectNationIds = new LinkedHashMap<>();

        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT id, name, town_id, rank_name, last_seen FROM residents")) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    ResidentImpl resident = new ResidentImpl(id, rs.getString("name"));
                    resident.setLastSeen(rs.getLong("last_seen"));
                    residents.put(id, resident);

                    String townId = rs.getString("town_id");
                    if (townId != null) {
                        residentTownIds.put(id, UUID.fromString(townId));
                        residentRankNames.put(id, rs.getString("rank_name"));
                    }
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT id, name, owner_id, max_claims, bank_balance, description, map_color, "
                                 + "home_world, home_x, home_y, home_z, home_yaw, home_pitch, open, pvp, frozen, "
                                 + "state_id, direct_nation_id FROM towns")) {
                while (rs.next()) {
                    UUID id = UUID.fromString(rs.getString("id"));
                    TownImpl town = TownImpl.rehydrate(id, rs.getString("name"), rs.getInt("max_claims"));
                    town.depositBank(rs.getDouble("bank_balance"));
                    town.setDescription(rs.getString("description"));
                    town.setMapColor(new Color(rs.getInt("map_color")));
                    town.setOpen(rs.getInt("open") != 0);
                    town.setPvpEnabled(rs.getInt("pvp") != 0);
                    town.setFrozen(rs.getInt("frozen") != 0);

                    String stateId = rs.getString("state_id");
                    if (stateId != null) {
                        townStateIds.put(id, UUID.fromString(stateId));
                    }
                    String directNationId = rs.getString("direct_nation_id");
                    if (directNationId != null) {
                        townDirectNationIds.put(id, UUID.fromString(directNationId));
                    }

                    String homeWorld = rs.getString("home_world");
                    if (homeWorld != null) {
                        World world = Bukkit.getWorld(homeWorld);
                        if (world != null) {
                            town.setHomeLocation(new Location(world,
                                    rs.getDouble("home_x"), rs.getDouble("home_y"), rs.getDouble("home_z"),
                                    rs.getFloat("home_yaw"), rs.getFloat("home_pitch")));
                        } else {
                            plugin.getLogger().warning("Town '" + town.getName() + "' has a home in unknown world '"
                                    + homeWorld + "'; skipping until that world loads.");
                        }
                    }
                    towns.put(id, town);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT town_id, name, is_owner_rank, is_default_rank FROM town_ranks")) {
                while (rs.next()) {
                    TownImpl town = towns.get(UUID.fromString(rs.getString("town_id")));
                    if (town == null) {
                        continue;
                    }
                    TownRankImpl rank = new TownRankImpl(rs.getString("name"));
                    rank.setOwnerRank(rs.getInt("is_owner_rank") != 0);
                    rank.setDefaultRank(rs.getInt("is_default_rank") != 0);
                    town.restoreRank(rank);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT town_id, rank_name, permission FROM rank_permissions")) {
                while (rs.next()) {
                    TownImpl town = towns.get(UUID.fromString(rs.getString("town_id")));
                    if (town == null) {
                        continue;
                    }
                    TownRankImpl rank = town.getRankImpl(rs.getString("rank_name"));
                    if (rank == null) {
                        continue;
                    }
                    rank.setPermission(TownPermission.valueOf(rs.getString("permission")), true);
                }
            }

            for (Map.Entry<UUID, UUID> entry : residentTownIds.entrySet()) {
                ResidentImpl resident = residents.get(entry.getKey());
                TownImpl town = towns.get(entry.getValue());
                if (town == null) {
                    continue;
                }
                String rankName = residentRankNames.get(entry.getKey());
                TownRankImpl rank = rankName != null ? town.getRankImpl(rankName) : null;
                if (rank == null) {
                    rank = town.getDefaultRankImpl();
                }
                town.restoreResident(resident, rank);
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT id, owner_id FROM towns")) {
                while (rs.next()) {
                    TownImpl town = towns.get(UUID.fromString(rs.getString("id")));
                    ResidentImpl owner = residents.get(UUID.fromString(rs.getString("owner_id")));
                    if (town != null && owner != null) {
                        town.restoreOwner(owner);
                    } else if (town != null) {
                        plugin.getLogger().severe("Town '" + town.getName() + "' references a missing owner resident; "
                                + "it will have no owner until fixed manually.");
                    }
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT world, chunk_x, chunk_z, town_id, plot_name FROM claims")) {
                while (rs.next()) {
                    TownImpl town = towns.get(UUID.fromString(rs.getString("town_id")));
                    if (town == null) {
                        continue;
                    }
                    String world = rs.getString("world");
                    int x = rs.getInt("chunk_x");
                    int z = rs.getInt("chunk_z");
                    ClaimImpl claim = new ClaimImpl(town, world, x, z);
                    claim.setPlotName(rs.getString("plot_name"));
                    town.addClaim(claim);
                    claims.put(new ChunkPos(world, x, z), claim);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT world, chunk_x, chunk_z, trust_level, permission FROM claim_permissions")) {
                while (rs.next()) {
                    ChunkPos pos = new ChunkPos(rs.getString("world"), rs.getInt("chunk_x"), rs.getInt("chunk_z"));
                    ClaimImpl claim = claims.get(pos);
                    if (claim == null) {
                        continue;
                    }
                    claim.setPermission(
                            TrustLevel.valueOf(rs.getString("trust_level")),
                            ClaimPermission.valueOf(rs.getString("permission")),
                            true);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT id, name, capital_town_id, bank_balance, description, map_color, open, frozen FROM nations")) {
                while (rs.next()) {
                    TownImpl capital = towns.get(UUID.fromString(rs.getString("capital_town_id")));
                    if (capital == null) {
                        continue;
                    }
                    UUID id = UUID.fromString(rs.getString("id"));
                    NationImpl nation = new NationImpl(id, rs.getString("name"), capital);
                    nation.depositBank(rs.getDouble("bank_balance"));
                    nation.setDescription(rs.getString("description"));
                    nation.setMapColor(new Color(rs.getInt("map_color")));
                    nation.setOpen(rs.getInt("open") != 0);
                    nation.setFrozen(rs.getInt("frozen") != 0);
                    nations.put(id, nation);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery(
                         "SELECT id, name, nation_id, capital_town_id, leader_id, bank_balance, description, map_color, open, frozen FROM states")) {
                while (rs.next()) {
                    NationImpl nation = nations.get(UUID.fromString(rs.getString("nation_id")));
                    TownImpl capital = towns.get(UUID.fromString(rs.getString("capital_town_id")));
                    if (nation == null || capital == null) {
                        continue;
                    }
                    UUID id = UUID.fromString(rs.getString("id"));
                    StateImpl state = new StateImpl(id, rs.getString("name"), nation, capital);
                    String leaderId = rs.getString("leader_id");
                    if (leaderId != null) {
                        ResidentImpl leader = residents.get(UUID.fromString(leaderId));
                        if (leader != null) {
                            state.restoreLeader(leader);
                        }
                    }
                    state.depositBank(rs.getDouble("bank_balance"));
                    state.setDescription(rs.getString("description"));
                    state.setMapColor(new Color(rs.getInt("map_color")));
                    state.setOpen(rs.getInt("open") != 0);
                    state.setFrozen(rs.getInt("frozen") != 0);
                    states.put(id, state);
                    nation.getStateImpls().add(state);
                }
            }

            for (Map.Entry<UUID, UUID> entry : townStateIds.entrySet()) {
                TownImpl town = towns.get(entry.getKey());
                StateImpl state = states.get(entry.getValue());
                if (town == null || state == null) {
                    continue;
                }
                town.setStateImpl(state);
                if (state.getCapitalImpl() != town) {
                    state.getTownImpls().add(town);
                }
            }
            for (Map.Entry<UUID, UUID> entry : townDirectNationIds.entrySet()) {
                TownImpl town = towns.get(entry.getKey());
                NationImpl nation = nations.get(entry.getValue());
                if (town == null || nation == null || town.getStateImpl() != null) {
                    continue;
                }
                town.setDirectNationImpl(nation);
                if (nation.getCapitalImpl() != town) {
                    nation.getDirectTownImpls().add(town);
                }
            }

            try (Statement statement = connection.createStatement();
                 ResultSet rs = statement.executeQuery("SELECT nation_a, nation_b, status FROM nation_relations")) {
                while (rs.next()) {
                    UUID a = UUID.fromString(rs.getString("nation_a"));
                    UUID b = UUID.fromString(rs.getString("nation_b"));
                    DiplomaticStatus status = DiplomaticStatus.valueOf(rs.getString("status"));
                    if (!nations.containsKey(a) || !nations.containsKey(b)) {
                        continue;
                    }
                    relations.computeIfAbsent(a, k -> new LinkedHashMap<>()).put(b, status);
                    relations.computeIfAbsent(b, k -> new LinkedHashMap<>()).put(a, status);
                }
            }
        }

        return new LoadResult(towns, residents, claims, nations, states, relations);
    }

    @Override
    public void saveTown(TownImpl town) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Location home = town.getHomeLocation().orElse(null);
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO towns (id, name, owner_id, max_claims, bank_balance, description, map_color,
                            home_world, home_x, home_y, home_z, home_yaw, home_pitch, open, pvp, frozen,
                            state_id, direct_nation_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET
                            name = excluded.name, owner_id = excluded.owner_id, max_claims = excluded.max_claims,
                            bank_balance = excluded.bank_balance, description = excluded.description,
                            map_color = excluded.map_color, home_world = excluded.home_world,
                            home_x = excluded.home_x, home_y = excluded.home_y, home_z = excluded.home_z,
                            home_yaw = excluded.home_yaw, home_pitch = excluded.home_pitch,
                            open = excluded.open, pvp = excluded.pvp, frozen = excluded.frozen,
                            state_id = excluded.state_id, direct_nation_id = excluded.direct_nation_id
                        """)) {
                    ps.setString(1, town.getUniqueId().toString());
                    ps.setString(2, town.getName());
                    ps.setString(3, town.getOwner().getUniqueId().toString());
                    ps.setInt(4, town.getMaxClaims());
                    ps.setDouble(5, town.getBankBalance());
                    ps.setString(6, town.getDescription());
                    ps.setInt(7, town.getMapColor().getRGB());
                    if (home != null) {
                        ps.setString(8, home.getWorld().getName());
                        ps.setDouble(9, home.getX());
                        ps.setDouble(10, home.getY());
                        ps.setDouble(11, home.getZ());
                        ps.setFloat(12, home.getYaw());
                        ps.setFloat(13, home.getPitch());
                    } else {
                        ps.setNull(8, java.sql.Types.VARCHAR);
                        ps.setNull(9, java.sql.Types.REAL);
                        ps.setNull(10, java.sql.Types.REAL);
                        ps.setNull(11, java.sql.Types.REAL);
                        ps.setNull(12, java.sql.Types.REAL);
                        ps.setNull(13, java.sql.Types.REAL);
                    }
                    ps.setInt(14, town.isOpen() ? 1 : 0);
                    ps.setInt(15, town.isPvpEnabled() ? 1 : 0);
                    ps.setInt(16, town.isFrozen() ? 1 : 0);
                    if (town.getStateImpl() != null) {
                        ps.setString(17, town.getStateImpl().getUniqueId().toString());
                    } else {
                        ps.setNull(17, java.sql.Types.VARCHAR);
                    }
                    if (town.getDirectNationImpl() != null) {
                        ps.setString(18, town.getDirectNationImpl().getUniqueId().toString());
                    } else {
                        ps.setNull(18, java.sql.Types.VARCHAR);
                    }
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM rank_permissions WHERE town_id = ?")) {
                    ps.setString(1, town.getUniqueId().toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM town_ranks WHERE town_id = ?")) {
                    ps.setString(1, town.getUniqueId().toString());
                    ps.executeUpdate();
                }
                try (PreparedStatement rankPs = connection.prepareStatement(
                        "INSERT INTO town_ranks (town_id, name, is_owner_rank, is_default_rank) VALUES (?, ?, ?, ?)");
                     PreparedStatement permPs = connection.prepareStatement(
                             "INSERT INTO rank_permissions (town_id, rank_name, permission) VALUES (?, ?, ?)")) {
                    for (TownRankImpl rank : town.getRankImpls()) {
                        rankPs.setString(1, town.getUniqueId().toString());
                        rankPs.setString(2, rank.getName());
                        rankPs.setInt(3, rank.isOwnerRank() ? 1 : 0);
                        rankPs.setInt(4, rank.isDefaultRank() ? 1 : 0);
                        rankPs.executeUpdate();

                        if (!rank.isOwnerRank()) {
                            for (TownPermission permission : rank.getPermissions()) {
                                permPs.setString(1, town.getUniqueId().toString());
                                permPs.setString(2, rank.getName());
                                permPs.setString(3, permission.name());
                                permPs.executeUpdate();
                            }
                        }
                    }
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save town " + town.getName(), e);
        }
    }

    @Override
    public void deleteTown(UUID townId) {
        String id = townId.toString();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM claim_permissions WHERE (world, chunk_x, chunk_z) IN "
                                + "(SELECT world, chunk_x, chunk_z FROM claims WHERE town_id = ?)")) {
                    ps.setString(1, id);
                    ps.executeUpdate();
                }
                execUpdate(connection, "DELETE FROM claims WHERE town_id = ?", id);
                execUpdate(connection, "UPDATE residents SET town_id = NULL, rank_name = NULL WHERE town_id = ?", id);
                execUpdate(connection, "DELETE FROM rank_permissions WHERE town_id = ?", id);
                execUpdate(connection, "DELETE FROM town_ranks WHERE town_id = ?", id);
                execUpdate(connection, "DELETE FROM towns WHERE id = ?", id);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete town " + id, e);
        }
    }

    @Override
    public void saveResident(ResidentImpl resident) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO residents (id, name, town_id, rank_name, last_seen)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         name = excluded.name, town_id = excluded.town_id,
                         rank_name = excluded.rank_name, last_seen = excluded.last_seen
                     """)) {
            ps.setString(1, resident.getUniqueId().toString());
            ps.setString(2, resident.getName());
            if (resident.getTownImpl() != null) {
                ps.setString(3, resident.getTownImpl().getUniqueId().toString());
                ps.setString(4, resident.getRankImpl() != null ? resident.getRankImpl().getName() : null);
            } else {
                ps.setNull(3, java.sql.Types.VARCHAR);
                ps.setNull(4, java.sql.Types.VARCHAR);
            }
            ps.setLong(5, resident.getLastSeen());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save resident " + resident.getName(), e);
        }
    }

    @Override
    public void saveClaim(ClaimImpl claim) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO claims (world, chunk_x, chunk_z, town_id, plot_name)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(world, chunk_x, chunk_z) DO UPDATE SET
                            town_id = excluded.town_id, plot_name = excluded.plot_name
                        """)) {
                    ps.setString(1, claim.getWorldName());
                    ps.setInt(2, claim.getChunkX());
                    ps.setInt(3, claim.getChunkZ());
                    ps.setString(4, claim.getTownImpl().getUniqueId().toString());
                    ps.setString(5, claim.getPlotName());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM claim_permissions WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                    ps.setString(1, claim.getWorldName());
                    ps.setInt(2, claim.getChunkX());
                    ps.setInt(3, claim.getChunkZ());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO claim_permissions (world, chunk_x, chunk_z, trust_level, permission) VALUES (?, ?, ?, ?, ?)")) {
                    for (TrustLevel level : TrustLevel.values()) {
                        for (ClaimPermission permission : ClaimPermission.values()) {
                            if (!claim.isPermissionSet(level, permission)) {
                                continue;
                            }
                            ps.setString(1, claim.getWorldName());
                            ps.setInt(2, claim.getChunkX());
                            ps.setInt(3, claim.getChunkZ());
                            ps.setString(4, level.name());
                            ps.setString(5, permission.name());
                            ps.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save claim " + claim.getWorldName()
                    + " " + claim.getChunkX() + "," + claim.getChunkZ(), e);
        }
    }

    @Override
    public void deleteClaim(String worldName, int chunkX, int chunkZ) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM claim_permissions WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                    ps.setString(1, worldName);
                    ps.setInt(2, chunkX);
                    ps.setInt(3, chunkZ);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM claims WHERE world = ? AND chunk_x = ? AND chunk_z = ?")) {
                    ps.setString(1, worldName);
                    ps.setInt(2, chunkX);
                    ps.setInt(3, chunkZ);
                    ps.executeUpdate();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete claim " + worldName + " " + chunkX + "," + chunkZ, e);
        }
    }

    @Override
    public void saveNation(NationImpl nation) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO nations (id, name, capital_town_id, bank_balance, description, map_color, open, frozen)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         name = excluded.name, capital_town_id = excluded.capital_town_id,
                         bank_balance = excluded.bank_balance, description = excluded.description,
                         map_color = excluded.map_color, open = excluded.open, frozen = excluded.frozen
                     """)) {
            ps.setString(1, nation.getUniqueId().toString());
            ps.setString(2, nation.getName());
            ps.setString(3, nation.getCapitalImpl().getUniqueId().toString());
            ps.setDouble(4, nation.getBankBalance());
            ps.setString(5, nation.getDescription());
            ps.setInt(6, nation.getMapColor().getRGB());
            ps.setInt(7, nation.isOpen() ? 1 : 0);
            ps.setInt(8, nation.isFrozen() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save nation " + nation.getName(), e);
        }
    }

    @Override
    public void deleteNation(UUID nationId) {
        String id = nationId.toString();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                execUpdate(connection, "DELETE FROM nation_relations WHERE nation_a = ? OR nation_b = ?", id, id);
                execUpdate(connection, "UPDATE towns SET state_id = NULL WHERE state_id IN (SELECT id FROM states WHERE nation_id = ?)", id);
                execUpdate(connection, "DELETE FROM states WHERE nation_id = ?", id);
                execUpdate(connection, "UPDATE towns SET direct_nation_id = NULL WHERE direct_nation_id = ?", id);
                execUpdate(connection, "DELETE FROM nations WHERE id = ?", id);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete nation " + id, e);
        }
    }

    @Override
    public void saveState(StateImpl state) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO states (id, name, nation_id, capital_town_id, leader_id, bank_balance, description, map_color, open, frozen)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(id) DO UPDATE SET
                         name = excluded.name, nation_id = excluded.nation_id, capital_town_id = excluded.capital_town_id,
                         leader_id = excluded.leader_id, bank_balance = excluded.bank_balance, description = excluded.description,
                         map_color = excluded.map_color, open = excluded.open, frozen = excluded.frozen
                     """)) {
            ps.setString(1, state.getUniqueId().toString());
            ps.setString(2, state.getName());
            ps.setString(3, state.getNationImpl().getUniqueId().toString());
            ps.setString(4, state.getCapitalImpl().getUniqueId().toString());
            if (state.getLeaderImpl() != null) {
                ps.setString(5, state.getLeaderImpl().getUniqueId().toString());
            } else {
                ps.setNull(5, java.sql.Types.VARCHAR);
            }
            ps.setDouble(6, state.getBankBalance());
            ps.setString(7, state.getDescription());
            ps.setInt(8, state.getMapColor().getRGB());
            ps.setInt(9, state.isOpen() ? 1 : 0);
            ps.setInt(10, state.isFrozen() ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save state " + state.getName(), e);
        }
    }

    @Override
    public void deleteState(UUID stateId) {
        String id = stateId.toString();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                execUpdate(connection, "UPDATE towns SET state_id = NULL WHERE state_id = ?", id);
                execUpdate(connection, "DELETE FROM states WHERE id = ?", id);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to delete state " + id, e);
        }
    }

    @Override
    public void saveNationRelation(UUID nationA, UUID nationB, DiplomaticStatus status) {
        String a = nationA.toString();
        String b = nationB.toString();
        boolean swap = a.compareTo(b) > 0;
        String first = swap ? b : a;
        String second = swap ? a : b;
        try (Connection connection = dataSource.getConnection()) {
            if (status == DiplomaticStatus.NEUTRAL) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM nation_relations WHERE nation_a = ? AND nation_b = ?")) {
                    ps.setString(1, first);
                    ps.setString(2, second);
                    ps.executeUpdate();
                }
                return;
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO nation_relations (nation_a, nation_b, status) VALUES (?, ?, ?)
                    ON CONFLICT(nation_a, nation_b) DO UPDATE SET status = excluded.status
                    """)) {
                ps.setString(1, first);
                ps.setString(2, second);
                ps.setString(3, status.name());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save nation relation " + a + "/" + b, e);
        }
    }

    private static void execUpdate(Connection connection, String sql, String param) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param);
            ps.executeUpdate();
        }
    }

    private static void execUpdate(Connection connection, String sql, String param1, String param2) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, param1);
            ps.setString(2, param2);
            ps.executeUpdate();
        }
    }
}
