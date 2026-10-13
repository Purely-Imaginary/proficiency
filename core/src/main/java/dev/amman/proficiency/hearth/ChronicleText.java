package dev.amman.proficiency.hearth;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The text side of the Hearth Chronicle (docs/HEARTH-SPEC.md, "Telling the tale"): cleaning a book
 * page, recognising a page already filed, and the {@code chronicle.jsonl} line {@code /mcjournal}
 * reads. Pure, so every rule is unit tested without a world.
 */
public final class ChronicleText {

    /** A page of a book holds at most this many characters in the game; the line never exceeds it. */
    public static final int MAX_PAGE = 1024;
    /** The title taken from a page's first line. */
    public static final int TITLE_MAX = 40;
    /** The file's shape version, so a reader can tell a future change. */
    public static final int LINE_VERSION = 1;

    private ChronicleText() {
    }

    /**
     * A page as the Chronicle keeps it: line endings unified, formatting codes and control
     * characters dropped, trailing spaces trimmed, runs of blank lines folded, cut to
     * {@link #MAX_PAGE}. Never null.
     */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), MAX_PAGE + 8));
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c == '\n' || c >= ' ' && c != '\u007f') {
                out.append(c);
            } else if (c == '\t') {
                out.append(' ');
            }
        }
        String[] lines = out.toString().split("\n", -1);
        StringBuilder folded = new StringBuilder();
        int blank = 0;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (trimmed.isEmpty()) {
                if (++blank > 1) {
                    continue;
                }
            } else {
                blank = 0;
            }
            if (folded.length() > 0) {
                folded.append('\n');
            }
            folded.append(trimmed);
        }
        String result = folded.toString().strip();
        if (result.length() > MAX_PAGE) {
            int cut = MAX_PAGE;
            if (Character.isHighSurrogate(result.charAt(cut - 1))) {
                cut--;
            }
            result = result.substring(0, cut).stripTrailing();
        }
        return result;
    }

    /** Whether a page has nothing worth filing: empty, or only whitespace and punctuation filler. */
    public static boolean blank(String page) {
        String cleaned = clean(page);
        for (int i = 0; i < cleaned.length(); i++) {
            if (Character.isLetterOrDigit(cleaned.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** A short title: the first line, cut to {@link #TITLE_MAX} at a word end with "..." when it was longer. */
    public static String title(String page) {
        String cleaned = clean(page);
        int end = cleaned.indexOf('\n');
        String line = (end < 0 ? cleaned : cleaned.substring(0, end)).strip();
        if (line.length() <= TITLE_MAX) {
            return line;
        }
        int cut = line.lastIndexOf(' ', TITLE_MAX);
        if (cut < TITLE_MAX / 2) {
            cut = TITLE_MAX;
        }
        if (Character.isHighSurrogate(line.charAt(cut - 1))) {
            cut--;
        }
        return line.substring(0, cut).stripTrailing() + "...";
    }

    /** A 64-bit FNV-1a hash of the cleaned page: the identity a page is filed under. */
    public static long hash(String page) {
        String cleaned = clean(page);
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < cleaned.length(); i++) {
            h ^= cleaned.charAt(i);
            h *= 0x100000001b3L;
        }
        return h;
    }

    /** The hashes of every non-blank page of a book (what a lectern's old content is recorded as). */
    public static Set<Long> hashes(List<String> pages) {
        Set<Long> out = new LinkedHashSet<>();
        for (String page : pages) {
            if (!blank(page)) {
                out.add(hash(page));
            }
        }
        return out;
    }

    /** One page that is new to the Chronicle: its place in the book and its cleaned text. */
    public record Fresh(int index, String text, long hash) {
    }

    /**
     * The pages of a book the Chronicle has not filed, in book order: not blank, and not in
     * {@code filed}. A page repeated inside the book counts once.
     */
    public static List<Fresh> fresh(Set<Long> filed, List<String> pages) {
        List<Fresh> out = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>(filed);
        for (int i = 0; i < pages.size(); i++) {
            String page = pages.get(i);
            if (blank(page)) {
                continue;
            }
            long hash = hash(page);
            if (seen.add(hash)) {
                out.add(new Fresh(i, clean(page), hash));
            }
        }
        return out;
    }

    /** {@code 2026-10-09T18:22:05Z}, the line's real time. */
    public static String iso(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).toString().replaceAll("\\.\\d+Z$", "Z");
    }

    /**
     * One {@code chronicle.jsonl} line (no trailing newline): the page id, author uuid and name, the
     * Hearth key, game time, real time, the Tales the page went with, the title and the text.
     */
    public static String jsonLine(long id, UUID author, String name, String hearth, long gameTime, long realMillis,
            double tales, String title, String text) {
        JsonObject line = new JsonObject();
        line.addProperty("v", LINE_VERSION);
        line.addProperty("id", id);
        line.addProperty("author", author.toString());
        line.addProperty("name", name == null ? "" : name);
        line.addProperty("hearth", hearth == null ? "" : hearth);
        line.addProperty("gameTime", gameTime);
        line.addProperty("real", iso(realMillis));
        line.addProperty("tales", Math.round(Math.max(0.0, tales) * 100.0) / 100.0);
        line.addProperty("title", title == null ? "" : title);
        line.addProperty("text", text == null ? "" : text);
        return new GsonBuilder().disableHtmlEscaping().create().toJson(line);
    }
}
