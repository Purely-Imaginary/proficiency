# Layout audit

Every screen that draws text with limited room fits it with `client/TextFit` and `client/TextBreak`.
The language decides how long a text is, so nothing is cut at a fixed pixel count any more.

## What each place does when the text is too long

| Place | Room | Now |
|---|---|---|
| Talent tree header (title, summary, signature move, numbers) | whole panel width | wraps by whole pieces ("a · b · c" moves a piece, never cuts one); at most 2 lines each, the last one ends in an ellipsis and the hover shows all |
| Talent tree footer hint | whole panel width | wraps by its 3-space pieces, up to 3 lines |
| Talent tree side column | 120 to 230 px, as wide as the longest synergy asks | ellipsis on a name, hover has it whole; the discipline line wraps |
| Talent tree grid | window | the roomiest of four grids that fits; below that the whole screen shrinks |
| Skills panel folded group | column width | the "best N" summary is dropped when it would not fit beside the name; LayoutDemo `panel-folded` |
| Skills panel columns | widest skill name | wider panel; one scrolling column when two do not fit; whole rows only above the footer |
| Skills panel footer | panel width | a text pair that does not fit side by side goes on two lines |
| Death recap | longest name, up to 60% of the screen | wider panel, wrapped lines (the survival line and the rested XP line each up to 3), fewer rows plus "+N more" when the window is short |
| Hearth tales panel (a homecoming, a page set down, a tale retold) | longest line, up to 60% of the screen | wider panel at the death recap's top (30%, clear of a top-right minimap); the title and each line wrap to 2 lines, the last ends in an ellipsis; on a short window the place rows fold away first (`tales.rows_folded`), never the amount; it steps under a discovery banner that would cover it (the first Tales at a Hearth come with Homebuilder's first-time banner); the Hearth tracker steps under it where there is room, as under the death recap (`client.RightEdge`) |
| Tempo fire on the HUD skill bar | the bar, 7 rows above it, and the multiplier past its right end | no text to fit: the label rises 5 rows while the fire burns so no flame reaches the name or the level; the multiplier is a 3x5 pixel font, at most 22 px past the bar's end (LayoutDemo group `tempo`: low, flare, mid, high, cap, sinking, smoke) |
| XP feed | to the right edge | the source is cut, the numbers are not; the factor line wraps to 2 |
| Banner | window | kicker and XP line wrap; a title shrinks to 1.4x, then goes on two lines |
| Ability wheel | the disc | each centre line is cut to the chord at its height; slots shrink on a crowded ring |
| Journal | panel | tabs size by label or go to two rows; list rows get an ellipsis and a hover |
| Config screen (Forge) | 240 to 400 px | rows widen to the longest label |
| Toast | 160 px | widens |
| Tooltips of the mod | window | wrapped, and drawn smaller if still taller than the screen |
| Rested XP tooltip lines (`Rested: N XP`, `Rusty`) | the panel tooltip | wrapped like the rest of the tooltip |
| Teacher toast (action bar) | window | one short line; the longer login line goes to chat, which wraps |
| Item tooltips of the mod | 240 px | wrapped |
| Action bar messages of the mod wider than the window | window | sent to the chat, which wraps |
| Hearth panel | 400 px in two columns, 280 in one | the tabs go under the title when both do not fit; a boon wraps to 2 lines; missing Comfort kinds 3 lines, the Character note 2 and the sieges line 4, each with a hover for the rest |
| Hearth-keepers tab | panel width | "mostly ..." sources end in an ellipsis; the two window buttons become one toggle when the footer is too narrow |
| Hearth tracker | a third of the screen | the bars shrink from 40 to 24 px first, then the need text is cut; it slides under (or over) a death recap or Tales panel that would cover it, and fades with the panel only where neither fits (`hearth.tracker.gives_way`) |
| Siege panel | 2/5 of the screen | lines are cut, but an outcome's recap line wraps to 3 lines; it steps under a discovery banner that would cover it |

Line breaking keeps a number with its unit ("20 s") and never starts a CJK line with closing
punctuation. `TextBreakTest` covers it.

## Measuring

1. Start a client with `PROFICIENCY_LAYOUT_AUDIT=<dir>` and `PROFICIENCY_LAYOUT_DEMO=all` (or a comma list
   of language codes). In a world it dumps `glyphs.json` (the width of every character), then walks every
   language and window setup through every screen, saving PNGs to `<dir>/shots` and writing
   `<dir>/demo.log` (which sites had to clip or wrap).
   `PROFICIENCY_LAYOUT_ONLY=tree,recap` and `PROFICIENCY_LAYOUT_SETUPS=0,1` narrow a run.
2. `python3 tools/layout/layout_audit.py <dir>/glyphs.json src/main/resources/assets/proficiency/lang out.json`
   measures the strings of every language against the limits of the old layout, per site.
3. `python3 tools/layout/make_sheets.py <dir>/shots <dir>/sheets 640` makes contact sheets, six languages each.

The demo (client/LayoutDemo) is on all three lines (shared code since the monorepo; Forge 1.20.1 keeps its own copy). It needs a private X display (Xvfb) and a throwaway instance. Do not use the real desktop.
GUI sizes below 320 x 240 do not exist in the game, so the smallest setup is 960 x 720 at scale 3. (1.34.0 adds 1280 x 720 at GUI 4, which Minecraft caps to 427 x 240, and a forced scale-4 setup, 320 x 180, that no player can reach, for the one-column Skillstone Face. `PROFICIENCY_LAYOUT_ONLY=stoneface` runs just the Face scenes; each prints a `FACE ... oneColumn= content= view= scroll=` line.)

Forge 1.20.1 run of the Hearth sites (2026-10-10, groups `hearth`, `panel`, `hud`, `tales`, `wheel`, en_us
and pl_pl, all five setups, the lean throwaway instance HearthF): the same clips as the 1.21.1 run and
nothing else (`hearth.panel.single_column` and the skills panel's single column at 320 x 240,
`tales.rows_folded` on short windows).

Forge 1.20.1 run in the full Reclamation pack (2026-10-10, polish-forge, instance ProfReclamationTest with
172 mods, groups `hearth`, `panel`, `hud`, `tales`, `wheel`, en_us and pl_pl, all five setups): the clips
of the HearthF run and nothing new for the Hearth, plus `feed.line` (the debug XP feed's source cut at
320 x 240 in Polish, as designed: the numbers are kept). `tales.rows_folded` came from the rule that kept the
panel above the tracker; the tracker now steps aside instead (since its code review, under the panel where
there is room: LayoutDemo's `hearth-tales` shot). Sheets are kept off-repo.
