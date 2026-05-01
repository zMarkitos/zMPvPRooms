# zMPvPRooms ⚔️

A premium, highly-optimized, and professional PvP arena system for Minecraft servers. Create both classic 1v1 duels and massive Clan wars with seamless integration with UltimateClans, PlaceholderAPI, and WorldGuard.

## 🌟 Features

* **Two Game Modes:**
  * **NORMAL:** Standard PvP rooms (1v1, 2v2, FFA).
  * **CLAN:** Exclusive clan versus clan warfare.
* **Premium Optimizations:**
  * Uses SQLite with `WAL` mode and atomic Upserts for crash-proof, zero-lag statistics.
  * Asynchronous leaderboards that never drop TPS.
* **Smart Security:**
  * Crash-proof match handling. If the server crashes, players in active arenas are safely returned to spawn on next join.
  * Dynamic command and inventory blocking during matches.
* **Dynamic Aesthetics:**
  * Integrated Bossbars, Actionbars, Titles, and Fireworks for victory celebrations.
  * Fully customizable translation files (`lang_EN.yml`, `lang_ES.yml`) with MiniMessage (`<color>`) and Hex (`&#RRGGBB`) support.
* **Visual Editor & Wand System:**
  * Create arenas quickly with an intuitive wand selector and in-game editor UI.

---

## 📦 Requirements
* **Java 17+**
* **Minecraft 1.19+** (Compatible up to 1.21)

### Soft Dependencies (Hooks)
* [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) - For statistics and leaderboards.
* [WorldGuard](https://dev.bukkit.org/projects/worldguard) - For auto-entrance and arena border detection.
* [UltimateClans (v8)](https://www.spigotmc.org/resources/ultimate-clans.59779/) - For Clan VS Clan mode.
* [Vault](https://www.spigotmc.org/resources/vault.34315/) - For future betting implementations.

---

## 🛠️ Installation

1. Drop the `zMPvPRooms.jar` into your `plugins` folder.
2. Restart the server to generate the configuration files.
3. Use the `/zmrooms` command to set the global return spawn and start creating arenas.

### Basic Setup Commands
* `/zmrooms setspawn` - Sets the global return spawn for all matches.
* `/zmrooms editor` - Opens the visual arena creator GUI.
* `/zmrooms wand` - Gives the wand to select arena boundaries.

---

## 📊 PlaceholderAPI

The plugin registers the expansions `rooms`, `zmrooms`, `zmpvp`, and `zmpvprooms`.

### General
* `%rooms_currentzone%` - Returns the arena the player is in, or `none`.

### Personal Statistics
* `%rooms_kills%`, `%rooms_deaths%`, `%rooms_wins%`, `%rooms_losses%` - Total combined stats.
* `%rooms_kdr%` - Global Kill/Death Ratio.
* `%rooms_streak%` - Current win streak.
* Specific modes: `%rooms_mynormalkills%`, `%rooms_myclanwins%`, etc.

### Leaderboards (Top)
* **Format:** `%rooms_top_<column>_<rank>%` (Returns Player Name)
* **Format:** `%rooms_top_<column>_value_<rank>%` (Returns Score Value)
* **Valid columns:** `normal_wins`, `clan_wins`, `normal_kills`, `clan_kills`, `normal_deaths`, `clan_deaths`

*Example:* `%rooms_top_normal_wins_1%` -> Returns the name of the #1 player in Normal Wins.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
