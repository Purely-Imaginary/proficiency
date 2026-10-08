package dev.amman.proficiency.fabric;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.mixin.AnvilMenuAccessor;
import dev.amman.proficiency.platform.Platform;
import dev.amman.proficiency.skill.PlayerSkills;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/** {@link Platform} on Fabric: Fabric API attachments, networking and plain registry calls. */
@SuppressWarnings("UnstableApiUsage")
public final class FabricPlatform implements Platform {

    /**
     * Skills live on the player as a Fabric data attachment, saved into the player's own NBT like the
     * NeoForge attachment. Deliberately NOT copyOnDeath: the Clone handler carries them across by
     * hand, because that is where the death penalty is charged.
     */
    public static final AttachmentType<PlayerSkills> SKILLS = AttachmentRegistry.<PlayerSkills>builder()
            .initializer(PlayerSkills::new)
            .persistent(PlayerSkills.CODEC)
            .buildAndRegister(Proficiency.id("skills"));

    /**
     * NeoForge's {@code Entity.getPersistentData()}: a free-form tag saved with the entity, as a
     * lazily created attachment.
     */
    private static final AttachmentType<CompoundTag> PERSISTENT_DATA = AttachmentRegistry.<CompoundTag>builder()
            .initializer(CompoundTag::new)
            .persistent(CompoundTag.CODEC)
            .copyOnDeath()
            .buildAndRegister(Proficiency.id("persistent_data"));

    /** Forces class init, so both attachment types are registered before any player loads. */
    public static void init() {
    }

    @Override
    public String loaderName() {
        return "Fabric";
    }

    @Override
    public PlayerSkills skills(Player player) {
        return player.getAttachedOrCreate(SKILLS);
    }

    @Override
    public CompoundTag persistentData(Entity entity) {
        return entity.getAttachedOrCreate(PERSISTENT_DATA);
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public Optional<String> modName(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getName());
    }

    @Override
    @Nullable
    public MinecraftServer currentServer() {
        return FabricServerLifecycle.currentServer();
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }

    @Override
    @Nullable
    public FoodProperties food(ItemStack stack, @Nullable LivingEntity entity) {
        return stack.get(DataComponents.FOOD);
    }

    @Override
    public void setAnvilCost(AnvilMenu anvil, int cost) {
        ((AnvilMenuAccessor) anvil).proficiency$cost().set(cost);
    }

    /** Registered eagerly, during class init of the caller. */
    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> factory) {
        T item = Registry.register(BuiltInRegistries.ITEM, Proficiency.id(name), factory.get());
        return () -> item;
    }

    @Override
    public Supplier<CreativeModeTab> registerTab(String name, Supplier<CreativeModeTab> factory) {
        CreativeModeTab tab = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Proficiency.id(name),
                factory.get());
        return () -> tab;
    }

    @Override
    public CreativeModeTab.Builder tabBuilder() {
        return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
    }
}
