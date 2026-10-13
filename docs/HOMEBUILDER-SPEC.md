# Homebuilder: the skill of keeping a home (spec, not built)

Status 2026-10-09: the user asked for it ("passive would give additional speed and reach and magnet
while in hearth range"); everything past the passive is a first proposal. It only exists alongside
the Hearth (docs/HEARTH-SPEC.md) and ships with it. Numbers marked *tune* are first guesses.

## Identity

| | |
|---|---|
| Id / category | `homebuilder`, **Construction** (user, 2026-10-09; next to Masonry and Decorating) |
| Fantasy | the one who keeps the home: works faster at home, pulls the group back to it, tells its stories |
| Passive | **at home**: faster movement, longer reach, an item magnet (user, 2026-10-09) |
| Signature proc | **Hearthglow**: a gain earned at home also charges the Hearth a second time |
| Ability | **House Proud**: 30 s of a doubled passive, shared with everyone at the Hearth |
| Capstone | **Homeward**: once a day, a channelled recall to your Hearth (user, 2026-10-09) |

"At home" means exactly what the Hearth means by it: inside a lit, not smouldering Hearth's range
(the cylinder). Any Hearth counts, not only one you fed. Not scaled by Warmth: it is your skill, not
the Hearth's boon, so it works the moment you step in.

## How it earns XP (Nightwalker's pattern)

Building pays Masonry and Decorating already, so Homebuilder must not pay for the same block twice.
It earns like Nightwalker (`NightwalkerMath`, `SharePot`): **a share of what you do at home**, plus
bursts for the home's milestones. Nothing else pays it.

- **Share**: 15% *(tune)* of every other skill's grant (before multipliers) earned at home, paid
  through a `SharePot` on Nightwalker's interval. Mining in the cellar, cooking in the kitchen,
  fighting a siege at the door: all of it trains Homebuilder.
- **Milestones**, once each per Hearth and per player:

| Milestone | XP *(tune)* |
|---|---|
| you lit the Hearth / sealed a stage (offering) | 150 x stage number |
| a Comfort kind appears at this Hearth for the first time and you placed it | 60 |
| a new Character block id at this Hearth that you placed | 10 |
| a siege defended (split by kills, min 10% each for anyone present) | 300 |
| a Feast you were part of | 25 |
| Tales paid in at this Hearth | 10% of the Tales |
| a Chronicle page written (the flat part) | 20 |

- Does **not** pay, so it cannot farm itself: Homebuilder's own XP is not shared back into its pot,
  not mirrored into the Hearth (it feeds no pool), and not counted as Tales.
- Telemetry kind for the share: `home` (like Nightwalker's `dark`).

## The passive (user, 2026-10-09)

One passive value `p` per skill as usual, `defaultMaxBonus` **0.20** *(tune)* at level 100, applied only
at home, as transient attribute modifiers added when you enter range and removed by the per-player
loop within 2 s of leaving (the same "in range until" flag the Hearth writes):

| Part | At level 100 (p = 0.20) | How |
|---|---|---|
| **Speed** | +20% movement speed | `movement_speed` multiplier `proficiency:homebuilder_speed` |
| **Reach** | +2 blocks | `block_interaction_range` (1.21.1) / `ForgeMod.BLOCK_REACH` (Forge 1.20.1), `+p x 10`; stacks with Mining's and Masonry's reach (Long Arm, Grand Architect), each its own modifier id |
| **Magnet** | items within 6 blocks fly to you | radius `1 + p x 25` blocks; **unlocked by the Tidy Home talent** (user, 2026-10-09), not by level |

Magnet rules (it must not wreck the base's own machinery):
- Pulls **item entities** only (not XP orbs; Tidy Home adds those), at most 8 per 5 ticks, nearest
  first; it moves them, it does not teleport, so they arc in.
- Never pulls items another player dropped in the last 5 s, items on a hopper or in water flow (farm
  and sorter lines), items with a pickup delay set by a mod, or anything while you **sneak**.
- A toggle in the ability wheel (and `/skills magnet`) turns it off for builders who are sorting.
- Server cost: one AABB query per player every 5 ticks, only at home.

## Hearthglow (signature proc)

Chance `defaultProcChance` **0.20** *(tune)*, rolled on a grant at home that fed the Hearth (at most
once per 10 s): that grant's mirror is added **again** to the same pool. Bounded by the Hearth's
hourly cap, so it speeds filling, never raises a ceiling. A small ember puff on the Hearth and on
the HUD gain line ("Hearthglow"). Signature power talents raise it to x2.5 of the mirror.

## House Proud (ability)

Activated from the ability wheel at home, cooldown 6 min, 30 s *(tune)*: your passive doubles
(speed, reach, magnet radius), and every other player in range gets your **undoubled** passive
for the duration. A warm-gold edge glow (the existing ability FX). Useful before a siege wave or for
a big build session.

## Talent tree (~90 points, generated like the others)

Added to `tools/talents_spec.py`; `tools/gen_talents.py` regenerates docs/TREES.md. Rows at levels
0 / 10 / 30 / 60 / 90 / 100, every branch fillable at 100 (house rule).

- **Hearthkeeper** · 5 ranks · level 0. Where the tree starts. _+5% XP per rank._
- **Light Step** · 5 ranks · level 10 (after Hearthkeeper). At home you move, reach and pull a little further. _+5% passive per rank._
- **Tidy Home** · 3 ranks · level 10 (after Hearthkeeper). Rank 1 **turns the magnet on**; rank 2 it also pulls XP orbs; rank 3 items fly in twice as fast.
- **Kindling** · 5 ranks · level 10 (after Hearthkeeper). Hearthglow comes more often. _+10% signature chance per rank._
- **Scaffolder** · 3 ranks · level 30 (after Light Step). At home, falls of up to 3 / 5 / 8 blocks do no damage (building on the roof).
- **Long Pull** · 3 ranks · level 30 (after Tidy Home). +1 block magnet radius per rank.
- **Ember Heart** · 3 ranks · level 30 (after Kindling). Hearthglow charges more. _+15% signature power per rank._
- **Warm Welcome** · 3 ranks · level 60 (after Scaffolder). Your Warmth fills 10% faster per rank (on top of Homecoming's readiness).
- **Storyteller** · 3 ranks · level 60 (after Long Pull). +10% Tales banked per rank on your trips.
- **Open House** · 3 ranks · level 60 (after Ember Heart). House Proud lasts longer and shares further. _+15% ability duration per rank, +20% tempo bonus per rank._
- **Night Watch** · 1 rank · level 90 (after Warm Welcome, Storyteller). Siege mobs in range glow for you, and you hear the siege horn an extra in-game hour early.
- **Homeward** · 1 rank · level 100 (after Night Watch, Open House). Once per in-game day, a 10 s channel (cancelled by damage or moving) brings you home to the Hearth you fed most. _+15% passive, -20% ability cooldown._

Homeward is a recall, so it shortens the walk home, and walking home is where Tales are at risk. It
After the Hearth's bell rings "come home", the channel is 5 s for 60 s. It keeps the risk: banked Tales pay only on arrival, and a death before the channel ends loses them as
usual. Telemetry `homeward` (uses, distance saved) to see if it makes the trip back too cheap.

## Synergy (one, optional)

**Heart of the Home**: Homebuilder *Open House* + Social *(its capstone)*: a Feast also counts as a
House Proud for everyone at it (the shared passive for 30 s, no cooldown used).

## Everything else a skill needs

- Same finish as every other skill (docs/HEARTH-SPEC.md "Finish and polish"): icon, ProcFx particles for
  Hearthglow, AbilityFx for House Proud, tree screen unlock FX, tooltips with the Shift layer.

- `Skill.HOMEBUILDER("homebuilder", SkillCategory.CONSTRUCTION, 0.20, 0.20)` in core, with the
  comment block the other entries have.
- Icon 16x16 + small (like `decorating.png`), discovery banner, first-time bonus line, XP feed name,
  passive lines in `SkillPassives` (speed %, reach, magnet radius), tooltip lines.
- Lang: en_us + natural Polish only; the other 10 languages fall back to English (user, 2026-10-09).
- Forge 1.20.1 port: hand-copied; reach via `ForgeMod.BLOCK_REACH`.
- Hearth spec: Homebuilder is listed among the skills that feed no pool.
- Tests: share pays only at home; milestones once per Hearth per player; no self-share; passive
  modifiers gone within 2 s of leaving or of the Hearth breaking or smouldering; magnet is off without Tidy Home and ignores sneaking,
  hoppers, water flow and another player's fresh drop; Hearthglow respects the hourly cap; Homeward
  cancelled by damage, loses Tales on a death mid-channel.

## Open

- Nothing open on the shape. Homeward is decided (user, 2026-10-09); every number is *tune*.
