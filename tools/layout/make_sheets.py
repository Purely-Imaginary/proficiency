#!/usr/bin/env python3
"""Contact sheets: one per (setup, scene), languages side by side (6 per sheet, two sheets)."""
import sys, os
from PIL import Image, ImageDraw, ImageFont
shots, out = sys.argv[1], sys.argv[2]
thumb_w = int(sys.argv[3]) if len(sys.argv) > 3 else 640
only = sys.argv[4].split(',') if len(sys.argv) > 4 else None
LANGS = ['en_us','pl_pl','zh_cn','ru_ru','pt_br','es_es','de_de','fr_fr','ja_jp','ko_kr','tr_tr','uk_ua']
os.makedirs(out, exist_ok=True)
groups = {}
for f in sorted(os.listdir(shots)):
    if not f.endswith('.png'): continue
    lang, setup, scene = f[:-4].split('__')
    groups.setdefault((setup, scene), {})[lang] = os.path.join(shots, f)
font = ImageFont.load_default()
made = []
for (setup, scene), files in sorted(groups.items()):
    if only and scene not in only and setup not in only and f'{setup}/{scene}' not in only: continue
    for part, langs in enumerate((LANGS[:6], LANGS[6:])):
        imgs = [(l, Image.open(files[l])) for l in langs if l in files]
        if not imgs: continue
        w0, h0 = imgs[0][1].size
        th = round(thumb_w * h0 / w0)
        cols = 3
        rows = (len(imgs) + cols - 1) // cols
        sheet = Image.new('RGB', (cols * thumb_w, rows * (th + 12)), (30, 30, 30))
        d = ImageDraw.Draw(sheet)
        for i, (l, im) in enumerate(imgs):
            x, y = (i % cols) * thumb_w, (i // cols) * (th + 12)
            sheet.paste(im.convert('RGB').resize((thumb_w, th), Image.LANCZOS), (x, y + 12))
            d.text((x + 4, y + 1), f'{l}  {setup}  {scene}', fill=(255, 255, 0), font=font)
        name = f'{setup}__{scene}__{part + 1}.png'
        sheet.save(os.path.join(out, name))
        made.append(name)
print(len(made), 'sheets')
