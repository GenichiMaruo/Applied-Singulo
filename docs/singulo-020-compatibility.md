# Singulo 0.20.0 compatibility preparation (1.20.1)

Applied Singulo 0.1.1 Beta requires Singulo [0.20.0,0.21.0). Both loaders are pinned to approved, generally public CurseForge files: Forge 9660568 and NeoForge 9660556, from final core merge f05ed56af936e1285708ee6ef9f3b67fbb81eef2. Their official CDN bytes match the independent CI SHA-256 records.

Keep existing AE2 and GuideME pins. No merge or publication until the final core port SHA is reviewed, published dependency URLs and hashes are fixed, all build/distribution/GameTests pass in fresh CI, and independent review completes. The 1.21.1-only publishing workflow must not be used for this target.

## Deterministic GameTest worlds

Legacy Forge's GameTestMain enters the dedicated-server Main startup path, which reads server.properties and creates dimensions before constructing GameTestServer. It does not use vanilla's flat-world factory. Missing properties therefore produced random normal terrain, including lava near physical-worker fixtures. An older local run captured two worker lava deaths and order cancellation; a later fresh CI run cancelled a large order, but that run did not retain its world or death diagnostics, so its immediate cause is not proven.

The earlier hypothesis that only a reused or contaminated world caused the failures was incomplete. A new world alone is not a deterministic environment.

prepareGameTestWorld now writes explicit flat layers, plains biome, fixed seed 1, no lakes/features/structures, and a unique test-only level name for every run under build/<loader>/gametest. Prior worlds are preserved, never deleted. Normal user/client worlds are untouched. Moving-worker fixtures assert survival with position, damage source and surrounding blocks. CI preserves logs, generated properties and level.dat even on failure. Validate the recorded generator type and seed as well as all 48 tests before publication; no timeout extension, invulnerability, weaker output assertion or production behavior change is used.
