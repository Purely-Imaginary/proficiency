"""Pixel art and model JSON for the Hearth, the Siege Trophy, the Siege Horn, the HUD glyphs and the GUI icons.

Everything is generated, nothing is hand-edited: a changed number here and a rerun rewrites the
PNGs, the .mcmeta files, the block models, the blockstates and the font provider list. The
Homebuilder skill icon is not here, it is a map in gen_skill_icons.py like every other skill.

    python3 tools/gen_hearth_art.py                      # write the assets
    python3 tools/gen_hearth_art.py --sheet out.png      # also the 8x contact sheet (renders the shipped models)
    python3 tools/gen_hearth_art.py --icon-sheet out.png # also the icon sheet: every GUI icon beside 8 skill icons at 1x, 2x, 4x

Ids and properties are the contract in docs/HEARTH-CONTRACT.md (the foundation lane registers them):

    proficiency:hearth         stage = 1..6, smouldering = true|false      (no item)
    proficiency:siege_trophy   tier = 3..6, facing = north|south|east|west  (+ BlockItem)
    proficiency:siege_horn     item
    glyphs U+E010..U+E018 Hearth Warmth frames 0..8, U+E019 siege, U+E01A Tales scroll,
           U+E01B Provisioned, U+E01C lock, U+E01D bell, U+E01E house
    GUI icons (16x16, textures/gui/hearth/, the paths the client lane draws through SkillIcons-style
           lookups): pool/<hearty|regen|fortified|workshop|homecoming>, comfort/<12 kinds>, stage/<1..6>,
           and warmth, siege, tales, provisioned, character, comfort, ledger, bell, horn, trophy.
           Their pixel maps are in tools/hearth_icon_maps.py; horn and trophy are the item sprites.

Rules the art follows: 16 px textures, light from the top left, 3 to 4 shades per material, no
anti-aliasing, transparent pixels are fully transparent. Flames are drawn on crossed planes (like the
vanilla campfire) with 4 animation frames; the particle work is code, never art. The flame planes need
a cutout render layer: the models carry "render_type": "minecraft:cutout" for NeoForge and Forge, and
Fabric needs BlockRenderLayerMap (see docs/hearth-build-log/art.md).
"""
import json
import math
import os
import random
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_skill_icons  # noqa: E402  (the skill icons: palette(), render(), read_categories())
from gen_skill_icons import BASE, outline  # noqa: E402  (the shared material ramps, the shared 1 px rim)
from hearth_icon_maps import ACCENT as ICON_ACCENT, MAPS as ICON_MAPS, NO_OUTLINE as ICON_BARE  # noqa: E402
from model_preview import FACE_NORMAL, FACE_VERTS, ModelRenderer  # noqa: E402  (vanilla face and rotation rules)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "common/src/main/resources/assets")
PROF = os.path.join(ASSETS, "proficiency")
BLOCK_TEX = os.path.join(PROF, "textures/block")
ITEM_TEX = os.path.join(PROF, "textures/item")
FONT_TEX = os.path.join(PROF, "textures/font")
GUI_TEX = os.path.join(PROF, "textures/gui/hearth")
SKILL_TEX = os.path.join(PROF, "textures/gui/skill")
MODELS = os.path.join(PROF, "models")
STATES = os.path.join(PROF, "blockstates")
FONT_JSON = os.path.join(ASSETS, "minecraft/font/default.json")

STAGES = range(1, 7)
TIERS = range(3, 7)
FACINGS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))


# ---------------------------------------------------------------------------------------
# small helpers
# ---------------------------------------------------------------------------------------
def rgb(name):
    return BASE[name]


def mix(a, b, t):
    return tuple(round(a[i] * (1 - t) + b[i] * t) for i in range(3))


def shade(c, f):
    """Scale a colour: f < 1 darker, f > 1 lighter (towards white)."""
    if f <= 1:
        return tuple(max(0, min(255, round(v * f))) for v in c)
    return mix(c, (255, 255, 255), f - 1)


def write_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)


def write_json(obj, path, ascii_only=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=2, ensure_ascii=ascii_only)
        fh.write("\n")


def write_mcmeta(path, **anim):
    write_json({"animation": anim}, path + ".mcmeta")


def paint_map(rows, pal, rim=None):
    """Char map to RGBA image. '.' is clear. `rim` (a colour) outlines the shape, like the skill icons."""
    h, w = len(rows), len(rows[0])
    assert all(len(r) == w for r in rows), [len(r) for r in rows]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), tuple(pal[ch]) + (255,))
    if rim:
        outline(img, rim)
    return img


# ---------------------------------------------------------------------------------------
# materials: stone, brick, bark, coal
# ---------------------------------------------------------------------------------------
class Mat:
    """Colours of one collar material: light, mid, dark and the mortar between the stones."""

    def __init__(self, light, mid, dark, mortar):
        self.light, self.mid, self.dark, self.mortar = light, mid, dark, mortar


GOLD = (rgb("G"), rgb("g"), rgb("h"))        # light, mid, dark
IRON = (rgb("i"), rgb("j"), rgb("k"))
GEM = ((170, 250, 240), (84, 214, 206), (34, 130, 140))   # diamond cyan

MATS = {
    1: Mat((150, 148, 144), (118, 116, 112), (84, 82, 80), (56, 54, 54)),
    2: Mat((190, 112, 88), (156, 82, 64), (118, 58, 46), (184, 176, 164)),
    3: Mat((170, 170, 168), (136, 136, 134), (100, 100, 100), (70, 70, 72)),
    4: Mat((176, 168, 152), (144, 136, 120), (108, 100, 88), (74, 68, 60)),
    5: Mat((98, 100, 116), (70, 72, 88), (48, 50, 62), (30, 30, 40)),
    6: Mat((74, 66, 84), (52, 46, 62), (34, 30, 42), (20, 18, 26)),
}
# wall height of the collar, top of the corner posts, in model units
COLLAR_H = {1: 4, 2: 5, 3: 5, 4: 6, 5: 6, 6: 7}
POST_H = {1: 0, 2: 0, 3: 9, 4: 10, 5: 12, 6: 13}     # top of the corner posts; they start on top of the collar wall


def cobble(mat, seed, w=16, h=16):
    """Rounded field stones: a toroidal Voronoi with dark mortar and a top-left light."""
    rnd = random.Random(seed)
    pts = [(rnd.uniform(0, w), rnd.uniform(0, h)) for _ in range(8)]
    tone = [rnd.choice((mat.light, mat.mid, mat.mid, mat.dark)) for _ in pts]
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            ds = []
            for i, (px, py) in enumerate(pts):
                dx = min(abs(x + .5 - px), w - abs(x + .5 - px))
                dy = min(abs(y + .5 - py), h - abs(y + .5 - py))
                ds.append((math.hypot(dx, dy), i, x + .5 - px, y + .5 - py))
            ds.sort()
            d1, i1, ox, oy = ds[0]
            if ds[1][0] - d1 < 1.1:
                c = mat.mortar
            else:
                c = tone[i1]
                if ox + oy < -1.4:
                    c = shade(c, 1.12)
                elif ox + oy > 2.0:
                    c = shade(c, 0.86)
            img.putpixel((x, y), c + (255,))
    return img


def bricks(mat, seed, w=16, h=16, bw=8, bh=4, jitter=True):
    """Running-bond bricks, 1 px mortar, one light row on top of each brick, one dark below."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), mat.mortar + (255,))
    for row in range(0, h, bh):
        off = (bw // 2) if (row // bh) % 2 else 0
        for bx in range(-bw, w + bw, bw):
            x0, x1 = bx + off, bx + off + bw - 1
            base = rnd.choice((mat.light, mat.mid, mat.mid, mat.dark)) if jitter else mat.mid
            for y in range(row, min(row + bh - 1, h)):
                for x in range(max(x0, 0), min(x1, w)):
                    c = base
                    if y == row:
                        c = shade(base, 1.12)
                    elif y == row + bh - 2:
                        c = shade(base, 0.88)
                    if x == x0:
                        c = shade(c, 1.06)
                    img.putpixel((x, y), c + (255,))
    return img


def slab_wall(mat, seed, w=16, h=16):
    """Smooth dressed stone: big blocks with a faint seam, a speckle of lighter and darker pixels."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        for x in range(w):
            c = mat.mid
            r = rnd.random()
            if r < 0.07:
                c = mat.light
            elif r < 0.13:
                c = mat.dark
            img.putpixel((x, y), c + (255,))
    for x in range(w):                       # two courses, 8 px, seam line
        img.putpixel((x, 7), mat.dark + (255,))
        img.putpixel((x, 8), shade(mat.light, 1.04) + (255,))
    for y in range(0, 7):
        img.putpixel((5, y), mat.dark + (255,))
        img.putpixel((6, y), shade(mat.light, 1.04) + (255,))
    for y in range(9, h):
        img.putpixel((11, y), mat.dark + (255,))
        img.putpixel((12, y), shade(mat.light, 1.04) + (255,))
    return img


def tiles(mat, seed, w=16, h=16):
    """Dark tiled stone, 4x4 tiles with a light top-left edge."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), mat.mortar + (255,))
    for ty in range(0, h, 4):
        for tx in range(0, w, 4):
            base = rnd.choice((mat.mid, mat.mid, mat.dark, mat.light))
            for y in range(ty, ty + 3):
                for x in range(tx, tx + 3):
                    c = base
                    if y == ty or x == tx:
                        c = shade(base, 1.14)
                    elif y == ty + 2 or x == tx + 2:
                        c = shade(base, 0.86)
                    img.putpixel((x, y), c + (255,))
    return img


# the wall pattern of each stage: fieldstone, brick, stone brick, dressed slab, tiles, tiles
WALL = {1: cobble, 2: bricks, 3: bricks, 4: slab_wall, 5: tiles, 6: tiles}


def wall_texture(stage):
    return WALL[stage](MATS[stage], 100 + stage)


def trim_rows(img, rows, ramp):
    """Overpaint whole rows of `img` with a metal trim: ramp = (light, mid, dark)."""
    for k, y in enumerate(rows):
        c = ramp[0] if k == 0 else ramp[1] if k < len(rows) - 1 or len(rows) == 1 else ramp[2]
        for x in range(img.width):
            img.putpixel((x, y), c + (255,))


def studs(img, ys, xs, ramp, size=2):
    for y in ys:
        for x in xs:
            for dy in range(size):
                for dx in range(size):
                    c = ramp[0] if (dx == 0 and dy == 0) else ramp[2] if (dx == size - 1 and dy == size - 1) else ramp[1]
                    img.putpixel((x + dx, y + dy), c + (255,))


def collar_atlas(stage):
    """32x32 static atlas for one stage. Layout (all in px):
       (0..15, 0..15)   wall face, the top row of the wall is row 0 and the wall is COLLAR_H rows tall
       (0..15, 16..31)  top view of the collar ring (a 2 px ring; walls cut their top out of it)
       (16..17, 0..11)  post shaft, (18..19, 0..1) post cap, (18..19, 2..3) post finial (stage VI)
       (20..31, 0..15)  filler so the particle sprite is opaque
    """
    m = MATS[stage]
    H = COLLAR_H[stage]
    atlas = Image.new("RGBA", (32, 32), m.mid + (255,))
    wall = wall_texture(stage)

    # --- wall: the texture's rows 0..15 are laid so row 0 is the top lip
    wall_img = wall.copy()
    # a top lip row that reads as the collar's rim, brighter than the face
    for x in range(16):
        c = wall_img.getpixel((x, 0))[:3]
        wall_img.putpixel((x, 0), shade(c, 1.18) + (255,))
    if stage == 3:
        studs(wall_img, (H // 2,), (3, 11), IRON, 2)
    if stage == 4:
        trim_rows(wall_img, (0, 1), GOLD)
    if stage == 5:
        trim_rows(wall_img, (0, 1), GOLD)
        studs(wall_img, (2,), (7,), GEM, 2)
    if stage == 6:
        trim_rows(wall_img, (0, 1, 2), GOLD)
        studs(wall_img, (4,), (3, 11), GOLD, 2)
    atlas.paste(wall_img, (0, 0))

    # --- ring top (rows 16..31): lighter than the faces, dark seam
    ring = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(500 + stage)
    for y in range(16):
        for x in range(16):
            if 2 <= x < 14 and 2 <= y < 14:
                ring.putpixel((x, y), shade(m.dark, 0.8) + (255,))
                continue
            c = m.light if rnd.random() > 0.3 else m.mid
            ring.putpixel((x, y), shade(c, 1.04) + (255,))
    # outer and inner edge of the ring, one darker row
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            ring.putpixel((x, y), shade(m.mid, 0.82) + (255,))
    for i in range(2, 14):
        for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
            ring.putpixel((x, y), shade(m.dark, 0.9) + (255,))
    if stage in (5, 6):                    # a gold inlay in the cap: dotted at V, solid and doubled at VI
        for i in range(1, 15):
            for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
                if stage == 6 or (i + x + y) % 2:
                    ring.putpixel((x, y), (GOLD[0] if (x + y) % 2 else GOLD[1]) + (255,))
        if stage == 6:
            for i in range(2, 14):
                for (x, y) in ((i, 2), (i, 13), (2, i), (13, i)):
                    ring.putpixel((x, y), GOLD[2] + (255,))
    if stage == 3:
        for i in range(1, 15):
            for (x, y) in ((i, 1), (i, 14), (1, i), (14, i)):
                ring.putpixel((x, y), IRON[1] + (255,))
    atlas.paste(ring, (0, 16))

    # --- post shaft (16..17 x 0..11): the material's mid, a light left column
    for y in range(12):
        atlas.putpixel((16, y), shade(m.mid, 1.12) + (255,))
        atlas.putpixel((17, y), shade(m.mid, 0.82) + (255,))
    if stage >= 4:                         # gold bands on the shaft
        for y in (1, 4):
            atlas.putpixel((16, y), GOLD[0] + (255,))
            atlas.putpixel((17, y), GOLD[2] + (255,))
    # --- post cap (18..19 x 0..1), finial (18..19 x 2..3)
    cap = IRON if stage == 3 else GOLD
    for (x, y) in ((18, 0), (19, 0), (18, 1), (19, 1)):
        atlas.putpixel((x, y), cap[1] + (255,))
    atlas.putpixel((18, 0), cap[0] + (255,))
    atlas.putpixel((19, 1), cap[2] + (255,))
    for (x, y) in ((18, 2), (19, 2), (18, 3), (19, 3)):
        atlas.putpixel((x, y), GOLD[1] + (255,))
    atlas.putpixel((18, 2), GOLD[0] + (255,))
    atlas.putpixel((19, 3), GOLD[2] + (255,))
    # filler (20..31 x 0..15)
    for y in range(16):
        for x in range(20, 32):
            atlas.putpixel((x, y), shade(m.mid, 0.9 + 0.1 * ((x + y) % 3 == 0)) + (255,))
    return atlas


# ---------------------------------------------------------------------------------------
# flames
# ---------------------------------------------------------------------------------------
# Every flame frame is 16x16, like the vanilla campfire's: an animated sprite whose frame is not a
# multiple of 16 px would cut the mip level of the WHOLE block atlas (SpriteLoader.stitch takes the
# minimum over all sprites). The flame never leaves the block: model elements are not clipped, so a plane
# taller than the block would push the flame tip and the sparks into the block above. So every stage uses
# the same plane (FLAME_BASE..FLAME_TOP, 15 units for 16 rows, a texel is 0.94 units high and about 1.06
# wide after the 45 degree turn) and the stages differ in what is drawn on it: the rows of the tallest
# tongue, the number and spread of the tongues, the colour and the sparks above the tip. check() fails
# on any block-model element that leaves the 0..16 cube.
FLAME_ROWS = 16
FLAME_BASE, FLAME_TOP = 3, 16        # the planes start between the logs and end at the top of the block
FLAME_X0, FLAME_X1 = 3, 13           # plane width before the turn; "rescale" stretches it by 1.41 along the diagonal
FLAME_H = {1: 8.6, 2: 9.4, 3: 10.0, 4: 10.6, 5: 11.2, 6: 11.8}   # rows of the tallest tongue in the frame
FLAME_SPARKS = {1: 0, 2: 0, 3: 1, 4: 2, 5: 3, 6: 5}
# tongues: (x offset from the centre, half width at the base, height relative to the tallest)
TONGUES = {
    1: [(0.0, 3.0, 1.0)],
    2: [(0.0, 3.4, 1.0), (-3.2, 1.9, 0.52)],
    3: [(0.0, 3.6, 1.0), (-3.6, 2.2, 0.58), (3.4, 2.0, 0.5)],
    4: [(0.0, 3.8, 1.0), (-3.6, 2.4, 0.64), (3.4, 2.4, 0.58), (-5.4, 1.4, 0.34)],
    5: [(0.0, 4.0, 1.0), (-3.6, 2.6, 0.68), (3.5, 2.6, 0.62), (-5.6, 1.5, 0.4), (5.5, 1.4, 0.36)],
    6: [(0.0, 4.2, 1.0), (-3.4, 2.8, 0.76), (3.4, 2.8, 0.7), (-5.6, 1.7, 0.46), (5.5, 1.6, 0.42),
        (-1.4, 2.0, 0.86)],
}
# layer colours from the rim inwards
FLAME_RAMP = {
    1: [(200, 54, 28), (238, 112, 36), (252, 176, 64)],
    2: [(200, 54, 28), (240, 116, 36), (253, 184, 70), (255, 224, 128)],
    3: [(196, 52, 28), (240, 120, 36), (254, 190, 72), (255, 230, 130)],
    4: [(198, 56, 28), (242, 126, 38), (255, 196, 76), (255, 236, 140), (255, 248, 200)],
    5: [(200, 60, 30), (244, 130, 40), (255, 204, 82), (255, 240, 150), (255, 250, 210)],
    6: [(232, 118, 30), (255, 176, 38), (255, 222, 78), (255, 244, 150), (255, 254, 228)],
}
SPARK = {1: (255, 190, 80), 2: (255, 190, 80), 3: (255, 200, 90), 4: (255, 214, 100),
         5: (255, 226, 120), 6: (255, 240, 150)}


def flame_frame(stage, frame, width=16):
    """One flame frame, 16 px wide and FLAME_ROWS tall. Each tongue is a teardrop that
    narrows to a tip and leans a little further the higher it goes; the lean and the height of
    every tongue change per frame, out of phase. Colour comes from the distance to the rim:
    red at the edge, then orange, yellow and a pale core."""
    rows = FLAME_ROWS
    H = FLAME_H[stage]
    rnd = random.Random(stage * 97 + frame * 13)
    mask = [[False] * width for _ in range(rows)]
    cx = 7.5
    for k, (ox, hw, rel) in enumerate(TONGUES[stage]):
        phase = (frame + k * 1.3) % 4
        lean = (0.0, 1.1, 0.0, -1.1)[int(phase) % 4] * (0.6 + 0.2 * k) * (1 if k % 2 == 0 else -1)
        th = H * rel + (0.0, 0.8, 0.0, -0.8)[int(phase) % 4] * (0 if k == 0 else 1) + (0.6 if frame == 1 and k == 0 else 0)
        th = max(3.0, th)
        for r in range(int(th) + 1):                 # r = rows above the base
            t = r / th
            half = hw * (1 - t) ** 0.85 if t < 1 else 0
            xc = cx + ox + lean * t ** 1.6
            y = rows - 1 - r
            for x in range(width):
                if abs(x + 0.5 - xc) <= half + (0.35 if r == 0 else 0):
                    mask[y][x] = True
    depth = [[0] * width for _ in range(rows)]
    for y in range(rows):
        for x in range(width):
            if not mask[y][x]:
                continue
            d = 0
            while d < 6:
                ok = True
                for (ox_, oy_) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ox_ * (d + 1), y + oy_ * (d + 1)
                    if ny >= rows:                   # the bottom edge is not a rim, the fire sits in the logs
                        continue
                    if not (0 <= nx < width and 0 <= ny < rows and mask[ny][nx]):
                        ok = False
                        break
                if not ok:
                    break
                d += 1
            depth[y][x] = d
    ramp = FLAME_RAMP[stage]
    img = Image.new("RGBA", (width, rows), (0, 0, 0, 0))
    for y in range(rows):
        for x in range(width):
            if mask[y][x]:
                # the lowest rows run a layer hotter, so the foot of the flame glows
                hot = 1 if (rows - 1 - y) <= max(2, H // 4) and depth[y][x] >= 1 else 0
                img.putpixel((x, y), ramp[min(depth[y][x] + hot, len(ramp) - 1)] + (255,))
    top_y = min((y for y in range(rows) for x in range(width) if mask[y][x]), default=rows)
    sparks = []
    for k in range(FLAME_SPARKS[stage]):       # single pixels above the tip, never two side by side
        sx = 2 + (k * 5 + frame * 3 + rnd.randint(0, 1)) % 12
        sy = top_y - 1 - ((k * 2 + frame) % 3)
        if 0 <= sy and not mask[sy][sx] and all(abs(sx - ox) > 1 or abs(sy - oy) > 1 for ox, oy in sparks):
            sparks.append((sx, sy))
            img.putpixel((sx, sy), SPARK[stage] + (255,))

    return img


# ---------------------------------------------------------------------------------------
# logs, embers, ash
# ---------------------------------------------------------------------------------------
GLOW = {   # coal colours, dim to bright, per stage
    1: [(96, 30, 22), (170, 48, 28), (224, 96, 34), (246, 140, 50)],
    2: [(100, 32, 22), (178, 54, 28), (232, 108, 36), (250, 156, 56)],
    3: [(104, 34, 22), (186, 62, 28), (238, 120, 38), (254, 172, 64)],
    4: [(108, 38, 22), (194, 72, 28), (244, 136, 40), (255, 190, 78)],
    5: [(112, 42, 22), (202, 84, 28), (248, 152, 44), (255, 208, 96)],
    6: [(120, 50, 24), (214, 104, 30), (252, 176, 52), (255, 232, 130)],
}
BARK = [(30, 24, 22), (52, 40, 32), (78, 60, 44), (104, 80, 56)]    # charred, dark to light
ASH = [(40, 38, 40), (58, 56, 58), (78, 76, 76), (98, 94, 92)]


def logs_frame(stage, frame, smoulder=False):
    """16x16 frame of the logs texture.
       rows 0..3, all columns      bark along the log (12 used, 3 rows used)
       rows 4..7, cols 0..3        the end of a log
       rows 4..15, cols 4..15      the ember bed (12x12) between the logs"""
    rnd = random.Random(stage * 31 + 7)         # same layout every frame, only the glow moves
    glow = GLOW[stage]
    img = Image.new("RGBA", (16, 16), BARK[0] + (255,))
    ph = frame * math.pi / 2

    def coal_colour(i, base_level):
        lvl = base_level + (1 if math.sin(ph + i * 1.7) > 0.35 else 0) - (1 if math.sin(ph + i * 1.7) < -0.6 else 0)
        return glow[max(0, min(3, lvl))]

    # bark: dark streaks with a few glowing cracks
    for y in range(4):
        for x in range(16):
            c = BARK[1] if (x + y * 3) % 5 else BARK[2]
            if y == 0:
                c = BARK[2] if rnd.random() < 0.5 else BARK[3]
            if y == 3:
                c = BARK[0] if rnd.random() < 0.6 else BARK[1]
            img.putpixel((x, y), c + (255,))
    crack_xs = sorted(rnd.sample(range(1, 15), 2 + stage // 2))
    for i, x in enumerate(crack_xs):
        y = 1 + (i % 2)
        if smoulder:
            img.putpixel((x, y), glow[1 if i == 1 else 0] + (255,))      # a few dying coals in the bark
            continue
        img.putpixel((x, y), coal_colour(i, 1 + (stage >= 4)) + (255,))
        if stage >= 3 and i % 2 == 0:
            img.putpixel((x + 1, y), coal_colour(i + 3, 1) + (255,))
    # log end 4x4 (cols 0..3, rows 4..7): bark ring, pale charred heart
    for y in range(4):
        for x in range(4):
            ring = x in (0, 3) or y in (0, 3)
            img.putpixel((x, 4 + y), (BARK[1] if ring else (150, 112, 70)) + (255,))
    if not smoulder:
        img.putpixel((1, 5), coal_colour(9, 2) + (255,))
        img.putpixel((2, 6), coal_colour(10, 1) + (255,))
    else:
        img.putpixel((1, 5), (120, 94, 62) + (255,))
    # the unused strip under the log end gets plain bark
    for y in range(8, 16):
        for x in range(4):
            img.putpixel((x, y), BARK[1 + (x + y) % 2] + (255,))
    # ember bed 12x12 (cols 4..15, rows 4..15): ash with coals
    bed = random.Random(stage * 53 + 3)
    for y in range(4, 16):
        for x in range(4, 16):
            c = ASH[bed.choice((0, 1, 1, 2))]
            img.putpixel((x, y), c + (255,))
    coals = [(bed.randint(4, 15), bed.randint(4, 15)) for _ in range(9 + stage)]
    for i, (x, y) in enumerate(coals):
        if smoulder:
            if i % 3 == 0:
                img.putpixel((x, y), glow[1 if i % 6 == 0 else 0] + (255,))
            continue
        img.putpixel((x, y), coal_colour(i, 1 + (i % 3 == 0)) + (255,))
        if i % 2 == 0 and x < 15:
            img.putpixel((x + 1, y), shade(coal_colour(i + 1, 0), 1.0) + (255,))
    return img


def strip(frames):
    """Stack frames vertically into one animated texture."""
    w, h = frames[0].size
    out = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.paste(f, (0, i * h))
    return out


# ---------------------------------------------------------------------------------------
# hearth models
# ---------------------------------------------------------------------------------------
def box(frm, to, faces):
    return {"from": list(frm), "to": list(to), "faces": faces}


def cuv(x0, y0, x1, y1, size=32):
    """UV for a pixel rectangle of a size x size atlas. Model UVs always run 0..16 over the whole
    texture, whatever its resolution, so pixel p of a 32 px atlas is uv p/2."""
    k = 16 / size
    return [x0 * k, y0 * k, x1 * k, y1 * k]


def hearth_model(stage, smouldering):
    H = COLLAR_H[stage]
    tex = {
        "particle": f"proficiency:block/hearth_stage_{stage}_collar",
        "collar": f"proficiency:block/hearth_stage_{stage}_collar",
        "logs": f"proficiency:block/hearth_smouldering_logs_{stage}" if smouldering
        else f"proficiency:block/hearth_stage_{stage}_logs",
    }
    if not smouldering:
        tex["fire"] = f"proficiency:block/hearth_stage_{stage}_fire"
    els = []
    # --- ash bed (the floor inside the collar)
    els.append(box((2, 0, 2), (14, 1, 14), {
        "up": {"uv": [4, 4, 16, 16], "texture": "#logs"},
    }))
    # --- collar walls: N and S are full width, W and E fit between them
    ring_top = {"n": cuv(0, 16, 16, 18), "s": cuv(0, 30, 16, 32), "w": cuv(0, 18, 2, 30), "e": cuv(14, 18, 16, 30)}
    els.append(box((0, 0, 0), (16, H, 2), {
        "north": {"uv": cuv(0, 0, 16, H), "texture": "#collar", "cullface": "north"},
        "south": {"uv": cuv(0, 0, 16, H), "texture": "#collar"},
        "east": {"uv": cuv(0, 0, 2, H), "texture": "#collar", "cullface": "east"},
        "west": {"uv": cuv(0, 0, 2, H), "texture": "#collar", "cullface": "west"},
        "up": {"uv": ring_top["n"], "texture": "#collar"},
        "down": {"uv": ring_top["n"], "texture": "#collar", "cullface": "down"},
    }))
    els.append(box((0, 0, 14), (16, H, 16), {
        "south": {"uv": cuv(0, 0, 16, H), "texture": "#collar", "cullface": "south"},
        "north": {"uv": cuv(0, 0, 16, H), "texture": "#collar"},
        "east": {"uv": cuv(0, 0, 2, H), "texture": "#collar", "cullface": "east"},
        "west": {"uv": cuv(0, 0, 2, H), "texture": "#collar", "cullface": "west"},
        "up": {"uv": ring_top["s"], "texture": "#collar"},
        "down": {"uv": ring_top["s"], "texture": "#collar", "cullface": "down"},
    }))
    els.append(box((0, 0, 2), (2, H, 14), {
        "west": {"uv": cuv(2, 0, 14, H), "texture": "#collar", "cullface": "west"},
        "east": {"uv": cuv(2, 0, 14, H), "texture": "#collar"},
        "up": {"uv": ring_top["w"], "texture": "#collar"},
        "down": {"uv": ring_top["w"], "texture": "#collar", "cullface": "down"},
    }))
    els.append(box((14, 0, 2), (16, H, 14), {
        "east": {"uv": cuv(2, 0, 14, H), "texture": "#collar", "cullface": "east"},
        "west": {"uv": cuv(2, 0, 14, H), "texture": "#collar"},
        "up": {"uv": ring_top["e"], "texture": "#collar"},
        "down": {"uv": ring_top["e"], "texture": "#collar", "cullface": "down"},
    }))
    # --- corner posts from stage III
    P = POST_H[stage]
    if P:
        corners = ((0, 0), (14, 0), (0, 14), (14, 14))
        for (x0, z0) in corners:
            x1, z1 = x0 + 2, z0 + 2
            vis = P - H            # a post starts on top of the wall: no coplanar faces with the wall's own
            faces = {
                d: {"uv": cuv(16, 0, 18, vis), "texture": "#collar"} for d in ("north", "south", "east", "west")
            }
            faces["up"] = {"uv": cuv(18, 0, 20, 2), "texture": "#collar"}
            els.append(box((x0, H, z0), (x1, P, z1), faces))
            if stage == 6:        # a gold finial cube on each post
                els.append(box((x0 + 0.5, P, z0 + 0.5), (x1 - 0.5, P + 1.5, z1 - 0.5), {
                    d: {"uv": cuv(18, 2, 20, 4), "texture": "#collar"}
                    for d in ("north", "south", "east", "west", "up")}))
    # --- logs: four, three units thick, stacked like a campfire. Long sides use the bark rows.
    def zlog(x0):
        return box((x0, 1, 2.5), (x0 + 3, 4, 13.5), {
            "east": {"uv": [0, 0, 11, 3], "texture": "#logs"},
            "west": {"uv": [0, 0, 11, 3], "texture": "#logs"},
            "up": {"uv": [0, 0, 11, 3], "rotation": 90, "texture": "#logs"},
            "north": {"uv": [0, 4, 3, 7], "texture": "#logs"},
            "south": {"uv": [0, 4, 3, 7], "texture": "#logs"},
        })

    def xlog(z0):
        return box((2.5, 4, z0), (13.5, 7, z0 + 3), {
            "north": {"uv": [0, 0, 11, 3], "texture": "#logs"},
            "south": {"uv": [0, 0, 11, 3], "texture": "#logs"},
            "up": {"uv": [0, 0, 11, 3], "texture": "#logs"},
            "east": {"uv": [0, 4, 3, 7], "texture": "#logs"},
            "west": {"uv": [0, 4, 3, 7], "texture": "#logs"},
        })
    els += [zlog(3), zlog(10), xlog(3), xlog(10)]
    # --- the flame: two crossed planes, like the vanilla campfire, the same plane for every stage (see FLAME_H)
    if not smouldering:
        fy0, fy1 = FLAME_BASE, FLAME_TOP
        els.append({"from": [FLAME_X0, fy0, 8], "to": [FLAME_X1, fy1, 8],
                    "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                    "shade": False,
                    "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#fire"},
                              "south": {"uv": [0, 0, 16, 16], "texture": "#fire"}}})
        els.append({"from": [8, fy0, FLAME_X0], "to": [8, fy1, FLAME_X1],
                    "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                    "shade": False,
                    "faces": {"west": {"uv": [0, 0, 16, 16], "texture": "#fire"},
                              "east": {"uv": [0, 0, 16, 16], "texture": "#fire"}}})
    return {"render_type": "minecraft:cutout", "textures": tex, "elements": els}


def write_hearth():
    """Collars, logs, blockstates. NOT the stage models or the fire textures: run tools/gen_hearth_block.py."""
    for stage in STAGES:
        write_png(collar_atlas(stage), os.path.join(BLOCK_TEX, f"hearth_stage_{stage}_collar.png"))
        # the flame textures and the stage models are written by gen_hearth_block.py (plus-shaped, taller)
        p = os.path.join(BLOCK_TEX, f"hearth_stage_{stage}_logs.png")
        write_png(strip([logs_frame(stage, f) for f in range(4)]), p)
        write_mcmeta(p, frametime=12)
        write_png(logs_frame(stage, 0, smoulder=True), os.path.join(BLOCK_TEX, f"hearth_smouldering_logs_{stage}.png"))
    variants = {}
    for stage in STAGES:
        variants[f"smouldering=false,stage={stage}"] = {"model": f"proficiency:block/hearth_stage_{stage}"}
        variants[f"smouldering=true,stage={stage}"] = {"model": f"proficiency:block/hearth_stage_{stage}_smouldering"}
    write_json({"variants": variants}, os.path.join(STATES, "hearth.json"))


# ---------------------------------------------------------------------------------------
# siege trophy: a wall plaque whose look grows with the tier (3..6)
# ---------------------------------------------------------------------------------------
TP = {   # pixel palette of the small sprites on the plaques: bone, shadow, ember eyes, gold
    "O": (232, 228, 208), "N": (196, 188, 160), "X": (34, 30, 30), "E": (226, 70, 44),
    "G": BASE["G"], "g": BASE["g"],
}

# plaque outline: frame box (x0, y0, x1, y1) in model units, board inset 1 unit on every side
TROPHY_FRAME = {3: (2, 2, 14, 14), 4: (1, 1, 15, 15), 5: (1, 1, 15, 13), 6: (0, 0, 16, 13)}


def sprite(rows, pal=TP):
    return paint_map(rows, pal)


SKULL7 = [
    ".OOOOO.",
    "OOOOOON",
    "OXXOXXN",
    "OXXOXXN",
    "OOOXOON",
    ".OOOONN",
    ".ONONO.",
]
SKULL7_RED = [r.replace("XX", "EE") for r in SKULL7]
HORN_LEFT = ["O..", "OO.", ".OO", "..O"]
HORN_LEFT_GOLD = ["G..", "gG.", ".gG", "..g"]


def line(img, p0, p1, cols, thick=False):
    """A straight line of pixels, light with a dark shadow pixel to its right (and left too if `thick`)."""
    (x0, y0), (x1, y1) = p0, p1
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        t = i / max(n, 1)
        x = round(x0 + (x1 - x0) * t)
        y = round(y0 + (y1 - y0) * t)
        img.putpixel((x, y), cols[0] + (255,))
        if thick and x > 0:
            img.putpixel((x - 1, y), cols[0] + (255,))
        if x + 1 < img.width:
            img.putpixel((x + 1, y), cols[1] + (255,))


def planks(w, h, base, dark, light, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), base + (255,))
    for y in range(h):
        for x in range(w):
            r = rnd.random()
            if r < 0.12:
                img.putpixel((x, y), light + (255,))
            elif r < 0.22:
                img.putpixel((x, y), dark + (255,))
    for y in range(3, h, 4):
        for x in range(w):
            img.putpixel((x, y), dark + (255,))
    return img


def cloth(w, h, base, dark, light, seed, thread=None):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (w, h), base + (255,))
    for y in range(h):
        for x in range(w):
            r = rnd.random()
            if r < 0.10:
                img.putpixel((x, y), light + (255,))
            elif r < 0.20:
                img.putpixel((x, y), dark + (255,))
    if thread:
        for x in range(w):
            img.putpixel((x, 0), thread + (255,))
            img.putpixel((x, h - 1), thread + (255,))
    return img


def frame_ring(w, h, light, mid, dark, corner=None):
    """The front of a frame: a 1 px ring, light top-left and dark bottom-right, optional corner studs."""
    img = Image.new("RGBA", (w, h), mid + (255,))
    for x in range(w):
        img.putpixel((x, 0), light + (255,))
        img.putpixel((x, h - 1), dark + (255,))
    for y in range(h):
        img.putpixel((0, y), light + (255,))
        img.putpixel((w - 1, y), dark + (255,))
    img.putpixel((w - 1, 0), mid + (255,))
    img.putpixel((0, h - 1), mid + (255,))
    if corner:
        for (x, y) in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
            img.putpixel((x, y), corner + (255,))
    return img


def board_art(tier):
    """The emblem on the board of one tier (board = frame inset by 1)."""
    fx0, fy0, fx1, fy1 = TROPHY_FRAME[tier]
    w, h = fx1 - fx0 - 2, fy1 - fy0 - 2
    if tier == 3:
        img = planks(w, h, BASE["w"], BASE["v"], BASE["u"], 31)
        img.alpha_composite(sprite(SKULL7), (1, 2))
    elif tier == 4:
        img = cloth(w, h, BASE["r"], BASE["e"], BASE["R"], 41)
        for (a, b) in (((1, 1), (w - 2, h - 2)), ((w - 2, 1), (1, h - 2))):
            line(img, a, b, (BASE["q"], BASE["j"]), thick=True)
        for (x, y) in ((1, 1), (w - 3, 1), (1, h - 2), (w - 3, h - 2)):     # gold crossguards
            for (dx, dy) in ((0, 0), (1, 0)):
                img.putpixel((x + dx, y + dy), BASE["G" if dx == 0 else "g"] + (255,))
        img.alpha_composite(sprite(SKULL7), ((w - 7) // 2, (h - 7) // 2))
    elif tier == 5:
        img = cloth(w, h, (44, 46, 58), (30, 32, 42), (62, 64, 78), 51, thread=BASE["g"])
        hl = sprite(HORN_LEFT)
        img.alpha_composite(hl, (0, 1))
        img.alpha_composite(hl.transpose(Image.FLIP_LEFT_RIGHT), (w - 3, 1))
        img.alpha_composite(sprite(SKULL7_RED), ((w - 7) // 2, 2))
    else:
        img = cloth(w, h, (110, 22, 30), (80, 14, 22), (150, 40, 44), 61, thread=BASE["G"])
        hl = sprite(HORN_LEFT_GOLD)
        img.alpha_composite(hl, (0, 1))
        img.alpha_composite(hl.transpose(Image.FLIP_LEFT_RIGHT), (w - 3, 1))
        img.alpha_composite(sprite(SKULL7_RED), ((w - 7) // 2, 3))
    return img


def trophy_atlas(tier):
    """32x32: front ring (0,0), board art (16,0), edge strip rows (0,16..), ornaments (16,16..)."""
    fx0, fy0, fx1, fy1 = TROPHY_FRAME[tier]
    fw, fh = fx1 - fx0, fy1 - fy0
    atlas = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    edge_ramp = {3: (BASE["w"], BASE["v"], BASE["x"]), 4: IRON, 5: GOLD, 6: GOLD}[tier]
    ring_ramp = {3: ((176, 136, 82), (134, 99, 56), (96, 68, 36)), 4: IRON, 5: GOLD, 6: GOLD}[tier]
    corner = {3: IRON[0], 4: GOLD[0], 5: IRON[0], 6: GEM[1]}[tier]
    atlas.paste(frame_ring(fw, fh, ring_ramp[0], ring_ramp[1], ring_ramp[2], corner), (0, 0))
    atlas.paste(board_art(tier), (16, 0))
    rnd = random.Random(900 + tier)
    for y in range(16, 32):                          # edge strip: the frame's side faces
        for x in range(16):
            c = edge_ramp[1] if rnd.random() > 0.25 else edge_ramp[0 if rnd.random() > 0.5 else 2]
            atlas.putpixel((x, y), c + (255,))
    for (x, y, c) in ((16, 16, BASE["O"]), (17, 16, BASE["O"]), (16, 17, BASE["N"]), (17, 17, (150, 142, 118))):
        atlas.putpixel((x, y), c + (255,))            # bone horn
    for (x, y, c) in ((18, 16, GOLD[0]), (19, 16, GOLD[1]), (18, 17, GOLD[1]), (19, 17, GOLD[2])):
        atlas.putpixel((x, y), c + (255,))            # gold crown
    for (x, y, c) in ((20, 16, GEM[0]), (21, 16, GEM[1]), (20, 17, GEM[1]), (21, 17, GEM[2])):
        atlas.putpixel((x, y), c + (255,))            # gem
    # keep the particle sprite opaque: fill the empty rest with the edge colour
    for y in range(32):
        for x in range(32):
            if atlas.getpixel((x, y))[3] == 0 and not (x < fw and y < fh) and not (16 <= x < 16 + fw - 2 and y < fh - 2):
                atlas.putpixel((x, y), edge_ramp[1] + (255,))
    return atlas


def trophy_model(tier):
    fx0, fy0, fx1, fy1 = TROPHY_FRAME[tier]
    fw, fh = fx1 - fx0, fy1 - fy0
    bw, bh = fw - 2, fh - 2
    T = "#atlas"

    def edge(w, d):
        return cuv(0, 16, w, 16 + d)

    def part(frm, to, front_uv, sides=True):
        f = {"north": {"uv": front_uv, "texture": T}}
        if sides:
            f["east"] = {"uv": edge(to[2] - frm[2], to[1] - frm[1]), "texture": T}
            f["west"] = {"uv": edge(to[2] - frm[2], to[1] - frm[1]), "texture": T}
            f["up"] = {"uv": edge(to[0] - frm[0], to[2] - frm[2]), "texture": T}
            f["down"] = {"uv": edge(to[0] - frm[0], to[2] - frm[2]), "texture": T}
        return {"from": list(frm), "to": list(to), "faces": f}

    els = [part((fx0, fy0, 15), (fx1, fy1, 16), cuv(0, 0, fw, fh)),
           part((fx0 + 1, fy0 + 1, 14.5), (fx1 - 1, fy1 - 1, 15), cuv(16, 0, 16 + bw, bh), sides=False)]
    orn = {"horn": cuv(16, 16, 18, 18), "crown": cuv(18, 16, 20, 18), "gem": cuv(20, 16, 22, 18)}

    def bit(frm, to, kind):
        return part(frm, to, orn[kind])

    if tier == 5:      # a crest of two bone horns rising from the top corners
        for (x0, mirror) in ((fx0, False), (fx1, True)):
            sgn = 1 if not mirror else -1
            def hx(a, b):
                lo, hi = (x0 + sgn * a, x0 + sgn * b)
                return (min(lo, hi), max(lo, hi))
            for (a, b, y0, y1) in ((0, 2, fy1, fy1 + 1), (0, 1.5, fy1 + 1, fy1 + 2), (-0.5, 1, fy1 + 2, fy1 + 3)):
                lo, hi = hx(a, b)
                els.append(bit((lo, y0, 15), (hi, y1, 16), "horn"))
    if tier == 6:      # a crown of three gold spikes; a diamond is set in the root of the middle one
        for (xa, xb, top) in ((2, 4, fy1 + 2), (7, 9, fy1 + 3), (12, 14, fy1 + 2)):
            els.append(bit((xa, fy1, 15), (xb, top, 16), "crown"))
        els.append(bit((7, fy1 - 1, 14.25), (9, fy1 + 1, 15), "gem"))
    return {"textures": {"particle": f"proficiency:block/siege_trophy_tier_{tier}",
                         "atlas": f"proficiency:block/siege_trophy_tier_{tier}"},
            "elements": els}


def write_trophy():
    for tier in TIERS:
        write_png(trophy_atlas(tier), os.path.join(BLOCK_TEX, f"siege_trophy_tier_{tier}.png"))
        write_json(trophy_model(tier), os.path.join(MODELS, f"block/siege_trophy_tier_{tier}.json"))
    variants = {}
    for tier in TIERS:
        for (face, rot) in FACINGS:
            v = {"model": f"proficiency:block/siege_trophy_tier_{tier}"}
            if rot:
                v["y"] = rot
            variants[f"facing={face},tier={tier}"] = v
    write_json({"variants": variants}, os.path.join(STATES, "siege_trophy.json"))


# ---------------------------------------------------------------------------------------
# siege horn: a curved war horn, mouthpiece at the left, bell at the top right, bound with gold and iron
# ---------------------------------------------------------------------------------------
HORN = {   # horn body ramp, light to dark, and the outline
    "l": (238, 218, 178), "m": (206, 178, 132), "d": (160, 126, 88), "D": (112, 84, 56), "o": (48, 34, 26),
}


def bezier(p0, p1, p2, p3, n):
    pts = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        pts.append((u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
                    u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1]))
    return pts


def horn_image():
    """A J-shaped horn: the mouthpiece at the left, the body runs along the bottom and swings up into a
    flared bell at the right that opens upwards. The radius grows from the mouthpiece to the bell. Bands,
    by position t along the body (0 = mouthpiece, 1 = bell): gold for t < 0.10, iron at 0.34 to 0.44, a thin
    gold band at 0.58 to 0.62, a second iron band at 0.76 to 0.81 (under the bell), and the gold rim of the
    bell itself (an ellipse seen from above) around a dark mouth."""
    N = 90
    path = bezier((1.8, 9.2), (2.0, 17.6), (12.0, 17.4), (11.6, 5.4), N)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    mask = {}
    for y in range(16):
        for x in range(16):
            best = None
            for i, (cx, cy) in enumerate(path):
                t = i / N
                r = 1.15 + 2.2 * t ** 1.25
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r and (best is None or d / r < best[0]):
                    best = (d / r, i, cx, cy, r)
            if best:
                mask[(x, y)] = best
    for (x, y), (rel, i, cx, cy, r) in mask.items():
        t = i / N
        light = ((-(x + 0.5 - cx)) + (-(y + 0.5 - cy))) / (math.sqrt(2) * max(r, 0.8))   # + = towards the top left
        c = HORN["l"] if light > 0.5 else HORN["m"] if light > -0.05 else HORN["d"] if light > -0.55 else HORN["D"]
        band = None
        if t < 0.10:
            band = GOLD
        elif 0.34 < t < 0.44:
            band = IRON
        elif 0.58 < t < 0.62:
            band = GOLD
        elif 0.76 < t < 0.81:
            band = IRON
        if band:
            c = band[0] if light > 0.3 else band[1] if light > -0.4 else band[2]
        img.putpixel((x, y), c + (255,))
    # the bell rim: a gold ellipse with a dark mouth inside it
    ex, ey = path[-1]
    rr = 1.15 + 2.2
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - ex) / (rr + 0.4), (y + 0.5 - (ey - 0.4)) / 1.9
            if dx * dx + dy * dy <= 1:
                img.putpixel((x, y), (GOLD[0] if (x + y) % 5 == 0 else GOLD[1] if dy < 0.2 else GOLD[2]) + (255,))
            dx2, dy2 = (x + 0.5 - ex) / (rr - 0.9), (y + 0.5 - (ey - 0.7)) / 1.05
            if dx2 * dx2 + dy2 * dy2 <= 1:
                img.putpixel((x, y), HORN["o"] + (255,))
    return outline(img, HORN["o"])


def horn_model():
    """item/generated, held like the vanilla goat horn."""
    return {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "proficiency:item/siege_horn"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, 180, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        },
    }


def trophy_item_image():
    """Inventory icon of the Siege Trophy: the plaque seen from the front (gold frame, red board, a
    skull, two bone horns on top). One icon for every tier: the tier lives in the block state."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = 1, 3, 14, 13
    d.rectangle([x0, y0, x1, y1], fill=GOLD[1] + (255,))
    for x in range(x0, x1 + 1):
        img.putpixel((x, y0), GOLD[0] + (255,))
        img.putpixel((x, y1), GOLD[2] + (255,))
    for y in range(y0, y1 + 1):
        img.putpixel((x0, y), GOLD[0] + (255,))
        img.putpixel((x1, y), GOLD[2] + (255,))
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=BASE["r"] + (255,))
    rnd = random.Random(77)
    for _ in range(14):
        img.putpixel((rnd.randint(x0 + 1, x1 - 1), rnd.randint(y0 + 1, y1 - 1)), rnd.choice((BASE["e"], BASE["R"])) + (255,))
    img.alpha_composite(sprite(SKULL7), (4, 5))
    for (x, y) in ((1, 2), (2, 1), (1, 1), (14, 2), (13, 1), (14, 1)):
        img.putpixel((x, y), (BASE["O"] if (x + y) % 2 else BASE["N"]) + (255,))
    return outline(img, HORN["o"])      # a 1 px dark rim, like the other items


def write_trophy_item():
    write_png(trophy_item_image(), os.path.join(ITEM_TEX, "siege_trophy.png"))
    write_json({"parent": "minecraft:item/generated", "textures": {"layer0": "proficiency:item/siege_trophy"}},
               os.path.join(MODELS, "item/siege_trophy.json"))


def write_horn():
    write_png(horn_image(), os.path.join(ITEM_TEX, "siege_horn.png"))
    write_json(horn_model(), os.path.join(MODELS, "item/siege_horn.json"))


# ---------------------------------------------------------------------------------------
# HUD font glyphs: 9x9 cells, left aligned, drawn white-safe (they carry their own colour)
# ---------------------------------------------------------------------------------------
GP = {   # glyph palette, gold and ember family
    "Y": (255, 246, 176), "f": (255, 210, 84), "F": (244, 134, 38), "r": (200, 54, 30), "d": (122, 34, 24),
    "G": BASE["G"], "g": BASE["g"], "h": BASE["h"],
    "b": (190, 148, 92), "B": (140, 102, 58), "D": (92, 64, 36),
    "a": (150, 144, 138), "A": (96, 92, 90), "S": (62, 60, 62),
    "i": BASE["i"], "k": BASE["k"],
    "p": (248, 232, 188), "P": (220, 196, 140), "n": (160, 124, 80),
    "t": (222, 184, 112), "T": (186, 146, 80), "u": (132, 98, 52), "e": (84, 132, 52),
    "R": (236, 96, 80),
}
GLYPH_W = GLYPH_H = 9
CELL = 9
ICON_W = 7      # every HUD glyph is exactly this wide: a bitmap font takes the advance from the last drawn column


# --- Warmth, frames 0..8: logs always sit at the bottom (so every frame keeps the same width and the HUD
#     does not jitter), the fire grows from cold embers to a full flame, and frame 8 gilds it.
WARMTH = [
    # 0: cold embers (grey logs, two dull coals)
    [".......", ".......", ".......", ".......", ".......", ".......", "..d.d..", ".aAaAa.", "ASAaASA"],
    # 1: embers glowing
    [".......", ".......", ".......", ".......", ".......", "..r.r..", ".rFdFr.", ".bBbBb.", "BDBDBDB"],
    # 2: first flicker
    [".......", ".......", ".......", ".......", "...F...", "..FfF..", ".rFfFr.", ".bBbBb.", "BDBDBDB"],
    # 3
    [".......", ".......", ".......", "...F...", "..FfF..", "..FfF..", ".rFYFr.", ".bBbBb.", "BDBDBDB"],
    # 4
    [".......", ".......", "...F...", "..FfF..", "..FfF..", ".rFYFr.", ".rFYFr.", ".bBbBb.", "BDBDBDB"],
    # 5
    [".......", "...F...", "..FfF..", "..FfF..", ".rFfFr.", ".rFYFr.", ".rFYFr.", ".bBbBb.", "BDBDBDB"],
    # 6
    [".......", "...F...", "..FfF..", ".rFfFr.", ".rFYFr.", "rFfYfFr", "rFfYfFr", ".bBbBb.", "BDBDBDB"],
    # 7: full flame
    ["...f...", "..FfF..", "..FfF..", ".rFYFr.", "rFfYfFr", "rFfYfFr", "rFfYfFr", ".bBbBb.", "BDBDBDB"],
    # 8: full flame, gilded: gold sparks and gold-capped logs
    ["..GfG..", "..FfF..", ".rFYFr.", ".rFYFr.", "rFfYfFr", "rFfYfFr", "rFfYfFr", ".GgggG.", "hgggggh"],
]
ICONS_HUD = {
    # U+E019 siege: a keep with a tall tower, a flag and an ember window
    "siege": ["..R....", "..Rrr..", "..k....", "a.a.a.a", "aaaaaaa", ".aSFSa.", ".aSFSa.", ".aaSaa.", "AAAAAAA"],
    # U+E01A Tales: a rolled scroll, ink lines
    "scroll": [".pppppp", "pPPPPPp", ".PnnnP.", ".PPPPP.", ".PnnPP.", ".PPPPP.", ".PnnnP.", "pPPPPPp", ".PPPPP."],
    # U+E01B Provisioned: a ration pack, a green strap
    "pack": [".ttttt.", "tTTeTTu", "teeeeeu", "tTTeTTu", "tTTeTTu", "teeeeeu", "tTTeTTu", ".uuuuu."],
    # U+E01C lock
    "lock": [".iiiii.", "i.....i", "i.....i", "gggGggg", "gGgGgGg", "gggkggg", "ggkkkgg", "hhhhhhh"],
    # U+E01D bell
    "bell": ["...g...", "..gGg..", ".gGGgg.", ".gGGgh.", ".gGggh.", "gGGgggh", "hhhhhhh", "...g..."],
    # U+E01E house (Homebuilder)
    "house": ["...R...", "..rRr..", ".rRRRr.", "rRRRRRr", ".ppppp.", ".pFfpP.", ".pDDpP.", ".pDDpP."],
}


def glyph_image(rows):
    """A map ICON_W wide into a 9x9 cell, left aligned and bottom aligned (the baseline sits one row above
    the bottom of the cell)."""
    assert all(len(r) == ICON_W for r in rows) and len(rows) <= GLYPH_H, [len(r) for r in rows]
    cell = ["." * GLYPH_W] * (GLYPH_H - len(rows)) + [r.ljust(GLYPH_W, ".") for r in rows]
    return paint_map(cell, GP)


def write_glyphs():
    warmth = Image.new("RGBA", (CELL * 9, CELL), (0, 0, 0, 0))
    for i, rows in enumerate(WARMTH):
        warmth.paste(glyph_image(rows), (i * CELL, 0))
    write_png(warmth, os.path.join(FONT_TEX, "hearth_warmth.png"))
    names = list(ICONS_HUD)
    hud = Image.new("RGBA", (CELL * len(names), CELL), (0, 0, 0, 0))
    for i, n in enumerate(names):
        rows = ICONS_HUD[n]
        hud.paste(glyph_image(rows), (i * CELL, 0))
    write_png(hud, os.path.join(FONT_TEX, "hearth_hud.png"))
    # the font providers: keep what is there (U+E000, U+E001), add ours
    with open(FONT_JSON, encoding="utf-8") as fh:
        font = json.load(fh)
    keep = [p for p in font["providers"] if p.get("file") not in ("proficiency:font/hearth_warmth.png", "proficiency:font/hearth_hud.png")]
    keep.append({"type": "bitmap", "file": "proficiency:font/hearth_warmth.png", "ascent": 8, "height": 9,
                 "chars": ["".join(chr(0xE010 + i) for i in range(9))]})
    keep.append({"type": "bitmap", "file": "proficiency:font/hearth_hud.png", "ascent": 8, "height": 9,
                 "chars": ["".join(chr(0xE019 + i) for i in range(len(names)))]})
    font["providers"] = keep
    write_json(font, FONT_JSON, ascii_only=True)     # \\uE0xx escapes, like the two glyphs already there


# ---------------------------------------------------------------------------------------
# GUI icons: 16x16 in the skill icons' style, one PNG per path the client draws
# ---------------------------------------------------------------------------------------
ICON_POOLS = ("hearty", "regen", "fortified", "workshop", "homecoming")
ICON_COMFORT = ("bed", "seat", "carpet", "banner", "fire", "light", "plant", "art", "books", "music", "storage", "trophy")
ICON_TOP = ("warmth", "siege", "tales", "provisioned", "character", "comfort", "ledger", "bell", "horn", "trophy")
ICON_FROM_ITEM = {"horn": horn_image, "trophy": trophy_item_image}     # the same object, the same sprite
ICON_EXTRA = {   # map letters the skill palette does not have: roast meat and terracotta, the tier V gem
    "T": (226, 148, 66), "U": (176, 98, 40), "V": (112, 58, 26),
    "B": GEM[0], "C": GEM[1], "K": GEM[2], "S": (232, 238, 244),    # S: steam
}
ICON_LIFT = {5: 0.22, 6: 0.3}      # stage emblems of the two darkest collars: share of white mixed in
ICON_LIMITS = {"colours": (4, 14), "fill": (0.15, 0.92), "reach": 14, "rim": 0.75}   # the skill icons' own range, checked below


def icon_names():
    """Every icon path under textures/gui/hearth/ without the extension, in the order the sheet shows them."""
    return ([f"pool/{n}" for n in ICON_POOLS] + [f"comfort/{k}" for k in ICON_COMFORT]
            + [f"stage/{n}" for n in STAGES] + list(ICON_TOP))


def icon_image(name):
    """One icon. Pixel maps go through gen_skill_icons.render (its palette and its rim rule); the stage
    emblems add the collar colours of their stage, so they match the block."""
    if name in ICON_FROM_ITEM:
        return ICON_FROM_ITEM[name]()
    _, _, colours = gen_skill_icons.read_categories()
    accent = ICON_ACCENT[name]
    extra = dict(ICON_EXTRA)
    if name.startswith("stage/"):
        stage = int(name.split("/")[1])
        m = MATS[stage]
        lift = ICON_LIFT.get(stage, 0)      # the dark tiers are lifted so a 16 px icon keeps its shape against its rim
        extra.update({k: mix(c, (255, 255, 255), lift) for k, c in (("H", m.light), ("M", m.mid), ("D", m.dark), ("m", m.mortar))})
    return gen_skill_icons.render(name, ICON_MAPS[name], colours[accent] if isinstance(accent, str) else accent,
                                  bare=ICON_BARE.get(name, ""), extra=extra)


def write_icons():
    for name in icon_names():
        write_png(icon_image(name), os.path.join(GUI_TEX, name + ".png"))


# ---------------------------------------------------------------------------------------
# self check: the rules a model, a texture and a font provider must follow to load in 1.20.1 and 1.21.1
# ---------------------------------------------------------------------------------------
def tex_path(ref):
    ns, _, p = ref.rpartition(":")
    return os.path.join(ASSETS, ns or "minecraft", "textures", p + ".png")


def own_models():
    """(file name, is a block model, parsed JSON) of every Hearth and Siege model on disk."""
    for dirpath, _, files in sorted(os.walk(MODELS)):
        for fn in sorted(files):
            if fn.startswith(("hearth_", "siege_")) and fn.endswith(".json"):
                with open(os.path.join(dirpath, fn), encoding="utf-8") as fh:
                    yield fn, os.path.basename(dirpath) == "block", json.load(fh)


# --- geometry in block space: element rotation and "rescale" applied, so rotated elements are checked too
def _dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _unit(a):
    n = math.sqrt(_dot(a, a))
    return [v / n for v in a]


def world_faces(el):
    """Every face of one model element as (direction name, unit normal, plane offset, quad), in block space."""
    f, t, rot = el["from"], el["to"], el.get("rotation")
    out = []
    for face in el["faces"]:
        quad = []
        for (xm, ym, zm) in FACE_VERTS[face]:
            p = [t[0] if xm else f[0], t[1] if ym else f[1], t[2] if zm else f[2]]
            quad.append(ModelRenderer._rotate(p, rot) if rot else p)
        n = list(FACE_NORMAL[face])
        if rot:       # the face normal turns with the element (rescale only stretches, it keeps the direction)
            n = ModelRenderer._rotate(n, {"origin": [0, 0, 0], "angle": rot["angle"], "axis": rot["axis"]})
        n = _unit(n)
        out.append((face, n, _dot(n, quad[0]), quad))
    return out


def _flat(quad, n):
    """The quad as 2D points in the plane with normal n, counter-clockwise."""
    helper = [0, 1, 0] if abs(n[1]) < 0.9 else [1, 0, 0]
    u = _unit(_cross(helper, n))
    v = _cross(n, u)
    pts = [(_dot(p, u), _dot(p, v)) for p in quad]
    if sum(pts[i][0] * pts[(i + 1) % 4][1] - pts[(i + 1) % 4][0] * pts[i][1] for i in range(4)) < 0:
        pts.reverse()
    return pts


def _overlap_area(a, b):
    """Area where two convex counter-clockwise polygons overlap (Sutherland-Hodgman, then the shoelace formula)."""
    poly = a
    for i in range(len(b)):
        (x1, y1), (x2, y2) = b[i], b[(i + 1) % len(b)]
        keep = []
        for j in range(len(poly)):
            p, q = poly[j], poly[(j + 1) % len(poly)]
            sp = (x2 - x1) * (p[1] - y1) - (y2 - y1) * (p[0] - x1)
            sq = (x2 - x1) * (q[1] - y1) - (y2 - y1) * (q[0] - x1)
            if sp >= 0:
                keep.append(p)
            if (sp >= 0) != (sq >= 0):
                k = sp / (sp - sq)
                keep.append((p[0] + (q[0] - p[0]) * k, p[1] + (q[1] - p[1]) * k))
        poly = keep
        if not poly:
            return 0.0
    return abs(sum(poly[i][0] * poly[(i + 1) % len(poly)][1] - poly[(i + 1) % len(poly)][0] * poly[i][1]
                   for i in range(len(poly)))) / 2


def zfighting_faces(elements):
    """Pairs of faces that look the same way, lie in one plane and overlap: they would flicker in game.
    Rotated elements are included. Two coincident faces that look OPPOSITE ways (the two sides of a flame
    plane) are fine: block models are drawn with back-face culling, so only one of them is ever seen."""
    faces = []
    for i, el in enumerate(elements):
        for (name, n, d, quad) in world_faces(el):
            faces.append((i, name, n, d, quad))
    bad = []
    for x in range(len(faces)):
        for y in range(x + 1, len(faces)):
            ia, na, n1, d1, q1 = faces[x]
            ib, nb, n2, d2, q2 = faces[y]
            if ia == ib or _dot(n1, n2) < 0.9999 or abs(d1 - d2) > 1e-4:
                continue
            if _overlap_area(_flat(q1, n1), _flat(q2, n1)) > 1e-3:
                bad.append(f"elements {ia} and {ib} both draw a face looking {na}/{nb} on the same plane (z-fighting)")
    return bad


def leaving_the_block(elements):
    """Indexes of the elements that reach outside the 0..16 cube once rotated. Model elements are not
    clipped: whatever pokes out is drawn over (or into) the neighbouring block."""
    out = []
    for i, el in enumerate(elements):
        f, t, rot = el["from"], el["to"], el.get("rotation")
        pts = [[t[0] if a else f[0], t[1] if b else f[1], t[2] if c else f[2]]
               for a in (0, 1) for b in (0, 1) for c in (0, 1)]
        if rot:
            pts = [ModelRenderer._rotate(p, rot) for p in pts]
        if any(not -1e-6 <= v <= 16 + 1e-6 for p in pts for v in p):
            out.append(i)
    return out


PLUS_REACH = 6      # px a Hearth model may reach into an edge neighbour cell; the diagonal cells hold the player's build
HEARTH_MAX_Y = 32


def leaving_the_plus(elements):
    """Indexes of the elements (rotation applied) outside the plus-shaped zone: the centre cell 0..16 on x and z,
    plus up to PLUS_REACH px into each edge neighbour (only where the other axis stays in 0..16). Height 0..32."""
    e = 1e-6
    out = []
    for i, el in enumerate(elements):
        f, t, rot = el["from"], el["to"], el.get("rotation")
        pts = [[t[0] if a else f[0], t[1] if b else f[1], t[2] if c else f[2]]
               for a in (0, 1) for b in (0, 1) for c in (0, 1)]
        if rot:
            pts = [ModelRenderer._rotate(p, rot) for p in pts]
        xs, ys, zs = [p[0] for p in pts], [p[1] for p in pts], [p[2] for p in pts]
        x0, x1, y0, y1, z0, z1 = min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)
        in_x = x0 >= -e and x1 <= 16 + e
        in_z = z0 >= -e and z1 <= 16 + e
        ok_x_arm = in_z and x0 >= -PLUS_REACH - e and x1 <= 16 + PLUS_REACH + e
        ok_z_arm = in_x and z0 >= -PLUS_REACH - e and z1 <= 16 + PLUS_REACH + e
        if not (ok_x_arm or ok_z_arm) or y0 < -e or y1 > HEARTH_MAX_Y + e:
            out.append(i)
    return out


def check_blockstates(err):
    """Every combination of properties has a variant, and every variant points at a model that exists."""
    want = {"hearth": {f"smouldering={sm},stage={n}" for n in STAGES for sm in ("true", "false")},
            "siege_trophy": {f"facing={f},tier={t}" for t in TIERS for f, _ in FACINGS}}
    for name, keys in want.items():
        with open(os.path.join(STATES, name + ".json"), encoding="utf-8") as fh:
            have = json.load(fh)["variants"]
        if set(have) != keys:
            err(f"{name}.json: missing {sorted(keys - set(have))} extra {sorted(set(have) - keys)}")
        for k, v in have.items():
            ns, _, p = v["model"].rpartition(":")
            if not os.path.exists(os.path.join(ASSETS, ns, "models", p + ".json")):
                err(f"{name}.json {k}: model {v['model']} missing")


def check_models(err):
    """Textures resolve, uv in range, rotation legal; block models stay inside the block and do not z-fight."""
    for fn, is_block, m in own_models():
        texs = m.get("textures", {})
        for k, v in texs.items():
            if not v.startswith("#") and not os.path.exists(tex_path(v)):
                err(f"{fn}: texture {k}={v} missing")
        els = m.get("elements", [])
        for i, el in enumerate(els):
            rot = el.get("rotation")
            if rot and (abs(rot["angle"]) > 45 or rot["angle"] % 22.5):
                err(f"{fn} element {i}: rotation {rot['angle']}")
            for face, fd in el["faces"].items():
                t = fd["texture"]
                if t.startswith("#") and t[1:] not in texs:
                    err(f"{fn} element {i} {face}: {t} is not defined")
                for u in fd.get("uv", []):
                    if not 0 <= u <= 16:
                        err(f"{fn} element {i} {face}: uv {fd['uv']} outside 0..16")
        if is_block and os.path.basename(fn).startswith("hearth_stage_"):
            for i in leaving_the_plus(els):
                err(f"{fn} element {i}: outside the plus-shaped zone (centre cell + 6 px into the edge cells, "
                    "height 0..32): a diagonal cell holds the player's build")
            for msg in zfighting_faces(els):
                err(f"{fn}: {msg}")
        elif is_block:
            for i in leaving_the_block(els):
                err(f"{fn} element {i}: reaches outside the 0..16 block (elements are not clipped)")
            for msg in zfighting_faces(els):
                err(f"{fn}: {msg}")


def check_textures(err):
    """Only fully opaque or fully clear pixels; animated sprites keep 16 px frames; mcmeta sane."""
    for root in (BLOCK_TEX, ITEM_TEX, FONT_TEX):
        for fn in sorted(os.listdir(root)):
            if not fn.endswith(".png") or not fn.startswith(("hearth_", "siege_")):
                continue
            img = Image.open(os.path.join(root, fn)).convert("RGBA")
            odd = [a for a in img.getdata() if 0 < a[3] < 255]
            if odd:
                err(f"{fn}: {len(odd)} translucent pixels (no anti-aliasing allowed)")
            meta = os.path.join(root, fn + ".mcmeta")
            if os.path.exists(meta):
                with open(meta, encoding="utf-8") as fh:
                    anim = json.load(fh)["animation"]
                fw = anim.get("width", img.width)
                fh_ = anim.get("height", img.width if "width" not in anim else fw)
                if fw % 16 or fh_ % 16 or img.height % fh_ or img.width % fw:
                    err(f"{fn}: animated frame {fw}x{fh_} in {img.size} (a frame must be a multiple of 16 px, "
                        "or the whole block atlas loses mip levels)")
            elif root == BLOCK_TEX and (img.width % 16 or img.height % 16):
                err(f"{fn}: size {img.size} is not a multiple of 16")


def check_glyphs(err):
    """Contiguous coverage U+E010..U+E01E, the two old glyphs untouched, every glyph the same advance."""
    with open(FONT_JSON, encoding="utf-8") as fh:
        providers = json.load(fh)["providers"]
    seen = {}
    for p in providers:
        for row in p["chars"]:
            for ch in row:
                if ch in seen:
                    err(f"font: U+{ord(ch):04X} defined twice")
                seen[ch] = p["file"]
    for cp in [0xE000, 0xE001] + list(range(0xE010, 0xE01F)):
        if chr(cp) not in seen:
            err(f"font: U+{cp:04X} is not defined")
    for fn in ("hearth_warmth.png", "hearth_hud.png"):
        im = Image.open(os.path.join(FONT_TEX, fn)).convert("RGBA")      # once per file, not once per cell
        if im.height != CELL or im.width % CELL:
            err(f"font: {fn} is {im.size}, cells are {CELL}x{CELL}")
        for i in range(im.width // CELL):
            box_ = im.crop((i * CELL, 0, (i + 1) * CELL, CELL)).getbbox()
            if not box_:
                err(f"font: {fn} cell {i} is empty")
            elif box_[2] != ICON_W:
                err(f"font: {fn} cell {i} is {box_[2]} px wide, every HUD glyph must be {ICON_W} "
                    "(the advance comes from the last drawn column, so a different width shifts the HUD row)")


def icon_stats(img):
    """(colours, fill share, longest side of the bounding box, share of rim pixels that are dark) of an icon."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    solid = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] == 255]
    edge = [(x, y) for x, y in solid
            if any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] == 0
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))]
    box = img.getbbox() or (0, 0, 0, 0)
    dark = sum(1 for x, y in edge if max(px[x, y][:3]) <= 100)
    return (len({px[x, y] for x, y in solid}), len(solid) / (w * h), max(box[2] - box[0], box[3] - box[1]),
            dark / len(edge) if edge else 0.0)


def check_icons(err, root=None):
    """Every contract path exists, 16x16, no translucent pixel, nothing stale, no two icons alike, and each
    icon sits inside the range the skill icons span: few colours, a rim, the whole 16 px used."""
    root = root or GUI_TEX
    want = icon_names()
    seen = {}
    for name in want:
        path = os.path.join(root, name + ".png")
        if not os.path.exists(path):
            err(f"icon {name}: {path} is missing")
            continue
        img = Image.open(path).convert("RGBA")
        if img.size != (16, 16):
            err(f"icon {name}: size {img.size}, icons are 16x16")
            continue
        if any(0 < a[3] < 255 for a in img.getdata()):
            err(f"icon {name}: translucent pixels (no anti-aliasing allowed)")
        colours, fill, reach, rim = icon_stats(img)
        lo, hi = ICON_LIMITS["colours"]
        if not lo <= colours <= hi:
            err(f"icon {name}: {colours} colours, the skill icons use {lo} to {hi}")
        lo, hi = ICON_LIMITS["fill"]
        if not lo <= fill <= hi:
            err(f"icon {name}: fills {fill:.0%} of the tile, the skill icons fill {lo:.0%} to {hi:.0%}")
        if reach < ICON_LIMITS["reach"]:
            err(f"icon {name}: only {reach} px across, a skill icon uses at least {ICON_LIMITS['reach']}")
        if rim < ICON_LIMITS["rim"]:
            err(f"icon {name}: only {rim:.0%} of its edge is the dark rim (the skill icons: 68% and up)")
        key = img.tobytes()
        if key in seen:
            err(f"icon {name}: pixel-identical to {seen[key]}")
        seen[key] = name
    wanted = {n + ".png" for n in want}
    for dirpath, _, files in os.walk(root):
        for fn in files:
            rel = os.path.relpath(os.path.join(dirpath, fn), root).replace(os.sep, "/")
            if fn.endswith(".png") and rel not in wanted:
                err(f"icon {rel}: stale, no icon of that name is in the contract")


def check():
    bad = []
    for fn in (check_blockstates, check_models, check_textures, check_glyphs, check_icons):
        fn(bad.append)
    return bad


def main():
    write_hearth()
    write_trophy()
    write_horn()
    write_trophy_item()
    write_glyphs()
    write_icons()
    problems = check()
    for p in problems:
        print("CHECK FAILED:", p)
    print("hearth, trophy, horn, glyphs and icons written" + (", checks failed" if problems else ", checks passed"))
    if "--sheet" in sys.argv:
        from hearth_art_sheet import build
        build(sys.argv[sys.argv.index("--sheet") + 1])
    if "--icon-sheet" in sys.argv:
        from hearth_art_sheet import build_icons
        build_icons(sys.argv[sys.argv.index("--icon-sheet") + 1], icon_names())
    if problems:
        sys.exit(1)


if __name__ == "__main__":
    main()
