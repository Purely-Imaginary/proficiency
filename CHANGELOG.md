# Changelog

## 1.3.0

The balance release: mastery stars, XP sources as datapack data, and balance telemetry. All three versions (NeoForge 1.21.1, Fabric 1.21.1, Forge 1.20.1) now come from one codebase and behave the same.

Mastery stars. The network protocol is now 5: server, clients and packs must update together, and a 1.2.0 client cannot join a 1.3.0 server.

- Once a skill is level 100, its XP fills an overflow bar that earns up to five stars. Star n costs n times the XP of level 99 to 100, so all five cost about as much as the last 15 levels. Stars are cosmetic: no stat, talent point or proc reads them.
- A death empties the current overflow bar and never takes a star. Old saves load with 0 stars.
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
