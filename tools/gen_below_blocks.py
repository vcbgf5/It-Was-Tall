#!/usr/bin/env python3
"""Textures of the stones of THE BELOW (16x16, tiling):
  forgotten_stone  pale, ash-grey stone worn smooth, with hairline cracks and faint scratched tally marks —
                   as if someone down there once counted the days on it
  oldrock          dark brown-black rock in thin layers, pitted, older than the world above
  and what is made of them: bricks of both, carved forgotten stone (an eye and tally marks), the ritual pillar
  (side + top), the oldrock lantern
Run: python3 tools/gen_below_blocks.py"""
import math, os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/block")


def tiling_noise(seed, cells):
    """Smooth value noise that tiles over 16 px (cells x cells lattice)."""
    rnd = random.Random(seed)
    grid = [[rnd.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        gx, gy = x / 16 * cells, y / 16 * cells
        x0, y0 = int(gx) % cells, int(gy) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        fx, fy = gx - int(gx), gy - int(gy)
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * sx
        b = grid[y1][x0] + (grid[y1][x1] - grid[y1][x0]) * sx
        return a + (b - a) * sy
    return at


def pick(pal, v):
    v = max(0.0, min(0.999, v))
    return pal[int(v * len(pal))]


def forgotten_stone():
    img = Image.new("RGBA", (16, 16))
    pal = [(104, 102, 96), (122, 120, 113), (138, 136, 128), (150, 148, 140), (162, 160, 151), (176, 174, 165)]
    n1, n2 = tiling_noise(11, 4), tiling_noise(12, 8)
    rnd = random.Random(13)
    for y in range(16):
        for x in range(16):
            v = 0.55 * n1(x, y) + 0.35 * n2(x, y) + rnd.uniform(-0.08, 0.08)
            img.putpixel((x, y), pick(pal, v) + (255,))
    # hairline cracks (wrap around the edges so the texture still tiles)
    crack = [(3, 9), (4, 10), (4, 11), (5, 12), (6, 12)]
    for (x, y) in crack:
        img.putpixel((x, y), (92, 90, 85, 255))
    # faint scratched tally marks, someone counting (barely darker than the stone)
    for x in (10, 12):
        for y in range(3, 6):
            img.putpixel((x, y), (128, 126, 119, 255))
    return img


def oldrock():
    img = Image.new("RGBA", (16, 16))
    pal = [(34, 29, 27), (44, 38, 35), (54, 47, 43), (64, 56, 51), (76, 66, 60), (90, 79, 71)]
    n1 = tiling_noise(21, 4)
    rnd = random.Random(22)
    for y in range(16):
        band = 0.5 + 0.5 * math.sin((y + 2.0 * n1(0, y)) / 16 * math.pi * 2 * 3)   # three thin layers
        for x in range(16):
            v = 0.45 * band + 0.4 * n1(x, y) + rnd.uniform(-0.1, 0.1)
            img.putpixel((x, y), pick(pal, v) + (255,))
    # the seams between the layers, and pits
    for y in (4, 10, 15):
        for x in range(16):
            if rnd.random() < 0.75:
                img.putpixel((x, y), (28, 24, 22, 255))
    for _ in range(4):
        x, y = rnd.randrange(16), rnd.randrange(16)
        img.putpixel((x, y), (24, 20, 18, 255))
    return img


def bricks(base, mortar, seed):
    """Stone bricks cut from a texture: two rows of bricks (offset), mortar lines darker, edges lighter."""
    img = base.copy()
    px = img.load()
    rnd = random.Random(seed)
    for y in range(16):
        for x in range(16):
            row = y // 8
            off = 0 if row == 0 else 4
            edge_h = y % 8 == 7
            edge_v = (x + off) % 8 == 7
            if edge_h or edge_v:
                px[x, y] = mortar + (255,)
            elif y % 8 == 0 or (x + off) % 8 == 0:
                r, g, b, a = px[x, y]
                px[x, y] = (min(255, r + 14), min(255, g + 14), min(255, b + 12), 255)
    return img


def carved(base):
    """Forgotten stone bricks with a deep-cut eye and tally marks: someone was here, counting, watched."""
    img = bricks(base, (88, 86, 81), 41)
    px = img.load()
    for x in range(3, 13):
        px[x, 4] = (70, 68, 64, 255)
        px[x, 11] = (70, 68, 64, 255)
    for y in range(5, 11):
        px[3, y] = (70, 68, 64, 255)
        px[12, y] = (70, 68, 64, 255)
    for (x, y) in [(6, 7), (7, 6), (8, 6), (9, 7), (8, 8), (7, 8)]:
        px[x, y] = (60, 58, 54, 255)
    px[7, 7] = (128, 18, 14, 255)
    px[8, 7] = (128, 18, 14, 255)
    return img


def pillar_side(fs, ol):
    """A carved column: forgotten stone with an oldrock band and grooves."""
    img = fs.copy()
    px = img.load()
    olp = ol.load()
    for y in range(16):
        for x in range(16):
            if 6 <= y <= 9:
                px[x, y] = olp[x, y]
            if x in (0, 15):
                r, g, b, a = px[x, y]
                px[x, y] = (max(0, r - 30), max(0, g - 30), max(0, b - 30), 255)
            if x in (4, 11) and not 6 <= y <= 9:
                r, g, b, a = px[x, y]
                px[x, y] = (max(0, r - 22), max(0, g - 22), max(0, b - 20), 255)
    return img


def pillar_top(fs):
    img = fs.copy()
    px = img.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = (86, 84, 79, 255)
    for (x, y) in [(7, 7), (8, 7), (7, 8), (8, 8)]:
        px[x, y] = (40, 34, 31, 255)
    return img


def lantern(ol):
    """Oldrock bricks with a glowing amber core showing through the cracks."""
    img = bricks(ol, (24, 20, 18), 51)
    px = img.load()
    glow = [(255, 214, 120), (246, 170, 70), (226, 120, 40)]
    for y in range(3, 13):
        for x in range(3, 13):
            d = abs(x - 7.5) + abs(y - 7.5)
            if d < 4.6:
                px[x, y] = glow[min(2, int(d / 1.6))] + (255,)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    fs, ol = forgotten_stone(), oldrock()
    fs.save(os.path.join(OUT, "forgotten_stone.png"))
    ol.save(os.path.join(OUT, "oldrock.png"))
    bricks(fs, (92, 90, 85), 31).save(os.path.join(OUT, "forgotten_stone_bricks.png"))
    bricks(ol, (24, 20, 18), 32).save(os.path.join(OUT, "oldrock_bricks.png"))
    carved(fs).save(os.path.join(OUT, "carved_forgotten_stone.png"))
    pillar_side(fs, ol).save(os.path.join(OUT, "ritual_pillar_side.png"))
    pillar_top(fs).save(os.path.join(OUT, "ritual_pillar_top.png"))
    lantern(ol).save(os.path.join(OUT, "oldrock_lantern.png"))


if __name__ == "__main__":
    main()
