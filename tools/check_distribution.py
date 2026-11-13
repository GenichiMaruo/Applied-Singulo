"""Bootstrap packaging check, not a gameplay test."""
from pathlib import Path
import json
import tomllib
import zipfile
root = Path(__file__).resolve().parents[1]
p = dict(s.split("=", 1) for s in (root / "gradle.properties").read_text().splitlines() if s and not s.startswith("#"))
name = f"{p['mod_archive_name']}-{p['mod_version']}-{p['minecraft_version']}{p['mod_loader_suffix']}.jar"
with zipfile.ZipFile(root / "build/libs" / name) as z:
    names = z.namelist()
    assert "LICENSE" in names
    assert not any("/gametest/" in n for n in names)
    assert "data/applied_singulo/structure/empty.nbt" not in names
    assert "io/github/genichimaruo/appliedsingulo/AppliedSingulo.class" in names
    m = tomllib.loads(z.read("META-INF/neoforge.mods.toml").decode())
    assert m["mods"][0]["modId"] == "applied_singulo"
    assert m["mods"][0]["version"] == p["mod_version"]
    d = {v["modId"]: v for v in m["dependencies"]["applied_singulo"]}
    assert set(d) == {"minecraft", "neoforge", "singulo", "ae2"}
    assert all(v["type"] == "required" for v in d.values())
    assert d["minecraft"]["versionRange"] == "[1.21.1]"
    assert d["singulo"]["versionRange"] == p["singulo_version_range"]
    assert d["ae2"]["versionRange"] == p["ae2_version_range"]
    assert json.loads(z.read("pack.mcmeta"))["pack"]["pack_format"] == 34
    assert not any(n.startswith(("appeng/", "io/github/genichimaruo/singulo/")) or n.endswith(".jar") for n in names)
    for n in names:
        if n.endswith(".class"): assert int.from_bytes(z.read(n)[6:8], "big") == 65, n
print(f"PASS: {name}; metadata, Java 21, no bundled dependencies")
