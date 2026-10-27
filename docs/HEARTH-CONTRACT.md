# Hearth contract: the shared API every lane builds on

Status 2026-10-09: written by the foundation task (lane `foundation`) and merged to `master` with
the code it describes; every lane extended it; the integration task (lane `integration`, i01) made
it the final 1.21.1 API that the Forge 1.20.1 port reads (see "Porting to Forge 1.20.1" at the end). The design is docs/HEARTH-SPEC.md and docs/HOMEBUILDER-SPEC.md; this page is
the code's shape. **Extend it additively only: add, never rename or remove.** A lane that adds a
shared surface (a hook, a payload, a data key, a field) adds a line here in the same commit.

## Package map

| Where | Package | What |
|---|---|---|
| core | `dev.amman.proficiency.hearth` | The pure model: data, rules, state, saves. No Minecraft. Compiled into all three jars (Java 17, DFU calls that exist on 1.20.1 too: no `DataResult.error(Supplier)`, no `lenientOptionalFieldOf`, no Java 21 APIs) |
| common | `dev.amman.proficiency.hearth` | Block, block entity, registries, attachment access, SavedData, hooks, events, range, reload |
| common | `dev.amman.proficiency.hearth.net` | Payloads, `HearthNet` (sends), `HearthClient`/`HearthClients` (client handler seam) |
| common | `dev.amman.proficiency.hearth.<lane>` | One package per lane: `charge`, `stages`, `comfort`, `effects`, `tales`, `sieges`, `client` |
| common | `dev.amman.proficiency.homebuilder` | The Homebuilder lane |
| neoforge | `neoforge.NeoForgeHearthNetwork`, `gametest.NeoForgeHearthGameTests` | Payload registration; the NeoForge-only sleep relay test |
| fabric | `fabric.FabricHearthNetwork`, `fabric.client.FabricHearthClientNetwork` | Payload types and receivers |
| forge-1.20.1 | the same packages, hand-copied | The foundation since ff01 (registries, block entity, capability, SavedData, hooks, payloads, config, data); the rest in three Forge lanes: docs/HEARTH-FORGE-CONTRACT.md |

A lane puts new pure code in core under `dev.amman.proficiency.hearth.<lane>` (or a new file in
`hearth`), and Minecraft code in its common package.

## Core API (pure, unit tested in `core/src/test/.../hearth`)

| Class | Use |
|---|---|
| `HearthPool` | `HEARTY, REGEN, FORTIFIED, WORKSHOP, HOMECOMING`; `id()`, `translationKey()` (`proficiency.hearth.pool.<id>`), `byId`, `byIndex`. The ordinal is the save and wire index: append only |
| `BoonType` | `hunger_slow, regen_out_of_combat, mob_damage_reduction, station_speed, refund_chance, readiness` |
| `HearthData` | The datapack model (record). `pool(p)` -> `PoolSpec` (`value(tier)`, `refundAt(tier)`, `holdSeconds`, `absorptionPerTier`, `comfortCap`, `warmthSeconds`, `provisionedMinutes`), `poolFor(Skill)` (null = feeds nothing), `talesPool()`, `feastPool()`, `poolWithBoon(b)`, `threshold(tier)`, `comfortForTier(tier)`, `decay()`, `warmth()`, `combatSeconds()`, `tales()` (`banks(Skill)`), `provisioned()`, `character()` (`forStage(s)`), `chronicle()`, `feast()`, `bell()`, `stages()`, `comfort()` (`kinds()`, `max()`), `siege()`. `DEFAULTS`, `TICKS_PER_HOUR` (72 000), `TICKS_PER_DAY` |
| `Hearths` | `data()`: the live `HearthData` (installed by the reload; `DEFAULTS` in unit tests) |
| `HearthDataLoader` | `ordered(files)` (sorts into `ORDER`: `DEFAULT_FILE` first, then the rest of `proficiency:`, then other namespaces), `load(files)` (merges in the order given), `parse(name, text)` -> `Result(data, files, skippedFiles, messages)` |
| `HearthRules` | `chargeTier`, `progress`, `characterStage`, `ceiling(built, character, d)`, `comfortCap(pool, comfort, d)`, `comfortNeeded`, `effectiveTier`, `lock(pool, charge, ceiling, comfort, d)` and `lock(pool, charge, builtStage, character, comfort, d)` (`Lock.NONE/CEILING/COMFORT/CHARACTER`: `CEILING` = build the next stage, `CHARACTER` = the next stage's Character is missing, which comes first because the offering is refused without it), `ceilingLock`, `need(lock, pool, tier, ceiling, d)`, `boonValue`, `warmthRiseSeconds(readinessTier, d)`, `provisionedMinutes(readinessTier, d)`, `light(stage, smouldering)`, `roman(tier)`; integration: `chargeLight(maxTier, smouldering)` (the block's light: 6 with no pool at a tier, 15 at any tier VI, 3 smouldering), `feedingMost(state, gameTime)` (the pool whose hourly cap took the most in the rolling hour, or null; bursts outside the cap do not count) |
| `HourlyCap` | Rolling 60 game minutes: `take(now, wanted, cap)` returns what fits, `room`, `used` |
| `HearthDecay` | `apply(charge, now, lastVisited, lastApplied, d)`: compounding, tier floor held for `graceHours` after the visit |
| `WarmthMath` | `step(w, inRange, inCombat, seconds, rise, drain)`, `isFull`, `withSpeedBonus`, `ratePerSecond`, `inCombat(lastDamage, now, combatSeconds)`, `provisionedUntil`, `provisioned(strength, from, until, now)` |
| `TempHp` | `cap(maxHealth, tier, perTier)`, `next(absorption, cap)` (one heart up, never down), `modifier(current, absorption, cap)` (only grows, 0 when absorption is gone) |
| `HoldQueue` | `add(pos, Held)`, `drop(pos)` (block broken), `takeDue(now)` (returns at once before `nextDue()`, the earliest due tick, so a long queue is not walked every second), `pending(pool)` |
| `HearthLedger` | `add(player, name, pool, source, amount, day)`, `addSiegeKill`, `addSiegeDefended`, `total`, `grandTotal`, `sources`, `ranking`, `topFeeder`; sources `SIEGE`, `TALES`, `CHRONICLE`, `FEAST` or a skill id; `Window.WEEK/LIFETIME`. Pass `RestedMath.today()` as the day |
| `TalesMath` | `banked(skill, xp, storytellerBonus, d)`, `payout(tales, d)` (pool -> amount; `feedAll` splits evenly), `writeBonus(lastPaid, d)`, `ratedBurst(pageTales, stars, d)`; tales lane adds `MIN_PAYOUT` (1.0), `Credit` (`add(pool, amount)` -> what went in), `settle(tales, d, credit)` -> `Settlement(added, paid, kept)` (one credit call per pool of the payout; the refused part is `kept`), `settle(state, tales, now, d)` (the same straight into a state's capped pools: tests), `fits(...)` (what `settle` would let in, no change), `burst(amount, d, credit)` and `burst(state, amount, d)` (outside the cap, split like a payout). The game side always passes `HearthCharge.credit` as the `Credit` |
| `ChronicleBook` | `write(author, hearthKey, tales, time)` -> `Page` with an id, `rate(id, stars)` (empty when unknown or already rated), `page(id)`. A saved author that does not parse loads as `HearthState.NOBODY`, never an exception; tales lane: `Page` also has `name`, `title`, `told` (all optional in the save), `write(author, name, hearthKey, tales, time, title)`, `markTold(id)`, `unannouncedFor(author)`; `Page.unannounced()` = rated and not yet told |
| `FeastWindow` | `onEat(player, now, gameDay, d)` -> `Outcome(STARTED/JOINED/NONE, warmed, charge)`, `openDinner`, `someoneAteWithin`, `feastedOn(day)`. The day a Feast starts on is spent; while its window is open (also past midnight) eaters only join it |
| `SiegeRules` | `Facts` + `conditionHolds`, `cooldownServed`, `chance(H)`, `roll(H, random)`, `hornAllowed`, `rolledDue`, `hornDue`, `isNight`, `startsNow(now, due, nightNow, nightBefore)`, `atNightfall` (`START/POSTPONE/FIZZLE`), `endedAt(playNow, gameNow)` / `fizzledEnd(playNow, gameNow, s)` -> `SiegeEnd(playTicks, gameTicks)` (both cooldown clocks, in ticks; a fizzle counts half of each served) for `HearthState.setLastSiegeEnd`, `waveSize`, `nextWaveDue`, `mobsFor`, `streakAfter`, `burst(charge, streak, d)`, `doused`; `Phase.IDLE/COUNTDOWN/ACTIVE`, `Outcome` |
| `BellRules` | `pick(stage, hearthBell, siegeOrWarning, ateRecently)` -> `Call.ALARM/DINNER/COME_HOME/NONE`, `ring(...)` -> `Ring(call, ready)` with the cooldowns, `inBox(dx, dy, dz)` (the bell is inside the 7x7 check box: `BOX_RADIUS` 3, `BOX_BELOW` 1, `BOX_ABOVE` 4; tales lane) |
| `HearthState` | One Hearth (see below), `CODEC` |
| `PlayerHearth` | One player (see below), `CODEC` |
| `TaleTrip` | (tales lane) a trip away: `kindOf(source)` (a grant source that names a place: `structure.`, `dimension.`, `biome.`), `highlights(places, max)` (structures, then dimensions, then biomes), `blocks(startCm, nowCm)`, `group(n)` ("3 200"), `charge(amount)` |
| `ChronicleText` | (tales lane) a book page: `clean`, `blank`, `title`, `hash`, `fresh(filedHashes, pages)` (pages the Chronicle has not filed), `hashes(pages)`, `jsonLine(...)` (the `chronicle.jsonl` line), `iso(ms)` |
| `HearthKey` | (tales lane) `parse("dimension\|x,y,z")` -> `(dimension, x, y, z)`, `packed()` |
| `HearthPos` | `pack(x, y, z)` = `BlockPos.asLong`, `of(long)`, `unpackX/Y/Z(long)` (no allocation), `key()` ("x,y,z") |
| `HearthGeometry` | `Range(radius, below, above)` (`DEFAULT` 24/8/16, `contains`), `inRange`, `horizontalDistance`, `chunksCovered` |
| `HearthIndexStore` | The per-dimension set + per-chunk lookup, packed longs, no allocation on lookups: `hearthAt(x, y, z, range)`, `tooClose(pos, minSpacing[, skip])` (the one spacing check), both returning `NONE` (`Long.MIN_VALUE`, outside every world border) for none |
| `stages.HearthStructure` (stages) | The build, layer by layer, as offsets from the campfire (x east, y up, z south): `check(probe)` -> `Check(built, problem)` (layers standing in a row from I, the first problem above them), `firstProblem(probe, upTo)`, `layer(probe, stage)`, `stage(built, highestOffering)` (the built stage: never above the offerings). `Problem(stage, kind, found, needed, where)` with `Kind` (`PLATFORM, RING, PILLAR, ROOF, SMOKE_HOLE, LANTERNS, BELL, BANNER, HEART`) and `Dir` (8 compass points + `MIDDLE`, `id()`). `Probe` is the world (the common side reads tags). Geometry for other lanes: `inBox(dx, dy, dz)` (the 7x7x6 box, y -1..4), `isPart(dx, dy, dz)` (the structure's fixed positions: the fire, the 5x5 platform, pillars, roof ring, lantern and banner spots; Comfort and Character skip these). Unit tested: `HearthStructureTest` |
| `charge.ChargeRules` | Charge lane. `route(skill, source, play, firstTime)` -> `Route.NONE/POOL/KILL` (play only, never `first\|`; a `kill\|` line takes `KILL` whatever the skill; Combat skills never feed through the mapping; Homebuilder never feeds at all: `neverFeeds(skill)` / `neverFeeds(skillId)`, `HOMEBUILDER` = `Skill.HOMEBUILDER.id()`), `pool(skill, d)`, `mirror(beforeRested, d)`, `combat(killXp, d)`, `siegeFeeds(siegeMob, hearthUnderSiege, victimInRange)`, `raidFeeds` (the victim was a raid's member, not a raider near one), `holdDue`, `stillStands`, `split(total, weights)` (sums exactly), `rose`, `lowerHighWater`, `effectiveTiers`, `chargeTiers`, `decayStep(state, now, someoneHome, force, d)` (true only when a pool's charge changed; the clocks move either way and `ChargeTick` saves clock-only moves at most once a minute); `COMBAT_SOURCE` (= `SIEGE`, raids too), `CAPPED_SOURCE` (`"capped"`), `MAX_HOLDS` (2048) |
| `charge.KeeperRows` | The Hearth-keepers table from a ledger: `of(ledger, pool or null, window, limit)` -> `Row(player, name, total, perPool[], sources, siegeKills, siegesDefended)`, `row`, `everyone(ledger, limit)`, `top(ledger, pool)` -> `Top(player, name, total, source)` (the week's top feeder), `cells(perPool, n)` (a stacked bar) |
| `charge.PoolColors` | `argb(pool)`, `rgb(pool)`: one colour per pool for stacked bars (the chat ledger uses it; the panel tab should too) |
| `skill.ExhaustionMath` (integration, outside the `hearth` package) | `keep(shares...)` (the product of every watcher's share of a climb, each clamped 0..1), `scaleClimb(before, now, keep)` (what to write back; a drop changes nothing); fix round i01y: `ROLL_OVER` (4.0, what vanilla's food tick takes above it), `afterRollOver(exhaustion)` (the value the tick leaves) and `refundTaken(exhaustion, amount, keep)` (Long Haul's refund scaled by the shares, never below zero). `hearth.effects.EffectsMath.scaleClimb` calls `scaleClimb` and is deprecated (kept, the contract never loses an entry) |

### `HearthState` (the block entity's data)

Charge: `charge(pool)`, `setCharge`, `addCapped(pool, amount, now, d)` (through the hourly cap; returns
what went in), `addUncapped` (bursts), `cap(pool)`. Derived: `chargeTier`, `ceiling(d)`,
`effectiveTier(pool, d)` (0 while smouldering), `lock(pool, d)` (tells `CEILING` from `CHARACTER`), `need(pool, d)` (what the panel row shows), `maxEffectiveTier`. Clocks:
`tickDecay(now, someoneHome, d)`, `onLit(now, playNow)`, `lastVisited`, `lastDecay`, `litAt`. Build:
`stage`/`setStage` (built stage 0..6), `highestOffering`, `smouldering`. Cache: `comfort()`,
`setComfortKinds(set)`, `comfortKinds`, `markComfortSeen(kind)` (true the first time), `character`,
`setCharacter`, `markCharacterSeen(id)`, `lastScan`. Sieges: `sieged` (first horn heard),
`siegePhase`, `siegeDue`, `siegeByHorn`, `postponed`, `wave`, `defenceStreak`, `douse`,
`lastSiegeEndPlay` (play ticks), `lastSiegeEndTick` (game ticks), `setLastSiegeEnd(SiegeEnd)` (both at once), `nightBefore`, `playHoursSinceSiege(playNow)`,
`ticksSinceSiege(now)`. Bell: `lastAlarm`, `lastComeHome`. Parts: `ledger()`, `holds()`, `feast()`.
`NEVER` = `Long.MIN_VALUE`. `siegeDue` is the one source of truth for a siege's due time; the world data only mirrors it (below). `NOBODY` = the all-zero UUID a bad saved UUID reads as.

### `PlayerHearth` (the player attachment `proficiency:hearth`)

In range: `seenBy(hearthPos, dimension, now)` (the Hearth tick; valid 40 ticks), `inRange(dim, now)`,
`inRangePos`, `clearInRange`, `wasHome`/`setWasHome` (edge cues). `warmth`/`setWarmth`. Tales:
`tales`, `addTales`, `takeTales`, `notePaid(amount)`, `chronicleOpen`, `takeChronicleBase()` (once).
Provisioned: `provision(strength, from, until)`, `provisionedAt(now)`. Temp HP: `absorbShare` (1.20.1),
`absorbModifier` (1.21.1 modifier size). Times: `lastDamage`, `lastMeal`, `lastFeast`,
`heardBell(pos, dim, until)` / `heardBell(now)`. Homebuilder: `homewardDay`, `magnetOff`,
`markMilestone(key)` / `hasMilestone`. Free extras: `mark(key, fallback)`/`setMark`, `markKeys()` (a copy),
`flag`/`setFlag`, keys named `<lane>.<what>` (`flags()` lists the flags that are on, `clearFlags(prefix)`). Life cycle: `clearOnDeath()` (Tales, Warmth, open Chronicle bonus,
Provisioned, temp-HP share, in-range, combat; returns the Tales lost), `copyFrom`.

## Common API

| Class | Use |
|---|---|
| `HearthBlocks` | `HEARTH` (block), `HEARTH_ENTITY` (block entity type), `SIEGE_TROPHY`, `SIEGE_TROPHY_ITEM`, `SIEGE_HORN` |
| `HearthBlock` | `STAGE` (1..6), `SMOULDERING`; integration: `GLOW` (0..6, the highest effective tier; the light is `HearthRules.chargeLight(glow, smouldering)`) and `FEED` (0..5: the pool fed most this hour as its wire index + 1, 0 for none), both kept by `HearthLook`; light from `HearthRules.light` until the integration task. Adds itself to the index on placement and calls `HearthIndexData.forget` when replaced. No use action, no drops yet (the stages lane added both, see "Stages additions" below) |
| `HearthBlockEntity` | `state()`, `setState`, `laneData(lane)` (a free `CompoundTag` per lane, saved), `packedPos()`, `key()` / static `key(level, pos)` ("dimension\|x,y,z", the same for an unloaded Hearth), `setChanged()` after any change. Ticks once a second: re-indexes itself, calls `HearthHooks.HEARTH_TICK`, then copies `state().siegeDue()` into the world data's mirror |
| `sieges.SiegeTrophyBlock` | `TIER` (3..6), `FACING` (the side the front looks at, back on the opposite edge, as the art draws it); hangs only on the sturdy side of a block (clicked face first, like a wall banner) and drops when that wall goes; the tier comes from the item (below). Filled by Sieges |
| `sieges.SiegeHornItem` | `use`: `SiegeService.blowHorn`; used up on success, a refusal says why on the action bar. Filled by Sieges |
| `HearthAttachments` | `of(player)` -> `PlayerHearth` |
| `HearthRange` | `range()` (config cylinder, cached: a field read, refreshed once a second), `inRange(hearthPos, pos)`, `inRange(hearthPos, Vec3)` (positions only: same dimension is the caller's part), `hearthAt(level, pos)` (index), `hearthAtPacked(level, x, y, z)` (no allocation, `HearthIndexStore.NONE` for none: per-tick callers such as a furnace), `hearthEntityAt`, `entity(level, pos)` (forgets an indexed ghost it meets), `homeOf(player)` (any Hearth that saw the player in the last 40 ticks), `homeEntity(player)`, `isHome(player)` (and not smouldering: what boons and Homebuilder mean) |
| `HearthIndexData` | Per dimension, `get(level)`: `add`, `remove`, `contains`, `hearthAt(pos)` / `hearthAt(x, y, z)` (packed), `tooClose(level, pos, minSpacing)` (the spacing check: confirms each hit in the world and forgets ghosts), static `forget(level, pos)` (a Hearth is gone: index + siege mirror), `prune(level)` (the server sweeps every 30 s), `store()` |
| `HearthWorldData` | Once per world, `get(server)`: `playTicks()`, `addPlayTicks(n)` (Sieges advances it), `chronicle()` (+ `setDirty()`), `siegeDue()` / `siegeDue(key)`: read-only mirror of every Hearth's `HearthState.siegeDue` for when its chunk is unloaded (write the state, never this) |
| `HearthReload` | Loads `data/<ns>/proficiency/hearth/*.json` at start and on every `/reload` (notices the resource manager swap), installs into `Hearths` |
| `HearthHooks` | The hooks, below |
| `HearthEvents` | The milestones, below |
| `Hearth` | `init()`: calls every lane's init once (from `Proficiency.init`), then `HearthLook.init()` last |
| `HearthLook` | Integration. The block's `GLOW` and `FEED` follow the state: `second(level, pos, hearth)` on `HEARTH_TICK` (registered last), a `setBlock` with `UPDATE_CLIENTS` only when one changed; 0 and 0 while smouldering. The client's `HearthBlockFx` tints motes by `FEED` |
| `event.ExhaustionWatch` | Integration. The one exhaustion watcher: `look(player, food)` (scale the climb since the last look, record it; harmless at any moment), called by the mixin `HearthFoodDataMixin` as `beforeFoodTick` at the head of `FoodData.tick` (looks, then records `ExhaustionMath.afterRollOver` of the value, the one vanilla is about to leave) and `afterFoodTick` at its tail (what the tick itself added: regeneration; fix round i01y, it was a `PlayerTickEvent.Post` look that saw only the net change on a tick that also took a food point, so that handler is gone and `platform/Subscribers` keeps only the logout handler); keeps `keep(player)` = Hearty (`PlayerEffects.hungerFactor`) x Night Owl (`NightwalkerEvents.nightOwlKeep`) x Lean Times (`TalentEnduranceEvents.leanTimesKeep`) of each climb. `refund(player, amount)` is Long Haul's step: it takes `amount x keep(player)` off and moves the record down by the same, so the shares multiply (before, the refund was the whole unscaled share and sprinting could lower exhaustion). `forget(uuid)` for tests. A new hunger-shaving talent adds its share here, never a watcher of its own; a new refund goes through `refund` |
| `net.HearthNet` | `sendState/Tracker/Ledger/SiegePanel/Ping/Hud(player, payload)` (each checks the channel), `queueGain(player, pool, amount, skill)` (flushed every 10 ticks), `stateOf(hearthEntity, reason)` (the one state builder), `markHudDirty(player)` / `takeHudDirty(player)`, `canReceive` |
| `net.HearthClients` | `get()`, `set(client)`: the installed `HearthClient`, a no-op until the client lane sets one |
| `stages.HearthStages` | The lane's init: `BLOCK_USE` (lighting, offerings, panel), `BLOCK_PLACED`/`BLOCK_BROKEN` (dirty marks, the campfire hint), `HEARTH_TICK` (presence, then the build), `REQUEST` (`STATE`). Fix round 2: putting a Hearth out listens on the shared `BreakEvent` itself (LOWEST, not canceled), not through `BLOCK_BROKEN`, so it tells the feeders whatever `hearth.enabled` says |
| `stages.HearthPresence` | `inRange(level, hearthPos)`: the players in the cylinder now (alive, not spectators). The tick writes `PlayerHearth.seenBy` on each and sends `hearth_state` `ENTER` on the edge; a broken Hearth clears its flags at once |
| `stages.HearthStageCheck` | `recheckNow(level, pos, hearth)`: read the build now and apply it (stage, blockstate, chat, `UPDATE` sends), for a test or a lane that cannot wait; `RECHECK_TICKS` (600: at most one check per 30 s after a block change in the box), `PERIODIC_TICKS` (2400). The blockstate's `stage` is the ceiling (1 at least), synced every second, so a Character change shows too. Fix round (s01x): a Character drop shows only after `FALL_DELAY_TICKS` (100); a fall or rise line goes out at most once per `TELL_GAP_TICKS` (600) per Hearth, folding what changed inside the gap; `second(level, pos, hearth)` runs the once-a-second part for a test. Fix round 2: a Hearth with no `lastCheck` (placed by a command, an editor or a test, not lit) is adopted in its first second: its stage counts as paid (`highestOffering` raised to it) and its build is first read at the usual times (2 minutes, or 30 s after a block change in its box), so a test's `setStage(n)` on an unbuilt Hearth holds for that long. A stage above the offerings at any check also counts as paid |
| `stages.StageBlocks` | `stageTag(data, stage)`, `isOffering(data, stage, stack)`, `offeringStage(data, stack)`, `offeringItem`, `offeringName`, `probe(level, pos, data)`, `LANTERN`/`BELL` tags. Fix round 2: the twelve ids are parsed once per installed `HearthData` (compared by identity); `offerings(data)` -> `Offerings` (`is(stage, stack)`, `stageOf(stack)`, `ids()`), also built from a list of ids |
| `stages.StageText` | Every stages line (`proficiency.hearth.stages.*`); `stageName(stage)` / `nameKey(stage)` (the stage names, for the panel too), `problem(p)`, `characterShort(n, need)`. `sink` (default `PLAYER`) is where lines go: a GameTest swaps it to read them |
| `stages.HearthBreaking` | `HOLD_TICKS` (60), `destroyProgress(player)` (1/60 a tick while sneaking, else 0: the 3 s sneak-hold as the block's break speed, identical on both sides). Fix round (s01x): `destroyProgress(player)` is the client's rule; the block calls `destroyProgress(player, pos)`, which on the server reads the ticks the player really sneaked since the swing began (counted once a server tick, `sneakTicks(player, pos)`), so sneaking only for the last tick does not break it |
| `stages.HearthOfferings` | `answers(stack)`: whether the server answers a click on a Hearth with this stack (the client-side `HearthBlock.useItemOn` swallows those). Fix round (s01x): the panel click does not send the ledger, because the client panel asks with `REQUEST LEDGER` as it opens (client lane) and the charge lane answers; `panelSender` is the test seam for the `PANEL` send; lighting and offerings also need `level.mayInteract`. Fix round 2: `serverOfferings(ids)` / `forgetServerOfferings()`: the client keeps the server's six offerings from the state payload (`HearthView` calls them on a state and on leaving), and `answers` reads those before its own data, which on a dedicated server's client is the shipped defaults |
| `charge.HearthCharge` | **The one way to add charge**: `credit(level, hearth, player, pool, source, amount, capped, dotsFrom)` and `credit(level, hearth, uuid, name, pool, source, amount, capped, dotsTo, dotsFrom)` (hourly cap when `capped`, ledger cell, `hearth_charge` telemetry, tracker dots, the tier watch when the pool's charge tier rose; returns what went in). Tales lane: Tales, Feast and Chronicle charge go through it with sources `TALES`, `FEAST`, `CHRONICLE`. Sieges lane: `siegeKill(hearth, uuid, name)`, `siegeDefended(hearth, present)`, `siegeBurst(level, hearth, kills, present, streak)` (outside the cap, split by kills, else evenly over the present), `cut(level, hearth, fraction)` (doused: off the pools, never the ledger). Stages lane: `sendLedger(player, hearth)` when the panel opens; `ledgerPayload(hearth)`, `mayRead(player, hearth)`. Reading the ledger marks the block entity changed only when the week rolled to a new day |
| `charge.ChargeTiers` | `check(level, hearth)` (call after changing anything that moves a tier outside `credit`; `credit` calls it when a pool's charge tier rose, `cut` always, the tick once a second), `playersInRange(level, pos)` |
| `charge.ChargeTick` | `someoneHome(level, pos, now)` (an online, living, non-AFK player in survival or adventure, in range: what holds decay off), `settleHolds(level, hearth, now)` (nothing settles while the Hearth smoulders: the holds wait for the relight) |
| `charge.ChargeEvents` | The kill context, taken at the highest priority of the death event: `siegeHearth` (the Hearth whose range a siege mob died in, while its `siegePhase` is `ACTIVE`; only that Hearth is fed, and only if it is the killer's home), `activeRaid` (the raid a raider was a member of as it died, from `RaidMember`, which the common mixin `HearthRaiderMixin` fills at the head of `Raider.die`, listed in both loaders' `proficiency.mixins.json`; no positional fallback) |
| `charge.ChargeText` | `pool(pool)` (name in its colour), `source(id)` (a skill's name or `proficiency.hearth.charge.source.<siege\|tales\|chronicle\|feast>`), `number(v)`, the glyph constants |
| `effects.HearthEffects` | The lane's init: `PLAYER_SECOND`, `SLEEP`, `INCOMING_DAMAGE`, `DEATH`, `HEARTH_TICK` (Workshop). `addWarmthBonus(player -> fraction)`: another Warmth speed bonus; bonuses add up and apply on top of Homecoming's readiness (`WarmthMath.withSpeedBonus`). The init registers Homebuilder's Warm Welcome itself (`HomebuilderApi.warmWelcome`, 0.10 a rank) |
| `effects.PlayerEffects` | `second(player)` (the per-player second; GameTests step it by hand), `tier(state, boon, d)` (effective tier of the pool giving a boon), `value(boon, tier, d)`, `hungerFactor(player)` (the exhaustion share Hearty leaves: 1 = none), `fortifies(attacker)` (a `Mob`, not a player, not an `OwnableEntity` with an owner UUID unless the owner is a loaded non-player), `lastHud(player)`, `endGrace(player)` (tests: a login holds Warmth, the edge and the HUD send for `HomeRules.LOGIN_GRACE_SECONDS`), `loggedIn(player)` (starts that hold; tests log a mock player in by hand), `takeLastEdge(player)` (tests: the edge flag of the last payload that carried one) |
| `effects.HearthTempHp` | `MODIFIER_ID` (`proficiency:hearth_absorption`, a permanent `max_absorption` modifier), `modifier(player)`, `active(player)` (absorption > 0 with the modifier: the `TEMP_HP` flag) |
| `effects.HearthStreak` | `holds(player)`: the survival streak is held (switched on, alive, any Hearth saw the player in the last 40 ticks, smouldering included). `SurvivalStreak.tick` calls it |
| `effects.HearthWorkshop` | `stationSpeed(level, hearth)` (the Workshop value while a player is in range, else 0), `refundChance(state, d)`, `refund(state, d)` -> `Refund(chance, tier)` (the best refund and the tier of the pool that gives it, which its `hearth_boon` row carries), `reversible()` (the reversible crafting recipe set, rebuilt when the server's `RecipeManager` changes), `setTestRoll(Double)` (tests) |
| `effects.HearthStations` | `speed(level, pos)`: the station speed at a block (one per-chunk index lookup, 0 when no Workshop works anywhere). Each Hearth publishes its speed once a second, valid 40 ticks; `prune(server)` (once a second) drops what ran out, so a Hearth whose chunk unloaded does not keep the lookup on. `publishForTest`, `published()` (tests). The mixins `HearthFurnaceMixin` (furnace, smoker, blast furnace) and `HearthBrewingStandMixin` (common, listed in both loaders' `proficiency.mixins.json`) add progress after vanilla's own tick; fuel burns as much faster as it cooks. Hearty: `HearthEffectEvents.hearty(player, food)` scales the exhaustion climb at the head of `FoodData.tick` (`HearthFoodDataMixin`, common, both loaders), before vanilla turns 4.0 into a food point, and after the player tick; since the integration task it calls `event.ExhaustionWatch.look`, which applies Hearty's share with Night Owl's and Lean Times'. Fix round i01y: the mixin calls `ExhaustionWatch.beforeFoodTick` / `afterFoodTick` itself (head and tail of the tick), so `hearty` has no caller and is `@Deprecated`, kept because the contract never loses an entry; `HearthRules.light(stage, smouldering)` is deprecated the same way (the block's light is `chargeLight`) |

### Sieges (lane `sieges`, filled 2026-10-09)

| Class | Use |
|---|---|
| core `hearth.sieges.SiegeClock` | `expectedStart(due, gameNow, dayNow)` (the dusk a due siege starts at), `missedNightfalls(...)` (dusks an unloaded Hearth slept through), `LIVE_TICKS`; fix round: `dayJumped(...)` (a bed or `/time` moved the day clock past game time), `nightFell(dayNow)`, `bedSkipsStart(due, gameNow, dayNow, nightBefore)` (a bed now would sleep through the dusk that starts a counting-down siege) |
| core `hearth.sieges.SiegeTally` | Fix round. `fall(playerCredit, byFellowSiegeMob)` -> `Fall.KILL/FRIENDLY/UNEARNED` (how one siege mob's end counts), `cleared(unearned)` -> `DEFENDED` or `UNEARNED` (every wave down) |
| core `hearth.sieges.DouseMeter` | `step`, `full`, `percent`, `crossed` (the 50 / 80 % warnings) |
| core `hearth.sieges.SiegeWaves` | `compose(size, ceiling, siege, random)` (the mix; `onePerWave` ids once a wave), `gates`, `gateAngles`, `near`, `direction(dx, dz)` + `DIRECTIONS` (lang suffixes) |
| core `hearth.sieges.HornCheck` | `check(...)` -> `Result` (`OK, OFF, PEACEFUL, NO_HEARTH, SMOULDERING, CEILING, BUSY, COOLDOWN`), `minutesLeft` |
| `sieges.SiegeService` | The siege second (`HEARTH_TICK`) and everything it does: `second(level, pos, hearth, now, day, online, dice)`, `facts` / `conditionHolds`, `roll`, `rollIfReady`, `blowHorn(player)`, `beginCountdown`, `start`, `end(level, pos, hearth, outcome, now, online)`, `cancel`, `setSmouldering`, `relight`. For other lanes: `concerned(level, hearth)` (online players the siege concerns), `showPanel(level, hearth, players)` (the bell's alarm pops the panel up), `siegeMobs(level, hearth)` (the living siege mobs, for the alarm's glow), `LANE` (its `laneData` tag). `MANUAL`: a GameTest seam (Hearth keys whose own tick leaves the siege alone) |
| `sieges.SiegeMobs` | `mark(mob, hearth, siegeId)`, `isSiegeMob`, `hearthOf` (packed, or `HearthIndexStore.NONE`), `siegeIdOf`, `mayGrief(entity)` (false for a siege mob and what it fired), `disarm`, `marching` |
| `sieges.SiegeTrophyItem` | The trophy's item (`HearthBlocks.SIEGE_TROPHY_ITEM`): `of(tier, day)`, `tier(stack)`. The tier is the vanilla `block_state` component, so placing applies it; the loot table copies it back |
| `sieges.SiegeWorldData` | Overworld `proficiency_hearth_sieges`: per Hearth key, who the siege concerns and what its panel last showed, the douse meter included (login resend while the Hearth is unloaded) |

- **Charge and ledger** go through the Charge lane: `HearthCharge.siegeKill` (each siege mob a player, or a
  player's pet, kills), `siegeDefended` (each player present at a defence who is online; one who logged off
  before the last mob fell gets `addSiegeDefended` directly), `siegeBurst` (the victory burst, outside the cap,
  split by kills, else evenly over the present) and `cut(dousedLoss)` (doused). The combat share of a siege
  kill stays the Charge lane's; Sieges does not count the same kills again.
- **Sounds**: Sieges plays world sounds at the Hearth (douse hiss, extinguish, amethyst chime, relight), the
  goat horn for the Siege Horn and the distant raid horn for an announcement. The per-player wave horn and
  the outcome chime are the siege panel's (client lane), so nothing plays twice.
- **Siege panel** (`siege_panel`), what the fields carry: `COUNTDOWN` `due` = the game time the siege is
  expected to start (the first dusk after the due time, from the level's clocks; sent again on a
  postponement and when Warm n/m changes), `warm`/`group` = Warm n/m. `WAVES` `due` = when the siege began,
  `left` = living siege mobs, `warm` = the douse meter in percent, `group` = 100 (sent on spawns, kills and
  every 10 % of douse). `OUTCOME` `outcome` = the `SiegeRules.Outcome` ordinal, `wave` = waves reached,
  `warm` = the douse percent. `CLEAR` = a cancel (sieges switched off, Peaceful). `outcome` is -1 outside
  `OUTCOME`. A concerned player who logs in gets the current panel again.
  Client (polish-121): the countdown bar drains over the time left when the client first saw that siege
  (`hearth.client.SiegeClock.span`, at most the warning; a postponement widens it), so a horn's siege a few
  minutes off starts full; the bar is `SiegeClock.bar(due, now, warning, span)`, the 3-argument form unchanged.
  `OUTCOME` panels for defended, doused and unearned carry a recap line
  (`proficiency.hearth.client.siege.outcome.<id>.sub`, up to three lines).
- After a burst, a douse or a relight Sieges sends `hearth_state` (`UPDATE`) and `hearth_tracker` to the
  players in range.
- `BLOCK_USE`: the Sieges listener (flint and steel on a smouldering Hearth relights it) is inserted first
  in line, so no lane that answers every click on the Hearth can swallow a relight.
- Two shared events (README "Writing a feature once"): `platform.event.entity.player.CanPlayerSleepEvent`
  (NeoForge relay; Fabric `EntitySleepEvents.ALLOW_SLEEPING`, asked before vanilla's checks) and
  `platform.event.entity.EntityMobGriefingEvent` (NeoForge relay of every per-entity griefing check;
  Fabric posts it only from `Level.explode` with the MOB interaction and `FarmBlock.fallOn`).
- `Mob.goalSelector` is open to common: `common/accesstransformer.cfg` at compile time, NeoForge's own AT
  and `fabric/src/main/resources/proficiency.accesswidener` at runtime.
- Command `/proficiency hearth siege status|call|start|end <outcome>|clear` (op level 2, at the Hearth
  whose range holds the source).
- Telemetry `hearth_siege` labels: `condition` (seconds), `roll` (value = chance), `hit`, `rolled`,
  `called`, `horn`, `postpone`, `start` (value = players), `wave` (source = ceiling, value = mobs),
  `kill` (source = entity id), `unearned` (source = entity id, a fall no player or pet dealt), `burst`
  (source = pool, value = charge), `outcome` (source = outcome id).
  `tools/balance_report.py` turns them into one line under the Hearth section (`siege_summary`): sieges
  announced per 10 h of active play, hours the condition held, outcomes, mean wave size.
- `BLOCK_BROKEN`: a Hearth broken with a siege pending or running cancels it (`SiegeService.cancel`).
- Fix round (2026-10-09), additive: `SiegeRules.Outcome.UNEARNED` (appended, so the ordinals before it
  stand): every wave is down before dawn but some siege mob fell without a player's or a pet's hand
  (lava, a fall, a golem, a portal, a vanish), so the siege is neither won nor lost (no burst, no trophy,
  streak kept). A siege mob killed by another siege mob, or a siege creeper that blew itself up, spoils
  nothing; a converted siege mob (zombie to drowned, skeleton to stray) keeps its place and its mark. The
  roster changes only on the shared `LivingDeathEvent` and `EntityLeaveLevelEvent` (removal reason read);
  a mob whose chunk is not loaded still stands. The client lane's panel shows
  `proficiency.hearth.client.siege.outcome.unearned` for it, in the quiet style. `cancel` now starts the
  cooldown from a countdown too and sends `hearth_state` (`UPDATE`) to the players in range (not when the
  Hearth is being broken). Beds: refused in range while counting down when the bed would sleep through the
  dusk that starts the siege; a dusk skipped anyway (a bed out of range, `/time add`) is a postponement.
- `tales.HearthBell.siegeLink` is set by `HearthSieges.init` (mobs = `SiegeService.siegeMobs`, concerned =
  `SiegeService.concerned`, panel = `SiegeService.showPanel`), so the bell's alarm reads the running siege.

Other shared additions: `SpawnOrigin.SIEGE` with `tagSiege(mob)` and `isSiege(entity)` (siege mobs
pay full combat XP); `TelemetryService.hearth(player or null, kind, label, source, value)`;
`platform.event.entity.player.PlayerWakeUpEvent` on the shared bus (NeoForge relays its own; Fabric
has no source yet); `Platform.hearth(player)`, `registerBlock`, `registerBlockEntity`.
Charge lane: `TelemetryService.hearth(uuid or null, name, kind, label, source, value)` for a player who
may be offline (a held credit landing after they left).

Stages additions (lane `stages`): `HearthBlock` now breaks only by a 3 s sneak-hold
(`getDestroyProgress` = `HearthBreaking.destroyProgress`, any tool; a swing without sneaking puts a
hint on the action bar), resists explosions (1200), swallows on the client the clicks the server
answers (no ghost block or fire), and pick-block gives a campfire; its loot table
(`data/proficiency/loot_table/blocks/hearth.json`) drops one plain campfire. `DiscoveryPayload`
gains `HEARTH` (5, a stage sealed or lit; `name` the stage name key, `id` = `hearthId(stage)`,
`with` who sealed it) and `HEARTH_FIRST` (6, the first Hearth a player lights); `DiscoveryBanner`
shows them with the stage icon (`textures/gui/hearth/stage/<n>.png`) through the new
`SkillIcons.drawTexture(graphics, texture, x, y, size, alpha)`. PlayerHearth flags used:
`stages.hint` (the campfire hint was shown), `stages.firstLit`. Block entity lane tag
`laneData("stages")`: `lastCheck`, `dirty`, `ceiling` (last told), `ready` (the stage told as ready),
`sealers` (UUIDs who lit or sealed: they hear the put-out line with the ledger's feeders).
Fix round (s01x): `ceiling` is now the ceiling the blockstate shows; `told` and `toldAt` are the ceiling
the last fall or rise line named and its game time; `lowSince` is when a Character drop below the
shown ceiling began (absent when none is pending).

Effects additions (lane `effects`): Fabric now posts `PlayerWakeUpEvent` from `HearthWakeUpMixin`
(head of `Player.stopSleepInBed`, like NeoForge's hook), so `SLEEP` fires on both loaders.
`SurvivalStreak.tick` returns early while `HearthStreak.holds(player)` (telemetry `streak_paused`).
`net.HearthClients` gains `hud(payload)` (the loaders' `hearth_hud` receiver: remembers the payload
and its arrival time, then calls `get().onHud`), `lastHud()`, `hudAt()` (`Util.getMillis` at arrival),
`streakSafe()` (the one rule: `STREAK_SAFE` while `home`) and `leaveWorld()` (forgets it, then
`get().onLeaveWorld()`). It is the client's one copy of the HUD packet: `HearthView.hud()/hudAt()/
streakSafe()` read it. The skills panel's streak block (`client.StreakAtHearth`) asks
`HearthClients.streakSafe()` and draws `HearthHud.glyph(now)`, the HUD's own glyph (Warmth moved on by
its rate, the `WarmthGlyph` frame, embers when smouldering).
The edge cues ("By the Hearth", "Left the Hearth's warmth", "Set out from the Hearth", the crackle)
are decided by the server and played by the client: the one payload of the edge second carries
`FLAG_EDGE_HOME`, `FLAG_EDGE_LEFT` or `FLAG_EDGE_SET_OUT` (`HearthHudPayload.edge()`), and
`HearthHud.accept` plays the cue for that flag, never for a `home` flip alone; the server sends no
action-bar line or sound. A set-out is a walk out of a Hearth that still burns at full Warmth (with or
without a Hearty tier); it sets Provisioned (`PlayerHearth.provision`) in the same second, before the
payload goes. A Hearth that breaks, smoulders or is switched off under the player, a death, and a
login (no payload at all while the login holds) carry no edge. Lang: `proficiency.hearth.effects.streak.safe[.detail]`,
`proficiency.hearth.effects.streak.next`.

### The tales lane (`hearth.tales`)

| Class | Use |
|---|---|
| `HearthTales` | `init()`; `storyteller`: a `ToDoubleFunction<ServerPlayer>` for the extra share of banked Tales a talent adds, `HomebuilderApi::storyteller` by default (0.10 a rank; a test may pin it) |
| `TalesBank` | Banking and homecoming. Hooks: `GRANT` (Movement and Expedition XP away from a lit Hearth's range banks `tales.share` of `beforeRested` into `PlayerHearth.tales`, skipping first-time lines and area extras; a discovery grant, source `structure.*`, `biome.*`, `dimension.*`, is remembered for the trip's line), `PLAYER_SECOND` (arriving at a lit Hearth the player fed, `ledger.total > 0`, pays the bank into the Tales pool through `HearthCharge.credit(..., HearthLedger.TALES, amount, capped = true, null)`, so the hourly cap, ledger source `tales`, `hearth_charge` telemetry, tracker dots and the tier watch are charge's; a bank under 1 charge is kept; a Hearth you never fed hears nothing and the trip goes on), `DEATH` (the bank is already lost by `clearOnDeath`; the trip ends). A trip is the player flag `tales.away`, the mark `tales.tripCm` (walked distance when it began) and place flags `tales.place.<language key>` (at most 8). Sends `hearth_tales` (kind `PAID`), fires `HearthEvents.TALES_PAID`, marks the HUD dirty when the whole-number scroll count changes |
| `HearthFeast` | `ITEM_EATEN` at a lit Hearth: `FeastWindow.onEat`, then every warmed eater who is still home gets Warmth 1.0 (`PlayerHearth.setWarmth`), `lastFeast`, the Feast pool charge through `HearthCharge.credit(..., HearthLedger.FEAST, share, capped = true, null)` (the hourly cap, split evenly between the warmed), `HearthEvents.FEAST` |
| `HearthBell` | `ring(player, level, bellPos)` makes the context call. A right click on a bell (`BLOCK_USE`) is noted and counts when vanilla's bell-ring statistic rose by the end of the tick (`HearthTalesEvents` ticks it). Alarm = `siegePhase != IDLE`: siege mobs in range glow `bell.alarmGlowSeconds`, everyone online in survival or adventure gets the chat line, same-dimension ones a `hearth_ping` (`ALARM`, `alarmCooldown` s: the client pops the siege panel and shows the arrow). Dinner: `openDinner`, chat to everyone, a `hearth_ping` (`DINNER`, the window length). Come home: chat to everyone, `hearth_ping` (`COME_HOME`, `comeHomeSeconds`) and `PlayerHearth.heardBell(pos, dim, now + comeHomeSeconds*20)` to the players in the Hearth's dimension (the flag Homebuilder's Homeward reads), a hum for a compass bound to the Hearth's lodestone. A bell outside the Hearth's 7x7 box or below stage V does nothing; a call on cooldown does nothing (no fall-through). Seam for the Sieges lane: set `HearthBell.siegeLink` (a `SiegeLink`: `mobs`, `concerned`, `showPanel`) from `HearthSieges.init` with `SiegeService.siegeMobs / concerned / showPanel`; the alarm then makes those mobs glow, pings and chats only the players the siege names, and calls `showPanel` for them. Without it the alarm falls back to `SpawnOrigin.SIEGE` mobs in range and every online survival or adventure player |
| `HearthChronicle` | Lecterns in range register with their Hearth on use (lane data `tales`: `lecterns`, `filed` page hashes, an hourly page limit of 12); the Hearth's second reads a registered lectern's book when it changed. A book already on a lectern when it is first used is remembered as filed. A new page is filed (`ChronicleBook.write`, one line in `<world>/proficiency/chronicle.jsonl`); the first one after a homecoming takes the last payout as its Tales and pays `chronicle.writeBonus` x that, outside the cap (through `HearthCharge.credit(..., HearthLedger.CHRONICLE, share, capped = false, null)`, `HearthEvents.CHRONICLE_WRITTEN`, `hearth_tales` kind `CHRONICLE`). `rate(server, id, stars)` pays `perStar` x stars x the page's Tales outside the cap (the same call) to the page's Hearth (its chunk is loaded when needed), tells the author at once or at next login (`hearth_tales` kind `RETOLD`); `drainQueue(server)` applies `chronicle-ratings.jsonl` (also at start and once a minute) |
| `ChronicleCommand` | `/proficiency hearth tale <page id> <stars 0-5>`, op level 2, output with an English fallback for RCON |

Files in the world folder: `proficiency/chronicle.jsonl` (append only, one object per page: `v`, `id`, `author`, `name`, `hearth` ("dimension|x,y,z"), `gameTime`, `real` (UTC ISO), `tales`, `title`, `text`); `proficiency/chronicle-ratings.jsonl` (`{"id":7,"stars":4}` per line, read and renamed by the mod; `chronicle-ratings.applied.jsonl` keeps what it did).

Telemetry from this lane: `hearth_tales` (labels `banked` with the skill as source, `paid`, `lost`), `hearth_charge` (sources `tales`, `feast`, `chronicle`), `tale_rating` (label = stars, value = charge paid).

## The hook and listener pattern

The game calls a dispatcher; lanes add listeners from their own init class. No lane edits a shared
file to be called.

```java
public static void init() {   // e.g. HearthCharge.init(), called once by Hearth.init()
    HearthHooks.GRANT.add(HearthCharge::onGrant);
    HearthHooks.HEARTH_TICK.add((level, pos, hearth) -> { ... hearth.setChanged(); });
    HearthEvents.STAGE_SEALED.add(event -> { ... });
    NeoForge.EVENT_BUS.register(MyChargeCommands.class);   // shared bus handlers of the lane's own
}
```

- Listeners run on the server thread in the order added. One that throws is logged (rate limited)
  and the next still runs.
- `hearth.enabled = false` silences every hook except `PLAYER_SECOND` and `DEATH`, which keep
  running so a lane can undo per-player effects.
- A lane's own shared-bus handlers (commands via `RegisterCommandsEvent`, other events) are
  registered from its init with `NeoForge.EVENT_BUS.register(Class)` (the shared bus in
  `platform.bus`), not by editing `Subscribers`. An event the bus lacks needs the class under
  `platform.event`, a relay in `NeoForgeEventBridge` and a Fabric mixin or callback (README,
  "Writing a feature once").
- Commands: register `literal("proficiency").then(literal("hearth")...)` from the lane's own
  `RegisterCommandsEvent` listener; Brigadier merges it into the existing `/proficiency` tree.

| Hook | Called from | Listener gets | Lanes |
|---|---|---|---|
| `GRANT` | end of `SkillService.grantOnce` (every paid grant, after the filters; fake players, spectators, disabled skills, zero amounts never arrive) | `Grant(player, skill, source, baseAmount, beforeRested, amount, banked, play, aoe)`; `firstTime()` for `first\|...` lines | charge (mirror `beforeRested` when `play` and not `firstTime()`), tales (bank away from home), homebuilder (share, Hearthglow) |
| `PLAYER_SECOND` | `PlayerLifecycleEvents`, the once-a-second player loop, every online player | `player` | effects (Warmth, streak hold, modifiers, edge cues, HUD send), homebuilder (passive modifiers), tales (pay on arrival), sieges |
| `INCOMING_DAMAGE` | shared `LivingIncomingDamageEvent`, server side, any living entity | the event (change the damage through it) | effects (Fortified, combat mark), homebuilder (Scaffolder), sieges |
| `SLEEP` | shared `PlayerWakeUpEvent` when `isSleepingLongEnough()` (a real sleep) | `player` | effects (Warmth to full) |
| `BLOCK_PLACED` | shared `EntityPlaceEvent`, server side | `level, pos, placed, placer or null` | charge (hold queue), comfort (rescan), stages (hint) |
| `BLOCK_BROKEN` | shared `BreakEvent` at LOWEST, not canceled | `level, pos, state, player` | charge (drop holds), comfort, stages (layer broken; the Hearth's own put-out has its own `BreakEvent` listener since fix round 2) |
| `ITEM_EATEN` | shared `LivingEntityUseItemEvent.Finish`, food only | `player, food copy` | tales (Feast, dinner bell moment) |
| `BLOCK_USE` | shared `RightClickBlock`, server side, at LOWEST and not for canceled clicks (s01x: after every other mod, so a claim mod's refusal stops it; Fabric runs LOWEST in a `UseBlockCallback` phase after the default one, `FabricHooks.LATE`; fix round 2: both phases share one event, so a cancel before LOWEST skips it on Fabric as on NeoForge, and a canceled click whose result was left at PASS ends there as FAIL) | `player, level, pos, state, hand, held`; return non-PASS to consume the click | stages (offerings, panel), tales (lectern, bell), sieges |

Stages runs before comfort, effects, tales and sieges on `BLOCK_USE`. On a Hearth it consumes only a
non-sneak main-hand click with an empty hand (opens the panel: `hearth_state` `PANEL`) or with an
offering item; flint and steel on a smouldering Hearth passes (relighting is the Sieges lane's), and
every other item passes. On a plain campfire it consumes only a lighting (or its spacing refusal).
| `HEARTH_TICK` | `HearthBlockEntity`, once a second per ticking Hearth | `level, pos, hearthEntity` | stages (in-range body: `PlayerHearth.seenBy`), charge (decay, holds), comfort (rescan timer), sieges (roll, clock), effects (Workshop) |
| `DEATH` | the Hearth's `Clone` handler after `clearOnDeath` | `player, data, talesLost` | tales (telemetry `hearth_tales` lost), effects (modifier cleanup) |
| `REQUEST` | `hearth_request` from a client | `player, request` | charge (`LEDGER`), stages (`STATE`) |

### Milestones (`HearthEvents`)

| Signal | Record | Fired by | Heard by |
|---|---|---|---|
| `STAGE_SEALED` | `(level, hearth, player, stage)`; lighting is stage 1 | stages | homebuilder (150 x stage) |
| `COMFORT_KIND_SEEN` | `(level, hearth, placer uuid or null, kind)` | comfort | homebuilder (60) |
| `CHARACTER_ID_SEEN` | `(level, hearth, placer uuid or null, blockId)` | comfort | homebuilder (10) |
| `SIEGE_DEFENDED` | `(level, hearth, kills per uuid, present players)` | sieges | homebuilder (300 split) |
| `FEAST` | `(level, hearth, eaters)` | tales | homebuilder (25) |
| `TALES_PAID` | `(level, hearth, player, tales)` | tales | homebuilder (10%) |
| `CHRONICLE_WRITTEN` | `(level, hearth, author, pageId, tales)` | tales | homebuilder (20) |
| `TIER_UP` | `(level, hearth, pool, from, to, topFeeder uuid or null, topSource or null)`: a tier the pool was not told before (the high-water follows the charge down, not the ceiling or Comfort, so a re-climb after decay is told again and a layer rebuilt is not); never while smouldering | charge | client (cues), anyone |

## Homebuilder API (for the other lanes)

`dev.amman.proficiency.homebuilder.HomebuilderApi`, static, server side, each 0 / false when the
skill is off or the talent not taken. The effects and tales lanes call these; the homebuilder lane
edits none of their files.

| Method | Talent | Use |
|---|---|---|
| `warmWelcome(player)` | Warm Welcome, 0.10 a rank | effects: Warmth rises this fraction faster, on top of Homecoming's readiness: `WarmthMath.withSpeedBonus(seconds, HomebuilderApi.warmWelcome(player))` |
| `storyteller(player)` | Storyteller, 0.10 a rank | tales: `TalesMath.banked(skill, xp, HomebuilderApi.storyteller(player), d)` |
| `nightWatch(player)` | Night Watch | sieges, if it wants to know (Night Watch itself is wired by homebuilder from `HEARTH_TICK`) |

Also readable: `homebuilder.HomePassive.bonus(player)` (the home passive in use now, 0 away),
`HomePassive.SPEED_ID` / `REACH_ID` (`proficiency:homebuilder_speed`, `proficiency:homebuilder_reach`),
`homebuilder.HomebuilderXp.SHARE_SOURCE` (`proficiency.xplog.source.home`, telemetry kind `home`),
`homebuilder.Homeward.channelling(player)`. Pure numbers and the talent tags: `skill.HomebuilderMath`
(core). Homebuilder's listeners: `GRANT` (share, then Hearthglow, after Charge's), `PLAYER_SECOND`,
`INCOMING_DAMAGE` (Scaffolder: plain fall damage while the fall distance is still counted, so not an
ender pearl's landing), `HEARTH_TICK` (Night Watch), `DEATH`, and every
`HearthEvents` signal (milestones; `FEAST` also for Heart of the Home). Hearthglow books its charge
through `HearthCharge.credit` (capped, ledger source the earning skill's id, dots from that skill), and
only for a grant `ChargeRules.route` sends to a pool; see docs/hearth-build-log/homebuilder.md.

Homeward's channel breaks on the shared `LivingDamageEvent.Post` (a hit that landed, after every
handler, shields and the hurt cooldown; absorption hearts taking it counts), not on
`INCOMING_DAMAGE`. Once a day means a new overworld day number or a full day (24 000 ticks) of game
time since the last arrival (mark `homebuilder.homeward_at`), so a world with `doDaylightCycle` off
still gets it back. The milestone rows that come again (`SIEGE_DEFENDED`, `FEAST`, `TALES_PAID`,
`CHRONICLE_WRITTEN`) pay every time they fire (a page id once); the first-time banner and the
`<hearth key>|<row>` milestone key are only for the first at each Hearth. Fire each once per real
occurrence: `FEAST` once per eater per Feast (the starters, then each joiner alone, as
`FeastWindow` reports them); Heart of the Home remembers the Feast's host per Hearth for the joiners.

**Done (effects fix round, checked by the integration task):** `HearthEffects.init` registers
`addWarmthBonus(HomebuilderApi::warmWelcome)`; `HearthEffectsGameTests#warmthRisesDrainsAndASleepFillsIt`
checks Warm Welcome III. (Was: "owed by the effects lane".) The tales lane reads `storyteller` through
`HearthTales.storyteller` (on master since a8f171c).

## Payloads (network protocol 7)

All play phase, registered on both loaders with the rest; every send checks the channel. Client
handlers call `HearthClients.get().onX(payload)` on the client thread.

| Payload id | Direction | Sent by | Handled by | When |
|---|---|---|---|---|
| `proficiency:hearth_state` (`HearthStatePayload`, reason `ENTER/TIER/PANEL/UPDATE`) | S2C | stages (enter range, panel open), charge (tier change), comfort (after a scan changed something); build it with `HearthNet.stateOf` | client: HUD glyph, panel (opens on `PANEL`), tracker, range ring | never on a timer |
| `proficiency:hearth_tracker` (`HearthTrackerPayload`) | S2C | charge (decay caught up), sieges (bursts) | client: tracker rows | on change |
| `proficiency:hearth_gain` (`HearthGainPayload`) | S2C | charge via `HearthNet.queueGain` (flushed with the 10-tick skill sync) | client: tracker dots from the skill bar | batched |
| `proficiency:hearth_ledger` (`HearthLedgerPayload`, both windows) | S2C | charge (panel open or `REQUEST LEDGER`) | client: ledger tab | once per open |
| `proficiency:siege_panel` (`SiegePanelPayload`, `CLEAR/COUNTDOWN/WAVES/OUTCOME`) | S2C | sieges | client: siege panel | start time + wave events |
| `proficiency:hearth_ping` (`HearthPingPayload`, `BellRules.Call` ordinal) | S2C | tales (the bell) | client: arrow, compass ping. `ALARM` also pops up the siege panel (for `ticks`) and ends with that Hearth's siege (an outcome or a clear drops it, so no call panel comes back after the outcome), `DINNER` shows the arrow for the Feast window, `COME_HOME` for 60 s. A call panel that takes over from the siege panel fades in | one per ring, to players in the Hearth's dimension |
| `proficiency:hearth_hud` (`HearthHudPayload`, flags `STREAK_SAFE`, `SMOULDERING`, `TEMP_HP`, one of `EDGE_HOME`/`EDGE_LEFT`/`EDGE_SET_OUT` on the edge second) | S2C | effects (owns the send; tales calls `HearthNet.markHudDirty`) | client: Warmth glyph, Provisioned badge, Tales scroll | on change; the client interpolates Warmth with `warmthPerSecond` |
| `proficiency:hearth_tales` (`HearthTalesPayload`, kind `PAID/CHRONICLE/RETOLD`, pool, amount, blocks, stars, places (language keys, at most 3), title) | S2C | tales | `HearthClient.onTales`; the default shows the pay-in panel (`client.TalesPayInHud`, after `DeathRecapHud`), so a client implementation need not handle it | a homecoming, a page set down, a tale retold |
| `proficiency:hearth_request` (`HearthRequestPayload`, `LEDGER/STATE`) | C2S | client (panel tabs) | `HearthHooks.REQUEST` | on demand |
| `proficiency:homebuilder` (`homebuilder.net.HomebuilderPayload`, kinds `MAGNET/CHANNEL/CHANNEL_END/SHARED/HOMEWARD_DAY/HOMEWARD_AT`) | S2C | homebuilder (`HomebuilderNet`) | `HomebuilderNet.client()`, installed by `client.HomebuilderHud`: the wheel's magnet and Homeward state, Homeward's channel ring, a friend's House Proud glow | login, on change |
| `proficiency:homebuilder_action` (`homebuilder.net.HomebuilderActionPayload`, `MAGNET/HOMEWARD`) | C2S | the ability wheel's two Homebuilder slots | `homebuilder.HomebuilderEvents.onAction` | on click |

`HearthStatePayload` ends in `extra` (`HearthStatePayload.Extra`, added by the client lane): the tier
`thresholds`, one `Boon(pool, boon, value, extra, next, nextExtra)` per pool (the boon type id; its number at
the pool's effective tier and at the tier above; the second number of regen = temp HP share of max health,
of Workshop = refund, of readiness = Provisioned minutes; regen's `value` is seconds per heart, readiness's
the seconds Warmth takes to fill), `comfortAll` (every kind there is), `characterMax`, `nextOffering` (the
item id the next stage asks for, "" at VI, and "" since polish-121 when that stage was paid before and
only fell, since a rebuilt layer owes no second offering), `spacing` and `warningTicks`. `HearthNet.stateOf` fills it with
`Extra.of(state, data, minSpacing)`; the old 18-argument constructor stays and sends `Extra.NONE`, which
makes the client fall back to the shipped defaults. Fix round 2 (stages) appended `offerings` (the six
stages' offering ids, an item id or `#tag` each, written last on the wire, at most 16): the client keeps
them for the Hearth block's click prediction (`HearthOfferings.serverOfferings`). The old 7-argument
`Extra` constructor stays and sends none.

Shared row type: `PoolRow(pool, charge, chargeTier, tier, progress, lock, need)`, built by
`PoolRow.of(state, data)`. `lock` is the `HearthRules.Lock` ordinal; `need` is the Comfort
(`COMFORT`), the stage to build (`CEILING`) or the Character the next stage wants (`CHARACTER`:
"Character 11/14", with the Hearth's `character` from the state payload).

## What the client does on its own (client lane)

Server lanes send state; the client turns it into moments. So no server lane sends these:

| Moment | The client does it from |
|---|---|
| Edge cues: crackle + "By the Hearth" / "Left the Hearth's warmth" / "Set out from the Hearth" on the action bar | the edge flag of a `hearth_hud` (`edge()`, decided by the server); the pack sound with "Set out" when Provisioned came along |
| Warmth 100% crackle and glow (a bed too) | the interpolated Warmth crossing 100% |
| The panel's unlock sweep along the stage track (the banner and sounds of a seal are the stages lane's) | a `hearth_state` of the same Hearth whose stage rose by one, while the panel is open |
| Banner "Your Hearth is marked for a siege" (polish-121: was "Smoke from your Hearth has been seen", untrue for a horn) | the first `siege_panel COUNTDOWN` of a siege (`hearth.client.SiegeClock.sameSiege`: same Hearth, start moved less than a day earlier or at most two days later, until that Hearth's `WAVES`/`OUTCOME`/`CLEAR`); not shown with `hearth.siegePanel` off |
| Raid horn per wave, advancement sound on Defended, fire hiss on Doused | `siege_panel WAVES` (new wave) / `OUTCOME`; they play with `hearth.siegePanel` off too (the switch hides the panel and the banner only) |
| Tier-up flash, roll, ring and chime (tracker on only) | the tracker's fill reaching the threshold, capped by `hearth_state` tiers |
| The charge lane's tier-up chat line dropped for this player | the line (`proficiency.hearth.charge.tier_up[.alone]`) is held while the tracker and `hud.levelUpFx` are on (`hearth.client.HearthChat.hold`, from `OverlayFallback.reroute`): dropped when the tracker plays that pool's tier-up on the screen, let through when the numeral turns over unseen (arrival snap, GUI hidden, tracker faded) or after 4 s |
| The tracker's rows start over (no catch-up flight or tier-up) | a `hearth_state` with reason `ENTER` |
| The tracker steps aside for the death recap and the Tales panel (they share the right edge for their five seconds) | core `client.RightEdge`: each panel `claim`s the rows it covers every frame it draws (its own fade as the strength; `release` when it ends), and `TrackerHud` asks `RightEdge.place`: under the panel (or over it, the shorter move) where it fits, sliding there and back (`RightEdge.Follower`), faded by the panel's fade only where neither fits (layout-audit site `hearth.tracker.gives_way`; a tier-up it misses goes to the chat). A new right-edge panel only claims a slot. Both panels start at `RightEdge.panelTop` (30%). `DeathRecapHud.showing()` and `TalesPayInHud.showing()` (0..1, their own fades) stay as API (polish-forge, 2026-10-10; claims after its code review) |

Client API other code may call: `hearth.client.HearthView.state()/home()/streakSafe()` (the skills panel's
streak block is the effects lane's `StreakAtHearth`, which reads `HearthClients.streakSafe()` and
`HearthHud.glyph(now)`), `client.DiscoveryBanner.showSiege(kicker, title)`, `client.HudKit` (TextFit, HudMath, the icon blit, `bannerTop()`/`bannerBottom()` of the discovery banner on
screen, for other packages), `client.DotFlight` (dots
from a start to an end point), `XpGainDots.gain(from, to, now, maxNew)` and `inAir()`, `SkillHud.dotAnchorX/Y`,
`showing(skill, now)`, `lineY(guiHeight)`, `HearthHud.lineWidth(font, now)` and `lineRoom(font, gap)` (the
skill line's room for the glyph group, eased with its fades; core `WarmthGlyph.lineRoom`), core
`WarmthGlyph.talesCount(tales)` (the Tales count text) and core `TrackerPresence` (the tracker's home clock,
told every HUD pass, drawn or hidden: `TrackerHud.hidden(now)` is the hidden pass). `showSiege` follows the
master banner switch only; its caller applies `hearth.siegePanel` (fc01's fix round). Test hooks: `/proficiency hearth demo <players> <scene> [pos]`
(operator, fake payloads only, `hearth.client.HearthDemoCommand`, registered from `Hearth.init`), the client
tour `PROFICIENCY_HEARTH_DEMO=<dir>` and the layout demo group `hearth`. Log: docs/hearth-build-log/client.md.
Since the Forge polish pass (polish-forge, 2026-10-10): a player who logs in or respawns at a Hearth gets
`hearth_state ENTER` at once (`HearthPresence.joined`, from `HearthCommonEvents`' login and respawn hooks; the
edge stays silent, the tracker gets its Comfort and Character lines at once; with the Hearth's chunk not loaded
yet the state is owed and its first tick sends it); the range and spacing rings draw full-bright motes
(`hearth.client.EmberDust`, the dust sprites at full light, found once a pulse and kept until a resource reload,
plain dust as the fallback; in `copySharedHearth`); the Tales panel sits at the death recap's 30%
(`RightEdge.panelTop`), clear of a top-right minimap. Forge 1.20.1 only: `SkillHud.lineY` rises with Forge's
raised action bar (core `client.LineLift`, moved once a frame at the head of `RenderGuiEvent.Post`), so the
glyphs and the edge cues keep clear.
The panel asks for the ledger itself (`hearth_request LEDGER`, once per opening, retried twice if the
charge lane's once-a-second answer was refused), so no lane needs to send it on panel open. Core
`hearth.client.LedgerView.feeds(source, pool, data)` says whether a ledger source can feed a pool.

## Data (`data/<ns>/proficiency/hearth/*.json`)

The mod's own `data/proficiency/proficiency/hearth/default.json` loads first (pinned, whatever its
path sorts as), then the mod's other files by path (a lane's `proficiency/hearth/<lane>.json` merges
over the defaults), then every other namespace alphabetically, each by path
(`HearthDataLoader.ORDER`). Objects merge key by key, arrays and values replace,
`"replace": true` drops everything before it (code defaults fill the gaps). Bad values are logged
and keep their default. `HearthDataTest.theShippedFileIsExactlyTheCodeDefaults` holds the file to
`HearthData.DEFAULTS`: change both together.

| Key | Default | Read by |
|---|---|---|
| `pools.<id>.feeds` | hearty `#category:gathering`, regen `decorating`, fortified `masonry`, workshop `#category:crafting`, homecoming `social`, `#tales` (also `#feast`) | charge |
| `pools.<id>.boon`, `.values` | spec table | effects |
| `pools.<id>.hold` | 600 s for regen and fortified | charge |
| `pools.regen.absorptionPerTier` | 0.10 | effects |
| `pools.<id>.comfortCap` | `half` for regen and homecoming, else `tiers` | everyone via `HearthRules` |
| `pools.workshop.refund` | 0.02 .. 0.08 | effects |
| `pools.homecoming.warmthSeconds`, `.provisionedMinutes` | 55..30, 16..30 | effects |
| `thresholds` | 200, 600, 1400, 2800, 5000, 8000 | all |
| `mirrorShare`, `combatShare`, `hourlyCap` | 0.10, 0.03, 300 | charge |
| `comfortForTier` | `{"5": 8, "6": 10}` | all via `HearthRules` |
| `decay.perHour`, `decay.graceHours` | 0.02, 24 | charge |
| `warmth.riseSeconds`, `warmth.drainSeconds` | 60, 30 | effects |
| `combatSeconds` | 5 (not in the spec's block; its "5 s since damage") | effects |
| `tales.share`, `tales.feeds`, `tales.feedAll` | 0.05, movement + expedition, false | tales |
| `provisioned.minutes` | 15 | effects |
| `character.max`, `character.forStage` | 50, `{"3": 8, "4": 14, "5": 24, "6": 40}` | comfort, stages |
| `chronicle.writeBonus`, `chronicle.perStar` | 0.25, 0.05 | tales |
| `feast.windowSeconds`, `.dinnerBellSeconds`, `.chargePerExtraEater`, `.dinnerTriggerSeconds` | 30, 120, 20, 60 | tales |
| `bell.alarmGlowSeconds`, `.alarmCooldown`, `.comeHomeSeconds`, `.comeHomeCooldown`, `.homewardChannelSeconds` | 10, 30, 60, 300, 5 | tales, homebuilder |
| `stages[6]` `{build, offering}` | `proficiency:hearth/stage/<firepit..heart_of_the_home>` block tags; flint and steel, copper, iron, gold, diamond block, heart of the sea | stages |
| `comfort.kinds`, `.scanCooldownSeconds`, `.rescanSeconds` | the 12 kinds, 30, 300 | comfort |
| `siege.requiresFirstHorn`, `cooldownPlayHours`, `cooldownDays`, `rampHours`, `pMax`, `pityHours`, `hornCooldownPlayHours`, `postponeNights`, `waves`, `baseMobs`, `perExtraPlayer`, `burst`, `streakBonus`, `streakCap`, `dousedLoss` | spec | sieges |
| `siege.warningTicks`, `hornMinDelaySeconds`, `nextWaveAt`, `rollSeconds`, `mobs` | 24000, 120, 0.5, 60, the mix by ceiling | sieges |
| `siege.douseRadius`, `dousePerMobSecond`, `douseMax`, `abandonGraceSeconds`, `spawnClearance`, `onePerWave` (added by Sieges) | 3.0, 1.0, 100, 10, 6, `["minecraft:evoker"]` | sieges |

The stage tags (`#proficiency:hearth/stage/*`, `#proficiency:hearth/stone`, `/brick`) belong to
stages; the comfort tags (`#proficiency:comfort/<kind>`, `/fireplace`, `#proficiency:hearth/character_ignore`,
`/character_family`) to comfort and exist since the comfort lane (see "Comfort lane" below). The stage tags
do not exist yet.

The stages lane's tags exist now (`data/proficiency/tags/block/hearth/`). Each `stages[i].build` tag is
that layer's main block: I `stage/firepit` = `#proficiency:hearth/stone` (polished stones, stone,
deepslate, tuff, blackstone and mud bricks, smooth stone), II `stage/hearth` = `#proficiency:hearth/brick`
(bricks, mud bricks, `#minecraft:terracotta`), III `stage/homestead` = `#minecraft:logs`, IV `stage/hall`
= `#minecraft:slabs` + `#minecraft:stairs` (the roof ring), V `stage/gathering_place` =
`#minecraft:banners` (wall banners on the pillars' outer faces), VI `stage/heart_of_the_home` =
lodestone. The second parts have their own tags: `#proficiency:hearth/lantern` (IV, hanging) and
`#proficiency:hearth/bell` (V, hung from a pillar or the roof ring). An `offering` is an item id or an
item tag (`#ns:path`); a damageable offering (flint and steel) loses durability, any other is consumed.

## Config

Server (`proficiency-server.toml`, section `hearth`): `enabled` (true), `range` (24.0, the radius),
`rangeBelow` (8), `rangeAbove` (16), `minSpacing` (64), `sieges` (true). Read with
`ProficiencyConfig.hearthEnabled()`, `hearthRange()`, `hearthRangeBelow()`, `hearthRangeAbove()`,
`hearthMinSpacing()`, `hearthSieges()`; the cylinder with `HearthRange.range()`.

Client (`proficiency-client.toml`, section `hearth`): `tracker` (false), `trackerY` (0.5),
`showRange` (true), `siegePanel` (true), `siegePanelY` (0.06, added by polish-121: the panel's top, so it can sit under a top-left minimap): `ProficiencyClientConfig.hearthTracker()` etc.

Server, section `homebuilder` (the homebuilder lane): `share` (0.15), `stageXp` (150, times the
stage), `comfortXp` (60), `characterXp` (10), `siegeXp` (300), `feastXp` (25), `talesShare` (0.10),
`chronicleXp` (20), `houseProudTicks` (600), `houseProudCooldownTicks` (7200):
`ProficiencyConfig.homeShare()` etc.

## Telemetry

`TelemetryService.hearth(player or null, kind, label, source, value)` counts one event and sums the
value per (player, kind, label, source) on a `"type":"hearth"` line. Kinds (`Telemetry.HEARTH_KINDS`):
`hearth_charge` (label = pool, source = skill id or ledger source: player and source fields),
`hearth_boon`, `hearth_warm`, `streak_paused`, `hearth_absorb`, `hearth_tales`, `hearth_siege`,
`tale_rating`, `homeward` (homebuilder: label `use` / `use_other_dimension` with source `channel` or
`bell` and the distance saved, label `cancel` with source `hurt` or `moved`). Homebuilder's share is the
XP kind `home`: grant it with source `proficiency.xplog.source.home`. `hearth_charge` with source `capped` is charge the hourly cap turned
away (label = pool); every other `hearth_charge` source is a ledger source. `balance_report.py` ends with a Hearth section (charge lane, completed by the integration task):
charge per pool per active hour and per hour at home, `capped`, time to each tier, charge per pool and
source, feeders and sources, the share at home from `streak_paused`, Warmth fills and the bed share
(`hearth_warm`), temp HP spent away and the boss share (`hearth_absorb`), Tales banked, paid and lost,
the Chronicle's flat bonus and ratings (`hearth_tales`, `tale_rating`, ledger source `chronicle`),
sieges per 10 h with every outcome, kills, unearned falls and postponements (`hearth_siege`),
Warmth-weighted boon time (`hearth_boon`), Homebuilder's XP by kind and share of all XP (XP kinds `home`
and `action`) and Homeward (`homeward`), then a table of every Hearth kind as counted. Test:
`python3 tools/tests/test_balance_hearth.py` against `tools/tests/fixtures/balance/hearthtown/`.

## Lane ownership

| Lane | Owns (package `hearth.<lane>` unless noted) |
|---|---|
| charge | The `GRANT` body (mirror into pools, `addCapped`, `queueGain`), the hold queue, the combat filter (siege and raid kills only), the ledger, decay and cap enforcement (`tickDecay` in the Hearth tick), telemetry emission, `/proficiency hearth ledger`, the `LEDGER` request, the `hearth_ledger` and `hearth_tracker` sends |
| stages | Lighting (campfire on the platform + offering), the structure check, stages and offerings, spacing (`HearthIndexData.tooClose(level, pos, minSpacing)`), breaking (sneak-hold, drops, chat line), the in-range tick body (`PlayerHearth.seenBy`), hints and the discovery banner, the `hearth_state` sends on enter and panel open, the stage tags, the loot table |
| comfort | The Comfort and Character scan, the comfort tags, Character, the pack mappings (Reclamation, Pandowo datapacks), `COMFORT_KIND_SEEN`/`CHARACTER_ID_SEEN` |
| effects | Warmth, boons, temp HP, the streak hold, Workshop and the furnace/brewing/exhaustion/sleep mixins (including the Fabric `PlayerWakeUpEvent` source), Provisioned and readiness, the `hearth_hud` send |
| client | `hearth.client`: the HUD glyph, the tracker (incl. the `SkillHud`/`XpGainDots` launch-point refactor), the panel and its ledger tab, the range ring, the cues, the `HearthClient` implementation |
| tales | Tales, the Chronicle, the Feast, the context bell (`hearth_ping`), the `/mcjournal` step, `/proficiency hearth tale` |
| sieges | Everything siege: the play clock (`addPlayTicks`), the due time and siege clocks on `HearthState` (`setSiegeDue`, `setLastSiegeEnd`; the world mirror follows by itself), the roll, the horn and trophy behaviour (`sieges.SiegeHornItem`, `sieges.SiegeTrophyBlock`), waves and AI, the siege panel's server side (`siege_panel`), `SIEGE_DEFENDED` |
| homebuilder | `homebuilder`: the skill, its tree (in `tools/talents_spec.py`, generated), passive, magnet, proc, ability, synergy; `homebuilder.net`; `client/HomebuilderHud`; the Homebuilder slots of `client/AbilityWheelScreen` |
| art, art2 (parallel) | Blockstates, models, textures, the font glyphs, the GUI icons; no Java |
| integration | `event/ExhaustionWatch`, `hearth/HearthLook` and the block's `GLOW`/`FEED`, `gametest/HearthTestGuard` and `HearthIntegrationGameTests`, the balance report's Hearth section, docs/hearth-build-log/integration.md (the abuse-check table) |

Every lane's definition of done includes the spec's "Finish and polish" rule (UI, animation, icons,
sounds, tooltips and text at the 1.1.0 standard, reusing the mod's own FX classes; icon paths under
`textures/gui/hearth/`).

Shared files (registries, `PlayerHearth`/`HearthState`/`HearthData`, payloads, lang, `SkillService`,
`PlayerLifecycleEvents`, mixin jsons, `Subscribers`, `NeoForgeEventBridge`) take minimal additive
edits only.

## Lang keys

The client lane's keys are `proficiency.hearth.client.*`: what each stage's build is (`stage.<1..6>.build`,
`stage.none`), boon lines (`boon.*`), the HUD, the panel, keepers tab, siege panel, bell calls (`call.<call id>`)
and the siege banner. It reuses the stages lane's stage names and `stages.character`, the comfort lane's kind
names and descriptions, and the charge lane's ledger source names.

Each lane adds en_us and natural Polish (pl_pl) for its own keys only, prefixed
`proficiency.hearth.<lane>.*` (or `proficiency.homebuilder.*`), appended at the end of the file. The
other ten languages come in the lang task. **Superseded (user, 2026-10-09):** Hearth and Homebuilder
strings ship in English and Polish only; the other ten languages fall back to English and get no new
keys. They keep the 16 Homebuilder keys added before that rule (the skill, proc and ability names, ten
`.short` lines and the three `proficiency.stat.home_*` lines), because both `LangFilesTest`s require
every `.short` and `proficiency.stat.*` key in every language. Polish
words (integration): the Hearth's charge is "żar"; "ładunki" are the survival bonus's stacks; no
gendered second-person forms. The foundation added: `block.proficiency.hearth`,
`block.proficiency.siege_trophy`, `item.proficiency.siege_horn`, `proficiency.hearth.pool.<id>`, and
`proficiency.configuration.hearth[.button|.tracker|.trackerY|.showRange|.siegePanel|.siegePanelY][.tooltip]`.
No key ending in `.short` unless every language gets it (`LangFilesTest`).
Stages added `proficiency.hearth.stages.*`: `name.<1..6>` (Firepit .. Heart of the Home; the panel may
use them), `dir.<dir>`, `problem.<kind>` (args: found, needed, direction), `character` ("Character
%s/%s"), `character.why.<3..6>`, the chat and action-bar lines, and `banner.first/stage/by` (the banner
kickers).
The charge lane added `proficiency.hearth.charge.source.<siege|tales|chronicle|feast>` (ledger source
names: reuse them in the panel's keepers tab, with skill names for skill sources), the tier-up line
`proficiency.hearth.charge.tier_up[.alone|.hover|.hover_line]` and the ledger table
`proficiency.hearth.charge.ledger.*`.

Tales lane keys: `proficiency.hearth.tales.*` (the homecoming line `paid.both/walked/places/plain`, `kept`, `lost`, `capped`, `feast.*`, `bell.*`, `chronicle.filed*`, `retold*`, `command.*`, `panel.*`). The four `paid.*` variants take the same four arguments (blocks, places, amount, pool name); `HearthTalesWireTest` checks every key against en_us and pl_pl and that both languages use the same placeholders.

## Comfort lane (m01)

Package `dev.amman.proficiency.hearth.comfort` (common) and `...hearth.comfort` (core, pure). Everything
below is additive; decisions: docs/hearth-build-log/comfort.md.

**The mark.** `PlacedBlocks.markComfort(level, pos, state)` is written by the comfort lane's `BLOCK_PLACED`
listener for every block a *player* placed (fake players included, creative included), whatever
`placedBlocksPayXp` says; `PlacedBlocks.clearComfort` by its `BLOCK_BROKEN` listener. It lives in a third
store of `PlacedBlocksData` (`"comfort"` in `proficiency_placed_blocks.dat`) and rides on pistons and falling
blocks like the gathering mark (`PlacedBlocks.pickUp` returns which marks the falling block carried,
`MARK_GATHERING` | `MARK_COMFORT`, and `land(level, pos, state, marks)` puts back exactly those: with
`placedBlocksPayXp` on only the comfort mark exists and a landed block gains no gathering mark).
`PlacedBlocks.rawComfortMarked` and `forEachPlaced(level, chunkX, chunkZ, minY, maxY, visitor)` (the comfort
marks, then the gathering marks from before the comfort mark existed that have no comfort mark) read it. A break
clears the mark of the block and of the other half of a door, tall plant or bed (`clearComfortPlacement`). A mark
whose block is gone is stale: dropped by the next scan of a Hearth that covers it, or when its chunk next loads
(`PlacedBlocks.sweepComfort`, called from the shared-bus `ChunkLoadEvent`, which both loaders post), so blocks
destroyed outside every Hearth's range do not leave marks in the save for good. With `hearth.enabled = false`
nothing is marked or swept. Core: `PlacedBlockStore.forEachInChunk`.

**The scan.** `HearthComfort.scanNow(level, hearthEntity)` (false and no change when a chunk of the range is not
loaded: the scan never loads one) and `HearthComfort.requestScan(level, hearthPos)` (flags a change; the Hearth's
own second runs the scan once `comfort.scanCooldownSeconds` (30) since the last one are over).
`ScanPolicy.due` (core) is the clock: a Hearth that never scanned, a flagged change after the cooldown, every
`comfort.rescanSeconds` (300) while a non-spectator is in range. Changes are flagged by `BLOCK_PLACED`,
`BLOCK_BROKEN`, a painting, item frame or armour stand joining the level (not loaded from a chunk) or being taken
down (killed or discarded, not saved away with its chunk: shared-bus `EntityLeaveLevelEvent`, NeoForge relay of its
own event, Fabric API `ENTITY_UNLOAD`) and the new shared-bus `ExplosionDetonateEvent` (NeoForge relay
`ExplosionEvent.Detonate`, Fabric mixin at the end of `Explosion.explode`). Nothing reports a frame being filled or a
stand dressed, so a scan that saw an empty frame or a bare stand (`ComfortTally.Result.pending`) flags the next
rescan again each cooldown for the first two minutes after it showed up (the window is not renewed by the scans it
causes; a new change renews it). A scan that finds a chunk of the range unloaded is not tried again for a cooldown
(`ComfortRequests.defer`), the change flag stays. The scan walks the comfort marks of the chunk columns the cylinder
touches, not the world, so natural terrain, villages and chest contents cannot count. The pending flags and placer
notes (`ComfortRequests`, core, in memory) are dropped when the Hearth goes: `HearthIndexData.forget` calls
`HearthComfort.hearthGone`, so every code path that removes a Hearth must keep calling `forget`.

**What counts.**

- *Comfort* = the distinct kinds (`HearthData.comfort().kinds()`, 12 shipped) among marked blocks **indoors**
  (`!level.canSeeSky(pos.above())`: the sky light on the block's top face; the block's own position holds no
  sky light when the block is opaque, so a barrel on a lawn would read as "indoors"; a dimension without sky
  light, the Nether and the End, counts everything as indoors) and the entities of one AABB query: entity types
  in the entity type tag `proficiency:comfort/<kind>` (shipped: `art` = painting, item frame, glow item frame;
  `trophy` = armor stand), an item frame only while it holds an item, an armour stand only while any armour slot
  holds something.
- *Character* = the distinct entries among marked blocks in the cylinder, inside or out: the item that places
  the block (`block.asItem()`), not `#proficiency:hearth/character_ignore` (dirt, coarse dirt, grass block,
  cobblestone, cobbled deepslate, netherrack, gravel, sand, red sand, the Hearth block), folded by
  `#proficiency:hearth/character_family/<name>` (one entry for every member, entry `#<tag id>`) or
  `#proficiency:hearth/character_family` (numbered variants `name_1`, `name_2` fold into `name`). Stored capped at
  `character.max`; the uncapped entries go to the milestones.
- *The Hearth's own structure never counts*: whatever stands at one of its fixed positions
  (`HearthStructure.isPart`: the fire, the 5x5 platform, the pillars, the roof ring, the lantern and banner spots) and
  the bell (`#proficiency:hearth/bell`) anywhere in the 7x7x6 box (`HearthStructure.inBox`). A chair or a lantern next
  to the fire is furniture; the same block placed on a structure position is the structure.
  The campfire a player placed and lit is a stale mark.

**The cache and the sends.** `HearthState.setComfortKinds` / `setCharacter` / `setLastScan`, then
`HearthNet.stateOf(hearth, UPDATE)` to every player in range when Comfort or Character changed. A kind that
starts to count (not on the Hearth's first scan) also plays `ComfortCue`: a chime only the people at home hear
(pitch climbs with Comfort), 8 happy-villager sparks over the Hearth, the gold action-bar line
`proficiency.hearth.comfort.gained` ("Comfort 5/12: +Seat"), and for the twelfth kind the level-up fanfare and
`proficiency.hearth.comfort.complete`. A client that shows its own toast for Comfort gains should replace this
cue (delete the `ComfortCue.play` call in `HearthComfort.apply`).

**Milestones.** `COMFORT_KIND_SEEN` / `CHARACTER_ID_SEEN` fire from the scan the first time a kind or an entry is
present at a Hearth (`HearthState.markComfortSeen` / `markCharacterSeen`, saved), with the placer when the lane saw a
non-fake player place a block worth it (remembered in memory until the milestone fires, so furniture placed before
the roof still names its placer; up to 8 notes per kind or id, one per block position, dropped when a player breaks
the block) and null otherwise (entities, the first scan of an old base, a restart in between). The placer is the
oldest one whose block still stands where it was placed and still counts for it (indoors for a kind, not the Hearth's
own structure); a block that was blown up, burnt or stands outdoors credits nobody. Character ids are
`minecraft:oak_stairs` or `#<tag id>` for a family.

**Names and icons for the client lane.** Core `ComfortKinds`: `translationKey(kind)`
(`proficiency.hearth.comfort.kind.<kind>`), `descriptionKey(kind)` (`.desc`, what counts), `iconPath(kind)`
(`textures/gui/hearth/comfort/<kind>.png`), `shipped(kind)`, `fallbackName(kind)` (a readable name for a kind a pack
adds; use `Component.translatableWithFallback`), `missing(present, all)`. `HearthComfort.missingKinds(state)`.
`ComfortScanner.worthOf(state)` says which kinds and which Character entry a block would be worth.

**Command.** `/proficiency hearth comfort` (anyone; reads the Hearth whose range holds you from the index) prints the
cached Comfort, the kinds counted and missing, and Character with what the next stage wants;
`/proficiency hearth comfort rescan` (op 2) scans first.

**Tags and data** (all under `data/proficiency/tags/`): `block/comfort/<12 kinds>.json` (vanilla members of the
spec), `block/comfort/fireplace.json` (empty: packs add; counts as `fire`), `block/hearth/character_ignore.json`,
`block/hearth/character_family.json` (empty), `entity_type/comfort/art.json` and `trophy.json`. Packs extend them with
plain data: `neoforge/packs/pandowo/datapack` (Handcrafted, Another Furniture, Farmer's Delight) and
`forge-1.20.1/packs/reclamation/datapack` (Macaw's Furniture, Farmer's Delight), generated and verified against the
jars by `tools/packs/gen_comfort_tags.py`. Hearth data keys read: `comfort.kinds`, `comfort.scanCooldownSeconds`,
`comfort.rescanSeconds`, `character.max`, `stages[].build`.

**Lang** (en_us + pl_pl): `proficiency.hearth.comfort.kind.<kind>[.desc]` (12), `proficiency.hearth.comfort.gained`,
`.complete`, `proficiency.hearth.comfort.command.*`.

**Tests.** Core `hearth/comfort/ComfortRulesTest`, `ComfortDataFilesTest`, `PlacedBlockStoreTest`; GameTests
`gametest/HearthComfortGameTests` (both loaders): kind tags, the mark, the lawn (abuse check 4), a roofed house,
`placedBlocksPayXp` on, chest contents, stale marks, entities, the range, Character and the dirt hut (abuse check
15), the Hearth's own structure, the Hearth's own second after a placement and a break, an explosion. A GameTest template is the
5x5 `proficiency:empty`: its floor is at y = 1, a neighbour's blocks and Hearth are in range (every test cleans up).

## The art contract (fixed)

| Asset | Shape |
|---|---|
| Blockstate `proficiency:hearth` | `stage` 1..6 x `smouldering` true/false (integration: the block also has `glow` 0..6 and `feed` 0..5; the file's variant keys name only `stage` and `smouldering`, so every `glow`/`feed` uses the same model) |
| Blockstate `proficiency:siege_trophy` | `tier` 3..6 x `facing` north/south/west/east (the way it faces, away from its wall) |
| Item `proficiency:siege_horn` | an item model |
| Item `proficiency:siege_trophy` | the block item |
| Font glyphs (`assets/minecraft/font/default.json`) | U+E010..U+E018 Warmth frames 0..8, U+E019 siege, U+E01A Tales, U+E01B Provisioned, U+E01C lock, U+E01D bell, U+E01E house (draw with a white text colour; all 7 px wide) |
| GUI icons (`textures/gui/hearth/<name>.png`, 16x16, drawn like `SkillIcons`: nearest sampling, 8 or 16 GUI px) | `pool/<hearty,regen,fortified,workshop,homecoming>`, `comfort/<bed,seat,carpet,banner,fire,light,plant,art,books,music,storage,trophy>`, `stage/<1..6>`, `warmth`, `siege`, `tales`, `provisioned`, `character`, `comfort`, `ledger`, `bell`, `horn`, `trophy`. `horn` and `trophy` are the item sprites; `comfort` is the Comfort n/12 icon, `comfort/trophy` the comfort kind (a cup), `trophy` the Siege Trophy plaque. A comfort kind a pack adds has no icon: draw its row without one. Generated by `tools/gen_hearth_art.py` from `tools/hearth_icon_maps.py` (docs/hearth-build-log/art2.md) |
| Render layer | The Hearth is cutout: `render_type` in the models (NeoForge, Forge), `BlockRenderLayerMap` in `ProficiencyFabricClient` (Fabric) |

## Stubs

Every stub returns the neutral value and carries `// LANE <name>`: the eight init classes, the
horn's `use`, the trophy's tier. `grep -rn "// LANE" common/src` lists them.

## Tests and traps

- **Every Hearth GameTest starts with `HearthTestGuard.watch(helper)`** (integration). On the test's
  end, passed or failed, it takes away every Hearth within 32 blocks (a running siege is cancelled
  first), every comfort-marked block in the structure (and one block around it) and the mock players the
  test made, and logs at INFO what it had to take. It relies on each Hearth test having its own batch.
  A Hearth left burning flags later tests' players as home and refuses their lightings.
- Integration GameTests (`gametest/HearthIntegrationGameTests`, both loaders): the exhaustion watcher at
  the food tick's head and tail (Night Owl, Lean Times; regeneration on a tick that took a point; Long Haul's
  refund), the block's glow and feed, the guard.
- Core: `core/src/test/.../hearth/*Test.java`, `telemetry/HearthTelemetryTest`. Common:
  `hearth/HearthWireTest` (positions, every payload codec, config defaults, protocol 7).
- GameTests: `gametest/HearthGameTests` (both loaders), `NeoForgeHearthGameTests` (NeoForge's
  wake-up event reaches `SLEEP`), `gametest/HearthChargeGameTests` (charge lane, both loaders). Each Hearth-placing test has its own `batch`, because the `empty`
  template is tiny and a neighbour's Hearth would be "in range".
- A mock player has 60 ticks of spawn protection: clear `spawnInvulnerableTime` (reflection, see
  `HearthGameTests.clearSpawnProtection`) before testing damage.
- A real sleep in a test: load `SleepTimer` 100 into the player and `setSleepingPos`, then wake.
- Fabric's wake-up event comes from the effects lane's `HearthWakeUpMixin` (since the effects lane),
  so `SLEEP` fires on both loaders; `HearthEffectsGameTests.warmthRisesDrainsAndASleepFillsIt`
  covers it on each.
- Effects GameTests (`gametest/HearthEffectsGameTests`, both loaders): each test primes its Hearth
  and steps `PlayerEffects.second` by hand in one tick, so the other lanes' Hearth ticks (Comfort
  scans, the structure check) cannot move the state between priming and checking. A mock player
  logs in when made: `PlayerEffects.endGrace` ends the login hold.
- `HearthHooks` are silent with `hearth.enabled = false` except `PLAYER_SECOND` and `DEATH`.
- `GRANT` also sees first-time lines and area-tool extras: filter with `firstTime()` and `aoe()`. A
  listener that grants XP itself re-enters `GRANT`: guard against your own skill and source (Homebuilder
  pays its share later through a pot, as Nightwalker does).
- `HearthReload.lastResult()` is the last load (files, problems); a lane that ships its own file in
  another namespace shows up there.
- Stages GameTests (`gametest/HearthStagesGameTests`, both loaders): a mock player's `isSpectator()`
  is always false and `isCreative()` always true whatever its game mode, so code that must tell them
  apart reads `getAbilities().instabuild` or the game mode. Mock players are not ticked: drive a
  break by hand with `gameMode.handleBlockBreakAction` and `gameMode.tick()`. Since s01x the server
  counts the sneak-hold once a server tick, so a Hearth break needs real ticks between the start and
  the stop (`runAfterDelay`); the level does tick a mock player's `gameMode` (its connection is what is
  not ticked). The structure check runs
  at most every 30 s; a test calls `HearthStageCheck.recheckNow` (or backdates `lastCheck`). A test that
  lights a Hearth sets `hearth.minSpacing` small around the click (`ConfigValue.set`, restored in the
  same tick), so a Hearth another test left behind cannot refuse it. Character belongs to the comfort
  lane, whose scan of unmarked test blocks may reset it between ticks: set it right before the click.
- Charge lane: the mirror needs `HearthRange.homeOf` (the in-range flag the stages lane writes): a test
  sets it with `PlayerHearth.seenBy`. Mock players all share one name, so count ledger rows, not names.
  The charge lane keeps `laneData("charge")` keys `tiers` and `told` (the last tiers and the high-water
  of told tiers); change a tier's inputs and call `ChargeTiers.check` if no charge moved.
- Masonry and Decorating shares wait for `BLOCK_PLACED` in the same tick as their grant: a GameTest calls
  `ExpansionEvents.placed` and then `HearthHooks.onBlockPlaced` for the same position.

## Porting to Forge 1.20.1 (what every lane noted; integration, 2026-10-09)

The Forge line compiles `core/` (the whole pure model, tests included) and the shared assets and
lang, and nothing of `common/` but the ten Hearth classes whose source is the same on both versions
(added by the forge-foundation fix round: `forge-1.20.1/build.gradle` `copySharedHearth`;
docs/HEARTH-FORGE-CONTRACT.md "Shared sources"; a change to one of them must compile on 1.20.1 too). Each `common/.../hearth/<lane>` and `common/.../homebuilder` package
is hand-copied there, as the rest of the 1.20.1 line was; the forge-foundation lane (ff01) did the
foundation, and docs/HEARTH-FORGE-CONTRACT.md says what is where on that line. What each 1.21.1 lane
noted for the copy, by lane:

| Lane | Notes for the port |
|---|---|
| foundation | Done by forge-foundation (ff01). Forge has priorities like NeoForge: `BLOCK_PLACED` and `BLOCK_USE` at LOWEST, `BLOCK_BROKEN` at LOWEST and not for canceled breaks |
| charge | The `charge` package; `HearthRaiderMixin` must come too (raid membership at the head of `Raider.die`; without it raid kills feed nothing). `Raider.getCurrentRaid` and `ServerLevel.getRaidAt` exist on 1.20.1 |
| stages | The sneak-hold counter needs a `ServerTickEvent` listener; `HearthStructure` is core |
| comfort | `PlacedBlocks.pickUp/land` with the marks bitmask, `clearComfortPlacement`, `sweepComfort` (from `ChunkEvent.Load`), the entity removal flag (Forge's `EntityLeaveLevelEvent`), `ComfortRequests` (core); `HearthIndexData.forget` must keep calling `HearthComfort.hearthGone` |
| effects | No `max_absorption` attribute: keep the Hearth's share in `PlayerHearth.absorbShare` and set the amount directly. Fortified through `LivingHurtEvent`, sleep through `PlayerWakeUpEvent`, the 1.20.1 furnace and brewing field names, the streak hold in the Forge `SkillsScreen` copy. Edge flags and the login-hold HUD silence are server-side and port as they are. `HearthFoodDataMixin` targets `FoodData.tick(Player)`, the same name on 1.20.1 |
| integration | `ExhaustionWatch`: the Forge copies of `NightwalkerEvents` (Night Owl) and `TalentEnduranceEvents` (Lean Times) still run their own watchers after the player tick; port them as shares of the one watcher with the food tick's head and tail hooks (1.21.1: `beforeFoodTick` / `afterFoodTick`), and send Long Haul's refund (`MovementEvents.refundSprintHunger`, still the whole unscaled share on 1.20.1) through `ExhaustionWatch.refund`. `HearthLook` and the block's `glow`/`feed` properties: the forge-foundation `HearthBlock` copy has only `stage` and `smouldering`, and its light still follows the stage (the blockstate file is shared and ignores the two new properties). `HearthTestGuard` for the Forge GameTests (Forge 1.20.1 dev runs official names too) |
| tales | `TalesBank`, `HearthFeast`, `HearthChronicle`, `HearthBell`, `TalesPayInHud`; the core parts (`TalesMath`, `TaleTrip`, `FeastWindow`, `ChronicleBook`, `ChronicleText`, `BellRules`, `HearthKey`) already compile there |
| sieges | The trophy's tier needs `BlockStateTag` (no `block_state` component); the roster needs an `EntityLeaveLevelEvent` relay that keeps the removal reason; check `Zombie.ZombieGroupData(isBaby, canSpawnJockey)`; `Mob.goalSelector` through an access transformer |
| homebuilder | Reach through `ForgeMod.BLOCK_REACH`; Homeward's hit check on a hit that landed (`LivingDamageEvent`); Scaffolder softens only `DamageTypes.FALL` while the fall distance is counted (check the 1.20.1 pearl code, which also resets it before the hit) |
| client | `hearth.client` and the shared client edits: `DotFlight`, `HudKit`, `DiscoveryBanner.showSiege/top`, the `OverlayFallback` hook (`HearthChat.hold`), the `SkillHud` anchor; the chat settle calls in `TrackerHud.render` and `HearthClientEvents` |
| art | The Hearth is cutout through `render_type` in its models on Forge (Fabric alone needs `BlockRenderLayerMap`) |

**Forge foundation fix round (ff01, 2026-10-09), changes on 1.21.1 too.** `HearthBlock` has no
`useWithoutItem` override any more: it answered SUCCESS on the client for every main-hand click, so
bread, bows, shields and off-hand torches did nothing on a Hearth; `useItemOn` alone swallows what the
server answers (vanilla calls it with the empty stack too). `data/minecraft/tags/block/wither_immune`
and `dragon_immune` list `proficiency:hearth`. `stages.HearthBreaking.destroyProgress` reads 0 on the
server below `MIN_SNEAKED` (42 ticks, vanilla's 70%), because vanilla's late finish after a refused STOP
multiplied by the ticks since the older swing and finished a second sneaking swing after a few ticks
(GameTest `HearthStagesGameTests#aRefusedFinishDoesNotShortenTheNextHold`). On Forge the integration
row's `HearthLook`/`glow`/`feed` and the stages row's sneak-hold counter are done by the foundation.

**Forge extras (fx01, 2026-10-10).** Sieges, tales and Homebuilder are on Forge 1.20.1 with common's API;
what changed on the way (events, NBT books and compasses, `ForgeMod.BLOCK_REACH`, Homeward's absorption
read) is in docs/HEARTH-FORGE-CONTRACT.md "forge-extras: what is in place" and
docs/hearth-build-log/forge-extras.md. No 1.21.1 file changed.

Decisions taken while building this: docs/hearth-build-log/foundation.md.
The stages lane's: docs/hearth-build-log/stages.md.
