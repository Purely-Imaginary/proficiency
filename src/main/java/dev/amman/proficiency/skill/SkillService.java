package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The one door XP goes through. Every hook calls {@link #grant}; nothing else touches
 * {@link PlayerSkills#addXp}.
 */
public final class SkillService {

    private SkillService() {
    }

    /** A grant with no first-time bonus: the second half of a split block, which the first half already paid for. */
    public static float grantNoFirstTime(Player player, Skill skill, double baseAmount, @Nullable String source) {
        return grantOnce(player, skill, baseAmount, source, 1.0);
    }

    public static void grant(Player player, Skill skill, double baseAmount) {
        grant(player, skill, baseAmount, null);
    }

    /**
     * As {@link #grant(Player, Skill, double)}, naming what paid for the XP log under the synergies
     * in the tree screen. {@code source} is a translation key: a block's or an entity's description
     * id, a biome key, or one of this mod's {@code proficiency.xplog.source.*} lines. Null is fine
     * and simply shows no source; the log is a courtesy, never a reason to skip a grant.
     *
     * @return the XP actually added after every multiplier, or 0 if nothing was granted
     */
    public static float grant(Player player, Skill skill, double baseAmount, @Nullable String source) {
        float amount = grantOnce(player, skill, baseAmount, source, 1.0);
        if (amount > 0 && player instanceof ServerPlayer serverPlayer) {
            firstTime(serverPlayer, skill, source);
        }
        return amount;
    }

    /**
     * Whether a source names a kind of thing worth a first-time bonus: a block, item, entity or
     * damage type (which Endurance names {@code proficiency.xplog.damage.*}). This mod's own lines (sprinting, a command, the convoy share) are not kinds, and
     * biomes, dimensions and structures already pay Wayfaring for being new.
     */
    public static boolean isKind(@Nullable String source) {
        if (source != null && source.startsWith("proficiency.xplog.damage.")) {
            // Endurance names damage types this way: a first fall, a first lava bath.
            return true;
        }
        return source != null && !source.isEmpty()
                && !source.startsWith(FirstTimeKinds.FIRST_PREFIX)
                && !source.startsWith("proficiency.")
                && !source.startsWith("biome.")
                && !source.startsWith("dimension.")
                && !source.startsWith("structure.");
    }

    /**
     * The first time a kind feeds a skill, a bonus on top, and the small banner. Stored in the
     * same visited set as places, as {@code first:<skill>:<canonical kind>}, so it is once per
     * player ever, and a death does not give it back.
     *
     * <p>Three gates, in this order. The kind is canonical ({@link FirstTimeKinds#canonical}), so
     * a variant is not new. A key stored under the raw source by an earlier version counts as
     * seen, and its canonical key is stored too so a sibling does not pay next. Then the build
     * cap: Masonry and Decorating pay a few per day, and over the cap the kind stays unseen.
     * The bonus is its own log line, source {@code first|<kind>}, so it never merges into "x2".
     */
    private static void firstTime(ServerPlayer player, Skill skill, @Nullable String source) {
        double base = ProficiencyConfig.firstTimeXp();
        if (base <= 0 || !isKind(source)) {
            return;
        }
        String kind = FirstTimeKinds.canonical(source);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        String key = "first:" + skill.id() + ":" + kind;
        if (skills.hasVisited(key)) {
            return;
        }
        for (String variant : FirstTimeKinds.legacyVariants(kind)) {
            if (skills.hasVisited("first:" + skill.id() + ":" + variant)) {
                skills.markVisited(key);
                return;
            }
        }
        if (FirstTimeKinds.isBuildSkill(skill)
                && !skills.takeBuildFirstTime(skill.id(), java.time.LocalDate.now().toEpochDay(),
                        ProficiencyConfig.firstTimeBuildPerDay())) {
            return;
        }
        skills.markVisited(key);
        // The tier goes in as a factor, not folded into the base, so the feed shows it.
        double tier = ProficiencyConfig.firstTimeTierScaling() ? FirstTimeTiers.multiplier(kind) : 1.0;
        float paid = grantOnce(player, skill, base, FirstTimeKinds.FIRST_PREFIX + kind, tier);
        dev.amman.proficiency.net.ProficiencyNetwork.sendDiscovery(player,
                dev.amman.proficiency.net.DiscoveryPayload.FIRST, kind, kind, skill, paid);
    }

    private static float grantOnce(Player player, Skill skill, double baseAmount,
            @Nullable String source, double tier) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return 0f;
        }
        if (serverPlayer.isSpectator() || !Double.isFinite(baseAmount) || baseAmount <= 0) {
            return 0f;
        }
        if (!ProficiencyConfig.enabled(skill)) {
            return 0f;
        }

        PlayerSkills skills = ProficiencyAttachments.of(serverPlayer);
        double rate = ProficiencyConfig.xpRate(skill) * ProficiencyConfig.xpMultiplier();
        double perk = skills.perkModifier(skill, dev.amman.proficiency.perk.PerkEffect.XP_RATE);
        double company = CompanyBonus.multiplier(serverPlayer, skill);
        // Social and Nightwalker are paid in 5-second lumps, so a tempo chain could never build.
        double tempo = skill == Skill.SOCIAL || skill == Skill.NIGHTWALKER
                ? 1.0 : Tempo.multiplier(serverPlayer, skill);
        double overflow = overflow(serverPlayer, skill);
        double inspired = inspiration(serverPlayer);
        double streak = SurvivalStreak.multiplier(skills);
        float amount = (float) (baseAmount * rate * perk * company * tempo * overflow * inspired
                * streak * tier);
        if (!Float.isFinite(amount) || amount <= 0) {
            return 0f;
        }
        skills.noteActive(serverPlayer.level().getGameTime());

        // Every factor that is not 1.0: the tree's log tooltip, the feed's second line, the CSV.
        java.util.List<XpFactors.Factor> factors = new java.util.ArrayList<>();
        XpFactors.add(factors, XpFactors.RATE, rate);
        XpFactors.add(factors, XpFactors.PERK, perk);
        XpFactors.add(factors, XpFactors.COMPANY, company);
        XpFactors.add(factors, XpFactors.TEMPO, tempo);
        XpFactors.add(factors, XpFactors.OVERFLOW, overflow);
        XpFactors.add(factors, XpFactors.INSPIRED, inspired);
        XpFactors.add(factors, XpFactors.STREAK, streak);
        XpFactors.add(factors, XpFactors.TIER, tier);
        // What the player actually got, after every multiplier, which is the number worth showing.
        XP_LOGS.computeIfAbsent(serverPlayer.getUUID(), id -> new XpLog())
                .add(skill.ordinal(), source, amount, (float) baseAmount, XpFactors.encode(factors),
                        System.currentTimeMillis());
        boolean recording = XpFeedRecorder.isRecording(serverPlayer.getUUID());
        if (skills.xpFeed() || recording) {
            if (recording) {
                XpFeedRecorder.record(serverPlayer, skill, source, (float) baseAmount, amount, factors);
            }
            if (skills.xpFeed()) {
                bufferFeed(serverPlayer, skill, source, amount, baseAmount, factors);
            }
        }

        int gained = skills.addXp(skill, amount);
        if (gained > 0) {
            onLevelUp(serverPlayer, skill, skills.level(skill) - gained, skills.level(skill));
        }
        // Social's only source: a share of what company just added. It ignores its own grants.
        SocialService.onGrant(serverPlayer, skill, source, amount, company);
        // Nightwalker's only share source: a part of any grant earned in the dark.
        NightwalkerService.onGrant(serverPlayer, skill, source, baseAmount, tier);
        return amount;
    }

    /** Sprinting, sneaking and swimming: the sources that pay a steady trickle. */
    static boolean isMovementSource(@Nullable String source) {
        return "proficiency.xplog.source.sprinting".equals(source)
                || "proficiency.xplog.source.sneaking".equals(source)
                || "proficiency.xplog.source.swimming".equals(source);
    }

    private static void bufferFeed(ServerPlayer player, Skill skill, @Nullable String source,
            float amount, double baseAmount, java.util.List<XpFactors.Factor> factors) {
        java.util.List<XpFeedGain> feed = XP_FEEDS.computeIfAbsent(player.getUUID(),
                id -> new java.util.ArrayList<>());
        // Movement pays about once a second. Fold a run of them into one feed line so a long sprint
        // does not flood the feed; the amounts are summed, so the totals stay exact.
        if (isMovementSource(source) && !feed.isEmpty()) {
            XpFeedGain last = feed.get(feed.size() - 1);
            if (last.skill() == skill.ordinal() && last.source().equals(source)) {
                feed.set(feed.size() - 1, new XpFeedGain(last.skill(), last.source(),
                        last.amount() + amount, last.base() + (float) baseAmount,
                        java.util.List.copyOf(factors)));
                return;
            }
        }
        if (feed.size() < XP_FEED_MAX) {
            feed.add(new XpFeedGain(skill.ordinal(), source == null ? "" : source,
                    amount, (float) baseAmount, java.util.List.copyOf(factors)));
        }
    }

    /**
     * Inspired: a multiplier on every skill's XP until a given game time. Kept here rather than in
     * the talent code because this is the one place every grant passes through; the talent only
     * decides when to start it. It stores an expiry in game time, so nothing has to tick it: an
     * expired entry is dropped on the next grant that reads it.
     */
    private record Inspiration(double multiplier, long until) {
    }

    private static final java.util.Map<java.util.UUID, Inspiration> INSPIRED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Every skill earns {@code multiplier} times the XP for the next {@code ticks} ticks. A weaker
     * one never replaces a stronger one still running: Social's Good Company must not cut short a
     * Wayfaring Inspired.
     */
    public static void inspire(ServerPlayer player, double multiplier, int ticks) {
        if (!(multiplier > 1.0) || ticks <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        Inspiration running = INSPIRED.get(player.getUUID());
        if (running != null && now < running.until() && running.multiplier() > multiplier) {
            return;
        }
        INSPIRED.put(player.getUUID(), new Inspiration(multiplier, now + ticks));
    }

    public static void forget(java.util.UUID player) {
        INSPIRED.remove(player);
        XP_LOGS.remove(player);
        XP_FEEDS.remove(player);
        XpFeedRecorder.stop(player);
        SocialService.forget(player);
        NightwalkerService.forget(player);
    }

    /**
     * Every online player's recent gains. Per session on purpose: dropped on logout by
     * {@link #forget}, never saved, and pushed to the owner by the sync tick in
     * PlayerLifecycleEvents only when it changed.
     */
    private static final java.util.Map<java.util.UUID, XpLog> XP_LOGS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * One raw gain for the opt-in debug feed: {@code amount} after every multiplier, {@code base}
     * as the call site asked for it, so the gap between the two is the multipliers at work; {@code factors} names each one.
     */
    public record XpFeedGain(int skill, String source, float amount, float base,
            java.util.List<XpFactors.Factor> factors) {
    }

    /** Most gains buffered between two sync ticks. A tree-felling burst is well under this. */
    public static final int XP_FEED_MAX = 64;

    /** Gains since the last sync tick, only for players with the feed on. */
    private static final java.util.Map<java.util.UUID, java.util.List<XpFeedGain>> XP_FEEDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Takes and clears this player's buffered gains; empty if none. */
    public static java.util.List<XpFeedGain> drainXpFeed(ServerPlayer player) {
        java.util.List<XpFeedGain> feed = XP_FEEDS.remove(player.getUUID());
        return feed == null ? java.util.List.of() : feed;
    }

    /** This player's log, or null if they have earned nothing this session. */
    @Nullable
    public static XpLog xpLog(ServerPlayer player) {
        return XP_LOGS.get(player.getUUID());
    }

    /** The Inspired multiplier running on this player now, 1.0 when none. */
    public static double inspiredMultiplier(ServerPlayer player) {
        return inspiration(player);
    }

    private static double inspiration(ServerPlayer player) {
        Inspiration inspiration = INSPIRED.get(player.getUUID());
        if (inspiration == null) {
            return 1.0;
        }
        if (player.level().getGameTime() >= inspiration.until()) {
            INSPIRED.remove(player.getUUID(), inspiration);
            return 1.0;
        }
        return inspiration.multiplier();
    }

    /** Overflow: while the ability runs, this skill earns double. */
    private static double overflow(ServerPlayer player, Skill skill) {
        return ActiveService.isFrenzied(player, skill)
                && dev.amman.proficiency.perk.TalentService.hasSpecial(player, skill, "overflow")
                ? 2.0 : 1.0;
    }

    private static void onLevelUp(ServerPlayer player, Skill skill, int from, int level) {
        ProficiencyNetwork.sendFullSync(player);
        ProficiencyNetwork.sendLevelUp(player, skill, level);

        // Only this player hears the chime. A shared base would be unbearable otherwise.
        player.playNotifySound(
                SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, 1.2f);
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.35, 0.45, 0.35, 0.02);

        // Crossing the unlock level is the moment the skill stops being a percentage, so say so.
        if (Milestones.passed(from, level, ProficiencyConfig.procUnlockLevel())
                && ProficiencyConfig.procsEnabled()) {
            player.sendSystemMessage(Component.translatable("proficiency.proc.unlocked",
                            Component.translatable(skill.translationKey()),
                            Component.translatable(skill.procKey()).withStyle(ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.translatable(skill.procDescKey())
                    .withStyle(ChatFormatting.GRAY));
            player.playNotifySound(
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
        }

        // Every tenth level by default (10, 20 ... 100), to the whole server. The highest one the
        // step crossed, so a gain that jumps 9 to 11 still announces 10.
        int milestone = Milestones.crossed(from, level, ProficiencyConfig.milestoneInterval(),
                ProficiencyConfig.milestoneMinLevel());
        if (ProficiencyConfig.announceMilestones() && milestone > 0) {
            Component message = Component.translatable(
                            milestone >= SkillMath.MAX_LEVEL
                                    ? "proficiency.announce.mastered" : "proficiency.announce.milestone",
                            player.getDisplayName(),
                            Component.translatable(skill.translationKey()),
                            milestone)
                    .withStyle(ChatFormatting.GOLD);
            player.server.getPlayerList().broadcastSystemMessage(message, false);
        }
    }

    /** Current passive bonus for a skill, as a fraction. Safe to call on either side. */
    public static double bonus(Player player, Skill skill) {
        return ProficiencyAttachments.of(player).bonus(skill);
    }

    public static int level(Player player, Skill skill) {
        return ProficiencyAttachments.of(player).level(skill);
    }
}
