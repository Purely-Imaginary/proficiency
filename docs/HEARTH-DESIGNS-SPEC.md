# Hearth designs: three lanes of six stages (spec, draft 2026-10-11)

User asks (2026-10-11): the Hearth builds must be deliberate and beautiful, each stage grander than the one
before, up to 9 x 9 x 7. Every stage needs stone, wood and metal. Wood and stone are required by type (any
kind the player likes); metals are named exactly. Three designs per stage, as three lanes: each stage of a
lane expands that lane's previous stage. Inspiration: the Blood Magic altar (one ring or one set of corner
pillars per tier, a capstone material per tier, block roles by tag).

## The data file

`common/src/main/resources/data/proficiency/hearth_designs/designs.json` (one file, a classpath resource in
every jar, read by `HearthDesigns.shipped()` on the server and on every client; it is not under
`proficiency/hearth/`, which datapacks merge as Hearth numbers). It replaces the six stage block tags as the source of truth for what a stage is. It also
feeds the guide's spinning 3D preview and the "first missing block" message.

```json
{
  "legend": { "S": "stone", "W": "log", ... },
  "lanes": [
    {
      "id": "longhouse",
      "stages": [
        { "stage": 1, "layers": { "-1": ["...9 rows of 9 chars..."], "0": [ ... ] } },
        ...
      ]
    }
  ]
}
```

- **Grid**: every layer is 9 rows of 9 characters. Row 0 is north (-Z), column 0 is west (-X). The campfire
  sits at row 4, column 4 of layer `"0"`. Layers run from `"-1"` (the floor under the fire) to `"5"`: seven
  tall at most.
- **A stage lists its whole structure**, not only what it adds. Stage k of a lane must contain every
  non-space cell of stage k-1 of the same lane, with the same character (a superset), except that a cell
  may move from a role to a stricter one of the same material (see the legend).
- **4-fold rotational symmetry** around the fire on every layer, so the checker never needs to try rotations
  and a player can build facing any way. Stair and slab facing is never checked.
- **Every stage contains stone, wood and metal**, and every stage after I adds at least one block of two of
  the three.
- Only the six stages' offerings stay as they are (flint and steel, copper block, iron block, gold block,
  diamond block, heart of the sea). Character gates stay.

## Legend (roles)

| Char | Role | Matches |
|---|---|---|
| `F` | the fire | the Hearth block itself (only row 4, column 4, layer 0) |
| `.` | air | must be air (the smoke column, walkways, doorways) |
| ` ` | anything | not checked |
| `S` | stone block | tag `#proficiency:hearth/stone`: any full stone-type block. Stone, cobblestone, mossy, smooth stone, every brick, polished and tile variant of stone, deepslate, tuff, blackstone, andesite, diorite, granite, basalt, mud bricks, bricks, sandstone, prismarine, end stone, nether bricks, quartz. The player picks the kind |
| `s` | stone shape | tag `#proficiency:hearth/stone_shaped`: stairs, slabs and walls of any of those |
| `W` | log | `#minecraft:logs` (any log, wood, stripped, stem, hyphae) |
| `P` | planks | `#minecraft:planks` |
| `w` | wood shape | wooden stairs, slabs, fences, fence gates, trapdoors (any wood) |
| `N` | banner | `#minecraft:banners` (any colour or pattern) |
| `i` | iron block | `minecraft:iron_block` |
| `c` | copper block | any copper block or cut copper, any oxidation, waxed or not |
| `g` | gold block | `minecraft:gold_block` |
| `b` | iron bars | `minecraft:iron_bars` |
| `h` | chain | `minecraft:chain` |
| `l` | lantern | `minecraft:lantern` or `minecraft:soul_lantern` |
| `a` | anvil | any anvil |
| `u` | cauldron | any cauldron |
| `B` | bell | `minecraft:bell` |
| `r` | lightning rod | `minecraft:lightning_rod` |
| `v` | water | `minecraft:water` as a SOURCE block (a waterlogged block does not count) |
| `V` | lava | `minecraft:lava` as a SOURCE block; forge lane only, always with `b` or `S` directly above |
| `f` | flower pot | any flower pot, empty or potted (`#minecraft:flower_pots`); needs a solid block below |
| `x` | storage | chest, trapped chest or barrel |
| `C` | candle | any `#minecraft:candles`; needs a solid block below |
| `m` | carpet | `#minecraft:wool_carpets` or moss carpet; needs a solid block below |
| `G` | glass | any glass block, stained or tinted glass, or pane |
| `z` | skull | any skull or head; needs a solid block below; at most 4 per lane |
| `I` | ice | packed ice or blue ice (not plain ice, it melts near the Hearth's light) |
| `n` | snow | snow block |
| `q` | moss | moss block |
| `Y` | magma | magma block |
| `O` | obsidian | obsidian or crying obsidian |
| `L` | leaves | any leaves (player placed, so persistent) |
| `k` | lodestone | `minecraft:lodestone` (stage VI only, under the fire) |

A cell moving from `S` to `s` (or `P` to `w`) in a later stage is allowed: same material, more shaping.

## Lanes (themes, to be drawn)

1. **Longhouse**: a Norse hall. Log pillars, plank and wooden-stair roof, chains and lanterns hung low.
   It grows into a long-roofed hall around the fire.
2. **Forge**: a smith's hearth. A heavy stone base and chimney, iron bars, an anvil and a cauldron, copper
   that will weather over time. It grows into a stone smithy with a tall chimney.
3. **Shrine**: an open pavilion. A stone terrace in rings, slender pillars, gold and copper accents and a
   bell, with a lightning rod crown at VI. It grows into a temple-like gathering place.

Rough size per stage (each lane may differ by a ring): I 3x3x2, II 5x5x2, III 5x5x4 to 7x7x4,
IV 7x7x5, V 7x7x6 to 9x9x6, VI 9x9x7. Each stage must look clearly grander than the previous one:
a new ring, new height, a new capstone material.

## Checking (as built)

- `core/.../hearth/stages/HearthDesigns` parses the file and matches it through a `Probe` (role letter and offset).
  `HearthStructure` is the rules on top: `check(probe)` gives the built stage (the highest stage any lane matches
  in full; lower stages are not asked, so a later stage may reshape S to s) and the problem of the next one.
- The air `.` is air (`BlockState.isAir`), ` ` is not checked, `F` is skipped (the caller guarantees the fire).
- A problem is the next stage's: the lane with the fewest wrong cells, its first wrong cell in scan order (layer -1
  up, north to south, west to east), as a role, an offset and the count still wrong. The chat line reads
  "stone wanted 2 north, 1 east, 1 up of the fire (23 blocks to go)" or "air wanted: smoke must rise above the fire".
- Roles are block tags (`StageBlocks.roleTag`): `#proficiency:hearth/{stone, stone_shaped, wood_shaped, copper, iron,
  gold, iron_bars, chain, lantern, bell, lightning_rod, lodestone}` plus vanilla `#minecraft:{logs, planks, banners,
  anvil, cauldrons}`. Data in both layouts (`tags/block/hearth` on 1.21, `tags/blocks/hearth` on 1.20.1).
- The Comfort and Character scan skips the **footprint**: the cells of the lane and stage the Hearth stands as
  (`HearthStructure.footprint`), and any bell in the box.
- For the guide: `HearthDesigns.shipped().lanes()`, `Lane.stage(k).cells()` (`dx, dy, dz, role`) and
  `HearthDesigns.defaultBlock(role)` (a block id per role; `StageBlocks.defaultState(role)` resolves it).
- Scanning cost: at most 9 x 9 x 7 = 567 cells per lane per stage, on the same events and intervals as before.

## Fluids (added 2026-10-11)

Water is required from stage III in every lane. Lava is forge-only from stage IV (four corner pits behind iron bars)
and in the shrine at stage VI (four outer-corner fire bowls); the longhouse has none. A fluid cell is a bucket the
player places, so it is held in by blocks: its four horizontal neighbours and the cell below are `S i c g k` (or the
same fluid); a lava cell may also have `b` iron bars beside it (lava does not flow through bars, which is what lets the
glow show; water does flow, so water needs full blocks). Above water nothing fluid; above lava air (`.`), `b` or `S`,
never an unspecified cell. Fluids never sit in the smoke column or on the fire's four edge cells at layers 0-1.
No `W P w N` within 2 cells sideways and from 1 below to 3 above any lava. Water at most 8 and lava at most 4 per lane
at VI. A pool at ring 3 needs a curb at ring 4, so the longhouse's stage III is 9 wide (the size table's III row
allows 5 to 9).

## More lanes, palettes and rules (added 2026-10-11)

Eight lanes: longhouse, forge, shrine, demonic (nether citadel, lava pits behind bars from IV), gothic (chapel with
corner spires), girly (pastel garden pavilion), ruin (overgrown shrine) and frost (spruce lodge on an ice plinth).
Lava is allowed in forge and demonic only. Each lane carries a `palette` (role to concrete block id valid on 1.20.1
and 1.21.1) used for the preview; the checker still accepts any block of the role.

Support: `f C m z` need a block directly below that can carry them (stone, wood, metal blocks, ice, snow, moss,
magma, obsidian, leaves, glass, storage; never air, a fluid, bars, chains or a lantern). Flammable for the lava fire
rule: `W P w N m q L`. At most 4 anvils, bells, gold blocks and skulls per lane.
