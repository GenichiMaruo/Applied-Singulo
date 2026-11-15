"""Validate release-ready bytes and write a receipt; never upload or publish."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys
import argparse
import os

root = Path(__file__).resolve().parents[1]
args = argparse.ArgumentParser()
args.add_argument("--gametest-log", type=Path, default=root / "build/integration-gametest.log")
options = args.parse_args()
subprocess.run([sys.executable, str(root / "tools/check_distribution.py")], check=True)
subprocess.run([sys.executable, str(root / "tools/check_gametest_log.py"), str(options.gametest_log)], check=True)
pins = json.loads((root / "tools/dependencies.json").read_text())
verified = {}
for name, pin in pins.items():
    if pin.get("unreleased"):
        raise SystemExit(f"NOT RELEASE READY: {name} is not yet verified generally public")
    if not pin.get("sha256") or not pin.get("url", "").startswith("https://"):
        raise SystemExit(f"NOT RELEASE READY: {name} needs a published HTTPS URL and SHA-256")
    data = (root / "libs" / name).read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != pin["sha256"]:
        raise SystemExit(f"Dependency hash mismatch: {name}")
    verified[name] = {"url": pin["url"], "sha256": digest}
head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
if os.environ.get("GITHUB_ACTIONS") != "true" or os.environ.get("GITHUB_SHA") != head:
    raise SystemExit("NOT RELEASE READY: use the fresh build/test/manifest artifact from CI for this commit")
subprocess.run(["git", "diff", "--exit-code", "HEAD", "--"], cwd=root, check=True)
p = dict(s.split("=", 1) for s in (root / "gradle.properties").read_text().splitlines()
         if s and not s.startswith("#"))
name = f"{p['mod_archive_name']}-{p['mod_version']}-{p['minecraft_version']}{p['mod_loader_suffix']}.jar"
data = (root / "build/libs" / name).read_bytes()
receipt = {"schema": 1, "published": False, "filename": name, "release_type": "beta",
           "git_commit": head, "ci_run_id": os.environ.get("GITHUB_RUN_ID"),
           "gametest_log_sha256": hashlib.sha256(options.gametest_log.read_bytes()).hexdigest(),
           "sha256": hashlib.sha256(data).hexdigest(), "sha512": hashlib.sha512(data).hexdigest(),
           "dependencies": verified}
(root / "build/release-manifest.json").write_text(json.dumps(receipt, indent=2) + "\n")
print(f"RELEASE READY (not published): {name}")
