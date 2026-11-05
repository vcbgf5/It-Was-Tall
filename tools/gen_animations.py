#!/usr/bin/env python3
"""Generates GeckoLib (Bedrock 1.8.0) animations for the tall_one model.

Rotation convention (same as the model and GeckoLib): degrees, added to the bone's base rotation.
  +X on a hanging limb swings its end BACKWARD, -X swings it FORWARD (front of the entity = north/-Z).
  +X on an upright part (torso/neck/head) tips it FORWARD.
Position offsets are in model pixels (16 = 1 block); -Z = forward.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/animations/entity/tall_one.animation.json")


def kf(*pairs):
    """kf((t, [x,y,z]), ...) -> {"t": [x,y,z]}"""
    return {f"{t:.4g}" if t % 1 else f"{t:.1f}": v for t, v in pairs}


def cycle(length, a, b, phase=0.0, axis=0, steps=8, freq=1):
    """Smooth sine-ish loop between a and b on one axis (sampled keyframes, linear between)."""
    import math
    out = []
    for i in range(steps + 1):
        t = length * i / steps
        s = (math.sin(2 * math.pi * (freq * i / steps + phase)) + 1) / 2
        v = [0, 0, 0]
        v[axis] = round(a + (b - a) * s, 2)
        out.append((round(t, 4), v))
    return kf(*out)


def merge(*dicts):
    """Sum several keyframe dicts that share the same timestamps."""
    res = {}
    for d in dicts:
        for t, v in d.items():
            res[t] = [round(x + y, 2) for x, y in zip(res.get(t, [0, 0, 0]), v)]
    return res


A = {}

# --- idle_stare: stands still, slight sway, head slowly tilts, sudden twitch -------------
A["idle_stare"] = dict(loop=True, animation_length=4.0, bones={
    "torso": {"rotation": cycle(4.0, -2, 2, axis=2)},
    "head": {"rotation": kf((0.0, [0, 0, 0]), (1.6, [4, 0, 6]), (1.9, [4, 0, 6]),
                            (1.95, [-6, 8, -14]), (2.25, [-6, 8, -14]), (3.2, [0, 0, -4]), (4.0, [0, 0, 0]))},
    "right_arm": {"rotation": cycle(4.0, -2, 3, phase=0.1)},
    "left_arm": {"rotation": cycle(4.0, -2, 3, phase=0.6)},
    "right_hand": {"rotation": kf((0.0, [0, 0, 0]), (2.9, [0, 0, 0]), (3.0, [-12, 0, 0]), (3.15, [0, 0, 0]), (4.0, [0, 0, 0]))},
    "root": {"position": cycle(4.0, 0, -0.3, axis=1)},
})

# --- walk: slow, long strides -----------------------------------------------------------
L = 1.6
A["walk"] = dict(loop=True, animation_length=L, bones={
    "right_leg": {"rotation": cycle(L, -24, 22, phase=0.25)},
    "left_leg": {"rotation": cycle(L, -24, 22, phase=0.75)},
    "right_shin": {"rotation": cycle(L, 0, 30, phase=0.0)},
    "left_shin": {"rotation": cycle(L, 0, 30, phase=0.5)},
    "right_arm": {"rotation": cycle(L, -14, 12, phase=0.75)},
    "left_arm": {"rotation": cycle(L, -14, 12, phase=0.25)},
    "right_forearm": {"rotation": cycle(L, -12, 0, phase=0.75)},
    "left_forearm": {"rotation": cycle(L, -12, 0, phase=0.25)},
    "torso": {"rotation": cycle(L, -3, 3, phase=0.25, axis=1, steps=8)},
    "head": {"rotation": cycle(L, -3, 3, phase=0.75, axis=2)},
    "root": {"position": cycle(L, 0, -0.8, axis=1, freq=2)},
})

# --- run (chase): heavily hunched, huge strides, arms flailing -------------------------
L = 0.6
A["run"] = dict(loop=True, animation_length=L, bones={
    "torso": {"rotation": merge(kf(*[(round(L * i / 8, 4), [32, 0, 0]) for i in range(9)]),
                                cycle(L, -4, 4, phase=0.25, axis=1))},
    "neck": {"rotation": kf((0.0, [-22, 0, 0]), (L, [-22, 0, 0]))},
    "head": {"rotation": merge(kf(*[(round(L * i / 8, 4), [-18, 0, -10]) for i in range(9)]),
                               cycle(L, -4, 4, axis=0))},
    "right_leg": {"rotation": cycle(L, -50, 40, phase=0.25)},
    "left_leg": {"rotation": cycle(L, -50, 40, phase=0.75)},
    "right_shin": {"rotation": cycle(L, 5, 70, phase=0.0)},
    "left_shin": {"rotation": cycle(L, 5, 70, phase=0.5)},
    "right_arm": {"rotation": merge(cycle(L, -75, 25, phase=0.75), kf(*[(round(L * i / 8, 4), [0, 0, 10]) for i in range(9)]))},
    "left_arm": {"rotation": merge(cycle(L, -75, 25, phase=0.25), kf(*[(round(L * i / 8, 4), [0, 0, -10]) for i in range(9)]))},
    "right_forearm": {"rotation": cycle(L, -45, -10, phase=0.75)},
    "left_forearm": {"rotation": cycle(L, -45, -10, phase=0.25)},
    "root": {"position": cycle(L, -2, -0.5, axis=1, freq=2)},
})

# --- sink: arms rise, head tilts back, drops into the ground (plays once, holds) -------
A["sink"] = dict(loop="hold_on_last_frame", animation_length=1.2, bones={
    "root": {"position": kf((0.0, [0, 0, 0]), (0.25, [0, 1, 0]), (0.45, [0, -6, 0]), (0.8, [0, -34, 0]), (1.2, [0, -64, 0]))},
    "right_arm": {"rotation": kf((0.0, [0, 0, 0]), (0.3, [-40, 0, 25]), (0.7, [-165, 0, 12]), (1.2, [-175, 0, 6]))},
    "left_arm": {"rotation": kf((0.0, [0, 0, 0]), (0.3, [-40, 0, -25]), (0.7, [-165, 0, -12]), (1.2, [-175, 0, -6]))},
    "right_forearm": {"rotation": kf((0.0, [0, 0, 0]), (0.7, [-20, 0, 0]), (1.0, [5, 0, 0]), (1.2, [-25, 0, 0]))},
    "left_forearm": {"rotation": kf((0.0, [0, 0, 0]), (0.7, [-20, 0, 0]), (1.0, [5, 0, 0]), (1.2, [-25, 0, 0]))},
    "head": {"rotation": kf((0.0, [0, 0, 0]), (0.4, [-35, 0, -10]), (1.2, [-40, 0, -10]))},
    "torso": {"rotation": kf((0.0, [0, 0, 0]), (0.3, [-12, 0, 0]), (1.2, [-8, 0, 0]))},
})

# --- dig: pickaxe swing with the right arm, body follows ------------------------------
L = 0.8
A["dig"] = dict(loop=True, animation_length=L, bones={
    "right_arm": {"rotation": kf((0.0, [-150, 0, 5]), (0.35, [-160, 0, 5]), (0.5, [-35, 0, 0]), (0.6, [-30, 0, 0]), (L, [-150, 0, 5]))},
    "right_forearm": {"rotation": kf((0.0, [-30, 0, 0]), (0.35, [-35, 0, 0]), (0.5, [-5, 0, 0]), (L, [-30, 0, 0]))},
    "left_arm": {"rotation": kf((0.0, [-30, 0, -6]), (0.5, [-45, 0, -6]), (L, [-30, 0, -6]))},
    "torso": {"rotation": kf((0.0, [-4, 0, 0]), (0.35, [-8, 4, 0]), (0.5, [16, -4, 0]), (0.6, [16, -4, 0]), (L, [-4, 0, 0]))},
    "head": {"rotation": kf((0.0, [8, 0, -10]), (0.5, [16, 0, -10]), (L, [8, 0, -10]))},
})

# --- knock: three slow knocks on glass ------------------------------------------------
L = 1.6
taps = [(0.0, [-40, 0, 0])]
for s in (0.3, 0.6, 0.9):
    taps += [(s, [-40, 0, 0]), (s + 0.08, [-62, 0, 0]), (s + 0.16, [-40, 0, 0])]
taps += [(L, [-40, 0, 0])]
A["knock"] = dict(loop=True, animation_length=L, bones={
    "right_arm": {"rotation": kf((0.0, [-75, 0, -12]), (L, [-75, 0, -12]))},
    "right_forearm": {"rotation": kf(*taps)},
    "head": {"rotation": kf((0.0, [6, 0, 18]), (1.1, [6, 0, 18]), (1.3, [6, 0, 26]), (L, [6, 0, 18]))},
    "torso": {"rotation": kf((0.0, [6, 0, 0]), (L, [6, 0, 0]))},
})

# --- attack: both arms rise and slam down with a lunge (once) ------------------------
A["attack"] = dict(loop=False, animation_length=0.55, bones={
    "right_arm": {"rotation": kf((0.0, [0, 0, 0]), (0.2, [-155, 0, 20]), (0.32, [-45, 0, 6]), (0.55, [0, 0, 0]))},
    "left_arm": {"rotation": kf((0.0, [0, 0, 0]), (0.2, [-155, 0, -20]), (0.32, [-45, 0, -6]), (0.55, [0, 0, 0]))},
    "right_forearm": {"rotation": kf((0.0, [0, 0, 0]), (0.2, [-30, 0, 0]), (0.32, [0, 0, 0]), (0.55, [0, 0, 0]))},
    "left_forearm": {"rotation": kf((0.0, [0, 0, 0]), (0.2, [-30, 0, 0]), (0.32, [0, 0, 0]), (0.55, [0, 0, 0]))},
    "torso": {"rotation": kf((0.0, [0, 0, 0]), (0.2, [-10, 0, 0]), (0.32, [28, 0, 0]), (0.55, [0, 0, 0]))},
    "head": {"rotation": kf((0.0, [0, 0, 0]), (0.2, [-20, 0, 0]), (0.32, [-10, 0, 0]), (0.55, [0, 0, 0]))},
    "root": {"position": kf((0.0, [0, 0, 0]), (0.32, [0, 0, -6]), (0.55, [0, 0, 0]))},
})

# --- emerge: rises out of the torn skin of the Old One (plays once, holds) ---------
A["emerge"] = dict(loop="hold_on_last_frame", animation_length=1.6, bones={
    "root": {"position": kf((0.0, [0, -46, 0]), (0.5, [0, -30, 0]), (1.2, [0, -6, 0]), (1.6, [0, 0, 0]))},
    "right_arm": {"rotation": kf((0.0, [-170, 0, 10]), (0.6, [-160, 0, 14]), (1.2, [-60, 0, 10]), (1.6, [0, 0, 0]))},
    "left_arm": {"rotation": kf((0.0, [-170, 0, -10]), (0.6, [-160, 0, -14]), (1.2, [-60, 0, -10]), (1.6, [0, 0, 0]))},
    "right_forearm": {"rotation": kf((0.0, [-30, 0, 0]), (0.8, [-10, 0, 0]), (1.6, [0, 0, 0]))},
    "left_forearm": {"rotation": kf((0.0, [-30, 0, 0]), (0.8, [-10, 0, 0]), (1.6, [0, 0, 0]))},
    "torso": {"rotation": kf((0.0, [-25, 0, 0]), (1.0, [-10, 0, 0]), (1.6, [0, 0, 0]))},
    "head": {"rotation": kf((0.0, [-40, 0, 0]), (0.9, [-30, 0, 15]), (1.3, [10, 0, -10]), (1.6, [0, 0, 0]))},
})

doc = {"format_version": "1.8.0",
       "animations": {f"animation.tall_one.{k}": v for k, v in A.items()}}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "w") as fh:
    json.dump(doc, fh, indent=1)
print("animations:", ", ".join(A))
