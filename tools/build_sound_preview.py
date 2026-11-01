#!/usr/bin/env python3
"""Builds tools/preview/it_was_tall_sounds.html: a self-contained page to listen to every mod sound.
Sounds are converted to MP3 (plays on every browser incl. iPhone) and embedded as data URIs."""
import base64
import json
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
OUT = os.path.join(ROOT, "tools/preview/it_was_tall_sounds.html")

# event -> (Polish label, when it plays)
INFO = {
    "entity.tall_one.stare": ("Patrzy", "co kilka sekund, gdy stoi i się na ciebie gapi"),
    "ambient.sting": ("Uderzenie", "w chwili, gdy go pierwszy raz widzisz"),
    "entity.tall_one.scream": ("Krzyk", "start pościgu"),
    "ambient.heartbeat": ("Bicie serca", "podczas pościgu i gdy jest blisko"),
    "entity.tall_one.step": ("Kroki", "chodzenie / ambient w nocy"),
    "entity.tall_one.attack": ("Atak", "uderzenie"),
    "entity.tall_one.hurt": ("Ból", "gdy oberwie"),
    "entity.tall_one.sink": ("Zapadanie się", "znika pod ziemią przy twojej bazie"),
    "jumpscare": ("JUMPSCARE", "0,1 s twarzy na ekranie — uwaga, głośne"),
    "entity.tall_one.dig": ("Kilof", "kopie się do ciebie w kopalni"),
    "entity.tall_one.knock": ("Pukanie", "puka w szybę"),
    "ambient.drone": ("Dron", "ambient: kopalnia, noc"),
    "ambient.wind": ("Wiatr", "ambient: noc na zewnątrz"),
    "ambient.creak": ("Skrzypienie", "ambient: w bazie w nocy, kopalnia"),
    "ambient.whisper": ("Szepty", "ambient: tuż za plecami"),
    "ambient.breath": ("Oddech", "ambient: tuż za plecami / stoi za tobą"),
    "ambient.distant_scream": ("Krzyk w oddali", "ambient: noc, kopalnia"),
    "ambient.knock_distant": ("Pukanie w oddali", "ambient: w bazie w nocy"),
}
GROUPS = [
    ("Postać", ["entity.tall_one.stare", "ambient.sting", "entity.tall_one.scream", "ambient.heartbeat",
                "entity.tall_one.step", "entity.tall_one.attack", "entity.tall_one.hurt", "entity.tall_one.sink", "jumpscare"]),
    ("Kopalnia i dom", ["entity.tall_one.dig", "entity.tall_one.knock"]),
    ("Ambient", ["ambient.drone", "ambient.wind", "ambient.creak", "ambient.whisper", "ambient.breath",
                 "ambient.distant_scream", "ambient.knock_distant"]),
]


def mp3_data(ogg):
    mp3 = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", ogg, "-c:a", "libmp3lame", "-b:a", "64k", "-f", "mp3", "-"],
                         check=True, capture_output=True).stdout
    return "data:audio/mpeg;base64," + base64.b64encode(mp3).decode()


sounds = json.load(open(os.path.join(ASSETS, "sounds.json")))
data = []
for title, events in GROUPS:
    items = []
    for ev in events:
        label, when = INFO[ev]
        files = [s["name"].split(":", 1)[1] for s in sounds[ev]["sounds"]]
        items.append({"event": ev, "label": label, "when": when,
                      "variants": [{"name": f, "src": mp3_data(os.path.join(ASSETS, "sounds", f + ".ogg"))} for f in files]})
    data.append({"title": title, "items": items})

face = "data:image/png;base64," + base64.b64encode(open(os.path.join(ASSETS, "textures/gui/jumpscare.png"), "rb").read()).decode()

HTML = r"""<!doctype html><html lang="pl"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>It Was Tall — dźwięki</title>
<style>
:root{--bg:#0b0c0e;--card:#15171b;--line:#2a2d33;--fg:#e9e5dd;--muted:#9c978e;--accent:#b8382c}
*{box-sizing:border-box}html,body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif}
main{max-width:860px;margin:0 auto;padding:24px 16px 64px}
h1{font-size:22px;letter-spacing:.12em;text-transform:uppercase;margin:0 0 4px}
p.sub{color:var(--muted);margin:0 0 20px}
h2{font-size:13px;letter-spacing:.14em;text-transform:uppercase;color:var(--muted);margin:28px 0 10px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(250px,1fr));gap:10px}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px}
.card h3{margin:0;font-size:16px}.card .when{color:var(--muted);font-size:13px;margin:2px 0 10px;min-height:2.6em}
.card code{font-size:11px;color:#6f6b64}
.vars{display:flex;flex-wrap:wrap;gap:6px}
button{background:#22252b;color:var(--fg);border:1px solid #393d45;border-radius:7px;padding:7px 11px;font:inherit;cursor:pointer}
button:hover{border-color:#6d6a63}button.playing{background:#3a1d1a;border-color:var(--accent)}
.bar{position:sticky;top:0;background:rgba(11,12,14,.92);backdrop-filter:blur(6px);padding:10px 0;display:flex;gap:10px;align-items:center;flex-wrap:wrap;z-index:2;border-bottom:1px solid var(--line)}
.bar label{color:var(--muted);font-size:13px;display:flex;gap:6px;align-items:center}
canvas{width:100%;height:56px;display:block;background:#0f1013;border-radius:8px;border:1px solid var(--line)}
#js{position:fixed;inset:0;background:#000 center/cover no-repeat;display:none;z-index:9}
.danger{border-color:var(--accent)}
</style></head><body><div id="js"></div><main>
<h1>It Was Tall — dźwięki</h1>
<p class="sub">Wszystkie dźwięki moda, wygenerowane proceduralnie. Kliknij, żeby odsłuchać. Najlepiej na słuchawkach, po ciemku.</p>
<div class="bar"><button id="stop">■ Stop</button><button id="night" class="danger">▶ Noc: losowy ambient</button>
<label><input type="checkbox" id="face" checked> twarz przy jumpscare (0,1 s)</label>
<label>głośność <input type="range" id="vol" min="0" max="1" step="0.05" value="0.8"></label></div>
<canvas id="scope" width="860" height="56"></canvas>
<div id="list"></div></main>
<script>
const DATA = __DATA__; const FACE = "__FACE__";
const ctx = new (window.AudioContext||window.webkitAudioContext)();
const master = ctx.createGain(); const analyser = ctx.createAnalyser(); analyser.fftSize = 2048;
master.connect(analyser); analyser.connect(ctx.destination);
const vol = document.getElementById('vol'); master.gain.value = vol.value; vol.oninput = () => master.gain.value = vol.value;
const buffers = {}; const playing = new Set();
async function buf(src){ if(!buffers[src]){ const r = await fetch(src); buffers[src] = await ctx.decodeAudioData(await r.arrayBuffer()); } return buffers[src]; }
async function play(src, btn, opts={}){
  await ctx.resume(); const b = await buf(src);
  const s = ctx.createBufferSource(); s.buffer = b; s.playbackRate.value = opts.rate || 1;
  let node = s;
  if (opts.pan !== undefined && ctx.createStereoPanner){ const p = ctx.createStereoPanner(); p.pan.value = opts.pan; node.connect(p); node = p; }
  const g = ctx.createGain(); g.gain.value = opts.gain ?? 1; node.connect(g); g.connect(master);
  s.start(); playing.add(s); if(btn) btn.classList.add('playing');
  s.onended = () => { playing.delete(s); if(btn) btn.classList.remove('playing'); };
  return s;
}
function jumpscare(){ if(!document.getElementById('face').checked) return; const el = document.getElementById('js'); el.style.backgroundImage = `url(${FACE})`; el.style.display='block'; setTimeout(()=>el.style.display='none', 100); }
const list = document.getElementById('list');
for (const g of DATA){
  const h = document.createElement('h2'); h.textContent = g.title; list.appendChild(h);
  const grid = document.createElement('div'); grid.className = 'grid'; list.appendChild(grid);
  for (const it of g.items){
    const c = document.createElement('div'); c.className = 'card';
    c.innerHTML = `<h3>${it.label}</h3><div class="when">${it.when}<br><code>itwastall:${it.event}</code></div>`;
    const v = document.createElement('div'); v.className = 'vars';
    it.variants.forEach((va, i) => { const b = document.createElement('button'); b.textContent = '▶ ' + (it.variants.length > 1 ? (i+1) : 'odtwórz');
      if (it.event === 'jumpscare') b.classList.add('danger');
      b.onclick = () => { play(va.src, b); if (it.event === 'jumpscare') jumpscare(); }; v.appendChild(b); });
    c.appendChild(v); grid.appendChild(c);
  }
}
document.getElementById('stop').onclick = () => { playing.forEach(s => { try{s.stop()}catch(e){} }); clearTimeout(nightT); nightOn=false; nightBtn.textContent='▶ Noc: losowy ambient'; };
// random night ambience, like in game (drone bed + random sounds around you)
const amb = DATA.find(g=>g.title==='Ambient').items; let nightOn=false, nightT=null; const nightBtn = document.getElementById('night');
const pick = a => a[Math.floor(Math.random()*a.length)];
function nightStep(){ if(!nightOn) return; const it = pick(amb); const va = pick(it.variants);
  const close = it.event.includes('whisper') || it.event.includes('breath');
  play(va.src, null, {pan: close ? (Math.random()<.5?-.8:.8) : Math.random()*1.6-.8, gain: close ? 1 : .6, rate: .9 + Math.random()*.2});
  nightT = setTimeout(nightStep, 4000 + Math.random()*6000); }
nightBtn.onclick = () => { nightOn = !nightOn; nightBtn.textContent = nightOn ? '❚❚ Noc: gra…' : '▶ Noc: losowy ambient';
  if (nightOn){ const d = amb.find(i=>i.event==='ambient.drone'); play(pick(d.variants).src, null, {gain:.5}); nightStep(); } else clearTimeout(nightT); };
// oscilloscope
const cv = document.getElementById('scope'), g2 = cv.getContext('2d'), arr = new Uint8Array(analyser.fftSize);
(function draw(){ requestAnimationFrame(draw); const w = cv.width = cv.clientWidth*devicePixelRatio, h = cv.height = 56*devicePixelRatio;
  analyser.getByteTimeDomainData(arr); g2.clearRect(0,0,w,h); g2.strokeStyle = '#b8382c'; g2.lineWidth = 1.5*devicePixelRatio; g2.beginPath();
  for(let i=0;i<arr.length;i++){ const x = i/arr.length*w, y = arr[i]/255*h; i?g2.lineTo(x,y):g2.moveTo(x,y); } g2.stroke(); })();
</script></body></html>"""

html = HTML.replace("__DATA__", json.dumps(data)).replace("__FACE__", face)
os.makedirs(os.path.dirname(OUT), exist_ok=True)
open(OUT, "w").write(html)
print("wrote", OUT, len(html) // 1024, "KB")
