package dev.amman.proficiency.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Client-side display settings: the discovery banners, the XP feed and the skill HUD. It is its own file
 * ({@code proficiency-client.toml}) and its own spec because the server never reads any of it, and
 * each player picks their own. Registered on the client dist only, so a dedicated server never
 * loads this class.
 *
 * <p>Every value is read through a getter that falls back to the default while the spec is not
 * loaded (the HUD can render a frame before the config finishes loading), as in
 * {@link ProficiencyConfig}. Each entry names its own translation key, which the in-game screen
 * ({@code client.ClientConfigScreen}) uses for its labels and tooltips.
 */
public final class ProficiencyClientConfig {

    public static final ForgeConfigSpec SPEC;

    private static final String LANG = "proficiency.configuration.";

    /** The banner's old fixed place: a seventh of the way down the screen. */
    public static final double DEFAULT_BANNER_Y = 1.0 / 7.0;
    /** The feed's old fixed place: a quarter of the way down. */
    public static final double DEFAULT_FEED_Y = 0.25;
    public static final int DEFAULT_FEED_X = 4;
    public static final int DEFAULT_FEED_LINES = 14;
    public static final int DEFAULT_FEED_SECONDS = 8;

    private static final ForgeConfigSpec.BooleanValue BANNERS_ENABLED;
    private static final ForgeConfigSpec.BooleanValue BANNERS_DISCOVERIES;
    private static final ForgeConfigSpec.BooleanValue BANNERS_FIRST_TIME;
    private static final ForgeConfigSpec.BooleanValue BANNERS_ENTRY;
    private static final ForgeConfigSpec.DoubleValue BANNERS_SCALE;
    private static final ForgeConfigSpec.DoubleValue BANNERS_Y;
    private static final ForgeConfigSpec.BooleanValue BANNERS_SOUND;
    private static final ForgeConfigSpec.BooleanValue BANNERS_REVEAL;
    private static final ForgeConfigSpec.BooleanValue TOOLTIP_SKILL_INFO;
    private static final ForgeConfigSpec.IntValue FEED_X;
    private static final ForgeConfigSpec.DoubleValue FEED_Y;
    private static final ForgeConfigSpec.IntValue FEED_MAX_LINES;
    private static final ForgeConfigSpec.BooleanValue FEED_SHOW_FACTORS;
    private static final ForgeConfigSpec.IntValue FEED_VISIBLE_SECONDS;
    private static final ForgeConfigSpec.BooleanValue HUD_XP_DOTS;
    private static final ForgeConfigSpec.BooleanValue HUD_LEVEL_UP_FX;
    private static final ForgeConfigSpec.BooleanValue HUD_ABILITY_FX;
    private static final ForgeConfigSpec.BooleanValue HUD_STREAK_FX;
    private static final ForgeConfigSpec.BooleanValue HUD_DEATH_RECAP;
    private static final ForgeConfigSpec.BooleanValue SCREENS_UNLOCK_FX;
    private static final ForgeConfigSpec.BooleanValue SCREENS_ACTIVITY;
    private static final ForgeConfigSpec.BooleanValue PROC_FX_ENABLED;
    private static final ForgeConfigSpec.BooleanValue UI_SKILL_ICONS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("The zone-name banners: a new biome, dimension or structure, a first-time kind, "
                + "and the small re-entry banner.")
                .translation(LANG + "banners").push("banners");
        BANNERS_ENABLED = b.comment("Master switch. Off hides every banner; the XP is still paid.")
                .translation(LANG + "banners.enabled").define("enabled", true);
        BANNERS_DISCOVERIES = b.comment("Banners for a new biome, dimension or structure.")
                .translation(LANG + "banners.discoveries").define("discoveries", true);
        BANNERS_FIRST_TIME = b.comment("Banners for a first-time kind (a new ore, mob or crop).")
                .translation(LANG + "banners.firstTime").define("firstTime", true);
        BANNERS_ENTRY = b.comment("The small banner, with no XP, when you step into a structure you know.")
                .translation(LANG + "banners.entry").define("entry", true);
        BANNERS_SCALE = b.comment("Size of every banner. 1.0 is the normal size.")
                .translation(LANG + "banners.scale").defineInRange("scale", 1.0, 0.5, 1.5);
        BANNERS_Y = b.comment("Top of the banner as a fraction of the screen height. 0.143 is the default.")
                .translation(LANG + "banners.yFraction")
                .defineInRange("yFraction", DEFAULT_BANNER_Y, 0.0, 0.6);
        BANNERS_SOUND = b.comment("Play a soft toast sound when a discovery banner appears. "
                + "The re-entry banner is always silent.")
                .translation(LANG + "banners.sound").define("sound", false);
        BANNERS_REVEAL = b.comment("The banner title appears letter by letter, tinted by dimension, "
                + "and a structure banner shows a small icon. Off: the whole title fades in at once.")
                .translation(LANG + "banners.reveal").define("reveal", true);
        b.pop();

        b.comment("The XP feed (/skills xpfeed): every gain as it lands.")
                .translation(LANG + "feed").push("feed");
        FEED_X = b.comment("Distance from the left edge, in GUI pixels.")
                .translation(LANG + "feed.x").defineInRange("x", DEFAULT_FEED_X, 0, 1000);
        FEED_Y = b.comment("Top of the feed as a fraction of the screen height.")
                .translation(LANG + "feed.yFraction")
                .defineInRange("yFraction", DEFAULT_FEED_Y, 0.0, 0.8);
        FEED_MAX_LINES = b.comment("Most lines kept on screen; older ones drop off.")
                .translation(LANG + "feed.maxLines")
                .defineInRange("maxLines", DEFAULT_FEED_LINES, 4, 30);
        FEED_SHOW_FACTORS = b.comment("Show the small line naming each multiplier under a gain.")
                .translation(LANG + "feed.showFactors").define("showFactors", true);
        FEED_VISIBLE_SECONDS = b.comment("Seconds a line stays before it fades.")
                .translation(LANG + "feed.visibleSeconds")
                .defineInRange("visibleSeconds", DEFAULT_FEED_SECONDS, 2, 60);
        b.pop();

        b.comment("The skill line and bar above the hotbar.")
                .translation(LANG + "hud").push("hud");
        HUD_XP_DOTS = b.comment("On a gain, small dots fly in from the right edge and fill the bar. "
                + "Off: the bar jumps straight to the new value.")
                .translation(LANG + "hud.xpDots").define("xpDots", true);
        HUD_LEVEL_UP_FX = b.comment("On a level-up, the bar flashes, the number rolls over and a ring "
                + "expands from the bar. Every tenth level it is bigger and gold.")
                .translation(LANG + "hud.levelUpFx").define("levelUpFx", true);
        HUD_ABILITY_FX = b.comment("While an ability runs, a soft glow on the screen edge and a ring "
                + "round the crosshair that drains. While it cools down, a sweep over the held item.")
                .translation(LANG + "hud.abilityFx").define("abilityFx", true);
        HUD_STREAK_FX = b.comment("The streak badge fills towards the next +1%, flares at +10, +25 "
                + "and +50%, and breaks apart when a death takes the streak.")
                .translation(LANG + "hud.streakFx").define("streakFx", true);
        HUD_DEATH_RECAP = b.comment("After a respawn, a small panel shows the XP bars the death wiped "
                + "and the streak it took.")
                .translation(LANG + "hud.deathRecap").define("deathRecap", true);
        b.pop();

        b.comment("The skill lines added to item tooltips.")
                .translation(LANG + "tooltip").push("tooltip");
        TOOLTIP_SKILL_INFO = b.comment("A tool, weapon or other item that trains a skill shows the "
                + "skill, your passive, the signature proc and the ability in its tooltip. "
                + "The details sit behind Shift.")
                .translation(LANG + "tooltip.skillInfo").define("skillInfo", true);
        b.pop();

        b.comment("The skills panel and the talent trees.")
                .translation(LANG + "screens").push("screens");
        SCREENS_UNLOCK_FX = b.comment("In a talent tree, a new rank sweeps the node full, the lines it "
                + "opens light up towards the next nodes, a finished capstone shimmers and a synergy "
                + "pulses once when it switches on.")
                .translation(LANG + "screens.unlockFx").define("unlockFx", true);
        SCREENS_ACTIVITY = b.comment("In the skills panel, a skill that gained XP in the last ten "
                + "minutes glows faintly and shows a small graph of its XP over the last two hours.")
                .translation(LANG + "screens.activity").define("activity", true);
        b.pop();

        b.comment("The particles that play when a skill's signature proc lands.")
                .translation(LANG + "procFx").push("procFx");
        PROC_FX_ENABLED = b.comment("Each skill's proc plays its own few particles, your own and other "
                + "players'. Off: no proc particles. The sound and the action-bar name stay. "
                + "The Particles video setting thins them out too.")
                .translation(LANG + "procFx.enabled").define("enabled", true);
        b.pop();

        b.comment("Skill icons across the interface.")
                .translation(LANG + "ui").push("ui");
        UI_SKILL_ICONS = b.comment("A small pixel icon of each skill next to its name: the HUD line, "
                + "the skills panel, the tree header, the XP feed, the banners, the death recap and "
                + "the ability wheel. Off: names only, and the wheel shows items.")
                .translation(LANG + "ui.skillIcons").define("skillIcons", true);
        b.pop();

        SPEC = b.build();
    }

    private ProficiencyClientConfig() {
    }

    /**
     * One row of the in-game settings screen. {@code min}/{@code max} matter only for numbers;
     * {@code section} is the header the row sits under.
     */
    public record Entry(String section, String key, ForgeConfigSpec.ConfigValue<?> value, double min,
            double max) {
    }

    /** Every entry, in file order, for {@code client.ClientConfigScreen}. */
    public static java.util.List<Entry> entries() {
        return java.util.List.of(
                new Entry("banners", "banners.enabled", BANNERS_ENABLED, 0, 0),
                new Entry("banners", "banners.discoveries", BANNERS_DISCOVERIES, 0, 0),
                new Entry("banners", "banners.firstTime", BANNERS_FIRST_TIME, 0, 0),
                new Entry("banners", "banners.entry", BANNERS_ENTRY, 0, 0),
                new Entry("banners", "banners.scale", BANNERS_SCALE, 0.5, 1.5),
                new Entry("banners", "banners.yFraction", BANNERS_Y, 0.0, 0.6),
                new Entry("banners", "banners.sound", BANNERS_SOUND, 0, 0),
                new Entry("banners", "banners.reveal", BANNERS_REVEAL, 0, 0),
                new Entry("feed", "feed.x", FEED_X, 0, 1000),
                new Entry("feed", "feed.yFraction", FEED_Y, 0.0, 0.8),
                new Entry("feed", "feed.maxLines", FEED_MAX_LINES, 4, 30),
                new Entry("feed", "feed.showFactors", FEED_SHOW_FACTORS, 0, 0),
                new Entry("feed", "feed.visibleSeconds", FEED_VISIBLE_SECONDS, 2, 60),
                new Entry("hud", "hud.xpDots", HUD_XP_DOTS, 0, 0),
                new Entry("hud", "hud.levelUpFx", HUD_LEVEL_UP_FX, 0, 0),
                new Entry("hud", "hud.abilityFx", HUD_ABILITY_FX, 0, 0),
                new Entry("hud", "hud.streakFx", HUD_STREAK_FX, 0, 0),
                new Entry("hud", "hud.deathRecap", HUD_DEATH_RECAP, 0, 0),
                new Entry("tooltip", "tooltip.skillInfo", TOOLTIP_SKILL_INFO, 0, 0),
                new Entry("screens", "screens.unlockFx", SCREENS_UNLOCK_FX, 0, 0),
                new Entry("screens", "screens.activity", SCREENS_ACTIVITY, 0, 0),
                new Entry("procFx", "procFx.enabled", PROC_FX_ENABLED, 0, 0),
                new Entry("ui", "ui.skillIcons", UI_SKILL_ICONS, 0, 0));
    }

    private static boolean flag(ForgeConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    private static double number(ForgeConfigSpec.DoubleValue value, double fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    private static int whole(ForgeConfigSpec.IntValue value, int fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static boolean bannersEnabled() {
        return flag(BANNERS_ENABLED, true);
    }

    public static boolean bannerDiscoveries() {
        return flag(BANNERS_DISCOVERIES, true);
    }

    public static boolean bannerFirstTime() {
        return flag(BANNERS_FIRST_TIME, true);
    }

    public static boolean bannerEntry() {
        return flag(BANNERS_ENTRY, true);
    }

    public static float bannerScale() {
        return (float) number(BANNERS_SCALE, 1.0);
    }

    public static float bannerYFraction() {
        return (float) number(BANNERS_Y, DEFAULT_BANNER_Y);
    }

    public static boolean bannerSound() {
        return flag(BANNERS_SOUND, false);
    }

    public static boolean bannerReveal() {
        return flag(BANNERS_REVEAL, true);
    }

    public static boolean tooltipSkillInfo() {
        return flag(TOOLTIP_SKILL_INFO, true);
    }

    public static int feedX() {
        return whole(FEED_X, DEFAULT_FEED_X);
    }

    public static float feedYFraction() {
        return (float) number(FEED_Y, DEFAULT_FEED_Y);
    }

    public static int feedMaxLines() {
        return whole(FEED_MAX_LINES, DEFAULT_FEED_LINES);
    }

    public static boolean feedShowFactors() {
        return flag(FEED_SHOW_FACTORS, true);
    }

    public static long feedVisibleMs() {
        return whole(FEED_VISIBLE_SECONDS, DEFAULT_FEED_SECONDS) * 1000L;
    }

    public static boolean hudXpDots() {
        return flag(HUD_XP_DOTS, true);
    }

    public static boolean hudLevelUpFx() {
        return flag(HUD_LEVEL_UP_FX, true);
    }

    public static boolean hudAbilityFx() {
        return flag(HUD_ABILITY_FX, true);
    }

    public static boolean hudStreakFx() {
        return flag(HUD_STREAK_FX, true);
    }

    public static boolean hudDeathRecap() {
        return flag(HUD_DEATH_RECAP, true);
    }

    public static boolean screensUnlockFx() {
        return flag(SCREENS_UNLOCK_FX, true);
    }

    public static boolean screensActivity() {
        return flag(SCREENS_ACTIVITY, true);
    }

    public static boolean procFxEnabled() {
        return flag(PROC_FX_ENABLED, true);
    }

    public static boolean uiSkillIcons() {
        return flag(UI_SKILL_ICONS, true);
    }
}
