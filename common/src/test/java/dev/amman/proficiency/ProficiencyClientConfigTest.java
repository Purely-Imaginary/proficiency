package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import org.junit.jupiter.api.Test;

/** The spec is never loaded in a unit test, so every getter must answer with its default. */
class ProficiencyClientConfigTest {

    @Test
    void gettersFallBackToDefaultsWhenNotLoaded() {
        assertFalse(ProficiencyClientConfig.SPEC.isLoaded());
        assertTrue(ProficiencyClientConfig.bannersEnabled());
        assertTrue(ProficiencyClientConfig.bannerDiscoveries());
        assertTrue(ProficiencyClientConfig.bannerFirstTime());
        assertTrue(ProficiencyClientConfig.bannerEntry());
        assertFalse(ProficiencyClientConfig.bannerSound());
        assertEquals(1.0f, ProficiencyClientConfig.bannerScale());
        assertEquals(1f / 7f, ProficiencyClientConfig.bannerYFraction(), 1e-6);
        assertEquals(4, ProficiencyClientConfig.feedX());
        assertEquals(0.25f, ProficiencyClientConfig.feedYFraction());
        assertEquals(14, ProficiencyClientConfig.feedMaxLines());
        assertTrue(ProficiencyClientConfig.feedShowFactors());
        assertEquals(8000L, ProficiencyClientConfig.feedVisibleMs());
        assertTrue(ProficiencyClientConfig.hudXpDots());
        assertTrue(ProficiencyClientConfig.hudLevelUpFx());
        assertTrue(ProficiencyClientConfig.hudAbilityFx());
        assertTrue(ProficiencyClientConfig.hudStreakFx());
        assertTrue(ProficiencyClientConfig.hudDeathRecap());
        assertTrue(ProficiencyClientConfig.procFxEnabled());
        assertTrue(ProficiencyClientConfig.uiSkillIcons());
        assertTrue(ProficiencyClientConfig.uiJuicyFeedback());
        assertEquals("", ProficiencyClientConfig.uiWaywheelPins());
        assertTrue(ProficiencyClientConfig.tooltipSkillInfo());
        assertFalse(ProficiencyClientConfig.tooltipAlwaysDetailed());
    }

    @Test
    void styleMigrationWaitsForTheFile() {
        // The first client tick may come before the file is loaded: the migration must not throw, must not mark
        // itself done (it has to run once the file is there) and the style reads CLASSIC until then.
        ProficiencyClientConfig.migrateUiStyle();
        ProficiencyClientConfig.migrateUiStyle();
        assertFalse(ProficiencyClientConfig.SPEC.isLoaded());
        assertEquals(dev.amman.proficiency.client.UiStyle.CLASSIC, ProficiencyClientConfig.uiStyle());
    }
}
