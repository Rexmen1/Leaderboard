package com.rex.leaderboards;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public class LeaderboardCommand implements CommandExecutor, TabCompleter {
   private final LeaderboardPlugin plugin;

   public LeaderboardCommand(LeaderboardPlugin plugin) {
      this.plugin = plugin;
   }

   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (sender instanceof Player) {
         Player player = (Player)sender;
         if (args.length < 1) {
            this.sendHelp(player);
            return true;
         } else {
            LeaderboardManager manager = this.plugin.getLeaderboardManager();
            if (args[0].equalsIgnoreCase("reload")) {
               if (!player.hasPermission("leaderboards.reload")) {
                  player.sendMessage("§cYou don't have permission to reload the plugin!");
                  return true;
               } else {
                  this.plugin.reloadConfig();
                  manager.reload();
                  player.sendMessage("§aConfiguration reloaded successfully!");
                  return true;
               }
            } else {
               String type;
               if (args[0].equalsIgnoreCase("update")) {
                  if (!player.hasPermission("leaderboards.update")) {
                     player.sendMessage("§cYou don't have permission to update leaderboards!");
                     return true;
                  } else if (args.length < 2) {
                     player.sendMessage("§cUsage: /leaderboard update <type|*>");
                     return true;
                  } else {
                     if (args[1].equals("*")) {
                        player.sendMessage("§aUpdating all leaderboards...");
                        manager.updateAllLeaderboards(() -> {
                           player.sendMessage("§aAll leaderboards have been updated!");
                        });
                     } else if (manager.exists(args[1])) {
                        type = args[1];
                        player.sendMessage("§aUpdating leaderboard: " + type);
                        manager.updateLeaderboard(type, () -> {
                           player.sendMessage("§aLeaderboard has been updated!");
                        });
                     } else {
                        player.sendMessage("§cLeaderboard type not found!");
                     }

                     return true;
                  }
               } else {
                  type = args[0].toLowerCase();
                  if (!manager.exists(type)) {
                     player.sendMessage("§cLeaderboard type not found!");
                     return true;
                  } else {
                     this.openLeaderboardGUI(player, type);
                     return true;
                  }
               }
            }
         }
      } else {
         sender.sendMessage("§cThis command can only be used by players!");
         return true;
      }
   }

   public void openLeaderboardGUI(Player player, String type, int page) {
      LeaderboardManager manager = this.plugin.getLeaderboardManager();
      int guiSize = this.plugin.getConfig().getInt("gui.size", 54);
      String title = this.plugin.getConfig().getString("gui.title", "Leaderboards - {type}").replace("{type}", manager.getTitle(type));
      Inventory gui = Bukkit.createInventory(new LeaderboardHolder(), guiSize, title + " - Page " + (page + 1));
      List<Entry<String, Double>> allPlayers = manager.getTopPlayers(type, Integer.MAX_VALUE);
      int itemsPerPage = 45;
      int totalPages = Math.max(1, (int)Math.ceil((double)allPlayers.size() / (double)itemsPerPage));
      int startIndex = page * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, allPlayers.size());
      List<Entry<String, Double>> pageEntries = allPlayers.subList(startIndex, endIndex);
      int slot = 0;

      ItemStack skull;
      for(Iterator var15 = pageEntries.iterator(); var15.hasNext(); gui.setItem(slot++, skull)) {
         Entry<String, Double> entry = (Entry)var15.next();
         skull = new ItemStack(Material.PLAYER_HEAD);
         SkullMeta meta = (SkullMeta)skull.getItemMeta();
         if (meta != null) {
            // Use player name only to avoid blocking API calls
            // Only set owner if player is online to avoid network lookups
            Player onlinePlayer = Bukkit.getPlayerExact((String)entry.getKey());
            if (onlinePlayer != null) {
               meta.setOwningPlayer(onlinePlayer);
            }
            
            meta.setDisplayName("§6#" + (startIndex + slot + 1) + " §f" + (String)entry.getKey());
            List<String> lore = new ArrayList();
            String format = this.plugin.getConfig().getString("leaderboards." + type + ".format", "{position}. {player}: {value}");
            format = format.replace("{position}", String.valueOf(startIndex + slot + 1)).replace("{player}", (CharSequence)entry.getKey()).replace("{value}", String.valueOf(entry.getValue()));
            lore.add("§7" + format);
            meta.setLore(lore);
            skull.setItemMeta(meta);
         }
      }

      ItemStack nextButton;
      ItemMeta nextMeta;
      if (page > 0) {
         nextButton = new ItemStack(Material.valueOf(this.plugin.getConfig().getString("gui.navigation.previous-page.item", "ARROW")));
         nextMeta = nextButton.getItemMeta();
         if (nextMeta != null) {
            nextMeta.setDisplayName(this.plugin.getConfig().getString("gui.navigation.previous-page.name", "§aPrevious Page"));
            nextButton.setItemMeta(nextMeta);
         }

         gui.setItem(this.plugin.getConfig().getInt("gui.navigation.previous-page.slot", 48), nextButton);
      }

      if (page < totalPages - 1 && !pageEntries.isEmpty()) {
         nextButton = new ItemStack(Material.valueOf(this.plugin.getConfig().getString("gui.navigation.next-page.item", "ARROW")));
         nextMeta = nextButton.getItemMeta();
         if (nextMeta != null) {
            nextMeta.setDisplayName(this.plugin.getConfig().getString("gui.navigation.next-page.name", "§aNext Page"));
            nextButton.setItemMeta(nextMeta);
         }

         gui.setItem(this.plugin.getConfig().getInt("gui.navigation.next-page.slot", 50), nextButton);
      }

      player.openInventory(gui);
   }

   public void openLeaderboardGUI(Player player, String type) {
      this.openLeaderboardGUI(player, type, 0);
   }

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      List<String> completions = new ArrayList();
      if (args.length == 1) {
         completions.addAll(this.plugin.getLeaderboardManager().getTypes());
         if (sender.hasPermission("leaderboards.update")) {
            completions.add("update");
         }

         if (sender.hasPermission("leaderboards.reload")) {
            completions.add("reload");
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
         completions.addAll(this.plugin.getLeaderboardManager().getTypes());
         completions.add("*");
      }

      return (List)completions.stream().filter((s) -> {
         return s.toLowerCase().startsWith(args[args.length - 1].toLowerCase());
      }).collect(Collectors.toList());
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
      player.sendMessage("  §f" + String.join("§7, §f", this.plugin.getLeaderboardManager().getTypes()));
      player.sendMessage("§8§l§m-------------------------------------------------");
   }
}
