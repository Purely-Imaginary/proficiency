package dev.amman.proficiency.skill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import dev.amman.proficiency.platform.bus.Event;
import org.jetbrains.annotations.Nullable;

/**
 * Posted on the NeoForge bus every time a skill's signature proc lands, rolled or frenzied. Talent
 * code that wants "when the proc fires, also do X" subscribes to this instead of editing the handler
 * that rolled it.
 *
 * @param target the living thing the proc was about, when there is one (combat)
 * @param pos    the block the proc was about, when there is one (gathering)
 * @param rolled false during a frenzy, where the proc did not roll but simply happened
 */
public final class SkillProcEvent extends Event {

    private final ServerPlayer player;
    private final Skill skill;
    @Nullable
    private final LivingEntity target;
    @Nullable
    private final BlockPos pos;
    private final boolean rolled;

    public SkillProcEvent(ServerPlayer player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos, boolean rolled) {
        this.player = player;
        this.skill = skill;
        this.target = target;
        this.pos = pos;
        this.rolled = rolled;
    }

    public ServerPlayer player() {
        return player;
    }

    public Skill skill() {
        return skill;
    }

    @Nullable
    public LivingEntity target() {
        return target;
    }

    @Nullable
    public BlockPos pos() {
        return pos;
    }

    public boolean rolled() {
        return rolled;
    }
}
