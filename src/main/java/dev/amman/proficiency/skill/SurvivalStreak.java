package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Staying alive pays. Every step of active play alive adds a stack, every stack adds to all skill
 * XP, and a death wipes the lot. The death cost is what you stop earning, not what you lose, which
 * suits a keep-inventory server. The numbers live on {@link PlayerSkills}; this reads the config
 * and does the talking.
 */
public final class SurvivalStreak {

    /** A stack count worth a chat line. Every tenth, plus the cap if it is not one. */
    private static final int MILESTONE_EVERY = 10;

    private SurvivalStreak() {
    }

    public static int stacks(PlayerSkills skills) {
        return skills.streakStacks(ProficiencyConfig.streakStepTicks(), ProficiencyConfig.streakMaxStacks());
    }

    public static double multiplier(PlayerSkills skills) {
        return 1.0 + stacks(skills) * ProficiencyConfig.streakXpPerStack();
    }

    /** Whole percent, for chat and the HUD. */
    public static int percent(int stacks) {
        return (int) Math.round(stacks * ProficiencyConfig.streakXpPerStack() * 100.0);
    }

    /** False when the config switches the streak off (a cap of 0 stacks). */
    public static boolean enabled() {
        return ProficiencyConfig.streakStepTicks() > 0 && ProficiencyConfig.streakMaxStacks() > 0;
    }

    public static boolean atCap(PlayerSkills skills) {
        return enabled() && stacks(skills) >= ProficiencyConfig.streakMaxStacks();
    }

    /** The cap in whole percent, the far end of the panel's second bar. */
    public static int capPercent() {
        return percent(Math.max(0, ProficiencyConfig.streakMaxStacks()));
    }

    /**
     * Fraction of the current step done, 0 to 1, for the panel's first bar. Full at the cap, where
     * there is no next step; empty when the streak is off.
     */
    public static float stepProgress(PlayerSkills skills) {
        if (!enabled()) {
            return 0f;
        }
        if (atCap(skills)) {
            return 1f;
        }
        long step = ProficiencyConfig.streakStepTicks();
        return (float) ((Math.max(0L, skills.streakTicks()) % step) / (double) step);
    }

    /** Fraction of the way from 0 stacks to the cap, 0 to 1, for the panel's second bar. */
    public static float capFill(PlayerSkills skills) {
        if (!enabled()) {
            return 0f;
        }
        return Math.min(1f, stacks(skills) / (float) ProficiencyConfig.streakMaxStacks());
    }

    /**
     * Whole minutes of active play until the next stack, rounded up; 0 at the cap or when the
     * streak is switched off. For the panel, so a player at 0 stacks can see the thing exists.
     */
    public static int minutesToNext(PlayerSkills skills) {
        long step = ProficiencyConfig.streakStepTicks();
        int max = ProficiencyConfig.streakMaxStacks();
        if (step <= 0 || max <= 0 || stacks(skills) >= max) {
            return 0;
        }
        long left = step - (skills.streakTicks() % step);
        return (int) ((left + 1199) / 1200);
    }

    /** Once a second from the server tick, for every living player. */
    public static void tick(ServerPlayer player, long elapsed) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int max = ProficiencyConfig.streakMaxStacks();
        if (!skills.tickStreak(player.level().getGameTime(), elapsed,
                ProficiencyConfig.streakActiveWindowTicks(), ProficiencyConfig.streakStepTicks(), max)) {
            return;
        }
        int stacks = stacks(skills);
        if (stacks % MILESTONE_EVERY == 0 || stacks == max) {
            player.sendSystemMessage(Component.translatable("proficiency.streak.milestone",
                    stacks, percent(stacks)).withStyle(ChatFormatting.GOLD));
            player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.5f);
        }
    }

    /** Returns the stacks a death took. */
    public static int onDeath(PlayerSkills skills) {
        return skills.loseStreak(ProficiencyConfig.streakStepTicks(), ProficiencyConfig.streakMaxStacks());
    }
}
