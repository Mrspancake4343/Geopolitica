package com.geopolitica.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    public String getStorageType() {
        return config.getString("storage.type", "sqlite");
    }

    public String getMysqlHost() {
        return config.getString("storage.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("storage.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("storage.mysql.database", "geopolitica");
    }

    public String getMysqlUsername() {
        return config.getString("storage.mysql.username", "geopolitica");
    }

    public String getMysqlPassword() {
        return config.getString("storage.mysql.password", "");
    }

    public boolean getMysqlUseSsl() {
        return config.getBoolean("storage.mysql.useSSL", false);
    }

    public int getMinNameLength() {
        return config.getInt("town.min-name-length", 3);
    }

    public int getMaxNameLength() {
        return config.getInt("town.max-name-length", 32);
    }

    public int getDefaultMaxClaims() {
        return config.getInt("town.max-claims", 64);
    }

    public int getFreeClaims() {
        return config.getInt("town.free-claims", 4);
    }

    public double getCostPerClaim() {
        return config.getDouble("town.cost-per-claim", 500.0);
    }

    public double getRefundPerClaim() {
        return config.getDouble("town.refund-per-claim", 250.0);
    }

    public boolean isRequireAdjacentClaims() {
        return config.getBoolean("town.require-adjacent-claims", true);
    }

    public int getMinDistanceBetweenTowns() {
        return config.getInt("town.min-distance-between-towns", 0);
    }

    public double getUpkeepPerClaimPerDay() {
        return config.getDouble("town.upkeep-per-claim-per-day", 5.0);
    }

    public String getUpkeepFailureAction() {
        return config.getString("town.upkeep-failure-action", "WARN");
    }

    public boolean getDefaultClaimPermission(String trustLevel, String permission) {
        return config.getBoolean("claims.default-permissions." + trustLevel + "." + permission, false);
    }

    public boolean isAdminBypassEnabled() {
        return config.getBoolean("general.admin-bypass", true);
    }
}
