package dev.amman.proficiency.homebuilder;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload;
import dev.amman.proficiency.homebuilder.net.HomebuilderNet;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingDamageEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import dev.amman.proficiency.skill.HomebuilderMath;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.UUID;

/**
 * Homebuilder's handlers on the shared bus (registered from {@link HomebuilderInit}, not
 * {@code Subscribers}): the magnet's 5-tick pull and the Homeward channel on the server tick, the
 * hits that break a channel, the login state, the logout cleanup, the two commands, and the ability
 * wheel's two Homebuilder slots.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class HomebuilderEvents {

    private static int ticks;

    private HomebuilderEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Homeward.tickAll(event.getServer());
        if (++ticks < HomebuilderMath.MAGNET_INTERVAL_TICKS) {
            return;
        }
        ticks = 0;
        if (!ProficiencyConfig.hearthEnabled()) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            HomePassive.pull(player);
        }
    }

    /** A hit that landed (after every handler, shields and the hurt cooldown) breaks a Homeward channel. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (!event.getEntity().level().isClientSide()) {
            Homeward.onDamaged(event);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HomebuilderNet.sendMagnet(player);
            HomebuilderNet.sendHomewardDay(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    static void forget(UUID player) {
        HomebuilderXp.forget(player);
        Hearthglow.forget(player);
        HouseProud.forget(player);
        Homeward.forget(player);
        NightWatch.forget(player);
    }

    // ---- Commands ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // Brigadier merges these into the /skills the mod already registers.
        event.getDispatcher().register(Commands.literal("skills")
                .then(Commands.literal("magnet")
                        .executes(context -> magnet(context, null))
                        .then(Commands.literal("on").executes(context -> magnet(context, Boolean.FALSE)))
                        .then(Commands.literal("off").executes(context -> magnet(context, Boolean.TRUE))))
                .then(Commands.literal("homeward").executes(HomebuilderEvents::homeward)));
    }

    private static int magnet(CommandContext<CommandSourceStack> context, Boolean off) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        toggleMagnet(player, off);
        return 1;
    }

    private static int homeward(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        Component refused = Homeward.start(player);
        if (refused != null) {
            context.getSource().sendFailure(refused);
            return 0;
        }
        return 1;
    }

    /**
     * The magnet switch: {@code off} null flips it. Without Tidy Home it says so and changes nothing.
     * The answer goes on the action bar, with a click, and the client hears the new state.
     */
    public static void toggleMagnet(ServerPlayer player, Boolean off) {
        if (HomePassive.tidyHome(player) <= 0) {
            player.displayClientMessage(Component.translatable("proficiency.homebuilder.magnet.locked")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        PlayerHearth data = HearthAttachments.of(player);
        boolean next = off == null ? !data.magnetOff() : off;
        data.setMagnetOff(next);
        player.displayClientMessage(Component.translatable(next ? "proficiency.homebuilder.magnet.off"
                : "proficiency.homebuilder.magnet.on").withStyle(next ? ChatFormatting.GRAY : ChatFormatting.GOLD), true);
        player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4f, next ? 0.8f : 1.2f);
        HomebuilderNet.sendMagnet(player);
    }

    /** The wheel's Homebuilder slots ({@link HomebuilderActionPayload}), on the server thread. */
    public static void onAction(ServerPlayer player, HomebuilderActionPayload payload) {
        switch (payload.action()) {
            case HomebuilderActionPayload.MAGNET -> toggleMagnet(player, null);
            case HomebuilderActionPayload.HOMEWARD -> {
                Component refused = Homeward.start(player);
                if (refused != null) {
                    player.displayClientMessage(refused.copy().withStyle(ChatFormatting.GRAY), true);
                }
            }
            default -> {
            }
        }
    }
}
