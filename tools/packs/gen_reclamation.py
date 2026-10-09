#!/usr/bin/env python3
"""Rebuilds the furniture-and-machines break rule of the Reclamation datapack from the pack's jars.

    python3 -I tools/packs/gen_reclamation.py JARS_DIR [JARS_DIR ...]

Same idea as gen_pandowo.py (it imports its jar scan): every id is checked against the jars'
#minecraft:mineable/axe tag, following sub-tags, and against a blockstate file, so a typo or a block
that is not in the tag stops the script. It rewrites the "furniture" rule (the break rule with skill
none) in reclamation.json and the block-ids.txt the unit test reads. Nothing else in the file changes.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import gen_pandowo as g  # noqa: E402

PACK = os.path.join(HERE, "..", "..", "forge-1.20.1", "packs", "reclamation")
RULE = os.path.join(PACK, "datapack", "data", "reclamation", "proficiency", "xp_sources", "reclamation.json")

FROM_AXE_TAG = [
    ("create", None), ("storagedrawers", None),
    ("farmersdelight", r"(cabinet|crate|cutting_board|basket)$"),
]
EXPLICIT = [  # picked by hand, each still has to be in the axe tag
    "ars_nouveau:alteration_table", "ars_nouveau:archwood_chest", "ars_nouveau:item_detector",
    "ars_nouveau:repository", "ars_nouveau:scribes_table", "ars_nouveau:storage_lectern",
    "botania:alfheim_portal", "botania:avatar", "botania:bellows", "botania:crafty_crate",
    "complicated_bees:apiary", "cookingforblockheads:fruit_basket", "cookingforblockheads:spice_rack",
    "cookingforblockheads:tool_rack", "embers:atmospheric_bellows", "embers:char_instiller",
    "embers:sealed_wood_keg", "enchanted:spinning_wheel", "naturesaura:auto_crafter",
    "naturesaura:flower_generator", "naturesaura:oak_generator", "naturesaura:offering_table",
    "naturesaura:wood_stand",
]


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    _, blocks, axe = g.scan(g.read_jars(argv[1:]))
    in_tag = set().union(*axe.values())
    problems = []
    ids = []
    for ns, pattern in FROM_AXE_TAG:
        found = [v for v in axe.get(ns, ()) if pattern is None or re.search(pattern, v.split(":")[1])]
        if not found:
            problems.append("no axe-tag blocks for " + ns)
        ids += found
    for i in EXPLICIT:
        if i not in in_tag:
            problems.append("not in the axe tag: " + i)
        ids.append(i)
    for i in ids:
        if i not in blocks:
            problems.append("no blockstate for " + i)
    if problems:
        sys.exit("\n".join(problems))
    ids = sorted(set(ids))
    with open(RULE) as f:
        data = json.load(f)
    rules = [r for r in data["break"] if r.get("skill") == "none"]
    if len(rules) != 1:
        sys.exit("expected exactly one skill none break rule, found %d" % len(rules))
    rules[0]["match"] = ids
    rules[0]["priority"] = g.BREAK_PRIORITY
    with open(RULE, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    named = {i.split(":")[0] for i in ids}
    with open(os.path.join(PACK, "block-ids.txt"), "w") as f:
        f.write("\n".join(sorted(b for b in blocks if b.split(":")[0] in named)) + "\n")
    print("wrote", len(ids), "break ids")


if __name__ == "__main__":
    main(sys.argv)
