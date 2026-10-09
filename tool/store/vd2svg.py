#!/usr/bin/env python3
"""Converts an Android VectorDrawable XML into an SVG, so the app's own drawables (the launcher
icon, its glyphs) can be placed in the store art exactly as the app draws them.

Handles what this project's drawables use: <vector> viewport, nested <group> transforms,
<clip-path>, <path> fill/stroke with colours, alphas, caps, joins, fill type, and aapt:attr
linear/radial gradients. A theme attribute colour (?attr/...) becomes `currentColor`.

    python3 vd2svg.py in.xml out.svg [--tint '#RRGGBB']
"""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"


def color(value, tint=None):
    """Android #RGB/#ARGB/#RRGGBB/#AARRGGBB to (css colour, alpha)."""
    if value is None:
        return None, 1.0
    if value.startswith("?") or value.startswith("@"):
        return (tint or "currentColor"), 1.0
    v = value.lstrip("#")
    if len(v) == 3:
        v = "".join(c * 2 for c in v)
    elif len(v) == 4:
        v = "".join(c * 2 for c in v)
    if len(v) == 6:
        return "#" + v, 1.0
    if len(v) == 8:
        return "#" + v[2:], int(v[:2], 16) / 255.0
    return value, 1.0


def num(el, name, default=None):
    v = el.get(A + name)
    if v is None:
        return default
    return float(v.replace("dp", "").replace("dip", "").replace("px", ""))


class Converter:
    def __init__(self, tint=None):
        self.tint = tint
        self.defs = []
        self.ids = 0

    def new_id(self, prefix):
        self.ids += 1
        return f"{prefix}{self.ids}"

    def gradient(self, grad):
        gid = self.new_id("g")
        kind = grad.get(A + "type", "linear")
        stops = []
        items = grad.findall("item")
        if items:
            for it in items:
                c, a = color(it.get(A + "color"), self.tint)
                stops.append((float(it.get(A + "offset", "0")), c, a))
        else:
            for off, key in ((0, "startColor"), (0.5, "centerColor"), (1, "endColor")):
                if grad.get(A + key):
                    c, a = color(grad.get(A + key), self.tint)
                    stops.append((off, c, a))
        stop_xml = "".join(
            f'<stop offset="{o}" stop-color="{c}" stop-opacity="{a:.4f}"/>' for o, c, a in stops
        )
        if kind == "radial":
            cx, cy = num(grad, "centerX", 0), num(grad, "centerY", 0)
            r = num(grad, "gradientRadius", 0)
            self.defs.append(
                f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{cx}" cy="{cy}" r="{r}">{stop_xml}</radialGradient>'
            )
        else:
            x1, y1 = num(grad, "startX", 0), num(grad, "startY", 0)
            x2, y2 = num(grad, "endX", 0), num(grad, "endY", 0)
            self.defs.append(
                f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{stop_xml}</linearGradient>'
            )
        return f"url(#{gid})"

    def paint(self, el, attr):
        """Fill or stroke paint: a plain colour attribute, or an aapt:attr gradient child."""
        for child in el.findall(AAPT + "attr"):
            if child.get("name") == "android:" + attr:
                grad = child.find("gradient")
                if grad is not None:
                    return self.gradient(grad), 1.0
        return color(el.get(A + attr), self.tint)

    def path(self, el):
        d = el.get(A + "pathData", "")
        fill, fill_a = self.paint(el, "fillColor")
        stroke, stroke_a = self.paint(el, "strokeColor")
        attrs = [f'd="{d}"']
        fa = num(el, "fillAlpha", 1.0) * fill_a
        sa = num(el, "strokeAlpha", 1.0) * stroke_a
        if fill is None or fa == 0:
            attrs.append('fill="none"')
        else:
            attrs.append(f'fill="{fill}"')
            if fa < 1:
                attrs.append(f'fill-opacity="{fa:.4f}"')
        if el.get(A + "fillType") == "evenOdd":
            attrs.append('fill-rule="evenodd"')
        sw = num(el, "strokeWidth", 0)
        if stroke and sw > 0 and sa > 0:
            attrs.append(f'stroke="{stroke}" stroke-width="{sw}"')
            if sa < 1:
                attrs.append(f'stroke-opacity="{sa:.4f}"')
            cap = el.get(A + "strokeLineCap")
            join = el.get(A + "strokeLineJoin")
            if cap:
                attrs.append(f'stroke-linecap="{cap}"')
            if join:
                attrs.append(f'stroke-linejoin="{join}"')
            miter = num(el, "strokeMiterLimit")
            if miter:
                attrs.append(f'stroke-miterlimit="{miter}"')
        return "<path " + " ".join(attrs) + "/>"

    def group(self, el):
        tx, ty = num(el, "translateX", 0), num(el, "translateY", 0)
        sx, sy = num(el, "scaleX", 1), num(el, "scaleY", 1)
        rot = num(el, "rotation", 0)
        px, py = num(el, "pivotX", 0), num(el, "pivotY", 0)
        # Android applies: translate(pivot) rotate scale translate(-pivot), then translate.
        t = f"translate({tx + px} {ty + py}) rotate({rot}) scale({sx} {sy}) translate({-px} {-py})"
        inner, clip = [], None
        for child in el:
            tag = child.tag
            if tag == "clip-path":
                cid = self.new_id("c")
                self.defs.append(f'<clipPath id="{cid}"><path d="{child.get(A + "pathData", "")}"/></clipPath>')
                clip = cid
            elif tag == "path":
                inner.append(self.path(child))
            elif tag == "group":
                inner.append(self.group(child))
        body = "".join(inner)
        if clip:
            body = f'<g clip-path="url(#{clip})">{body}</g>'
        return f'<g transform="{t}">{body}</g>'

    def convert(self, root):
        vw, vh = num(root, "viewportWidth"), num(root, "viewportHeight")
        w, h = num(root, "width", vw), num(root, "height", vh)
        body = self.group(root).replace('<g transform="translate(0 0) rotate(0) scale(1 1) translate(0 0)">', "<g>", 1)
        alpha = num(root, "alpha", 1.0)
        tint, _ = color(root.get(A + "tint"), self.tint) if root.get(A + "tint") else (None, 1)
        style = f' style="color:{tint}"' if tint else ""
        opacity = f' opacity="{alpha}"' if alpha < 1 else ""
        defs = f"<defs>{''.join(self.defs)}</defs>" if self.defs else ""
        return (
            f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {vw} {vh}"{style}{opacity}>'
            f"{defs}{body}</svg>"
        )


def convert_file(src, tint=None):
    return Converter(tint).convert(ET.parse(src).getroot())


def _mul(m, n):
    """3x2 affine matrices [a, b, c, d, e, f] as in canvas setTransform: m then n applied after."""
    a, b, c, d, e, f = n
    A, B, C, D, E, F = m
    return [a * A + c * B, b * A + d * B, a * C + c * D, b * C + d * D, a * E + c * F + e, b * E + d * F + f]


def to_ops(src):
    """The drawable as drawing operations with resolved matrices, for canvas drawing in JS:
    {w, h, ops: [{d, m, fill, fa, stroke, sa, sw, cap, join, evenOdd}]}. Colours are kept (for
    untinted use); a tint replaces them, as Android's SRC_IN tint does."""
    import math
    root = ET.parse(src).getroot()
    ops = []

    def walk(el, m):
        for child in el:
            if child.tag == "group":
                tx, ty = num(child, "translateX", 0), num(child, "translateY", 0)
                sx, sy = num(child, "scaleX", 1), num(child, "scaleY", 1)
                rot = math.radians(num(child, "rotation", 0))
                px, py = num(child, "pivotX", 0), num(child, "pivotY", 0)
                g = [1, 0, 0, 1, -px, -py]
                g = _mul(g, [sx, 0, 0, sy, 0, 0])
                g = _mul(g, [math.cos(rot), math.sin(rot), -math.sin(rot), math.cos(rot), 0, 0])
                g = _mul(g, [1, 0, 0, 1, px + tx, py + ty])
                walk(child, _mul(g, m))
            elif child.tag == "path":
                fill, fa = color(child.get(A + "fillColor"))
                stroke, sa = color(child.get(A + "strokeColor"))
                for at in child.findall(AAPT + "attr"):
                    if at.get("name") == "android:fillColor":
                        fill, fa = "#000000", 1.0
                fa *= num(child, "fillAlpha", 1.0)
                sa *= num(child, "strokeAlpha", 1.0)
                ops.append({
                    "d": child.get(A + "pathData", ""), "m": [round(v, 6) for v in m],
                    "fill": fill if fill and fa > 0 else None, "fa": round(fa, 4),
                    "stroke": stroke if stroke and sa > 0 and num(child, "strokeWidth", 0) > 0 else None,
                    "sa": round(sa, 4), "sw": num(child, "strokeWidth", 0),
                    "cap": child.get(A + "strokeLineCap", "butt"), "join": child.get(A + "strokeLineJoin", "miter"),
                    "evenOdd": child.get(A + "fillType") == "evenOdd",
                })

    walk(root, [1, 0, 0, 1, 0, 0])
    return {"w": num(root, "viewportWidth"), "h": num(root, "viewportHeight"),
            "dpw": num(root, "width"), "dph": num(root, "height"), "ops": ops}


if __name__ == "__main__":
    args = sys.argv[1:]
    if args and args[0] == "--all":
        # Every drawable in the app, as SVG and as canvas ops, into build/drawable/.
        import glob, json, os
        res, out = args[1], args[2]
        os.makedirs(out, exist_ok=True)
        for f in sorted(glob.glob(os.path.join(res, "*.xml"))):
            name = os.path.basename(f)[:-4]
            try:
                root = ET.parse(f).getroot()
                if root.tag != "vector":
                    continue
                open(os.path.join(out, name + ".svg"), "w").write(convert_file(f))
                open(os.path.join(out, name + ".json"), "w").write(json.dumps(to_ops(f)))
            except ET.ParseError:
                pass
        sys.exit(0)
    tint = None
    if "--tint" in args:
        i = args.index("--tint")
        tint = args[i + 1]
        del args[i : i + 2]
    svg = convert_file(args[0], tint)
    if len(args) > 1:
        with open(args[1], "w") as f:
            f.write(svg)
    else:
        print(svg)
