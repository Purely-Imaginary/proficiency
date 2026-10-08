package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.Skill;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The skill icons: one 16x16 texture per skill, and the rule that an icon never overflows a row. */
class SkillIconsTest {

    @Test
    void everySkillHasItsOwnSixteenPixelIcon() throws IOException {
        Map<String, Skill> seen = new HashMap<>();
        for (Skill skill : Skill.VALUES) {
            String path = "/assets/proficiency/textures/gui/skill/" + skill.id() + ".png";
            try (InputStream in = SkillIconsTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "no icon for " + skill.id() + " (run tools/gen_skill_icons.py)");
                BufferedImage image = ImageIO.read(in);
                assertEquals(16, image.getWidth(), skill.id());
                assertEquals(16, image.getHeight(), skill.id());
                assertTrue(image.getColorModel().hasAlpha(), skill.id() + " has no transparency");
                int[] pixels = image.getRGB(0, 0, 16, 16, null, 0, 16);
                int opaque = 0;
                for (int argb : pixels) {
                    int alpha = argb >>> 24;
                    assertTrue(alpha == 0 || alpha == 255, skill.id() + " is anti-aliased");
                    if (alpha == 255) {
                        opaque++;
                    }
                }
                assertTrue(opaque >= 40 && opaque < 256, skill.id() + " is empty or has no background");
                Skill twin = seen.put(Arrays.toString(pixels), skill);
                assertTrue(twin == null, skill.id() + " is a copy of " + twin);
            }
        }
    }

    @Test
    void fitShrinksThenDropsTheIcon() {
        assertEquals(16, SkillIcons.fit(100, 82, 16));
        assertEquals(8, SkillIcons.fit(100, 83, 16));
        assertEquals(8, SkillIcons.fit(100, 90, 8));
        assertEquals(0, SkillIcons.fit(100, 91, 8));
        assertEquals(0, SkillIcons.fit(100, 50, 0));
    }

    @Test
    void advanceIsIconPlusGapOrNothing() {
        assertEquals(0, SkillIcons.advance(0));
        assertEquals(10, SkillIcons.advance(8));
        assertEquals(18, SkillIcons.advance(16));
    }
}
