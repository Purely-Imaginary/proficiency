#!/usr/bin/env python3
"""Offline estimator: width in GUI pixels of every limited-room string in every language, against
the limit of its site in the OLD layout (before the layout pass) and in the NEW one.

glyphs.json comes from the real client (LayoutAudit.dumpGlyphWidths), so widths are the game's own.
usage: layout_audit.py <glyphs.json> <lang dir> <out.json>
"""
import json
import re
import sys
from collections import defaultdict

glyphs_path, lang_dir, out_path = sys.argv[1:4]
G = {int(k): v for k, v in json.load(open(glyphs_path)).items()}
LANGS = ['en_us', 'pl_pl', 'zh_cn', 'ru_ru', 'pt_br', 'es_es', 'de_de', 'fr_fr', 'ja_jp', 'ko_kr', 'tr_tr', 'uk_ua']
D = {l: json.load(open(f'{lang_dir}/{l}.json')) for l in LANGS}
SKILLS = ['alchemy', 'archery', 'axes', 'beastslaying', 'blocking', 'charger', 'cooking', 'courage', 'crossbows',
          'decorating', 'endurance', 'engineering', 'excavation', 'farming', 'fishing', 'guardian', 'jumping', 'maces',
          'masonry', 'mining', 'nightwalker', 'running', 'smithing', 'sneaking', 'social', 'spellcasting',
          'spelunking', 'swimming', 'swords', 'tactician', 'tridents', 'unarmed', 'wayfaring', 'woodcutting']
CATS = ['combat', 'gathering', 'movement', 'crafting', 'mastery', 'expedition', 'construction', 'social', 'survival']


def w(s):
    s = re.sub('§.', '', s)
    return sum(G.get(ord(c), 0) for c in s)


def fmt(lang, key, *args):
    s = D[lang].get(key) or D['en_us'][key]
    it = iter(args)
    pos = {}

    def sub(m):
        if m.group(0) == '%%':
            return '%'
        idx = m.group(1)
        if idx:
            return str(args[int(idx[:-1]) - 1])
        return str(next(it))
    return re.sub(r'%(\d+\$)?[sd]|%%', sub, s)


def skill(l, s):
    return D[l]['proficiency.skill.' + s]


res = defaultdict(lambda: defaultdict(lambda: {'count': 0, 'of': 0, 'worst': None, 'max': 0, 'limit': None}))


def check(site, lang, text, limit, label=''):
    r = res[site][lang]
    r['of'] += 1
    r['limit'] = limit
    width = w(text)
    if width > limit:
        r['count'] += 1
    if width - limit > r['max'] or r['worst'] is None:
        r['max'] = max(r['max'], width - limit)
        r['worst'] = f'{label}: {text[:70]} ({width}px)'


syn_keys = [k for k in D['en_us'] if re.fullmatch(r'proficiency\.synergy\.[a-z_]+', k)
            and k.split('.')[2] not in ('header', 'line', 'line_active', 'need', 'need_grandmasters', 'grant',
                                        'every_skill', 'awakened', 'faded')]
talent_desc = [k for k in D['en_us'] if re.fullmatch(r'proficiency\.talent\.[a-z_]+\.[a-z_0-9]+\.desc', k)]
structure_keys = [k for k in D['en_us'] if k.startswith(('structure.', 'dimension.'))]

OLD = {'tree_grid': 208, 'side': 160, 'tree_inner': 382, 'col': 154, 'recap_name': 58, 'recap_text': 134,
       'tab': 73, 'screen_a': 426 - 8, 'screen_b': 320 - 8}

for l in LANGS:
    for s in SKILLS:
        name = skill(l, s)
        check('tree.title', l, fmt(l, 'proficiency.tree.title', name), OLD['tree_grid'] - 10, s)
        check('tree.summary', l, fmt(l, 'proficiency.tree.summary', 60, 60, 12, 86), OLD['tree_grid'], s)
        proc = fmt(l, 'proficiency.tree.proc', D[l]['proficiency.proc.' + s], D[l]['proficiency.proc.' + s + '.desc'])
        check('tree.proc (description cut, hover)', l, proc, OLD['tree_grid'], s)
        passive = fmt(l, 'proficiency.stats.passive', '12.5')
        procp = max(fmt(l, 'proficiency.stats.proc', '8', '×1.25'), fmt(l, 'proficiency.stats.proc_locked', 25), key=w)
        abil = max(fmt(l, 'proficiency.stats.ability', 20, 180), fmt(l, 'proficiency.stats.ability_locked', 50), key=w)
        check('tree.numbers', l, f'{passive} · {procp} · {abil}', OLD['tree_grid'], s)
        for c in ('gathering', 'combat', 'expedition', 'construction', 'crafting', 'mastery', 'movement', 'survival'):
            if 'proficiency.category.' + c in D[l]:
                check('tree.discipline (side column)', l,
                      fmt(l, 'proficiency.tree.discipline', D[l]['proficiency.category.' + c], 33), OLD['side'], c)
        # panel
        check('panel.row (name+level)', l, name + ' ' * 0 + '100', OLD['col'] - 4 - 0, s)
        check('panel.row with icon', l, name + '100', OLD['col'] - 10 - 4, s)
        check('recap.name', l, name, OLD['recap_name'], s)
        check('hud.line', l, fmt(l, 'proficiency.hud.line', name, 100) + ' ' + 'Ⰶ' + '50%', OLD['screen_b'] - 8 - 10, s)
        check('toast.line', l, fmt(l, 'proficiency.toast.line', name, 100), 140, s)
        check('wheel.centre (name)', l, name, 122, s)
        check('discovery.xp line', l, fmt(l, 'proficiency.discovery.xp', 150, name), OLD['screen_b'], s)
        check('discovery.kicker (first)', l, fmt(l, 'proficiency.discovery.first', name), OLD['screen_b'] * 0.95, s)
    for c in CATS:
        if 'proficiency.category.' + c in D[l]:
            check('panel.category header', l, D[l]['proficiency.category.' + c], OLD['col'], c)
    for k in syn_keys:
        check('tree.synergy row', l, '0/3 ' + (D[l].get(k) or D['en_us'][k]), OLD['side'], k)
    check('tree.footer under side column', l, fmt(l, 'proficiency.tree.footer', 'spelunking'), OLD['tree_grid'], '')
    check('tree.footer past panel edge', l, fmt(l, 'proficiency.tree.footer', 'spelunking'), OLD['tree_inner'], '')
    check('panel.streak title+cap', l, fmt(l, 'proficiency.streak.title', 50) + fmt(l, 'proficiency.streak.cap', 50) + ' ' * 3, 320, '')
    check('panel.streak next+value', l, fmt(l, 'proficiency.streak.next', 51, 99) + fmt(l, 'proficiency.streak.value', 50, 50) + ' ' * 3, 320, '')
    check('recap.title', l, fmt(l, 'proficiency.recap.title'), OLD['recap_text'], '')
    check('recap.streak', l, fmt(l, 'proficiency.recap.streak', 31), OLD['recap_text'] - 12, '')
    check('recap.more', l, fmt(l, 'proficiency.recap.more', 12), OLD['recap_text'], '')
    check('recap.rested (wraps to 3 lines)', l, fmt(l, 'proficiency.recap.rested', 1234, 12), OLD['recap_text'] * 3, '')
    check('tooltip.rested', l, fmt(l, 'proficiency.tooltip.rested', 1234), 320 - 8 - 12 - 4, '')
    check('tooltip.rusty (wraps to 2 lines)', l, fmt(l, 'proficiency.tooltip.rusty', 12), (320 - 8 - 12 - 4) * 2, '')
    check('toast.teaching (action bar)', l, fmt(l, 'proficiency.teaching.paid', '12.5'), 426 * 0.9, '')
    for t in ('biomes', 'structures', 'dimensions', 'skills'):
        check('journal.tab label', l, D[l]['proficiency.journal.tab.' + t], OLD['tab'], t)
    check('journal.button', l, D[l]['proficiency.journal.button'], 56, '')
    for k in ('proficiency.discovery.biome', 'proficiency.discovery.dimension', 'proficiency.discovery.structure'):
        check('discovery.kicker', l, fmt(l, k, 'x') + fmt(l, 'proficiency.discovery.with', '', 'Nikola') if False else fmt(l, k, 'x'), 426 * 0.95, k)
    check('feed.header/overflow', l, fmt(l, 'proficiency.xpfeed.overflow', '123.4', 25), 418, '')
    facts = ' · '.join(f"{D[l]['proficiency.xpfeed.factor.' + f]} x1.30" for f in ('tempo', 'streak', 'perk', 'company', 'tier'))
    check('feed.detail (x0.75)', l, facts, 418 / 0.75, '')
    for k in talent_desc:
        text = D[l].get(k) or D['en_us'][k]
        check('tooltip.talent desc vs 1280x720 gui4 (320)', l, text, 320 - 8 - 12 - 4, k)
    for s in SKILLS:
        check('tooltip.skill desc vs 320', l, D[l]['proficiency.skill.' + s + '.desc'], 320 - 8 - 12 - 4, s)
        check('tooltip.proc desc vs 320', l, D[l]['proficiency.proc.' + s + '.desc'], 320 - 8 - 12 - 4, s)
    for k in structure_keys:
        text = D[l].get(k) or D['en_us'][k]
        check('banner.title (needs > 261px -> small)', l, text, 261, k)
    for k in ('proficiency.wheel.hint', 'proficiency.wheel.cancel', 'proficiency.wheel.in_hands', 'proficiency.wheel.ready',
              'proficiency.tooltip.details'):
        text = fmt(l, k, 'G') if k.endswith('hint') else fmt(l, k)
        longest = max((w(x) for x in re.split(r'\s+', text)), default=0)
        check('wheel.centre (longest word)', l, 'x' * 0 + max(re.split(r'\s+', text), key=w), 122, k)
    for k in ('proficiency.stats.passive', 'proficiency.stats.proc', 'proficiency.stats.ability', 'proficiency.tooltip.details'):
        text = fmt(l, k, '12.5', '×1.25', '20', '180') if 'stats' in k else fmt(l, k)
        check('item tooltip line (wraps at 240)', l, text, 240, k)
    # config
    for k in D['en_us']:
        if k.startswith('proficiency.configuration.') and not k.endswith('.tooltip') and k.count('.') == 3 and '.section' not in k:
            check('config.label (+": Off")', l, (D[l].get(k) or D['en_us'][k]) + ': 0.000', 240 - 12, k)
    # ---- two-layer tooltips (short lines, stat rows, hints) ----
    # Limits: an item tooltip wraps at 240; a screen tooltip at 300 minus its 8 px padding; the tree
    # header's proc line has the panel width of the smallest window (about 296).
    TIP_ITEM, TIP_SCREEN, TIP_HEADER = 240, 292, 296
    stat_keys = [k for k in D['en_us'] if k.startswith('proficiency.stat.') and k not in ('proficiency.stat.row', 'proficiency.stat.now')]
    for k in stat_keys:
        label = fmt(l, k, 5) if k.endswith('courage_damage') else fmt(l, k)
        # worst plausible values: a multiplier to x9.99 and a percent to 100.0%, a reach to 12.50
        for vals in (('×1.00', '×9.99'), ('0%', '100.0%'), ('4.5', '12.50')):
            check('tip.stat row (item tooltip, wraps at 240)', l, fmt(l, 'proficiency.stat.row', label, *vals), TIP_ITEM, k)
            check('tip.stat row (screen tooltip, 292)', l, fmt(l, 'proficiency.stat.row', label, *vals), TIP_SCREEN, k)
        check('tip.headline (item tooltip, 240)', l, fmt(l, 'proficiency.stat.now', label, '×9.99'), TIP_ITEM, k)
    for k in D['en_us']:
        if k.endswith('.short') and k.startswith('proficiency.talent.'):
            check('tip.talent short line (292)', l, D[l].get(k) or D['en_us'][k], TIP_SCREEN, k)
        elif k.endswith('.short') and k.startswith('proficiency.synergy.'):
            check('tip.synergy short line (292)', l, D[l].get(k) or D['en_us'][k], TIP_SCREEN, k)
        elif k.endswith('.short') and k.startswith('proficiency.proc.'):
            name = D[l]['proficiency.proc.' + k.split('.')[2]]
            check('tip.proc short line (292)', l, D[l].get(k) or D['en_us'][k], TIP_SCREEN, k)
            check('tip.header proc line (name: short, one line)', l, fmt(l, 'proficiency.tree.proc', name, D[l].get(k) or D['en_us'][k]), TIP_HEADER, k)
    for k in ('proficiency.tree.click.short', 'proficiency.tree.materials.short', 'proficiency.synergy.state.awake',
              'proficiency.synergy.state.parts', 'proficiency.tooltip.details'):
        check('tip.hint and state lines (292)', l, fmt(l, k, 12, 12), TIP_SCREEN, k)
    check('tip.hint (item tooltip, 240)', l, fmt(l, 'proficiency.tooltip.details'), TIP_ITEM, '')

    # action bar
    for k in D['en_us']:
        if k.startswith(('proficiency.compass', 'proficiency.talent.msg', 'proficiency.station', 'proficiency.guardian',
                         'proficiency.tactician', 'proficiency.company', 'proficiency.social', 'proficiency.perk.missing',
                         'proficiency.wheel.none', 'proficiency.active')) and not k.endswith(('.desc',)):
            text = D[l].get(k) or D['en_us'][k]
            text = re.sub(r'%(\d+\$)?[sd]', 'Xxxxxx', text)
            check('action bar message vs 320', l, text, 320 - 16, k)

out = {site: {lang: v for lang, v in per.items()} for site, per in res.items()}
json.dump(out, open(out_path, 'w'), ensure_ascii=False, indent=1)

# summary table
sites = sorted(out)
print(f"{'site':52s} " + ' '.join(f'{l[:2]:>4s}' for l in LANGS) + '   total')
for site in sites:
    row = [out[site][l]['count'] for l in LANGS]
    print(f'{site:52s} ' + ' '.join(f'{c:4d}' for c in row) + f'   {sum(row)}')
