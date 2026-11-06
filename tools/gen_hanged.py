#!/usr/bin/env python3
"""THE HANGED: the rope texture (16x16, twisted brown hemp) used by the client renderer for the rope and the
noose. Run: python3 tools/gen_hanged.py"""
import os, random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/entity/hanged_rope.png")


def main():
    r = random.Random(77)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            # diagonal strands: light / dark bands every 4 px, shifted per row = the twist
            band = ((x + y) // 2) % 4
            base = [(122, 92, 58), (104, 76, 46), (86, 62, 38), (110, 82, 50)][band]
            n = r.randint(-7, 7)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
    for _ in range(14):   # loose fibres
        x, y = r.randrange(16), r.randrange(16)
        img.putpixel((x, y), (150, 120, 82, 255))
    img.save(OUT)
    print("rope written")


if __name__ == "__main__":
    main()
