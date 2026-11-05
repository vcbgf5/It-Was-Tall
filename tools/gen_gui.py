#!/usr/bin/env python3
"""Generates the GUI / HUD textures of It Was Tall (all original, procedural):

  textures/gui/journal.png   512x256  open leather journal (400x256) + sprites column (x >= 400)
  textures/gui/note.png      256x256  torn parchment page (224x240)
  textures/gui/toast.png     256x64   dark torn paper strip for task notifications (220x44)
  textures/gui/vignette.png  256x256  smoky dark vignette (alpha), shared by the fear HUD / overlays
  textures/gui/chase_vignette.png 256x256 red chase overlay with veins creeping from the edges

Sprite map of journal.png (keep in sync with JournalScreen):
  ribbon (inactive)  x=400 y=0   20x34      ribbon (active) x=424 y=0 20x40
  tension frame      x=400 y=48  104x10     tension fill    x=400 y=60 100x6
  stage mark empty   x=400 y=72  10x10      stage mark full x=412 y=72 10x10
  close X            x=400 y=88  12x12
  checkbox off/on    x=416 y=88  10x10 / x=428 y=88 10x10
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/gui")
rnd = random.Random(1311)


def noise_layer(w, h, scale, amount, seed):
    r = random.Random(seed)
    small = Image.new("L", (max(1, w // scale), max(1, h // scale)))
    px = small.load()
    for y in range(small.height):
        for x in range(small.width):
            px[x, y] = r.randint(128 - amount, 128 + amount)
    return small.resize((w, h), Image.BICUBIC)


def tint(base_rgb, noise, strength):
    """Apply a grey noise layer (centred at 128) onto a flat colour."""
    w, h = noise.size
    img = Image.new("RGB", (w, h), base_rgb)
    p, n = img.load(), noise.load()
    for y in range(h):
        for x in range(w):
            d = (n[x, y] - 128) * strength
            r, g, b = base_rgb
            p[x, y] = (max(0, min(255, int(r + d))), max(0, min(255, int(g + d * 0.9))), max(0, min(255, int(b + d * 0.75))))
    return img


def parchment(w, h, seed, edge_dark=70):
    n1 = noise_layer(w, h, 6, 22, seed)
    n2 = noise_layer(w, h, 1, 10, seed + 1)
    page = tint((214, 196, 158), n1, 0.55)
    page = Image.blend(page, tint((214, 196, 158), n2, 0.9), 0.35)
    p = page.load()
    # darker, burnt-looking edges
    for y in range(h):
        for x in range(w):
            d = min(x, y, w - 1 - x, h - 1 - y)
            k = max(0.0, 1 - d / 18.0) ** 1.6
            r, g, b = p[x, y]
            p[x, y] = (int(r - edge_dark * k), int(g - edge_dark * 1.1 * k), int(b - edge_dark * 1.2 * k))
    return page


def blood_smear(img, x, y, w, h, seed, alpha=150, drips=2):
    """A dried blood splatter: one irregular main blot with a darker rim, satellite droplets
    flung in one direction and a few drips running down with a rounded drop at the end."""
    r = random.Random(seed)
    layer = Image.new("RGBA", img.size)
    d = ImageDraw.Draw(layer)
    cx, cy = x + w * 0.45, y + h * 0.4
    base = min(w, h) * 0.32
    # main blot: overlapping lobes
    for _ in range(9):
        ox, oy = r.gauss(0, base * 0.45), r.gauss(0, base * 0.3)
        rad = base * r.uniform(0.45, 0.9)
        d.ellipse([cx + ox - rad, cy + oy - rad * 0.8, cx + ox + rad, cy + oy + rad * 0.8], fill=(110, 12, 10, alpha))
    # satellite droplets, flung to one side
    ang0 = r.uniform(0, math.tau)
    for _ in range(16):
        ang = ang0 + r.gauss(0, 0.5)
        dist = base * r.uniform(1.2, 3.2)
        rad = r.uniform(0.6, 2.2) * (1.4 - dist / (base * 3.4))
        px_, py_ = cx + math.cos(ang) * dist, cy + math.sin(ang) * dist * 0.7
        d.ellipse([px_ - rad, py_ - rad, px_ + rad, py_ + rad], fill=(105, 10, 8, int(alpha * 0.9)))
    # drips with a rounded drop at the end
    for _ in range(drips):
        dx = cx + r.uniform(-base * 0.6, base * 0.6)
        ln = r.uniform(h * 0.6, h * 1.4)
        width = r.uniform(1.2, 2.4)
        d.line([dx, cy, dx + r.uniform(-0.8, 0.8), cy + ln], fill=(100, 9, 7, alpha), width=int(round(width)))
        d.ellipse([dx - width, cy + ln - width, dx + width, cy + ln + width * 1.3], fill=(92, 8, 6, alpha))
    soft = layer.filter(ImageFilter.GaussianBlur(0.7))
    # darker rim: the same shape eroded and lightened in the middle (dried blood looks like that)
    a = soft.split()[3]
    core = a.filter(ImageFilter.MinFilter(3)).filter(ImageFilter.GaussianBlur(1.2))
    rim = Image.new("RGBA", img.size, (62, 4, 3, 0))
    rim.putalpha(a)
    centre = Image.new("RGBA", img.size, (130, 22, 16, 0))
    centre.putalpha(core.point(lambda v: int(v * 0.75)))
    img.alpha_composite(rim)
    img.alpha_composite(centre)


def scratches(img, box, seed, count=5, color=(60, 40, 25, 60)):
    d = ImageDraw.Draw(img, "RGBA")
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for _ in range(count):
        sx, sy = r.uniform(x0, x1), r.uniform(y0, y1)
        ang = r.uniform(-0.4, 0.4) + math.pi / 2.6
        ln = r.uniform(12, 30)
        for k in range(3):  # three claw lines
            ox = k * 3
            d.line([sx + ox, sy, sx + ox + math.cos(ang) * ln, sy + math.sin(ang) * ln], fill=color, width=1)


# ------------------------------------------------------------------------------ journal
def journal():
    W, H = 512, 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # leather cover 400x256
    cover_noise = noise_layer(400, 256, 3, 30, 21)
    cover = tint((58, 34, 24), cover_noise, 0.45).convert("RGBA")
    cd = ImageDraw.Draw(cover)
    cd.rounded_rectangle([0, 0, 399, 255], radius=10, outline=(28, 16, 11, 255), width=3)
    # stitching around the edge
    for x in range(14, 386, 8):
        cd.line([x, 7, x + 4, 7], fill=(150, 120, 80, 200))
        cd.line([x, 248, x + 4, 248], fill=(150, 120, 80, 200))
    for y in range(14, 242, 8):
        cd.line([7, y, 7, y + 4], fill=(150, 120, 80, 200))
        cd.line([392, y, 392, y + 4], fill=(150, 120, 80, 200))
    mask = Image.new("L", (400, 256), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 399, 255], radius=10, fill=255)
    img.paste(cover, (0, 0), mask)
    # two pages, a slight page stack under each
    for side, (px, seed) in enumerate(((16, 31), (202, 41))):
        stack = Image.new("RGBA", (184, 230), (176, 158, 120, 255))
        img.paste(stack, (px + (0 if side == 0 else 2), 15))
        page = parchment(182, 226, seed).convert("RGBA")
        img.paste(page, (px, 13))
    # gutter shadow in the middle
    g = Image.new("RGBA", (40, 230))
    gd = ImageDraw.Draw(g)
    for i in range(20):
        a = int(150 * (1 - i / 20) ** 2)
        gd.line([20 - i, 0, 20 - i, 229], fill=(30, 18, 10, a))
        gd.line([19 + i, 0, 19 + i, 229], fill=(30, 18, 10, a))
    img.alpha_composite(g, (180, 13))
    # blood: a splatter at the corner of the right page and a smudge where a hand held the left one
    blood_smear(img, 318, 188, 60, 26, 5, 165, drips=3)
    blood_smear(img, 26, 20, 34, 14, 9, 120, drips=1)
    d = ImageDraw.Draw(img, "RGBA")

    # ------------------------------------------------------------- sprites (x >= 400)
    def ribbon(x, y, w, h, col):
        rb = Image.new("RGBA", (w, h))
        rd = ImageDraw.Draw(rb)
        rd.polygon([(0, 0), (w - 1, 0), (w - 1, h - 1), (w // 2, h - 7), (0, h - 1)], fill=col)
        for yy in range(0, h - 7, 2):
            rd.line([1, yy, w - 2, yy], fill=tuple(max(0, c - 18) for c in col[:3]) + (255,))
        rd.line([0, 0, 0, h - 1], fill=(0, 0, 0, 90))
        rd.line([w - 1, 0, w - 1, h - 1], fill=(0, 0, 0, 90))
        img.alpha_composite(rb, (x, y))

    ribbon(400, 0, 20, 34, (92, 22, 20, 255))    # inactive: dark red
    ribbon(424, 0, 20, 40, (168, 30, 24, 255))   # active: blood red, longer
    # tension meter
    d.rectangle([400, 48, 503, 57], outline=(50, 34, 22, 255), fill=(190, 170, 130, 255))
    for x in range(400, 500):
        t = (x - 400) / 99
        col = (int(110 + 70 * t), int(40 - 30 * t), int(30 - 20 * t), 255)
        d.line([x, 60, x, 65], fill=col)
    # stage marks (ink circle empty / filled)
    d.ellipse([401, 73, 409, 81], outline=(50, 30, 20, 255), width=1)
    d.ellipse([413, 73, 421, 81], fill=(120, 18, 14, 255), outline=(50, 10, 8, 255))
    # close X (scratched)
    d.line([402, 90, 410, 98], fill=(70, 20, 14, 255), width=2)
    d.line([410, 90, 402, 98], fill=(70, 20, 14, 255), width=2)
    # checkbox off / on
    d.rectangle([416, 88, 425, 97], outline=(50, 34, 22, 255))
    d.rectangle([428, 88, 437, 97], outline=(50, 34, 22, 255))
    d.line([430, 92, 432, 95], fill=(120, 18, 14, 255), width=2)
    d.line([432, 95, 436, 89], fill=(120, 18, 14, 255), width=2)
    img.save(os.path.join(OUT, "journal.png"))


# ------------------------------------------------------------------------------ note
def note():
    W = H = 256
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    page = parchment(224, 240, 77, 85).convert("RGBA")
    # torn edges: random bites out of the border
    mask = Image.new("L", (224, 240), 255)
    md = ImageDraw.Draw(mask)
    r = random.Random(4)
    for x in range(0, 224, 3):
        md.rectangle([x, 0, x + 2, r.randint(0, 4)], fill=0)
        md.rectangle([x, 240 - r.randint(1, 6), x + 2, 239], fill=0)
    for y in range(0, 240, 3):
        md.rectangle([0, y, r.randint(0, 3), y + 2], fill=0)
        md.rectangle([224 - r.randint(1, 4), y, 223, y + 2], fill=0)
    md.polygon([(170, 240), (224, 190), (224, 240)], fill=0)  # torn corner
    page.putalpha(mask)
    shadow = Image.new("RGBA", (W, H))
    shadow.paste((0, 0, 0, 120), (20, 14), mask)
    img.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(4)))
    img.alpha_composite(page, (16, 8))
    blood_smear(img, 36, 196, 56, 22, 3, 160, drips=2)
    img.save(os.path.join(OUT, "note.png"))


# ------------------------------------------------------------------------------ toast
def toast():
    img = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    n = noise_layer(220, 44, 2, 26, 55)
    strip = tint((26, 22, 22), n, 0.5).convert("RGBA")
    mask = Image.new("L", (220, 44), 255)
    md = ImageDraw.Draw(mask)
    r = random.Random(8)
    for x in range(0, 220, 2):
        md.rectangle([x, 0, x + 1, r.randint(0, 2)], fill=0)
        md.rectangle([x, 44 - r.randint(1, 3), x + 1, 43], fill=0)
    md.polygon([(200, 0), (220, 0), (220, 14)], fill=0)
    strip.putalpha(mask)
    d = ImageDraw.Draw(strip, "RGBA")
    d.line([4, 3, 214, 3], fill=(90, 70, 60, 120))
    img.alpha_composite(strip, (0, 0))
    img.save(os.path.join(OUT, "toast.png"))


# ------------------------------------------------------------------------------ vignettes
def _fbm(S, seed, octaves=((8, 1.0), (16, 0.5), (32, 0.25), (64, 0.12))):
    """Smooth fractal noise 0..1 (sum of upscaled random grids)."""
    acc = [[0.0] * S for _ in range(S)]
    total = 0.0
    for cells, weight in octaves:
        layer = noise_layer(S, S, max(1, S // cells), 120, seed + cells).load()
        for y in range(S):
            row = acc[y]
            for x in range(S):
                row[x] += (layer[x, y] / 255.0) * weight
        total += weight
    return [[v / total for v in row] for row in acc]


def _smooth(e0, e1, v):
    t = max(0.0, min(1.0, (v - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def vignette():
    """Dark, smoky edge: smooth falloff, deeper corners, the border breathes with soft noise."""
    S = 256
    fbm = _fbm(S, 404)
    img = Image.new("RGBA", (S, S))
    p = img.load()
    for y in range(S):
        for x in range(S):
            dx, dy = (x - S / 2 + 0.5) / (S / 2), (y - S / 2 + 0.5) / (S / 2)
            # rounded-rectangle distance: wide screens keep the centre clear, corners get darker
            d = (abs(dx) ** 3 + abs(dy) ** 3) ** (1 / 3)
            n = fbm[y][x]
            edge = _smooth(0.52 + (n - 0.5) * 0.22, 1.08, d)
            a = edge ** 1.35
            r = int(12 + 10 * (1 - n))
            p[x, y] = (r, 2, 4, int(255 * min(1.0, a)))
    img.save(os.path.join(OUT, "vignette.png"))


def chase_vignette():
    """Red chase overlay: a blood-red rim with dark veins creeping in from the edges."""
    S = 256
    fbm = _fbm(S, 777)
    veins = Image.new("L", (S, S), 0)
    vd = ImageDraw.Draw(veins)
    r = random.Random(99)
    for _ in range(26):
        # start on a random edge, wander inwards with branches
        side = r.randint(0, 3)
        t = r.uniform(0, S)
        x, y = [(t, 0), (S, t), (t, S), (0, t)][side]
        ang = [math.pi / 2, math.pi, -math.pi / 2, 0][side] + r.uniform(-0.6, 0.6)
        width = r.uniform(2.5, 4.5)
        for step in range(r.randint(10, 22)):
            nx, ny = x + math.cos(ang) * 4.5, y + math.sin(ang) * 4.5
            vd.line([x, y, nx, ny], fill=int(255 * min(1.0, width / 4)), width=max(1, int(width)))
            x, y = nx, ny
            ang += r.uniform(-0.45, 0.45)
            width *= 0.9
            if r.random() < 0.12:  # branch
                bx, by, ba = x, y, ang + r.choice([-1, 1]) * r.uniform(0.5, 1.0)
                bw = width * 0.8
                for _ in range(r.randint(3, 7)):
                    nbx, nby = bx + math.cos(ba) * 4, by + math.sin(ba) * 4
                    vd.line([bx, by, nbx, nby], fill=int(200 * min(1.0, bw / 4)), width=max(1, int(bw)))
                    bx, by = nbx, nby
                    ba += r.uniform(-0.5, 0.5)
                    bw *= 0.85
    veins = veins.filter(ImageFilter.GaussianBlur(0.8))
    vp = veins.load()
    img = Image.new("RGBA", (S, S))
    p = img.load()
    for y in range(S):
        for x in range(S):
            dx, dy = (x - S / 2 + 0.5) / (S / 2), (y - S / 2 + 0.5) / (S / 2)
            d = (abs(dx) ** 3 + abs(dy) ** 3) ** (1 / 3)
            n = fbm[y][x]
            rim = _smooth(0.5 + (n - 0.5) * 0.25, 1.05, d)
            vein = vp[x, y] / 255.0 * _smooth(0.25, 0.95, d)
            a = min(1.0, rim ** 1.2 * 0.9 + vein * 0.85)
            # bright blood red at the inner rim, almost black at the very edge and in the veins
            dark = min(1.0, _smooth(0.85, 1.15, d) + vein * 0.7)
            red = int(150 * (1 - dark) + 34 * dark)
            p[x, y] = (red, int(6 + 8 * (1 - dark)), int(8 + 6 * (1 - dark)), int(255 * a))
    img.save(os.path.join(OUT, "chase_vignette.png"))


def note_item():
    """16x16 inventory icon: a folded, torn parchment slip with a drop of blood."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    p = img.load()
    r = random.Random(16)
    for y in range(2, 15):
        for x in range(3, 13):
            if (x, y) in ((3, 2), (12, 14), (12, 13), (3, 14)):
                continue                      # torn corners
            base = 205 if x < 8 else 188      # folded: right half in shadow
            v = base + r.randint(-12, 8)
            p[x, y] = (v, int(v * 0.9), int(v * 0.72), 255)
    for x in range(3, 13):                    # dark outline top/bottom
        p[x, 2] = (110, 90, 64, 255)
        p[x, 14] = (96, 78, 55, 255)
    for y in range(2, 15):
        p[3, y] = (110, 90, 64, 255)
        p[12, y] = (90, 72, 50, 255)
    for y in (5, 7, 9, 11):                   # ink lines
        for x in range(5, 11 if y != 11 else 8):
            p[x, y] = (70, 56, 44, 255)
    for (x, y) in ((10, 11), (11, 11), (10, 12), (11, 10)):   # blood
        p[x, y] = (120, 14, 10, 255)
    os.makedirs(os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/item"), exist_ok=True)
    img.save(os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/item/note.png"))


if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    journal()
    note()
    toast()
    vignette()
    chase_vignette()
    note_item()
    print("gui textures written to", OUT)
