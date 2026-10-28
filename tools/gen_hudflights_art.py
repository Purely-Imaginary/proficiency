"""Pixel art for the 1.37.0 HUD flights: the talent point coin (16x16) and the brazier socket it flies to.

    python3 tools/gen_hudflights_art.py            # writes the PNGs
    python3 tools/gen_hudflights_art.py --sheet out.png   # an 8x contact sheet

Same rules as the skill icons (tools/gen_skill_icons.py): a 1 px dark outline added by the script, light from the
top left, 3 to 4 shades per material, no anti-aliasing. Ember gold is the coin's metal; the brazier is the
Skillstone's dark iron with the same ember in its bowl.
  point_coin.png     16x16
  point_brazier.png  18x54: three 18x18 frames stacked, cold, lit A, lit B (the HUD alternates A and B)
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "common/src/main/resources/assets/proficiency/textures/gui")

OUTLINE = (0x2a, 0x14, 0x08)


def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def outline(img):
    w, h = img.size
    src = img.copy()
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] == 0 and any(
                    0 <= x + dx < w and 0 <= y + dy < h and src.getpixel((x + dx, y + dy))[3] > 0
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                img.putpixel((x, y), OUTLINE + (255,))
    return img


# Ember gold: lightest to darkest.
G1, G2, G3, G4, G5 = rgb(0xFFF0A8), rgb(0xFFCB4A), rgb(0xF0A02C), rgb(0xC26A16), rgb(0x8A4410)

# The face's emblem: a small flame, one pixel of light on its left (the same flame the Tempo fire uses).
FLAME = [
    "....X....",
    "...XX....",
    "...XXX...",
    "..XXXX...",
    "..XXXXX..",
    ".XXXXXXX.",
    ".XXXXXXX.",
    ".XXXXXXX.",
    "..XXXXX..",
]


def coin():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    c = 7.5
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if d > 6.6:
                continue
            # Light from the top left: a point's shade is how far toward the lower right it sits on the rim.
            lean = ((x - c) + (y - c)) / (2 * 6.6)
            if d > 5.2:
                col = G1 if lean < -0.45 else G2 if lean < 0.1 else G4 if lean < 0.55 else G5
            else:
                col = G2 if lean < -0.25 else G3
            img.putpixel((x, y), col + (255,))
    # The raised face has a 1 px inner groove on its lower right.
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - c, y - c)
            if 4.7 < d <= 5.4 and ((x - c) + (y - c)) > 1.5:
                img.putpixel((x, y), G5 + (255,))
    for j, row in enumerate(FLAME):
        for i, ch in enumerate(row):
            if ch == "X":
                px, py = 3.5 + i, 3.5 + j
                px, py = int(px + 0.5), int(py + 0.5)
                shade = G5 if (i + j) % 5 else G4
                if 0 <= px < 16 and 0 <= py < 16 and img.getpixel((px, py))[3]:
                    img.putpixel((px, py), shade + (255,))
    # Two glints, top left.
    for p in ((4, 3), (3, 4)):
        img.putpixel(p, (255, 255, 255, 255))
    return outline(img)


I1, I2, I3, I4 = rgb(0x6E6A78), rgb(0x4A4756), rgb(0x34323E), rgb(0x1E1D26)
E1, E2, E3 = rgb(0xFFF3B0), rgb(0xFFB63D), rgb(0xE2571B)

# One 18x18 frame: legs, a bowl, and (lit) the ember heap and flame. 'f' 'F' flame, 'e' ember, others iron.
BRAZIER = [
    "..................",
    "..................",
    "..................",
    "..................",
    "..................",
    "..................",
    "..................",
    "..................",
    "..................",
    "..................",
    "...1222222222222..",
    "..122333333333344.",
    "..122333333333344.",
    "...133333333344...",
    "....1333333344....",
    ".....13333344.....",
    "....1.2.33.2.4....",
    "...1..2....4..4...",
]
FLAME_A = [
    ".........f........",
    "........fF........",
    "........fFf.......",
    ".......fFFF.......",
    "......ffFFFf......",
    "......fFFFFF......",
    ".....ffFFFFFf.....",
    ".....fFFFFFFf.....",
    "......eEeEeE......",
    ".......eeee.......",
]
FLAME_B = [
    "..........f.......",
    ".........fF.......",
    "........fFF.......",
    "........fFFf......",
    ".......ffFFFf.....",
    "......fFFFFFf.....",
    ".....ffFFFFFFf....",
    ".....fFFFFFFFf....",
    "......eEeEeEe.....",
    ".......eeee.......",
]


def brazier_frame(flame):
    img = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    pal = {"1": I1, "2": I2, "3": I3, "4": I4}
    for y, row in enumerate(BRAZIER):
        for x, ch in enumerate(row):
            if ch in pal:
                img.putpixel((x, y), pal[ch] + (255,))
    if flame is not None:
        # The flame sits in the bowl: its last row on the bowl's rim (row 10).
        top = 10 - len(flame) + 1
        fpal = {"f": E3, "F": E2, "e": E3, "E": E1}
        for j, row in enumerate(flame):
            for i, ch in enumerate(row):
                if ch in fpal:
                    img.putpixel((i, top + j), fpal[ch] + (255,))
        # An ember glow on the bowl's top face.
        for x in range(4, 14):
            img.putpixel((x, 11), E3 + (255,) if x % 2 else E2 + (255,))
    return outline(img)


def brazier():
    sheet = Image.new("RGBA", (18, 54), (0, 0, 0, 0))
    for n, flame in enumerate((None, FLAME_A, FLAME_B)):
        sheet.alpha_composite(brazier_frame(flame), (0, 18 * n))
    return sheet


def main():
    coin_img, braz = coin(), brazier()
    # The Forge line takes common's assets, so one copy serves all three.
    os.makedirs(OUT, exist_ok=True)
    coin_img.save(os.path.join(OUT, "point_coin.png"))
    braz.save(os.path.join(OUT, "point_brazier.png"))
    if "--sheet" in sys.argv:
        path = sys.argv[sys.argv.index("--sheet") + 1]
        sheet = Image.new("RGBA", (16 * 8 + 18 * 8 * 3 + 40, 18 * 8 + 16), (40, 40, 48, 255))
        sheet.alpha_composite(coin_img.resize((128, 128), Image.NEAREST), (8, 8))
        for n in range(3):
            fr = braz.crop((0, 18 * n, 18, 18 * n + 18)).resize((144, 144), Image.NEAREST)
            sheet.alpha_composite(fr, (160 + n * 152, 8))
        sheet.save(path)


if __name__ == "__main__":
    main()
