#!/usr/bin/env python3
"""The HUD icons of It Was Tall (16x16 pixel art, in the journal's colours: parchment, ink, dried blood) and the
shared HUD frame (nine-slice). They live in the GUI sprite atlas: textures/gui/sprites/hud/*.png.
Run: python3 tools/gen_hud_icons.py"""
import json, math, os, random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/textures/gui/sprites/hud")
INK = (24, 14, 10, 255)
BLOOD, BLOOD_D, BLOOD_L = (128, 18, 14, 255), (78, 8, 6, 255), (186, 44, 32, 255)
PARCH, PARCH_D = (222, 204, 166, 255), (170, 150, 112, 255)


def new():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def shadow(img):
    """A 1 px dark drop shadow under every opaque pixel (down-right), so icons read on any background."""
    out = new()
    px = img.load()
    for x in range(16):
        for y in range(16):
            if px[x, y][3] > 0 and x + 1 < 16 and y + 1 < 16:
                out.putpixel((x + 1, y + 1), (0, 0, 0, 150))
    out.alpha_composite(img)
    return out


def save(img, name, use_shadow=True):
    os.makedirs(OUT, exist_ok=True)
    (shadow(img) if use_shadow else img).save(os.path.join(OUT, name + ".png"))


def sun():
    img = new(); d = ImageDraw.Draw(img)
    for a in range(8):
        ang = a * math.pi / 4
        x0, y0 = 7.5 + math.cos(ang) * 5, 7.5 + math.sin(ang) * 5
        x1, y1 = 7.5 + math.cos(ang) * 7, 7.5 + math.sin(ang) * 7
        d.line([x0, y0, x1, y1], fill=(214, 150, 54, 255))
    d.ellipse([3, 3, 12, 12], fill=(236, 196, 92, 255), outline=(150, 92, 30, 255))
    d.ellipse([5, 5, 8, 8], fill=(252, 230, 150, 255))
    return img


def moon():
    img = new(); d = ImageDraw.Draw(img)
    d.ellipse([2, 2, 13, 13], fill=(214, 214, 200, 255), outline=(110, 110, 104, 255))
    d.ellipse([6, 0, 17, 11], fill=(0, 0, 0, 0))            # carve the crescent
    for (x, y) in ((4, 8), (5, 11), (3, 6)):
        img.putpixel((x, y), (160, 160, 150, 255))
    img.putpixel((9, 12), BLOOD)                              # a drop on the tip
    return img


def shelter(lit):
    img = new(); d = ImageDraw.Draw(img)
    d.polygon([(8, 1), (15, 7), (1, 7)], fill=(96, 40, 28, 255), outline=INK)       # roof
    d.rectangle([3, 7, 12, 14], fill=(120, 94, 64, 255), outline=INK)               # walls
    d.rectangle([5, 9, 7, 11], fill=(250, 210, 110, 255) if lit else (30, 24, 20, 255))   # window
    d.rectangle([9, 10, 11, 14], fill=(60, 38, 22, 255))                              # door
    if lit:
        img.putpixel((6, 10), (255, 240, 180, 255))
    return img


def heart(frame):
    """An anatomical heart: 0 relaxed, 1 contracted (the beat), 2 dark (racing)."""
    img = new(); d = ImageDraw.Draw(img)
    body = [BLOOD, BLOOD_L, BLOOD_D][frame]
    shrink = 1 if frame == 1 else 0
    d.ellipse([2 + shrink, 4 + shrink, 10 - shrink, 14 - shrink], fill=body, outline=INK)
    d.ellipse([6 + shrink, 3 + shrink, 13 - shrink, 12 - shrink], fill=body, outline=INK)
    d.rectangle([6, 0, 8, 4], fill=(150, 60, 70, 255), outline=INK)                 # aorta
    d.line([9, 2, 12, 0], fill=(80, 70, 140, 255), width=2)                         # vein
    d.line([4, 7, 6, 11], fill=BLOOD_D)                                             # a vessel on it
    d.point((5, 6), fill=(230, 120, 110, 255))                                      # highlight
    return img


def needle():
    """The compass needle pointing up (rotated by the code around the sprite's centre, so it is drawn symmetric
    around that centre: between pixel columns 7|8 and rows 7|8): blood-red tip, bone tail, iron pivot."""
    img = new()
    half = {1: 1, 2: 1, 3: 2, 4: 2, 5: 3, 6: 3, 7: 3, 8: 3, 9: 3, 10: 3, 11: 2, 12: 2, 13: 1, 14: 1}
    for y, hw in half.items():
        x0, x1 = 8 - hw, 7 + hw
        for x in range(x0, x1 + 1):
            edge = x in (x0, x1) or y in (1, 14) or half.get(y - 1, 0) < hw and abs(x - 7.5) > half.get(y - 1, 0) - 0.5 \
                or half.get(y + 1, 0) < hw and abs(x - 7.5) > half.get(y + 1, 0) - 0.5
            img.putpixel((x, y), INK if edge else (BLOOD_L if y <= 7 else PARCH))
    for x in (7, 8):
        for y in (7, 8):
            img.putpixel((x, y), (120, 120, 126, 255))
    return img


def stage(n):
    """Stages 0-5: It comes closer. 0 an empty horizon ... 5 its face right in front of you."""
    img = new(); d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], fill=(18, 20, 30, 255))
    if n <= 2:
        d.rectangle([0, 11, 15, 15], fill=(16, 26, 14, 255))                        # ground
        for x in (1, 4, 12, 14):                                                    # trees on the horizon
            d.polygon([(x, 6), (x - 2, 11), (x + 2, 11)], fill=(8, 12, 8, 255))
    if n == 1:
        d.line([8, 8, 8, 11], fill=(4, 4, 6, 255))                                  # a speck far away
        d.point((8, 8), fill=(200, 200, 200, 255))
    elif n == 2:
        d.rectangle([7, 5, 8, 11], fill=(6, 6, 8, 255))
        d.point((7, 6), fill=(230, 230, 230, 255)); d.point((8, 6), fill=(230, 230, 230, 255))
    elif n == 3:
        d.rectangle([0, 13, 15, 15], fill=(16, 26, 14, 255))
        d.rectangle([6, 2, 9, 13], fill=(6, 6, 8, 255))                             # a tall figure, closer
        d.line([5, 5, 4, 12], fill=(6, 6, 8, 255)); d.line([10, 5, 11, 12], fill=(6, 6, 8, 255))
        d.point((7, 4), fill=(245, 245, 245, 255)); d.point((8, 4), fill=(245, 245, 245, 255))
    elif n == 4:
        d.rectangle([4, 3, 11, 15], fill=(6, 6, 8, 255))                            # head and shoulders
        d.rectangle([2, 9, 13, 15], fill=(6, 6, 8, 255))
        d.point((6, 6), fill=(255, 240, 230, 255)); d.point((9, 6), fill=(255, 240, 230, 255))
    elif n == 5:
        d.rectangle([1, 0, 14, 15], fill=(10, 9, 10, 255))                          # the face
        d.rectangle([3, 4, 5, 6], fill=(255, 245, 240, 255)); d.rectangle([10, 4, 12, 6], fill=(255, 245, 240, 255))
        d.point((4, 5), fill=BLOOD); d.point((11, 5), fill=BLOOD)
        d.line([4, 11, 11, 11], fill=(200, 190, 180, 255))                          # the grin
        for x in range(4, 12, 2): d.point((x, 12), fill=(200, 190, 180, 255))
    d.rectangle([0, 0, 15, 15], outline=INK)
    return img


def frame():
    """16x16 nine-slice HUD frame (border 4): dark leather, a thin blood-red edge, iron rivets in the corners."""
    r = random.Random(7)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            n = r.randint(-5, 5)
            img.putpixel((x, y), (30 + n, 18 + n, 13 + n, 214))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(8, 4, 3, 240))
    d.rectangle([1, 1, 14, 14], outline=(104, 18, 14, 230))
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        img.putpixel((x, y), (150, 140, 128, 255))
    return img


def main():
    save(sun(), "sun"); save(moon(), "moon")
    save(shelter(True), "shelter_on"); save(shelter(False), "shelter_off")
    for i in range(3): save(heart(i), f"heart_{i}")
    save(needle(), "needle", use_shadow=False)
    for n in range(6): save(stage(n), f"stage_{n}", use_shadow=False)
    frame().save(os.path.join(OUT, "frame.png"))
    with open(os.path.join(OUT, "frame.png.mcmeta"), "w") as fh:
        json.dump({"gui": {"scaling": {"type": "nine_slice", "width": 16, "height": 16, "border": 4}}}, fh)
    print("hud icons written")


if __name__ == "__main__":
    main()
