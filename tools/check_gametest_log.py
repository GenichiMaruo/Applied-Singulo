"""Reject zero-test, missing, or partially failing runs."""
from pathlib import Path
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
expected = 30
assert f"{expected} GAME TESTS COMPLETE" in text, "Expected test count was not executed"
assert f"All {expected} required tests passed" in text, "Required GameTests did not all pass"
print(f"PASS: all {expected} addon GameTests executed")
