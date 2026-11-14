"""Validate release-ready bytes and write a receipt; never upload or publish."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
subprocess.run([sys.executable, str(root / "tools/check_distribution.py")], check=True)
pins = json.loads((root / "tools/dependencies.json").read_text())
verified = {}
for name, pin in pins.items():
    if pin.get("unreleased") or not pin.get("sha256") or not pin.get("url", "").startswith("https://"):
        raise SystemExit(f"NOT RELEASE READY: {name} needs a published HTTPS URL and SHA-256")
    data = (root / "libs" / name).read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != pin["sha256"]:
        raise SystemExit(f"Dependency hash mismatch: {name}")
    verified[name] = {"url": pin["url"], "sha256": digest}
p = dict(s.split("=", 1) for s in (root / "gradle.properties").read_text().splitlines()
         if s and not s.startswith("#"))
name = f"{p['mod_archive_name']}-{p['mod_version']}-{p['minecraft_version']}{p['mod_loader_suffix']}.jar"
data = (root / "build/libs" / name).read_bytes()
receipt = {"schema": 1, "published": False, "filename": name, "release_type": "beta",
           "git_commit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
           "sha256": hashlib.sha256(data).hexdigest(), "sha512": hashlib.sha512(data).hexdigest(),
           "dependencies": verified}
(root / "build/release-manifest.json").write_text(json.dumps(receipt, indent=2) + "\n")
print(f"RELEASE READY (not published): {name}")
