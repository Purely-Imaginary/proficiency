# UX pass (1.28.0)

His ask: "UI/UX pass on all Proficiency screens - it is getting crowded in there." Audit in real NeoForge 1.21.1 and
Forge 1.20.1 clients at GUI scale 2, 3 and 4 (LayoutDemo, en_us, 1920x1080 gui4 and gui2, 960x720 gui3). Rule: nothing is removed. Information moves behind a hover, a click or a fold.

## Findings and fixes

| Screen | What was crowded or unclear | Fix (this version) |
|---|---|---|
| Skills panel | 38 skills in 9 groups always open: two full columns at scale 2, one 10-row scrolling column at scale 3 and 4 with the footer eating 4 more rows. Every group header the same weight as the rows. | Each group header has an arrow and folds on click (Shift-click: show only this group, again: show all). A folded header says "best N". Folded state is kept for the session. Hover on a header says how. |
| Skills panel tooltip (Shift) | Eight colours in 12 lines (yellow, aqua, magenta, green, grey). The "tree" and "kinds" lines were coloured like warnings. | Numbers keep green/grey. Tree and kinds lines are grey. Yellow is kept for the one line that asks you to act (points to spend); with 0 points it is dim. |
| Talent tree | Footer carried a chat command (`/proficiency respec beastslaying`) that is 40 percent of the line and read as noise. | Footer is "Click: one rank   Shift-click: fill   Hover for how to reset"; the hover has the command and the cost. |
| Expertise | Same footer problem. | Same: "Hover here for the command". |
| Codex titles | 38 identical rows (name, 0, four boxes) in the colour of their category: a coloured wall with no landmarks. | Rows sit under one coloured header per category with a rule; names are white (dim at 0 kinds found), so colour says the group once. Clicks on headers do nothing. |
| Discovery journal | A biome list of 60 rows of "???" below the few found. | A run of unfound entries is one line: "62 still to find". The count is the same, the scroll is not. |
| Hearth panel | Five pools, each boon line in its own colour (green, teal, orange, pink): four competing colours on one page. | The pool colour stays on the icon, bar and tier chip. The boon words are white (dim when not yet earned). |

## Seen and left alone

Reviewed in the shots and judged fine: Hearth guide (Build tab: two tab rows and a design strip are the minimum for eight
designs), self-build and range buttons, ability wheel centre text (five lines, each a different kind of fact), the HUD
skill line, the XP feed, Tempo flames (own audit group), banners, toasts. Their overflow is already covered by
`docs/LAYOUT-AUDIT.md`.

## Not done, and why

- Persisting folded groups across restarts: the choice is session-only; a client config key can follow if he wants it.
- A tabbed skills panel: folding gives the same reduction with no new screen state.
- Other languages: new strings are en_us only (batch rule); they fall back to English.

## Layout audit

LayoutDemo gained scene `panel-folded` (six groups folded, all three scales). The Codex and journal scenes already
existed and now show the grouped list. `panel.single_column` still applies at 320 x 240; a folded panel is the way out
of it.
