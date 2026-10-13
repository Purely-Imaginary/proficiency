# Hearth on Forge 1.20.1: the foundation and the three Forge lanes

Status 2026-10-09: written by the Forge foundation task (lane `forge-foundation`, ff01) and merged to
`master` with the code it describes. The design is docs/HEARTH-SPEC.md and docs/HOMEBUILDER-SPEC.md.
The shared API is docs/HEARTH-CONTRACT.md (the 1.21.1 lines). This page says what the Forge 1.20.1
line has, how a 1.21.1 class maps to it, and which Forge lane owns what. **Extend it additively
only.** A Forge lane that adds a shared surface adds a line here in the same commit.

The Forge line cannot compile `common/` as a whole (README, "Where things live"). Each lane hand-copies
common's classes into `forge-1.20.1/src/main/java/dev/amman/proficiency/<same package>/`, keeps the
public API word for word, and applies the API map below. A port is done when its common GameTests pass
on Forge too (as `@GameTestHolder` classes, see "Tests"). The exception is the short list of Hearth
classes whose source is the same on both versions: the build compiles common's own files (see
"Shared sources").

## What the foundation ported

| Piece | Forge 1.20.1 | Notes |
|---|---|---|
| Registries | `hearth.HearthBlocks`: `HEARTH` (block, no item), `HEARTH_ENTITY`, `SIEGE_TROPHY`, `SIEGE_TROPHY_ITEM`, `SIEGE_HORN` | Forge `DeferredRegister`s (`BLOCKS`, `BLOCK_ENTITIES`, `ITEMS`), registered from the `Proficiency` constructor. Each field is a `RegistryObject`, a `Supplier`: `HearthBlocks.HEARTH.get()` reads as on 1.21.1. Horn and trophy are in the creative tab |
| The block | `hearth.HearthBlock`: `STAGE` 1..6, `SMOULDERING`, `GLOW` 0..6 and `FEED` 0..5 (1.21.1's integration task; kept by `HearthLook`), light by `HearthRules.chargeLight(glow, smouldering)`, indexes itself on place, `HearthIndexData.forget` on replace | One `use`: on the client `SUCCESS` only when `HearthBlock.predicts(hand, held)` (main hand and `stages.HearthOfferings.answers(held)`: an empty hand, flint and steel, later the offerings), `PASS` otherwise and always on the server, as 1.21.1's `useItemOn` (fix round: every main-hand click was swallowed, so bread, bows, shields and off-hand torches did nothing on a Hearth). `minecraft:wither_immune` and `dragon_immune` list it on both lines. Break speed and the swing call `stages.HearthBreaking` (the server's sneak count is ported); the client ticker calls the client stub `HearthBlockFx` |
| The block entity | `hearth.HearthBlockEntity`: `state()`, `setState`, `laneData(lane)`, `packedPos()`, `key()`, static `key(level, pos)`, the once-a-second tick (index, `HEARTH_TICK`, siege mirror) | `saveAdditional(tag)` / `load(tag)`; the same NBT keys as 1.21.1 (`hearth`, `lanes`) |
| Trophy | `hearth.sieges.SiegeTrophyBlock` (whole: wall placement, survive, shape, pick-block); `SiegeTrophyItem.of(tier, day)`, `tier(stack)`, `day(stack)` | The tier is the vanilla `BlockStateTag` (1.21.1's `block_state` component); `BlockItem` applies it when hung and the loot table's `copy_state` writes it back. The day is under `ProficiencyData` (`item.ItemData.CUSTOM`) |
| Player data | `hearth.HearthAttachments`: capability `proficiency:hearth` holding `PlayerHearth`, saved with `PlayerHearth.CODEC` under `ForgeCaps` | Registered from the `Proficiency` constructor. `of(player)` returns a throwaway when the capability is invalidated, like `ProficiencyAttachments` |
| Death and End exit | `HearthCommonEvents.onClone`: `PlayerLifecycleEvents.readOldCap(event, HearthAttachments.HEARTH)`, `copyFrom`, then `clearOnDeath` + `HearthHooks.onDeath` on a death | Forge invalidates the dead body's capabilities before `Clone`; every Clone handler reads through `readOldCap` (revive, read, invalidate in a `finally`) |
| World data | `HearthIndexData` (per dimension, `proficiency_hearths`), `HearthWorldData` (overworld, `proficiency_hearth_world`: play clock, Chronicle register, siege due mirror) | 1.20.1 `SavedData`: `computeIfAbsent(load, create, name)`, `save(tag)` |
| Hooks and events | `HearthHooks` (all 11 hooks, same listener types; `INCOMING_DAMAGE` gets `compat.LivingIncomingDamageEvent`), `HearthEvents` (all 8 signals), `Hearth.init` (calls every lane's init, then `HearthLook.init()` last) | Common's own sources (see "Shared sources"). See "Hook call sites" |
| Range | `HearthRange` | Common's own source |
| Data reload | `HearthReload`: reloads on the resource-manager swap, checked every server tick | Common's own source |
| Glow and feed | `HearthLook`: once a second, last on `HEARTH_TICK`, sets `GLOW` and `FEED` when they changed (`UPDATE_CLIENTS` only) | Common's own source. The motes' tint by `FEED` is forge-client's `HearthBlockFx` |
| Sneak-hold | `stages.HearthBreaking`: the holds, the server's break speed (the sneaked share; 0 below 70% sneaked), `tickHolds` (from `HearthCommonEvents.onServerTick`), `forgetAll` (server stop), `sneakTicks` | Ported so the block's promise holds on a foundation build. forge-server adds the action-bar hint in `onAttack` and `onBroken`; it does not register the tick or the stop again |
| Network | protocol `"7"`; 11 payloads appended to `ProficiencyNetwork`'s one channel; `HearthNet` (sends + `stateOf` + gain queue + HUD dirty mark), `HearthClient` / `HearthClients` (common's own sources; no-op until the client lane sets one), `HomebuilderNet` (`send`, `client()`, `setClient`) | See "Payloads" |
| Config | server `[hearth]` and `[homebuilder]`; client `[hearth]` (`tracker`, `trackerY`, `showRange`, `siegePanel`), shown by `ClientConfigScreen` under the "Hearth" header | Same keys, defaults and getters as 1.21.1 |
| Telemetry | `TelemetryService.hearth(player, kind, label, source, value)` and `hearth(uuid, name, ...)` | Kinds are core's `Telemetry.HEARTH_KINDS` |
| SpawnOrigin | `SIEGE`, `tagSiege(mob)`, `isSiege(entity)` | In the entity's persistent data, as the other origins |
| Discovery banner kinds | `DiscoveryPayload.HEARTH` (5), `HEARTH_FIRST` (6), `hearthId(stage)`, `hearthStage(id)` | Wire constants only; the banner is forge-client's, the send forge-server's |
| Charge API | `hearth.charge.HearthCharge` ported whole except `init`: `credit` (both), `cut`, `siegeKill`, `siegeDefended`, `siegeBurst`, `ledgerPayload`, `sendLedger`, `mayRead`, `onRequest` (public on Forge), `forget` (public; called on logout by `HearthCommonEvents`), `remembersRequest` (tests) | So forge-extras has a working charge API in parallel. Its two seams `ChargeTick.markTrackerDirty` and `ChargeTiers.check` are stubs until forge-server ports them |
| Other cross-lane APIs | `hearth.charge.ChargeText`, `homebuilder.HomebuilderApi` (`warmWelcome`, `storyteller`, `nightWatch`): common's own sources | `ChargeText` is used by charge and by the client's demo command; `HomebuilderApi` by effects and tales |
| Click prediction | `stages.HearthOfferings`: `answers(stack)` (foundation rule: an empty stack or flint and steel), `serverOfferings(ids)` and `forgetServerOfferings()` (no-ops) | So `HearthBlock` is final and forge-client's `HearthView` compiles. forge-server ports common's whole class over it (the offerings join `answers` with the offerings themselves) |
| Homebuilder tempo | `SkillService.grantOnce`: Homebuilder pays no tempo (like Social, Nightwalker) | 1.21.1's line, so forge-extras does not edit `SkillService` |
| Client entry | `ProficiencyClientSetup.init` calls `HearthClientInit.init()` and `HomebuilderHud.install()`; leaving a world calls `HomebuilderHud.reset()`, `TalesPayInHud.reset()` and `HearthClients.leaveWorld()` | No Forge lane edits `ProficiencyClientSetup` for the Hearth |
| Assets | none copied: the build lays in common's `assets/**` (blockstates, block and item models, textures, `textures/gui/hearth/**` icons, the font glyphs U+E010..U+E01E in `assets/minecraft/font/default.json`) | The Hearth models carry `"render_type": "minecraft:cutout"`, which Forge 1.20.1 reads: no render-layer code |
| Lang | none copied: `mergeLang` takes common's en_us and pl_pl (every Hearth and Homebuilder key) and lays `forge-1.20.1/src/main/lang/*.json` over them | A Forge-only string goes in the overlay |
| Hearth data | `data/proficiency/proficiency/hearth/default.json`: laid in from common by `processResources` | The folder is the mod's own, the same on 1.20.1: one file for every line. The stage designs (`data/proficiency/hearth_designs/designs.json`, read by core's `HearthDesigns` as a classpath resource) are laid in the same way by `processResources` |
| Tags | `data/proficiency/tags/blocks/hearth/**`, `tags/blocks/comfort/**`, `tags/entity_types/comfort/**`; `data/minecraft/tags/blocks/wither_immune.json` and `dragon_immune.json` (`proficiency:hearth`, `replace: false`; common has the same under `tags/block/`) | 1.20.1's plural folders; the 1.21-only tuff blocks in `hearth/stone` are `{"id": ..., "required": false}` (a missing id fails the whole tag) |
| Loot, recipe | `loot_tables/blocks/hearth.json` (one campfire), `loot_tables/blocks/siege_trophy.json` (`copy_state` tier), `recipes/siege_horn.json` (`"result": {"item": ...}`), `advancements/recipes/combat/siege_horn.json` | |

Not ported by the foundation: every lane class marked "to port" in the class map, and every stub's body.

## Hook call sites on Forge 1.20.1

| Hook | Called from |
|---|---|
| `GRANT` | end of `SkillService.grantOnce` (after Social and Nightwalker), the same arguments as 1.21.1 |
| `PLAYER_SECOND` | `PlayerLifecycleEvents.onServerTick`, the once-a-second loop over every online player, after Nightwalker |
| `INCOMING_DAMAGE` | `HearthCommonEvents.onIncomingDamage`: the port's `compat.LivingIncomingDamageEvent`, posted by `LivingEntityDamageMixin` in `LivingEntity.hurt` at NeoForge's spot (after the invulnerable, dead and fire checks, before the shield; a changed amount applies before armour). Server side only |
| `SLEEP` | Forge's `PlayerWakeUpEvent` while `isSleepingLongEnough()` (Forge posts it at the head of `Player.stopSleepInBed`, the player still asleep) |
| `BLOCK_PLACED` | Forge's `BlockEvent.EntityPlaceEvent` at LOWEST, not canceled (a bed or door arrives once, as `EntityMultiPlaceEvent`) |
| `BLOCK_BROKEN` | Forge's `BlockEvent.BreakEvent` at LOWEST, not canceled, a `ServerPlayer` breaking |
| `ITEM_EATEN` | Forge's `LivingEntityUseItemEvent.Finish` when `getItem().getFoodProperties(player) != null` |
| `BLOCK_USE` | Forge's `PlayerInteractEvent.RightClickBlock` at LOWEST, not canceled; a non-PASS answer cancels it with that result |
| `HEARTH_TICK` | `HearthBlockEntity.serverTick`, once a second |
| `DEATH` | `HearthCommonEvents.onClone` after `clearOnDeath` |
| `REQUEST` | `ProficiencyNetwork`, on the server thread, when a `hearth_request` arrives |

`HearthCommonEvents` (`@Mod.EventBusSubscriber`) also runs the plumbing: data reload at start and on
`/reload`, the gain flush every 10 ticks, the range refresh every second, the index sweep every 30 s,
the sneak-hold count every tick (`HearthBreaking.tickHolds`) and its clear on server stop,
`HearthNet.forget` and `HearthCharge.forget` on logout. `hearth.enabled = false` silences every hook but `PLAYER_SECOND` and
`DEATH`, as on 1.21.1.

## Payloads (protocol "7")

One `SimpleChannel` (`proficiency:main`); the registration index is the wire format, so new payloads
are appended. Every record keeps common's `STREAM_CODEC` through the `net.codec.StreamCodec` shim and
has `ID` (the 1.21.1 payload id) instead of `TYPE`.

| Index | Payload | Direction | Handler |
|---|---|---|---|
| 0..10 | the older payloads (sync .. unlock) | | unchanged |
| 11 | `HearthStatePayload` (`hearth_state`) | S2C | `HearthClients.get().onState` |
| 12 | `HearthTrackerPayload` (`hearth_tracker`) | S2C | `onTracker` |
| 13 | `HearthGainPayload` (`hearth_gain`) | S2C | `onGains` |
| 14 | `HearthLedgerPayload` (`hearth_ledger`) | S2C | `onLedger` |
| 15 | `SiegePanelPayload` (`siege_panel`) | S2C | `onSiegePanel` |
| 16 | `HearthPingPayload` (`hearth_ping`) | S2C | `onPing` |
| 17 | `HearthHudPayload` (`hearth_hud`) | S2C | `HearthClients.hud` (remembers it, then `onHud`) |
| 18 | `HearthTalesPayload` (`hearth_tales`) | S2C | `onTales` (default: `TalesClient` -> `client.TalesPayInHud`) |
| 19 | `HearthRequestPayload` (`hearth_request`) | C2S | `HearthHooks.onRequest` |
| 20 | `HomebuilderPayload` (`homebuilder`) | S2C | `HomebuilderNet.client()` |
| 21 | `HomebuilderActionPayload` (`homebuilder_action`) | C2S | `homebuilder.HomebuilderEvents.onAction` |

Sends: `HearthNet.sendState/Tracker/Ledger/SiegePanel/Ping/Hud/Tales`, `HomebuilderNet.send`, or
`ProficiencyNetwork.sendIfPresent(player, payload)`. All of them skip a client without the channel
(and a GameTest mock player). `HearthNet.canReceive(player)`; a ported `canReceive(player, X.TYPE)`
becomes `canReceive(player)`: one channel carries every payload, so there is no per-payload answer
(fix round: the two-argument overload that ignored its id is gone). A new payload is appended to
`ProficiencyNetwork.registerHearth` by the foundation's rule: add, never reorder.

## Shared sources

`forge-1.20.1/build.gradle`'s `copySharedHearth` copies these from `common/src/main/java` into
`build/generated/sharedHearth` and compiles them into the Forge jar, so a master edit to one of them
reaches this line, or fails its compile, instead of drifting (fix round: drift had already happened
once, the `HearthStatePayload.Extra` offerings):

| Class | Note |
|---|---|
| `hearth/HearthEvents`, `HearthRange`, `HearthReload`, `HearthLook` | as they are |
| `hearth/HearthHooks` | the copy renames `platform.event.entity.living.LivingIncomingDamageEvent` to `compat.LivingIncomingDamageEvent` (the only difference) |
| `hearth/charge/ChargeText`, `hearth/net/HearthClient`, `HearthClients`, `PoolRow`, `homebuilder/HomebuilderApi` | as they are |
| `hearth/charge/ChargeCommands`, `ChargeGrants`, `ChargeTick`, `ChargeTiers`; `hearth/comfort/ComfortCue`, `ComfortScanner`, `ComfortTags`; `hearth/effects/HearthStations`, `HearthStreak`; `hearth/stages/HearthPresence`, `HearthStageCheck`, `StageBlocks`, `StageText` | as they are (forge-server's fix round: the lane's port had copied them word for word) |
| `hearth/client/HearthBlockFx`, `HearthChat`, `HearthClientImpl`, `HearthRangeRing`, `HearthText`, `HearthView`; `client/DotFlight`, `HudKit`, `StreakAtHearth` | as they are (forge-client's fix round: the lane's port had copied them word for word, and `HearthBlockFx` had already drifted once). Client-only classes: a 1.21.1 edit to one must compile against 1.20.1's client too (`tools/check-all.sh` builds it) |
| `hearth/client/EmberDust` | as it is (polish-forge: the range ring's full-bright mote, a client-only particle with no registered type) |

A class in the list must not also exist under `forge-1.20.1/src/main/java` (javac fails on the
duplicate). A Forge lane that needs one of them to differ on 1.20.1 either changes common's file (both
lines get it, if it compiles on both) or takes it out of the list, copies it into `forge-1.20.1/`, and
says so here. A file that leaves common fails `copySharedHearth`. The same build rule could take more
files once their sources match (a payload record does not: `ID` against `TYPE`).

## API map: 1.21.1 to Forge 1.20.1 for the Hearth

docs/FORGE-1.20.1-PORT.md "API mapping decisions" holds the general table (attributes, effects, data
components, ticks). The Hearth adds these:

| 1.21.1 (common) | Forge 1.20.1 | Who meets it |
|---|---|---|
| `NeoForge.EVENT_BUS.register(X.class)` (shared bus) | `MinecraftForge.EVENT_BUS.register(X.class)` with Forge's events; `@SubscribeEvent` from `net.minecraftforge.eventbus.api` | all |
| `platform.event.*` shared events | Forge's own: `ServerTickEvent.Post` -> `TickEvent.ServerTickEvent` with `phase == END`; `PlayerTickEvent.Post` -> `TickEvent.PlayerTickEvent` END; `RegisterCommandsEvent` (Forge's) | all |
| `LivingIncomingDamageEvent` | `compat.LivingIncomingDamageEvent` (already wired to `INCOMING_DAMAGE`) | forge-server (Fortified), forge-extras (Scaffolder) |
| `LivingDamageEvent.Post` (Homeward's channel break) | `compat.LivingDamageEvent.Post` (posted at every return of `actuallyHurt`) | forge-extras |
| `PlayerWakeUpEvent` relay / Fabric mixin | Forge's `PlayerWakeUpEvent` (already wired to `SLEEP`) | forge-server |
| `CanPlayerSleepEvent` (no sleep in range during a siege) | Forge's `PlayerSleepInBedEvent` (`setResult(BedSleepingProblem.OTHER_PROBLEM)` plus a message) | forge-extras |
| `EntityMobGriefingEvent` | Forge's `net.minecraftforge.event.entity.EntityMobGriefingEvent` (`setResult(Result.DENY)`) | forge-extras |
| `EntityLeaveLevelEvent` with a removal reason | Forge's `EntityLeaveLevelEvent`; read `event.getEntity().getRemovalReason()` there (`setRemoved` sets it before the level stops tracking the entity; a chunk unload reads `UNLOADED_TO_CHUNK` or `UNLOADED_WITH_PLAYER`, so that mob still stands) | forge-extras (siege roster), forge-server (comfort entities) |
| `ChunkLoadEvent` | Forge's `ChunkEvent.Load` (server level, `getChunk()` a `LevelChunk`) | forge-server |
| `ExplosionDetonateEvent` | Forge's `ExplosionEvent.Detonate` | forge-server |
| `EntityJoinLevelEvent.loadedFromDisk()` | Forge's `EntityJoinLevelEvent.loadedFromDisk()` | forge-server |
| `max_absorption` attribute (temp HP) | none in 1.20.1: set absorption directly and keep the Hearth's part in `PlayerHearth.absorbShare` (saved) so a later hit spends it first and nothing else is lost | forge-server |
| `BLOCK_INTERACTION_RANGE` (Homebuilder reach) | `ForgeMod.BLOCK_REACH`; modifier UUID from `compat.Attr` (id `proficiency:homebuilder_reach`) | forge-extras |
| `Attributes.MOVEMENT_SPEED` modifier by id | UUID from `compat.Attr`, `Operation.MULTIPLY_BASE` / `ADDITION` | forge-extras |
| `DamageTypes.FALL` and `fallDistance > 0` (Scaffolder) | same: 1.20.1's pearl landing also resets fall distance before its fall damage (`ThrownEnderpearl.onHit`) | forge-extras |
| `DataComponents.BLOCK_STATE` (trophy tier) | `BlockStateTag` (done: `SiegeTrophyItem`) | foundation |
| `DataComponents.CUSTOM_DATA` | NBT under `ProficiencyData` (`item.ItemData`) | all |
| `ItemStack.consume(n, player)` | `if (!player.getAbilities().instabuild) stack.shrink(n)` | forge-extras (horn) |
| `Zombie.ZombieGroupData(isBaby, canSpawnJockey)` | the same public constructor exists in 1.20.1; check `finalizeSpawn`'s `SpawnGroupData` argument is passed through | forge-extras |
| `Mob.goalSelector` via an access transformer | a mixin accessor (`@Accessor("goalSelector")`, like the line's `LivingEntityInvoker`); the refmap remaps it for the SRG jar | forge-extras |
| `Raider.die` head (`HearthRaiderMixin`) | the same method in 1.20.1; add the mixin to `proficiency.mixins.json` | forge-server |
| `FoodData.tick(Player)` (`HearthFoodDataMixin`) | the same signature in 1.20.1 | forge-server |
| `AbstractFurnaceBlockEntity.serverTick`, `BrewingStandBlockEntity.serverTick` (furnace and brewing mixins) | the same static methods in 1.20.1; check the local variable shapes before targeting them | forge-server |
| `ResourceLocation.parse` / `fromNamespaceAndPath` | `new ResourceLocation(...)` | all |
| `SavedData.Factory`, `HolderLookup.Provider` arguments | gone: `computeIfAbsent(load, create, name)`, `save(tag)`, `load(tag)` | all |
| `BlockEntity.loadAdditional(tag, registries)` | `load(tag)`; `saveWithFullMetadata()` and `BlockEntity.loadStatic(pos, state, tag)` in tests | all |
| GUI sprites (`blitSprite`) | textures: `graphics.blit(texture, x, y, u, v, w, h, texW, texH)`; each screen calls `renderBackground(graphics)` first; `mouseScrolled(x, y, delta)` | forge-client |
| `RenderGuiEvent.Post` / layered HUD | Forge's `RenderGuiEvent.Post`, as the line's `SkillHud` already does (or `RenderGuiOverlayEvent.Post` to sit after one vanilla overlay) | forge-client |
| `RenderLevelStageEvent` (range ring) | Forge's `RenderLevelStageEvent` exists in 1.20.1 | forge-client |
| Fabric `BlockRenderLayerMap` | not needed: the models' `render_type` | forge-client (verify in a client) |

## Lane ownership

Three Forge lanes run in parallel after this foundation. A lane ports the listed common classes,
fills the stubs marked `// FORGE LANE <lane>` (`grep -rn "FORGE LANE" forge-1.20.1/src`), adds its
GameTests and its lines to this page, and stays out of the others' files.

### forge-server: stages, comfort, charge, effects

| Owns | Notes |
|---|---|
| `hearth/charge/**`: `ChargeGrants`, `ChargeEvents`, `ChargeCommands`, `ChargeTick` (replace the stub), `ChargeTiers` (replace the stub), `HearthCharge.init` (fill it; the rest of the class is ported), `ChargeText` (common's own source: change it in common) | `ChargeEvents` registers on `MinecraftForge.EVENT_BUS`; its `onLogout` port calls `ChargeGrants.forget` only (`HearthCharge.forget` is wired by the foundation). Add `mixin/HearthRaiderMixin` (records the raid at the head of `Raider.die`) and its entry in `proficiency.mixins.json`: without it raid kills feed nothing (c01x) |
| `hearth/stages/**`: `HearthStages` (fill), `HearthBreaking` (the server's count, its tick and its stop clear are ported and wired by the foundation; add the hint in `onAttack` and `onBroken`, and do not register the tick or the stop again), `HearthOfferings` (port common's whole class over the foundation's part: `answers` with the offerings, `serverOfferings`, `forgetServerOfferings`; `HearthBlock.use` already asks it), `HearthPresence`, `HearthStageCheck`, `StageBlocks`, `StageText`, `StageFx` | `BLOCK_USE` already runs at LOWEST after claim mods. Sends `DiscoveryPayload.HEARTH`/`HEARTH_FIRST` (constants in place). Port common's `aRefusedFinishDoesNotShortenTheNextHold` with the stages GameTests (the foundation's `onlyAServerCountedSneakHoldPutsItOut` covers the Forge block) |
| `hearth/comfort/**`: `HearthComfort` (fill, keep `hearthGone`), `ComfortScanner`, `ComfortTags`, `ComfortCue`, `ComfortCommand`; `skill/PlacedBlocks.java`, `skill/PlacedBlocksData.java` (the comfort store, `pickUp` bitmask, `land`, `sweepComfort`, `clearComfortPlacement`), `event/PlacedBlockEvents.java` | Forge events: `ChunkEvent.Load`, `EntityLeaveLevelEvent`, `EntityJoinLevelEvent`, `ExplosionEvent.Detonate`. The tags are already in place (foundation); the Reclamation comfort datapack is in `packs/reclamation/datapack` |
| `hearth/effects/**`: `HearthEffects` (fill), `PlayerEffects`, `HearthEffectEvents`, `HearthStations`, `HearthStreak`, `HearthTempHp`, `HearthWorkshop`; mixins `HearthFurnaceMixin`, `HearthBrewingStandMixin`, `HearthFoodDataMixin` (+ `proficiency.mixins.json`); `skill/SurvivalStreak.java` (the hold) | Temp HP without `max_absorption`: a saved Hearth share (`PlayerHearth.absorbShare`). Fortified: on `INCOMING_DAMAGE` as on 1.21.1 (the compat event, before armour), or Forge's `LivingHurtEvent` (also before armour, after the shield); one of the two, with a GameTest that shows the reduction and that players and their pets are not reduced. Sleep: listen to `HearthHooks.SLEEP` (wired). Register `addWarmthBonus(HomebuilderApi::warmWelcome)` (the API is ported) |
| `event/ExhaustionWatch` (1.21.1 integration): the one exhaustion watcher at the head and the tail of `FoodData.tick` (`HearthFoodDataMixin`: `beforeFoodTick`, `afterFoodTick`; fix round i01y replaced the look after the player tick, which saw only the net change on a tick that also took a food point); the Forge `NightwalkerEvents` (Night Owl) and `TalentEnduranceEvents` (Lean Times) become shares of it instead of watchers of their own, and Long Haul (`MovementEvents.refundSprintHunger`) calls `ExhaustionWatch.refund(player, amount)` instead of writing the exhaustion itself, so the shares multiply | With the effects port (Hearty is the third share) |
| GameTests: Forge ports of `HearthChargeGameTests`, `HearthStagesGameTests` (and the claim test of `NeoForgeHearthStagesGameTests`), `HearthComfortGameTests`, `HearthEffectsGameTests`, `HearthIntegrationGameTests` (the exhaustion part; glow and feed are the foundation's); `gametest/HearthTestGuard` (1.21.1 integration: every Hearth GameTest starts with `watch(helper)`; port it first, the siege cancel line waits for forge-extras' `SiegeService`) | |

### forge-client: everything the player sees of the Hearth

| Owns | Notes |
|---|---|
| `hearth/client/**`: `HearthClientInit` (fill: `HearthClients.set(new HearthClientImpl())` and the Forge-bus listeners), `HearthClientImpl`, `HearthClientEvents`, `HearthHud`, `TrackerHud`, `HearthScreen` (panel + ledger tab), `HearthRangeRing`, `HearthBlockFx` (replace the stub: particles by stage, motes tinted by the block's `FEED`), `SiegePanelHud` (siege panel, bell arrow, compass ping), `HearthChat`, `HearthView` (calls `stages.HearthOfferings.serverOfferings` / `forgetServerOfferings`, in place), `HearthText`, `HearthDemo`, `HearthDemoData`, `HearthDemoCommand` (fill: Forge's `RegisterCommandsEvent`) | `HearthClients` (common's own source) and the payload handlers are in place. Keep `HearthBlockFx` free of client types in fields and static init: `HearthBlock.getTicker` names it on the server too (only the client calls it) |
| Shared client files: `client/SkillHud.java` (the Warmth glyph after the streak badge, the dot launch point), `client/DiscoveryBanner.java` (the `HEARTH` kinds, `showSiege`, `top()`/`bottom()`), `client/SkillIcons.java` (`drawTexture`), `client/OverlayFallback.java` (the tier-up chat hold), `client/SkillsScreen.java` + new `client/StreakAtHearth.java` (the panel's streak block; the effects lane's on 1.21.1, but it draws `HearthHud.glyph(now)`), new `client/HudKit.java`, `client/DotFlight.java`, `client/LayoutDemo.java` (the `hearth` group) | The art's render layer needs no code (`render_type`); check it in a real client |
| LayoutAudit entries for Forge (docs/LAYOUT-AUDIT.md), the client's demo tour | |

### forge-extras: sieges, tales, Homebuilder

| Owns | Notes |
|---|---|
| `hearth/sieges/**`: `HearthSieges` (fill), `SiegeService`, `SiegeEvents`, `SiegeCommands`, `SiegeGoal`, `SiegeMobs`, `SiegeNotices`, `SiegeSpawner`, `SiegeWorldData`; `SiegeHornItem.use` (fill), `SiegeTrophyItem` tooltip (add `appendHoverText(stack, level, lines, flag)`; `of`/`tier`/`day` are done), `SiegeTrophyBlock` (done) | Forge events: `EntityLeaveLevelEvent` with `getRemovalReason()` for the roster (a mob in an unloaded chunk still stands), `LivingDeathEvent`, `EntityMobGriefingEvent`, `PlayerSleepInBedEvent`. `Mob.goalSelector` through a mixin accessor. Charge through `HearthCharge.siegeKill/siegeDefended/siegeBurst/cut` (ported) |
| `hearth/tales/**`: `HearthTales` (fill), `TalesBank`, `HearthFeast`, `HearthBell`, `HearthChronicle`, `ChronicleCommand`, `HearthTalesEvents`, `TalesFx`, `TalesText`; `client/TalesPayInHud.java` (replace the stub); `hearth/tales/client/TalesClient` is wired | Charge through `HearthCharge.credit` (ported). `<world>/proficiency/chronicle*.jsonl`: `server.getWorldPath(LevelResource.ROOT)` |
| `homebuilder/**`: `HomebuilderInit` (fill), `HomebuilderEvents` (fill `onAction`), `HomebuilderXp`, `HomePassive`, `Hearthglow`, `HouseProud`, `Homeward`, `NightWatch`; `homebuilder/net/HomebuilderNet` (add `sendMagnet`, `sendHomewardDay`); `HomebuilderApi` is common's own source | Reach: `ForgeMod.BLOCK_REACH`. Scaffolder: `DamageTypes.FALL` with `fallDistance > 0`. Homeward breaks on `compat.LivingDamageEvent.Post` |
| Shared files: `client/HomebuilderHud.java` (replace the stub), `client/AbilityWheelScreen.java` (the two Homebuilder slots), `client/AbilityFx.java` (House Proud gold), `skill/ActiveService.java` (House Proud as the ability), `skill/NightwalkerService.java` (no share of Homebuilder's share) | |
| GameTests: Forge ports of `SiegeGameTests`, `TalesGameTests`, `HomebuilderGameTests` | Tests that read a pool's tier-up or tracker rows wait for forge-server's `ChargeTiers`/`ChargeTick`; until it merges, assert the charge and the ledger (both work) |

#### forge-extras: what is in place (fx01, 2026-10-10)

Ported from common at master 851574a; decisions in docs/hearth-build-log/forge-extras.md. The stubs of
this lane (`HearthSieges.init`, `SiegeHornItem.use`, `HearthTales.init`, `TalesPayInHud.accept/reset`,
`HomebuilderInit.init`, `HomebuilderEvents.onAction`, `HomebuilderHud.install/reset`) are filled, and every
class marked "to port" for forge-extras in the class map is in place with common's public API.

| Piece | Forge 1.20.1 |
|---|---|
| Handler classes | `SiegeEvents`, `SiegeCommands`, `HearthTalesEvents`, `HomebuilderEvents` are registered on `MinecraftForge.EVENT_BUS` from the lane's init (no `@Mod.EventBusSubscriber`); tick handlers read `TickEvent.ServerTickEvent` at `phase == END` |
| `Mob.goalSelector` | public on Forge 1.20.1 (Forge's own access transformer): used directly, no accessor mixin, no `proficiency.mixins.json` entry from this lane |
| Siege mob marks | `getPersistentData()` (common's `platform.EntityData`) |
| Roster removal | Forge's `EntityLeaveLevelEvent`, reason from `entity.getRemovalReason()` |
| Griefing | Forge's `EntityMobGriefingEvent`: `Result.DENY` for a siege mob and what it fired |
| Beds | Forge's `PlayerSleepInBedEvent` (`setResult(OTHER_PROBLEM)`), asked before vanilla's checks and before vanilla sets the respawn point: a refused bed still becomes the spawn when vanilla would have made it one (`SiegeEvents.keepSpawn`: awake, alive, natural dimension, in reach, not obstructed), as on NeoForge, where `CanPlayerSleepEvent` comes after the spawn is set (fix round) |
| Horn, trophy | `stack.shrink(1)` unless `abilities.instabuild`; the trophy tooltip reads `SiegeTrophyItem.tier/day` |
| Chronicle, bell | book pages and title from NBT (`pages`, `title`; signed pages are JSON text); `HearthChronicle.signature(stack)` stands for `hashItemAndComponents`; a compass's lodestone from `CompassItem.getLodestonePosition` |
| Tales sends | `HearthNet.sendTales` |
| Homebuilder modifiers | `ForgeMod.BLOCK_REACH` (`ADDITION`) and `MOVEMENT_SPEED` (`MULTIPLY_TOTAL`), named by `compat.Attr.uuid(HomePassive.REACH_ID / SPEED_ID)` |
| Homeward's hit | `compat.LivingDamageEvent.Post`; the absorption a channelling player held is noted on `LivingDamageEvent.Pre` (LOWEST), so a hit the absorption hearts took counts, as NeoForge's `getReduction(ABSORPTION)`. Forge's own `LivingDamageEvent` comes after absorption: a hit another mod cancels there does not count (`HomebuilderEvents.onForgeDamage`, LOWEST, cancelled ones too), as a cancel on NeoForge, which comes before absorption (fix round) |
| `HomebuilderNet` | `sendMagnet`, `sendHomewardDay` added |
| Shared-file edits | `ActiveService` (House Proud), `NightwalkerService` (no share of Homebuilder's share), `AbilityFx` (gold; `edgeGlow` package-visible), `AbilityWheelScreen` (common's file with the three 1.20.1 differences), `SkillPassives.homeMagnet`, `LayoutDemo` (`tales` group) |
| GameTests | `SiegeGameTests`, `TalesGameTests`, `HomebuilderGameTests` (common's names); each starts with `gametest/ExtrasTestGuard.watch(helper)` (Hearths within 40 blocks with their sieges and `MANUAL` entries, the test's mock players; common's `HearthTestGuard` minus the comfort marks, which are forge-server's). The Forge GameTest world is ordinary terrain, so the siege rig cuts a flat arena for the wave ring. Fix round: `ExtrasTestGuard` also takes away the siege mobs within 96 blocks, and `ExtrasTestGuard.atEnd(helper, cleanup)` runs a test's own cleanup when the test ends, so the siege rig's cleanup (sieges cancelled, Hearths and players gone) also runs after a failure in a later tick or a timeout |
| Unit tests | `hearth/HearthTalesWireTest` (merged lang files), `HomebuilderSaveCompatTest`, `client/TalesPayInHudTest` |
| Client icons | `TalesPayInHud` draws with a private copy of common's `SkillIcons.drawTexture` until forge-client's `SkillIcons` has it (since fc01's merge it calls `SkillIcons.drawTexture`, as common does; the private copy is gone) |

### Shared files and the merge rule

The foundation already made the edits every lane needs in `Proficiency`, `ProficiencyNetwork`,
`ProficiencyClientSetup`, `PlayerLifecycleEvents`, `SkillService`, `SpawnOrigin`, `TelemetryService`,
both configs, `ProficiencyItems`, `DiscoveryPayload` and `build.gradle`. A lane that still needs one of
them makes a minimal additive edit and says so here. A new Clone handler reads the old body through
`PlayerLifecycleEvents.readOldCap(event, capability)`, never with its own revive/invalidate. `proficiency.mixins.json`: forge-server adds four
entries, forge-extras appends its accessor; on a conflict keep both. Lang overlays
(`forge-1.20.1/src/main/lang/*.json`): keep both sides' keys, master's order, check with
`python3 -m json.tool`. Commands: each lane registers its own `/proficiency hearth ...` branch from its
own `RegisterCommandsEvent` listener; Brigadier merges them.

## forge-client: ported (fc01, 2026-10-10)

Everything in the forge-client row of "Lane ownership" is on Forge, copied from common with only the
1.20.1 API changed (decisions: docs/hearth-build-log/forge-client.md). The three forge-client stubs
are filled: `HearthClientInit.init` installs `HearthClientImpl` and registers `HearthClientEvents` on
`MinecraftForge.EVENT_BUS`; `HearthBlockFx.clientTick` is common's (motes tinted by `FEED`);
`HearthDemoCommand` registers on Forge's `RegisterCommandsEvent`. The rows of the class map marked
"to port" or "stub" for forge-client (`hearth/client/**`, `client/StreakAtHearth`, `client/HudKit`,
`client/DotFlight`) now read "ported". Where the line differs:

| 1.21.1 (common) | Forge 1.20.1 |
|---|---|
| `OverlayFallback.reroute` called by the loader | `OverlayFallback.onSystemMessage` (`ClientChatReceivedEvent.System`) calls `HearthChat.hold` first and cancels the event for a held line |
| `RenderGuiEvent.Post` also fires under F1 (the HUD pass's hidden branch, `TrackerHud.hidden`, moves the tracker's numbers and its home clock on) | Forge 1.20.1 posts no `RenderGuiEvent` under F1 with no screen open: `HearthClientEvents.onClientTick` runs the hidden branch then, `onRenderGui` with a screen open; both call the one `hiddenPass` (`HearthHud.frame`, `TrackerHud.hidden`) |
| The compass ping reads `DataComponents.LODESTONE_TRACKER` | `CompassItem.isLodestoneCompass(stack)` and `getLodestonePosition(stack.getTag())` |
| `HearthDemo`/`HearthDemoData` payloads are `CustomPacketPayload` | `Object` (Forge payloads are plain records on one channel) |

The Homebuilder HUD, the tale pay-in panel, the wheel's two Homebuilder slots, House Proud's gold glow
and the LayoutDemo `tales` group stay forge-extras' (its port; this lane's copies were put back).
Unit tests: `forge-1.20.1/src/test/.../hearth/client/HearthChatTest`, `HearthClientWireTest`, copies of
common's. The client area has no GameTests on 1.21.1 either.

## Stubs

Each stub returns the neutral value (nothing happens, 0, the vanilla-like answer) and carries
`// FORGE LANE <lane>`:

| Stub | Lane |
|---|---|
| `hearth.charge.HearthCharge.init`, `ChargeTick.markTrackerDirty`, `ChargeTiers.check` | forge-server |
| `hearth.stages.HearthStages.init`, `HearthBreaking.onAttack` (the hint only; the hold is ported), `HearthOfferings.answers` (the offerings), `serverOfferings`, `forgetServerOfferings` | forge-server |
| `hearth.comfort.HearthComfort.init`, `hearthGone` | forge-server |
| `hearth.effects.HearthEffects.init` | forge-server |
| `hearth.client.HearthClientInit.init`, `HearthBlockFx.clientTick`, `HearthDemoCommand.init` | forge-client |
| `hearth.tales.HearthTales.init`, `client.TalesPayInHud.accept/reset` | forge-extras |
| `hearth.sieges.HearthSieges.init`, `SiegeHornItem.use` | forge-extras |
| `homebuilder.HomebuilderInit.init`, `HomebuilderEvents.onAction`, `client.HomebuilderHud.install/reset` | forge-extras |

## Class map: common to forge-1.20.1

Same package, same file name. "Ported" = in place, the public API as on 1.21.1. "Stub" = the file exists
with the API the foundation needed and `// FORGE LANE` bodies. "To port" = not on Forge yet.

| Common class | Forge | Lane |
|---|---|---|
| `hearth/Hearth`, `HearthAttachments`, `HearthBlock`, `HearthBlockEntity`, `HearthBlocks`, `HearthCommonEvents`, `HearthIndexData`, `HearthWorldData` | ported | foundation |
| `hearth/HearthEvents`, `HearthHooks`, `HearthLook`, `HearthRange`, `HearthReload` | common's own source ("Shared sources") | foundation (changes go to common) |
| `hearth/net/*` (9 payloads, `HearthNet`) | ported (`HearthNet.sendTales` added) | foundation |
| `hearth/net/PoolRow`, `HearthClient`, `HearthClients` | common's own source | foundation (changes go to common) |
| `homebuilder/net/HomebuilderPayload`, `HomebuilderActionPayload` | ported | foundation |
| `homebuilder/net/HomebuilderNet` | ported: `send`, `client`, `setClient`; to port: `sendMagnet`, `sendHomewardDay` | foundation / forge-extras |
| `hearth/charge/HearthCharge` | ported but `init` (stub) | forge-server |
| `hearth/charge/ChargeText` | common's own source | forge-server (changes go to common) |
| `hearth/charge/ChargeTick`, `ChargeTiers` | stub | forge-server |
| `hearth/charge/ChargeGrants`, `ChargeEvents`, `ChargeCommands` | to port | forge-server |
| `hearth/stages/HearthStages` | stub | forge-server |
| `hearth/stages/HearthBreaking` | ported but the hint and `onBroken` | forge-server |
| `hearth/stages/HearthOfferings` | stub (the client prediction's entry points) | forge-server |
| `hearth/stages/HearthPresence`, `HearthStageCheck`, `StageBlocks`, `StageFx`, `StageText` | to port | forge-server |
| `hearth/comfort/HearthComfort` | stub | forge-server |
| `hearth/comfort/ComfortCommand`, `ComfortCue`, `ComfortScanner`, `ComfortTags` | to port | forge-server |
| `hearth/effects/HearthEffects` | stub | forge-server |
| `hearth/effects/HearthEffectEvents`, `HearthStations`, `HearthStreak`, `HearthTempHp`, `HearthWorkshop`, `PlayerEffects` | to port | forge-server |
| `hearth/client/HearthClientInit`, `HearthBlockFx`, `HearthDemoCommand` | stub | forge-client |
| `hearth/client/HearthChat`, `HearthClientEvents`, `HearthClientImpl`, `HearthDemo`, `HearthDemoData`, `HearthHud`, `HearthRangeRing`, `HearthScreen`, `HearthText`, `HearthView`, `SiegePanelHud`, `TrackerHud` | to port | forge-client |
| `hearth/sieges/SiegeTrophyBlock` | ported | foundation (forge-extras owns behaviour changes) |
| `hearth/sieges/SiegeTrophyItem` | `of`, `tier`, `day` ported; tooltip to port | forge-extras |
| `hearth/sieges/HearthSieges`, `SiegeHornItem` | stub | forge-extras |
| `hearth/sieges/SiegeCommands`, `SiegeEvents`, `SiegeGoal`, `SiegeMobs`, `SiegeNotices`, `SiegeService`, `SiegeSpawner`, `SiegeWorldData` | to port | forge-extras |
| `hearth/tales/HearthTales` | stub | forge-extras |
| `hearth/tales/client/TalesClient` | ported (calls the `client.TalesPayInHud` stub) | forge-extras |
| `hearth/tales/ChronicleCommand`, `HearthBell`, `HearthChronicle`, `HearthFeast`, `HearthTalesEvents`, `TalesBank`, `TalesFx`, `TalesText` | to port | forge-extras |
| `homebuilder/HomebuilderApi` | common's own source | forge-extras (changes go to common) |
| `homebuilder/HomebuilderInit`, `HomebuilderEvents` | stub | forge-extras |
| `homebuilder/Hearthglow`, `HomePassive`, `HomebuilderXp`, `Homeward`, `HouseProud`, `NightWatch` | to port | forge-extras |
| `client/HomebuilderHud`, `client/TalesPayInHud` | stub | forge-extras |
| `client/StreakAtHearth`, `client/HudKit`, `client/DotFlight` | to port | forge-client |
| `mixin/HearthRaiderMixin`, `HearthFoodDataMixin`, `HearthFurnaceMixin`, `HearthBrewingStandMixin` | to port | forge-server |
| `gametest/HearthGameTests` | ported (+ player save, data, trophy, charge API, and the fix round's click, immunity, sneak-hold, clicked-wall, glow and logout tests) | foundation |
| `gametest/HearthTestGuard`, `HearthIntegrationGameTests` | to port (glow and feed are in `HearthGameTests`) | forge-server |
| `gametest/HearthChargeGameTests`, `HearthStagesGameTests`, `HearthComfortGameTests`, `HearthEffectsGameTests` | to port | forge-server |
| `gametest/SiegeGameTests`, `TalesGameTests`, `HomebuilderGameTests` | to port | forge-extras |
| common test `hearth/HearthWireTest` | ported as `forge-1.20.1/src/test/.../hearth/HearthForgeWireTest` | foundation |
| common test `hearth/HearthTalesWireTest`, `HomebuilderSaveCompatTest` | to port | forge-extras |

Core's Hearth (`core/.../hearth/**`, `skill/HomebuilderMath`, `client/XpGainDots`, the core client
models) is already compiled into the Forge jar and unit tested once for every line.

## Tests

- Unit: `forge-1.20.1/src/test/.../hearth/HearthForgeWireTest` (positions, every payload's codec through
  the shim, protocol 7, payload ids, config defaults, the four client rows, both world SavedData files)
  and `HearthForgeDataTest` (every Hearth and Comfort tag is common's, 1.21-only ids optional, every plain
  vanilla id exists on 1.20.1 per `packs/reclamation/item-ids.txt`, loot tables, recipe, advancement, the
  wither and dragon immunity tags equal on both lines, and the build's copy of `default.json` and the
  shared assets). A change to common's Hearth tags fails this test until the Forge copy follows.
- GameTests: `forge-1.20.1/.../gametest/HearthGameTests` (21): block entity save/load, the player
  capability save/load, the registry vs the art contract and the data load, the 1.20.1 tags, loot tables
  and recipe, a trophy hung with its tier, the index and the once-a-second tick, ghost entries, the
  siege mirror, a cancelled placement, every hook (grant, player second, placed, broken, eaten, use,
  damage, sleep, death and End exit), siege spawn origin, the charge API; fix round: only a click the
  server answers is swallowed, the Wither and the dragon cannot break it, only a server-counted
  sneak-hold puts it out (also after a refused finish), the trophy takes the clicked wall, glow and
  feed, a logout forgets the ledger request limit.
- A Forge GameTest class is `@GameTestHolder(Proficiency.MOD_ID)` + `@PrefixGameTestTemplate(false)`,
  template `"empty"`, players from `MockPlayers.make(helper)` (vanilla's mock player throws on Forge),
  `MinecraftForge.EVENT_BUS.post(...)` returns whether the event was canceled. Each Hearth-placing test
  gets its own `batch`, as on 1.21.1, and removes every Hearth it placed.
- Build: `kbuild.sh <lane> ./gradlew --no-daemon :forge-1.20.1:build :forge-1.20.1:runGameTestServer`.

## Expected to move

The integration lane is finishing the 1.21.1 lines in parallel with this foundation; it was ported
from master at its merge base. Before a Forge lane finishes, it diffs
`git log <its base>..master -- common/src/main/java/dev/amman/proficiency/{hearth,homebuilder} core/`
and ports what changed in its own files. Likely movers: payload fields (new ones go at the end of a
record's wire form: port them to the Forge records the same way, which keeps protocol 7), new
`HearthHooks` or `HearthEvents` entries (add them to the Forge copies additively), lang keys (free:
`mergeLang` takes them), tags and `default.json` (`HearthForgeDataTest` fails until the Forge tags
follow; `default.json` is copied by the build), and Warm Welcome's registration (owed by effects on
1.21.1; on Forge it is forge-server's line in `HearthEffects.init`).

Already moved once: stages fix round 2 (master 7e9d500, merged into this foundation before it landed)
appended `offerings` to `HearthStatePayload.Extra`; the Forge record has it (the payload port is a
script over common's files, so a re-run picks such a change up). The same round made the client's click
prediction read the server's offerings (`HearthOfferings.answers`), but 1.21.1's `useWithoutItem` still
answered SUCCESS on the client for every main-hand click, and the Forge `use` copied that.

Moved in the foundation's fix round (2026-10-09), on both lines: the client swallows only a click the
server answers (1.21.1: no `useWithoutItem` override any more; Forge: `HearthBlock.predicts`); the
Hearth is `wither_immune` and `dragon_immune`; the server's sneak-hold reads 0 below 70% sneaked
(`HearthBreaking.MIN_SNEAKED`), because vanilla's late finish after a refused STOP otherwise finished a
second sneaking swing after a few ticks. From 1.21.1's integration task (master cabe847): the block's
`GLOW`/`FEED` and `HearthLook` are on Forge; `ExhaustionWatch`, `HearthTestGuard` and the Polish wording
pass are not (see the lanes above; lang is free through `mergeLang`).

Decisions: docs/hearth-build-log/forge-foundation.md.

## forge-server: what the lane ported (fs01, 2026-10-10)

Additive to the tables above: every class the ownership table lists for forge-server is in place, and
every `// FORGE LANE forge-server` stub is filled. Decisions: docs/hearth-build-log/forge-server.md.

| Piece | Forge 1.20.1 | Differs from 1.21.1 |
|---|---|---|
| Charge | `hearth/charge/**` whole: `ChargeGrants`, `ChargeEvents`, `ChargeCommands`, `ChargeTick`, `ChargeTiers`, `HearthCharge.init`; `mixin/HearthRaiderMixin` | `ChargeEvents.onLogout` forgets only `ChargeGrants` (the foundation forgets the ledger request limit) |
| Stages | `hearth/stages/**` whole: `HearthStages`, `HearthOfferings` (the offerings join `answers`), `HearthPresence`, `HearthStageCheck`, `StageBlocks`, `StageText`, `StageFx`; `HearthBreaking` hint and `onBroken` | The hold's tick and stop clear stay the foundation's. An offering's take: `hurtAndBreak(1, player, broadcastBreakEvent)`, else `shrink(1)` unless `instabuild`. Every refused lighting on hearth stone (or the Heart) with flint and steel or a fire charge, at a campfire vanilla cannot light (lit or waterlogged), is consumed (`SUCCESS`, nothing spent): not the pack's stage-I offering, `mayChange` false, waterlogged, a gap in the platform. Forge counts a campfire as flammable, so vanilla would set a fire on top that never burns out (fix round: only the gap was covered) |
| Comfort | `hearth/comfort/**` whole; `skill/PlacedBlocks` and `PlacedBlocksData` (the comfort store), `event/PlacedBlockEvents` (the marks bitmask on a falling block) | Forge's `ExplosionEvent.Detonate` (`Explosion.getPosition()`), `ChunkEvent.Load`, `EntityJoinLevelEvent`, `EntityLeaveLevelEvent`; the AgriCraft-aware `SkillTools.isRipeCrop(level, pos, state)` kept |
| Effects | `hearth/effects/**` whole; mixins `HearthFurnaceMixin`, `HearthBrewingStandMixin`, `HearthFoodDataMixin`; `event/ExhaustionWatch` (Night Owl and Lean Times are shares; fix round: common's i01y design, `beforeFoodTick` at the head and `afterFoodTick` at the tail of `FoodData.tick`, no player-tick look, Long Haul's refund through `ExhaustionWatch.refund`); `SurvivalStreak` hold | Fortified cuts on Forge's `LivingHurtEvent` (`PlayerEffects.fortified`; the combat mark stays on `INCOMING_DAMAGE`). Temp HP: `PlayerHearth.absorbShare` is what the Hearth added; a hit spends it first (`HearthTempHp.spent`, from `compat.LivingDamageEvent.Pre` at LOWEST); `HearthTempHp.share(player)` is the Hearth's part (it plays 1.21.1's `modifier(player)` role; the alias of that name is gone, fix round); no `MODIFIER_ID`. 1.21.1's deprecated `HearthEffectEvents.hearty` (no callers) is not on Forge. Workshop reads 1.20.1's `Recipe#getId` and Forge's stack-aware remainder |
| GameTests | `gametest/HearthTestGuard` (common's, with `bounds(helper)`; it ends a running siege through `SiegeService`), `ForgeTestEvents`, `HearthChargeGameTests`, `HearthStagesGameTests` (with the claim-mod test), `HearthComfortGameTests`, `HearthEffectsGameTests`, `HearthIntegrationGameTests`; every foundation `HearthGameTests` test starts with `watch` | `runGameTestServer` runs in a flat world in its own game directory, `run/gametest` (`build.gradle` writes `level-type=minecraft:flat` into its `server.properties`; the dev server's `run/` is not touched), as vanilla's GameTestServer and the 1.21.1 lines do. Fix round: `aRefusedLightingOnHearthStoneLeavesNoFire` (Forge only), and common's `whatTheFoodTickAddsIsScaledEvenOnTheTickThatTookAPoint` and `longHaulRefundsAShareOfWhatTheOtherSharesLeftSoTheyMultiply`; the siege rig no longer cuts an arena |

Not here, by the ownership table: the panel's "Stacks safe at the Hearth" block (`client/StreakAtHearth`
in `SkillsScreen`) is forge-client's, because it draws forge-client's `HearthHud.glyph`; the server side
of it (the `STREAK_SAFE` flag, the hold, `streak_paused`) is in.
