#!/usr/bin/env python3
"""
Builds every app icon from one picture: tools/icon_source.png (512 x 512, full-bleed artwork).

    python3 tools/make_icon.py

Writes
  app/src/main/res/mipmap-<density>/ic_launcher_foreground.png   adaptive icon: the artwork, edges feathered
  app/src/main/res/mipmap-<density>/ic_launcher_background.png   adaptive icon: purple glow behind it
  app/src/main/res/mipmap-<density>/ic_launcher_monochrome.png   Android 13 themed icon (white shape, tinted by Android)
  app/src/main/res/mipmap-<density>/ic_launcher.png              Android 7 (rounded square)
  app/src/main/res/mipmap-<density>/ic_launcher_round.png        Android 7 (circle)
  tools/play_store_icon_512.png                                   Play Console icon (the artwork as it is)
  app/src/main/assets/legal/app_logo.png                         website logo (256 x 256)
  tools/build/icon_preview.png                                    how it looks in circle / squircle / square masks

Adaptive icons are 108 units with only the middle 72 visible (a circle on some phones). The artwork's cards and text
reach about 270 px from its centre, so it is drawn 64 units wide: everything stays inside even a circular mask.
"""
import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
RES = os.path.join(ROOT, "app", "src", "main", "res")
SRC = os.path.join(TOOLS, "icon_source.png")

DENSITIES = {"mdpi": 1.0, "hdpi": 1.5, "xhdpi": 2.0, "xxhdpi": 3.0, "xxxhdpi": 4.0}
ART_UNITS = 64.0          # artwork width inside the 108-unit adaptive canvas
CANVAS = 922              # master canvas (px): the 512 px artwork at its own size spans 64 of 108 units
FEATHER = 24              # px of the artwork's edge faded into the background (its cards and text start 44+ px in)
BG_CENTER = (60, 3, 108)  # glow behind the artwork, matched to the artwork's own edge colours
BG_EDGE = (34, 2, 70)


def radial_bg(size):
    """The purple glow: lighter in the middle, darker at the edge, like the artwork's vignette."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    c = size / 2
    for y in range(size):
        for x in range(size):
            t = min(1.0, math.hypot(x - c, y - c) / (size / 2))
            px[x, y] = tuple(round(BG_CENTER[i] + (BG_EDGE[i] - BG_CENTER[i]) * t) for i in range(3))
    return img


def feathered(art):
    """The artwork with its outer FEATHER px fading to transparent, so it melts into the background."""
    w, h = art.size
    mask = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(mask)
    for i in range(FEATHER):
        v = round(255 * ((i + 1) / FEATHER) ** 1.5)
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=v)
    d.rectangle([FEATHER, FEATHER, w - 1 - FEATHER, h - 1 - FEATHER], fill=255)
    out = art.convert("RGBA")
    out.putalpha(ImageChops.multiply(out.getchannel("A"), mask))
    return out


def on_canvas(layer, art_units=ART_UNITS, size=CANVAS):
    """[layer] (artwork-sized) centred on a transparent 108-unit canvas, [art_units] wide."""
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    w = round(size * art_units / 108)
    canvas.alpha_composite(layer.resize((w, w), Image.LANCZOS), ((size - w) // 2, (size - w) // 2))
    return canvas


def drop_specks(mask, min_area):
    """Removes white blobs smaller than [min_area] px (bits of light rays and sparkles)."""
    w, h = mask.size
    px = mask.load()
    seen = bytearray(w * h)
    out = mask.copy()
    opx = out.load()
    for y in range(h):
        for x in range(w):
            if px[x, y] < 128 or seen[y * w + x]:
                continue
            stack, blob = [(x, y)], []
            seen[y * w + x] = 1
            while stack:
                cx, cy = stack.pop()
                blob.append((cx, cy))
                for nx, ny in ((cx + 1, cy), (cx - 1, cy), (cx, cy + 1), (cx, cy - 1)):
                    if 0 <= nx < w and 0 <= ny < h and not seen[ny * w + nx] and px[nx, ny] >= 128:
                        seen[ny * w + nx] = 1
                        stack.append((nx, ny))
            if len(blob) < min_area:
                for bx, by in blob:
                    opx[bx, by] = 0
    return out


def monochrome_shape(art):
    """White where the artwork has cards, the CM gem and the lettering; holes for the card symbols and outlines."""
    rgb = art.convert("RGB")
    r, g, b = rgb.split()
    # not the purple background: clearly green (white, gold, yellow) or clearly red (red cards and letters)
    greenish = g.point(lambda v: 255 if v > 60 else 0)
    reddish = ImageChops.subtract(r, b).point(lambda v: 255 if v > 40 else 0)
    shape = ImageChops.lighter(greenish, reddish)
    # thin light rays go (a light opening keeps the CM letters and card symbols), then leftover specks
    shape = shape.filter(ImageFilter.MinFilter(5)).filter(ImageFilter.MaxFilter(5))
    shape = drop_specks(shape, min_area=400)
    shape = shape.filter(ImageFilter.GaussianBlur(0.8))
    out = Image.new("RGBA", art.size, (255, 255, 255, 0))
    out.putalpha(shape)
    return out


def masked(img, shape):
    """[img] cut to a launcher [shape]: circle, squircle or rounded square."""
    size = img.size[0]
    m = Image.new("L", (size * 4, size * 4), 0)
    d = ImageDraw.Draw(m)
    if shape == "circle":
        d.ellipse([0, 0, size * 4 - 1, size * 4 - 1], fill=255)
    elif shape == "squircle":
        n, c = 4.0, size * 2
        pts = [(c + c * math.copysign(abs(math.cos(t)) ** (2 / n), math.cos(t)),
                c + c * math.copysign(abs(math.sin(t)) ** (2 / n), math.sin(t))) for t in [i * math.pi / 180 for i in range(360)]]
        d.polygon(pts, fill=255)
    else:
        d.rounded_rectangle([0, 0, size * 4 - 1, size * 4 - 1], radius=size * 4 * 0.18, fill=255)
    out = img.convert("RGBA")
    out.putalpha(ImageChops.multiply(out.getchannel("A"), m.resize((size, size), Image.LANCZOS)))
    return out


def visible(layer_canvas):
    """The middle 72 of 108 units: what a launcher shows of an adaptive layer."""
    s = layer_canvas.size[0]
    cut = round(s * 18 / 108)
    return layer_canvas.crop((cut, cut, s - cut, s - cut))


def main():
    art = Image.open(SRC).convert("RGBA")
    assert art.size == (512, 512), "icon_source.png must be 512 x 512"

    fg = on_canvas(feathered(art))
    bg = radial_bg(CANVAS).convert("RGBA")
    mono = on_canvas(monochrome_shape(art), art_units=ART_UNITS * 0.92)
    full = bg.copy()
    full.alpha_composite(fg)

    for name, scale in DENSITIES.items():
        d = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        layer = round(108 * scale)
        fg.resize((layer, layer), Image.LANCZOS).save(os.path.join(d, "ic_launcher_foreground.png"), optimize=True)
        bg.resize((layer, layer), Image.LANCZOS).convert("RGB").save(os.path.join(d, "ic_launcher_background.png"), optimize=True)
        mono.resize((layer, layer), Image.LANCZOS).save(os.path.join(d, "ic_launcher_monochrome.png"), optimize=True)
        legacy = round(48 * scale)
        # Android 7: the artwork itself in a rounded square; the circle uses the safe-zone version
        masked(art.resize((legacy, legacy), Image.LANCZOS), "rsquare").save(os.path.join(d, "ic_launcher.png"), optimize=True)
        masked(visible(full).resize((legacy, legacy), Image.LANCZOS), "circle").save(os.path.join(d, "ic_launcher_round.png"), optimize=True)

    art.convert("RGB").save(os.path.join(TOOLS, "play_store_icon_512.png"), optimize=True)
    art.resize((256, 256), Image.LANCZOS).save(os.path.join(ROOT, "app", "src", "main", "assets", "legal", "app_logo.png"), optimize=True)

    # preview: circle, squircle, rounded square, themed (monochrome tinted)
    os.makedirs(os.path.join(TOOLS, "build"), exist_ok=True)
    tile = 256
    sheet = Image.new("RGBA", (tile * 4 + 50, tile + 20), (235, 235, 240, 255))
    v = visible(full).resize((tile, tile), Image.LANCZOS)
    tinted = Image.new("RGBA", (tile, tile), (210, 225, 255, 255))
    vm = visible(mono).resize((tile, tile), Image.LANCZOS)
    tinted.alpha_composite(Image.new("RGBA", (tile, tile), (30, 60, 120, 255)), (0, 0))
    tinted = Image.composite(Image.new("RGBA", (tile, tile), (30, 60, 120, 255)), Image.new("RGBA", (tile, tile), (210, 225, 255, 255)), vm.getchannel("A"))
    for i, (img, shape) in enumerate([(v, "circle"), (v, "squircle"), (v, "rsquare"), (tinted, "circle")]):
        sheet.alpha_composite(masked(img, shape), (10 + i * (tile + 10), 10))
    sheet.save(os.path.join(TOOLS, "build", "icon_preview.png"))
    print("icons written")


if __name__ == "__main__":
    main()
