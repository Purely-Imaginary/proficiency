package dev.amman.proficiency.hearth.tales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * The sounds and particles of the tales lane, in the mod's own idioms (the level-up chime pair, the
 * talent unlock resonance, the streak's rising note): server side, a handful of particles each, so
 * a client with Particles set to Minimal sees almost none and a busy Hearth costs nothing.
 */
final class TalesFx {

    private TalesFx() {
    }

    /** Tales paid: glyphs fly from the player into the fire, the level-up chime pair. */
    static void paid(ServerPlayer player, BlockPos hearth) {
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.3f);
        ServerLevel level = player.serverLevel();
        Vec3 fire = Vec3.atCenterOf(hearth).add(0.0, 0.9, 0.0);
        Vec3 from = player.position().add(0.0, 1.2, 0.0).subtract(fire);
        // ENCHANT flies from (position + velocity) to position: spawn at the fire, start at the player.
        for (int i = 0; i < 6; i++) {
            level.sendParticles(ParticleTypes.ENCHANT, fire.x, fire.y, fire.z, 0, from.x, from.y + i * 0.1, from.z, 1.0);
        }
    }

    /** A page set down: a page turn and a soft resonance at the lectern. */
    static void page(ServerPlayer author, BlockPos lectern) {
        ServerLevel level = author.serverLevel();
        level.playSound(null, lectern, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.9f, 1.0f);
        author.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.7f, 1.2f);
        level.sendParticles(ParticleTypes.ENCHANT, lectern.getX() + 0.5, lectern.getY() + 1.4, lectern.getZ() + 0.5,
                10, 0.35, 0.3, 0.35, 0.6);
    }

    /** A Feast: warmth for one eater, the streak's rising note and a few sparks. */
    static void warmed(ServerPlayer eater) {
        eater.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 1.5f);
        eater.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.6f, 1.3f);
        ServerLevel level = eater.serverLevel();
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, eater.getX(), eater.getY() + 1.2, eater.getZ(), 6, 0.4, 0.5,
                0.4, 0.02);
        level.sendParticles(ParticleTypes.FLAME, eater.getX(), eater.getY() + 0.4, eater.getZ(), 3, 0.3, 0.1, 0.3, 0.01);
    }

    /** The alarm: the bell resonates, sparks fly off it. */
    static void alarm(ServerLevel level, BlockPos bell) {
        level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.6f, 0.9f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, bell.getX() + 0.5, bell.getY() + 0.5, bell.getZ() + 0.5, 12, 0.4,
                0.4, 0.4, 0.2);
    }

    /** The dinner bell: a bright chime and notes rising off the bell. */
    static void dinner(ServerLevel level, BlockPos bell) {
        level.playSound(null, bell, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2f, 1.1f);
        level.sendParticles(ParticleTypes.NOTE, bell.getX() + 0.5, bell.getY() + 1.1, bell.getZ() + 0.5, 6, 0.4, 0.2,
                0.4, 1.0);
    }

    /** The call home: a long resonance and light rising off the bell. */
    static void comeHome(ServerLevel level, BlockPos bell) {
        level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.4f, 1.2f);
        level.sendParticles(ParticleTypes.END_ROD, bell.getX() + 0.5, bell.getY() + 0.9, bell.getZ() + 0.5, 8, 0.2, 0.4,
                0.2, 0.05);
    }

    /** What a far player hears of the bell: a quiet note, so the call is never silent. */
    static void heardFar(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5f, 0.9f);
    }

    /** A compass bound to the Hearth hums. */
    static void compass(ServerPlayer player) {
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.4f);
    }

    /** A rated tale retold: the talent-unlock pair. */
    static void retold(ServerPlayer author) {
        author.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 1.0f);
        author.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.4f);
    }
}
