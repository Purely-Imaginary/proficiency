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

## Installing

The folder holding `pack.mcmeta` is the datapack. On a server, copy this `datapack` folder into
`world/datapacks/` under the name `reclamation_xp` and run `/reload`, or restart. `/datapack list`
should show `file/reclamation_xp`. The server log then says `XP sources: Loaded N XP sources from
2 files`. On a single-player world, copy it into `saves/<world>/datapacks/`.

Check one id with `/proficiency xpsources croptopia:apple_crop`.
