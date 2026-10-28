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
    private static final ForgeConfigSpec.BooleanValue TOOLTIP_ALWAYS_DETAILED;
    private static final ForgeConfigSpec.IntValue FEED_X;
    private static final ForgeConfigSpec.DoubleValue FEED_Y;
    private static final ForgeConfigSpec.IntValue FEED_MAX_LINES;
    private static final ForgeConfigSpec.BooleanValue FEED_SHOW_FACTORS;
    private static final ForgeConfigSpec.IntValue FEED_VISIBLE_SECONDS;
    private static final ForgeConfigSpec.BooleanValue HUD_XP_DOTS;
    private static final ForgeConfigSpec.BooleanValue HUD_LEVEL_UP_FX;
    private static final ForgeConfigSpec.BooleanValue HUD_ABILITY_FX;
    private static final ForgeConfigSpec.BooleanValue HUD_STREAK_FX;
    private static final ForgeConfigSpec.BooleanValue HUD_SHOW_TEMPO;
    private static final ForgeConfigSpec.BooleanValue HUD_DEATH_RECAP;
    private static final ForgeConfigSpec.BooleanValue SCREENS_UNLOCK_FX;
    private static final ForgeConfigSpec.BooleanValue SCREENS_ACTIVITY;
    private static final ForgeConfigSpec.BooleanValue PROC_FX_ENABLED;
    private static final ForgeConfigSpec.BooleanValue UI_SKILL_ICONS;
    private static final ForgeConfigSpec.EnumValue<dev.amman.proficiency.client.UiStyle> UI_STYLE;
    private static final ForgeConfigSpec.BooleanValue UI_STYLE_MIGRATED;
    private static final ForgeConfigSpec.ConfigValue<String> UI_WAYWHEEL_PINS;
    private static final ForgeConfigSpec.BooleanValue UI_BONUS_FLIGHT;
    private static final ForgeConfigSpec.BooleanValue UI_POINT_COIN;
    private static final ForgeConfigSpec.BooleanValue UI_READY_TOASTS;
    private static final ForgeConfigSpec.BooleanValue UI_UNLOCK_SLAM;
    private static final ForgeConfigSpec.BooleanValue UI_RESTED_POUR;
    private static final ForgeConfigSpec.BooleanValue UI_MATERIAL_TOOLTIPS;
    private static final ForgeConfigSpec.BooleanValue UI_JUICY_FEEDBACK;
    private static final ForgeConfigSpec.BooleanValue UI_RECORD_TOASTS;
    private static final ForgeConfigSpec.BooleanValue UI_MILESTONE_CEREMONIES;
    private static final ForgeConfigSpec.BooleanValue UI_ABILITY_GLINT;
    private static final ForgeConfigSpec.BooleanValue UI_ADVANCEMENT_BURST;
    private static final ForgeConfigSpec.BooleanValue UI_SYNERGY_SLAM;
    private static final ForgeConfigSpec.BooleanValue UI_HAUL_CARD;
    private static final ForgeConfigSpec.BooleanValue UI_COOP_STREAMS;
    private static final ForgeConfigSpec.BooleanValue UI_TEMPO_CRIT_FLARE;
    private static final ForgeConfigSpec.BooleanValue UI_HUD_STACK;
    private static final ForgeConfigSpec.DoubleValue UI_HUD_STACK_LINGER;
    private static final ForgeConfigSpec.BooleanValue HEARTH_TRACKER;
    private static final ForgeConfigSpec.DoubleValue HEARTH_TRACKER_Y;
    private static final ForgeConfigSpec.BooleanValue HEARTH_SHOW_RANGE;
    private static final ForgeConfigSpec.BooleanValue HEARTH_SIEGE_PANEL;
    private static final ForgeConfigSpec.DoubleValue HEARTH_SIEGE_PANEL_Y;

    /** The tracker's default height: the middle of the right edge. */
    public static final double DEFAULT_TRACKER_Y = 0.5;
    public static final double DEFAULT_SIEGE_PANEL_Y = 0.06;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

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
                + "The details sit behind Shift (or see alwaysDetailed).")
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
        UI_RECORD_TOASTS = b.comment("A toast with a chime and a burst when you set a new personal best in a skill "
                + "(XP per minute, Tempo chain, biggest grant). At most one per skill every 30 seconds. "
                + "Off: no toast; the Records line on the skill's screen stays.")
                .translation(LANG + "ui.recordToasts").define("recordToasts", true);
        UI_MILESTONE_CEREMONIES = b.comment("Reaching level 10, 25, 50, 75 or 100 in a skill crowns the normal level-up: a "
                + "banner naming the milestone and what it opened, a firework in the skill's colour over you "
                + "and a rising fanfare. Each tier is bigger than the last; level 100 is the gold Mastered "
                + "ceremony, once per skill. Off: the normal level-up only. The Distortion Effects video "
                + "setting at 0% keeps the flashes and sounds but stops the banner's movement.")
                .translation(LANG + "ui.milestoneCeremonies").define("milestoneCeremonies", true);
        UI_ABILITY_GLINT = b.comment("When an ability comes off cooldown its icon pops, a glint sweeps across it and a soft "
                + "chime plays: the Waywheel belt token under MODERN, the hotbar slot of the held item under CLASSIC. "
                + "Nothing for abilities that were already ready when you joined. Needs ui.juicyFeedback.")
                .translation(LANG + "ui.abilityGlint").define("abilityGlint", true);
        UI_ADVANCEMENT_BURST = b.comment("Advancement toasts (and FTB Quests toasts) get the success treatment as they "
                + "slide in: sparks, a shine across the toast and a ring; a challenge adds a second ring, more sparks "
                + "and a glow round the frame. Needs ui.juicyFeedback.")
                .translation(LANG + "ui.advancementBurst").define("advancementBurst", true);
        UI_SYNERGY_SLAM = b.comment("When you wake a synergy, its skill icons fly in from the screen sides and slam "
                + "together at the centre with a flash, sparks and a short shake; the synergy's chip forms, its name "
                + "and effect appear on a plate, and the chip flies to the bottom-right corner where a +1 synergy "
                + "counter ticks. About 1.8 seconds, several in a row queue, input is never blocked. It replaces "
                + "the chat lines. Needs ui.juicyFeedback; the Distortion Effects video setting at 0% keeps "
                + "only the flashes and the sounds.")
                .translation(LANG + "ui.synergySlam").define("synergySlam", true);
        UI_HAUL_CARD = b.comment("Today's Haul: in a bed a \"Today's Haul\" button opens a card of what you earned since your last "
                + "haul, the bars filling as the XP flies in, and at login a small reminder of an unseen haul. "
                + "/proficiency haul shows the card whatever this says.")
                .translation(LANG + "ui.haulCard").define("haulCard", true);
        UI_COOP_STREAMS = b.comment("Co-op XP streams: when a player in company range earns XP, a short trickle of their "
                + "dots arcs from them into your skill bar while company pays you (a faint trail to their nameplate "
                + "otherwise), small faces left of your skill bar show who you are in company with, and a soft "
                + "plink plays when company adds to your own XP. Without ui.juicyFeedback or at Distortion Effects "
                + "0% the dots do not fly; the faces and sounds stay.")
                .translation(LANG + "ui.coopStreams").define("coopStreams", true);
        UI_TEMPO_CRIT_FLARE = b.comment("A vanilla jump crit with a weapon holds its Tempo chain twice as long for the next "
                + "pause, and the flames on the skill bar flare white to say so. Off: no white flare (the longer "
                + "window stays, it is a server rule).")
                .translation(LANG + "ui.tempoCritFlare").define("tempoCritFlare", true);
        UI_HUD_STACK = b.comment("A stack of recent skills above the HUD skill line. The line itself keeps the skill in your "
                + "hand; any other skill that earns XP gets a small row above it (icon, name and level, a thin bar its "
                + "XP dots fly into, its Tempo flames small, its own level-up pop). At most 3 rows, in the order they "
                + "first earned, so nothing jumps; a row fades ui.hudStackLinger seconds after its last gain. With an "
                + "empty hand the line stays on Running while you run and jump. Off: one line, as before 1.54.0.")
                .translation(LANG + "ui.hudStack").define("hudStack", true);
        UI_HUD_STACK_LINGER = b.comment("Seconds a row of the recent-skills stack stays after its last XP gain.")
                .translation(LANG + "ui.hudStackLinger").defineInRange("hudStackLinger", 4.0, 1.0, 30.0);
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
        UI_BONUS_FLIGHT = b.comment("Bonus loot flies to you: an extra drop from a double drop, Motherlode, Second Catch, "
                + "Double Hook or a talent flies as its item icon from where it appeared into the hotbar slot it lands in, "
                + "with a glint and a soft plink. HUD only. Also still while the Distortion Effects video setting is 0%.")
                .translation(LANG + "ui.bonusFlight").define("bonusFlight", true);
        UI_POINT_COIN = b.comment("A talent point earned on a level-up flies as a glowing coin from the skill bar to a small "
                + "brazier right of the hotbar, which shows your unspent points and glows while any are left. HUD only.")
                .translation(LANG + "ui.pointCoin").define("pointCoin", true);
        UI_READY_TOASTS = b.comment("A toast with a soft chime when a skill gets a talent rank you can take right now (a level-up, "
                + "or picking up the last missing material). At most one per skill every 5 minutes; never for ranks already "
                + "ready when the world loads (one summary toast at most).")
                .translation(LANG + "ui.readyToasts").define("readyToasts", true);
        UI_UNLOCK_SLAM = b.comment("Under the MODERN style, filling a tree's root wakes the signature and the ability with a "
                + "show: the root's socket rises off the tree, the materials you paid fly into it and morph it into the "
                + "Face card it unlocks, the slab turns to the Face and the card slams into its slot with dust, a jolt "
                + "and an ember glow. About 2 s; a click or a key lands it at once. Needs ui.juicyFeedback. Distortion "
                + "Effects at 0% keep the glow and the sounds but nothing moves.")
                .translation(LANG + "ui.unlockSlam").define("unlockSlam", true);
        UI_RESTED_POUR = b.comment("When rested XP pays double, blue dots stream from the rested segment of the skill bar into "
                + "the fill. HUD only.")
                .translation(LANG + "ui.restedPour").define("restedPour", true);
        UI_MATERIAL_TOOLTIPS = b.comment("An item that an unpaid talent node still wants as a material says so in its tooltip: "
                + "the skill, the node and how many you carry. The nearest three show, Shift shows all.")
                .translation(LANG + "ui.materialTooltips").define("materialTooltips", true);
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
                new Entry("hud", "hud.showTempo", HUD_SHOW_TEMPO, 0, 0),
                new Entry("hud", "hud.deathRecap", HUD_DEATH_RECAP, 0, 0),
                new Entry("tooltip", "tooltip.skillInfo", TOOLTIP_SKILL_INFO, 0, 0),
                new Entry("tooltip", "tooltip.alwaysDetailed", TOOLTIP_ALWAYS_DETAILED, 0, 0),
                new Entry("screens", "screens.unlockFx", SCREENS_UNLOCK_FX, 0, 0),
                new Entry("screens", "screens.activity", SCREENS_ACTIVITY, 0, 0),
                new Entry("procFx", "procFx.enabled", PROC_FX_ENABLED, 0, 0),
                new Entry("ui", "ui.skillIcons", UI_SKILL_ICONS, 0, 0),
                new Entry("ui", "ui.juicyFeedback", UI_JUICY_FEEDBACK, 0, 0),
                new Entry("ui", "ui.recordToasts", UI_RECORD_TOASTS, 0, 0),
                new Entry("ui", "ui.abilityGlint", UI_ABILITY_GLINT, 0, 0),
                new Entry("ui", "ui.advancementBurst", UI_ADVANCEMENT_BURST, 0, 0),
                new Entry("ui", "ui.synergySlam", UI_SYNERGY_SLAM, 0, 0),
                new Entry("ui", "ui.haulCard", UI_HAUL_CARD, 0, 0),
                new Entry("ui", "ui.coopStreams", UI_COOP_STREAMS, 0, 0),
                new Entry("ui", "ui.tempoCritFlare", UI_TEMPO_CRIT_FLARE, 0, 0),
                new Entry("ui", "ui.hudStack", UI_HUD_STACK, 0, 0),
                new Entry("ui", "ui.hudStackLinger", UI_HUD_STACK_LINGER, 1.0, 30.0),
                new Entry("ui", "ui.style", UI_STYLE, 0, 0),
                new Entry("ui", "ui.bonusFlight", UI_BONUS_FLIGHT, 0, 0),
                new Entry("ui", "ui.pointCoin", UI_POINT_COIN, 0, 0),
                new Entry("ui", "ui.readyToasts", UI_READY_TOASTS, 0, 0),
                new Entry("ui", "ui.unlockSlam", UI_UNLOCK_SLAM, 0, 0),
                new Entry("ui", "ui.restedPour", UI_RESTED_POUR, 0, 0),
                new Entry("ui", "ui.materialTooltips", UI_MATERIAL_TOOLTIPS, 0, 0),
                new Entry("hearth", "hearth.tracker", HEARTH_TRACKER, 0, 0),
                new Entry("hearth", "hearth.trackerY", HEARTH_TRACKER_Y, 0.1, 0.9),
                new Entry("hearth", "hearth.showRange", HEARTH_SHOW_RANGE, 0, 0),
                new Entry("hearth", "hearth.siegePanel", HEARTH_SIEGE_PANEL, 0, 0),
                new Entry("hearth", "hearth.siegePanelY", HEARTH_SIEGE_PANEL_Y, 0.0, 0.9));
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

    public static boolean uiMaterialTooltips() {
        return flag(UI_MATERIAL_TOOLTIPS, true);
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

    public static boolean uiRecordToasts() {
        return flag(UI_RECORD_TOASTS, true);
    }

    public static boolean uiMilestoneCeremonies() {
        return flag(UI_MILESTONE_CEREMONIES, true);
    }

    public static boolean uiBonusFlight() {
        return flag(UI_BONUS_FLIGHT, true);
    }

    public static boolean uiPointCoin() {
        return flag(UI_POINT_COIN, true);
    }

    public static boolean uiReadyToasts() {
        return flag(UI_READY_TOASTS, true);
    }

    public static boolean uiRestedPour() {
        return flag(UI_RESTED_POUR, true);
    }

    public static boolean uiAbilityGlint() {
        return flag(UI_ABILITY_GLINT, true);
    }

    public static boolean uiAdvancementBurst() {
        return flag(UI_ADVANCEMENT_BURST, true);
    }

    public static boolean uiUnlockSlam() {
        return flag(UI_UNLOCK_SLAM, true);
    }

    public static boolean uiSynergySlam() {
        return flag(UI_SYNERGY_SLAM, true);
    }

    public static boolean uiHaulCard() {
        return flag(UI_HAUL_CARD, true);
    }

    public static boolean uiCoopStreams() {
        return flag(UI_COOP_STREAMS, true);
    }

    public static boolean uiTempoCritFlare() {
        return flag(UI_TEMPO_CRIT_FLARE, true);
    }

    /** A session override of ui.hudStack for the layout demo ({@code null}: the file's value). */
    private static Boolean sessionHudStack;

    public static boolean uiHudStack() {
        return sessionHudStack != null ? sessionHudStack : flag(UI_HUD_STACK, true);
    }

    /** For the layout demo only: turns the stack on or off for this session without writing the file. */
    public static void setSessionHudStack(Boolean on) {
        sessionHudStack = on;
    }

    /** A session override of the linger for the layout demo (0: the file's value). */
    private static long sessionLingerMs;

    /** For the layout demo only: holds the rows up for {@code ms} (0 goes back to ui.hudStackLinger). */
    public static void setUiHudStackLingerForDemo(long ms) {
        sessionLingerMs = ms;
    }

    /** ui.hudStackLinger in milliseconds. */
    public static long uiHudStackLingerMs() {
        if (sessionLingerMs > 0) {
            return sessionLingerMs;
        }
        double seconds = SPEC.isLoaded() ? UI_HUD_STACK_LINGER.get() : 4.0;
        return Math.round(Math.max(1.0, Math.min(30.0, seconds)) * 1000);
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
