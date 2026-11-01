#!/usr/bin/env python3
"""Generates the It Was Tall entity model (GeckoLib / Bedrock geo 1.12.0) and its textures.

Outputs (relative to repo root):
  src/main/resources/assets/itwastall/geo/entity/tall_one.geo.json
  src/main/resources/assets/itwastall/textures/entity/tall_one.png
  src/main/resources/assets/itwastall/textures/entity/tall_one_glowmask.png
  src/main/resources/assets/itwastall/textures/gui/jumpscare.png
"""
import json
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
TEX = 256          # texture size in texels
DENSITY = 2        # texels per model pixel
rng = random.Random(1337)
FACE_PHOTO = os.path.join(ROOT, "tools/source/face_photo.png")
FACE_CROP = (210, 40, 370, 360)   # face region in the photo (1:2, like the head front)
FACE_W, FACE_H = 48, 96           # texels used for the head front (higher density than the body)

# ---------------------------------------------------------------- model ----
# Coordinates in model pixels (16 px = 1 block), y up, front of the entity faces north (-Z).
# Cube "origin" = min corner. Bones listed parent-first.
# Each cube: (name, origin, size, material)
BONES = [
    dict(name="root", pivot=[0, 0, 0]),
    dict(name="body", parent="root", pivot=[0, 27, 0], cubes=[
        ("pelvis", [-3.5, 26, -1.5], [7, 3, 3], "skin"),
    ]),
    dict(name="torso", parent="body", pivot=[0, 29, 0], rotation=[12, 0, 0], cubes=[
        ("ribs", [-2.5, 29, -1.25], [5, 8, 2.5], "ribs"),
        ("chest", [-4, 37, -2], [8, 6, 4], "skin"),
        ("shoulders", [-6, 42, -1.5], [12, 2, 3], "skin"),
    ]),
    dict(name="neck", parent="torso", pivot=[0, 44, 0], rotation=[18, 0, 0], cubes=[
        ("neck", [-1, 44, -1], [2, 4, 2], "skin"),
    ]),
    dict(name="head", parent="neck", pivot=[0, 47.5, 0], rotation=[-12, 0, 10], cubes=[
        ("skull", [-2.5, 47, -2.5], [5, 10, 5], "head"),
        ("jaw", [-1.5, 45.5, -2.3], [3, 2, 3.5], "jaw"),
    ]),
    dict(name="right_arm", parent="torso", pivot=[-6, 43, 0], rotation=[-16, 0, 4], cubes=[
        ("right_upper_arm", [-7.5, 29, -1], [2, 14, 2], "skin"),
    ]),
    dict(name="right_forearm", parent="right_arm", pivot=[-6.5, 29, 0], rotation=[-6, 0, 0], cubes=[
        ("right_lower_arm", [-7.25, 16, -0.75], [1.5, 13, 1.5], "skin"),
    ]),
    dict(name="right_hand", parent="right_forearm", pivot=[-6.5, 16, 0], cubes=[
        ("right_palm", [-7.5, 13, -1], [2, 3, 2], "skin"),
        ("right_finger_1", [-7.5, 6, -1], [0.5, 7, 0.5], "finger"),
        ("right_finger_2", [-6.75, 5, -1], [0.5, 8, 0.5], "finger"),
        ("right_finger_3", [-6, 6.5, -1], [0.5, 6.5, 0.5], "finger"),
        ("right_thumb", [-7.5, 9, 0.5], [0.5, 4, 0.5], "finger"),
    ]),
    dict(name="right_leg", parent="root", pivot=[-2, 27, 0], rotation=[-6, 0, 2], cubes=[
        ("right_thigh", [-3, 13, -1], [2, 14, 2], "skin"),
    ]),
    dict(name="right_shin", parent="right_leg", pivot=[-2, 13, 0], rotation=[10, 0, 0], cubes=[
        ("right_lower_leg", [-2.75, 1, -0.75], [1.5, 12, 1.5], "skin"),
        ("right_foot", [-3, 0, -3], [2, 1, 4], "foot"),
    ]),
]


def mirror_bone(b):
    """Right -> left mirror across X."""
    m = dict(name=b["name"].replace("right", "left"), pivot=[-b["pivot"][0], b["pivot"][1], b["pivot"][2]])
    if "parent" in b:
        m["parent"] = b["parent"].replace("right", "left")
    if "rotation" in b:
        r = b["rotation"]
        m["rotation"] = [r[0], -r[1], -r[2]]
    m["cubes"] = [(n.replace("right", "left"), [-(o[0] + s[0]), o[1], o[2]], s, mat)
                  for (n, o, s, mat) in b.get("cubes", [])]
    return m


BONES += [mirror_bone(b) for b in list(BONES) if b["name"].startswith("right")]

# --------------------------------------------------------- UV packing ------
FACES = ["north", "south", "east", "west", "up", "down"]


def face_dims(size, face):
    x, y, z = size
    if face in ("north", "south"):
        return x, y
    if face in ("east", "west"):
        return z, y
    return x, z


class Packer:
    def __init__(self, w):
        self.w, self.x, self.y, self.row_h = w, 0, 0, 0

    def place(self, w, h):
        if self.x + w > self.w:
            self.x, self.y, self.row_h = 0, self.y + self.row_h + 1, 0
        pos = (self.x, self.y)
        self.x += w + 1
        self.row_h = max(self.row_h, h)
        return pos


packer = Packer(TEX)
face_regions = []   # (cube_name, material, face, x, y, w, h)
geo_bones = []
for b in BONES:
    gb = dict(name=b["name"], pivot=b["pivot"])
    if "parent" in b:
        gb["parent"] = b["parent"]
    if "rotation" in b:
        gb["rotation"] = b["rotation"]
    cubes = []
    for (name, origin, size, mat) in b.get("cubes", []):
        uv = {}
        for f in FACES:
            fw, fh = face_dims(size, f)
            tw, th = max(1, math.ceil(fw * DENSITY)), max(1, math.ceil(fh * DENSITY))
            if name == "skull" and f == "north":
                tw, th = FACE_W, FACE_H   # high-res photo face
            px, py = packer.place(tw, th)
            uv[f] = {"uv": [px, py], "uv_size": [tw, th]}
            face_regions.append((name, mat, f, px, py, tw, th))
        cubes.append({"origin": origin, "size": size, "uv": uv})
    if cubes:
        gb["cubes"] = cubes
    geo_bones.append(gb)

assert packer.y + packer.row_h <= TEX, "texture atlas overflow"

geo = {
    "format_version": "1.12.0",
    "minecraft:geometry": [{
        "description": {
            "identifier": "geometry.itwastall.tall_one",
            "texture_width": TEX,
            "texture_height": TEX,
            "visible_bounds_width": 3,
            "visible_bounds_height": 4.5,
            "visible_bounds_offset": [0, 1.75, 0],
        },
        "bones": geo_bones,
    }],
}

# ----------------------------------------------------------- painting ------
SKIN = (34, 31, 31)


def jitter(c, amt):
    d = rng.randint(-amt, amt)
    return tuple(max(0, min(255, v + d)) for v in c)


def paint_face(img, glow, mat, face, x, y, w, h):
    px = img.load()
    gx = glow.load()
    base = {"skin": SKIN, "ribs": SKIN, "head": (40, 37, 36), "jaw": (36, 33, 32),
            "finger": (30, 28, 28), "foot": (26, 24, 24)}[mat]
    for j in range(h):
        for i in range(w):
            c = jitter(base, 6)
            # faint vertical veins/streaks on long limbs
            if mat == "skin" and h > 10 and (i * 7 + x) % 5 == 0 and rng.random() < 0.35:
                c = tuple(v - 7 for v in c)
            # darker edge to define the silhouette
            if w > 2 and h > 2 and (i == 0 or j == 0 or i == w - 1 or j == h - 1):
                c = tuple(int(v * 0.75) for v in c)
            px[x + i, y + j] = c + (255,)
    if mat == "ribs" and face in ("north", "east", "west"):
        for j in range(2, h - 1, 3):
            for i in range(1, w - 1):
                px[x + i, y + j] = jitter((62, 57, 54), 4) + (255,)
            if face == "north":
                px[x + w // 2, y + j] = (30, 27, 27, 255)  # sternum gap
    if mat == "finger" and face not in ("up",):
        for j in range(max(0, h - 3), h):   # pale claw tips
            for i in range(w):
                px[x + i, y + j] = jitter((132, 124, 112), 6) + (255,)
    if mat == "head" and face == "north":
        paste_face(img, glow, x, y, w, h, base)
    if mat == "head" and face == "up":
        for j in range(h):
            for i in range(w):
                if rng.random() < 0.15:
                    px[x + i, y + j] = jitter((52, 48, 46), 4) + (255,)


def paste_face(img, glow, x, y, w, h, skin):
    """Photo face, darkened and faded into the head colour at the edges; bright parts glow faintly."""
    photo = Image.open(FACE_PHOTO).convert("L").crop(FACE_CROP).resize((w, h), Image.LANCZOS)
    pp = photo.load()
    px, gx = img.load(), glow.load()
    for j in range(h):
        for i in range(w):
            lum = pp[i, j] / 255.0
            lum = min(1.0, max(0.0, (lum - 0.12) * 1.35))       # contrast
            fx = min(i + 0.5, w - i - 0.5) / (w * 0.18)
            fy = min(j + 0.5, h - j - 0.5) / (h * 0.10)
            a = max(0.0, min(1.0, min(fx, fy)))                  # 0 at edge -> 1 inside
            face = (int(185 * lum), int(182 * lum), int(178 * lum))
            c = tuple(int(sk * (1 - a) + fc * a) for sk, fc in zip(skin, face))
            px[x + i, y + j] = c + (255,)
            gl = int(255 * max(0.0, lum - 0.35) * 0.55 * a)
            if gl > 0:
                gx[x + i, y + j] = (gl, gl, gl, 255)


img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
for (name, mat, f, x, y, w, h) in face_regions:
    paint_face(img, glow, mat, f, x, y, w, h)


# --------------------------------------------------------- jumpscare -------
def make_jumpscare():
    """Full-screen jumpscare: the photo, contrast boosted, with a dark vignette."""
    im = Image.open(FACE_PHOTO).convert("RGB")
    im = im.resize((im.width * 2, im.height * 2), Image.LANCZOS)
    W, H = im.size
    px = im.load()
    for yy in range(H):
        for xx in range(W):
            d = math.hypot((xx - W * 0.52) / W, (yy - H * 0.5) / H) / 0.62
            k = max(0.0, 1 - d ** 2.2)
            px[xx, yy] = tuple(max(0, min(255, int(((v / 255 - 0.08) * 1.3) * 255 * k))) for v in px[xx, yy])
    return im


os.makedirs(os.path.join(ASSETS, "geo/entity"), exist_ok=True)
os.makedirs(os.path.join(ASSETS, "textures/entity"), exist_ok=True)
os.makedirs(os.path.join(ASSETS, "textures/gui"), exist_ok=True)
with open(os.path.join(ASSETS, "geo/entity/tall_one.geo.json"), "w") as fh:
    json.dump(geo, fh, indent=2)
img.save(os.path.join(ASSETS, "textures/entity/tall_one.png"))
glow.save(os.path.join(ASSETS, "textures/entity/tall_one_glowmask.png"))
make_jumpscare().save(os.path.join(ASSETS, "textures/gui/jumpscare.png"))
print("bones:", len(geo_bones), "faces:", len(face_regions), "atlas rows used:", packer.y + packer.row_h)
