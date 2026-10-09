#!/usr/bin/env python3
"""Flattens every PNG under store/graphics to 24-bit RGB (Play refuses alpha), checks each
against Play's size rules, and lists what was made."""
import os
import sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "store", "graphics")


def check(path, im):
    w, h = im.size
    name = os.path.relpath(path, ROOT)
    if "feature-graphic" in name and (w, h) != (1024, 500):
        sys.exit(f"{name}: feature graphic must be 1024 x 500, is {w} x {h}")
    if "screenshots" in name or "extras/card" in name or "extras/bleed" in name:
        if min(w, h) < 320 or max(w, h) > 3840 or max(w, h) > 2 * min(w, h):
            sys.exit(f"{name}: {w} x {h} breaks Play's screenshot limits")
    return f"{name:44s} {w} x {h}  {os.path.getsize(path) // 1024} KB"


lines = []
for d, _, files in os.walk(ROOT):
    for f in sorted(files):
        if not f.endswith(".png"):
            continue
        p = os.path.join(d, f)
        im = Image.open(p)
        if im.mode != "RGB":
            im = im.convert("RGB")
        im.save(p, optimize=True)
        lines.append(check(p, Image.open(p)))
print("\n".join(sorted(lines)))
