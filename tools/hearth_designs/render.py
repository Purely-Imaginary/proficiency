#!/usr/bin/env python3
"""Isometric previews of Hearth designs. Usage: render.py designs.json outdir
Writes <lane>-<stage>.png (18 files) and sheet.png (rows = lanes, columns = stages)."""
import json, os, sys
from PIL import Image, ImageDraw, ImageFont

ROMAN = ["I", "II", "III", "IV", "V", "VI"]
# role -> (colour, box (x0,x1,y0,y1,z0,z1) as cell fractions, extra)
FULL = (0, 1, 0, 1, 0, 1)
HALF = (0, 1, 0, 1, 0, .5)
def thin(w, h=1.0, z0=0): return (.5 - w / 2, .5 + w / 2, .5 - w / 2, .5 + w / 2, z0, z0 + h)
STYLE = {
    "S": ((140, 140, 146), [FULL]), "s": ((186, 186, 192), [HALF]),
    "W": ((108, 78, 46), [FULL]), "P": ((200, 164, 104), [FULL]), "w": ((214, 182, 126), [HALF]),
    "i": ((222, 226, 230), [FULL]), "c": ((214, 116, 62), [FULL]), "g": ((246, 205, 60), [FULL]),
    "b": ((90, 94, 100), [(.45, .55, 0, 1, 0, 1), (0, 1, .45, .55, 0, 1)]),
    "h": ((70, 72, 78), [thin(.14)]),
    "l": ((255, 214, 90), [(.3, .7, .3, .7, .25, .7), (.45, .55, .45, .55, .7, .85)]),
    "B": ((236, 190, 50), [(.3, .7, .3, .7, .35, .85), (.45, .55, .45, .55, .85, 1)]),
    "N": ((196, 40, 44), [(.44, .56, .1, .9, .0, 1)]),
    "r": ((228, 130, 60), [thin(.14)]),
    "k": ((58, 52, 66), [FULL]),
    "a": ((72, 74, 82), [(.2, .8, .2, .8, 0, .25), (.35, .65, .3, .7, .25, .5), (.1, .9, .25, .75, .5, .8)]),
    "u": ((78, 82, 90), [(.05, .95, .05, .95, 0, .8)]),
    "v": ((64, 130, 235), [(0, 1, 0, 1, 0, .9)]), "V": ((255, 112, 20), [(0, 1, 0, 1, 0, .9)]),
    "f": ((176, 98, 70), [(.3, .7, .3, .7, 0, .35), (.4, .6, .4, .6, .35, .6)]),
    "x": ((150, 104, 58), [(.08, .92, .08, .92, 0, .8)]),
    "C": ((242, 232, 205), [(.4, .6, .4, .6, 0, .45), (.46, .54, .46, .54, .45, .6)]),
    "m": ((214, 96, 138), [(0, 1, 0, 1, 0, .07)]),
    "G": ((170, 215, 238), [(0, 1, 0, 1, 0, 1)]),
    "z": ((228, 222, 204), [(.25, .75, .25, .75, 0, .5)]),
    "I": ((136, 188, 244), [FULL]), "n": ((244, 247, 252), [FULL]), "q": ((86, 122, 52), [FULL]),
    "Y": ((190, 66, 24), [FULL]), "O": ((34, 22, 54), [FULL]), "L": ((58, 132, 52), [FULL]),
    "F": ((238, 120, 30), [(.1, .9, .1, .9, 0, .15), (.3, .7, .3, .7, .15, .7)]),
}
def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in c)

def palette_styles(pal):
    st = {k: (v[0], list(v[1])) for k, v in STYLE.items()}
    if not pal: return st
    def has(key, *words): return any(w in pal.get(key, "") for w in words)
    sc = (146, 146, 150)
    for words, c in ((("quartz",), (236, 232, 226)), (("diorite",), (214, 214, 218)), (("mossy",), (118, 138, 104)),
                     (("blackstone",), (60, 54, 70)), (("deepslate",), (86, 86, 96)), (("stone_brick",), (150, 150, 154)),
                     (("cobbled",), (110, 110, 116))):
        if has("S", *words): sc = c; break
    st["S"] = (sc, st["S"][1]); st["s"] = (shade(sc, 1.15), st["s"][1])
    wc = (170, 135, 80)
    for words, c in ((("spruce",), (112, 82, 52)), (("dark_oak",), (76, 52, 32)), (("crimson",), (134, 46, 70)),
                     (("cherry",), (228, 170, 182)), (("jungle",), (160, 116, 78)), (("oak",), (172, 136, 82))):
        if has("P", *words): wc = c; break
    lc = (108, 78, 46)
    for words, c in ((("spruce_log",), (78, 56, 36)), (("dark_oak",), (60, 42, 26)), (("crimson",), (110, 40, 62)),
                     (("cherry",), (190, 130, 146)), (("jungle",), (120, 90, 56))):
        if has("W", *words): lc = c; break
    st["W"] = (lc, st["W"][1]); st["P"] = (wc, st["P"][1]); st["w"] = (shade(wc, 1.1), st["w"][1])
    cols = {"pink": (236, 140, 170), "red": (190, 44, 44), "black": (44, 44, 52), "white": (238, 238, 238),
            "light_blue": (110, 184, 232), "gray": (120, 120, 126), "green": (80, 140, 60), "purple": (150, 80, 200), "moss": (92, 124, 52)}
    def named(key, default):
        v = pal.get(key, "")
        for k in ("light_blue", "pink", "red", "black", "white", "gray", "green", "purple", "moss"):
            if k in v: return cols[k]
        return default
    st["m"] = (named("m", st["m"][0]), st["m"][1]); st["N"] = (named("N", st["N"][0]), st["N"][1])
    st["G"] = (named("G", st["G"][0]), st["G"][1]); st["C"] = (named("C", st["C"][0]), st["C"][1])
    if has("c", "oxidized"): st["c"] = ((70, 164, 142), st["c"][1])
    elif has("c", "weathered"): st["c"] = ((112, 146, 134), st["c"][1])
    return st

def lane_cells(stage):
    out = []
    for z, rows in stage["layers"].items():
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch in STYLE:
                    out.append((int(z), c - 4, r - 4, ch))  # x east, y south, centred on the fire
    return out

def draw_stage(stage, A=22, ss=2, title=None, pal=None):
    STYLE_ = palette_styles(pal)
    A *= ss; B = A // 2; C = int(A * 1.12)
    W = int(20 * A); H = int(9.4 * B * 2 + 8.4 * C + 70 * ss)
    img = Image.new("RGBA", (W, H), (28, 30, 38, 255)); dr = ImageDraw.Draw(img)
    ox, oy = W // 2, H - int(10 * B * 2) + 18 * ss + int(8 * C) - 18 * ss - 6 * B
    oy = int(H - (9 * B) - 22 * ss - 0.0 * C)  # screen y of the (x+y=0, z=-1) origin... adjusted below
    def P(x, y, z):  # world (cells) -> screen
        return (ox + (x - y) * A, oy + (x + y) * B - (z + 1) * C - 4 * B - 14 * ss)
    # ground diamond hint
    g = [P(-4.5, -4.5, -1), P(4.5, -4.5, -1), P(4.5, 4.5, -1), P(-4.5, 4.5, -1)]
    dr.polygon(g, fill=(36, 40, 50), outline=(52, 58, 70))
    items = sorted(lane_cells(stage), key=lambda t: (t[0] + t[1] + t[2], t[0], t[1]))
    for z, x, y, ch in items:
        col, boxes = STYLE_[ch]
        if ch in "vVG":
            (x0, x1, y0, y1, z0, z1), = boxes
            X0, X1, Y0, Y1, Z0, Z1 = x + x0 - .5, x + x1 - .5, y + y0 - .5, y + y1 - .5, z + z0, z + z1
            pts_ = [P(xx, yy, zz) for xx in (X0, X1) for yy in (Y0, Y1) for zz in (Z0, Z1)]
            bx0 = int(min(p[0] for p in pts_) - 2 * A); by0 = int(min(p[1] for p in pts_) - 2 * A)
            bx1 = int(max(p[0] for p in pts_) + 2 * A); by1 = int(max(p[1] for p in pts_) + 2 * A)
            bx0, by0, bx1, by1 = max(0, bx0), max(0, by0), min(img.width, bx1), min(img.height, by1)
            ov = Image.new("RGBA", (max(1, bx1 - bx0), max(1, by1 - by0)), (0, 0, 0, 0)); od = ImageDraw.Draw(ov)
            sh = lambda poly: [(q[0] - bx0, q[1] - by0) for q in poly]
            al = {"v": 150, "V": 255, "G": 95}[ch]
            top = [P(X0, Y0, Z1), P(X1, Y0, Z1), P(X1, Y1, Z1), P(X0, Y1, Z1)]
            left = [P(X0, Y1, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X0, Y1, Z1)]
            right = [P(X1, Y0, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X1, Y0, Z1)]
            for poly, f in ((left, .8), (right, .6), (top, 1.12)):
                od.polygon(sh(poly), fill=shade(col, f) + (al,), outline=shade(col, .6) + (min(255, al + 40),), width=ss)
            if ch == "V":
                cx = sum(p[0] for p in top) / 4; cy = sum(p[1] for p in top) / 4
                for rad, a in ((1.9, 40), (1.4, 70), (.9, 110)):
                    od.ellipse([cx - bx0 - rad * A, cy - by0 - rad * B, cx - bx0 + rad * A, cy - by0 + rad * B], fill=(255, 170, 50, a))
                od.polygon(sh([((cx + (p[0] - cx) * .6), (cy + (p[1] - cy) * .6)) for p in top]), fill=(255, 220, 90, 255))
            reg = Image.alpha_composite(img.crop((bx0, by0, bx1, by1)), ov)
            img.paste(reg, (bx0, by0)); dr = ImageDraw.Draw(img)
            continue
        for (x0, x1, y0, y1, z0, z1) in boxes:
            X0, X1, Y0, Y1, Z0, Z1 = x + x0 - .5, x + x1 - .5, y + y0 - .5, y + y1 - .5, z + z0, z + z1
            top = [P(X0, Y0, Z1), P(X1, Y0, Z1), P(X1, Y1, Z1), P(X0, Y1, Z1)]
            left = [P(X0, Y1, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X0, Y1, Z1)]
            right = [P(X1, Y0, Z0), P(X1, Y1, Z0), P(X1, Y1, Z1), P(X1, Y0, Z1)]
            lw = max(1, ss // 2 + (0 if ch in "bhrN" else 1))
            glow = ch in "lF"
            tcol = col if glow else shade(col, 1.15)
            for poly, f in ((left, 0.8), (right, 0.58), (top, 1.0)):
                fc = col if glow else (shade(col, f) if poly is not top else tcol)
                if ch == "k" and poly is left:
                    fc = (58, 52, 66)
                dr.polygon(poly, fill=fc, outline=shade(col, .45) if ch not in "bhrN" else None, width=lw)
            if ch == "k":  # lodestone stripe
                for poly in (left, right):
                    a, b, c2, d2 = poly
                    mid = lambda p, q, t: (p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t)
                    dr.polygon([mid(a, d2, .4), mid(b, c2, .4), mid(b, c2, .6), mid(a, d2, .6)], fill=(190, 60, 70))
            if ch == "u":
                t0 = top; cx = sum(p[0] for p in t0) / 4; cy = sum(p[1] for p in t0) / 4
                dr.polygon([((cx + (p[0] - cx) * .7), (cy + (p[1] - cy) * .7)) for p in t0], fill=(40, 90, 150))
    img = img.convert("RGB").resize((W // ss, H // ss), Image.LANCZOS)
    return img

def font(n):
    for p in ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", "/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf"):
        if os.path.exists(p): return ImageFont.truetype(p, n)
    return ImageFont.load_default()

def count(stage):
    n = 0
    for rows in stage["layers"].values():
        for row in rows:
            n += sum(1 for ch in row if ch in STYLE and ch not in "Fv V")
    return n

def main():
    if len(sys.argv) != 3: sys.exit(__doc__)
    data = json.load(open(sys.argv[1])); out = sys.argv[2]
    os.makedirs(out, exist_ok=True)
    CW, CH = 300, 250; LABW = 140; HEAD = 36
    sheet = Image.new("RGB", (LABW + 6 * CW, HEAD + len(data["lanes"]) * CH), (20, 22, 28)); sd = ImageDraw.Draw(sheet)
    f1, f2 = font(18), font(14)
    from PIL import ImageChops
    imgs = {(i, j): draw_stage(st, pal=l.get("palette")) for i, l in enumerate(data["lanes"]) for j, st in enumerate(l["stages"])}
    box = None
    for im in imgs.values():
        b = ImageChops.difference(im, Image.new("RGB", im.size, (28, 30, 38))).getbbox()
        box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    pad = 12
    box = (max(0, box[0] - pad), max(0, box[1] - pad), box[2] + pad, box[3] + pad)
    imgs = {k: v.crop(box) for k, v in imgs.items()}
    for j in range(6): sd.text((LABW + j * CW + CW // 2 - 14, 8), ROMAN[j], fill=(230, 230, 235), font=f1)
    for i, lane in enumerate(data["lanes"]):
        sd.text((10, HEAD + i * CH + CH // 2 - 10), lane["id"].capitalize(), fill=(230, 230, 235), font=f1)
        for j, st in enumerate(lane["stages"]):
            img = imgs[(i, j)]
            img.save(os.path.join(out, f"{lane['id']}-{st['stage']}.png"))
            s = min((CW - 10) / img.width, (CH - 34) / img.height)
            th = img.resize((max(1, int(img.width * s)), max(1, int(img.height * s))), Image.LANCZOS)
            x0 = LABW + j * CW + (CW - th.width) // 2
            sheet.paste(th, (x0, HEAD + i * CH + (CH - 24 - th.height) // 2))
            sd.text((LABW + j * CW + 8, HEAD + i * CH + CH - 22), f"{ROMAN[j]}  {count(st)} blocks", fill=(150, 156, 170), font=f2)
    sheet.save(os.path.join(out, "sheet.png"))
    print("wrote", out)

main()
