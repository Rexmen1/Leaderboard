package com.rex.leaderboards;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTextureCache {
    private final LeaderboardPlugin plugin;
    private final File cacheFile;
    private final Map<String, PlayerProfile> textureCache;
    private final Map<String, Long> lastUpdate;
    private FileConfiguration cacheConfig;

    // Cache settings from config
    private boolean cacheEnabled;
    private long cacheExpiry; // in milliseconds
    private int maxCacheSize;

    public PlayerTextureCache(LeaderboardPlugin plugin) {
        this.plugin = plugin;
        this.cacheFile = new File(plugin.getDataFolder(), "texture-cache.yml");
        this.textureCache = new ConcurrentHashMap<>();
        this.lastUpdate = new ConcurrentHashMap<>();

        loadConfig();
        loadCache();
    }

    private void loadConfig() {
        // Load texture cache settings from main config
        this.cacheEnabled = plugin.getConfig().getBoolean("texture-cache.enabled", true);
        this.cacheExpiry = plugin.getConfig().getLong("texture-cache.expiry-days", 7) * 24 * 60 * 60 * 1000L; // Convert
                                                                                                              // days to
                                                                                                              // milliseconds
        this.maxCacheSize = plugin.getConfig().getInt("texture-cache.max-size", 1000);

        plugin.getLogger().info("Texture cache: " + (cacheEnabled ? "enabled" : "disabled") +
                " | Expiry: " + (cacheExpiry / (24 * 60 * 60 * 1000L)) + " days" +
                " | Max size: " + maxCacheSize);
    }

    private void loadCache() {
        if (!cacheEnabled)
            return;

        if (!cacheFile.exists()) {
            try {
                cacheFile.getParentFile().mkdirs();
                cacheFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create texture cache file: " + e.getMessage());
                return;
            }
        }

        cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);

        // Load cached texture metadata only - actual textures will be loaded when
        // players come online
        if (cacheConfig.contains("textures")) {
            for (String playerName : cacheConfig.getConfigurationSection("textures").getKeys(false)) {
                try {
                    long timestamp = cacheConfig.getLong("textures." + playerName + ".timestamp", 0);

                    // Check if cache entry is not expired
                    if (System.currentTimeMillis() - timestamp < cacheExpiry) {
                        // Just record that we have this player cached
                        lastUpdate.put(playerName, timestamp);
                        debug("Registered cached player: " + playerName);
                    } else {
                        debug("Expired cache entry removed for: " + playerName);
                        cacheConfig.set("textures." + playerName, null);
                    }
                } catch (Exception e) {
                    debug("Failed to load cache entry for " + playerName + ": " + e.getMessage());
                    cacheConfig.set("textures." + playerName, null);
                }
            }
        }

        // Clean up cache if it's too large
        cleanupCache();
        saveCache();

        plugin.getLogger().info("Loaded metadata for " + lastUpdate.size()
                + " cached players (textures will load when players are online)");
    }

    public void cachePlayerTexture(Player player) {
        if (!cacheEnabled)
            return;

        String playerName = player.getName();
        UUID playerUUID = player.getUniqueId();

        try {
            // Get the player's profile with textures
            PlayerProfile profile = player.getPlayerProfile();

            // Cache the profile in memory
            textureCache.put(playerName, profile);
            lastUpdate.put(playerName, System.currentTimeMillis());

            // Save metadata to file (not the actual texture data)
            if (cacheConfig != null) {
                cacheConfig.set("textures." + playerName + ".uuid", playerUUID.toString());
                cacheConfig.set("textures." + playerName + ".timestamp", System.currentTimeMillis());
                saveCache();

                debug("Cached texture for player: " + playerName);
            }

            // Clean up if cache is getting too large
            if (textureCache.size() > maxCacheSize) {
                cleanupCache();
            }

        } catch (Exception e) {
            debug("Failed to cache texture for " + playerName + ": " + e.getMessage());
        }
    }

    public void applyTextureToSkull(SkullMeta meta, String playerName) {
        try {
            // First try to get online player (highest priority)
            Player onlinePlayer = Bukkit.getPlayerExact(playerName);
            if (onlinePlayer != null) {
                meta.setOwningPlayer(onlinePlayer);
                // Also cache this player's texture for future use
                if (cacheEnabled) {
                    cachePlayerTexture(onlinePlayer);
                }
                debug("Applied online player texture: " + playerName);
                return;
            }

            // For offline players, only use cache if enabled and available
            if (cacheEnabled) {
                PlayerProfile cachedProfile = textureCache.get(playerName);
                if (cachedProfile != null) {
                    meta.setOwnerProfile(cachedProfile);
                    debug("Applied cached texture: " + playerName);
                    return;
                }

                // If we have metadata but no texture in memory, it means the player was cached
                // before but the server restarted - leave default head until they come online
                if (lastUpdate.containsKey(playerName)) {
                    debug("Player " + playerName + " is cached but texture not in memory (will load when online)");
                } else {
                    debug("No cached texture available for offline player: " + playerName + " (using default head)");
                }
            } else {
                debug("Texture cache disabled - using default head for offline player: " + playerName);
            }

        } catch (Exception e) {
            debug("Error applying texture to skull for " + playerName + ": " + e.getMessage());
        }
    }

    private void cleanupCache() {
        if (textureCache.size() <= maxCacheSize)
            return;

        debug("Cleaning up texture cache (current size: " + textureCache.size() + ")");

        // Remove oldest entries based on last update time
        lastUpdate.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .limit(textureCache.size() - maxCacheSize + 50) // Remove extra to avoid frequent cleanup
                .forEach(entry -> {
                    String playerName = entry.getKey();
                    textureCache.remove(playerName);
                    lastUpdate.remove(playerName);
                    if (cacheConfig != null) {
                        cacheConfig.set("textures." + playerName, null);
                    }
                    debug("Removed old cache entry: " + playerName);
                });

        saveCache();
    }

    public void clearExpiredEntries() {
        if (!cacheEnabled)
            return;

        long currentTime = System.currentTimeMillis();
        int removedCount = 0;

        // Create a copy of the key set to avoid concurrent modification
        for (String playerName : new HashMap<>(lastUpdate).keySet()) {
            long lastUpdateTime = lastUpdate.get(playerName);
            if (currentTime - lastUpdateTime > cacheExpiry) {
                textureCache.remove(playerName);
                lastUpdate.remove(playerName);
                if (cacheConfig != null) {
                    cacheConfig.set("textures." + playerName, null);
                }
                removedCount++;
                debug("Removed expired cache entry: " + playerName);
            }
        }

        if (removedCount > 0) {
            saveCache();
            plugin.getLogger().info("Cleaned up " + removedCount + " expired texture cache entries");
        }
    }

    private void saveCache() {
        if (!cacheEnabled || cacheConfig == null)
            return;

        try {
            cacheConfig.save(cacheFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save texture cache: " + e.getMessage());
        }
    }

    public void reload() {
        // Clear current cache
        textureCache.clear();
        lastUpdate.clear();

        // Reload settings and cache
        loadConfig();
        loadCache();
    }

    public void shutdown() {
        saveCache();
        textureCache.clear();
        lastUpdate.clear();
    }

    // Statistics methods
    public int getCacheSize() {
        return textureCache.size();
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public boolean hasTexture(String playerName) {
        return textureCache.containsKey(playerName);
    }

    private void debug(String message) {
        if (plugin.getConfig().getBoolean("debug.enabled", false) &&
                plugin.getConfig().getBoolean("debug.log-texture-cache", false)) {
            plugin.getLogger().info("[TextureCache] " + message);
        }
    }
}
