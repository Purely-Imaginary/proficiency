# Proficiency

A Valheim-style skill system for Minecraft. You get better at what you do. Mining raises Mining,
swinging an axe raises Axes, and sneaking past mobs raises Sneaking. There is no class to pick and no
character sheet to fill in.

One codebase builds three jars:

| Jar | Loader | Minecraft | Java |
|---|---|---|---|
| `proficiency-<version>.jar` | NeoForge 21.1.192 or newer | 1.21.1 | 21 |
| `proficiency-<version>-fabric.jar` | Fabric Loader 0.16.14 or newer, Fabric API 0.116.17+1.21.1 | 1.21.1 | 21 |
| `proficiency-<version>-forge-1.20.1.jar` | Forge 47.4.0 or newer | 1.20.1 | 17 |

![A talent tree](docs/talent-tree.png)

## What it adds

- **34 skills** in six categories, each from 0 to 100. Gathering, crafting, combat, movement,
  construction and survival.
- **A passive per skill** that grows with the level, and a **signature proc** from level 25. Mining
  hits a Motherlode, Woodcutting fells the whole tree, Swords land a Perfect Strike.
- **A talent tree per skill.** One point per level, and every tree can be filled completely by
  level 100. Branch choices set priority, never a lockout. A respec costs 1 XP level per 10 points.
- **17 synergies** between trees. Each pays off when you invest in two related skills.
- **An active ability per skill** from level 50. Tap the ability key for the held item's ability,
  hold it for a wheel with every ability you have unlocked.
- **Mastery stars.** After level 100 a skill's XP fills an overflow bar that earns up to five
  gold stars, shown next to the skill name. Stars are cosmetic. A death cuts the bar and never
  takes a star.
- **Rested XP.** A skill you have not used for a while rests. Its rested pool fills while you are
  active and away from that skill (up to 1.5 levels' worth, full after about 9 active hours), and
  every grant in that skill pays double until the pool is spent. The HUD bar and the skills panel
  show the pool as a faint blue segment. **A death empties every rested pool.**
- **Teaching, inside Social.** A player near you who is at least 20 levels above you in a skill and
  is using it fills your rested pool in that skill 3 times as fast. When you spend what they put in,
  they earn Social XP. Three new Social talents (Quick Study, Wide Classroom, Teacher's Pride) tune
  it. The mentor XP bonus is +25% now, not +50%, so a student near a teacher is not paid twice.
- **Survival bonus.** +1% skill XP per active hour alive, up to +50%. A death wipes the bonus and
  the XP bars, never your levels or talents.
- **Discovery.** Banners for new biomes, dimensions and structures, a one-time bonus for every new
  kind of block, mob and item, and a discovery journal.
- **Items.** The Forester's Compass (finds unvisited biomes and structures), the Friend Compass, and
  station recipes made with a tool on a station (a ladle on a water cauldron, a smithing hammer on
  an anvil).
- **HUD.** The current skill's bar sits above the hotbar with the skill's icon, and XP dots fly into
  it. A level-up rolls the number over with a small burst, gold on every tenth level. A badge
  fills as your survival bonus grows. While an ability runs, the screen edge glows in the skill's
  colour and a ring around the crosshair drains. On cooldown, the item shows a sweep. After a
  death, a short recap shows the bars you lost. Every tenth level is announced in chat.
- **Screens and effects.** Spending a talent point animates the node and lights the path to the
  nodes it opens. The skills panel marks skills you used recently and graphs today's XP. Every
  skill's proc has its own particle effect, discovery banners reveal their title letter by
  letter, and tool tooltips show the skill's level, proc chance and ability. Each effect has a
  switch in `config/proficiency-client.toml`.
- **Fair XP.** Blocks you placed pay no gathering XP when you break them. Mobs from spawn eggs,
  dispensers, commands and buckets pay no combat XP, and spawner mobs pay 25%. Unripe crops pay
  nothing. Kills pay a bonus on top of the hits, bigger for tougher mobs, and the first kill of a
  new mob type pays a one-time bonus.
- **XP sources as data.** What a block, mob, structure or biome pays is read from datapack files.
  A modpack can retune a source, add a modded one or switch one off, with no rebuild and nothing
  to install on clients. See "Tuning XP with a datapack".
- **Balance telemetry.** The server counts XP, active time, procs and deaths and writes them to
  daily files, and a script turns them into a report. See "Balance telemetry".
- **Tooltips in two layers.** Hovering a tool, a skill or a talent shows a short summary. Hold
  Shift for everything: the real numbers your passive changes (Block reach 4.5 → 5.1, Break
  speed ×1.00 → ×1.30), proc chance and power, costs and requirements. Power users can make the
  detail permanent with one client setting.
- **12 languages.** English, Polish, Simplified Chinese, Russian, Brazilian Portuguese, Spanish,
  German, French, Japanese, Korean, Turkish and Ukrainian, each written for meaning with the
  game's own terms, and every screen laid out so long words wrap instead of clipping.

Every tree, node and synergy is listed in [docs/TREES.md](docs/TREES.md). The full list of changes is
in [CHANGELOG.md](CHANGELOG.md).

## Install

Put the jar for your loader in the `mods/` folder of the **server and every client**. The mod
registers items, so a client without it cannot join a server that has it. Version 1.4.0 speaks
network protocol 6: a 1.3.0 or older client cannot join a 1.4.0 server, so update the server, the
clients and any modpack together.

An existing server keeps its saved `mentorBonus = 0.5`. The new default is 0.25: set it by hand in
`config/proficiency-server.toml` if you want the new balance.

Default keys on NeoForge and Fabric: **K** opens the skills panel, **G** uses an ability (hold it
for the wheel). On Forge 1.20.1 the keys are **I** and **`** (backtick), because K and G are taken
in the Reclamation pack. All of them can be rebound under Controls.

## Commands

| Command | Who |
|---|---|
| `/skills` | anyone, own levels |
| `/proficiency perks [skill]` | anyone, own trees |
| `/proficiency respec <skill>` | anyone, empties one tree for XP levels |
| `/proficiency synergies` | anyone, every synergy and your progress |
| `/proficiency recipes` | anyone, the station recipes |
| `/proficiency top <skill>` | anyone, best online player in that skill |
| `/skills xpfeed [on\|off]` | anyone, a live XP feed with every multiplier |
| `/proficiency set <player> <skill> <level>` | op |
| `/proficiency addxp <player> <skill> <amount>` | op |
| `/proficiency stars <player> <skill> <0-5>` | op, sets a skill to level 100 with that many stars |
| `/proficiency rested <player> <skill> <xp>` | op, sets a skill's rested pool (0 empties it) |
| `/proficiency xpsources <id>` | op, which XP rule matched a block, item, mob, structure or biome and why |
| `/proficiency xpaudit <mod>` | op, a mod's ores, logs, crops and mobs that pay nothing or the wrong skill |
| `/proficiency reset <player>` | op |

## Configuration

- `config/proficiency-server.toml`, created on first start: per-skill switches and XP rates, the
  level curve, the survival bonus, abilities, the placed-block and spawner rules
  (`placedBlocksPayXp`, `spawnerMobXp`, `artificialMobXp`), the kill bonus (`killBonusBase`,
  `killBonusHealthDivisor`, `killBonusCap`, `killBonusBossMultiplier`), Mastery stars
  (`masteryStarFactor`, `masteryMaxStars`, 0 turns them off) and telemetry (`telemetry.enabled`,
  `telemetry.retentionDays`) and rested XP and teaching (`rested.capFactor`, 0 turns it off,
  `rested.fullHours`, `rested.extra`, `rested.teachFactor`, `rested.teacherShare`).
- `config/proficiency-client.toml`, per player: banners, the XP feed position, the XP dots, every
  visual effect, and `tooltip.alwaysDetailed` (always show the Shift layer).

Levels cost `8 + 2 * L^1.35` XP by default, about 44,000 XP from 0 to 100. Change `curveFloor`,
`curveBase` and `curveExponent` in the server config to make it faster or slower. Star n costs n
times the XP of level 99 to 100 (times `masteryStarFactor`), so all five stars cost about as much
as the last 15 levels.

## Tuning XP with a datapack

Every number that decides what an action pays is data. Put a file at
`data/<namespace>/proficiency/xp_sources/<name>.json` in any datapack (`world/datapacks/` on a
server) and run `/reload`. Only the server reads these files. The mod's own rules are the file
`data/proficiency/proficiency/xp_sources/defaults.json` inside the jar, and with no datapack the
game pays exactly what 1.2.0 paid.

A file is a JSON object with one array of rules per domain: `break`, `place`, `craft`, `cast`,
`kill`, `kill_bonus`, `boss`, `structure`, `biome`, `dimension` and `first_time`. A rule names what
it matches (`"match"`: an id, a `#tag`, `mymod:*` or `*`), the `skill` (or `"none"` to pay
nothing) and the `xp`, a number or `{ "base", "per_hardness", "per_health", "min", "max" }`. The
highest `priority` wins, then the more specific match, then the rule loaded later. A bad file or
rule is logged and skipped, never a crash.

```json
{
  "break": [
    { "match": "mymod:ruby_ore", "skill": "mining", "tool": "pickaxe",
      "xp": { "base": 2.0, "per_hardness": 0.5, "max": 8.0 } },
    { "match": "#mymod:orchard_logs", "skill": "woodcutting", "xp": 1.5 },
    { "match": "mymod:rice", "skill": "farming", "xp": 1.0, "flags": ["crop"] },
    { "match": "minecraft:bookshelf", "skill": "none" }
  ],
  "place": [
    { "match": "mymod:*", "exclude": { "ids": ["mymod:steam_engine"] },
      "skill": "decorating", "xp": 1.0, "flags": ["decor"] },
    { "match": "mymod:steam_engine", "skill": "engineering", "xp": 3.0, "flags": ["machine"] }
  ],
  "kill": [
    { "match": "mymod:pet_dog", "skill": "none" }
  ],
  "kill_bonus": [
    { "match": "mymod:titan", "xp": { "base": 6.0, "per_health": 0.02, "max": 40.0 } }
  ],
  "boss": [
    { "match": "mymod:titan", "boss": true }
  ],
  "structure": [
    { "match": "mymod:sunken_temple", "xp": 80, "flags": ["grand"] }
  ],
  "biome": [
    { "match": "mymod:glow_forest", "xp": 40 }
  ],
  "first_time": [
    { "match": "mymod:flawless_gem_block", "kind": "block", "multiplier": 4.0 }
  ]
}
```

`/proficiency xpsources <id>` shows which rule won for an id and why the others lost.
`/proficiency xpaudit <mod>` lists what a mod adds that pays nothing or the wrong skill. The
Reclamation pack's own datapack is in `forge-1.20.1/packs/reclamation/datapack/`.

The held-block ability key on a client works out the build skill from the shipped defaults, because
clients never receive the datapack. That changes only which ability a held block fires, never the XP.

## Balance telemetry

Every server counts, in memory, what its players earn and appends it to
`<world>/proficiency/telemetry/YYYY-MM-DD.jsonl` every five minutes and when the server stops. It
is on by default, sends no packets and does no disk work per grant. `telemetry.retentionDays`
(60) deletes old files at start.

`tools/balance_report.py` turns the files into one HTML page and a markdown summary: XP per active
hour, source mix, dead and runaway skills, time to level, proc rate against the configured chance
and death cost. `--anon` hides player names.

## Works with

Nothing is a hard dependency. With these installed the mod picks them up: Create, Applied
Energistics 2 and storage mods (Engineering), Ars Nouveau (Spellcasting), Farmer's Delight
(Cooking), Twilight Forest, the Aether and boss mods (Beastslaying), furniture mods such as
Supplementaries and Chipped (Decorating), Falling Tree (Timber stands down) and Right Click Harvest.

## Build

The three loaders build from this one tree. `core/` is pure Java shared by every jar, `common/` is
the Minecraft 1.21.1 code NeoForge and Fabric share, and `neoforge/`, `fabric/` and `forge-1.20.1/`
hold what each loader needs.

```sh
./gradlew build
```

The jars are in `neoforge/build/libs/`, `fabric/build/libs/` and `forge-1.20.1/build/libs/`.
`tools/check-all.sh` builds all three, runs every unit test and then the three in-world GameTest
suites one after another. `./gradlew :neoforge:runGameTestServer`, `:fabric:runGametest` and
`:forge-1.20.1:runGameTestServer` run one suite. Java 21 is needed, and the Forge 1.20.1 project
also needs a Java 17 that Gradle can find.

The branches `fabric-1.21.1` and `forge-1.20.1` hold the old per-loader releases up to 1.2.0 and
are no longer updated. Everything from 1.3.0 on is on `main`.

## License

MIT, see [LICENSE](LICENSE).
