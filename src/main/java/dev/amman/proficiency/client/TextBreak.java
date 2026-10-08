package dev.amman.proficiency.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Line breaking and cutting for plain strings, with the width of a string supplied by the caller
 * (the game font in the client, a fixed table in the unit test). No Minecraft classes here.
 *
 * <p>Breaks fall at spaces, and between any two CJK characters. Two rules keep a break from
 * reading badly: a number never ends a line apart from its unit ("20" and "s", "5" and "min"),
 * and closing punctuation of a CJK line never starts one.
 */
public final class TextBreak {

    /** The width of a string in pixels. */
    @FunctionalInterface
    public interface Measure {
        int width(String text);
    }

    public static final String ELLIPSIS = "…";
    /** Characters that may not start a line in CJK text. */
    private static final String NO_START = "、。，．！？：；）」』】》〉ー"
            + "…»”’)]}!?,.:;%";
    /** Characters that may not end a line in CJK text. */
    private static final String NO_END = "（「『【《〈«“‘([{";

    private TextBreak() {
    }

    /** {@code text} cut to {@code width} with an ellipsis, or whole when it already fits. */
    public static String clip(Measure measure, String text, int width) {
        if (text.isEmpty() || measure.width(text) <= width) {
            return text;
        }
        if (measure.width(ELLIPSIS) > width) {
            return "";
        }
        int end = 0;
        int i = 0;
        while (i < text.length()) {
            int next = i + Character.charCount(text.codePointAt(i));
            if (measure.width(text.substring(0, next).stripTrailing() + ELLIPSIS) > width) {
                break;
            }
            end = next;
            i = next;
        }
        return text.substring(0, end).stripTrailing() + ELLIPSIS;
    }

    private static boolean cjk(int cp) {
        return (cp >= 0x2E80 && cp <= 0xD7AF) || (cp >= 0xF900 && cp <= 0xFAFF) || (cp >= 0xFF00 && cp <= 0xFFEF)
                || (cp >= 0x20000 && cp <= 0x2FFFF);
    }

    /** The pieces a line may be broken between. A piece keeps the space that follows it. */
    static List<String> tokens(String text) {
        List<String> raw = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == ' ') {
                word.append(' ');
                raw.add(word.toString());
                word.setLength(0);
            } else if (cjk(cp)) {
                if (word.length() > 0) {
                    raw.add(word.toString());
                    word.setLength(0);
                }
                raw.add(new String(Character.toChars(cp)));
            } else {
                word.appendCodePoint(cp);
            }
        }
        if (word.length() > 0) {
            raw.add(word.toString());
        }
        // Glue: closers to what they close, openers to what they open, a number to its unit.
        List<String> out = new ArrayList<>();
        for (String piece : raw) {
            String last = out.isEmpty() ? null : out.get(out.size() - 1);
            String lastTrim = last == null ? "" : last.stripTrailing();
            boolean closer = NO_START.indexOf(piece.codePointAt(0)) >= 0;
            boolean opener = !lastTrim.isEmpty() && NO_END.indexOf(lastTrim.codePointBefore(lastTrim.length())) >= 0;
            if (last != null && (closer || opener || (isNumber(last) && isUnit(piece)))) {
                out.set(out.size() - 1, last + piece);
            } else {
                out.add(piece);
            }
        }
        return out;
    }

    private static boolean isNumber(String piece) {
        String p = piece.strip();
        if (p.isEmpty()) {
            return false;
        }
        boolean digit = false;
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (Character.isDigit(c)) {
                digit = true;
            } else if ("+-.,:/×%x".indexOf(c) < 0) {
                return false;
            }
        }
        return digit;
    }

    /** A short word after a number: a unit (s, sec, min, с, 秒). */
    private static boolean isUnit(String piece) {
        String p = piece.strip();
        if (p.isEmpty() || p.length() > 4 || isNumber(p)) {
            return false;
        }
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (!Character.isLetter(c) && c != '.' && c != '/') {
                return false;
            }
        }
        return true;
    }

    /** {@code text} wrapped to {@code width}; one over-long word is broken by characters. */
    public static List<String> wrap(Measure measure, String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String token : tokens(text)) {
            String piece = line.length() == 0 ? token.stripLeading() : token;
            String candidate = line + piece;
            if (measure.width(candidate.stripTrailing()) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
            }
            String rest = token.stripLeading();
            while (measure.width(rest.stripTrailing()) > width && rest.length() > 1) {
                int cut = 0;
                int i = 0;
                while (i < rest.length()) {
                    int next = i + Character.charCount(rest.codePointAt(i));
                    if (measure.width(rest.substring(0, next)) > width && cut > 0) {
                        break;
                    }
                    cut = next;
                    i = next;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        if (line.length() > 0) {
            lines.add(line.toString().stripTrailing());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * Packs a line made of pieces joined by a separator into as few lines as fit, moving a whole
     * piece to the next line rather than cutting it. {@code splitRegex} finds the separators and
     * {@code join} puts them back. A piece wider than a line is wrapped on its own. Over
     * {@code maxLines} the last line is clipped with an ellipsis and {@code overflow[0]} is set.
     */
    public static List<String> pack(Measure measure, String text, int width, int maxLines, boolean[] overflow,
            String splitRegex, String join) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String raw : text.split(splitRegex)) {
            String piece = raw.strip();
            if (piece.isEmpty()) {
                continue;
            }
            String joined = line.length() == 0 ? piece : line + join + piece;
            if (measure.width(joined) <= width) {
                line.setLength(0);
                line.append(joined);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (measure.width(piece) <= width) {
                line.append(piece);
            } else {
                List<String> broken = wrap(measure, piece, width);
                lines.addAll(broken.subList(0, broken.size() - 1));
                line.append(broken.get(broken.size() - 1));
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return limit(measure, lines, width, maxLines, overflow);
    }

    /** At most {@code maxLines} lines: the rest is folded into the last one, which is clipped. */
    public static List<String> limit(Measure measure, List<String> lines, int width, int maxLines,
            boolean[] overflow) {
        if (lines.size() <= maxLines) {
            return lines;
        }
        if (overflow != null) {
            overflow[0] = true;
        }
        StringBuilder rest = new StringBuilder(lines.get(maxLines - 1));
        for (int i = maxLines; i < lines.size(); i++) {
            rest.append(' ').append(lines.get(i));
        }
        List<String> cut = new ArrayList<>(lines.subList(0, maxLines - 1));
        cut.add(clip(measure, rest.toString(), width));
        return cut;
    }
}
