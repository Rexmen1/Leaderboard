# Leaderboards Plugin

A Minecraft Spigot/Paper plugin that displays player leaderboards using PlaceholderAPI.

## Features

- Display player statistics in interactive GUI leaderboards
- Support for multiple leaderboard types (configurable)
- PlaceholderAPI integration for flexible data sources
- Paginated GUI with navigation
- Automatic periodic updates
- Admin commands for manual updates and configuration reload
- **Player head texture caching** - Shows custom skins for offline players
- Automatic texture cache management with configurable expiry

## Dependencies

- **Spigot/Paper** 1.20+
- **PlaceholderAPI** 2.11.5+

## Installation

1. Download the plugin JAR file
2. Place it in your server's `plugins` folder
3. Ensure PlaceholderAPI is installed and enabled
4. Start/restart your server
5. Configure the plugin in `plugins/Leaderboards/config.yml`

## Configuration

The plugin creates a `config.yml` file with the following structure:

```yaml
# Update interval in seconds
update-interval: 30

# Debug settings
debug:
  enabled: false
  log-updates: false
  log-texture-cache: false

# Texture Cache Settings
texture-cache:
  enabled: true
  expiry-days: 7 # How many days to keep cached textures
  max-size: 1000 # Maximum number of player textures to cache

# GUI Settings
gui:
  title: "Leaderboards - {type}"
  size: 54 # 6 rows
  navigation:
    next-page:
      slot: 50
      item: ARROW
      name: "§aNext Page"
    previous-page:
      slot: 48
      item: ARROW
      name: "§aPrevious Page"

# Leaderboard Types
leaderboards:
  mobkills:
    title: "Top Mob Kills"
    placeholder: "%statistic_mob_kills%"
    format: "{position}. {player}: {value} kills"
  blocks:
    title: "Top Blocks Mined"
    placeholder: "%statistic_mine_block%"
    format: "{position}. {player}: {value} blocks"
```

## Commands

- `/leaderboard <type>` - Open a leaderboard GUI
- `/leaderboard update <type>` - Manually update a specific leaderboard (requires `leaderboards.update` permission)
- `/leaderboard update *` - Update all leaderboards (requires `leaderboards.update` permission)
- `/leaderboard reload` - Reload plugin configuration (requires `leaderboards.reload` permission)
- `/leaderboard cache` - View texture cache information (requires `leaderboards.reload` permission)
- `/leaderboard cache clear` - Clear expired texture cache entries (requires `leaderboards.reload` permission)

**Aliases:** `/lb`

## Permissions

- `leaderboards.update` - Allows updating leaderboards manually (default: op)
- `leaderboards.reload` - Allows reloading plugin configuration (default: op)

## PlaceholderAPI Integration

The plugin registers placeholders with the identifier `leaderboard`:

- `%leaderboard_name_<type>_<position>%` - Gets the player name at the specified position
- `%leaderboard_value_<type>_<position>%` - Gets the value at the specified position

Example: `%leaderboard_name_mobkills_1%` gets the top player's name in the mob kills leaderboard.

## Player Head Texture Caching

The plugin features an intelligent texture caching system that ensures all players show their custom skins in leaderboards, even when offline:

### How it works:

1. **Online Player Detection**: When players are online, their textures are automatically cached
2. **Offline Display**: When players go offline, their cached textures are used instead of the default Steve skin
3. **Automatic Updates**: Player textures are refreshed whenever they come online
4. **Cache Management**: Old textures are automatically cleaned up based on configurable expiry settings

### Configuration:

```yaml
texture-cache:
  enabled: true # Enable/disable texture caching
  expiry-days: 7 # Keep textures for 7 days
  max-size: 1000 # Maximum cached textures
```

### Benefits:

- **Better User Experience**: All players show their custom skins
- **Performance Optimized**: No blocking API calls for offline players
- **Server Friendly**: Configurable cache limits prevent memory issues
- **Automatic Management**: Self-cleaning cache with expiry system

## Building from Source

Requirements:

- Java 17+
- Maven 3.6+

```bash
# Clone the repository
git clone <repository-url>
cd leaderboards

# Build the plugin
mvn clean package

# The compiled JAR will be in target/Leaderboards-1.0.jar
```

## Issues Fixed in This Version

This is a reconstructed version of a decompiled plugin with the following fixes:

1. **Added missing `author` field** in `plugin.yml` (required for PlaceholderAPI expansion)
2. **Fixed empty `data.yml`** with proper structure and documentation
3. **Fixed GUI title parsing bug** in `LeaderboardGUIListener` that caused navigation issues
4. **Added proper Maven project structure** for easy building and dependency management
5. **Added missing `reloadConfiguration()` method** in the main plugin class
6. **Improved error handling** in GUI navigation and placeholder parsing
7. **NEW: Player texture caching system** - Shows custom skins for offline players without server performance impact

## License

This plugin is provided as-is for educational and server use purposes.
