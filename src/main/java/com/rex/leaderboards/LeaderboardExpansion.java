package com.rex.leaderboards;

import java.util.List;
import java.util.Map.Entry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public class LeaderboardExpansion extends PlaceholderExpansion {
   private final LeaderboardPlugin plugin;

   public LeaderboardExpansion(LeaderboardPlugin plugin) {
      this.plugin = plugin;
   }

   public String getIdentifier() {
      return "leaderboard";
   }

   public String getAuthor() {
      return (String)this.plugin.getDescription().getAuthors().get(0);
   }

   public String getVersion() {
      return this.plugin.getDescription().getVersion();
   }

   public boolean persist() {
      return true;
   }

   public String onRequest(OfflinePlayer player, String params) {
      String[] args = params.split("_");
      if (args.length != 3) {
         return null;
      } else {
         String requestType = args[0];
         String type = args[1];

         int position;
         try {
            position = Integer.parseInt(args[2]) - 1;
         } catch (NumberFormatException var9) {
            return null;
         }

         if (!this.plugin.getLeaderboardManager().exists(type)) {
            return null;
         } else {
            List<Entry<String, Double>> topPlayers = this.plugin.getLeaderboardManager().getTopPlayers(type, position + 1);
            if (position >= topPlayers.size()) {
               return "N/A";
            } else {
               Entry<String, Double> entry = (Entry)topPlayers.get(position);
               if (requestType.equals("name")) {
                  return (String)entry.getKey();
               } else {
                  return requestType.equals("value") ? String.valueOf(entry.getValue()) : null;
               }
            }
         }
      }
   }
}
