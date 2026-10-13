package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tier changes of one Hearth: every pool's effective tier is kept in the lane's tag, compared after
 * a credit that raised a pool's charge tier, after a cut, and once a second (the ceiling and Comfort
 * move tiers too).
 *
 * <ul>
 * <li>Any change sends {@code hearth_state} (reason {@code TIER}) to the players in range.</li>
 * <li>A tier the pool has not been told before (see {@link ChargeRules#lowerHighWater}) fires
 * {@link HearthEvents#TIER_UP} and tells the players in range in chat, naming the week's top feeder
 * of that pool, with the level-up chime and a burst of flame at the Hearth.</li>
 * <li>While the Hearth smoulders nothing is compared: relighting brings the tiers back silently.</li>
 * </ul>
 */
public final class ChargeTiers {

    static final String LANE = "charge";
    static final String TIERS = "tiers";
    static final String TOLD = "told";

    private ChargeTiers() {
    }

    /** Compares the tiers with the last ones seen; sends, fires and announces what changed. */
    public static void check(ServerLevel level, HearthBlockEntity hearth) {
        HearthState state = hearth.state();
        if (state.smouldering()) {
            return;
        }
        HearthData data = Hearths.data();
        CompoundTag tag = hearth.laneData(LANE);
        int[] now = ChargeRules.effectiveTiers(state, data);
        if (!tag.contains(TIERS, Tag.TAG_INT_ARRAY) || tag.getIntArray(TIERS).length != HearthPool.COUNT) {
            // First sight of this Hearth (just lit): nothing to announce yet.
            tag.putIntArray(TIERS, now);
            tag.putIntArray(TOLD, now);
            hearth.setChanged();
            return;
        }
        int[] before = tag.getIntArray(TIERS);
        int[] storedTold = tag.contains(TOLD, Tag.TAG_INT_ARRAY) ? tag.getIntArray(TOLD) : new int[0];
        int[] told = ChargeRules.lowerHighWater(storedTold, ChargeRules.chargeTiers(state, data));
        List<HearthPool> news = ChargeRules.rose(told, now);
        for (HearthPool pool : news) {
            told[pool.ordinal()] = now[pool.ordinal()];
        }
        boolean changed = !Arrays.equals(before, now);
        if (!changed && Arrays.equals(told, storedTold) && news.isEmpty()) {
            return;
        }
        tag.putIntArray(TIERS, now);
        tag.putIntArray(TOLD, told);
        hearth.setChanged();

        BlockPos pos = hearth.getBlockPos();
        List<ServerPlayer> near = playersInRange(level, pos);
        for (HearthPool pool : news) {
            announce(level, hearth, pool, before[pool.ordinal()], now[pool.ordinal()], near);
        }
        if (changed && !near.isEmpty()) {
            HearthStatePayload payload = HearthNet.stateOf(hearth, HearthStatePayload.TIER);
            for (ServerPlayer player : near) {
                HearthNet.sendState(player, payload);
            }
        }
    }

    /** The tier event, then the chat line, the chime and the flame. */
    static void announce(ServerLevel level, HearthBlockEntity hearth, HearthPool pool, int from, int to,
            List<ServerPlayer> near) {
        HearthLedger ledger = HearthCharge.rollToToday(hearth);
        KeeperRows.Top top = KeeperRows.top(ledger, pool);
        BlockPos pos = hearth.getBlockPos().immutable();
        HearthEvents.TIER_UP.fire(new HearthEvents.TierUp(level, pos, pool, from, to,
                top == null ? null : top.player(), top == null ? null : top.source()));
        Component line = ChargeText.tierUp(pool, to, top,
                KeeperRows.of(ledger, pool, HearthLedger.Window.WEEK, 3));
        for (ServerPlayer player : near) {
            player.sendSystemMessage(line);
            // The level-up chime, a step lower: the Hearth's moment, not a skill's.
            player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.6f,
                    0.8f + 0.05f * to);
            if (to >= HearthData.TIERS) {
                player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.4f, 0.9f);
            }
        }
        level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                6 + 2 * to, 0.25, 0.35, 0.25, 0.02);
        level.sendParticles(to >= HearthData.TIERS ? ParticleTypes.END_ROD : ParticleTypes.LAVA,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, to >= HearthData.TIERS ? 12 : 3,
                0.3, 0.3, 0.3, 0.01);
    }

    /** Every living, non-spectating player standing in this Hearth's range, AFK or not. */
    public static List<ServerPlayer> playersInRange(ServerLevel level, BlockPos pos) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && HearthRange.homeIs(level, pos, player.position())) {
                out.add(player);
            }
        }
        return out;
    }
}
