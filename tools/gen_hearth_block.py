"""Grander Hearth block models + flame/accent textures (supersedes the block part of gen_hearth_art.py).

    python3 tools/gen_hearth_block.py [--sheet out.png]

Writes models/block/hearth_stage_N{,_smouldering}.json, textures/block/hearth_stage_N_fire.png(+mcmeta,
frame size per stage, 1 px per block unit) and textures/block/hearth_accent.png (iron, copper, gold,
ember, ash swatches). Collar and log textures are reused as they are.
Footprint rule (the approved building designs keep blocks in the four diagonal cells around the fire):
every element stays in the centre cell (0..16 on x and z) plus at most 6 px into each EDGE neighbour
(x in -6..22 only where z is in 0..16, z in -6..22 only where x is in 0..16). Height is free up to 32.
Fire frames are 16 px wide and 16 or 32 high (the block atlas needs multiples of 16 for mip levels).
Do not re-run gen_hearth_art.py afterwards for the block part: it would restore the old models.
"""
import json, math, os, random, sys
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common", "src", "main", "resources", "assets", "proficiency")
MODELS = os.path.join(ROOT, "models", "block")
TEX = os.path.join(ROOT, "textures", "block")
NS = "proficiency:block/"

# ---------------------------------------------------------------- accent texture (4x4 swatches, 1 uv unit = 1 px)
def swatch(base, hi, lo):
    return (base, hi, lo)
ACC = {  # name -> (cx, cy, base, hi, lo)
    "iron": (0, 0, (107, 111, 120), (150, 155, 165), (66, 69, 78)),
    "dark": (4, 0, (44, 46, 54), (72, 76, 88), (24, 25, 31)),
    "copper": (8, 0, (181, 102, 58), (222, 144, 90), (122, 62, 34)),
    "gold": (12, 0, (232, 184, 32), (255, 230, 102), (168, 124, 16)),
    "ember": (0, 4, (255, 138, 31), (255, 214, 74), (196, 56, 12)),
    "ash": (4, 4, (90, 85, 80), (122, 116, 108), (60, 57, 53)),
    "glow": (8, 4, (255, 200, 60), (255, 246, 196), (232, 118, 30)),
}
def write_accent():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for name, (cx, cy, base, hi, lo) in ACC.items():
        for y in range(4):
            for x in range(4):
                c = base
                if x == 0 and y == 0 or (x, y) == (1, 0) or (x, y) == (0, 1):
                    c = hi
                elif x == 3 and y == 3 or (x, y) == (3, 2) or (x, y) == (2, 3):
                    c = lo
                img.putpixel((cx + x, cy + y), c + (255,))
    img.save(os.path.join(TEX, "hearth_accent.png"))
def acc(name, w=4, h=4):
    cx, cy = ACC[name][:2]
    return [cx, cy, cx + min(w, 4), cy + min(h, 4)]

# ---------------------------------------------------------------- flame textures
RAMP = {
    "warm": [(179, 32, 10), (232, 80, 26), (255, 154, 28), (255, 210, 63), (255, 246, 196)],
    "gold": [(214, 86, 26), (255, 140, 30), (255, 200, 56), (255, 232, 110), (255, 254, 228)],
}
FLAME = {  # stage: (painted width, painted height, frame height, painted bottom above the frame bottom, quad bottom y)
    1: (8, 8, 16, 0, 3), 2: (10, 11, 16, 0, 4), 3: (12, 14, 16, 0, 7),
    4: (14, 18, 32, 8, 0), 5: (16, 19, 32, 9, 0), 6: (16, 22, 32, 10, 0),
}
RAMPS = {1: "warm", 2: "warm", 3: "warm", 4: "warm", 5: "gold", 6: "gold"}
def write_fire(stage):
    pw, ph, FH, off, _ = FLAME[stage]
    ramp = RAMPS[stage]
    W = 16
    rnd = random.Random(stage * 77)
    sheet = Image.new("RGBA", (W, FH * 4), (0, 0, 0, 0))
    phs = rnd.random() * 6
    for f in range(4):
        for xi in range(pw):
            x = xi + (W - pw) // 2
            cx = (xi + 0.5 - pw / 2) / (pw / 2)
            env = max(0.0, 1 - abs(cx) ** 1.5)
            tongue = 0.5 + 0.5 * math.sin(xi * 1.9 + f * 1.57 + phs)
            tongue2 = 0.5 + 0.5 * math.sin(xi * 0.8 - f * 0.9 + phs * 2)
            h = (ph - 2) * (0.22 + 0.78 * env) * (0.72 + 0.18 * tongue + 0.10 * tongue2)
            if abs(cx) < 0.25:
                h = min(ph - 1, h + 1.5 + (f % 2))
            for y in range(int(h)):
                t = y / max(h, 1)
                core = env * (1 - 0.65 * t)
                if t > 0.82 and rnd.random() < 0.45:
                    continue
                k = min(4, int(core * 5.2))
                if y < 2 and env > 0.15:
                    k = max(k, 3)
                sheet.putpixel((x, f * FH + FH - 1 - off - y), RAMP[ramp][k] + (255,))
        for _ in range(2 if stage > 2 else 1):  # sparks above the tips
            sx = rnd.randrange((W - pw) // 2 + 1, (W + pw) // 2 - 1)
            sy = FH - off - ph + rnd.randrange(0, 2) - 1
            if 0 <= sy and sheet.getpixel((sx, f * FH + sy))[3] == 0:
                sheet.putpixel((sx, f * FH + sy), RAMP[ramp][3] + (255,))
    path = os.path.join(TEX, f"hearth_stage_{stage}_fire.png")
    sheet.save(path)
    with open(path + ".mcmeta", "w") as fh:
        json.dump({"animation": {"frametime": 3, "width": W, "height": FH}}, fh, indent=2)

# ---------------------------------------------------------------- model building
def r(v):
    return int(v) if float(v) == int(v) else round(float(v), 3)

class Model:
    def __init__(self, stage, cold):
        self.stage, self.cold, self.el = stage, cold, []
    def add(self, frm, to, faces, **extra):
        e = {"from": [r(v) for v in frm], "to": [r(v) for v in to]}
        e.update(extra)
        e["faces"] = faces
        self.el.append(e)
    def stone(self, frm, to, top=True, pillar=False):
        """Collar-textured box. Side tile tops at v 0 (gold trim rows live there), top is the plain fill."""
        w = lambda a: min(abs(a), 16) / 2
        (x1, y1, z1), (x2, y2, z2) = frm, to
        h = min(y2 - y1, 16) / 2
        if pillar:
            sx = sz = [8, 0, 9, min(h, 8)]
        else:
            sx = [0, 0, w(x2 - x1), h]
            sz = [0, 0, w(z2 - z1), h]
        f = {"north": {"uv": sx, "texture": "#collar"}, "south": {"uv": sx, "texture": "#collar"},
             "east": {"uv": sz, "texture": "#collar"}, "west": {"uv": sz, "texture": "#collar"}}
        if top:
            f["up"] = {"uv": [10, 2, 14, 6], "texture": "#collar"}
        self.add(frm, to, f)
    def metal(self, frm, to, mat, top=True):
        if self.cold and mat == "ember":
            mat = "ash"
        u = acc(mat)
        f = {k: {"uv": u, "texture": "#acc"} for k in ("north", "south", "east", "west")}
        if top:
            f["up"] = {"uv": u, "texture": "#acc"}
        self.add(frm, to, f)
    def log(self, frm, to, axis):
        (x1, y1, z1), (x2, y2, z2) = frm, to
        L = (z2 - z1) if axis == "z" else (x2 - x1)
        T = y2 - y1
        T2 = (x2 - x1) if axis == "z" else (z2 - z1)
        if axis == "z":
            f = {"east": {"uv": [0, 0, L, T], "texture": "#logs"}, "west": {"uv": [0, 0, L, T], "texture": "#logs"},
                 "up": {"uv": [0, 0, L, T2], "rotation": 90, "texture": "#logs"},
                 "north": {"uv": [0, 4, T2, 4 + T], "texture": "#logs"}, "south": {"uv": [0, 4, T2, 4 + T], "texture": "#logs"}}
        else:
            f = {"north": {"uv": [0, 0, L, T], "texture": "#logs"}, "south": {"uv": [0, 0, L, T], "texture": "#logs"},
                 "up": {"uv": [0, 0, L, T2], "texture": "#logs"},
                 "east": {"uv": [0, 4, T2, 4 + T], "texture": "#logs"}, "west": {"uv": [0, 4, T2, 4 + T], "texture": "#logs"}}
        self.add(frm, to, f)
    def ring(self, lo, hi, y1, y2, th, **kw):
        """Four wall boxes around the square lo..hi, thickness th, north/south span the full width."""
        self.stone((lo, y1, lo), (hi, y2, lo + th), **kw)
        self.stone((lo, y1, hi - th), (hi, y2, hi), **kw)
        self.stone((lo, y1, lo + th), (lo + th, y2, hi - th), **kw)
        self.stone((hi - th, y1, lo + th), (hi, y2, hi - th), **kw)
    def mring(self, lo, hi, y1, y2, th, mat):
        self.metal((lo, y1, lo), (hi, y2, lo + th), mat)
        self.metal((lo, y1, hi - th), (hi, y2, hi), mat)
        self.metal((lo, y1, lo + th), (lo + th, y2, hi - th), mat)
        self.metal((hi - th, y1, lo + th), (hi, y2, hi - th), mat)
    def band(self, lo, hi, y1, y2, mat):
        """A thin metal band hugging the outside of a wall ring (0.5 proud)."""
        self.metal((lo - .5, y1, lo - .5), (hi + .5, y2, lo), mat, top=False)
        self.metal((lo - .5, y1, hi), (hi + .5, y2, hi + .5), mat, top=False)
        self.metal((lo - .5, y1, lo), (lo, y2, hi), mat, top=False)
        self.metal((hi, y1, lo), (hi + .5, y2, hi), mat, top=False)
    def logs(self, dy):
        self.log((3, 1 + dy, 2.5), (6, 4 + dy, 13.5), "z")
        self.log((10, 1 + dy, 2.5), (13, 4 + dy, 13.5), "z")
        self.log((2.5, 4 + dy, 3.2), (13.5, 7 + dy, 6.2), "x")
        self.log((2.5, 4 + dy, 9.8), (13.5, 7 + dy, 12.8), "x")
    def bed(self, dy):
        if dy <= 0:
            return
        u = acc("ash" if self.cold else "ember")
        f = {k: {"uv": u, "texture": "#acc"} for k in ("north", "south", "east", "west", "up")}
        self.add((2, 1, 2), (14, 1 + dy, 14), f)
    def flame(self, y0, y1, w):
        rot = {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}
        full = {"uv": [0, 0, 16, 16], "texture": "#fire"}
        self.add((8 - w / 2, y0, 8), (8 + w / 2, y1, 8), {"north": dict(full), "south": dict(full)}, rotation=rot, shade=False)
        self.add((8, y0, 8 - w / 2), (8, y1, 8 + w / 2), {"west": dict(full), "east": dict(full)}, rotation=dict(rot), shade=False)
    def spike(self, cx, cz, top, base=2.0, y0=0):
        """Gold crown spike: base block, narrower shaft, ember tip."""
        a = base / 2
        t1 = y0 + (top - y0) * 0.45
        t2 = y0 + (top - y0) * 0.8
        self.metal((cx - a, y0, cz - a), (cx + a, t1, cz + a), "gold")
        self.metal((cx - .7, t1, cz - .7), (cx + .7, t2, cz + .7), "gold")
        self.metal((cx - .45, t2, cz - .45), (cx + .45, top, cz + .45), "ember")
    def json(self):
        d = self.stage
        tex = {"particle": NS + f"hearth_stage_{d}_collar", "collar": NS + f"hearth_stage_{d}_collar",
               "logs": NS + (f"hearth_smouldering_logs_{d}" if self.cold else f"hearth_stage_{d}_logs"),
               "acc": NS + "hearth_accent"}
        if not self.cold:
            tex["fire"] = NS + f"hearth_stage_{d}_fire"
        return {"render_type": "minecraft:cutout", "textures": tex, "elements": self.el}

def arms(m, y1, y2, r, w, tier_top=True):
    """Four arms of a cross: r px out of the centre cell, w wide, on the axes only."""
    a, b = 8 - w / 2, 8 + w / 2
    m.stone((-r, y1, a), (0, y2, b))
    m.stone((16, y1, a), (16 + r, y2, b))
    m.stone((a, y1, -r), (b, y2, 0))
    m.stone((a, y1, 16), (b, y2, 16 + r))

def faceband(m, y1, y2, mat):
    """A thin metal band 0.5 px proud of each wall face of the 0..16 ring, never over the corners' diagonals."""
    m.metal((0, y1, -.5), (16, y2, 0), mat, top=False)
    m.metal((0, y1, 16), (16, y2, 16.5), mat, top=False)
    m.metal((-.5, y1, 0), (0, y2, 16), mat, top=False)
    m.metal((16, y1, 0), (16.5, y2, 16), mat, top=False)

def rim(m, y1, y2, out, inner, mat):
    """A flared brazier rim: four boxes along the edges that bulge `out` px beyond the ring, octagon-like."""
    m.metal((0, y1, -out), (16, y2, inner), mat)
    m.metal((0, y1, 16 - inner), (16, y2, 16 + out), mat)
    m.metal((-out, y1, inner), (inner, y2, 16 - inner), mat)
    m.metal((16 - inner, y1, inner), (16 + out, y2, 16 - inner), mat)

def corner_posts(m, y1, y2):
    for cx, cz in ((0, 0), (13, 0), (0, 13), (13, 13)):
        m.stone((cx, y1, cz), (cx + 3, y2, cz + 3), pillar=True)

def firedogs(m, mat, x_out, x_in):
    """Two andirons on the west and east arms: a low bar along z, a tall front post with a ball, a short back post."""
    for side in (0, 1):
        x = x_out if side == 0 else 16 + x_in
        zf = 11 if side == 0 else 3.5
        zb = 3.5 if side == 0 else 11
        m.metal((x, 2, 3.5), (x + 1.5, 3.2, 12.5), "iron")
        m.metal((x, 3.2, zf), (x + 1.5, 12, zf + 1.5), "iron")
        m.metal((x - .5, 12, zf - .5), (x + 2, 14.5, zf + 2), mat)
        m.metal((x, 3.2, zb), (x + 1.5, 6, zb + 1.5), "iron")
        m.metal((x, 6, zb), (x + 1.5, 6.8, zb + 1.5), "dark" if mat == "iron" else mat)

def build(stage, cold):
    m = Model(stage, cold)
    m.add((2, 0, 2), (14, 1, 14), {"up": {"uv": [4, 4, 16, 16], "texture": "#logs"}})
    if stage == 1:
        m.ring(1, 15, 0, 3, 2)
        dy = 0
    elif stage == 2:
        m.ring(0, 16, 0, 5, 3)
        arms(m, 0, 2, 2, 8)
        for cx, cz in ((0, 0), (13, 0), (0, 13), (13, 13)):
            m.stone((cx, 5, cz), (cx + 3, 6.5, cz + 3))
        dy = 0.5
    elif stage == 3:
        m.stone((0, 0, 0), (16, 2, 16))
        arms(m, 0, 2, 4, 10)
        m.ring(0, 16, 2, 8, 3)
        corner_posts(m, 8, 11)
        m.log((16.3, 2, 4), (18.0, 3.7, 12), "z")
        m.log((18.0, 2, 4), (19.7, 3.7, 12), "z")
        m.log((17.15, 3.7, 4.5), (18.85, 5.4, 11.5), "z")
        m.metal((16.3, 2, 3), (19.7, 6, 4), "iron")
        m.metal((16.3, 2, 12), (19.7, 6, 13), "iron")
        dy = 3.5
    elif stage == 4:
        m.stone((0, 0, 0), (16, 2, 16))
        arms(m, 0, 2, 5, 10)
        m.ring(0, 16, 2, 9, 3)
        corner_posts(m, 9, 13)
        for cx, cz in ((0, 0), (13, 0), (0, 13), (13, 13)):
            m.metal((cx + .5, 13, cz + .5), (cx + 2.5, 14, cz + 2.5), "gold")
        firedogs(m, "iron", -3.5, 2.0)
        for z0 in (-4, 17):                       # gold-capped stone posts on the north and south arms
            m.stone((6.5, 2, z0), (9.5, 6, z0 + 3), pillar=True)
            m.metal((6.5, 6, z0), (9.5, 7, z0 + 3), "gold")
        dy = 4.5
    elif stage == 5:
        m.stone((0, 0, 0), (16, 2, 16))
        arms(m, 0, 2, 6, 12)
        arms(m, 2, 3.5, 4, 8)
        m.ring(0, 16, 2, 10, 3)
        faceband(m, 5.5, 7, "copper")
        rim(m, 10, 11.5, 3, 2.5, "copper")
        m.metal((0, 11.5, -3), (16, 12.2, -1.8), "gold")
        m.metal((0, 11.5, 17.8), (16, 12.2, 19), "gold")
        m.metal((-3, 11.5, 2.5), (-1.8, 12.2, 13.5), "gold")
        m.metal((17.8, 11.5, 2.5), (19, 12.2, 13.5), "gold")
        for cx, cz in ((0, 0), (13, 0), (0, 13), (13, 13)):
            m.stone((cx, 11.5, cz), (cx + 3, 15.5, cz + 3), pillar=True)
            m.metal((cx, 15.5, cz), (cx + 3, 16.5, cz + 3), "gold")
            m.metal((cx + .5, 16.5, cz + .5), (cx + 2.5, 18.5, cz + 2.5), "copper")
        firedogs(m, "copper", -4.8, 3.3)
        dy = 5.5
    else:
        m.stone((0, 0, 0), (16, 2, 16))
        arms(m, 0, 2, 6, 14)
        arms(m, 2, 3.5, 4, 10)
        m.ring(0, 16, 2, 11, 3)
        faceband(m, 6.5, 8.2, "gold")
        rim(m, 11, 12.5, 5, 3, "gold")
        for cx, cz in ((1.5, 1.5), (14.5, 1.5), (1.5, 14.5), (14.5, 14.5)):
            m.spike(cx, cz, 23, base=2.6, y0=12.5)
        for cx, cz in ((8, -3.2), (8, 19.2), (-3.2, 8), (19.2, 8)):
            m.spike(cx, cz, 18, base=2.0, y0=12.5)
        for cx, cz in ((8, -2.5), (8, 18.5), (-2.5, 8), (18.5, 8)):   # ember lanterns on the arms
            m.metal((cx - 1, 3.5, cz - 1), (cx + 1, 5.5, cz + 1), "ember")
        dy = 6.5
    m.bed(dy)
    m.logs(dy)
    if not cold:
        _, _, fh, _, q0 = FLAME[stage]
        m.flame(q0, q0 + fh, 16)
    return m

def main():
    write_accent()
    for s in range(1, 7):
        write_fire(s)
        for cold in (False, True):
            name = f"hearth_stage_{s}{'_smouldering' if cold else ''}.json"
            with open(os.path.join(MODELS, name), "w") as fh:
                json.dump(build(s, cold).json(), fh, indent=2)
                fh.write("\n")
    if "--sheet" in sys.argv:
        sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
        from model_preview import ModelRenderer
        rr = ModelRenderer([os.path.join(ROOT, "..", "..")])
        names = [f"proficiency:block/hearth_stage_{i}" for i in range(1, 7)] + ["proficiency:block/hearth_stage_5_smouldering"]
        ims = [rr.render(n, scale=8, frame=1) for n in names]
        w = max(i.width for i in ims); h = max(i.height for i in ims)
        S = Image.new("RGBA", (w * 4, h * 2), (40, 44, 56, 255))
        for k, i in enumerate(ims):
            S.alpha_composite(i, ((k % 4) * w + (w - i.width) // 2, (k // 4) * h + (h - i.height) // 2))
        S.convert("RGB").save(sys.argv[sys.argv.index("--sheet") + 1])

if __name__ == "__main__":
    main()
