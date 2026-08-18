package com.geopolitica.core.listener;

import com.geopolitica.core.service.TownServiceImpl;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class PlayerJoinListener implements Listener {

    private final TownServiceImpl townService;

    public PlayerJoinListener(TownServiceImpl townService) {
        this.townService = townService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        townService.trackPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }
}
