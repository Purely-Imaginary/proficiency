package dev.amman.proficiency.compat;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.AgriHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

/**
 * Optional, soft support for AgriCraft 4 (Forge 1.20.1). There is no compile or runtime dependency:
 * the mod is only looked at when {@code ModList} says "agricraft" is loaded, and everything is read
 * through reflection against AgriCraft's API interfaces ({@code AgriCrop}, {@code AgriGrowthStage}).
 * The Methods are cached. If any lookup or call fails (a different AgriCraft version), this logs
 * once and turns itself off for the rest of the session: never a crash, and never a wrong payment,
 * because every question then answers "no crop".
 *
 * <p>The block entity is the truth, not the block state: the state of {@code agricraft:crop} holds
 * only the stick variant, a flag and a light level, never the plant or its growth.
 */
public final class AgriCraftCompat {

    private static final String MOD_ID = "agricraft";
    private static final String API = "com.agricraft.agricraft.api.crop.";

    private static boolean tried;
    private static boolean on;
    private static boolean warned;
    private static Block cropBlock;
    private static Class<?> agriCropClass;
    private static Class<?> seedItemClass;
    private static Method hasPlant;
    private static Method getPlantId;
    private static Method getGrowthStage;
    private static Method stageIndex;
    private static Method stageIsMature;

    private AgriCraftCompat() {
    }

    /** Whether AgriCraft is present and its API matched what this class expects. */
    public static boolean active() {
        if (!tried) {
            init();
        }
        return on;
    }

    private static synchronized void init() {
        if (tried) {
            return;
        }
        if (ModList.get() == null) {
            return;
        }
        tried = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        try {
            ClassLoader loader = AgriCraftCompat.class.getClassLoader();
            agriCropClass = Class.forName(API + "AgriCrop", false, loader);
            Class<?> stageClass = Class.forName(API + "AgriGrowthStage", false, loader);
            seedItemClass = Class.forName("com.agricraft.agricraft.common.item.AgriSeedItem", false, loader);
            hasPlant = agriCropClass.getMethod("hasPlant");
            getPlantId = agriCropClass.getMethod("getPlantId");
            getGrowthStage = agriCropClass.getMethod("getGrowthStage");
            stageIndex = stageClass.getMethod("index");
            stageIsMature = stageClass.getMethod("isMature");
            if (hasPlant.getReturnType() != boolean.class || getPlantId.getReturnType() != String.class
                    || stageIndex.getReturnType() != int.class || stageIsMature.getReturnType() != boolean.class
                    || !stageClass.isAssignableFrom(getGrowthStage.getReturnType())) {
                throw new NoSuchMethodException("unexpected AgriCraft API return types");
            }
            cropBlock = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(MOD_ID, "crop"));
            if (cropBlock == null || cropBlock == Blocks.AIR) {
                throw new IllegalStateException("block agricraft:crop is not registered");
            }
            on = true;
            Proficiency.LOG.info("AgriCraft found: crop sticks pay Farming XP (harvest, break, planting)");
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void disable(Throwable t) {
        if (!warned) {
            warned = true;
            Proficiency.LOG.warn("AgriCraft support is off: its API did not match ({}). Crop sticks pay "
                    + "no Farming XP.", t.toString());
        }
        on = false;
    }

    /** The block of an AgriCraft crop (plant or bare sticks). */
    public static boolean isCropBlock(BlockState state) {
        return active() && state.getBlock() == cropBlock;
    }

    /** An AgriCraft seed in hand. */
    public static boolean isSeed(ItemStack stack) {
        return active() && !stack.isEmpty() && seedItemClass.isInstance(stack.getItem());
    }

    /**
     * What the crop at this position says now, or null when it is not an AgriCraft crop. A crop
     * with only sticks has {@code hasPlant == false}.
     */
    public static AgriHarvest.Snapshot snapshot(Level level, BlockPos pos) {
        if (!active() || !isCropBlock(level.getBlockState(pos))) {
            return null;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        if (entity == null || !agriCropClass.isInstance(entity)) {
            return null;
        }
        try {
            if (!(Boolean) hasPlant.invoke(entity)) {
                return new AgriHarvest.Snapshot(false, "", 0, false);
            }
            String id = (String) getPlantId.invoke(entity);
            Object stage = getGrowthStage.invoke(entity);
            if (stage == null) {
                return new AgriHarvest.Snapshot(true, id, 0, false);
            }
            return new AgriHarvest.Snapshot(true, id, (Integer) stageIndex.invoke(stage),
                    (Boolean) stageIsMature.invoke(stage));
        } catch (java.lang.reflect.InvocationTargetException | NullPointerException e) {
            // One odd block entity: treat it as "no crop" without turning support off.
            return null;
        } catch (Throwable t) {
            // The API itself does not match (wrong types, access): that is permanent.
            disable(t);
            return null;
        }
    }

    /** A mature plant stands here: breaking it is a harvest. */
    public static boolean maturePlant(Level level, BlockPos pos) {
        return AgriHarvest.breakPays(snapshot(level, pos));
    }
}
