# Development

Build both loader artifacts:

```shell
heavy -- ./gradlew --no-daemon -Pfabricmoddingconventions.devClientOptions=/nonexistent build
```

Run command, persistence, teleport, and access-control GameTests on both loaders:

```shell
heavy -- ./gradlew --no-daemon -Pfabricmoddingconventions.devClientOptions=/nonexistent runAllGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [FabricModdingConventions](https://github.com/brainage04/FabricModdingConventions) for shared build, GameTest, recording, and publishing conventions.
