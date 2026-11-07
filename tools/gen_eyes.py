#!/usr/bin/env python3
"""THE EYES: one pair of glowing eyes (32x16, drawn additively by the client: black = nothing).
Run: python3 tools/gen_eyes.py"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/entity/eye_pair.png")


def eye(d, cx):
    d.polygon([(cx - 5, 8), (cx - 2, 5), (cx + 2, 5), (cx + 5, 8), (cx + 2, 10), (cx - 2, 10)], fill=(225, 228, 220, 255))
    d.rectangle([cx - 1, 6, cx, 9], fill=(150, 30, 26, 255))      # a thin red iris
    d.point((cx - 1, 7), fill=(20, 4, 4, 255))                     # the pupil


def main():
    img = Image.new("RGBA", (32, 16), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    eye(d, 9)
    eye(d, 23)
    img.save(OUT)
    print("eyes written")


if __name__ == "__main__":
    main()
