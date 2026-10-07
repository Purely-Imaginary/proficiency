package dev.amman.proficiency;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SkillService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which XP sources earn the one-time "first time" bonus, and that the feed flag survives a save. */
class FirstTimeKindTest {

    @Test
    void blocksMobsItemsAndDamageAreKinds() {
        assertTrue(SkillService.isKind("block.minecraft.iron_ore"));
        assertTrue(SkillService.isKind("entity.minecraft.creeper"));
        assertTrue(SkillService.isKind("item.minecraft.bread"));
        assertTrue(SkillService.isKind("proficiency.xplog.damage.fall"));
    }

    @Test
    void placesAndThisModsOwnLinesAreNot() {
        assertFalse(SkillService.isKind(null));
        assertFalse(SkillService.isKind(""));
        assertFalse(SkillService.isKind("proficiency.xplog.source.sprinting"));
        assertFalse(SkillService.isKind("proficiency.xplog.source.command"));
        assertFalse(SkillService.isKind("biome.minecraft.plains"));
        assertFalse(SkillService.isKind("dimension.minecraft.the_nether"));
        assertFalse(SkillService.isKind("structure.minecraft.village_plains"));
    }

    @Test
    void theXpFeedFlagIsSaved() {
        PlayerSkills skills = new PlayerSkills();
        skills.setXpFeed(true);
        var json = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, skills).getOrThrow();
        PlayerSkills back = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(back.xpFeed());
    }
}
