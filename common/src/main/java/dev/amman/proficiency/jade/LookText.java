package dev.amman.proficiency.jade;

import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.charge.PoolColors;
import dev.amman.proficiency.hearth.client.HearthPalette;
import dev.amman.proficiency.hearth.client.HearthText;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.look.HearthLookData;
import dev.amman.proficiency.look.LookRules;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The words and colours of the Jade lines, kept apart from the Jade API so they read the same on the
 * NeoForge/Fabric plugin and the Forge one, and so no class that talks to Jade does any wording.
 * Everything is {@code proficiency.jade.*} in the lang files.
 */
public final class LookText {

    /** The gold of a first-time mark: the same as a maxed skill's number. */
    public static final int GOLD = SkillPalette.MAXED & 0xFFFFFF;
    /** A line that pays nothing: dim, like the panel's secondary text. */
    public static final int DIM = SkillPalette.TEXT_DIM & 0xFFFFFF;

    private LookText() {
    }

    public static int colour(Skill skill) {
        return SkillPalette.accent(skill.category()) & 0xFFFFFF;
    }

    private static MutableComponent skillName(Skill skill) {
        return Component.translatable(skill.translationKey());
    }

    /** "Mining +1.2 XP", in the skill's colour. */
    public static Component pays(Skill skill, float xp) {
        return Component.translatable("proficiency.jade.pays", skillName(skill), LookRules.formatXp(xp))
                .withStyle(style -> style.withColor(colour(skill)));
    }

    /** "Placed by a player: pays no XP", dim. */
    public static Component placed(Skill skill) {
        return Component.translatable("proficiency.jade.placed", skillName(skill))
                .withStyle(style -> style.withColor(DIM));
    }

    /** "✦ First time +15 XP", gold: appended after the payment. */
    public static Component first(float xp) {
        return Component.translatable("proficiency.jade.first", LookRules.formatXp(xp))
                .withStyle(style -> style.withColor(GOLD));
    }

    /** "Swords: hit +1.0 · kill +4.5 XP", in the skill's colour. */
    public static Component mob(Skill skill, float hit, float kill) {
        String key = kill > 0 ? (hit > 0 ? "proficiency.jade.mob" : "proficiency.jade.mob.kill") : "proficiency.jade.mob.hit";
        MutableComponent line = kill > 0 && hit > 0
                ? Component.translatable(key, skillName(skill), LookRules.formatXp(hit), LookRules.formatXp(kill))
                : Component.translatable(key, skillName(skill), LookRules.formatXp(kill > 0 ? kill : hit));
        return line.withStyle(style -> style.withColor(colour(skill)));
    }

    /** "Spawned by an egg or a command: pays no XP", dim. */
    public static Component mobNothing(Skill skill) {
        return Component.translatable("proficiency.jade.mob.none", skillName(skill))
                .withStyle(style -> style.withColor(DIM));
    }

    /** The Hearth tooltip's lines, from the tag the server wrote ({@code HearthLookData}). */
    public static java.util.List<Component> hearthLines(CompoundTag data) {
        java.util.List<Component> lines = new java.util.ArrayList<>();
        String laneId = data.getString(HearthLookData.LANE);
        Component lane = laneId.isEmpty() ? null : Component.translatable("proficiency.hearth.guide.lane." + laneId);
        MutableComponent head = Component.literal(LookRules.hearthHeadline(
                HearthText.stageName(data.getInt(HearthLookData.STAGE)).getString(), lane == null ? "" : lane.getString()));
        lines.add(head.withStyle(style -> style.withColor(HearthPalette.EMBER & 0xFFFFFF)));
        if (data.getBoolean(HearthLookData.SMOULDERS)) {
            lines.add(Component.translatable("proficiency.jade.hearth.smoulders")
                    .withStyle(style -> style.withColor(LookText.DIM)));
        } else {
            int tier = data.getInt(HearthLookData.TIER);
            int feeding = data.getInt(HearthLookData.FEEDING);
            HearthPool pool = feeding > 0 && feeding <= HearthPool.VALUES.length ? HearthPool.VALUES[feeding - 1] : null;
            if (tier <= 0) {
                lines.add(Component.translatable("proficiency.jade.hearth.charge.none")
                        .withStyle(style -> style.withColor(LookText.DIM)));
            } else if (pool == null) {
                lines.add(Component.translatable("proficiency.jade.hearth.charge", HearthText.roman(tier)));
            } else {
                lines.add(Component.translatable("proficiency.jade.hearth.charge.pool", HearthText.roman(tier),
                        Component.translatable(pool.translationKey())
                                .withStyle(style -> style.withColor(PoolColors.rgb(pool)))));
            }
            lines.add(Component.translatable("proficiency.jade.hearth.warmth",
                    LookRules.warmthPercent(data.getFloat(HearthLookData.WARMTH))));
        }
        HearthStatePayload.Build build = HearthLookData.build(data);
        if (build.on()) {
            lines.add(HearthText.buildStatus(build, build.seconds())
                    .copy().withStyle(style -> style.withColor(HearthText.buildColour(build) & 0xFFFFFF)));
        }
        return lines;
    }
}
