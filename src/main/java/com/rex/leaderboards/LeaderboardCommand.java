package com.rex.leaderboards;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {
	private final LeaderboardPlugin plugin;

	public LeaderboardCommand(LeaderboardPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("§cThis command can only be used by players!");
			return true;
		}

		if (args.length < 1) {
			sendHelp(player);
			return true;
		}

		LeaderboardManager manager = plugin.getLeaderboardManager();

		if (args[0].equalsIgnoreCase("reload")) {
			if (!player.hasPermission("leaderboards.reload")) {
				player.sendMessage("§cYou don't have permission to reload the plugin!");
				return true;
			}
			
			plugin.reloadConfig();
			manager.reload();
			player.sendMessage("§aConfiguration reloaded successfully!");
			return true;
		}

		if (args[0].equalsIgnoreCase("update")) {
			if (!player.hasPermission("leaderboards.update")) {
				player.sendMessage("§cYou don't have permission to update leaderboards!");
				return true;
			}

			if (args.length < 2) {
				player.sendMessage("§cUsage: /leaderboard update <type|*>");
				return true;
			}

			if (args[1].equals("*")) {
				player.sendMessage("§aUpdating all leaderboards...");
				manager.updateAllLeaderboards(() -> {
					player.sendMessage("§aAll leaderboards have been updated!");
				});
			} else if (manager.exists(args[1])) {
				String type = args[1];
				player.sendMessage("§aUpdating leaderboard: " + type);
				manager.updateLeaderboard(type, () -> {
					player.sendMessage("§aLeaderboard has been updated!");
				});
			} else {
				player.sendMessage("§cLeaderboard type not found!");
			}

			return true;
		}

		String type = args[0].toLowerCase();
		if (!manager.exists(type)) {
			player.sendMessage("§cLeaderboard type not found!");
			return true;
		}

		openLeaderboardGUI(player, type);
		return true;
	}

	public void openLeaderboardGUI(Player player, String type, int page) {
		LeaderboardManager manager = plugin.getLeaderboardManager();
		int guiSize = plugin.getConfig().getInt("gui.size", 54);
		String title = plugin.getConfig().getString("gui.title", "Leaderboards - {type}")
				.replace("{type}", manager.getTitle(type));

		Inventory gui = Bukkit.createInventory(null, guiSize, title + " - Page " + (page + 1));

		// Get all players and calculate total pages
		List<Map.Entry<String, Double>> allPlayers = manager.getTopPlayers(type, Integer.MAX_VALUE);
		int totalPages = (allPlayers.size() + 44) / 45; // Round up division
		
		// Get players for current page
		int startIndex = page * 45;
		int endIndex = Math.min(startIndex + 45, allPlayers.size());
		List<Map.Entry<String, Double>> pageEntries = allPlayers.subList(startIndex, endIndex);

		// Add leaderboard entries
		int slot = 0;
		for (Map.Entry<String, Double> entry : pageEntries) {
			ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
			org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) skull.getItemMeta();
			if (meta != null) {
				meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.getKey()));
				meta.setDisplayName("§6#" + (startIndex + slot + 1) + " §f" + entry.getKey());
				List<String> lore = new ArrayList<>();
				String format = plugin.getConfig().getString("leaderboards." + type + ".format", "{position}. {player}: {value}");
				format = format.replace("{position}", String.valueOf(startIndex + slot + 1))
						 .replace("{player}", entry.getKey())
						 .replace("{value}", String.valueOf(entry.getValue()));
				lore.add("§7" + format);
				meta.setLore(lore);
				skull.setItemMeta(meta);
			}
			gui.setItem(slot++, skull);
		}

		// Add navigation buttons if needed
		if (page > 0) {
			// Previous page button
			ItemStack prevButton = new ItemStack(Material.valueOf(plugin.getConfig().getString("gui.navigation.previous-page.item", "ARROW")));
			ItemMeta prevMeta = prevButton.getItemMeta();
			if (prevMeta != null) {
				prevMeta.setDisplayName(plugin.getConfig().getString("gui.navigation.previous-page.name", "§aPrevious Page"));
				prevButton.setItemMeta(prevMeta);
			}
			gui.setItem(plugin.getConfig().getInt("gui.navigation.previous-page.slot", 48), prevButton);
		}

		if (page < totalPages - 1) {
			// Next page button
			ItemStack nextButton = new ItemStack(Material.valueOf(plugin.getConfig().getString("gui.navigation.next-page.item", "ARROW")));
			ItemMeta nextMeta = nextButton.getItemMeta();
			if (nextMeta != null) {
				nextMeta.setDisplayName(plugin.getConfig().getString("gui.navigation.next-page.name", "§aNext Page"));
				nextButton.setItemMeta(nextMeta);
			}
			gui.setItem(plugin.getConfig().getInt("gui.navigation.next-page.slot", 50), nextButton);
		}

		player.openInventory(gui);
	}

	public void openLeaderboardGUI(Player player, String type) {
		openLeaderboardGUI(player, type, 0);
	}



	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
		List<String> completions = new ArrayList<>();
		
		if (args.length == 1) {
			// First argument: type or "update" or "reload"
			completions.addAll(plugin.getLeaderboardManager().getTypes());
			if (sender.hasPermission("leaderboards.update")) {
				completions.add("update");
			}
			if (sender.hasPermission("leaderboards.reload")) {
				completions.add("reload");
			}
		} else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
			// Second argument after "update": type or "*"
			completions.addAll(plugin.getLeaderboardManager().getTypes());
			completions.add("*");
		}

		// Filter based on current input
		return completions.stream()
				.filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
				.collect(Collectors.toList());
	}

	private void sendHelp(Player player) {
		player.sendMessage("§8§l§m--------------------§r §6§lLeaderboards §8§l§m--------------------");
		player.sendMessage("");
		player.sendMessage("§6Commands:");
		player.sendMessage("  §f/leaderboard <type> §7- View a leaderboard");
		if (player.hasPermission("leaderboards.update")) {
			player.sendMessage("  §f/leaderboard update <type> §7- Update specific leaderboard");
			player.sendMessage("  §f/leaderboard update * §7- Update all leaderboards");
		}
		if (player.hasPermission("leaderboards.reload")) {
			player.sendMessage("  §f/leaderboard reload §7- Reload plugin configuration");
		}
		player.sendMessage("");
		player.sendMessage("§6Available Types:");
		player.sendMessage("  §f" + String.join("§7, §f", plugin.getLeaderboardManager().getTypes()));
		player.sendMessage("§8§l§m-------------------------------------------------");
	}
}