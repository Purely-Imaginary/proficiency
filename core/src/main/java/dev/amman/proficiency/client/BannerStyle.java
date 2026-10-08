package dev.amman.proficiency.client;

/**
 * The pure parts of the discovery banner's look: the dimension tint, how many letters of the title
 * show at a given age, and which icon family a structure id belongs to. No Minecraft types, so a
 * unit test can cover them.
 */
final class BannerStyle {

    static final long LETTER_MS = 25;
    static final long REVEAL_CAP_MS = 600;
    /** How long each new letter takes to ink in. */
    static final long INK_MS = 150;

    static final int OVERWORLD = 0x7BD35A;
    static final int NETHER = 0xFF5A3C;
    static final int END = 0xB57BFF;

    static final int ICON_NONE = -1;
    static final int ICON_GENERIC = 0;
    static final int ICON_VILLAGE = 1;
    static final int ICON_OUTPOST = 2;
    static final int ICON_TEMPLE = 3;
    static final int ICON_STRONGHOLD = 4;
    static final int ICON_MANSION = 5;
    static final int ICON_MONUMENT = 6;
    static final int ICON_MINESHAFT = 7;

    private BannerStyle() {
    }

    /** Green, red and purple for the vanilla three; a readable hue from the id for anything else. */
    static int dimensionTint(String dimensionId) {
        switch (dimensionId) {
            case "minecraft:overworld":
                return OVERWORLD;
            case "minecraft:the_nether":
                return NETHER;
            case "minecraft:the_end":
                return END;
            default:
                float hue = (dimensionId.hashCode() & 0x7FFFFFFF) % 360 / 360f;
                return hsvToRgb(hue, 0.55f, 0.95f);
        }
    }

    /** The kicker and rules: the tint pulled halfway to a warm grey so it stays quieter than the title. */
    static int kickerTint(int tint) {
        return mix(tint, 0xCFC8B8, 0.45f);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) * (1 - t) + ((b >> 16) & 0xFF) * t);
        int g = Math.round(((a >> 8) & 0xFF) * (1 - t) + ((b >> 8) & 0xFF) * t);
        int bl = Math.round((a & 0xFF) * (1 - t) + (b & 0xFF) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Milliseconds per letter: 25, shortened so a long title still finishes inside the cap. */
    static long perLetter(int length) {
        return Math.max(1L, Math.min(LETTER_MS, REVEAL_CAP_MS / Math.max(1, length)));
    }

    /** Letters that have started to appear at this age. At least one, at most the whole title. */
    static int started(long age, int length) {
        if (length <= 0) {
            return 0;
        }
        long n = age / perLetter(length) + 1;
        return (int) Math.min(length, Math.max(1L, n));
    }

    /** When the last letter has finished inking in. */
    static long revealDone(int length) {
        return length * perLetter(length) + INK_MS;
    }

    /** Only vanilla structures get a family icon; modded ones fall back to the generic map. */
    static int iconFamily(String structureId) {
        if (structureId == null || structureId.isEmpty()) {
            return ICON_NONE;
        }
        if (!structureId.startsWith("minecraft:")) {
            return ICON_GENERIC;
        }
        String path = structureId.substring(10);
        if (path.startsWith("village")) {
            return ICON_VILLAGE;
        }
        if (path.contains("outpost")) {
            return ICON_OUTPOST;
        }
        if (path.contains("temple") || path.contains("pyramid")) {
            return ICON_TEMPLE;
        }
        if (path.startsWith("stronghold")) {
            return ICON_STRONGHOLD;
        }
        if (path.contains("mansion")) {
            return ICON_MANSION;
        }
        if (path.contains("monument")) {
            return ICON_MONUMENT;
        }
        if (path.startsWith("mineshaft")) {
            return ICON_MINESHAFT;
        }
        return ICON_GENERIC;
    }

    /** Vanilla's {@code Mth.hsvToRgb}, line for line, so this class needs no Minecraft. */
    static int hsvToRgb(float hue, float saturation, float value) {
        int sector = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - sector;
        float p = value * (1.0F - saturation);
        float q = value * (1.0F - f * saturation);
        float t = value * (1.0F - (1.0F - f) * saturation);
        float r;
        float g;
        float b;
        switch (sector) {
            case 0 -> {
                r = value;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = value;
                b = p;
            }
            case 2 -> {
                r = p;
                g = value;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = value;
            }
            case 4 -> {
                r = t;
                g = p;
                b = value;
            }
            case 5 -> {
                r = value;
                g = p;
                b = q;
            }
            default -> throw new IllegalArgumentException("hue " + hue);
        }
        return channel(r) << 16 | channel(g) << 8 | channel(b);
    }

    private static int channel(float c) {
        return Math.max(0, Math.min(255, (int) (c * 255.0F)));
    }
}
