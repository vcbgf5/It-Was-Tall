#!/usr/bin/env python3
"""Texture of the CRACKED BEDROCK block (16x16): bedrock-dark stone with a deep crack that glows faintly red,
as if something lit were down there. Run: python3 tools/gen_cracked_bedrock.py"""
import os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/block/cracked_bedrock.png")


def main():
    rnd = random.Random(6413)
    img = Image.new("RGBA", (16, 16))
    greys = [(38, 38, 40), (58, 58, 62), (86, 86, 90), (120, 120, 124), (28, 28, 30)]
    for y in range(16):
        for x in range(16):
            w = [5, 6, 3, 1, 3]
            img.putpixel((x, y), greys[rnd.choices(range(5), w)[0]] + (255,))
    # the crack: a jagged path from top to bottom with two branches; red glow in its heart
    path = [(7, 0), (7, 1), (6, 2), (6, 3), (7, 4), (8, 5), (8, 6), (7, 7), (7, 8), (8, 9), (9, 10), (9, 11), (8, 12), (8, 13), (7, 14), (7, 15)]
    branch = [(8, 6), (9, 6), (10, 5), (11, 5), (12, 4), (7, 8), (6, 9), (5, 9), (4, 10), (3, 11)]
    for (x, y) in path + branch:
        img.putpixel((x, y), (10, 6, 6, 255))
    for (x, y) in path[2:-2]:
        img.putpixel((x, y), (150, 26, 18, 255) if (x + y) % 3 else (214, 60, 34, 255))
    for (x, y) in branch[1:4] + branch[6:8]:
        img.putpixel((x, y), (96, 16, 12, 255))
    img.save(OUT)


if __name__ == "__main__":
    main()
