# Cobble Comfort Sync

Spigot/Bukkit plugin (1.21+) that shares player positions between clients running the Cobble Comfort mod, so
other players show up on the minimap and map even beyond render distance and in other dimensions.

- **Opt-in:** a player is shared only after switching on *Share position* in the mod (map layer sidebar or config),
  and only sharing players receive the others.
- Players without the mod are never sent anything and never show up.
- Shared: name, world, position, facing, and (unless disabled) health and armour.

## Install

Build with `./gradlew :server-plugin:build` and put `server-plugin/build/libs/cobble-comfort-sync-<version>.jar`
into the server's `plugins/` folder.

## Config (`plugins/CobbleComfortSync/config.yml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `interval-ticks` | `10` | How often positions are sent (20 = once a second). |
| `max-distance` | `0` | Only share players within this many blocks in the same world (0 = no limit, all worlds). |
| `hide-spectators` | `true` | Do not share players in spectator mode. |
| `hide-invisible` | `true` | Do not share players with the invisibility effect. |
| `share-health` | `true` | Send health and armour too. |

Vanished players (hidden with `Player#hidePlayer`, as vanish plugins do) are not sent to those who cannot see them.

## Commands and permissions

- `/cobblecomfort` lists who shares; `/cobblecomfort reload` reloads the config (`cobblecomfort.admin`, ops).
- `cobblecomfort.share` (default: everyone) allows sharing and seeing others.

## Protocol

Plugin channels `cobblecomfort:share` (client → server) and `cobblecomfort:players` (server → client); the format is
documented in `Protocol.java` and matches `PositionSync` in the mod.
