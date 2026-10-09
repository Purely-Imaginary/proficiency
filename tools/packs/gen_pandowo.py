#!/usr/bin/env python3
"""Builds the Pandowo XP datapack (neoforge/packs/pandowo) from the pack's jars.

    python3 -I tools/packs/gen_pandowo.py JARS_DIR [JARS_DIR ...]

JARS_DIR holds the Pandowo mod jars (the survey copied them to pan/ and pan2/). Every id the datapack
names is checked against what the jars register: an entity id must be in a jar's language file, an
entity tag or one of the explicit extras below, a block id must have a blockstate file. A typo stops
the script. It writes the datapack JSON and the two id lists the unit test reads
(entity-ids.txt, block-ids.txt). Nothing is guessed at runtime: edit the tables here and run it again.
"""
import glob
import json
import os
import re
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "neoforge", "packs", "pandowo")

# ---- what to pay nothing for ---------------------------------------------------------------------

KILL_GROUPS = [
    ("Summons and familiars of Ars Nouveau. A summon spell is a free supply of kills.", [
        "ars_nouveau:ally_vex", "ars_nouveau:summon_horse", "ars_nouveau:summon_skeleton",
        "ars_nouveau:summon_wolf", "ars_nouveau:familiar_amethyst_golem", "ars_nouveau:familiar_bookwyrm",
        "ars_nouveau:familiar_drygmy", "ars_nouveau:familiar_starbuncle",
        "ars_nouveau:familiar_whirlisprig", "ars_nouveau:familiar_wixie"]),
    ("Ars Nouveau's own helpers: they belong to a player and never attack. The Dummy is a training target.", [
        "ars_nouveau:starbuncle", "ars_nouveau:gift_starby", "ars_nouveau:wixie", "ars_nouveau:whirlisprig",
        "ars_nouveau:drygmy", "ars_nouveau:bookwyrm", "ars_nouveau:amethyst_golem", "ars_nouveau:dummy"]),
    ("Things a player builds or brings along: golems, a clay golem, the cloud and fire minions of the Aether "
     "staves, an illusion of the player, a loyal zombie, Mowzie's player-bound Umvuthana and the Dragon "
     "Mounts dragon (one id for wild and tamed, so none).", [
        "friendsandfoes:copper_golem", "friendsandfoes:tuff_golem", "friendsandfoes:player_illusion",
        "takesapillage:clay_golem", "aether:cloud_minion", "aether:fire_minion", "twilightforest:loyal_zombie",
        "mowziesmobs:umvuthana_follower_player", "mowziesmobs:umvuthana_follower_raptor",
        "mowziesmobs:umvuthana_crane_player", "dmr:dragon"]),
    ("Livestock and tame or harmless animals: breeding Aether and Twilight Forest animals is a supply of kills.", [
        "aether:aerbunny", "aether:aerwhale", "aether:flying_cow", "aether:phyg", "aether:sheepuff", "aether:moa",
        "deep_aether:quail", "deep_aether:aerglow_fish", "deep_aether:windfly",
        "twilightforest:bighorn_sheep", "twilightforest:boar", "twilightforest:deer", "twilightforest:dwarf_rabbit",
        "twilightforest:penguin", "twilightforest:squirrel", "twilightforest:tiny_bird", "twilightforest:raven",
        "friendsandfoes:moobloom", "friendsandfoes:crab", "friendsandfoes:rascal", "friendsandfoes:glare",
        "wetland_whimsy:crane", "mowziesmobs:lantern"]),
    ("Traders and quest givers. They are people, not game.", [
        "goblintraders:goblin_trader", "goblintraders:vein_goblin_trader", "supplementaries:red_merchant",
        "piglinproliferation:piglin_traveler", "twilightforest:quest_ram"]),
    ("Target Dummy: a hit on it pays and a kill is free, so it would be an unlimited safe skill farm.", [
        "dummmmmmy:target_dummy"]),
]

BOSS_IDS = [
    "cataclysm:the_harbinger", "cataclysm:maledictus", "cataclysm:scylla", "mowziesmobs:sculptor",
    "twilightforest:plateau_boss", "deep_aether:eots_controller",
]
SEGMENT = "deep_aether:eots_segment"

# Between the shovel rule (-40) and the shipped axe rule (-50): a pickaxe or shovel break of a block that
# is in both tags still pays Mining or Excavation, and anything else (an axe, a hand) pays no Woodcutting.
BREAK_PRIORITY = -45

# Furniture and machines that sit in #minecraft:mineable/axe, so a natural one (a village or a ruin
# carries a lot of furniture) paid Woodcutting. Mod wildcards first, then ids taken from each jar's own
# axe tag by a name filter.
BREAK_WILDCARDS = ["handcrafted:*", "another_furniture:*"]
BREAK_FROM_AXE_TAG = [
    # (namespace, regex on the path, or None for every entry of the tag)
    ("create", None), ("createaddition", None), ("toms_storage", None), ("functionalstorage", None),
    ("farmersdelight", r"(cabinet|crate|cutting_board|basket)$"),
    ("twilightdelight", r"(cabinet|crate)$"), ("mynethersdelight", r"_crate$"), ("endersdelight", r"_crate$"),
    ("wetland_whimsy", r"cabinet$"), ("morevillagers", None), ("sawmill", None), ("tradingpost", None),
    ("barteringstation", None), ("illagerinvasion", r"imbuing_table$"),
    ("supplementaries", r"^(bellows|clock_block|lock_block|notice_board|pulley_block|speaker_block|way_sign|"
                        r"way_sign_wall|flower_box)$"),
    ("ars_nouveau", r"^(alteration_table|scribes_table|storage_lectern|repository|repository_controller|"
                    r"item_detector|archwood_chest)$"),
]

# Entity ids that no language file or tag lists but the mod registers (read from its class files).
EXTRA_ENTITIES = ["dmr:dragon"]


def read_jars(dirs):
    jars = []
    for d in dirs:
        jars += sorted(glob.glob(os.path.join(d, "*.jar")))
    return jars


def _tag_values(raw):
    for v in raw.get("values", []):
        yield v if isinstance(v, str) else v.get("id", "")


def _norm(v):
    """A bare value in a tag file is a minecraft: id."""
    return v if ":" in v or v.startswith("#") else "minecraft:" + v


def resolve_axe(block_tags):
    """block_tags: {'ns:path': [values]} from every jar. Returns {namespace: {ids}} of minecraft:mineable/axe,
    following #sub-tags (a tag file in any jar adds to the same tag, as it does in the game)."""
    out, seen = {}, set()

    def walk(tag):
        if tag in seen:
            return
        seen.add(tag)
        for v in block_tags.get(tag, ()):
            v = _norm(v)
            if v.startswith("#"):
                walk(v[1:])
            elif v:
                out.setdefault(v.split(":")[0], set()).add(v)
    walk("minecraft:mineable/axe")
    return out


def scan(jars):
    entities, blocks, block_tags = set(), set(), {}
    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            continue
        for n in z.namelist():
            m = re.match(r"assets/([^/]+)/lang/en_us\.json$", n)
            if m:
                try:
                    lang = json.loads(z.read(n))
                except ValueError:
                    lang = {}
                for k, v in lang.items():
                    mm = re.match(r"entity\.([a-z0-9_]+)\.([a-z0-9_]+)$", k)
                    if mm and isinstance(v, str):
                        entities.add(mm.group(1) + ":" + mm.group(2))
            m = re.match(r"data/([^/]+)/tags/entity_types?/(.+)\.json$", n)
            if m:
                for v in _tag_values(json.loads(z.read(n))):
                    if v and not v.startswith("#"):
                        entities.add(_norm(v))
            m = re.match(r"assets/([^/]+)/blockstates/(.+)\.json$", n)
            if m:
                blocks.add(m.group(1) + ":" + m.group(2))
            m = re.match(r"data/([^/]+)/tags/blocks?/(.+)\.json$", n)
            if m:
                block_tags.setdefault(m.group(1) + ":" + m.group(2), []).extend(_tag_values(json.loads(z.read(n))))
    entities.update(EXTRA_ENTITIES)
    return entities, blocks, resolve_axe(block_tags)


def break_ids(axe, blocks, problems):
    """The explicit block ids of BREAK_FROM_AXE_TAG, each checked against a blockstate."""
    ids = []
    for ns, pattern in BREAK_FROM_AXE_TAG:
        found = sorted(v for v in axe.get(ns, ()) if pattern is None or re.search(pattern, v.split(":")[1]))
        if not found:
            problems.append("no axe-tag blocks for " + ns)
        ids += found
    for i in ids:
        if i not in blocks:
            problems.append("no blockstate for " + i)
    return sorted(set(ids))


def code_registers(jars, ids, problems):
    """A second opinion on entity ids, apart from the language file the id list came from: the owning
    mod's code must contain the id's path as a string (a registration call names it)."""
    blobs = {}
    for jar in jars:
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            continue
        ns = set()
        data = bytearray()
        for n in z.namelist():
            m = re.match(r"assets/([^/]+)/lang/", n)
            if m:
                ns.add(m.group(1))
            if n.endswith(".class"):
                data += z.read(n)
        for x in ns:
            blobs.setdefault(x, bytearray()).extend(data)
    for i in ids:
        ns, path = i.split(":")
        blob = blobs.get(ns)
        if blob is not None and path.encode() not in blob:
            problems.append("no code in the %s jar names %s (lang key only?)" % (ns, i))


def main(argv):
    if len(argv) < 2:
        sys.exit(__doc__)
    entities, blocks, axe = scan(read_jars(argv[1:]))
    problems = []

    kill = []
    for why, ids in KILL_GROUPS:
        for i in ids:
            if i not in entities:
                problems.append("unknown entity " + i)
        kill.append({"match": ids, "skill": "none", "why": why})
    kill_bonus = [{"match": ids, "skill": "none",
                   "why": why + " The kill bonus (and the first-time bonus that rides on it) is free too, "
                          "or a weapon skill would still farm them."} for why, ids in KILL_GROUPS]
    kill.append({
        "match": [SEGMENT], "skill": "none",
        "why": "One of the Eye of the Storm's many segments. Only the controller counts as a kill."})
    for i in BOSS_IDS + [SEGMENT]:
        if i not in entities:
            problems.append("unknown boss " + i)

    brk_ids = break_ids(axe, blocks, problems)
    jars = read_jars(argv[1:])
    code_registers(jars, [i for _, ids in KILL_GROUPS for i in ids] + BOSS_IDS + [SEGMENT], problems)
    if problems:
        sys.exit("\n".join(problems))

    rule = {
        "_comment": "Pandowo (NeoForge 1.21.1, Better MC pack). Generated by tools/packs/gen_pandowo.py from the pack's jars; edit the tables there. Install: see ../README.md.",
        "break": [{
            "match": BREAK_WILDCARDS + brk_ids, "skill": "none", "priority": BREAK_PRIORITY,
            "why": "Furniture and machines are in #minecraft:mineable/axe, so one that generates in a village or a ruin paid Woodcutting 0.5 + 0.3 x hardness. A block somebody placed already pays nothing; this covers the ones found in the world."}],
        "kill": kill,
        "kill_bonus": kill_bonus + [{
            "match": [SEGMENT], "skill": "none",
            "why": "No kill bonus per segment of the Eye of the Storm."}],
        "boss": [
            {"match": BOSS_IDS, "boss": True,
             "why": "Listed explicitly as well as through the mods' own boss tags (#c:bosses, #neoforge:bosses), which the shipped rule already follows. Harbinger, Maledictus, Scylla, the Sculptor, the Twilight plateau boss and the Eye of the Storm controller were not in the notable_bosses tag."},
            {"match": [SEGMENT], "boss": False,
             "why": "The segments carry the boss tag too; counting each would pay the boss kill bonus once per segment."}],
        "first_time": [
            {"match": BOSS_IDS, "kind": "entity", "multiplier": 10.0, "priority": -5,
             "why": "The first-time bonus for a boss, as for every notable boss."},
            {"match": [SEGMENT], "kind": "entity", "multiplier": 1.0, "priority": -5,
             "why": "A segment is not a boss to the first-time bonus either."}],
    }
    path = os.path.join(OUT, "datapack", "data", "pandowo", "proficiency", "xp_sources")
    os.makedirs(path, exist_ok=True)
    with open(os.path.join(path, "pandowo.json"), "w") as f:
        json.dump(rule, f, indent=2)
        f.write("\n")
    with open(os.path.join(OUT, "datapack", "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"pack_format": 48, "description": "Proficiency XP sources for the Pandowo pack"}}, f, indent=2)
        f.write("\n")
    named_ns = {i.split(":")[0] for i in brk_ids}
    with open(os.path.join(OUT, "entity-ids.txt"), "w") as f:
        f.write("\n".join(sorted(entities)) + "\n")
    with open(os.path.join(OUT, "block-ids.txt"), "w") as f:
        f.write("\n".join(sorted(b for b in blocks if b.split(":")[0] in named_ns)) + "\n")
    print("wrote", len(kill), "kill rules,", len(brk_ids), "break ids,", len(entities), "entity ids")


if __name__ == "__main__":
    main(sys.argv)
