# zMPvPRooms

A premium PvP arena system for Minecraft servers. Create classic duel rooms and clan wars with PlaceholderAPI, WorldGuard, and multiple clan-plugin integrations.

## Features

* **Two game modes**
  * `NORMAL`: standard PvP rooms such as 1v1, 2v2, or FFA.
  * `CLAN`: clan-vs-clan battles with provider-based clan support.
* **Optimized storage**
  * SQLite with WAL mode and atomic updates.
  * Global stats plus per-room stats for top placeholders.
  * Async leaderboard refreshes for low TPS impact.
* **Safe gameplay**
  * Match handling is designed to keep players safe on disconnects or crashes.
  * Command blocking and inventory protection during matches.
* **Polished feedback**
  * Bossbars, actionbars, titles, sounds, and fireworks.
  * Customizable language files in English and Spanish.
* **Fast setup**
  * In-game room editor and wand-based arena selection.
  * WorldGuard integration for arena bounds and region checks.

## Requirements

* Java 17+
* Minecraft 1.19+ compatible up to 1.21

### Soft Dependencies

* [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) - statistics, leaderboards, and custom placeholders.
* [WorldGuard](https://modrinth.com/plugin/worldguard) - arena borders and region detection.
* [Vault](https://www.spigotmc.org/resources/vault.34315/) - economy integration for betting.

### Clan Plugin Compatibility

The plugin supports these clan providers:

* `UltimateClans`
* `uClans`
* `ByteClans`
* `ApexClan`

The active provider is selected automatically by the internal priority list in the config.

## Installation

1. Drop the `zMPvPRooms.jar` into your `plugins` folder.
2. Restart the server to generate the configuration files.
3. Use `/zmrooms` to set the global return spawn and start creating arenas.

### Basic Setup Commands

* `/zmrooms create <name>` - Creates a new arena with the given name.
* `/zmrooms edit` - Opens the visual arena creator GUI.
* `/zmrooms setspawn` - Sets the global return spawn for all matches.

## PlaceholderAPI

The plugin registers these expansions:

* `%zmrooms_...%`
* `%rooms_...%`
* `%zmpvp_...%`
* `%zmpvprooms_...%`

### General

* `%zmrooms_currentzone%` - Returns the arena the player is in, or `none`.

### Personal Statistics

* `%zmrooms_kills%`, `%zmrooms_deaths%`, `%zmrooms_wins%`, `%zmrooms_losses%` - Total combined stats.
* `%zmrooms_kdr%` - Global kill/death ratio.
* `%zmrooms_streak%` - Current win streak.
* Specific modes: `%zmrooms_mynormalkills%`, `%zmrooms_myclanwins%`, etc.

### Leaderboards

* `Format:` `%zmrooms_top_<column>_<rank>%` returns the player name.
* `Format:` `%zmrooms_top_<column>_value_<rank>%` returns the score value.
* `Global top:` `%zmrooms_top_normal_wins_1%`
* `Per-room top:` add the room name at the end, for example `%zmrooms_top_normal_wins_1_room1%`
* `Valid columns:` `normal_wins`, `clan_wins`, `normal_kills`, `clan_kills`, `normal_deaths`, `clan_deaths`

Examples:

* `%zmrooms_top_normal_wins_1%` returns the #1 player in global normal wins.
* `%zmrooms_top_normal_wins_value_1_room1%` returns the score value of the #1 player in `room1`.

### Broadcast Placeholders

* `room_joined_broadcast` and `room_left_broadcast` support `%room%`.
* Example: `&a%player% &7joined room &e%room%&7.`

## Notes

* The plugin keeps global stats and per-room stats separately.
* The per-room leaderboard data is stored without changing or deleting the existing global stats table.
* Language files are available in:
  * `lang_EN.yml`
  * `lang_ES.yml`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
