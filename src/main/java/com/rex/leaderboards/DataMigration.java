package com.rex.leaderboards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class DataMigration {
    
    private final LeaderboardPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Logger logger;
    
    public DataMigration(LeaderboardPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.logger = plugin.getLogger();
    }
    
    /**
     * Migrate data from YAML to MySQL
     * @return CompletableFuture<Boolean> indicating success or failure
     */
    public CompletableFuture<Boolean> migrateFromYaml() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File dataFile = new File(plugin.getDataFolder(), "data.yml");
                if (!dataFile.exists()) {
                    logger.info("No data.yml file found, nothing to migrate.");
                    return true;
                }
                
                FileConfiguration yamlData = YamlConfiguration.loadConfiguration(dataFile);
                Set<String> leaderboardTypes = yamlData.getKeys(false);
                
                if (leaderboardTypes.isEmpty()) {
                    logger.info("No leaderboard data found in data.yml, nothing to migrate.");
                    return true;
                }
                
                logger.info("Starting migration of " + leaderboardTypes.size() + " leaderboard types from YAML to MySQL...");
                
                int totalPlayers = 0;
                int migratedPlayers = 0;
                
                for (String leaderboardType : leaderboardTypes) {
                    ConfigurationSection typeSection = yamlData.getConfigurationSection(leaderboardType);
                    if (typeSection == null) continue;
                    
                    logger.info("Migrating leaderboard type: " + leaderboardType);
                    
                    Set<String> playerNames = typeSection.getKeys(false);
                    for (String playerName : playerNames) {
                        // Skip the last_update field
                        if ("last_update".equals(playerName)) continue;
                        
                        double value = typeSection.getDouble(playerName, 0.0);
                        if (value > 0) {
                            totalPlayers++;
                            
                            // For migration, we'll use a placeholder UUID since we only have player names
                            // In a real scenario, you might want to look up UUIDs from Mojang API
                            String placeholderUuid = generatePlaceholderUuid(playerName);
                            
                            try {
                                boolean success = databaseManager.updatePlayerData(
                                    placeholderUuid, 
                                    playerName, 
                                    leaderboardType, 
                                    value
                                ).get();
                                
                                if (success) {
                                    migratedPlayers++;
                                    logger.fine("Migrated " + playerName + " (" + leaderboardType + "): " + value);
                                } else {
                                    logger.warning("Failed to migrate " + playerName + " (" + leaderboardType + ")");
                                }
                            } catch (Exception e) {
                                logger.severe("Error migrating " + playerName + " (" + leaderboardType + "): " + e.getMessage());
                            }
                        }
                    }
                }
                
                logger.info("Migration completed! Migrated " + migratedPlayers + "/" + totalPlayers + " player records.");
                
                if (migratedPlayers == totalPlayers) {
                    // Create backup of original data file
                    createBackup(dataFile);
                    return true;
                } else {
                    logger.warning("Migration completed with errors. Original data.yml preserved.");
                    return false;
                }
                
            } catch (Exception e) {
                logger.severe("Migration failed: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }
    
    /**
     * Generate a placeholder UUID for migration purposes
     * This is not a real UUID but serves as a unique identifier for migration
     */
    private String generatePlaceholderUuid(String playerName) {
        // Create a deterministic UUID-like string based on player name
        // This ensures consistency if migration is run multiple times
        int hash = playerName.hashCode();
        return String.format("00000000-0000-0000-0000-%012d", Math.abs(hash));
    }
    
    /**
     * Create a backup of the original data file
     */
    private void createBackup(File originalFile) {
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            File backupFile = new File(backupDir, "data_backup_" + timestamp + ".yml");
            
            // Copy the file
            java.nio.file.Files.copy(originalFile.toPath(), backupFile.toPath());
            
            logger.info("Created backup of original data.yml at: " + backupFile.getPath());
            
        } catch (Exception e) {
            logger.warning("Failed to create backup of data.yml: " + e.getMessage());
        }
    }
    
    /**
     * Verify migration by comparing record counts
     */
    public CompletableFuture<Boolean> verifyMigration() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File dataFile = new File(plugin.getDataFolder(), "data.yml");
                if (!dataFile.exists()) {
                    return true; // Nothing to verify
                }
                
                FileConfiguration yamlData = YamlConfiguration.loadConfiguration(dataFile);
                Set<String> leaderboardTypes = yamlData.getKeys(false);
                
                boolean allVerified = true;
                
                for (String leaderboardType : leaderboardTypes) {
                    ConfigurationSection typeSection = yamlData.getConfigurationSection(leaderboardType);
                    if (typeSection == null) continue;
                    
                    // Count YAML records (excluding last_update)
                    long yamlCount = typeSection.getKeys(false).stream()
                        .filter(key -> !"last_update".equals(key))
                        .count();
                    
                    // Count MySQL records
                    try {
                        long mysqlCount = databaseManager.getTopPlayers(leaderboardType, Integer.MAX_VALUE)
                            .get().size();
                        
                        if (yamlCount == mysqlCount) {
                            logger.info("Verification passed for " + leaderboardType + ": " + yamlCount + " records");
                        } else {
                            logger.warning("Verification failed for " + leaderboardType + 
                                ": YAML has " + yamlCount + " records, MySQL has " + mysqlCount + " records");
                            allVerified = false;
                        }
                    } catch (Exception e) {
                        logger.severe("Failed to verify " + leaderboardType + ": " + e.getMessage());
                        allVerified = false;
                    }
                }
                
                return allVerified;
                
            } catch (Exception e) {
                logger.severe("Migration verification failed: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get migration statistics
     */
    public CompletableFuture<Map<String, Object>> getMigrationStats() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> stats = new HashMap<>();
            
            try {
                File dataFile = new File(plugin.getDataFolder(), "data.yml");
                if (!dataFile.exists()) {
                    stats.put("yamlExists", false);
                    stats.put("yamlRecords", 0);
                    stats.put("mysqlRecords", 0);
                    return stats;
                }
                
                FileConfiguration yamlData = YamlConfiguration.loadConfiguration(dataFile);
                Set<String> leaderboardTypes = yamlData.getKeys(false);
                
                int totalYamlRecords = 0;
                int totalMysqlRecords = 0;
                
                for (String leaderboardType : leaderboardTypes) {
                    ConfigurationSection typeSection = yamlData.getConfigurationSection(leaderboardType);
                    if (typeSection == null) continue;
                    
                    // Count YAML records
                    int yamlCount = (int) typeSection.getKeys(false).stream()
                        .filter(key -> !"last_update".equals(key))
                        .count();
                    totalYamlRecords += yamlCount;
                    
                    // Count MySQL records
                    try {
                        int mysqlCount = databaseManager.getTopPlayers(leaderboardType, Integer.MAX_VALUE)
                            .get().size();
                        totalMysqlRecords += mysqlCount;
                    } catch (Exception e) {
                        logger.warning("Failed to get MySQL count for " + leaderboardType);
                    }
                }
                
                stats.put("yamlExists", true);
                stats.put("leaderboardTypes", leaderboardTypes.size());
                stats.put("yamlRecords", totalYamlRecords);
                stats.put("mysqlRecords", totalMysqlRecords);
                stats.put("migrationNeeded", totalYamlRecords > totalMysqlRecords);
                
            } catch (Exception e) {
                logger.severe("Failed to get migration stats: " + e.getMessage());
                stats.put("error", e.getMessage());
            }
            
            return stats;
        });
    }
}