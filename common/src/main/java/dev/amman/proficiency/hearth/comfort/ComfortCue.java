package dev.amman.proficiency.hearth.comfort;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/**
 * The moment a Comfort kind starts to count: the level-up idiom of the mod (a chime only the people
 * at home hear, eight happy-villager sparks over the Hearth, a gold line on the action bar), nothing a
 * client has to draw. The note climbs with the Comfort; the twelfth kind adds the level-up fanfare and
 * a bold line. Kinds that stop counting are silent: the tracker and the panel show the number.
 */
final class ComfortCue {

    private ComfortCue() {
    }

    /**
     * @param home       the players at the Hearth ({@code HearthPresence.inRange}): the only ones who see and hear it
     * @param comfort    the Comfort now
     * @param max        the most it can be (12 shipped)
     * @param gained     the kinds that just started to count
     * @param wasNotFull whether the Comfort was below {@code max} before (the fanfare is for the moment it gets there)
     */
    static void play(ServerLevel level, BlockPos hearth, List<ServerPlayer> home, int comfort, int max,
            List<String> gained, boolean wasNotFull) {
        boolean complete = max > 0 && comfort >= max && wasNotFull;
        List<Component> names = new ArrayList<>();
        for (String kind : gained) {
            names.add(ComfortCommand.name(kind));
        }
        Component line = complete
                ? Component.translatable("proficiency.hearth.comfort.complete", comfort, max)
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                : Component.translatable("proficiency.hearth.comfort.gained", comfort, max,
                        ComponentUtils.formatList(names, Component.literal(", "))).withStyle(ChatFormatting.GOLD);
        float pitch = 0.8f + 0.4f * (max > 0 ? Math.min(1f, comfort / (float) max) : 0f);
        for (ServerPlayer player : home) {
            player.displayClientMessage(line, true);
            // Only the people at home hear it: a shared base would be unbearable otherwise.
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f, pitch);
            if (complete) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.0f);
            }
        }
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, hearth.getX() + 0.5, hearth.getY() + 1.2, hearth.getZ() + 0.5,
                8, 0.35, 0.45, 0.35, 0.02);
    }
}
