# Development verification: 2026-11-14, ME interface and batching

- `./gradlew build`, `tools/check_distribution.py` PASS.
- 18 addon GameTests PASS (adds: batching into one order, 4096 split and escrow-full rejection, Singulo recipe → AE2 pattern conversion, ME interface grid node host and console detection, outbox pull of expected outputs only).
- `./gradlew runClient` reaches the title screen with both blocks registered; no ERROR log lines.
- Not verified: a real ME network crafting through the interface with androids, AE2 crafting calculation with the exported patterns in game, channel/power behaviour.

# Development verification: 2026-11-14 (published Singulo 0.18.0)

## Exact inputs
- Singulo 0.18.0 published JAR (CurseForge file 9596414), SHA-256 `1ebf3ffcc50c73a6ac6e4cbcf35e6ed8b1fc98fefcf52c5b6202205bafc7ff7a`, identical bytes from CurseMaven. Contains `AndroidOrders` with `snapshot`.
- AE2 19.2.19 / GuideME 21.1.1 as pinned in `tools/dependencies.json`.
- Java 21 / Minecraft 1.21.1 / NeoForge 21.1.256.

## Passed locally
- `./gradlew build` with automatic pinned dependency download; `tools/check_distribution.py` PASS.
- 13 addon GameTests with real AE2, GuideME and Singulo loaded (8 previous + 5 bridge: AE2 capability lookup, unlinked push rejected without keeping materials, multi-output pattern rejected, supply face take-only, delivery face accepts only expected outputs and jobs/outputs survive save/load).
- `./gradlew runClient` with no arguments reaches the title screen with all four mods and no ERROR log lines.

## Not yet verified
- End-to-end in game: ME terminal → CPU → Pattern Provider → bridge → androids → Delivery Port → ME. Needs a world with a stocked console and androids.
- Dedicated server start, restart/unload during each job phase, ME full, console broken, multiple bridges sharing one console.
- Remote CI for this change (not pushed).

---

# Development verification: 2026-11-13

## Exact inputs
- Singulo feature/0.18.0 local commit: `466101f6de5d39288977bbe2e0ae2ee6c084a315`.
- API worktree commit: `c146fac910937f4d9f578c87a16e5e8448d4b61d`; its tree equals the feature commit: `8485c82d62fa8440faadfc83f006d948ff8d1f5d`.
- Tested base JAR SHA-256: `8931b198cfe1246061529e92f883ea6113ef872235e91421ccca96875e1fe9f9`.
- AE2 19.2.19 and GuideME 21.1.1: official URLs and SHA-256 in `tools/dependencies.json`.
- Java 21 / Minecraft 1.21.1 / NeoForge 21.1.256.

## Passed locally
- Singulo standard build; 8 API GameTests including actual crafting/delivery and receipt persistence/idempotency (not all existing Singulo GameTests).
- Applied Singulo build and distribution check, no bundled dependencies or test classes.
- 8 addon GameTests with the actual AE2, GuideME and Singulo mods loaded: pattern output/count decoding, no pattern consumption, defensive copy, non-pattern rejection, count overflow rejection, multiple outputs rejection, fluid input/output rejection, unavailable console status and command registration.
- Tested addon JAR SHA-256: `f3876c1e4302391b64df1fc66345a5b63c86598baa5ed9b68dbef06cd377979b`.

## Not yet verified / deliberately not implemented
- Interactive client UX and the entire Singulo regression suite.
- Pattern Provider acceptance, ingredient escrow/reservation, return-on-cancel, ME network output accounting and fully automatic crafting.
- These commands read an AE2 pattern's requested output but use the console's registered recipes and existing supply ports. They do not execute the AE2 pattern recipe or withdraw its ingredients from ME storage.
- Remote CI for this implementation: not run. The base API commit is intentionally local only; the addon implementation is also not pushed until the required source is available. Existing remote scaffold remains unchanged.
- No release, merge, tag, or publication was performed for this implementation.

## Next safe increment
Implement durable bridge identity and a material reservation/return contract before accepting AE2 Pattern Provider inputs. Test full-buffer, restart, chunk unload, cancellation, ownership and disconnected-grid behavior before enabling automatic submission.

# Development verification: 2026-11-14 (Singulo 0.19.0 escrow)

## Exact inputs
- Singulo `feature/0.19.0` commit `b43db6ba03a8b22b433186989fec75b97e52faf8` (pushed to origin; unreleased), built from a `git archive` of that commit by `fetchDependencies`.
- AE2 19.2.19 and GuideME 21.1.1: official URLs and SHA-256 in `tools/dependencies.json`.
- Java 21 / Minecraft 1.21.1 / NeoForge 21.1.256.

## Passed locally
- `gradlew build` and `tools/check_distribution.py` (PASS, plus the expected NOT PUBLISHABLE notice for the unreleased Singulo).
- 18/18 addon GameTests with real AE2, GuideME and Singulo loaded, including:
  - a real moving android crafting 4 batched AE2 pushes (1 log -> 4 planks each) as one escrow request for a remote ME interface: 16 planks claimed by request id, the shared chest's 32 logs untouched, the escrow acknowledged and released (about 286 game ticks);
  - breaking an ME interface with a submitted request: cancelled, both escrowed logs dropped at its position exactly once, request acknowledged;
  - recipe export limited to escrow-eligible crafting recipes, batching and limits, persistence, no item capabilities.

## Not yet verified
- Interactive client: an AE2 crafting CPU job through a powered ME network into the interface (`pushPattern` path) and back into ME storage.
- Remote CI with the source-built Singulo dependency.
- No release, merge, tag, push or publication was performed.

## Update: escrow trip-size fix (Singulo `bed2d08b`, local branch `fix/0.19.0-escrow-trip-size`)
- In game, a large AE2 order (102 fences at a Quantum Android Workbench) made the android fetch and return materials forever. Cause in Singulo: escrow trips were sized as if products could spill into the workbench buffer (which escrow disables), and partial stacks were not merged before products went into the carry.
- Reproduced by two new addon GameTests (1024 planks at an Android Workbench, 102 fences at a Quantum Android Workbench): both fail on `b43db6ba`, pass on `bed2d08b`.
- Singulo full GameTest suite on `bed2d08b`: 797/797. Addon: build, distribution check, 20/20 GameTests via the plain pinned build.

## Update: ME network as Singulo storage (Singulo `9dd8251d`, local branch `feature/0.19.0-shared-storage`)
- New addon GameTest: two ME interfaces of one powered network (creative energy cell, drive with a 1k cell) touch the same Supply Port; the console counts 100 inserted cobblestone once (200 when the key is deliberately made per interface), extraction/insertion go through ME, a face towards the drive and another owner's interface show nothing.
- Singulo full GameTest suite on `9dd8251d`: 797/797. Addon: 21/21 GameTests via the plain pinned build.

## Update: chained crafting in one order (Singulo `74652737`)
- Singulo: private escrow requests may run a chain of plain crafting steps; new real-worker test (8 sticks from 1 log in one request); full suite 798/798.
- Addon: chained patterns (exact ratio, e.g. 5 logs -> 12 fences; cycles never export self-consuming patterns); a real AE2 crafting calculation pushes the chain once when logs are stored and falls back to the single-step fence pattern when only planks and sticks are stored; a real android crafts a chained push as one order (planks, sticks, fences) leaving shared stock untouched. 24/24 GameTests via the plain pinned build.

## Update: deterministic chain priority and large-slot fetch (Singulo `d09ff94f`)
- Root cause of separate planks/sticks/fence orders in game: AE2 keeps one item's patterns in a HashSet and sorts only by provider priority, so the chain/step order was random. Chains now come from a second internal node with priority 1. The AE2 calculation test (logs only -> chain; planks+sticks only -> step; logs+planks+sticks -> chain) passed 3 runs in a row.
- Root cause of "waiting for space" when ordering from Singulo with items in ME: Singulo extracted at most one stack per slot. New addon GameTest (200 sticks from 200 planks stored only in ME) failed before the Singulo fix (recipe mismatch after a partial fetch) and passes after it. Singulo full suite 798/798.
- Full player path GameTest: an AE2 crafting job for 24 fences on a real crafting CPU (1k crafting storage) pushes the chain pattern twice (10 logs); the interface submits one escrow request; a real android crafts planks, sticks and fences in one Singulo order; 24 fences arrive in ME and exactly 10 of 64 logs are used. 26/26 GameTests.

## Update: interface only, panel, AE2 screen
- Bridge removed. Shared logic (`AndroidInterfaceLogic`) hosts both the block and the new cable panel.
- GameTests 24/24: all interface tests, plus the panel on a real cable next to a Supply Port (joins the grid, 3 recipes + 2 chains, ME seen by the Supply Port only through the faced side, priority stored, AE2 part locator finds the panel as menu host, status correct) and the status record's network round trip.
- The screen itself (client rendering) cannot be exercised by GameTests; the client was started to check model/style loading.

## Update: emitter, monitor, keep in stock, machines and fluids
- New addon GameTests (30/30 total): status emitter follows the network (stuck without console, quiet once set); order monitor shows a batching order; keep in stock with a real crafting CPU and android makes planks until ME holds 8 (setting persists); a real android smelts raw iron in a furnace through the interface queue; AE2 fluid inputs become Singulo fluid samples, batch by amount, and claimed fluids reach an ME fluid cell.
- Singulo: escrow requests may run machine steps and carry fluids (custody keeps one sample per fluid in mB; tanks fetch from and return to custody; partial claims in mB; cancelled workers return tank fluids). New Singulo GameTests: a real android runs a furnace step in private custody; custody keeps fluid amounts.

## Review correction: private escrow capability boundary

The earlier machine/furnace success notes describe the development implementation, not the supported
release boundary. Machine custody was subsequently rejected by Singulo's safety review. Applied now
omits processing recipes and locally rejects stale machine-only requests before transfer, returning
all held inputs. The machine GameTest now verifies non-advertisement, no custody transfer, complete
input refund and untouched furnace fuel. Plain crafting and fluid custody remain tested separately.

## Tagged 0.19.0 base compatibility

Singulo CI 43628318212 actual JAR, SHA-256
`ec45f7d6a6a9dbac654c7a2048df085ca709f055868def39aeee4132d44ed942`,
was downloaded and verified. Its source tree matches tag v0.19.0 / merge
`0f6fcff7645a2a5066a072acce55d4a03884080e`. Applied build, all 40 GameTests
and distribution checks passed against those bytes. This used a local dependency override for
compatibility testing only; final published URL/hash pinning and release-ready validation remain pending.
