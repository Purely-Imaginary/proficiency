package dev.amman.proficiency.skill;

import dev.amman.proficiency.Proficiency;
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

    private static final TagKey<Block> ORES = block("c", "ores");
    private static final TagKey<Block> ORE_DIAMOND = block("c", "ores/diamond");
    private static final TagKey<Block> ORE_EMERALD = block("c", "ores/emerald");
    private static final TagKey<Block> ORE_SCRAP = block("c", "ores/netherite_scrap");
    private static final TagKey<EntityType<?>> NOTABLE_BOSSES = TagKey.create(
            Registries.ENTITY_TYPE, Proficiency.id("notable_bosses"));

    private static volatile Map<String, Object> byDescription;

    private FirstTimeTiers() {
    }

    private static TagKey<Block> block(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
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

    static double multiplier(String kind) {
        try {
            Object thing = index().get(kind);
            if (thing instanceof Block block) {
                var holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
                boolean premium = holder.is(ORE_DIAMOND) || holder.is(ORE_EMERALD)
                        || holder.is(ORE_SCRAP)
                        || block == net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS;
                return FirstTimeKinds.blockMultiplier(premium || holder.is(ORES), premium);
            }
            if (thing instanceof EntityType<?> type) {
                // Boss first: a boss with no max-health attribute must still pay x10.
                if (type.is(NOTABLE_BOSSES)) {
                    return FirstTimeKinds.entityMultiplier(true, 0.0);
                }
                return FirstTimeKinds.entityMultiplier(false, defaultMaxHealth(type));
            }
            if (thing instanceof Item item) {
                return FirstTimeKinds.itemMultiplier(item.getDefaultInstance().getRarity());
            }
        } catch (RuntimeException e) {
            // A bonus tier is a courtesy: an odd modded entry pays the flat amount, never an error.
        }
        return 1.0;
    }

    @SuppressWarnings("unchecked")
    private static double defaultMaxHealth(EntityType<?> type) {
        EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
        if (!DefaultAttributes.hasSupplier(living)) {
            return 0.0;
        }
        var supplier = DefaultAttributes.getSupplier(living);
        return supplier.hasAttribute(Attributes.MAX_HEALTH)
                ? supplier.getBaseValue(Attributes.MAX_HEALTH) : 0.0;
    }
}
