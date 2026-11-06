#!/usr/bin/env python3
"""THE DEEP ONE — a long, thin thing that crawls upside down on cave ceilings, on all fours.

Writes (GeckoLib, Bedrock box-UV geometry, model faces north / -Z):
  geo/entity/deep_one.geo.json
  textures/entity/deep_one.png            64 x 64, pale dead skin
  textures/entity/deep_one_glowmask.png   emissive: two pin-prick eyes
  animations/entity/deep_one.animation.json   idle, crawl
The hitbox is 1.0 x 0.9 blocks; the limbs reach up to y = 14 px, the top of the box, where the
ceiling is — the body hangs under it and the head dangles towards you.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")

# (name, parent, pivot, cube origin, size, uv)
LIMBS = [("fl", 1, -8), ("fr", -1, -8), ("bl", 1, 8), ("br", -1, 8)]
UPPER_UV = [(40, 24), (40, 28), (40, 32), (40, 36)]
LOWER_UV = [(0, 40), (8, 40), (16, 40), (24, 40)]


def bones():
    b = [
        {"name": "root", "pivot": [0, 0, 0]},
        {"name": "torso", "parent": "root", "pivot": [0, 7, 0], "cubes": [{"origin": [-3, 5, -10], "size": [6, 3, 20], "uv": [0, 0]}]},
        {"name": "neck", "parent": "torso", "pivot": [0, 6, -10], "cubes": [{"origin": [-1, 5, -15], "size": [2, 2, 5], "uv": [24, 24]}]},
        {"name": "head", "parent": "neck", "pivot": [0, 6, -15], "cubes": [{"origin": [-3, 0, -19], "size": [6, 7, 5], "uv": [0, 24]}]},
    ]
    for i, (n, side, z) in enumerate(LIMBS):
        sx = 3 * side
        upper_origin = [3, 6, z - 1] if side > 0 else [-10, 6, z - 1]
        lower_x = 9 if side > 0 else -11
        b.append({"name": f"{n}_upper", "parent": "torso", "pivot": [sx, 7, z],
                  "cubes": [{"origin": upper_origin, "size": [7, 2, 2], "uv": list(UPPER_UV[i])}]})
        b.append({"name": f"{n}_lower", "parent": f"{n}_upper", "pivot": [lower_x + 1, 7, z],
                  "cubes": [{"origin": [lower_x, 7, z - 1], "size": [2, 7, 2], "uv": list(LOWER_UV[i])}]})
    return b


def write_geo():
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.itwastall.deep_one", "texture_width": 64, "texture_height": 64,
                        "visible_bounds_width": 3, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones()}]}
    with open(os.path.join(ASSETS, "geo/entity/deep_one.geo.json"), "w") as fh:
        json.dump(geo, fh, indent=2)


def box_regions(u, v, w, h, d):
    """Bedrock box-UV faces of a cube: name -> (x, y, w, h)."""
    return {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d), "east": (u, v + d, d, h),
            "north": (u + d, v + d, w, h), "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}


def write_textures():
    r = random.Random(42)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def skin(x, y):
        base = r.randint(150, 178)
        tint = r.choice([(0, 0, 0), (-12, -8, -4), (-28, -24, -20), (6, 4, 2)])
        return (base + tint[0] + 8, base + tint[1] + 4, base + tint[2], 255)

    cubes = [(0, 0, 6, 3, 20), (0, 24, 6, 7, 5), (24, 24, 2, 2, 5)] + [(u, v, 7, 2, 2) for u, v in UPPER_UV] + [(u, v, 2, 7, 2) for u, v in LOWER_UV]
    for (u, v, w, h, d) in cubes:
        for name, (x0, y0, fw, fh) in box_regions(u, v, w, h, d).items():
            for x in range(x0, x0 + fw):
                for y in range(y0, y0 + fh):
                    img.putpixel((x, y), skin(x, y))
    # spine ridge along the torso top, darker bruises
    up = box_regions(0, 0, 6, 3, 20)["up"]
    for y in range(up[1], up[1] + up[3]):
        img.putpixel((up[0] + 2, y), (96, 90, 84, 255))
        img.putpixel((up[0] + 3, y), (104, 98, 90, 255))
    for _ in range(40):
        x, y = r.randrange(0, 52), r.randrange(0, 23)
        if img.getpixel((x, y))[3]:
            img.putpixel((x, y), (118, 112, 108, 255))
    # the face (north face of the head): sunken black sockets, a long thin mouth
    fx, fy, fw, fh = box_regions(0, 24, 6, 7, 5)["north"]
    for (x, y) in [(fx + 1, fy + 2), (fx + 1, fy + 3), (fx + 4, fy + 2), (fx + 4, fy + 3)]:
        img.putpixel((x, y), (8, 6, 6, 255))
    for x in range(fx + 1, fx + 5):
        img.putpixel((x, fy + 5), (40, 8, 8, 255))
    img.putpixel((fx, fy + 5), (60, 14, 14, 255))
    img.putpixel((fx + 5, fy + 5), (60, 14, 14, 255))
    # long dark fingers at the tips of the lower limbs (the top, touching the ceiling)
    for (u, v) in LOWER_UV:
        for name in ("north", "south", "east", "west"):
            x0, y0, fw, fh = box_regions(u, v, 2, 7, 2)[name]
            for x in range(x0, x0 + fw):
                img.putpixel((x, y0), (60, 56, 52, 255))
                img.putpixel((x, y0 + 1), (90, 84, 78, 255))
    # glow: one bright pin-prick in each socket
    for (x, y) in [(fx + 1, fy + 3), (fx + 4, fy + 3)]:
        glow.putpixel((x, y), (235, 240, 255, 255))
    img.save(os.path.join(ASSETS, "textures/entity/deep_one.png"))
    glow.save(os.path.join(ASSETS, "textures/entity/deep_one_glowmask.png"))


def kf(*pairs):
    return {f"{t:.4g}" if t % 1 else f"{t:.1f}": v for t, v in pairs}


def cyc(length, a, b, phase, axis, steps=8):
    out = []
    for i in range(steps + 1):
        s = (math.sin(2 * math.pi * (i / steps + phase)) + 1) / 2
        v = [0, 0, 0]
        v[axis] = round(a + (b - a) * s, 2)
        out.append((round(length * i / steps, 4), v))
    return kf(*out)


def write_animations():
    A = {}
    A["idle"] = dict(loop=True, animation_length=4.0, bones={
        "head": {"rotation": kf((0.0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.3, [0, 0, 28]), (2.6, [0, 0, 28]), (2.65, [0, 0, -6]), (4.0, [0, 0, 0]))},
        "torso": {"position": cyc(4.0, -0.3, 0.3, 0, 1)},
        "fl_lower": {"rotation": kf((0.0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.05, [0, 0, 6]), (3.15, [0, 0, 0]), (4.0, [0, 0, 0]))},
    })
    L = 0.8
    A["crawl"] = dict(loop=True, animation_length=L, bones={
        # diagonal pairs move together, like something that learned to walk from watching spiders
        "fl_upper": {"rotation": cyc(L, -22, 22, 0.0, 1)}, "br_upper": {"rotation": cyc(L, -22, 22, 0.0, 1)},
        "fr_upper": {"rotation": cyc(L, -22, 22, 0.5, 1)}, "bl_upper": {"rotation": cyc(L, -22, 22, 0.5, 1)},
        "torso": {"position": cyc(L, -0.4, 0.4, 0.25, 1), "rotation": cyc(L, -3, 3, 0.0, 2)},
        "head": {"rotation": cyc(L, -8, 8, 0.1, 1)},
    })
    doc = {"format_version": "1.8.0", "animations": {f"animation.deep_one.{k}": v for k, v in A.items()}}
    with open(os.path.join(ASSETS, "animations/entity/deep_one.animation.json"), "w") as fh:
        json.dump(doc, fh, indent=1)


if __name__ == "__main__":
    write_geo()
    write_textures()
    write_animations()
    print("deep one: geo, textures, animations written")
