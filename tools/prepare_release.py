"""Validate published dependency pins and tested distribution bytes; never upload or publish."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys

parser = argparse.ArgumentParser()
parser.add_argument('--loader', choices=('forge', 'neoforge'), required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
pins = json.loads((root / 'tools/dependencies.json').read_text())
verified = {}
for name, pin in pins.items():
    if pin.get('loader', args.loader) != args.loader:
        continue
    if pin.get('unreleased') or not pin.get('sha256') or not pin.get('url', '').startswith('https://'):
        raise SystemExit(f'NOT RELEASE READY: {name} needs a published HTTPS URL and SHA-256')
    data = (root / 'libs' / name).read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    if digest != pin['sha256']:
        raise SystemExit(f'Dependency hash mismatch: {name}')
    verified[name] = {'url': pin['url'], 'sha256': digest}
subprocess.run([sys.executable, str(root / 'tools/check_distribution.py'), '--loader', args.loader], check=True)
subprocess.run([sys.executable, str(root / 'tools/check_gametest_log.py'),
                str(root / 'build' / args.loader / 'gametest/logs/latest.log')], check=True)
p = dict(s.split('=', 1) for s in (root / 'gradle.properties').read_text().splitlines()
         if s and not s.startswith('#'))
suffix = 'F' if args.loader == 'forge' else 'N'
name = f"{p['mod_archive_name']}-{p['mod_version']}-{p['minecraft_version']}{suffix}.jar"
data = (root / 'build' / args.loader / 'libs' / name).read_bytes()
receipt = {'schema': 1, 'published': False, 'filename': name, 'release_type': 'beta', 'loader': args.loader,
           'git_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
           'sha256': hashlib.sha256(data).hexdigest(), 'sha512': hashlib.sha512(data).hexdigest(),
           'dependencies': verified}
manifest = root / 'build' / args.loader / 'release-manifest.json'
manifest.write_text(json.dumps(receipt, indent=2) + '\n')
print(f'RELEASE READY (not published): {name}; {manifest}')
