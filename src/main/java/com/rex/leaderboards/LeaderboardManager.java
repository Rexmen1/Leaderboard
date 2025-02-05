package com.rex.leaderboards;

import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class LeaderboardManager {
	private final LeaderboardPlugin plugin;
	private final Map<String, String> leaderboardPlaceholders;
	private final Map<String, String> leaderboardTitles;
	private final File dataFile;
	private FileConfiguration data;
	private static final int BATCH_SIZE = 50; // Process 50 players per batch
	private static final long BATCH_DELAY = 20L; // 1 second delay between batches (20 ticks)

	public LeaderboardManager(LeaderboardPlugin plugin) {
		this.plugin = plugin;
		this.leaderboardPlaceholders = new HashMap<>();
		this.leaderboardTitles = new HashMap<>();
		this.dataFile = new File(plugin.getDataFolder(), "data.yml");
		loadLeaderboards();
		loadData();
	}

	private void loadData() {
		if (!dataFile.exists()) {
			plugin.saveResource("data.yml", false);
		}
		data = YamlConfiguration.loadConfiguration(dataFile);
	}

	private void saveData() {
		try {
			data.save(dataFile);
		} catch (IOException e) {
			plugin.getLogger().severe("Could not save data file!");
		}
	}

	private void loadLeaderboards() {
		ConfigurationSection section = plugin.getConfig().getConfigurationSection("leaderboards");
		if (section == null) return;

		for (String type : section.getKeys(false)) {
			String placeholder = section.getString(type + ".placeholder");
			String title = section.getString(type + ".title", type);
			if (placeholder != null) {
				leaderboardPlaceholders.put(type, placeholder);
				leaderboardTitles.put(type, title);
			}
		}
	}

	private void debug(String message) {
		if (plugin.getConfig().getBoolean("debug.enabled", false) && 
			plugin.getConfig().getBoolean("debug.log-updates", false)) {
			plugin.getLogger().info(message);
		}
	}

	public void updateLeaderboard(String type, UpdateCallback callback) {
		if (!leaderboardPlaceholders.containsKey(type)) {
			if (callback != null) callback.onComplete();
			return;
		}

		String placeholder = leaderboardPlaceholders.get(type);
		ConfigurationSection typeSection = data.getConfigurationSection(type);
		if (typeSection == null) {
			typeSection = data.createSection(type);
		}

		final ConfigurationSection finalSection = typeSection;
		Map<String, Double> existingValues = new HashMap<>();

		// Keep existing data
		for (String playerName : finalSection.getKeys(false)) {
			if (!playerName.equals("last_update")) {
				existingValues.put(playerName, finalSection.getDouble(playerName));
			}
		}

		// Update online players
		Map<String, Double> newValues = new HashMap<>();
		for (Player player : Bukkit.getOnlinePlayers()) {
			try {
				String result = PlaceholderAPI.setPlaceholders(player, placeholder);
				try {
					double value = Double.parseDouble(result);
					if (value > 0) {
						newValues.put(player.getName(), value);
						debug("Updated " + type + " for " + player.getName() + ": " + value);
					}
				} catch (NumberFormatException ignored) {
					debug("Failed to parse value for " + player.getName() + " with placeholder " + placeholder);
				}
			} catch (Exception e) {
				debug("Error processing stats for " + player.getName() + ": " + e.getMessage());
			}
		}

		// Update all values at once
		for (Map.Entry<String, Double> entry : newValues.entrySet()) {
			finalSection.set(entry.getKey(), entry.getValue());
		}

		// Store last update time
		finalSection.set("last_update", System.currentTimeMillis());

		// Save data
		saveData();

		if (callback != null) {
			callback.onComplete();
		}
	}

	public void updateAllLeaderboards(UpdateCallback callback) {
		// Process each leaderboard type
		List<String> types = new ArrayList<>(leaderboardPlaceholders.keySet());
		for (String type : types) {
			updateLeaderboard(type, null);
		}
		if (callback != null) {
			callback.onComplete();
		}
	}

	public List<Map.Entry<String, Double>> getTopPlayers(String type, int limit) {
		ConfigurationSection typeSection = data.getConfigurationSection(type);
		if (typeSection == null) return new ArrayList<>();

		Map<String, Double> values = new HashMap<>();
		for (String key : typeSection.getKeys(false)) {
			if (!key.equals("last_update")) {
				values.put(key, typeSection.getDouble(key));
			}
		}

		return values.entrySet().stream()
				.sorted(Map.Entry.<String, Double>comparingByValue().reversed())
				.limit(limit)
				.toList();
	}

	public long getLastUpdate(String type) {
		ConfigurationSection typeSection = data.getConfigurationSection(type);
		if (typeSection == null) return 0;
		return typeSection.getLong("last_update", 0);
	}



	public String getTitle(String type) {
		return leaderboardTitles.getOrDefault(type, type);
	}

	public boolean exists(String type) {
		return leaderboardPlaceholders.containsKey(type);
	}

	public Set<String> getTypes() {
		return leaderboardPlaceholders.keySet();
	}

	public void reload() {
		// Backup existing placeholders and titles
		Map<String, String> oldPlaceholders = new HashMap<>(leaderboardPlaceholders);
		Map<String, String> oldTitles = new HashMap<>(leaderboardTitles);
		
		// Clear and reload configurations
		leaderboardPlaceholders.clear();
		leaderboardTitles.clear();
		loadLeaderboards();
		
		// Check for removed leaderboard types
		Set<String> removedTypes = new HashSet<>(oldPlaceholders.keySet());
		removedTypes.removeAll(leaderboardPlaceholders.keySet());
		if (!removedTypes.isEmpty()) {
			debug("Removed leaderboard types: " + String.join(", ", removedTypes));
		}
		
		// Check for new or modified leaderboard types
		for (String type : leaderboardPlaceholders.keySet()) {
			if (!oldPlaceholders.containsKey(type)) {
				debug("New leaderboard type added: " + type);
			} else if (!oldPlaceholders.get(type).equals(leaderboardPlaceholders.get(type))) {
				debug("Modified leaderboard placeholder: " + type);
			}
		}
		
		debug("Configuration reloaded - Leaderboard data preserved");
	}

	public void shutdown() {
		saveData();
	}
}