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
