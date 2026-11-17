# CurseForge publication: Minecraft 26.1.2

This branch publishes `Applied-Singulo-<version>-26.1.2N.jar` as **Beta**, for NeoForge and Java 25, to Applied Singulo (project 1806522). Never reuse a 1.21.1 or 1.20.1 artifact or tag.

1. Review and merge the release PR into `26.1.2`. Wait for the **first-attempt successful push Build** on that exact maintained-branch commit, including all 48 GameTests and production-JAR validation. PR runs and feature-branch runs cannot be promoted.
2. Verify every dependency is generally public, pinned by HTTPS URL and SHA-256 in `tools/dependencies.json`, and the CI artifact includes its `release-manifest.json`. Pending markers stop promotion.
3. Create immutable `v<version>-26.1.2N` on that exact commit. Never move an existing tag. Check existing CurseForge files, GitHub releases (including drafts), and permanent upload-lock refs before starting.
4. Dispatch **Publish CurseForge** from `26.1.2` with that Build `verified_run_id`, the `release_tag`, and `release_type=beta`. The `curseforge` environment provides `CURSEFORGE_TOKEN` and `CURSEFORGE_PROJECT_ID` (must equal 1806522). Never print credentials.
5. The workflow verifies tag/commit, maintained branch, workflow identity, immutable artifact ID/digest, receipt, JAR hashes, dependencies and tests, then promotes the **same CI bytes without rebuilding**. It creates a permanent create-only lock before the asset-free draft and before the single upload attempt.
6. After upload, verify author status, the public file page and an actual download hash. Workflow success alone does not mean moderation has approved the file. GitHub Releases contain notes/links only, never JARs.

Do not dispatch multiple publication workflows at once. On failure or uncertain results, do **not** retry the upload, delete/adopt a lock, move a tag, overwrite files, or create another release to evade the guard. Inspect the permanent lock, draft/published release, `cf-lock-*` receipt and CurseForge registration first. Escalate uncertain uploads for manual reconciliation; an accepted upload can return an error.

Offline checks: `python3 -B -m unittest discover -s tools -p 'test_*.py'`. These tests never upload, create tags or contact the network.
