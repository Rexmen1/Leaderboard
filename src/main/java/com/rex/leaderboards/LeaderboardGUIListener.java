package com.rex.leaderboards;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.entity.Player;

public class LeaderboardGUIListener implements Listener {
	private final LeaderboardPlugin plugin;

	public LeaderboardGUIListener(LeaderboardPlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		String title = event.getView().getTitle();
		if (!title.startsWith("Leaderboards - ")) {
			return;
		}
		
		event.setCancelled(true);
		
		if (!(event.getWhoClicked() instanceof Player player)) {
			return;
		}

		// Extract type and current page
		String[] titleParts = title.split(" - ");
		if (titleParts.length != 3) return;
		
		String type = titleParts[1];
		int currentPage = Integer.parseInt(titleParts[2].replace("Page ", "")) - 1;

		// Check if clicked navigation buttons
		int clickedSlot = event.getSlot();
		int prevSlot = plugin.getConfig().getInt("gui.navigation.previous-page.slot", 48);
		int nextSlot = plugin.getConfig().getInt("gui.navigation.next-page.slot", 50);

		if (clickedSlot == prevSlot && currentPage > 0) {
			// Go to previous page
			((LeaderboardCommand) plugin.getCommand("leaderboard").getExecutor())
				.openLeaderboardGUI(player, type, currentPage - 1);
		} else if (clickedSlot == nextSlot) {
			// Go to next page
			((LeaderboardCommand) plugin.getCommand("leaderboard").getExecutor())
				.openLeaderboardGUI(player, type, currentPage + 1);
		}
	}
}
