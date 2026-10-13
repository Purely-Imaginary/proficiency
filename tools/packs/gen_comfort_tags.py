#!/usr/bin/env python3
"""Builds the Comfort tag mappings of the Pandowo and Reclamation datapacks from the packs' jars.

    python3 -I tools/packs/gen_comfort_tags.py [--pandowo JARS_DIR ...] [--reclamation JARS_DIR ...]

The Hearth counts furniture through block tags, proficiency:comfort/<kind> (docs/HEARTH-SPEC.md,
"Comfort"); a pack adds its furniture to them with plain data. This writes those files:

    neoforge/packs/pandowo/datapack/data/proficiency/tags/block/comfort/<kind>.json     (1.21.1, tags/block)
    forge-1.20.1/packs/reclamation/datapack/data/proficiency/tags/blocks/comfort/<kind>.json  (1.20.1, tags/blocks)

and the list of everything they name that the jars really carry (comfort-tag-ids.txt next to each
pack's block-ids.txt), which the unit test reads. Every mapping names either a block tag the mod ships
itself (the mod's own "chairs", "benches", ... so a new wood type is covered by the mod's next update)
or an explicit block id; each is checked against the jars (a tag file, or a blockstate file), and a
miss stops the script. Entries are "required": false, so a pack that drops a mod loses nothing.

What goes where is a decision, written once in the tables below:
  seat       chairs, benches, sofas, couches, stools
  storage    cabinets, drawers, cupboards, counters, wardrobes and every table and desk (a table has no
             kind of its own: the spec puts modded tables in storage or seat by pack choice, and these
             packs choose storage), shelves
  bed        a fancy bed          books   a bookshelf or a stack of books    light  a lamp
  plant      a plant pot, a flower box     trophy  a mob trophy              carpet  a rug
  fireplace  a stove or an oven (the kitchen fires; the fire kind includes #proficiency:comfort/fireplace)
Mod blocks that are no furniture (trims, crockery, curtains, shutters, crates, cushions, sinks) are left out.
"""
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..")
PANDOWO = os.path.join(ROOT, "neoforge", "packs", "pandowo")
RECLAMATION = os.path.join(ROOT, "forge-1.20.1", "packs", "reclamation")

KINDS = ["bed", "seat", "carpet", "banner", "fire", "light", "plant", "art", "books", "music", "storage", "trophy"]
EXTRA_KINDS = ["fireplace"]

FD_CABINETS = ["acacia", "bamboo", "birch", "cherry", "crimson", "dark_oak", "jungle", "mangrove", "oak", "spruce", "warped"]

# kind -> entries; "#ns:tag" is a block tag a jar ships, anything else an explicit block id.
PANDOWO_MAP = {
    "bed": ["#handcrafted:fancy_beds"],
    "seat": ["#handcrafted:chairs", "#handcrafted:benches", "#handcrafted:couches", "#handcrafted:dining_benches",
             "#another_furniture:chairs", "#another_furniture:benches", "#another_furniture:sofas",
             "#another_furniture:stools", "#another_furniture:tall_stools"],
    "carpet": ["farmersdelight:canvas_rug"],
    "light": ["#another_furniture:lamps"],
    "plant": ["#handcrafted:pots", "#another_furniture:flower_boxes"],
    "books": ["handcrafted:stackable_book"],
    "storage": ["#handcrafted:cupboards", "#handcrafted:drawers", "#handcrafted:nightstands",
                "#handcrafted:counters", "#handcrafted:desks", "#handcrafted:tables", "#handcrafted:side_tables",
                "#handcrafted:shelves", "#another_furniture:drawers", "#another_furniture:shelves",
                "#another_furniture:tables"] + ["farmersdelight:%s_cabinet" % w for w in FD_CABINETS],
    "trophy": ["#handcrafted:trophies"],
    "fireplace": ["farmersdelight:stove", "handcrafted:oven"],
}

# Macaw's Furniture ships a tag per piece ("wadrobe" is the mod's own spelling).
MCW = ["chair", "modern_chair", "stool_chair", "striped_chair", "chaise", "couch"]
MCW_BOOKS = ["bookshelf", "bookshelf_cupboard", "bookshelf_drawer", "lower_bookshelf_drawer"]
MCW_STORAGE = ["cabinet", "counter", "cupboard_counter", "drawer", "double_drawer", "drawer_counter",
               "double_drawer_counter", "large_drawer", "triple_drawer", "lower_triple_drawer", "wadrobe",
               "double_wardrobe", "modern_wardrobe", "table", "coffee_table", "end_table", "glass_table", "desk",
               "covered_desk", "modern_desk"]
RECLAMATION_MAP = {
    "seat": ["#mcwfurnitures:" + t for t in MCW],
    "carpet": ["farmersdelight:canvas_rug"],
    "books": ["#mcwfurnitures:" + t for t in MCW_BOOKS],
    "storage": ["#mcwfurnitures:" + t for t in MCW_STORAGE] + ["#farmersdelight:cabinets"],
    "fireplace": ["farmersdelight:stove"],
}


def read_jars(dirs):
    import glob
    jars = []
    for d in dirs:
        jars += sorted(glob.glob(os.path.join(d, "*.jar")))
    return jars


def scan(jars):
    """(block ids with a blockstate file, block tag ids, block tags with their values)"""
    blocks, tags = set(), {}
    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            continue
        for n in z.namelist():
            m = re.match(r"assets/([^/]+)/blockstates/(.+)\.json$", n)
            if m:
                blocks.add(m.group(1) + ":" + m.group(2))
            m = re.match(r"data/([^/]+)/tags/blocks?/(.+)\.json$", n)
            if m:
                try:
                    raw = json.loads(z.read(n).decode("utf-8-sig"))
                except ValueError:
                    continue
                values = [v if isinstance(v, str) else v.get("id", "") for v in raw.get("values", [])]
                tags.setdefault(m.group(1) + ":" + m.group(2), []).extend(values)
    return blocks, tags


def check(mapping, blocks, tags, label):
    problems, verified = [], []
    for kind, entries in mapping.items():
        for entry in entries:
            if entry.startswith("#"):
                if entry[1:] not in tags:
                    problems.append("%s: no block tag %s in any jar" % (label, entry))
                else:
                    verified.append("tag " + entry)
            elif entry not in blocks:
                problems.append("%s: no blockstate for %s" % (label, entry))
            else:
                verified.append("block " + entry)
    return problems, sorted(set(verified))


def write_tags(mapping, folder, tag_dir):
    out = os.path.join(folder, "datapack", "data", "proficiency", "tags", tag_dir, "comfort")
    os.makedirs(out, exist_ok=True)
    for kind in KINDS + EXTRA_KINDS:
        path = os.path.join(out, kind + ".json")
        entries = mapping.get(kind)
        if not entries:
            if os.path.exists(path):
                os.remove(path)
            continue
        with open(path, "w") as f:
            f.write('{\n  "replace": false,\n  "values": [\n')
            f.write(",\n".join('    { "id": %s, "required": false }' % json.dumps(e) for e in entries))
            f.write("\n  ]\n}\n")


def main(argv):
    args = {"--pandowo": [], "--reclamation": []}
    current = None
    for a in argv[1:]:
        if a in args:
            current = a
        elif current:
            args[current].append(a)
        else:
            sys.exit(__doc__)
    if not any(args.values()):
        sys.exit(__doc__)
    problems = []
    for flag, folder, mapping, tag_dir in [("--pandowo", PANDOWO, PANDOWO_MAP, "block"),
                                           ("--reclamation", RECLAMATION, RECLAMATION_MAP, "blocks")]:
        if not args[flag]:
            continue
        blocks, tags = scan(read_jars(args[flag]))
        bad, verified = check(mapping, blocks, tags, flag[2:])
        problems += bad
        if bad:
            continue
        write_tags(mapping, folder, tag_dir)
        with open(os.path.join(folder, "comfort-tag-ids.txt"), "w") as f:
            f.write("\n".join(verified) + "\n")
        print("%s: %d kinds, %d verified entries" % (flag[2:], len(mapping), len(verified)))
    if problems:
        sys.exit("\n".join(problems))


if __name__ == "__main__":
    main(sys.argv)
