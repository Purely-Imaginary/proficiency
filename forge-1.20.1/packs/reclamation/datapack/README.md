# Reclamation XP datapack

Retunes the Proficiency XP sources for the Reclamation pack. It does not touch
`config/proficiency-materials.json`, which holds the talent materials and is a different thing.

What it changes, all found by running `/proficiency xpaudit <mod>` in the real pack:

- Croptopia's 26 fruit trees (`croptopia:apple_crop`, ...) are leaf blocks and paid Woodcutting. They pay Farming now.
- Allies, summons and familiars of Ars Nouveau, Ars Elemental and Enchanted pay no Beastslaying XP. Before, killing a summoned wolf paid like a wild mob.
- Guardian of Gaia and the Wilden Chimera count as bosses (kill bonus doubled, boss first-time bonus).

Left alone on purpose: `croptopia:salt_ore` pays Excavation because it is dug with a shovel.

## Installing

The folder holding `pack.mcmeta` is the datapack. On a server, copy this `datapack` folder into
`world/datapacks/` under the name `reclamation_xp` and run `/reload`, or restart. `/datapack list`
should show `file/reclamation_xp`. The server log then says `XP sources: Loaded N XP sources from
2 files`. On a single-player world, copy it into `saves/<world>/datapacks/`.

Check one id with `/proficiency xpsources croptopia:apple_crop`.
