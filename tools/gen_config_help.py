#!/usr/bin/env python3
"""Reads ItWasTallConfig.java and writes assets/itwastall/config_help.json: for every option its section, a label and
the help text from its /** comment */ — the in-game settings screen shows them (labels and tooltips).
Run: python3 tools/gen_config_help.py  (again whenever options are added)"""
import json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/java/com/itwastall/config/ItWasTallConfig.java")
OUT = os.path.join(ROOT, "src/main/resources/assets/itwastall/config_help.json")

def label(name):
    words = re.sub(r"([a-z0-9])([A-Z])", r"\1 \2", name).split()
    return " ".join(w.capitalize() if i == 0 else w.lower() for i, w in enumerate(words))

def main():
    lines = open(SRC, encoding="utf-8").read().split("\n")
    out, section, doc, cls = [], "General", [], None
    in_doc = False
    for ln in lines:
        s = ln.strip()
        m = re.match(r"public static final class (\w+)", s)
        if m:
            cls = m.group(1)
            if cls not in ("Values",):
                section = {"Activation": "Activation", "Tension": "Tension"}.get(cls, cls)
            continue
        m = re.match(r"//\s*-+\s*(.+?)\s*-+\s*$", s)
        if m and cls == "Values":
            section = m.group(1).strip().rstrip("-").strip()
            section = re.sub(r"\s*\(.*?\)\s*", " ", section).strip()
            continue
        if s.startswith("/**"):
            in_doc, doc = True, []
            s = s[3:]
        if in_doc:
            end = "*/" in s
            s = s.replace("*/", "").lstrip("*").strip()
            if s: doc.append(s)
            if end: in_doc = False
            continue
        m = re.match(r"public (boolean|int|double|float|long|String) (\w+)\s*=", s)
        if m and cls in ("Values", "Activation", "Tension"):
            name = m.group(2)
            if name == "configVersion":
                doc = []
                continue
            path = name if cls == "Values" else {"Activation": "activation.", "Tension": "tension."}[cls] + name
            text = " ".join(doc)
            text = re.sub(r"\{@\w+\s+([^}]+)\}", r"\1", text)
            out.append({"path": path, "section": section, "label": label(name), "help": text})
            doc = []
        elif s and not s.startswith("//") and not s.startswith("@"):
            doc = [] if not in_doc else doc
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump(out, fh, indent=1, ensure_ascii=False)
    print(len(out), "options,", len({o["section"] for o in out}), "sections")

if __name__ == "__main__":
    main()
