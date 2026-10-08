package dev.amman.proficiency.client;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextBreakTest {

    /** Latin 6 pixels a character, CJK and fullwidth 8, like the game font roughly. */
    private static final TextBreak.Measure FONT = text -> {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            width += cp >= 0x2E80 ? 8 : 6;
        }
        return width;
    };

    @Test
    void clipKeepsShortTextAndCutsLongTextWithAnEllipsis() {
        assertEquals("Mining", TextBreak.clip(FONT, "Mining", 40));
        String cut = TextBreak.clip(FONT, "Beastslaying", 40);
        assertTrue(cut.endsWith("\u2026"));
        assertTrue(FONT.width(cut) <= 40, cut);
        assertEquals("Beast\u2026", cut);
    }

    @Test
    void clipNeverReturnsMoreThanTheRoom() {
        assertEquals("", TextBreak.clip(FONT, "Mining", 3));
    }

    @Test
    void wrapBreaksAtSpacesAndKeepsEveryWord() {
        List<String> lines = TextBreak.wrap(FONT, "Hold Shift for skill details", 80);
        assertEquals("Hold Shift for skill details", String.join(" ", lines));
        for (String line : lines) {
            assertTrue(FONT.width(line) <= 80, line);
        }
    }

    @Test
    void aNumberStaysWithItsUnit() {
        // "... for 20" would fit on the first line, but the "s" would then stand alone.
        List<String> lines = TextBreak.wrap(FONT, "on every action for 20 s", 6 * 20);
        for (String line : lines) {
            assertFalse(line.equals("s"), lines.toString());
        }
        assertTrue(lines.stream().anyMatch(line -> line.endsWith("20 s")), lines.toString());
    }

    @Test
    void cjkBreaksBetweenCharactersButNotBeforeClosingPunctuation() {
        List<String> lines = TextBreak.wrap(FONT, "\u4E09\u53C9\u621F\u547D\u4E2D\u9020\u6210\u53CC\u500D\u4F24\u5BB3\u3002", 8 * 6);
        for (String line : lines) {
            assertFalse(line.startsWith("\u3002"), lines.toString());
            assertTrue(FONT.width(line) <= 8 * 6, line);
        }
        assertEquals("\u4E09\u53C9\u621F\u547D\u4E2D\u9020\u6210\u53CC\u500D\u4F24\u5BB3\u3002", String.join("", lines));
    }

    @Test
    void aWordLongerThanTheLineIsBrokenByCharacters() {
        List<String> lines = TextBreak.wrap(FONT, "Abklingzeitverkuerzung", 6 * 8);
        assertTrue(lines.size() >= 3);
        for (String line : lines) {
            assertTrue(FONT.width(line) <= 6 * 8, line);
        }
        assertEquals("Abklingzeitverkuerzung", String.join("", lines));
    }

    @Test
    void packMovesWholePiecesAndNeverCutsOneInTheMiddle() {
        String text = "Passive +12% \u00B7 Proc 8%, power x1.25 \u00B7 Ability 20 s, cooldown 180 s";
        boolean[] cut = new boolean[1];
        List<String> lines = TextBreak.pack(FONT, text, 6 * 40, 2, cut, "\\s*\u00B7\\s*", " \u00B7 ");
        assertFalse(cut[0]);
        assertEquals(2, lines.size());
        assertEquals("Passive +12% \u00B7 Proc 8%, power x1.25", lines.get(0));
        assertEquals("Ability 20 s, cooldown 180 s", lines.get(1));
    }

    @Test
    void packFlagsAndClipsWhatDoesNotFitInTheLines() {
        String text = "aaaa bbbb \u00B7 cccc dddd \u00B7 eeee ffff \u00B7 gggg hhhh";
        boolean[] cut = new boolean[1];
        List<String> lines = TextBreak.pack(FONT, text, 6 * 10, 2, cut, "\\s*\u00B7\\s*", " \u00B7 ");
        assertTrue(cut[0]);
        assertEquals(2, lines.size());
        assertTrue(lines.get(1).endsWith("\u2026"));
        assertTrue(FONT.width(lines.get(1)) <= 6 * 10);
    }

    @Test
    void everyWrappedLineFitsWhateverTheText() {
        Random random = new Random(7);
        String alphabet = "ab cd ef 12 s % \u00B7 \u4E09\u53C9\u3002 ";
        for (int round = 0; round < 500; round++) {
            StringBuilder text = new StringBuilder();
            int length = 1 + random.nextInt(60);
            for (int i = 0; i < length; i++) {
                text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            int width = 24 + random.nextInt(120);
            for (String line : TextBreak.wrap(FONT, text.toString(), width)) {
                assertTrue(FONT.width(line) <= width, "'" + line + "' in '" + text + "' at " + width);
            }
        }
    }
}
