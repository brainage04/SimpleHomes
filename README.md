# SimpleHomes

SimpleHomes is a server-side only Fabric and NeoForge mod for Minecraft 26.3 that adds named, persistent homes with explicit per-home sharing.

Home names are case-insensitive and may contain 1-32 letters, numbers, underscores, or hyphens. Sharing one home does not expose any other home belonging to the same player.

## Features

- Named, persistent homes.
- Explicit per-home sharing.
- A configurable per-player home limit.

## Getting started

- Fabric Loader >=0.19.5 with Fabric API, or NeoForge >=26.3.0.99.
- BrainageLib >=2.0.0 <3.0.0 for the matching loader.
- Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge. Vanilla clients can join without installing the mod.

## Commands

- `/sethome <name>` - creates a home at your current dimension, block position, yaw, and pitch, or updates an existing home with that name.
- `/home <name>` - teleports you to one of your homes.
- `/sharehome <name> <players>` - grants the selected online players access to one of your homes.
- `/sharehome revoke <name> <players>` - revokes that access, also from players who are offline. It suggests the players who have access.
- `/homeof <player> <name>` - teleports to that player's home when they have shared it with you, also while they are offline. The obstruction check of `/home` applies.
- `/simplehomes` or `/simplehomes help` - lists these commands and your home limit.

Messages use BrainageLib's style: a grey `[SimpleHomes]` prefix with a green body for success and a red body for errors. SimpleHomes is listed in BrainageLib's `/servermods` help and first-join notice.

## Configuration

The first launch creates `config/simplehomes.json`:

```json
{
  "maximumHomes": 10
}
```

`maximumHomes` must be a whole number from 1 to 2147483647. Updating an existing home remains possible when a player has reached the limit. Restart the server after changing the file.

## Compatibility

No known incompatibilities.

## Documentation

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) - build and GameTest commands.
- [docs/RELEASE.md](docs/RELEASE.md) - release automation.
- [docs/MODRINTH.md](docs/MODRINTH.md) - optional Modrinth publishing.
