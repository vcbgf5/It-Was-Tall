#!/usr/bin/env python3
"""Generates the world-generated structures of It Was Tall (MODPLAN §17) for Minecraft 1.21.1:

  abandoned_cabin   surface   forests / taigas / plains   cabin with notes, a sign, a chest
  roadside_shrine   surface   plains / meadows / forests  kapliczka with a skull and an offering chest
  torch_tunnel      underground                           old mine tunnel, dying torches, a dead end
  sign_room         underground                           a buried room whose walls are covered in signs
  crystal_grotto    underground                           blue-lit grotto, GUARANTEED Warding Crystal

For each one it writes: structure template (.nbt), worldgen/structure, worldgen/structure_set,
worldgen/template_pool, the biome tag and the chest loot table. Requires nbtlib (pip install nbtlib).
Run: python3 tools/gen_structures.py
"""
import json
import os
import random

import nbtlib
from nbtlib.tag import Byte, Compound, Int, List, String

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src/main/resources/data/itwastall")
DATA_VERSION = 3955  # 1.21.1
NS = "itwastall"


# =========================================================================== template builder
class Template:
    def __init__(self, sx, sy, sz, seed):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.rng = random.Random(seed)

    def set(self, x, y, z, name, props=None, nbt=None):
        sx, sy, sz = self.size
        if 0 <= x < sx and 0 <= y < sy and 0 <= z < sz:
            self.blocks[(x, y, z)] = (name if ":" in name else "minecraft:" + name, props or {}, nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, props)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def save(self, name):
        palette, index, blocks = [], {}, []
        for (x, y, z), (bname, props, nbt) in sorted(self.blocks.items()):
            key = (bname, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = Compound({"Name": String(bname)})
                if props:
                    entry["Properties"] = Compound({k: String(str(v).lower()) for k, v in props.items()})
                palette.append(entry)
            b = Compound({"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])})
            if nbt is not None:
                b["nbt"] = to_nbt(nbt)
            blocks.append(b)
        root = Compound({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(v) for v in self.size]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })
        path = os.path.join(DATA, "structure", name + ".nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbtlib.File(root, gzipped=True).save(path)
        return len(blocks)


def to_nbt(v):
    if isinstance(v, bool):
        return Byte(1 if v else 0)
    if isinstance(v, int):
        return Int(v)
    if isinstance(v, str):
        return String(v)
    if isinstance(v, dict):
        return Compound({k: to_nbt(x) for k, x in v.items()})
    if isinstance(v, list):
        if all(isinstance(x, str) for x in v):
            return List[String]([String(x) for x in v])
        return List[Compound]([to_nbt(x) for x in v])
    raise TypeError(v)


def chest(t, x, y, z, facing, loot):
    t.set(x, y, z, "chest", {"facing": facing, "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": f"{NS}:chests/{loot}"})


def sign_nbt(lines, color="red", glowing=False):
    msgs = [json.dumps({"text": l}) for l in (lines + ["", "", "", ""])[:4]]
    empty = [json.dumps({"text": ""})] * 4
    return {"id": "minecraft:sign", "is_waxed": True,
            "front_text": {"messages": msgs, "color": color, "has_glowing_text": glowing},
            "back_text": {"messages": empty, "color": "black", "has_glowing_text": False}}


def wall_sign(t, x, y, z, facing, lines, wood="dark_oak", color="red"):
    t.set(x, y, z, f"{wood}_wall_sign", {"facing": facing, "waterlogged": "false"}, sign_nbt(lines, color))


def stairs(facing, half="bottom"):
    return {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"}


# =========================================================================== structures
def abandoned_cabin():
    """11 x 10 x 10. Walls x1..9 / z1..7, front (door) at z = 7 facing south, porch at z = 8."""
    t = Template(11, 10, 10, 11)
    r = t.rng
    # floor
    for x in range(1, 10):
        for z in range(1, 8):
            edge = x in (1, 9) or z in (1, 7)
            if edge:
                t.set(x, 0, z, "mossy_cobblestone" if r.random() < 0.3 else "cobblestone")
            else:
                t.set(x, 0, z, "coarse_dirt" if r.random() < 0.12 else "spruce_planks")
    for x in range(2, 9):
        t.set(x, 0, 8, "spruce_slab", {"type": "top", "waterlogged": "false"})        # porch boards
    # walls + hollow interior
    for x in range(1, 10):
        for z in range(1, 8):
            for y in range(1, 4):
                edge = x in (1, 9) or z in (1, 7)
                corner = x in (1, 9) and z in (1, 7)
                if not edge:
                    t.set(x, y, z, "air")
                else:
                    t.set(x, y, z, "spruce_log" if corner else "spruce_planks", {"axis": "y"} if corner else None)
    # windows (some smashed)
    for (x, z) in ((3, 1), (7, 1), (3, 7), (7, 7), (1, 4), (9, 4)):
        t.set(x, 2, z, "air" if r.random() < 0.3 else "glass_pane")
    # door (closed, wooden: anyone can walk in... and anything)
    t.set(5, 1, 7, "spruce_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(5, 2, 7, "spruce_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    # gable roof: slopes towards ±x, ridge along z, overhang z = 0 and z = 9
    for z in range(0, 10):
        for k in range(0, 5):
            y = 4 + k
            t.set(k, y, z, "spruce_stairs", stairs("east"))
            t.set(10 - k, y, z, "spruce_stairs", stairs("west"))
        t.set(5, 9, z, "spruce_slab", {"type": "bottom", "waterlogged": "false"})
    for z in (1, 7):                                   # gable ends
        for k in range(0, 5):
            for x in range(k + 1, 10 - k):
                t.set(x, 4 + k, z, "spruce_planks")
    for z in range(2, 7):                              # attic air
        for k in range(0, 5):
            for x in range(k + 1, 10 - k):
                t.set(x, 4 + k, z, "air")
    # porch posts
    for x in (1, 9):
        for y in range(1, 4):
            t.set(x, y, 8, "spruce_fence", {"waterlogged": "false"})
    # inside
    t.set(2, 1, 2, "brown_bed", {"facing": "south", "part": "head", "occupied": "false"})
    t.set(2, 1, 3, "brown_bed", {"facing": "south", "part": "foot", "occupied": "false"})
    t.set(8, 1, 2, "crafting_table")
    t.set(8, 1, 3, "furnace", {"facing": "west", "lit": "false"})
    chest(t, 8, 1, 6, "west", "abandoned_cabin")
    t.set(4, 1, 4, "spruce_stairs", stairs("west"))                 # chair turned to the wall
    t.set(6, 1, 3, "spruce_stairs", stairs("north", "top"))         # knocked over
    for (x, y, z) in ((2, 3, 6), (8, 3, 2), (2, 3, 2), (6, 6, 4), (4, 5, 5)):
        t.set(x, y, z, "cobweb")
    t.set(5, 8, 4, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(5, 7, 4, "soul_lantern", {"hanging": "true", "waterlogged": "false"})
    wall_sign(t, 5, 2, 2, "south", ["it was", "tall", "", "don't open"], wood="spruce")
    return t


def roadside_shrine():
    """5 x 9 x 6: a kapliczka facing south (z+), offering chest in front."""
    t = Template(5, 9, 6, 22)
    r = t.rng
    t.fill(0, 0, 0, 4, 0, 4, "cobblestone")
    t.fill(1, 1, 1, 3, 1, 3, "mossy_stone_brick_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(2, 1, 2, "stone_bricks")
    t.set(2, 2, 2, "stone_bricks")
    t.set(2, 3, 2, "chiseled_stone_bricks")
    t.set(2, 4, 1, "stone_bricks")
    t.set(1, 4, 2, "stone_brick_wall", {"waterlogged": "false"})
    t.set(3, 4, 2, "stone_brick_wall", {"waterlogged": "false"})
    t.set(2, 4, 2, "skeleton_skull", {"rotation": "0"})                  # rotation 0 = facing south
    t.set(2, 5, 2, "dark_oak_planks")
    t.set(2, 5, 3, "dark_oak_stairs", stairs("north"))
    t.set(2, 5, 1, "dark_oak_stairs", stairs("south"))
    t.set(1, 5, 2, "dark_oak_stairs", stairs("east"))
    t.set(3, 5, 2, "dark_oak_stairs", stairs("west"))
    t.set(2, 6, 2, "dark_oak_slab", {"type": "bottom", "waterlogged": "false"})
    t.set(2, 7, 2, "dark_oak_fence", {"waterlogged": "false"})
    for (x, z) in ((1, 1), (3, 1), (1, 3), (3, 3)):
        if r.random() < 0.75:
            t.set(x, 2, z, "candle", {"candles": str(1 + r.randrange(3)), "lit": "true", "waterlogged": "false"})
    for x in range(0, 5):
        if x != 2 and r.random() < 0.5:
            t.set(x, 1, 5, "wither_rose")
    t.fill(0, 0, 5, 4, 0, 5, "coarse_dirt")
    chest(t, 2, 1, 4, "south", "roadside_shrine")
    return t


def torch_tunnel():
    """5 x 5 x 28 buried mine tunnel along z. Supports every 4 blocks, torches dying out towards
    the end, rails, cobwebs, a collapsed dead end with a sign and a chest."""
    t = Template(5, 5, 28, 33)
    r = t.rng
    rock = ["stone", "stone", "andesite", "cobblestone", "tuff", "gravel"]
    for z in range(28):
        for x in range(5):
            for y in range(5):
                shell = x in (0, 4) or y in (0, 4) or z == 27
                t.set(x, y, z, r.choice(rock) if shell else "air")
        if z < 25:
            t.set(2, 1, z, "rail", {"shape": "north_south", "waterlogged": "false"})
            if r.random() < 0.15:
                t.set(2, 1, z, "air")                                   # missing rail pieces
    for z in range(1, 25, 4):
        for y in (1, 2):
            t.set(1, y, z, "oak_fence", {"waterlogged": "false"})
            t.set(3, y, z, "oak_fence", {"waterlogged": "false"})
        for x in (1, 2, 3):
            t.set(x, 3, z, "oak_planks")
        lit = r.random() < (1 - z / 30)                                 # the deeper, the darker
        t.set(1, 2, z + 1, "wall_torch" if lit else "air", {"facing": "east"} if lit else None)
        if r.random() < 0.4:
            t.set(3, 2, z + 1, "soul_wall_torch", {"facing": "west"})
    for _ in range(7):
        t.set(r.choice((1, 3)), 3, r.randrange(2, 26), "cobweb")
    # the collapse at the end
    for x in range(1, 4):
        for y in range(1, 4):
            if r.random() < 0.6:
                t.set(x, y, 26, "gravel" if r.random() < 0.5 else "cobblestone")
    t.set(2, 1, 26, "air")
    t.set(2, 2, 26, "air")
    wall_sign(t, 2, 2, 25, "north", ["TURN", "AROUND"], wood="oak")
    chest(t, 1, 1, 25, "north", "torch_tunnel")
    return t


def sign_room():
    """9 x 6 x 9 buried room: every free wall space is a sign."""
    t = Template(9, 6, 9, 44)
    r = t.rng
    bricks = ["stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks"]
    for x in range(9):
        for y in range(6):
            for z in range(9):
                shell = x in (0, 8) or y in (0, 5) or z in (0, 8)
                t.set(x, y, z, r.choice(bricks) if shell else "air")
    t.fill(1, 0, 1, 7, 0, 7, "polished_andesite")
    # a way in: a broken hole in the south wall
    t.set(4, 1, 8, "air")
    t.set(4, 2, 8, "air")
    messages = [["IT WAS", "TALL"], ["IT SEES", "YOU"], ["DON'T", "HIDE"], ["JUST", "RUN"], ["TURN", "AROUND"],
                ["IT KNOWS", "WHERE YOU", "SLEEP"], ["LET ME", "IN"], ["I WAS", "HERE"], ["NOT", "ALONE"],
                ["IT DIGS"], ["IT WAITS"], ["COUNT", "THE NAMES"], ["NO", "WINDOWS"], ["IT WAS", "NEVER", "OUTSIDE"]]
    walls = []
    for y in (1, 2, 3):
        for i in range(1, 8):
            walls += [(i, y, 1, "south"), (i, y, 7, "north"), (1, y, i, "east"), (7, y, i, "west")]
    for (x, y, z, f) in walls:
        if (x, z) in ((4, 7),) and y <= 2:
            continue                                                    # keep the hole clear
        if r.random() < 0.8:
            wall_sign(t, x, y, z, f, r.choice(messages), wood=r.choice(["dark_oak", "spruce", "oak"]),
                      color=r.choice(["red", "red", "black", "white"]))
    t.set(4, 1, 4, "chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": f"{NS}:chests/sign_room"})
    t.set(4, 4, 4, "chain", {"axis": "y", "waterlogged": "false"})
    t.set(4, 3, 4, "soul_lantern", {"hanging": "true", "waterlogged": "false"})
    return t


def crystal_grotto():
    """11 x 8 x 11 buried grotto lit blue: soul lanterns, a blue glow in glass, blue ice, and the
    pedestal with the chest that always holds a Warding Crystal."""
    t = Template(11, 8, 11, 55)
    r = t.rng
    shell = ["deepslate", "deepslate", "tuff", "smooth_basalt", "cobbled_deepslate"]
    cx, cy, cz = 5, 3, 5
    for x in range(11):
        for y in range(8):
            for z in range(11):
                d = ((x - cx) / 5.2) ** 2 + ((y - cy) / 3.6) ** 2 + ((z - cz) / 5.2) ** 2
                if d < 0.78 + r.uniform(-0.06, 0.06) and y >= 1:
                    t.set(x, y, z, "air")
                elif d < 1.35:
                    t.set(x, y, z, r.choice(shell))
    t.fill(2, 0, 2, 8, 0, 8, "deepslate_tiles")
    # the pedestal and the chest
    t.set(5, 1, 5, "polished_deepslate")
    t.set(5, 2, 5, "chest", {"facing": "south", "type": "single", "waterlogged": "false"},
          {"id": "minecraft:chest", "LootTable": f"{NS}:chests/crystal_grotto"})
    # the blue glow: sea lanterns under light blue glass, blue ice pillars, soul lanterns
    for (x, z) in ((2, 5), (8, 5), (5, 2), (5, 8)):
        t.set(x, 1, z, "sea_lantern")
        t.set(x, 2, z, "light_blue_stained_glass")
    for (x, z) in ((3, 3), (7, 7), (3, 7), (7, 3)):
        t.set(x, 1, z, "blue_ice")
        if r.random() < 0.7:
            t.set(x, 2, z, "blue_ice")
    for (x, z) in ((4, 6), (6, 4)):
        t.set(x, 1, z, "soul_lantern", {"hanging": "false", "waterlogged": "false"})
    for _ in range(5):
        t.set(r.randrange(2, 9), 1, r.randrange(2, 9), "amethyst_cluster", {"facing": "up", "waterlogged": "false"})
    t.set(5, 1, 5, "polished_deepslate")                              # keep the pedestal
    # a crack to get in from the surrounding caves
    t.set(5, 1, 10, "air")
    t.set(5, 2, 10, "air")
    t.set(5, 1, 9, "air")
    t.set(5, 2, 9, "air")
    return t


# =========================================================================== data files
NOTE = lambda nid, title, body: {
    "type": "minecraft:item", "name": f"{NS}:note",
    "functions": [
        {"function": "minecraft:set_custom_data",
         "tag": "{note_id:\"%s\",note_title:\"%s\",note_body:\"%s\",itwastall_cave_note:1b}"
                % (nid, title.replace('"', "'"), body.replace('"', "'"))},
        {"function": "minecraft:set_name", "name": title, "target": "item_name"},
    ]}


def item(name, lo=1, hi=1, weight=1):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": weight}
    if hi > 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def crystal_pool(chance):
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:warding_crystal"}]}
    if chance < 1:
        pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool


LOOT = {
    "abandoned_cabin": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [
            NOTE("cabin_1", "THE CABIN", "I built this place far from everyone.|So nobody would hear me.|Now I am the one who hears.|Something walks around the house every night.|Three steps. Stop. Three steps."),
            NOTE("cabin_2", "WINDOWS", "I covered every window.|It still looks in.|I can feel it.|Tall shadows don't need windows."),
            NOTE("cabin_3", "LAST NIGHT", "It knocked.|I didn't answer.|In the morning the door was open.|I always lock the door.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 5}, "entries": [
            item("bread", 1, 3, 3), item("coal", 2, 6, 3), item("torch", 2, 6, 3), item("string", 1, 4, 2),
            item("iron_nugget", 2, 7, 2), item("bone", 1, 3, 2), item("candle", 1, 2, 1)]},
        crystal_pool(0.1)]},
    "roadside_shrine": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [NOTE("shrine_1", "IT ACCEPTS", "Leave something in the box.|Food. Light. Anything it wants.|The village left bread here for sixty years.|Last winter the box was full.|We were not.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            item("bread", 1, 2, 3), item("candle", 1, 3, 3), item("honey_bottle", 1, 1, 1), item("emerald", 1, 2, 1),
            item("wheat", 2, 5, 2)]}]},
    "torch_tunnel": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [NOTE("tunnel_1", "SHIFT REPORT", "Day 3. Torches keep going out.|Day 5. Someone is mining ahead of us.|There is nobody ahead of us.|Day 6. The tunnel is longer than we dug it.|Day 7.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
            item("rail", 2, 8, 3), item("torch", 3, 10, 3), item("coal", 3, 9, 3), item("raw_iron", 1, 4, 2),
            item("iron_pickaxe", 1, 1, 1), item("bread", 1, 2, 2)]},
        crystal_pool(0.15)]},
    "sign_room": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [NOTE("signs_1", "THE SIGNS", "We didn't write them.|Every morning there are more.|They are about you.|They are always about whoever reads them.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            item("ink_sac", 1, 3, 3), item("paper", 2, 6, 3), item("candle", 1, 2, 2), item("ender_pearl", 1, 1, 1),
            item("glow_ink_sac", 1, 2, 1)]},
        crystal_pool(0.35)]},
    "crystal_grotto": {"type": "minecraft:chest", "pools": [
        crystal_pool(1.0),
        {"rolls": 1, "entries": [NOTE("grotto_1", "THE BLUE GLOW", "It does not come near the blue light.|I don't know why.|I took the crystal.|Now it doesn't come near ME.|Don't lose it. Don't ever lose it.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            item("amethyst_shard", 2, 6, 3), item("lapis_lazuli", 3, 9, 3), item("diamond", 1, 2, 1), item("soul_torch", 2, 5, 2)]}]},
}

SURFACE_FOREST = ["minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga",
                  "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga",
                  "minecraft:plains", "minecraft:meadow", "minecraft:windswept_forest", "minecraft:old_growth_birch_forest"]
SURFACE_OPEN = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                "minecraft:birch_forest", "minecraft:taiga", "minecraft:savanna", "minecraft:snowy_plains"]

STRUCTURES = {
    # name: (builder, surface?, biomes, spacing, separation, salt, y-range for underground)
    "abandoned_cabin": (abandoned_cabin, True, SURFACE_FOREST, 40, 16, 74611301, None),
    "roadside_shrine": (roadside_shrine, True, SURFACE_OPEN, 32, 12, 74611302, None),
    "torch_tunnel": (torch_tunnel, False, "#minecraft:is_overworld", 36, 14, 74611303, (-24, 36)),
    "sign_room": (sign_room, False, "#minecraft:is_overworld", 52, 22, 74611304, (-40, 20)),
    "crystal_grotto": (crystal_grotto, False, "#minecraft:is_overworld", 44, 18, 74611305, (-50, 0)),
}


def write(rel, data):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(data, fh, indent=2)


def main():
    for name, (builder, surface, biomes, spacing, separation, salt, yr) in STRUCTURES.items():
        n = builder().save(name)
        if isinstance(biomes, list):
            write(f"tags/worldgen/biome/has_structure/{name}.json", {"replace": False, "values": biomes})
            biome_ref = f"#{NS}:has_structure/{name}"
        else:
            biome_ref = biomes
        structure = {
            "type": "minecraft:jigsaw",
            "biomes": biome_ref,
            "step": "surface_structures" if surface else "underground_structures",
            "spawn_overrides": {},
            "terrain_adaptation": "beard_thin" if surface else "bury",
            "start_pool": f"{NS}:{name}/start",
            "size": 1,
            "max_distance_from_center": 80,
            "use_expansion_hack": False,
        }
        if surface:
            structure["start_height"] = {"absolute": -1}
            structure["project_start_to_heightmap"] = "WORLD_SURFACE_WG"
        else:
            structure["start_height"] = {"type": "minecraft:uniform",
                                         "min_inclusive": {"absolute": yr[0]}, "max_inclusive": {"absolute": yr[1]}}
        write(f"worldgen/structure/{name}.json", structure)
        write(f"worldgen/structure_set/{name}.json", {
            "structures": [{"structure": f"{NS}:{name}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": separation, "salt": salt}})
        write(f"worldgen/template_pool/{name}/start.json", {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element", "location": f"{NS}:{name}",
                "projection": "rigid", "processors": "minecraft:empty"}}]})
        write(f"loot_table/chests/{name}.json", LOOT[name])
        print(f"{name:18s} {n:5d} blocks  {'surface' if surface else 'underground'}  spacing {spacing}")


if __name__ == "__main__":
    main()
