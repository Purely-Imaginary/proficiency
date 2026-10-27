package dev.amman.proficiency.jade;

import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.look.LookRules;
import dev.amman.proficiency.look.XpLookData;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The lines of a block's tooltip, from the tag {@link XpLookData#block} wrote. The same code prints them
 * whether the tag came through Jade's own data channel (Jade 15) or through the mod's small request packet
 * (Forge 1.20.1, where Jade 11 only carries server data for block entities).
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
final class BlockLookView {

    private BlockLookView() {
    }

    /** The skill of the tag, or null when it carries none (nothing to show). */
    static Skill skillOf(CompoundTag data) {
        if (!data.contains(XpLookData.SKILL)) {
            return null;
        }
        int ordinal = data.getInt(XpLookData.SKILL);
        return ordinal < 0 || ordinal >= Skill.VALUES.length ? null : Skill.VALUES[ordinal];
    }

    static void append(ITooltip tooltip, CompoundTag data, IPluginConfig config) {
        Skill skill = skillOf(data);
        if (skill == null) {
            return;
        }
        int flags = data.getInt(XpLookData.FLAGS);
        line(tooltip, skill, data, LookRules.has(flags, LookRules.PLACED) ? LookText.placed(skill)
                : LookText.pays(skill, data.getFloat(XpLookData.XP)), config);
    }

    /** The icon, the line in the skill's colour, and the first-time mark: the one layout blocks and mobs share. */
    static void line(ITooltip tooltip, Skill skill, CompoundTag data, Component line, IPluginConfig config) {
        if (SkillIcons.enabled()) {
            tooltip.add(new SkillIconElement(skill));
            tooltip.append(line);
        } else {
            tooltip.add(line);
        }
        if (LookRules.has(data.getInt(XpLookData.FLAGS), LookRules.FIRST) && config.get(ProficiencyJadePlugin.LOOK_FIRST)) {
            tooltip.append(Component.literal("  "));
            tooltip.append(LookText.first(data.getFloat(XpLookData.FIRST)));
        }
    }
}
