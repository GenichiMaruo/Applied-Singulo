import copy
import gzip
from pathlib import Path
import tempfile
import unittest
from check_gametest_world import LAYERS, check, read_nbt, validate

class WorldContractTests(unittest.TestCase):
    def root(self):
        return {'Data': {'WorldGenSettings': {'seed': 1, 'generate_features': 0, 'dimensions': {'minecraft:overworld': {'generator': {'type':'minecraft:flat', 'settings': {'layers':copy.deepcopy(LAYERS), 'biome':'minecraft:plains', 'lakes':0, 'features':0, 'structure_overrides':[]}}}}}}}
    def test_accepts_exact_flat_settings(self):
        validate(self.root())
    def test_rejects_normal_generator(self):
        root=self.root(); root['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['type']='minecraft:noise'
        with self.assertRaises(ValueError): validate(root)
    def test_rejects_wrong_seed(self):
        root=self.root(); root['Data']['WorldGenSettings']['seed']=2
        with self.assertRaises(ValueError): validate(root)
    def test_rejects_lava_layer(self):
        root=self.root(); root['Data']['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']['settings']['layers'][2]['block']='minecraft:lava'
        with self.assertRaises(ValueError): validate(root)
    def test_rejects_features(self):
        root=self.root(); root['Data']['WorldGenSettings']['generate_features']=1
        with self.assertRaises(ValueError): validate(root)
    def test_rejects_world_path_escape(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder); (path/'server.properties').write_text('level-name=../player-world',encoding='utf-8')
            with self.assertRaises(ValueError): check(path)
    def test_rejects_truncated_nbt(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'level.dat'; path.write_bytes(gzip.compress(bytes([10,0,0,3])))
            with self.assertRaises(ValueError): read_nbt(path)

if __name__ == '__main__': unittest.main()
