package com.rex.leaderboards;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import java.util.List;
import java.util.Map;

public class LeaderboardExpansion extends PlaceholderExpansion {
	private final LeaderboardPlugin plugin;

	public LeaderboardExpansion(LeaderboardPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public String getIdentifier() {
		return "leaderboard";
	}

	@Override
	public String getAuthor() {
		return plugin.getDescription().getAuthors().get(0);
	}

	@Override
	public String getVersion() {
		return plugin.getDescription().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public String onRequest(OfflinePlayer player, String params) {
		String[] args = params.split("_");
		if (args.length != 3) return null;

		String requestType = args[0]; // name or value
		String type = args[1]; // leaderboard type
		int position;

		try {
			position = Integer.parseInt(args[2]) - 1; // Convert to 0-based index
		} catch (NumberFormatException e) {
			return null;
		}

		if (!plugin.getLeaderboardManager().exists(type)) {
			return null;
		}

		List<Map.Entry<String, Double>> topPlayers = 
			plugin.getLeaderboardManager().getTopPlayers(type, position + 1);

		if (position >= topPlayers.size()) {
			return "N/A";
		}

		Map.Entry<String, Double> entry = topPlayers.get(position);
		
		if (requestType.equals("name")) {
			return entry.getKey();
		} else if (requestType.equals("value")) {
			return String.valueOf(entry.getValue());
		}

		return null;
	}
}