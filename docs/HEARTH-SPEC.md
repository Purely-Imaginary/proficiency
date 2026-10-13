# Hearth: a home you build, warm up in, set out from and defend (spec, not built)

Status 2026-10-09: designed with the user in conversation, then reviewed adversarially against the
code the same day; this version folds in that review. Nothing is implemented. Numbers marked *tune*
are first guesses, to be set from balance telemetry (`tools/balance_report.py`), not from this page.

## What it is, and what it is for

The **Hearth** is a campfire a group turns into the heart of its base, stage by stage, beacon-style.
While players earn skill XP near it, it gathers **charge** (a mirror of that XP, never a cut) into
per-boon pools. Charged pools give **boons** to players who have warmed up by it: slower hunger,
regen and temp HP, less mob damage, faster stations, faster rested XP. **Comfort** counts the
distinct kinds of furniture *indoors* around it and gates the strongest effects.

The three things it must make fun (user, 2026-10-09), and the mechanic that carries each:

| Goal | Carried by |
|---|---|
| **A reason to build a home, not a lawn base** | the six-stage build; Comfort counting distinct kinds that are *under a roof*; trophies on the walls |
| **Prepare for the adventure**, so home and expedition form a loop | Warmth (a full rest before leaving), temp HP that lasts until spent, Tales paid in on the way back |
| **Reward defending the home** | Sieges: when the whole group is home and rested, the Hearth draws an invasion, announced a day ahead |

It is a core feature behind a server switch (`hearth.enabled`, default true), not a separate mod,
because it hooks the XP grant path (`SkillService`) and sits next to the survival bonus, rested XP
and teaching (docs/RESTED-AND-TEACHING.md).

## Decisions already made (user, 2026-10-09)

| Decision | Rejected alternative, and why |
|---|---|
| The Hearth **mirrors** XP: players keep 100%, the Hearth gains 10% of it *(tune)* on top | Skimming XP from players: it reads as a tax and nobody builds one |
| Boons are fed by **what the group does there**: each boon has its own XP sources | One generic charge: loses the "a mining base gets miner perks" identity |
| **Masonry → Fortified**, **Decorating → Comfort regen**; no combat boon | A combat buff from a base: invites mob-farm abuse |
| **Defending the home feeds every pool** ("defending the home is heroic"); in practice: siege kills | Combat feeding nothing: a fight for the base is exactly the group play to reward |
| **Built in the world, never crafted**: a campfire on a platform, lit; six stages, beacon-like | A crafted block: you would never see the home grow |
| **Six stages**, build ladder and boon tiers one-to-one ("more stages if possible") | Four: too few steps between a firepit and a finished hall |
| **Comfort counts distinct kinds** with a visible ceiling (n/12); the top regen tier needs all 12 | Counting quantity: ten candles would beat a furnished room |
| **Death stays as painful as it is**: no boon ever softens the XP-bar, survival-bonus or rested wipe | Any "keep X on death" boon |
| Core feature with a switch; boons, comfort kinds, stages and siege numbers are **datapack data** | A separate module: it needs XP-pipeline hooks only core has |
| **Temp HP: 10% of max health per tier** (60% at VI), absorption that **lasts until spent** | Flat hearts (too low, and max health moves with Endurance); a timer |
| **Warmth**: boons ramp up over a minute in range; sleeping in a bed there fills it at once | Instant boons: stepping through the door would equal coming home |
| **The survival streak does not grow at home**, any Hearth, even an uncharged one | Letting it grow at home: it is a survival bonus, not a sit-at-home bonus |
| **Sieges**: a *condition* (whole group home at full Warmth) + cooldown + a dice roll, ~1 per 10 h of play; a crafted horn can call one; announced a day ahead with a left-side countdown. **Random sieges stay off until the first Siege Horn is blown at that Hearth** (user, 2026-10-09) | A fixed schedule; an un-announced attack while people are AFK |
| **No faster rested refill** at home (user, 2026-10-09); Homecoming gives readiness instead | A rested-fill boon: it would halve the rebuild after a death's rested wipe |
| **Temp HP is a portable buffer on purpose**: fill up at home, carry it into the boss fight (user, 2026-10-09: "exactly like that") | A timer or a boss cap: preparing at home is the point |
| **Set out / Provisioned** and Homecoming's **readiness** boon | — |
| **Character gates the ceiling from stage III up**, a different bar per stage | Gating only stage VI: variety should matter all the way up |
| **Hearth Chronicle**: a flat bonus for writing a tale at all; `/mcjournal` rates it later and pays more for a good one | The mod judging quality itself: it cannot |
| **Tales** pay expedition XP into the Hearth on the way home, lost on death; **Feast** gathers the group | Nothing pulling you home |
| **Range is a cylinder**, so the mine under the base is not home (numbers to tune) | A sphere: a branch mine 20 blocks down counted as home |
| **Siege "everyone"** = every online, active player in survival or adventure: a friend out exploring blocks the roll until they come home | Only players in range: the siege would not need the whole group |
| **Offerings are lost** when a Hearth is broken | Refunding them: moving a base should cost something |
| Opt-in **Hearth tracker** on the middle of the right edge, animated with the XP-gain dots | Progress only in the right-click panel |

## Building and upgrading

A campfire becomes a Hearth when it stands on its stage-I design and is lit with an offering. What is
built around it sets the **ceiling**: the highest tier any pool can reach. Charge and Comfort decide
the tier inside that ceiling.

What a stage is made of is **docs/HEARTH-DESIGNS-SPEC.md**: three lanes (Longhouse, Forge, Shrine) of six
9x9 stage designs, in one data file read on the server and on every client
(`data/proficiency/hearth_designs/designs.json`). Wood and stone are required by type (any kind the
player likes, by block tag), metals are named. A Hearth's built stage is the highest stage that *any*
lane matches in full, so a group may switch lanes when it rebuilds. The check box is **9x9x7**,
centred on the campfire.

| Ceiling | Offering (right-click, consumed) |
|---|---|
| **I, Firepit** | flint and steel (lights it; durability only) |
| **II, Hearth** | a copper block |
| **III, Homestead** | an iron block |
| **IV, Hall** | a gold block |
| **V, Gathering Place** | a diamond block |
| **VI, Heart of the Home** | a heart of the sea |

- Sieges need ceiling **III**, so a new player's Firepit is never attacked.
- An offering is taken only when that layer is complete. A wrong or missing block gets a chat line
  naming the first wrong cell of the lane closest to complete: its role and where it is from the fire
  ("stone wanted 2 north, 1 east, 1 up of the fire (23 blocks to go)").
- **Breaking a layer**: the ceiling drops; charge is **held, never cut**, and the pools show capped
  until it is rebuilt. Offerings are not owed again (the block entity remembers the highest one paid).
- **Spacing**, checked when lighting, per dimension: no other Hearth's campfire within **64 blocks** horizontally
  (`hearth.minSpacing`, default `2 x range + 16`), so ranges never overlap and "in range" always means
  one Hearth. A refused lighting names the distance to the other one.
- **Breaking the Hearth** takes a 3 s sneak-hold (no accidental pickaxe swing) and goes through the
  normal block-break event, so claim mods (FTB Chunks) protect it. It drops a plain
  campfire and loses offerings and charge, so moving a base is a real decision. A chat line to
  everyone who fed it names who broke it. No ownership: this is a co-op mod.
- Lighting animation: the campfire flares and becomes the Hearth block (a campfire-like model with a
  stone collar). Its look grows with the ceiling: ember glow, sparks, a taller flame, smoke out of
  the roof hole, a soft chime when the bell stage is sealed, a slow golden halo at VI.
- A lodestone at VI keeps its vanilla job: a compass bound to it points home.
- Discoverability: the first campfire a player places on a 3x3 of hearth stone gets the action-bar
  hint "Light it with flint and steel to make a Hearth"; the discovery banner fires on the first
  Hearth lit.
- **Range**: a **cylinder** around the campfire, radius 24, from 8 blocks below to 16 above
  *(all three tune; user, 2026-10-09)*, one meaning for gathering, boons, comfort, the streak and
  sieges. A branch mine under the base is outside it. Spacing (64) stays horizontal.
- The Hearth block entity ticks once a second (server), never per tick.

## Charge: what feeds what

Hook: **inside `SkillService.grantOnce`** (every grant path ends there: `grant`, `grantKill`,
`grantAoe`, `grantNoFirstTime`), after the existing filters and **before rested is added**: mirror
`beforeRested`, only when `play` is true and the source is not `first|...`. Mirror the paid
`amount`, not `banked` (banked is 0 for a skill with all stars; a veteran must still feed). That
makes the `rested`, `teaching`, `command` and `first-time` telemetry kinds feed nothing, as intended.

Every grant to a player in range adds `amount x mirrorShare` (10%, *tune*) to the pool listed for
that skill. Filters that already ran for free: player-placed blocks (when `placedBlocksPayXp` is off),
`SpawnOrigin` (egg/command/dispenser/bucket mobs pay 0, spawner mobs a share), fake players, the
AOE 25% share.

| Pool / boon | Fed by | Boon per tier I..VI (*tune*) |
|---|---|---|
| **Hearty** | Gathering: Mining, Woodcutting, Excavation, Farming, Fishing | hunger drains 8 / 15 / 22 / 28 / 34 / 40% slower |
| **Comfort regen** | Decorating (held 10 min, see below) | out of combat (5 s without damage) 1 heart per 14 / 12 / 10 / 8 / 6 / 4 s; temp HP 10% of max health per tier (see "Temp HP") |
| **Fortified** | Masonry (held 10 min) | damage from mobs -4 / -7 / -10 / -13 / -16 / -20%; never from players or their tamed animals, falls, fire, lava or void |
| **Workshop** | Crafting: Smithing, Cooking, Alchemy | furnaces, smokers, blast furnaces and brewing stands in range run 8 / 15 / 22 / 28 / 34 / 40% faster while a player is in range; 2 / 3 / 4 / 5 / 6 / 8% material refund at crafting tables in range, never on reversible recipes |
| **Homecoming** | Social, **Tales** and **Feasts** (see "Setting out and coming home") | **readiness**: Warmth fills in 55 / 50 / 45 / 40 / 35 / 30 s instead of 60, and Provisioned lasts 16 / 18 / 20 / 22 / 25 / 30 min instead of 15. Never touches rested XP (user, 2026-10-09) |

Movement, Expedition, Survival, Mastery (Spellcasting, Engineering) and **Homebuilder**
(docs/HOMEBUILDER-SPEC.md, the home skill, which earns a share of XP made at home) feed nothing in
range; packs can map the others. Movement and Expedition XP earned **away** from home come back as Tales.

**Masonry and Decorating are held 10 minutes** *(tune)*. Their mirrored charge goes into a pending
list keyed by block position and is credited only if the placed block is still there 10 minutes
later. Today placing pays and breaking a placed block pays nothing, but a block can be placed again
one spot over, and the paid-position memory (`ExpansionEvents` `PAID_PLACEMENTS`) lasts 10 minutes
and is wiped on logout. Holding the charge makes the "shuffle one block" farm worth 0 to the Hearth
without touching how the skills themselves pay.

**Reversible recipes** (a recipe whose output crafts back into its inputs: storage blocks, nuggets,
ingots) are excluded from the Workshop refund. The set is computed at datapack reload.

### Combat: defending the home

Combat XP in range feeds the Hearth **only from kills of siege mobs** (and of raiders while a vanilla
`Raid` is active within range): `combatShare` 0.03 *(tune)* of the kill XP to **every** pool, within
the hourly cap. Hits, blocking, damage taken (Endurance), role XP (`guardian_`, `charger_`,
`tactician_` sources) and ordinary kills feed nothing.

Why so narrow: the earlier "a mob that targeted someone in range" filter let both classic farms
through (a caged villager as bait; a dark-room grinder finished with a sword), and per-hit and
damage-taken XP never passes a kill filter at all (falling off a tower pays Endurance). Sieges make
defence happen in normal play, so the farmable rules are not needed. Siege mobs carry a new
`SpawnOrigin` kind, `siege`.

### Tiers, cap and decay

- Thresholds *(tune)*: I = 200, II = 600, III = 1 400, IV = 2 800, V = 5 000, VI = 8 000 charge, which
  is 2 000 / 6 000 / 14 000 / 28 000 / 50 000 / 80 000 skill XP earned in range at 10%.
- **Hourly cap**: at most 300 charge per pool per Hearth in any rolling 60 minutes *(tune)*; VI takes ~27 h of capped
  play per pool. Siege victory bursts sit outside the cap.
- **Comfort gates**: Hearty, Fortified and Workshop need Comfort 8 for V and 10 for VI. Comfort regen
  and Homecoming use the stricter `floor(comfort / 2)` cap (VI needs all 12).
- **Decay** is applied lazily: the block entity stores the game time someone was last there (an
  online, non-AFK player in survival or adventure, in range) and, when its chunk loads or ticks,
  applies 2% of charge per hour of game time since *(tune)*, never below the current tier's floor in
  the first 24 h after that visit. Server downtime is not counted (game time stops). An AFK alt or a
  spectator does not hold decay off.

## Warmth: a full rest before you set out

Every player has a **Warmth** value from 0 to 100%. Player-side boons work at `tier value x Warmth`.

- **In range**: 0 to 100% in **60 s** *(tune)*, linear. It does not rise during combat (5 s since
  damage), though you keep what you have.
- **Sleeping**: a real sleep in a bed in range (night or thunderstorm, `sleepCounter >= 100`, the
  point vanilla counts you as asleep) sets Warmth to 100% at once. Clicking a bed and getting up
  does nothing. No daytime naps: vanilla refuses them, and forcing one would skip the
  night for everyone.
- **Out of range**: drains at twice the rate (0 in 30 s).
- Scaled by Warmth: Hearty, Comfort regen, Fortified. Not scaled: Workshop (furnaces are
  not people). Temp HP refills **only at 100%**.
- Not scaled, by design: the streak pause and the charge mirror start the moment you are in range.
- Saved with the player; a relog at home keeps it.
- **Owned by the per-player loop**, not the Hearth: the Hearth's tick only writes
  `inRangeOf = pos, until = gameTime + 40` on each player it sees. The existing once-a-second player
  loop (`PlayerLifecycleEvents`) reads that to rise or drain Warmth, pause the streak, remove the
  absorption modifier and fire the edge cues. So a Hearth broken, unloaded or switched off while you
  stand by it, a `/tp` or a dimension change, all end "in range" within 2 s instead of leaving the
  streak paused forever.

## Temp HP (Comfort regen, every tier)

At full health, at 100% Warmth, out of combat and in range, Comfort regen keeps ticking at its own
rate and adds **absorption** instead, up to **10% of current max health per tier**: 10 / 20 / 30 / 40 /
50 / 60% for I..VI *(tune)*. At 20 max health tier VI is 12 points (6 gold hearts), filled in ~24 s at
the tier VI rate; at 40 max health it is 24 points.

- The tier used is the Comfort-capped regen tier (`floor(comfort / 2)`): 60% needs all 12 kinds.
- It **stays until damage spends it**: no timer, leaving range, travelling and logging out all keep
  it. Only home refills it. Death clears it like any absorption; abuse check 6 holds.
- **No stacking**: the Hearth tops up to `max(current absorption, its cap)`, never adding to Charger's
  first blood, Guardian, golden apples or the Aegis / Indomitable talent caps.
- 1.21.1: a persistent `max_absorption` modifier (`proficiency:hearth_absorption`) sized to the
  Hearth's cap. It is **only ever grown** while absorption is above 0 (shrinking it would clamp the
  absorption through vanilla's attribute update and break "never clawed back") and is **removed by
  the player loop** once absorption reaches 0. 1.20.1 has no cap attribute: set the amount directly
  and keep the Hearth's share in player data.
- Side effect, accepted: hits soaked by absorption pay no Endurance XP (`EnduranceEvents`), so temp
  HP trims Endurance XP a little. Telemetry watches it.
- Decorating's **Cozy Room** (vanilla Regeneration from 10 decorative blocks nearby) still works and
  stacks with Comfort regen for health; temp HP fills only from the Hearth's own tick.

## The survival streak does not grow at home

In range of any Hearth (any stage or charge), the survival streak **does not advance**. Stacks are
kept and still multiply XP earned at the base. Death at home still wipes it.

- Hook: `SurvivalStreak.tick` returns early when the player-loop flag says "in range".
- HUD: the streak badge shows the hearth glyph and reads **"Stacks safe at the Hearth"** (not
  "paused": home should not feel like a penalty box). `minutesToNext` reads "at the Hearth".

## Setting out and coming home

- **Set out** (user, 2026-10-09): leaving range at 100% Warmth plays a short cue, the action bar says "Set
  out from the Hearth", and the **Provisioned** badge carries the Hearty value with you for 15 min
  *(tune; Homecoming's readiness stretches it to 30)*, fading linearly. Not a combat boon; it makes leaving a ready state.
- **Tales**: Movement and Expedition XP you earn **away** from home is banked as Tales (5% of it,
  *tune*), shown on the HUD as a small scroll count. Coming back into range at your Hearth pays the
  Tales into **Homecoming** (and, with `talesFeedAll`, a share to every pool), with a chat line: "You
  told of 3 200 blocks walked and a stronghold". A death away from home **loses the banked Tales**:
  stakes, not a softener. Tales also gives a solo player a way to charge Homecoming (Social only pays
  in company, and the mod's rule is that one person can finish everything alone).
- **Telling the tale** (user, 2026-10-09): a **Hearth Chronicle**, a writable book on a lectern
  inside range (the lectern also counts for the `books` comfort kind).
  - **Flat bonus, in game**: with unpaid Tales, writing anything at all on a new page adds **+25%**
    *(tune)* to the Tales paid, once per homecoming. No word minimum, no quality check: the mod
    cannot judge a story.
  - **Rated bonus, later, by `/mcjournal`**: each page is saved with its author, game time, the Tales
    it went with and a page id, in `<world>/proficiency/chronicle.jsonl`. When `/mcjournal` runs for
    that world, it reads new pages, rates each 0-5 against the world's activity feed for that play
    day (is it specific, is it true to what happened, does it have a voice; an invented exploit
    rates 0), and sends `/proficiency hearth tale <page id> <stars>` over RCON. The mod pays a
    **charge burst of stars x 5%** *(tune)* of that page's Tales into Homecoming, outside the hourly
    cap, once per page, and tells the author in chat next login: "Your tale of the stronghold was
    retold at the Hearth: 4/5". Telemetry kind `tale_rating`, not `command`, so it is not filtered out.
  - The pages land in the world journal next to the play-day digest.
  - Follow-up outside the mod: the `/mcjournal` skill needs a "rate new Chronicle pages" step.

## Sieges: the home under attack

### When a siege can happen

A siege is not scheduled; it is a **chance that opens while a condition holds** (user, 2026-10-09).

**Off until the first horn.** A Hearth never rolls for a siege until someone has blown a **Siege
Horn** at it once (the block entity stores `sieged = true`). The group chooses when the threat
begins; a builder group that never crafts a horn is never attacked. After that first horn siege
ends, won or lost, the rolling below switches on for that Hearth for good, and the cooldown and the
pity clock start from that siege. A Hearth that is broken and lit again starts with the flag off.

The Hearth panel says so: "Quiet. Blow a Siege Horn here to call the first siege; after it, they may
come on their own." Once on: "Sieges: on, last one 6 h of play ago".

Condition, all of (once the flag is on):
0. The first horn siege at this Hearth has happened.
1. **The whole group is home and rested**: every online, non-AFK player in survival or adventure
   (any dimension) is in range of **this** Hearth at 100% Warmth. AFK uses the streak's own notion
   (no XP in the active window). Solo play: you alone are "everyone".
2. The Hearth's ceiling is **III or higher** and some pool is at tier I or higher.
3. **Cooldown**: at least 4 h of play *(tune)* since the last siege ended, and 2 in-game days.
   "Play" = server time with at least one non-AFK player online, stored in a world `SavedData`.
4. `hearth.sieges` is on and the difficulty is not peaceful.

**The roll**: once per 60 s while the condition holds. The chance grows with play time since the last
siege `H`: 0 below the cooldown, then `pMax x min(1, (H - 4) / 6)`, aiming at **one siege per ~10 h of
play** *(tune `pMax` from telemetry, since how often a group is home and rested is unknown)*. A pity
rule: at `H >= 16` the next minute the condition holds is a hit.

Sleeping fills everyone's Warmth at once, so a group bedding down is the classic moment the
condition holds. That is fine: the cooldown and the roll keep it rare.

### The Siege Horn

The only way to start sieges at a Hearth, and after that a way to call one on your own schedule. A crafted **Siege Horn** *(recipe tune: a goat horn, 2 gold ingots, 1 iron ingot)*, blown in range of a
Hearth at ceiling III+, calls a siege for the **next nightfall** (at least 2 real minutes away). It
skips the Warmth condition and the roll, needs only a short cooldown (1 h of play since the last
siege), and is consumed. For groups that want the fight on their schedule.

### Warning: a day ahead, a countdown on the left

On a hit, the siege is **due at `gameTime + 24 000`** (one in-game day, 20 real minutes; sleeping
skips `dayTime`, not `gameTime`, so beds cannot shorten the warning) and then starts at the next
dusk, like Terraria's goblin invasion. The target game time is stored in the Hearth block
entity and in the world `SavedData`, so a restart keeps it.

- Announcement to everyone who was in the condition, and to anyone who later enters range: a distant
  horn, a chat line ("Smoke from your Hearth has been seen. They come at nightfall tomorrow.").
- **Left-side siege panel**, temporary, not opt-in (client switch `hearth.siegePanel`, default on),
  at the **top left** (y about 0.06 *(tune)*), clear of the debug XP feed, which starts at y 0.25:
  a siege glyph, "Siege in 18:20" and a bar draining to zero. When the siege starts it switches to
  waves ("Wave 2/3, 7 left"), and after it "Defended!" or "The Hearth smoulders" for 10 s, then it
  goes. While counting down it also shows "Warm n/m" (who in the group is home and warm) and an arrow
  to the Hearth when you are away. Synced only to the players it concerns, never every second (a start
  time plus wave events).

### The siege itself

- At nightfall, at least one non-AFK player must be in range, or the siege **postpones one night**,
  at most twice, then fizzles with no penalty (cooldown counts as half served).
- **3 waves** spawn at the edge of range, the next when the previous is half dead *(tune)*. Size grows
  with the ceiling (III: 4 mobs a wave, VI: 12) and with each extra non-AFK player in range (+50%).
  Mix by ceiling: III zombies, skeletons, spiders; IV + pillagers; V + vindicators and witches;
  VI + an evoker. Vanilla mobs tagged `SpawnOrigin` `siege`, with one added goal, "go to the Hearth"
  (Goal/GoalSelector exist on 1.21.1 and Forge 1.20.1 alike). Not the vanilla `Raid` system: it is
  tied to villages, Bad Omen and its own wave table.
- **They come for the fire**: a siege mob next to the Hearth adds to a **douse meter**. Siege mobs
  never break blocks (no mob griefing for them, whatever the gamerule says); their creepers do no
  block damage. Sleeping is refused in range while a siege runs.
- **Defended**: every wave dead before dawn, killed by players or their pets. Rewards:
  - a **charge burst** of 10% of the next tier's threshold to every pool, outside the hourly cap;
  - a **Siege Trophy** (a wall-mounted block, its look by ceiling) that counts for the `trophy` comfort kind;
  - the **defence streak** +1, shown in the Hearth panel, with a +5% burst per streak *(cap 5)*;
  - kill XP feeds every pool through `combatShare`.
- **Doused** (meter full): the Hearth **smoulders**: boons off and Warmth 0 for everyone in range
  until someone relights it with flint and steel, and every pool loses 10% charge. The build, the
  ceiling and the death penalty are never touched.
- Dawn with mobs left: the siege ends, neither won nor lost; leftovers burn or despawn.
- Everyone leaves range mid-siege: siege mobs despawn, neither won nor lost. Nothing spawns at a
  Hearth with nobody home.
- Dying in a siege costs exactly what death always costs.

## Who feeds the Hearth (user, 2026-10-09)

The Hearth keeps a **ledger** of who added charge and from what, so a group can see who is carrying
the home.

- Stored in the block entity, per player UUID: charge added **per pool** and **per source**, where a
  source is a skill (mining, cooking, ...) or one of `siege` (kill share and the victory burst split by
  kills), `tales`, `chronicle` (flat and rated), `feast`. Two windows: **lifetime** (since this
  Hearth was lit) and **last 7 days of play** (a ring of 7 daily buckets). Bounded: ~40 sources x
  players who ever fed it, a few KB.
- Also per player: **siege kills** and **sieges defended**, for the defence streak line.
- Lost with the Hearth when it is broken, like the charge.
- **Panel tab "Hearth-keepers"**: a ranked list (this week / lifetime toggle) with each player's
  total, a small stacked bar of their share per pool in the pool colours, and their top three
  sources ("Amman: 1 240, mostly Mining, Masonry, Tales"). Clicking a pool row in the main tab
  ranks players for that pool alone.
- **Tier-up chat line** names the top feeder of that pool this week: "Hearty reached III, carried by
  Rowan's farming".
- Command: `/proficiency hearth ledger [pool] [week|lifetime]` prints the same table (any player in
  range; ops from anywhere with a position argument).
- Telemetry `hearth_charge` gains the player and source fields, so `balance_report.py` can show
  feeding per player and per source across all Hearths.
- Synced to a client only when the panel opens (one packet), never on a timer.

## Comfort: a furnished home, not a lawn

The Hearth counts the **distinct kinds** of comfort blocks in range. Each kind counts once however
many there are, and only when it is **indoors**: no sky above it (`canSeeSky` false at the block).
A flat lawn with twelve blocks on it scores 0; a roofed house with them scores 12. The Hearth
structure's own blocks never count.

| Kind (block tag `proficiency:comfort/<kind>`) | Vanilla members (packs add theirs) |
|---|---|
| `bed` | #minecraft:beds |
| `seat` | #minecraft:stairs (the vanilla chair), modded chairs, benches, sofas |
| `carpet` | #minecraft:wool_carpets, moss carpet |
| `banner` | #minecraft:banners (not the Hearth's own pillar banners) |
| `fire` | campfire, soul campfire, a fireplace of `#proficiency:comfort/fireplace` (a lit furnace does not count) |
| `light` | lantern, soul lantern, #minecraft:candles, sea lantern, glowstone |
| `plant` | #minecraft:flower_pots |
| `art` | paintings, item frames holding an item (entities) |
| `books` | bookshelf, chiseled bookshelf, lectern |
| `music` | jukebox, note block |
| `storage` | barrel, chest, decorated pot |
| `trophy` | mob heads and skulls, an armour stand wearing armour (entity), Siege Trophies |

12 kinds, all reachable on vanilla: the HUD and tooltip show **Comfort n/12** with the missing kinds
named. (The earlier `table` kind had no vanilla member, which made 12/12, regen VI and 60% temp HP
impossible on a vanilla server; modded tables now go in `storage` or `seat` by pack choice.)

- **Placed only**, using a comfort mark of its own, written on every player block placement whatever
  `placedBlocksPayXp` says (today `PlacedBlocks` writes nothing when that option is on, which would
  leave Comfort at 0). Entities (paintings, frames, armour stands) count when present; nothing tracks
  who placed them, and nothing needs to: they cannot come from natural generation inside a base.
- **Character** (user, 2026-10-09): the number of **distinct block ids** a player placed in range,
  inside or out (walls, roofs, floors, windows, paths all count), shown as **"Character n/50"**. Bulk
  filler never counts: `#proficiency:hearth/character_ignore` (dirt, cobblestone, cobbled deepslate,
  netherrack, gravel, sand, the Hearth's own structure). Each id counts once however many there are,
  like Comfort. It is a list with a visible end that rewards a varied, built house over a box of one
  material.
  - **It gates the ceiling from stage III up**: III needs Character **8**, IV **14**, V **24**, VI **40**
    (provisional, measured 2026-10-09, see below). The ceiling is `min(built stage, stage Character allows)`, so a perfect Hall in a dirt hut
    stays at II. Charge above it is held, never cut, exactly like a broken layer.
  - The offering for a stage is refused while Character is short, naming it ("Character 11/14: the
    Hall wants a home that looks lived in").
  - The panel shows Character next to Comfort, with the next stage's bar.
  - **One id per item**: a block counts as the item that places it (`block.asItem()`), so `torch` and
    `wall_torch`, `lantern` and its hanging state, are one entry, not two.
  - *(Option, if VI turns out too easy in packs)*: a tag `#proficiency:hearth/character_family` that
    folds a decorative family (Chipped's variants of one block) into one entry.
  - **Measured on real bases** (2026-10-09, cylinder r24, -8/+16 around the bed; placed-block marks
    only existed on Reclamation, the rest is all ids minus natural terrain):

    | Base | Character (estimate) | Lands at |
    |---|---|---|
    | Pandowo, Wren's camp (jungle planks, a few torches) | 7 | below III |
    | Pandowo, Amman/Rowan one-room hut (aspen, glass, beds, chests) | 12 | III |
    | Reclamation, a day-old farm hut (marks say 17; ~25 with ruins) | 17-25 | IV, V at the top |
    | Ammaniątka, Amman's moss/spruce cave base (Create, AE2, paintings) | ~50 | VI |
    | Ammaniątka main base (deepslate and dark oak compound, ~15 Chipped variants) | ~100 | VI |

    So a lived-in base sits at 12-30 and showpieces at 50-100. If VI should be rarer than the cave
    base, raise it to 48 with a max of 60, or turn on the family tag (the main base drops to ~60-70).
    Re-check after a few more days of Reclamation marks. Scripts and per-id lists:
    `tools/` in the job folder of the 2026-10-09 session (not kept; rerun from the description).
  - Counted by the same scan as Comfort, with the same comfort mark (so it works with
    `placedBlocksPayXp` on).

Packs map furniture through the tags, no code per mod. The datapacks
`forge-1.20.1/packs/reclamation` and `neoforge/packs/pandowo` ship mappings for Handcrafted, Another
Furniture, Macaw's furniture and Farmer's Delight cabinets and stoves.

**Scan cost**: the scan walks the range (~58 000 positions) when a block changes in range (place,
break, explosion events), at most every 30 s, and **every 5 min while someone is in range** to catch
what no event reports (fire, endermen, water, pistons, WorldEdit). Entities via one AABB query. The
kind set is cached. Never per tick.

## Gathering

- **Feast** (user, 2026-10-09): two or more players eating within 30 s of each other in range fill
  each other's Warmth to 100% and add a Homecoming charge of 20 *(tune)* per extra eater, once per
  in-game day per Hearth. Solo play gets nothing from it; Tales are the solo route.
- **The bell** (user, 2026-10-09: a context bell). The stage-V bell (the one in the 7x7x6 check box)
  does one thing when rung, picked by the moment, first match wins:
  1. **Alarm**, during a siege or its countdown: siege mobs in range glow for 10 s (vanilla bells do
     this for raiders), and the left-side siege panel pops up for every online player who is part of
     the siege, with the arrow home. Cooldown 30 s.
  2. **Dinner bell**, within 60 s of someone eating in range: opens a **2-minute Feast window**
     (instead of 30 s) and tells every online player "Dinner at the Hearth" in chat. Anyone who gets
     there and eats inside the window joins the Feast. Feast rules otherwise unchanged (once per
     in-game day per Hearth). Cooldown: one per Feast.
  3. **Come home**, any other time: every player in the dimension sees the Hearth's position for
     60 s (a HUD arrow, the siege panel's arrow, and a ping on the compass if they hold one bound to
     its lodestone). Anyone who heard it gets **Homeward with a 5 s channel** instead of 10 for those
     60 s (Homebuilder capstone). Cooldown 5 min.
  - The action bar names which call it was ("You ring the alarm" / "dinner" / "You call them home"),
    so the context is never a guess. Players in other dimensions get the chat line only.
  - Ringing any other bell does nothing special. A Hearth below stage V has no bell job.
  - Server cost: one event per ring; the position and the arrow are one packet each, no streaming.

## What the player sees

- **The block** glows with charge (light 6 at no charge to 15 at any tier VI), particles in the colour
  of the category feeding it most this hour.
- **Hover / right-click**: each pool with its tier, a bar to the next, active boon numbers, `Comfort
  n/12` with the missing kinds named, the Comfort the next tier needs, the ceiling and what the next
  stage needs, the defence streak, "Show range for 30 s".
- **HUD**: in range, a hearth glyph next to the survival-bonus badge that **fills with Warmth**
  (embers to a full flame, a soft crackle and glow at 100%, the same cue when a bed fills it). The
  tooltip lists active boons. Away: the Provisioned badge and the Tales scroll when they apply.
- **Hearth tracker (opt-in, `hearth.tracker`, default off)**: a narrow column at the middle of the
  right edge, in range only, fading 5 s after you leave. One row per pool with charge: glyph, roman
  tier, a thin bar, `Comfort n/12` under them; a pool held back by Comfort or the ceiling shows a
  lock and what it needs.
  - **Gain animation**: one `XpGainDots` (core) per row, in "tier units" so the wrap logic handles a
    tier-up. The mirrored share of a gain flies as dots from the skill bar to the row. `MAX_LIVE` is
    per instance today, so the tracker adds a **global budget of 6 dots** across its rows. The
    launch point is drawn in `SkillHud`; the tracker passes its own start and end points.
  - **Tier up**: `LevelUpFx` is static, one effect at a time and keyed by `Skill`, so the tracker
    gets an instance-based `TierUpFx` (flash, numeral roll-over, ring; gold at VI) instead of reusing it.
  - Respects `hud.xpDots` and `hud.levelUpFx`.
- **Showing the range** (no cost unless asked): looking at a Hearth, or holding a campfire or flint
  and steel near a hearth platform, draws a ring of client particles where the range meets the
  terrain (~75 points, a pulse every 2 s); holding them also shows other Hearths' 64-block spacing as
  a red ring. The panel's "Show range for 30 s" keeps it up. Crossing the edge: a crackle and "By the
  Hearth" / "Left the Hearth's warmth" on the action bar. `hearth.showRange` (true) turns it off;
  particles follow the Particles video setting.
- **Siege panel** on the left (see "Sieges").
- **Chat**: one line per new tier, to players in range; the tracker's tier-up replaces it for you.
- All strings in en_us + natural Polish only (user, 2026-10-09). The other 10 shipped languages get no Hearth keys and fall back to English.

## Finish and polish (user, 2026-10-09)

"I want the hearth part of the mod on par with the rest of the mod": the Hearth and Homebuilder ship
at the same finish as the 1.1.0 visual release and everything after it. UI, animations, icons, sounds,
tooltips and text are part of every lane's definition of done, not a later pass.

- **Reuse the mod's idioms, do not invent new ones.** Tier-up and stage-sealed moments follow
  `LevelUpFx` (flash, roll-over, ring; gold at VI). House Proud follows `AbilityFx` (edge glow,
  crosshair ring, cooldown sweep). The Warmth glyph lives like `StreakBadge` (fill, flare at 100%).
  Siege outcome and Tales pay-in panels follow `DeathRecapHud`. Stage sealing in the panel follows the
  `TalentTreeScreen` unlock FX. First Hearth lit, a stage sealed and a siege announced use
  `DiscoveryBanner` / `SkillToasts`. Hearthglow uses `ProcFxPlayer`. Text uses `TextFit`,
  tooltips use `TooltipDetail` (short, details behind Shift), icons go through `SkillIcons`.
- **Every element animates in and out** like its neighbours (same easing and durations); nothing
  pops in where the mod eases. Sounds on the same kinds of moments the mod already sounds.
- **Icons everywhere the mod uses icons**, 16x16 in the skill-icon style: each pool/boon, each comfort
  kind, each stage I-VI, Warmth, siege, Tales, Provisioned, Character, Comfort, ledger, bell, horn,
  trophy, Homebuilder. No text-only rows where the mod shows an icon. Paths:
  `textures/gui/hearth/pool/<hearty|regen|fortified|workshop|homecoming>.png`,
  `textures/gui/hearth/comfort/<kind>.png`, `textures/gui/hearth/stage/<1..6>.png`,
  `textures/gui/hearth/<warmth|siege|tales|provisioned|character|comfort|ledger|bell|horn|trophy>.png`.
- **Layout**: every new HUD element and screen passes `LayoutAudit` at GUI scales 1-4 and the smallest
  supported window size (docs/LAYOUT-AUDIT.md), and never overlaps the skill line, streak badge, XP feed,
  vanilla effect icons, boss bars or the held-item name.
- **Text**: every string in English and natural Polish, in the mod's voice; other languages fall back to English (user, 2026-10-09).
- **Before release**: a polish-parity pass puts each Hearth screen next to the existing screen it
  mirrors in a real client (NeoForge, Fabric, Forge), at night and day, GUI scale 2 and 4, and fixes
  every gap it finds.

## Data

`data/<ns>/proficiency/hearth/*.json`, merged like `xp_sources` (later files override, `replace`).
Everything with a number lives here:

```json
{
  "pools": {
    "hearty":     { "feeds": ["#category:gathering"], "boon": "hunger_slow", "values": [0.08, 0.15, 0.22, 0.28, 0.34, 0.40] },
    "regen":      { "feeds": ["decorating"], "hold": 600, "boon": "regen_out_of_combat", "values": [14, 12, 10, 8, 6, 4],
                    "absorptionPerTier": 0.10, "comfortCap": "half" },
    "fortified":  { "feeds": ["masonry"], "hold": 600, "boon": "mob_damage_reduction", "values": [0.04, 0.07, 0.10, 0.13, 0.16, 0.20] },
    "workshop":   { "feeds": ["#category:crafting"], "boon": "station_speed", "values": [0.08, 0.15, 0.22, 0.28, 0.34, 0.40],
                    "refund": [0.02, 0.03, 0.04, 0.05, 0.06, 0.08] },
    "homecoming": { "feeds": ["social", "#tales"], "boon": "readiness", "warmthSeconds": [55, 50, 45, 40, 35, 30],
                    "provisionedMinutes": [16, 18, 20, 22, 25, 30], "comfortCap": "half" }
  },
  "thresholds": [200, 600, 1400, 2800, 5000, 8000],
  "mirrorShare": 0.10,
  "combatShare": 0.03,
  "hourlyCap": 300,
  "comfortForTier": { "5": 8, "6": 10 },
  "decay": { "perHour": 0.02, "graceHours": 24 },
  "warmth": { "riseSeconds": 60, "drainSeconds": 30 },
  "tales": { "share": 0.05, "feeds": ["movement", "expedition"], "feedAll": false },
  "provisioned": { "minutes": 15 },
  "character": { "max": 50, "forStage": { "3": 8, "4": 14, "5": 24, "6": 40 } },
  "chronicle": { "writeBonus": 0.25, "perStar": 0.05 },
  "feast": { "windowSeconds": 30, "dinnerBellSeconds": 120, "chargePerExtraEater": 20 },
  "bell": { "alarmGlowSeconds": 10, "alarmCooldown": 30, "comeHomeSeconds": 60, "comeHomeCooldown": 300,
            "homewardChannelSeconds": 5 },
  "stages": [ { "build": "proficiency:hearth/stage/firepit", "offering": "minecraft:flint_and_steel" }, "..." ],
  "siege": {
    "requiresFirstHorn": true, "cooldownPlayHours": 4, "cooldownDays": 2, "rampHours": 6, "pMax": 0.02, "pityHours": 16,
    "hornCooldownPlayHours": 1, "postponeNights": 2,
    "waves": 3, "baseMobs": { "3": 4, "4": 6, "5": 9, "6": 12 }, "perExtraPlayer": 0.5,
    "burst": 0.10, "streakBonus": 0.05, "streakCap": 5, "dousedLoss": 0.10
  }
}
```

Boon *types* are code (hunger_slow, regen_out_of_combat, mob_damage_reduction, station_speed,
refund_chance, readiness); which pool feeds which boon, and every number, are data.

## Config

Server (`proficiency-server.toml`): `hearth.enabled` (true), `hearth.range` (24), `hearth.minSpacing`
(64), `hearth.sieges` (true).

Client (`proficiency-client.toml`, new `hearth` section): `tracker` (false), `trackerY` (0.5),
`showRange` (true), `siegePanel` (true).

## Telemetry

New kinds: `hearth_charge` (per pool, per hour), `hearth_boon` (Warmth-weighted seconds per boon per
player), `hearth_warm` (seconds to 100%, how often a bed did it), `streak_paused` (seconds at home),
`hearth_absorb` (temp HP spent outside range, split boss vs other damage), `hearth_tales` (banked,
paid, lost), `hearth_siege` (condition seconds, rolls, hits, horn calls, outcome, wave sizes,
players). `balance_report.py` gets a Hearth section: charge per hour per pool, time to each tier at
real rates, share of play time at home, siege rate per 10 h of play.

## Abuse checks (each needs a test)

1. Two Hearths within 64 blocks: the second refuses to light, naming the distance.
2. Farms charge nothing: a caged-villager zombie farm, a dark-room grinder finished by sword, a
   spawner, and per-hit, blocking and fall-damage XP in range all add 0. Only siege and raid kills feed.
3. Shuffling one Masonry block: credit is held 10 min and dropped when the block is gone; a block
   still standing after 10 min credits once.
4. Comfort from items in a chest: never. A lawn with all 12 kinds under the sky: Comfort 0. With
   `placedBlocksPayXp=true` Comfort still counts.
5. Fortified vs PvP: damage from players and from players' tamed animals is never reduced.
6. Death: no boon touches the death penalty (dying in range, or in a siege, still wipes bars, bonus,
   rested; banked Tales are lost).
7. Fake players (Create deployers etc.) feed nothing (existing guard).
8. Temp HP does not stack: Charger's absorption plus the Hearth gives max(theirs, Hearth cap). It
   survives a relog and an hour away, is gone only when spent or on death; the 1.21.1 modifier
   never shrinks while absorption is above 0 and is removed at 0.
9. Temp HP follows the Comfort-capped tier: tier VI charge with Comfort 7 gives 30%, not 60%. With
   40 max health tier VI gives 24 points; dropping back to 20 max keeps what is left.
10. Streak: no progress for 10 min at home, resumes from the same value outside; the multiplier
    still applies at home. Breaking the Hearth while standing by it ends the pause within 2 s.
11. Warmth: 0 on walking in, half at 30 s, full from a night's sleep; a daytime bed does nothing;
    10 s outside costs 20 s back; temp HP does not refill below 100%.
12. Workshop: no refund on iron ingot <-> block; furnaces in range run at normal speed with nobody home.
13. Sieges: a Hearth that never heard a horn never rolls, however long the group plays there; the
    first horn siege switches rolling on for that Hearth only; relighting a broken one turns it off.
    Never at ceiling I-II; never with nobody home (postpone, then fizzle); an AFK alt neither
    satisfies nor blocks the condition; siege mobs break no blocks; a restart mid-countdown keeps the
    time; the horn respects its 1 h cooldown; two sieges never overlap.
14. Decay: an unloaded Hearth catches up on load; an AFK alt in range does not hold decay off.
15. Character: a full Hall built in a one-material dirt hut stays at ceiling II; 200 cobblestone
    variants of nothing count 0; removing blocks drops the ceiling and holds (never cuts) charge.
16. Chronicle: one write bonus per homecoming (a second page pays nothing); a page id rates once
    (a second `/proficiency hearth tale` for it is refused); the command needs op level 2.
17. Ledger: every mirrored point lands in exactly one (player, pool, source) cell, and the cells sum to
    the pool's charge added (decay and siege losses come off the pool, not the ledger); the weekly
    window drops day 8.
18. Bell: the context order holds (alarm beats dinner beats come home); a non-Hearth bell does nothing;
    the come-home Homeward discount lasts 60 s and only for players who heard it.

## Build notes

- Logic (pools, tiers, decay, cap, comfort rules, warmth, siege roll and cooldown) in `core/` with
  unit tests; block, block entity, scans, effects and siege AI in `common/` (1.21.1), hand-copied to
  `forge-1.20.1/` like the rest.
- Hook: `SkillService.grantOnce`, see "Charge".
- Hunger: scale exhaustion (`FoodData.addExhaustion` via event/mixin) by `1 - value x Warmth`.
- Sleep: NeoForge 1.21.1's sleep event (`CanPlayerSleepEvent`/`PlayerSleepInBedEvent`, check which
  exists), Forge 1.20.1 `PlayerSleepInBedEvent`; the Fabric shim has no sleep event, so a mixin.
- Mob damage: the existing `LivingIncomingDamageEvent`, which the repo already fires on Fabric
  (`fabric/.../LivingEntityMixin`); Forge 1.20.1 `LivingHurtEvent`. Source is a `Mob`, not a player,
  and not an `OwnableEntity` with a player owner.
- Stations: furnace and brewing speed via a mixin on cook time; Hearth positions cached per chunk so
  the furnace tick looks up "in a Hearth's range" in O(1).
- Protocol bump (6 -> 7 on both lines). The tracker gets the mirrored share on the existing gain payload (pool id + amount),
  plus a Hearth-state packet on entering range and on tier change; the siege panel gets a start time
  and wave events. Nothing streams every second.

## Not decided / open (the user's calls)

- Character gates (8 / 14 / 24 / 40 of 50) are measured but provisional: re-check once Reclamation has
  a few more days of placed-block marks.

## Not done

Nothing is built. No code, no data, no lang strings, no tests exist for the Hearth yet.
