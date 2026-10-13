# Changelog

## 1.9.0

Network protocol 9 (unchanged).

- **Visualize a Hearth stage, as a Patchouli book does.** With Patchouli installed (Pandowo, Reclamation), every stage in the Hearth guide's Build tab has Patchouli's eye button under its preview. Click it and the stage's ghost follows your crosshair until you right-click a block (the fire goes on top of it), then Patchouli shows the progress bar, the block you look at, the red air cells and "Complete!". Click again to clear. Patchouli is called through its API by reflection: nothing is copied and it stays optional.
- **The panel's "Show build guide" uses the same visualizer** when Patchouli is installed, anchored on that Hearth with the chosen lane's next stage. Without Patchouli (Lesznica) it draws its own ghosts as before.

## 1.8.1

Network protocol 9 (unchanged). Balance fixes from the 2026-10-12 report (Reclamation, one week).

- **Smelting pays Smithing by the recipe's worth.** Each item taken from a furnace used to pay 1 XP flat, so charcoal from logs paid the same as an iron ingot (74% of a week's smelting XP was charcoal). Smithing XP per item is now the recipe's experience / 0.7: iron and copper stay at 1, gold 1.43, charcoal 0.21, stone and glass 0.14 (floor 0.1, ceiling 1.5). A smelt with no recipe found pays 1, as before. Cooking is unchanged.
- **Area tools pay 10% per extra block, not 25%** (`aoeXpShare`). A 3x3 hammer swing paid 3x a pickaxe swing, and the extras out-earned the blocks you aimed at. Now it pays 1.8x. An existing server config keeps its old value until it is edited.

## 1.8.0

Network protocol 9 (unchanged): 1.7.0 clients can still join.

- **Hearth Reach: the range grows with Homebuilder.** Every Homebuilder XP gain made in range of a Hearth charges that Hearth's Reach, which never drains. The range radius climbs from `range` (24) to `rangeMax` (64 by default) as Reach fills to `reachFullXp` (5000). Home-ness, Warmth, boons, charge, Tales and station speed-ups use the grown range. The house itself (Comfort scan, siege ring, bell, lecterns) stays at the base 24. Grown ranges may overlap; you belong to the nearest Hearth. Spec: `docs/HEARTH-REACH-SPEC.md`.
- **Hearth ranges on Xaero's World Map** (Forge 1.20.1): the ranges of the Hearths you have visited show as an ember-orange area with an edge. F8 shows or hides them (rebindable). It is remembered per world.
- Reclamation: `rangeMax = 192`, so a fully charged Hearth at the starting base covers the whole starting island.

## 1.7.0

Network protocol 9: 1.6.0 clients cannot join a 1.7.0 server.

- **Critical hits count.** A critical melee hit (sword, axe, mace, trident or fist; vanilla's jump crit) pays 1.5x hit XP, shown as the "critical hit" factor in the XP feed and log, and doubles the signature move's chance for that hit. Bows are unchanged: Deadeye and Hanging Shot already read the full draw.
- **Three crit talents.** Lunge (Swords): a sprinting hit is a crit, ready every 9/6/3 seconds. Rend (Axes): an axe crit opens a wound, so the target takes 15% more weapon damage for 4 seconds. Flow (Unarmed): a crit punch gives Speed and Haste for 1 second per rank. Each tree is now 99 points, so it still fills at level 100.
- **Leaping Blade** (Swords + Jumping, the 158th synergy): sword crits deal x1.75 instead of x1.5.
- **The HUD stops showing Unarmed for every empty hand.** With nothing in your hand, the skill line shows Jumping just after a jump and Running otherwise. Unarmed appears only when it actually earns XP. Swimming, Sneaking and Endurance keep their moments, and the ability key still means Unarmed.
- Spec: `docs/CRITS-SPEC.md`. Every new string is in all 12 languages.

## 1.6.0

Network protocol 8: 1.5.x clients cannot join a 1.6.0 server.

- **Expertise, a tree across all skills.** One point per 100 skill levels added up over every skill (35 at most) and one per 5 Mastery stars (35 at most), 70 in all. Thirteen nodes of global power (XP, passives by category, proc chance and power, ability duration and cooldown) that one player can fill completely. Open it with the Expertise button on the skills panel or `/proficiency expertise`. Respec costs one XP level per 10 points, and is free at your own lit Hearth. Spec: `docs/EXPERTISE-SPEC.md`.
- **131 new synergies, 157 in all.** Every skill now takes part in 9 or 10. 102 are numeric boosts; 29 change how a pair plays (Falling Hammer, Pavise, Dragonbolt, Lifeguard, Charcoal Burner, Moon Garden, High Diver, Housewarming and more). Spec: `docs/SYNERGIES-2-SPEC.md`.
- The tree screen's synergy list shows awake ones first, folds the rest into "+N more" (hover lists them, the mouse wheel scrolls), and `/skills synergies [skill]` no longer floods the chat.
- Every new string is in all 12 languages.

## 1.5.6

- **Signature moves call out above the crosshair.** When your proc lands, its name ("Motherlode!", "Timber!") pops in just above the crosshair in the skill's colour, holds for about half a second, then fades and drifts up. It replaces the action-bar line, which sat far from where you were looking. Only your own procs show; a frenzy keeps the line up instead of popping it on every swing.
- **The signature move and the ability now need the tree's root, filled.** On top of their levels (25 and 50 by default), each skill's signature and ability stay off until the first node of its talent tree (Pickwork, Lumberjack, Swordplay...) is bought in full. Levelling alone no longer switches anything on, so a player who never opens the trees is never surprised by procs. Reaching level 25 without the root says which node to fill; filling it says what just unlocked. The tooltips, the tree header, the skills panel and the ability wheel say "fill <root>" when that is the only thing missing. Server config `signatureNeedsRoot` (default true) turns the rule off.
- Existing players with high levels and an unfilled root lose their procs and abilities until they fill it.

## 1.5.5

- **Five more Hearth designs, eight in all.** Demonic (a nether citadel with lava pits behind iron bars and a crimson crown), Gothic (a dark chapel with stained glass, candles and spires), Girly (a pastel garden pavilion with flower pots, carpets and a glass cupola), Overgrown Ruin (weathered cracked stonework with copper, ferns and a half-collapsed arch crown) and Frost Lodge (a spruce lodge on a packed or blue ice plinth with snow roofs). The Shrine is wooden again and lava-free; lava now shows in the Forge (ground-level pits) and the Demonic citadel.
- **Decorations as design blocks**: flower pots, chests or barrels, candles, carpets, glass, skulls, plus packed or blue ice, snow, moss, magma, obsidian and leaves. The older designs gained banners, potted flowers, barrels and candles where they suit.
- **Suggested palettes**: each design has a palette (pink cherry for Girly, deepslate and dark oak for Gothic, and so on) that the guide previews and the build-guide ghosts show. Any block of the right kind still counts.
- The guide's design picker has two rows of four; the panel's design button cycles all eight.

## 1.5.4

- **Water and lava in the Hearth designs.** Every design needs water from stage III: eight curbed pools in the Longhouse and the Shrine, four quench pools under iron grates in the Forge. The Forge also needs four lava crucibles from stage IV, each under an iron grate, with no wood near them (lava sets wood alight). Only source blocks count; flowing or waterlogged blocks do not. The guide lists them as buckets, and the build guide and the previews show them as tinted water and lava. Stage III is now up to 9 wide, since each pool has a stone curb.

## 1.5.3

- **Hearth stage designs.** The six stages are now three lanes of designs (Longhouse, Forge, Shrine), each stage a 9x9 grid up to 7 tall (`docs/HEARTH-DESIGNS-SPEC.md`). Wood and stone count by block tag (any stone-type block, any log or plank), metals are named. The built stage is the highest stage any lane matches in full. A refused offering names the lane closest to complete, its first wrong block as a role and a position from the fire, and how many blocks are still wrong. The Comfort and Character scan skips the blocks of the design the Hearth stands as. The old stage block tags are gone.
- **The Hearth block grows with its stage**: a stone ring at I, a brick collar at II, a raised plinth with a log rack at III, iron firedogs and gold caps at IV, a copper brazier at V and a gold-crowned brazier with a flame about two blocks tall at VI. The model stays clear of the build's cells around the fire. Embers rise higher each stage; VI has a golden helix of motes.
- **Build guide in the Hearth panel**: "Show build guide" and a design button. While on, the next stage of the chosen design shows as translucent ghost blocks around the Hearth; blocks in the way of air cells show as red boxes. It turns off when you leave the Hearth's range.
- **Hearth guide, Build tab**: a design picker and a slowly turning 3D preview of every stage, with what the stage adds, the materials it needs, the offering and the Character gate.
- Existing Hearths are checked against the new designs: a Hearth built to the old layout drops back until rebuilt. Its charge is held and offerings already paid are not owed again.

## 1.5.2

- **Five Siege Horns, one per siege level.** I Siege Horn (stage III), II War Horn (stage IV, adds pillagers), III Battle Horn (stage V, adds vindicators and witches), IV Doom Horn (stage VI, an evoker leads each wave), V Horn of the Warhost (stage VI, a ravager and an evoker lead each wave, 15 mobs a wave). Each horn crafts from the one before: II = Siege Horn + crossbow + 2 emeralds; III = War Horn + gold block + 2 diamonds; IV = Battle Horn + diamond block + 2 blaze rods; V = Doom Horn + netherite ingot + totem of undying. The recipe unlocks when you hold the previous horn.
- A horn's siege has the horn's level, whatever the stage would roll. A rolled siege keeps the stage's level (III -> I .. VI -> IV). The trophy follows the siege level, and the victory burst scales with the siege level against the stage's own (a level I horn at stage VI pays a quarter; the Warhost at VI pays a quarter more).
- Each horn has its own call, deeper with each level. The tooltip names the level, the stage it needs and the foes it adds. `/proficiency hearth siege call [level]` takes a level.
- Data: `siege.baseMobs` and `siege.mobs` accept key 7 (level V); `onePerWave` fields each listed leader once.

## 1.5.1

- **Hearth guide.** The Homebuilder talent tree has a "Hearth guide" button at the top right. It opens a guide with three tabs. Build lists the six stages with their blocks, the offering as an item and the Character each stage needs; hover a stage for the exact blocks. Boons lists Warmth and the five pools with what feeds each and the boon at tier I and VI. Comfort lists the twelve furniture kinds and what Character counts. At home, the guide marks the sealed stages, the next stage, each pool's tier and the Comfort kinds the home has. Client side only: protocol 7 is unchanged, and a 1.5.0 server works with it. English and Polish.

## 1.5.0

The Hearth release. Two new things that belong together: **the Hearth**, a campfire a group turns into the heart of its home, stage by stage, and **Homebuilder**, a new skill for keeping that home. **The network protocol is now 7**: the server, the pack and every client must update together, and a 1.4.x client is dropped when it joins a 1.5.0 server. All three lines (NeoForge 1.21.1, Fabric 1.21.1 and Forge 1.20.1) carry all of it. Design and numbers: `docs/HEARTH-SPEC.md` and `docs/HOMEBUILDER-SPEC.md`. What the code exposes: `docs/HEARTH-CONTRACT.md` (and `docs/HEARTH-FORGE-CONTRACT.md` for the 1.20.1 line).

### The Hearth

Players keep every point of XP they earn. While they earn it near a Hearth, the Hearth also gathers a mirror of it, 10%, as **charge** in five pools. Charged pools give **boons** to whoever has warmed up by the fire. A furnished home under a roof earns the strongest boons, and a finished one draws an invasion.

Build stages.

- **A campfire on a 3x3 platform** of polished stone or stone, deepslate, tuff, blackstone or mud bricks, lit with flint and steel, becomes a Hearth: stage I, the Firepit. Nothing is crafted, so the home visibly grows.
- **Five more stages**, each a layer you build around the fire plus an offering you right-click onto the Hearth, taken only when the layer is complete. II Hearth: the floor widened to 5x5 with an outer ring of bricks or terracotta, and a copper block. III Homestead: four log pillars, 3 tall, on the corners, and an iron block. IV Hall: a ring of slabs or stairs on the pillars with the middle 3x3 left open as a smoke hole, a lantern under each side, and a gold block. V Gathering Place: a bell and a banner on each pillar, and a diamond block. VI Heart of the Home: a lodestone under the fire, and a heart of the sea.
- A missing piece is named in chat ("north-east pillar: 2 of 3 logs"). A layer that falls lowers the ceiling and keeps the charge, and rebuilding needs no second offering.
- Hearths keep 64 blocks apart, per dimension. A Hearth is put out with a 3 second sneak-hold through the normal break (so claim mods protect it). It drops a plain campfire, and its charge and offerings are lost; everyone who fed it is told who did it.
- **Range** is a cylinder: radius 24, from 8 blocks below the fire to 16 above, so the branch mine under the base is not home. It means the same everywhere: who is home, what charges the Hearth, what counts for Comfort, where sieges spawn.

Charge and boons.

| Pool | Fed by | Boon, tier I to VI |
|---|---|---|
| Hearty | Mining, Woodcutting, Excavation, Farming, Fishing | hunger 8 / 15 / 22 / 28 / 34 / 40% slower |
| Comfort Regen | Decorating | out of combat, a heart every 14 / 12 / 10 / 8 / 6 / 4 seconds; at full health, temp HP up to 10% of max health a tier |
| Fortified | Masonry | damage from mobs 4 / 7 / 10 / 13 / 16 / 20% lower, never from players or their pets |
| Workshop | Smithing, Cooking, Alchemy | furnaces, smokers, blast furnaces and brewing stands in range 8 to 40% faster while someone is home; 2 to 8% material refund at crafting tables, never on a reversible recipe |
| Homecoming | Social, Tales, Feasts | Warmth fills in 55 down to 30 seconds instead of 60; Provisioned lasts 16 to 30 minutes instead of 15 |

- A grant earned in range adds 10% of its XP to one pool, through a cap of 300 per pool an hour. Only paid play feeds: no first-time lines, rested extra, commands or teaching. Thresholds for tiers I to VI: 200, 600, 1 400, 2 800, 5 000 and 8 000.
- Masonry and Decorating shares count only if the block still stands 10 minutes later, so shuffling one block charges nothing.
- Combat feeds only through kills of siege mobs and of raiders in a running raid (3% of the kill XP to every pool). Farms, hits, blocking and falls feed nothing, and neither do fake players. Homebuilder, Movement, Expedition, Survival and Mastery skills feed nothing at home.
- Charge decays 2% an hour of game time while no active survival player is home, never below the tier floor during the first day after a visit. A Hearth in an unloaded chunk catches up when it loads.
- **Character and Comfort.** The ceiling is the lowest of the stage built and the stage **Character** allows: the number of distinct block ids placed in range (filler such as dirt, cobblestone and sand does not count): 8 for stage III, 14 for IV, 24 for V and 40 for VI. **Comfort** is the number of distinct furniture kinds placed indoors in range (12: bed, seat, carpet, banner, fire, light, plant, art, books, music, storage, trophy). A pool's working tier is the least of its charge tier, the ceiling and its Comfort cap, and charge above it is held, never cut. The Comfort cap is Comfort 8 for tier V and 10 for tier VI on Hearty, Fortified and Workshop; Comfort Regen and Homecoming rise one tier for every two kinds (all 12 for tier VI). Quantity never counts: ten candles are one kind. Only blocks a player placed count, and chest contents never do.
- **The ledger.** Every point of charge is recorded by player, pool and source, for the last 7 days of play and for the Hearth's lifetime. A tab on the Hearth panel ranks the keepers, a new tier tells the players in range who carried it that week, and `/proficiency hearth ledger` prints the same table.

Warmth, temp HP and the survival bonus.

- Everyone has a **Warmth** of 0 to 100%. It fills in 60 seconds in range (not within 5 seconds of damage), drains in 30 seconds away, and a night's sleep in a bed in range fills it at once. Hearty, Comfort Regen and Fortified work at tier times Warmth.
- **Temp HP** refills only at full Warmth, lasts until damage spends it (a relog, travel and time away keep it), never stacks with other absorption (the larger wins), and is gone on a death.
- **The survival bonus does not grow in range of any Hearth**; the stacks you have keep paying. **A death costs what it always costs.** No boon softens the bar wipe, the bonus loss or the rested wipe.
- Night Owl, Lean Times and Hearty now share one hunger watcher, so they multiply (see "Fixed on the way").

Setting out, coming home and gathering.

- Leaving range at full Warmth **sets out**: Provisioned carries the Hearty value with you for 15 minutes (up to 30 with Homecoming), fading.
- Movement and Expedition XP earned away banks 5% as **Tales**. Coming home to a Hearth you fed pays them into Homecoming ("You told of 3 200 blocks walked and a stronghold"), and a death away loses them.
- **The Chronicle.** A page written in a writable book on a lectern in range adds 25% to the Tales paid, once per homecoming. A server script can rate the page afterwards with `/proficiency hearth tale <page id> <0-5>`, which pays stars times 5% of its Tales.
- **A Feast.** Two or more players eating within 30 seconds at a Hearth fill each other's Warmth and add Homecoming charge (20 a guest), once per in-game day per Hearth.
- **The bell.** The stage V bell rings one of three calls, picked by the moment: an alarm during a siege (siege mobs glow), a dinner bell within a minute of someone eating (a two-minute Feast window and a chat call to everyone online), or a come-home call (the Hearth shows on screen for a minute and Homeward channels in 5 seconds).

Sieges.

- **Off until someone blows a Siege Horn** (goat horn, 2 gold ingots and 1 iron ingot, shapeless) at that Hearth: the horn calls a siege for the next nightfall, at least 2 real minutes away. After that first one, a Hearth at stage III or higher with a pool at tier I or higher rolls once a minute, but only while every active survival player online is home at full Warmth, after 4 hours of play and 2 in-game days since the last siege. The aim is one per 10 hours of play, and one is certain after 16.
- A siege is announced a day ahead in chat and on a countdown panel at the top left, and starts at dusk. Nobody home at dusk postpones it twice, then it fizzles.
- Three waves come in from the edge of range for the fire: bigger with the ceiling and the players home, and mixed from zombies, skeletons and spiders up to pillagers, vindicators, witches and evokers by stage. Siege mobs break no blocks, and nobody can sleep in range while one runs.
- Every wave killed by players or their pets before dawn is a **defended** siege: a charge burst to every pool outside the cap, a **Siege Trophy** for the wall (it keeps the stage it was won at and the day) and a defence streak. A mob taken by lava, a golem or a portal makes the siege unearned. Mobs that stay at the fire too long douse it: the Hearth smoulders (boons and Warmth off, every pool down 10%) until it is relit with flint and steel.

What you see.

- A Warmth glyph beside the survival badge, a Provisioned badge and a Tales scroll away from home, and the cues "By the Hearth", "Left the Hearth's warmth" and "Set out from the Hearth". The skill line eases its room for the glyph.
- Right-clicking a Hearth with an empty hand opens its panel: the stage track, each pool's tier, bar and boon, Comfort and Character with what is missing, the sieges, and the Hearth-keepers tab. Stage sealing plays the talent-unlock effect.
- An opt-in **tracker** on the right edge (`hearth.tracker`) with a row per pool. It flies XP-gain dots into its rows and plays its own tier-up, and it gives way to the Tales panel and the death recap.
- The block glows with charge (light 6 with no tier, up to 15 at any tier VI), its motes take the colour of the pool fed most this hour, and a range ring shows where the range meets the terrain when you look at a Hearth or plan one with a campfire or flint and steel.
- 15 HUD glyphs and 33 icons (pools, Comfort kinds, stages, Warmth, siege, Tales, ledger, bell, horn, trophy) in the style of the skill icons, and sounds on the same kinds of moments the rest of the mod already sounds. The siege outcome and the Tales pay-in use the death recap's panel idiom. Your first Hearth, a sealed stage and a Homebuilder milestone use the discovery banner. A first Hearth and a milestone follow the first-time switch (`banners.firstTime`); a sealed stage follows only the master switch (`banners.enabled`). Every new HUD piece was checked at GUI scales 1 to 4 (`docs/LAYOUT-AUDIT.md`).

### Homebuilder

The 35th skill, in the Construction category next to Masonry and Decorating. It only means anything by a Hearth: "at home" is the range of a lit Hearth that is not smouldering (any Hearth).

- **It earns a share of what you do at home** (15% of every other skill's grant earned there, paid every 5 seconds as one "At home" line, like Nightwalker's) and the home's **milestones**, once each per Hearth and player: lighting or sealing a stage (150 times the stage), a new Comfort kind you placed (60), a new Character block id you placed (10), a siege defended (300, split by kills), a Feast (25), Tales paid in (10% of them) and a Chronicle page (20). Building already pays Masonry and Decorating, so it never pays for the same block twice, and it feeds no Hearth pool.
- **The passive, at home only:** movement speed up to +20%, block reach up to +2 (its own modifier, stacking with Mining's and Masonry's) and, with the talent Tidy Home, an item magnet of up to 6 blocks. The magnet leaves hoppers, water-carried items, a fresh drop of someone else and your own sneaking alone, and switches off from the ability wheel or `/skills magnet [on|off]`. The effects are gone within 2 seconds of leaving range.
- **Hearthglow** (the signature): a grant at home that fed the Hearth can charge the same pool a second time, at most once per 10 seconds, with an ember puff on the fire.
- **House Proud** (the ability, from the wheel at home): 30 seconds, 6 minute cooldown. Your passive doubles and everyone at the Hearth shares your undoubled one, with a warm-gold edge glow and ring.
- **A tree of 99 points** with the capstone **Homeward**: once per in-game day, a 10 second channel (5 seconds for a minute after a come-home bell) that a hit or a step breaks, taking you to the Hearth you fed most. Tales are paid when you arrive, and a death mid-channel loses them. `/skills homeward` or its wheel slot.
- A synergy, **Heart of the Home**, with Social: a Feast counts as a House Proud for everyone at it.
- An old save loads with Homebuilder at 0 and nothing else changed.

### Fixed on the way

- **Night Owl, Lean Times and Long Haul now multiply.** Night Owl and Lean Times each watched the same hunger and wrote it back, and the Hearth's slower hunger needed to join them. One watcher at the food tick now takes the product of the shares. It also found two bugs while it was written: Long Haul refunded its whole unscaled share after another share had already shaved the charge, so they added instead of multiplying, and the exhaustion natural regeneration adds on the tick a food point is taken stayed unscaled. Both are fixed on all three lines.
- **The skill line and the action bar.** On Forge, the skill line above the hotbar rises with Forge's raised action bar and no longer sits under it.

### Numbers, config and data

- Every Hearth number is datapack data: `data/<ns>/proficiency/hearth/*.json` (the mod's `default.json` first, later files override). The build layers, the comfort kinds and the Character filler are block tags (`#proficiency:hearth/*`, `#proficiency:comfort/<kind>`). Numbers the spec marked as first guesses are meant to be tuned from the balance report's new Hearth section.
- New server config: `[hearth]` (`enabled`, `range`, `rangeBelow`, `rangeAbove`, `minSpacing`, `sieges`) and `[homebuilder]` (`share`, the milestone XP, `houseProudTicks`, `houseProudCooldownTicks`). `hearth.enabled = false` leaves every Hearth standing and silent. New client settings: `hearth.tracker`, `hearth.trackerY`, `hearth.showRange`, `hearth.siegePanel`, `hearth.siegePanelY`.
- New commands: `/proficiency hearth ledger`, `comfort`, `siege status|call|start|end|clear` and `tale`, `/proficiency hearth demo` (op, a client test aid), `/skills magnet` and `/skills homeward`.
- **The Pandowo and Reclamation datapacks** now add their furniture mods to some of the twelve Comfort kinds. Pandowo (Handcrafted, Another Furniture, Farmer's Delight) covers nine: bed, seat, carpet, fire, light, plant, books, storage and trophy. Reclamation (Macaw's Furniture, Farmer's Delight) covers five: seat, storage, books, carpet and fire. Banner, art and music have only their vanilla members, and every kind can be filled with vanilla blocks, so Comfort 8 and 10 (tiers V and VI) stay reachable in both packs, with less modded furniture to help in Reclamation. The datapacks are not inside the jar. Install each one separately, as its README says (Pandowo's through Paxi's `config/paxi/datapacks`), or only vanilla furniture counts for Comfort.
- Balance telemetry has new kinds (`hearth_charge`, `hearth_boon`, `hearth_warm`, `streak_paused`, `hearth_absorb`, `hearth_tales`, `hearth_siege`, `tale_rating`, `homeward`) and `tools/balance_report.py` a Hearth section: charge by pool, source and player, boon uptime, sieges, Tales, Homebuilder's share of all XP and Homeward's uses.
- **Languages.** All Hearth and Homebuilder strings exist in English and natural Polish. The other ten shipped languages have no Hearth keys and show English for them. Sixteen Homebuilder keys that were added to those languages before this rule stay as they are.

### For servers and packs

- The protocol is 7. Update the server, the pack and every client together. Delete the old `proficiency-1.4.x` jar: two copies crash.
- Install the datapacks that fit the pack, separately from the jar: `neoforge/packs/pandowo/datapack` for Pandowo and `forge-1.20.1/packs/reclamation/datapack` for Reclamation (each README says how). They are not bundled in the jar.
- Saves: a new player attachment `proficiency:hearth` (Warmth, Tales, Provisioned, temp HP), the world data `proficiency_hearths`, `proficiency_hearth_world` and `proficiency_hearth_sieges`, and a `proficiency/chronicle.jsonl` in the world folder. An older save loads with none of it. A server config gains the two new sections on first boot, and the log may say it is correcting the file once.
- Fabric posts the mob-griefing check only for explosions and farmland, so a siege mob a pack adds that destroys blocks another way (an enderman, a ravager) follows the plain game rule there. The shipped siege mix has none, and siege zombies cannot break doors.

### Tested

- `tools/check-all.sh` on the release tree: core 695, common 209 and Forge 269 unit tests and 56 Python tool tests, no failures. In-world tests: NeoForge 280, Fabric 278 and Forge 295, all passed. Every abuse check of the spec and every Homebuilder test has a named GameTest on all three lines.
- A polish-parity pass in real clients on NeoForge, Fabric and Forge (the full Reclamation pack), by day and night, at GUI scales 2 and 4 and in Polish, put each Hearth screen and effect next to the existing one it mirrors. It found gaps (a ring of flames through the player's face at stage III, a siege panel in the hotbar row at a high setting, an alarm call that outlived its siege, a tracker that overlapped the Tales panel) and each was fixed and seen fixed.
- Not played: the numbers as fun. The first telemetry week decides the pool thresholds and the siege rate.

### Known limits and open numbers

- Wave 1 of a stage VI siege can douse the fire in about 8 seconds when one defender stands against it (`siege.baseMobs` and the douse rate in the datapack). Look at it in the first telemetry before changing it.
- A Feast was checked in a real client with a second player on Fabric, but not on Forge (the Reclamation pack has no dedicated server). The Forge GameTests cover the server side of the Feast and of House Proud.
- Where a right-edge panel and the tracker both want the same space at GUI scale 4 on a 1080p screen, the tracker fades with the panel.
- A pack that adds mobs a siege should not use, or furniture the datapacks do not map, needs a datapack edit. Nothing is guessed from names.
- The telemetry cannot tell a Hearth-hour from a player-hour, so the report's per-hour columns at home are per player-hour.

## 1.4.5

One HUD fix, found by playing 1.4.4. No packet changes: the protocol stays 6, so 1.4.4 and 1.4.5 play together.

- **The rested segment on the skill bar is eaten by new XP, not pushed along.** When XP arrived, the faint blue rested segment started at the animating fill, so the whole blue part slid to the right while the green fill grew. Now the far end of the segment is fixed to your real XP plus the stretch the pool covers, and only the near end follows the fill: the green grows into the blue and the far end stays where it was. While the fill is still finishing the level (or star) before, no segment is drawn, so a level-up shows no stray blue; the segment appears once the fill has wrapped to the new level.
- **The blue segment now shows how far the pool carries the bar.** A paid gain spends `extra` times its own size from the pool (rested XP's extra, 1.0 by default) and moves the bar by the gain plus that. The old segment was as long as the pool itself, so even a fixed far end crept right with every paid gain (measured on Forge: 15 pixels on one dug block). The segment is now the pool times (1 + extra) / extra, so a paid gain eats exactly its own length. At the default extra of 1.0 that is twice the pool; at other values of `rested.extra` it follows the setting, and with an extra of 0 (the pool never pays) no segment is drawn. The skills panel rows use the same length. One limit: the survival streak also moves the bar without spending the pool, so while a streak bonus is active the far end still drifts right a little.

Checked in real clients on a private display: Forge 1.20.1 and Fabric 1.21.1, with a throwaway server each. Digging blocks with a rested pool left the far end of the segment on the same pixel before, during and after the fill's animation, and a level-up rollover drew no blue until the fill had wrapped. On Forge, Master Mason placed 4 blocks with the stack still at 8, and a plain Masonry refund chance gave 22 refunds for 20 placements. Breaking every placed block and collecting the drops gave back exactly the 40 blocks the test started with, so refunds still arrive and picking the blocks up still repays them.

## 1.4.4

Three fixes found by playing 1.4.3 in real clients (Reclamation on Forge, Pandowo on NeoForge, Lesznica on Fabric). No packet changes: the protocol stays 6, so 1.4.3 and 1.4.4 play together.

- **Refunds now arrive on Forge.** On Forge 1.20.1, Master Mason, Master Decorator and the refund chances handed the block back from inside the placement event, and Forge resets the stack in your hand around that event, so the refunded block vanished: you placed one block and simply lost it. On Forge only, a refund given during a placement now waits for your next tick and then joins your inventory; NeoForge and Fabric never had the problem and still hand it over at once. The refund is remembered at the moment it is handed over, not before, so a block you place and break in the same tick, or a death or crash in that one tick, can never charge you for an item you did not get or leave you with a free one. Logging out first hands it over, so a disconnect never eats it. Picking the block up again repays it as before.
- **Ore from a vein miner or hammer no longer pays full Spelunking.** A 27-block iron vein on Pandowo paid Mining correctly (one full line and 26 quarter lines) but a full Spelunking line for every ore, with the tempo bonus climbing to x1.5. Extra ores of an area swing now pay a quarter of their Spelunking XP too, and roll no proc, the same as their Mining XP. Spelunking also ignores the ores that Timber-style cascades of your own break, like Mining does.
- **Pandowo datapack:** removed `ars_nouveau:familiar_jabberwog`. It has a language entry but is not a real entity in Ars Nouveau 5.10.5, so the server logged a warning on every start.

Checked in real clients on 1.4.3 and found correct: AgriCraft planting, harvest and breaking (Reclamation), the hammer, excavator, paxel and Meka-Tool numbers, the construction wand paying one placement, the Reclamation and Pandowo datapacks loading, summons and dummies paying nothing, a Mowzie's Sculptor kill counting as a boss, Ars spells paying Spellcasting while parchment and glyphs pay nothing, Yung's mineshaft and stronghold counting once per family, and Lesznica mining, woodcutting and Right Click Harvest.

## 1.4.3

Data and tuning changes, and four exploit closures. No packet changes. The only save change is one extra list in the placed-blocks save file, which an older version ignores, so servers and clients still play together.

### Exploit closures

- **Refunds can no longer be farmed by picking the block back up.** Master Mason and Master Decorator (free placement), the Masonry and Decorating refund chances, Lamplighter, Bulk Order, Engineering's Overclock and Blueprint all hand the block back when you place it. Picking that block up again used to give it to you a second time, so place and pick up made a free block (a free machine, for Engineering) every cycle. Now a refund is a loan against a block that stays. A refunded block remembers how many items it was refunded. If you break it, its own drop is removed first, and any refund beyond that one item is taken from your inventory. If you pick it up with something that fires no break event (a Create wrench, an AE2 wrench, most click-to-dismantle tools), your click is watched for one more tick, in the dimension it happened in, and the whole refund is taken from your inventory if the block is gone. If your pockets cannot cover it, you owe the rest and your next refunds pay it off, so emptying your inventory does not dodge it. A block destroyed some other way (a creeper, an explosion) repays nothing, which is what placing it would have cost without the talent. Clicking a refunded block that stays put (opening a chest, building onto it, stripping a log, tilling dirt, waxing copper) costs nothing: the refund simply rides on the new block. A refunded torch, door or flower that pops off when its support goes has its own drop withheld, since you already hold the refund. Placing a new block on a spot clears any old refund mark left there, and breaking one in creative clears it too. Only plain items repay a refund, so a filled shulker box is never taken from your inventory. Logging out right after a pick-up settles the watched click first, so disconnecting does not dodge it. Pistons carry the refund mark with the block. Blocks that fall (sand, gravel, concrete powder) are no longer refunded, because they may not stay where you put them. The mark is saved with the world, but what you owe is kept in memory only: a server restart forgives it, so the guard stops a repeatable loop, not a one-off. Only a right click is watched; a pick-up by left click or by an entity interaction fires no event we can see, and is not covered.
- **A wand or gadget pays one placement, not one per block.** Building Gadgets, Construction Wand, the Create symmetry wand and any similar tool post one placement event per block, as you, in one tick, so one use used to pay Masonry, Decorating or Engineering XP and refunds once per block (up to 1024 blocks per click). Now only the first placement of a tick pays, and no player is paid for more than 8 placements in any sliding second, counting only placements that actually paid (a torch or sign does not use up the budget) (a person bridging by hand does 5 to 8). The rule is generic and does not name any mod. Placing blocks is still marked, so breaking them back still pays nothing.
- **Target dummies pay no combat XP.** Hitting one gave weapon XP for as long as you liked. A new entity tag `proficiency:no_combat_xp` (shipped with Dummmmmmy's `dummmmmmy:target_dummy`, optional, extendable by datapack) stops the hit XP, the kill bonus and the kill credit. Any entity whose id contains "dummy" is treated the same, so a dummy from a mod the tag does not name yet is covered. The damage itself is unchanged, so you can still read the numbers.
- **Machines' fake players earn nothing.** Create deployers, the Mekanism digital miner, Ars Nouveau turrets and rituals and the like act through a fake player. XP, proc rolls, extra drops and placement pay now skip them, and no packet is ever sent to one. They are recognised by their class (anything that is or extends a `FakePlayer`) and by having no connection, so it works the same on NeoForge, Fabric and Forge without any of those mods being present.
- **Not changed: pay on a break that something cancels later.** Mods that check permission by posting a break event (Building Gadgets 2, Construction Wand, Ars) and a claim mod that cancels later at the same priority can still pay once for a denied break. Moving the payout after every other listener would also move it past the area-tool bookkeeping 1.4.2 added, so it stays as it is until it can be tested against a real claim mod.

### Data and tuning

- **Spellcasting no longer pays for any click with an Ars item.** Before, any right-click with an item from `ars_nouveau`, `ars_additions` or `arseng` paid 0.5 XP a second: a blank parchment, a glyph or a ritual stone worked as well as a spell book, so a held auto-clicker farmed Spellcasting. Now only real casters pay: Ars Nouveau's spell books, caster tome, wand, spell bow, spell crossbow, enchanter's sword, rod and gauntlet, Ars Elemental's caster tomes and spell horn, and Ars Artifice's spell gems. They are the item tag `proficiency:caster_items`, so a datapack can add more. Spell projectiles and Ars damage still pay as before. The tag was checked against the 1.20.1 Ars jars too. This is the stopgap; paying from the spells that actually resolve is still to do.
- **Boss rules follow the mods' own boss tags.** The boss kill bonus, the Boss Bane talent and the x10 first-time bonus now also apply to everything in `#c:bosses`, `#neoforge:bosses` and `#forge:bosses`, besides our own list. A new boss mod works without an edit. The Pandowo datapack also names the bosses that were missing (Cataclysm's Harbinger, Maledictus and Scylla, Mowzie's Sculptor, the Twilight plateau boss, Deep Aether's Eye of the Storm) and says the Eye's segments are not bosses, so the bonus is not paid once per segment. Courage reads the same boss rules, so it agrees with the kill bonus about what a boss is.
- **A Pandowo datapack** (`neoforge/packs/pandowo`, installed through Paxi's `config/paxi/datapacks`). Summons, familiars, helpers, golems, minions, Dragon Mounts dragons, livestock and tame animals, traders and the Target Dummy pay no Beastslaying XP, and no weapon-skill kill bonus or first-time bonus either, so a free summon cannot farm a weapon skill. It also stops furniture and machines in `mineable/axe` (Handcrafted, Another Furniture, Create parts, Farmer's Delight cabinets and crates, Functional Storage, Tom's Storage) from paying Woodcutting when found in the world (a pickaxe or shovel break still pays Mining or Excavation). The README next to it says how to install it. Reclamation's datapack gets the same furniture and machine rule.
- **Grand structures from replacement mods.** Yung's stronghold, ocean monument and fortress, Detailed and Terrain's illager manor, the Aether's gold dungeon, Dungeons Arise's big keeps and Repurposed Structures' mansions, monuments, strongholds and ancient cities count as grand: the announcement and the grand amount, like the vanilla ones they replace.
- **One discovery per structure type, not per biome variant.** Yung's Better Mineshafts (13 ids) and Repurposed Structures (about 100 ids) each paid a full Wayfaring discovery per variant. Now a variant counts as the vanilla structure it replaces (any mineshaft is the Mineshaft, any village the Village, a Yung's stronghold the Stronghold, and so on), or as one shared discovery where vanilla has no twin (the Repurposed nether temples and land ruins). Nothing is lost: anything already discovered under an old id still counts as seen, including a variant id that no list ever named, and the Discovery Journal now shows it as discovered too (the server used to count it while the journal did not).

## 1.4.2

Area tools now pay sensibly. **Hammers, excavators, broadaxes, paxels, vein miners and the Meka-Tool no longer pay full XP for every block they break, and paxels and the Meka-Tool now train skills at all.** The network protocol stays 6, so 1.4.0, 1.4.1 and 1.4.2 clients and servers play together. This release changes all three loaders (the first rule and the second rule below are shared code); it was measured in a real client on the Reclamation pack (Forge 1.20.1).

What was wrong, measured on 1.4.1 in the Reclamation pack.

- **A 3x3 hammer, excavator or broadaxe paid nine full blocks.** One swing at a stone wall gave nine full Mining lines with a climbing tempo bonus (1.00 up to 1.16 for the ninth). An area tool breaks its neighbours through the player, so every neighbour looked like a block the player broke alone. Dirt with an excavator and oak logs with a broadaxe did the same. The first-time bonus and the proc roll were open to every extra block too.
- **Paxels and the Meka-Tool paid nothing on stone and dirt.** The mod only knew a pickaxe by its tag or by being a `PickaxeItem`. The Botanist, Manasteel and Sky paxels (tagged only as paxels), the Mekanism diamond paxel and the Meka-Tool are neither, so mining stone or digging dirt with them paid no XP. Logs still paid Woodcutting, because that rule asks for no tool.

What happens now.

- **One block is the block you broke.** Per player and per server tick, the first break is the primary: full XP, first-time bonus, proc roll and talents, exactly as before. Every other block that player breaks in the same tick is an extra, whatever tool or mod broke it. The rule is generic: it does not know reclamation_util, Mekanism or any vein-mining mod by name.
- **An extra pays 25% of its normal XP.** New config `aoeXpShare` (default 0.25, 0 pays nothing, 1 pays the full amount). An extra gets no first-time bonus, no proc roll, no talent that reacts to a break (Vein Miner, Landslide, Timber, Scythe Sweep) and no tempo step or tempo bonus. Master Farming's always-replant still applies to every crop of a swing.
- **A refused break never takes the primary slot.** If a claim or spawn protection cancels the block you aimed at, your next block is the primary and pays in full, not a quarter. Blocks in different dimensions never count as the same block.
- **Extras are still smelted and collected.** Mountain King's auto-smelt and magnet apply to every block the swing took, as they already did for the mod's own Timber and Landslide. The double-drop chance and proc copies do not.
- **The usual block rules still apply per block.** A block a player placed, ore the tool cannot harvest and an unripe crop pay nothing as an extra, the same as on their own.
- **The mod's own cascades are unchanged.** Timber, Landslide and the vein talents still pay no XP for their extra blocks.
- **The XP feed names it.** An extra's line carries an `aoe` factor (`x0.25`), and the balance telemetry counts the XP under its own `aoe` kind, so the report shows how much XP comes from area tools.
- **Tools are recognised by what they can do.** An item that can dig like a pickaxe, shovel, axe or hoe (the loader's tool actions on Forge and NeoForge) now counts as one, and so does anything in a `forge:tools/paxels` or `c:tools/paxels` tag. A paxel is a pickaxe, a shovel and an axe at once; the block picks the skill, so a swing never pays twice. The old tag and class checks stay as a fallback. Fabric has no tool actions, so it reads the `c:tools/*` tags and the paxel tags. Swinging a paxel at a mob is still not an Axes weapon: the weapon rule is unchanged. The break-speed bonus applies to the held tool by the same test.

Measured in a real client (Reclamation pack, Forge 1.20.1, a stone, dirt and oak-log wall, survival).

| Tool | 1.4.1 | 1.4.2 |
|---|---|---|
| Diamond hammer, stone, 3x3 | 9 full lines, tempo 1.00 to 1.16 | 1 full line + 8 lines at 0.2494 (0.25 x 0.95 x perk 1.05), no tempo step |
| Diamond excavator, dirt, 3x3 | 9 full lines + a first-time bonus | 1 full + 8 at 0.1625 (0.25 x 0.65) |
| Diamond broadaxe, oak logs, 3x3 | 9 full lines + a first-time bonus | 1 full + 8 at 0.275 (0.25 x 1.1) |
| Botanist, Manasteel, Sky and Mekanism diamond paxels, stone | nothing | Mining, one block |
| The same paxels, dirt | nothing | Excavation, one block |
| The same paxels, oak log | Woodcutting | Woodcutting (same) |
| Meka-Tool (charged), stone and dirt | nothing | Mining and Excavation |

Vein mining (Pandowo's Vein Mining 5.0.0 on NeoForge) was not run in a client. Its code breaks the vein inside the first block's break event, at low priority, one `fireBlockBreak` per block, so the same rule applies; the NeoForge and Fabric GameTests prove the rule with a stand-in tool that does the same thing.

For servers and packs: nothing to install except the jar. The one new setting is `aoeXpShare` in `config/proficiency-server.toml`. The XP defaults file and datapacks are unchanged.

## 1.4.1

A fix for the Reclamation pack (Forge 1.20.1 only). **Crop sticks from AgriCraft now pay Farming XP.** The network protocol stays 6, so 1.4.0 and 1.4.1 clients and servers play together. NeoForge and Fabric have no AgriCraft support to fix, so their 1.4.1 jars differ from 1.4.0 only in the version number.

Why it paid nothing. An AgriCraft crop is not a vanilla crop. It is one block, `agricraft:crop`, and the plant, its growth and its ripeness live in a block entity. The normal harvest is a right-click that drops the produce and sets the plant back a stage, so no block is ever broken and the mod never saw a harvest. Breaking a mature plant paid nothing either: no XP rule matched the block, and crop sticks you place yourself count as player-placed.

What pays now.

- **Right-click harvest.** Harvesting a mature plant by right-click pays what a ripe wheat harvest pays (1.0 Farming XP by default). The mod checks the plant before the click and at the end of the same tick: the same plant, mature before, at a lower growth stage after, is a harvest.
- **First-time bonus and log label are per plant.** The XP log shows the plant's own name (Wheat, Diamahlia, ...), and the first-time bonus is once per plant species, the same way vanilla pays once per crop block.
- **Bountiful Harvest and the Farming passive's bonus drop** copy what the harvest dropped, like they do for vanilla crops. Replanting, Seed Saver and Golden Crop do not apply, because the plant is never removed.
- **Breaking a mature plant** pays the same as breaking ripe wheat. Breaking a young plant or bare crop sticks pays nothing.
- **Planting a seed** on crop sticks, or on soil, pays the same 0.5 Farming XP as planting wheat. Placing bare crop sticks pays nothing (it used to pay Decorating XP).
- **Numbers are data.** The Reclamation datapack has a break rule and a place rule for `agricraft:crop` (1.0 and 0.5). Change them there. With no rule, wheat's rule is used.
- **No exploits.** A crop pays at most once per position per tick, so a break and a click in the same tick cannot pay twice. Lifting a plant with a trowel and putting it back pays nothing. A machine or fake player (a deployer, for example) pays nothing.

How it is built. AgriCraft is optional. There is no dependency: the mod looks for it by name at start-up and reads it through its public API classes. If AgriCraft is missing, or an update changes the API, the log says so once and the support turns itself off. It never crashes.

Fixes after review: a seed in the off hand now pays planting, creative players get no harvest XP, procs or bonus drops from crops, and one odd crop block can no longer switch AgriCraft support off for the whole session.

Checked in a real client (Reclamation pack, AgriCraft 4.0.6, wheat): planting paid 0.5, harvest by right-click paid 1.0 each time, a forced Farming proc dropped about three times the produce, breaking a mature plant paid 1.0 once, and breaking a young plant, harvesting a young plant and placing and breaking bare sticks paid nothing. Screenshots and the XP feed files are in `screenshots/proficiency-forge-1201/agricraft-2026-10-08/` on the build box.

Install the datapack change too (`forge-1.20.1/packs/reclamation/datapack/`, in the pack as `kubejs/data/reclamation/proficiency/xp_sources/reclamation.json`). Without it the support still works, with wheat's numbers.

## 1.4.0

The rested XP release. Skills now rest while you are away from them and pay double when you come back, and a high-level player can teach a lower-level one inside Social. **The network protocol is now 6**: servers, clients and packs must update together, and a 1.3.0 client cannot join a 1.4.0 server. Design, numbers and limits: `docs/RESTED-AND-TEACHING.md`.

Rested XP.

- Every skill has a rested pool. It fills while you are active (not AFK) and the skill has not paid XP for a minute, up to 1.5 times what the skill's current level costs, and is full after about 9 active hours.
- Spending: each grant in that skill pays double until the pool is empty (the extra comes out of the pool). It adds to the survival bonus and does not multiply it. At level 100 the extra feeds the Mastery star bar.
- A death empties every skill's pool, teacher-filled parts included, on top of the bar wipe and the survival-bonus loss. A Death Ward keeps none of it.
- The HUD bar and the skills panel show the pool as a faint blue segment after the fill. The panel tooltip says `Rested: N XP`, and `Rusty` after a skill has gone 7 real days without XP (flavour only, no penalty). The death recap and chat say how much rested XP the death took.
- New server config section `[rested]`: `capFactor`, `fullHours`, `extra`, `teachFactor`, `teacherShare`. `capFactor = 0` turns it off. New op command `/proficiency rested <player> <skill> <xp>`.
- Saves: new optional fields `rested` and `usedDay`; old worlds load with empty pools and no rust. Sync packet and death recap carry the pool, so the protocol is 6.

Teaching, inside Social.

- A player near you who is at least the mentor gap (20 levels) above you in a skill, and is using it, fills your rested pool in that skill 3 times as fast as resting. Both must be active. Each part of the pool remembers its teacher.
- When you spend a teacher's part, the teacher earns Social XP, 25% of the XP you spent. An offline teacher is paid at the next login (the credit is saved in the world). If you die, the credit still waiting on you is lost with your pools. A small toast tells the teacher, at most every 30 seconds.
- Three new Social talents: Quick Study (teaching fills faster), Wide Classroom (teaching reaches further) and Teacher's Pride (the teacher's cut grows from 25% to 35%). To keep the tree at 99 points, Kinship and A Good Word went from 5 ranks to 2 stronger ranks with the same total effect.
- **Mentor XP bonus lowered from +50% to +25%** (`company.mentorBonus`, 0.5 to 0.25), so a student near a teacher is not paid twice. Wise Counsel now takes it from +25% to +40%. An existing server config keeps 0.5 until it is edited: set `mentorBonus = 0.25` on live servers and check the mentor-heavy skills in the balance report a week later.

Telemetry.

- New XP kinds `rested` (the extra XP grants gained from a pool) and `teaching` (Social XP paid to teachers). `tools/balance_report.py` has Rested XP and Teaching XP columns, a summary line and source-mix colours for them.

Fixes for what the code reviews of 1.3.0 found. No packet or save change, no gameplay number changed.

Mastery stars.

- A level 100 skill with Mastery switched off (`masteryMaxStars = 0`) shows a full HUD bar again, as in 1.2.0, instead of an empty one.
- Earning a star no longer snaps the HUD bar: the bar value counts stars, so a star flies its dots in like a level-up. The skills panel bar and the activity sparkline use the same value, so the panel shows the star bar at 100 and the sparkline never reads 101.
- A cap lowered after stars were earned shows 3/3, not 5/3, in the tooltip and next to the name.
- `/proficiency stars <player> <skill> 0` no longer promotes the skill to level 100, and a count above the cap is clamped to it.
- Earning a star in the same grant as a level-up sends one sync, not two. The star chime and sparks are private feedback and are not gated by `announceMilestones`; the docs now say so.
- Wording: a death cuts the overflow bar by the skill's Death Ward share, it does not always empty it.

Balance telemetry.

- Proc chances above 1 are clamped before they are summed, so the report no longer flags every such skill as "proc rate off".
- A skill row with no grant in its window carries no level instead of 0, and the report keeps the last known level. The level also survives a flush.
- Operator `/skills addxp` no longer counts as active or engaged time, and no longer refreshes the survival streak's activity clock.
- A player counts as active from real input (moving, turning, or XP from play), not from passive trickles, so AFK players stop showing as active.
- A flush keeps the engaged minute of a skill that is still in use. A lone payout after a long quiet spell is credited 10 seconds, not a full minute.
- Spellcasting and other grants with no source are `action`, not `other`. Endurance and Blocking hits taken are `damage`, not `mob`. XP dropped past the last star is no longer counted as earned.
- A failed write is retried on the next flush instead of losing five minutes. Old files are pruned daily, and a new world in the same JVM starts with clean counts.
- `tools/balance_report.py` uses Warsaw dates, one XP curve per server, and ignores rows without a level.

## 1.3.0

The balance release: mastery stars, XP sources as datapack data, and balance telemetry. All three versions (NeoForge 1.21.1, Fabric 1.21.1, Forge 1.20.1) now come from one codebase and behave the same.

Mastery stars. The network protocol is now 5: server, clients and packs must update together, and a 1.2.0 client cannot join a 1.3.0 server.

- Once a skill is level 100, its XP fills an overflow bar that earns up to five stars. Star n costs n times the XP of level 99 to 100, so all five cost about as much as the last 15 levels. Stars are cosmetic: no stat, talent point or proc reads them.
- A death cuts the current overflow bar the way it cuts every XP bar (a Death Ward keeps its share of it) and never takes a star. Old saves load with 0 stars.
- Small gold stars next to the skill name in the skills panel, the tree screen and the HUD line. The HUD bar of a level 100 skill shows progress to the next star in its own colour, a new star plays a lighter level-up moment, and the server gets a chat line per star (the fifth is a grand master line). Hovering shows stars x/5 and progress.
- Config: `masteryStarFactor` (1.0) and `masteryMaxStars` (5, 0 turns it off). New op command `/proficiency stars <player> <skill> <0-5>`.
- README: "Mastery stars".

XP sources are data now. On its own this changes no packet, and with no datapack the game pays exactly what 1.2.0 paid.

- Block breaking, block placing, machine crafting, spell casting, mob kills, the kill bonus, what counts as a boss, structure, biome and dimension discoveries, and the first-time tiers are read from `data/<namespace>/proficiency/xp_sources/*.json`. A pack can retune a source, add a modded one or switch one off, and `/reload` applies it.
- The mod's own rules are the file `defaults.json` in the jar. A golden test and a GameTest that walks every registered block, mob, item and structure hold it to the 1.2.0 hardcoded rules.
- A bad file or rule is logged and skipped, never a crash. Unknown ids are logged once. The server log gets one line, "XP sources: Loaded N XP sources from M files".
- New op commands: `/proficiency xpsources <id>` shows which rule matched and why, and `/proficiency xpaudit <mod>` lists a mod's ores, logs, crops and mobs that pay nothing or the wrong skill.
- The Reclamation pack has its own datapack in `forge-1.20.1/packs/reclamation/datapack/`.
- README: "Tuning XP with a datapack".

Balance telemetry. No packet or save change.

- Every server counts XP per player, skill and source kind, active time, level-ups, procs, deaths and XP lost in memory, and appends them to `<world>/proficiency/telemetry/YYYY-MM-DD.jsonl` every five minutes and on stop. A grant does no disk work. Config `telemetry.enabled` (true) and `telemetry.retentionDays` (60, old files are deleted at start).
- `tools/balance_report.py` turns the files into one HTML page and a markdown summary: XP per active hour, source mix, dead and runaway skills, time to level, proc rate against the configured chance, death cost, with `--anon`.
- README: "Balance telemetry".

## 1.2.0

The readable release. The network protocol is unchanged from 1.1.0 (still 4), so a 1.1.0 client can join a 1.2.0 server, but update both sides to get the new tooltips and the kill bonus.

- Every text fits its room in all 12 languages. Long lines wrap or end in an ellipsis with the full text on hover. The talent tree fits 1280x720 and the Steam Deck screen.
- Kills pay a kill bonus of 2 + max health / 5, kept between 2 and 20, and doubled for bosses. All four numbers are server config values.
- The first-time bonus for a mob type now comes with the first kill, not the first hit.
- Tooltips are short by default, with the full detail on Shift. The detailed layer shows what a passive really changes, for example "Block reach: 4.5 -> 5.1", for all 34 skills.
- A new client setting, "Always show details", keeps the detailed layer on.
- Short one-line descriptions for talents, signature moves and synergies, in all 12 languages.

## 1.1.1

The localisation release.

- Ten new languages are in. Simplified Chinese, Russian, Brazilian Portuguese, Spanish, German, French, Japanese, Korean, Turkish and Ukrainian. Each was translated for meaning, using the game's own terms in that language, and reviewed.
- Polish is complete on every loader.
- The survival streak is now called the survival bonus.
- No gameplay change. Servers and clients on 1.1.0 and 1.1.1 can play together.
- Internal build, not released publicly.

## 1.1.0

The visual release. Nothing in the XP rules changed. Every effect below has its own client toggle, so you can switch any of them off.

- A level-up moment on the HUD when a skill levels.
- While an ability runs, the screen edge glows and a ring around the crosshair drains. On cooldown, the item shows a sweep.
- The streak badge changes with the streak instead of staying flat.
- A death recap shows what the death cost you.
- Unlocking a talent plays an animation. The skills panel glows for skills you used recently and draws a small sparkline of recent XP.
- Item tooltips show the skill an item trains.
- The discovery banner reveals its text letter by letter, tints by dimension and shows an icon for structures.
- Each skill has its own proc particle effect.
- All 34 skills have an icon, shown in the HUD, the skills panel, the talent tree, the XP feed, banners, the death recap and the ability wheel.
- The network protocol is now version 4. Server, clients and pack must update together. An old client is refused at login.

## 1.0.0 (NeoForge)

The first release. What is in it:

- 34 skills in six categories, each with its own talent tree. Trees fill completely by level 100 (1 point per level).
- 17 synergies between trees, and a respec that costs 1 XP level per 10 points.
- Passives, signature procs and an ability per skill. Tap G for the held item's ability, hold it for the ability wheel.
- Survival streak: +1% XP per active hour alive, capped at 50%. A death wipes the XP bars, never the levels.
- Discovery: banners for new biomes, dimensions and structures, a first-time bonus for every new kind of block, mob and item, and a discovery journal.
- Forester's Compass (biome and structure search), Friend Compass, station recipes, and the XP feed for tuning.
- HUD: skill bar with XP dots, streak badge, tenth-level server announcements.
- Nightwalker: Dark Sight, off while you hold a light.
- Masonry and Decorating split by block shape; planting pays Farming; unripe crops pay nothing.
- Placed blocks pay no gathering XP when broken. Spawn-egg, dispenser, command and bucket mobs pay no combat XP; spawner mobs pay 25%. All three are server config values.
