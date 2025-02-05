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

## Wiki

This section provides detailed information about the plugin's usage, configuration, and features.

### Placeholders

Use these placeholders in other plugins or broadcasts:

| Placeholder                     | Description                                                                 | Example                     |
|---------------------------------|-----------------------------------------------------------------------------|------------------------------|
| `%leaderboard_name_<type>_<position>%` | Returns the player name at the specified position in the leaderboard.       | `%leaderboard_name_mobkills_1%` |
| `%leaderboard_value_<type>_<position>%` | Returns the value at the specified position in the leaderboard.             | `%leaderboard_value_blocks_3%` |

`<type>` should be replaced with the leaderboard type (e.g., `mobkills`, `blocks`).
`<position>` should be replaced with the desired position (e.g., `1`, `2`, `3`).


### Commands

| Command              | Description                                                                 | Permission Required |
|-----------------------|-----------------------------------------------------------------------------|-----------------------|
| `/leaderboard <type>` | Opens the leaderboard GUI for the specified type.                             | None                   |
| `/leaderboard update <type>` | Updates the specified leaderboard.                                           | `leaderboards.update` |
| `/leaderboard update *` | Updates all leaderboards.                                                    | `leaderboards.update` |
| `/leaderboard reload`  | Reloads the plugin configuration.                                          | `leaderboards.reload`  |


### Permissions

| Permission           | Description                                                                 | Default Value |
|-----------------------|-----------------------------------------------------------------------------|-----------------|
| `leaderboards.update` | Allows updating leaderboards.                                           | `op`             |
| `leaderboards.reload`  | Allows reloading the plugin configuration.                                          | `op`             |


### Usage

1. **Installation:** Download the latest release and place the JAR file in your server's plugins folder.
2. **Configuration:** Configure the leaderboards in the `config.yml` file.
3. **Commands:** Use the commands listed above to view and update leaderboards.
4. **PlaceholderAPI:** Use the PlaceholderAPI placeholders to display leaderboard data in other plugins or broadcasts.


### Further Information

- **Automatic Updates:** Leaderboards are automatically updated every 30 seconds (configurable).
- **Data Persistence:** Leaderboard data is saved persistently to `data.yml`.
- **Pagination:** Leaderboards with more than 45 entries are paginated.
- **GUI Security:** The leaderboard GUI is protected against item manipulation.