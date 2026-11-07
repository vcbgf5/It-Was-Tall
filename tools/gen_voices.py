#!/usr/bin/env python3
"""IT SPEAKS. Voice lines of the Tall One and the radio broadcasts, made offline with espeak-ng (text to speech)
and then bent out of shape with numpy: It is slowed down and pitched far too low, doubled by an even lower voice
and a breathy whisper, with a light ring-mod wobble and reverb; the radio is band-passed, crackling, with drop-outs
and a bed of static. The radio can also read numbers digit by digit (your coordinates).

Writes assets/itwastall/sounds/voice/*.ogg and merges the entries into sounds.json.
Needs: espeak-ng, ffmpeg, numpy.   Run: python3 tools/gen_voices.py
"""
import json, os, subprocess, sys, tempfile, wave
import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_sounds as gs  # noqa: E402  (DSP helpers and the ogg writer)

SR = gs.SR

# It: key -> line. The keys are the sound ids voice.it.<key> (com.itwastall.registry.ModVoices).
IT_LINES = {
    # anywhere
    "im_here": "I'm here.",
    "know_everything": "I know everything.",
    "see_you": "I see you.",
    "dont_turn": "Don't turn around.",
    "found_you": "Found you.",
    "cant_hide": "You can't hide.",
    "come_closer": "Come closer.",
    "why_running": "Why are you running?",
    "always_here": "I was always here.",
    "look_at_me": "Look at me.",
    "dark_now": "It's dark now.",
    "go_to_sleep": "Go to sleep.",
    "behind_you": "I'm right behind you.",
    "nobody_coming": "Nobody is coming.",
    # caves and mines
    "where_are_you": "Where are you?",
    "what_mining": "What are you mining here?",
    "dig_deeper": "Dig deeper.",
    "hear_digging": "I can hear you digging.",
    "quiet_down_here": "It's so quiet down here.",
    # the base
    "let_me_in": "Let me in.",
    "where_you_sleep": "I know where you sleep.",
    "door_open": "You left the door open.",
    # the chase
    "run": "Run.",
    "there_you_are": "There you are.",
}

# The radio: key -> line, read by voice.radio.<key>
RADIO_LINES = {
    "not_a_test": "This is not a test. Stay inside.",
    "do_not_go_out": "Do not go outside tonight.",
    "it_is_coming": "It is coming. It is tall.",
    "count_rooms": "Count the rooms. If there is one more than yesterday, leave.",
    "keep_lights": "Keep the lights on.",
    "at_your_door": "Someone is standing at your door.",
    "not_the_window": "Do not look at the window.",
    "red_moon": "The moon will be red tonight.",
    "lost_contact": "We have lost contact with the last survivor.",
    "dont_answer": "If you hear knocking, do not answer.",
    "knows_name": "It knows your name.",
    "position": "Your position is",
    "repeat": "I repeat.",
    "x": "x", "z": "z", "minus": "minus",
    "d0": "zero", "d1": "one", "d2": "two", "d3": "three", "d4": "four",
    "d5": "five", "d6": "six", "d7": "seven", "d8": "eight", "d9": "nine",
}


def speak(text, voice, speed, pitch):
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        subprocess.run(["espeak-ng", "-v", voice, "-s", str(speed), "-p", str(pitch), "-w", tmp.name, text], check=True)
        with wave.open(tmp.name, "rb") as w:
            sr = w.getframerate()
            pcm = np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(float) / 32768.0
    os.unlink(tmp.name)
    if sr != SR:
        pcm = np.interp(np.arange(0, len(pcm), sr / SR), np.arange(len(pcm)), pcm)
    return pcm


def stretch(x, factor):
    """Slower and lower by `factor` (> 1), like a tape running too slow."""
    idx = np.arange(0, len(x) - 1, 1.0 / factor)
    return np.interp(idx, np.arange(len(x)), x)


def envelope(x, win=0.02):
    n = max(1, int(win * SR))
    return np.convolve(np.abs(x), np.ones(n) / n, mode="same")


def it_voice(text):
    base = speak(text, "en-us+m3", 125, 15)
    low = stretch(base, 1.38)
    lower = stretch(base, 1.62)
    lower = np.pad(lower, (int(0.03 * SR), 0))
    n = max(len(low), len(lower))
    low, lower = np.pad(low, (0, n - len(low))), np.pad(lower, (0, n - len(lower)))
    breath = gs.spectrum_filter(gs.rng.standard_normal(n), 900, 4500) * envelope(low) * 2.2
    v = low + 0.55 * lower + 0.35 * breath
    v = gs.distort(v, 1.8)
    v = gs.ringmod(v, 28, 0.18)
    v = gs.spectrum_filter(v, 60, 5200)
    v = gs.reverb(v, 2.2, 0.32, 2600, "out")
    return np.concatenate([gs.silence(0.05), v])


def radio_voice(text, bed=True):
    v = speak(text, "en-gb", 140, 45)
    v = gs.spectrum_filter(v, 450, 3000)
    v = gs.distort(v, 2.5)
    # drop-outs: the signal fades away for a moment
    r = np.random.default_rng(abs(hash(text)) % (2 ** 32))
    for _ in range(int(len(v) / SR * 1.2)):
        s = r.integers(0, max(1, len(v) - int(0.08 * SR)))
        v[s:s + int(r.uniform(0.02, 0.07) * SR)] *= 0.08
    if bed:
        static = gs.spectrum_filter(gs.rng.standard_normal(len(v) + int(0.3 * SR)), 800, 6000) * 0.05
        static[:len(v)] += v
        v = static
    return v


def static_loop():
    x = gs.spectrum_filter(gs.rng.standard_normal(int(3 * SR)), 600, 7000) * 0.4
    for _ in range(40):                          # crackles
        s = gs.rng.integers(0, len(x) - 200)
        x[s:s + 60] += gs.rng.uniform(-1, 1, 60)
    return x


def main():
    sj_path = os.path.join(gs.ASSETS, "sounds.json")
    with open(sj_path) as fh:
        sounds = json.load(fh)
    sounds = {k: v for k, v in sounds.items() if not k.startswith("voice.")}
    for key, line in IT_LINES.items():
        gs.save(f"voice/it_{key}", it_voice(line), 0.92)
        sounds[f"voice.it.{key}"] = {"sounds": [{"name": f"itwastall:voice/it_{key}", "attenuation_distance": 32}],
                                     "subtitle": "subtitles.itwastall.voice.it"}
    for key, line in RADIO_LINES.items():
        short = key in ("x", "z", "minus") or key.startswith("d")
        gs.save(f"voice/radio_{key}", radio_voice(line, bed=not short), 0.85)
        sounds[f"voice.radio.{key}"] = {"sounds": [{"name": f"itwastall:voice/radio_{key}", "attenuation_distance": 8}],
                                        "subtitle": "subtitles.itwastall.voice.radio"}
    gs.save("voice/radio_static", static_loop(), 0.5)
    sounds["voice.radio.static"] = {"sounds": [{"name": "itwastall:voice/radio_static", "attenuation_distance": 8}],
                                    "subtitle": "subtitles.itwastall.voice.radio_static"}
    with open(sj_path, "w") as fh:
        json.dump(sounds, fh, indent=2)
    for lang, it, radio, st in (("en_us", "Something speaks", "Radio voice", "Radio static"),
                                ("pl_pl", "Coś mówi", "Głos z radia", "Szum radia")):
        p = os.path.join(gs.ASSETS, "lang", lang + ".json")
        with open(p, encoding="utf-8") as fh:
            d = json.load(fh)
        d["subtitles.itwastall.voice.it"] = it
        d["subtitles.itwastall.voice.radio"] = radio
        d["subtitles.itwastall.voice.radio_static"] = st
        with open(p, "w", encoding="utf-8") as fh:
            json.dump(d, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
    print(len(IT_LINES), "It lines,", len(RADIO_LINES), "radio clips written")


if __name__ == "__main__":
    main()
