#!/usr/bin/env python3
"""It Was Tall UI — the built-in resource pack "It Was Tall UI" (enabled by default, can be switched off in
Resource Packs): every Minecraft button, slider, text field and checkbox in the mod's style (dark stitched
leather, ink-red edges, the journal's checkbox), the title logo "IT WAS TALL" with running blood, the line
under it, plus the title-screen background (assets/itwastall/textures/gui/title_background.png).

Writes into the repo. Never touches textures/gui/journal.png (hand-edited). The Tall One silhouette for
the background is rendered from the real model (tools/build_icon.py) in a temporary folder.
Run: python3 tools/gen_ui.py
"""
import json, math, os, random, shutil, sys, tempfile
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "src/main/resources/resourcepacks/itwastall_ui")
OUT = PACK
JOURNAL = Image.open(ROOT + '/src/main/resources/assets/itwastall/textures/gui/journal.png').convert('RGBA')
r = random.Random(1311)

OUTLINE = (14, 8, 6, 255)
LEATHER = (58, 36, 26)
STITCH = (150, 124, 92, 255)
RED = (122, 20, 16, 255)

def save(img, rel):
    p = os.path.join(OUT, rel); os.makedirs(os.path.dirname(p), exist_ok=True); img.save(p)

def leather(w, h, base, var=10, seed=0):
    rr = random.Random(seed)
    img = Image.new('RGBA', (w, h))
    for x in range(w):
        for y in range(h):
            n = rr.randint(-var, var)
            # faint horizontal grain + cracks
            g = int(4 * math.sin((x * 0.31 + y * 1.7) + rr.random() * 0.5))
            c = tuple(max(0, min(255, base[i] + n + g)) for i in range(3))
            img.putpixel((x, y), c + (255,))
    for _ in range(w * h // 90):                      # tiny cracks
        x, y = rr.randrange(w), rr.randrange(h)
        img.putpixel((x, y), tuple(max(0, base[i] - 22) for i in range(3)) + (255,))
    return img

def button(state):
    w, h = 200, 20
    base = {'normal': LEATHER, 'hover': (74, 40, 28), 'disabled': (46, 42, 40)}[state]
    img = leather(w, h, base, var=8 if state != 'disabled' else 4, seed={'normal': 1, 'hover': 2, 'disabled': 3}[state])
    d = ImageDraw.Draw(img)
    edge = {'normal': (34, 20, 14, 255), 'hover': RED, 'disabled': (30, 28, 27, 255)}[state]
    d.rectangle([0, 0, w - 1, h - 1], outline=OUTLINE)
    d.rectangle([1, 1, w - 2, h - 2], outline=edge)
    # top light / bottom shade inside the border
    for x in range(2, w - 2):
        img.putpixel((x, 2), tuple(min(255, c + 18) for c in img.getpixel((x, 2))[:3]) + (255,))
        img.putpixel((x, h - 3), tuple(max(0, c - 18) for c in img.getpixel((x, h - 3))[:3]) + (255,))
    # the stitch, 2 px inside (dashed), like the journal's binding
    sc = {'normal': STITCH, 'hover': (196, 120, 96, 255), 'disabled': (92, 86, 80, 255)}[state]
    for x in range(4, w - 4):
        if (x // 2) % 2 == 0:
            img.putpixel((x, 3), sc)
            img.putpixel((x, h - 4), sc)
    for y in range(4, h - 4):
        if (y // 2) % 2 == 0:
            img.putpixel((3, y), sc)
            img.putpixel((w - 4, y), sc)
    if state != 'disabled':
        # a few dried blood specks (look fine when the centre tiles)
        for _ in range(9):
            x, y = r.randrange(8, w - 8), r.randrange(6, h - 6)
            img.putpixel((x, y), (96, 14, 12, 255))
    return img

def slider(state):
    w, h = 200, 20
    img = leather(w, h, (28, 18, 14), var=5, seed=7)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=RED if state == 'hover' else (10, 6, 5, 255))
    # a groove down the middle
    for x in range(3, w - 3):
        img.putpixel((x, 9), (12, 8, 6, 255)); img.putpixel((x, 10), (20, 13, 10, 255))
    return img

def handle(state):
    w, h = 8, 20
    img = Image.new('RGBA', (w, h))
    bone = (206, 194, 168) if state == 'normal' else (230, 176, 150)
    for x in range(w):
        for y in range(h):
            n = r.randint(-8, 8)
            img.putpixel((x, y), tuple(max(0, min(255, bone[i] + n - y)) for i in range(3)) + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=OUTLINE)
    d.line([1, h - 2, w - 2, h - 2], fill=(90, 78, 62, 255))
    if state == 'hover': d.rectangle([1, 1, w - 2, h - 3], outline=RED)
    for y in (6, 10, 14): img.putpixel((3, y), (120, 104, 84, 255)); img.putpixel((4, y), (120, 104, 84, 255))
    return img

def text_field(state):
    w, h = 200, 20
    img = Image.new('RGBA', (w, h), (12, 8, 7, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=(176, 60, 44, 255) if state == 'hover' else (110, 92, 73, 255))
    return img

def checkbox(selected, hover):
    # the journal's own 10x10 checkbox, doubled
    u = 428 if selected else 416
    cb = JOURNAL.crop((u, 88, u + 10, 98)).resize((20, 20), Image.NEAREST)
    img = Image.new('RGBA', (20, 20), (0, 0, 0, 0))
    img.alpha_composite(cb)
    if hover:
        d = ImageDraw.Draw(img); d.rectangle([0, 0, 19, 19], outline=(196, 40, 30, 255))
    return img

# 5x7 pixel font (uppercase) for the logo and the edition line
FONT = {
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"], "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"], "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"], "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"], "M": ["10001", "11011", "10101", "10101", "10001", "10001", "10001"],
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"], "H": ["10001", "10001", "10001", "11111", "10001", "10001", "10001"],
    "N": ["10001", "11001", "10101", "10011", "10001", "10001", "10001"], "G": ["01111", "10000", "10000", "10111", "10001", "10001", "01110"],
    "C": ["01111", "10000", "10000", "10000", "10000", "10000", "01111"], " ": ["000"] * 7, ".": ["0", "0", "0", "0", "0", "0", "1"],
}

def text_width(text, px):
    return sum((len(FONT[c][0]) + 1) * px for c in text) - px

def draw_text(img, text, x, y, px, fill, edge=None, glitch=None, drips=None, seed=5):
    rr = random.Random(seed)
    d = ImageDraw.Draw(img)
    cx = x
    cells = []
    for i, ch in enumerate(text):
        g = FONT[ch]
        ox, oy = cx, y
        if i == glitch: ox += px // 2; oy += px // 2
        for row, line in enumerate(g):
            for col, bit in enumerate(line):
                if bit == '1': cells.append((ox + col * px, oy + row * px, i))
        cx += (len(g[0]) + 1) * px
    if edge:   # dark shadow + red edge, then the stone face
        for (gx, gy, i) in cells: d.rectangle([gx + px // 3, gy + px // 3, gx + px + px // 3 - 1, gy + px + px // 3 - 1], fill=(8, 0, 0, 230))
        for (gx, gy, i) in cells: d.rectangle([gx - 2, gy - 2, gx + px + 1, gy + px + 1], fill=edge)
    for (gx, gy, i) in cells:
        for xx in range(gx, gx + px):
            for yy in range(gy, gy + px):
                n = rr.randint(-14, 14)
                c = fill if not callable(fill) else fill(i)
                img.putpixel((xx, yy), (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255))
    if drips:  # blood running down from a few letters
        for _ in range(drips):
            gx, gy, i = rr.choice([c for c in cells if c[1] == max(cc[1] for cc in cells if cc[2] == c[2])])
            length = rr.randint(px, px * 3)
            wdt = max(3, px // 3)
            sx = gx + rr.randint(0, px - wdt)
            d.rectangle([sx, gy + px, sx + wdt, gy + px + length], fill=(110, 12, 10, 255))
            d.ellipse([sx - 2, gy + px + length - 2, sx + wdt + 2, gy + px + length + wdt + 2], fill=(110, 12, 10, 255))

def letter_mask(text, px, size, y, glitch=None):
    m = Image.new('L', size, 0); d = ImageDraw.Draw(m)
    w = text_width(text, px); cx = (size[0] - w) // 2
    for i, ch in enumerate(text):
        g = FONT[ch]; ox, oy = cx, y
        if i == glitch: ox += px // 2; oy += px // 3
        for row, line in enumerate(g):
            for col, bit in enumerate(line):
                if bit == '1': d.rectangle([ox + col * px, oy + row * px, ox + (col + 1) * px - 1, oy + (row + 1) * px - 1], fill=255)
        cx += (len(g[0]) + 1) * px
    return m

def shift(m, dx, dy):
    out = Image.new('L', m.size, 0); out.paste(m, (dx, dy)); return out

def carve(size, mask, top, bottom, edge, outer, bevel, shadow_off, drips, seed, glitch_mask=None, glitch_col=None):
    rr = random.Random(seed)
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    black = Image.new('RGBA', size, (6, 0, 0, 235))
    img.paste(black, (0, 0), shift(mask, shadow_off, shadow_off).filter(ImageFilter.MaxFilter(3)))
    img.paste(Image.new('RGBA', size, outer), (0, 0), mask.filter(ImageFilter.MaxFilter(9)))
    img.paste(Image.new('RGBA', size, edge), (0, 0), mask.filter(ImageFilter.MaxFilter(5)))
    bbox = mask.getbbox()
    face = Image.new('RGBA', size)
    for y in range(bbox[1], bbox[3]):
        t = (y - bbox[1]) / max(1, bbox[3] - bbox[1])
        base = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(bbox[0], bbox[2]):
            n = rr.randint(-12, 12)
            face.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
    img.paste(face, (0, 0), mask)
    if glitch_mask is not None:
        gl = Image.new('RGBA', size, glitch_col + (255,))
        img.paste(gl, (0, 0), Image.composite(glitch_mask, Image.new('L', size, 0), mask))
    # bevel: the top-left inner rim catches the moonlight
    rim = Image.composite(mask, Image.new('L', size, 0), Image.eval(shift(mask, bevel, bevel), lambda v: 255 - v))
    img.paste(Image.new('RGBA', size, (200, 196, 204, 255)), (0, 0), rim)
    if drips:
        bleed(img, mask, bbox, drips, rr)
    return img

def blood_col(t, rr):
    """t 0 = fresh at the source (dark, almost black-red) .. 1 = the hanging drop (wet, brighter)."""
    base = (int(70 + 70 * t), int(4 + 8 * t), int(4 + 6 * t))
    n = rr.randint(-6, 6)
    return (max(0, base[0] + n), base[1], base[2], 255)

def bleed(img, mask, bbox, drips, rr):
    W, H = img.size
    px = img.load()
    edge_bottom = [(x, y) for x in range(bbox[0], bbox[2]) for y in range(bbox[1], bbox[3])
                   if mask.getpixel((x, y)) and (y + 1 >= H or not mask.getpixel((x, y + 1)))]
    edge_top = [(x, y) for x in range(bbox[0], bbox[2]) for y in range(bbox[1], bbox[3])
                if mask.getpixel((x, y)) and (y == 0 or not mask.getpixel((x, y - 1)))]
    # 1) blood poured over the tops of some letters, running down their faces in uneven tongues
    for _ in range(drips // 2 + 1):
        x0, y0 = rr.choice(edge_top)
        width = rr.randint(18, 46)
        for x in range(x0, min(W, x0 + width)):
            run = rr.randint(4, 12) + int(10 * abs(math.sin((x - x0) * 0.35 + rr.random())))
            for y in range(y0 - 2, min(H, y0 + run)):
                if mask.getpixel((x, min(H - 1, max(0, y)))) or y < y0:
                    px[x, y] = blood_col((y - y0) / max(1, run), rr)
    # 2) long drips from the bottom edges: they narrow as they fall and end in a fat drop
    for _ in range(drips):
        x, y = rr.choice(edge_bottom)
        length = rr.randint(14, 70)
        w0 = rr.randint(4, 8)
        for k in range(length):
            t = k / length
            w = max(2, int(w0 * (1 - 0.55 * t)))
            for dx in range(w):
                xx, yy = x + dx - w // 2, y + k
                if 0 <= xx < W and 0 <= yy < H: px[xx, yy] = blood_col(t * 0.8, rr)
        r0 = max(3, w0 // 2 + 2)
        cx, cy = x, y + length
        for dx in range(-r0, r0 + 1):
            for dy in range(-r0, r0 + 2):
                if dx * dx + (dy * 0.85) ** 2 <= r0 * r0 and 0 <= cx + dx < W and 0 <= cy + dy < H:
                    px[cx + dx, cy + dy] = blood_col(1.0, rr)
        if 0 <= cx - r0 // 2 < W and 0 <= cy - r0 // 2 < H:          # a wet highlight on the drop
            px[cx - r0 // 2, cy - r0 // 2] = (230, 120, 110, 255)
            if cy - r0 // 2 + 1 < H: px[cx - r0 // 2, cy - r0 // 2 + 1] = (190, 60, 52, 255)
    # 3) spatter around the letters
    for _ in range(drips * 9):
        x, y = rr.choice(edge_bottom + edge_top)
        x += rr.randint(-40, 40); y += rr.randint(-26, 30)
        rad = rr.choice([1, 1, 1, 2, 2, 3])
        for dx in range(-rad, rad + 1):
            for dy in range(-rad, rad + 1):
                if dx * dx + dy * dy <= rad * rad and 0 <= x + dx < W and 0 <= y + dy < H:
                    px[x + dx, y + dy] = blood_col(rr.random() * 0.6, rr)

def logo():
    # 1024x256 atlas; the game draws its top 1024x176 as the 256x44 logo
    size = (1024, 256)
    text = "IT WAS TALL"
    m = letter_mask(text, 15, size, 30, glitch=8)
    only_a = letter_mask("        A  ", 15, size, 30, glitch=8)    # the one wrong letter
    return carve(size, m, (128, 122, 128), (52, 48, 56), (96, 10, 8, 255), (10, 2, 2, 255), 3, 7, 12, 5, only_a, (92, 22, 18))

def edition():
    size = (512, 64)
    m = letter_mask("SOMETHING TALL IS WATCHING.", 3, size, 18)
    return carve(size, m, (214, 200, 182), (150, 138, 124), (80, 10, 8, 255), (8, 2, 2, 255), 1, 2, 0, 9)

def background(sil=None, meta=None):
    W, H = 1920, 1080
    img = Image.new('RGBA', (W, H))
    px = img.load()
    for y in range(H):
        t = min(1.0, y / 700)
        c = (int(8 + 18 * t), int(10 + 20 * t), int(20 + 22 * t))
        for x in range(W): px[x, y] = c + (255,)
    d = ImageDraw.Draw(img)
    for _ in range(320):
        x, y = r.randrange(W), r.randrange(560); b = r.randint(80, 210)
        px[x, y] = (b, b, b + 12, 255)
    mx, my = 430, 230
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0)); gd = ImageDraw.Draw(glow)
    for rad, a in ((260, 16), (170, 30), (110, 56)): gd.ellipse([mx - rad, my - rad, mx + rad, my + rad], fill=(190, 200, 220, a))
    img.alpha_composite(glow.filter(ImageFilter.GaussianBlur(45)))
    d.ellipse([mx - 66, my - 66, mx + 66, my + 66], fill=(216, 218, 206, 255))
    for (cx, cy, rr_) in ((mx - 22, my - 16, 13), (mx + 24, my + 20, 9), (mx + 8, my - 32, 6)): d.ellipse([cx - rr_, cy - rr_, cx + rr_, cy + rr_], fill=(192, 194, 184, 255))
    def spruce(x, base, height, col):
        for k in range(11):
            yy = base - height + k * height // 11
            half = int((k + 1) * height / 28)
            d.polygon([(x, yy - height // 9), (x - half, yy + height // 10), (x + half, yy + height // 10)], fill=col)
        d.rectangle([x - max(2, height // 70), base - height // 12, x + max(2, height // 70), base], fill=col)
    def fog(y0, spread, alpha, n=40):
        f = Image.new('RGBA', (W, H), (0, 0, 0, 0)); fd = ImageDraw.Draw(f)
        for _ in range(n):
            x = r.randrange(-300, W); y = y0 + r.randint(-spread, spread); w2 = r.randint(300, 800)
            fd.ellipse([x, y - 26, x + w2, y + 34], fill=(150, 162, 180, alpha))
        img.alpha_composite(f.filter(ImageFilter.GaussianBlur(28)))
    # the far tree line on the horizon
    for i in range(140): spruce(r.randrange(-40, W + 40), 650, r.randint(70, 150), (22, 28, 40, 255))
    # the clearing in front of it
    for y in range(650, H):
        t = (y - 650) / (H - 650)
        c = (int(18 - 8 * t), int(22 - 10 * t), int(30 - 14 * t))
        d.line([0, y, W, y], fill=c + (255,))
    fog(660, 25, 34, 50)
    # the silhouette, standing in the clearing, off to the right (the buttons sit in the middle)
    if sil is not None:
        bbox = sil.getbbox(); s = sil.crop(bbox)
        th = 440; sw = int(s.width * th / s.height); s = s.resize((sw, th), Image.LANCZOS)
        sx, sy = 1470 - sw // 2, 905 - th
        img.alpha_composite(s, (sx, sy))
        sc = th / (bbox[3] - bbox[1])
        eyes = [(sx + (ex - bbox[0]) * sc, sy + (ey - bbox[1]) * sc) for (ex, ey) in meta['eyes']]
    else:
        eyes = []
    fog(905, 20, 40, 30)
    # big dark spruces only at the edges, framing the picture
    for x in (40, 150, 260, 1690, 1800, 1900):
        spruce(x + r.randint(-30, 30), 1140, r.randint(760, 980), (4, 5, 8, 255))
    # everything soft and out of focus, like a dream you cannot wake from ...
    img = img.filter(ImageFilter.GaussianBlur(6))
    # ... except the eyes: they stay sharp, with a glow
    d = ImageDraw.Draw(img)
    for (ex2, ey2) in eyes:
        g2 = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ImageDraw.Draw(g2).ellipse([ex2 - 12, ey2 - 12, ex2 + 12, ey2 + 12], fill=(255, 230, 220, 120))
        img.alpha_composite(g2.filter(ImageFilter.GaussianBlur(6)))
        d.rectangle([ex2 - 2, ey2 - 1, ex2 + 1, ey2 + 1], fill=(255, 250, 246, 255))
    # film grain
    gpx = img.load()
    for _ in range(W * H // 14):
        x, y = r.randrange(W), r.randrange(H); c = gpx[x, y]; n = r.randint(-9, 9)
        gpx[x, y] = (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), 255)
    vig = Image.new('L', (W, H), 0); vd = ImageDraw.Draw(vig)
    for k in range(60):
        a = int(255 * (k / 60) ** 2.0)
        vd.ellipse([-W * 0.25 + k * 12, -H * 0.3 + k * 7, W * 1.25 - k * 12, H * 1.3 - k * 7], fill=a)
    return Image.composite(img, Image.new('RGBA', (W, H), (0, 0, 0, 255)), vig.filter(ImageFilter.GaussianBlur(60)))

NINE_SLICE = {
    "button": (200, 20, 3), "button_highlighted": (200, 20, 3), "button_disabled": (200, 20, 3),
    "slider": (200, 20, 1), "slider_highlighted": (200, 20, 1),
    "text_field": (200, 20, 1), "text_field_highlighted": (200, 20, 1),
    "slider_handle": (8, 20, {"left": 2, "top": 2, "right": 2, "bottom": 3}),
    "slider_handle_highlighted": (8, 20, {"left": 2, "top": 2, "right": 2, "bottom": 3}),
}


def render_silhouette():
    sys.path.insert(0, os.path.join(ROOT, "tools"))
    import build_icon
    tmp = tempfile.mkdtemp(prefix="itwastall_ui_")
    try:
        shutil.copy(os.path.join(ROOT, "tools/preview/three.min.js"), tmp)
        build_icon.PREVIEW = tmp
        return build_icon.render_silhouette()
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def main():
    W = 'assets/minecraft/textures/gui/sprites/widget/'
    for st, name in (('normal', 'button'), ('hover', 'button_highlighted'), ('disabled', 'button_disabled')): save(button(st), W + name + '.png')
    save(slider('normal'), W + 'slider.png'); save(slider('hover'), W + 'slider_highlighted.png')
    save(handle('normal'), W + 'slider_handle.png'); save(handle('hover'), W + 'slider_handle_highlighted.png')
    save(text_field('normal'), W + 'text_field.png'); save(text_field('hover'), W + 'text_field_highlighted.png')
    for sel in (False, True):
        for hov in (False, True):
            save(checkbox(sel, hov), W + 'checkbox' + ('_selected' if sel else '') + ('_highlighted' if hov else '') + '.png')
    for name, (w, h, border) in NINE_SLICE.items():
        with open(os.path.join(OUT, W + name + '.png.mcmeta'), 'w') as fh:
            json.dump({"gui": {"scaling": {"type": "nine_slice", "width": w, "height": h, "border": border}}}, fh, indent=2)
    save(logo(), 'assets/minecraft/textures/gui/title/minecraft.png')
    save(edition(), 'assets/minecraft/textures/gui/title/edition.png')
    with open(os.path.join(OUT, 'pack.mcmeta'), 'w') as fh:
        json.dump({"pack": {"pack_format": 34, "description": "It Was Tall UI: buttons, title logo and checkboxes in the mod's style"}}, fh, indent=2)
    sil, meta = render_silhouette()
    bg = background(sil, meta).convert('RGB')
    bg.save(os.path.join(ROOT, 'src/main/resources/assets/itwastall/textures/gui/title_background.png'), optimize=True)
    print('ui written')


if __name__ == '__main__':
    main()
