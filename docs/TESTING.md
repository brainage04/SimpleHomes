# Test review

## Frozen pruning rubric (2026-10-19)

- Keep: exercises production behavior and has an independent regression oracle, or checks actual loader lifecycle integration.
- Redundant: every meaningful assertion is covered by a named retained test; sharing setup or a few assertions is not enough.
- Tautological: checks a copied implementation, a mock's own response, isolated registration wiring, or a constant without exercising a player-visible consequence.
- Both loaders running the same shared GameTest is parity coverage, not duplication.

Candidates were identified from test names before reading their bodies. Only the potential registration, limit and sharing overlaps were read for pruning.

## Original inventory and decisions

Unit tests:

- `registersEveryPublicCommand`: redundant with `allHomeCommandsAreRegistered`, which checks the live dispatcher after each loader's startup. Removed the isolated dispatcher test.
- `enforcesTheLimitButAllowsExistingHomesToBeUpdated`: keep. The server limit test does not cover updating a home at the limit.
- `scopesSharedAccessToAnOwnerAndHome`: keep. The server sharing test does not cover revocation, missing-home revocation or another owner's namespace.
- `loadsTheConfigurableMaximumAndWritesTheDefault`: keep. Configuration has no other test.

Shared server GameTests, registered on Fabric and NeoForge:

- `allHomeCommandsAreRegistered`: keep. Checks each loader's actual command registration lifecycle.
- `namedHomesRespectLimitsAndTeleport`: keep. Checks real server teleportation as well as limits.
- `homeSharingIsOwnerAndHomeScoped`: keep. Checks real server teleportation and per-home access state.

Client GameTest:

- `SimpleHomesClientGameTest.runTest`: keep. Exercises the connected client command tree and teleport flow.

No tautological test was removed. The loader entrypoint methods delegate to the shared bodies and are not separate behavioral tests.

## Coverage gaps addressed

Previously untested behavior included command name validation and case folding, command-level limits, sharing and revocation through the dispatcher, serialized homes and shares, invalid configuration, cross-dimension rotation restoration, unavailable dimensions, and the result of client-issued commands.

- Unit tests now round-trip the production saved-data codec, preserve owner/home access boundaries, retain shares when updating, remove revoked shares, count duplicate/self grants correctly, and verify dirty-state changes.
- Invalid config cases check fallback from a previously loaded value without overwriting the bad file; configuration tests restore the default afterward.
- Shared GameTests execute the real commands for creation, invalid names, limits, updates, guest denial, grant, duplicate grant, per-home isolation, revocation and owner access. Temporary online-player registrations are removed in `finally`.
- A shared teleport test checks Nether position and rotation and the no-movement result for an unavailable dimension.
- The client scenario now asserts that `/sethome` creates data and `/home` restores the saved location, rather than relying only on recording captions.

There are no gamerules or mod-specific rendering hooks. Both loader startup paths and production jars are exercised by the conventions suites. Still not automated: client-visible chat styling, offline-profile lookup with a real authentication service, and stopping/restarting a server process with the same world directory. The codec round-trip covers the mod-owned persistence logic, not vanilla's disk storage implementation.

## Code review regressions

- The `revoke` subcommand used to hide the identically named home from `/sharehome`. The sharing command test now grants, uses and revokes access to a home named `revoke`.
- Gson's integer conversion used to truncate fractions and wrap large numbers. A parameterized unit test rejects fractional and overflowing limits instead of silently accepting another value.
- The teleport service used to return success even when Minecraft rejected the teleport. A server GameTest uses a removed player to exercise Minecraft's real rejection path.

Review decisions left unchanged: English literal messages and plain white failures are a presentation/localization decision; offline-mode profile resolution can identify a never-seen name, so `/homeof Nobody base` currently reports that the resolved profile has no such home. Neither loader owns background threads or open resources, and both call the same configuration and command registration code.
