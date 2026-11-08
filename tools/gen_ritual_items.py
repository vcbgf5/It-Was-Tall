#!/usr/bin/env python3
"""Items of the ending, and the sketch on the Ritual Plan:
  item/hollow_heart     a black, dried heart with a hole through it, faint red glow
  item/ritual_plan      a folded sheet with a drawing on it
  item/forgotten_book   a book bound in pale stone-grey leather with an eye
  gui/ritual_plan.png   (256x192) the plan: top view of the ritual and a side view, drawn in ink on parchment
Run: python3 tools/gen_ritual_items.py"""
import math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/item")
GUI = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/gui")
INK = (24, 14, 10, 255)
BLOOD, BLOOD_D, BLOOD_L = (128, 18, 14, 255), (78, 8, 6, 255), (186, 44, 32, 255)
PARCH, PARCH_D = (222, 204, 166, 255), (170, 150, 112, 255)


def grid(rows, pal):
    img = Image.new("RGBA", (16, 16))
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c != ".":
                img.putpixel((x, y), pal[c])
    return img


def hollow_heart():
    return grid([
        "................",
        ".......rr.......",
        "....kk.rr.kk....",
        "...kddkrrkddk...",
        "..kddddkkddddk..",
        "..kdRddddddddk..",
        "..kdRdddddddddk.",
        "..kddddkkddddk..",
        "..kdddk..kdddk..",
        "...kddk..kddk...",
        "....kddkkddk....",
        ".....kddddk.....",
        "......kddk......",
        ".......kk.......",
        "................",
        "................"], {"k": INK, "d": (52, 14, 12, 255), "R": BLOOD_L, "r": BLOOD})


def ritual_plan():
    return grid([
        "................",
        "..kkkkkkkkkkk...",
        "..kpppppppppkk..",
        "..kpppkpppppPk..",
        "..kpppppppppPk..",
        "..kpkpprpppkpk..",
        "..kpppprrppppk..",
        "..kpppprpppppk..",
        "..kpppppppppPk..",
        "..kpppkpppppPk..",
        "..kpppppppppPk..",
        "..kpPPPPPPPPPk..",
        "..kkkkkkkkkkkk..",
        "................",
        "................",
        "................"], {"k": INK, "p": PARCH, "P": PARCH_D, "r": BLOOD})


def forgotten_book():
    return grid([
        "................",
        "...kkkkkkkkkk...",
        "..kGGGGGGGGGGk..",
        "..kGgggggggGGk..",
        "..kGgkkkkkgGGk..",
        "..kGkwwrwwkGGk..",
        "..kGgkkkkkgGGk..",
        "..kGgggggggGGk..",
        "..kGgggggggGGk..",
        "..kGgggggggGGk..",
        "..kGGGGGGGGGGk..",
        "..kkkkkkkkkkkk..",
        "..kppppppppppk..",
        "...kkkkkkkkkk...",
        "................",
        "................"], {"k": INK, "G": (150, 148, 140, 255), "g": (122, 120, 113, 255),
                             "w": (236, 226, 204, 255), "r": BLOOD, "p": PARCH})


def plan_sketch():
    """256x192 parchment: left the top view (oldrock 3x3 floor, campfire, four pillars 3 blocks away), right the side view."""
    W, H = 256, 192
    rnd = random.Random(7)
    img = Image.new("RGBA", (W, H), PARCH)
    px = img.load()
    for y in range(H):
        for x in range(W):
            n = rnd.randint(-10, 6)
            r, g, b, a = PARCH
            edge = min(x, y, W - 1 - x, H - 1 - y)
            dark = max(0, 18 - edge) * 2
            px[x, y] = (max(0, r + n - dark), max(0, g + n - dark), max(0, b + n - dark - 4), 255)
    d = ImageDraw.Draw(img)
    ink = (40, 26, 18, 255)
    # ---- top view: a 7x7 grid of cells, 14 px each
    C, ox, oy = 14, 14, 40
    for i in range(8):
        d.line([ox, oy + i * C, ox + 7 * C, oy + i * C], fill=(150, 130, 100, 255))
        d.line([ox + i * C, oy, ox + i * C, oy + 7 * C], fill=(150, 130, 100, 255))
    def cell(cx, cy, fill):
        d.rectangle([ox + cx * C + 2, oy + cy * C + 2, ox + cx * C + C - 2, oy + cy * C + C - 2], fill=fill, outline=ink)
    for cx in range(2, 5):
        for cy in range(2, 5):
            cell(cx, cy, (70, 60, 54, 255))                     # oldrock bricks
    x0, y0 = ox + 3 * C, oy + 3 * C                           # the campfire
    d.rectangle([x0 + 3, y0 + 3, x0 + C - 3, y0 + C - 3], fill=(120, 80, 44, 255), outline=ink)
    d.polygon([(x0 + 7, y0 + 2), (x0 + 11, y0 + 9), (x0 + 3, y0 + 9)], fill=(226, 120, 40, 255))
    for (cx, cy) in ((3, 0), (0, 3), (6, 3), (3, 6)):          # the pillars
        cell(cx, cy, (176, 174, 165, 255))
        px_c, py_c = ox + cx * C + C // 2, oy + cy * C + C // 2
        d.ellipse([px_c - 2, py_c - 2, px_c + 2, py_c + 2], fill=(250, 226, 130, 255), outline=ink)
    # distance marks
    d.line([ox + 3 * C + C // 2, oy + C, ox + 3 * C + C // 2, oy + 3 * C], fill=(128, 18, 14, 255))
    # ---- side view
    sx, gy = 136, 150
    d.line([sx - 4, gy, sx + 112, gy], fill=ink, width=2)
    for i in range(3):                                         # oldrock floor (3 wide)
        d.rectangle([sx + 36 + i * 12, gy - 12, sx + 48 + i * 12, gy], fill=(70, 60, 54, 255), outline=ink)
    d.rectangle([sx + 48 + 2, gy - 22, sx + 60 - 2, gy - 12], fill=(120, 80, 44, 255), outline=ink)   # campfire on top
    d.polygon([(sx + 54, gy - 34), (sx + 59, gy - 22), (sx + 49, gy - 22)], fill=(226, 120, 40, 255))
    for px_l in (sx + 0, sx + 96):                             # two pillars, 2 high, candle on top
        d.rectangle([px_l, gy - 24, px_l + 12, gy], fill=(176, 174, 165, 255), outline=ink)
        d.line([px_l, gy - 12, px_l + 12, gy - 12], fill=ink)
        d.rectangle([px_l + 4, gy - 32, px_l + 8, gy - 24], fill=(236, 226, 204, 255), outline=ink)
        d.polygon([(px_l + 6, gy - 38), (px_l + 8, gy - 33), (px_l + 4, gy - 33)], fill=(250, 200, 90, 255))
    # the heart into the fire
    d.line([sx + 54, gy - 60, sx + 54, gy - 40], fill=(128, 18, 14, 255), width=2)
    d.polygon([(sx + 50, gy - 44), (sx + 58, gy - 44), (sx + 54, gy - 38)], fill=(128, 18, 14, 255))
    d.ellipse([sx + 49, gy - 70, sx + 59, gy - 60], fill=(52, 14, 12, 255), outline=ink)
    return img


def main():
    os.makedirs(ITEM, exist_ok=True)
    hollow_heart().save(os.path.join(ITEM, "hollow_heart.png"))
    ritual_plan().save(os.path.join(ITEM, "ritual_plan.png"))
    forgotten_book().save(os.path.join(ITEM, "forgotten_book.png"))
    plan_sketch().save(os.path.join(GUI, "ritual_plan.png"))


if __name__ == "__main__":
    main()
