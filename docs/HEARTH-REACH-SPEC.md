# Hearth Reach: the range grows with Homebuilder

Asked for on 2026-10-12. Before this, every Hearth had the same fixed range (a 24-block cylinder,
`hearth.range`). The user wanted the range to grow as the home grows. On Reclamation, the
biggest Hearth should cover the whole starting island. The user chose the source: the Hearth
charges itself from Homebuilder XP gains.

## Rule

- Every Homebuilder XP gain a player makes while in range of a Hearth is added to that Hearth's
  **Reach**, at its base amount. Reach is saved on the Hearth (`reach` in its state). It never
  drains, so a range never shrinks.
- The radius in whole blocks is `range + (rangeMax - range) x min(1, reach / reachFullXp)`.
  - `range` defaults to 24.
  - `rangeMax` defaults to 64.
  - `reachFullXp` defaults to 5000. That is roughly a dozen mid-level Homebuilder levels' worth of
    XP, all earned at that one Hearth.
- The height limits stay the same for every Hearth (`rangeBelow` 8, `rangeAbove` 16), so a mine
  under the base never becomes home.
- Ranges may overlap once they grow. A block then belongs to the **nearest** Hearth. `minSpacing`
  is still checked when a Hearth is lit.

## Territory and house

The grown radius is the Hearth's **territory**: being home, Warmth, boons, charge mirroring, Tales,
furnace and station speed-ups, and kills that feed charge.

The **house** stays at the base `range`: the Comfort and Character scan, the siege spawn ring and
the bell. A 192-block scan would be heavy, and a siege spawning 190 blocks out would be no siege.

## Reclamation

The starting island was measured on 2026-10-12 from the "New World" region files. It is the ring
island around spawn, x −105..120, z −42..192. From the user's base at about (−3, 22), the farthest
land is 190 blocks away. From the island's centre (7, 75) it is 141 blocks. The Reclamation config
sets `rangeMax = 192`.

## Map

When Xaero's World Map is installed, the client draws the ranges of the Hearths it knows as a
chunk highlight. The highlight follows chunks, so a chunk counts when its centre is inside the
radius. The toggle is in the Hearth panel and in the client config (`hearth.mapOverlay`). Forge
1.20.1 (Reclamation) comes first.
