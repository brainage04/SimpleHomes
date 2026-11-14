# Development

The GameTests use BrainageLib's test fixtures (`brainagelib-gametest`), which are not on Maven Central. Clone [BrainageLib](https://github.com/brainage04/BrainageLib) beside this checkout, as `../BrainageLib`, at the tag for the `brainagelib_version` in `gradle.properties`, and publish it to its local repository:

```shell
git clone --branch v2.0.0 https://github.com/brainage04/BrainageLib.git ../BrainageLib
../BrainageLib/gradlew -p ../BrainageLib publishAllPublicationsToLocalRepository
```

Build both loader artifacts:

```shell
./gradlew build
```

Plugins come from Maven Central at `minecraftmodconventions_version` in `gradle.properties`. A `../MinecraftModConventions` checkout beside the project is used only with `-PuseLocalConventions`; without it a stale checkout cannot replace that version.

Run command, persistence, teleport, and access-control GameTests on both loaders:

```shell
./gradlew runAllGameTests
```

The project was initialized from [ModernMinecraftModTemplate](https://github.com/brainage04/ModernMinecraftModTemplate) and uses [MinecraftModConventions](https://github.com/brainage04/MinecraftModConventions) for shared build, GameTest, recording, and publishing conventions.
