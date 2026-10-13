package dev.amman.proficiency.platform.event.entity.player;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * A player tries to sleep in a bed, server side: set a {@link Player.BedSleepingProblem} to refuse it
 * (null lets them sleep). NeoForge source: its own {@code CanPlayerSleepEvent}, after vanilla's checks
 * ({@link #getVanillaProblem} is vanilla's answer). Fabric: {@code EntitySleepEvents.ALLOW_SLEEPING},
 * which asks before vanilla's checks, so there the vanilla problem is always null and vanilla still
 * refuses on its own afterwards. Added by the Sieges lane (no sleeping in range while a siege runs).
 */
public class CanPlayerSleepEvent extends PlayerEvent {

    private final BlockPos pos;
    @Nullable
    private final Player.BedSleepingProblem vanillaProblem;
    @Nullable
    private Player.BedSleepingProblem problem;

    public CanPlayerSleepEvent(ServerPlayer player, BlockPos pos, @Nullable Player.BedSleepingProblem vanillaProblem) {
        super(player);
        this.pos = pos;
        this.vanillaProblem = vanillaProblem;
        this.problem = vanillaProblem;
    }

    @Override
    public ServerPlayer getEntity() {
        return (ServerPlayer) super.getEntity();
    }

    /** The bed. */
    public BlockPos getPos() {
        return pos;
    }

    @Nullable
    public Player.BedSleepingProblem getVanillaProblem() {
        return vanillaProblem;
    }

    @Nullable
    public Player.BedSleepingProblem getProblem() {
        return problem;
    }

    /** Refuses the sleep with {@code problem}; null allows it. */
    public void setProblem(@Nullable Player.BedSleepingProblem problem) {
        this.problem = problem;
    }
}
