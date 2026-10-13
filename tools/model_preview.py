"""A tiny software renderer for Minecraft block model JSON, for contact sheets only.

It reads the real model, blockstate-free JSON and texture files, so a sheet made with it shows what
the shipped assets contain. It is not a game renderer: no ambient occlusion, no mip-maps, no
translucency (a texel is opaque or skipped). It follows vanilla's face and UV rules (FaceBakery,
BlockFaceUV), the element rotation with "rescale", the per-face shade, and the first frame of an
animated texture (or any frame you ask for).

    from model_preview import ModelRenderer
    r = ModelRenderer([assets_root_a, assets_root_b])        # directories that hold assets/<ns>/...
    img = r.render("proficiency:block/hearth_stage_3", scale=12, frame=0, azimuth=45)

Pure Pillow, no numpy. A 12 px/unit render of a 20-element model takes a second or two.
"""
import json
import math
import os

from PIL import Image

# Vertex order of each face, as FaceBakery builds it: indexes into (x1,y1,z1) / (x2,y2,z2).
# Each entry is (x_is_max, y_is_max, z_is_max) per vertex.
FACE_VERTS = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
FACE_SHADE = {"down": 0.5, "up": 1.0, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}
FACE_NORMAL = {"down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1), "south": (0, 0, 1),
               "west": (-1, 0, 0), "east": (1, 0, 0)}


def default_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        "north": [16 - x2, 16 - y2, 16 - x1, 16 - y1],
        "south": [x1, 16 - y2, x2, 16 - y1],
        "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1],
        "west": [z1, 16 - y2, z2, 16 - y1],
        "up": [x1, z1, x2, z2],
        "down": [x1, 16 - z2, x2, 16 - z1],
    }[face]


class ModelRenderer:
    def __init__(self, roots):
        self.roots = list(roots)
        self._tex = {}

    # ---- file lookup -------------------------------------------------------------------
    def _path(self, ref, kind, ext):
        ns, _, p = ref.rpartition(":")
        ns = ns or "minecraft"
        for root in self.roots:
            f = os.path.join(root, "assets", ns, kind, p + ext)
            if os.path.exists(f):
                return f
        raise FileNotFoundError(f"{ref} ({kind}{ext}) not found in {self.roots}")

    def load_model(self, ref):
        """Merged model: textures (child wins) and the elements of the nearest model that has some."""
        with open(self._path(ref, "models", ".json"), encoding="utf-8") as fh:
            data = json.load(fh)
        textures, elements = {}, data.get("elements")
        parent = data.get("parent")
        if parent and parent not in ("minecraft:block/block", "block/block", "minecraft:builtin/generated"):
            pt, pe = self.load_model(parent if ":" in parent else "minecraft:" + parent)
            textures.update(pt)
            if elements is None:
                elements = pe
        textures.update(data.get("textures", {}))
        return textures, elements or []

    def texture(self, ref, frame=0):
        key = (ref, frame)
        if key in self._tex:
            return self._tex[key]
        f = self._path(ref, "textures", ".png")
        img = Image.open(f).convert("RGBA")
        meta = f + ".mcmeta"
        if os.path.exists(meta):
            with open(meta, encoding="utf-8") as fh:
                anim = json.load(fh).get("animation", {})
            fw = anim.get("width", img.width)
            fh_ = anim.get("height", fw if "width" in anim else img.width)
            n = img.height // fh_
            k = frame % max(n, 1)
            img = img.crop((0, k * fh_, fw, (k + 1) * fh_))
        self._tex[key] = img
        return img

    # ---- render ------------------------------------------------------------------------
    def render(self, ref, scale=12, frame=0, azimuth=45, pitch=30, background=None):
        textures, elements = self.load_model(ref)

        def resolve(t):
            seen = 0
            while t.startswith("#") and seen < 8:
                t = textures.get(t[1:], "missingno")
                seen += 1
            return t

        pa = math.radians(pitch)
        # The camera sits at azimuth `azimuth` (45 = south-east, so it sees the top, south and east faces).
        cx, cz = math.sin(math.radians(azimuth)), math.cos(math.radians(azimuth))
        dx, dz = -cx, -cz            # horizontal view direction, camera -> scene
        rx, rz = -dz, dx             # screen-right = d x up

        def project(p):
            x, y, z = (p[0] - 8, p[1] - 8, p[2] - 8)
            sx = x * rx + z * rz
            # depth towards camera: negative of the forward component
            along = x * dx + z * dz  # positive = away from camera
            sy = y * math.cos(pa) + along * math.sin(pa)  # up on screen (away = higher)
            depth = -along * math.cos(pa) + y * math.sin(pa)
            return sx, sy, depth

        quads = []
        for el in elements:
            f, t = el["from"], el["to"]
            rot = el.get("rotation")
            shade = el.get("shade", True)
            for face, fd in el.get("faces", {}).items():
                uv = fd.get("uv") or default_uv(face, f, t)
                verts = []
                for (xm, ym, zm) in FACE_VERTS[face]:
                    p = [t[0] if xm else f[0], t[1] if ym else f[1], t[2] if zm else f[2]]
                    if rot:
                        p = self._rotate(p, rot)
                    verts.append(p)
                u1, v1, u2, v2 = uv
                corners = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
                r = (fd.get("rotation", 0) // 90) % 4
                uvs = [corners[(i + r) % 4] for i in range(4)]
                tex = resolve(fd["texture"])
                quads.append((verts, uvs, tex, FACE_SHADE[face] if shade else 1.0))

        # screen extents
        pts = [project(v) for q in quads for v in q[0]]
        if not pts:
            return Image.new("RGBA", (8, 8))
        minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
        miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
        pad = 2
        W = int((maxx - minx) * scale) + pad * 2 + 1
        H = int((maxy - miny) * scale) + pad * 2 + 1
        img = Image.new("RGBA", (W, H), background or (0, 0, 0, 0))
        px = img.load()
        zbuf = [[-1e9] * W for _ in range(H)]

        for verts, uvs, texref, shade in quads:
            try:
                tex = self.texture(texref if ":" in texref else "minecraft:" + texref, frame)
            except FileNotFoundError:
                continue
            tw, th = tex.size
            tpx = tex.load()
            P = [project(v) for v in verts]

            def scr(p):
                return ((p[0] - minx) * scale + pad, (maxy - p[1]) * scale + pad, p[2])

            S = [scr(p) for p in P]
            ax, ay, az = S[3][0] - S[0][0], S[3][1] - S[0][1], S[3][2] - S[0][2]  # a: along u
            bx, by, bz = S[1][0] - S[0][0], S[1][1] - S[0][1], S[1][2] - S[0][2]  # b: along v
            det = ax * by - ay * bx
            if abs(det) < 1e-6:
                continue
            xs = [s[0] for s in S]
            ys = [s[1] for s in S]
            x0, x1 = max(int(min(xs)), 0), min(int(max(xs)) + 1, W - 1)
            y0, y1 = max(int(min(ys)), 0), min(int(max(ys)) + 1, H - 1)
            uv0, uv1, uv3 = uvs[0], uvs[1], uvs[3]
            for y in range(y0, y1 + 1):
                for x in range(x0, x1 + 1):
                    qx, qy = x + 0.5 - S[0][0], y + 0.5 - S[0][1]
                    a = (qx * by - qy * bx) / det
                    b = (ax * qy - ay * qx) / det
                    if a < 0 or b < 0 or a >= 1 or b >= 1:
                        continue
                    d = S[0][2] + a * az + b * bz
                    if d <= zbuf[y][x]:
                        continue
                    u = uv0[0] + a * (uv3[0] - uv0[0]) + b * (uv1[0] - uv0[0])
                    v = uv0[1] + a * (uv3[1] - uv0[1]) + b * (uv1[1] - uv0[1])
                    tx = min(max(int(u / 16 * tw), 0), tw - 1)
                    ty = min(max(int(v / 16 * th), 0), th - 1)
                    c = tpx[tx, ty]
                    if c[3] < 128:
                        continue
                    zbuf[y][x] = d
                    px[x, y] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255)
        return img

    @staticmethod
    def _rotate(p, rot):
        ox, oy, oz = rot["origin"]
        ang = math.radians(rot["angle"])
        axis = rot["axis"]
        x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
        if rot.get("rescale"):
            s = 1 / math.cos(ang)
            if axis == "y":
                x, z = x * s, z * s
            elif axis == "x":
                y, z = y * s, z * s
            else:
                x, y = x * s, y * s
        c, s_ = math.cos(ang), math.sin(ang)
        if axis == "y":
            x, z = x * c + z * s_, -x * s_ + z * c
        elif axis == "x":
            y, z = y * c - z * s_, y * s_ + z * c
        else:
            x, y = x * c - y * s_, x * s_ + y * c
        return [x + ox, y + oy, z + oz]
