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
