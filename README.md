# SimpleHomes

SimpleHomes adds named, persistent homes with explicit per-home sharing.

## Commands

| Command | Behavior |
| --- | --- |
| `/sethome <name>` | Creates a home at your current dimension, position, yaw, and pitch, or updates an existing home with that name. |
| `/home <name>` | Teleports you to one of your homes. |
| `/sharehome <name> <players>` | Grants the selected online players access to one of your homes. |
| `/sharehome revoke <name> <players>` | Revokes that access. |
| `/homeof <player> <name>` | Teleports to that player's home when they have shared it with you. |

Home names are case-insensitive and may contain 1-32 letters, numbers, underscores, or hyphens. Sharing one home does not expose any other home belonging to the same player.

## Configuration

The first launch creates `config/simplehomes.json`:

```json
{
  "maximumHomes": 10
}
```

`maximumHomes` must be at least 1. Updating an existing home remains possible when a player has reached the limit.

## Loaders and installation

Fabric and NeoForge builds are provided for Minecraft 26.2.

Install exactly one matching JAR on the server: the Fabric JAR with Fabric API, or the NeoForge JAR with NeoForge. Vanilla clients can join without installing the mod.

## Development

Build both loader artifacts:

```shell
./gradlew build
```

Run command registration, limits, teleport, and access-control GameTests on both loaders:

```shell
./gradlew runAllProductionGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [FabricModdingConventions](https://github.com/brainage04/FabricModdingConventions) for shared build, GameTest, recording, and publishing conventions.

Release automation is documented in [docs/RELEASE.md](docs/RELEASE.md). Optional Modrinth publishing is documented in [docs/MODRINTH.md](docs/MODRINTH.md).
