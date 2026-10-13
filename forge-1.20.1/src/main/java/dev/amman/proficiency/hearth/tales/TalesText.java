package dev.amman.proficiency.hearth.tales;

import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.TaleTrip;
import dev.amman.proficiency.net.DiscoveryNames;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Every line the tales lane says, built in one place (keys {@code proficiency.hearth.tales.*}). The
 * server sends translatable components, so each player reads their own language.
 */
final class TalesText {

    private static final String P = "proficiency.hearth.tales.";

    private TalesText() {
    }

    static Component poolName(HearthPool pool) {
        return Component.translatable(pool.translationKey());
    }

    /**
     * A place as a name: its own translation when the game has one, else a name made from the id
     * ({@code structure.minecraft.stronghold} reads "Stronghold" with no translation at all).
     */
    static Component place(String languageKey) {
        int dot = languageKey.indexOf('.');
        String rest = dot < 0 ? languageKey : languageKey.substring(dot + 1);
        int namespaceEnd = rest.indexOf('.');
        String id = namespaceEnd < 0 ? rest : rest.substring(0, namespaceEnd) + ":" + rest.substring(namespaceEnd + 1).replace('.', '/');
        return DiscoveryNames.component(languageKey, id);
    }

    static Component places(List<String> keys) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                out.append(Component.literal(", "));
            }
            out.append(place(keys.get(i)));
        }
        return out;
    }

    /** The homecoming line: "You told of 3 200 blocks walked and of what you found: Stronghold. Homecoming +142". */
    static Component paid(long blocks, List<String> places, double amount, Component pool) {
        String key;
        if (blocks > 0 && !places.isEmpty()) {
            key = "paid.both";
        } else if (blocks > 0) {
            key = "paid.walked";
        } else if (!places.isEmpty()) {
            key = "paid.places";
        } else {
            key = "paid.plain";
        }
        return Component.translatable(P + key, TaleTrip.group(blocks), places(places), TaleTrip.charge(amount), pool)
                .withStyle(ChatFormatting.GOLD);
    }

    static Component everyPool() {
        return Component.translatable(P + "allpools");
    }

    static Component capped() {
        return Component.translatable(P + "capped").withStyle(ChatFormatting.GRAY);
    }

    static Component kept(double kept) {
        return Component.translatable(P + "kept", TaleTrip.charge(kept)).withStyle(ChatFormatting.GRAY);
    }

    static Component lost(double lost) {
        return Component.translatable(P + "lost", TaleTrip.charge(lost)).withStyle(ChatFormatting.RED);
    }

    static Component feastStarted(int eaters, double charge, Component pool) {
        return charge >= 0.5
                ? Component.translatable(P + "feast.started.charge", eaters, TaleTrip.charge(charge), pool)
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable(P + "feast.started", eaters).withStyle(ChatFormatting.GOLD);
    }

    static Component feastJoined(double charge, Component pool) {
        return charge >= 0.5
                ? Component.translatable(P + "feast.joined.charge", TaleTrip.charge(charge), pool)
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable(P + "feast.joined").withStyle(ChatFormatting.GOLD);
    }

    static Component bellAction(String call) {
        return Component.translatable(P + "bell." + call).withStyle(ChatFormatting.GOLD);
    }

    static Component bellCooldown() {
        return Component.translatable(P + "bell.cooldown").withStyle(ChatFormatting.GRAY);
    }

    static Component bellChat(String call, Component ringer, boolean farAway) {
        String key = P + "bell." + call + ".chat" + (farAway && call.equals("come_home") ? ".far" : "");
        return Component.translatable(key, ringer).withStyle(ChatFormatting.GOLD);
    }

    static Component compassHum() {
        return Component.translatable(P + "bell.compass").withStyle(ChatFormatting.AQUA);
    }

    static Component chronicleFiled(double charge, Component pool) {
        return charge >= 0.5
                ? Component.translatable(P + "chronicle.filed", TaleTrip.charge(charge), pool)
                        .withStyle(ChatFormatting.GOLD)
                : Component.translatable(P + "chronicle.filed.plain").withStyle(ChatFormatting.GOLD);
    }

    static Component retold(String title, int stars) {
        return (title == null || title.isEmpty()
                ? Component.translatable(P + "retold.untitled", stars)
                : Component.translatable(P + "retold", title, stars)).withStyle(ChatFormatting.GOLD);
    }
}
