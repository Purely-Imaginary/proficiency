#!/usr/bin/env python3
"""Palette-map siege_horn.png into the four higher horn tiers."""
import os
from PIL import Image
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
D = os.path.join(ROOT, 'common/src/main/resources/assets/proficiency/textures/item')
h = lambda s: tuple(int(s[i:i+2], 16) for i in (0, 2, 4)) + (255,)
# source roles
BODY = ['eedab2', 'ceb284', 'a07e58', '705438']          # light..dark horn
BAND = ['fff48c', 'f2c43c', 'b27e20']                    # gold light..dark
METAL = ['c4c4c4', '8a8a8a', '58585c']                   # mouthpiece bits
OUT = '30221a'
TIERS = {
 'siege_horn_2': dict(  # War Horn: steel bands, darker horn
    body=['c8b890', 'a69470', '7e6c4e', '554632'], band=['f0f4f8', 'aab4c0', '66707e'],
    metal=['d8dce0', '9aa0a8', '5a5e66'], out='241a14', px={}),
 'siege_horn_3': dict(  # Battle Horn: gold + emerald/cyan inset
    body=['eedab2', 'ceb284', 'a07e58', '705438'], band=['fff8a8', 'ffd23c', 'c48a1c'],
    metal=['c4c4c4', '8a8a8a', '58585c'], out='30221a',
    px={(10, 7): '2fe07a', (11, 7): '7af5ff', (10, 8): '14a85a', (11, 8): '2fd0e0'}),
 'siege_horn_4': dict(  # Doom Horn: charcoal, blood red bands, fire highlight
    body=['6a6670', '4c4852', '36323a', '24202a'], band=['e03a2a', 'a01418', '5c0a10'],
    metal=['7a7078', '48404a', '2c262e'], out='120c10',
    px={(12, 3): 'ffb028', (11, 3): 'ff7a14', (9, 6): 'ff7a14', (9, 7): 'ffb028'}),
 'siege_horn_5': dict(  # Horn of the Warhost: netherite + bright gold + enchant glint
    body=['5e5258', '443a42', '2e262e', '1c161e'], band=['fffbb0', 'ffd820', 'd89400'],
    metal=['a89cb0', '6a5e76', '3c3446'], out='0e080c',
    px={(10, 7): 'e070ff', (9, 7): 'ff9af0', (11, 8): 'b040f0', (9, 2): 'f0a8ff', (12, 2): 'c060ff'}),
}
src = Image.open(os.path.join(D, 'siege_horn.png')).convert('RGBA')
for name, t in TIERS.items():
    m = {h(OUT): h(t['out'])}
    for k, srcpal in (('body', BODY), ('band', BAND), ('metal', METAL)):
        for a, b in zip(srcpal, t[k]):
            m[h(a)] = h(b)
    out = src.copy()
    for y in range(16):
        for x in range(16):
            p = src.getpixel((x, y))
            if p[3]:
                out.putpixel((x, y), m[p])
    for (x, y), c in t['px'].items():
        if src.getpixel((x, y))[3]:
            out.putpixel((x, y), h(c))
    out.save(os.path.join(D, name + '.png'))
    print('wrote', name)
