#!/usr/bin/env python3
"""Builds a self-contained HTML 3D viewer for the tall_one model (mimics GeckoLib bone/UV handling)."""
import base64
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
OUT = os.path.join(ROOT, "tools/preview/it_was_tall_preview.html")


def b64(path):
    with open(path, "rb") as fh:
        return "data:image/png;base64," + base64.b64encode(fh.read()).decode()


def jpg(path):
    import io
    from PIL import Image
    buf = io.BytesIO()
    Image.open(path).convert("RGB").save(buf, "JPEG", quality=85)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode()


geo = json.load(open(os.path.join(ASSETS, "geo/entity/tall_one.geo.json")))
three = open(os.path.join(ROOT, "tools/preview/three.min.js")).read()

HTML = r"""<!doctype html>
<html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>It Was Tall — model</title>
<style>
:root{--bg:#0d0f12;--fg:#e8e4dc;--muted:#9a958c;--panel:rgba(16,18,22,.78)}
html,body{margin:0;height:100%;background:var(--bg);color:var(--fg);font:14px/1.4 system-ui,sans-serif;overflow:hidden}
canvas{display:block;touch-action:none}
#ui{position:fixed;left:16px;top:16px;right:16px;display:flex;gap:8px;flex-wrap:wrap;align-items:center;pointer-events:none}
#ui h1{font-size:16px;margin:0 8px 0 0;letter-spacing:.08em;text-transform:uppercase}
#ui button{pointer-events:auto;background:var(--panel);color:var(--fg);border:1px solid #333;border-radius:6px;padding:6px 10px;cursor:pointer;font:inherit}
#ui button.on{border-color:#c9c2b4}
#anims{position:fixed;left:16px;right:16px;bottom:40px;display:flex;gap:6px;flex-wrap:wrap;justify-content:center}
#anims button{background:var(--panel);color:var(--fg);border:1px solid #333;border-radius:6px;padding:6px 10px;cursor:pointer;font:inherit}
#anims button.on{border-color:#c9c2b4;background:#2a2620}
#anims button.scene{border-color:#7a2a22}
#hint{position:fixed;left:16px;bottom:14px;color:var(--muted);font-size:12px}
</style></head><body>
<div id="ui"><h1>It Was Tall</h1>
<button data-v="front">Przód</button><button data-v="three">3/4</button><button data-v="side">Bok</button>
<button data-v="head">Głowa</button><button id="night">Noc</button><button id="spin">Obrót</button><button id="ref">Gracz obok</button><button id="js">Jumpscare 0.1s</button></div>
<div id="jsov" style="position:fixed;inset:0;background:#000 center/cover no-repeat;display:none;z-index:9"></div>
<div id="anims"></div>
<div id="hint">Przeciągnij, żeby obracać · kółko = zoom</div>
<script>__THREE__</script>
<script>
const GEO = __GEO__;
const ANIMS = __ANIM__["animations"];
const TEX_URL = "__TEX__", GLOW_URL = "__GLOW__";
const P = 1/16;
const renderer = new THREE.WebGLRenderer({antialias:true, preserveDrawingBuffer:true});
renderer.setPixelRatio(Math.min(2, devicePixelRatio));
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera(40, 1, 0.05, 200);
const params = new URLSearchParams(location.hash.slice(1));
let night = params.get('night') === '1', spin = params.get('spin') === '1', showRef = params.get('ref') !== '0';
let yaw = 0.0, pitch = 0.08, dist = 7.5, target = new THREE.Vector3(0, 1.75, 0);

const loader = new THREE.TextureLoader();
function tex(url){ const t = loader.load(url, ()=>render()); t.magFilter = THREE.NearestFilter; t.minFilter = THREE.NearestFilter; t.flipY = false; return t; }
const baseTex = tex(TEX_URL), glowTex = tex(GLOW_URL);
const desc = GEO["minecraft:geometry"][0].description, TW = desc.texture_width, TH = desc.texture_height;
const SHADE = {north:.8, south:.8, east:.6, west:.6, up:1, down:.5};

// Corners per face as seen from outside: BL, BR, TR, TL
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
function cubeGeometry(cube, pivot, shadeMul){
  // GeckoLib flips X of bedrock geometry
  const [ox,oy,oz] = cube.origin, [sx,sy,sz] = cube.size;
  const a = [(-(ox+sx)) - (-pivot[0]), oy - pivot[1], oz - pivot[2]].map(v=>v*P);
  const b = [(-ox) - (-pivot[0]), oy+sy - pivot[1], oz+sz - pivot[2]].map(v=>v*P);
  const pos=[], uv=[], col=[], idx=[];
  for (const f of Object.keys(SHADE)){
    const u = cube.uv[f]; if(!u) continue;
    const [u0,v0] = u.uv, [uw,vh] = u.uv_size;
    const uvs = [[u0,v0+vh],[u0+uw,v0+vh],[u0+uw,v0],[u0,v0]];
    const c = corners(f,a,b), base = pos.length/3, s = SHADE[f]*shadeMul;
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
const D2R = Math.PI/180;
function buildModel(texture, opts){
  const groups = {}, root = new THREE.Group();
  for (const bone of GEO["minecraft:geometry"][0].bones){
    const g = new THREE.Group(), pv = bone.pivot;
    const parent = bone.parent ? groups[bone.parent] : null;
    const pp = parent ? parent.userData.pivot : [0,0,0];
    g.userData.pivot = pv;
    g.position.set((-pv[0] - -pp[0])*P, (pv[1]-pp[1])*P, (pv[2]-pp[2])*P);
    const r = bone.rotation || [0,0,0];
    g.rotation.order = 'ZYX';
    g.rotation.set(-r[0]*D2R, -r[1]*D2R, r[2]*D2R);
    g.userData.baseRot = r; g.userData.basePos = g.position.clone(); g.userData.name = bone.name;
    for (const c of (bone.cubes||[])){
      const m = new THREE.Mesh(cubeGeometry(c, pv, 1), opts.material);
      g.add(m);
    }
    groups[bone.name] = g;
    (parent || root).add(g);
  }
  root.userData.groups = groups;
  return root;
}
const baseMat = new THREE.MeshBasicMaterial({map: baseTex, vertexColors: true, side: THREE.DoubleSide, alphaTest: .5});
const glowMat = new THREE.MeshBasicMaterial({map: glowTex, side: THREE.DoubleSide, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false, polygonOffset: true, polygonOffsetFactor: -1});
const model = buildModel(baseTex, {material: baseMat});
const glowModel = buildModel(glowTex, {material: glowMat});
scene.add(model, glowModel);

// ground: blocky grass
const gc = document.createElement('canvas'); gc.width = gc.height = 16;
const gx = gc.getContext('2d');
for(let y=0;y<16;y++) for(let x=0;x<16;x++){ const n = Math.random()*22-11; gx.fillStyle = `rgb(${88+n},${128+n},${58+n})`; gx.fillRect(x,y,1,1); }
const gt = new THREE.CanvasTexture(gc); gt.magFilter = THREE.NearestFilter; gt.wrapS = gt.wrapT = THREE.RepeatWrapping; gt.repeat.set(64,64);
const groundMat = new THREE.MeshBasicMaterial({map: gt, color: 0xffffff});
const ground = new THREE.Mesh(new THREE.PlaneGeometry(64,64), groundMat); ground.rotation.x = -Math.PI/2; scene.add(ground);

// reference: plain grey player-sized figure (1.8 blocks tall incl. head)
const ref = new THREE.Group();
const refMat = new THREE.MeshBasicMaterial({color: 0x8a8f99});
function box(w,h,d,x,y,z,shade){ const m = new THREE.Mesh(new THREE.BoxGeometry(w*P,h*P,d*P), refMat.clone()); m.material.color.multiplyScalar(shade||1); m.position.set(x*P,y*P,z*P); ref.add(m); }
box(4,12,4,-2,6,0,.85); box(4,12,4,2,6,0,.85); box(8,12,4,0,18,0); box(4,12,4,-6,18,0,.8); box(4,12,4,6,18,0,.8); box(8,8,8,0,28,0,1.05);
ref.position.set(1.4, 0, 0.2); scene.add(ref);

function applyLook(){
  scene.background = new THREE.Color(night ? 0x05070c : 0x9fb6c8);
  scene.fog = new THREE.Fog(night ? 0x05070c : 0x9fb6c8, night ? 4 : 14, night ? 16 : 40);
  const k = night ? .2 : 1;
  baseMat.color.setRGB(k, k, k*1.1); groundMat.color.setRGB(k, k, k*1.15);
  ref.children.forEach(m=>m.material.color.setHex(0x8a8f99).multiplyScalar(k));
  glowModel.visible = night; ref.visible = showRef;
  document.getElementById('night').classList.toggle('on', night);
  document.getElementById('spin').classList.toggle('on', spin);
  document.getElementById('ref').classList.toggle('on', showRef);
}
const VIEWS = {
  front: {yaw: 0, pitch: .06, dist: 7.5, ty: 1.75},
  three: {yaw: -.75, pitch: .12, dist: 7.5, ty: 1.75},
  side:  {yaw: -Math.PI/2, pitch: .05, dist: 7.5, ty: 1.75},
  head:  {yaw: -.35, pitch: .02, dist: 2.2, ty: 3.15},
  low:   {yaw: -.25, pitch: -.18, dist: 4.2, ty: 2.4},
};
function setView(v){ const s = VIEWS[v]; if(!s) return; yaw=s.yaw; pitch=s.pitch; dist=s.dist; target.set(0, s.ty, 0); render(); }
function render(){
  const w = innerWidth, h = innerHeight;
  renderer.setSize(w,h); camera.aspect = w/h; camera.updateProjectionMatrix();
  // model front faces -Z, so the camera sits on the -Z side at yaw 0
  camera.position.set(target.x + Math.sin(yaw)*Math.cos(pitch)*dist, target.y + Math.sin(pitch)*dist, target.z - Math.cos(yaw)*Math.cos(pitch)*dist);
  camera.lookAt(target);
  renderer.render(scene, camera);
}
let drag = null;
renderer.domElement.addEventListener('pointerdown', e=>{ drag = [e.clientX, e.clientY]; renderer.domElement.setPointerCapture(e.pointerId); });
addEventListener('pointerup', ()=> drag = null);
addEventListener('pointermove', e=>{ if(!drag) return; yaw -= (e.clientX-drag[0])*.008; pitch = Math.max(-.6, Math.min(1.2, pitch + (e.clientY-drag[1])*.006)); drag=[e.clientX,e.clientY]; render(); });
addEventListener('wheel', e=>{ dist = Math.max(1.2, Math.min(20, dist*(1+Math.sign(e.deltaY)*.1))); render(); }, {passive:true});
addEventListener('resize', render);
document.querySelectorAll('[data-v]').forEach(b=>b.onclick=()=>setView(b.dataset.v));
document.getElementById('night').onclick=()=>{night=!night; applyLook(); render();};
document.getElementById('spin').onclick=()=>{spin=!spin; applyLook();};
const jsImg = "__JS__"; const jsov = document.getElementById('jsov'); jsov.style.backgroundImage = `url(${jsImg})`;
function jumpscare(ms){ jsov.style.display='block'; setTimeout(()=>jsov.style.display='none', ms); }
document.getElementById('js').onclick=()=>jumpscare(100);
if (params.get('js')==='1') jsov.style.display='block';
document.getElementById('ref').onclick=()=>{showRef=!showRef; applyLook(); render();};
// ---------------- animations (GeckoLib semantics: rotation added to base, x/y negated; position x negated) ----
function sample(chan, t){
  const keys = Object.keys(chan).map(Number).sort((a,b)=>a-b);
  const get = k => chan[Object.keys(chan).find(s=>Number(s)===k)];
  if (t <= keys[0]) return get(keys[0]);
  if (t >= keys[keys.length-1]) return get(keys[keys.length-1]);
  for (let i=0;i<keys.length-1;i++) if (t>=keys[i] && t<=keys[i+1]){
    const a = get(keys[i]), b = get(keys[i+1]), f = (t-keys[i])/(keys[i+1]-keys[i]);
    return [0,1,2].map(j=>a[j]+(b[j]-a[j])*f);
  }
}
function applyAnim(obj, anim, t){
  for (const g of Object.values(obj.userData.groups)){
    const b = anim && anim.bones[g.userData.name] || {};
    const r = b.rotation ? sample(b.rotation, t) : [0,0,0], br = g.userData.baseRot;
    g.rotation.set(-(br[0]+r[0])*D2R, -(br[1]+r[1])*D2R, (br[2]+r[2])*D2R);
    const p = b.position ? sample(b.position, t) : [0,0,0];
    g.position.copy(g.userData.basePos).add(new THREE.Vector3(-p[0]*P, p[1]*P, p[2]*P));
  }
}
const NAMES = {idle_stare:'Stoi i patrzy', walk:'Chód', run:'Pościg', sink:'Zapada się', dig:'Kopie', knock:'Puka w szybę', attack:'Atak'};
let current = params.get('anim') || 'idle_stare', animT = Number(params.get('t') || 0), paused = params.get('pause') === '1';
let scene_ = null, last = performance.now();
const bar = document.getElementById('anims');
function animKey(n){ return 'animation.tall_one.' + n; }
function setAnim(n){ current = n; animT = 0; scene_ = null; model.position.set(0,0,0); glowModel.position.set(0,0,0); markAnim(); }
function markAnim(){ bar.querySelectorAll('button[data-a]').forEach(b=>b.classList.toggle('on', b.dataset.a===current && !scene_)); }
Object.keys(NAMES).forEach(n=>{ const b=document.createElement('button'); b.textContent=NAMES[n]; b.dataset.a=n; b.onclick=()=>setAnim(n); bar.appendChild(b); });
const sb = document.createElement('button'); sb.textContent='▶ Scena: patrzy → goni → zapada się → jumpscare'; sb.className='scene'; bar.appendChild(sb);
sb.onclick = ()=>{ scene_ = {t:0, scared:false}; setView('front'); dist = 11; target.set(0,1.6,0); markAnim(); };
function playTime(anim, t){
  const L = anim.animation_length;
  if (anim.loop === true) return t % L;
  return t % (L + 0.8);            // preview: hold the last frame for a moment, then replay
}
function step(dt){
  let name = current, t;
  if (scene_){
    const st = scene_.t += dt;
    let z;
    if (st < 3)          { name='idle_stare'; t=st; z=7; }
    else if (st < 4.6)   { name='run'; t=st-3; z=7 - (st-3)*6.2; }
    else if (st < 5.9)   { name='sink'; t=Math.min(st-4.6, 1.2); z=7 - 1.6*6.2; }
    else                 { name='sink'; t=1.2; z=7 - 1.6*6.2; }
    if (st > 6.3 && !scene_.scared){ scene_.scared = true; jumpscare(100); }
    if (st > 7.5){ scene_ = null; current='idle_stare'; animT=0; markAnim(); z=0; }
    model.position.set(0,0,z); glowModel.position.set(0,0,z);
    applyAnim(model, ANIMS[animKey(name)], t); applyAnim(glowModel, ANIMS[animKey(name)], t);
    return;
  }
  if (!paused) animT += dt;
  const a = ANIMS[animKey(name)];
  t = paused ? Math.min(animT, a.animation_length) : playTime(a, animT);
  applyAnim(model, a, t); applyAnim(glowModel, a, t);
}
markAnim();
(function loop(now){ const dt = Math.min(.05, ((now||performance.now()) - last)/1000); last = now||performance.now();
  if(spin) yaw += .01; step(dt); render(); requestAnimationFrame(loop); })();
applyLook(); setView(params.get('view') || 'three');
</script></body></html>"""

html = (HTML.replace("__THREE__", three)
        .replace("__GEO__", json.dumps(geo))
        .replace("__ANIM__", open(os.path.join(ASSETS, "animations/entity/tall_one.animation.json")).read())
        .replace("__TEX__", b64(os.path.join(ASSETS, "textures/entity/tall_one.png")))
        .replace("__JS__", jpg(os.path.join(ASSETS, "textures/gui/jumpscare.png")))
        .replace("__GLOW__", b64(os.path.join(ASSETS, "textures/entity/tall_one_glowmask.png"))))
with open(OUT, "w") as fh:
    fh.write(html)
print("wrote", OUT, len(html) // 1024, "KB")
