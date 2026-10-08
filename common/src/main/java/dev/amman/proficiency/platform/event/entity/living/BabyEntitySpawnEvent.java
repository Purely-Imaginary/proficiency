package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.Event;
import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Two animals bred; the child may be replaced, or the birth canceled. */
public class BabyEntitySpawnEvent extends Event implements ICancellableEvent {

    private final Mob parentA;
    private final Mob parentB;
    @Nullable
    private final Player causedByPlayer;
    @Nullable
    private AgeableMob child;

    public BabyEntitySpawnEvent(Mob parentA, Mob parentB, @Nullable AgeableMob proposedChild) {
        Player cause = null;
        if (parentA instanceof Animal a) {
            cause = a.getLoveCause();
        }
        if (cause == null && parentB instanceof Animal b) {
            cause = b.getLoveCause();
        }
        this.parentA = parentA;
        this.parentB = parentB;
        this.causedByPlayer = cause;
        this.child = proposedChild;
    }

    public Mob getParentA() {
        return parentA;
    }

    public Mob getParentB() {
        return parentB;
    }

    @Nullable
    public Player getCausedByPlayer() {
        return causedByPlayer;
    }

    @Nullable
    public AgeableMob getChild() {
        return child;
    }

    public void setChild(@Nullable AgeableMob proposedChild) {
        this.child = proposedChild;
    }
}
