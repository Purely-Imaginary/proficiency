package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The blocks and items of the build: what a role of the stage designs is made of in the game (block tags,
 * so packs widen them), and the six offerings from the Hearth data.
 *
 * <ul>
 * <li>Each role of docs/HEARTH-DESIGNS-SPEC.md's legend is a block tag ({@link #ROLE_TAGS}):
 * {@code #proficiency:hearth/stone} for S, {@code .../stone_shaped} for s, {@code #minecraft:logs} for W,
 * {@code #minecraft:planks} for P, {@code .../wood_shaped} for w and for p (the lantern post, drawn as a fence), {@code #minecraft:banners} for N,
 * {@code .../iron}, {@code .../copper}, {@code .../gold}, {@code .../iron_bars}, {@code .../chain},
 * {@code .../lantern}, {@code #minecraft:anvil}, {@code #minecraft:cauldrons}, {@code .../bell},
 * {@code .../lightning_rod} and {@code .../lodestone}; and the dressing roles: {@code #minecraft:flower_pots},
 * {@code .../storage}, {@code #minecraft:candles}, {@code .../carpet}, {@code .../glass}, {@code .../skull},
 * {@code .../ice}, {@code .../snow}, {@code .../moss}, {@code .../magma}, {@code .../obsidian} and {@code #minecraft:leaves}. The air role (a dot) is air.</li>
 * <li>Each stage's {@code offering} is an item id or an item tag ({@code #ns:path}).</li>
 * </ul>
 */
public final class StageBlocks {

    public static final TagKey<Block> LANTERN = TagKey.create(Registries.BLOCK, Proficiency.id("hearth/lantern"));
    public static final TagKey<Block> BELL = TagKey.create(Registries.BLOCK, Proficiency.id("hearth/bell"));

    /** Role letter to block tag id ({@code #} marks a vanilla tag), one per role of the legend. */
    private static final String[][] ROLE_IDS = {
            {"S", "proficiency:hearth/stone"}, {"s", "proficiency:hearth/stone_shaped"},
            {"W", "minecraft:logs"}, {"P", "minecraft:planks"}, {"w", "proficiency:hearth/wood_shaped"},
            {"p", "proficiency:hearth/wood_shaped"},
            {"N", "minecraft:banners"}, {"i", "proficiency:hearth/iron"}, {"c", "proficiency:hearth/copper"},
            {"g", "proficiency:hearth/gold"}, {"b", "proficiency:hearth/iron_bars"},
            {"h", "proficiency:hearth/chain"}, {"l", "proficiency:hearth/lantern"},
            {"a", "minecraft:anvil"}, {"u", "minecraft:cauldrons"}, {"B", "proficiency:hearth/bell"},
            {"r", "proficiency:hearth/lightning_rod"}, {"k", "proficiency:hearth/lodestone"},
            {"f", "minecraft:flower_pots"}, {"x", "proficiency:hearth/storage"}, {"C", "minecraft:candles"},
            {"m", "proficiency:hearth/carpet"}, {"G", "proficiency:hearth/glass"}, {"z", "proficiency:hearth/skull"},
            {"I", "proficiency:hearth/ice"}, {"n", "proficiency:hearth/snow"}, {"q", "proficiency:hearth/moss"},
            {"Y", "proficiency:hearth/magma"}, {"O", "proficiency:hearth/obsidian"}, {"L", "minecraft:leaves"}};

    /** The block tag of each role letter (indexed by the letter); null for air, the fire and the blank. */
    @SuppressWarnings("unchecked")
    private static final TagKey<Block>[] ROLE_TAGS = new TagKey[128];

    static {
        for (String[] role : ROLE_IDS) {
            ResourceLocation id = ResourceLocation.tryParse(role[1]);
            ROLE_TAGS[role[0].charAt(0)] = TagKey.create(Registries.BLOCK, id);
        }
    }

    private StageBlocks() {
    }

    /**
     * Whether a state is a fluid SOURCE block of a fluid role: {@code 'v'} water, {@code 'V'} lava. These roles
     * are not block tags: the fluid state decides, and only a fluid block counts (a waterlogged block holds
     * water too, but it is no source block of its own; flowing water is no source).
     */
    public static boolean isFluidSource(char role, BlockState state) {
        if (!(state.getBlock() instanceof LiquidBlock)) {
            return false;
        }
        var fluid = state.getFluidState();
        return fluid.isSource() && fluid.is(role == 'v' ? FluidTags.WATER : FluidTags.LAVA);
    }

    /** The block tag a role is made of ({@code 'S'} gives {@code #proficiency:hearth/stone}); null for air, F and unknown. */
    @Nullable
    public static TagKey<Block> roleTag(char role) {
        return role < ROLE_TAGS.length ? ROLE_TAGS[role] : null;
    }

    /**
     * A concrete block for a role, for the guide's previews and for tests: {@link HearthDesigns#defaultBlock}
     * resolved (air for the dot, a campfire for the fire, the water and lava source blocks for v and V).
     */
    public static BlockState defaultState(char role) {
        String id = HearthDesigns.defaultBlock(role);
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        return location == null ? Blocks.AIR.defaultBlockState()
                : BuiltInRegistries.BLOCK.getOptional(location).orElse(Blocks.AIR).defaultBlockState();
    }

    /**
     * The block a lane suggests for a role (its palette entry, else the default), as a default state: floor
     * variants for skulls and banners, a chest facing north, a single candle. Air for the dot, the fire's
     * campfire for F, air for an id that is not in the registry.
     */
    public static BlockState laneState(HearthDesigns.Lane lane, char role) {
        String id = lane.block(role);
        ResourceLocation location = id == null ? null : ResourceLocation.tryParse(id);
        return location == null ? Blocks.AIR.defaultBlockState()
                : BuiltInRegistries.BLOCK.getOptional(location).orElse(Blocks.AIR).defaultBlockState();
    }

    private static HearthData.Stage stageSpec(HearthData data, int stage) {
        List<HearthData.Stage> stages = data.stages();
        if (stage >= 1 && stage <= stages.size()) {
            return stages.get(stage - 1);
        }
        return HearthData.DEFAULTS.stages().get(Math.max(0, Math.min(HearthData.TIERS, stage) - 1));
    }

    // ---- Parsed once per data install ------------------------------------------------------------

    /**
     * The six offerings of one {@link HearthData}, parsed once: a reload installs a new data object, so the
     * last one parsed is kept and compared by identity. Every click reads this instead of parsing six ids again.
     */
    private static final class Parsed {
        final HearthData data;
        final Offerings offerings;

        Parsed(HearthData data) {
            this.data = data;
            List<String> offerings = new ArrayList<>(HearthData.TIERS);
            for (int stage = 1; stage <= HearthData.TIERS; stage++) {
                offerings.add(stageSpec(data, stage).offering());
            }
            this.offerings = new Offerings(offerings);
        }
    }

    @Nullable
    private static volatile Parsed parsed;

    private static Parsed parsed(HearthData data) {
        Parsed p = parsed;
        if (p == null || p.data != data) {
            p = new Parsed(data);
            parsed = p;
        }
        return p;
    }

    /**
     * The six offerings, each an item id or an item tag ({@code #ns:path}), resolved once. Built from the
     * server's data here, and on a client from the ids the state payload carries
     * ({@link HearthOfferings#serverOfferings}), so a pack's offerings are known on both sides.
     */
    public static final class Offerings {
        private final List<String> ids;
        private final Item[] items = new Item[HearthData.TIERS];
        @SuppressWarnings("unchecked")
        private final TagKey<Item>[] tags = new TagKey[HearthData.TIERS];

        public Offerings(List<String> ids) {
            List<String> kept = new ArrayList<>(HearthData.TIERS);
            for (int i = 0; i < HearthData.TIERS; i++) {
                String id = i < ids.size() ? ids.get(i) : null;
                kept.add(id == null ? "" : id);
                if (id == null || id.isEmpty()) {
                    continue;
                }
                if (id.startsWith("#")) {
                    ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
                    tags[i] = tag == null ? null : TagKey.create(Registries.ITEM, tag);
                } else {
                    ResourceLocation location = ResourceLocation.tryParse(id);
                    items[i] = location == null ? null : BuiltInRegistries.ITEM.getOptional(location).orElse(null);
                }
            }
            this.ids = Collections.unmodifiableList(kept);
        }

        /** The ids as given, one per stage ("" for none). */
        public List<String> ids() {
            return ids;
        }

        /** Whether {@code stack} is the offering of {@code stage}. */
        public boolean is(int stage, ItemStack stack) {
            if (stack.isEmpty() || stage < 1 || stage > HearthData.TIERS) {
                return false;
            }
            TagKey<Item> tag = tags[stage - 1];
            if (tag != null) {
                return stack.is(tag);
            }
            Item item = items[stage - 1];
            return item != null && stack.is(item);
        }

        /** The stage whose offering {@code stack} is, 1..6, or 0 for none (the lowest wins on a tie). */
        public int stageOf(ItemStack stack) {
            if (stack.isEmpty()) {
                return 0;
            }
            for (int stage = 1; stage <= HearthData.TIERS; stage++) {
                if (is(stage, stack)) {
                    return stage;
                }
            }
            return 0;
        }

        /** The offering's item, or null for a tag or an unknown id. */
        @Nullable
        Item item(int stage) {
            return stage < 1 || stage > HearthData.TIERS ? null : items[stage - 1];
        }
    }

    /** The six offerings of {@code data}, parsed. */
    public static Offerings offerings(HearthData data) {
        return parsed(data).offerings;
    }

    /** Whether {@code stack} is the offering of {@code stage} (an item id, or {@code #tag} for an item tag). */
    public static boolean isOffering(HearthData data, int stage, ItemStack stack) {
        return offerings(data).is(stage, stack);
    }

    /** The stage whose offering {@code stack} is, 1..6, or 0 for none (the lowest wins on a tie). */
    public static int offeringStage(HearthData data, ItemStack stack) {
        return offerings(data).stageOf(stack);
    }

    /** The offering's item, or null for a tag or an unknown id. */
    @Nullable
    public static Item offeringItem(HearthData data, int stage) {
        return offerings(data).item(stage);
    }

    /** The offering's name for a chat line: the item's name, or the tag id. */
    public static Component offeringName(HearthData data, int stage) {
        Item item = offeringItem(data, stage);
        if (item != null) {
            return item.getDescription();
        }
        return Component.literal(String.valueOf(stageSpec(data, stage).offering()));
    }

    /** The Hearth's build read from the world, around the campfire at {@code origin}. */
    public static HearthStructure.Probe probe(BlockGetter level, BlockPos origin, HearthData data) {
        return new LevelProbe(level, origin, false);
    }

    /**
     * {@link #probe} that never loads a chunk: a block in a chunk that is not loaded reads as air. For a read
     * that is only a tooltip (the Jade Hearth lane), where loading a neighbour from a look would be a cost
     * the build itself does not ask for.
     */
    public static HearthStructure.Probe probeLoaded(LevelReader level, BlockPos origin) {
        return new LevelProbe(level, origin, true);
    }

    /** {@link HearthStructure.Probe} over a level: the role tags, air for the dot. */
    static final class LevelProbe implements HearthStructure.Probe {

        private final BlockGetter level;
        private final BlockPos origin;
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        private final boolean loadedOnly;

        LevelProbe(BlockGetter level, BlockPos origin, boolean loadedOnly) {
            this.loadedOnly = loadedOnly;
            this.level = level;
            this.origin = origin.immutable();
        }

        @Override
        public boolean is(char role, int dx, int dy, int dz) {
            cursor.setWithOffset(origin, dx, dy, dz);
            BlockState state = loadedOnly && level instanceof LevelReader reader && !reader.hasChunkAt(cursor)
                    ? Blocks.AIR.defaultBlockState() : level.getBlockState(cursor);
            if (role == '.') {
                return state.isAir();
            }
            if (HearthDesigns.isFluid(role)) {
                return isFluidSource(role, state);
            }
            TagKey<Block> tag = roleTag(role);
            return tag != null && state.is(tag);
        }
    }
}
