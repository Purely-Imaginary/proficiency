package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.NightwalkerService;
import dev.amman.proficiency.skill.SharePot;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SocialService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How Homebuilder earns (docs/HOMEBUILDER-SPEC.md, "How it earns XP"): a share of every other
 * skill's grant earned at home, paid every 5 s through a {@link SharePot} as one "At home" line, and
 * the home's milestones: the ones that come once (stages, Comfort kinds, Character ids) once per
 * Hearth and per player, the ones that come again (sieges, Feasts, Tales, Chronicle pages) once per
 * player each time they happen.
 *
 * <p>The anti-farm rules are the design:
 * <ul>
 * <li>No source of its own: building already pays Masonry and Decorating, so nothing here pays for a
 * block a second time.</li>
 * <li>Homebuilder's own grants, the other share pots' payouts (Social's company share, Nightwalker's
 * dark share), first-time bonuses, an op's {@code addxp} and teaching never feed the share.</li>
 * <li>Homebuilder feeds no Hearth pool (no pool names it) and is not Tales.</li>
 * <li>A milestone (a stage, a Comfort kind, a Character id) is keyed by the Hearth's position, so
 * breaking and relighting a Hearth pays nothing twice. A death empties the unpaid pot, like every
 * other XP bar.</li>
 * <li>The rows that happen again (a siege defended, a Feast, Tales paid in, a Chronicle page) pay
 * each time they happen; their own rules already bound them (a siege's cooldown, one Feast a day per
 * Hearth, Tales lost on death, one write bonus per homecoming). Only the first at each Hearth gets
 * the first-time banner.</li>
 * </ul>
 */
public final class HomebuilderXp {

    /** The share's log and feed line; the telemetry kind {@code home}. */
    public static final String SHARE_SOURCE = "proficiency.xplog.source.home";
    /** Milestone lines: {@code <prefix><stage>} for a stage, {@code <prefix><kind>} for a Comfort kind. */
    public static final String STAGE_SOURCE = "proficiency.xplog.source.home_stage.";
    public static final String COMFORT_SOURCE = "proficiency.xplog.source.home_comfort.";
    public static final String CHARACTER_SOURCE = "proficiency.xplog.source.home_character";
    public static final String SIEGE_SOURCE = "proficiency.xplog.source.home_siege";
    public static final String FEAST_SOURCE = "proficiency.xplog.source.home_feast";
    public static final String TALES_SOURCE = "proficiency.xplog.source.home_tales";
    public static final String CHRONICLE_SOURCE = "proficiency.xplog.source.home_chronicle";

    private static final SharePot POT =
            new SharePot(Skill.HOMEBUILDER, SHARE_SOURCE, HomebuilderMath.PAY_INTERVAL_TICKS);
    /** The last Chronicle page each player was paid for, so a page fired twice pays once. */
    private static final Map<UUID, Long> LAST_PAGE = new ConcurrentHashMap<>();

    private HomebuilderXp() {
    }

    static void init() {
        HearthEvents.STAGE_SEALED.add(e -> milestone(e.player(), e.level(), e.hearth(), "stage:" + e.stage(),
                HomebuilderMath.stageXp(e.stage(), ProficiencyConfig.homeStageXp()), STAGE_SOURCE + e.stage(), true));
        HearthEvents.COMFORT_KIND_SEEN.add(e -> milestone(online(e.level(), e.placer()), e.level(), e.hearth(),
                "comfort:" + e.kind(), ProficiencyConfig.homeComfortXp(), COMFORT_SOURCE + e.kind(), true));
        HearthEvents.CHARACTER_ID_SEEN.add(e -> milestone(online(e.level(), e.placer()), e.level(), e.hearth(),
                "character:" + e.blockId(), ProficiencyConfig.homeCharacterXp(), CHARACTER_SOURCE, false));
        HearthEvents.SIEGE_DEFENDED.add(HomebuilderXp::onSiegeDefended);
        // FEAST fires when a Feast starts (its eaters) and again for each eater who joins it (that
        // eater alone), so each eater is in it once per Feast.
        HearthEvents.FEAST.add(e -> {
            for (ServerPlayer eater : e.eaters()) {
                occasion(eater, e.level(), e.hearth(), "feast", ProficiencyConfig.homeFeastXp(), FEAST_SOURCE);
            }
        });
        HearthEvents.TALES_PAID.add(e -> occasion(e.player(), e.level(), e.hearth(), "tales",
                e.tales() * ProficiencyConfig.homeTalesShare(), TALES_SOURCE));
        HearthEvents.CHRONICLE_WRITTEN.add(HomebuilderXp::onChronicle);
    }

    // ---- The share ------------------------------------------------------------------------------

    /** {@code HearthHooks.GRANT}: a share of another skill's grant earned at home goes into the pot. */
    static void onGrant(HearthHooks.Grant grant) {
        if (!counts(grant) || !ProficiencyConfig.enabled(Skill.HOMEBUILDER)) {
            return;
        }
        ServerPlayer player = grant.player();
        if (!HearthRange.isHome(player)) {
            return;
        }
        double aoe = grant.aoe() ? Math.min(1.0, ProficiencyConfig.aoeXpShare()) : 1.0;
        POT.add(player.getUUID(), HomebuilderMath.share(grant.baseAmount(), ProficiencyConfig.xpRate(grant.skill()),
                aoe, ProficiencyConfig.homeShare()));
    }

    /**
     * Whether a grant may feed the share at all (before "at home"): not Homebuilder's own, not
     * another share pot's payout, not a first-time bonus, and real play (no command, no teaching).
     */
    public static boolean counts(HearthHooks.Grant grant) {
        String source = grant.source();
        return grant.skill() != Skill.HOMEBUILDER && grant.play() && !grant.firstTime()
                && !SocialService.SOURCE.equals(source) && !NightwalkerService.DARK_SOURCE.equals(source)
                && !SHARE_SOURCE.equals(source);
    }

    /** Base Homebuilder XP collected at home and not paid yet. */
    public static double pending(ServerPlayer player) {
        return POT.pending(player.getUUID());
    }

    /** Pays the pot now. The per-player second does it every 5 s; tests call it directly. */
    public static float flush(ServerPlayer player) {
        return POT.flush(player);
    }

    /** Once a second: pay the pot when it is due. */
    static void tick(ServerPlayer player) {
        if (POT.due(player)) {
            POT.flush(player);
        }
    }

    static void onDeath(UUID player) {
        POT.wipe(player);
    }

    static void forget(UUID player) {
        POT.forget(player);
        LAST_PAGE.remove(player);
    }

    // ---- Milestones -----------------------------------------------------------------------------

    /**
     * Pays one milestone once per Hearth and per player: the key is the Hearth's key ("dimension|x,y,z")
     * plus {@code what}. A first-time banner announces it unless {@code banner} is off (Character ids
     * come in dozens). Returns the XP paid, 0 when it was paid before or nothing was owed.
     *
     * <p>The key is stored only once the grant paid something: a grant that pays nothing (a
     * spectator, a Homebuilder rate of 0) leaves the milestone owed instead of spending it.
     */
    public static float milestone(@Nullable ServerPlayer player, ServerLevel level, BlockPos hearth, String what,
            double xp, String source, boolean banner) {
        if (!owed(player, xp)) {
            return 0f;
        }
        String key = HearthBlockEntity.key(level, hearth) + "|" + what;
        if (HearthAttachments.of(player).hasMilestone(key)) {
            return 0f;
        }
        float paid = pay(player, xp, source, banner);
        if (paid > 0) {
            HearthAttachments.of(player).markMilestone(key);
        }
        return paid;
    }

    /**
     * Pays a row that happens again (a siege defended, a Feast, Tales paid in, a Chronicle page)
     * every time. The first one at each Hearth gets the first-time banner and is remembered under the
     * milestone key {@code <hearth key>|<what>}; later ones are a plain line in the XP feed.
     */
    public static float occasion(@Nullable ServerPlayer player, ServerLevel level, BlockPos hearth, String what,
            double xp, String source) {
        if (!owed(player, xp)) {
            return 0f;
        }
        String key = HearthBlockEntity.key(level, hearth) + "|" + what;
        boolean first = !HearthAttachments.of(player).hasMilestone(key);
        float paid = pay(player, xp, source, first);
        if (paid > 0 && first) {
            HearthAttachments.of(player).markMilestone(key);
        }
        return paid;
    }

    private static boolean owed(@Nullable ServerPlayer player, double xp) {
        return player != null && xp > 0 && Double.isFinite(xp) && ProficiencyConfig.enabled(Skill.HOMEBUILDER);
    }

    private static float pay(ServerPlayer player, double xp, String source, boolean banner) {
        float paid = SkillService.grant(player, Skill.HOMEBUILDER, xp, source);
        if (paid > 0) {
            // The Discovery journal's Skills tab lists it with the other skills' first times.
            if (ProficiencyAttachments.of(player).markVisited("first:" + Skill.HOMEBUILDER.id() + ":" + source)) {
                dev.amman.proficiency.skill.CodexService.onKindFound(player, Skill.HOMEBUILDER);
            }
        }
        if (banner && paid > 0) {
            // The first-time banner: "First time · Homebuilder / Stage III sealed / +450 Homebuilder XP".
            ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.FIRST, source, source, Skill.HOMEBUILDER, paid);
        }
        return paid;
    }

    /** A Chronicle page's flat part: once per page (the Tales lane fires it once per homecoming). */
    static void onChronicle(HearthEvents.ChronicleWritten event) {
        ServerPlayer author = event.author();
        if (author == null) {
            return;
        }
        Long last = LAST_PAGE.get(author.getUUID());
        if (last != null && last == event.pageId()) {
            return;
        }
        if (occasion(author, event.level(), event.hearth(), "chronicle", ProficiencyConfig.homeChronicleXp(),
                CHRONICLE_SOURCE) > 0) {
            LAST_PAGE.put(author.getUUID(), event.pageId());
        }
    }

    /** A defended siege: 300 XP split by kills, at least 10% each for anyone present; every siege pays. */
    static void onSiegeDefended(HearthEvents.SiegeDefended event) {
        List<UUID> present = new ArrayList<>();
        for (ServerPlayer player : event.present()) {
            present.add(player.getUUID());
        }
        Map<UUID, Double> split = HomebuilderMath.siegeSplit(event.kills(), present,
                ProficiencyConfig.homeSiegeXp(), HomebuilderMath.SIEGE_MIN_SHARE);
        for (Map.Entry<UUID, Double> entry : split.entrySet()) {
            occasion(online(event.level(), entry.getKey()), event.level(), event.hearth(), "siege",
                    entry.getValue(), SIEGE_SOURCE);
        }
    }

    @Nullable
    private static ServerPlayer online(ServerLevel level, @Nullable UUID id) {
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }
}
