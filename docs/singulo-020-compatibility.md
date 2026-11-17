# Singulo 0.20.0 compatibility preparation (1.20.1)

Applied Singulo 0.1.1 Beta requires Singulo [0.20.0,0.21.0). Both loaders are pinned to approved, generally public CurseForge files: Forge 9660568 and NeoForge 9660556, from final core merge f05ed56af936e1285708ee6ef9f3b67fbb81eef2. Their official CDN bytes match the independent CI SHA-256 records.

Keep existing AE2 and GuideME pins. No merge or publication until the final core port SHA is reviewed, published dependency URLs and hashes are fixed, all build/distribution/GameTests pass in fresh CI, and independent review completes. The 1.21.1-only publishing workflow must not be used for this target.

## Local world isolation

Use a fresh, dedicated GameTest world for final compatibility validation; never point the run at a player world. CI starts in a fresh workspace. Preserve a failed local test world before replacing it so environment-dependent failures can be investigated.

During 0.20 preparation, an old local Forge test world caused two physical-worker tests to fail: both workers died in flowing lava, cancelling their orders. Diagnostic death/escrow stacks and positions were captured; independent region inspection found lava in the affected old chunks and none in the fresh comparison chunks. With the normal pinned dependency JAR and unchanged assertions, all 48 tests passed twice in the fresh comparison world. No production behavior change, timeout extension or invulnerability workaround was used. The original lava's provenance was not established; do not infer a Singulo order API defect from that contaminated test world.
