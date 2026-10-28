package dev.amman.proficiency.jade;

import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.TooltipDetail;
import dev.amman.proficiency.look.LookRules;
import dev.amman.proficiency.look.PlayerLookData;
import dev.amman.proficiency.look.PlayerLookRules;
import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.CodexService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import snownee.jade.api.ITooltip;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines of another player's tooltip, from the tag {@link PlayerLookData#write} wrote: at most three short
 * ones (title, best skills, what you gain together), and with Shift the full list of gains. The same code on
 * both Minecraft versions (Jade 15 data channel and Jade 11 entity data).
 */
final class PlayerLookView {

    /** The gold of a Codex title: the same as the first-time mark. */
    private static final int GREEN = 0x7FD36B;

    private PlayerLookView() {
    }

    /** The skills of an ordinal array, dropping anything out of range. */
    static List<Skill> skills(int[] ordinals) {
        List<Skill> out = new ArrayList<>();
        for (int o : ordinals) {
            if (o >= 0 && o < Skill.VALUES.length) {
                out.add(Skill.VALUES[o]);
            }
        }
        return out;
    }

    static List<PlayerLookRules.Pick> picks(int[] flat) {
        List<PlayerLookRules.Pick> out = new ArrayList<>();
        for (int i = 0; i + 1 < flat.length; i += 2) {
            if (flat[i] >= 0 && flat[i] < Skill.VALUES.length && flat[i + 1] > 0) {
                out.add(new PlayerLookRules.Pick(Skill.VALUES[flat[i]], flat[i + 1]));
            }
        }
        return out;
    }

    private static String names(List<Skill> skills) {
        StringBuilder out = new StringBuilder();
        for (Skill skill : skills) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(Component.translatable(skill.translationKey()).getString());
        }
        return out.toString();
    }

    private static MutableComponent dim(Component text) {
        return text.copy().withStyle(style -> style.withColor(LookText.DIM));
    }

    static void append(ITooltip tooltip, CompoundTag data) {
        if (!data.contains(PlayerLookData.PRESENT)) {
            return;
        }
        Codex.Title title = Codex.parse(data.getString(PlayerLookData.TITLE));
        if (title != null) {
            tooltip.add(CodexService.titleName(title).copy().withStyle(style -> style.withColor(LookText.GOLD)));
        }
        List<PlayerLookRules.Pick> top = picks(data.getIntArray(PlayerLookData.TOP));
        if (!top.isEmpty()) {
            if (SkillIcons.enabled()) {
                tooltip.add(new TopSkillsElement(top));
            } else {
                MutableComponent row = Component.empty();
                for (PlayerLookRules.Pick pick : top) {
                    if (!row.getSiblings().isEmpty()) {
                        row.append(Component.literal("  "));
                    }
                    row.append(Component.translatable(pick.skill().translationKey()).append(" " + pick.level())
                            .withStyle(style -> style.withColor(LookText.colour(pick.skill()))));
                }
                tooltip.add(row);
            }
        }
        int flags = data.getInt(PlayerLookData.FLAGS);
        double extra = data.getFloat(PlayerLookData.EXTRA);
        float radius = data.getFloat(PlayerLookData.RADIUS);
        List<Skill> mentors = skills(data.getIntArray(PlayerLookData.MENTORS));
        List<Skill> students = skills(data.getIntArray(PlayerLookData.STUDENTS));
        boolean company = (flags & PlayerLookRules.IN_COMPANY) != 0;
        boolean teach = (flags & PlayerLookRules.CAN_TEACH_ME) != 0;
        boolean teaching = (flags & PlayerLookRules.I_CAN_TEACH) != 0;
        boolean hearth = (flags & PlayerLookRules.SAME_HEARTH) != 0;
        if (!TooltipDetail.detailed()) {
            shortGains(tooltip, company, extra, radius, mentors, students, teach, teaching, hearth);
            return;
        }
        // The full list: one line per gain, each saying where it applies.
        tooltip.add(company
                ? Component.translatable("proficiency.jade.players.company", PlayerLookRules.percent(extra))
                        .withStyle(style -> style.withColor(GREEN))
                : dim(Component.translatable("proficiency.jade.players.apart", Math.round(radius))));
        if (!mentors.isEmpty()) {
            tooltip.add(Component.translatable("proficiency.jade.players.mentor", names(mentors))
                    .withStyle(style -> style.withColor(company ? GREEN : LookText.DIM)));
        }
        if (!students.isEmpty()) {
            tooltip.add(Component.translatable("proficiency.jade.players.student", names(students))
                    .withStyle(style -> style.withColor(teaching ? GREEN : LookText.DIM)));
        }
        if (teach) {
            tooltip.add(Component.translatable("proficiency.jade.players.teach")
                    .withStyle(style -> style.withColor(GREEN)));
        }
        if (teaching) {
            tooltip.add(Component.translatable("proficiency.jade.players.teaches")
                    .withStyle(style -> style.withColor(GREEN)));
        }
        if (hearth) {
            tooltip.add(Component.translatable("proficiency.jade.players.hearth")
                    .withStyle(style -> style.withColor(LookText.GOLD)));
        }
    }

    /** The third short line: the gain that applies now, or the distance it needs, with a dim "Shift: more". */
    private static void shortGains(ITooltip tooltip, boolean company, double extra, float radius, List<Skill> mentors,
            List<Skill> students, boolean teach, boolean teaching, boolean hearth) {
        List<Component> parts = new ArrayList<>();
        if (company) {
            parts.add(Component.translatable(mentors.isEmpty()
                    ? "proficiency.jade.players.short.company" : "proficiency.jade.players.short.mentor",
                    PlayerLookRules.percent(extra)).withStyle(style -> style.withColor(GREEN)));
        }
        if (teach || teaching) {
            parts.add(Component.translatable(teach ? "proficiency.jade.players.short.teach"
                    : "proficiency.jade.players.short.teaches").withStyle(style -> style.withColor(GREEN)));
        }
        if (hearth) {
            parts.add(Component.translatable("proficiency.jade.players.short.hearth")
                    .withStyle(style -> style.withColor(LookText.GOLD)));
        }
        if (parts.isEmpty()) {
            parts.add(dim(Component.translatable("proficiency.jade.players.short.apart", Math.round(radius))));
        }
        MutableComponent line = Component.empty();
        for (Component part : parts) {
            if (!line.getSiblings().isEmpty()) {
                line.append(dim(Component.literal(" · ")));
            }
            line.append(part);
        }
        boolean more = company ? (!mentors.isEmpty() || !students.isEmpty() || teach || teaching || hearth)
                : true;
        if (more) {
            line.append(dim(Component.translatable("proficiency.jade.players.more")));
        }
        tooltip.add(line);
    }
}
