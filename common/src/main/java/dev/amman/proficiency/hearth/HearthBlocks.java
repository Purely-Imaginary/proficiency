package dev.amman.proficiency.hearth;

import dev.amman.proficiency.hearth.sieges.SiegeHornItem;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyBlock;
import dev.amman.proficiency.platform.Platform;
import dev.amman.proficiency.platform.Services;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * The Hearth's registry entries. The ids and blockstate properties are the art contract
 * (docs/HEARTH-CONTRACT.md): the art lane draws blockstates, models and textures for exactly these.
 *
 * <ul>
 * <li>{@code proficiency:hearth}: block + block entity, no item (it is built in the world).</li>
 * <li>{@code proficiency:siege_trophy}: block (tier 3..6, horizontal facing) with its item.</li>
 * <li>{@code proficiency:siege_horn}, {@code siege_horn_2} .. {@code siege_horn_5}: the five horns.</li>
 * </ul>
 */
public final class HearthBlocks {

    private static final Platform PLATFORM = Services.platform();

    public static final Supplier<HearthBlock> HEARTH = PLATFORM.registerBlock("hearth",
            () -> new HearthBlock(HearthBlock.defaultProperties()));

    public static final Supplier<BlockEntityType<HearthBlockEntity>> HEARTH_ENTITY =
            PLATFORM.registerBlockEntity("hearth", HearthBlockEntity::new, HEARTH);

    public static final Supplier<SiegeTrophyBlock> SIEGE_TROPHY = PLATFORM.registerBlock("siege_trophy",
            () -> new SiegeTrophyBlock(SiegeTrophyBlock.defaultProperties()));

    public static final Supplier<BlockItem> SIEGE_TROPHY_ITEM = PLATFORM.registerItem("siege_trophy",
            () -> new dev.amman.proficiency.hearth.sieges.SiegeTrophyItem(SIEGE_TROPHY.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final Supplier<Item> SIEGE_HORN = PLATFORM.registerItem("siege_horn",
            () -> new SiegeHornItem(1, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Supplier<Item> SIEGE_HORN_2 = PLATFORM.registerItem("siege_horn_2",
            () -> new SiegeHornItem(2, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final Supplier<Item> SIEGE_HORN_3 = PLATFORM.registerItem("siege_horn_3",
            () -> new SiegeHornItem(3, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Supplier<Item> SIEGE_HORN_4 = PLATFORM.registerItem("siege_horn_4",
            () -> new SiegeHornItem(4, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final Supplier<Item> SIEGE_HORN_5 = PLATFORM.registerItem("siege_horn_5",
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

    /** Forces class init, which hands every entry to the loader's registries. */
    public static void init() {
    }
}
