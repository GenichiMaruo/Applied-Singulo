# Verified CurseForge publication (1.20.1)

Publication requires explicit authorization. Target is Applied Singulo project 1806522, Beta only.

1. Verify publicly available dependency URLs and hashes in tools/dependencies.json, then merge the reviewed compatibility PR into 1.20.1.
2. Wait for the first-attempt push Build on that exact merge commit. Both integration (forge) and integration (neoforge) must pass all 50 GameTests and produce their release receipts.
3. Create immutable tags v<mod_version>-1.20.1F and v<mod_version>-1.20.1N at that commit. Never move an existing tag.
4. Dispatch Publish CurseForge from 1.20.1 with verified_run_id, the matching release_tag, loader (forge or neoforge), and beta. Dispatch one loader at a time, waiting for complete termination before the other. No JAR is rebuilt.
5. The gate verifies exact maintained-branch CI commit, immutable artifact archive digest, loader-specific receipt, JAR metadata, public dependency hashes, and all test results. It confirms destination 1806522 before a permanent create-only upload lock and asset-free draft reservation.
6. After success, verify author status, public file page and downloaded JAR hash. Submission is not moderation approval.

Never automatically retry an uncertain upload. Inspect the permanent applied-singulo-cf-upload-lock-v1 tag, all draft/published releases and the author file list. Never delete/adopt a lock or duplicate an accepted upload. GitHub releases contain notes and links, never distribution JARs. No token is printed or stored in repository files.

Offline safety tests: python3 -m unittest discover -s tools -p 'test_*.py'.
