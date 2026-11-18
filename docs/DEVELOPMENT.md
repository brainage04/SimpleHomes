# Development

Build both loader artifacts:

```shell
heavy -- ./gradlew --no-daemon build
```

Run command, persistence, teleport, and access-control GameTests on both loaders:

```shell
heavy -- ./gradlew --no-daemon runAllGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [MinecraftModConventions](https://github.com/brainage04/MinecraftModConventions) for shared build, GameTest, recording, and publishing conventions.

Plugins come from Maven Central at `minecraftmodconventions_version` in `gradle.properties`. A `../MinecraftModConventions` checkout beside the project is used only with `-PuseLocalConventions`; without it a stale checkout cannot replace that version.
