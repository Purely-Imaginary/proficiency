package dev.amman.proficiency.xp;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** {@link XpSubject}s over the real registries. */
public final class XpSubjects {

    private XpSubjects() {
    }

    private static final Map<String, TagKey<?>> TAG_KEYS = new ConcurrentHashMap<>();

    /** One TagKey per registry and tag text, so the hot path allocates nothing. */
    @SuppressWarnings("unchecked")
    private static <T> TagKey<T> tagKey(ResourceKey<? extends Registry<T>> registry, String tag) {
        return (TagKey<T>) TAG_KEYS.computeIfAbsent(registry.location() + "|" + tag, k -> {
            ResourceLocation location = ResourceLocation.tryParse(tag);
            return location == null ? null : TagKey.create(registry, location);
        });
    }

    private abstract static class Base<T> implements XpSubject {
        private final String kind;
        private final String id;
        private final Holder<T> holder;
        private final ResourceKey<? extends Registry<T>> registry;

        Base(String kind, ResourceLocation id, Holder<T> holder, ResourceKey<? extends Registry<T>> registry) {
            this.kind = kind;
            this.id = id == null ? "minecraft:unknown" : id.toString();
            this.holder = holder;
            this.registry = registry;
        }

        @Override
        public String kind() {
            return kind;
        }

        @Override
        public String id() {
            return id;
        }

        abstract boolean builtin(String tag);

        @Override
        public boolean hasTag(String tag) {
            if (XpBuiltin.isBuiltin(tag)) {
                return builtin(tag);
            }
            if (holder == null || registry == null) {
                return false;
            }
            TagKey<T> key = tagKey(registry, tag);
            return key != null && holder.is(key);
        }
    }

    public static XpSubject block(BlockState state, double hardness) {
        Block block = state.getBlock();
        return new Base<Block>("block", BuiltInRegistries.BLOCK.getKey(block),
                BuiltInRegistries.BLOCK.wrapAsHolder(block), Registries.BLOCK) {
            @Override
            boolean builtin(String tag) {
                return XpBuiltin.block(tag, state);
            }

            @Override
            public double hardness() {
                return hardness;
            }
        };
    }

    /** A block with no world to ask for hardness: placing, and the client's ability key. */
    public static XpSubject block(BlockState state) {
        return block(state, 0.0);
    }

    public static XpSubject item(Item item) {
        return new Base<Item>("item", BuiltInRegistries.ITEM.getKey(item),
                BuiltInRegistries.ITEM.wrapAsHolder(item), Registries.ITEM) {
            @Override
            boolean builtin(String tag) {
                return XpBuiltin.item(tag, item);
            }
        };
    }

    public static XpSubject item(ItemStack stack) {
        return item(stack.getItem());
    }

    public static XpSubject entity(EntityType<?> type, double maxHealth) {
        return new Base<EntityType<?>>("entity", BuiltInRegistries.ENTITY_TYPE.getKey(type),
                BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type), Registries.ENTITY_TYPE) {
            @Override
            boolean builtin(String tag) {
                return false;
            }

            @Override
            public double maxHealth() {
                return maxHealth;
            }
        };
    }

    /** The max health an entity type is born with, 0 for a type with no attributes (arrows, items). */
    @SuppressWarnings("unchecked")
    public static double defaultMaxHealth(EntityType<?> type) {
        try {
            EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
            if (!DefaultAttributes.hasSupplier(living)) {
                return 0.0;
            }
            var supplier = DefaultAttributes.getSupplier(living);
            return supplier.hasAttribute(Attributes.MAX_HEALTH)
                    ? supplier.getBaseValue(Attributes.MAX_HEALTH) : 0.0;
        } catch (RuntimeException e) {
            return 0.0;
        }
    }

    public static XpSubject entity(LivingEntity entity) {
        return entity(entity.getType(), entity.getMaxHealth());
    }

    public static XpSubject structure(ResourceLocation id, Holder<Structure> holder) {
        return new Base<Structure>("structure", id, holder, Registries.STRUCTURE) {
            @Override
            boolean builtin(String tag) {
                return false;
            }
        };
    }

    public static XpSubject biome(ResourceLocation id, Holder<Biome> holder) {
        return new Base<Biome>("biome", id, holder, Registries.BIOME) {
            @Override
            boolean builtin(String tag) {
                return false;
            }
        };
    }

    public static XpSubject dimension(ResourceLocation id) {
        return new Base<Object>("dimension", id, null, null) {
            @Override
            boolean builtin(String tag) {
                return false;
            }
        };
    }
}
