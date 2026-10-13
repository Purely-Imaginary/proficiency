# Critical hits

Built 2026-10-12 for 1.7.0 (protocol 9). Before this, the mod read the crit flag only for bows
(Deadeye, Hanging Shot). A melee jump crit paid and rolled exactly like a plain hit.

## What a crit does

A critical melee hit is a hit with a sword, axe, mace, trident or empty hand that the game counts
as critical. That is vanilla's jump crit, or a crit that a talent forces (Lunge).

- **XP:** the hit XP is multiplied by 1.5. The feed and the XP log show this as the `crit` factor.
  The kill bonus is not changed. 1.5 is deliberate: at 3x, every melee player would hop before
  each swing, and the melee skills would level about three times faster than the rest.
- **Signature proc:** the proc chance is doubled for that hit, up to a certain proc.

Numbers: `core/.../skill/CritRules.java` (unit test `CritRulesTest`).

## Talents and synergy

| Node | Tree, row | Ranks | Effect |
|---|---|---|---|
| Lunge | Swords, row 2 (after Keen Edge) | 3 | A sprinting sword hit is a crit. Ready again after 9 / 6 / 3 s. |
| Rend | Axes, row 1 | 1 | An axe crit opens a wound: for 4 s the target takes 15% more damage from weapon hits (any player's). The crit that opens the wound does not get the bonus. |
| Flow | Unarmed, row 3 (after Stagger) | 3 | A crit punch gives Speed I and Haste I for 1 s per rank. |
| Leaping Blade (synergy) | Swords Lunge 1 + Jumping High Jump 3 | | A sword crit deals x1.75 instead of x1.5. Grants +5% Swords proc power and +10% Jumping XP. |

The tree totals are now Swords 99, Axes 99 and Unarmed 99 points, so each tree still fills at level 100.

Lunge has a cooldown so it cannot replace jumping. Vanilla blocks a crit while sprinting, so a
Lunge hit also keeps the sprint knockback.

## How it is wired

- `CriticalHitEvent` (shim in `common/platform/event/entity/player`) is NeoForge's event. NeoForge
  relays its own event through `NeoForgeEventBridge.CriticalHit`. Fabric posts it from
  `PlayerMixin`: a `@ModifyVariable` on boolean local 9 of `Player.attack` (by index, because a
  production jar has no local variable table) and a `@ModifyConstant` on the single `1.5f`.
- `CritEvents` (HIGH) applies Lunge and Leaping Blade. At LOWEST it records the final verdict per
  player (target id, game time).
- `CombatEvents.onIncomingDamage` calls `CritEvents.takeCrit` for a direct melee hit. The check
  matches the target and the tick, and uses the mark up, so a Cleave or Shockwave splash is never a crit.
- Forge 1.20.1 has its own `CritEvents` on Forge's event: `Result.ALLOW` forces a crit, and the
  damage modifier is the multiplier.

Tests: `CritGameTests` (vanilla crit damage and the XP factor, Lunge and its cooldown, Leaping
Blade, Rend, Flow, each with a control) on all three loaders. They go through the real
`Player.attack`.

## HUD: the empty hand

An empty hand used to put Unarmed on the HUD line every time, but fist fights are rare. The line
now follows `core/.../skill/HudSkillPick.java`:

- Unarmed shows only while it earns XP (4 s after a gain), or while its ability runs. This applies
  whatever the feet are doing.
- Otherwise, the empty-hand moments keep their skill: Swimming in water, Sneaking when crouched,
  Endurance at half health.
- Otherwise, Jumping shows for 3 s after a jump, and Running shows the rest of the time. A jump
  is "in the air, 0.25 blocks above where the feet left the ground". This uses height, not take-off
  speed, because a slow frame rate missed the speed check in a real client.
- When the line falls back to Running, it only changes its label. It does not pop up again by itself.

The ability key is unchanged: an empty hand still casts the Unarmed ability.
