package dev.amman.proficiency.coop;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.skill.CompanyBonus;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.skill.SocialMath;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Co-op XP streams (1.45.0), the server half. Every grant goes into a {@link CoopRules.Ledger}; once a second the
 * ledger is drained and each player gets one small batch: the partners in company with them (the pip row, sent
 * when it changes), one stream per partner who earned XP within company range, and the company XP their own
 * grants carried. A player out of range of an earner gets nothing about them. The batch is cosmetic and optional:
 * it goes only to a client that registered it, so an older client still joins and sees nothing.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's copySharedHearth); the send is each
 * line's {@code ProficiencyNetwork.sendCoop}.
 */
public final class CoopService {

    private static final CoopRules.Ledger LEDGER = new CoopRules.Ledger();
    /** What each player's client was last told about its partners. */
    private static final Map<UUID, List<CoopBatch.Partner>> LAST_PARTNERS = new HashMap<>();
    /** Batches since each player's client was last sent anything: a non-empty pip row is repeated every few. */
    private static final Map<UUID, Integer> QUIET = new HashMap<>();
    /** A client that dropped its state (a world change, a reload) gets its pip row back within this many seconds. */
    private static final int REPEAT_PARTNERS = 5;
    private static int ticks;

    private CoopService() {
    }

    /**
     * Called from the grant, after the XP is paid.
     *
     * @param amount  what the player got, every multiplier included
     * @param company the grant's company factor (1.0 alone)
     */
    public static void onGrant(ServerPlayer player, Skill skill, double amount, double company) {
        if (!ProficiencyConfig.coopStreams()) {
            return;
        }
        LEDGER.add(player.getUUID(), skill.ordinal(), amount, company);
    }

    /** Every server tick; sends once every {@link CoopRules#BATCH_TICKS}. */
    public static void tick(MinecraftServer server) {
        if (++ticks < CoopRules.BATCH_TICKS) {
            return;
        }
        ticks = 0;
        Map<UUID, CoopBatch> batches = build(server.getPlayerList().getPlayers());
        if (batches.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            CoopBatch batch = batches.get(player.getUUID());
            if (batch != null) {
                ProficiencyNetwork.sendCoop(player, batch);
            }
        }
    }

    /**
     * Drains the second's ledger into one batch per player who has news (a stream, own company XP, or a changed
     * partner list). Public for the GameTests, which cannot receive packets.
     */
    public static Map<UUID, CoopBatch> build(List<ServerPlayer> players) {
        List<CoopRules.Earned> earned = LEDGER.drainEarned();
        Map<UUID, CoopBatch> out = new LinkedHashMap<>();
        if (!ProficiencyConfig.coopStreams()) {
            LEDGER.clearOwn();
            return out;
        }
        Map<UUID, ServerPlayer> byId = new HashMap<>();
        Map<UUID, Double> radius = new HashMap<>();
        for (ServerPlayer player : players) {
            if (eligible(player)) {
                byId.put(player.getUUID(), player);
                radius.put(player.getUUID(), radiusOf(player));
            }
        }
        for (ServerPlayer me : byId.values()) {
            double myRadius = radius.get(me.getUUID());
            List<CoopBatch.Partner> partners = new ArrayList<>();
            for (ServerPlayer other : byId.values()) {
                if (other != me && other.level() == me.level()
                        && CoopRules.inCompany(me.distanceTo(other), myRadius)) {
                    partners.add(new CoopBatch.Partner(other.getUUID(), other.getGameProfile().getName()));
                }
            }
            partners.sort(Comparator.comparing(CoopBatch.Partner::name, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(CoopBatch.Partner::id));
            if (partners.size() > CoopRules.MAX_PARTNERS) {
                partners = new ArrayList<>(partners.subList(0, CoopRules.MAX_PARTNERS));
            }

            List<CoopBatch.Stream> streams = new ArrayList<>();
            for (CoopRules.Earned e : earned) {
                ServerPlayer earner = byId.get(e.earner());
                if (earner == null || earner == me || earner.level() != me.level()
                        || streams.size() >= CoopRules.MAX_STREAMS) {
                    continue;
                }
                double distance = me.distanceTo(earner);
                Skill top = e.topSkill() >= 0 && e.topSkill() < Skill.VALUES.length ? Skill.VALUES[e.topSkill()]
                        : Skill.VALUES[0];
                // Social has no company factor of its own (it is paid from it), so ask with any other skill.
                Skill ask = top == Skill.SOCIAL ? Skill.VALUES[0] : top;
                boolean paid = distance <= myRadius && CompanyBonus.multiplier(me, ask) > 1.0;
                CoopRules.Mode mode = CoopRules.mode(distance, myRadius, radius.get(earner.getUUID()), paid);
                if (mode == CoopRules.Mode.NONE) {
                    continue;
                }
                streams.add(new CoopBatch.Stream(earner.getUUID(), earner.getGameProfile().getName(), top.ordinal(),
                        (float) e.xp(), CoopRules.dotsFor(e.xp()), mode == CoopRules.Mode.PAYS,
                        (float) earner.getX(), (float) (earner.getY() + earner.getBbHeight() * 0.6),
                        (float) earner.getZ()));
            }
            float own = (float) LEDGER.takeOwn(me.getUUID());
            CoopBatch batch = new CoopBatch(partners, streams, own);
            List<CoopBatch.Partner> before = LAST_PARTNERS.getOrDefault(me.getUUID(), List.of());
            boolean changed = !CoopBatch.samePartners(before, partners);
            int quiet = QUIET.getOrDefault(me.getUUID(), 0) + 1;
            boolean repeat = !partners.isEmpty() && quiet >= REPEAT_PARTNERS;
            if (batch.hasNews() || changed || repeat) {
                out.put(me.getUUID(), batch);
                quiet = 0;
            }
            QUIET.put(me.getUUID(), quiet);
            LAST_PARTNERS.put(me.getUUID(), List.copyOf(partners));
        }
        LEDGER.clearOwn();
        return out;
    }

    private static boolean eligible(ServerPlayer player) {
        return !player.isSpectator() && player.isAlive() && !SkillTools.isFakePlayer(player);
    }

    private static double radiusOf(ServerPlayer player) {
        return SocialMath.radius(ProficiencyConfig.companyRadius(), CompanyBonus.talents(player));
    }

    /** Logout: forget what the player earned and what their client was told. */
    public static void forget(UUID player) {
        LEDGER.forget(player);
        LAST_PARTNERS.remove(player);
        QUIET.remove(player);
    }
}
