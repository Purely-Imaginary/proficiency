package dev.amman.proficiency.hearth.tales;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.TaleTrip;
import dev.amman.proficiency.hearth.TalesMath;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * {@code /proficiency hearth tale <page id> <stars 0-5>} (op level 2): what {@code /mcjournal} sends
 * over RCON for each Chronicle page it rated. Pays the page's Tales x {@code perStar} x stars into its
 * Hearth outside the hourly cap, once per page; a second call for the same page is refused.
 * Brigadier merges this {@code proficiency} root with the mod's other one.
 *
 * <p>Output is {@code translatableWithFallback}, so an RCON client on a server whose language has no
 * mod strings still reads English.
 */
public final class ChronicleCommand {

    private static final String P = "proficiency.hearth.tales.command.";

    private ChronicleCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("proficiency").then(Commands.literal("hearth").then(Commands.literal("tale")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("page", LongArgumentType.longArg(1))
                        .then(Commands.argument("stars", IntegerArgumentType.integer(0, TalesMath.MAX_STARS))
                                .executes(ChronicleCommand::tale)))));
    }

    private static int tale(CommandContext<CommandSourceStack> context) {
        long id = LongArgumentType.getLong(context, "page");
        int stars = IntegerArgumentType.getInteger(context, "stars");
        CommandSourceStack source = context.getSource();
        HearthChronicle.Rated rated = HearthChronicle.rate(source.getServer(), id, stars);
        switch (rated.status()) {
            case UNKNOWN -> {
                source.sendFailure(Component.translatableWithFallback(P + "unknown",
                        "There is no Chronicle page %s", id));
                return 0;
            }
            case ALREADY -> {
                source.sendFailure(Component.translatableWithFallback(P + "already",
                        "Chronicle page %s has already been rated", id));
                return 0;
            }
            case DEFERRED -> {
                // Only a batch with a chunk budget defers; the command has none. Kept so the switch is whole.
                source.sendFailure(Component.translatableWithFallback(P + "deferred",
                        "Chronicle page %s: its Hearth is not loaded, try again when it is", id));
                return 0;
            }
            default -> {
            }
        }
        if (!rated.hearthFound() && rated.page().tales() > 0.0) {
            source.sendSuccess(() -> Component.translatableWithFallback(P + "nohearth",
                    "Chronicle page %s rated %s/5 (its Hearth is gone, nothing was paid)", id, stars), true);
            return 1;
        }
        HearthPool pool = Hearths.data().talesPool();
        source.sendSuccess(() -> Component.translatableWithFallback(P + "rated",
                "Chronicle page %s rated %s/5: %s +%s at the Hearth", id, stars,
                Component.translatable(pool.translationKey()), TaleTrip.charge(rated.paid())), true);
        return 1;
    }
}
