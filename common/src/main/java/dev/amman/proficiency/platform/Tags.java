package dev.amman.proficiency.platform;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * The three NeoForge common tags the mod reads. On 1.21 NeoForge and Fabric share the {@code c:}
 * convention namespace, so these are the same tags Fabric API fills; {@code c:is_magic} is also
 * shipped by this mod as data in case nothing else defines it.
 */
public final class Tags {

    private Tags() {
    }

    public static final class Blocks {
        public static final TagKey<Block> ORES = TagKey.create(Registries.BLOCK, c("ores"));
        public static final TagKey<Block> GRAVELS = TagKey.create(Registries.BLOCK, c("gravels"));

        private Blocks() {
        }
    }

    public static final class DamageTypes {
        public static final TagKey<DamageType> IS_MAGIC = TagKey.create(Registries.DAMAGE_TYPE, c("is_magic"));

        private DamageTypes() {
        }
    }

    public static final class EntityTypes {
        public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE, c("bosses"));

        private EntityTypes() {
        }
    }

    private static ResourceLocation c(String path) {
        return ResourceLocation.fromNamespaceAndPath("c", path);
    }
}
