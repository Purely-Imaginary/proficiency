#!/usr/bin/env python3
"""Bakes the Skillstone UI textures (ui.style = modern) from the approved design kit.

The design package lives in a local `uidesign` folder (kit.py, dir_a.py). This script reuses its
material code so the game draws the same carved stone, well and fire the mockups show:

  stone_outer.png  slab rim brickwork (12x6 bricks), tiled by the screen
  stone_inner.png  slab face brickwork (10x5 bricks)
  well.png         dark sunk slate brickwork (8x4 bricks)
  light.png        stepped radial light, drawn additively and tinted (64x64)
  dial_<R>.png     the dial ring and its degree ticks, radius R
  pit_<r>.png      the fire well at rest, radius r (no fire)
  fire_<r>.png     8 frames of the lit well stacked vertically, radius r
  flame_<n>.png    8 frames of a socket's flame (n x n, stacked vertically): a full talent node (1.30.0 tree)

  hearth_fire.png  8 frames of the fireplace flame (110 x 74, stacked vertically) (1.31.0 Hearth face)
  hearth_brick.png the fireplace's brickwork (14x7 bricks), tiled

  wheel_<R>.png    the Waywheel's carved stone disc: rim brickwork, inset face, nine wedge seams and the
                   dial ring on top, same frame as dial_<R> (1.33.0 Waywheel, R in WHEELS; 128 since 1.34.0)
  token_<n>.png    3 frames (n x n, stacked): a round carved stone token, rest / raised (hover) / dark
                   (level 0): outline, lit rim, the ember groove, a sunk face (1.34.0 Waywheel, n in TOKENS)
  ring_<n>.png     21 frames (n x n, stacked): the ember level ring in that groove, frame k = k/20 lit,
                   clockwise from the top, the molten head brightest
  pip.png          2 frames (5 x 5, stacked): the green "ready" pip and the gold pin nail

Run with "wheel" to bake only the Waywheel discs (and the pits they need), "tokens" for its round tokens.

Run with "flames" to bake only the socket flames, "hearth" for the Hearth face's.

Run: python3 tools/gen_skillstone.py  (writes common/src/main/resources/.../textures/gui/skillstone)
"""
import math, os, random, sys

sys.path.insert(0, 'uidesign')
from PIL import Image, ImageDraw  # noqa: E402
import kit as K  # noqa: E402
import dir_a as A  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'common', 'src', 'main', 'resources', 'assets', 'proficiency', 'textures', 'gui',
                   'skillstone')
DIALS = (149, 91, 61)
PITS = tuple(int(r * .44) for r in DIALS)
# The Waywheel's discs: 128 for 960x540 (1080p GUI 2, the D mockup's on-screen size), the two smaller
# dials, and one for a 320x180 GUI (GUI 4 at 720p). Java: WaywheelMath.WHEELS.
WHEELS = (128, 91, 61, 45)
# Round tokens: side n -> (rim width, groove width). The face radius n/2 - 1 - rim - groove must match
# WaywheelMath.TOKEN_FACE.
TOKENS = {30: (3, 2), 22: (2, 2), 18: (1, 2), 16: (1, 1)}
RING_STEPS = 20
FRAMES = 8


def tile(w, h, base, seed, bw, bh, var=.06, hi=1.12, lo=.86):
    # Draw wider than needed and crop, so the brick bond wraps without a seam column.
    im = K.stone_bricks(w + bw * 2, h, base=base, seed=seed, bw=bw, bh=bh, var=var, hi=hi, lo=lo)
    out = im.crop((bw, 0, bw + w, h))
    # Copy the first column of half-bricks to the right edge so the odd rows wrap cleanly.
    return out


def light_tex(n=64, steps=7):
    im = Image.new('RGBA', (n, n), (0, 0, 0, 255))
    d = ImageDraw.Draw(im)
    c = n / 2
    for i in range(steps, 0, -1):
        f = i / steps
        k = ((1 - f) + 1 / steps) ** 1.4
        v = int(255 * min(1, k))
        d.ellipse((c - c * f, c - c * f, c + c * f - 1, c + c * f - 1), fill=(v, v, v, 255))
    return im


def pit(r, fire, frame, k):
    size = 2 * (r + 5)
    im = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    cx = cy = size // 2
    K.CLIP = None
    A.firepit(im, cx, cy, r, fire=fire, k=k, frame=frame)
    # firepit lights the stone around the rim too; keep only the well itself (the screen adds the halo).
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse((cx - r - 4, cy - r - 4, cx + r + 4, cy + r + 4), fill=255)
    a = im.split()[3]
    from PIL import ImageChops
    im.putalpha(ImageChops.multiply(a, mask))
    return im


def dial(R):
    n = 2 * (R + 22)
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = n // 2
    for rr, col, w in ((R + 15, (24, 19, 18), 3), (R + 13, (130, 112, 104), 1)):
        d.ellipse((c - rr, c - rr, c + rr, c + rr), outline=col + (255,), width=w)
    for i in range(36):
        a = i * math.pi / 18
        r0, r1 = R + 16, R + (20 if i % 4 == 0 else 18)
        d.line((c + r0 * math.cos(a), c + r0 * math.sin(a), c + r1 * math.cos(a), c + r1 * math.sin(a)),
               fill=(30, 24, 22, 255))
    return im


FLAMES = (10, 14, 16, 22)


def flames():
    os.makedirs(OUT, exist_ok=True)
    for n in FLAMES:
        strip = Image.new('RGBA', (n, n * FRAMES), (0, 0, 0, 0))
        for f in range(FRAMES):
            strip.alpha_composite(K.flame(n, n, seed=n, phase=f / FRAMES), (0, f * n))
        strip.save(os.path.join(OUT, f'flame_{n}.png'))


def hearth():
    os.makedirs(OUT, exist_ok=True)
    w, h = 110, 74
    strip = Image.new('RGBA', (w, h * FRAMES), (0, 0, 0, 0))
    for f in range(FRAMES):
        strip.alpha_composite(K.flame(w, h, seed=5, phase=f / FRAMES), (0, f * h))
    strip.save(os.path.join(OUT, 'hearth_fire.png'))
    # The logs are not baked: the game draws vanilla's campfire_log_lit at run time (no Mojang pixels in the jar).
    tile(112, 56, K.shade(A.STONE, 1.25), 50, 14, 7).save(os.path.join(OUT, 'hearth_brick.png'))


def wheel(R):
    """The Waywheel disc: a carved stone wheel in the slab's material, the dial ring laid over it."""
    n = 2 * (R + 22)
    c = n / 2 - .5
    D = R + 20
    face_r = R + 11
    outer = tile(n, n, K.shade(A.STONE, 1.12), 11, 12, 6, var=.03)
    inner = tile(n, n, A.STONE, 12, 10, 5)
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    op, ip, px = outer.load(), inner.load(), im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if r > D + .5:
                continue
            # Light from the top left: +1 there, -1 at the bottom right.
            lit = -(dx + dy) / (max(r, 1) * math.sqrt(2))
            if r > face_r:
                col = op[x, y][:3]
                if r > D - 2.5:
                    k = 1 + .30 * lit if lit > 0 else 1 + .45 * lit
                    col = K.shade(col, k)
                if r > D - .8:
                    col = (24, 18, 18)
            else:
                col = ip[x, y][:3]
                if r > face_r - 2.5:
                    # The face is sunk: dark at the top left, lit at the bottom right.
                    k = 1 - .40 * lit if lit > 0 else 1 - .22 * lit
                    col = K.shade(col, k)
                if r > face_r - .8:
                    col = (30, 24, 22)
            px[x, y] = tuple(int(v) for v in col) + (255,)
    d = ImageDraw.Draw(im)
    pit_r = int(R * .44)
    for i in range(9):
        a = -math.pi / 2 + (i + .5) * 2 * math.pi / 9
        r0, r1 = pit_r + 7, face_r - 2
        x0, y0 = c + r0 * math.cos(a), c + r0 * math.sin(a)
        x1, y1 = c + r1 * math.cos(a), c + r1 * math.sin(a)
        # A carved seam: a dark cut and its lit lower lip.
        d.line((x0 + 1, y0 + 1, x1 + 1, y1 + 1), fill=K.shade(A.STONE, 1.3) + (255,))
        d.line((x0, y0, x1, y1), fill=(26, 20, 19, 255), width=2 if R >= 61 else 1)
    im.alpha_composite(dial(R))
    return im


def token(n, state):
    """A round carved stone token: a dark outline, a rim lit from the top left, the dark ember groove and a
    sunk brick face. state 0 rest, 1 raised (hover), 2 dark (a skill still at level 0)."""
    rim, groove = TOKENS[n]
    R = n / 2
    c = n / 2 - .5
    k_rim, k_face = ((1.0, 1.0), (1.22, 1.12), (.82, .66))[state]
    rim_tex = tile(n, n, K.shade(A.STONE, 1.24 * k_rim), 21 + n, 7, 4, var=.04)
    face_tex = tile(n, n, K.shade(A.STONE, 1.0 * k_face), 31 + n, 7, 4, var=.07)
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    rp, fp, px = rim_tex.load(), face_tex.load(), im.load()
    r_out = R - .25
    r_rim = r_out - 1
    r_groove = r_rim - rim
    r_face = r_groove - groove
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if r > r_out:
                continue
            lit = -(dx + dy) / (max(r, 1) * math.sqrt(2))
            if r > r_rim:
                col = (24, 18, 18)
            elif r > r_groove:
                col = rp[x, y][:3]
                col = K.shade(col, 1 + .34 * lit if lit > 0 else 1 + .42 * lit)
            elif r > r_face:
                # The groove the ember runs in: cut deep, its lower lip catching a little light.
                col = (30, 22, 20) if lit > -.35 else (44, 34, 31)
            else:
                col = fp[x, y][:3]
                if r > r_face - 1.5:
                    col = K.shade(col, 1 - .38 * lit if lit > 0 else 1 - .25 * lit)
            px[x, y] = tuple(int(v) for v in col) + (255,)
    return im


def ring(n, k):
    """Frame k of the ember level ring: k / RING_STEPS of the groove lit, clockwise from the top."""
    rim, groove = TOKENS[n]
    R = n / 2
    c = n / 2 - .5
    r_groove = R - 1.25 - rim
    r_face = r_groove - groove
    t = k / RING_STEPS
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    if k == 0:
        return im
    px = im.load()
    for y in range(n):
        for x in range(n):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy)
            if not (r_face < r <= r_groove):
                continue
            a = (math.atan2(dx, -dy) / (2 * math.pi)) % 1.0
            if a >= t:
                continue
            # Brighter toward the molten head; the outer pixel of a two-wide groove is the hotter one.
            head = a / t
            outer = groove < 2 or r > r_face + groove / 2
            col = K.ember_col(.42 + .42 * head + (.1 if outer else 0))
            px[x, y] = col + (255,)
    return im


def pips():
    im = Image.new('RGBA', (5, 10), (0, 0, 0, 0))
    px = im.load()
    shape = [' ### ', '#####', '#####', '#####', ' ### ']
    for f, (body, hi, lo) in enumerate((((112, 214, 112), (226, 255, 226), (46, 104, 46)),
                                        ((255, 214, 150), (255, 246, 222), (140, 90, 30)))):
        for y, row in enumerate(shape):
            for x, ch in enumerate(row):
                if ch != '#':
                    continue
                col = body
                if x + y >= 6:
                    col = lo
                px[x, y + 5 * f] = col + (255,)
        px[1, 1 + 5 * f] = hi + (255,)
        if f == 1:
            px[2, 2 + 5 * f] = (140, 90, 30, 255)
    return im


def tokens():
    os.makedirs(OUT, exist_ok=True)
    for n in TOKENS:
        strip = Image.new('RGBA', (n, n * 3), (0, 0, 0, 0))
        for f in range(3):
            strip.alpha_composite(token(n, f), (0, f * n))
        strip.save(os.path.join(OUT, f'token_{n}.png'))
        strip = Image.new('RGBA', (n, n * (RING_STEPS + 1)), (0, 0, 0, 0))
        for k in range(RING_STEPS + 1):
            strip.alpha_composite(ring(n, k), (0, k * n))
        strip.save(os.path.join(OUT, f'ring_{n}.png'))
    pips().save(os.path.join(OUT, 'pip.png'))


def wheels():
    os.makedirs(OUT, exist_ok=True)
    for R in WHEELS:
        wheel(R).save(os.path.join(OUT, f'wheel_{R}.png'))
        r = int(R * .44)
        if r not in PITS:
            k = 4 if r >= 50 else 2
            pit(r, 0.0, 0, k).save(os.path.join(OUT, f'pit_{r}.png'))
            frames = [pit(r, 1.0, f, k) for f in range(FRAMES)]
            strip = Image.new('RGBA', (frames[0].width, frames[0].height * FRAMES), (0, 0, 0, 0))
            for i, f in enumerate(frames):
                strip.paste(f, (0, i * f.height))
            strip.save(os.path.join(OUT, f'fire_{r}.png'))


def main():
    os.makedirs(OUT, exist_ok=True)
    tile(96, 48, K.shade(A.STONE, 1.12), 1, 12, 6, var=.03).save(os.path.join(OUT, 'stone_outer.png'))
    tile(100, 40, A.STONE, 2, 10, 5).save(os.path.join(OUT, 'stone_inner.png'))
    tile(64, 32, (32, 26, 25), 3, 8, 4, var=.08, hi=1.25, lo=.7).save(os.path.join(OUT, 'well.png'))
    light_tex().save(os.path.join(OUT, 'light.png'))
    for R in DIALS:
        dial(R).save(os.path.join(OUT, f'dial_{R}.png'))
    for r in PITS:
        k = 4 if r >= 50 else 2
        pit(r, 0.0, 0, k).save(os.path.join(OUT, f'pit_{r}.png'))
        frames = [pit(r, 1.0, f, k) for f in range(FRAMES)]
        strip = Image.new('RGBA', (frames[0].width, frames[0].height * FRAMES), (0, 0, 0, 0))
        for i, f in enumerate(frames):
            strip.paste(f, (0, i * f.height))
        strip.save(os.path.join(OUT, f'fire_{r}.png'))
    wheels()
    tokens()
    print('wrote', OUT)


if __name__ == '__main__':
    random.seed(1)
    if sys.argv[1:] == ['flames']:
        flames()
    elif sys.argv[1:] == ['hearth']:
        hearth()
    elif sys.argv[1:] == ['wheel']:
        wheels()
    elif sys.argv[1:] == ['tokens']:
        tokens()
    else:
        main()
        flames()
        hearth()
