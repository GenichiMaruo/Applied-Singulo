"""Promote exact successful maintained-branch CI bytes; never rebuild or upload."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tomllib
import zipfile

REPO = 'GenichiMaruo/Applied-Singulo'

def api(endpoint):
    return json.loads(subprocess.check_output(['gh', 'api', f'repos/{REPO}/{endpoint}'], text=True))

def git(source, *args):
    return subprocess.check_output(['git', '-C', str(source), *args], text=True).strip()

def inspect(run_id, tag, source, loader):
    if loader not in ('forge', 'neoforge'): raise RuntimeError('Invalid loader')
    suffix = 'F' if loader == 'forge' else 'N'
    props = dict(x.split('=', 1) for x in (source / 'gradle.properties').read_text().splitlines() if '=' in x and not x.startswith('#'))
    if tag != 'v' + props['mod_version'] + '-1.20.1' + suffix or props['minecraft_version'] != '1.20.1':
        raise RuntimeError('Tag/version/target mismatch')
    commit = git(source, 'rev-parse', 'HEAD')
    if git(source, 'status', '--porcelain', '--untracked-files=all', '--ignored=matching'):
        raise RuntimeError('Release source is not clean')
    ref = api(f'git/ref/tags/{tag}')['object']
    while ref['type'] == 'tag':
        ref = api('git/tags/' + ref['sha'])['object']
    if ref['type'] != 'commit' or ref['sha'] != commit:
        raise RuntimeError('Tag/source mismatch')
    if api(f'compare/{commit}...1.20.1')['status'] not in ('ahead', 'identical'):
        raise RuntimeError('Tag is not on maintained branch')
    run = api(f'actions/runs/{run_id}')
    if not (run['status'] == 'completed' and run['conclusion'] == 'success' and run['event'] == 'push'
            and run['head_sha'] == commit and run['head_branch'] == '1.20.1'
            and run['path'] == '.github/workflows/build.yml' and run['head_repository']['full_name'] == REPO
            and run['run_attempt'] == 1):
        raise RuntimeError('Require first-attempt successful maintained-branch Build for exact tag commit')
    jobs = api(f'actions/runs/{run_id}/jobs?per_page=100')['jobs']
    if len(jobs) != 2 or {j['name'] for j in jobs} != {'integration (forge)', 'integration (neoforge)'} or any(j['conclusion'] != 'success' for j in jobs):
        raise RuntimeError('Unexpected/missing integration job')
    rows = api(f'actions/runs/{run_id}/artifacts?per_page=100')
    matches = [a for a in rows['artifacts'] if a['name'] == f'applied-singulo-1.20.1-{loader}-development']
    if rows['total_count'] > 100 or len(matches) != 1 or matches[0]['expired']:
        raise RuntimeError('Missing/ambiguous/expired artifact')
    artifact = matches[0]
    if not re.fullmatch('sha256:[0-9a-f]{64}', artifact.get('digest', '')):
        raise RuntimeError('Missing artifact digest')
    return {'repo':REPO, 'run_id':run_id, 'commit':commit, 'tag':tag, 'version':props['mod_version'], 'loader':loader, 'artifact':artifact}

def promote(metadata, source):
    loader = metadata['loader']
    if loader not in ('forge', 'neoforge'): raise RuntimeError('Invalid loader')
    suffix = 'F' if loader == 'forge' else 'N'
    artifact = metadata['artifact']
    # Obtain the exact artifact archive by immutable ID and verify its GitHub digest before extraction.
    data = subprocess.check_output(['gh', 'api', f"repos/{REPO}/actions/artifacts/{artifact['id']}/zip"])
    if 'sha256:' + hashlib.sha256(data).hexdigest() != artifact['digest']:
        raise RuntimeError('Artifact archive digest mismatch')
    import io
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = archive.namelist()
        if len(set(names)) != len(names) or any(n.startswith('/') or '..' in Path(n).parts or '\\' in n for n in names):
            raise RuntimeError('Unsafe artifact paths')
        manifest_names = [n for n in names if n.endswith('release-manifest.json')]
        if len(manifest_names) != 1:
            raise RuntimeError('Missing unique release receipt')
        receipt = json.loads(archive.read(manifest_names[0]))
        name = f"Applied-Singulo-{metadata['version']}-1.20.1{suffix}.jar"
        if (receipt['filename'] != name or receipt['git_commit'] != metadata['commit']
                or str(receipt['ci_run_id']) != str(metadata['run_id']) or receipt['published'] is not False
                or receipt['release_type'] != 'beta' or receipt['loader'] != loader):
            raise RuntimeError('Receipt provenance mismatch')
        jars = [n for n in names if n.endswith('.jar')]
        logs = [n for n in names if n.endswith('gametest/logs/latest.log')]
        if len(jars) != 1 or Path(jars[0]).name != name or len(logs) != 1:
            raise RuntimeError('Unexpected JAR/log set')
        jar, log = archive.read(jars[0]), archive.read(logs[0])
        if hashlib.sha256(jar).hexdigest() != receipt['sha256'] or hashlib.sha512(jar).hexdigest() != receipt['sha512'] or hashlib.sha256(log).hexdigest() != receipt['gametest_log_sha256']:
            raise RuntimeError('Receipt content hash mismatch')
        text = log.decode('utf-8', errors='replace')
        if '50 GAME TESTS COMPLETE' not in text or 'All 50 required tests passed' not in text:
            raise RuntimeError('Missing 50-test success')
        pins = json.loads((source / 'tools/dependencies.json').read_text())
        expected = {}
        for dep, pin in pins.items():
            if pin.get('loader', loader) != loader: continue
            if pin.get('unreleased') or pin.get('pending_public') or not pin.get('url','').startswith('https://') or not re.fullmatch('[0-9a-f]{64}',pin.get('sha256','')):
                raise RuntimeError('Dependency not public/pinned')
            expected[dep] = {'url':pin['url'], 'sha256':pin['sha256']}
        if receipt['dependencies'] != expected:
            raise RuntimeError('Dependency receipt mismatch')
        with zipfile.ZipFile(io.BytesIO(jar)) as z:
            mod = tomllib.loads(z.read('META-INF/mods.toml').decode())
            props = dict(x.split('=',1) for x in (source/'gradle.properties').read_text().splitlines() if '=' in x and not x.startswith('#'))
            deps = {d['modId']:d for d in mod['dependencies']['applied_singulo']}
            if mod['mods'][0]['modId'] != 'applied_singulo' or mod['mods'][0]['version'] != metadata['version'] or deps['singulo']['versionRange'] != props['singulo_version_range'] or deps['minecraft']['versionRange'] != '[1.20.1]':
                raise RuntimeError('JAR metadata mismatch')
            if deps['ae2']['versionRange'] != props['ae2_version_range'] or deps['forge']['versionRange'] != '[' + props['forge_version' if loader == 'forge' else 'neoforge_version'] + ',)' or not all(d.get('mandatory') is True for d in deps.values()):
                raise RuntimeError('Loader/AE2 dependency metadata mismatch')
            if not any(n.endswith('.class') for n in z.namelist()) or len(z.namelist()) != len(set(z.namelist())) or any(int.from_bytes(z.read(n)[6:8], 'big') != 61 for n in z.namelist() if n.endswith('.class')):
                raise RuntimeError('Duplicate entries or wrong Java class version')
            if any(n.startswith(('appeng/','io/github/genichimaruo/singulo/')) or n.endswith('.jar') or '/gametest/' in n for n in z.namelist()):
                raise RuntimeError('Bundled dependency or test code')
        out = Path('release-artifacts'); out.mkdir()
        (out/name).write_bytes(jar)
        (out/'release-manifest.json').write_text(json.dumps(receipt,indent=2)+'\n')
        (out/'provenance.json').write_text(json.dumps(metadata,indent=2)+'\n')
        print('Verified exact CI artifact, receipt, dependencies and 50 GameTests:', name, receipt['sha256'])

def main():
    p=argparse.ArgumentParser(); p.add_argument('mode',choices=['check','promote']); p.add_argument('--repo',choices=[REPO],required=True); p.add_argument('--run',type=int,required=True); p.add_argument('--tag',required=True); p.add_argument('--source',type=Path,required=True); p.add_argument('--loader',choices=['forge','neoforge'],required=True); a=p.parse_args()
    if a.run<=0 or not re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+-1\.20\.1[FN]',a.tag): raise RuntimeError('Invalid tag/run')
    metadata=inspect(a.run,a.tag,a.source,a.loader)
    path=Path('verified-release.json')
    if a.mode=='check':
        path.write_text(json.dumps(metadata,indent=2)+'\n')
        if os.environ.get('GITHUB_OUTPUT'):
            with open(os.environ['GITHUB_OUTPUT'],'a') as f: f.write('neoforge_artifact='+metadata['artifact']['name']+'\n')
    else:
        if metadata!=json.loads(path.read_text()): raise RuntimeError('Provenance changed')
        promote(metadata,a.source)

if __name__=='__main__': main()
