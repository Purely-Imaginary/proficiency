package dev.amman.proficiency.platform;

import dev.amman.proficiency.skill.PlayerSkills;
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

/**
 * Everything the shared 1.21.1 code needs from a mod loader that vanilla does not have. One
 * implementation per loader, found with {@link java.util.ServiceLoader} through
 * {@code META-INF/services}: {@code NeoForgePlatform} and {@code FabricPlatform}. Events are not
 * here: each loader posts its events on the shared bus ({@code platform.bus.NeoForge.EVENT_BUS}).
 */
public interface Platform {

    /** Which loader this is, for logs. */
    String loaderName();

    /** The player's skills attachment, created empty on first use. */
    PlayerSkills skills(Player player);

    /** NeoForge's {@code Entity.getPersistentData()}: a free-form tag saved with the entity. */
    CompoundTag persistentData(Entity entity);

    boolean isModLoaded(String modId);

    /** The display name of an installed mod, by id. */
    Optional<String> modName(String modId);

    /** The running server, or null when there is none. */
    @Nullable
    MinecraftServer currentServer();

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Whether this player's client negotiated the channel; sending one it did not throws. */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

    /** The food a stack gives this entity; NeoForge lets items answer per entity. */
    @Nullable
    FoodProperties food(ItemStack stack, @Nullable LivingEntity entity);

    /** Sets the level cost an anvil shows and charges. */
    void setAnvilCost(AnvilMenu anvil, int cost);

    /** Registers an item now or at the loader's registration time. Call {@code get()} only after. */
    <T extends Item> Supplier<T> registerItem(String name, Supplier<T> factory);

    Supplier<CreativeModeTab> registerTab(String name, Supplier<CreativeModeTab> factory);

    /** A creative tab builder; NeoForge and Fabric start it differently. */
    CreativeModeTab.Builder tabBuilder();
}
