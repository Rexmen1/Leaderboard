# Leaderboards Plugin

A Spigot plugin that creates dynamic leaderboards for various statistics using PlaceholderAPI.

## Features

- Multiple leaderboard types configurable via config.yml
- Automatic updates every 30 seconds
- Paginated GUI interface
- PlaceholderAPI support for external usage
- Persistent data storage
- Efficient batch processing

## Requirements

- Spigot/Paper 1.20+
- PlaceholderAPI

## Installation

1. Download the latest release from the releases page
2. Place the jar in your server's plugins folder
3. Restart your server
4. Configure leaderboard types in config.yml

## Configuration

```yaml
# Update interval in seconds
update-interval: 30

# Debug settings
debug:
  enabled: false
  log-updates: false

# Leaderboard Types
leaderboards:
  mobkills:
    title: "Top Mob Kills"
    placeholder: "%statistic_mob_kills%"
    format: "{position}. {player}: {value} kills"
```

## Commands

- `/leaderboard <type>` - View a leaderboard
- `/leaderboard update <type>` - Update specific leaderboard
- `/leaderboard update *` - Update all leaderboards
- `/leaderboard reload` - Reload configuration

## Permissions

- `leaderboards.update` - Allow updating leaderboards
- `leaderboards.reload` - Allow reloading configuration

## PlaceholderAPI Support

Use these placeholders in other plugins:

- `%leaderboard_name_<type>_<position>%` - Get player name at position
- `%leaderboard_value_<type>_<position>%` - Get value at position

Example:
```
%leaderboard_name_mobkills_1% - Name of #1 player in mobkills
%leaderboard_value_blocks_3% - Value of #3 player in blocks mined
```

## Building

1. Clone the repository
2. Run `mvn clean package`
3. Find the jar in `target/` directory

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.