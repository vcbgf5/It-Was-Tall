#!/usr/bin/env python3
"""Generates the world-generated structures of It Was Tall (MODPLAN §17) for Minecraft 1.21.1:

  abandoned_cabin   surface   forests / taigas / plains   cabin with notes, a sign, a chest
  roadside_shrine   surface   plains / meadows / forests  kapliczka with a skull and an offering chest
  torch_tunnel      underground                           old mine tunnel, dying torches, a dead end
  sign_room         underground                           a buried room whose walls are covered in signs
  crystal_grotto    underground                           blue-lit grotto, GUARANTEED Warding Crystal
  deep_lair         deep underground (below Y 0)          the Deep One's den: sculk that listens, a wool trail, dropped blocks
  counting_stones   surface   plains / forests            ten numbered stones in a ring, counting down to a cold fire
  guest_house       surface   plains / forests            a small house set for two; the second guest never left
  dream_well        surface   plains / meadows            a dry well with a bed at the bottom

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


def deep_lair():
    """13 x 7 x 13 low cavern, the Deep One's den (MODPLAN extension): sculk that hears you, a wool trail
    that does not, blocks lying around where it shook them out of someone's hands, the hint chest."""
    t = Template(13, 7, 13, 77)
    r = t.rng
    cx, cy, cz = 6, 2, 6
    for x in range(13):
        for y in range(7):
            for z in range(13):
                d = ((x - cx) / 6.2) ** 2 + ((y - cy) / 2.6) ** 2 + ((z - cz) / 6.2) ** 2
                if d < 0.8 + r.uniform(-0.07, 0.07) and y >= 1:
                    t.set(x, y, z, "air")
                elif d < 1.3:
                    t.set(x, y, z, r.choice(["deepslate", "cobbled_deepslate", "tuff", "deepslate"]))
    t.fill(1, 0, 1, 11, 0, 11, "deepslate")
    # sculk everywhere it can listen, sensors around the edge
    for _ in range(40):
        x, z = r.randrange(1, 12), r.randrange(1, 12)
        if t.get(x, 1, z) == "minecraft:air":
            t.set(x, 0, z, "sculk")
    for (x, z) in ((2, 4), (10, 8), (4, 10), (8, 2)):
        t.set(x, 1, z, "sculk_sensor", {"sculk_sensor_phase": "inactive", "power": "0", "waterlogged": "false"})
    # the safe way: a wool carpet trail from the crack to the chest (wool does not make a sound)
    for z in range(6, 13):
        t.set(6, 1, z, "gray_carpet")
    # cobwebs on the ceiling, where it hangs
    for _ in range(14):
        x, z = r.randrange(2, 11), r.randrange(2, 11)
        for y in (4, 3):
            if t.get(x, y, z) == "minecraft:air" and t.get(x, y + 1, z) not in (None, "minecraft:air"):
                t.set(x, y, z, "cobweb")
                break
    # what it shook out of people's hands: odd single blocks on the floor, a dead torch (no flame)
    for name in ("cobblestone", "oak_planks", "dirt", "crafting_table", "torch", "stone_bricks", "glass"):
        x, z = r.randrange(2, 11), r.randrange(2, 11)
        if t.get(x, 1, z) == "minecraft:air":
            t.set(x, 1, z, "redstone_torch" if name == "torch" else name, {"lit": "false"} if name == "torch" else None)
    chest(t, 6, 1, 5, "south", "deep_lair")
    t.set(6, 1, 6, "gray_carpet")
    # the crack in from the caves
    for y in (1, 2):
        t.set(6, y, 12, "air")
        t.set(6, y, 11, "air")
    return t


def counting_stones():
    """13 x 6 x 13: ten mossy pillars in a ring with signs 10 .. 1, counting down to a cold campfire
    and the hint chest in the middle."""
    t = Template(13, 6, 13, 88)
    r = t.rng
    import math
    for x in range(13):
        for z in range(13):
            if (x - 6) ** 2 + (z - 6) ** 2 <= 36:
                t.set(x, 0, z, r.choice(["coarse_dirt", "podzol", "coarse_dirt", "gravel"]))
                for y in range(1, 6):
                    t.set(x, y, z, "air")
    facings = {(0, -1): "north", (0, 1): "south", (1, 0): "east", (-1, 0): "west"}
    for i in range(10):
        a = -math.pi / 2 + i * 2 * math.pi / 10
        x, z = 6 + round(math.cos(a) * 5), 6 + round(math.sin(a) * 5)
        h = 2 + (i % 3)
        for y in range(1, h + 1):
            t.set(x, y, z, r.choice(["mossy_stone_bricks", "mossy_cobblestone", "cracked_stone_bricks"]))
        # the number faces the middle
        dx, dz = 6 - x, 6 - z
        step = (0 if abs(dx) < abs(dz) else (1 if dx > 0 else -1), 0 if abs(dx) >= abs(dz) else (1 if dz > 0 else -1))
        sx, sz = x + step[0], z + step[1]
        face = facings[step]
        wall_sign(t, sx, 2, sz, face, ["", str(10 - i), "", ""], wood="spruce", color="red")
    t.set(6, 1, 6, "campfire", {"lit": "false", "signal_fire": "false", "facing": "north", "waterlogged": "false"})
    chest(t, 6, 1, 8, "north", "counting_stones")
    return t


def guest_house():
    """9 x 7 x 9 little house set for two: two beds, two chairs, two plates — and signs on the wall like a
    player list. The hint chest stands by the second bed."""
    t = Template(9, 7, 9, 99)
    r = t.rng
    t.fill(0, 0, 0, 8, 0, 8, "cobblestone")
    for x in range(1, 8):
        for z in range(1, 8):
            t.set(x, 0, z, "spruce_planks")
            edge = x in (1, 7) or z in (1, 7)
            for y in range(1, 4):
                t.set(x, y, z, ("spruce_log" if (x in (1, 7) and z in (1, 7)) else "spruce_planks") if edge else "air")
    for (x, z) in ((1, 4), (7, 4), (4, 1)):
        t.set(x, 2, z, "glass_pane")
    # flat roof with a lip
    t.fill(0, 4, 0, 8, 4, 8, "dark_oak_slab")
    for x in range(1, 8):
        for z in range(1, 8):
            t.set(x, 4, z, "dark_oak_planks")
    # the door (south)
    t.set(4, 1, 7, "spruce_door", {"facing": "south", "half": "lower", "hinge": "left", "open": "false", "powered": "false"})
    t.set(4, 2, 7, "spruce_door", {"facing": "south", "half": "upper", "hinge": "left", "open": "false", "powered": "false"})
    # two beds, side by side
    for x in (2, 6):
        t.set(x, 1, 2, "white_bed" if x == 2 else "gray_bed", {"facing": "south", "part": "head", "occupied": "false"})
        t.set(x, 1, 3, "white_bed" if x == 2 else "gray_bed", {"facing": "south", "part": "foot", "occupied": "false"})
    # a table for two
    t.set(4, 1, 4, "oak_fence")
    t.set(4, 2, 4, "oak_pressure_plate")
    t.set(3, 1, 4, "oak_stairs", stairs("west"))
    t.set(5, 1, 4, "oak_stairs", stairs("east"))
    # the "player list" on the wall
    wall_sign(t, 3, 2, 2, "south", ["PLAYERS:", "you", "guest", ""], wood="spruce", color="black")
    wall_sign(t, 5, 2, 2, "south", ["guest:", "\"welcome", "back\"", ""], wood="spruce", color="red")
    t.set(6, 1, 5, "candle", {"candles": "2", "lit": "false", "waterlogged": "false"})
    chest(t, 6, 1, 4, "west", "guest_house")
    return t


def dream_well():
    """7 x 9 x 7: a dry stone well; at the bottom, three blocks down, someone made a bed. White flowers
    around it. The hint chest leans on the well."""
    t = Template(7, 9, 7, 111)
    r = t.rng
    G = 4                                            # ground row
    for x in range(7):
        for z in range(7):
            t.set(x, G, z, "grass_block", {"snowy": "false"})
            for y in range(G + 1, 9):
                t.set(x, y, z, "air")
            for y in range(0, G):
                t.set(x, y, z, "dirt")
    # the shaft: 3 x 3 inside, stone bricks, bottom at y 1
    for x in range(1, 6):
        for z in range(1, 6):
            for y in range(0, G + 2):
                inner = 2 <= x <= 4 and 2 <= z <= 4
                if inner and 1 <= y <= G + 1:
                    t.set(x, y, z, "air")
                elif not inner and y >= 0:
                    t.set(x, y, z, r.choice(["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"]))
    t.fill(2, 0, 2, 4, 0, 4, "moss_block")
    t.set(3, 1, 2, "red_bed", {"facing": "north", "part": "head", "occupied": "false"})
    t.set(3, 1, 3, "red_bed", {"facing": "north", "part": "foot", "occupied": "false"})
    # the roof over the well
    for (x, z) in ((1, 1), (5, 1), (1, 5), (5, 5)):
        for y in (G + 2, G + 3):
            t.set(x, y, z, "spruce_fence")
    for x in range(1, 6):
        for z in range(1, 6):
            t.set(x, G + 4, z, "spruce_slab")
    t.set(3, G + 3, 3, "chain", {"axis": "y", "waterlogged": "false"})
    # white flowers
    for _ in range(9):
        x, z = r.randrange(0, 7), r.randrange(0, 7)
        if not (1 <= x <= 5 and 1 <= z <= 5):
            t.set(x, G + 1, z, r.choice(["lily_of_the_valley", "oxeye_daisy", "azure_bluet"]))
    wall_sign(t, 3, G + 1, 0, "north", ["", "don't wake", "it", ""], wood="spruce", color="red")
    chest(t, 6, G + 1, 3, "west", "dream_well")
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


BOOK = lambda nid, title, body: {
    "type": "minecraft:item", "name": f"{NS}:lore_book",
    "functions": [
        {"function": "minecraft:set_custom_data",
         "tag": "{note_id:\"%s\",note_title:\"%s\",note_body:\"%s\"}"
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
    # ---- the hint books for the new creatures ----
    "deep_lair": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [BOOK("hint_deep_one", "THE DEEP ONE", "Below the bedrock line the tunnels are not empty.|It crawls on the ceiling. Long. Thin. On all fours.|You only ever see it from the corner of your eye. Look straight at it and it is gone.|It puts the torches out behind you.|It hears you RUN. It does not hear you sneak.|Walk on wool if you can.|And hold on to what you carry. It shakes the blocks right out of your hands.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            item("white_wool", 2, 6, 3), item("torch", 2, 6, 2), item("echo_shard", 1, 1, 1), item("raw_iron", 1, 4, 2)]},
        crystal_pool(0.2)]},
    "counting_stones": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [BOOK("hint_counter", "THE COUNT", "If you hear counting at night, out in the open:|ten. nine. eight.|Every number, it is closer. You will see it between the trees.|Do not run around. Get under a roof before it says zero.|Or turn your back to it and do not move until it is done.|I was too slow once. I still hear the zero.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 3}, "entries": [
            item("candle", 1, 3, 3), item("coal", 2, 5, 2), item("bone", 1, 3, 2)]}]},
    "guest_house": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [BOOK("hint_guest", "A GUEST", "There was a name in the player list I did not know.|It wrote in the chat. It wrote what I wrote yesterday.|Then I saw it. Far away. Its back to me. It never turned around.|I walked up to it. It was gone.|In my chest there was a note I did not write.|We are two in this world. The list says three.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
            item("bread", 1, 3, 3), item("cookie", 2, 4, 2), item("candle", 1, 2, 2), item("name_tag", 1, 1, 1)]}]},
    "dream_well": {"type": "minecraft:chest", "pools": [
        {"rolls": 1, "entries": [BOOK("hint_sleeper", "THE SLEEPER", "In the dream there is a room with a bed. Someone sleeps in it.|It has your face. Your clothes. Your hands.|Walk past it quietly. Do not run. Do not open anything near it.|If it sits up, the Old One knows exactly where you are.|There is something blue by its bed. Take it, slowly.")]},
        {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3}, "entries": [
            item("white_wool", 1, 3, 2), item("lily_of_the_valley", 1, 3, 2), item("feather", 1, 3, 2)]}]},
}

SURFACE_FOREST = ["minecraft:forest", "minecraft:birch_forest", "minecraft:dark_forest", "minecraft:taiga",
                  "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:snowy_taiga",
                  "minecraft:plains", "minecraft:meadow", "minecraft:windswept_forest", "minecraft:old_growth_birch_forest"]
SURFACE_OPEN = ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:forest",
                "minecraft:birch_forest", "minecraft:taiga", "minecraft:savanna", "minecraft:snowy_plains"]

SURFACE_ALL = sorted(set(SURFACE_FOREST + SURFACE_OPEN + ["minecraft:cherry_grove", "minecraft:savanna_plateau",
                                                       "minecraft:windswept_savanna", "minecraft:grove", "minecraft:flower_forest"]))

STRUCTURES = {
    # name: (builder, surface?, biomes, spacing, separation, salt, y-range underground / start offset surface)
    # spacing in chunks: about one every 300-380 blocks (it used to be 500-830 — nobody ever found them)
    "abandoned_cabin": (abandoned_cabin, True, SURFACE_FOREST, 22, 8, 74611301, None),
    "roadside_shrine": (roadside_shrine, True, SURFACE_OPEN, 20, 7, 74611302, None),
    "torch_tunnel": (torch_tunnel, False, "#minecraft:is_overworld", 18, 6, 74611303, (-24, 36)),
    "sign_room": (sign_room, False, "#minecraft:is_overworld", 24, 9, 74611304, (-40, 20)),
    "crystal_grotto": (crystal_grotto, False, "#minecraft:is_overworld", 22, 8, 74611305, (-50, 0)),
    "deep_lair": (deep_lair, False, "#minecraft:is_overworld", 18, 6, 74611306, (-56, -10)),
    "counting_stones": (counting_stones, True, SURFACE_ALL, 20, 7, 74611307, None),
    "guest_house": (guest_house, True, SURFACE_ALL, 22, 8, 74611308, None),
    "dream_well": (dream_well, True, SURFACE_ALL, 22, 8, 74611309, -5),   # surface: 4 rows of shaft go below ground
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
            # yr for a surface structure = how far below the surface the template starts (default 1 row)
            structure["start_height"] = {"absolute": yr if isinstance(yr, int) else -1}
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
