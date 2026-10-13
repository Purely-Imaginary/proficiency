package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.hearth.HearthPool;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The Hearth's tale panel keeps the death recap's timings and its number and stars light up on their own clocks. */
class TalesPayInHudTest {

    @Test
    void thePanelFadesLikeTheDeathRecap() {
        assertEquals(DeathRecapHud.TOTAL_MS, TalesPayInHud.TOTAL_MS);
        assertEquals(DeathRecapHud.FADE_IN_MS, TalesPayInHud.FADE_IN_MS);
        assertEquals(DeathRecapHud.FADE_OUT_MS, TalesPayInHud.FADE_OUT_MS);
        for (long t : new long[] {0, 100, 250, 1000, 4000, 4500, 4900, 5000}) {
            assertEquals(DeathRecapHud.opacity(t), TalesPayInHud.opacity(t), 1e-6, "at " + t + " ms");
        }
        assertEquals(0f, TalesPayInHud.opacity(-5), 1e-6);
    }

    @Test
    void theNumberRollsUpOnceThePanelIsUp() {
        assertEquals(0f, TalesPayInHud.roll(0), 1e-6);
        assertEquals(0f, TalesPayInHud.roll(TalesPayInHud.ROLL_DELAY_MS), 1e-6);
        assertEquals(1f, TalesPayInHud.roll(TalesPayInHud.ROLL_DELAY_MS + TalesPayInHud.ROLL_MS), 1e-6);
        float last = 0f;
        for (long t = 0; t <= 1500; t += 50) {
            float now = TalesPayInHud.roll(t);
            assertTrue(now >= last, "the number never counts down: " + t);
            last = now;
        }
        assertTrue(TalesPayInHud.ROLL_DELAY_MS + TalesPayInHud.ROLL_MS < TalesPayInHud.TOTAL_MS - TalesPayInHud.FADE_OUT_MS,
                "the number has landed before the panel starts to fade");
    }

    @Test
    void theStarsLightOneByOne() {
        assertEquals(0, TalesPayInHud.starsLit(4, 0));
        assertEquals(0, TalesPayInHud.starsLit(4, TalesPayInHud.STAR_DELAY_MS - 1));
        assertEquals(1, TalesPayInHud.starsLit(4, TalesPayInHud.STAR_DELAY_MS));
        assertEquals(2, TalesPayInHud.starsLit(4, TalesPayInHud.STAR_DELAY_MS + TalesPayInHud.STAR_STAGGER_MS));
        assertEquals(4, TalesPayInHud.starsLit(4, TalesPayInHud.TOTAL_MS), "never more than the rating");
        assertEquals(0, TalesPayInHud.starsLit(0, TalesPayInHud.TOTAL_MS), "a zero-star tale lights none");
        assertTrue(TalesPayInHud.STAR_DELAY_MS + 5 * TalesPayInHud.STAR_STAGGER_MS < TalesPayInHud.TOTAL_MS - TalesPayInHud.FADE_OUT_MS,
                "all five can be lit before the fade");
    }

    @Test
    void everyIconThePanelDrawsIsASixteenPixelTexture() throws IOException {
        java.util.List<String> paths = new java.util.ArrayList<>();
        paths.add("/assets/proficiency/textures/gui/hearth/tales.png");
        for (HearthPool pool : HearthPool.VALUES) {
            paths.add("/assets/proficiency/textures/gui/hearth/pool/" + pool.id() + ".png");
        }
        for (String path : paths) {
            try (InputStream in = TalesPayInHudTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "missing " + path);
                BufferedImage image = ImageIO.read(in);
                assertEquals(16, image.getWidth(), path);
                assertEquals(16, image.getHeight(), path);
            }
        }
    }

    @Test
    void thePanelSitsAtTheDeathRecapsHeight() {
        // At 10% it covered a minimap in the top right corner (Xaero's in the Reclamation and Pandowo packs). Both
        // panels ask the same rule, so moving the recap moves this one with it, under a banner too.
        for (int guiHeight : new int[] {120, 240, 266, 270, 360, 540, 1080}) {
            for (int banner : new int[] {0, 60, 200}) {
                assertEquals(DeathRecapHud.topFor(guiHeight, banner), TalesPayInHud.wantedTop(guiHeight, banner),
                        "at " + guiHeight + " under a banner ending at " + banner);
            }
        }
        assertTrue(TalesPayInHud.wantedTop(270, 0) >= 270 * 29 / 100, "below a minimap reaching 29% of the screen");
    }
}
