package dev.amman.proficiency.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side display settings: the discovery banners, the XP feed and the skill HUD. It is its own file
 * ({@code proficiency-client.toml}) and its own spec because the server never reads any of it, and
 * each player picks their own. Registered on the client dist only, so a dedicated server never
 * loads this class.
 *
 * <p>Every value is read through a getter that falls back to the default while the spec is not
 * loaded (the HUD can render a frame before the config finishes loading), as in
 * {@link ProficiencyConfig}. Each entry names its own translation key: without one, NeoForge's
 * config screen would use the bare entry name, and "enabled" would clash with other sections.
 */
public final class ProficiencyClientConfig {

    public static final ModConfigSpec SPEC;

    private static final String LANG = "proficiency.configuration.";

    /** The banner's old fixed place: a seventh of the way down the screen. */
    public static final double DEFAULT_BANNER_Y = 1.0 / 7.0;
    /** The feed's old fixed place: a quarter of the way down. */
    public static final double DEFAULT_FEED_Y = 0.25;
    public static final int DEFAULT_FEED_X = 4;
    public static final int DEFAULT_FEED_LINES = 14;
    public static final int DEFAULT_FEED_SECONDS = 8;

    private static final ModConfigSpec.BooleanValue BANNERS_ENABLED;
    private static final ModConfigSpec.BooleanValue BANNERS_DISCOVERIES;
    private static final ModConfigSpec.BooleanValue BANNERS_FIRST_TIME;
    private static final ModConfigSpec.BooleanValue BANNERS_ENTRY;
    private static final ModConfigSpec.DoubleValue BANNERS_SCALE;
    private static final ModConfigSpec.DoubleValue BANNERS_Y;
    private static final ModConfigSpec.BooleanValue BANNERS_SOUND;
    private static final ModConfigSpec.BooleanValue BANNERS_REVEAL;
    private static final ModConfigSpec.BooleanValue TOOLTIP_SKILL_INFO;
    private static final ModConfigSpec.BooleanValue TOOLTIP_ALWAYS_DETAILED;
    private static final ModConfigSpec.IntValue FEED_X;
    private static final ModConfigSpec.DoubleValue FEED_Y;
    private static final ModConfigSpec.IntValue FEED_MAX_LINES;
    private static final ModConfigSpec.BooleanValue FEED_SHOW_FACTORS;
    private static final ModConfigSpec.IntValue FEED_VISIBLE_SECONDS;
    private static final ModConfigSpec.BooleanValue HUD_XP_DOTS;
    private static final ModConfigSpec.BooleanValue HUD_LEVEL_UP_FX;
    private static final ModConfigSpec.BooleanValue HUD_ABILITY_FX;
    private static final ModConfigSpec.BooleanValue HUD_STREAK_FX;
    private static final ModConfigSpec.BooleanValue HUD_SHOW_TEMPO;
    private static final ModConfigSpec.BooleanValue HUD_DEATH_RECAP;
    private static final ModConfigSpec.BooleanValue SCREENS_UNLOCK_FX;
    private static final ModConfigSpec.BooleanValue SCREENS_ACTIVITY;
    private static final ModConfigSpec.BooleanValue PROC_FX_ENABLED;
    private static final ModConfigSpec.BooleanValue UI_SKILL_ICONS;
    private static final ModConfigSpec.EnumValue<dev.amman.proficiency.client.UiStyle> UI_STYLE;
    private static final ModConfigSpec.BooleanValue UI_STYLE_MIGRATED;
    private static final ModConfigSpec.ConfigValue<String> UI_WAYWHEEL_PINS;
    private static final ModConfigSpec.BooleanValue UI_JUICY_FEEDBACK;
    private static final ModConfigSpec.BooleanValue HEARTH_TRACKER;
    private static final ModConfigSpec.DoubleValue HEARTH_TRACKER_Y;
    private static final ModConfigSpec.BooleanValue HEARTH_SHOW_RANGE;
    private static final ModConfigSpec.BooleanValue HEARTH_SIEGE_PANEL;
    private static final ModConfigSpec.DoubleValue HEARTH_SIEGE_PANEL_Y;

    /** The tracker's default height: the middle of the right edge. */
    public static final double DEFAULT_TRACKER_Y = 0.5;
    public static final double DEFAULT_SIEGE_PANEL_Y = 0.06;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("The zone-name banners: a new biome, dimension or structure, a first-time kind, "
                + "and the small re-entry banner.")
                .translation(LANG + "banners").push("banners");
        BANNERS_ENABLED = b.comment("Master switch. Off hides every banner; the XP is still paid.")
                .translation(LANG + "banners.enabled").define("enabled", true);
        BANNERS_DISCOVERIES = b.comment("Banners for a new biome, dimension or structure.")
                .translation(LANG + "banners.discoveries").define("discoveries", true);
        BANNERS_FIRST_TIME = b.comment("Banners for a first-time kind (a new ore, mob or crop, your first Hearth or a "
                + "Homebuilder milestone). Only the master switch hides a new Hearth stage.")
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
        HUD_SHOW_TEMPO = b.comment("Flames on the skill bar and the multiplier at its end show your Tempo chain: "
                + "they grow hotter with it, flare on a step up and die down to smoke when the window lapses.")
                .translation(LANG + "hud.showTempo").define("showTempo", true);
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
        TOOLTIP_ALWAYS_DETAILED = b.comment("Every tooltip of the mod shows its detailed layer at once "
                + "and the Shift hint goes away. Off: short tooltips, details on Shift.")
                .translation(LANG + "tooltip.alwaysDetailed").define("alwaysDetailed", false);
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
        UI_JUICY_FEEDBACK = b.comment("Click feedback in the screens: a node you cannot take wiggles left and "
                + "right with a red flash and a dull thunk, a click being answered shakes with a rising tick, "
                + "and a success pops, bursts into pixel sparks, rings and chimes. GUI only, no world "
                + "particles. Off: the plain click sound. Also quiet when the Distortion Effects video "
                + "setting is 0%.")
                .translation(LANG + "ui.juicyFeedback").define("juicyFeedback", true);
        UI_STYLE = b.comment("The look of the skills screens. CLASSIC: the panels as they were. MODERN: the "
                + "Skillstone, a carved slab with nine category runes around a fire well, each lit by your "
                + "progress, and a stone face per skill. Switch it here or from the slab's corner rune; no "
                + "restart. Pages not yet carved open their classic screen.")
                .translation(LANG + "ui.style").defineEnum("style", dev.amman.proficiency.client.UiStyle.MODERN);
        UI_STYLE_MIGRATED = b.comment("Internal. Set once the 1.33.0 change of the default style to MODERN has been applied "
                + "to an older file; after that your own choice of ui.style is kept.")
                .define("styleMigrated", false);
        UI_WAYWHEEL_PINS = b.comment("Skills pinned beside the hotbar under the MODERN style, as a comma list of skill "
                + "ids (at most 3, for example mining,swords). Right-click a skill on the Waywheel to pin or unpin it.")
                .translation(LANG + "ui.waywheelPins").define("waywheelPins", "");
        b.pop();

        b.comment("The Hearth on your screen: the tracker, the range ring and the siege panel.")
                .translation(LANG + "hearth").push("hearth");
        HEARTH_TRACKER = b.comment("A narrow column on the right edge, at home only: one row per charged "
                + "pool with its tier and bar, and Comfort under them. Off by default.")
                .translation(LANG + "hearth.tracker").define("tracker", false);
        HEARTH_TRACKER_Y = b.comment("Middle of the tracker as a fraction of the screen height.")
                .translation(LANG + "hearth.trackerY")
                .defineInRange("trackerY", DEFAULT_TRACKER_Y, 0.1, 0.9);
        HEARTH_SHOW_RANGE = b.comment("Show the Hearth's range as a ring of particles when you look at a "
                + "Hearth or hold a campfire or flint and steel near one.")
                .translation(LANG + "hearth.showRange").define("showRange", true);
        HEARTH_SIEGE_PANEL = b.comment("The siege panel at the top left: the countdown, the waves and the "
                + "outcome of a siege at your Hearth.")
                .translation(LANG + "hearth.siegePanel").define("siegePanel", true);
        HEARTH_SIEGE_PANEL_Y = b.comment("Top of the siege panel as a fraction of the screen height. A minimap in the "
                + "top left corner (Xaero's) covers 0.06: about 0.32 puts the panel under it.")
                .translation(LANG + "hearth.siegePanelY")
                .defineInRange("siegePanelY", DEFAULT_SIEGE_PANEL_Y, 0.0, 0.9);
        b.pop();

        SPEC = b.build();
    }

    private ProficiencyClientConfig() {
    }

    private static boolean flag(ModConfigSpec.BooleanValue value, boolean fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    private static double number(ModConfigSpec.DoubleValue value, double fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    private static int whole(ModConfigSpec.IntValue value, int fallback) {
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

    public static boolean tooltipAlwaysDetailed() {
        return flag(TOOLTIP_ALWAYS_DETAILED, false);
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

    public static boolean hudShowTempo() {
        return flag(HUD_SHOW_TEMPO, true);
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

    public static boolean uiJuicyFeedback() {
        return flag(UI_JUICY_FEEDBACK, true);
    }

    public static dev.amman.proficiency.client.UiStyle uiStyle() {
        if (!SPEC.isLoaded()) {
            return dev.amman.proficiency.client.UiStyle.CLASSIC;
        }
        migrateUiStyle();
        return UI_STYLE.get();
    }

    /**
     * One time: a file written before MODERN became the default holds CLASSIC as the old default, so it moves to
     * MODERN. Run from the first client tick (the title screen), before any config screen can write
     * {@code ui.style}: a stock config screen sets the value without {@code styleMigrated}, and a CLASSIC picked
     * there must not be moved by a migration that runs later. No-op once done or while the file is not loaded.
     */
    public static void migrateUiStyle() {
        if (styleChecked || !SPEC.isLoaded()) {
            return;
        }
        styleChecked = true;
        if (!UI_STYLE_MIGRATED.get()) {
            UI_STYLE_MIGRATED.set(true);
            UI_STYLE.set(dev.amman.proficiency.client.UiStyle.MODERN);
            try {
                SPEC.save();
            } catch (RuntimeException e) {
                org.slf4j.LoggerFactory.getLogger("proficiency").warn("Could not save ui.style migration", e);
            }
        }
    }

    /** True once {@link #migrateUiStyle} has looked at the loaded file this session. */
    private static boolean styleChecked;

    /** Sets {@code ui.style} and writes the file: the slab's toggle rune. */
    public static void setUiStyle(dev.amman.proficiency.client.UiStyle style) {
        if (SPEC.isLoaded()) {
            UI_STYLE.set(style);
            // A hand pick is never a default left in an old file: the one-time migration must not touch it.
            UI_STYLE_MIGRATED.set(true);
            try {
                SPEC.save();
            } catch (RuntimeException e) {
                // A read-only or locked config file: the style still switches for this session.
                org.slf4j.LoggerFactory.getLogger("proficiency").warn("Could not save ui.style", e);
            }
        }
    }

    /** The Waywheel's pinned skills, a comma list of skill ids (WaywheelMath.pins cleans it). */
    public static String uiWaywheelPins() {
        if (!SPEC.isLoaded()) {
            return sessionPins;
        }
        String value = UI_WAYWHEEL_PINS.get();
        return value == null ? "" : value;
    }

    /** Pins only for this session when the file is not loaded (tests, an early frame). */
    private static String sessionPins = "";

    /** Sets {@code ui.waywheelPins} and writes the file: a right-click on the Waywheel. */
    public static void setUiWaywheelPins(String csv) {
        if (!SPEC.isLoaded()) {
            sessionPins = csv;
            return;
        }
        UI_WAYWHEEL_PINS.set(csv);
        try {
            SPEC.save();
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("proficiency").warn("Could not save ui.waywheelPins", e);
        }
    }

    public static boolean uiSkillIcons() {
        return flag(UI_SKILL_ICONS, true);
    }

    public static boolean hearthTracker() {
        return flag(HEARTH_TRACKER, false);
    }

    public static float hearthTrackerY() {
        return (float) number(HEARTH_TRACKER_Y, DEFAULT_TRACKER_Y);
    }

    public static boolean hearthShowRange() {
        return flag(HEARTH_SHOW_RANGE, true);
    }

    public static boolean hearthSiegePanel() {
        return flag(HEARTH_SIEGE_PANEL, true);
    }

    public static float hearthSiegePanelY() {
        return (float) number(HEARTH_SIEGE_PANEL_Y, DEFAULT_SIEGE_PANEL_Y);
    }
}
