# SimpleHomes

SimpleHomes is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds homes that can be shared with other players.

Home names are case-insensitive and may contain 1-32 letters, numbers, underscores, or hyphens (`revoke` is reserved because it is a subcommand of `/sharehome`).

## Features

- Named homes
- Per-home sharing
- Per-player home limits

## Requirements

- Fabric Loader >=0.19.5 w/ Fabric API or NeoForge >=26.3.0.99
- BrainageLib >=2.0.0

## Commands

- `/sethome <name>` - creates a home (or updates an existing home with that name) at your exact position and rotation for that dimension
- `/home <name>` - teleports you to the specified home (unless it's no longer safe - see below)
- `/sharehome <name> <players>` - grants access to the specified home to the specified players (online or offline)
- `/sharehome revoke <name> <players>` - revokes access to the specified home from the specified players (online or offline)
- `/homeof <player> <name>` - teleports to that player's home if you have been granted access (works while they're offline, as long as they've joined this server before)
- `/simplehomes` or `/simplehomes help` - lists these commands and your home limit

## Safety

`/home` and `/homeof` won't teleport you if the home is:

- blocked (no room to stand or even crawl)
- in lava (checked against your whole body, so lava at head height counts - skipped in creative, spectator, with infinite Fire Resistance, or when the `fire_damage` gamerule is off)
- over the void (no block below it down to the bottom of the world)
- high enough up that the fall would hurt you (going by the game's fall damage rule - no ground within 128 blocks counts as a fall that hurts)

You stay where you are instead. A home in an unloaded chunk still works - the chunk loads first and you arrive a moment later.

If you're already falling when you teleport, the fall resets when you arrive (like an ender pearl) unless the `simplehomes:reset_fall` gamerule is off.

## Config

- `/gamerule simplehomes:maximum_homes <1-2147483647>` - the most homes a player can have (default 10, updating an existing home still works at the limit)
- `/gamerule simplehomes:reset_fall <true|false>` - `true` (default) resets a player's fall on arrival like an ender pearl, `false` keeps the fall like vanilla `/tp`

## Documentation

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - build and GameTest commands.
- [docs/RELEASE.md](docs/RELEASE.md) - release automation.
- [docs/MODRINTH.md](docs/MODRINTH.md) - optional Modrinth publishing.
