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
    private static final ForgeConfigSpec.IntValue FEED_X;
    private static final ForgeConfigSpec.DoubleValue FEED_Y;
    private static final ForgeConfigSpec.IntValue FEED_MAX_LINES;
    private static final ForgeConfigSpec.BooleanValue FEED_SHOW_FACTORS;
    private static final ForgeConfigSpec.IntValue FEED_VISIBLE_SECONDS;
    private static final ForgeConfigSpec.BooleanValue HUD_XP_DOTS;

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
                new Entry("feed", "feed.x", FEED_X, 0, 1000),
                new Entry("feed", "feed.yFraction", FEED_Y, 0.0, 0.8),
                new Entry("feed", "feed.maxLines", FEED_MAX_LINES, 4, 30),
                new Entry("feed", "feed.showFactors", FEED_SHOW_FACTORS, 0, 0),
                new Entry("feed", "feed.visibleSeconds", FEED_VISIBLE_SECONDS, 2, 60),
                new Entry("hud", "hud.xpDots", HUD_XP_DOTS, 0, 0));
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
}
