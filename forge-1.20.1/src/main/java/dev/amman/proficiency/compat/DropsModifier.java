package dev.amman.proficiency.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amman.proficiency.Proficiency;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Posts {@link BlockDropsEvent} for every block loot roll that has a breaker, the way NeoForge
 * posts it from {@code Block.dropResources}. Forge 1.20.1 has no drops event for blocks; a global
 * loot modifier is its supported place to change them. Registered in
 * {@code data/forge/loot_modifiers/global_loot_modifiers.json}.
 *
 * <p>The stacks are wrapped in item entities at the block (no entity is added to the world), the
 * event's handlers see and edit those exactly as on NeoForge, and whatever is left goes back to
 * vanilla as stacks, which then pops them as usual.
 */
public class DropsModifier extends LootModifier {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, Proficiency.MOD_ID);

    public static final RegistryObject<Codec<DropsModifier>> BLOCK_DROPS = SERIALIZERS.register(
            "block_drops", () -> RecordCodecBuilder.create(instance -> codecStart(instance)
                    .apply(instance, DropsModifier::new)));

    public DropsModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected @NotNull ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        Entity breaker = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (state == null || origin == null || breaker == null) {
            return loot;
        }
        ServerLevel level = context.getLevel();
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        BlockPos pos = BlockPos.containing(origin);
        List<ItemEntity> drops = new ArrayList<>(loot.size());
        for (ItemStack stack : loot) {
            drops.add(new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
        }
        MinecraftForge.EVENT_BUS.post(new BlockDropsEvent(level, pos, state, drops, breaker,
                tool == null ? ItemStack.EMPTY : tool));
        ObjectArrayList<ItemStack> result = new ObjectArrayList<>(drops.size());
        for (ItemEntity drop : drops) {
            if (!drop.getItem().isEmpty()) {
                result.add(drop.getItem());
            }
        }
        return result;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return BLOCK_DROPS.get();
    }
}
