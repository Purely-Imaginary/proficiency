package dev.amman.proficiency.recipe;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Makes the station recipes in {@link WorldRecipes}. Drop the ingredients into a water cauldron,
 * or onto an anvil or an enchanting table, then use the station's tool on it: a Ladle, a Smithing
 * Hammer, a Book. The player who uses the tool is the one who must know the recipe.
 *
 * <p>With no dropped items there, the tool does what it always did, so the anvil and the table
 * still open their screens.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class StationEvents {

    private StationEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        InteractionResult result = use(player, level, event.getPos(), event.getHand(), event.getItemStack());
        if (result != null) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }

    /**
     * Ingredients on a station stay there while you stand next to it. Sneak to pick them back up.
     * Without this, the player at the anvil takes the items back two seconds after throwing them.
     */
    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        if (event.getEntity().isShiftKeyDown()) {
            return;
        }
        ItemEntity item = event.getItem();
        if (onStation(item.level().getBlockState(item.blockPosition()))
                || onStation(item.level().getBlockState(item.blockPosition().below()))) {
            event.setCanceled(true);
        }
    }

    private static boolean onStation(BlockState state) {
        return state.getBlock() instanceof AbstractCauldronBlock
                || state.is(BlockTags.ANVIL)
                || state.is(Blocks.ENCHANTING_TABLE);
    }

    /** What to answer the click with, or null to leave it to vanilla. Loader-neutral. */
    @Nullable
    public static InteractionResult use(ServerPlayer player, ServerLevel level, BlockPos pos,
            InteractionHand hand, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        Station station = stationFor(state, tool);
        if (station == null) {
            return null;
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class,
                new AABB(pos.getX(), pos.getY() + 0.1, pos.getZ(), pos.getX() + 1, pos.getY() + 1.6, pos.getZ() + 1),
                ItemEntity::isAlive);
        if (items.isEmpty()) {
            return null;
        }
        if (station == Station.CAULDRON && !state.is(Blocks.WATER_CAULDRON)) {
            tell(player, Component.translatable("proficiency.station.needs_water"), ChatFormatting.GRAY);
            return InteractionResult.SUCCESS;
        }

        Map<String, Integer> available = new HashMap<>();
        for (ItemEntity item : items) {
            available.merge(id(item.getItem()), item.getItem().getCount(), Integer::sum);
        }
        WorldRecipe recipe = WorldRecipes.matchKnown(station, available, r -> knows(player, r));
        if (recipe == null) {
            WorldRecipe locked = WorldRecipes.match(station, available);
            if (locked == null) {
                tell(player, Component.translatable("proficiency.station.nothing"), ChatFormatting.GRAY);
            } else {
                tell(player, Component.translatable("proficiency.station.locked", resultName(locked),
                        teacher(locked), Component.translatable(locked.skill().translationKey())),
                        ChatFormatting.RED);
            }
            return InteractionResult.SUCCESS;
        }

        ItemStack made = result(level, recipe);
        if (made.isEmpty()) {
            Proficiency.LOG.warn("Station recipe {} has no result {}", recipe.id(), recipe.result());
            return InteractionResult.SUCCESS;
        }
        consume(items, recipe);
        ItemEntity out = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, made);
        // Tossed toward the player, off the station: on it, the pickup hold would keep it there.
        double dx = player.getX() - (pos.getX() + 0.5);
        double dz = player.getZ() - (pos.getZ() + 0.5);
        double length = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        out.setDeltaMovement(dx / length * 0.2, 0.3, dz / length * 0.2);
        out.setDefaultPickUpDelay();
        level.addFreshEntity(out);

        if (station == Station.ENCHANTING_TABLE) {
            if (!player.getAbilities().instabuild) {
                tool.shrink(1);
            }
        } else {
            tool.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(hand));
        }
        if (station == Station.CAULDRON) {
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }
        effects(level, pos, station);
        player.swing(hand, true);
        SkillService.grant(player, recipe.skill(), recipe.xp(), made.getDescriptionId());
        tell(player, Component.translatable("proficiency.station.made", made.getHoverName()), ChatFormatting.GREEN);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static Station stationFor(BlockState state, ItemStack tool) {
        String held = id(tool);
        if (held.equals(Station.CAULDRON.tool()) && state.getBlock() instanceof AbstractCauldronBlock) {
            return Station.CAULDRON;
        }
        if (held.equals(Station.ANVIL.tool()) && state.is(BlockTags.ANVIL)) {
            return Station.ANVIL;
        }
        if (held.equals(Station.ENCHANTING_TABLE.tool()) && state.is(Blocks.ENCHANTING_TABLE)) {
            return Station.ENCHANTING_TABLE;
        }
        return null;
    }

    public static boolean knows(ServerPlayer player, WorldRecipe recipe) {
        return TalentService.hasSpecial(player, recipe.skill(), recipe.unlock());
    }

    /** The node that teaches a recipe, by name. */
    public static Component teacher(WorldRecipe recipe) {
        for (Talent talent : Talents.of(recipe.skill())) {
            if (recipe.unlock().equals(talent.special())) {
                return Component.translatable(talent.nameKey());
            }
        }
        return Component.literal(recipe.unlock());
    }

    public static Component resultName(WorldRecipe recipe) {
        if (recipe.enchantment() != null) {
            ResourceLocation id = new ResourceLocation(recipe.enchantment());
            return Component.translatable("enchantment." + id.getNamespace() + "." + id.getPath());
        }
        return BuiltInRegistries.ITEM.get(new ResourceLocation(recipe.result())).getDescription();
    }

    private static ItemStack result(ServerLevel level, WorldRecipe recipe) {
        if (recipe.enchantment() != null) {
            Enchantment enchantment = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS
                    .getValue(new ResourceLocation(recipe.enchantment()));
            return enchantment == null ? ItemStack.EMPTY
                    : EnchantedBookItem.createForEnchantment(new EnchantmentInstance(enchantment, 1));
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(recipe.result())), recipe.count());
    }

    private static void consume(List<ItemEntity> items, WorldRecipe recipe) {
        for (Map.Entry<String, Integer> need : recipe.ingredients().entrySet()) {
            int left = need.getValue();
            for (ItemEntity item : items) {
                if (left == 0) {
                    break;
                }
                ItemStack stack = item.getItem();
                if (!item.isAlive() || !id(stack).equals(need.getKey())) {
                    continue;
                }
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
                if (stack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(stack);
                }
            }
        }
    }

    private static void effects(ServerLevel level, BlockPos pos, Station station) {
        SoundEvent sound;
        ParticleOptions particle;
        switch (station) {
            case CAULDRON -> {
                sound = SoundEvents.BREWING_STAND_BREW;
                particle = ParticleTypes.BUBBLE_POP;
            }
            case ANVIL -> {
                sound = SoundEvents.ANVIL_USE;
                particle = ParticleTypes.CRIT;
            }
            default -> {
                sound = SoundEvents.ENCHANTMENT_TABLE_USE;
                particle = ParticleTypes.ENCHANT;
            }
        }
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.sendParticles(particle, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                16, 0.3, 0.2, 0.3, 0.05);
    }

    private static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void tell(ServerPlayer player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), true);
    }
}
