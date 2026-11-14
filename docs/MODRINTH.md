# Modrinth and CurseForge publishing

SimpleHomes publishes both loader JARs from one release. `.github/workflows/release.yml` holds a single job that calls the shared multi-loader release workflow in `brainage04/MinecraftModConventions`, pinned at `v3.4.0`.

## When it runs

The workflow starts on a push of a tag named `v*`, and on nothing else. The tag must be `v` plus `mod_version` from `gradle.properties`, so a release of `mod_version=1.2.0` needs the tag `v1.2.0`. A tag that does not match fails during preparation, before anything is built or published.

## The two JARs

The preparation job runs `./gradlew clean build` and picks out:

- `simplehomes-<mod_version>.jar`, the Fabric release, from `build/libs`;
- the NeoForge release, as the one file in `build/libs` matching `simplehomes-neoforge-*.jar`. If the pattern matches no file or more than one, the release fails.

Both JARs are attached to a GitHub release titled `SimpleHomes vX.Y.Z`. The release body is the annotated tag message; when the tag carries no message, GitHub-generated notes are used instead. A tag containing a hyphen, such as `v1.2.0-rc1`, is marked as a prerelease.

## Destinations

After preparation the workflow runs one job per destination:

| Job | Publishes |
| --- | --- |
| `github` | the GitHub release with both JARs |
| `modrinth-fabric` | the Fabric JAR to Modrinth |
| `modrinth-neoforge` | the NeoForge JAR to the same Modrinth project |
| `curseforge-fabric` | the Fabric JAR to CurseForge |
| `curseforge-neoforge` | the NeoForge JAR to CurseForge |

The two Modrinth jobs publish to one project. The Fabric job resolves the project first; the NeoForge job waits for it and reuses its project id. The NeoForge version is uploaded as `<mod_version>-neoforge`, so the two loader versions of a release do not collide as Modrinth version numbers.

The CurseForge jobs upload with the project slug `simplehomes` and the project id from the `CURSEFORGE_PROJECT_ID` variable.

## Secrets and variables

Set these under the repository's Settings, Secrets and variables, Actions.

Secrets:

- `MODRINTH_TOKEN`: Modrinth personal access token.
- `CURSEFORGE_TOKEN`: CurseForge API token.

`GITHUB_TOKEN` is provided by GitHub and the workflow passes it as its release token, so no extra secret is needed for the GitHub release.

Variables:

- `MODRINTH_PROJECT_ID`, optional: the id of the existing Modrinth project, handed to the workflow. It can stay empty, because the Fabric job resolves the project from the slug and creates it as a draft project when it does not exist. The slug is the `fabric.mod.json` id (`simplehomes`) unless `.modrinth/project.json` sets a different one.
- `CURSEFORGE_PROJECT_ID`, required for CurseForge: the numeric CurseForge project id.

## What is skipped

- No `MODRINTH_TOKEN`: both Modrinth jobs log a notice that Modrinth publication is skipped and run nothing else. The GitHub release still happens.
- No `CURSEFORGE_TOKEN`: both CurseForge jobs log the same kind of notice and skip.
- `CURSEFORGE_TOKEN` set but `CURSEFORGE_PROJECT_ID` empty: the CurseForge job fails.
- The Modrinth version number already present on the project: that upload is skipped, because the project's version list is checked first.
- The GitHub release for the tag already exists: it is edited and its assets are replaced instead of creating a second release.

A failed Modrinth project or icon synchronization does not block the version upload. The job publishes the version and then fails, so the stale project metadata stays visible.

## Project metadata

The Modrinth project is filled from the repository, and the Fabric job refreshes it on every release:

- `README.md` is the project body.
- `fabric/src/main/resources/fabric.mod.json` provides the title, description, licence and contact links; a missing link falls back to the GitHub repository URL.
- `.modrinth/project.json` overrides the slug, categories and side support.
- `.modrinth/neoforge-dependencies.json` provides the dependencies of the NeoForge version.
- `common/src/main/resources/assets/simplehomes/icon.png` is the project icon.

The `depends`, `recommends`, `suggests`, `conflicts` and `breaks` entries of `fabric.mod.json` become Modrinth version dependencies, taking each mod id as a Modrinth slug; `minecraft`, `java` and `fabricloader` are left out. A `dependency_overrides` entry in `.modrinth/project.json` can remap one of them with `project_id`, `project_slug` or `dependency_type`, or drop it with `skip`.

`docs/RELEASE.md` covers cutting the tag.
