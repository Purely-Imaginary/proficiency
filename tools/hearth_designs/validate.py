#!/usr/bin/env python3
"""Validate Hearth designs.json against HEARTH-DESIGNS-SPEC.md. Usage: validate.py designs.json"""
import json, sys

STONE, WOOD = set("Ss"), set("WPw")
METAL = set("icgbhlauBrk")
ROMAN = ["I", "II", "III", "IV", "V", "VI"]
UPGRADES = {("S", "s"), ("P", "w")}
# stage -> (footprint min, max, height min, max); height counts layer -1 up to the top block
SIZES = {1: (3, 5, 2, 3), 2: (5, 7, 2, 4), 3: (5, 9, 4, 5), 4: (7, 9, 5, 6), 5: (7, 9, 6, 7), 6: (9, 9, 7, 7)}
MAX_I, MAX_VI = 20, 350
MAX_WATER, MAX_LAVA = 8, 8
FLUID = set("vV")
WET_OK = "SicgkOI"   # blocks that may hold water
LAVA_OK = "SicgkO"   # blocks that may hold lava (plus bars on the same layer)
FULL = set("SicgkvV")   # roles a fluid may be held by (full solid blocks or the same fluid)
BURN = set("WPwNmqL")   # flammable for the lava fire rule
SUPPORT = set("SsWPicgkInqYOLGx")   # roles that can carry a pot, candle, carpet or skull
LAVA_LANES = {"forge", "demonic"}
COSTLY = {"a": 4, "B": 4, "g": 4, "z": 4}
PAL_KEYS = list("SsWPwGNmfC")
EXPECTED = ["longhouse", "forge", "shrine", "demonic", "gothic", "girly", "ruin", "frost"]

def cells(layers):
    """dict (z,r,c)->ch for non-space cells"""
    d = {}
    for z, rows in layers.items():
        for r, row in enumerate(rows):
            for c, ch in enumerate(row):
                if ch != " ":
                    d[(int(z), r, c)] = ch
    return d

def solid(ch):
    return ch not in " ."

def check_stage(legend, lane, st, prev):
    res = []  # (rule, ok, detail)
    L = st["layers"]
    def add(rule, ok, detail=""):
        res.append((rule, ok, detail))
    # grids
    bad = []
    for z, rows in L.items():
        if len(rows) != 9: bad.append(f"layer {z}: {len(rows)} rows")
        for r, row in enumerate(rows):
            if len(row) != 9: bad.append(f"layer {z} row {r}: {len(row)} chars")
            for ch in row:
                if ch not in legend: bad.append(f"layer {z} row {r}: char {ch!r} not in legend")
    add("grids 9x9, legend chars", not bad, "; ".join(bad[:3]))
    if bad: return res, None
    zs = sorted(int(z) for z in L)
    add("layers within -1..5", all(-1 <= z <= 5 for z in zs), str(zs))
    d = cells(L)
    fs = [k for k, v in d.items() if v == "F"]
    add("F only at layer 0 (4,4), present", fs == [(0, 4, 4)], str(fs))
    asym = [k for k, v in d.items() if d.get((k[0], k[2], 8 - k[1]), " ") != v]
    add("4-fold symmetry", not asym, f"first mismatch {asym[:2]}")
    if prev is None:
        add("superset of previous stage", True, "first stage")
    else:
        pd = prev[0]
        miss = []
        for k, v in pd.items():
            n = d.get(k, " ")
            if n == v or (v, n) in UPGRADES or (k == (-1, 4, 4) and v == "S" and n == "k"):
                continue
            miss.append((k, v, n))
        add("superset of previous stage", not miss, f"first break {miss[:2]}")
    ch = {c for v in d.values() for c in v}
    add("has stone, wood and metal", bool(ch & STONE and ch & WOOD and ch & METAL),
        f"stone={bool(ch & STONE)} wood={bool(ch & WOOD)} metal={bool(ch & METAL)}")
    if prev is not None:
        pd = prev[0]
        newm = []
        for name, S in (("stone", STONE), ("wood", WOOD), ("metal", METAL)):
            if any(v in S and pd.get(k, " ") != v for k, v in d.items()):
                newm.append(name)
        add("adds new cells in >=2 materials", len(newm) >= 2, "new: " + ",".join(newm))
    above = [(z, d.get((z, 4, 4), " ")) for z in range(1, 6)]
    add("smoke column above F clear", all(c in " ." for _, c in above), str(above))
    under = d.get((-1, 4, 4), " ")
    want = "k" if st["stage"] == 6 else "S"
    add(f"under the fire is {want}", under == want, f"got {under!r}")
    solids = [k for k, v in d.items() if solid(v)]
    zmax = max(k[0] for k in solids)
    rmin, rmax = min(k[1] for k in solids), max(k[1] for k in solids)
    cmin, cmax = min(k[2] for k in solids), max(k[2] for k in solids)
    fp = max(rmax - rmin + 1, cmax - cmin + 1)
    h = zmax + 2
    box = (rmax - rmin + 1, cmax - cmin + 1, h)
    if prev is None:
        add("bounding box grows or stays", True, f"{box[0]}x{box[1]}x{box[2]}")
    else:
        pb = prev[1]
        add("bounding box grows or stays", all(a >= b for a, b in zip(box, pb)), f"{pb} -> {box}")
    if st["stage"] == 6:
        add("stage VI fits 9x9x7", box[0] <= 9 and box[1] <= 9 and box[2] <= 7, str(box))
    a, b, c, e = SIZES[st["stage"]]
    add("size follows spec table", a <= fp <= b and c <= h <= e, f"{fp}x{fp}x{h}, wanted fp {a}-{b} h {c}-{e}")
    def side_open(cells_):
        return all(d.get((z, r, c), " ") in " ." for (r, c) in cells_ for z in (0, 1))
    sides = {"W": [(4, c) for c in range(0, 4)], "E": [(4, c) for c in range(5, 9)],
             "N": [(r, 4) for r in range(0, 4)], "S": [(r, 4) for r in range(5, 9)]}
    op = [k for k, v in sides.items() if side_open(v)]
    add("walkable entrance (layers 0+1)", bool(op), "open sides " + "".join(op))
    # ---- fluids
    fl = {k: v for k, v in d.items() if v in FLUID}
    bad = []
    for (z, r, c), v in fl.items():
        for dz_, dr_, dc_ in ((0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1), (-1, 0, 0)):
            n = (z + dz_, r + dr_, c + dc_)
            ok_ = n in d and (d[n] in (WET_OK if v == "v" else LAVA_OK) or d[n] == v or (v == "V" and d[n] == "b" and n[0] == z and dz_ == 0))
            if not ok_: bad.append(f"{v}@{(z, r, c)} leaks via {n} = {d.get(n, ' ')!r}")
        up = d.get((z + 1, r, c), " ")
        if v == "v" and up in FLUID: bad.append(f"v@{(z, r, c)} fluid above")
        if v == "V" and (up not in ".bSO" or up == " "): bad.append(f"V@{(z, r, c)} needs air, b or S above, has {up!r}")
        if z < 0 and v: bad.append(f"{v}@{(z, r, c)} at floor layer")
    add("fluid containment (sides+below solid, grate above lava)", not bad, "; ".join(bad[:2]))
    bad = []
    for (z, r, c), v in fl.items():
        if v != "V": continue
        for (z2, r2, c2), w in d.items():
            if w in BURN and abs(r2 - r) <= 2 and abs(c2 - c) <= 2 and -1 <= z2 - z <= 3:
                bad.append(f"{w}@{(z2, r2, c2)} near V@{(z, r, c)}")
    add("fire safety (no wood/banner near lava)", not bad, "; ".join(bad[:2]))
    add("lava only in forge and demonic lanes", lane["id"] in LAVA_LANES or not any(v == "V" for v in fl.values()),
        f"lane {lane['id']}")
    zone = {(z, 4, 4) for z in range(1, 6)} | {(z, r, c) for z in (0, 1) for (r, c) in ((4, 3), (4, 5), (3, 4), (5, 4))}
    add("fluids clear of smoke column and fire plus-zone", not any(k in zone for k in fl), "")
    nw = sum(1 for v in fl.values() if v == "v"); nl = sum(1 for v in fl.values() if v == "V")
    add("fluid counts", True, f"water {nw}, lava {nl}")
    if st["stage"] == 6:
        add(f"stage VI water <= {MAX_WATER}, lava <= {MAX_LAVA}", nw <= MAX_WATER and nl <= MAX_LAVA, f"{nw}, {nl}")
    if st["stage"] >= 3: add("water present (stage >= III)", nw > 0, str(nw))
    if lane["id"] == "forge" and st["stage"] >= 4: add("lava present (forge, stage >= IV)", nl > 0, str(nl))
    if lane["id"] == "demonic" and st["stage"] >= 4: add("lava present (demonic, stage >= IV)", nl > 0, str(nl))
    # support rule: pots, candles, carpets and skulls need a solid block below
    bad = [f"{ch}@{k} on {d.get((k[0]-1,k[1],k[2]),' ')!r}" for k, ch in d.items() if ch in "fCmz"
           and (k[0] < 0 or d.get((k[0]-1, k[1], k[2]), " ") not in SUPPORT)]
    add("pots/candles/carpets/skulls have support below", not bad, "; ".join(bad[:2]))
    cc = {}
    for ch in d.values(): cc[ch] = cc.get(ch, 0) + 1
    bad = [f"{ch} x{cc[ch]}" for ch, mx in COSTLY.items() if cc.get(ch, 0) > mx]
    add("costly blocks (anvil, bell, gold, skull) <= 4", not bad, ", ".join(bad))
    if prev is not None:
        add("fluids never removed", all(d.get(k) == v for k, v in prev[0].items() if v in FLUID), "")
    cnt = {}
    for k, v in d.items():
        if solid(v) and v != "F" and v not in FLUID: cnt[v] = cnt.get(v, 0) + 1
    tot = sum(cnt.values())
    stn = sum(n for c_, n in cnt.items() if c_ in STONE)
    wd = sum(n for c_, n in cnt.items() if c_ in WOOD)
    mt = sum(n for c_, n in cnt.items() if c_ in METAL)
    if st["stage"] == 1: add(f"stage I under ~{MAX_I} blocks", tot <= MAX_I, f"{tot}")
    if st["stage"] == 6: add(f"stage VI under ~{MAX_VI} blocks", tot <= MAX_VI, f"{tot}")
    add("block counts", True, f"total {tot}: stone {stn}, wood {wd}, metal {mt}, other {tot - stn - wd - mt} | "
        + " ".join(f"{k}{v}" for k, v in sorted(cnt.items())))
    return res, (d, box)

def main():
    if len(sys.argv) != 2: sys.exit(__doc__)
    data = json.load(open(sys.argv[1]))
    legend = data["legend"]
    fails = 0
    ids = [l["id"] for l in data["lanes"]]
    ok = ids == EXPECTED
    print(("PASS" if ok else "FAIL"), "lanes are " + ", ".join(EXPECTED), ids)
    fails += not ok
    for lane in data["lanes"]:
        nums = [s["stage"] for s in lane["stages"]]
        ok = nums == [1, 2, 3, 4, 5, 6]
        print(("PASS" if ok else "FAIL"), f"[{lane['id']}] six stages 1..6", nums)
        fails += not ok
        pal = lane.get("palette", {})
        miss = [k for k in PAL_KEYS if not str(pal.get(k, "")).startswith("minecraft:")]
        print(("PASS" if not miss else "FAIL"), f"[{lane['id']}] palette has minecraft: ids for {''.join(PAL_KEYS)}", "missing " + "".join(miss) if miss else "")
        fails += bool(miss)
        prev = None
        for st in lane["stages"]:
            res, info = check_stage(legend, lane, st, prev)
            for rule, good, detail in res:
                tag = "PASS" if good else "FAIL"
                fails += not good
                print(f"{tag} [{lane['id']} {ROMAN[st['stage']-1]}] {rule}" + (f" ({detail})" if detail else ""))
            if info is None: break
            prev = info
    print("\nRESULT:", "ALL PASS" if not fails else f"{fails} FAIL")
    sys.exit(1 if fails else 0)

main()
