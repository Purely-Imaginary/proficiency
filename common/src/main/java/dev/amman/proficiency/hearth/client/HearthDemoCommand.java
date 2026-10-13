package dev.amman.proficiency.hearth.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

/**
 * {@code /proficiency hearth demo <players> <scene> [pos]}: operator only (level 2). Sends the fake
 * payloads of one scene ({@link HearthDemoData#SCENES}) to the players over the real channels, so the
 * client lane's HUD, tracker, panel and siege panel can be checked before the server lanes send
 * anything, and from a server console. It changes nothing on the server. Server side; registered from
 * {@code Hearth.init}.
 */
public final class HearthDemoCommand {

    private static boolean started;

    private HearthDemoCommand() {
    }

    public static synchronized void init() {
        if (started) {
            return;
        }
        started = true;
        NeoForge.EVENT_BUS.register(HearthDemoCommand.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("proficiency").then(Commands.literal("hearth")
                .then(Commands.literal("demo").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("scene", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                HearthDemoData.SCENES, builder))
                                        .executes(context -> run(context, null))
                                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                .executes(context -> run(context,
                                                        BlockPosArgument.getBlockPos(context, "pos")))))))));
    }

    private static int run(CommandContext<CommandSourceStack> context, BlockPos at) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        String scene = StringArgumentType.getString(context, "scene");
        if (!HearthDemoData.SCENES.contains(scene)) {
            context.getSource().sendFailure(Component.translatable("proficiency.hearth.client.demo.unknown", scene,
                    String.join(", ", HearthDemoData.SCENES)));
            return 0;
        }
        int sent = 0;
        for (ServerPlayer player : players) {
            BlockPos pos = at != null ? at : player.blockPosition();
            String dimension = player.level().dimension().location().toString();
            if ("tierup".equals(scene)) {
                // The charge lane's tier-up chat line comes first, as ChargeTiers sends it (then the state, then
                // the gain with the skill sync), so the client's chat filter is tested on the real path.
                player.sendSystemMessage(dev.amman.proficiency.hearth.charge.ChargeText.tierUp(
                        HearthDemoData.TIER_UP_POOL, HearthDemoData.TIER_UP_TIER, null, java.util.List.of()));
            }
            for (CustomPacketPayload payload : HearthDemoData.payloads(scene, pos.asLong(), dimension,
                    player.level().getGameTime())) {
                send(player, payload);
            }
            sent++;
        }
        int count = sent;
        context.getSource().sendSuccess(() -> Component.translatable("proficiency.hearth.client.demo.sent", scene, count),
                false);
        return count;
    }

    private static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (payload instanceof HearthStatePayload state) {
            HearthNet.sendState(player, state);
        } else if (payload instanceof HearthHudPayload hud) {
            HearthNet.sendHud(player, hud);
        } else if (payload instanceof HearthLedgerPayload ledger) {
            HearthNet.sendLedger(player, ledger);
        } else if (payload instanceof SiegePanelPayload siege) {
            HearthNet.sendSiegePanel(player, siege);
        } else if (payload instanceof HearthPingPayload ping) {
            HearthNet.sendPing(player, ping);
        } else if (payload instanceof HearthGainPayload gains) {
            for (HearthGainPayload.Gain gain : gains.gains()) {
                var pool = dev.amman.proficiency.hearth.HearthPool.byIndex(gain.pool());
                var skill = gain.skill() >= 0 && gain.skill() < dev.amman.proficiency.skill.Skill.VALUES.length
                        ? dev.amman.proficiency.skill.Skill.VALUES[gain.skill()] : null;
                if (pool != null) {
                    HearthNet.queueGain(player, pool, gain.amount(), skill);
                }
            }
        }
    }
}
