# Changelog

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
