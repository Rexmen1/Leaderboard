package com.rex.leaderboards;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener implements Listener {
    private final LeaderboardPlugin plugin;

    public PlayerEventListener(LeaderboardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Cache the player's texture with a slight delay to ensure the player is fully
        // loaded
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.getTextureCache().cachePlayerTexture(player);
            }
        }, 20L); // 1 second delay
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Cache the player's texture before they disconnect
        // Run immediately since the player is still online
        plugin.getTextureCache().cachePlayerTexture(player);
    }
}
