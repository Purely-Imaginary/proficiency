# Changelog

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
