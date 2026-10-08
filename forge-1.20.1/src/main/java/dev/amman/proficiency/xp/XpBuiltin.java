package dev.amman.proficiency.xp;

import dev.amman.proficiency.skill.BuildClassifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * The {@code proficiency:builtin/*} tags: questions only code can answer (is this block an
 * instance of a class, does it fill a whole cube). A rule can name them like any tag.
 */
public final class XpBuiltin {

    public static final String PREFIX = "proficiency:builtin/";

    public static final Set<String> NAMES = Set.of(
            "crop_block", "planting_block", "decorative_shape", "split_block", "full_cube",
            "rarity_uncommon", "rarity_rare", "rarity_epic");

    private XpBuiltin() {
    }

    public static boolean isBuiltin(String tag) {
        return tag.startsWith(PREFIX);
    }

    public static boolean known(String tag) {
        return isBuiltin(tag) && NAMES.contains(tag.substring(PREFIX.length()));
    }

    public static boolean block(String tag, BlockState state) {
        return switch (tag.substring(PREFIX.length())) {
            case "crop_block" -> state.getBlock() instanceof CropBlock;
            case "planting_block" -> state.getBlock() instanceof CropBlock
                    || state.getBlock() instanceof StemBlock
                    || state.getBlock() instanceof NetherWartBlock
                    || state.getBlock() instanceof SweetBerryBushBlock
                    || state.getBlock() instanceof CocoaBlock;
            case "decorative_shape" -> BuildClassifier.isDecorativeClass(state.getBlock());
            case "split_block" -> state.getBlock() instanceof StairBlock
                    || state.getBlock() instanceof SlabBlock
                    || state.getBlock() instanceof WallBlock;
            case "full_cube" -> BuildClassifier.isFullCube(state);
            default -> false;
        };
    }

    public static boolean item(String tag, Item item) {
        Rarity rarity = item.getDefaultInstance().getRarity();
        return switch (tag.substring(PREFIX.length())) {
            case "rarity_uncommon" -> rarity == Rarity.UNCOMMON;
            case "rarity_rare" -> rarity == Rarity.RARE;
            case "rarity_epic" -> rarity == Rarity.EPIC;
            default -> false;
        };
    }
}
