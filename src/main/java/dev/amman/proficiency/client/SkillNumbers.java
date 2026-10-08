package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
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

    /**
     * The compact block for an item's tooltip: passive, proc and the ability's ready or cooldown
     * state. The passive and proc are the header line's own parts; only the ability line differs,
     * because an item shows what you can do now. {@code gameTime} is the client level's tick.
     */
    public static List<Component> itemLines(PlayerSkills skills, Skill skill, long gameTime) {
        List<Component> lines = new ArrayList<>(3);
        lines.add(passivePart(skills, skill));
        lines.add(procPart(skills, skill));
        if (skills.level(skill) < ActiveService.unlockLevel()) {
            lines.add(abilityLockedPart());
        } else {
            Component abilityName = Component.translatable(skill.activeKey());
            long cooling = skills.cooldownRemaining(skill, gameTime);
            lines.add(cooling > 0
                    ? Component.translatable("proficiency.tooltip.ability.cooldown", abilityName,
                            seconds((cooling + 19) / 20 * 20))
                    : Component.translatable("proficiency.tooltip.ability.ready", abilityName));
        }
        return lines;
    }

    /** The hover: where each number comes from, and what the next level gives. */
    public static List<Component> tooltip(PlayerSkills skills, Skill skill) {
        int level = skills.level(skill);
        boolean maxed = level >= SkillMath.MAX_LEVEL;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.stats.title", level)
                .withStyle(style -> style.withColor(SkillPalette.accent(skill.category()))));

        // Passive.
        PlayerSkills.Modifier passive = skills.modifier(skill, PerkEffect.BONUS);
        lines.add(Component.translatable("proficiency.stats.passive", pct(skills.bonus(skill)))
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));
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
