package com.rex.leaderboards;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class LeaderboardPlugin extends JavaPlugin {
   private LeaderboardManager leaderboardManager;
   private PlayerTextureCache textureCache;
   private BukkitTask updateTask;
   private BukkitTask cacheCleanupTask;

   public void onEnable() {
      this.saveDefaultConfig();

      // Initialize texture cache
      this.textureCache = new PlayerTextureCache(this);

      this.leaderboardManager = new LeaderboardManager(this);
      LeaderboardCommand commandExecutor = new LeaderboardCommand(this);
      this.getCommand("leaderboard").setExecutor(commandExecutor);
      this.getCommand("leaderboard").setTabCompleter(commandExecutor);

      // Register event listeners
      this.getServer().getPluginManager().registerEvents(new LeaderboardGUIListener(this), this);
      this.getServer().getPluginManager().registerEvents(new PlayerEventListener(this), this);

      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         (new LeaderboardExpansion(this)).register();
         this.getLogger().info("Registered PlaceholderAPI expansion!");
      }

      int updateInterval = this.getConfig().getInt("update-interval", 30);
      this.startUpdateTask(updateInterval);
      this.startCacheCleanupTask();
      this.getLogger().info("Leaderboards plugin enabled!");
   }

   public void onDisable() {
      if (this.updateTask != null) {
         this.updateTask.cancel();
      }

      if (this.cacheCleanupTask != null) {
         this.cacheCleanupTask.cancel();
      }

      if (this.leaderboardManager != null) {
         this.leaderboardManager.shutdown();
      }

      if (this.textureCache != null) {
         this.textureCache.shutdown();
      }

      this.getLogger().info("Leaderboards plugin disabled!");
   }

   private void startUpdateTask(int intervalSeconds) {
      this.updateTask = this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
         this.leaderboardManager.updateAllLeaderboards((UpdateCallback) null);
      }, 20L, (long) intervalSeconds * 20L);
   }

   private void startCacheCleanupTask() {
      // Run cache cleanup every hour
      this.cacheCleanupTask = this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
         if (this.textureCache != null) {
            this.textureCache.clearExpiredEntries();
         }
      }, 72000L, 72000L); // 72000 ticks = 1 hour
   }

   public LeaderboardManager getLeaderboardManager() {
      return this.leaderboardManager;
   }

   public PlayerTextureCache getTextureCache() {
      return this.textureCache;
   }
}
