#!/usr/bin/env python3
"""The forest CHOPPING BLOCK scene: textures, block models, blockstates, item models and the loot table of the
blocks itwastall:chopping_stump (a stump with an axe in it) and itwastall:blood (a splash on the ground).
The stump's bark uses the game's own oak log texture by reference (nothing copied).
Run: python3 tools/gen_chopping.py"""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src/main/resources/assets/itwastall")
D = os.path.join(ROOT, "src/main/resources/data/itwastall")
BLOOD_DARK, BLOOD, BLOOD_WET = (74, 6, 6), (112, 12, 10), (150, 24, 20)


def save_png(img, rel):
    p = os.path.join(A, rel); os.makedirs(os.path.dirname(p), exist_ok=True); img.save(p)


def save_json(obj, base, rel):
    p = os.path.join(base, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as fh: json.dump(obj, fh, indent=2)


def stump_top():
    """Cut wood with growth rings, an axe notch and a dark pool of blood running over one edge."""
    r = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            ring = int(d * 1.6) % 2
            base = (168, 132, 86) if ring == 0 else (146, 112, 70)
            if d > 6.6: base = (92, 70, 44)            # bark rim
            n = r.randint(-6, 6)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
    for y in range(3, 13): img.putpixel((7, y), (60, 44, 28, 255)); img.putpixel((8, y), (84, 62, 40, 255))   # axe cut
    for (cx, cy, rad) in ((9, 9, 3.6), (12, 12, 2.2), (6, 11, 1.6)):           # blood
        for x in range(16):
            for y in range(16):
                d = math.hypot(x - cx, y - cy) + r.random() * 0.9
                if d < rad: img.putpixel((x, y), (BLOOD if d > rad * 0.45 else BLOOD_DARK) + (255,))
    for (x, y) in ((10, 8), (11, 10)): img.putpixel((x, y), BLOOD_WET + (255,))
    for y in range(12, 16): img.putpixel((13, y), BLOOD + (255,))          # running over the edge
    return img


def stump_side():
    """Overlay for the stump's sides: blood running down from the top (transparent elsewhere)."""
    r = random.Random(9)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in (3, 4, 9, 12):
        length = r.randint(3, 9)
        for y in range(6, 6 + length):
            img.putpixel((x, y), (BLOOD if y < 6 + length - 1 else BLOOD_DARK) + (255,))
    for x in range(1, 15):
        if r.random() < 0.7: img.putpixel((x, 6), BLOOD_DARK + (255,))
    return img


def axe_blade():
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            v = 150 + int(30 * (1 - y / 15)) + random.Random(x * 31 + y).randint(-8, 8)
            img.putpixel((x, y), (v, v, v + 6, 255))
    d = ImageDraw.Draw(img)
    d.line([0, 15, 15, 15], fill=(210, 210, 216, 255))                     # the sharp edge
    for x in range(2, 14):                                                   # blood on the edge
        if x % 3: img.putpixel((x, 14), BLOOD + (255,))
    img.putpixel((5, 12), BLOOD_DARK + (255,)); img.putpixel((9, 13), BLOOD + (255,))
    return img


def splash(seed):
    """16x16 transparent splatter: a main pool, drops around it, darker in the middle."""
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    blobs = [(r.uniform(5, 11), r.uniform(5, 11), r.uniform(3.2, 4.8))]
    for _ in range(r.randint(4, 7)):
        a = r.uniform(0, math.tau); dist = r.uniform(4.5, 7.0)
        blobs.append((7.5 + math.cos(a) * dist, 7.5 + math.sin(a) * dist, r.uniform(0.6, 1.5)))
    for x in range(16):
        for y in range(16):
            for i, (cx, cy, rad) in enumerate(blobs):
                d = math.hypot(x - cx, y - cy) + r.random() * 0.7
                if d < rad:
                    col = BLOOD_DARK if (i == 0 and d < rad * 0.5) else BLOOD
                    img.putpixel((x, y), col + (235,))
                    break
    cx, cy, _ = blobs[0]
    img.putpixel((int(cx) - 1, int(cy) - 1), BLOOD_WET + (240,))
    return img


def main():
    save_png(stump_top(), "textures/block/chopping_stump_top.png")
    save_png(stump_side(), "textures/block/chopping_stump_blood.png")
    save_png(axe_blade(), "textures/block/axe_blade.png")
    for i in range(1, 4): save_png(splash(40 + i), f"textures/block/blood_{i}.png")

    side_faces = lambda tex: {f: {"uv": [1, 6, 15, 16], "texture": tex} for f in ("north", "south", "east", "west")}
    rot = {"origin": [8, 10, 7.5], "axis": "x", "angle": -22.5}
    stump = {
        "parent": "block/block",
        "textures": {"particle": "minecraft:block/oak_log", "side": "minecraft:block/oak_log",
                     "top": "itwastall:block/chopping_stump_top", "bottom": "minecraft:block/oak_log_top",
                     "blood": "itwastall:block/chopping_stump_blood", "handle": "minecraft:block/stripped_dark_oak_log",
                     "blade": "itwastall:block/axe_blade"},
        "elements": [
            {"from": [1, 0, 1], "to": [15, 10, 15],
             "faces": dict(side_faces("#side"), up={"uv": [1, 1, 15, 15], "texture": "#top"},
                           down={"uv": [1, 1, 15, 15], "texture": "#bottom", "cullface": "down"})},
            {"from": [0.95, 0, 0.95], "to": [15.05, 10, 15.05], "faces": side_faces("#blood")},
            # the axe: blade buried in the cut, handle leaning out of it
            {"from": [7.4, 7, 3.5], "to": [8.6, 12.5, 9], "rotation": rot,
             "faces": {f: {"uv": [0, 0, 16, 16], "texture": "#blade"} for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [7.4, 12, 6.8], "to": [8.6, 24, 8.2], "rotation": rot,
             "faces": {f: {"uv": [7, 0, 9, 16], "texture": "#handle"} for f in ("north", "south", "east", "west", "up", "down")}},
        ],
        "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]}},
    }
    save_json(stump, A, "models/block/chopping_stump.json")
    save_json({"variants": {f"facing={f}": {"model": "itwastall:block/chopping_stump", "y": y}
                            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}},
              A, "blockstates/chopping_stump.json")
    save_json({"parent": "itwastall:block/chopping_stump"}, A, "models/item/chopping_stump.json")

    for i in range(1, 4):
        save_json({"ambientocclusion": False, "textures": {"particle": f"itwastall:block/blood_{i}", "blood": f"itwastall:block/blood_{i}"},
                   "elements": [{"from": [0, 0.1, 0], "to": [16, 0.1, 16],
                                 "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#blood"},
                                           "down": {"uv": [0, 16, 16, 0], "texture": "#blood"}}}]},
                  A, f"models/block/blood_{i}.json")
    save_json({"variants": {"": [{"model": f"itwastall:block/blood_{i}", "y": y} for i in range(1, 4) for y in (0, 90, 180, 270)]}},
              A, "blockstates/blood.json")
    save_json({"parent": "minecraft:item/generated", "textures": {"layer0": "itwastall:block/blood_1"}}, A, "models/item/blood.json")

    save_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "itwastall:chopping_stump"}],
                                                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
              D, "loot_table/blocks/chopping_stump.json")
    print("chopping block written")


if __name__ == "__main__":
    main()
