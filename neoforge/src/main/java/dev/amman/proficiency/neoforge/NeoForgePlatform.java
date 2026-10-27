package dev.amman.proficiency.neoforge;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.platform.Platform;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/** {@link Platform} on NeoForge: deferred registers, the attachment API and NeoForge networking. */
public final class NeoForgePlatform implements Platform {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Proficiency.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Proficiency.MOD_ID);

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Proficiency.MOD_ID);

    /** The player's skills, saved with the player as {@code proficiency:skills}. */
    public static final Supplier<AttachmentType<PlayerSkills>> SKILLS = ATTACHMENTS.register(
            "skills",
            () -> AttachmentType.builder(PlayerSkills::new)
                    .serialize(PlayerSkills.CODEC)
                    .build());

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Proficiency.MOD_ID);

    public static final DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Proficiency.MOD_ID);

    /**
     * The player's Hearth state, saved with the player as {@code proficiency:hearth}. Not copied on
     * death by the loader: the Hearth's Clone handler copies it and clears what a death costs.
     */
    public static final Supplier<AttachmentType<dev.amman.proficiency.hearth.PlayerHearth>> HEARTH =
            ATTACHMENTS.register("hearth",
                    () -> AttachmentType.builder(dev.amman.proficiency.hearth.PlayerHearth::new)
                            .serialize(dev.amman.proficiency.hearth.PlayerHearth.CODEC)
                            .build());

    @Override
    public String loaderName() {
        return "NeoForge";
    }

    @Override
    public PlayerSkills skills(Player player) {
        return player.getData(SKILLS.get());
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return entity.getPersistentData();
    }

    @Override
    public boolean canDig(ItemStack stack, String kind) {
        if (stack.isEmpty()) {
            return false;
        }
        net.neoforged.neoforge.common.ItemAbility ability = switch (kind) {
            case "pickaxe" -> net.neoforged.neoforge.common.ItemAbilities.PICKAXE_DIG;
            case "shovel" -> net.neoforged.neoforge.common.ItemAbilities.SHOVEL_DIG;
            case "axe" -> net.neoforged.neoforge.common.ItemAbilities.AXE_DIG;
            case "hoe" -> net.neoforged.neoforge.common.ItemAbilities.HOE_DIG;
            default -> null;
        };
        return Platform.super.canDig(stack, kind) || (ability != null && stack.canPerformAction(ability));
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public Optional<String> modName(String modId) {
        return ModList.get().getModContainerById(modId)
                .map(container -> container.getModInfo().getDisplayName());
    }

    @Override
    @Nullable
    public MinecraftServer currentServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    @Override
    public void refreshTabListName(ServerPlayer player) {
        player.refreshTabListName();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return NetworkRegistry.hasChannel(player.connection, type.id());
    }

    @Override
    @Nullable
    public FoodProperties food(ItemStack stack, @Nullable LivingEntity entity) {
        return stack.getFoodProperties(entity);
    }

    @Override
    public void setAnvilCost(AnvilMenu anvil, int cost) {
        anvil.setMaximumCost(cost);
    }

    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> factory) {
        return ITEMS.register(name, factory);
    }

    @Override
    public Supplier<CreativeModeTab> registerTab(String name, Supplier<CreativeModeTab> factory) {
        return TABS.register(name, factory);
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public dev.amman.proficiency.hearth.PlayerHearth hearth(Player player) {
        return player.getData(HEARTH.get());
    }

    @Override
    public <T extends net.minecraft.world.level.block.Block> Supplier<T> registerBlock(String name,
            Supplier<T> factory) {
        return BLOCKS.register(name, factory);
    }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
            Supplier<net.minecraft.world.level.block.entity.BlockEntityType<T>> registerBlockEntity(String name,
                    BlockEntityFactory<T> factory, Supplier<? extends net.minecraft.world.level.block.Block> block) {
        return BLOCK_ENTITIES.register(name, () -> net.minecraft.world.level.block.entity.BlockEntityType.Builder
                .<T>of(factory::create, block.get()).build(null));
    }
}
