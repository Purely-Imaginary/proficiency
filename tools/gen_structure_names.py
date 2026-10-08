#!/usr/bin/env python3
"""List the structure and dimension ids of a modpack, with and without a display name.

Run it where the packs live (the machine that has the game packs), read-only:
    python3 tools/gen_structure_names.py ~/pandowo ~/mcnowy [--lang common/src/main/resources/assets/proficiency/lang/en_us.json]

Ids come from data/<ns>/worldgen/structure/**/*.json and data/<ns>/dimension/*.json inside
mods/*.jar (nested jarjar jars too) and world/datapacks. A structure counts as named when the
mod's own lang has structure.<ns>.<path>, or when our lang file has it. Variants collapse the
same way StructureIds.canonical does. Prints MISSING ids last, so a pack update is one diff.
"""
import glob
import io
import json
import os
import re
import sys
import zipfile

STRUCT = re.compile(r"^data/([^/]+)/worldgen/structure/(.+)\.json$")
DIM = re.compile(r"^data/([^/]+)/dimension/([^/]+)\.json$")
LANG = re.compile(r"^assets/([^/]+)/lang/en_us\.json$")


def canonical(i):
    """Mirror of StructureIds.canonical in the mod."""
    for prefix, to in (("minecraft:ruined_portal", "minecraft:ruined_portal"),
                       ("minecraft:village_", "minecraft:village"),
                       ("minecraft:ocean_ruin_", "minecraft:ocean_ruin")):
        if i.startswith(prefix):
            return to
    return {"minecraft:shipwreck_beached": "minecraft:shipwreck",
            "minecraft:mineshaft_mesa": "minecraft:mineshaft"}.get(i, i)


def scan(z, structs, dims, lang, depth=0):
    for n in z.namelist():
        m = STRUCT.match(n)
        if m:
            structs.add(f"{m[1]}:{m[2]}")
        m = DIM.match(n)
        if m:
            dims.add(f"{m[1]}:{m[2]}")
        if LANG.match(n):
            try:
                lang.update(json.loads(z.read(n).decode("utf-8", "replace")))
            except ValueError:
                pass
        if depth < 2 and n.endswith(".jar") and "META-INF/jarjar" in n:
            scan(zipfile.ZipFile(io.BytesIO(z.read(n))), structs, dims, lang, depth + 1)


def main():
    args = sys.argv[1:]
    ours = {}
    if "--lang" in args:
        k = args.index("--lang")
        ours = json.load(open(args[k + 1], encoding="utf-8"))
        del args[k:k + 2]
    for pack in args:
        pack = os.path.expanduser(pack)
        structs, dims, lang = set(), set(), {}
        for jar in sorted(glob.glob(pack + "/mods/*.jar")):
            try:
                scan(zipfile.ZipFile(jar), structs, dims, lang)
            except zipfile.BadZipFile:
                pass
        for dp in glob.glob(pack + "/world/datapacks/*"):
            if dp.endswith(".zip"):
                scan(zipfile.ZipFile(dp), structs, dims, lang)
            elif os.path.isdir(dp):
                for root, _, files in os.walk(dp):
                    for f in files:
                        rel = os.path.relpath(os.path.join(root, f), dp)
                        m = STRUCT.match(rel)
                        if m:
                            structs.add(f"{m[1]}:{m[2]}")
                        m = DIM.match(rel)
                        if m:
                            dims.add(f"{m[1]}:{m[2]}")

        def key(kind, i):
            ns, path = i.split(":")
            return f"{kind}.{ns}.{path.replace('/', '.')}"

        print(f"# {pack}: {len(structs)} structure ids, {len(dims)} dimensions")
        missing = []
        for kind, ids in (("structure", sorted({canonical(s) for s in structs})),
                          ("dimension", sorted(dims))):
            for i in ids:
                k = key(kind, i)
                if k in lang:
                    print(f"mod-named  {k} = {lang[k]}")
                elif k in ours:
                    print(f"ours       {k} = {ours[k]}")
                else:
                    missing.append(k)
        for k in missing:
            print(f"MISSING    {k}")


if __name__ == "__main__":
    main()
