"""Download only pinned official dependencies, rejecting changed bytes."""
from pathlib import Path
import hashlib
import json
import urllib.request
root = Path(__file__).resolve().parents[1]
out = root / "libs"
out.mkdir(exist_ok=True)
for name, expected in json.loads((root / "tools/dependencies.json").read_text()).items():
    path = out / name
    data = path.read_bytes() if path.exists() else urllib.request.urlopen(expected["url"], timeout=120).read()
    if hashlib.sha256(data).hexdigest() != expected["sha256"]:
        raise SystemExit(f"Hash mismatch: {name}; refusing dependency")
    if not path.exists():
        path.write_bytes(data)
    print(f"Verified {name}")
