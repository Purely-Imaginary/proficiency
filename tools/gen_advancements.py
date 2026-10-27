#!/usr/bin/env python3
"""Generates the Proficiency advancement tab for every line from one spec.

  common/src/main/resources/data/proficiency/advancement/<id>.json        (1.21.1: NeoForge + Fabric)
  forge-1.20.1/src/main/resources/data/proficiency/advancements/<id>.json (1.20.1 format)
  common/src/main/resources/assets/proficiency/lang/en_us.json            (titles + descriptions, English only)
  docs/ADVANCEMENTS.md                                                    (the id list, for FTB Quests)

Every advancement has ONE criterion, "event", of trigger minecraft:impossible. The mod awards it by
name from the code point that earns it (ProficiencyAdvancements.award), so FTB Quests and datapacks
can reference proficiency:<id> like any advancement. Keep ids in sync with core Achievements.java
(AdvancementDataTest checks the two against each other).
"""
import json, os, re, sys
from collections import OrderedDict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BG = "minecraft:textures/gui/advancements/backgrounds/adventure.png"

ADV = []  # dicts: id, parent, icon, frame, title, desc, hidden, chat, group


def add(id, parent, icon, title, desc, frame="task", hidden=False, chat=False, group=""):
    ADV.append(dict(id=id, parent=parent, icon=icon, title=title, desc=desc, frame=frame,
                    hidden=hidden, chat=chat, group=group))


add("root", None, "proficiency:foresters_compass", "Proficiency",
    "Doing a thing makes you better at it. Your milestones are kept here.", group="Root")

CATS = [
    ("combat", "Combat", "minecraft:iron_sword"),
    ("gathering", "Gathering", "minecraft:iron_pickaxe"),
    ("movement", "Movement", "minecraft:leather_boots"),
    ("crafting", "Crafting", "minecraft:anvil"),
    ("mastery", "Mastery skills", "minecraft:enchanted_book"),
    ("expedition", "Expedition", "minecraft:compass"),
    ("construction", "Construction", "minecraft:bricks"),
    ("social", "Social", "minecraft:cake"),
    ("survival", "Survival", "minecraft:lantern"),
]
TIERS = [(10, "task", "Novice"), (25, "task", "Practised"),
         (50, "goal", "Seasoned"), (100, "challenge", "Mastered")]
for cid, cname, icon in CATS:
    prev = "root"
    for level, frame, word in TIERS:
        id = "level_%s_%d" % (cid, level)
        add(id, prev, icon, "%s: %s" % (cname, word),
            "Reach level %d in any %s skill." % (level, cname.lower().replace(" skills", "")),
            frame=frame, chat=(level == 100), group="Levels")
        prev = id

add("first_star", "root", "minecraft:nether_star", "A Star Is Earned",
    "Earn your first Mastery star by playing on past level 100.", frame="goal", group="Mastery")
add("five_stars", "first_star", "minecraft:beacon", "Grand Master",
    "Earn all five Mastery stars in one skill.", frame="challenge", chat=True, group="Mastery")
add("jack_of_all_trades", "root", "minecraft:crafting_table", "Jack of All Trades",
    "Reach level 10 in every skill.", frame="goal", group="Mastery")
add("master_of_all", "jack_of_all_trades", "minecraft:dragon_egg", "Master of All Trades",
    "Reach level 100 in every skill.", frame="challenge", chat=True, group="Mastery")
add("first_synergy", "root", "minecraft:amethyst_shard", "Better Together",
    "Wake your first synergy by filling the talents it joins.", group="Talents")
add("first_signature_proc", "root", "minecraft:firework_star", "There It Is",
    "See a skill's signature proc land.", group="Talents")
add("first_expertise", "root", "minecraft:experience_bottle", "Expertise",
    "Spend your first Expertise point.", group="Expertise")
add("expertise_full", "first_expertise", "minecraft:end_crystal", "Fully Versed",
    "Fill the whole Expertise tree.", frame="challenge", chat=True, group="Expertise")

HEARTH = [("campfire", "Lit", "Light your first Hearth."),
          ("copper_block", "Settled", "Seal stage II of a Hearth with its offering."),
          ("iron_block", "Homestead", "Seal stage III of a Hearth with its offering."),
          ("gold_block", "A Proper Hall", "Seal stage IV of a Hearth with its offering."),
          ("diamond_block", "Gathering Place", "Seal stage V of a Hearth with its offering."),
          ("heart_of_the_sea", "Heart of the Home", "Seal stage VI, the last stage of a Hearth.")]
prev = "root"
for i, (icon, name, desc) in enumerate(HEARTH, 1):
    id = "hearth_stage_%d" % i
    add(id, prev, "minecraft:" + icon, name, desc,
        frame="task" if i < 4 else ("goal" if i < 6 else "challenge"), chat=(i == 6), group="Hearth")
    prev = id
HORNS = ["siege_horn", "siege_horn_2", "siege_horn_3", "siege_horn_4", "siege_horn_5"]
prev = "hearth_stage_1"
for i in range(1, 6):
    id = "siege_%d" % i
    add(id, prev, "proficiency:" + HORNS[i - 1], "Held the Line %s" % ["I", "II", "III", "IV", "V"][i - 1],
        "Defend your Hearth against a level %d siege." % i,
        frame="goal" if i < 5 else "challenge", chat=(i == 5), group="Sieges")
    prev = id

add("hidden_places", "root", "minecraft:filled_map", "Well Travelled",
    "Visit twenty biomes, dimensions and structures.", frame="goal", hidden=True, group="Hidden")
add("hidden_feast", "hearth_stage_1", "minecraft:cooked_beef", "Dinner Is Served",
    "Sit down at a Hearth Feast.", hidden=True, group="Hidden")
add("hidden_tales", "hearth_stage_1", "minecraft:written_book", "Tell Me a Tale",
    "Bring your Tales home and have them paid in at a Hearth.", hidden=True, group="Hidden")
add("hidden_chronicle", "hearth_stage_1", "minecraft:writable_book", "Dear Diary",
    "Write a page of a Hearth's Chronicle.", hidden=True, group="Hidden")
add("hidden_death", "root", "minecraft:totem_of_undying", "Only the Bars",
    "Die with a thousand levels in total and lose only the bars: every level stays.", hidden=True, group="Hidden")


# Layout. Vanilla stacks the children of one node in a column, so the tab used to be 19 children of the
# root: one very tall column. Rows in the tree are its leaves, so the tab is shortened by making lanes:
# a category's four levels run left to right, and a related category continues the same lane after the
# top tier. Only the display parent changes; ids, criteria and the awarding code are untouched, and a
# child is shown as soon as it is earned (with its parents), so the order of grants does not matter.
#   root -> six branch heads, each a real earnable advancement (the first level 10 of a theme).
LANES = [
    # (new parent, [ids chained after it, in order])
    ("level_combat_100", ["level_survival_10", "level_survival_25", "level_survival_50", "level_survival_100"]),
    ("level_gathering_100", ["level_crafting_10", "level_crafting_25", "level_crafting_50", "level_crafting_100"]),
    ("level_movement_100", ["level_expedition_10", "level_expedition_25", "level_expedition_50",
                           "level_expedition_100", "hidden_places"]),
    ("first_star", ["five_stars", "jack_of_all_trades", "master_of_all", "hidden_death"]),
    ("hearth_stage_1", ["level_construction_10", "level_construction_25", "level_construction_50",
                        "level_construction_100", "level_social_10", "level_social_25", "level_social_50",
                        "level_social_100"]),
    ("hearth_stage_1", ["hidden_feast", "hidden_tales", "hidden_chronicle"]),
]
# Second lane under the Mastery head: talents and Expertise.
LANES.append(("first_star", ["first_synergy", "first_signature_proc", "first_expertise", "expertise_full"]))
BRANCH_HEADS = ["level_combat_10", "level_gathering_10", "level_movement_10", "level_mastery_10", "first_star",
                "hearth_stage_1"]


def apply_layout():
    by_id = {a["id"]: a for a in ADV}
    for head in BRANCH_HEADS:
        by_id[head]["parent"] = "root"
    for parent, chain in LANES:
        for id in chain:
            by_id[id]["parent"] = parent
            parent = id
    # Hearth stages and sieges keep their parents (two lanes off stage I). Every node must reach the root.
    for a in ADV:
        seen, cur = set(), a["id"]
        while by_id[cur]["parent"] is not None:
            assert cur not in seen, "cycle at " + cur
            seen.add(cur)
            cur = by_id[cur]["parent"]
        assert cur == "root", a["id"]


def lang_key(id, part):
    return "advancement.proficiency.%s.%s" % (id, part)


def doc(adv, forge):
    icon = {"item": adv["icon"]} if forge else {"id": adv["icon"]}
    display = OrderedDict()
    display["icon"] = icon
    display["title"] = {"translate": lang_key(adv["id"], "title")}
    display["description"] = {"translate": lang_key(adv["id"], "desc")}
    if adv["parent"] is None:
        display["background"] = BG
    display["frame"] = adv["frame"]
    display["show_toast"] = adv["parent"] is not None
    display["announce_to_chat"] = adv["chat"]
    display["hidden"] = adv["hidden"]
    out = OrderedDict()
    if adv["parent"]:
        out["parent"] = "proficiency:" + adv["parent"]
    out["display"] = display
    out["criteria"] = {"event": {"trigger": "minecraft:impossible"}}
    out["requirements"] = [["event"]]
    return out


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def is_generated(path):
    try:
        with open(path, encoding="utf-8") as f:
            crit = json.load(f).get("criteria")
        return crit == {"event": {"trigger": "minecraft:impossible"}}
    except (ValueError, OSError):
        return False


def main():
    apply_layout()
    ids = set(a["id"] for a in ADV)
    for d in ("common/src/main/resources/data/proficiency/advancement",
              "forge-1.20.1/src/main/resources/data/proficiency/advancements"):
        full = os.path.join(ROOT, d)
        os.makedirs(full, exist_ok=True)
        for f in os.listdir(full):
            # Only files this generator wrote (its criterion signature) and no longer owns are removed;
            # a hand-written advancement beside them is left alone.
            if f.endswith(".json") and f[:-5] not in ids and is_generated(os.path.join(full, f)):
                os.remove(os.path.join(full, f))
    for adv in ADV:
        write(os.path.join(ROOT, "common/src/main/resources/data/proficiency/advancement", adv["id"] + ".json"), doc(adv, False))
        write(os.path.join(ROOT, "forge-1.20.1/src/main/resources/data/proficiency/advancements", adv["id"] + ".json"), doc(adv, True))
    lang_path = os.path.join(ROOT, "common/src/main/resources/assets/proficiency/lang/en_us.json")
    with open(lang_path, encoding="utf-8") as f:
        lang = json.load(f, object_pairs_hook=OrderedDict)
    # Only the keys of ids this generator owns are replaced; other keys, even under the same prefix, stay.
    owned = set(lang_key(i, part) for i in ids for part in ("title", "desc"))
    for k in [k for k in lang if k in owned]:
        del lang[k]
    for adv in ADV:
        lang[lang_key(adv["id"], "title")] = adv["title"]
        lang[lang_key(adv["id"], "desc")] = adv["desc"]
    write(lang_path, lang)
    lines = ["# Advancements", "", "Generated by `tools/gen_advancements.py`. Every id is `proficiency:<id>` with one",
             "criterion named `event`. FTB Quests: use an Advancement task with the id.", "",
             "The tab is laid out as six branches off the root (Combat, Gathering, Movement, Mastery skills,",
             "Mastery and Expertise, Hearth), so it is wide rather than tall. The `after` column is the display",
             "parent only: nothing requires it, and every advancement is earned on its own.", "",
             "| id | title | needs | after | hidden |", "|---|---|---|---|---|"]
    for adv in ADV:
        lines.append("| `%s` | %s | %s | %s | %s |" % (adv["id"], adv["title"], adv["desc"],
                                                  "`%s`" % adv["parent"] if adv["parent"] else "", "yes" if adv["hidden"] else ""))
    with open(os.path.join(ROOT, "docs/ADVANCEMENTS.md"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(len(ADV), "advancements")


if __name__ == "__main__":
    main()
