package dev.amman.proficiency.skill;

import dev.amman.proficiency.xp.XpDomain;
import dev.amman.proficiency.xp.XpMatch;
import dev.amman.proficiency.xp.XpSources;
import dev.amman.proficiency.xp.XpSubject;
import dev.amman.proficiency.xp.XpSubjects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns a source key back into the block, entity or item behind it and asks
 * {@link FirstTimeKinds} how rare it is. Only called when a first-time bonus really happens,
 * which is rare, so the description-id map is built on first use and never on a hot path.
 * Anything that cannot be resolved (a damage type, a mod line) is x1.
 */
final class FirstTimeTiers {

    private static volatile Map<String, Object> byDescription;

    private FirstTimeTiers() {
    }

    /** Blocks first, so a block item resolves to the block, which is what its key names. */
    private static Map<String, Object> index() {
        Map<String, Object> map = byDescription;
        if (map == null) {
            synchronized (FirstTimeTiers.class) {
                map = byDescription;
                if (map == null) {
                    map = new HashMap<>();
                    for (Block block : BuiltInRegistries.BLOCK) {
                        map.putIfAbsent(block.getDescriptionId(), block);
                    }
                    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                        map.putIfAbsent(type.getDescriptionId(), type);
                    }
                    for (Item item : BuiltInRegistries.ITEM) {
                        map.putIfAbsent(item.getDescriptionId(), item);
                    }
                    byDescription = map;
                }
            }
        }
        return map;
    }

    /**
     * The first-time tier of a kind, from the "first_time" rules of xp_sources (ores x2, the rare
     * ones x5, bosses x10, big mobs x3 and x5, items by rarity in the shipped rules).
     */
    static double multiplier(String kind) {
        try {
            Object thing = index().get(kind);
            XpSubject subject = null;
            if (thing instanceof Block block) {
                subject = XpSubjects.block(block.defaultBlockState());
            } else if (thing instanceof EntityType<?> type) {
                subject = XpSubjects.entity(type, XpSubjects.defaultMaxHealth(type));
            } else if (thing instanceof Item item) {
                subject = XpSubjects.item(item);
            }
            if (subject != null) {
                XpMatch match = XpSources.table().match(XpDomain.FIRST_TIME, subject);
                if (match != null && match.rule().multiplier != null) {
                    return match.rule().multiplier;
                }
            }
        } catch (RuntimeException e) {
            // A bonus tier is a courtesy: an odd modded entry pays the flat amount, never an error.
        }
        return 1.0;
    }
}
