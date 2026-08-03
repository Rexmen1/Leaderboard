package com.rex.leaderboards;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.file.FileConfiguration;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class DatabaseManager {
    
    private final LeaderboardPlugin plugin;
    private final Logger logger;
    private HikariDataSource dataSource;
    private ExecutorService dbExecutor;
    private String tablePrefix;
    private boolean isConnected = false;
    
    public DatabaseManager(LeaderboardPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }
    
    /**
     * Initialize the database connection using HikariCP
     */
    public boolean initialize() {
        try {
            FileConfiguration config = plugin.getConfig();
            
            // Check if MySQL is enabled
            if (!"mysql".equalsIgnoreCase(config.getString("storage.type", "yaml"))) {
                logger.info("MySQL storage is disabled. Using YAML storage.");
                return false;
            }
            
            String host = config.getString("storage.mysql.host", "localhost");
            int port = config.getInt("storage.mysql.port", 3306);
            String database = config.getString("storage.mysql.database", "leaderboards");
            String username = config.getString("storage.mysql.username", "root");
            String password = config.getString("storage.mysql.password", "password");
            this.tablePrefix = config.getString("storage.mysql.table-prefix", "lb_");
            
            // HikariCP configuration
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            
            // Connection pool settings
            hikariConfig.setMaximumPoolSize(config.getInt("storage.mysql.pool.maximum-pool-size", 10));
            hikariConfig.setMinimumIdle(config.getInt("storage.mysql.pool.minimum-idle", 2));
            hikariConfig.setConnectionTimeout(config.getLong("storage.mysql.pool.connection-timeout", 30000));
            hikariConfig.setIdleTimeout(config.getLong("storage.mysql.pool.idle-timeout", 600000));
            hikariConfig.setMaxLifetime(config.getLong("storage.mysql.pool.max-lifetime", 1800000));
            
            // Connection validation and keepalive
            hikariConfig.setKeepaliveTime(300000); // 5 minutes - sends a keepalive query
            hikariConfig.setValidationTimeout(5000); // 5 seconds to validate a connection
            hikariConfig.setConnectionTestQuery("SELECT 1");
            
            // Additional settings for better performance
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");
            
            this.dataSource = new HikariDataSource(hikariConfig);
            
            // Create dedicated thread pool for database operations
            int poolSize = config.getInt("storage.mysql.pool.maximum-pool-size", 10);
            this.dbExecutor = Executors.newFixedThreadPool(Math.min(poolSize, 4), r -> {
                Thread t = new Thread(r, "Leaderboards-DB");
                t.setDaemon(true);
                return t;
            });
            
            // Test connection
            try (Connection connection = dataSource.getConnection()) {
                logger.info("Successfully connected to MySQL database!");
                this.isConnected = true;
                return true;
            }
            
        } catch (SQLException e) {
            logger.severe("Failed to connect to MySQL database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Update database schema if needed
     */
    public boolean updateSchema() {
        if (!isConnected) return false;
        
        try (Connection connection = getConnection()) {
            // Check if the table exists first
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tables = metaData.getTables(null, null, tablePrefix + "leaderboard_types", null)) {
                if (tables.next()) {
                    // Table exists, check and update all column sizes
                    boolean needsUpdate = false;
                    
                    // Check type_name column
                    try (ResultSet columns = metaData.getColumns(null, null, tablePrefix + "leaderboard_types", "type_name")) {
                        if (columns.next()) {
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            logger.info("Current type_name column size: " + columnSize);
                            if (columnSize < 100) {
                                needsUpdate = true;
                            }
                        }
                    }
                    
                    // Check display_title column
                    try (ResultSet columns = metaData.getColumns(null, null, tablePrefix + "leaderboard_types", "display_title")) {
                        if (columns.next()) {
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            logger.info("Current display_title column size: " + columnSize);
                            if (columnSize < 255) {
                                needsUpdate = true;
                            }
                        }
                    }
                    
                    // Check placeholder column
                    try (ResultSet columns = metaData.getColumns(null, null, tablePrefix + "leaderboard_types", "placeholder")) {
                        if (columns.next()) {
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            logger.info("Current placeholder column size: " + columnSize);
                            if (columnSize < 500) {
                                needsUpdate = true;
                            }
                        }
                    }
                    
                    // Check format column
                    try (ResultSet columns = metaData.getColumns(null, null, tablePrefix + "leaderboard_types", "format")) {
                        if (columns.next()) {
                            int columnSize = columns.getInt("COLUMN_SIZE");
                            logger.info("Current format column size: " + columnSize);
                            if (columnSize < 255) {
                                needsUpdate = true;
                            }
                        }
                    }
                    
                    if (needsUpdate) {
                        // Update all columns at once
                        String alterTable = "ALTER TABLE " + tablePrefix + "leaderboard_types " +
                                "MODIFY COLUMN type_name VARCHAR(100) NOT NULL, " +
                                "MODIFY COLUMN display_title VARCHAR(255) NOT NULL, " +
                                "MODIFY COLUMN placeholder VARCHAR(500) NOT NULL, " +
                                "MODIFY COLUMN format VARCHAR(255) NOT NULL";
                        
                        try (PreparedStatement stmt = connection.prepareStatement(alterTable)) {
                            stmt.executeUpdate();
                            logger.info("Database schema updated: all columns expanded to larger sizes");
                            return true;
                        }
                    } else {
                        logger.info("Database schema is up to date");
                        return true;
                    }
                } else {
                    logger.info("Table doesn't exist yet, will be created by createTables()");
                    return true;
                }
            }
            
        } catch (SQLException e) {
            logger.warning("Failed to update database schema: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Recreate the leaderboard_types table with correct schema
     */
    public boolean recreateTypesTable() {
        if (!isConnected) return false;
        
        try (Connection connection = getConnection()) {
            // Drop the existing table
            String dropTable = "DROP TABLE IF EXISTS " + tablePrefix + "leaderboard_types";
            try (PreparedStatement stmt = connection.prepareStatement(dropTable)) {
                stmt.executeUpdate();
                logger.info("Dropped existing leaderboard_types table");
            }
            
            // Recreate with correct schema
            String createTypesTable = "CREATE TABLE " + tablePrefix + "leaderboard_types (" +
                    "type_name VARCHAR(100) PRIMARY KEY, " +
                    "display_title VARCHAR(255) NOT NULL, " +
                    "placeholder VARCHAR(500) NOT NULL, " +
                    "format VARCHAR(255) NOT NULL DEFAULT '%.0f', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            
            try (PreparedStatement stmt = connection.prepareStatement(createTypesTable)) {
                stmt.executeUpdate();
                logger.info("Recreated leaderboard_types table with correct schema");
                return true;
            }
            
        } catch (SQLException e) {
            logger.severe("Failed to recreate leaderboard_types table: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Create the necessary tables for leaderboard data
     */
    public boolean createTables() {
        if (!isConnected) return false;
        
        try (Connection connection = getConnection()) {
            // Create leaderboard_data table
            String createDataTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "leaderboard_data (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "player_uuid VARCHAR(36) NOT NULL, " +
                    "player_name VARCHAR(16) NOT NULL, " +
                    "leaderboard_type VARCHAR(50) NOT NULL, " +
                    "value DOUBLE NOT NULL DEFAULT 0, " +
                    "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "INDEX idx_type_value (leaderboard_type, value DESC), " +
                    "INDEX idx_player_type (player_uuid, leaderboard_type), " +
                    "UNIQUE KEY unique_player_type (player_uuid, leaderboard_type)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            
            // Create leaderboard_types table for metadata
            String createTypesTable = "CREATE TABLE IF NOT EXISTS " + tablePrefix + "leaderboard_types (" +
                    "type_name VARCHAR(100) PRIMARY KEY, " +
                    "display_title VARCHAR(255) NOT NULL, " +
                    "placeholder VARCHAR(500) NOT NULL, " +
                    "format VARCHAR(255) NOT NULL DEFAULT '%.0f', " +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
            
            try (PreparedStatement stmt1 = connection.prepareStatement(createDataTable);
                 PreparedStatement stmt2 = connection.prepareStatement(createTypesTable)) {
                
                stmt1.executeUpdate();
                stmt2.executeUpdate();
                
                logger.info("Database tables created successfully!");
                return true;
            }
            
        } catch (SQLException e) {
            logger.severe("Failed to create database tables: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get a connection from the pool
     */
    public Connection getConnection() throws SQLException {
        if (!isConnected || dataSource == null) {
            throw new SQLException("Database is not connected");
        }
        return dataSource.getConnection();
    }
    
    /**
     * Update or insert leaderboard data for a player
     */
    public CompletableFuture<Boolean> updatePlayerData(String playerUuid, String playerName, String leaderboardType, double value) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected) return false;
            
            String sql = "INSERT INTO " + tablePrefix + "leaderboard_data (player_uuid, player_name, leaderboard_type, value) " +
                        "VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE player_name = VALUES(player_name), value = VALUES(value), last_updated = CURRENT_TIMESTAMP";
            
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid);
                stmt.setString(2, playerName);
                stmt.setString(3, leaderboardType);
                stmt.setDouble(4, value);
                
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                logger.severe("Failed to update player data: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        }, dbExecutor);
    }
    
    /**
     * Get top players for a specific leaderboard type
     */
    public CompletableFuture<List<Map<String, Object>>> getTopPlayers(String leaderboardType, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> topPlayers = new ArrayList<>();
            if (!isConnected) return topPlayers;
            
            String sql = "SELECT player_uuid, player_name, value, last_updated FROM " + tablePrefix + "leaderboard_data " +
                        "WHERE leaderboard_type = ? ORDER BY value DESC LIMIT ?";
            
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                
                stmt.setString(1, leaderboardType);
                stmt.setInt(2, limit);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> playerData = new HashMap<>();
                        playerData.put("uuid", rs.getString("player_uuid"));
                        playerData.put("name", rs.getString("player_name"));
                        playerData.put("value", rs.getDouble("value"));
                        playerData.put("lastUpdated", rs.getTimestamp("last_updated"));
                        topPlayers.add(playerData);
                    }
                }
                
            } catch (SQLException e) {
                logger.severe("Failed to get top players: " + e.getMessage());
                e.printStackTrace();
            }
            
            return topPlayers;
        }, dbExecutor);
    }
    
    /**
     * Get player's position in a specific leaderboard
     */
    public CompletableFuture<Integer> getPlayerPosition(String playerUuid, String leaderboardType) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected) return -1;
            
            String sql = "SELECT COUNT(*) + 1 as position FROM " + tablePrefix + "leaderboard_data " +
                        "WHERE leaderboard_type = ? AND value > (SELECT value FROM " + tablePrefix + "leaderboard_data " +
                        "WHERE player_uuid = ? AND leaderboard_type = ?)";
            
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                
                stmt.setString(1, leaderboardType);
                stmt.setString(2, playerUuid);
                stmt.setString(3, leaderboardType);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("position");
                    }
                }
                
            } catch (SQLException e) {
                logger.severe("Failed to get player position: " + e.getMessage());
                e.printStackTrace();
            }
            
            return -1;
        }, dbExecutor);
    }
    
    /**
     * Register a new leaderboard type
     */
    public CompletableFuture<Boolean> registerLeaderboardType(String typeName, String displayTitle, String placeholder, String format) {
        return CompletableFuture.supplyAsync(() -> {
            if (!isConnected) return false;
            
            String sql = "INSERT INTO " + tablePrefix + "leaderboard_types (type_name, display_title, placeholder, format) " +
                        "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                        "display_title = VALUES(display_title), placeholder = VALUES(placeholder), format = VALUES(format)";
            
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql)) {
                
                stmt.setString(1, typeName);
                stmt.setString(2, displayTitle);
                stmt.setString(3, placeholder);
                stmt.setString(4, format);
                
                int rowsAffected = stmt.executeUpdate();
                logger.info("Successfully registered leaderboard type: " + typeName);
                return rowsAffected > 0;
                
            } catch (SQLException e) {
                logger.severe("Failed to register leaderboard type: " + e.getMessage());
                logger.severe("SQL State: " + e.getSQLState() + ", Error Code: " + e.getErrorCode());
                e.printStackTrace();
                return false;
            }
        }, dbExecutor);
    }
    
    /**
     * Get all registered leaderboard types
     */
    public CompletableFuture<Map<String, Map<String, String>>> getLeaderboardTypes() {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, String>> types = new HashMap<>();
            if (!isConnected) return types;
            
            String sql = "SELECT type_name, display_title, placeholder, format FROM " + tablePrefix + "leaderboard_types";
            
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    Map<String, String> typeData = new HashMap<>();
                    typeData.put("title", rs.getString("display_title"));
                    typeData.put("placeholder", rs.getString("placeholder"));
                    typeData.put("format", rs.getString("format"));
                    types.put(rs.getString("type_name"), typeData);
                }
                
            } catch (SQLException e) {
                logger.severe("Failed to get leaderboard types: " + e.getMessage());
                e.printStackTrace();
            }
            
            return types;
        }, dbExecutor);
    }
    
    /**
     * Close the database connection
     */
    public void close() {
        if (dbExecutor != null) {
            dbExecutor.shutdown();
            try {
                if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection closed.");
        }
        isConnected = false;
    }
    
    /**
     * Get the executor service for running async database tasks
     */
    public ExecutorService getExecutor() {
        return dbExecutor;
    }
    
    /**
     * Check if the database is connected
     */
    public boolean isConnected() {
        return isConnected && dataSource != null && !dataSource.isClosed();
    }
    
    /**
     * Get the table prefix
     */
    public String getTablePrefix() {
        return tablePrefix;
    }
}