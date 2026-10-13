package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The chat lines for the two halves of unlocking a signature: the level and the tree's root. The
 * signature and the ability need both, so whichever comes second is the moment it switches on,
 * and the one that comes first says what is still missing. Nobody gets a proc they never asked for.
 */
public final class SignatureNotice {

    private SignatureNotice() {
    }

    /** The skill just crossed the proc unlock level. */
    public static void levelReached(ServerPlayer player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        if (skills.rootOpen(skill)) {
            unlocked(player, skill);
            return;
        }
        Talent root = Talents.root(skill);
        player.sendSystemMessage(Component.translatable("proficiency.proc.needs_root",
                        Component.translatable(skill.translationKey()),
                        Component.translatable(skill.procKey()).withStyle(ChatFormatting.BOLD),
                        Component.translatable(root.nameKey()).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.AQUA));
    }

    /** A rank just filled this talent. Speaks only when it was the root that opens the signature. */
    public static void rootFilled(ServerPlayer player, Talent talent) {
        if (talent.kind() != Talent.Kind.ROOT || !SkillTuning.current().signatureNeedsRoot()) {
            return;
        }
        Skill skill = talent.skill();
        int level = ProficiencyAttachments.of(player).level(skill);
        if (level >= ProficiencyConfig.procUnlockLevel()) {
            unlocked(player, skill);
        } else {
            player.sendSystemMessage(Component.translatable("proficiency.proc.root_waits",
                            Component.translatable(skill.procKey()).withStyle(ChatFormatting.BOLD),
                            ProficiencyConfig.procUnlockLevel())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (level >= ActiveService.unlockLevel()) {
            player.sendSystemMessage(Component.translatable("proficiency.active.root_opened",
                            Component.translatable(skill.activeKey()).withStyle(ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void unlocked(ServerPlayer player, Skill skill) {
        if (!ProficiencyConfig.procsEnabled()) {
            return;
        }
        player.sendSystemMessage(Component.translatable("proficiency.proc.unlocked",
                        Component.translatable(skill.translationKey()),
                        Component.translatable(skill.procKey()).withStyle(ChatFormatting.BOLD))
                .withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.translatable(skill.procDescKey())
                .withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
    }
}
