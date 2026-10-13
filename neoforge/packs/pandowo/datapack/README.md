# Pandowo XP datapack

Retunes the Proficiency XP sources for the Pandowo pack (NeoForge 1.21.1, the Better MC modpack).
Generated from the pack's own jars by `tools/packs/gen_pandowo.py` (every id is checked against what
the jars register); edit the tables there, not the JSON. `config/proficiency-*.toml` is untouched.

What it changes:

- **Beastslaying pays for wild mobs only.** Summons, familiars and helpers (Ars Nouveau), golems and a
  player's illusion (Friends & Foes, It Takes a Pillage), the Aether cloud and fire minions, Dragon
  Mounts dragons, livestock and tame animals (Aether, Twilight Forest, Deep Aether, Moobloom, crabs),
  goblin and other traders, and the Target Dummy pay nothing. Before, a summon spell or a breeding pen
  was a free supply of kills, and `dummmmmmy:target_dummy` was a safe farm.
- **Bosses.** The shipped rule already follows `#c:bosses`, `#neoforge:bosses` and `#forge:bosses`. This
  pack also names The Harbinger, Maledictus, Scylla, the Sculptor, the Twilight plateau boss and the Eye
  of the Storm controller, and says the Eye's segments are not bosses (they carry the tag, and would
  pay the boss bonus once each).
- **Furniture and machines in `#minecraft:mineable/axe`** (Handcrafted, Another Furniture, Create parts,
  Farmer's Delight cabinets and crates, Functional Storage, Tom's Storage, ...) pay no Woodcutting when
  broken (other skills are unaffected: a pickaxe still pays Mining). A village or a ruin carries a lot of furniture.

Kills of the listed summons, livestock and golems also pay no kill bonus and no first-time bonus.

## Comfort tags (the Hearth, 1.5.0)

The Hearth counts twelve kinds of furniture, each a block tag `proficiency:comfort/<kind>`
(docs/HEARTH-SPEC.md, "Comfort"). `data/proficiency/tags/block/comfort/` adds this pack's furniture to them, for
Handcrafted, Another Furniture and Farmer's Delight (Macaw's Furniture is not in this pack):

| Kind | Added |
|---|---|
| `seat` | Handcrafted chairs, benches, couches and dining benches; Another Furniture chairs, benches, sofas, stools and tall stools |
| `storage` | Handcrafted cupboards, drawers, nightstands, counters, desks, tables, side tables and shelves; Another Furniture drawers, shelves and tables; the Farmer's Delight cabinets (tables are storage here: the spec leaves "storage or seat" to the pack) |
| `bed` | Handcrafted fancy beds |
| `light` | Another Furniture lamps |
| `plant` | Handcrafted pots, Another Furniture flower boxes |
| `books` | Handcrafted stackable books |
| `trophy` | Handcrafted mob trophies |
| `carpet` | the Farmer's Delight canvas rug |
| `fireplace` (counts as `fire`) | the Farmer's Delight stove, the Handcrafted oven |

The table covers nine of the twelve kinds. Banner, art and music have only their vanilla members here, which is
enough: all twelve are reachable with vanilla blocks. This datapack is not inside the jar (see Installing).

Most entries are the mods' own block tags (`#handcrafted:chairs`), so a new wood type in a mod update is
covered without a change here. Every entry is `required: false`. Left out on purpose: trims, crockery,
curtains, shutters, crates, cushions and Another Furniture's lamp connectors, which are no furniture.
`comfort-tag-ids.txt` lists what the jars carry (the unit test holds the tags to it); regenerate both with
`python3 -I tools/packs/gen_comfort_tags.py --pandowo JARS_DIR`.

Regenerate with `python3 -I tools/packs/gen_pandowo.py JARS_DIR...`.

Left alone on purpose: modded mobs not listed here (hostile ones) pay as before; real logs, planks and
crops pay as before.

## Installing

Pandowo already runs **Paxi**, which loads every folder or zip in `config/paxi/datapacks/` as a
force-enabled datapack for every world (that is where the pack's other fixes live), so the pack
delivers this one with no per-world step. From the folder holding `pack.mcmeta`:

    python3 -m zipfile -c proficiency_xp_pandowo.zip pack.mcmeta data

Copy `proficiency_xp_pandowo.zip` into `config/paxi/datapacks/` of the server (and into the pack the
players update from, if the pack ships configs). A folder named `proficiency_xp_pandowo` with
`pack.mcmeta` and `data/` inside works too. Restart, or `/reload`. The server log then says `XP
sources: Loaded N XP sources from 2 files`. Check one id with `/proficiency xpsources
ars_nouveau:summon_wolf`.

Without Paxi, copy the same zip into `world/datapacks/` and run `/reload`. KubeJS's `kubejs/data/`
folder also loads as a datapack: put `data/pandowo/...` there. Only the server reads these files.
