package com.geopolitica.core.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Soft-dependency wrapper around Vault's economy service, accessed entirely
 * via reflection so Geopolitica has no compile-time dependency on VaultAPI
 * (avoiding a fragile external Maven repository at build time) and cannot
 * end up with a duplicate, classloader-incompatible copy of Vault's classes
 * bundled in its own jar. When Vault is absent, every cost check succeeds
 * and every charge is a no-op, so claim costs and upkeep are effectively
 * disabled rather than blocking play.
 */
public class EconomyHook {

    private final JavaPlugin plugin;
    private Object economy;
    private Method hasMethod;
    private Method withdrawMethod;
    private Method depositMethod;
    private Method formatMethod;
    private Method transactionSuccessMethod;

    public EconomyHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(economyClass.asSubclass(Object.class));
            if (provider == null) {
                return false;
            }
            economy = provider.getProvider();

            hasMethod = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            withdrawMethod = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            depositMethod = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            formatMethod = economyClass.getMethod("format", double.class);

            Class<?> responseClass = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            transactionSuccessMethod = responseClass.getMethod("transactionSuccess");
            return true;
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(Level.WARNING, "Vault was found but its economy API could not be hooked; economy features disabled.", e);
            economy = null;
            return false;
        }
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public boolean has(OfflinePlayer player, double amount) {
        if (economy == null) {
            return true;
        }
        try {
            return (boolean) hasMethod.invoke(economy, player, amount);
        } catch (ReflectiveOperationException e) {
            logFailure(e);
            return true;
        }
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        if (economy == null) {
            return true;
        }
        try {
            Object response = withdrawMethod.invoke(economy, player, amount);
            return (boolean) transactionSuccessMethod.invoke(response);
        } catch (ReflectiveOperationException e) {
            logFailure(e);
            return false;
        }
    }

    public void deposit(OfflinePlayer player, double amount) {
        if (economy == null) {
            return;
        }
        try {
            depositMethod.invoke(economy, player, amount);
        } catch (ReflectiveOperationException e) {
            logFailure(e);
        }
    }

    public String format(double amount) {
        if (economy == null) {
            return String.valueOf(amount);
        }
        try {
            return (String) formatMethod.invoke(economy, amount);
        } catch (ReflectiveOperationException e) {
            logFailure(e);
            return String.valueOf(amount);
        }
    }

    private void logFailure(ReflectiveOperationException e) {
        plugin.getLogger().log(Level.WARNING, "Vault economy call failed", e);
    }
}
