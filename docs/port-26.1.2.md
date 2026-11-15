# Minecraft 26.1.2 NeoForge port checkpoint

Status: **incomplete / not publishable** (2026-11-15).

## Verified upstream inputs

- Minecraft 26.1.2, NeoForge 26.1.2.126, Java 25, ModDevGradle 2.0.148.
- AE2 official release `v26.1.13-beta` (source `c7c1812d337622e40a60930144dd0ba506edf813`).
- GuideME official release `v26.1.14-beta`.
- Official binary download URLs and SHA-256 are pinned in `tools/dependencies.json`.
- Singulo 26.1.2 is **not yet buildable**. Its current development checkpoint is
  `b2eba799b09b722dae75357c4c3c00399d40554c`, not a verified dependency artifact.
  The default build intentionally stops rather than loading an incompatible 1.21.1 runtime.

## Implemented migration (not runtime validated)

- Java 25 and target metadata; Identifier and jspecify nullable annotations.
- CompoundTag optional getters/UUID and ItemStack codecs; root-map bridge preserving
  queue fields while adapting block/part state to ValueInput/ValueOutput.
- SavedData codec for orphaned escrow recovery; removal-side-effect inventory recovery.
- Typed AE2 ClientActionKey / StreamCodec for menu actions.
- RegisterGameTestsEvent function registration retaining the existing test methods.
- NeoForge 26 registration helpers, block entity constructors, item identifiers.
- AE2 JSON part models and Minecraft item definitions; monitor extract/submit renderer,
  status emitter model data, screen extraction API. Icon/progress/count/idle/error display
  behavior is retained in source, but must be visually verified.

## Remaining blockers and next steps

1. Finish and verify Singulo's matching 26.1.2 artifact, then pin its exact source/hash.
   Do not substitute the 1.21.1 artifact at runtime or treat diagnostic compilation as integration.
2. Replace the removed `Capabilities.ItemHandler.BLOCK` in shared ME storage registration
   and the matching test. The public Singulo storage API still uses IItemHandler.
   A new explicitly named Android shared-storage capability is a candidate requiring
   changes on both sides. Do **not** re-use NeoForge's existing capability ID with a wrong type.
   Do **not** wrap immediate ME mutations in pretend transactions: nested abort/rollback
   must not duplicate or lose items. Retain owner checks, grid deduplication and invalidation.
3. Run all real GameTests after capability integration, including reload/cancel/escrow recovery,
   fluid refund, owner isolation, shared storage and pattern component identity.
4. Verify SavedData persistence location/schema migration, node state loading before level binding,
   partial-NBT/malformed data handling and dedicated-server class loading.
5. Verify in game with BGM disabled: GUI actions, six monitor orientations, item frame/hand models,
   emitter modes and cable power/channel indicators.
6. `build`, `tools/check_distribution.py`, GameTest log check and release provenance validation
   must all pass before any release. No new release tag, merge, upload or publication was made.

## Validation so far

The official Minecraft/NeoForge artifacts resolve. Diagnostic Java compilation used the old
Singulo 1.21.1 JAR **only** to expose source-level migration errors, with an explicit Gradle override.
It is not a compatible dependency and was never launched. All asset JSON files parse.
No distributable 26.1.2 JAR or passing game test run exists yet. Packaging checks cannot pass
until a correctly built artifact exists. Existing 1.21.1 / 1.20.1 branches are untouched.

Latest diagnostic compile: 3 errors, all removed Capabilities.ItemHandler references (2 registrations + 1 access test). Other migrated sources compile against official 26.1.2/AE2 dependencies and the explicitly overridden old API JAR; this does not prove compatibility with the unfinished new Singulo API. Default dependency guard rejects the unresolved artifact as expected.

## Shared storage follow-up

The shared registration now targets Singulo's explicitly separate
`AndroidStorageCapabilities.SHARED_ITEMS` (`singulo:android_shared_storage`, IItemHandler).
The matching API and native-to-legacy root-transaction adapter were added to the Singulo
26.1.2 development worktree. These changes require a new verified Singulo build; the
legacy 1.21.1 diagnostic JAR does not contain this API. The default pending dependency
and publication guard remain in force. Owner and network-key checks remain in MeStorageView.

## Real 26.1.2 compile checkpoint

`compileJava` and `build` passed against the actual development Singulo 26.1.2 JAR,
NeoForge 26.1.2.126, AE2 26.1.13-beta and GuideME 26.1.14-beta. This includes
compiling the addon GameTest source, but does not execute those tests.
`tools/check_distribution.py` passed metadata, Java 25 bytecode, icon, test exclusion
and no bundled dependency checks for `Applied-Singulo-0.1.0-26.1.2N.jar`.
The pending/unreleased dependency gate is intentionally still active: the current
Singulo source is being validated, so a development-only `-PsinguloJar` override
was used. No merge, push of dependencies, release tag or publication was performed.
Dedicated-server, integration GameTests and visual client checks remain required.

## Headless integration result

The real 26.1.2 server run passed all **46 Applied tests plus 1 Minecraft built-in**.
The first run exposed five obsolete crafting ingredient JSON files even though its
existing 45 tests passed. Those recipes were migrated and a new regression test now
checks all five recipes load, match their actual ingredient grids, and produce the
correct item/count. The final run has no datapack parse errors; the strengthened
`check_gametest_log.py` and `check_distribution.py` both pass.

Evidence: `build/port-gametest-real26-3.log` (47 required passed), Java 25,
NeoForge 26.1.2.126, AE2 26.1.13-beta, GuideME 26.1.14-beta, development Singulo
JAR SHA-256 `5a5e19d1f35a1044c878a5f6266d3a46f5a98857c86e42ce53f7b3268bd6f316`.
The addon also now imports the former flat orphaned-escrow save without deleting its
backup; it refuses a reset/replay if the new namespaced save exists but cannot decode.
Client visual validation remains outstanding; UI automation was stopped at the user's
Escape request. Publication remains blocked pending validated/pinned Singulo provenance.

### Final headless rerun

Repeated `runGameTestServer build` with the updated Singulo development JAR SHA-256
`a640076f5c30920b9bef71cb52c36a4c236a0b3a9811800e21b248b109fad4c2`.
All 47 required tests passed again (46 addon + 1 built-in), no ERROR lines,
log validation and packaging checks passed. Log: `build/port-gametest-final26.log`.
The resulting local Applied JAR SHA-256 is
`c62a5d5bd6c7e8acc5893f7ba234804ee79a9b80a5a07d373c7fdae8bba4ad78`.
This remains a development artifact, not a publishable CI receipt. No UI was started.

### Final Sodium-target checkpoint and AE2 test synchronization

The final Singulo dependency hash is now
`0b42f241c71ecf36c1ddebd5d5303dcf79547522c5c2fc5166015a493a04ad25`.
An initial rerun exposed a timing-dependent test fixture: AE2 26.1.13-beta's
`NetworkCraftingSimulationState` deliberately uses `getCachedInventory()` for
machine-origin calculations. The test switched its stored materials and immediately
started another calculation in the same tick, sometimes snapshotting old inventory.
The fixture now waits for the actual expected cached material counts before each
calculation. The original chain-selection assertions are unchanged, and no product
logic was relaxed. The corrected full rerun passed all 47 tests.

Two consecutive final runs passed (`port-gametest-final26-3.log` and `-4.log`),
with no ERROR lines. Packaging validation also passed; UI remains untested.
