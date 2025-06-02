package com.rex.leaderboards;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class LeaderboardPlugin extends JavaPlugin {
   private LeaderboardManager leaderboardManager;
   private BukkitTask updateTask;

   public void onEnable() {
      this.saveDefaultConfig();
      this.leaderboardManager = new LeaderboardManager(this);
      LeaderboardCommand commandExecutor = new LeaderboardCommand(this);
      this.getCommand("leaderboard").setExecutor(commandExecutor);
      this.getCommand("leaderboard").setTabCompleter(commandExecutor);
      this.getServer().getPluginManager().registerEvents(new LeaderboardGUIListener(this), this);
      if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
         (new LeaderboardExpansion(this)).register();
         this.getLogger().info("Registered PlaceholderAPI expansion!");
      }

      int updateInterval = this.getConfig().getInt("update-interval", 30);
      this.startUpdateTask(updateInterval);
      this.getLogger().info("Leaderboards plugin enabled!");
   }

   public void onDisable() {
      if (this.updateTask != null) {
         this.updateTask.cancel();
      }

      if (this.leaderboardManager != null) {
         this.leaderboardManager.shutdown();
      }

      this.getLogger().info("Leaderboards plugin disabled!");
   }

   private void startUpdateTask(int intervalSeconds) {
      this.updateTask = this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
         this.leaderboardManager.updateAllLeaderboards((UpdateCallback)null);
      }, 20L, (long)intervalSeconds * 20L);
   }

   public LeaderboardManager getLeaderboardManager() {
      return this.leaderboardManager;
   }
}
