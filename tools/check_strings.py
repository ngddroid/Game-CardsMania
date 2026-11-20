#!/usr/bin/env python3
"""Checks the app's string resources: every values/strings_*.xml must parse, have the same keys (strings and plurals)
in values-hi and values-gu, use the same format placeholders in all three languages, escape apostrophes, and no key may
be defined twice in one language. Usage: python3 tools/check_strings.py [area]   (area = file suffix, e.g. 'mania')"""
import os, re, sys
import xml.etree.ElementTree as ET

RES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res")
LANGS = ["values", "values-hi", "values-gu"]
only = sys.argv[1] if len(sys.argv) > 1 else None
errors = []
PH = re.compile(r"%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc%]")

def texts(el):
    return "".join(el.itertext())

def raw_ok(raw_xml, name):
    # an apostrophe must be escaped (\') unless the whole text is wrapped in double quotes
    for m in re.finditer(r'<(string|item)([^>]*)>(.*?)</\1>', raw_xml, re.S):
        body = m.group(3)
        if body.startswith('"') and body.endswith('"'):
            continue
        for i, ch in enumerate(body):
            if ch == "'" and (i == 0 or body[i - 1] != "\\"):
                errors.append(f"{name}: unescaped apostrophe in: {body[:60]}")
                break

def load(path):
    raw = open(path, encoding="utf-8").read()
    raw_ok(raw, path)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as e:
        errors.append(f"{path}: XML error {e}")
        return {}
    out = {}
    for el in root:
        n = el.get("name")
        if el.tag == "string":
            out[("s", n)] = texts(el)
        elif el.tag == "plurals":
            out[("p", n)] = {it.get("quantity"): texts(it) for it in el}
        else:
            continue
    return out

def placeholders(v):
    if isinstance(v, dict):
        return sorted({p for t in v.values() for p in [m.group(0) for m in PH.finditer(t)] if p != "%%"})
    return sorted(m.group(0) for m in PH.finditer(v) if m.group(0) != "%%")

all_keys = {l: {} for l in LANGS}
for f in sorted(os.listdir(os.path.join(RES, "values"))):
    if not (f.startswith("strings") and f.endswith(".xml")):
        continue
    if only and f != f"strings_{only}.xml":
        # still collect keys for duplicate detection
        pass
    per = {}
    for l in LANGS:
        p = os.path.join(RES, l, f)
        if not os.path.exists(p):
            if l != "values" and f != "strings.xml":
                errors.append(f"missing {l}/{f}")
            per[l] = {}
            continue
        per[l] = load(p)
        for k in per[l]:
            if k in all_keys[l]:
                errors.append(f"duplicate key {k[1]} in {l}/{f} and {l}/{all_keys[l][k]}")
            all_keys[l][k] = f
    if f == "strings.xml":
        continue
    base = per["values"]
    for l in LANGS[1:]:
        tr = per[l]
        miss = [k[1] for k in base if k not in tr]
        extra = [k[1] for k in tr if k not in base]
        if miss: errors.append(f"{l}/{f}: missing keys {miss[:8]}{'…' if len(miss) > 8 else ''}")
        if extra: errors.append(f"{l}/{f}: extra keys {extra[:8]}")
        for k, v in base.items():
            if k in tr and placeholders(v) != placeholders(tr[k]):
                errors.append(f"{l}/{f}: placeholders differ for {k[1]}: {placeholders(v)} vs {placeholders(tr[k])}")
            if k[0] == "s" and k in tr and tr[k].strip() == "" and v.strip() != "":
                errors.append(f"{l}/{f}: empty translation for {k[1]}")

if only:
    errors = [e for e in errors if f"strings_{only}.xml" in e or "duplicate" in e]
print("\n".join(errors) if errors else "OK: all string files are consistent")
sys.exit(1 if errors else 0)
