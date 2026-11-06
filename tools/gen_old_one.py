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

# The body is cut down the middle, head to feet: each half (leg + half torso + arm + half head) hinges on
# the outer edge of its arm at the feet, so when it tears open the two halves fall away to the sides.
# NB: GeckoLib mirrors Bedrock X, so a RIGHT half's outer side is its "east" face, a LEFT half's is "west".
BONES = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "half_right", "parent": "root", "pivot": [-8, 0, 0]},
    {"name": "half_left", "parent": "root", "pivot": [8, 0, 0]},
    {"name": "right_leg", "parent": "half_right", "pivot": [-2, 12, 0], "cubes": [
        {"origin": [-4, 0, -2], "size": [4, 12, 4], "uv": faces((4, 20, 4, 12), (12, 20, 4, 12), (8, 20, 4, 12), (0, 20, 4, 12), (4, 16, 4, 4), (8, 16, 4, 4))}]},
    {"name": "left_leg", "parent": "half_left", "pivot": [2, 12, 0], "cubes": [
        {"origin": [0, 0, -2], "size": [4, 12, 4], "uv": faces((20, 52, 4, 12), (28, 52, 4, 12), (24, 52, 4, 12), (16, 52, 4, 12), (20, 48, 4, 4), (24, 48, 4, 4))}]},
    {"name": "torso_right", "parent": "half_right", "pivot": [-4, 12, 0], "cubes": [
        {"origin": [-4, 12, -2], "size": [4, 12, 4], "uv": faces((20, 20, 4, 12), (36, 20, 4, 12), (16, 20, 4, 12), FL(4, 12), (20, 16, 4, 4), (28, 16, 4, 4))}]},
    {"name": "torso_left", "parent": "half_left", "pivot": [4, 12, 0], "cubes": [
        {"origin": [0, 12, -2], "size": [4, 12, 4], "uv": faces((24, 20, 4, 12), (32, 20, 4, 12), FL(4, 12), (28, 20, 4, 12), (24, 16, 4, 4), (32, 16, 4, 4))}]},
    {"name": "right_arm", "parent": "half_right", "pivot": [-5, 22, 0], "cubes": [
        {"origin": [-8, 12, -2], "size": [4, 12, 4], "uv": faces((44, 20, 4, 12), (52, 20, 4, 12), (48, 20, 4, 12), (40, 20, 4, 12), (44, 16, 4, 4), (48, 16, 4, 4))}]},
    {"name": "left_arm", "parent": "half_left", "pivot": [5, 22, 0], "cubes": [
        {"origin": [4, 12, -2], "size": [4, 12, 4], "uv": faces((36, 52, 4, 12), (44, 52, 4, 12), (40, 52, 4, 12), (32, 52, 4, 12), (36, 48, 4, 4), (40, 48, 4, 4))}]},
    {"name": "head_right", "parent": "half_right", "pivot": [0, 24, 0], "cubes": [
        {"origin": [-4, 24, -4], "size": [4, 8, 8], "uv": faces((8, 8, 4, 8), (28, 8, 4, 8), (0, 8, 8, 8), FL(8, 8), (8, 0, 4, 8), (16, 0, 4, 8))}]},
    {"name": "head_left", "parent": "half_left", "pivot": [0, 24, 0], "cubes": [
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


def both(ch_right, ch_left=None):
    return ch_right, (ch_left if ch_left is not None else ch_right)


def mirror_z(frames):
    """The same keyframes with Z (sideways) rotation flipped, for the other half."""
    return {k: [v[0], -v[1], -v[2]] for k, v in frames.items()}


def animations():
    A = {}
    # the two head halves share a pivot (the neck), so identical keys keep the head whole
    tilt = kf((0.0, [0, 0, 0]), (2.6, [0, 0, 0]), (3.4, [0, 0, 14]), (4.8, [0, 0, 14]), (4.85, [0, 0, -3]), (6.0, [0, 0, 0]))
    sway = cyc(6.0, -0.6, 0.6, axis=0)     # X rotation: both halves pivot on the same X axis line
    A["idle"] = dict(loop=True, animation_length=6.0, bones={
        # stands far too still; every few seconds the head tilts, then snaps back
        "head_right": {"rotation": tilt}, "head_left": {"rotation": tilt},
        "half_right": {"rotation": sway}, "half_left": {"rotation": sway},
    })
    A["walk"] = dict(loop=True, animation_length=1.0, bones={
        "right_leg": {"rotation": cyc(1.0, -26, 26, 0.25)},
        "left_leg": {"rotation": cyc(1.0, -26, 26, 0.75)},
        # the arms do NOT swing — it walks like it forgot how
        "right_arm": {"rotation": cyc(1.0, -3, 3, 0.75)},
        "left_arm": {"rotation": cyc(1.0, -3, 3, 0.25)},
    })
    # the dream hunt (OUTSPACE): a stiff, too-fast sprint, leaning forward, arms pumping
    lean = kf((0.0, [10, 0, 0]), (0.5, [10, 0, 0]))
    A["run"] = dict(loop=True, animation_length=0.5, bones={
        "half_right": {"rotation": lean}, "half_left": {"rotation": lean},
        "right_leg": {"rotation": cyc(0.5, -48, 48, 0.25)},
        "left_leg": {"rotation": cyc(0.5, -48, 48, 0.75)},
        "right_arm": {"rotation": cyc(0.5, -42, 42, 0.75)},
        "left_arm": {"rotation": cyc(0.5, -42, 42, 0.25)},
        "head_right": {"rotation": kf((0.0, [-12, 0, 0]), (0.5, [-12, 0, 0]))},
        "head_left": {"rotation": kf((0.0, [-12, 0, 0]), (0.5, [-12, 0, 0]))},
    })
    L = 2.4
    sh = shudder(0.0, 0.45, 4, axis=0, seed=3)   # X only, so the halves shake together
    # 0-0.45 it shudders; 0.45-0.7 a crack opens down the middle (head first); 0.7-1.45 the two halves
    # fall away to the LEFT and RIGHT, gathering speed, bounce once and lie on the ground, flesh up.
    half_r = kf(*sh, (0.45, [0, 0, 0]), (0.7, [0, 0, 7]), (0.9, [0, 0, 16]), (1.1, [0, 0, 38]), (1.3, [0, 0, 70]),
                (1.45, [0, 0, 90]), (1.58, [0, 0, 82]), (1.72, [0, 0, 90]), (L, [0, 0, 90]))
    head_r = kf((0.0, [0, 0, 0]), (0.45, [0, 0, 0]), (0.62, [0, 0, 26]), (0.9, [0, 0, 30]), (1.45, [0, 0, 22]),
                (1.6, [0, 0, 36]), (L, [0, 0, 32]))
    arm_r = kf((0.0, [0, 0, 0]), (0.7, [0, 0, 0]), (1.2, [-10, 0, -6]), (1.45, [-6, 0, 0]), (L, [-4, 0, 0]))
    # the leg is narrower than torso + arm: once the half lies on its side the foot drops to the ground
    leg_r = kf((0.0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.45, [0, 0, -12]), (1.62, [0, 0, -22]), (L, [0, 0, -20]))
    A["split"] = dict(loop="hold_on_last_frame", animation_length=L, bones={
        # GeckoLib's mirrored X: a NEGATIVE Z rotation tips the right half outwards (to its right)
        "half_right": {"rotation": mirror_z(half_r)}, "half_left": {"rotation": half_r},
        "head_right": {"rotation": mirror_z(head_r)}, "head_left": {"rotation": head_r},
        "right_arm": {"rotation": mirror_z(arm_r)}, "left_arm": {"rotation": arm_r},
        "right_leg": {"rotation": mirror_z(leg_r)}, "left_leg": {"rotation": leg_r},
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
