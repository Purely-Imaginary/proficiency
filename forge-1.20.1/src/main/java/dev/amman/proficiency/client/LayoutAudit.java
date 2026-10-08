package dev.amman.proficiency.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Developer tool, off unless {@code PROFICIENCY_LAYOUT_AUDIT=<dir>} is in the client's environment.
 * Writes the width in GUI pixels of every character the font can draw (so a script can measure any
 * string in any language without a screenshot) and, while a demo runs, counts how often a screen
 * had to clip or wrap text, per site ({@link TextFit#clips}). See {@link LayoutDemo}.
 */
final class LayoutAudit {

    private static final String DIR = System.getenv("PROFICIENCY_LAYOUT_AUDIT");

    private LayoutAudit() {
    }

    static boolean active() {
        return DIR != null && !DIR.isEmpty();
    }

    static Path dir() {
        return Path.of(DIR);
    }

    /** {"<codepoint>": width} for U+0020 to U+FFFF, as the current font measures it. */
    static void dumpGlyphWidths(Font font) {
        try {
            Files.createDirectories(dir());
            JsonObject out = new JsonObject();
            for (int cp = 0x20; cp <= 0xFFFF; cp++) {
                if (cp >= 0xD800 && cp <= 0xDFFF) {
                    continue;
                }
                int width = font.width(new String(Character.toChars(cp)));
                out.addProperty(Integer.toString(cp), width);
            }
            Files.writeString(dir().resolve("glyphs.json"), new GsonBuilder().create().toJson(out),
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void note(String text) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve("demo.log"), text + "\n", StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            // A log that cannot be written must not stop the client.
        }
    }
}
