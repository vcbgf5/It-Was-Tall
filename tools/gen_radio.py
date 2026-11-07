#!/usr/bin/env python3
"""The RADIO item texture (16x16): an old battered portable radio with an antenna and a dial.
Run: python3 tools/gen_radio.py"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/item/radio.png")


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.line([11, 1, 13, 5], fill=(150, 150, 156, 255))                       # antenna
    d.point((11, 1), fill=(200, 40, 30, 255))
    d.rectangle([1, 5, 14, 14], fill=(74, 52, 36, 255), outline=(26, 16, 10, 255))   # body
    d.rectangle([2, 6, 13, 7], fill=(96, 70, 48, 255))
    d.rectangle([2, 8, 8, 13], fill=(40, 36, 32, 255))                      # speaker grille
    for y in (9, 11):
        d.line([3, y, 7, y], fill=(70, 64, 58, 255))
    d.rectangle([10, 8, 13, 10], fill=(200, 176, 120, 255), outline=(30, 20, 12, 255))  # dial
    d.point((11, 9), fill=(170, 30, 20, 255))
    d.ellipse([10, 11, 12, 13], fill=(140, 140, 146, 255))                  # knob
    img.save(OUT)
    print("radio written")


if __name__ == "__main__":
    main()
