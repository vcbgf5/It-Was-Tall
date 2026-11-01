#!/usr/bin/env python3
"""Procedurally synthesises every sound of It Was Tall and writes sounds.json + subtitle strings.

All sounds are mono .ogg (needed for 3D positional audio in Minecraft).
Requires numpy and ffmpeg (libvorbis).  Run:  python3 tools/gen_sounds.py
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/itwastall")
OUT = os.path.join(ASSETS, "sounds")
SR = 22050
rng = np.random.default_rng(666)


# =========================================================================== DSP helpers
def n_(sec):
    return int(round(sec * SR))


def t_axis(sec):
    return np.arange(n_(sec)) / SR


def noise(sec):
    return rng.standard_normal(n_(sec))


def spectrum_filter(x, lo=0.0, hi=None, tilt=0.0):
    """Band-pass via FFT mask with soft edges; tilt in dB/octave."""
    hi = hi or SR / 2
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    mask = 1 / (1 + (lo / np.maximum(f, 1)) ** 4) * 1 / (1 + (f / hi) ** 4)
    if tilt:
        mask *= (np.maximum(f, 20) / 1000.0) ** (tilt / 6.02)
    return np.fft.irfft(X * mask, len(x))


def sweep_filter(x, centers, q=4.0, block=1024):
    """Time-varying band-pass (STFT overlap-add). centers(t) -> Hz."""
    hop = block // 2
    win = np.hanning(block)
    out = np.zeros(len(x) + block)
    f = np.fft.rfftfreq(block, 1 / SR)
    for start in range(0, len(x), hop):
        seg = x[start:start + block]
        if len(seg) < block:
            seg = np.pad(seg, (0, block - len(seg)))
        c = max(30.0, float(centers(start / SR)))
        mask = np.exp(-0.5 * ((f - c) / (c / q)) ** 2)
        out[start:start + block] += np.fft.irfft(np.fft.rfft(seg * win) * mask, block) * win
    return out[:len(x)]


def formants(x, freqs, q=8.0):
    return sum(sweep_filter(x, (lambda t, fr=fr: fr), q=q) for fr in freqs)


def env(sec, attack, release, curve=2.0):
    t = t_axis(sec)
    e = np.ones_like(t)
    a = t < attack
    e[a] = (t[a] / max(attack, 1e-4)) ** curve
    r = t > sec - release
    e[r] = (np.clip(sec - t[r], 0, None) / max(release, 1e-4)) ** curve
    return e


def decay(sec, tau):
    return np.exp(-t_axis(sec) / tau)


def saw(freq):
    return 2 * ((np.cumsum(freq) / SR) % 1.0) - 1


def sine(freq):
    return np.sin(2 * np.pi * np.cumsum(freq) / SR)


def const(sec, f):
    return np.full(n_(sec), float(f))


def distort(x, drive=4.0):
    return np.tanh(x * drive) / np.tanh(drive)


def mix(*xs):
    n = max(len(x) for x in xs)
    return sum(np.pad(x, (0, n - len(x))) for x in xs)


def place(total_sec, parts):
    """parts = [(time, signal), ...] -> one signal."""
    out = np.zeros(n_(total_sec))
    for tm, s in parts:
        i = n_(tm)
        if i >= len(out):
            continue
        k = min(len(s), len(out) - i)
        out[i:i + k] += s[:k]
    return out


def silence(sec):
    return np.zeros(n_(sec))


def reflections(x, room=0.03, count=6):
    out = np.copy(x)
    for i in range(1, count + 1):
        d = n_(room * i * (0.7 + 0.6 * rng.random()))
        g = 0.55 ** i
        out = mix(out, np.pad(x * g, (d, 0)))
    return out


def reverb(x, seconds=2.5, mix_=0.35, damp=3000, space="cave"):
    """space: 'cave' (dense, dark, long early reflections), 'out' (sparse, airy), 'room' (short)."""
    if space == "out":
        x = reflections(x, 0.09, 3)
    elif space == "cave":
        x = reflections(x, 0.045, 7)
    else:
        x = reflections(x, 0.012, 4)
    ir = noise(seconds) * np.exp(-t_axis(seconds) * 6.9 / seconds)
    ir = spectrum_filter(ir, 80, damp)
    ir /= np.sqrt(np.sum(ir ** 2))
    n = len(x) + len(ir)
    wet = np.fft.irfft(np.fft.rfft(x, n) * np.fft.rfft(ir, n), n)
    dry = np.pad(x, (0, len(ir)))
    return (1 - mix_) * dry + mix_ * wet * 0.6


def reverse_swell(x, seconds=1.2):
    """'Sucking' reversed reverb that rises into the sound."""
    head = x[:n_(0.5)]
    tail = reverb(head, seconds, 1.0, 2500)[::-1]
    tail = tail / (np.max(np.abs(tail)) or 1) * 0.6
    return np.concatenate([tail[:-n_(0.05)] if len(tail) > n_(0.05) else tail, x])


def ringmod(x, f, amount=0.5):
    return x * (1 - amount + amount * sine(np.full(len(x), float(f))))


def pad_to(x, sec):
    n = n_(sec)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def normalize(x, peak=0.9):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1
    return x / m * peak


# =========================================================================== instruments
def drone(sec, base, seed):
    t = t_axis(sec)
    x = np.zeros_like(t)
    parts = [(1, 1.0), (1.007, 0.8), (1.414, 0.28), (1.06, 0.22), (2.01, 0.25), (0.5, 0.7), (2.83, 0.12)]
    for i, (m, a) in enumerate(parts):
        lfo = 1 + 0.004 * np.sin(2 * np.pi * (0.05 + 0.03 * i) * t + seed + i)
        x += a * sine(base * m * lfo)
    x = sweep_filter(x, lambda tt: 180 + 140 * np.sin(2 * np.pi * tt / sec * 1.3 + seed), q=1.2) * 2 + x * 0.4
    sub = sine(const(sec, 31)) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.45 * t) ** 8) * 0.9
    rumble = spectrum_filter(noise(sec), 25, 140) * 2.5
    air = sweep_filter(noise(sec), lambda tt: 400 + 250 * np.sin(2 * np.pi * tt / sec * 1.5 + seed), q=3) * 0.6
    swell = 0.6 + 0.4 * np.sin(2 * np.pi * t / sec - np.pi / 2) ** 2
    return reverb((x * 0.5 + sub + rumble + air) * swell * env(sec, 2.5, 3.0), 3.0, 0.4, 1500)


def wind(sec):
    x = noise(sec)
    howl = sweep_filter(x, lambda tt: 300 + 500 * (0.5 + 0.5 * np.sin(2 * np.pi * tt / 3.7)) * (0.7 + 0.3 * np.sin(tt * 1.3)), q=9)
    howl2 = sweep_filter(x, lambda tt: 700 + 300 * np.sin(2 * np.pi * tt / 5.1 + 1), q=14) * 0.6
    base = spectrum_filter(x, 60, 600) * 0.3
    return reverb((howl * 3 + howl2 * 2 + base) * env(sec, 2.0, 2.5), 2.0, 0.3, space="out")


def creak(sec, f0, f1, space="room"):
    t = t_axis(sec)
    f = f0 + (f1 - f0) * (t / sec) ** 1.5 + 6 * np.sin(2 * np.pi * 7 * t) + rng.normal(0, 1.5, len(t)).cumsum() / np.sqrt(len(t)) * 20
    phase = np.cumsum(np.maximum(f, 3)) / SR
    clicks = (np.diff(np.floor(phase), prepend=0) > 0).astype(float)
    clicks *= 0.6 + 0.4 * rng.random(len(t))
    body = sum(spectrum_filter(clicks, fr * 0.85, fr * 1.15) * a for fr, a in [(420, 1.0), (900, 0.6), (1700, 0.35), (2600, 0.2)])
    return reverb(body * env(sec, 0.15, 0.4) * 4, 1.2, 0.3, 2500, space)


VOWELS = {"a": (750, 1200, 2500), "o": (500, 900, 2400), "u": (350, 800, 2300), "e": (550, 1800, 2500),
          "i": (300, 2200, 2900), "r": (420, 1250, 1650), "n": (260, 1500, 2500), "uh": (650, 1250, 2500)}


def whisper_syllables(sylls, gap=0.06, breathy=1.0):
    """sylls = [(sound_from, sound_to, seconds, sibilant_start)]; formants glide from one vowel to the next."""
    out = []
    for a, b, d, sib in sylls:
        x = noise(d)
        fa, fb = VOWELS[a], VOWELS[b]
        v = sum(sweep_filter(x, (lambda tt, i=i: fa[i] + (fb[i] - fa[i]) * min(1, tt / d * 1.4)), q=7) * w
                for i, w in enumerate((1.0, 0.8, 0.45)))
        shape = np.sin(np.pi * t_axis(d) / d) ** 0.8
        seg = v * shape * 2.2
        if sib:
            s = spectrum_filter(noise(0.11), 3500, 9500) * env(0.11, 0.03, 0.06) * 0.9 * breathy
            seg = np.concatenate([s, seg])
        out += [seg, silence(gap * (0.6 + rng.random()))]
    return np.concatenate(out)


def whisper(sec, syllables):
    keys = list(VOWELS)
    sylls = [(rng.choice(keys), rng.choice(["a", "o", "u", "e", "i", "uh"]), rng.uniform(0.12, 0.28), rng.random() < 0.45)
             for _ in range(syllables)]
    x = whisper_syllables(sylls)
    x = pad_to(x, max(sec, len(x) / SR))
    return reverb(reverse_swell(x, 1.0), 1.5, 0.4, 4000)


def whisper_run():
    word = [("r", "uh", 0.22, False), ("uh", "n", 0.18, False)]
    x = np.concatenate([whisper_syllables(word, 0.02), silence(0.35), whisper_syllables(word, 0.02) * 0.7])
    return reverb(reverse_swell(x, 0.8), 1.4, 0.45, 4500)


def breath(sec, inhale=True):
    t = t_axis(sec)
    x = noise(sec)
    shape = np.sin(np.pi * t / sec) ** (1.5 if inhale else 0.8)
    if not inhale:
        shape *= np.exp(-t / sec)
    centers = (lambda tt: 900 + 600 * tt / sec) if inhale else (lambda tt: 1300 - 600 * tt / sec)
    b = sweep_filter(x, centers, q=2.5) + spectrum_filter(x, 150, 500) * 0.4
    # a wet, slightly voiced rattle on the exhale
    if not inhale:
        b += spectrum_filter(saw(const(sec, 85 + rng.uniform(-8, 8))), 100, 900) * 0.12 * shape
    return b * shape


def heartbeat(beats, start_bpm, end_bpm):
    out = []
    for i in range(beats):
        bpm = start_bpm + (end_bpm - start_bpm) * i / max(1, beats - 1)
        period = 60 / bpm

        def thump(sec, f0, f1, amp):
            t = t_axis(sec)
            body = sine(f0 + (f1 - f0) * t / sec) * decay(sec, 0.06)
            flesh = spectrum_filter(noise(sec), 30, 160) * decay(sec, 0.035) * 0.8
            return (body + flesh) * amp

        lub = thump(0.22, 62, 38, 1.0)
        dub = thump(0.18, 75, 45, 0.65)
        beat = place(period, [(0, lub), (0.27, dub)])
        out.append(beat)
    x = np.concatenate(out)
    x = spectrum_filter(x, 20, 260)  # muffled, like inside your ears
    return reverb(distort(x * 2.2, 1.3), 0.4, 0.15, 800, "room")


def scream(sec, f_start, f_peak, f_end, harsh=1.0, cracks=3):
    t = t_axis(sec)
    curve = np.interp(t, [0, sec * 0.22, sec], [f_start, f_peak, f_end])
    crack = np.ones_like(t)
    for _ in range(cracks):  # voice breaks: short jumps up an interval
        c0 = rng.uniform(0.15, 0.8) * sec
        c1 = c0 + rng.uniform(0.04, 0.12)
        crack[(t > c0) & (t < c1)] = rng.choice([1.5, 1.33, 0.75])
    vib = 1 + 0.03 * np.sin(2 * np.pi * 9 * t) + 0.012 * rng.standard_normal(len(t)).cumsum() / np.sqrt(len(t))
    f = curve * vib * crack
    voice = sum(saw(f * d) for d in (1.0, 1.013, 0.987, 2.004))
    voice = formants(voice, [800, 1150, 2900, 3900], q=5)
    voice = ringmod(voice, 63, 0.35)  # inhuman tone
    grit = spectrum_filter(noise(sec), 1500, 6000) * 0.5 * harsh
    x = distort((voice + grit) * env(sec, 0.03, sec * 0.5, 1.5) * 2, 3 * harsh)
    return reverb(x, 2.0, 0.35)


def screech(sec):
    t = t_axis(sec)
    f = 2200 + 600 * np.sin(2 * np.pi * 23 * t) + 900 * t / sec
    return distort(saw(f) * env(sec, 0.01, sec * 0.7), 3)


def jumpscare(seed_pitch=1.0):
    sec = 1.6
    a = scream(sec, 600 * seed_pitch, 1300 * seed_pitch, 500, harsh=1.7, cracks=4)
    b = scream(sec, 430 * seed_pitch, 920 * seed_pitch, 380, harsh=1.7, cracks=4)
    t = t_axis(0.9)
    sub = sine(85 * np.exp(-t * 2.2) + 25) * decay(0.9, 0.35) * 3
    static = spectrum_filter(noise(0.18), 800, 9000) * (rng.random(n_(0.18)) > 0.4) * 2.5
    x = pad_to(mix(a, b * 0.8, screech(0.9) * 0.45), sec + 2)
    x = mix(x, sub, static)
    return distort(x * 2.2, 2.8)


def sting(sec, notes=(880, 932.3, 987.8, 1244.5)):
    t = t_axis(sec)
    x = sum(saw(np.full(len(t), f) * (1 + 0.004 * np.sin(2 * np.pi * (5 + i) * t))) for i, f in enumerate(notes))
    x = spectrum_filter(x, 500, 5000) * env(sec, 0.02, sec * 0.8, 1.2)
    hit = spectrum_filter(noise(0.4), 30, 200) * decay(0.4, 0.12) * 3
    return reverb(mix(x, hit), 3.0, 0.45)


def knock_once(f=190.0):
    k = (sine(const(0.25, f)) * 1.0 + sine(const(0.25, f * 2.16)) * 0.5) * decay(0.25, 0.035)
    k += spectrum_filter(noise(0.25), 800, 4000) * decay(0.25, 0.006) * 1.5
    return k


def glass_knock():
    sec = 0.5
    modes = sum(sine(const(sec, fr * rng.uniform(0.97, 1.03))) * a * decay(sec, tau)
                for fr, a, tau in [(1850, 0.7, 0.12), (2930, 0.5, 0.09), (4150, 0.35, 0.06), (5600, 0.2, 0.04)])
    click = spectrum_filter(noise(sec), 2000, 9000) * decay(sec, 0.004) * 1.5
    knuckle = sine(const(sec, 320)) * decay(sec, 0.02) * 0.8
    return modes * 0.7 + click + knuckle


def knocks(times, fn=knock_once, space="room", rev=1.0):
    sec = times[-1] + 0.6
    x = place(sec, [(tm, fn() * rng.uniform(0.8, 1.0)) for tm in times])
    return reverb(x, rev, 0.25, 3000, space)


def pick_hit(pitch=1.0):
    sec = 0.6
    ping = sum(sine(const(sec, f * pitch)) * a for f, a in [(2350, 0.6), (3720, 0.4), (5100, 0.25)]) * decay(sec, 0.08)
    crunch = spectrum_filter(noise(sec), 300, 3000) * decay(sec, 0.05) * 1.2
    thud = sine(const(sec, 90)) * decay(sec, 0.04)
    gravel = place(sec, [(0.05 + 0.03 * i, spectrum_filter(noise(0.03), 1500, 6000) * decay(0.03, 0.006) * 0.4) for i in range(4)])
    return reverb(ping * 0.5 + crunch + thud + gravel, 1.2, 0.3, 3500)


def rumble(sec):
    x = spectrum_filter(noise(sec), 20, 180) * 3
    cr = (rng.random(n_(sec)) > 0.9985).astype(float)
    cr = spectrum_filter(cr, 300, 3000) * 6
    return reverb((x + cr) * env(sec, 0.1, sec * 0.6), 1.5, 0.3)


def step(heavy=1.0):
    sec = 0.4
    t = t_axis(sec)
    thud = sine(70 * np.exp(-t * 6) + 40) * decay(sec, 0.06) * heavy
    flesh = spectrum_filter(noise(sec), 40, 300) * decay(sec, 0.03) * 0.9 * heavy
    grit = spectrum_filter(noise(sec), 400, 5000) * decay(sec, 0.04) * 0.6
    return thud + flesh + grit


def limp_steps(count=2, gap_long=0.75, gap_short=0.35, space="room"):
    parts, tm = [], 0.0
    for i in range(count):
        parts.append((tm, step(1.2 if i % 2 == 0 else 0.7)))
        if i % 2 == 0:  # drag the bad leg
            drag = spectrum_filter(noise(0.25), 300, 2500) * env(0.25, 0.08, 0.15) * 0.25
            parts.append((tm + 0.1, drag))
        tm += gap_long if i % 2 == 0 else gap_short
    return reverb(place(tm + 0.5, parts), 1.0, 0.25, 3000, space)


def whoosh_hit():
    sec = 0.7
    t = t_axis(sec)
    w = sweep_filter(noise(sec), lambda tt: 300 + 2500 * (tt / sec), q=2) * np.sin(np.pi * np.clip(t / 0.35, 0, 1)) * (t < 0.35)
    hit = sine(const(0.3, 60)) * decay(0.3, 0.06) * 2 + spectrum_filter(noise(0.3), 100, 2000) * decay(0.3, 0.03)
    return distort(place(sec, [(0, w * 2), (0.33, hit)]), 2)


def groan(sec, f):
    t = t_axis(sec)
    curve = f * (1 - 0.25 * t / sec) * (1 + 0.02 * np.sin(2 * np.pi * 5 * t)) * (1 + 0.04 * rng.standard_normal(len(t)).cumsum() / np.sqrt(len(t)))
    v = formants(saw(curve) + 0.3 * noise(sec), [450, 800, 2600], q=6)
    return reverb(distort(ringmod(v, 41, 0.3) * env(sec, 0.05, sec * 0.6) * 3, 2), 1.0, 0.3)


def growl(sec, f=68):
    t = t_axis(sec)
    jitter = 1 + 0.08 * np.sin(2 * np.pi * 23 * t) * rng.random(len(t))
    curve = f * jitter * (1 + 0.15 * np.sin(2 * np.pi * t / sec))
    v = formants(saw(curve) + 0.5 * noise(sec), [300, 650, 1900], q=4)
    flutter = 0.6 + 0.4 * (np.sin(2 * np.pi * 17 * t) > 0)
    return reverb(distort(v * flutter * env(sec, 0.3, 0.5) * 2.5, 2.5), 1.2, 0.3, 2000)


def sniff():
    parts = []
    for i, tm in enumerate((0.0, 0.18, 0.33, 0.75, 0.92)):
        d = 0.12
        s = sweep_filter(noise(d), lambda tt: 1800 + 1500 * tt / d, q=2) * env(d, 0.02, 0.07) * (1.0 if i < 3 else 0.8)
        parts.append((tm, s))
    return reverb(place(1.3, parts), 0.6, 0.2, 4000, "room")


def scrape(sec):
    t = t_axis(sec)
    rhythm = (0.5 + 0.5 * np.sin(2 * np.pi * 2.3 * t)) ** 2
    x = sweep_filter(noise(sec), lambda tt: 900 + 500 * np.sin(2 * np.pi * 0.7 * tt), q=3) * rhythm
    return reverb(x * env(sec, 0.1, 0.3) * 2, 1.0, 0.3)


# ----------------------------------------------------------------------- small scares
def twig_snap():
    sec = 0.5
    parts = [(0.0 + i * rng.uniform(0.008, 0.02), spectrum_filter(noise(0.05), 900, 7000) * decay(0.05, 0.004) * (1.4 - i * 0.3))
             for i in range(3)]
    wood = (sine(const(sec, 620)) * 0.6 + sine(const(sec, 1130)) * 0.4) * decay(sec, 0.025)
    return reverb(mix(place(sec, parts), wood), 1.4, 0.35, 5000, "out")


def pebble_fall():
    parts, tm, gap = [], 0.0, 0.22
    for i in range(6):
        fr = rng.uniform(2200, 4200)
        click = (sine(const(0.08, fr)) * decay(0.08, 0.012) + spectrum_filter(noise(0.08), 2000, 8000) * decay(0.08, 0.003)) * (1 - i * 0.12)
        parts.append((tm, click))
        tm += gap
        gap *= 0.62
    return reverb(place(tm + 0.4, parts), 2.5, 0.45, 3500, "cave")


def door_creak_short():
    c = creak(0.8, 55, 30)
    latch = spectrum_filter(noise(0.06), 1500, 6000) * decay(0.06, 0.008) * 0.6
    return mix(c, np.pad(latch, (n_(0.75), 0)))


def giggle():
    parts, tm = [], 0.0
    for i in range(rng.integers(4, 7)):
        d = rng.uniform(0.08, 0.13)
        f = (420 - i * 18) * rng.uniform(0.95, 1.05)
        v = formants(saw(const(d, f)) + 0.4 * noise(d), [560, 1700, 2600], q=6)
        parts.append((tm, ringmod(v, 160, 0.5) * np.sin(np.pi * t_axis(d) / d) ** 0.7))
        tm += d + rng.uniform(0.03, 0.07)
    return reverb(distort(place(tm + 0.2, parts) * 2, 2), 2.0, 0.5, 3500)


def metal_scrape():
    sec = 1.3
    t = t_axis(sec)
    x = noise(sec)
    res = sum(sweep_filter(x, (lambda tt, fr=fr: fr * (1 + 0.15 * tt / sec)), q=40) for fr in (870, 1430, 2380))
    tone = sum(sine(fr * (1 + 0.1 * t / sec)) * a for fr, a in ((1430, 0.15), (2380, 0.08)))
    return reverb((res * 3 + tone) * env(sec, 0.2, 0.4), 2.0, 0.4, 4000)


def static_burst(sec=0.6):
    x = spectrum_filter(noise(sec), 400, 9000)
    gate = np.repeat(rng.random(n_(sec) // 200 + 1) > 0.45, 200)[:n_(sec)]
    hum = sine(const(sec, 50)) * 0.2 + sine(const(sec, 150)) * 0.1
    return (x * gate + hum) * env(sec, 0.01, 0.15)


def music_box_note(f):
    sec = 1.8
    partials = [(1, 1.0, 0.6), (2.01, 0.35, 0.35), (3.98, 0.2, 0.2), (5.4, 0.12, 0.12)]
    x = sum(sine(const(sec, f * m * 0.985)) * a * decay(sec, tau) for m, a, tau in partials)
    tine = spectrum_filter(noise(0.02), 3000, 9000) * decay(0.02, 0.003) * 0.2
    return mix(x, tine)


def music_box():
    x = place(2.6, [(0.0, music_box_note(1046)), (0.42, music_box_note(987) * 0.8), (1.1, music_box_note(1108) * 0.6)])
    return reverb(x, 3.0, 0.5, 3500, "room")


# --------------------------------------------------------------------------- ambient extras
def cave_drip(sec=7.0):
    parts = []
    for _ in range(rng.integers(3, 6)):
        d = 0.12
        tt = t_axis(d)
        f = rng.uniform(900, 1600) * (1 + 1.4 * tt / d)
        parts.append((rng.uniform(0, sec - 1), sine(f) * decay(d, 0.025) * rng.uniform(0.5, 1.0)))
    return reverb(place(sec, parts), 3.0, 0.6, 3000, "cave")


def rock_fall():
    sec = 2.8
    base = spectrum_filter(noise(sec), 30, 300) * env(sec, 0.05, 2.0) * 2
    pebbles = place(sec, [(rng.uniform(0, 2.2), (sine(const(0.06, rng.uniform(1500, 4000))) * decay(0.06, 0.01)
                                                 + spectrum_filter(noise(0.06), 1000, 7000) * decay(0.06, 0.004)) * rng.uniform(0.3, 1))
                          for _ in range(40)])
    return reverb(base + pebbles, 2.5, 0.45, 2500, "cave")


def chains(sec=5.0):
    parts = []
    tm = 0.2
    while tm < sec - 0.6:
        clink = sum(sine(const(0.25, fr * rng.uniform(0.95, 1.05))) * a * decay(0.25, 0.05)
                    for fr, a in ((1200, 0.6), (2710, 0.4), (4310, 0.25)))
        rattle = spectrum_filter(noise(0.25), 800, 5000) * decay(0.25, 0.02) * 0.5
        parts.append((tm, (clink + rattle) * rng.uniform(0.4, 1)))
        tm += rng.uniform(0.08, 0.5)
    return reverb(place(sec, parts), 3.5, 0.7, 2000, "cave")


def hum_pulse(sec=12.0):
    t = t_axis(sec)
    x = sine(const(sec, 58)) + 0.5 * sine(const(sec, 116.6)) + 0.25 * sine(const(sec, 175.3))
    x *= 0.55 + 0.45 * np.sin(2 * np.pi * 0.7 * t) ** 2
    hiss = spectrum_filter(noise(sec), 2000, 6000) * 0.05
    return reverb((x + hiss) * env(sec, 2.0, 2.5), 2.0, 0.35, 1500)


def reversed_voice():
    v = mix(whisper(2.0, 6) * 0.8, groan(1.4, 110) * 0.4)
    return reverb(v[::-1], 2.0, 0.5, 2500)[::-1]


def tinnitus(sec=3.5):
    t = t_axis(sec)
    f = 7400 * (1 + 0.003 * np.sin(2 * np.pi * 4 * t))
    return sine(f) * np.exp(-t / 1.4) * env(sec, 0.02, 0.5)


def message_sound():
    w = whisper_run()
    st = static_burst(0.35) * 0.4
    hit = spectrum_filter(noise(0.6), 25, 150) * decay(0.6, 0.18) * 1.5
    return mix(hit, st, np.pad(w * 1.2, (n_(0.15), 0)))


# =========================================================================== catalogue
# event -> list of (file, generator, peak).  Subtitles: event -> (english, polish)
def build_catalogue():
    v = lambda *items: list(items)
    return {
        # ---- creature
        "entity.tall_one.stare": v(("entity/stare1", lambda: mix(drone(6, 46, 0.0), sting(4) * 0.4), .9),
                                   ("entity/stare2", lambda: mix(drone(6, 41, 1.3), whisper(3, 6) * 1.5), .9),
                                   ("entity/stare3", lambda: mix(drone(6, 38, 2.2), growl(2.5, 60) * 0.5), .9)),
        "entity.tall_one.scream": v(("entity/scream1", lambda: scream(2.2, 350, 820, 260), .9),
                                    ("entity/scream2", lambda: scream(2.0, 420, 950, 300), .9),
                                    ("entity/scream3", lambda: scream(1.8, 300, 1100, 240, 1.3, 5), .9)),
        "entity.tall_one.step": v(*[(f"entity/step{i}", (lambda h=h: step(h)), .7) for i, h in enumerate((1.0, 1.2, 0.8, 1.1), 1)]),
        "entity.tall_one.sink": v(("entity/sink", lambda: rumble(1.8), .9)),
        "entity.tall_one.hurt": v(("entity/hurt1", lambda: groan(0.6, 95), .9), ("entity/hurt2", lambda: groan(0.5, 120), .9),
                                  ("entity/hurt3", lambda: groan(0.7, 80), .9)),
        "entity.tall_one.attack": v(("entity/attack1", whoosh_hit, .9), ("entity/attack2", whoosh_hit, .9)),
        "entity.tall_one.knock": v(("entity/knock_glass1", lambda: knocks([0.0, 0.45, 0.9], glass_knock), .85),
                                   ("entity/knock_glass2", lambda: knocks([0.0, 0.3, 1.1], glass_knock), .85),
                                   ("entity/knock_door", lambda: knocks([0.0, 0.42, 0.85]), .85)),
        "entity.tall_one.dig": v(*[(f"entity/dig{i}", (lambda p=p: pick_hit(p)), .8) for i, p in enumerate((1.0, 0.94, 1.07, 0.9), 1)]),
        "entity.tall_one.growl": v(("entity/growl1", lambda: growl(1.6), .85), ("entity/growl2", lambda: growl(2.2, 58), .85)),
        "entity.tall_one.sniff": v(("entity/sniff", sniff, .8)),
        "entity.tall_one.scrape": v(("entity/scrape1", lambda: scrape(1.2), .8), ("entity/scrape2", lambda: scrape(1.6), .8)),
        "jumpscare": v(("jumpscare1", lambda: jumpscare(1.0), .98), ("jumpscare2", lambda: jumpscare(0.85), .98)),
        # ---- ambience
        "ambient.drone": v(("ambient/drone1", lambda: drone(16, 43, 0.4), .7), ("ambient/drone2", lambda: drone(18, 37, 2.1), .7),
                           ("ambient/drone3", lambda: drone(14, 52, 4.0), .7)),
        "ambient.wind": v(("ambient/wind1", lambda: wind(10), .6), ("ambient/wind2", lambda: wind(12), .6)),
        "ambient.creak": v(("ambient/creak1", lambda: creak(1.4, 18, 31), .8), ("ambient/creak2", lambda: creak(2.0, 25, 14), .8),
                           ("ambient/creak3", lambda: creak(1.0, 40, 22), .8), ("ambient/creak4", lambda: creak(2.6, 12, 26), .8)),
        "ambient.distant_scream": v(*[(f"ambient/distant_scream{i}", (lambda a=a: reverb(scream(*a) * 0.5, 4.0, 0.75, 1700, "out")), .5)
                                      for i, a in enumerate(((2.5, 500, 700, 300), (3.0, 380, 560, 200), (2.2, 600, 900, 350)), 1)]),
        "ambient.whisper": v(*[(f"ambient/whisper{i}", (lambda s=s, y=y: whisper(s, y)), .8)
                               for i, (s, y) in enumerate(((2.0, 5), (2.6, 7), (1.6, 4), (3.2, 9), (2.2, 6)), 1)]),
        "ambient.breath": v(*[(f"ambient/breath{i}", (lambda a=a, b=b: np.concatenate([breath(a, True), silence(0.15), breath(b, False)])), .6)
                              for i, (a, b) in enumerate(((1.3, 1.6), (1.0, 2.0), (0.7, 1.1)), 1)]),
        "ambient.heartbeat": v(("ambient/heartbeat", lambda: heartbeat(8, 70, 130), .9)),
        "ambient.sting": v(("ambient/sting1", lambda: sting(3.0), .8), ("ambient/sting2", lambda: sting(3.0, (622, 659, 698, 932)), .8)),
        "ambient.knock_distant": v(("ambient/knock_distant1", lambda: reverb(knocks([0.0, 0.5, 1.0, 1.9]) * 0.6, 3.0, 0.6, 1500), .6),
                                   ("ambient/knock_distant2", lambda: reverb(knocks([0.0, 0.33, 0.66]) * 0.6, 3.0, 0.65, 1300), .6)),
        "ambient.cave_drip": v(("ambient/cave_drip1", cave_drip, .6), ("ambient/cave_drip2", cave_drip, .6)),
        "ambient.rock_fall": v(("ambient/rock_fall", rock_fall, .7)),
        "ambient.chains": v(("ambient/chains", chains, .55)),
        "ambient.hum": v(("ambient/hum", hum_pulse, .55)),
        "ambient.reversed_voice": v(("ambient/reversed_voice1", reversed_voice, .7), ("ambient/reversed_voice2", reversed_voice, .7)),
        # ---- small scares
        "scare.twig": v(("scare/twig1", twig_snap, .9), ("scare/twig2", twig_snap, .9)),
        "scare.pebble": v(("scare/pebble1", pebble_fall, .8), ("scare/pebble2", pebble_fall, .8)),
        "scare.door": v(("scare/door1", door_creak_short, .8), ("scare/door2", door_creak_short, .8)),
        "scare.glass_tap": v(("scare/glass_tap1", lambda: knocks([0.0], glass_knock), .8), ("scare/glass_tap2", lambda: knocks([0.0, 0.18], glass_knock), .8)),
        "scare.knock": v(("scare/knock", lambda: knocks([0.0], lambda: knock_once(150), "room", 1.5), .9)),
        "scare.footsteps": v(("scare/footsteps1", lambda: limp_steps(2), .85), ("scare/footsteps2", lambda: limp_steps(3), .85)),
        "scare.whisper_run": v(("scare/whisper_run", whisper_run, .85)),
        "scare.giggle": v(("scare/giggle1", giggle, .75), ("scare/giggle2", giggle, .75)),
        "scare.metal": v(("scare/metal", metal_scrape, .7)),
        "scare.static": v(("scare/static", static_burst, .6)),
        "scare.breath": v(("scare/breath", lambda: breath(0.9, False) * 1.4, .8)),
        "scare.music_box": v(("scare/music_box", music_box, .7)),
        # ---- ui
        "ui.message": v(("ui/message", message_sound, .9)),
        "ui.tinnitus": v(("ui/tinnitus", tinnitus, .35)),
    }


SUBTITLES = {
    "entity.tall_one.stare": ("Something is watching", "Coś cię obserwuje"),
    "entity.tall_one.scream": ("It screams", "Krzyk"),
    "entity.tall_one.step": ("Heavy footsteps", "Ciężkie kroki"),
    "entity.tall_one.sink": ("Ground crumbles", "Kruszy się ziemia"),
    "entity.tall_one.hurt": ("It hurts", "To obrywa"),
    "entity.tall_one.attack": ("It strikes", "To uderza"),
    "entity.tall_one.knock": ("Knocking on glass", "Pukanie w szybę"),
    "entity.tall_one.dig": ("Digging nearby", "Ktoś kopie w pobliżu"),
    "entity.tall_one.growl": ("Low growl", "Niskie warczenie"),
    "entity.tall_one.sniff": ("Sniffing", "Węszenie"),
    "entity.tall_one.scrape": ("Scraping", "Drapanie"),
    "ambient.wind": ("Wind howls", "Wyje wiatr"),
    "ambient.creak": ("Something creaks", "Coś skrzypi"),
    "ambient.distant_scream": ("Distant scream", "Krzyk w oddali"),
    "ambient.whisper": ("Whispering", "Szepty"),
    "ambient.breath": ("Breathing behind you", "Oddech za tobą"),
    "ambient.heartbeat": ("Heartbeat", "Bicie serca"),
    "ambient.knock_distant": ("Distant knocking", "Pukanie w oddali"),
    "ambient.cave_drip": ("Dripping", "Kapanie"),
    "ambient.rock_fall": ("Rocks fall", "Osypują się kamienie"),
    "ambient.chains": ("Chains rattle", "Brzęk łańcuchów"),
    "ambient.reversed_voice": ("Wrong voice", "Dziwny głos"),
    "scare.twig": ("Twig snaps", "Trzask gałązki"),
    "scare.pebble": ("Pebble falls", "Spada kamyk"),
    "scare.door": ("Door creaks", "Skrzypią drzwi"),
    "scare.glass_tap": ("Tap on glass", "Stuknięcie w szybę"),
    "scare.knock": ("Knock", "Puknięcie"),
    "scare.footsteps": ("Footsteps behind you", "Kroki za tobą"),
    "scare.whisper_run": ("\"run\"", "„uciekaj”"),
    "scare.giggle": ("Giggling", "Chichot"),
    "scare.metal": ("Metal scrapes", "Zgrzyt metalu"),
    "scare.static": ("Static", "Trzaski"),
    "scare.breath": ("Breath on your neck", "Oddech na karku"),
    "scare.music_box": ("Music box", "Pozytywka"),
}
STREAM = {"ambient.drone", "ambient.wind", "ambient.hum"}


def save(name, x, peak=0.9):
    path = os.path.join(OUT, name + ".ogg")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    x = normalize(np.asarray(x, dtype=float), peak)
    fade = min(len(x) // 4, n_(0.01))
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
    return len(x) / SR


def main():
    # start clean so removed sounds don't linger in the jar
    if os.path.isdir(OUT):
        for dp, _, files in os.walk(OUT):
            for f in files:
                if f.endswith(".ogg"):
                    os.remove(os.path.join(dp, f))
    cat = build_catalogue()
    sounds_json = {}
    count = 0
    for event, variants in cat.items():
        entry = {"sounds": []}
        if event in SUBTITLES:
            entry["subtitle"] = "subtitles.itwastall." + event
        for file, gen, peak in variants:
            sec = save(file, gen(), peak)
            s = {"name": "itwastall:" + file}
            if event in STREAM:
                s["stream"] = True
            entry["sounds"].append(s)
            count += 1
            print(f"{event:28s} {file:30s} {sec:5.1f}s")
        sounds_json[event] = entry
    with open(os.path.join(ASSETS, "sounds.json"), "w") as fh:
        json.dump(sounds_json, fh, indent=1)
    for lang, idx in (("en_us", 0), ("pl_pl", 1)):
        p = os.path.join(ASSETS, "lang", lang + ".json")
        d = json.load(open(p, encoding="utf-8"))
        d = {k: v for k, v in d.items() if not k.startswith("subtitles.itwastall.")}
        for ev, names in SUBTITLES.items():
            d["subtitles.itwastall." + ev] = names[idx]
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(d, fh, indent=2, ensure_ascii=False)
    print(f"{count} files, {len(sounds_json)} events")


if __name__ == "__main__":
    main()
