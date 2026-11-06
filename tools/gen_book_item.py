#!/usr/bin/env python3
"""Writes textures/item/lore_book.png: the mod's own book item (16x16 pixel art) — dark worn leather,
a pale page edge, a dried blood hand-print smear and a red bookmark ribbon. Touches nothing else
(the GUI atlas from gen_gui.py is hand-edited now and must not be regenerated)."""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/item/lore_book.png")

_ = None
K = (24, 14, 10, 255)      # outline
L1 = (64, 36, 26, 255)     # leather
L2 = (84, 50, 34, 255)     # leather light
L3 = (46, 26, 19, 255)     # leather dark
P1 = (222, 206, 170, 255)  # page
P2 = (190, 172, 136, 255)  # page shade
R1 = (128, 18, 16, 255)    # blood / ribbon
R2 = (86, 10, 10, 255)
G = (150, 120, 70, 255)    # brass corner

ART = [
    "................",
    "...KKKKKKKKKK...",
    "..KGLLLLLLLLGK..",
    "..KLHLLLLLLLLKP.",
    "..KLLLLLLLLDLKP.",
    "..KLLLRRLLLLLKP.",
    "..KLLRRRRLLLLKP.",
    "..KLLRRRLLLLLKP.",
    "..KLLLRLLLLLLKP.",
    "..KLLLLLLLLLLKP.",
    "..KLLLLLLLLLDKS.",
    "..KLLLLLLLLLLKS.",
    "..KGLLLLLLLLGKS.",
    "...KKKKKKBKKKK..",
    ".........B......",
    ".........b......",
]
COL = {".": _, "K": K, "L": L1, "H": L2, "D": L3, "P": P1, "S": P2, "R": R1, "G": G, "B": R1, "b": R2}


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(ART):
        for x, ch in enumerate(row):
            c = COL[ch]
            if c is not None:
                img.putpixel((x, y), c)
    # a little leather grain
    for (x, y) in [(5, 3), (9, 4), (7, 9), (10, 11), (4, 10), (11, 6)]:
        img.putpixel((x, y), L3)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
