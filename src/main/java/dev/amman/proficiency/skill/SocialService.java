package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pays the Social skill. Every grant that the company bonus boosted adds a share of the extra to
 * a small pot ({@link #onGrant}); the pot is paid out at most every 5 seconds ({@link #tick}), so
 * mining next to a friend makes one "Working together" line that counts up instead of a Social
 * line between every block.
 *
 * <p>The anti-farm rules are all here, and they are the design:
 * <ul>
 * <li>Only a grant with a company factor above 1.0 pays. Alone pays nothing.</li>
 * <li>Standing next to someone while idle makes no grants, so it pays nothing.</li>
 * <li>Social's own grants never pay Social, and company never multiplies Social (see
 * {@link CompanyBonus#multiplier}). No feedback loop.</li>
 * <li>An op's {@code /proficiency addxp} does not count as work.</li>
 * </ul>
 */
public final class SocialService {

    /** The log and feed line for Social XP. */
    public static final String SOURCE = "proficiency.xplog.source.company";
    private static final String COMMAND_SOURCE = "proficiency.xplog.source.command";

    /** The shared pot code, also used by Nightwalker. */
    private static final SharePot POT = new SharePot(Skill.SOCIAL, SOURCE, SocialMath.PAY_INTERVAL_TICKS);
    /** When Good Company last rolled, per player. */
    private static final Map<UUID, Long> LAST_ROLL = new ConcurrentHashMap<>();

    private SocialService() {
    }

    /**
     * Called by {@link SkillService} for every grant, after every multiplier.
     *
     * @param amount  the XP the grant actually added
     * @param company the company factor that grant used
     */
    static void onGrant(ServerPlayer player, Skill skill, @Nullable String source, float amount,
            double company) {
        if (skill == Skill.SOCIAL || COMMAND_SOURCE.equals(source)
                || !ProficiencyConfig.enabled(Skill.SOCIAL)) {
            return;
        }
        POT.add(player.getUUID(), SocialMath.share(amount, company, ProficiencyConfig.socialShare()));
    }

    /** Base Social XP collected and not paid yet. For tests and the feed. */
    public static double pending(ServerPlayer player) {
        return POT.pending(player.getUUID());
    }

    /** Once a second from the server tick: pays the pot if 5 seconds have passed since the last. */
    public static void tick(ServerPlayer player) {
        if (POT.due(player)) {
            flush(player);
        }
    }

    /**
     * Pays everything in the pot now and rolls Good Company. Returns the Social XP added.
     * The tick calls it every 5 seconds; tests call it directly.
     */
    public static float flush(ServerPlayer player) {
        float paid = POT.flush(player);
        if (paid > 0) {
            rollGoodCompany(player, player.level().getGameTime());
        }
        return paid;
    }

    /**
     * Good Company, the signature: you and everyone within your company radius earn more XP for
     * 15 seconds. It rolls at most once a minute; Kindred Spirits (the ability) skips the wait and
     * makes every payout a Good Company, which keeps it up for the whole twenty seconds.
     */
    private static void rollGoodCompany(ServerPlayer player, long now) {
        boolean frenzied = ActiveService.isFrenzied(player, Skill.SOCIAL);
        Long lastRoll = LAST_ROLL.get(player.getUUID());
        if (!frenzied && lastRoll != null && now - lastRoll < SocialMath.PROC_GATE_TICKS
                && now >= lastRoll) {
            return;
        }
        // The gate counts attempts, not hits: a missed roll also waits a minute. That is the
        // intended rate (about one Good Company per 60 s / chance), as the README says.
        LAST_ROLL.put(player.getUUID(), now);
        if (!ProcService.fire(player, Skill.SOCIAL)) {
            return;
        }
        double multiplier = SocialMath.goodCompany(ProcService.power(player, Skill.SOCIAL));
        for (ServerPlayer other : CompanyBonus.companyNear(player)) {
            SkillService.inspire(other, multiplier, SocialMath.GOOD_COMPANY_TICKS);
            if (other != player) {
                other.displayClientMessage(Component.translatable("proficiency.social.good_company",
                        player.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
    }

    /**
     * A death wipes the XP bars, so unpaid Social XP goes too. Called from the death clone,
     * because the 1-second tick may never see the dead player (a fast respawn). The proc gate
     * stays, so dying does not reset the once-a-minute Good Company wait.
     */
    public static void onDeath(UUID player) {
        POT.wipe(player);
    }

    public static void forget(UUID player) {
        POT.forget(player);
        LAST_ROLL.remove(player);
    }
}
