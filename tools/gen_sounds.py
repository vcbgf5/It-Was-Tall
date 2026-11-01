#!/usr/bin/env python3
"""Procedurally synthesises all horror sounds for It Was Tall (mono .ogg, needed for 3D positional audio).

Requires numpy and ffmpeg (libvorbis). Output: src/main/resources/assets/itwastall/sounds/**.ogg
Run: python3 tools/gen_sounds.py
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/sounds")
SR = 22050
rng = np.random.default_rng(666)


# ------------------------------------------------------------------ helpers --
def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def noise(sec):
    return rng.standard_normal(int(sec * SR))


def spectrum_filter(x, lo=0.0, hi=None, tilt=0.0):
    """Band-pass via FFT mask with soft edges; tilt (dB/oct) darkens or brightens."""
    hi = hi or SR / 2
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) * 1 / (1 + (f / hi) ** 4)
    if tilt:
        mask *= (np.maximum(f, 20) / 1000.0) ** (tilt / 6.02)
    return np.fft.irfft(X * mask, len(x))


def sweep_filter(x, centers, q=4.0, block=1024):
    """Time-varying band-pass (STFT overlap-add); centers = function(t) -> Hz."""
    hop = block // 2
    win = np.hanning(block)
    out = np.zeros(len(x) + block)
    f = np.fft.rfftfreq(block, 1 / SR)
    for start in range(0, len(x), hop):
        seg = x[start:start + block]
        if len(seg) < block:
            seg = np.pad(seg, (0, block - len(seg)))
        c = centers(start / SR)
        bw = c / q
        mask = np.exp(-0.5 * ((f - c) / bw) ** 2)
        out[start:start + block] += np.fft.irfft(np.fft.rfft(seg * win) * mask, block) * win
    return out[:len(x)]


def formants(x, freqs, q=8.0):
    return sum(sweep_filter(x, (lambda t, fr=fr: fr), q=q) for fr in freqs)


def env(sec, attack, release, curve=2.0):
    t = t_axis(sec)
    e = np.ones_like(t)
    a = t < attack
    e[a] = (t[a] / attack) ** curve
    r = t > sec - release
    e[r] = ((sec - t[r]) / release) ** curve
    return e


def decay(sec, tau):
    return np.exp(-t_axis(sec) / tau)


def reverb(x, seconds=2.5, mix=0.35, damp=3000):
    ir = noise(seconds) * np.exp(-t_axis(seconds) * 6.9 / seconds)
    ir = spectrum_filter(ir, 80, damp)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + len(ir)
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.pad(x, (0, len(ir)))
    return (1 - mix) * dry + mix * wet * 0.6


def saw(freq_curve):
    phase = np.cumsum(freq_curve) / SR
    return 2 * (phase % 1.0) - 1


def sine(freq_curve):
    return np.sin(2 * np.pi * np.cumsum(freq_curve) / SR)


def distort(x, drive=4.0):
    return np.tanh(x * drive) / np.tanh(drive)


def pad_to(x, sec):
    n = int(sec * SR)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def mix(*xs):
    n = max(len(x) for x in xs)
    return sum(np.pad(x, (0, n - len(x))) for x in xs)


def normalize(x, peak=0.9):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def save(name, x, peak=0.9):
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = normalize(x, peak)
    fade = min(len(x) // 4, int(0.01 * SR))
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", tmp.name, "-c:a", "libvorbis", "-q:a", "4", path], check=True)
    os.unlink(tmp.name)
    print(f"{name:28s} {len(x) / SR:5.1f}s")


# ------------------------------------------------------------------- sounds --
def drone(sec, base, seed_shift):
    t = t_axis(sec)
    x = np.zeros_like(t)
    for i, (mult, amp) in enumerate([(1, 1.0), (1.007, 0.8), (1.5 * 0.995, 0.35), (2.01, 0.25), (0.5, 0.6)]):
        lfo = 1 + 0.004 * np.sin(2 * np.pi * (0.05 + 0.03 * i) * t + seed_shift + i)
        x += amp * sine(base * mult * lfo)
    rumble = spectrum_filter(noise(sec), 25, 140) * 2.5
    air = sweep_filter(noise(sec), lambda tt: 400 + 250 * np.sin(2 * np.pi * tt / sec * 1.5 + seed_shift), q=3) * 0.6
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * t / sec - np.pi / 2) ** 2
    return reverb((x * 0.5 + rumble + air) * swell * env(sec, 2.5, 3.0), 3.0, 0.4, 1500)


def wind(sec):
    x = noise(sec)
    howl = sweep_filter(x, lambda tt: 300 + 500 * (0.5 + 0.5 * np.sin(2 * np.pi * tt / 3.7)) * (0.7 + 0.3 * np.sin(tt * 1.3)), q=9)
    base = spectrum_filter(x, 60, 600) * 0.3
    return reverb((howl * 3 + base) * env(sec, 2.0, 2.5), 2.0, 0.3)


def creak(sec, f0, f1):
    t = t_axis(sec)
    f = f0 + (f1 - f0) * (t / sec) ** 1.5 + 6 * np.sin(2 * np.pi * 7 * t)
    phase = np.cumsum(f) / SR
    clicks = (np.diff(np.floor(phase), prepend=0) > 0).astype(float)
    clicks *= 0.6 + 0.4 * rng.random(len(t))
    body = sum(spectrum_filter(clicks, fr * 0.85, fr * 1.15) * a for fr, a in [(420, 1.0), (900, 0.6), (1700, 0.35)])
    return reverb(body * env(sec, 0.15, 0.4) * 4, 1.2, 0.3, 2500)


def whisper(sec, syllables):
    x = noise(sec)
    t = t_axis(sec)
    e = np.zeros_like(t)
    starts = np.sort(rng.uniform(0, sec * 0.8, syllables))
    for s in starts:
        d = rng.uniform(0.12, 0.3)
        e += np.exp(-0.5 * ((t - s - d / 2) / (d / 3)) ** 2) * rng.uniform(0.5, 1)
    vowel = [rng.choice([700, 400, 300, 550]), rng.choice([1100, 1900, 2300, 900]), 2700]
    voiced = formants(x, vowel, q=6) * 2
    hiss = spectrum_filter(x, 3500, 9000) * (e ** 2) * 0.8
    return reverb((voiced * e + hiss) * env(sec, 0.05, 0.2), 1.5, 0.45, 4000)


def breath(sec, inhale=True):
    t = t_axis(sec)
    x = noise(sec)
    shape = np.sin(np.pi * t / sec) ** (1.5 if inhale else 0.8)
    if not inhale:
        shape *= np.exp(-t / sec)
    centers = (lambda tt: 900 + 600 * tt / sec) if inhale else (lambda tt: 1300 - 600 * tt / sec)
    b = sweep_filter(x, centers, q=2.5) + spectrum_filter(x, 150, 500) * 0.4
    return b * shape


def heartbeat(beats, start_bpm, end_bpm):
    out = []
    for i in range(beats):
        bpm = start_bpm + (end_bpm - start_bpm) * i / max(1, beats - 1)
        period = 60 / bpm
        lub = sine(np.full(int(0.18 * SR), 55.0)) * decay(0.18, 0.05) + spectrum_filter(noise(0.18), 30, 120) * decay(0.18, 0.03) * 0.5
        dub = sine(np.full(int(0.15 * SR), 70.0)) * decay(0.15, 0.04) * 0.7
        beat = np.zeros(int(period * SR))
        beat[:len(lub)] += lub
        o = int(0.26 * SR)
        beat[o:o + len(dub)] += dub[:max(0, len(beat) - o)]
        out.append(beat)
    return distort(np.concatenate(out) * 1.5, 1.5)


def scream(sec, f_start, f_peak, f_end, harsh=1.0):
    t = t_axis(sec)
    curve = np.interp(t, [0, sec * 0.25, sec], [f_start, f_peak, f_end])
    vib = 1 + 0.03 * np.sin(2 * np.pi * 9 * t) + 0.01 * rng.standard_normal(len(t)).cumsum() / np.sqrt(len(t))
    voice = sum(saw(curve * vib * d) for d in (1.0, 1.013, 0.987, 2.004))
    voice = formants(voice, [800, 1150, 2900, 3900], q=5)
    grit = spectrum_filter(noise(sec), 1500, 6000) * 0.5 * harsh
    x = distort((voice + grit) * env(sec, 0.05, sec * 0.5, 1.5) * 2, 3 * harsh)
    return reverb(x, 2.0, 0.35)


def sting(sec):
    t = t_axis(sec)
    notes = [880, 932.3, 987.8, 1244.5]  # dissonant cluster
    x = sum(saw(np.full(len(t), f) * (1 + 0.004 * np.sin(2 * np.pi * (5 + i) * t))) for i, f in enumerate(notes))
    x = spectrum_filter(x, 500, 5000) * env(sec, 0.02, sec * 0.8, 1.2)
    hit = spectrum_filter(noise(0.4), 30, 200) * decay(0.4, 0.12) * 3
    x[:len(hit)] += hit
    return reverb(x, 3.0, 0.45)


def knock_once():
    k = (sine(np.full(int(0.25 * SR), 190.0)) * 1.0 + sine(np.full(int(0.25 * SR), 410.0)) * 0.5) * decay(0.25, 0.035)
    k += spectrum_filter(noise(0.25), 800, 4000) * decay(0.25, 0.006) * 1.5
    return k


def knocks(times):
    sec = times[-1] + 0.6
    x = np.zeros(int(sec * SR))
    for tm in times:
        k = knock_once() * rng.uniform(0.8, 1.0)
        i = int(tm * SR)
        x[i:i + len(k)] += k
    return reverb(x, 1.0, 0.25)


def pick_hit(pitch=1.0):
    sec = 0.6
    ping = sum(sine(np.full(int(sec * SR), f * pitch)) * a for f, a in [(2350, 0.6), (3720, 0.4), (5100, 0.25)]) * decay(sec, 0.08)
    crunch = spectrum_filter(noise(sec), 300, 3000) * decay(sec, 0.05) * 1.2
    thud = sine(np.full(int(sec * SR), 90.0)) * decay(sec, 0.04)
    return reverb(ping * 0.5 + crunch + thud, 1.2, 0.3, 3500)


def rumble(sec):
    x = spectrum_filter(noise(sec), 20, 180) * 3
    cr = (rng.random(int(sec * SR)) > 0.9985).astype(float)
    cr = spectrum_filter(cr, 300, 3000) * 6
    return reverb((x + cr) * env(sec, 0.1, sec * 0.6), 1.5, 0.3)


def step():
    sec = 0.35
    thud = sine(np.full(int(sec * SR), 70.0)) * decay(sec, 0.05)
    grit = spectrum_filter(noise(sec), 400, 5000) * decay(sec, 0.04) * 0.6
    return thud + grit


def whoosh_hit():
    sec = 0.7
    t = t_axis(sec)
    w = sweep_filter(noise(sec), lambda tt: 300 + 2500 * (tt / sec), q=2) * np.sin(np.pi * np.clip(t / 0.35, 0, 1)) * (t < 0.35)
    h = np.zeros_like(t)
    hit = sine(np.full(int(0.3 * SR), 60.0)) * decay(0.3, 0.06) * 2 + spectrum_filter(noise(0.3), 100, 2000) * decay(0.3, 0.03)
    i = int(0.33 * SR)
    h[i:i + len(hit)] += hit[:len(h) - i]
    return distort(w * 2 + h, 2)


def jumpscare():
    sec = 1.6
    a = scream(sec, 600, 1250, 500, harsh=1.6)
    b = scream(sec, 430, 900, 380, harsh=1.6)
    hit = spectrum_filter(noise(0.5), 25, 160) * decay(0.5, 0.15) * 5
    x = pad_to(mix(a, b * 0.8), sec + 2)
    x[:len(hit)] += hit
    return distort(x * 2, 2.5)


def groan(sec, f):
    t = t_axis(sec)
    curve = f * (1 - 0.25 * t / sec) * (1 + 0.02 * np.sin(2 * np.pi * 5 * t))
    v = formants(saw(curve) + 0.3 * noise(sec), [450, 800, 2600], q=6)
    return reverb(distort(v * env(sec, 0.05, sec * 0.6) * 3, 2), 1.0, 0.3)


def main():
    # creature
    save("entity/stare1", mix(drone(6, 46, 0.0), sting(4) * 0.4))
    save("entity/stare2", mix(drone(6, 41, 1.3), whisper(3, 6) * 1.5))
    save("entity/scream1", scream(2.2, 350, 820, 260))
    save("entity/scream2", scream(2.0, 420, 950, 300))
    for i in range(1, 4):
        save(f"entity/step{i}", step(), 0.7)
    save("entity/sink", rumble(1.8))
    save("entity/hurt1", groan(0.6, 95))
    save("entity/hurt2", groan(0.5, 120))
    save("entity/attack", whoosh_hit())
    save("entity/knock", knocks([0.0, 0.42, 0.85]))
    for i, p in enumerate([1.0, 0.94, 1.07, 0.9], 1):
        save(f"entity/dig{i}", pick_hit(p), 0.8)
    save("jumpscare", jumpscare(), 0.98)
    # ambience
    save("ambient/drone1", drone(16, 43, 0.4), 0.7)
    save("ambient/drone2", drone(18, 37, 2.1), 0.7)
    save("ambient/drone3", drone(14, 52, 4.0), 0.7)
    save("ambient/wind1", wind(10), 0.6)
    save("ambient/wind2", wind(12), 0.6)
    save("ambient/creak1", creak(1.4, 18, 31), 0.8)
    save("ambient/creak2", creak(2.0, 25, 14), 0.8)
    save("ambient/creak3", creak(1.0, 40, 22), 0.8)
    save("ambient/distant_scream1", reverb(scream(2.5, 500, 700, 300) * 0.5, 4.0, 0.75, 1800), 0.5)
    save("ambient/distant_scream2", reverb(scream(3.0, 380, 560, 200) * 0.5, 4.0, 0.75, 1500), 0.5)
    for i, (sec, syl) in enumerate([(2.0, 5), (2.6, 7), (1.6, 4), (3.2, 9)], 1):
        save(f"ambient/whisper{i}", whisper(sec, syl), 0.8)
    save("ambient/breath1", np.concatenate([breath(1.3, True), np.zeros(int(0.2 * SR)), breath(1.6, False)]), 0.6)
    save("ambient/breath2", np.concatenate([breath(1.0, True), np.zeros(int(0.1 * SR)), breath(2.0, False)]), 0.6)
    save("ambient/heartbeat", heartbeat(8, 70, 130), 0.9)
    save("ambient/sting", sting(3.0), 0.8)
    save("ambient/knock_distant", reverb(knocks([0.0, 0.5, 1.0, 1.9]) * 0.6, 3.0, 0.6, 1500), 0.6)


if __name__ == "__main__":
    main()
