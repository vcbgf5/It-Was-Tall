#!/usr/bin/env python3
"""Night scenes: textures, models, blockstates and loot of itwastall:footprint (a shoe print), itwastall:tall_footprint
(the tall one's long print, gone by morning) and itwastall:eye_stone (a stone with an eye carved into it), itwastall:glitch (a block with a broken, flickering
texture) and itwastall:corruption_source (black stone with pulsing red veins).
Run: python3 tools/gen_scenes.py"""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
A = os.path.join(ROOT, "src/main/resources/assets/itwastall")
D = os.path.join(ROOT, "src/main/resources/data/itwastall")


def save_png(img, rel):
    p = os.path.join(A, rel); os.makedirs(os.path.dirname(p), exist_ok=True); img.save(p)


def save_json(obj, base, rel):
    p = os.path.join(base, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as fh: json.dump(obj, fh, indent=2)


def shoe_prints():
    """Two staggered boot prints (left and right foot), toes pointing up (= north = the walking direction)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    col, deep = (40, 28, 18, 150), (24, 16, 10, 175)
    for (x, y) in ((3, 7), (9, 1)):
        d.rounded_rectangle([x, y, x + 3, y + 5], radius=1, fill=col)       # sole
        d.rectangle([x, y + 6, x + 3, y + 7], fill=col)                     # heel gap
        d.rounded_rectangle([x, y + 6, x + 3, y + 8], radius=1, fill=deep)  # heel
        for k in range(y + 1, y + 5, 2): d.line([x + 1, k, x + 2, k], fill=deep)   # tread
    return img


def tall_print():
    """One print of a long, narrow foot that fills the whole block: a thin sole and three long toes."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    col, deep = (14, 10, 10, 190), (6, 4, 4, 220)
    d.polygon([(6, 15), (9, 15), (10, 9), (9, 5), (7, 5), (6, 9)], fill=col)          # sole
    d.ellipse([6, 12, 9, 15], fill=deep)                                                # heel pressed deep
    for (x0, x1) in ((5, 3), (8, 8), (10, 12)):                                          # three long toes
        d.line([(x0 + 1, 5), (x1 + (1 if x1 > x0 else 0), 0)], fill=deep, width=1)
    return img


def stone(seed, eye=False):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            v = 112 + r.randint(-12, 12)
            img.putpixel((x, y), (v, v, v + 2, 255))
    for _ in range(10):
        x, y = r.randrange(16), r.randrange(16)
        img.putpixel((x, y), (86, 86, 88, 255))
    if eye:
        d = ImageDraw.Draw(img)
        cut = (34, 32, 34, 255)
        d.arc([2, 4, 13, 13], 200, 340, fill=cut)            # upper lid
        d.arc([2, 2, 13, 11], 20, 160, fill=cut)             # lower lid
        d.ellipse([6, 5, 9, 10], outline=cut, fill=(60, 56, 58, 255))
        d.rectangle([7, 7, 8, 8], fill=(140, 20, 16, 255))   # a red pupil, painted in
    return img


def glitch_frames():
    """16x64: four 16x16 frames of a broken texture (magenta/black checker, smeared rows), animated."""
    import random as _r
    r = _r.Random(66)
    out = Image.new("RGBA", (16, 64))
    for f in range(4):
        img = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                on = ((x // 4) + (y // 4)) % 2 == 0
                c = (230, 20, 220) if on else (12, 8, 12)
                n = r.randint(-20, 20)
                img.putpixel((x, y), (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n // 2)), max(0, min(255, c[2] + n)), 255))
        for _ in range(3):                                   # smeared, shifted rows
            y = r.randrange(16)
            shift = r.randint(-5, 5)
            row = [img.getpixel(((x - shift) % 16, y)) for x in range(16)]
            for x in range(16): img.putpixel((x, y), row[x])
        if f % 2: img.putpixel((r.randrange(16), r.randrange(16)), (160, 0, 0, 255))
        out.paste(img, (0, f * 16))
    return out


def source_frames():
    """16x32: two frames of the corruption source: black stone with red veins that pulse."""
    import random as _r
    out = Image.new("RGBA", (16, 32))
    for f in range(2):
        r = _r.Random(70)
        img = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                v = 14 + r.randint(-6, 6)
                img.putpixel((x, y), (v, v - 4 if v > 4 else 0, v, 255))
        d = ImageDraw.Draw(img)
        col = (150, 16, 12, 255) if f == 0 else (220, 40, 28, 255)
        for (a, b) in (((1, 3), (7, 8)), ((7, 8), (14, 5)), ((7, 8), (6, 15)), ((3, 12), (6, 15))):
            d.line([a, b], fill=col)
        out.paste(img, (0, f * 16))
    return out


def flat_model(tex):
    return {"ambientocclusion": False, "textures": {"particle": tex, "print": tex},
            "elements": [{"from": [0, 0.1, 0], "to": [16, 0.1, 16],
                          "faces": {"up": {"uv": [0, 0, 16, 16], "texture": "#print"},
                                    "down": {"uv": [0, 16, 16, 0], "texture": "#print"}}}]}


def main():
    save_png(shoe_prints(), "textures/block/footprint.png")
    save_png(tall_print(), "textures/block/tall_footprint.png")
    save_png(stone(3), "textures/block/eye_stone_side.png")
    save_png(stone(4, eye=True), "textures/block/eye_stone_top.png")
    for name in ("footprint", "tall_footprint"):
        save_json(flat_model(f"itwastall:block/{name}"), A, f"models/block/{name}.json")
        save_json({"variants": {f"facing={f}": {"model": f"itwastall:block/{name}", "y": y}
                                for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}},
                  A, f"blockstates/{name}.json")
        save_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"itwastall:block/{name}"}}, A, f"models/item/{name}.json")
    save_json({"parent": "minecraft:block/cube_bottom_top",
               "textures": {"top": "itwastall:block/eye_stone_top", "side": "itwastall:block/eye_stone_side",
                            "bottom": "itwastall:block/eye_stone_side"}}, A, "models/block/eye_stone.json")
    save_json({"variants": {"": {"model": "itwastall:block/eye_stone"}}}, A, "blockstates/eye_stone.json")
    save_json({"parent": "itwastall:block/eye_stone"}, A, "models/item/eye_stone.json")
    save_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "itwastall:eye_stone"}],
                                                      "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
              D, "loot_table/blocks/eye_stone.json")
    save_png(glitch_frames(), "textures/block/glitch.png")
    with open(os.path.join(A, "textures/block/glitch.png.mcmeta"), "w") as fh:
        json.dump({"animation": {"frametime": 3, "frames": [0, 2, 1, 3, 0, 1]}}, fh)
    save_png(source_frames(), "textures/block/corruption_source.png")
    with open(os.path.join(A, "textures/block/corruption_source.png.mcmeta"), "w") as fh:
        json.dump({"animation": {"frametime": 12, "interpolate": True}}, fh)
    for name in ("glitch", "corruption_source"):
        save_json({"parent": "minecraft:block/cube_all", "textures": {"all": f"itwastall:block/{name}"}}, A, f"models/block/{name}.json")
        save_json({"variants": {"": {"model": f"itwastall:block/{name}"}}}, A, f"blockstates/{name}.json")
        save_json({"parent": f"itwastall:block/{name}"}, A, f"models/item/{name}.json")
    print("scene blocks written")


if __name__ == "__main__":
    main()
