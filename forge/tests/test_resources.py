import importlib.util
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]
class ConversionTests(unittest.TestCase):
 def setUp(self):
  spec=importlib.util.spec_from_file_location('forge_resources',ROOT/'resources.py')
  self.module=importlib.util.module_from_spec(spec);spec.loader.exec_module(self.module)
 def test_1201_resource_directories(self):
  self.assertEqual(self.module.target_path('resources/wwoo_main/data/minecraft/loot_table/blocks/stone.json'),'resources/wwoo_main/data/minecraft/loot_tables/blocks/stone.json')
  self.assertEqual(self.module.target_path('data/minecraft/tags/block/logs.json'),'data/minecraft/tags/blocks/logs.json')
  self.assertEqual(self.module.target_path('data/minecraft/structure/test.nbt'),'data/minecraft/structures/test.nbt')
 def test_loot_tool_predicates_become_nbt_era_predicates(self):
  converted=self.module.target_json({'condition':'minecraft:match_tool','predicate':{'predicates':{'minecraft:enchantments':[{'enchantments':'minecraft:silk_touch','levels':{'min':1}}]}}})
  self.assertEqual(converted['predicate'],{'enchantments':[{'enchantment':'minecraft:silk_touch','levels':{'min':1}}]})
 def test_looting_bonus_retains_count_and_limit(self):
  self.assertEqual(self.module.target_json({'function':'minecraft:enchanted_count_increase','enchantment':'minecraft:looting','count':1,'limit':4}),{'function':'minecraft:looting_enchant','count':1,'limit':4})
 def test_block_state_short_grass_renamed_without_changing_assets(self):
  self.assertEqual(self.module.target_json({'Name':'minecraft:short_grass'}),{'Name':'minecraft:grass'})
 def test_uniform_count_keeps_boundaries(self):
  self.assertEqual(self.module.target_json({'type':'minecraft:uniform','min':2,'max':4}),{'type':'minecraft:uniform','min':2,'max':4})
if __name__=='__main__':unittest.main()
