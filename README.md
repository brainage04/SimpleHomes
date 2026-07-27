# ModernMinecraftModTemplate

My template for modern Minecraft Fabric mods, with common code in `src/main`, client-only code in `src/client`, and GameTests in `src/gametest`.

The easiest way to use this is:

1. Click `Use this template` and create your new repository.
2. Open the new repository's `Actions` tab.
3. Run the `Initialize Template Repo` workflow.
4. Choose `both`, `server`, or `client` for the mod side.

However, if you are using a Linux-based operating system, it is possible to clone this repository, and perform a refactor by triggering the `init.sh` script like so:
Local initialization requires `jq`; metadata changes are applied as validated JSON transformations rather than text substitutions.

```shell
./init.sh [--side=both|server|client] <mod_name>
```

Where `<mod_name>` is your GitHub repository name/mod name.
The optional `--side` flag defaults to `both`.
The script records the choice as `mod_side=both|client|server`; the convention plugins derive Loom source sets and GameTest tasks from that property.
Use `--side=server` to generate a server-only repo and remove the client entrypoint/source set from the generated project.
Use `--side=client` to generate a client-only repo and remove the dedicated-server GameTest path from the generated project.
When the GitHub Actions workflow initializes a template repository, it uses the repository name as `<mod_name>`.
Generated packages always use `io.github.brainage04.<mod_id>`, where `<mod_id>` is sanitized from `<mod_name>` so it is safe for Fabric mod IDs and Java package names.

The workflow and script are designed to be run once. After successful initialization, they safely delete:
  - Leftover unused folders that are not tracked by Git (such as `src/main/java/io/github/brainage04/modernminecraftmodtemplate`, `src/client/java/io/github/brainage04/modernminecraftmodtemplate`, and `src/main/resources/modernminecraftmodtemplate`).
  - The `init` script after successful execution.

GitHub Actions initialization preserves files under `.github/workflows`.
The workflow uses GitHub's generated `GITHUB_TOKEN`, which can push normal repository content but cannot update workflow files.
This means repositories initialized through Actions keep the one-shot `init` workflow file, but `init.sh` is deleted and the workflow should not be run again.
If you run `init.sh` locally and push with your own Git credentials, the script also removes the one-shot `init` workflow. The shared client-GameTest workflow remains in every generated repository and skips execution when `mod_side=server`.

For local development after initialisation:
  - Use the Java version configured by `java_version` in `gradle.properties` (`25` by default) or newer for Gradle and Minecraft.
  - `./gradlew runServer` launches the common/server side when you keep `--side=both` or choose `--side=server`.
  - `./gradlew runClient` launches the client side when you keep `--side=both` or choose `--side=client`.
  - Mod Menu is included as a development dependency and a minimal `modmenu` entrypoint is kept in the generated mod metadata so you can test the integration during local client development without having to re-add it by hand.
  - The template includes both a server command example in `src/main` and a client command example in `src/client`.

# Testing

Run:

```shell
./gradlew test
```

The template includes example tests under `src/test/java` that show two useful patterns:
  - Fabric-aware tests that boot Fabric Loader and inspect loaded mod metadata.
  - Plain unit tests for your own code, such as command registration.

For integration-style server tests, run:

```shell
./gradlew runGameTest
```

The template includes a separate `src/gametest` source set with a minimal server GameTest that checks the example command was registered on the server.
Server GameTests also run automatically as part of `./gradlew build`, which is what the included GitHub Actions workflow executes.

For client-side GameTests, run:

```shell
./gradlew runClientGameTest
```

The template also includes a minimal client GameTest that boots the client, connects to an in-process dedicated server through FabricModdingConventions's defensive client-join helper, starts the recording handshake, and checks that the client initializer ran in an in-world context.
When you initialise with `--side=client`, the generated repo keeps this client GameTest path and removes the dedicated-server GameTest path.

On Ubuntu, local headless client GameTests need Xvfb and the same OpenGL/windowing libraries that the GitHub Actions workflow installs. Recorded client GameTests also need ffmpeg and PipeWire tools:

```shell
sudo apt-get update
sudo apt-get install -y ffmpeg pipewire-bin xvfb mesa-utils libflite1 libgl1-mesa-dri libglx-mesa0 libxi6 libxrandr2 libxrender1 libxtst6 libxinerama1 libxcursor1 libxxf86vm1
```

Run the client GameTest through Xvfb:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
xvfb-run -a --server-args="-screen 0 1280x720x24" \
./gradlew --no-daemon runClientGameTest
```

Record the client GameTest through FabricModdingConventions:

```shell
ALSOFT_DRIVERS=null LIBGL_ALWAYS_SOFTWARE=1 \
./gradlew --no-daemon recordClientGameTest
```

# Publishing

Release automation is documented in [docs/RELEASE.md](docs/RELEASE.md).
Optional Modrinth publishing is documented in [docs/MODRINTH.md](docs/MODRINTH.md).

# Credits

Thank you to [nea89o](https://github.com/nea89o)
for developing the GitHub Actions [workflow](https://github.com/nea89o/Forge1.8.9Template/blob/master/.github/workflows/init.yml)
and [script](https://github.com/nea89o/Forge1.8.9Template/blob/master/make-my-own.sh)
from which I based my workflow and script off of.
