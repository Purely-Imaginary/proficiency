# Hearth designs tools

Source of truth: `common/src/main/resources/data/proficiency/hearth_designs/designs.json`
(format and legend in `docs/HEARTH-DESIGNS-SPEC.md`).

    python3 tools/hearth_designs/validate.py <designs.json>        # PASS/FAIL per rule, per lane and stage; exit 1 on any FAIL
    python3 tools/hearth_designs/render.py <designs.json> <outdir>  # <lane>-<stage>.png x18 and sheet.png (needs Pillow)

validate.py checks grid shape, layer range, the single F, 4-fold symmetry, superset between stages (S to s, P to w,
and S to k under the fire at VI are the only changes), stone+wood+metal, new materials per stage, the clear smoke column,
bounding box growth, the size table, an open entrance at layers 0 and 1, fluid rules (containment, lava grate, fire safety, caps) and prints block and fluid counts per stage.
The render is isometric from the south-east; roofs hide interiors.

Regenerate the JSON with `python3 tools/hearth_designs/gen.py [designs.json]` (default: the live path above) (the generator places cells with 4-fold rotation; edit the stage functions there).

Each lane has a `palette` (concrete block ids per role for previews; `render.py` tints its colours from it).

## Longhouse
I: stone pad, four log posts, a lantern on each. II: a 5x5 stone floor, stone-footed posts with lanterns and low fence rails. III: a 7x7 open timber frame on stone footings, eight stone-curbed water pools in the wall gaps beside the doors, log posts at the corners and beside each doorway, fence half-walls, a log wall plate, fire visible from outside. IV: stone steps around the hall and the first roof tier, a ring of wooden stairs for the eave and a second ring stepping up. V: second roof tier closing in toward the smoke hole, with a chain and lantern hung under each eave corner. VI: third tier narrows the smoke hole to one block, copper finials on the eave corners, banners at the doors, lodestone under the fire.

## Forge
I: a cross of stone with plank floor edges, stone stubs topped with iron bars. II: a 5x5 stone floor, stone benches along the paths with a cauldron on four of them. III: a 7x7 stone shell with barred windows and stone arches over the doors, a quench pool (water) in each inner corner, an iron-bar grate over each still-empty outer corner, and a hollow two-course chimney hood over the fire. IV: the corners fill with lava pits glowing at ground level through iron bars (grate above, bars outside), anvils on plinths in front of them, a slab roof with copper corner caps and the chimney's third course. V: stone parapet on the roof, a fourth chimney course of iron bars, a cauldron beside each gate. VI: copper crown on the chimney top, a stone gate pylon opposite each cauldron, lodestone under the fire.

## Shrine
I: stone pad, four fence posts and lanterns. II: a 5x5 terrace, stone step rails, tall fence pillars capped with copper. III: a ring of stone steps at 7x7, eight curbed pools beside the doorways and a wooden roof floating on the pillars around an open oculus. IV: an outer ring of fence pillars with copper caps carrying a wider second roof, potted flowers on the rails. V: a wider outer terrace, a raised roof tier closing the oculus to the smoke hole, a bell on a chain and a candle beside each doorway. VI: gold and copper crown blocks, lightning rods at the corners, a low stone wall around the terrace, banners at the doors and lodestone under the fire.

## Demonic
I: magma-block pillars with soul lanterns on a pad with crimson plank edges. II: blackstone benches with iron-bar rails and a plank boardwalk. III: a 7x7 blackstone shell with barred, still-empty pits in the wall tops and a pool in each inner corner. IV: roof slabs, tall corner spikes, and eight lava pits in the wall tops glowing behind iron bars (gate-side bars outside, grates above). V: a second roof ring, obsidian gate pillars with soul lanterns, soul lanterns hanging over the doorways. VI: a crimson crown ring, iron-bar spikes and skulls on the corner spikes, lodestone under the fire.

## Gothic
I: stone pad with soul lanterns. II: stone benches with candles and a plank boardwalk. III: a 7x7 chapel with stained-glass windows, timber piers, pointed arch lintels, a water font in each inner corner. IV: a slab vault, corner spires, flying buttresses, chains and lanterns over the fonts. V: spires rise to layer 5 and a bell hangs over each doorway. VI: gable spires with lightning rods above the doors, banners and lodestone.

## Girly
I: pad with fence posts and lanterns. II: cherry benches with potted flowers and carpets, lantern posts. III: eight curbed pools, fence trellis arches over the doors and a first canopy ring. IV: a wider canopy and a lantern hung over each pool. V: a glass cupola, an outer terrace with lamp posts and candles. VI: a top canopy tier with gold finials, banners at the doors.

## Ruin
I: cracked-stone pad with dark log stubs topped by iron bars. II: broken stone stubs and benches with a potted fern and moss on them. III: stepped broken walls, curbed pools, four leaf tufts on the corner tops, a weathered copper lintel over each door and a fallen log over each pool. IV: dark beams across the piers, inner arches, moss on the corner stubs. V: gate stubs with copper caps, ferns and moss carpet patches on an outer terrace, broken arch springers beside each door. VI: half-collapsed arch crown, tall piers with copper keystones over the doors, stepped corner stubs and lodestone under the fire.

## Frost
I: spruce posts and soul lanterns on a blue ice plinth. II: a wider ice plinth, snow-capped corner posts. III: a closed spruce lodge with glass windows over the pools and a log wall plate. IV: spruce eaves with a snow-block roof tier and chains with soul lanterns at the corners. V: a second snow tier and a stone chimney around the smoke hole. VI: porch pillars on ice, chain icicles under the eaves and lodestone.

The fire rule shapes the lava lanes: no wood or other flammable (`W P w N m q L`) within 2 blocks sideways and from 1 below to 3 above any lava, so wood there appears only as floor planks and as roofs at layer 5. Lava neighbours may be iron bars (lava cannot flow through them, so the glow shows); water neighbours must be full blocks.
