package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillPassives;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The tree screen's "your numbers" line: the passive, the signature proc's chance and power, and
 * the ability's duration and cooldown, at the player's level with the talents they own. Every
 * number comes from the method gameplay calls ({@link PlayerSkills#bonus},
 * {@link PlayerSkills#procChance}, {@link PlayerSkills#procPower}, {@link ActiveService}); this
 * class only formats them.
 */
public final class SkillNumbers {

    private SkillNumbers() {
    }

    /** One line for the header: "Passive +12% · Proc 8%, power ×1.25 · Ability 20 s, cooldown 180 s". */
    public static Component line(PlayerSkills skills, Skill skill) {
        int level = skills.level(skill);
        MutableComponent line = passivePart(skills, skill);
        line.append(" · ");
        line.append(procPart(skills, skill));
        line.append(" · ");
        if (level >= ActiveService.unlockLevel()) {
            line.append(Component.translatable("proficiency.stats.ability",
                    seconds(ActiveService.durationTicks(skills, skill)),
                    seconds(ActiveService.cooldownTicks(skills, skill))));
        } else {
            line.append(abilityLockedPart());
        }
        return line;
    }

    /** "Passive +12%". Shared by the header line and the item tooltip. */
    static MutableComponent passivePart(PlayerSkills skills, Skill skill) {
        return Component.translatable("proficiency.stats.passive", pct(skills.bonus(skill)));
    }

    /** "Proc 8%, power ×1.25", or "Proc at level 25" before it unlocks. Reads the chance once. */
    static MutableComponent procPart(PlayerSkills skills, Skill skill) {
        double chance = skills.procChance(skill);
        if (chance > 0) {
            return Component.translatable("proficiency.stats.proc", pct(chance), times(skills.procPower(skill)));
        }
        return Component.translatable("proficiency.stats.proc_locked", procUnlock());
    }

    static MutableComponent abilityLockedPart() {
        return Component.translatable("proficiency.stats.ability_locked", ActiveService.unlockLevel());
    }

    // ---- The passive as concrete stats ----------------------------------------------------------

    /** One stat's label, with its arguments. */
    private static MutableComponent statName(SkillPassives.Stat stat) {
        return Component.translatable(stat.labelKey(), stat.labelArgs());
    }

    /** A stat value in its own format: "×1.36", "7.2%", "4.78". */
    static String value(SkillPassives.Format format, double value) {
        return switch (format) {
            case MULT -> "×" + fixed(value, 2);
            case PERCENT -> number(value * 100, 1) + "%";
            case NUMBER -> number(value, 2);
        };
    }

    /** The detail layer: "Break speed: ×1.00 → ×1.36" for every stat the passive changes. */
    public static List<Component> statRows(PlayerSkills skills, Skill skill) {
        List<Component> rows = new ArrayList<>(3);
        for (SkillPassives.Stat stat : SkillPassives.of(skills, skill)) {
            boolean changed = Math.abs(stat.current() - stat.base()) > 1e-9;
            rows.add(Component.translatable("proficiency.stat.row",
                    statName(stat).withStyle(ChatFormatting.GRAY),
                    Component.literal(value(stat.format(), stat.base())).withStyle(ChatFormatting.DARK_GRAY),
                    Component.literal(value(stat.format(), stat.current()))
                            .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
        }
        return rows;
    }

    /** The short layer's one line: the skill's first stat as it is now, "Break speed: ×1.36". */
    public static Component headline(PlayerSkills skills, Skill skill) {
        SkillPassives.Stat stat = SkillPassives.of(skills, skill).get(0);
        boolean changed = Math.abs(stat.current() - stat.base()) > 1e-9;
        return Component.translatable("proficiency.stat.now", statName(stat).withStyle(ChatFormatting.GRAY),
                Component.literal(value(stat.format(), stat.current()))
                        .withStyle(changed ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }

    /** The ability's state on an item: ready or cooling down; null before the ability unlocks. */
    private static Component abilityState(PlayerSkills skills, Skill skill, long gameTime) {
        if (skills.level(skill) < ActiveService.unlockLevel()) {
            return null;
        }
        Component abilityName = Component.translatable(skill.activeKey());
        long cooling = skills.cooldownRemaining(skill, gameTime);
        return cooling > 0
                ? Component.translatable("proficiency.tooltip.ability.cooldown", abilityName,
                        seconds((cooling + 19) / 20 * 20))
                : Component.translatable("proficiency.tooltip.ability.ready", abilityName);
    }

    /**
     * The block under an item's tooltip header. Short: the one stat that matters and, once it
     * exists, the ability's state. Detailed: every stat as base to current, the signature move and
     * the ability. {@code gameTime} is the client level's tick.
     */
    public static List<Component> itemLines(PlayerSkills skills, Skill skill, long gameTime, boolean detailed) {
        List<Component> lines = new ArrayList<>(6);
        Component ability = abilityState(skills, skill, gameTime);
        if (!detailed) {
            lines.add(headline(skills, skill));
            if (ability != null) {
                lines.add(ability);
            }
            return lines;
        }
        lines.addAll(statRows(skills, skill));
        lines.add(procPart(skills, skill));
        lines.add(ability != null ? ability : abilityLockedPart());
        return lines;
    }

    /** The tree header's hover, short or detailed. */
    public static List<Component> tooltip(PlayerSkills skills, Skill skill, boolean detailed) {
        return detailed ? detailedTooltip(skills, skill) : shortTooltip(skills, skill);
    }

    /** Short: the title, the stat that matters, the signature move. */
    private static List<Component> shortTooltip(PlayerSkills skills, Skill skill) {
        int level = skills.level(skill);
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.stats.title", level)
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category()))));
        lines.add(headline(skills, skill));
        double chance = skills.procChance(skill);
        lines.add(chance > 0
                ? Component.translatable("proficiency.tooltip.proc", Component.translatable(skill.procKey()), pct(chance))
                        .withStyle(ChatFormatting.LIGHT_PURPLE)
                : Component.translatable("proficiency.tooltip.proc_locked", procUnlock())
                        .withStyle(ChatFormatting.DARK_PURPLE));
        return lines;
    }

    /**
     * The detailed hover, top-down: the passive with its stat lines and where they come from, then
     * the signature move, then the ability, then what the skill is about.
     */
    private static List<Component> detailedTooltip(PlayerSkills skills, Skill skill) {
        int level = skills.level(skill);
        boolean maxed = level >= SkillMath.MAX_LEVEL;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.stats.title", level)
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category()))));

        // Passive: the summary, the stats, the breakdown, the next level.
        PlayerSkills.Modifier passive = skills.modifier(skill, PerkEffect.BONUS);
        lines.add(Component.translatable("proficiency.stats.passive", pct(skills.bonus(skill)))
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.addAll(statRows(skills, skill));
        lines.add(Component.translatable("proficiency.stats.passive.parts",
                pct(SkillMath.bonus(skill, level)), times(passive.ranks()), times(passive.synergies()),
                times(passive.discipline())).withStyle(ChatFormatting.DARK_GRAY));
        if (!maxed) {
            lines.add(Component.translatable("proficiency.stats.next",
                    "+" + pct(skills.bonusAt(skill, level + 1)) + "%").withStyle(ChatFormatting.DARK_GRAY));
        }

        // Signature proc.
        lines.add(Component.literal(" "));
        Component procName = Component.translatable(skill.procKey());
        PlayerSkills.Modifier chance = skills.modifier(skill, PerkEffect.PROC_CHANCE);
        PlayerSkills.Modifier power = skills.modifier(skill, PerkEffect.PROC_POWER);
        if (skills.procChance(skill) > 0) {
            lines.add(Component.translatable("proficiency.stats.proc.named", procName,
                    pct(skills.procChance(skill)), times(skills.procPower(skill)))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            lines.add(Component.translatable("proficiency.stats.proc.parts",
                    pct(SkillMath.procChance(skill, level)), times(chance.ranks()), times(chance.synergies()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            if (!maxed) {
                lines.add(Component.translatable("proficiency.stats.next",
                        pct(skills.procChanceAt(skill, level + 1)) + "%").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else {
            lines.add(Component.translatable("proficiency.stats.proc.locked", procName, procUnlock())
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        lines.add(Component.translatable("proficiency.stats.power.parts",
                times(power.ranks()), times(power.synergies())).withStyle(ChatFormatting.DARK_GRAY));
        if (situational(skill)) {
            lines.add(Component.translatable("proficiency.stats.proc.situational")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }

        // Ability.
        lines.add(Component.literal(" "));
        Component abilityName = Component.translatable(skill.activeKey());
        if (level >= ActiveService.unlockLevel()) {
            lines.add(Component.translatable("proficiency.stats.ability.named", abilityName,
                    seconds(ActiveService.durationTicks(skills, skill)),
                    seconds(ActiveService.cooldownTicks(skills, skill))).withStyle(ChatFormatting.GOLD));
        } else {
            lines.add(Component.translatable("proficiency.stats.ability.locked", abilityName,
                    ActiveService.unlockLevel()).withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable("proficiency.stats.ability.parts",
                seconds(ActiveService.durationTicks()),
                times(skills.perkModifier(skill, PerkEffect.ABILITY_DURATION)),
                seconds(ProficiencyConfig.abilityCooldown()),
                times(skills.perkModifier(skill, PerkEffect.ABILITY_COOLDOWN))).withStyle(ChatFormatting.DARK_GRAY));

        // What the skill is about, last: it is the longest part.
        lines.add(Component.literal(" "));
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));
        return lines;
    }

    /** The skills whose proc chance ProcService raises by where you are (see its situational()). */
    private static boolean situational(Skill skill) {
        return skill == Skill.TRIDENTS || skill == Skill.FISHING || skill == Skill.CHARGER
                || skill == Skill.MINING;
    }

    private static int procUnlock() {
        return ProficiencyConfig.procUnlockLevel();
    }

    /** A fraction as a percent: one decimal under 10 (8.5), whole above (24). */
    static String pct(double fraction) {
        double percent = fraction * 100;
        return number(percent, Math.abs(percent) < 10 ? 1 : 0);
    }

    /** A multiplier: "×1.25", trailing zeros dropped ("×1", "×1.5"). */
    static String times(double multiplier) {
        return "×" + number(multiplier, 2);
    }

    static String seconds(long ticks) {
        return number(ticks / 20.0, ticks % 20 == 0 ? 0 : 1);
    }

    /** A number with exactly {@code decimals} decimals, in the player's decimal mark. */
    static String fixed(double value, int decimals) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", value);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null
                && minecraft.options.languageCode.startsWith("pl")) {
            text = text.replace('.', ',');
        }
        return text;
    }

    private static String number(double value, int decimals) {
        String text = String.format(Locale.ROOT, "%." + decimals + "f", value);
        if (text.contains(".")) {
            text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        // Polish writes a decimal comma.
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.options != null
                && minecraft.options.languageCode.startsWith("pl")) {
            text = text.replace('.', ',');
        }
        return text;
    }
}
