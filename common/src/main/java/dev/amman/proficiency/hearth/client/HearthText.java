package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.comfort.ComfortKinds;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The Hearth's words on the client: boon lines from the numbers the server sent, names of stages,
 * Comfort kinds and ledger sources, what a locked pool waits on. One place, so the HUD tooltip, the
 * tracker and the panel say the same thing the same way. Keys under {@code proficiency.hearth.client.}.
 */
public final class HearthText {

    static final String KEY = "proficiency.hearth.client.";

    private HearthText() {
    }

    public static Component pool(int pool) {
        HearthPool p = HearthPool.byIndex(pool);
        return p == null ? Component.literal("?") : Component.translatable(p.translationKey());
    }

    /** "Firepit" .. "Heart of the Home" (the stages lane's names). */
    public static Component stageName(int stage) {
        return stage >= 1 && stage <= 6 ? Component.translatable("proficiency.hearth.stages.name." + stage)
                : Component.translatable(KEY + "stage.none");
    }

    /**
     * The self-build's status line: progress with the countdown to the next block while it places blocks, or what
     * it waits for. One place for the panel and the Jade tooltip.
     */
    public static Component buildStatus(HearthStatePayload.Build build, long secondsLeft) {
        if (build.reason() == 0) {
            return Component.translatable("proficiency.hearth.client.panel.build.progress", build.done(), build.total(),
                    String.format(Locale.ROOT, "%d:%02d", secondsLeft / 60, secondsLeft % 60));
        }
        return switch (build.reason()) {
            case 1 -> Component.translatable("proficiency.hearth.client.panel.build.wait.material",
                    dev.amman.proficiency.hearth.stages.HearthAutoBuild.materials(String.join(",", build.missing())));
            case 2 -> Component.translatable("proficiency.hearth.client.panel.build.wait.foreign");
            default -> Component.translatable("proficiency.hearth.client.panel.build.wait.support");
        };
    }

    /** The status line's colour: ember while it builds, locked grey while it waits. */
    public static int buildColour(HearthStatePayload.Build build) {
        return build.reason() == 0 ? HearthPalette.EMBER
                : HearthPalette.LOCKED;
    }

    /** The build the stage asks for, in one sentence. */
    public static Component stageBuild(int stage) {
        return Component.translatable(KEY + "stage." + Math.max(1, Math.min(6, stage)) + ".build");
    }

    /** A Comfort kind's name (the comfort lane's); a kind a pack added and named nowhere reads as its id. */
    public static Component kind(String kind) {
        return Component.translatableWithFallback(ComfortKinds.translationKey(kind), ComfortKinds.fallbackName(kind));
    }

    /** What counts as that kind ("Bookshelves and lecterns."), or null for a kind a pack added. */
    @Nullable
    public static Component kindDesc(String kind) {
        String key = ComfortKinds.descriptionKey(kind);
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    /** A ledger source: a skill's name, or the charge lane's names for siege, tales, chronicle, feast. */
    public static Component source(String source) {
        Skill skill = Skill.byId(source);
        if (skill != null) {
            return Component.translatable(skill.translationKey());
        }
        String key = "proficiency.hearth.charge.source." + source;
        return net.minecraft.locale.Language.getInstance().has(key) ? Component.translatable(key)
                : Component.literal(source);
    }

    public static String roman(int tier) {
        return tier <= 0 ? "0" : HearthRules.roman(tier);
    }

    /** The tier as the tracker and the panel's tier box show it: a dim dot below tier I. */
    public static String tierMark(int tier) {
        return tier <= 0 ? "\u00B7" : HearthRules.roman(tier);
    }

    /**
     * What a held pool waits on, short, for after the lock glyph and its icon ({@link #needIcon}): the
     * Comfort or Character there is over what the next step wants ("6/8", "20/24"), or the stage to build
     * ("IV"). Null for none.
     */
    @Nullable
    public static String needShort(int lock, int need, int comfort, int character) {
        HearthRules.Lock which = lockOf(lock);
        if (which == null) {
            return null;
        }
        return switch (which) {
            case COMFORT -> comfort + "/" + need;
            case CHARACTER -> character + "/" + need;
            case CEILING -> roman(need);
            case NONE -> null;
        };
    }

    /** The same in words, for when icons are off: "Comfort 6/8", "Character 20/24", "Stage IV". */
    @Nullable
    public static Component needFull(int lock, int need, int comfort, int character) {
        HearthRules.Lock which = lockOf(lock);
        if (which == null) {
            return null;
        }
        return switch (which) {
            case COMFORT -> Component.translatable(KEY + "comfort", comfort, need);
            case CHARACTER -> character(character, need);
            case CEILING -> Component.translatable(KEY + "need.ceiling", roman(need));
            case NONE -> null;
        };
    }

    /** The icon of what a held pool waits on, under {@code textures/gui/hearth/}: comfort, character, stage/n. */
    @Nullable
    public static String needIcon(int lock, int need) {
        HearthRules.Lock which = lockOf(lock);
        if (which == null) {
            return null;
        }
        return switch (which) {
            case COMFORT -> "comfort";
            case CHARACTER -> "character";
            case CEILING -> "stage/" + Math.max(1, Math.min(6, need));
            case NONE -> null;
        };
    }

    @Nullable
    private static HearthRules.Lock lockOf(int lock) {
        return lock <= 0 || lock >= HearthRules.Lock.values().length ? null : HearthRules.Lock.values()[lock];
    }

    /** The long form for the panel's tooltip: "Held at III: build stage IV" and so on. */
    @Nullable
    public static Component held(int lock, int need, int tier) {
        if (lock <= 0 || lock >= HearthRules.Lock.values().length) {
            return null;
        }
        return switch (HearthRules.Lock.values()[lock]) {
            case COMFORT -> Component.translatable(KEY + "lock.comfort", roman(tier), need);
            case CEILING -> Component.translatable(KEY + "lock.ceiling", roman(tier), roman(need));
            case CHARACTER -> Component.translatable(KEY + "lock.character", roman(tier), need);
            case NONE -> null;
        };
    }

    private static String pct(double fraction) {
        return Long.toString(Math.round(fraction * 100));
    }

    private static String num(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05 ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * The boon of a pool at its tier, as one line in its colour ("Hunger drains 22% slower"), from the
     * numbers the server sent; null when the pool gives nothing yet or the server sent no numbers.
     * {@code next} says the tier above instead.
     */
    @Nullable
    public static Component boonLine(int pool, int tier, boolean next) {
        if (!next && tier <= 0) {
            return null;
        }
        HearthStatePayload.Boon boon = HearthView.boon(pool);
        if (boon == null) {
            return null;
        }
        float value = next ? boon.next() : boon.value();
        float extra = next ? boon.nextExtra() : boon.extra();
        MutableComponent line = boonText(boon.boon(), value, extra);
        if (line == null) {
            return null;
        }
        return line.withStyle(style -> style.withColor(HearthPalette.pool(pool) & 0xFFFFFF));
    }

    @Nullable
    static MutableComponent boonText(String boon, float value, float extra) {
        if (!(value > 0) && !(extra > 0)) {
            return null;
        }
        return switch (boon) {
            case "hunger_slow" -> Component.translatable(KEY + "boon.hunger_slow", pct(value));
            case "regen_out_of_combat" -> extra > 0
                    ? Component.translatable(KEY + "boon.regen_out_of_combat", num(value))
                            .append(Component.literal(" · "))
                            .append(Component.translatable(KEY + "boon.regen_temp", pct(extra)))
                    : Component.translatable(KEY + "boon.regen_out_of_combat", num(value));
            case "mob_damage_reduction" -> Component.translatable(KEY + "boon.mob_damage_reduction", pct(value));
            case "station_speed" -> extra > 0
                    ? Component.translatable(KEY + "boon.station_speed", pct(value))
                            .append(Component.literal(" · "))
                            .append(Component.translatable(KEY + "boon.refund", pct(extra)))
                    : Component.translatable(KEY + "boon.station_speed", pct(value));
            case "refund_chance" -> Component.translatable(KEY + "boon.refund", pct(value));
            case "readiness" -> Component.translatable(KEY + "boon.readiness", num(value))
                    .append(Component.literal(" · "))
                    .append(Component.translatable(KEY + "boon.provisioned", num(extra)));
            default -> null;
        };
    }

    /** "Comfort 9/12", "Character 31/50". */
    public static Component comfort(int comfort, int max) {
        return Component.translatable(KEY + "comfort", comfort, max);
    }

    public static Component character(int character, int max) {
        return Component.translatable("proficiency.hearth.stages.character", character, max);
    }

    static Component dim(String key, Object... args) {
        return Component.translatable(KEY + key, args).withStyle(ChatFormatting.GRAY);
    }
}
