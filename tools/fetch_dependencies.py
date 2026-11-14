"""Download only pinned official dependencies, rejecting changed bytes.

Entries pinned to a source commit (unreleased APIs) have no published file; Gradle's
fetchDependencies task builds them from that exact commit.
"""
from pathlib import Path
import hashlib
import json
import urllib.request
root = Path(__file__).resolve().parents[1]
out = root / "libs"
out.mkdir(exist_ok=True)
for name, expected in json.loads((root / "tools/dependencies.json").read_text()).items():
    if "sha256" not in expected:
        print(f"Skipped {name}: unreleased, built by Gradle from commit {expected['commit'][:12]}")
        continue
    path = out / name
    data = path.read_bytes() if path.exists() else urllib.request.urlopen(urllib.request.Request(expected["url"], headers={"User-Agent": "Applied-Singulo-build"}), timeout=120).read()
    if hashlib.sha256(data).hexdigest() != expected["sha256"]:
        raise SystemExit(f"Hash mismatch: {name}; refusing dependency")
    if not path.exists():
        path.write_bytes(data)
    print(f"Verified {name}")
