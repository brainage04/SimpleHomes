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
