"""Verify the actual generated test world, not merely desired properties."""
import argparse
import gzip
import io
from pathlib import Path
import re
import struct

LAYERS = [{'block':'minecraft:bedrock','height':1}, {'block':'minecraft:dirt','height':2}, {'block':'minecraft:grass_block','height':1}]

def read_nbt(path):
    with gzip.open(path, 'rb') as stream:
        raw = stream.read(8 * 1024 * 1024 + 1)
    if len(raw) > 8 * 1024 * 1024: raise ValueError('Oversized level.dat')
    source = io.BytesIO(raw)
    def take(n):
        if n < 0 or n > len(raw): raise ValueError('Invalid NBT length')
        b = source.read(n)
        if len(b) != n: raise ValueError('Truncated NBT')
        return b
    def number(fmt): return struct.unpack('>' + fmt, take(struct.calcsize('>' + fmt)))[0]
    def string(): return take(number('H')).decode('utf-8')
    def value(kind, depth=0):
        if depth > 64: raise ValueError('NBT nesting too deep')
        if kind in (1,2,3,4,5,6): return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[kind])
        if kind == 7: return take(number('i'))
        if kind == 8: return string()
        if kind == 9:
            item_kind, count = number('B'), number('i')
            if count < 0 or count > 100000: raise ValueError('Invalid list size')
            return [value(item_kind, depth + 1) for _ in range(count)]
        if kind == 10:
            result = {}
            while True:
                item_kind = number('B')
                if item_kind == 0: return result
                name = string()
                if name in result: raise ValueError('Duplicate NBT name')
                result[name] = value(item_kind, depth + 1)
        if kind in (11,12):
            count = number('i')
            if count < 0 or count > 100000: raise ValueError('Invalid array size')
            return [number('i' if kind == 11 else 'q') for _ in range(count)]
        raise ValueError('Unknown NBT kind')
    kind = number('B')
    if kind != 10: raise ValueError('Root must be compound')
    string()
    return value(kind)

def validate(root):
    settings = root['Data']['WorldGenSettings']
    if settings['seed'] != 1 or settings['generate_features'] != 0: raise ValueError('Wrong seed or structures enabled')
    generator = settings['dimensions']['minecraft:overworld']['generator']
    if generator['type'] != 'minecraft:flat': raise ValueError('Non-flat test world')
    flat = generator['settings']
    if flat['layers'] != LAYERS or flat['biome'] != 'minecraft:plains': raise ValueError('Wrong flat layers or biome')
    if flat['lakes'] != 0 or flat['features'] != 0 or flat['structure_overrides'] != []: raise ValueError('Unexpected world features')

def check(directory):
    directory = directory.resolve()
    props = dict(line.split('=', 1) for line in (directory / 'server.properties').read_text(encoding='utf-8').splitlines() if line and not line.startswith('#') and '=' in line)
    name = props.get('level-name', '')
    if not re.fullmatch(r'applied-singulo-flat-[0-9a-f-]{36}', name): raise ValueError('Not a dedicated generated test world')
    world = (directory / name).resolve()
    if world.parent != directory: raise ValueError('Test world escaped its directory')
    validate(read_nbt(world / 'level.dat'))
    print(f'PASS: {name}; actual generator flat, seed 1, exact layers, no world features')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--loader', choices=('forge','neoforge'), required=True)
    args = parser.parse_args()
    check(Path(__file__).resolve().parents[1] / 'build' / args.loader / 'gametest')
