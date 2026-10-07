package dev.amman.proficiency.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Points at one friend, wherever they are. The owner's idea (2026-09-29): craft it, right-click a
 * friend to register them, and from then on the needle shows where they are; right-click the air
 * and your camera turns to face them.
 *
 * <p>TWO SOURCES FOR THE NEEDLE, on purpose. The client already knows the exact position of every
 * player inside its entity-tracking range, so {@code ProficiencyClientSetup} reads that first and
 * the needle follows a friend smoothly. Beyond that range the client knows nothing, so the server
 * keeps a coarse copy in the stack's lodestone tracker. It is rewritten only when the friend has
 * moved {@link #COARSE_STEP} blocks or changed dimension, because every component change on a held
 * stack replays the equip animation — a per-second update would make the item bob constantly.
 *
 * <p>Offline, or in another dimension: the tracker is cleared (or points into the other
 * dimension), and vanilla's needle maths spins, which is exactly what a vanilla compass does in the
 * Nether. Only players can be registered; the friend is stored by UUID with the name beside it for
 * the tooltip, so a renamed friend is still found.
 */
public class FriendCompassItem extends Item {

    static final String FRIEND_KEY = "friend";
    static final String FRIEND_NAME_KEY = "friend_name";

    /** How far the friend must move before the server rewrites the coarse position. */
    private static final double COARSE_STEP = 24.0;
    private static final int UPDATE_INTERVAL = 20;

    public FriendCompassItem(Properties properties) {
        super(properties);
    }

    /** The registered friend, or null. Read on both sides: the client needle uses it too. */
    public static UUID friend(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.hasUUID(FRIEND_KEY) ? tag.getUUID(FRIEND_KEY) : null;
    }

    private static String friendName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(FRIEND_NAME_KEY);
    }

    /** Right-click a player: they become the friend this compass follows. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (!(target instanceof Player friend)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        // The held stack, not the argument: in creative the argument can be a copy.
        ItemStack held = player.getItemInHand(hand);
        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(FRIEND_KEY, friend.getUUID());
        tag.putString(FRIEND_NAME_KEY, friend.getGameProfile().getName());
        held.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        pointAt(held, friend);
        player.sendSystemMessage(Component.translatable("proficiency.friend_compass.registered",
                friend.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
        return InteractionResult.SUCCESS;
    }

    /** Right-click the air: turn to face the friend, and say how far away they are. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(held);
        }
        UUID id = friend(held);
        if (id == null) {
            serverPlayer.sendSystemMessage(Component.translatable("proficiency.friend_compass.unset")
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(held);
        }
        ServerPlayer friend = serverPlayer.server.getPlayerList().getPlayer(id);
        if (friend == null) {
            serverPlayer.sendSystemMessage(Component.translatable("proficiency.friend_compass.offline",
                    friendName(held)).withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(held);
        }
        if (friend.level() != serverPlayer.level()) {
            serverPlayer.sendSystemMessage(Component.translatable("proficiency.friend_compass.elsewhere",
                    friend.getDisplayName(),
                    Component.literal(friend.level().dimension().location().getPath()))
                    .withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.success(held);
        }
        Vec3 from = serverPlayer.getEyePosition();
        Vec3 to = friend.getEyePosition();
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        float pitch = (float) -(Mth.atan2(dy, flat) * Mth.RAD_TO_DEG);
        // Position relative (+0), rotation absolute: only the camera moves. A teleport packet is the
        // one way the server can turn a player's view; setting the rotation server-side alone does
        // nothing on the client.
        serverPlayer.connection.teleport(0, 0, 0, yaw, pitch,
                EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z));
        serverPlayer.sendSystemMessage(Component.translatable("proficiency.friend_compass.facing",
                friend.getDisplayName(), (int) Math.sqrt(dx * dx + dy * dy + dz * dz))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        // Watchful Compass (Guardian): the friend's health too.
        if (dev.amman.proficiency.perk.TalentService.rank(serverPlayer,
                dev.amman.proficiency.skill.Skill.GUARDIAN, "compass_watch") > 0) {
            serverPlayer.sendSystemMessage(dev.amman.proficiency.event.GuardianEvents.health(
                    "proficiency.guardian.friend_health", friend).withStyle(ChatFormatting.RED));
        }
        serverPlayer.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.2f);
        serverPlayer.getCooldowns().addCooldown(this, 10);
        return InteractionResultHolder.success(held);
    }

    /** Keeps the coarse position fresh for when the friend is beyond the client's tracking range. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer)
                || (level.getGameTime() + slot) % UPDATE_INTERVAL != 0) {
            return;
        }
        UUID id = friend(stack);
        if (id == null) {
            return;
        }
        ServerPlayer friend = server.getServer().getPlayerList().getPlayer(id);
        if (friend == null) {
            if (stack.has(DataComponents.LODESTONE_TRACKER)) {
                stack.remove(DataComponents.LODESTONE_TRACKER);
            }
            return;
        }
        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        GlobalPos known = tracker == null ? null : tracker.target().orElse(null);
        if (known == null || !known.dimension().equals(friend.level().dimension())
                || known.pos().distSqr(friend.blockPosition()) > COARSE_STEP * COARSE_STEP) {
            pointAt(stack, friend);
        }
    }

    private static void pointAt(ItemStack stack, Player friend) {
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                Optional.of(GlobalPos.of(friend.level().dimension(), friend.blockPosition())), false));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String name = friendName(stack);
        lines.add(name.isEmpty()
                ? Component.translatable("proficiency.friend_compass.tooltip_unset").withStyle(ChatFormatting.GRAY)
                : Component.translatable("proficiency.friend_compass.tooltip", name).withStyle(ChatFormatting.LIGHT_PURPLE));
        lines.add(Component.translatable("proficiency.friend_compass.tooltip_use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
