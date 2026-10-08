package dev.amman.proficiency.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BannerStyleTest {

    @Test
    void vanillaDimensionsHaveTheirColours() {
        assertEquals(BannerStyle.OVERWORLD, BannerStyle.dimensionTint("minecraft:overworld"));
        assertEquals(BannerStyle.NETHER, BannerStyle.dimensionTint("minecraft:the_nether"));
        assertEquals(BannerStyle.END, BannerStyle.dimensionTint("minecraft:the_end"));
    }

    @Test
    void moddedDimensionTintIsStableAndReadable() {
        int a = BannerStyle.dimensionTint("twilightforest:twilight_forest");
        assertEquals(a, BannerStyle.dimensionTint("twilightforest:twilight_forest"));
        int max = Math.max((a >> 16) & 0xFF, Math.max((a >> 8) & 0xFF, a & 0xFF));
        int min = Math.min((a >> 16) & 0xFF, Math.min((a >> 8) & 0xFF, a & 0xFF));
        assertTrue(max >= 200, "bright enough to read on a dark scene");
        assertTrue(min >= 80, "never a near-black channel set");
    }

    @Test
    void revealFinishesInsideTheCap() {
        for (int length : new int[] {1, 5, 24, 25, 40, 120}) {
            long perLetter = BannerStyle.perLetter(length);
            assertTrue(perLetter >= 1 && perLetter <= BannerStyle.LETTER_MS);
            assertTrue(length * perLetter <= BannerStyle.REVEAL_CAP_MS, "length " + length);
        }
        assertEquals(25, BannerStyle.perLetter(8));
    }

    @Test
    void lettersStartOneAtATime() {
        assertEquals(1, BannerStyle.started(0, 10));
        assertEquals(2, BannerStyle.started(25, 10));
        assertEquals(10, BannerStyle.started(10_000, 10));
        assertEquals(0, BannerStyle.started(100, 0));
    }

    @Test
    void structureFamiliesAndTheGenericFallback() {
        assertEquals(BannerStyle.ICON_VILLAGE, BannerStyle.iconFamily("minecraft:village_plains"));
        assertEquals(BannerStyle.ICON_OUTPOST, BannerStyle.iconFamily("minecraft:pillager_outpost"));
        assertEquals(BannerStyle.ICON_TEMPLE, BannerStyle.iconFamily("minecraft:desert_pyramid"));
        assertEquals(BannerStyle.ICON_TEMPLE, BannerStyle.iconFamily("minecraft:jungle_pyramid"));
        assertEquals(BannerStyle.ICON_STRONGHOLD, BannerStyle.iconFamily("minecraft:stronghold"));
        assertEquals(BannerStyle.ICON_MANSION, BannerStyle.iconFamily("minecraft:mansion"));
        assertEquals(BannerStyle.ICON_MONUMENT, BannerStyle.iconFamily("minecraft:monument"));
        assertEquals(BannerStyle.ICON_MINESHAFT, BannerStyle.iconFamily("minecraft:mineshaft_mesa"));
        assertEquals(BannerStyle.ICON_GENERIC, BannerStyle.iconFamily("minecraft:igloo"));
        assertEquals(BannerStyle.ICON_GENERIC, BannerStyle.iconFamily("yungsbetterdungeons:skeleton_dungeon"));
        assertEquals(BannerStyle.ICON_NONE, BannerStyle.iconFamily(""));
    }
}
