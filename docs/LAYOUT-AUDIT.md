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
| Skills panel columns | widest skill name | wider panel; one scrolling column when two do not fit; whole rows only above the footer |
| Skills panel footer | panel width | a text pair that does not fit side by side goes on two lines |
| Death recap | longest name, up to 60% of the screen | wider panel, wrapped lines, fewer rows plus "+N more" when the window is short |
| XP feed | to the right edge | the source is cut, the numbers are not; the factor line wraps to 2 |
| Banner | window | kicker and XP line wrap; a title shrinks to 1.4x, then goes on two lines |
| Ability wheel | the disc | each centre line is cut to the chord at its height; slots shrink on a crowded ring |
| Journal | panel | tabs size by label or go to two rows; list rows get an ellipsis and a hover |
| Config screen (Forge) | 240 to 400 px | rows widen to the longest label |
| Toast | 160 px | widens |
| Tooltips of the mod | window | wrapped, and drawn smaller if still taller than the screen |
| Item tooltips of the mod | 240 px | wrapped |
| Action bar messages of the mod wider than the window | window | sent to the chat, which wraps |

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
GUI sizes below 320 x 240 do not exist in the game, so the smallest setup is 960 x 720 at scale 3.
