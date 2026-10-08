package dev.amman.proficiency.client;

/**
 * One place that forgets every HUD effect's state. The loaders call it from their client logout
 * hook, and {@link SkillHud} calls it if a frame is drawn with no player, so a recap, a ring or a
 * remembered level never reaches the next world or server.
 */
public final class HudState {

    private HudState() {
    }

    public static void reset() {
        StreakBadge.reset();
        LevelUpFx.reset();
        DeathRecapHud.reset();
        AbilityFx.reset();
        SkillHud.reset();
        SkillActivity.reset();
        ClientSync.reset();
        SkillTooltip.reset();
    }
}
