#!/usr/bin/env python3
"""Generates the OLD ONE (Skinwalker Steve) assets:

  geo/entity/old_one.geo.json          player-proportioned GeckoLib model whose head and torso are split
                                        into halves (so the body can tear open), UV-mapped onto the
                                        vanilla 64x64 skin layout
  textures/entity/old_one_overlay.png  OUR layer drawn over the vanilla Steve skin: hollow black eyes,
                                        a stretched mouth, blood, and the raw flesh inside the halves
  textures/entity/old_one_eyes.png     emissive pinpoints inside the eye sockets
  animations/entity/old_one.animation.json  idle, walk, split (the body tears open and collapses)

The base texture is the game's own minecraft:textures/entity/player/wide/steve.png, referenced at
runtime and never copied into the mod.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
FLESH = (56, 16)  # transparent region of the vanilla skin; our overlay paints flesh there


def uv(u, v, w, h):
    return {"uv": [u, v], "uv_size": [w, h]}


def faces(north, south, east, west, up, down):
    return {"north": uv(*north), "south": uv(*south), "east": uv(*east), "west": uv(*west), "up": uv(*up), "down": uv(*down)}


FL = lambda w, h: (FLESH[0], FLESH[1], w, h)

BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "right_leg", "parent": "root", "pivot": [-2, 12, 0], "cubes": [
        {"origin": [-4, 0, -2], "size": [4, 12, 4], "uv": faces((4, 20, 4, 12), (12, 20, 4, 12), (8, 20, 4, 12), (0, 20, 4, 12), (4, 16, 4, 4), (8, 16, 4, 4))}]},
    {"name": "left_leg", "parent": "root", "pivot": [2, 12, 0], "cubes": [
        {"origin": [0, 0, -2], "size": [4, 12, 4], "uv": faces((20, 52, 4, 12), (28, 52, 4, 12), (24, 52, 4, 12), (16, 52, 4, 12), (20, 48, 4, 4), (24, 48, 4, 4))}]},
    {"name": "body", "parent": "root", "pivot": [0, 12, 0]},
    # torso halves hinge on their OUTER edges (like two doors).
    # NB: GeckoLib mirrors Bedrock X, so a RIGHT half's outer side is its "east" face, a LEFT half's is "west".
    {"name": "torso_right", "parent": "body", "pivot": [-4, 12, 0], "cubes": [
        {"origin": [-4, 12, -2], "size": [4, 12, 4], "uv": faces((20, 20, 4, 12), (36, 20, 4, 12), (16, 20, 4, 12), FL(4, 12), (20, 16, 4, 4), (28, 16, 4, 4))}]},
    {"name": "torso_left", "parent": "body", "pivot": [4, 12, 0], "cubes": [
        {"origin": [0, 12, -2], "size": [4, 12, 4], "uv": faces((24, 20, 4, 12), (32, 20, 4, 12), FL(4, 12), (28, 20, 4, 12), (24, 16, 4, 4), (32, 16, 4, 4))}]},
    {"name": "right_arm", "parent": "body", "pivot": [-5, 22, 0], "cubes": [
        {"origin": [-8, 12, -2], "size": [4, 12, 4], "uv": faces((44, 20, 4, 12), (52, 20, 4, 12), (48, 20, 4, 12), (40, 20, 4, 12), (44, 16, 4, 4), (48, 16, 4, 4))}]},
    {"name": "left_arm", "parent": "body", "pivot": [5, 22, 0], "cubes": [
        {"origin": [4, 12, -2], "size": [4, 12, 4], "uv": faces((36, 52, 4, 12), (44, 52, 4, 12), (40, 52, 4, 12), (32, 52, 4, 12), (36, 48, 4, 4), (40, 48, 4, 4))}]},
    {"name": "head", "parent": "body", "pivot": [0, 24, 0]},
    {"name": "head_right", "parent": "head", "pivot": [0, 24, 0], "cubes": [
        {"origin": [-4, 24, -4], "size": [4, 8, 8], "uv": faces((8, 8, 4, 8), (28, 8, 4, 8), (0, 8, 8, 8), FL(8, 8), (8, 0, 4, 8), (16, 0, 4, 8))}]},
    {"name": "head_left", "parent": "head", "pivot": [0, 24, 0], "cubes": [
        {"origin": [0, 24, -4], "size": [4, 8, 8], "uv": faces((12, 8, 4, 8), (24, 8, 4, 8), FL(8, 8), (16, 8, 8, 8), (12, 0, 4, 8), (20, 0, 4, 8))}]},
]

GEO = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.itwastall.old_one", "texture_width": 64, "texture_height": 64,
                    "visible_bounds_width": 2, "visible_bounds_height": 2.5, "visible_bounds_offset": [0, 1, 0]},
    "bones": BONES}]}


# --------------------------------------------------------------------------- textures
def overlay():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    p = img.load()
    r = random.Random(4)
    # hollow eyes: the whites and irises become black sockets (two pixels tall)
    for x in (9, 10, 13, 14):
        for y in (11, 12, 13):
            p[x, y] = (8, 4, 4, 255) if y != 11 else (40, 18, 14, 255)
    # a mouth stretched far too wide, dark red inside
    for x in range(9, 15):
        p[x, 14] = (30, 6, 6, 255)
    for x in range(10, 14):
        p[x, 15] = (70, 10, 8, 255)
    # dried blood down the chin and the shirt
    for (x, y) in ((10, 15), (11, 16 - 1), (13, 15)):
        p[x, y] = (96, 12, 8, 255)
    for (x, y) in ((22, 20), (22, 21), (22, 22), (23, 21), (25, 20), (25, 21), (26, 23), (21, 26), (26, 27), (24, 24)):
        p[x, y] = (110, 14, 10, 230)
    # blood on the hands (bottom of the arms)
    for (x, y) in ((44, 30), (45, 31), (47, 31), (37, 62), (38, 63), (36, 63)):
        p[x, y] = (100, 12, 8, 235)
    # flesh inside the halves: dark wet red with lighter streaks and a few white bone flecks
    fx, fy = FLESH
    for y in range(fy, fy + 16):
        for x in range(fx, fx + 8):
            v = r.randint(-14, 14)
            base = (120 + v, 18 + v // 3, 16 + v // 3, 255)
            if (x + y * 3) % 7 == 0:
                base = (160 + v, 40, 34, 255)
            if r.random() < 0.06:
                base = (210, 200, 180, 255)
            p[x, y] = base
    img.save(os.path.join(ASSETS, "textures/entity/old_one_overlay.png"))


def eyes():
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    p = img.load()
    p[10, 12] = (255, 255, 255, 255)
    p[13, 12] = (255, 255, 255, 255)
    img.save(os.path.join(ASSETS, "textures/entity/old_one_eyes.png"))


# --------------------------------------------------------------------------- animations
def kf(*pairs):
    return {f"{t:.4g}" if t % 1 else f"{t:.1f}": v for t, v in pairs}


def cyc(length, a, b, phase=0.0, axis=0, steps=8, freq=1):
    out = []
    for i in range(steps + 1):
        s = (math.sin(2 * math.pi * (freq * i / steps + phase)) + 1) / 2
        v = [0, 0, 0]
        v[axis] = round(a + (b - a) * s, 2)
        out.append((round(length * i / steps, 4), v))
    return kf(*out)


def shudder(t0, t1, amp, axis=2, step=0.05, seed=1):
    r = random.Random(seed)
    out, t = [], t0
    while t < t1 - 1e-6:
        v = [0, 0, 0]
        v[axis] = round(r.uniform(-amp, amp), 2)
        out.append((round(t, 3), v))
        t += step
    return out


def animations():
    A = {}
    A["idle"] = dict(loop=True, animation_length=6.0, bones={
        # stands far too still; every few seconds the head tilts, then snaps back
        "head": {"rotation": kf((0.0, [0, 0, 0]), (2.6, [0, 0, 0]), (3.4, [0, 0, 14]), (4.8, [0, 0, 14]), (4.85, [0, 0, -3]), (6.0, [0, 0, 0]))},
        "body": {"rotation": cyc(6.0, -0.6, 0.6, axis=0)},
    })
    A["walk"] = dict(loop=True, animation_length=1.0, bones={
        "right_leg": {"rotation": cyc(1.0, -26, 26, 0.25)},
        "left_leg": {"rotation": cyc(1.0, -26, 26, 0.75)},
        # the arms do NOT swing — it walks like it forgot how
        "right_arm": {"rotation": cyc(1.0, -3, 3, 0.75)},
        "left_arm": {"rotation": cyc(1.0, -3, 3, 0.25)},
    })
    L = 2.4
    sh = shudder(0.0, 0.45, 5, seed=3)
    # Everything stays joined: nothing swings on its own. The head cracks and the chest opens like doors,
    # the arms hang limp at the sides, then the whole empty skin deflates in place (root scale, pivot at
    # the feet), like a costume with nobody left inside it.
    A["split"] = dict(loop="hold_on_last_frame", animation_length=L, bones={
        "root": {"scale": kf((0.0, [1, 1, 1]), (1.0, [1, 1, 1]), (1.3, [1.02, 0.86, 1.02]), (1.65, [1.08, 0.55, 1.08]),
                             (2.0, [1.14, 0.28, 1.14]), (2.25, [1.18, 0.14, 1.18]), (L, [1.2, 0.12, 1.2])),
                 "rotation": kf((0.0, [0, 0, 0]), (1.0, [0, 0, 0]), (2.0, [4, 0, 2]), (L, [5, 0, 2]))},
        "body": {"rotation": kf(*sh, (0.45, [0, 0, 0]), (0.7, [6, 0, 0]), (1.0, [3, 0, 0]), (L, [2, 0, 0]))},
        "head": {"rotation": kf(*shudder(0.0, 0.45, 8, axis=1, seed=5), (0.45, [0, 0, 0]), (0.65, [18, 0, 0]), (1.0, [12, 0, 0]), (L, [10, 0, 0]))},
        # the head cracks in two, hinged at the neck
        "head_right": {"rotation": kf((0.0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.62, [0, 0, 32]), (0.9, [0, 0, 44]), (L, [0, 0, 50]))},
        "head_left": {"rotation": kf((0.0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.62, [0, 0, -32]), (0.9, [0, 0, -44]), (L, [0, 0, -50]))},
        # the chest opens like two doors (hinged at the shoulders)
        "torso_right": {"rotation": kf((0.0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.75, [0, 80, 0]), (1.0, [0, 95, 0]), (L, [0, 100, 0]))},
        "torso_left": {"rotation": kf((0.0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.75, [0, -80, 0]), (1.0, [0, -95, 0]), (L, [0, -100, 0]))},
        # arms just hang there — a little forward, never out to the sides
        "right_arm": {"rotation": kf((0.0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.8, [-8, 0, 3]), (L, [-4, 0, 4]))},
        "left_arm": {"rotation": kf((0.0, [0, 0, 0]), (0.5, [0, 0, 0]), (0.8, [-8, 0, -3]), (L, [-4, 0, -4]))},
        "right_leg": {"rotation": kf((0.0, [0, 0, 0]), (1.0, [0, 0, 0]), (L, [0, 0, 2]))},
        "left_leg": {"rotation": kf((0.0, [0, 0, 0]), (1.0, [0, 0, 0]), (L, [0, 0, -2]))},
    })
    doc = {"format_version": "1.8.0", "animations": {f"animation.old_one.{k}": v for k, v in A.items()}}
    with open(os.path.join(ASSETS, "animations/entity/old_one.animation.json"), "w") as fh:
        json.dump(doc, fh, indent=1)


def main():
    with open(os.path.join(ASSETS, "geo/entity/old_one.geo.json"), "w") as fh:
        json.dump(GEO, fh, indent=2)
    overlay()
    eyes()
    animations()
    print("old one: geo, overlay, eyes, animations written")


if __name__ == "__main__":
    main()
