package com.rex.leaderboards;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import me.clip.placeholderapi.PlaceholderAPI;

public class LeaderboardPlugin extends JavaPlugin {
	private LeaderboardManager leaderboardManager;
	private BukkitTask updateTask;

	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		
		// Initialize managers
		this.leaderboardManager = new LeaderboardManager(this);
		
		// Register commands and tab completer
		LeaderboardCommand commandExecutor = new LeaderboardCommand(this);
		getCommand("leaderboard").setExecutor(commandExecutor);
		getCommand("leaderboard").setTabCompleter(commandExecutor);
		
		// Register GUI listener
		getServer().getPluginManager().registerEvents(new LeaderboardGUIListener(this), this);
		
		// Register PlaceholderAPI expansion
		if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			new LeaderboardExpansion(this).register();
			getLogger().info("Registered PlaceholderAPI expansion!");
		}
		
		// Start update task
		int updateInterval = getConfig().getInt("update-interval", 30);
		startUpdateTask(updateInterval);
		
		getLogger().info("Leaderboards plugin enabled!");
	}
	
	@Override
	public void onDisable() {
		if (updateTask != null) {
			updateTask.cancel();
		}
		if (leaderboardManager != null) {
			leaderboardManager.shutdown();
		}
		getLogger().info("Leaderboards plugin disabled!");
	}
	
	private void startUpdateTask(int intervalSeconds) {
		updateTask = getServer().getScheduler().runTaskTimerAsynchronously(this, 
			() -> leaderboardManager.updateAllLeaderboards(null), 
			20L, // Initial delay of 1 second
			intervalSeconds * 20L // Convert to ticks
		);
	}
	
	public LeaderboardManager getLeaderboardManager() {
		return leaderboardManager;
	}
}