package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Homebuilder's passive, which works only at home (docs/HOMEBUILDER-SPEC.md, "The passive"): faster
 * movement, longer reach and, with Tidy Home, an item magnet. Plus Scaffolder's softer falls.
 *
 * <ul>
 * <li><b>Speed and reach</b> are transient modifiers with their own ids, set by the per-player
 * second while you are home and removed by it within 2 s of leaving range or of the Hearth breaking,
 * smouldering or being switched off ("home" itself runs out 2 s after the Hearth last saw you).
 * Reach stacks with Mining's and Masonry's, each its own modifier. Transient, so a relog or a
 * respawn never keeps them.</li>
 * <li><b>The magnet</b> pulls item entities (and with Tidy Home 2 experience orbs) towards you every
 * 5 ticks, at most 8 at a time, nearest first, moving them so they arc in. It leaves alone what the
 * base's own machinery is handling: items on a hopper, in water flow, with a pickup delay a mod set,
 * and any player's fresh drop (5 s), and it pulls nothing while you sneak or with the switch off.
 * The area query asks for item entities only (and orbs only with Tidy Home 2), so a base full of
 * mobs, minecarts or armour stands costs it nothing.</li>
 * <li><b>Scaffolder</b> softens a real fall only: an ender pearl's landing is fall damage too in
 * 1.21.1, but the game clears the fall distance before it, so it is told apart by that.</li>
 * </ul>
 */
public final class HomePassive {

    public static final ResourceLocation SPEED_ID = Proficiency.id("homebuilder_speed");
    public static final ResourceLocation REACH_ID = Proficiency.id("homebuilder_reach");

    private HomePassive() {
    }

    // ---- What the passive is now ------------------------------------------------------------------

    /**
     * The passive this player uses this second, 0 away from home: their own (doubled by House Proud)
     * or a friend's shared one, whichever is larger. A share Open House reaches past the range works
     * outside it too.
     */
    public static double bonus(ServerPlayer player) {
        if (!ProficiencyConfig.enabled(Skill.HOMEBUILDER) || !ProficiencyConfig.hearthEnabled()
                || !player.isAlive() || player.isSpectator()) {
            return 0.0;
        }
        return bonus(player, HearthRange.isHome(player));
    }

    /** As {@link #bonus(ServerPlayer)} past its switches, with "at home" already known. */
    private static double bonus(ServerPlayer player, boolean home) {
        double shared = HouseProud.sharedBonus(player, home);
        if (!home) {
            return shared;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        return HomebuilderMath.effectiveBonus(skills.bonus(Skill.HOMEBUILDER),
                ActiveService.isFrenzied(player, Skill.HOMEBUILDER), shared);
    }

    // ---- Speed and reach --------------------------------------------------------------------------

    /** The per-player second: set or clear both modifiers. */
    public static void tick(ServerPlayer player) {
        apply(player, bonus(player));
    }

    /** Sets the speed and reach modifiers for {@code bonus} (0 removes both). Public for the GameTests. */
    public static void apply(Player player, double bonus) {
        set(player.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, HomebuilderMath.speed(bonus),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE), REACH_ID, HomebuilderMath.reach(bonus),
                AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(AttributeInstance attribute, ResourceLocation id, double amount,
            AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        AttributeModifier existing = attribute.getModifier(id);
        if (!(amount > 0)) {
            if (existing != null) {
                attribute.removeModifier(id);
            }
            return;
        }
        if (existing != null && existing.amount() == amount && existing.operation() == operation) {
            return;
        }
        attribute.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    // ---- Scaffolder -------------------------------------------------------------------------------

    /**
     * {@code HearthHooks.INCOMING_DAMAGE}: at home, a fall needs a few more blocks to hurt. Plain fall
     * damage only (not a landing on dripstone spikes), and only from a real fall: the hit comes while
     * the fall distance is still counted, which an ender pearl's landing (fall damage with the distance
     * already cleared) is not.
     */
    static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.getSource().is(DamageTypes.FALL)
                || !(player.fallDistance > 0f) || !ProficiencyConfig.enabled(Skill.HOMEBUILDER)) {
            return;
        }
        int rank = HomebuilderMath.rank(ProficiencyAttachments.of(player), HomebuilderMath.SCAFFOLDER);
        if (rank <= 0 || !HearthRange.isHome(player)) {
            return;
        }
        float left = HomebuilderMath.scaffolderDamage(event.getAmount(), rank);
        if (left <= 0f) {
            event.setCanceled(true);
        } else {
            event.setAmount(left);
        }
    }

    // ---- The magnet -------------------------------------------------------------------------------

    /** Tidy Home's rank for this player, 0 without it. */
    public static int tidyHome(Player player) {
        return HomebuilderMath.rank(ProficiencyAttachments.of(player), HomebuilderMath.TIDY_HOME);
    }

    /** The magnet's radius for this player now, 0 when it is off (no Tidy Home, switched off, away, sneaking). */
    public static double magnetRadius(ServerPlayer player) {
        return magnetRadius(player, tidyHome(player));
    }

    /** As {@link #magnetRadius(ServerPlayer)} with Tidy Home's rank already read. */
    private static double magnetRadius(ServerPlayer player, int tidy) {
        if (tidy <= 0 || player.isShiftKeyDown() || HearthAttachments.of(player).magnetOff()
                || !ProficiencyConfig.enabled(Skill.HOMEBUILDER) || !ProficiencyConfig.hearthEnabled()
                || !player.isAlive() || player.isSpectator() || !HearthRange.isHome(player)) {
            return 0.0;
        }
        double bonus = bonus(player, true);
        if (!(bonus > 0)) {
            return 0.0;
        }
        return HomebuilderMath.magnetRadius(bonus,
                HomebuilderMath.rank(ProficiencyAttachments.of(player), HomebuilderMath.LONG_PULL));
    }

    /**
     * Every 5 ticks for each player: an area query for item entities (and one for orbs with Tidy
     * Home 2), then a nudge for at most 8 of them. Returns how many. The queries are by class, so
     * only items (orbs) are looked at, never the other entities in the box.
     */
    public static int pull(ServerPlayer player) {
        int tidy = tidyHome(player);
        double radius = magnetRadius(player, tidy);
        if (!(radius > 0)) {
            return 0;
        }
        ServerLevel level = player.serverLevel();
        Vec3 target = player.position().add(0, player.getBbHeight() * 0.5, 0);
        AABB box = player.getBoundingBox().inflate(radius);
        double reach = radius * radius;
        Predicate<Entity> near = entity -> entity.isAlive() && entity.distanceToSqr(target) <= reach;
        List<Entity> found = new ArrayList<>(level.getEntitiesOfClass(ItemEntity.class, box,
                item -> near.test(item) && pullable(item, player)));
        if (tidy >= 2) {
            found.addAll(level.getEntitiesOfClass(ExperienceOrb.class, box,
                    orb -> near.test(orb) && !orb.isInWater() && !onHopper(level, orb)));
        }
        if (found.isEmpty()) {
            return 0;
        }
        found.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(target)));
        int moved = 0;
        for (Entity entity : found) {
            if (moved >= HomebuilderMath.MAGNET_MAX_PER_PULL) {
                break;
            }
            Vec3 to = target.subtract(entity.position());
            if (to.lengthSqr() < HomebuilderMath.MAGNET_DONE * HomebuilderMath.MAGNET_DONE) {
                // Close enough for vanilla's own pickup; counting it keeps the 8 nearest honest.
                moved++;
                continue;
            }
            double[] v = HomebuilderMath.pull(to.x, to.y, to.z, tidy >= 3, entity.onGround());
            entity.setDeltaMovement(v[0], v[1], v[2]);
            entity.hasImpulse = true;
            moved++;
        }
        return moved;
    }

    /** Whether the magnet may take this item: the base's machinery and other players' hands come first. */
    public static boolean pullable(ItemEntity item, ServerPlayer player) {
        if (item.hasPickUpDelay() || item.isInWater() || onHopper(item.level(), item)) {
            return false;
        }
        // A drop a player just made (handing something over, throwing it in the lava) stays put 5 s.
        Entity owner = item.getOwner();
        return !(owner instanceof Player) || item.getAge() >= HomebuilderMath.FRESH_DROP_TICKS;
    }

    /** On a hopper (or right above one): a sorter or a collection line is working on it. */
    static boolean onHopper(net.minecraft.world.level.Level level, Entity entity) {
        BlockPos at = entity.blockPosition();
        return level.getBlockState(at).getBlock() instanceof HopperBlock
                || level.getBlockState(at.below()).getBlock() instanceof HopperBlock;
    }
}
