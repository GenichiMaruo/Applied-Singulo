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
