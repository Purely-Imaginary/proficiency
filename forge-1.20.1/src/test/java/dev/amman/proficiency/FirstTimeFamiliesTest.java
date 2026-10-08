package dev.amman.proficiency;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.FirstTimeKinds;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kind families, the daily build cap and its persistence, the tier maths, the log source. */
class FirstTimeFamiliesTest {

    @Test
    void variantsShareOneKind() {
        assertEquals("block.minecraft.iron_ore", FirstTimeKinds.canonical("block.minecraft.deepslate_iron_ore"));
        assertEquals("block.minecraft.oak_log", FirstTimeKinds.canonical("block.minecraft.stripped_oak_log"));
        assertEquals("block.minecraft.oak_log", FirstTimeKinds.canonical("block.minecraft.oak_wood"));
        assertEquals("block.minecraft.oak_log", FirstTimeKinds.canonical("block.minecraft.stripped_oak_wood"));
        assertEquals("block.minecraft.crimson_stem", FirstTimeKinds.canonical("block.minecraft.stripped_crimson_hyphae"));
        assertEquals("block.minecraft.crimson_stem", FirstTimeKinds.canonical("block.minecraft.crimson_hyphae"));
        assertEquals("block.mod.tin_ore", FirstTimeKinds.canonical("block.mod.deepslate_tin_ore"));
    }

    @Test
    void everythingElseIsUntouched() {
        assertEquals("block.minecraft.deepslate_bricks", FirstTimeKinds.canonical("block.minecraft.deepslate_bricks"));
        assertEquals("block.minecraft.deepslate", FirstTimeKinds.canonical("block.minecraft.deepslate"));
        assertEquals("block.minecraft.wood", FirstTimeKinds.canonical("block.minecraft.wood"));
        assertEquals("entity.minecraft.stripped_oak_wood", FirstTimeKinds.canonical("entity.minecraft.stripped_oak_wood"));
        assertEquals("item.minecraft.deepslate_iron_ore", FirstTimeKinds.canonical("item.minecraft.deepslate_iron_ore"));
        assertEquals("block.minecraft.", FirstTimeKinds.canonical("block.minecraft."));
        assertEquals(null, FirstTimeKinds.canonical(null));
    }

    @Test
    void theFirstTimeSourceIsNotAKind() {
        assertFalse(SkillService.isKind(FirstTimeKinds.FIRST_PREFIX + "block.minecraft.iron_ore"));
    }

    @Test
    void buildCapCountsPerSkillPerDay() {
        PlayerSkills skills = new PlayerSkills();
        for (int i = 0; i < 3; i++) {
            assertTrue(skills.takeBuildFirstTime("masonry", 100L, 3));
        }
        assertFalse(skills.takeBuildFirstTime("masonry", 100L, 3));
        assertTrue(skills.takeBuildFirstTime("decorating", 100L, 3), "each skill has its own count");
        assertTrue(skills.takeBuildFirstTime("masonry", 101L, 3), "a new day starts at zero");
        assertFalse(skills.takeBuildFirstTime("masonry", 100L, 0), "cap 0 pays none");
    }

    @Test
    void buildCountSurvivesASaveAndACopy() {
        PlayerSkills original = new PlayerSkills();
        original.takeBuildFirstTime("masonry", 200L, 2);
        original.takeBuildFirstTime("masonry", 200L, 2);
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE,
                PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                        .getOrThrow(false, m -> { throw new AssertionError(m); }))
                .getOrThrow(false, m -> { throw new AssertionError(m); });
        assertFalse(restored.takeBuildFirstTime("masonry", 200L, 2), "a relog must not refill the cap");
        PlayerSkills copy = new PlayerSkills();
        copy.copyFrom(original);
        assertFalse(copy.takeBuildFirstTime("masonry", 200L, 2), "a respawn must not refill it either");
        assertTrue(Skill.MASONRY != null && FirstTimeKinds.isBuildSkill(Skill.DECORATING));
        assertFalse(FirstTimeKinds.isBuildSkill(Skill.MINING));
    }

    @Test
    void tierMultipliers() {
        assertEquals(1.0, FirstTimeKinds.blockMultiplier(false, false));
        assertEquals(2.0, FirstTimeKinds.blockMultiplier(true, false));
        assertEquals(5.0, FirstTimeKinds.blockMultiplier(true, true));
        assertEquals(10.0, FirstTimeKinds.entityMultiplier(true, 20));
        assertEquals(1.0, FirstTimeKinds.entityMultiplier(false, 39.9));
        assertEquals(3.0, FirstTimeKinds.entityMultiplier(false, 40));
        assertEquals(3.0, FirstTimeKinds.entityMultiplier(false, 99));
        assertEquals(5.0, FirstTimeKinds.entityMultiplier(false, 100));
        assertEquals(1.0, FirstTimeKinds.itemMultiplier(Rarity.COMMON.name()));
        assertEquals(2.0, FirstTimeKinds.itemMultiplier(Rarity.UNCOMMON.name()));
        assertEquals(3.0, FirstTimeKinds.itemMultiplier(Rarity.RARE.name()));
        assertEquals(5.0, FirstTimeKinds.itemMultiplier(Rarity.EPIC.name()));
        assertEquals(1.0, FirstTimeKinds.itemMultiplier(null));
    }

    @Test
    void legacyVariantsListEveryRawFormOfAKind() {
        var iron = FirstTimeKinds.legacyVariants("block.minecraft.iron_ore");
        assertTrue(iron.contains("block.minecraft.iron_ore"));
        assertTrue(iron.contains("block.minecraft.deepslate_iron_ore"));
        var oak = FirstTimeKinds.legacyVariants("block.minecraft.oak_log");
        for (String raw : new String[] {"stripped_oak_log", "oak_wood", "stripped_oak_wood"}) {
            assertTrue(oak.contains("block.minecraft." + raw), raw);
        }
        var stem = FirstTimeKinds.legacyVariants("block.minecraft.crimson_stem");
        assertTrue(stem.contains("block.minecraft.crimson_hyphae"));
        assertTrue(stem.contains("block.minecraft.stripped_crimson_hyphae"));
        assertEquals(java.util.List.of("entity.minecraft.creeper"),
                FirstTimeKinds.legacyVariants("entity.minecraft.creeper"));
        for (String kind : new String[] {"block.minecraft.iron_ore", "block.minecraft.oak_log"}) {
            for (String v : FirstTimeKinds.legacyVariants(kind)) {
                assertEquals(kind, FirstTimeKinds.canonical(v));
            }
        }
    }
}
