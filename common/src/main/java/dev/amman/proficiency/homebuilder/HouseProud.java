package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.homebuilder.net.HomebuilderNet;
import dev.amman.proficiency.homebuilder.net.HomebuilderPayload;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * House Proud, Homebuilder's ability (docs/HOMEBUILDER-SPEC.md): cast at home from the ability
 * wheel, 30 s on a 6 minute cooldown (server config, then the usual duration and cooldown talents).
 * The caster's passive doubles (speed, reach, magnet radius) and every other player at the same
 * Hearth gets the caster's undoubled passive while it runs; Open House reaches friends a few blocks
 * outside the range too. The edge glow and the crosshair ring are the mod's own ability FX, in warm
 * gold for this one.
 *
 * <p>A shared passive is refreshed each second by the caster's own second, valid two seconds, so it
 * ends within 2 s of the ability ending, the caster leaving, or the friend walking away. Two hosts
 * at once do not add up and do not take turns: the stronger share holds, and on a tie the one already
 * running keeps it until it ends. The Heart of the Home synergy hands the same shared passive to
 * everyone at a Feast for 30 s; that one is fixed in time and works only at home.
 */
public final class HouseProud {

    /** A passive handed to a player by someone else's House Proud (or a Feast): {@code byId} hands it, {@code by} is their name. */
    record Shared(double bonus, long until, boolean homeOnly, UUID byId, String by) {
    }

    private static final Map<UUID, Shared> SHARED = new ConcurrentHashMap<>();

    /**
     * A Feast's Heart of the Home host per Hearth key, while the Feast can still be joined: the Tales
     * lane fires {@code FEAST} with the starting eaters and again with each joiner alone, so a joiner
     * finds the host here.
     */
    private record FeastHost(UUID id, String name, double bonus, java.util.Set<UUID> eaters, long until) {
    }

    private static final Map<String, FeastHost> FEAST_HOSTS = new ConcurrentHashMap<>();
    /** Two Hearth seconds, the same as "in range". */
    private static final long REFRESH_VALID_TICKS = 40L;

    private HouseProud() {
    }

    static void init() {
        HearthEvents.FEAST.add(HouseProud::onFeast);
    }

    /** Refuses the cast away from home (called by {@code ActiveService.activate} before it starts). */
    @Nullable
    public static Component refuse(ServerPlayer player) {
        return HearthRange.isHome(player) ? null
                : Component.translatable("proficiency.homebuilder.house_proud.not_home");
    }

    /** The cast landed (the frenzy is on): tell the friends at the Hearth. */
    public static void started(ServerPlayer caster) {
        HearthBlockEntity home = HearthRange.homeEntity(caster);
        if (home == null) {
            return;
        }
        caster.serverLevel().playSound(null, home.getBlockPos(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                1.4f, 0.9f);
        share(caster, home);
    }

    /** Once a second for the caster while it runs: hand the undoubled passive to the friends in reach. Public for the GameTests. */
    public static void tick(ServerPlayer caster) {
        if (!ActiveService.isFrenzied(caster, Skill.HOMEBUILDER)) {
            return;
        }
        HearthBlockEntity home = HearthRange.homeEntity(caster);
        if (home != null && !home.state().smouldering()) {
            share(caster, home);
        }
    }

    private static void share(ServerPlayer caster, HearthBlockEntity home) {
        double bonus = ProficiencyAttachments.of(caster).bonus(Skill.HOMEBUILDER);
        if (!(bonus > 0)) {
            return;
        }
        ServerLevel level = caster.serverLevel();
        long now = level.getGameTime();
        double extra = HomebuilderMath.OPEN_HOUSE_REACH_PER_RANK
                * HomebuilderMath.rank(ProficiencyAttachments.of(caster), HomebuilderMath.OPEN_HOUSE);
        for (ServerPlayer friend : level.players()) {
            if (friend == caster || friend.isSpectator() || !friend.isAlive()
                    || !inReach(home.getBlockPos(), friend, extra)) {
                continue;
            }
            give(friend, new Shared(bonus, now + REFRESH_VALID_TICKS, false, caster.getUUID(),
                    caster.getGameProfile().getName()),
                    now + ProficiencyAttachments.of(caster).frenzyRemaining(Skill.HOMEBUILDER, now));
        }
    }

    /** Inside the Hearth's cylinder, widened by {@code extra} blocks sideways (Open House). */
    static boolean inReach(BlockPos hearth, ServerPlayer player, double extra) {
        HearthGeometry.Range range = HearthRange.range();
        HearthGeometry.Range wide = extra > 0
                ? new HearthGeometry.Range(range.radius() + extra, range.below(), range.above()) : range;
        return wide.contains(player.getX() - (hearth.getX() + 0.5), player.getY() - hearth.getY(),
                player.getZ() - (hearth.getZ() + 0.5));
    }

    /**
     * Hands a shared passive over. The friend hears about it (a line on the action bar, a chime, the
     * client's faint gold edge) only when it starts, not on every refresh; {@code showUntil} is when
     * the client should expect it to end.
     */
    private static void give(ServerPlayer friend, Shared shared, long showUntil) {
        Shared before = SHARED.get(friend.getUUID());
        long now = friend.level().getGameTime();
        boolean running = before != null && before.until() >= now;
        // A share from someone else that is still running and at least as strong is kept: two hosts
        // at once do not add up, and two equal hosts do not overwrite each other every second (each
        // change would be a new chime and action-bar line).
        if (running && before.bonus() >= shared.bonus() && !before.byId().equals(shared.byId())) {
            return;
        }
        SHARED.put(friend.getUUID(), shared);
        if (!running || !before.byId().equals(shared.byId())) {
            friend.displayClientMessage(Component.translatable("proficiency.homebuilder.house_proud.shared",
                    shared.by()).withStyle(ChatFormatting.GOLD), true);
            friend.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.5f);
            HomebuilderNet.send(friend, new HomebuilderPayload(HomebuilderPayload.SHARED, showUntil, 0, shared.by()));
        }
    }

    /**
     * Once a second for every player: a share that ran out is dropped and the client told, so its
     * glow and the modifiers end within 2 s of the host stopping or the friend walking away.
     */
    static void expire(ServerPlayer player) {
        Shared shared = SHARED.get(player.getUUID());
        if (shared != null && shared.until() < player.level().getGameTime() && SHARED.remove(player.getUUID(), shared)) {
            HomebuilderNet.send(player, new HomebuilderPayload(HomebuilderPayload.SHARED, 0L, 0, ""));
        }
    }

    /** The passive someone else hands this player now, 0 for none. */
    static double sharedBonus(ServerPlayer player, boolean home) {
        Shared shared = SHARED.get(player.getUUID());
        if (shared == null) {
            return 0.0;
        }
        if (shared.until() < player.level().getGameTime()) {
            return 0.0;
        }
        return shared.homeOnly() && !home ? 0.0 : shared.bonus();
    }

    /**
     * Heart of the Home: a Feast counts as a House Proud for everyone at it, 30 s, no cooldown used.
     * The host is the eater with the synergy and the largest passive; everyone else at the Feast,
     * joiners included, gets the host's undoubled passive (the host already has their own). A host
     * who joins late hands it to the eaters who were there first.
     */
    static void onFeast(HearthEvents.Feast feast) {
        if (feast.eaters().isEmpty()) {
            return;
        }
        long now = feast.level().getGameTime();
        String key = HearthBlockEntity.key(feast.level(), feast.hearth());
        long join = feastJoinTicks();
        // Expired, or from another world's clock (a single-player save opened after another).
        FEAST_HOSTS.values().removeIf(host -> host.until() < now || host.until() - now > join);
        FeastHost known = FEAST_HOSTS.get(key);
        java.util.Set<UUID> eaters = new java.util.LinkedHashSet<>();
        if (known != null) {
            eaters.addAll(known.eaters());
        }
        ServerPlayer host = null;
        double best = known == null || known.id() == null ? 0.0 : known.bonus();
        for (ServerPlayer eater : feast.eaters()) {
            eaters.add(eater.getUUID());
            PlayerSkills skills = ProficiencyAttachments.of(eater);
            double bonus = skills.bonus(Skill.HOMEBUILDER);
            if (HomebuilderMath.heartOfHome(skills) && bonus > best) {
                best = bonus;
                host = eater;
            }
        }
        long keep = now + join;
        if (host == null) {
            if (known == null || known.id() == null) {
                FEAST_HOSTS.put(key, new FeastHost(null, "", 0.0, eaters, keep));
                return;
            }
            FEAST_HOSTS.put(key, new FeastHost(known.id(), known.name(), known.bonus(), eaters, keep));
            // The host was there already: only this event's eaters are new to it.
            hand(feast, known.id(), known.name(), known.bonus(), feast.eaters(), now);
            return;
        }
        String name = host.getGameProfile().getName();
        FEAST_HOSTS.put(key, new FeastHost(host.getUUID(), name, best, eaters, keep));
        // A new (or better) host: everyone at the Feast so far gets it.
        java.util.List<ServerPlayer> all = new java.util.ArrayList<>();
        for (UUID id : eaters) {
            ServerPlayer eater = feast.level().getServer().getPlayerList().getPlayer(id);
            if (eater != null) {
                all.add(eater);
            }
        }
        hand(feast, host.getUUID(), name, best, all, now);
    }

    private static void hand(HearthEvents.Feast feast, UUID host, String name, double bonus,
            java.util.List<ServerPlayer> eaters, long now) {
        long until = now + HomebuilderMath.HEART_OF_HOME_TICKS;
        for (ServerPlayer eater : eaters) {
            if (!eater.getUUID().equals(host) && eater.isAlive() && !eater.isSpectator()) {
                give(eater, new Shared(bonus, until, true, host, name), until);
            }
        }
    }

    /** How long after its last eater a Feast can still be joined (the longest window it opens). */
    private static long feastJoinTicks() {
        var feast = dev.amman.proficiency.hearth.Hearths.data().feast();
        double seconds = Math.max(feast.windowSeconds(), feast.dinnerTriggerSeconds() + feast.dinnerBellSeconds());
        return Math.max(20L, Math.round(seconds * 20.0));
    }

    static void forget(UUID player) {
        SHARED.remove(player);
    }

    /** Test seam: what {@link #sharedBonus} would read, without the clock. */
    public static double sharedFor(ServerPlayer player) {
        Shared shared = SHARED.get(player.getUUID());
        return shared == null ? 0.0 : shared.bonus();
    }

    /** Test seam: who hands this player their shared passive now, null for nobody. */
    @Nullable
    public static UUID sharedBy(ServerPlayer player) {
        Shared shared = SHARED.get(player.getUUID());
        return shared == null ? null : shared.byId();
    }
}
