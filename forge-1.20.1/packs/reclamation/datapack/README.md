# Reclamation XP datapack

Retunes the Proficiency XP sources for the Reclamation pack. It does not touch
`config/proficiency-materials.json`, which holds the talent materials and is a different thing.

What it changes, all found by running `/proficiency xpaudit <mod>` in the real pack:

- Croptopia's 26 fruit trees (`croptopia:apple_crop`, ...) are leaf blocks and paid Woodcutting. They pay Farming now.
- AgriCraft crop sticks pay Farming XP like wheat: 1.0 for a mature plant harvested by right-click or broken, 0.5 for planting a seed. The rules for `agricraft:crop` are the numbers to tune (Proficiency 1.4.1 or later does the harvest detection; see the CHANGELOG).
- Allies, summons and familiars of Ars Nouveau, Ars Elemental and Enchanted pay no Beastslaying XP. Before, killing a summoned wolf paid like a wild mob.
- Guardian of Gaia and the Wilden Chimera count as bosses (kill bonus doubled, boss first-time bonus).
- Furniture and machines that sit in `#minecraft:mineable/axe` (Create parts, Storage Drawers, Farmer's
  Delight cabinets and crates, Cooking for Blockheads, a few Botania, Embers and Nature's Aura machines,
  Ars Nouveau stations) pay no Woodcutting when broken. Ids from each jar's own axe tag; regenerate with `python3 -I tools/packs/gen_reclamation.py JARS_DIR`.

Left alone on purpose: `croptopia:salt_ore` pays Excavation because it is dug with a shovel.

## Comfort tags (the Hearth, 1.5.0)

The Hearth counts twelve kinds of furniture, each a block tag `proficiency:comfort/<kind>`
(docs/HEARTH-SPEC.md, "Comfort"). `data/proficiency/tags/blocks/comfort/` (1.20.1 names the folder `blocks`)
adds this pack's furniture to them, for Macaw's Furniture and Farmer's Delight (Handcrafted and Another
Furniture are not in this pack):

| Kind | Added |
|---|---|
| `seat` | Macaw's chairs, modern, stool and striped chairs, chaises and couches |
| `storage` | Macaw's cabinets, counters, drawers, wardrobes, tables and desks of every kind; the Farmer's Delight cabinets (tables are storage here: the spec leaves "storage or seat" to the pack) |
| `books` | Macaw's bookshelves |
| `carpet` | the Farmer's Delight canvas rug |
| `fireplace` (counts as `fire`) | the Farmer's Delight stove |

Every entry is a block tag the mods ship (`#mcwfurnitures:chair`) or an explicit id, `required: false`. Macaw's
kitchen sinks are left out. `comfort-tag-ids.txt` lists what the jars carry (the unit test holds the tags
to it); regenerate both with `python3 -I tools/packs/gen_comfort_tags.py --reclamation JARS_DIR`. These
files cover five of the twelve kinds (seat, storage, books, carpet, fire). Banner, art, music, bed, light,
plant and trophy have only their vanilla members here (no furniture mod in this pack fits them), which is
enough: all twelve are reachable with vanilla blocks. The Forge 1.20.1 build of the mod has had the Hearth since
1.5.0, so the tags are read. They are not inside the jar; install the datapack as below.

## Installing

The folder holding `pack.mcmeta` is the datapack. On a server, copy this `datapack` folder into
`world/datapacks/` under the name `reclamation_xp` and run `/reload`, or restart. `/datapack list`
should show `file/reclamation_xp`. The server log then says `XP sources: Loaded N XP sources from
2 files`. On a single-player world, copy it into `saves/<world>/datapacks/`.

Check one id with `/proficiency xpsources croptopia:apple_crop`.
