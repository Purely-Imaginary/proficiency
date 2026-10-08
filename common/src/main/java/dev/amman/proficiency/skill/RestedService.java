package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import net.minecraft.server.level.ServerPlayer;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rested XP on the server. Once a second it lets every active player's unused skills rest
 * ({@link RestedMath#rest}) and lets teachers fill their students' pools ({@link RestedMath#teach}).
 * Spending is in {@link SkillService}, where every grant passes. The design is in
 * docs/RESTED-AND-TEACHING.md.
 *
 * <p>A teacher for a skill is someone near the student (within the teacher's own company radius
 * plus Wide Classroom), not AFK, who earned XP in that skill in the last 5 seconds, and whose
 * level in it is at least the student's mentor gap above the student's. The student must be
 * active too. With several candidates the highest level teaches.
 */
public final class RestedService {

    private RestedService() {
    }

    /** One teacher who is teaching one skill right now. */
    record Teacher(UUID id, int level, int fillRank) {
    }

    /** Once a second from the server tick, for every online player. */
    public static void tick(ServerPlayer player, int elapsedTicks) {
        if (!RestedMath.enabled() || !player.isAlive() || player.isSpectator() || elapsedTicks <= 0) {
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        long now = player.level().getGameTime();
        long window = ProficiencyConfig.streakActiveWindowTicks();
        if (!skills.isActive(now, window)) {
            return;
        }
        double seconds = elapsedTicks / 20.0;
        Map<Skill, Teacher> teachers = teachersFor(player, skills, now, window);
        for (Map.Entry<Skill, Teacher> entry : teachers.entrySet()) {
            RestedMath.teach(skills, entry.getKey(), entry.getValue().id(), entry.getValue().fillRank(),
                    seconds);
        }
        RestedMath.rest(skills, now, window, seconds, teachers.keySet());
        // A lowered level or config can leave a pool over its cap.
        skills.trimRested();
    }

    /** The teacher each skill has for this student right now, if any. Empty when nobody is near. */
    static Map<Skill, Teacher> teachersFor(ServerPlayer student, PlayerSkills mine, long now, long window) {
        Map<Skill, Teacher> found = new EnumMap<>(Skill.class);
        List<ServerPlayer> others = null;
        int gap = SocialMath.mentorGap(ProficiencyConfig.mentorGap(), CompanyBonus.talents(student));
        for (ServerPlayer other : student.serverLevel().players()) {
            if (other == student || other.isSpectator() || !other.isAlive()) {
                continue;
            }
            PlayerSkills theirs = ProficiencyAttachments.of(other);
            if (!theirs.isActive(now, window)) {
                continue;
            }
            double radius = RestedMath.teachRadius(
                    SocialMath.radius(ProficiencyConfig.companyRadius(), CompanyBonus.talents(other)),
                    TalentService.rank(theirs, Skill.SOCIAL, "teach_radius"));
            if (other.distanceToSqr(student) > radius * radius) {
                continue;
            }
            for (Skill skill : Skill.VALUES) {
                if (!RestedMath.usedLately(now, theirs.usedAt(skill))
                        || !RestedMath.canTeach(theirs.level(skill), mine.level(skill), gap)) {
                    continue;
                }
                Teacher best = found.get(skill);
                if (best == null || theirs.level(skill) > best.level()) {
                    found.put(skill, new Teacher(other.getUUID(), theirs.level(skill),
                            TalentService.rank(theirs, Skill.SOCIAL, "teach_rate")));
                }
            }
        }
        return found;
    }

    /** On login: an old save has no "last used" days, so stamp today and nothing starts out rusty. */
    public static void onLogin(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        if (skills.stampUnusedDays(RestedMath.today())) {
            skills.markDirty();
        }
        TeachingService.onLogin(player);
    }

    /**
     * Spends the pool on one grant, for {@link SkillService}: returns the extra XP and records what
     * each teacher is owed. {@code amount} is the grant after every multiplier and {@code streak}
     * the survival multiplier inside it.
     */
    static float spend(ServerPlayer player, PlayerSkills skills, Skill skill, float amount, double streak) {
        RestedMath.Spend spend = RestedMath.spend(skills, skill, amount, streak,
                teacher -> TeachingService.cutRank(player.server, teacher));
        if (spend.extra() > 0f) {
            TeachingService.credit(player, spend.credits());
        }
        return spend.extra();
    }

    /** Everyone a death touches: the pools go, and so does the credit waiting on this student. */
    public static Map<Skill, Float> onDeath(ServerPlayer dead, PlayerSkills fresh) {
        Map<Skill, Float> lost = fresh.loseRested();
        TeachingService.onStudentDeath(dead.server, dead.getUUID());
        return lost;
    }

    public static void forget(UUID player) {
        TeachingService.forget(player);
    }
}
