package dev.amman.proficiency.skill;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who last hurt a mob with which weapon skill, so a kill the weapon did not cause directly (lava
 * after a sword hit, a fall, fire) still credits the skill that did the work. Kept in the mob's own
 * saved data, so nothing outlives the mob.
 */
public final class KillCredit {

    /** How long after a hit an environmental death still credits it: five seconds. */
    public static final long WINDOW_TICKS = 100;

    private static final String TAG = "proficiency_last_hit";

    private KillCredit() {
    }

    public static void record(LivingEntity target, Player player, Skill skill) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("p", player.getUUID());
        tag.putString("s", skill.id());
        tag.putLong("t", target.level().getGameTime());
        dev.amman.proficiency.platform.EntityData.of(target).put(TAG, tag);
    }

    /** The skill of this player's last hit on the target if it was recent enough, else null. */
    @Nullable
    public static Skill recent(LivingEntity target, UUID player) {
        CompoundTag data = dev.amman.proficiency.platform.EntityData.of(target);
        if (!data.contains(TAG, 10)) {
            return null;
        }
        CompoundTag tag = data.getCompound(TAG);
        if (!tag.hasUUID("p") || !player.equals(tag.getUUID("p"))) {
            return null;
        }
        long age = target.level().getGameTime() - tag.getLong("t");
        if (age < 0 || age > WINDOW_TICKS) {
            return null;
        }
        return Skill.byId(tag.getString("s"));
    }

    /** The UUID of whoever hit the target last within the window, or null. */
    @Nullable
    public static UUID recentHitter(LivingEntity target) {
        CompoundTag data = dev.amman.proficiency.platform.EntityData.of(target);
        if (!data.contains(TAG, 10)) {
            return null;
        }
        CompoundTag tag = data.getCompound(TAG);
        long age = target.level().getGameTime() - tag.getLong("t");
        return tag.hasUUID("p") && age >= 0 && age <= WINDOW_TICKS ? tag.getUUID("p") : null;
    }
}
