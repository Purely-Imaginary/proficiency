# Rested XP and teaching

Design approved by the owner on 2026-10-08, built the same day. This file started as the approved
spec and now says what was built. Code: `core/.../skill/RestedMath.java`, `RestedPool.java`,
`TeachingLedger.java` (pure, unit tested) and `common/.../skill/RestedService.java`,
`TeachingService.java` (the world side; Forge 1.20.1 keeps a copy in `forge-1.20.1/`).

## The owner's rules

1. A **death empties every skill's rested pool**, teacher-filled parts included, and the teacher's
   pending credit for that student. It sits on top of the bar wipe and the survival-bonus loss.
   Intent: a death should hurt almost like losing the inventory, which he dislikes, without taking
   any items.
2. Teaching lives **inside the existing Social skill**. No new skill. Three new Social tree nodes,
   the tree still at most 100 points, fully fillable, distinct from every other tree.

## Rested XP, per skill

- Each skill has a pool. It fills while the player is **active** (not AFK) and the skill is **not
  in use**. Active is the survival streak's own test: XP earned within the survival window
  (`survival.activeWindowMinutes`, 5 by default). In use means the skill paid XP in the last minute
  (60 s). An operator's `/proficiency addxp` is neither activity nor use.
- The cap is **1.5 times what the skill's current level costs** (`rested.capFactor`), so it grows
  with the level. At level 100 the cost is the next Mastery star's. A skill with all its stars
  (or with Mastery off) has no pool.
- The idle rate fills the cap in **9 active hours** (`rested.fullHours`).
- **Spending**: every grant in that skill gains `rested.extra` times itself (1.0, so it doubles)
  from the pool until the pool is empty. The extra comes out of the pool. It is applied to every
  grant source, first-time bonuses and trickles included, except an operator command and a teacher
  payout.
- **Additive with the survival bonus, not multiplied.** The extra is worked out from the grant
  without the survival factor: a grant of 10 plain XP at survival x1.2 pays 12, and a full pool adds
  10, so 22 and not 24. The formula is `RestedMath.extra`.
- At **level 100** the extra goes into the Mastery star bar like any other XP.
- Rested XP is also in the XP log's factor line (`rested x1.83`), the recorded XP feed and the
  telemetry.
- Not saved as a bar: the pool is saved in full, per skill (`rested` field, with each teacher's
  UUID). Old saves load with empty pools. A pool above its cap is cut on load.

## Teaching (Social)

- A **teacher** is a player who is near the student (within the teacher's own company radius plus
  Wide Classroom), not AFK, who earned XP in that skill within the last 5 seconds, and whose level
  in it is at least the student's mentor gap above the student's (`company.mentorLevelGap`, 20, and
  the student's own Patient Mentor / Heart of the Group shrink it). The student must be active as
  well. If several players qualify for a skill, the highest level teaches.
- While that holds, the student's pool in that skill fills at **3 times the idle rate**
  (`rested.teachFactor`), under the same cap. The student does not have to be idle in the skill:
  working next to the teacher is the usual case.
- Each part of the pool **remembers its teacher's UUID** (up to 8 teachers per skill). Spending
  takes teacher parts first, oldest first, then the idle part.
- When the student spends a teacher's part, the teacher earns **Social XP, 25% of the XP spent**
  (`rested.teacherShare`), as base XP: Social's own multipliers apply when it is paid. A teacher
  who is online is paid every 5 seconds in one "Teaching" XP log line, with a small toast
  (an action-bar line) at most every 30 seconds. A teacher who is offline is paid **at the next
  login**: the credit waits in `world/proficiency/teaching_credit.txt`, saved every 30 seconds and
  on stop, so it survives a restart.
- **Student death**: the student's pools are emptied and any credit still waiting on that student
  (unpaid, online or offline teacher) is lost with them.
- A teaching payout does not count as activity or as using Social, and does not spend rested XP,
  so it cannot keep an AFK teacher "teaching" or feed itself.
- The Teacher's Pride rank is read from the teacher when they are online. For an offline teacher
  the base 25% is used.

### New Social nodes

| Node | Row | Ranks x cost | Effect |
|---|---|---|---|
| Quick Study | 2 (level 30), after A Good Word | 2 x 2 | the rested XP you teach fills 15% faster per rank |
| Wide Classroom | 3 (level 60), after Quick Study | 2 x 2 | students count from 6 blocks further away per rank |
| Teacher's Pride | 4 (level 90), after Wise Counsel and Wide Classroom | 2 x 2 | 5 points more of the XP your students spend per rank: 25% to 35% |

To keep the tree at 100 points Kinship and A Good Word went from 5 ranks to 2 ranks of 2.5 times
the effect each (Kinship +12.5% passive per rank, A Good Word +25% Good Company chance per rank),
which is the same total effect for 12 fewer points. The tree is 99 points (it was 99 before),
and every node still fills at level 100. A player who had spent ranks above the new maximum has
them clamped on load and the points come back to spend. The three new nodes are priced 2 points a rank by hand (`per_rank` in
`tools/talents_spec.py`), the usual row price would have been 18 points for them.

## Mentor bonus: old and new

The live mentor XP multiplier, `company.mentorBonus`, was **0.50** (+50% XP near a mentor). It is
**0.25** from this change on, because a student near a teacher now also gets rested XP, and would
otherwise be paid twice for the same lesson. Wise Counsel still adds 5 points a rank: +25% becomes
+40% at full. **An existing `proficiency-server.toml` keeps its old 0.5 until someone edits it**,
so set `mentorBonus = 0.25` on a live server when this version goes on. After a week compare the
mentor-heavy skills' XP per hour in the balance report before and after.

## Display

- **HUD bar**: a blue segment after the filled part, as long as the XP that will pay double (the
  pool over the bar in front of the player, cut at the end of the bar).
- **Skills panel**: the same blue segment on each row's bar. The row tooltip says `Rested: N XP`,
  and after 7 real days with no XP in a skill it adds a `Rusty` line. Rusty is flavour only, no
  penalty. Old saves get today's date for every played skill on first login, so nothing starts
  out rusty.
- **Death**: a chat line (`proficiency.death.rested`), and a blue line under the death recap panel.
- **Teacher toast**: an action-bar line, `Your teaching paid off: +3.5 Social XP`, at most every
  30 seconds, and a chat line at login for credit that waited.

## Wire and save

- Network protocol **6** (was 5): the sync packet carries, for every skill, the pool (float) and the
  whole days since the skill was used (varint); the death recap carries the rested XP lost and the
  number of skills. The client sees only the total, never who taught it.
- Save: new optional fields `rested` (idle XP and a list of `teacher`/`xp` parts per skill) and
  `usedDay` (real epoch day per skill).

## Telemetry

Two new XP kinds in the balance telemetry: `rested` (the extra XP grants gained from a pool, not a
grant of its own, so it adds no grant count and opens no "in use" window) and `teaching` (the Social
XP paid to teachers; passive, like the other trickles, so it proves no activity). The balance
report has **Rested XP** (with its share of the skill's XP) and **Teaching XP** columns in the
XP table, a line in the summary, and colours in the source mix.

## Config (`[rested]`)

| Key | Default | Meaning |
|---|---|---|
| `capFactor` | 1.5 | pool cap in levels' worth of XP; 0 turns rested XP and teaching off |
| `fullHours` | 9 | active hours to fill an empty pool |
| `extra` | 1.0 | extra XP per grant from the pool, as a multiple of the grant |
| `teachFactor` | 3 | teaching fills this many times faster than resting |
| `teacherShare` | 0.25 | teacher's Social XP as a share of the XP the student spends |

`/proficiency rested <player> <skill> <xp>` (op 2) sets a pool, for tests and screenshots.

## Known limits

- "Active" is the survival streak's notion, so a trickle that pays XP while nobody is at the
  controls (the Nightwalker outdoor trickle) keeps the player active, as it already does for the
  survival bonus. Teaching credit and operator commands do not.
- A teacher counts only if they earned XP in the skill in the last 5 seconds. A teacher mining in
  Skill A fills students' Mining pools, not their Swords pools.
- Teachers and students are found by distance inside one dimension.
- Teacher's Pride is read for online teachers only.

## Verified, 2026-10-08

- Unit tests: core 398, common 176, forge 217 (rested pool, cap, fill, AFK no-fill, spend, additive with
  the survival bonus, death wipe, teacher credit, offline pending credit and its save, old and
  hostile saves, the sync packet; the Social tree).
- GameTests: 123 NeoForge, 123 Fabric, 125 Forge 1.20.1, all passing (13 new ones per line: double
  pay and drain, additive with the streak, no spend on an operator grant, the star bar at level
  100, AFK rests nothing, a skill in use does not rest, teaching 3x and the remembered teacher, an
  AFK teacher teaches nothing, teacher paid, offline credit paid at login, death empties pools and
  pending credit, the packets, the telemetry kind).
- Real client (NeoForge, throwaway SkillTest instance and `~/mc-skilltest` server on a private
  Xvfb display, never the real desktop): the blue segment on the HUD bar and on the panel rows, the
  `Rested: 40 XP` tooltip, a real death with the blue "Rested XP lost" line in the recap, and the
  Social tree with the three new nodes.
- Not checked in a real client: two players teaching each other (the GameTests cover it), the
  `Rusty` tooltip line (unit tested only), and the Fabric and Forge clients' drawing of the blue
  segment (the same code, built and tested, not looked at).
