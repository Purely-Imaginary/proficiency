"""The Tempo flames: an animated pixel-art strip that burns on top of the HUD skill bar.

One PNG, textures/gui/tempo_flames.png, read by client/TempoBadge on every line:

    rows      0-4  heat 1 (embers) to heat 5 (the cap, yellow-white), 6 the flare, 6 smoke (see ROWS)
    each row  a block STRIP_W wide at x = row * BLOCK_W, FRAMES frames stacked down, FRAME_H tall each
    glyphs    at y = GLYPH_Y: "x", "0" to "9", "." in a 3x5 pixel font, GLYPH_ADV apart, white (tinted in game)

The strip is as wide as the bar (70). The HUD blits only its right end, as long as the bar's fill, so the
tapered right end always sits on the fill edge. The bottom row of a frame lies over the bar's top row
(a soft glow: the fire comes out of the bar); the other 7 rows rise above it.

Everything loops over FRAMES (sines with whole periods), so the game picks a frame from the clock and the
animation is the same at 30 or 240 fps. No anti-aliasing: every pixel is one solid colour.

    python3 tools/gen_tempo_flames.py                 # writes the PNG
    python3 tools/gen_tempo_flames.py --sheet out.png  # also an 8x preview of every row and frame
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/proficiency/textures/gui/tempo_flames.png")

# Keep in step with client/TempoBadge (both lines) and core client/TempoFlames.
STRIP_W = 70
BLOCK_W = 72
FRAME_H = 8
FRAMES = 16
ROWS = ["heat1", "heat2", "heat3", "heat4", "heat5", "flare", "smoke"]
GLYPH_Y = FRAMES * FRAME_H
GLYPH_ADV = 4
TEX_W = 512
TEX_H = GLYPH_Y + 8

TAU = math.tau


def rgb(v, a=255):
    return ((v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF, a)


# Outer edge first, hottest core last.
PALETTES = {
    "heat2": [0xD4401C, 0xF27422, 0xFF9C30],
    "heat3": [0xE2541E, 0xFF8826, 0xFFAE38, 0xFFCE50],
    "heat4": [0xEE6A22, 0xFF9A2C, 0xFFC03E, 0xFFDE66, 0xFFF0A0],
    "heat5": [0xF8862A, 0xFFB038, 0xFFD452, 0xFFEC8A, 0xFFF8D0, 0xFFFFF4],
    "flare": [0xFFA238, 0xFFC850, 0xFFE27E, 0xFFF2B4, 0xFFFAE0],
}
# Tongue heights (rows above the bar), lowest and highest, and how often the bed between tongues burns.
SHAPES = {
    "heat2": dict(lo=0.6, hi=2.6, bed=0, gap=4, width=1.2, sparks=0),
    "heat3": dict(lo=1.5, hi=4.6, bed=1, gap=4, width=1.6, sparks=1),
    "heat4": dict(lo=2.4, hi=6.2, bed=1, gap=4, width=1.8, sparks=2),
    "heat5": dict(lo=3, hi=7.4, bed=2, gap=4, width=2.0, sparks=3),
    "flare": dict(lo=4.5, hi=7.6, bed=2, gap=3.5, width=2.2, sparks=5),
}


def tongues(rng, gap):
    """Tongue centres along the strip with their own phase, speed and sway."""
    out = []
    x = rng.uniform(0, gap)
    while x < STRIP_W + 2:
        out.append(dict(x=x, ph=rng.uniform(0, TAU), k=rng.choice([1, 1, 2]), sway=rng.uniform(0, TAU),
                        sk=rng.choice([1, 2]), size=rng.uniform(0.75, 1.0)))
        x += gap + rng.uniform(-1.0, 1.5)
    return out


def taper(x):
    """The right end is the fill edge: the last columns burn lower, so the fire ends in a point."""
    from_end = STRIP_W - 1 - x
    return [0.35, 0.55, 0.75, 0.9][from_end] if from_end < 4 else 1.0


def flame_frame(name, t, rng_seed):
    shape = SHAPES[name]
    pal = PALETTES[name]
    rng = random.Random(rng_seed)
    ts = tongues(rng, shape["gap"])
    heights = []
    for x in range(STRIP_W):
        h = 0.0
        for tg in ts:
            cx = tg["x"] + 0.8 * math.sin(TAU * tg["sk"] * t / FRAMES + tg["sway"])
            hh = shape["lo"] + (shape["hi"] - shape["lo"]) * (0.5 + 0.5 * math.sin(TAU * tg["k"] * t / FRAMES + tg["ph"]))
            hh *= tg["size"]
            d = abs(x - cx) / shape["width"]
            if d < 1.6:
                h = max(h, hh * max(0.0, 1.0 - (d / 1.6) ** 1.6))
        bed = shape["bed"] if (x * 7 + t) % 5 else max(0, shape["bed"] - 1)
        h = max(h, bed)
        heights.append(max(0, min(7, int(round(h * taper(x))))))
    # Solid map: rows 0..6 above the bar (6 is the lowest), row 7 the glow on the bar's top row.
    solid = [[False] * STRIP_W for _ in range(FRAME_H)]
    for x, h in enumerate(heights):
        for r in range(h):
            solid[6 - r][x] = True
        solid[7][x] = h > 0
    # Layers: distance (4-neighbour) to an empty pixel, the bar below counting as fire.
    dist = [[0] * STRIP_W for _ in range(FRAME_H)]
    for y in range(FRAME_H):
        for x in range(STRIP_W):
            if not solid[y][x]:
                continue
            best = 9
            for dy in range(-4, 5):
                for dx in range(-4, 5):
                    yy, xx = y + dy, x + dx
                    if abs(dx) + abs(dy) >= best:
                        continue
                    if yy >= FRAME_H or xx < 0 or xx >= STRIP_W:
                        continue
                    if yy < 0 or not solid[yy][xx]:
                        best = abs(dx) + abs(dy)
            dist[y][x] = best
    img = Image.new("RGBA", (STRIP_W, FRAME_H), (0, 0, 0, 0))
    px = img.load()
    for y in range(FRAME_H):
        for x in range(STRIP_W):
            if solid[y][x]:
                # Hotter towards the bar: the bottom two rows gain a layer.
                layer = dist[y][x] - 1 + (1 if y == 7 else 0)
                c = pal[max(0, min(len(pal) - 1, layer))]
                px[x, y] = rgb(c, 150 if y == 7 else 255)
    # Sparks: a pixel breaks off a tongue's tip and rises, one row per frame, for three frames.
    srng = random.Random(rng_seed * 31 + 7)
    for i in range(shape["sparks"] * 4):
        sx = srng.randrange(2, STRIP_W - 3)
        start = srng.randrange(FRAMES)
        age = (t - start) % FRAMES
        if age >= 3:
            continue
        top = 6 - heights[sx]
        y = top - age - 1
        x = sx + (1 if (age == 2 and i % 2) else 0)
        if 0 <= y < 7 and not solid[y][x]:
            px[x, y] = rgb(pal[min(len(pal) - 1, 2 + (1 if age == 0 else 0))], 255 if age < 2 else 190)
    return img


def ember_frame(t, seed):
    """Heat 1: a few small embers on the bar, each glowing up and down on its own beat, one drifting up."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (STRIP_W, FRAME_H), (0, 0, 0, 0))
    px = img.load()
    ramp = [0x6E1A0C, 0xA82A14, 0xDC4E1A, 0xFF8226, 0xFFB040]
    x = rng.randrange(1, 4)
    while x < STRIP_W - 1:
        ph = rng.uniform(0, TAU)
        k = rng.choice([1, 2, 2, 3])
        b = 0.5 + 0.5 * math.sin(TAU * k * t / FRAMES + ph)
        if b > 0.2:
            c = ramp[min(4, int(b * 5))]
            px[x, 6] = rgb(c)
            px[x, 7] = rgb(ramp[max(0, min(4, int(b * 5)) - 1)], 140)
            if b > 0.8 and rng.random() < 0.6:
                px[x, 5] = rgb(ramp[2])
        x += rng.randrange(4, 8)
    # One ember lifts off every so often and cools as it rises.
    for i, sx in enumerate(range(9, STRIP_W - 4, 23)):
        age = (t + i * 5) % FRAMES
        if age < 4:
            y = 5 - age
            px[sx + (age // 2) * (1 if i % 2 else -1), y] = rgb(ramp[3 - min(3, age)])
    return img


def smoke_frame(t, seed):
    """What is left when the fire dies: dull embers on the bar and wisps that rise and curl, paler higher up."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (STRIP_W, FRAME_H), (0, 0, 0, 0))
    px = img.load()
    greys = [0x77726D, 0x8E8984, 0xA7A29D, 0xC2BDB8]
    x = rng.randrange(1, 4)
    while x < STRIP_W - 1:
        ph = rng.randrange(FRAMES)
        curl = rng.uniform(0, TAU)
        # The head climbs one row every two frames from the bar to the top; a trail of three follows it.
        head = ((t + ph) % FRAMES) // 2
        for i in range(4):
            rise = head - i
            if rise < 0 or rise > 6:
                continue
            y = 6 - rise
            xx = x + int(round(math.sin(curl + rise * 0.8) * 1.3))
            if 0 <= xx < STRIP_W:
                g = greys[min(3, rise // 2)]
                px[xx, y] = rgb(g, max(70, 235 - rise * 18 - i * 35))
        x += rng.randrange(3, 6)
    # Dull embers still glowing on the bar.
    x = rng.randrange(2, 6)
    while x < STRIP_W - 1:
        if (t // 3 + x) % 4:
            px[x, 7] = rgb(0x7A2410, 200)
            px[x, 6] = rgb(0x5A1A0C, 150)
        x += rng.randrange(6, 11)
    return img


GLYPHS = {
    "x": ["...", "...", "x.x", ".x.", "x.x"],
    "0": ["xxx", "x.x", "x.x", "x.x", "xxx"],
    "1": [".x.", "xx.", ".x.", ".x.", "xxx"],
    "2": ["xxx", "..x", "xxx", "x..", "xxx"],
    "3": ["xxx", "..x", ".xx", "..x", "xxx"],
    "4": ["x.x", "x.x", "xxx", "..x", "..x"],
    "5": ["xxx", "x..", "xxx", "..x", "xxx"],
    "6": ["xxx", "x..", "xxx", "x.x", "xxx"],
    "7": ["xxx", "..x", ".x.", ".x.", ".x."],
    "8": ["xxx", "x.x", "xxx", "x.x", "xxx"],
    "9": ["xxx", "x.x", "xxx", "..x", "xxx"],
    ".": ["...", "...", "...", "...", "x.."],
}
GLYPH_ORDER = "x0123456789."


def build():
    tex = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    for r, name in enumerate(ROWS):
        for f in range(FRAMES):
            if name == "heat1":
                frame = ember_frame(f, 11)
            elif name == "smoke":
                frame = smoke_frame(f, 17)
            else:
                frame = flame_frame(name, f, 100 + r)
            tex.paste(frame, (r * BLOCK_W, f * FRAME_H))
    px = tex.load()
    for i, ch in enumerate(GLYPH_ORDER):
        for y, line in enumerate(GLYPHS[ch]):
            for x, c in enumerate(line):
                if c == "x":
                    px[i * GLYPH_ADV + x, GLYPH_Y + y] = (255, 255, 255, 255)
    return tex


def sheet(tex, path, scale=8):
    """Every row's frames side by side over a dark bar, scaled up, to judge the art."""
    cols = 4
    pad = 4
    cell_w = STRIP_W + pad
    cell_h = FRAME_H + 3 + pad
    out = Image.new("RGBA", (cols * cell_w, len(ROWS) * (FRAMES // cols) * cell_h), (58, 52, 48, 255))
    for r in range(len(ROWS)):
        for f in range(FRAMES):
            cx = (f % cols) * cell_w
            cy = (r * (FRAMES // cols) + f // cols) * cell_h
            bar = Image.new("RGBA", (STRIP_W, 2), (122, 196, 90, 255))
            out.paste(bar, (cx, cy + FRAME_H - 1))
            frame = tex.crop((r * BLOCK_W, f * FRAME_H, r * BLOCK_W + STRIP_W, (f + 1) * FRAME_H))
            out.alpha_composite(frame, (cx, cy))
    out = out.resize((out.width * scale, out.height * scale), Image.NEAREST)
    out.save(path)


if __name__ == "__main__":
    tex = build()
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    tex.save(OUT)
    print("wrote", OUT)
    if "--sheet" in sys.argv:
        p = sys.argv[sys.argv.index("--sheet") + 1]
        sheet(tex, p)
        print("wrote", p)
