package com.rex.leaderboards;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

public class LeaderboardGUIListener implements Listener {
   private final LeaderboardPlugin plugin;

   public LeaderboardGUIListener(LeaderboardPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInventoryClick(InventoryClickEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof LeaderboardHolder) {
         event.setCancelled(true);
         HumanEntity var3 = event.getWhoClicked();
         if (!(var3 instanceof Player)) {
            return;
         }

         Player player = (Player)var3;
         if (event.getClickedInventory() != null && event.getClickedInventory().getHolder() instanceof LeaderboardHolder) {
            String title = event.getView().getTitle();
            
            String[] titleParts = title.split(" - ");
            if (titleParts.length < 2) {
               return;
            }

            String displayedType;
            String pageStr;
            
            if (titleParts.length == 3) {
               // Format: "Leaderboards - {type} - Page X"
               displayedType = titleParts[1];
               pageStr = titleParts[2];
            } else if (titleParts.length == 2) {
               // Format: "{type} - Page X" (when gui title template doesn't include "Leaderboards")
               displayedType = titleParts[0];
               pageStr = titleParts[1];
            } else {
               return;
            }
            
            int currentPage;
            try {
               currentPage = Integer.parseInt(pageStr.replace("Page ", "")) - 1;
            } catch (NumberFormatException e) {
               return;
            }
            
            // Find the actual leaderboard type from configured types
            String actualType = null;
            for (String configType : this.plugin.getLeaderboardManager().getTypes()) {
               String configTitle = this.plugin.getLeaderboardManager().getTitle(configType);
               if (configTitle.equals(displayedType) || configType.equals(displayedType)) {
                  actualType = configType;
                  break;
               }
            }
            
            if (actualType == null) {
               return;
            }
            
            int clickedSlot = event.getSlot();
            int prevSlot = this.plugin.getConfig().getInt("gui.navigation.previous-page.slot", 48);
            int nextSlot = this.plugin.getConfig().getInt("gui.navigation.next-page.slot", 50);
            
            // Get command executor safely
            Object executor = this.plugin.getCommand("leaderboard").getExecutor();
            if (!(executor instanceof LeaderboardCommand)) {
               return;
            }
            LeaderboardCommand commandExecutor = (LeaderboardCommand) executor;
            
            if (clickedSlot == prevSlot && currentPage > 0) {
               commandExecutor.openLeaderboardGUI(player, actualType, currentPage - 1);
            } else if (clickedSlot == nextSlot) {
               // Check if there are more pages available
               int totalPlayers = this.plugin.getLeaderboardManager().getTopPlayers(actualType, Integer.MAX_VALUE).size();
               int itemsPerPage = 45;
               int totalPages = Math.max(1, (int)Math.ceil((double)totalPlayers / (double)itemsPerPage));
               
               if (currentPage < totalPages - 1) {
                  commandExecutor.openLeaderboardGUI(player, actualType, currentPage + 1);
               }
            }
         }
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInventoryDrag(InventoryDragEvent event) {
      if (event.getView().getTopInventory().getHolder() instanceof LeaderboardHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInventoryMoveItem(InventoryMoveItemEvent event) {
      if (event.getSource().getHolder() instanceof LeaderboardHolder || event.getDestination().getHolder() instanceof LeaderboardHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInventoryPickupItem(InventoryPickupItemEvent event) {
      if (event.getInventory().getHolder() instanceof LeaderboardHolder) {
         event.setCancelled(true);
      }

   }

   @EventHandler(
      priority = EventPriority.HIGHEST,
      ignoreCancelled = true
   )
   public void onInventoryInteract(InventoryInteractEvent event) {
      if (event.getInventory().getHolder() instanceof LeaderboardHolder) {
         event.setCancelled(true);
      }

   }
}
