package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Working near other people earns faster. This is the whole of the multiplayer design: nothing is
 * gated behind a second player, because one person has to be able to finish every tree alone. What
 * a second person changes is the rate. The one exception is Social, which is paid from this
 * bonus and so only grows in company.
 *
 * <p>Mentor is the big one and it is deliberately asymmetric: the novice gets the bonus, not the
 * expert. Bringing someone along costs the expert nothing and is worth a lot to the beginner, which
 * is the behaviour worth encouraging.
 *
 * <p>The Social skill's tree changes every number here: radius, camaraderie, mentor bonus and gap,
 * lingering, crowd size and teaching. The arithmetic is {@link SocialMath}.
 */
public final class CompanyBonus {

    /** XP is granted many times a second; rescanning the player list each time would be silly. */
    private static final int CACHE_TICKS = 40;

    private record Cached(long tick, double multiplier) {
    }

    private static final Map<UUID, Map<Skill, Cached>> CACHE = new ConcurrentHashMap<>();

    private CompanyBonus() {
    }

    /**
     * The company factor for one grant: 1.0 alone, 1.15 with camaraderie, and so on. Always 1.0
     * for Social itself: Social is paid out of what company adds, so letting company multiply
     * Social too would pay the same thing twice.
     */
    public static double multiplier(ServerPlayer player, Skill skill) {
        if (skill == Skill.SOCIAL) {
            return 1.0;
        }
        long now = player.level().getGameTime();
        Map<Skill, Cached> perSkill =
                CACHE.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        Cached cached = perSkill.get(skill);
        if (cached != null && now - cached.tick() < CACHE_TICKS && now >= cached.tick()) {
            return cached.multiplier();
        }
        double multiplier = compute(player, skill, now);
        perSkill.put(skill, new Cached(now, multiplier));
        return multiplier;
    }

    /** The Social talents of one player that change the bonus. */
    public static SocialMath.Talents talents(ServerPlayer player) {
        return SkillPassives.socialTalents(ProficiencyAttachments.of(player));
    }

    /** Who counts as company for this player in this skill, right now, before any lingering. */
    static SocialMath.Company scan(ServerPlayer player, Skill skill, SocialMath.Talents mine) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int myLevel = skills.level(skill);
        int mySocial = skills.level(Skill.SOCIAL);
        int gap = SocialMath.mentorGap(ProficiencyConfig.mentorGap(), mine);
        double radius = SocialMath.radius(ProficiencyConfig.companyRadius(), mine);
        double radiusSquared = radius * radius;

        int others = 0;
        boolean mentor = false;
        double teaching = 0.0;
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            if (other.distanceToSqr(player) > radiusSquared) {
                continue;
            }
            others++;
            PlayerSkills theirs = ProficiencyAttachments.of(other);
            if (theirs.level(skill) >= myLevel + gap) {
                mentor = true;
            }
            // Teaching: a better Social player lifts the people around them.
            int rank = TalentService.rank(theirs, Skill.SOCIAL, "teaching");
            if (rank > 0 && theirs.level(Skill.SOCIAL) > mySocial) {
                teaching = Math.max(teaching, SocialMath.teaching(rank));
            }
        }
        return new SocialMath.Company(others, mentor, teaching);
    }

    private static double compute(ServerPlayer player, Skill skill, long now) {
        SocialMath.Talents mine = talents(player);
        SocialMath.Company company = scan(player, skill, mine);
        Map<Skill, Seen> seen = LAST_SEEN.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>());
        if (company.others() > 0) {
            seen.put(skill, new Seen(now, company));
        } else {
            // Stay a While: the bonus outlasts the others by a few seconds per rank.
            Seen last = seen.get(skill);
            if (last != null && SocialMath.lingers(now, last.tick(), mine.linger())) {
                company = last.company();
            }
        }
        double passive = ProficiencyAttachments.of(player).bonus(Skill.SOCIAL);
        return 1.0 + SocialMath.extra(company, ProficiencyConfig.camaraderieBonus(),
                ProficiencyConfig.mentorBonus(), mine, passive);
    }

    private record Seen(long tick, SocialMath.Company company) {
    }

    /** The last time each skill had company, for Stay a While. */
    private static final Map<UUID, Map<Skill, Seen>> LAST_SEEN = new ConcurrentHashMap<>();

    /**
     * Everyone nearby who is far enough behind this player in a skill to count as their student.
     * Used to pass on the specialist material a proc just produced: the expert loses nothing and
     * the novice gets the thing their own tree is asking for, which is the whole idea. The gap is
     * the student's own (Patient Mentor and Heart of the Group shrink it), the radius the expert's.
     */
    public static List<ServerPlayer> studentsNear(ServerPlayer expert, Skill skill) {
        int expertLevel = ProficiencyAttachments.of(expert).level(skill);
        double radius = SocialMath.radius(ProficiencyConfig.companyRadius(), talents(expert));
        double radiusSquared = radius * radius;
        List<ServerPlayer> students = new java.util.ArrayList<>();
        for (ServerPlayer other : expert.serverLevel().players()) {
            if (other == expert || other.isSpectator()) {
                continue;
            }
            if (other.distanceToSqr(expert) > radiusSquared) {
                continue;
            }
            int gap = SocialMath.mentorGap(ProficiencyConfig.mentorGap(), talents(other));
            if (ProficiencyAttachments.of(other).level(skill) + gap <= expertLevel) {
                students.add(other);
            }
        }
        return students;
    }

    /** Everyone within this player's company radius, for Good Company. Includes the player. */
    public static List<ServerPlayer> companyNear(ServerPlayer player) {
        double radius = SocialMath.radius(ProficiencyConfig.companyRadius(), talents(player));
        double radiusSquared = radius * radius;
        List<ServerPlayer> near = new java.util.ArrayList<>();
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || (!other.isSpectator() && other.isAlive()
                    && other.distanceToSqr(player) <= radiusSquared)) {
                near.add(other);
            }
        }
        return near;
    }

    /** Drops only the 2-second cache, keeping the linger memory. For tests. */
    public static void rescan(UUID player) {
        CACHE.remove(player);
    }

    /** Drops everything kept for this player: the cache and the linger memory. */
    public static void forget(UUID player) {
        CACHE.remove(player);
        LAST_SEEN.remove(player);
    }
}
