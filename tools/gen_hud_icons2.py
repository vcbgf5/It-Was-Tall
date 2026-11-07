#!/usr/bin/env python3
"""More HUD sprites of It Was Tall, in the same style as tools/gen_hud_icons.py (journal colours):
  hud/eye_0..5   24x14  THE EYE: closed .. wide open (It is watching you; wide open = it is about to move)
  hud/save_0..7  16x16  SAVING: a sheet of parchment, a quill writing line after line
  hud/flashlight_on / _off  16x16  THE FLASHLIGHT: an old metal torch, its lens lit (with a beam) or dark
Run: python3 tools/gen_hud_icons2.py"""
import math, os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/gui/sprites/hud")
INK = (24, 14, 10, 255)
BLOOD, BLOOD_D, BLOOD_L = (128, 18, 14, 255), (78, 8, 6, 255), (186, 44, 32, 255)
PARCH, PARCH_D = (222, 204, 166, 255), (170, 150, 112, 255)
WHITE = (236, 226, 204, 255)
VEIN = (176, 70, 56, 255)


def shadow(img):
    w, h = img.size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for x in range(w - 1):
        for y in range(h - 1):
            if px[x, y][3] > 0:
                out.putpixel((x + 1, y + 1), (0, 0, 0, 150))
    out.alpha_composite(img)
    return out


def eye(n):
    """n = 0 (shut) .. 5 (wide open)."""
    W, H = 24, 14
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    cx, cy = 11.5, 6.5
    open_ = n / 5.0
    half = [max(0.0, 5.6 * math.sin(math.pi * (x + 0.5) / W)) for x in range(W)]
    if n == 0:
        # a shut lid: a dark curved line with three lashes hanging down
        for x in range(2, W - 2):
            y = round(cy + 1.2 * math.sin(math.pi * (x + 0.5) / W))
            px[x, y] = INK
        for lx in (7, 11, 15):
            y = round(cy + 1.2 * math.sin(math.pi * (lx + 0.5) / W))
            px[lx, y + 1] = INK
            px[lx - (1 if lx < 11 else -1 if lx > 11 else 0), y + 2] = INK
        return shadow(img)
    for x in range(W):
        top = half[x] * open_
        bot = half[x] * max(0.35, open_)
        for y in range(H):
            dy = y + 0.5 - cy
            if -top <= dy <= bot:
                px[x, y] = WHITE
    # veins creeping in from the corners, more the wider it opens
    veins = [((2, 6), (6, 5), (8, 6)), ((2, 7), (5, 8), (7, 8)), ((21, 6), (18, 5), (16, 6)), ((21, 7), (18, 8), (16, 8))]
    for i, v in enumerate(veins):
        if i >= n - 1 + 2:
            continue
        for a, b in zip(v, v[1:]):
            steps = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
            for s in range(steps + 1):
                x = round(a[0] + (b[0] - a[0]) * s / max(1, steps))
                y = round(a[1] + (b[1] - a[1]) * s / max(1, steps))
                if px[x, y] == WHITE:
                    px[x, y] = VEIN
    # the iris (blood red) and a pupil that narrows to a slit as it opens
    r = 3.6
    for x in range(W):
        for y in range(H):
            if px[x, y][3] == 0:
                continue
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                px[x, y] = BLOOD_L if d < r - 1.2 else BLOOD
            pw = 1.6 - 0.25 * n
            if abs(x + 0.5 - cx) <= max(0.5, pw) and abs(y + 0.5 - cy) <= 2.6 and px[x, y][3] > 0:
                px[x, y] = INK
    if n >= 2:
        px[int(cx) - 1, int(cy) - 2] = WHITE if px[int(cx) - 1, int(cy) - 2][3] else px[int(cx) - 1, int(cy) - 2]
    # the outline (lids), lashes on top
    mask = [[px[x, y][3] > 0 for y in range(H)] for x in range(W)]
    for x in range(W):
        for y in range(H):
            if mask[x][y]:
                continue
            near = any(0 <= x + dx < W and 0 <= y + dy < H and mask[x + dx][y + dy]
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near:
                px[x, y] = INK
    if n >= 3:
        for lx in (6, 11, 16):
            top = round(cy - half[lx] * open_) - 2
            if 0 <= top < H:
                px[lx, top] = INK
                if top - 1 >= 0:
                    px[lx + (1 if lx > 11 else -1 if lx < 11 else 0), top - 1] = INK
    return shadow(img)


def save_icon(f):
    """f = 0..7: the quill writes the lines of a page one after another."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # the sheet, a little crooked corner
    d.rectangle([1, 2, 10, 14], fill=PARCH, outline=INK)
    d.point((10, 2), fill=(0, 0, 0, 0)); d.point((9, 3), fill=INK); d.point((10, 3), fill=INK)
    d.line([2, 13, 9, 13], fill=PARCH_D)
    lines = [(3, 4, 9), (3, 6, 8), (3, 8, 9), (3, 10, 7)]
    total = 8.0
    progress = (f + 1) / total * len(lines)        # how many lines are written (fractional)
    tip = None
    for i, (x0, y, x1) in enumerate(lines):
        part = max(0.0, min(1.0, progress - i))
        if part <= 0:
            break
        xe = x0 + round((x1 - x0) * part)
        for x in range(x0, xe + 1):
            if (x + i) % 4 != 3:                     # handwriting: little gaps between the words
                img.putpixel((x, y), INK)
        tip = (xe, y)
    if f == 7:
        img.putpixel((8, 11), BLOOD)                 # a drop of blood as the signature
        img.putpixel((8, 12), BLOOD_D)
    # the quill: its nib at the end of the line being written, the feather up and to the right
    tx, ty = tip if tip else (3, 4)
    pts = [(tx, ty), (tx + 1, ty - 1), (tx + 2, ty - 2), (tx + 3, ty - 3), (tx + 4, ty - 4), (tx + 5, ty - 5)]
    for i, (x, y) in enumerate(pts):
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), INK if i < 2 else WHITE)
    for (x, y) in pts[2:]:
        for (ox, oy) in ((1, 0), (0, -1)):
            if 0 <= x + ox < 16 and 0 <= y + oy < 16 and img.getpixel((x + ox, y + oy))[3] == 0:
                img.putpixel((x + ox, y + oy), PARCH_D)
    return shadow(img)


def flashlight(on):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    G, GL = (110, 110, 118, 255), (168, 168, 176, 255)
    # the body, lying diagonally: tail bottom-left, head (lens) top-right
    for i in range(7):
        x, y = 2 + i, 12 - i
        d.point((x, y), fill=INK); d.point((x + 1, y), fill=G); d.point((x + 1, y + 1), fill=INK)
        d.point((x, y - 1), fill=INK); d.point((x + 2, y + 1), fill=INK)
        if i == 2: d.point((x + 1, y), fill=BLOOD)              # the switch
    d.polygon([(8, 4), (11, 1), (14, 4), (11, 7)], fill=GL, outline=INK)
    lens = (250, 226, 130, 255) if on else (40, 40, 48, 255)
    d.polygon([(10, 3), (11, 2), (13, 4), (12, 5)], fill=lens)
    if on:
        for (x, y) in ((14, 1), (15, 0), (13, 0), (15, 2)):
            img.putpixel((x, y), (250, 226, 130, 200))
    return shadow(img)


def main():
    os.makedirs(OUT, exist_ok=True)
    for n in range(6):
        eye(n).save(os.path.join(OUT, f"eye_{n}.png"))
    for f in range(8):
        save_icon(f).save(os.path.join(OUT, f"save_{f}.png"))
    flashlight(True).save(os.path.join(OUT, "flashlight_on.png"))
    flashlight(False).save(os.path.join(OUT, "flashlight_off.png"))


if __name__ == "__main__":
    main()
