#!/usr/bin/env python3
"""Pulls the Quick slider's AGSL fill shaders out of the app's Kotlin sources, assembled exactly
as ShaderSources.source() and SurgeSources.source() assemble them, into build/shaders/.

The store art then draws its fills with the phone's own shaders: change one in the app, re-run
this, and the next render shows the change.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
VIEW = os.path.join(HERE, "..", "..", "app", "src", "main", "java", "com", "newagedevs", "gesturevolume", "ui", "view")
OUT = os.path.join(HERE, "build", "shaders")


def trim_indent(s):
    """Kotlin's String.trimIndent(): drop a blank first and last line, then the common indent."""
    lines = s.split("\n")
    if lines and not lines[0].strip():
        lines = lines[1:]
    if lines and not lines[-1].strip():
        lines = lines[:-1]
    indents = [len(l) - len(l.lstrip()) for l in lines if l.strip()]
    cut = min(indents) if indents else 0
    return "\n".join(l[cut:] if l.strip() else "" for l in lines)


def raw_strings(path):
    """Every `val NAME = \"\"\"...\"\"\".trimIndent()` in a file, trimmed, by name."""
    src = open(path, encoding="utf-8").read()
    if "${" in src:
        sys.exit(f"{path}: a string template appeared in the shader sources; teach this script about it")
    out = {}
    for m in re.finditer(r'val\s+([A-Z_]+)\s*=\s*"""(.*?)"""\.trimIndent\(\)(\s*\+\s*"\\n")?', src, re.S):
        out[m.group(1)] = trim_indent(m.group(2)) + ("\n" if m.group(3) else "")
    return out, src


def mapping(src, fn):
    """The `when` in body(): which constant each look's name selects, and the fallback."""
    m = re.search(rf"private fun body\(\w+: String\): String = when \((\w+)\.sanitize\(\w+\)\) \{{(.*?)\n    \}}", src, re.S)
    if not m:
        sys.exit("body() not found in " + fn)
    pairs = re.findall(r"\w+\.(\w+)\s*->\s*(\w+)", m.group(2))
    fallback = re.search(r"else\s*->\s*(\w+)", m.group(2)).group(1)
    return pairs, fallback


def main():
    os.makedirs(OUT, exist_ok=True)
    shaders, ssrc = raw_strings(os.path.join(VIEW, "ShaderSources.kt"))
    surge, gsrc = raw_strings(os.path.join(VIEW, "SurgeSources.kt"))
    written = []
    pairs, fallback = mapping(ssrc, "ShaderSources.kt")
    for const in [c for _, c in pairs] + [fallback]:
        name = const.lower().replace("_", "-")
        body = shaders[const]
        text = shaders["HEAD"] + shaders["HELPERS"] + body + shaders["TAIL"]
        open(os.path.join(OUT, f"{name}.agsl"), "w").write(text)
        written.append(name)
    pairs, fallback = mapping(gsrc, "SurgeSources.kt")
    for const in [c for _, c in pairs] + [fallback]:
        name = "surge-" + const.lower().replace("_", "-")
        text = surge["HEAD"] + shaders["HELPERS"] + surge["FRONT"] + surge[const] + surge["TAIL"]
        open(os.path.join(OUT, f"{name}.agsl"), "w").write(text)
        written.append(name)
    print("shaders:", " ".join(sorted(set(written))))


if __name__ == "__main__":
    main()
