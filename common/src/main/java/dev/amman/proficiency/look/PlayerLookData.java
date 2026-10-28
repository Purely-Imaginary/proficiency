package dev.amman.proficiency.look;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.CompanyBonus;
import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.skill.SocialMath;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * The server half of the Jade tooltip of another player: their picked Codex title, their three best skills,
 * and what the two of you gain together now (company, mentor, teaching, a shared Hearth). It reads and changes
 * nothing, and writes nothing at all when the server switched it off ({@code company.jadePlayers}), so a server
 * can keep levels private. The rules are {@link PlayerLookRules}.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class PlayerLookData {

    /** Present (1) when the tag has anything to show. */
    public static final String PRESENT = "plv";
    /** The picked Codex title id ({@code mining:25}); absent for none. */
    public static final String TITLE = "pls";
    /** Top skills, interleaved: ordinal, level, ordinal, level ... */
    public static final String TOP = "plt";
    /** {@link PlayerLookRules} flag bits. */
    public static final String FLAGS = "plg";
    /** The XP fraction company adds for the looking player (a float, 0.15 = +15%). */
    public static final String EXTRA = "plx";
    /** The looking player's company radius, in blocks. */
    public static final String RADIUS = "plr";
    /** Ordinals of the skills the other leads the looker in (their mentoring of you). */
    public static final String MENTORS = "plm";
    /** Ordinals of the skills the looker leads the other in (your mentoring of them). */
    public static final String STUDENTS = "pln";

    private PlayerLookData() {
    }

    public static void write(ServerPlayer looker, Entity entity, CompoundTag out) {
        if (!(entity instanceof ServerPlayer other) || other == looker || !ProficiencyConfig.jadePlayers()
                || SkillTools.isFakePlayer(other) || SkillTools.isFakePlayer(looker) || other.isSpectator()) {
            return;
        }
        PlayerSkills mine = ProficiencyAttachments.of(looker);
        PlayerSkills theirs = ProficiencyAttachments.of(other);
        int[] myLevels = levels(mine);
        int[] theirLevels = levels(theirs);
        out.putByte(PRESENT, (byte) 1);
        if (Codex.parse(theirs.codexTitle()) != null) {
            out.putString(TITLE, theirs.codexTitle());
        }
        List<PlayerLookRules.Pick> top = PlayerLookRules.top(theirLevels, PlayerLookRules.TOP);
        int[] flat = new int[top.size() * 2];
        for (int i = 0; i < top.size(); i++) {
            flat[i * 2] = top.get(i).skill().ordinal();
            flat[i * 2 + 1] = top.get(i).level();
        }
        out.putIntArray(TOP, flat);

        BlockPos myHome = HearthRange.homeOf(looker);
        boolean sameHearth = myHome != null && myHome.equals(HearthRange.homeOf(other))
                && looker.level() == other.level();
        PlayerLookRules.Gains gains = PlayerLookRules.gains(looker.distanceTo(other), side(looker, mine, myLevels),
                side(other, theirs, theirLevels), ProficiencyConfig.camaraderieBonus(), ProficiencyConfig.mentorBonus(),
                mine.bonus(Skill.SOCIAL), sameHearth, ProficiencyConfig.restedCapFactor() > 0);
        out.putInt(FLAGS, gains.flags());
        out.putFloat(EXTRA, (float) gains.companyExtra());
        out.putFloat(RADIUS, (float) gains.companyRadius());
        out.putIntArray(MENTORS, ordinals(gains.mentors()));
        out.putIntArray(STUDENTS, ordinals(gains.students()));
    }

    private static int[] levels(PlayerSkills skills) {
        int[] out = new int[Skill.VALUES.length];
        for (Skill skill : Skill.VALUES) {
            out[skill.ordinal()] = ProficiencyConfig.enabled(skill) ? skills.level(skill) : 0;
        }
        return out;
    }

    private static PlayerLookRules.Side side(ServerPlayer player, PlayerSkills skills, int[] levels) {
        SocialMath.Talents talents = CompanyBonus.talents(player);
        double radius = SocialMath.radius(ProficiencyConfig.companyRadius(), talents);
        return new PlayerLookRules.Side(levels, radius,
                RestedMath.teachRadius(radius, TalentService.rank(skills, Skill.SOCIAL, "teach_radius")),
                SocialMath.mentorGap(ProficiencyConfig.mentorGap(), talents), talents,
                TalentService.rank(skills, Skill.SOCIAL, "teaching"), skills.level(Skill.SOCIAL));
    }

    private static int[] ordinals(List<Skill> skills) {
        int[] out = new int[skills.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = skills.get(i).ordinal();
        }
        return out;
    }
}
