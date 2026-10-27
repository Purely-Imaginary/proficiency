# Mastery riders (1.27.0, reworked in 1.35.0)

A Mastery star is felt. Every skill at level 100 earns a bespoke **rider** from its stars: a real, skill-flavoured mechanic on the skill's own signature proc. 1.27.0 gave every skill a rider, but most were a few seconds of a vanilla potion and four of them paid the same item count at 1 and 2 stars. 1.35.0 replaced all 38 with the table below: each one does something to the world (a phantom slash, a ping through rock, a flung catch of fish, a replanted furrow), and **every star from 1 to 5 is a step up**, never a plateau (`MasteryEffectsTest` fails on a repeated value). The rider is the same on NeoForge, Fabric and Forge 1.20.1.

His decision (2026-10-26): Mastery stars MAY give stat and mechanic effects. They still give no talent point, never raise a proc chance and never soften a death.

## Rules (all 38 rows obey them)

- **Only the skill's own proc.** The rider fires when the proc lands (`ProcService.celebrate`, which every proc path goes through, rolled, frenzied or forced). It never rolls on its own, never raises a proc chance, power, level or talent point.
- **Never chains.** A rider's damage is magic with the player as cause and no weapon (`indirectMagic(null, player)`). Every proc path in `CombatEvents` reads a held weapon, an arrow or a trident as the direct entity, so this source starts nothing. Riders that place, grow or break (Landslide) go through `level.destroyBlock`/`setBlock`, which raise no break event, XP or proc.
- **Level 100 and a star.** `masteryMaxStars` lowers the rider, 0 switches every rider off.
- **No death-softening.** A death wipes bars and never takes a star or a level; the rider is the same after it (`aDeathLeavesTheRiderAlone`). No rider revives, shields from a killing blow or cancels a death penalty. Shrug It Off never washes off Wither or instant damage, Rebound adds Slow Falling only as the landing of its own jump, no rider gives Absorption or Resistance.
- **No wealth loops.** Items are trivial goods only (fish: cod, salmon, tropical, pufferfish; bread; saplings; flowers; eggs; the loot of loose dirt, sand, gravel and clay), small counts (at most 5 to 12 per proc), and gifts (bread, fish, eggs) stop at 64 carried. Nothing gives emeralds, ingots, ore or XP. Landslide skips blocks a player placed. Tractor Beam skips what another player threw.
- **Fake players never.** A machine's fake player gets no rider (`FakeActors.isFake`), and the proc's own XP guards (placed blocks, repeats, AFK) still stand in front of it.
- **Bounded crowds, honest flourish.** An area or chain rider touches at most 8 foes (`MasteryMath.MAX_VICTIMS`, nearest first), so a mob grinder is no mass kill. A rider with nothing to act on (no foe, no unlit fire, no ore) does nothing and shows no flourish. Delayed hits are dropped, not run, when the server stops (`MasteryTasks.laterEvenOnStop` is only for marker clean-up). New Horizon searches at most once per 5 s per player and caches the structure list per level.
- **Fair targets.** Damage and debuffs go only to hostile mobs (`Enemy`; never a neutral one such as a zombified piglin, piglin or enderman unless it is already after the player) or a mob already hunting the player, never to another player, a tamed pet, an armor stand or a target dummy.
- **GPU-light.** Each rider is one scan of its radius (entities by one box query; ore and chest scans over loaded chunks only), one burst of a few particles and one sound. The glowing markers are at most 12 block-display entities for 6 s, never saved: a marker that comes back off disk is dropped as it loads (`MasteryTasks.onMarkLoaded`).
- **Delay.** The proc's own blow lands after the proc is announced, so a phantom hit on the same foe waits 6 ticks (`MasteryTasks.later`) and resets the foe's hurt invulnerability so it is not swallowed.
- **Distinct from talents and synergies.** Talents change what the proc does or how often; a rider is what the proc leaves behind, chosen from what that skill's own moment feels like.

The numbers live in core `MasteryEffects` (the headline value per star) and `MasteryMath` (fixed damage, dash and launch physics, caps), both unit tested; the actions in `MasteryRiders` (common, hand-ported to forge-1.20.1); the delayed hits and glow markers in `MasteryTasks`; the text in en_us only (`proficiency.mastery.<skill>`, `.desc`, `.short`, and two Wayfaring message keys).

## Table

Column "Per star" is the headline number at stars 1 to 5, "Step" says what that number is.

| Skill | Rider | Proc it rides | Per star (1 / 2 / 3 / 4 / 5) | What it does | Step | VFX / SFX |
|---|---|---|---|---|---|---|
| swords | Echo Slash | Perfect Strike | 3 / 5 / 7 / 9 / 12 | A phantom second slash lands 6 ticks after the proc (after the proc's own blow), on the foe and every hostile within 2.5 blocks of it. Magic damage, N each. | damage N | two sweep arcs and enchanted-hit sparks on each foe, sweep sound |
| axes | Whirl | Cleave | 2 / 3 / 4 / 5 / 6 | A spin: every hostile within N blocks of you takes 4 damage and is knocked back. | radius N | ring of sweep arcs and crits at the spin's edge, sweep sound |
| maces | Tremor | Shockwave | 3 / 4 / 5 / 6 / 7 | Every hostile within N blocks is thrown 0.75 up. They take their own fall damage, the rider deals none. | radius N | ring of dust clouds, low blast |
| tridents | Arcing Tide | Riptide Strike | 1 / 2 / 3 / 4 / 5 | The charge arcs from the target to N more hostiles (each the nearest unvisited one to the last, within 8 blocks), 4 damage each, 3 ticks apart. | N extra targets | electric sparks along each arc, thunder tick |
| unarmed | Flurry | Stunning Blow | 2 / 3 / 4 / 5 / 6 | N extra jabs in the same blink, 2 damage each, as one hit 6 ticks after the proc. | N jabs (2N damage) | a crit burst per jab, knockback thud |
| blocking | Shield Bash | Riposte | 3 / 4 / 5 / 6 / 7 | The attacker (or the nearest hostile within 4 blocks) is hurled about N blocks away and takes 2 damage. | N blocks of throw | dust puff, shield-block clang |
| endurance | Shrug It Off | Grit | 2 / 3 / 5 / 7 / 9 | Washes off the first N kinds of harmful effect you carry: Slowness, Weakness, Mining Fatigue, Nausea, Blindness, Hunger, Poison, Darkness, Levitation. Never Wither, never instant damage. | N kinds | green sparks (more per kind washed), brewing hiss |
| archery | Ricochet | Piercing Shot | 1 / 2 / 3 / 4 / 5 | The arrow glances on N times, each time to the nearest hostile it has not hit, 5 damage, 3 ticks apart. | N hops | crit trail between victims, arrow tick |
| crossbows | Splinter Bolt | Double Load | 2 / 3 / 4 / 5 / 6 | The bolt splinters: every hostile within N blocks of the target (not the target) takes 4 damage. | radius N | smoke ring and crit burst, crossbow clack |
| courage | War Cry | Rally | 4 / 6 / 8 / 10 / 12 | Every hostile within N blocks is shoved back and weakened (Weakness I, 6 s). | radius N | a sonic boom ahead of you, roar |
| guardian | Warding Pulse | Intercept | 3 / 4 / 5 / 6 / 7 | Every hostile within N blocks is pushed hard off you. | radius N | ring of glow motes, anvil ring |
| charger | Stampede | Breach | 4 / 6 / 8 / 10 / 12 | The lane N blocks long and 3.4 wide in front of you is cleared: hostiles in it take 4 damage and are flung sideways and up. | lane length N | a trail of dust down the lane, gallop |
| tactician | Pinning Fire | Called Shot | 4 / 6 / 8 / 10 / 12 | Every hostile within N blocks gets Slowness II and a glow for 5 s. | radius N | crit ring, spyglass click |
| mining | Motherlode Ping | Motherlode | 6 / 8 / 10 / 12 / 14 | Every ore within N blocks (up to 12, nearest first) gets a glowing marker that shows through rock for 6 s, then goes away. | radius N | gold outline through walls, end-rod motes, resonating amethyst |
| woodcutting | Replant | Timber | 2 / 3 / 4 / 5 / 6 | N saplings (the species of the log or leaf nearby, oak otherwise) are planted on bare dirt around the stump, at least 2 apart. | N saplings | green sparks at each, grass rustle |
| excavation | Landslide Slip | Landslide | 2 / 4 / 6 / 9 / 12 | The N nearest loose ground blocks (dirt, grass, sand, gravel, clay, mud) around the spot give way and drop their loot. Placed blocks, blocks beside a fluid and the one you stand on are skipped. No XP, no events. | N blocks | the blocks' own break particles, gravel crunch |
| farming | Green Thumb | Bountiful Harvest | 2 / 3 / 5 / 7 / 10 | The N nearest growing crops within 6 blocks are bone-mealed, and as many bare furrows as you have stars are replanted with the crop around. | N crops (stars = replants) | bone-meal sparkle, bone-meal sound |
| fishing | Second Catch | Treasure Haul | 1 / 2 / 3 / 4 / 5 | N more fish (cod, salmon, tropical, pufferfish only) are hooked and flung to you. | N fish | splash spray, bobber splash |
| husbandry | Open Barn | Twins | 6 / 8 / 10 / 12 / 14 | Within N blocks: sheared sheep regrow wool, each hen lays an egg (8 at most), calves grow 20 s per star. Another player's tamed pet is left alone. | radius N (calf growth 20 s x stars) | hearts over each animal, egg pop |
| running | Gale Step | Second Wind | 3 / 4 / 5 / 6 / 7 | A burst forward in the way you look, carrying you about N blocks. | N blocks | dust at your feet, wing beat |
| sneaking | Smoke Step | Shadowed | 4 / 6 / 8 / 10 / 12 | Hunters within N blocks forget the hunt and you vanish for a moment (2 s + 0.5 s per star). | radius N | smoke puff (larger per star), enderman whisper |
| jumping | Rebound | Featherfall | 2 / 3 / 4 / 5 / 6 | A spring throws you about N blocks up, with 5 s of Slow Falling. | N blocks of height | dust ring, slime bounce |
| swimming | Riptide Glide | Undertow | 3 / 4 / 6 / 8 / 10 | In water: the current carries you about N blocks the way you look and your breath is full. On land nothing happens. | N blocks | bubbles, dolphin leap |
| riding | Rested Mount | Tailwind | 2 / 4 / 6 / 8 / 10 | The mount you ride recovers N health and is washed clean of harmful effects. | N health | hearts over the mount, horse breath |
| smithing | Quench | Masterwork | 5 / 10 / 15 / 20 / 25 | Worn armour and the tool in your hand regain N% of their maximum durability (at least 1). | N percent | lava drips and flame, anvil ring |
| cooking | Seconds Served | Second Helping | 2 / 3 / 4 / 5 / 6 | You and everyone within 8 blocks get N food points and as much saturation. | N food | green sparks on everyone fed, a bite |
| alchemy | Reagent Echo | Efficient Draught | 10 / 20 / 30 / 40 / 50 | Every helpful effect you have is stretched by N% of what it has left (5 s to 2 min, never past 20 min in all). | N percent | witch swirl |
| spellcasting | Magic Missiles | Arcane Surge | 1 / 2 / 3 / 4 / 5 | N missiles fly at N separate hostiles within 10 blocks (nearest first), 3 damage each, 2 ticks apart. | N missiles | violet line to each, glyphs on landing, enchanting-table chime |
| engineering | Tractor Beam | Overengineered | 4 / 6 / 8 / 11 / 14 | Every dropped item within N blocks (24 at most, never one another player threw) flies to your feet. | radius N | spark line from each item, beacon hum |
| beastslaying | Fresh Kill | Trophy Kill | 2 / 3 / 4 / 5 / 6 | The kill feeds you: N food points and N saturation. | N food | green sparks |
| wayfaring | New Horizon Mark | New Horizon | 6 / 10 / 14 / 18 / 24 | Searches N chunks for the nearest structure you have not seen (the same list as the Forester's Compass) and tells you its name, distance and compass heading. | N chunks | a trail of end-rod motes pointing the way, chime |
| spelunking | Cave Sense | Cave Sense | 12 / 16 / 20 / 26 / 32 | Unlooted loot chests (gold marker) and monster spawners (red marker) within N blocks glow through the rock for 6 s (12 at most). A chest someone has emptied or built does not. | radius N | coloured outlines through walls, resonating amethyst |
| masonry | Repoint | Master Mason | 2 / 4 / 6 / 9 / 12 | Up to N cracked or mossy blocks (stone bricks, cobblestone, deepslate, nether and blackstone brick kinds) within 5 blocks are restored to clean stone. Placed blocks are left as built. | N blocks | wax-off sparks, stone set |
| decorating | Bloom | Inspired | 3 / 5 / 7 / 9 / 12 | N small flowers open on the grass within 4 blocks. | N flowers | green sparks, azalea rustle |
| homebuilder | Hearthglow Relight | Hearthglow | 4 / 6 / 8 / 10 / 12 | Every unlit campfire and candle within N blocks (24 at most) is lit, and you get a warm bite: saturation equal to your stars. | radius N (saturation = stars) | flames at each, fire charge |
| social | Toast | Good Company | 2 / 3 / 4 / 5 / 6 | You and everyone in your company radius regain N health. | N health | hearts over each, glass clink |
| commerce | Thrown In | Good Deal | 1 / 2 / 3 / 4 / 5 | The nearest villager within 8 blocks throws N loaves of bread to you (nothing once you carry 64). | N bread | green sparks and a villager's yes |
| nightwalker | Moon Gaze | Moonlit | 6 / 8 / 10 / 12 / 14 | Every hostile within N blocks glows silver for 8 s. | radius N | end-rod crown over each, soft chime |

## Where it shows

- The skill row tooltip in the skills panel (level 100): the rider's name and its effect at the stars earned (star 1's number when none is earned yet); with Shift the whole 1-to-5 table.
- The tree screen header: a gold line under the numbers, "Mastery (stars): name: effect", hover for the same tooltip. Below level 100 the line is absent.
- On the proc: the rider's own effect, then gold sparks, a soft chime and the rider's name with its stars on the action bar (at most once every 2 s). A rider that finds nothing to act on (no ore in range, no foe, no debuff to wash off) does nothing and shows no flourish.

## Tests

- core `MasteryEffectsTest`: every skill has a rider, every one grows at EVERY star, level and cap gates, a death changes nothing. `MasteryMathTest`: dash distances, rebound heights, Reagent Echo bounds, Quench percent, Shrug It Off order, item and radius caps.
- GameTests `MasteryRiderGameTests` on all three loaders: one test per world effect (28 of them: every damage rider with its delay and its out-of-range stranger, the crowd riders, the markers that glow and then go, replant, landslide, green thumb, fish, barn, dashes, rebound, glide, mount, quench, seconds, echo, missiles, tractor, kill feed, compass, cave sense, repoint, bloom, relight, toast, bread, moon gaze), the level and star gates, the death rule.
