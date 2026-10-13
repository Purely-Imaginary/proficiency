package dev.amman.proficiency.platform.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * May this entity change the world right now (the {@code mobGriefing} game rule, asked per entity)?
 * {@link #canGrief} starts at the game rule; a listener can allow or forbid it. Server side.
 *
 * <p>NeoForge source: its own {@code EntityMobGriefingEvent}, from every place vanilla reads the rule
 * for an entity (mob explosions, door breaking, farmland trampling, crop eating, ...). Fabric: posted
 * from two mixins only, a mob-caused explosion ({@code Level.explode} with the MOB interaction) and
 * farmland trampling; elsewhere the Fabric line reads the plain rule. Added by the Sieges lane
 * (siege mobs never grief), so the Fabric sources are the ones sieges need.
 */
public class EntityMobGriefingEvent extends EntityEvent {

    private final boolean mobGriefingEnabled;
    private boolean canGrief;

    public EntityMobGriefingEvent(Level level, Entity entity) {
        this(entity, level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING));
    }

    public EntityMobGriefingEvent(Entity entity, boolean mobGriefingEnabled) {
        super(entity);
        this.mobGriefingEnabled = mobGriefingEnabled;
        this.canGrief = mobGriefingEnabled;
    }

    /** The game rule. */
    public boolean isMobGriefingEnabled() {
        return mobGriefingEnabled;
    }

    public boolean canGrief() {
        return canGrief;
    }

    public void setCanGrief(boolean canGrief) {
        this.canGrief = canGrief;
    }
}
