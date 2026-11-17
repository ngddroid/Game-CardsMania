#!/usr/bin/env python3
"""
Cards Mania — app icon generator (stdlib only).

One geometry description -> Android vector drawables + SVG sources:
  app/src/main/res/drawable/ic_launcher_background.xml
  app/src/main/res/drawable/ic_launcher_foreground.xml
  app/src/main/res/drawable/ic_launcher_monochrome.xml
  app/src/main/res/drawable/splash_icon.xml
  tools/icon.svg                      (512x512 full-bleed, Play Store master)
  tools/build/icon_legacy_square.svg  (legacy launcher, rounded square)
  tools/build/icon_legacy_round.svg   (legacy launcher, circle)

Artwork (108x108 adaptive-icon viewport): three fanned gem cards (Sapphire,
Ruby, Emerald) with gold frames, and a faceted gold "CM" gem badge in front,
on a royal-purple radial burst. Foreground art stays inside the 66dp safe
circle centred at (54,54).

    python3 -I tools/gen_icon.py
"""
import math
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAW = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
TOOLS = os.path.join(ROOT, "tools")
BUILD = os.path.join(TOOLS, "build")

# ------------------------------------------------------------------ geometry

CX, CY = 54.0, 54.0
CARD_W, CARD_H = 24.0, 34.0
CARD_CX, CARD_CY = 54.0, 47.0
PIVOT = (54.0, 80.0)
FRAME_R = 3.4
INSET = 1.5
BODY_R = 2.2

GEMS = {
    "sapphire": ("#FF5FA8FF", "#FF1E7BFF", "#FF0A3A8C"),
    "ruby": ("#FFFF6F96", "#FFE0245E", "#FF7E0B33"),
    "emerald": ("#FF5CF0B0", "#FF19C37D", "#FF07673F"),
}
CARDS = [(-18.0, "sapphire"), (18.0, "emerald"), (0.0, "ruby")]  # draw order

BADGE_C = (54.0, 66.0)
BADGE_R = 17.0          # half diagonal
TABLE_K = 0.62          # inner table facet scale


def f(v):
    s = f"{v:.3f}".rstrip("0").rstrip(".")
    return s if s not in ("-0", "") else "0"


def rrect(x0, y0, x1, y1, r, ccw=False):
    """Rounded rectangle path using only M/L/A/Z."""
    if not ccw:
        return (f"M{f(x0 + r)},{f(y0)} L{f(x1 - r)},{f(y0)} A{f(r)},{f(r)} 0 0 1 {f(x1)},{f(y0 + r)} "
                f"L{f(x1)},{f(y1 - r)} A{f(r)},{f(r)} 0 0 1 {f(x1 - r)},{f(y1)} "
                f"L{f(x0 + r)},{f(y1)} A{f(r)},{f(r)} 0 0 1 {f(x0)},{f(y1 - r)} "
                f"L{f(x0)},{f(y0 + r)} A{f(r)},{f(r)} 0 0 1 {f(x0 + r)},{f(y0)} Z")
    return (f"M{f(x0 + r)},{f(y0)} A{f(r)},{f(r)} 0 0 0 {f(x0)},{f(y0 + r)} "
            f"L{f(x0)},{f(y1 - r)} A{f(r)},{f(r)} 0 0 0 {f(x0 + r)},{f(y1)} "
            f"L{f(x1 - r)},{f(y1)} A{f(r)},{f(r)} 0 0 0 {f(x1)},{f(y1 - r)} "
            f"L{f(x1)},{f(y0 + r)} A{f(r)},{f(r)} 0 0 0 {f(x1 - r)},{f(y0)} Z")


def poly(pts):
    return "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + " Z"


def rhombus(cx, cy, hw, hh, ccw=False):
    pts = [(cx, cy - hh), (cx + hw, cy), (cx, cy + hh), (cx - hw, cy)]
    if ccw:
        pts = [pts[0], pts[3], pts[2], pts[1]]
    return poly(pts)


def circle(cx, cy, r):
    return (f"M{f(cx - r)},{f(cy)} A{f(r)},{f(r)} 0 1 1 {f(cx + r)},{f(cy)} "
            f"A{f(r)},{f(r)} 0 1 1 {f(cx - r)},{f(cy)} Z")


# ------------------------------------------------------------------ scene model
# A tiny scene graph rendered both to VectorDrawable XML and SVG.
# node kinds:
#   ("path", d, fill, extra)   fill = "#AARRGGBB" | ("linear", x1,y1,x2,y2, stops) | ("radial", cx,cy,r, stops)
#                               extra: dict(stroke=, stroke_w=, cap=, join=, even_odd=)
#   ("group", rotation, pivot, children, clip_d)


def card_nodes(gem):
    light, base, dark = GEMS[gem]
    x0, y0 = CARD_CX - CARD_W / 2, CARD_CY - CARD_H / 2
    x1, y1 = CARD_CX + CARD_W / 2, CARD_CY + CARD_H / 2
    bx0, by0, bx1, by1 = x0 + INSET, y0 + INSET, x1 - INSET, y1 - INSET
    nodes = []
    # soft drop shadow
    nodes.append(("path", rrect(x0 + 0.7, y0 + 1.4, x1 + 0.7, y1 + 1.4, FRAME_R), "#59000000", {}))
    # gold frame
    nodes.append(("path", rrect(x0, y0, x1, y1, FRAME_R),
                  ("linear", x0, y0, x1, y1, [(0.0, "#FFFFF0B8"), (0.45, "#FFFFC93C"), (1.0, "#FFB07800")]), {}))
    # gem body
    nodes.append(("path", rrect(bx0, by0, bx1, by1, BODY_R),
                  ("linear", bx0, by0, bx1, by1, [(0.0, light), (0.5, base), (1.0, dark)]), {}))
    # faceted sheen + emblem, clipped to the body
    inner = []
    inner.append(("path", poly([(bx0, by0), (bx0 + 15.5, by0), (bx0, by0 + 19.0)]), "#30FFFFFF", {}))
    inner.append(("path", poly([(bx1, by1), (bx1 - 9.0, by1), (bx1, by1 - 12.0)]), "#26000000", {}))
    # centre rhombus emblem (white, with a gem-coloured core)
    inner.append(("path", rhombus(CARD_CX, CARD_CY, 7.2, 10.0),
                  ("linear", CARD_CX - 7, CARD_CY - 10, CARD_CX + 7, CARD_CY + 10,
                   [(0.0, "#FFFFFFFF"), (1.0, "#FFE6DCFF")]), {}))
    inner.append(("path", rhombus(CARD_CX, CARD_CY, 4.0, 5.6),
                  ("linear", CARD_CX - 4, CARD_CY - 5.6, CARD_CX + 4, CARD_CY + 5.6,
                   [(0.0, light), (1.0, dark)]), {}))
    inner.append(("path", poly([(CARD_CX, CARD_CY - 5.6), (CARD_CX + 4.0, CARD_CY), (CARD_CX, CARD_CY)]),
                  "#40FFFFFF", {}))
    # corner pips
    inner.append(("path", rhombus(bx0 + 2.9, by0 + 3.6, 1.5, 2.1), "#E6FFFFFF", {}))
    inner.append(("path", rhombus(bx1 - 2.9, by1 - 3.6, 1.5, 2.1), "#E6FFFFFF", {}))
    nodes.append(("group", 0.0, (0, 0), inner, rrect(bx0, by0, bx1, by1, BODY_R)))
    return nodes


def badge_nodes():
    cx, cy = BADGE_C
    R = BADGE_R
    t = R * TABLE_K
    o = [(cx, cy - R), (cx + R, cy), (cx, cy + R), (cx - R, cy)]
    i = [(cx, cy - t), (cx + t, cy), (cx, cy + t), (cx - t, cy)]
    nodes = []
    nodes.append(("path", circle(cx, cy, 21.0),
                  ("radial", cx, cy, 21.0, [(0.0, "#80FFC93C"), (0.55, "#33FFC93C"), (1.0, "#00FFC93C")]), {}))
    nodes.append(("path", poly([(x + 0.6, y + 1.3) for x, y in o]), "#66000000", {}))
    # crown facets (top-left, top-right, bottom-right, bottom-left)
    facet = [
        ([o[3], o[0], i[0], i[3]], "#FFFFF3C8"),
        ([o[0], o[1], i[1], i[0]], "#FFFFD45E"),
        ([o[1], o[2], i[2], i[1]], "#FFB57700"),
        ([o[2], o[3], i[3], i[2]], "#FFE29D14"),
    ]
    for pts, col in facet:
        nodes.append(("path", poly(pts), col, {}))
    # table facet
    nodes.append(("path", poly(i),
                  ("linear", cx - t, cy - t, cx + t, cy + t, [(0.0, "#FFFFF0B0"), (0.55, "#FFFFCF4A"), (1.0, "#FFF0A800")]),
                  {}))
    # outline
    nodes.append(("path", poly(o), "#00000000", {"stroke": "#FF6E4300", "stroke_w": 0.7, "join": "round"}))
    # C M monogram strokes
    royal = "#FF2A0E5C"
    cr = 3.1
    ccx = cx - 4.1
    a0, a1 = math.radians(48), math.radians(312)
    sx, sy = ccx + cr * math.cos(a0), cy - cr * math.sin(a0)
    ex, ey = ccx + cr * math.cos(a1), cy - cr * math.sin(a1)
    c_d = f"M{f(sx)},{f(sy)} A{f(cr)},{f(cr)} 0 1 0 {f(ex)},{f(ey)}"
    mx0, mx1 = cx + 0.4, cx + 6.2
    my0, my1 = cy - 3.0, cy + 3.1
    m_d = (f"M{f(mx0)},{f(my1)} L{f(mx0)},{f(my0)} L{f((mx0 + mx1) / 2)},{f(cy + 0.4)} "
           f"L{f(mx1)},{f(my0)} L{f(mx1)},{f(my1)}")
    st = {"stroke": royal, "stroke_w": 1.9, "cap": "round", "join": "round"}
    nodes.append(("path", c_d, "#00000000", st))
    nodes.append(("path", m_d, "#00000000", st))
    # sparkle on the top-left facet
    sp = (cx - 7.5, cy - 7.0)
    nodes.append(("path", poly([(sp[0], sp[1] - 2.6), (sp[0] + 0.6, sp[1] - 0.6), (sp[0] + 2.6, sp[1]),
                                 (sp[0] + 0.6, sp[1] + 0.6), (sp[0], sp[1] + 2.6), (sp[0] - 0.6, sp[1] + 0.6),
                                 (sp[0] - 2.6, sp[1]), (sp[0] - 0.6, sp[1] - 0.6)]), "#F2FFFFFF", {}))
    return nodes


def foreground_nodes():
    nodes = []
    for ang, gem in CARDS:
        nodes.append(("group", ang, PIVOT, card_nodes(gem), None))
    nodes.extend(badge_nodes())
    return nodes


def background_nodes(size=108.0, ccx=54.0, ccy=52.0):
    nodes = []
    nodes.append(("path", f"M0,0 L{f(size)},0 L{f(size)},{f(size)} L0,{f(size)} Z",
                  ("radial", ccx, ccy - 2, size * 0.68,
                   [(0.0, "#FF7B35D6"), (0.42, "#FF3E1488"), (1.0, "#FF16063D")]), {}))
    # sunburst rays
    rays = []
    n = 16
    R = size * 0.95
    for k in range(n):
        a0 = TAU_ * (k / n) - math.pi / 2
        a1 = a0 + TAU_ / (2 * n)
        rays.append(poly([(ccx, ccy), (ccx + R * math.cos(a0), ccy + R * math.sin(a0)),
                          (ccx + R * math.cos(a1), ccy + R * math.sin(a1))]))
    nodes.append(("path", " ".join(rays),
                  ("radial", ccx, ccy, size * 0.62, [(0.0, "#2EFFFFFF"), (0.5, "#14FFFFFF"), (1.0, "#00FFFFFF")]),
                  {}))
    # golden glow behind the cards
    nodes.append(("path", circle(ccx, ccy + 3, size * 0.32),
                  ("radial", ccx, ccy + 3, size * 0.32, [(0.0, "#59FFC93C"), (1.0, "#00FFC93C")]), {}))
    # subtle vignette
    nodes.append(("path", f"M0,0 L{f(size)},0 L{f(size)},{f(size)} L0,{f(size)} Z",
                  ("radial", ccx, ccy, size * 0.75, [(0.0, "#00000000"), (0.75, "#00000000"), (1.0, "#59000000")]),
                  {}))
    return nodes


TAU_ = 2 * math.pi


def mono_nodes():
    """Single-colour line-art version for Android 13 themed icons.

    A one-colour layer can't paint opaque "knockouts", so every card ring is
    clipped (nested clip groups => intersection) to exclude the badge and any
    card drawn above it.
    """
    white = "#FFFFFFFF"
    cx, cy = BADGE_C
    R = BADGE_R
    rect = "M0,0 L108,0 L108,108 L0,108 Z "
    badge_hole = rect + rhombus(cx, cy, R + 2.2, R + 2.2, ccw=True)
    out = []
    for idx, (ang, _gem) in enumerate(CARDS):
        x0, y0 = CARD_CX - CARD_W / 2, CARD_CY - CARD_H / 2
        x1, y1 = CARD_CX + CARD_W / 2, CARD_CY + CARD_H / 2
        ring = rrect(x0, y0, x1, y1, FRAME_R) + " " + rrect(x0 + 2.0, y0 + 2.0, x1 - 2.0, y1 - 2.0, BODY_R, ccw=True)
        emblem = rhombus(CARD_CX, CARD_CY, 6.0, 8.4)
        g = ("group", ang, PIVOT, [("path", ring, white, {}), ("path", emblem, white, {})], None)
        for a2, _ in CARDS[idx + 1:]:
            g = ("group", 0.0, (0, 0), [g], rect + rotated_card_ccw(a2, grow=1.6))
        g = ("group", 0.0, (0, 0), [g], badge_hole)
        out.append(g)
    o = [(cx, cy - R), (cx + R, cy), (cx, cy + R), (cx - R, cy)]
    ring_d = poly(o) + " " + rhombus(cx, cy, R - 2.4, R - 2.4, ccw=True)
    out.append(("path", ring_d, white, {}))
    for node in badge_nodes():
        if node[0] == "path" and node[3].get("cap") == "round":
            out.append(("path", node[1], "#00000000", dict(node[3], stroke=white, stroke_w=2.3)))
    return out


def rotated_card_ccw(ang, grow=0.0):
    """Outline of a rotated card as an explicit CCW polygon (rounded corners approximated)."""
    x0, y0 = CARD_CX - CARD_W / 2 - grow, CARD_CY - CARD_H / 2 - grow
    x1, y1 = CARD_CX + CARD_W / 2 + grow, CARD_CY + CARD_H / 2 + grow
    pts = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    a = math.radians(ang)
    px, py = PIVOT
    rot = []
    for x, y in pts:
        dx, dy = x - px, y - py
        rot.append((px + dx * math.cos(a) - dy * math.sin(a), py + dx * math.sin(a) + dy * math.cos(a)))
    rot.reverse()  # CCW in screen space
    return poly(rot)


# ------------------------------------------------------------------ VectorDrawable writer

def vd_fill(fill, ind):
    if isinstance(fill, str):
        return f' android:fillColor="{fill}"', ""
    kind = fill[0]
    pad = " " * ind
    if kind == "linear":
        _, x1, y1, x2, y2, stops = fill
        head = (f'{pad}<aapt:attr name="android:fillColor">\n'
                f'{pad}    <gradient android:type="linear" android:startX="{f(x1)}" android:startY="{f(y1)}" '
                f'android:endX="{f(x2)}" android:endY="{f(y2)}">\n')
    else:
        _, cx, cy, r, stops = fill
        head = (f'{pad}<aapt:attr name="android:fillColor">\n'
                f'{pad}    <gradient android:type="radial" android:centerX="{f(cx)}" android:centerY="{f(cy)}" '
                f'android:gradientRadius="{f(r)}">\n')
    items = "".join(f'{pad}        <item android:offset="{f(o)}" android:color="{c}" />\n' for o, c in stops)
    tail = f"{pad}    </gradient>\n{pad}</aapt:attr>\n"
    return "", head + items + tail


def vd_node(node, ind):
    pad = " " * ind
    if node[0] == "path":
        _, d, fill, extra = node
        attr, child = vd_fill(fill, ind + 4)
        s = f'{pad}<path\n{pad}    android:pathData="{d}"{attr}'
        if extra.get("stroke"):
            s += f'\n{pad}    android:strokeColor="{extra["stroke"]}" android:strokeWidth="{f(extra["stroke_w"])}"'
            if extra.get("cap"):
                s += f' android:strokeLineCap="{extra["cap"]}"'
            if extra.get("join"):
                s += f' android:strokeLineJoin="{extra["join"]}"'
        if child:
            return s + ">\n" + child + f"{pad}</path>\n"
        return s + " />\n"
    _, rot, pivot, children, clip = node
    s = f"{pad}<group"
    if rot:
        s += f' android:rotation="{f(rot)}" android:pivotX="{f(pivot[0])}" android:pivotY="{f(pivot[1])}"'
    s += ">\n"
    if clip:
        s += f'{pad}    <clip-path android:pathData="{clip}" />\n'
    for c in children:
        s += vd_node(c, ind + 4)
    return s + f"{pad}</group>\n"


def write_vd(path, nodes, size=108, wrap=None):
    body = "".join(vd_node(n, 4) for n in nodes)
    if wrap:
        body = wrap(body)
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Generated by tools/gen_icon.py — edit the script, not this file. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           f'    android:width="{size}dp"\n    android:height="{size}dp"\n'
           f'    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
           f"{body}</vector>\n")
    with open(path, "w") as fh:
        fh.write(xml)


# ------------------------------------------------------------------ SVG writer

class SvgCtx:
    def __init__(self):
        self.defs = []
        self.n = 0

    def uid(self, p):
        self.n += 1
        return f"{p}{self.n}"


def svg_color(c):
    # #AARRGGBB -> (rgb, opacity)
    a = int(c[1:3], 16) / 255.0
    return "#" + c[3:], a


def svg_fill(ctx, fill):
    if isinstance(fill, str):
        rgb, a = svg_color(fill)
        return f'fill="{rgb}" fill-opacity="{a:.3f}"'
    gid = ctx.uid("g")
    if fill[0] == "linear":
        _, x1, y1, x2, y2, stops = fill
        g = f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{f(x1)}" y1="{f(y1)}" x2="{f(x2)}" y2="{f(y2)}">'
    else:
        _, cx, cy, r, stops = fill
        g = f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{f(cx)}" cy="{f(cy)}" r="{f(r)}">'
    for o, c in stops:
        rgb, a = svg_color(c)
        g += f'<stop offset="{f(o)}" stop-color="{rgb}" stop-opacity="{a:.3f}"/>'
    g += "</linearGradient>" if fill[0] == "linear" else "</radialGradient>"
    ctx.defs.append(g)
    return f'fill="url(#{gid})"'


def svg_node(ctx, node):
    if node[0] == "path":
        _, d, fill, extra = node
        s = f'<path d="{d}" {svg_fill(ctx, fill)}'
        if extra.get("stroke"):
            rgb, a = svg_color(extra["stroke"])
            s += f' stroke="{rgb}" stroke-opacity="{a:.3f}" stroke-width="{f(extra["stroke_w"])}"'
            if extra.get("cap"):
                s += f' stroke-linecap="{extra["cap"]}"'
            if extra.get("join"):
                s += f' stroke-linejoin="{extra["join"]}"'
        return s + "/>"
    _, rot, pivot, children, clip = node
    attrs = ""
    if rot:
        attrs += f' transform="rotate({f(rot)} {f(pivot[0])} {f(pivot[1])})"'
    if clip:
        cid = ctx.uid("c")
        ctx.defs.append(f'<clipPath id="{cid}"><path d="{clip}" clip-rule="nonzero"/></clipPath>')
        attrs += f' clip-path="url(#{cid})"'
    return f"<g{attrs}>" + "".join(svg_node(ctx, c) for c in children) + "</g>"


def write_svg(path, layers, px=512, shape=None, fg_scale=1.0):
    """layers: list of node lists in 108-space. shape: None (full bleed) | 'rsquare' | 'circle'."""
    ctx = SvgCtx()
    k = px / 108.0
    inner = ""
    for i, nodes in enumerate(layers):
        content = "".join(svg_node(ctx, n) for n in nodes)
        if i > 0 and fg_scale != 1.0:
            content = (f'<g transform="translate(54 54) scale({fg_scale}) translate(-54 -54)">' + content + "</g>")
        inner += content
    body = f'<g transform="scale({k:.6f})">{inner}</g>'
    if shape:
        cid = ctx.uid("m")
        if shape == "circle":
            mask = circle(54, 54, 54 * 0.92)
        else:
            m = 108 * 0.04
            mask = rrect(m, m, 108 - m, 108 - m, 108 * 0.2)
        ctx.defs.append(f'<clipPath id="{cid}"><path d="{mask}"/></clipPath>')
        body = f'<g transform="scale({k:.6f})"><g clip-path="url(#{cid})">{inner}</g></g>'
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{px}" height="{px}" viewBox="0 0 {px} {px}">'
           f'<defs>{"".join(ctx.defs)}</defs>{body}</svg>\n')
    with open(path, "w") as fh:
        fh.write(svg)


# ------------------------------------------------------------------ main

def main():
    os.makedirs(BUILD, exist_ok=True)
    fg = foreground_nodes()
    bg = background_nodes()
    write_vd(os.path.join(DRAW, "ic_launcher_background.xml"), bg)
    write_vd(os.path.join(DRAW, "ic_launcher_foreground.xml"), fg)
    write_vd(os.path.join(DRAW, "ic_launcher_monochrome.xml"), mono_nodes())

    # Splash: burst disc (r=36, the visible splash circle) + artwork scaled to fit inside it.
    disc_clip = circle(54, 54, 36)
    splash_bg = ("group", 0.0, (0, 0), background_nodes(), disc_clip)

    body = vd_node(splash_bg, 4)
    body += ('    <group android:scaleX="1.0" android:scaleY="1.0" android:pivotX="54" android:pivotY="54">\n'
             + "".join(vd_node(n, 8) for n in fg) + "    </group>\n")
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Generated by tools/gen_icon.py — edit the script, not this file. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           '    android:width="108dp"\n    android:height="108dp"\n'
           '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
           f"{body}</vector>\n")
    with open(os.path.join(DRAW, "splash_icon.xml"), "w") as fh:
        fh.write(xml)

    write_svg(os.path.join(TOOLS, "icon.svg"), [bg, fg], px=512, fg_scale=1.28)
    write_svg(os.path.join(BUILD, "icon_legacy_square.svg"), [bg, fg], px=512, shape="rsquare", fg_scale=1.32)
    write_svg(os.path.join(BUILD, "icon_legacy_round.svg"), [bg, fg], px=512, shape="circle", fg_scale=1.3)
    write_svg(os.path.join(BUILD, "icon_monochrome_preview.svg"), [mono_nodes()], px=512)
    write_svg(os.path.join(BUILD, "icon_adaptive_preview.svg"), [bg, fg], px=512, shape="circle", fg_scale=1.0)
    print("icons written")


if __name__ == "__main__":
    main()
