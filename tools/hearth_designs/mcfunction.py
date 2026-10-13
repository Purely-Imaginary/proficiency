#!/usr/bin/env python3
"""Emit setblock commands for every Hearth design lane at a stage (default 6), using each lane's palette.

    python3 mcfunction.py <designs.json> <outdir> [stage]

Writes <outdir>/build_<lane>.mcfunction with the fire (layer 0, row 4, col 4) at x = 30 * lane index, z = 0,
y = FIRE_Y. Order: solids, then attached blocks (lanterns, banners, candles, carpets, pots, bells, rods), then
fluids last so they do not flow. For the orbit-video rig (2026-10-11, 1.5.5 palettes).
"""
import json, sys, os

FIRE_Y = -60
SPACING = 30
# fallbacks for roles a lane's palette omits (or that no palette defines)
FALLBACK = {"S": "stone_bricks", "s": "stone_brick_slab", "W": "oak_log", "P": "oak_planks", "w": "oak_slab",
            "N": "red_banner", "c": "cut_copper", "i": "iron_block", "g": "gold_block", "b": "iron_bars",
            "u": "cauldron", "v": "water", "V": "lava", "k": "lodestone", "a": "anvil", "B": "bell",
            "r": "lightning_rod", "h": "chain", "l": "lantern", "f": "potted_fern", "x": "barrel",
            "C": "candle", "m": "red_carpet", "G": "glass", "z": "skeleton_skull", "I": "blue_ice",
            "n": "snow_block", "q": "moss_block", "Y": "magma_block", "O": "obsidian", "L": "oak_leaves"}
LATE = set("lNBrCmfzh")   # attached / fragile blocks: second pass
FLUID = set("vV")          # last


def outward(dx, dz):
    if abs(dz) >= abs(dx):
        return "south" if dz > 0 else "north"
    return "east" if dx > 0 else "west"


def rot16(dx, dz):
    # skull / banner rotation 0..15, 0 = south, 4 = west, 8 = north, 12 = east (facing outward)
    return {"south": 0, "west": 4, "north": 8, "east": 12}[outward(dx, dz)]


def state(role, bid, layer, dx, dz, above, below_solid):
    n = bid.replace("minecraft:", "")
    f = outward(dx, dz)
    if n.endswith("_wall_banner") or n.endswith("_banner"):
        base = n.replace("_wall_banner", "").replace("_banner", "")
        if below_solid:
            return f"{base}_banner[rotation={rot16(dx, dz)}]"
        return f"{base}_wall_banner[facing={f}]"
    if n.endswith("_skull") or n.endswith("_head"):
        return f"{n}[rotation={rot16(dx, dz)}]"
    if n.endswith("_slab"):
        return f"{n}[type=bottom]"
    if n.endswith("_stairs"):
        return f"{n}[facing={f},half=bottom]"
    if n.endswith("_log") or n.endswith("_stem") or n.endswith("_wood"):
        axis = ("x" if abs(dz) > abs(dx) else "z") if layer == 3 else "y"
        return f"{n}[axis={axis}]"
    if n.endswith("candle"):
        return f"{n}[lit=true]"
    if n.endswith("leaves"):
        return f"{n}[persistent=true]"
    if n == "barrel":
        return "barrel[facing=up]"
    if n == "lantern" or n == "soul_lantern":
        return f"{n}[hanging={'true' if above == 'h' else 'false'}]"
    if n == "bell":
        return f"bell[attachment=floor,facing={'north' if f in ('north', 'south') else 'east'}]"
    if n == "anvil":
        return f"anvil[facing={'east' if f in ('north', 'south') else 'north'}]"
    if n == "lightning_rod":
        return "lightning_rod[facing=up]"
    if n == "chain":
        return "chain[axis=y]"
    return n


def lane_cmds(lane, stage, cx):
    st = [s for s in lane["stages"] if s["stage"] == stage][0]
    pal = lane.get("palette", {})
    layers = st["layers"]
    p1, p2, p3 = [], [], []
    for ly in sorted(layers, key=int):
        rows = layers[ly]
        up = layers.get(str(int(ly) + 1))
        dn = layers.get(str(int(ly) - 1))
        for r in range(9):
            for c in range(9):
                ch = rows[r][c]
                if ch in " .":
                    continue
                dx, dz = c - 4, r - 4
                x, y, z = cx + dx, FIRE_Y + int(ly), dz
                if ch == "F":
                    p1.append(f"setblock {x} {y} {z} proficiency:hearth[stage={stage}]")
                    continue
                bid = pal.get(ch) or FALLBACK[ch]
                above = up[r][c] if up else " "
                below = dn[r][c] if dn else " "
                below_solid = below not in " .lbhNBCmfzr" if dn else True
                s = state(ch, bid, int(ly), dx, dz, above, below_solid)
                cmd = f"setblock {x} {y} {z} minecraft:{s}"
                (p3 if ch in FLUID else p2 if ch in LATE else p1).append(cmd)
    return p1 + p2 + p3


def main():
    d = json.load(open(sys.argv[1]))
    out = sys.argv[2]
    stage = int(sys.argv[3]) if len(sys.argv) > 3 else 6
    os.makedirs(out, exist_ok=True)
    for i, lane in enumerate(d["lanes"]):
        cmds = lane_cmds(lane, stage, i * SPACING)
        open(f"{out}/build_{lane['id']}.mcfunction", "w").write("\n".join(cmds) + "\n")
        print(lane["id"], i * SPACING, len(cmds), "commands")


main()
