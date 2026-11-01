#!/usr/bin/env python3
"""Builds the mod icon: the real Tall One model rendered as a black silhouette (no photo texture),
standing on a night horizon in fog, moon behind it, glowing eyes, red pixel title.

Steps: writes tools/preview/icon_render.html (three.js), renders it with Playwright (node tools/preview/render_icon.js),
then composes the icon with Pillow.
Outputs: src/main/resources/assets/itwastall/icon.png (512) and tools/icon/icon_1024.png (+ sizes preview).
Requires: tools/preview/three.min.js (three.js r128), node + playwright, Pillow, numpy.
"""
import json
import math
import os
import random
import subprocess

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
PREVIEW = os.path.join(ROOT, "tools/preview")
OUT_DIR = os.path.join(ROOT, "tools/icon")
S = 1024
rnd = random.Random(7)

RENDER_HTML = r"""<!doctype html><html><head><meta charset="utf-8"><style>html,body{margin:0;background:transparent}</style></head><body>
<script>__THREE__</script><script>
const GEO = __GEO__, ANIMS = __ANIM__.animations, P = 1/16, D2R = Math.PI/180;
const W = 1024, H = 1024;
const renderer = new THREE.WebGLRenderer({antialias:true, alpha:true, preserveDrawingBuffer:true});
renderer.setSize(W, H); renderer.setClearColor(0x000000, 0); document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(30, 1, 0.05, 200);
const mat = new THREE.MeshBasicMaterial({color: 0x000000, side: THREE.DoubleSide});
function cubeGeometry(cube, pivot){
  const [ox,oy,oz] = cube.origin, [sx,sy,sz] = cube.size;
  const g = new THREE.BoxGeometry(sx*P, sy*P, sz*P);
  g.translate(((-(ox+sx)) - (-pivot[0]) + sx/2)*P, (oy - pivot[1] + sy/2)*P, (oz - pivot[2] + sz/2)*P);
  return g;
}
const groups = {}; const root = new THREE.Group();
for (const bone of GEO["minecraft:geometry"][0].bones){
  const g = new THREE.Group(), pv = bone.pivot, parent = bone.parent ? groups[bone.parent] : null;
  const pp = parent ? parent.userData.pivot : [0,0,0];
  g.userData.pivot = pv; g.userData.name = bone.name; g.userData.baseRot = bone.rotation || [0,0,0];
  g.position.set((-pv[0] + pp[0])*P, (pv[1]-pp[1])*P, (pv[2]-pp[2])*P); g.userData.basePos = g.position.clone();
  g.rotation.order = 'ZYX';
  for (const c of (bone.cubes||[])) g.add(new THREE.Mesh(cubeGeometry(c, pv), mat));
  groups[bone.name] = g; (parent || root).add(g);
}
function sample(ch, t){ const ks = Object.keys(ch).map(Number).sort((a,b)=>a-b); const get = k => ch[Object.keys(ch).find(s=>Number(s)===k)];
  if (t <= ks[0]) return get(ks[0]); if (t >= ks[ks.length-1]) return get(ks[ks.length-1]);
  for (let i=0;i<ks.length-1;i++) if (t>=ks[i] && t<=ks[i+1]){ const a=get(ks[i]), b=get(ks[i+1]), f=(t-ks[i])/(ks[i+1]-ks[i]); return [0,1,2].map(j=>a[j]+(b[j]-a[j])*f); } }
const anim = ANIMS["animation.tall_one.idle_stare"], T = 2.0;   // the moment it jerks its head to the side
for (const g of Object.values(groups)){
  const b = anim.bones[g.userData.name] || {}, r = b.rotation ? sample(b.rotation, T) : [0,0,0], br = g.userData.baseRot;
  g.rotation.set(-(br[0]+r[0])*D2R, -(br[1]+r[1])*D2R, (br[2]+r[2])*D2R);
  const p = b.position ? sample(b.position, T) : [0,0,0];
  g.position.copy(g.userData.basePos).add(new THREE.Vector3(-p[0]*P, p[1]*P, p[2]*P));
}
scene.add(root);
// eye markers on the front face of the skull (front = -Z), in head-bone local space
const head = groups.head, hp = head.userData.pivot;
const eyes = [[-1.1, 54.6], [1.3, 54.6]].map(([x,y]) => { const o = new THREE.Object3D(); o.position.set((-x + hp[0])*P, (y - hp[1])*P, (-2.55 - hp[2])*P); head.add(o); return o; });
// low camera in front, looking slightly up: makes it feel taller
camera.position.set(1.1, 0.35, -11.5); camera.lookAt(0, 1.45, 0);
scene.updateMatrixWorld(true);
renderer.render(scene, camera);
window.EYES = eyes.map(o => { const v = new THREE.Vector3(); o.getWorldPosition(v); v.project(camera); return [(v.x+1)/2*W, (1-v.y)/2*H]; });
const box = new THREE.Box3().setFromObject(root), pts = [];
for (const x of [box.min.x, box.max.x]) for (const y of [box.min.y, box.max.y]) for (const z of [box.min.z, box.max.z]) { const v = new THREE.Vector3(x,y,z).project(camera); pts.push([(v.x+1)/2*W, (1-v.y)/2*H]); }
window.FEET_Y = Math.max(...pts.map(p=>p[1]));
window.DONE = true;
</script></body></html>"""

RENDER_JS = r"""const { chromium } = require('playwright'); const path = require('path'); const fs = require('fs');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1024, height: 1024 } });
  p.on('pageerror', e => console.error('pageerror', e.message));
  await p.goto('file://' + path.join(__dirname, 'icon_render.html'));
  await p.waitForFunction(() => window.DONE);
  const meta = await p.evaluate(() => ({ eyes: window.EYES, feetY: window.FEET_Y }));
  await p.screenshot({ path: path.join(__dirname, 'icon_silhouette.png'), omitBackground: true });
  fs.writeFileSync(path.join(__dirname, 'icon_silhouette.json'), JSON.stringify(meta));
  await b.close();
})();
"""

# 5x7 pixel font for the title
FONT = {
    "I": ["11111", "00100", "00100", "00100", "00100", "00100", "11111"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "W": ["10001", "10001", "10001", "10101", "10101", "11011", "10001"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "L": ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    " ": ["00000"] * 7,
}


def render_silhouette():
    three = open(os.path.join(PREVIEW, "three.min.js")).read()
    geo = open(os.path.join(ASSETS, "geo/entity/tall_one.geo.json")).read()
    anim = open(os.path.join(ASSETS, "animations/entity/tall_one.animation.json")).read()
    html = RENDER_HTML.replace("__THREE__", three).replace("__GEO__", geo).replace("__ANIM__", anim)
    open(os.path.join(PREVIEW, "icon_render.html"), "w").write(html)
    js = os.path.join(PREVIEW, "render_icon.js")
    open(js, "w").write(RENDER_JS)
    subprocess.run(["node", js], check=True)
    sil = Image.open(os.path.join(PREVIEW, "icon_silhouette.png")).convert("RGBA")
    meta = json.load(open(os.path.join(PREVIEW, "icon_silhouette.json")))
    return sil, meta


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def draw_title(img, text, cx, y, px, color, glitch_index=None):
    d = ImageDraw.Draw(img)
    width = len(text) * 6 * px - px
    x0 = cx - width // 2
    for layer, (dx, dy, col) in enumerate([(px // 2, px // 2, (25, 0, 0, 230)), (0, 0, color)]):
        for i, ch in enumerate(text):
            gx = x0 + i * 6 * px + dx
            gy = y + dy
            if i == glitch_index:
                gx += px
                gy -= px // 2
            for r, row in enumerate(FONT[ch]):
                for c, bit in enumerate(row):
                    if bit == "1":
                        d.rectangle([gx + c * px, gy + r * px, gx + (c + 1) * px - 1, gy + (r + 1) * px - 1], fill=col)


def compose(sil, meta):
    img = Image.new("RGBA", (S, S))
    # --- night sky gradient
    sky = np.zeros((S, S, 3))
    top, mid, low = np.array([6, 8, 18]), np.array([18, 24, 44]), np.array([40, 46, 62])
    for y in range(S):
        t = y / S
        c = top + (mid - top) * min(1, t / 0.55) if t < 0.55 else mid + (low - mid) * min(1, (t - 0.55) / 0.2)
        sky[y, :, :] = c
    img = Image.fromarray(sky.astype(np.uint8)).convert("RGBA")
    d = ImageDraw.Draw(img)
    # stars
    for _ in range(90):
        x, y = rnd.randrange(S), rnd.randrange(int(S * 0.6))
        b = rnd.randint(90, 220)
        s = rnd.choice([2, 2, 3, 4])
        d.rectangle([x, y, x + s, y + s], fill=(b, b, min(255, b + 20), 255))
    # moon with glow, behind the head
    eyes = meta["eyes"]
    head_x = sum(e[0] for e in eyes) / 2
    head_y = sum(e[1] for e in eyes) / 2
    mx, my, mr = head_x + 40, head_y - 30, 150
    glow = Image.new("RGBA", (S, S))
    gd = ImageDraw.Draw(glow)
    gd.ellipse([mx - mr * 2.2, my - mr * 2.2, mx + mr * 2.2, my + mr * 2.2], fill=(150, 165, 200, 70))
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    img = Image.alpha_composite(img, glow)
    moon = Image.new("RGBA", (S, S))
    md = ImageDraw.Draw(moon)
    md.ellipse([mx - mr, my - mr, mx + mr, my + mr], fill=(222, 222, 210, 255))
    for _ in range(9):  # craters
        cx, cy, cr = mx + rnd.uniform(-0.6, 0.6) * mr, my + rnd.uniform(-0.6, 0.6) * mr, rnd.uniform(0.08, 0.2) * mr
        md.ellipse([cx - cr, cy - cr, cx + cr, cy + cr], fill=(196, 196, 186, 255))
    img = Image.alpha_composite(img, moon.filter(ImageFilter.GaussianBlur(1.5)))
    # --- ground: dark pixel grass hill
    horizon = int(meta["feetY"]) - 6
    ground = Image.new("RGBA", (S, S))
    gd = ImageDraw.Draw(ground)
    block = 32
    for x in range(0, S, block):
        h = horizon + int(14 * math.sin(x / 170.0)) // 8 * 8
        for y in range(h, S, block):
            v = rnd.randint(-6, 6)
            col = (14 + v, 26 + v, 14 + v, 255) if y < h + block else (10 + v, 12 + v, 10 + v, 255)
            gd.rectangle([x, y, x + block - 1, y + block - 1], fill=col)
    img = Image.alpha_composite(img, ground)
    # --- the silhouette
    img = Image.alpha_composite(img, sil)
    # --- fog over the feet
    fog = np.zeros((S, S), dtype=float)
    small = np.random.default_rng(3).random((S // 32, S // 32))
    noise = np.array(Image.fromarray((small * 255).astype(np.uint8)).resize((S, S), Image.BICUBIC)) / 255.0
    for y in range(S):
        dist = abs(y - horizon)
        fog[y] = max(0.0, 1 - dist / 170.0) ** 1.6
    fog = np.clip(fog * (0.55 + 0.45 * noise), 0, 1)
    fog_img = Image.new("RGBA", (S, S), (120, 130, 150, 0))
    fog_img.putalpha(Image.fromarray((fog * 170).astype(np.uint8)).filter(ImageFilter.GaussianBlur(14)))
    img = Image.alpha_composite(img, fog_img)
    # --- glowing eyes
    eye_glow = Image.new("RGBA", (S, S))
    ed = ImageDraw.Draw(eye_glow)
    for ex, ey in eyes:
        ed.ellipse([ex - 18, ey - 18, ex + 18, ey + 18], fill=(255, 235, 220, 120))
    eye_glow = eye_glow.filter(ImageFilter.GaussianBlur(10))
    img = Image.alpha_composite(img, eye_glow)
    d = ImageDraw.Draw(img)
    for ex, ey in eyes:
        d.rectangle([ex - 5, ey - 5, ex + 5, ey + 5], fill=(255, 250, 245, 255))
    # --- vignette
    yy, xx = np.mgrid[0:S, 0:S]
    v = np.clip(((xx - S / 2) ** 2 + (yy - S / 2) ** 2) ** 0.5 / (S * 0.72), 0, 1) ** 2.2
    vig = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    vig.putalpha(Image.fromarray((v * 235).astype(np.uint8)))
    img = Image.alpha_composite(img, vig)
    # --- title
    draw_title(img, "IT WAS TALL", S // 2, int(S * 0.855), 12, (196, 22, 16, 255), glitch_index=4)
    # --- film grain
    arr = np.array(img).astype(int)
    grain = np.random.default_rng(9).integers(-9, 10, (S, S, 1))
    arr[:, :, :3] = np.clip(arr[:, :, :3] + grain, 0, 255)
    return Image.fromarray(arr.astype(np.uint8)).convert("RGB")


def main():
    sil, meta = render_silhouette()
    icon = compose(sil, meta)
    os.makedirs(OUT_DIR, exist_ok=True)
    icon.save(os.path.join(OUT_DIR, "icon_1024.png"))
    icon.resize((512, 512), Image.LANCZOS).save(os.path.join(ASSETS, "icon.png"))
    # readability check: 128 / 64 / 32 px
    sheet = Image.new("RGB", (128 + 64 + 32 + 40, 140), (30, 30, 30))
    x = 10
    for s in (128, 64, 32):
        sheet.paste(icon.resize((s, s), Image.LANCZOS), (x, 6))
        x += s + 10
    sheet.save(os.path.join(OUT_DIR, "icon_sizes.png"))
    for f in ("icon_render.html", "render_icon.js", "icon_silhouette.png", "icon_silhouette.json"):
        os.remove(os.path.join(PREVIEW, f))
    print("eyes", meta["eyes"], "feetY", meta["feetY"])


if __name__ == "__main__":
    main()
