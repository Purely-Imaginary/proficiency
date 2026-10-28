package dev.amman.proficiency.skill;

import dev.amman.proficiency.net.BonusLootPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tells a client which items were a bonus, so its HUD can fly them in. A skill's own luck (the double drop and
 * Motherlode, Second Catch, Double Hook, Forager and the other talent drops, a Mastery gift) calls {@link #note};
 * nothing here changes what drops. The server tick calls {@link #flush} once a tick, so a Timber that adds forty
 * logs is one small packet, merged by item and cut at {@link BonusLootPayload#MAX} entries. Only the extra
 * items are noted, never the ordinary drops.
 */
public final class BonusLoot {

    /** What each player has waiting this tick, in order; server thread only. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** The last packet built for each player, kept so a GameTest can read it (a mock player has no connection). */
    private static final Map<UUID, BonusLootPayload> LAST = new HashMap<>();

    private BonusLoot() {
    }

    private static final class Pending {
        final ServerPlayer player;
        final Map<String, BonusLootPayload.Entry> merged = new LinkedHashMap<>();

        Pending(ServerPlayer player) {
            this.player = player;
        }
    }

    /** {@code stack} (its whole count) appeared as a bonus at ({@code x}, {@code y}, {@code z}) for {@code player}. */
    public static void note(Player player, ItemStack stack, double x, double y, double z) {
        if (!(player instanceof ServerPlayer server) || stack.isEmpty() || SkillTools.isFakePlayer(player)) {
            return;
        }
        Pending pending = PENDING.computeIfAbsent(server.getUUID(), id -> new Pending(server));
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        // One icon per item per half block of where it appeared: a ten-log Timber is one flight of ten.
        String key = item + "@" + Math.round(x * 2) + "," + Math.round(y * 2) + "," + Math.round(z * 2);
        BonusLootPayload.Entry old = pending.merged.get(key);
        if (old != null) {
            pending.merged.put(key, new BonusLootPayload.Entry(item, old.count() + stack.getCount(),
                    old.x(), old.y(), old.z()));
        } else if (pending.merged.size() < 4 * BonusLootPayload.MAX) {
            pending.merged.put(key, new BonusLootPayload.Entry(item, stack.getCount(), (float) x, (float) y, (float) z));
        }
    }

    public static void note(Player player, ItemStack stack, Vec3 at) {
        note(player, stack, at.x, at.y, at.z);
    }

    /** What is waiting for {@code player} this tick (a GameTest reads this before the tick flushes it). */
    public static int pending(Player player) {
        Pending pending = PENDING.get(player.getUUID());
        return pending == null ? 0 : pending.merged.size();
    }

    /** The last packet built for {@code player}, or null. Kept for GameTests. */
    public static BonusLootPayload last(Player player) {
        return LAST.get(player.getUUID());
    }

    /** Forgets the stored packet (GameTests, logout). */
    public static void forget(UUID player) {
        PENDING.remove(player);
        LAST.remove(player);
    }

    /** Sends everything waiting, one packet per player. Called once a server tick. */
    public static void flush() {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Pending> all = new ArrayList<>(PENDING.values());
        PENDING.clear();
        for (Pending pending : all) {
            flush(pending);
        }
    }

    /** Sends what is waiting for one player now. */
    public static void flush(ServerPlayer player) {
        Pending pending = PENDING.remove(player.getUUID());
        if (pending != null) {
            flush(pending);
        }
    }

    private static void flush(Pending pending) {
        if (pending.merged.isEmpty()) {
            return;
        }
        List<BonusLootPayload.Entry> entries = new ArrayList<>(pending.merged.values());
        if (entries.size() > BonusLootPayload.MAX) {
            // The biggest piles first; the rest are still in the bag, only unflown.
            entries.sort((a, b) -> Integer.compare(b.count(), a.count()));
            entries = new ArrayList<>(entries.subList(0, BonusLootPayload.MAX));
        }
        BonusLootPayload payload = new BonusLootPayload(List.copyOf(entries));
        LAST.put(pending.player.getUUID(), payload);
        ProficiencyNetwork.sendBonusLoot(pending.player, payload);
    }
}
