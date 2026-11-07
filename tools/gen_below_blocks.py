#!/usr/bin/env python3
"""Textures of the stones of THE BELOW (16x16, tiling):
  forgotten_stone  pale, ash-grey stone worn smooth, with hairline cracks and faint scratched tally marks —
                   as if someone down there once counted the days on it
  oldrock          dark brown-black rock in thin layers, pitted, older than the world above
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


def main():
    os.makedirs(OUT, exist_ok=True)
    forgotten_stone().save(os.path.join(OUT, "forgotten_stone.png"))
    oldrock().save(os.path.join(OUT, "oldrock.png"))


if __name__ == "__main__":
    main()
