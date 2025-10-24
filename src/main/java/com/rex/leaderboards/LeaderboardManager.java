package com.rex.leaderboards;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class LeaderboardManager {
   private final LeaderboardPlugin plugin;
   private final Map<String, String> leaderboardPlaceholders;
   private final Map<String, String> leaderboardTitles;
   private final Map<String, String> leaderboardFormats;
   private final File dataFile;
   private FileConfiguration data;
   private DatabaseManager databaseManager;
   private boolean useMySQL = false;
   private static final int BATCH_SIZE = 50;
   private static final long BATCH_DELAY = 20L;

   public LeaderboardManager(LeaderboardPlugin plugin) {
      this.plugin = plugin;
      this.leaderboardPlaceholders = new HashMap();
      this.leaderboardTitles = new HashMap();
      this.leaderboardFormats = new HashMap();
      this.dataFile = new File(plugin.getDataFolder(), "data.yml");
      
      // Check storage type and initialize accordingly
      String storageType = plugin.getConfig().getString("storage.type", "yaml");
      this.useMySQL = "mysql".equalsIgnoreCase(storageType);
      
      if (this.useMySQL) {
            this.databaseManager = new DatabaseManager(plugin);
            if (this.databaseManager.initialize()) {
                // Try to update schema first
                if (!this.databaseManager.updateSchema()) {
                    // If schema update fails, recreate the types table
                    plugin.getLogger().warning("Schema update failed, recreating leaderboard_types table...");
                    this.databaseManager.recreateTypesTable();
                }
                this.databaseManager.createTables();
                plugin.getLogger().info("Using MySQL storage for leaderboards");
            } else {
                plugin.getLogger().warning("Failed to initialize MySQL, falling back to YAML storage");
                this.useMySQL = false;
            }
        }
      
      this.loadLeaderboards();
      if (!this.useMySQL) {
         this.loadData();
      }
   }

   private void loadData() {
      if (!this.dataFile.exists()) {
         this.plugin.saveResource("data.yml", false);
      }

      this.data = YamlConfiguration.loadConfiguration(this.dataFile);
   }

   private void saveData() {
      try {
         this.data.save(this.dataFile);
      } catch (IOException var2) {
         this.plugin.getLogger().severe("Could not save data file!");
      }

   }

   private void loadLeaderboards() {
      ConfigurationSection section = this.plugin.getConfig().getConfigurationSection("leaderboards");
      if (section != null) {
         Iterator var2 = section.getKeys(false).iterator();

         while(var2.hasNext()) {
            String type = (String)var2.next();
            String placeholder = section.getString(type + ".placeholder");
            String title = section.getString(type + ".title", type);
            String format = section.getString(type + ".format", "%.0f");
            if (placeholder != null) {
               this.leaderboardPlaceholders.put(type, placeholder);
               this.leaderboardTitles.put(type, title);
               this.leaderboardFormats.put(type, format);
               
               // Register leaderboard type in MySQL if enabled
               if (this.useMySQL && this.databaseManager != null) {
                  this.databaseManager.registerLeaderboardType(type, title, placeholder, format);
               }
            }
         }

      }
   }

   private void debug(String message) {
      if (this.plugin.getConfig().getBoolean("debug.enabled", false) && this.plugin.getConfig().getBoolean("debug.log-updates", false)) {
         this.plugin.getLogger().info(message);
      }

   }

   public void updateLeaderboard(String type, UpdateCallback callback) {
      if (!this.leaderboardPlaceholders.containsKey(type)) {
         if (callback != null) {
            callback.onComplete();
         }
         return;
      }

      String placeholder = this.leaderboardPlaceholders.get(type);
      
      if (this.useMySQL && this.databaseManager != null) {
         // MySQL storage
         this.updateLeaderboardMySQL(type, placeholder, callback);
      } else {
         // YAML storage
         this.updateLeaderboardYAML(type, placeholder, callback);
      }
   }
   
   private void updateLeaderboardMySQL(String type, String placeholder, UpdateCallback callback) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      
      for (Player player : Bukkit.getOnlinePlayers()) {
         try {
            String result = PlaceholderAPI.setPlaceholders(player, placeholder);
            try {
               double value = Double.parseDouble(result);
               if (value > 0.0D) {
                  CompletableFuture<Void> future = this.databaseManager.updatePlayerData(
                     player.getUniqueId().toString(), 
                     player.getName(), 
                     type, 
                     value
                  ).thenAccept(success -> {
                     if (success) {
                        this.debug("Updated " + type + " for " + player.getName() + ": " + value);
                     } else {
                        this.debug("Failed to update " + type + " for " + player.getName());
                     }
                  });
                  futures.add(future);
               }
            } catch (NumberFormatException e) {
               this.debug("Failed to parse value for " + player.getName() + " with placeholder " + placeholder);
            }
         } catch (Exception e) {
            this.debug("Error processing stats for " + player.getName() + ": " + e.getMessage());
         }
      }
      
      // Wait for all updates to complete
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
         .thenRun(() -> {
            if (callback != null) {
               callback.onComplete();
            }
         });
   }
   
   private void updateLeaderboardYAML(String type, String placeholder, UpdateCallback callback) {
      ConfigurationSection typeSection = this.data.getConfigurationSection(type);
      if (typeSection == null) {
         typeSection = this.data.createSection(type);
      }

      ConfigurationSection finalSection = typeSection;
      Map<String, Double> existingValues = new HashMap();
      Iterator var7 = typeSection.getKeys(false).iterator();

      while(var7.hasNext()) {
         String playerName = (String)var7.next();
         if (!playerName.equals("last_update")) {
            existingValues.put(playerName, finalSection.getDouble(playerName));
         }
      }

      Map<String, Double> newValues = new HashMap();
      Iterator var16 = Bukkit.getOnlinePlayers().iterator();

      while(var16.hasNext()) {
         Player player = (Player)var16.next();

         String var10001;
         try {
            String result = PlaceholderAPI.setPlaceholders(player, placeholder);

            try {
               double value = Double.parseDouble(result);
               if (value > 0.0D) {
                  newValues.put(player.getName(), value);
                  this.debug("Updated " + type + " for " + player.getName() + ": " + value);
               }
            } catch (NumberFormatException var13) {
               var10001 = player.getName();
               this.debug("Failed to parse value for " + var10001 + " with placeholder " + placeholder);
            }
         } catch (Exception var14) {
            var10001 = player.getName();
            this.debug("Error processing stats for " + var10001 + ": " + var14.getMessage());
         }
      }

      var16 = newValues.entrySet().iterator();

      while(var16.hasNext()) {
         Entry<String, Double> entry = (Entry)var16.next();
         finalSection.set((String)entry.getKey(), entry.getValue());
      }

      finalSection.set("last_update", System.currentTimeMillis());
      this.saveData();
      if (callback != null) {
         callback.onComplete();
      }
   }

   public void updateAllLeaderboards(UpdateCallback callback) {
      List<String> types = new ArrayList(this.leaderboardPlaceholders.keySet());
      Iterator var3 = types.iterator();

      while(var3.hasNext()) {
         String type = (String)var3.next();
         this.updateLeaderboard(type, (UpdateCallback)null);
      }

      if (callback != null) {
         callback.onComplete();
      }

   }

   public List<Entry<String, Double>> getTopPlayers(String type, int limit) {
      if (this.useMySQL && this.databaseManager != null) {
         // MySQL storage - return synchronously for compatibility
         try {
            List<Map<String, Object>> topPlayersData = this.databaseManager.getTopPlayers(type, limit).get();
            return topPlayersData.stream()
               .map(playerData -> Map.entry((String) playerData.get("name"), (Double) playerData.get("value")))
               .collect(Collectors.toList());
         } catch (Exception e) {
            this.plugin.getLogger().severe("Failed to get top players from MySQL: " + e.getMessage());
            return new ArrayList<>();
         }
      } else {
         // YAML storage
         ConfigurationSection typeSection = this.data.getConfigurationSection(type);
         if (typeSection == null) {
            return new ArrayList();
         } else {
            Map<String, Double> values = new HashMap();
            Iterator var5 = typeSection.getKeys(false).iterator();

            while(var5.hasNext()) {
               String key = (String)var5.next();
               if (!key.equals("last_update")) {
                  values.put(key, typeSection.getDouble(key));
               }
            }

            return values.entrySet().stream().sorted(Entry.<String, Double>comparingByValue().reversed()).limit((long)limit).toList();
         }
      }
   }

   public long getLastUpdate(String type) {
      if (this.useMySQL && this.databaseManager != null) {
         // For MySQL, we don't track last update time in the same way
         // Return current time as MySQL data is always current
         return System.currentTimeMillis();
      } else {
         ConfigurationSection typeSection = this.data.getConfigurationSection(type);
         return typeSection == null ? 0L : typeSection.getLong("last_update", 0L);
      }
   }

   public String getTitle(String type) {
      return (String)this.leaderboardTitles.getOrDefault(type, type);
   }

   public boolean exists(String type) {
      return this.leaderboardPlaceholders.containsKey(type);
   }
   
   public String getFormat(String type) {
      return leaderboardFormats.getOrDefault(type, "%.0f");
   }
   
   public DatabaseManager getDatabaseManager() {
      return databaseManager;
   }

   public Set<String> getTypes() {
      return this.leaderboardPlaceholders.keySet();
   }

   public void reload() {
      Map<String, String> oldPlaceholders = new HashMap(this.leaderboardPlaceholders);
      new HashMap(this.leaderboardTitles);
      this.leaderboardPlaceholders.clear();
      this.leaderboardTitles.clear();
      this.loadLeaderboards();
      Set<String> removedTypes = new HashSet(oldPlaceholders.keySet());
      removedTypes.removeAll(this.leaderboardPlaceholders.keySet());
      if (!removedTypes.isEmpty()) {
         this.debug("Removed leaderboard types: " + String.join(", ", removedTypes));
      }

      Iterator var4 = this.leaderboardPlaceholders.keySet().iterator();

      while(var4.hasNext()) {
         String type = (String)var4.next();
         if (!oldPlaceholders.containsKey(type)) {
            this.debug("New leaderboard type added: " + type);
         } else if (!((String)oldPlaceholders.get(type)).equals(this.leaderboardPlaceholders.get(type))) {
            this.debug("Modified leaderboard placeholder: " + type);
         }
      }

      this.debug("Configuration reloaded - Leaderboard data preserved");
   }

   public void shutdown() {
      if (this.useMySQL && this.databaseManager != null) {
         this.databaseManager.close();
      } else {
         this.saveData();
      }
   }
}
