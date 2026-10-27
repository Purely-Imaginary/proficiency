# Expertise

One tree across all 35 skills. It is paid for with Expertise points, which come from breadth
(levels in many skills) and depth (Mastery stars). Code: `core/.../perk/Expertise.java` (the table
and the points), `ExpertiseNode.java`, `PlayerSkills` (ranks, checks, the modifier part),
`common/.../perk/ExpertiseService.java` (buy, respec, the "new point" line),
`client/ExpertiseScreen.java`. Forge 1.20.1 keeps hand copies of the common classes.

## Points

- 1 point per 100 skill levels, all skills added up. At most 38 (38 skills at level 100).
- 1 point per 5 Mastery stars, all skills added up. At most 38 (38 skills at five stars).
- So 76 at most. The points are computed from the levels and stars every time. No counter is
  stored, so nothing can drift. An op who lowers a level lowers the points; spent points above
  the new total stay spent, and the free count reads 0.
- A grant that crosses a threshold (a level-up or a star) sends one chat line and a chime:
  "Expertise: a new point! 12 / 70 earned, 3 free."

## Nodes

The grid is the talent tree's: 6 rows, 5 columns. A node opens when every parent is full. No node
excludes another. The whole tree costs 70, so a player with every point fills it.

| Node | Row, col | Ranks x cost | Parents | Per rank | Reaches |
|---|---|---|---|---|---|
| Breadth (trunk) | 0, 2 | 3 x 1 | none | XP +1% | every skill |
| Martial Habit | 1, 0 | 3 x 2 | Breadth | passive +2% | Combat |
| Craftsman's Hands | 1, 2 | 3 x 2 | Breadth | passive +2% | Gathering, Crafting, Mastery |
| Roamer's Ease | 1, 4 | 3 x 2 | Breadth | passive +2% | Movement, Expedition, Construction, Social, Survival |
| Keen Edge | 2, 0 | 2 x 2 | Martial Habit | signature chance +3% | Combat |
| Lucky Hands | 2, 2 | 2 x 2 | Craftsman's Hands | signature chance +3% | the Craft categories |
| Good Fortune | 2, 4 | 2 x 2 | Roamer's Ease | signature chance +3% | the Roaming categories |
| Weight of Practice (bridge) | 3, 1 | 3 x 3 | Keen Edge, Lucky Hands | signature power +2% | every skill |
| Steady Rhythm (bridge) | 3, 3 | 2 x 3 | Lucky Hands, Good Fortune | tempo bonus +5% | every skill |
| Long Fury | 4, 0 | 2 x 3 | Weight of Practice | ability duration +3% | every skill |
| Scholar | 4, 2 | 2 x 2 | Weight, Rhythm | XP +1% | every skill |
| Second Wind | 4, 4 | 2 x 3 | Steady Rhythm | ability cooldown -3% | every skill |
| Polymath (capstone) | 5, 2 | 1 x 6 | Long Fury, Scholar, Second Wind | XP +2%, passive +2%, signature chance +2% | every skill |

Costs: 3 + 18 + 12 + 9 + 6 + 6 + 4 + 6 + 6 = 70.

## Effects at a full tree

Every effect is a multiplier on a `PerkEffect` the skills already read. `PlayerSkills.modifier`
returns it as its own part, after ranks, synergies and Discipline:
`total = ranks x synergies x Discipline x Expertise`, clamped as before (the cooldown never under a
quarter). The tree screen's breakdown adds ", Expertise x1.08" when the part is not 1, so the
parts still multiply back to the number the game uses (`SkillNumbersSourceTest`).

| Effect | Full tree, per skill |
|---|---|
| XP | +7% |
| Passive | +8% (6% from the skill's branch, 2% from Polymath) |
| Signature chance | +8% (6% branch, 2% Polymath) |
| Signature power | +6% |
| Tempo streak bonus | +10% |
| Ability duration | +6% |
| Ability cooldown | -6% |

About one synergy's worth on each number. Nothing doubles, and no node touches the death cost
(owner rule; `ExpertiseTest` fails on a `DEATH_WARD` node).

## Respec

`/proficiency expertise respec`, or the button in the screen (click twice). It empties the tree and
returns every point. It costs one vanilla XP level per 10 points refunded (at least 1), like a talent
respec. It is free at the player's own lit Hearth (`HearthRange.isHome`). Creative players pay nothing.

## Save and wire

`PlayerSkills` saves an optional `expertise` map (node id to rank). An older save has no field and
loads an empty tree. An unknown node id is dropped and a rank above the maximum is clamped. The sync
packet appends the ranks after the rested block, and the client sends `proficiency:expertise`
(action 0 = one rank into a node, 1 = respec). Both raised the network protocol to 8.

## Commands

| Command | Who |
|---|---|
| `/proficiency expertise` | anyone: own points and every node |
| `/proficiency expertise invest <node>` | anyone: one rank |
| `/proficiency expertise respec` | anyone |
| `/proficiency expertise show <player>` | op 2 |
| `/proficiency expertise fill <player>` | op 2: every node full, no checks (testing) |
| `/proficiency expertise reset <player>` | op 2: empty, free |
