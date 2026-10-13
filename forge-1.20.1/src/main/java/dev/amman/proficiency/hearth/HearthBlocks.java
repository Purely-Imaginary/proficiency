package dev.amman.proficiency.hearth;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.sieges.SiegeHornItem;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyBlock;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Hearth's registry entries on Forge 1.20.1 (common's {@code HearthBlocks}, through Forge's
 * deferred registers instead of {@code Platform.registerBlock}). The ids and blockstate properties are
 * the art contract (docs/HEARTH-CONTRACT.md); the shared assets draw exactly these.
 *
 * <ul>
 * <li>{@code proficiency:hearth}: block + block entity, no item (it is built in the world).</li>
 * <li>{@code proficiency:siege_trophy}: block (tier 3..6, horizontal facing) with its item.</li>
 * <li>{@code proficiency:siege_horn}, {@code siege_horn_2} .. {@code siege_horn_5}: the five horns.</li>
 * </ul>
 *
 * Every field is a {@link RegistryObject}, a {@code Supplier}: {@code HearthBlocks.HEARTH.get()} reads
 * the same as on 1.21.1, valid once the registries fired.
 */
public final class HearthBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Proficiency.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Proficiency.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Proficiency.MOD_ID);

    public static final RegistryObject<HearthBlock> HEARTH = BLOCKS.register("hearth",
            () -> new HearthBlock(HearthBlock.defaultProperties()));

    @SuppressWarnings("DataFlowIssue") // the type's data fixer is null on purpose, as for every mod block entity
    public static final RegistryObject<BlockEntityType<HearthBlockEntity>> HEARTH_ENTITY =
            BLOCK_ENTITIES.register("hearth",
                    () -> BlockEntityType.Builder.of(HearthBlockEntity::new, HEARTH.get()).build(null));

    public static final RegistryObject<SiegeTrophyBlock> SIEGE_TROPHY = BLOCKS.register("siege_trophy",
            () -> new SiegeTrophyBlock(SiegeTrophyBlock.defaultProperties()));

    public static final RegistryObject<BlockItem> SIEGE_TROPHY_ITEM = ITEMS.register("siege_trophy",
            () -> new SiegeTrophyItem(SIEGE_TROPHY.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> SIEGE_HORN = ITEMS.register("siege_horn",
            () -> new SiegeHornItem(1, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SIEGE_HORN_2 = ITEMS.register("siege_horn_2",
            () -> new SiegeHornItem(2, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SIEGE_HORN_3 = ITEMS.register("siege_horn_3",
            () -> new SiegeHornItem(3, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SIEGE_HORN_4 = ITEMS.register("siege_horn_4",
            () -> new SiegeHornItem(4, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SIEGE_HORN_5 = ITEMS.register("siege_horn_5",
            () -> new SiegeHornItem(5, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /** The horn of a siege level, 1..5 (clamped). */
    public static Item hornOf(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> SIEGE_HORN.get();
            case 2 -> SIEGE_HORN_2.get();
            case 3 -> SIEGE_HORN_3.get();
            case 4 -> SIEGE_HORN_4.get();
            default -> SIEGE_HORN_5.get();
        };
    }

    private HearthBlocks() {
    }

    /** Hands the three registers to the mod bus. Called once from the mod constructor. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        ITEMS.register(modBus);
    }
}
