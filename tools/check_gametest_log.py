"""Reject zero-test, missing, failing, or datapack-broken runs."""
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
expected_addon = 46
# Minecraft 26 also runs its built-in always_pass test.
expected_total = expected_addon + 1
assert re.search(r"Running test environment 'applied_singulo:default' batch 0 \(" + str(expected_addon) + r" tests\)", text), "Addon test inventory was not executed"
assert f"{expected_total} GAME TESTS COMPLETE" in text, "Expected test count was not executed"
assert f"All {expected_total} required tests passed" in text, "Required GameTests did not all pass"
assert "Couldn't parse data file" not in text, "Datapack data failed to load"
print(f"PASS: all {expected_addon} addon GameTests and one Minecraft built-in test executed")
