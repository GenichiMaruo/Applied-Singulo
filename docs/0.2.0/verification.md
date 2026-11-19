# 0.2.0 / Minecraft 1.21.1 NeoForge verification

Development verification on 2026-11-19 (not a release receipt):
- Candidate Singulo 0.21.0 JAR SHA-256 `6d6ccc35a88520de2bb065473ae6dbf05d5744fe45cfce775d36da0ca847d86d`.
- Local dependency override only; NOT verified public production dependency.
- `build`: PASS, Java 21 / NeoForge 21.1.256.
- `tools/check_distribution.py`: PASS; test fixture excluded, no bundled dependencies.
- `runGameTestServer`: all **47** required GameTests passed (twice; final run uses powered ME provider).
- `tools/check_gametest_log.py`: PASS with strict count 47.
- Publication/receipt Python tests: **14 passed**.

New end-to-end regression: powered Android ME Interface exports the validated 1000 mB lava alternative,
accepts two actual provider pushes (each lava + obsidian), batches them into one private request,
a tank-equipped Android crafts two crying obsidian, and no synthetic empty bucket is returned.
The separate shared chest remains untouched. Existing fluid cancellation, partial return, persistence,
orphan recovery and replay custody regressions remain in the required suite.

Also verifies reusable-tool and liquid-main-product exclusion. This release does not promise private
machine/procedure execution or liquid main outputs. It retains original physical-bucket patterns.

Before merge/publication: pin final generally-public core bytes and rerun the full suite, then use the
exact maintained-branch CI artifact/receipt. All Applied variants must be ready before CF publication.
