"""Contact sheet of the Hearth, Siege Trophy, Siege Horn, Homebuilder icon and HUD glyphs.

Reads the shipped files under common/src/main/resources (not the generator's internals), so a sheet
shows what the jars contain. Textures are drawn at 8x with nearest-neighbour scaling; the block
models are rendered by tools/model_preview.py; the HUD glyphs are also drawn at 1x on six
backgrounds with a Minecraft-style text shadow, which is how they read in the game.

    python3 tools/gen_hearth_art.py --sheet build/art-sheet.png
    python3 tools/gen_hearth_art.py --icon-sheet build/art2-sheet.png

The icon sheet (build_icons) lays every GUI icon of textures/gui/hearth/ beside eight skill icons at 1x, 2x
and 4x, on the grey of an inventory slot and on the dark of the mod's panels, so "one family" is judged by
eye at the sizes the game draws them.
"""
import os

from PIL import Image, ImageDraw

from model_preview import ModelRenderer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "common/src/main/resources")
TEX = os.path.join(RES, "assets/proficiency/textures")
BG = (62, 64, 70)
PANEL = (96, 98, 106)
INK = (235, 235, 235)
S = 8


def tex(path):
    return Image.open(os.path.join(TEX, path)).convert("RGBA")


def up(img, k=S):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def frames(img, h):
    return [img.crop((0, i * h, img.width, (i + 1) * h)) for i in range(img.height // h)]


class Sheet:
    def __init__(self, width):
        self.width = width
        self.items = []     # (y, image or text)
        self.y = 12

    def title(self, text):
        self.items.append((self.y, text, None))
        self.y += 26

    def row(self, images, gap=14, bg=None, pad=6):
        """Place images left to right on one row; returns nothing. `bg` paints a panel behind each."""
        h = max(i.height for i in images) + 2 * pad
        self.items.append((self.y, images, (gap, bg, pad)))
        self.y += h + 10

    def render(self):
        wide = self.width
        for _, what, extra in self.items:
            if not isinstance(what, str):
                gap, _, pad = extra
                wide = max(wide, 24 + sum(i.width + gap for i in what) + pad)
        out = Image.new("RGB", (wide, self.y + 6), BG)
        d = ImageDraw.Draw(out)
        for y, what, extra in self.items:
            if isinstance(what, str):
                d.text((12, y), what, fill=INK)
                continue
            gap, bg, pad = extra
            x = 12
            h = max(i.height for i in what) + 2 * pad
            for im in what:
                if bg:
                    d.rectangle([x - pad, y, x + im.width + pad - 1, y + h - 1], fill=bg)
                rgba = im.convert("RGBA")
                out.paste(rgba, (x, y + pad + (h - 2 * pad - im.height) // 2), rgba)
                x += im.width + gap
        return out


def shadow(g):
    s = g.copy()
    px = s.load()
    for y in range(s.height):
        for x in range(s.width):
            r, gg, b, a = px[x, y]
            if a:
                px[x, y] = (r // 4, gg // 4, b // 4, 255)
    return s


def glyph_strip(glyphs, bg, k):
    """The glyphs in a row at 1x with their text shadow, advance = widest column + 1 (the font rule)."""
    adv = [(g.getbbox() or (0, 0, 0, 0))[2] + 1 for g in glyphs]
    im = Image.new("RGB", (sum(adv) + 6, 12), bg)
    x = 3
    for g, a in zip(glyphs, adv):
        sh = shadow(g)
        im.paste(sh, (x + 1, 1), sh)
        im.paste(g, (x, 1), g)
        x += a
    return im.resize((im.width * k, im.height * k), Image.NEAREST)


def build(path):
    r = ModelRenderer([RES])
    sheet = Sheet(900)

    # ---- hearth models
    sheet.title("HEARTH  proficiency:hearth   stage 1..6 (left to right), lit (frame 0) above, smouldering below; isometric render of the shipped model JSON")
    for sm in (False, True):
        sheet.row([r.render("proficiency:block/hearth_stage_%d%s" % (n, "_smouldering" if sm else ""), scale=11,
                            background=(*PANEL, 255)) for n in range(1, 7)], gap=14)
    sheet.title("same, seen from the north-east, animation frame 2")
    sheet.row([r.render("proficiency:block/hearth_stage_%d" % n, scale=11, azimuth=135, frame=2,
                        background=(*PANEL, 255)) for n in range(1, 7)], gap=14)

    # ---- hearth textures, 8x
    sheet.title("HEARTH textures at 8x: collar atlas 32x32 | ember logs (4 frames) | smouldering logs | flame (4 frames of 16x16)")
    for n in range(1, 7):
        row = [up(tex("block/hearth_stage_%d_collar.png" % n))]
        row += [up(f) for f in frames(tex("block/hearth_stage_%d_logs.png" % n), 16)]
        row += [up(tex("block/hearth_smouldering_logs_%d.png" % n))]
        row += [up(f) for f in frames(tex("block/hearth_stage_%d_fire.png" % n), 16)]
        sheet.row(row, gap=10, bg=PANEL)

    # ---- trophy
    sheet.title("SIEGE TROPHY  proficiency:siege_trophy   tier 3..6, front view (facing north) | three-quarter view | atlas 8x")
    sheet.row([r.render("proficiency:block/siege_trophy_tier_%d" % t, scale=18, azimuth=180, pitch=0,
                        background=(*PANEL, 255)) for t in range(3, 7)], gap=14)
    sheet.row([r.render("proficiency:block/siege_trophy_tier_%d" % t, scale=18, azimuth=150, pitch=20,
                        background=(*PANEL, 255)) for t in range(3, 7)], gap=14)
    sheet.row([up(tex("block/siege_trophy_tier_%d.png" % t)) for t in range(3, 7)], gap=10, bg=PANEL)

    # ---- items and the skill icon
    sheet.title("ITEMS and SKILL ICON at 8x, then 1x and 3x on a dark and a grey tile")
    names = [("item/siege_horn.png", "siege_horn"), ("item/siege_trophy.png", "siege_trophy item"),
             ("gui/skill/homebuilder.png", "homebuilder icon"), ("gui/skill/decorating.png", "(decorating, for scale)"),
             ("item/trail_ration.png", "(trail ration, for scale)")]
    row = []
    for p, _ in names:
        row.append(up(tex(p)))
    sheet.row(row, gap=14, bg=PANEL)
    small = []
    for p, _ in names:
        t = tex(p)
        tile = Image.new("RGBA", (16 * 4 + 8, 16 * 3 + 16), (24, 24, 28, 255))
        tile.paste(t, (4, 4), t)
        t3 = up(t, 3)
        tile.paste(t3, (4 + 20, 4), t3)
        small.append(tile)
        tile2 = Image.new("RGBA", (16 * 4 + 8, 16 * 3 + 16), (139, 139, 139, 255))
        tile2.paste(t, (4, 4), t)
        tile2.paste(t3, (4 + 20, 4), t3)
        small.append(tile2)
    sheet.row(small, gap=6)

    # ---- glyphs
    sheet.title("HUD GLYPHS  U+E010..E018 Hearth Warmth frames 0..8 | U+E019 siege, E01A tales scroll, E01B provisioned, E01C lock, E01D bell, E01E house")
    warm, hud = tex("font/hearth_warmth.png"), tex("font/hearth_hud.png")
    g_warm = [warm.crop((i * 9, 0, i * 9 + 9, 9)) for i in range(warm.width // 9)]
    g_hud = [hud.crop((i * 9, 0, i * 9 + 9, 9)) for i in range(hud.width // 9)]
    sheet.row([up(g, S + 2) for g in g_warm], gap=10, bg=PANEL)
    sheet.row([up(g, S + 2) for g in g_hud], gap=10, bg=PANEL)
    sheet.title("the same glyphs as the game draws them (1x with the text shadow, scaled 6x), on dark, grass, stone, sky, snow, wood")
    for bg in ((28, 28, 32), (92, 140, 60), (125, 125, 125), (120, 170, 230), (235, 240, 245), (150, 110, 70)):
        sheet.row([glyph_strip(g_warm + g_hud, bg, 6)], gap=0, pad=2)
    streak, star = tex("font/streak.png"), tex("font/star.png")
    sheet.title("for scale: the existing streak chevrons U+E000 and star U+E001, next to the last two Warmth frames")
    sheet.row([glyph_strip([streak, star] + g_warm[7:9], (28, 28, 32), 6)], gap=0, pad=2)

    img = sheet.render()
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("sheet", path, img.size)


# ---------------------------------------------------------------------------------------
# icon sheet: the Hearth's GUI icons next to eight skill icons at 1x, 2x, 4x
# ---------------------------------------------------------------------------------------
SKILL_REFS = ("swords", "mining", "farming", "cooking", "decorating", "masonry", "social", "spelunking")
SLOT = (139, 139, 139)          # an inventory slot
DARK = (24, 24, 28)             # the mod's panels
ICON_GROUPS = (("POOLS", "pool/"), ("COMFORT KINDS", "comfort/"), ("STAGE EMBLEMS", "stage/"), ("HEARTH ICONS", ""))


def icon_row(new, ref, k, bg, labels=None):
    """New icons, a divider, the skill icons: every icon scaled k times on a tile of `bg`."""
    pad, gap = max(2, k), max(3, 2 * k)
    side = 16 * k + 2 * pad
    n = len(new) + len(ref)
    width = n * side + (n - 1) * gap + 3 * gap
    out = Image.new("RGB", (width, side + (12 if labels else 0)), BG)
    d = ImageDraw.Draw(out)
    x = 0
    for i, im in enumerate(new + ref):
        if i == len(new):
            x += 3 * gap
            d.line([x - 2 * gap, 0, x - 2 * gap, side - 1], fill=(120, 122, 130))
        d.rectangle([x, 0, x + side - 1, side - 1], fill=bg)
        big = up(im, k)
        out.paste(big, (x + pad, pad), big)
        if labels and i < len(new):
            d.text((x, side + 1), labels[i][:max(4, side // 6)], fill=INK)
        x += side + gap
    return out


def build_icons(path, names):
    """`names` are the icon paths under textures/gui/hearth/ without the extension (gen_hearth_art.icon_names)."""
    refs = [tex(f"gui/skill/{s}.png") for s in SKILL_REFS]
    blocks = []
    for title, prefix in ICON_GROUPS:
        group = [n for n in names if (n.startswith(prefix) if prefix else "/" not in n)]
        if not group:
            continue
        new = [tex(f"gui/hearth/{n}.png") for n in group]
        labels = [n.split("/")[-1] for n in group]
        rows = [("%s: new icons, then 8 skill icons (swords, mining, farming, cooking, decorating, masonry, social, spelunking)" % title, None),
                (None, icon_row(new, refs, 4, SLOT, labels)),
                (None, icon_row(new, refs, 2, SLOT)),
                (None, icon_row(new, refs, 1, SLOT)),
                (None, icon_row(new, refs, 2, DARK)),
                (None, icon_row(new, refs, 1, DARK))]
        blocks.append(rows)
    flat = [r for b in blocks for r in b]
    width = max((im.width for _, im in flat if im), default=900) + 24
    height = 12 + sum(26 if t else im.height + 8 for t, im in flat) + 16 * len(blocks)
    out = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(out)
    y = 12
    for b in blocks:
        for t, im in b:
            if t:
                d.text((12, y), t, fill=INK)
                y += 26
            else:
                out.paste(im, (12, y))
                y += im.height + 8
        y += 16
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)
    print("icon sheet", path, out.size)
