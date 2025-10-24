package com.rex.leaderboards;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
         Player player = (Player) sender;
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
                  this.plugin.getTextureCache().reload();
                  player.sendMessage("§aConfiguration reloaded successfully!");
                  return true;
               }
            } else if (args[0].equalsIgnoreCase("cache")) {
               if (!player.hasPermission("leaderboards.reload")) {
                  player.sendMessage("§cYou don't have permission to view cache info!");
                  return true;
               } else {
                  PlayerTextureCache cache = this.plugin.getTextureCache();
                  player.sendMessage("§6§lTexture Cache Info:");
                  player.sendMessage("§7Status: " + (cache.isCacheEnabled() ? "§aEnabled" : "§cDisabled"));
                  player.sendMessage("§7Cached textures: §f" + cache.getCacheSize());
                  if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                     cache.clearExpiredEntries();
                     player.sendMessage("§aExpired cache entries cleared!");
                  }
                  return true;
               }
            } else if (args[0].equalsIgnoreCase("migrate")) {
               if (!player.hasPermission("leaderboards.admin")) {
                  player.sendMessage("§cYou don't have permission to use migration commands!");
                  return true;
               } else {
                  this.handleMigrationCommand(player, args);
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
      String title = this.plugin.getConfig().getString("gui.title", "Leaderboards - {type}").replace("{type}",
            manager.getTitle(type));
      Inventory gui = Bukkit.createInventory(new LeaderboardHolder(), guiSize, title + " - Page " + (page + 1));
      List<Entry<String, Double>> allPlayers = manager.getTopPlayers(type, Integer.MAX_VALUE);
      int itemsPerPage = 45;
      int totalPages = Math.max(1, (int) Math.ceil((double) allPlayers.size() / (double) itemsPerPage));
      int startIndex = page * itemsPerPage;
      int endIndex = Math.min(startIndex + itemsPerPage, allPlayers.size());
      List<Entry<String, Double>> pageEntries = allPlayers.subList(startIndex, endIndex);
      int slot = 0;

      ItemStack skull;
      for (Iterator var15 = pageEntries.iterator(); var15.hasNext(); gui.setItem(slot++, skull)) {
         Entry<String, Double> entry = (Entry) var15.next();
         skull = new ItemStack(Material.PLAYER_HEAD);
         SkullMeta meta = (SkullMeta) skull.getItemMeta();
         if (meta != null) {
            // Use texture cache for both online and offline players
            this.plugin.getTextureCache().applyTextureToSkull(meta, (String) entry.getKey());

            meta.setDisplayName("§6#" + (startIndex + slot + 1) + " §f" + (String) entry.getKey());
            List<String> lore = new ArrayList<>();
            String format = this.plugin.getConfig().getString("leaderboards." + type + ".format",
                  "{position}. {player}: {value}");
            format = format.replace("{position}", String.valueOf(startIndex + slot + 1))
                  .replace("{player}", (CharSequence) entry.getKey())
                  .replace("{value}", String.valueOf(entry.getValue()));
            lore.add("§7" + format);
            meta.setLore(lore);
            skull.setItemMeta(meta);
         }
      }

      ItemStack nextButton;
      ItemMeta nextMeta;
      if (page > 0) {
         nextButton = new ItemStack(
               Material.valueOf(this.plugin.getConfig().getString("gui.navigation.previous-page.item", "ARROW")));
         nextMeta = nextButton.getItemMeta();
         if (nextMeta != null) {
            nextMeta.setDisplayName(
                  this.plugin.getConfig().getString("gui.navigation.previous-page.name", "§aPrevious Page"));
            nextButton.setItemMeta(nextMeta);
         }

         gui.setItem(this.plugin.getConfig().getInt("gui.navigation.previous-page.slot", 48), nextButton);
      }

      if (page < totalPages - 1 && !pageEntries.isEmpty()) {
         nextButton = new ItemStack(
               Material.valueOf(this.plugin.getConfig().getString("gui.navigation.next-page.item", "ARROW")));
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
      List<String> completions = new ArrayList<>();
      if (args.length == 1) {
         completions.addAll(this.plugin.getLeaderboardManager().getTypes());
         if (sender.hasPermission("leaderboards.update")) {
            completions.add("update");
         }
         if (sender.hasPermission("leaderboards.reload")) {
            completions.add("reload");
            completions.add("cache");
         }
         if (sender.hasPermission("leaderboards.admin")) {
            completions.add("migrate");
         }
      } else if (args.length == 2 && args[0].equalsIgnoreCase("update")) {
         completions.addAll(this.plugin.getLeaderboardManager().getTypes());
         completions.add("*");
      } else if (args.length == 2 && args[0].equalsIgnoreCase("migrate")) {
         completions.add("start");
         completions.add("status");
         completions.add("verify");
      }

      return (List) completions.stream().filter((s) -> {
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
         player.sendMessage("  §f/leaderboard cache §7- View texture cache info");
      }
      if (player.hasPermission("leaderboards.admin")) {
         player.sendMessage("  §f/leaderboard migrate status §7- Check migration status");
         player.sendMessage("  §f/leaderboard migrate start §7- Start YAML to MySQL migration");
         player.sendMessage("  §f/leaderboard migrate verify §7- Verify migration integrity");
      }

      player.sendMessage("");
      player.sendMessage("§6Available Types:");
      player.sendMessage("  §f" + String.join("§7, §f", this.plugin.getLeaderboardManager().getTypes()));
      player.sendMessage("§8§l§m-------------------------------------------------");
   }

   private void handleMigrationCommand(Player player, String[] args) {
      LeaderboardManager manager = this.plugin.getLeaderboardManager();
      
      // Check if MySQL is enabled
      if (!this.plugin.getConfig().getString("storage.type", "yaml").equalsIgnoreCase("mysql")) {
         player.sendMessage("§cMigration commands are only available when MySQL storage is enabled!");
         player.sendMessage("§7Please set storage.type to 'mysql' in config.yml and restart the server.");
         return;
      }
      
      if (args.length < 2) {
         player.sendMessage("§cUsage: /leaderboard migrate <status|start|verify>");
         return;
      }
      
      String subCommand = args[1].toLowerCase();
      
      switch (subCommand) {
         case "status":
            this.handleMigrationStatus(player, manager);
            break;
         case "start":
            this.handleMigrationStart(player, manager);
            break;
         case "verify":
            this.handleMigrationVerify(player, manager);
            break;
         default:
            player.sendMessage("§cUnknown migration command: " + subCommand);
            player.sendMessage("§7Available commands: status, start, verify");
            break;
      }
   }
   
   private void handleMigrationStatus(Player player, LeaderboardManager manager) {
      player.sendMessage("§6§lMigration Status");
      player.sendMessage("§7Checking migration status...");
      
      DataMigration migration = new DataMigration(this.plugin, manager.getDatabaseManager());
      migration.getMigrationStats().thenAccept(stats -> {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (stats.containsKey("error")) {
               player.sendMessage("§cError checking migration status: " + stats.get("error"));
               return;
            }
            
            boolean yamlExists = (Boolean) stats.get("yamlExists");
            if (!yamlExists) {
               player.sendMessage("§aNo YAML data file found - nothing to migrate.");
               return;
            }
            
            int leaderboardTypes = (Integer) stats.get("leaderboardTypes");
            int yamlRecords = (Integer) stats.get("yamlRecords");
            int mysqlRecords = (Integer) stats.get("mysqlRecords");
            boolean migrationNeeded = (Boolean) stats.get("migrationNeeded");
            
            player.sendMessage("§7Leaderboard types: §f" + leaderboardTypes);
            player.sendMessage("§7YAML records: §f" + yamlRecords);
            player.sendMessage("§7MySQL records: §f" + mysqlRecords);
            
            if (migrationNeeded) {
               player.sendMessage("§e⚠ Migration needed! Use '/leaderboard migrate start' to begin.");
            } else {
               player.sendMessage("§a✓ Migration appears complete.");
            }
         });
      });
   }
   
   private void handleMigrationStart(Player player, LeaderboardManager manager) {
      player.sendMessage("§6§lStarting Migration");
      player.sendMessage("§7Migrating YAML data to MySQL...");
      player.sendMessage("§c⚠ This process may take some time for large datasets.");
      
      DataMigration migration = new DataMigration(this.plugin, manager.getDatabaseManager());
      migration.migrateFromYaml().thenAccept(success -> {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (success) {
               player.sendMessage("§a✓ Migration completed successfully!");
               player.sendMessage("§7Your YAML data has been backed up in the 'backups' folder.");
               player.sendMessage("§7Use '/leaderboard migrate verify' to verify the migration.");
            } else {
               player.sendMessage("§c✗ Migration failed or completed with errors.");
               player.sendMessage("§7Check the console for detailed error messages.");
               player.sendMessage("§7Your original data.yml file has been preserved.");
            }
         });
      });
   }
   
   private void handleMigrationVerify(Player player, LeaderboardManager manager) {
      player.sendMessage("§6§lVerifying Migration");
      player.sendMessage("§7Verifying data integrity...");
      
      DataMigration migration = new DataMigration(this.plugin, manager.getDatabaseManager());
      migration.verifyMigration().thenAccept(verified -> {
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (verified) {
               player.sendMessage("§a✓ Migration verification passed!");
               player.sendMessage("§7All data has been successfully migrated to MySQL.");
            } else {
               player.sendMessage("§c✗ Migration verification failed!");
               player.sendMessage("§7Some data may not have been migrated correctly.");
               player.sendMessage("§7Check the console for detailed information.");
            }
         });
      });
   }
}
