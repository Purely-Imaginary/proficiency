package dev.amman.proficiency.client;

import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** BannerStyle copies vanilla's hsvToRgb so it can live in core; it must give vanilla's colours. */
class HsvToRgbTest {

    @Test
    void sameAsVanilla() {
        for (int h = 0; h < 360; h++) {
            for (float s : new float[] {0f, 0.3f, 0.55f, 1f}) {
                for (float v : new float[] {0f, 0.5f, 0.95f, 1f}) {
                    float hue = h / 360f;
                    assertEquals(Mth.hsvToRgb(hue, s, v), BannerStyle.hsvToRgb(hue, s, v), h + " " + s + " " + v);
                }
            }
        }
    }
}
