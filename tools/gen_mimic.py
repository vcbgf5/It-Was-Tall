#!/usr/bin/env python3
"""THE MIMICS: blood overlays (64x32, transparent) drawn on top of the game's own cow / pig / sheep textures by
the client renderer — splashes all over the body, the muzzle soaked and running. Nothing is copied from the
game: the overlays only follow the standard UV layout of those models.
Run: python3 tools/gen_mimic.py"""
import math, os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/entity/mimic")
DARK, MID, WET = (70, 6, 6), (112, 12, 10), (150, 22, 18)

# front face of the head (x, y, w, h) in each texture: the muzzle is its lower half
HEAD_FRONT = {"cow": (6, 6, 8, 8), "pig": (8, 8, 8, 8), "sheep": (8, 8, 6, 6), "sheep_fur": (8, 8, 6, 6)}


def overlay(name, seed, splashes):
    r = random.Random(seed)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    px = img.load()

    def blob(cx, cy, rad, col=MID):
        for x in range(int(cx - rad - 1), int(cx + rad + 2)):
            for y in range(int(cy - rad - 1), int(cy + rad + 2)):
                if 0 <= x < 64 and 0 <= y < 32 and math.hypot(x - cx, y - cy) + r.random() * 0.8 < rad:
                    px[x, y] = (DARK if math.hypot(x - cx, y - cy) < rad * 0.45 else col) + (235,)

    for _ in range(splashes):                       # splashes over the whole sheet (all faces)
        blob(r.uniform(0, 64), r.uniform(0, 32), r.uniform(0.8, 2.6))
    hx, hy, hw, hh = HEAD_FRONT[name]               # the muzzle: soaked, dripping down
    for x in range(hx, hx + hw):
        for y in range(hy + hh // 2, hy + hh):
            if r.random() < 0.8: px[x, y] = (MID if r.random() < 0.7 else DARK) + (240,)
    for x in range(hx, hx + hw, 2):
        for y in range(hy + hh, min(32, hy + hh + r.randint(1, 4))):
            px[x, y] = MID + (230,)
    for _ in range(4): px[r.randrange(hx, hx + hw), r.randrange(hy + hh // 2, hy + hh)] = WET + (250,)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for i, (name, n) in enumerate((("cow", 22), ("pig", 20), ("sheep", 16), ("sheep_fur", 26))):
        overlay(name, 100 + i, n).save(os.path.join(OUT, f"{name}_blood.png"))
    print("mimic overlays written")


if __name__ == "__main__":
    main()
