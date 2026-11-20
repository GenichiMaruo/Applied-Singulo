"""Validate each reobfuscated 1.20.1 artifact, never the development JAR."""
from pathlib import Path
import argparse
import hashlib
import json
import tomllib
import zipfile
parser = argparse.ArgumentParser()
parser.add_argument('--loader', choices=('forge', 'neoforge'), required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
p = dict(s.split('=', 1) for s in (root / 'gradle.properties').read_text().splitlines() if s and not s.startswith('#'))
suffix = 'F' if args.loader == 'forge' else 'N'
name = f"{p['mod_archive_name']}-{p['mod_version']}-{p['minecraft_version']}{suffix}.jar"
artifact = root / 'build' / args.loader / 'libs' / name
with zipfile.ZipFile(artifact) as z:
    names = z.namelist()
    assert len(names) == len(set(names)), 'duplicate entries'
    assert 'LICENSE' in names
    assert not any('/gametest/' in n for n in names)
    assert 'data/applied_singulo/structures/empty.nbt' not in names
    assert not any(n.startswith("data/applied_singulo_test/") for n in names)
    assert 'io/github/genichimaruo/appliedsingulo/AppliedSingulo.class' in names
    m = tomllib.loads(z.read('META-INF/mods.toml').decode())
    assert 'META-INF/neoforge.mods.toml' not in names
    assert m['mods'][0]['modId'] == 'applied_singulo'
    assert m['mods'][0]['version'] == p['mod_version']
    assert m['mods'][0]['logoFile'] == 'applied-singulo.png'
    assert z.read('applied-singulo.png') == (root / 'src/main/resources/applied-singulo.png').read_bytes()
    d = {v['modId']: v for v in m['dependencies']['applied_singulo']}
    assert set(d) == {'minecraft', 'forge', 'singulo', 'ae2'}
    assert all(v['mandatory'] is True for v in d.values())
    assert d['minecraft']['versionRange'] == '[1.20.1]'
    assert d['singulo']['versionRange'] == p['singulo_version_range']
    assert d['ae2']['versionRange'] == p['ae2_version_range']
    version = p['forge_version' if args.loader == 'forge' else 'neoforge_version']
    assert d['forge']['versionRange'] == f'[{version},)'
    assert json.loads(z.read('pack.mcmeta'))['pack']['pack_format'] == 15
    assert not any(n.startswith(('appeng/', 'io/github/genichimaruo/singulo/')) or n.endswith('.jar') for n in names)
    for n in names:
        if n.endswith('.class'): assert int.from_bytes(z.read(n)[6:8], 'big') == 61, n
        if n.startswith('data/applied_singulo/recipes/') and n.endswith('.json'):
            result = json.loads(z.read(n)).get('result')
            assert isinstance(result, dict) and 'item' in result and 'id' not in result, n
print(f'PASS: {name}; metadata, Java 17, logo bytes, recipes, no bundled dependencies')
print('SHA-256: ' + hashlib.sha256(artifact.read_bytes()).hexdigest())
unreleased = [k for k, v in json.loads((root / 'tools/dependencies.json').read_text()).items()
              if v.get('unreleased') and v.get('loader', args.loader) == args.loader]
if unreleased:
    print('NOT PUBLISHABLE: unreleased dependency: ' + ', '.join(unreleased))
