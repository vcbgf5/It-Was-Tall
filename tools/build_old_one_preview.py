#!/usr/bin/env python3
"""Builds tools/preview/old_one_preview.html: the Old One (Skinwalker Steve) and the Tall One in one
3D scene, with the full encounter: it stands and stares, you walk up, the body tears open and It
climbs out and runs at you.

The Steve skin is read from the local Minecraft jar in the Gradle cache (it is Mojang's texture, so the
generated HTML is NOT committed — see .gitignore). Needs tools/preview/three.min.js (three.js r128).
"""
import base64
import glob
import io
import json
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
OUT = os.path.join(ROOT, "tools/preview/old_one_preview.html")


def b64_file(path):
    with open(path, "rb") as fh:
        return "data:image/png;base64," + base64.b64encode(fh.read()).decode()


def steve_png():
    """Steve from the Minecraft client jar in the Gradle cache; a plain placeholder otherwise."""
    for jar in glob.glob(os.path.expanduser("~/.gradle/caches/**/*minecraft*client*.jar"), recursive=True):
        try:
            with zipfile.ZipFile(jar) as z:
                data = z.read("assets/minecraft/textures/entity/player/wide/steve.png")
                return "data:image/png;base64," + base64.b64encode(data).decode()
        except (KeyError, zipfile.BadZipFile, OSError):
            continue
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for (x0, y0, x1, y1, c) in ((0, 0, 32, 16, (150, 110, 80)), (16, 16, 40, 32, (0, 160, 160)),
                                (40, 16, 56, 32, (150, 110, 80)), (0, 16, 16, 32, (60, 60, 150)),
                                (16, 48, 32, 64, (60, 60, 150)), (32, 48, 48, 64, (150, 110, 80))):
        for x in range(x0, x1):
            for y in range(y0, y1):
                img.putpixel((x, y), c + (255,))
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


HTML = r"""<!doctype html><html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Old One — podgląd</title>
<style>
html,body{margin:0;height:100%;background:#0b0c0f;color:#e8e4dc;font:14px/1.4 system-ui,sans-serif;overflow:hidden}
canvas{display:block;touch-action:none}
#ui{position:fixed;left:12px;top:12px;right:12px;display:flex;gap:6px;flex-wrap:wrap;align-items:center}
#ui h1{font-size:15px;margin:0 8px 0 0;letter-spacing:.08em;text-transform:uppercase}
button{background:rgba(20,22,26,.85);color:#e8e4dc;border:1px solid #3a3d44;border-radius:6px;padding:6px 10px;cursor:pointer;font:inherit}
button.on{border-color:#c9c2b4}button.scene{border-color:#8a2a22}
#hint{position:fixed;left:12px;bottom:10px;color:#8f8a80;font-size:12px}
#cap{position:fixed;left:0;right:0;bottom:36px;text-align:center;color:#d7cfc0;font-size:15px;text-shadow:0 1px 3px #000}
</style></head><body>
<div id="ui"><h1>Old One</h1>
<button data-a="idle">Stoi i patrzy</button><button data-a="walk">Chodzi</button><button data-a="split">Rozrywa się</button>
<button id="scene" class="scene">▶ Scena: podchodzisz → ciało pęka → wychodzi It</button>
<button id="night">Noc</button></div>
<div id="cap"></div>
<div id="hint">Przeciągnij, żeby obracać · kółko = zoom</div>
<script>__THREE__</script>
<script>
const OLD = {geo: __OLD_GEO__, anims: __OLD_ANIM__.animations, prefix: 'animation.old_one.'};
const TALL = {geo: __TALL_GEO__, anims: __TALL_ANIM__.animations, prefix: 'animation.tall_one.'};
const P = 1/16, D2R = Math.PI/180;
const renderer = new THREE.WebGLRenderer({antialias:true, preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(2, devicePixelRatio)); document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(45, 1, 0.05, 200);
const params = new URLSearchParams(location.hash.slice(1));
let night = params.get('night') === '1';
let yaw = -0.3, pitch = 0.06, dist = 5.2, target = new THREE.Vector3(0, 1.1, 0);
const loader = new THREE.TextureLoader();
function tex(url){ const t = loader.load(url); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.flipY = false; return t; }
const SHADE = {north:.8, south:.8, east:.6, west:.6, up:1, down:.5};
function corners(f, a, b){
  const [x0,y0,z0] = a, [x1,y1,z1] = b;
  switch(f){
    case 'north': return [[x1,y0,z0],[x0,y0,z0],[x0,y1,z0],[x1,y1,z0]];
    case 'south': return [[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]];
    case 'east':  return [[x1,y0,z1],[x1,y0,z0],[x1,y1,z0],[x1,y1,z1]];
    case 'west':  return [[x0,y0,z0],[x0,y0,z1],[x0,y1,z1],[x0,y1,z0]];
    case 'up':    return [[x1,y1,z1],[x0,y1,z1],[x0,y1,z0],[x1,y1,z0]];
    case 'down':  return [[x1,y0,z0],[x0,y0,z0],[x0,y0,z1],[x1,y0,z1]];
  }
}
function cubeGeometry(cube, pivot, TW, TH){
  const [ox,oy,oz] = cube.origin, [sx,sy,sz] = cube.size;
  const a = [(-(ox+sx)) + pivot[0], oy - pivot[1], oz - pivot[2]].map(v=>v*P);
  const b = [(-ox) + pivot[0], oy+sy - pivot[1], oz+sz - pivot[2]].map(v=>v*P);
  const pos=[], uv=[], col=[], idx=[];
  for (const f of Object.keys(SHADE)){
    const u = cube.uv[f]; if(!u) continue;
    const [u0,v0] = u.uv, [uw,vh] = u.uv_size;
    const uvs = [[u0,v0+vh],[u0+uw,v0+vh],[u0+uw,v0],[u0,v0]];
    const c = corners(f,a,b), base = pos.length/3, s = SHADE[f];
    for(let i=0;i<4;i++){ pos.push(...c[i]); uv.push(uvs[i][0]/TW, uvs[i][1]/TH); col.push(s,s,s); }
    idx.push(base,base+1,base+2, base,base+2,base+3);
  }
  const g = new THREE.BufferGeometry();
  g.setAttribute('position', new THREE.Float32BufferAttribute(pos,3));
  g.setAttribute('uv', new THREE.Float32BufferAttribute(uv,2));
  g.setAttribute('color', new THREE.Float32BufferAttribute(col,3));
  g.setIndex(idx);
  return g;
}
function buildModel(def, material){
  const groups = {}, root = new THREE.Group();
  const d = def.geo["minecraft:geometry"][0], TW = d.description.texture_width, TH = d.description.texture_height;
  for (const bone of d.bones){
    const g = new THREE.Group(), pv = bone.pivot, parent = bone.parent ? groups[bone.parent] : null;
    const pp = parent ? parent.userData.pivot : [0,0,0];
    g.userData.pivot = pv;
    g.position.set((-pv[0] + pp[0])*P, (pv[1]-pp[1])*P, (pv[2]-pp[2])*P);
    const r = bone.rotation || [0,0,0];
    g.rotation.order = 'ZYX'; g.rotation.set(-r[0]*D2R, -r[1]*D2R, r[2]*D2R);
    g.userData.baseRot = r; g.userData.basePos = g.position.clone(); g.userData.name = bone.name;
    for (const c of (bone.cubes||[])) g.add(new THREE.Mesh(cubeGeometry(c, pv, TW, TH), material));
    groups[bone.name] = g; (parent || root).add(g);
  }
  root.userData.groups = groups;
  return root;
}
function sample(ch, t){
  const ks = Object.keys(ch).map(Number).sort((a,b)=>a-b), get = k => ch[Object.keys(ch).find(s=>Number(s)===k)];
  if (t <= ks[0]) return get(ks[0]); if (t >= ks[ks.length-1]) return get(ks[ks.length-1]);
  for (let i=0;i<ks.length-1;i++) if (t>=ks[i] && t<=ks[i+1]){ const a=get(ks[i]), b=get(ks[i+1]), f=(t-ks[i])/(ks[i+1]-ks[i]); return [0,1,2].map(j=>a[j]+(b[j]-a[j])*f); }
}
function applyAnim(models, anim, t){
  for (const obj of models) for (const g of Object.values(obj.userData.groups)){
    const b = anim && anim.bones[g.userData.name] || {};
    const r = b.rotation ? sample(b.rotation, t) : [0,0,0], br = g.userData.baseRot;
    g.rotation.set(-(br[0]+r[0])*D2R, -(br[1]+r[1])*D2R, (br[2]+r[2])*D2R);
    const p = b.position ? sample(b.position, t) : [0,0,0];
    g.position.copy(g.userData.basePos).add(new THREE.Vector3(-p[0]*P, p[1]*P, p[2]*P));
  }
}
function animTime(a, t){ return a.loop === true ? t % a.animation_length : Math.min(t, a.animation_length); }

// ---------- materials & models
const steveTex = tex("__STEVE__"), overTex = tex("__OVERLAY__"), eyeTex = tex("__EYES__");
const tallTex = tex("__TALL_TEX__"), tallGlow = tex("__TALL_GLOW__");
const mBase = new THREE.MeshBasicMaterial({map: steveTex, vertexColors: true, side: THREE.DoubleSide, alphaTest: .5});
const mOver = new THREE.MeshBasicMaterial({map: overTex, vertexColors: true, side: THREE.DoubleSide, alphaTest: .5, polygonOffset: true, polygonOffsetFactor: -1, polygonOffsetUnits: -1});
const mEyes = new THREE.MeshBasicMaterial({map: eyeTex, side: THREE.DoubleSide, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -2});
const mTall = new THREE.MeshBasicMaterial({map: tallTex, vertexColors: true, side: THREE.DoubleSide, alphaTest: .5});
const mTallGlow = new THREE.MeshBasicMaterial({map: tallGlow, side: THREE.DoubleSide, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1});
const old = new THREE.Group(), oldLayers = [buildModel(OLD, mBase), buildModel(OLD, mOver), buildModel(OLD, mEyes)];
oldLayers.forEach(m => old.add(m)); scene.add(old);
const tall = new THREE.Group(), tallLayers = [buildModel(TALL, mTall), buildModel(TALL, mTallGlow)];
tallLayers.forEach(m => tall.add(m)); scene.add(tall); tall.visible = false;

// "Old One" name tag, like a player's
const tc = document.createElement('canvas'); tc.width = 256; tc.height = 48; const tg = tc.getContext('2d');
tg.fillStyle = 'rgba(0,0,0,.35)'; tg.fillRect(40, 6, 176, 36); tg.fillStyle = '#fff'; tg.font = 'bold 26px monospace'; tg.textAlign = 'center'; tg.fillText('Old One', 128, 33);
const tagTex = new THREE.CanvasTexture(tc); tagTex.magFilter = THREE.NearestFilter;
const tag = new THREE.Sprite(new THREE.SpriteMaterial({map: tagTex, depthTest: false})); tag.scale.set(1.0, 0.19, 1); tag.position.set(0, 2.35, 0); old.add(tag);

// ground
const gc = document.createElement('canvas'); gc.width = gc.height = 16; const gx = gc.getContext('2d');
for (let y=0;y<16;y++) for (let x=0;x<16;x++){ const n = Math.random()*22-11; gx.fillStyle = `rgb(${88+n},${128+n},${58+n})`; gx.fillRect(x,y,1,1); }
const gt = new THREE.CanvasTexture(gc); gt.magFilter = THREE.NearestFilter; gt.wrapS = gt.wrapT = THREE.RepeatWrapping; gt.repeat.set(64,64);
const groundMat = new THREE.MeshBasicMaterial({map: gt});
const ground = new THREE.Mesh(new THREE.PlaneGeometry(64,64), groundMat); ground.rotation.x = -Math.PI/2; scene.add(ground);

function applyLook(){
  scene.background = new THREE.Color(night ? 0x05070c : 0x9fb6c8);
  scene.fog = new THREE.Fog(night ? 0x05070c : 0x9fb6c8, night ? 5 : 14, night ? 18 : 40);
  const k = night ? .22 : 1;
  for (const m of [mBase, mOver, mTall]) m.color.setRGB(k, k, k * 1.1);
  groundMat.color.setRGB(k, k, k * 1.15);
  mEyes.visible = night; mTallGlow.visible = night;
  document.getElementById('night').classList.toggle('on', night);
}
function render(){
  const w = innerWidth, h = innerHeight; renderer.setSize(w,h); camera.aspect = w/h; camera.updateProjectionMatrix();
  camera.position.set(target.x + Math.sin(yaw)*Math.cos(pitch)*dist, target.y + Math.sin(pitch)*dist, target.z - Math.cos(yaw)*Math.cos(pitch)*dist);
  camera.lookAt(target); renderer.render(scene, camera);
}
let drag = null;
renderer.domElement.addEventListener('pointerdown', e => { drag = [e.clientX, e.clientY]; });
addEventListener('pointerup', () => drag = null);
addEventListener('pointermove', e => { if (!drag) return; yaw -= (e.clientX-drag[0])*.008; pitch = Math.max(-.5, Math.min(1.2, pitch + (e.clientY-drag[1])*.006)); drag = [e.clientX, e.clientY]; });
addEventListener('wheel', e => { dist = Math.max(1.5, Math.min(20, dist*(1+Math.sign(e.deltaY)*.1))); }, {passive: true});
addEventListener('resize', render);

// ---------- state
let mode = params.get('anim') || 'idle', t = Number(params.get('t') || 0), paused = params.get('pause') === '1';
let sc = null, last = performance.now();
const cap = document.getElementById('cap');
function setMode(a){ mode = a; t = 0; sc = null; tall.visible = false; old.visible = true; old.position.set(0,0,0); yaw = -0.3; dist = 5.2; target.set(0,1.1,0); mark(); }
function mark(){ document.querySelectorAll('[data-a]').forEach(b => b.classList.toggle('on', !sc && b.dataset.a === mode)); cap.textContent = ''; }
document.querySelectorAll('[data-a]').forEach(b => b.onclick = () => setMode(b.dataset.a));
document.getElementById('night').onclick = () => { night = !night; applyLook(); };
document.getElementById('scene').onclick = () => { sc = {t: 0}; old.visible = true; old.position.set(0,0,0); tall.visible = false; yaw = 0; pitch = 0.02; mark(); };

function step(dt){
  if (sc){
    const s = sc.t += dt;
    // 0-3 s: you walk up to it (camera moves in); it just stares
    if (s < 3){ dist = 9 - s * 1.6; target.set(0, 1.25, 0); applyAnim(oldLayers, OLD.anims[OLD.prefix + 'idle'], s); cap.textContent = 'Stoi. Patrzy na ciebie. Podchodzisz bliżej…'; }
    // 3-5.4 s: the body tears open
    else if (s < 5.4){ applyAnim(oldLayers, OLD.anims[OLD.prefix + 'split'], s - 3); cap.textContent = 'Ciało pęka.';
      if (s > 3.5){ tall.visible = true; applyAnim(tallLayers, TALL.anims[TALL.prefix + 'emerge'], s - 3.5); target.set(0, 1.7, 0); dist = 5.5; cap.textContent = 'Coś z niego wychodzi.'; } }
    // 5.4-6.6: the empty skin lies there, It finishes rising
    else if (s < 6.6){ old.visible = s < 6.0; tall.visible = true; applyAnim(oldLayers, OLD.anims[OLD.prefix + 'split'], 2.4); applyAnim(tallLayers, TALL.anims[TALL.prefix + 'emerge'], s - 3.5); cap.textContent = 'It was never Steve.'; }
    // then it runs at you
    else if (s < 8){ old.visible = false; tall.visible = true; applyAnim(tallLayers, TALL.anims[TALL.prefix + 'run'], s - 6.6); tall.position.z = -(s - 6.6) * 2.8; target.set(0, 1.7, tall.position.z); cap.textContent = 'RUN.'; }
    else { sc = null; setMode('idle'); }
    return;
  }
  if (!paused) t += dt;
  const a = OLD.anims[OLD.prefix + mode];
  applyAnim(oldLayers, a, paused ? Math.min(t, a.animation_length) : animTime(a, t));
}
applyLook(); mark();
if (params.get('scene')) { sc = {t: Number(params.get('scene'))}; yaw = 0; }
(function loop(now){ now = now || performance.now(); const dt = Math.min(.05, (now - last)/1000); last = now; step(params.get('frozen') ? 0 : dt); render(); requestAnimationFrame(loop); })();
</script></body></html>"""


def main():
    three = open(os.path.join(ROOT, "tools/preview/three.min.js")).read()
    html = (HTML.replace("__THREE__", three)
            .replace("__OLD_GEO__", open(os.path.join(ASSETS, "geo/entity/old_one.geo.json")).read())
            .replace("__OLD_ANIM__", open(os.path.join(ASSETS, "animations/entity/old_one.animation.json")).read())
            .replace("__TALL_GEO__", open(os.path.join(ASSETS, "geo/entity/tall_one.geo.json")).read())
            .replace("__TALL_ANIM__", open(os.path.join(ASSETS, "animations/entity/tall_one.animation.json")).read())
            .replace("__STEVE__", steve_png())
            .replace("__OVERLAY__", b64_file(os.path.join(ASSETS, "textures/entity/old_one_overlay.png")))
            .replace("__EYES__", b64_file(os.path.join(ASSETS, "textures/entity/old_one_eyes.png")))
            .replace("__TALL_TEX__", b64_file(os.path.join(ASSETS, "textures/entity/tall_one.png")))
            .replace("__TALL_GLOW__", b64_file(os.path.join(ASSETS, "textures/entity/tall_one_glowmask.png"))))
    with open(OUT, "w") as fh:
        fh.write(html)
    print("wrote", OUT, len(html) // 1024, "KB")


if __name__ == "__main__":
    main()
