package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingBreatheEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Running, Sneaking, Jumping and Swimming. These are the only skills that pay out per metre rather
 * than per action, so they need somewhere to keep the fractional remainder between ticks.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class MovementEvents {

    private static final ResourceLocation SPRINT_MODIFIER = Proficiency.id("sprint_speed");
    private static final ResourceLocation SNEAK_MODIFIER = Proficiency.id("sneak_speed");
    private static final ResourceLocation SWIM_MODIFIER = Proficiency.id("swim_speed");
    private static final ResourceLocation PARKOUR_MODIFIER = Proficiency.id("parkour_step");

    /** Parkour lifts vanilla's 0.6 step to a full block, the step height a horse already has. */
    private static final double PARKOUR_STEP = 0.4;

    /** XP per metre. Twenty metres of sprinting is worth one swing of a sword. */
    private static final double RUN_XP_PER_METRE = 0.05;
    private static final double SNEAK_XP_PER_METRE = 0.10;
    private static final double SWIM_XP_PER_METRE = 0.10;

    private static final Map<UUID, Tracker> TRACKERS = new ConcurrentHashMap<>();

    private MovementEvents() {
    }

    private static final class Tracker {
        double lastX;
        double lastY;
        double lastZ;
        boolean seeded;
        final MovementPayout run = new MovementPayout();
        final MovementPayout sneak = new MovementPayout();
        final MovementPayout swim = new MovementPayout();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            applySpeedModifiers(player);
            return;
        }

        Tracker tracker = TRACKERS.computeIfAbsent(player.getUUID(), id -> new Tracker());
        double dx = player.getX() - tracker.lastX;
        double dy = player.getY() - tracker.lastY;
        double dz = player.getZ() - tracker.lastZ;
        boolean seeded = tracker.seeded;
        tracker.lastX = player.getX();
        tracker.lastY = player.getY();
        tracker.lastZ = player.getZ();
        tracker.seeded = true;

        applySpeedModifiers(player);

        if (!seeded || player.isPassenger() || player.getAbilities().flying) {
            return;
        }
        // A teleport or a portal would otherwise pay out thousands of metres in one tick.
        double stepSquared = dx * dx + dy * dy + dz * dz;
        if (stepSquared > 100.0) {
            return;
        }

        if (player.isSwimming() || player.isInWater()) {
            double metres = Math.sqrt(stepSquared);
            payOut(player, Skill.SWIMMING, tracker.swim, metres * SWIM_XP_PER_METRE);
        } else if (player.isSprinting() && player.onGround()) {
            double metres = Math.sqrt(dx * dx + dz * dz);
            payOut(player, Skill.RUNNING, tracker.run, metres * RUN_XP_PER_METRE);
            refundSprintHunger(player, FoodConstants.EXHAUSTION_SPRINT * metres);
        } else if (player.isCrouching()) {
            double metres = Math.sqrt(dx * dx + dz * dz);
            payOut(player, Skill.SNEAKING, tracker.sneak, metres * SNEAK_XP_PER_METRE);
        }
    }

    /**
     * Pays movement XP in small grants (see {@link MovementPayout}) so a steady run, swim or sneak
     * keeps the tempo chain alive. Jumping is not here: it pays per jump, which is already frequent
     * enough. The XP feed merges consecutive movement grants into one line, with exact totals.
     */
    private static void payOut(Player player, Skill skill, MovementPayout meter, double earned) {
        double paid = meter.add(earned);
        if (paid <= 0) {
            return;
        }
        SkillService.grant(player, skill, paid, switch (skill) {
            case RUNNING -> "proficiency.xplog.source.sprinting";
            case SNEAKING -> "proficiency.xplog.source.sneaking";
            case SWIMMING -> "proficiency.xplog.source.swimming";
            default -> null;
        });

        // One roll per whole point of XP earned, not per grant, so a proc is still roughly one
        // every hundred metres now that grants are smaller and more frequent.
        for (int roll = meter.procRolls(paid); roll > 0; roll--) {
            if (!ProcService.fire(player, skill)) {
                continue;
            }
            // Adrenaline, Shadowmeld and Current Rider: proc power is read as a longer buff.
            double power = ProcService.power(player, skill);
            switch (skill) {
                case RUNNING -> player.addEffect(
                        new MobEffectInstance(MobEffects.MOVEMENT_SPEED, (int) (120 * power), 1));
                case SNEAKING -> player.addEffect(
                        new MobEffectInstance(MobEffects.INVISIBILITY, (int) (80 * power), 0));
                case SWIMMING -> player.addEffect(
                        new MobEffectInstance(MobEffects.DOLPHINS_GRACE, (int) (120 * power), 0));
                default -> {
                    // Jumping rolls on the landing instead, where there is something to cancel.
                }
            }
        }
    }

    /**
     * Long Haul, whose node id is still "endurance". There is no exhaustion event, so this hands
     * back its share of what vanilla just charged for the same metres (0.1 per metre on the
     * ground, 0.2 per sprint-jump). A refund that lands on the tick the food bar rolls over is
     * lost, which is a sliver of one hunger point.
     */
    private static void refundSprintHunger(Player player, double charged) {
        int rank = TalentService.rank(player, Skill.RUNNING, "endurance");
        if (rank <= 0 || charged <= 0) {
            return;
        }
        FoodData food = player.getFoodData();
        double refund = charged * Math.min(1.0, rank * 0.10);
        food.setExhaustion((float) Math.max(0.0, food.getExhaustionLevel() - refund));
    }

    /**
     * Speed is granted as transient attribute modifiers that come and go with the stance, so the
     * bonus applies to sprinting without also making a walking player faster.
     */
    private static void applySpeedModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean inWater = player.isSwimming() || player.isInWater();
        setModifier(speed, SWIM_MODIFIER,
                inWater ? SkillService.bonus(player, Skill.SWIMMING) : 0);
        setModifier(speed, SPRINT_MODIFIER,
                !inWater && player.isSprinting() ? SkillService.bonus(player, Skill.RUNNING) : 0);
        setModifier(speed, SNEAK_MODIFIER,
                !inWater && !player.isSprinting() && player.isCrouching()
                        ? SkillService.bonus(player, Skill.SNEAKING) : 0);

        // Parkour: only while sprinting, so a walking player can still stop at a ledge. Step-up is
        // resolved by whichever side moves the player, which for a player is the client.
        AttributeInstance step = player.getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get());
        if (step != null) {
            boolean parkour = !inWater && player.isSprinting()
                    && TalentService.rank(player, Skill.RUNNING, "parkour") > 0;
            setModifier(step, PARKOUR_MODIFIER, parkour ? PARKOUR_STEP : 0,
                    AttributeModifier.Operation.ADDITION);
        }
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount) {
        setModifier(attribute, id, amount, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount,
            AttributeModifier.Operation operation) {
        AttributeModifier existing = attribute.getModifier(dev.amman.proficiency.compat.Attr.uuid(id));
        if (amount <= 0) {
            if (existing != null) {
                attribute.removeModifier(dev.amman.proficiency.compat.Attr.uuid(id));
            }
            return;
        }
        if (existing != null && existing.getAmount() == amount) {
            return;
        }
        dev.amman.proficiency.compat.Attr.setTransient(attribute, dev.amman.proficiency.compat.Attr.mod(id, amount, operation));
    }

    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        double bonus = SkillService.bonus(player, Skill.JUMPING);
        if (bonus > 0) {
            // maxBonus is read as extra height; height goes with the square of the launch speed.
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y * dev.amman.proficiency.skill.SkillPassives.jumpLaunch(bonus), motion.z);
            player.hasImpulse = true;
        }
        if (player.isSprinting()) {
            // Leap. Both sides, like the height above: the client is what actually moves.
            int leap = TalentService.rank(player, Skill.JUMPING, "leap");
            if (leap > 0) {
                Vec3 motion = player.getDeltaMovement();
                double stretch = 1.0 + leap * 0.05;
                player.setDeltaMovement(motion.x * stretch, motion.y, motion.z * stretch);
                player.hasImpulse = true;
            }
        }
        if (!player.level().isClientSide()) {
            SkillService.grant(player, Skill.JUMPING, 0.2, "proficiency.xplog.source.jumping");
            if (player.isSprinting()) {
                refundSprintHunger(player, FoodConstants.EXHAUSTION_SPRINT_JUMP);
            }
        }
    }

    /**
     * The other half of Jumping: you land better than you used to, and sometimes not at all.
     *
     * <p>Every fall talent is settled in this one handler, so their order is fixed instead of left
     * to two subscribers at the same priority. The passive shortens the fall; Feather Weight and
     * Cat Landing scale the damage multiplier, so the three compose multiplicatively. Zephyr's cap
     * is not here: it caps the damage itself, in {@link TalentMovementEvents}.
     */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        float fallen = event.getDistance();
        double bonus = SkillService.bonus(player, Skill.JUMPING);
        if (bonus > 0) {
            event.setDistance((float) (event.getDistance() * dev.amman.proficiency.skill.SkillPassives.fallFactor(bonus)));
        }
        double softer = TalentMovementEvents.fallDamageFactor(player);
        if (softer < 1.0) {
            event.setDamageMultiplier((float) (event.getDamageMultiplier() * softer));
        }
        TalentMovementEvents.stomp(player, fallen);
        // Only worth announcing on a fall that was going to hurt.
        if (event.getDistance() > 3.5f && ProcService.fire(player, Skill.JUMPING)) {
            event.setDistance(0f);
            if (TalentService.rank(player, Skill.JUMPING, "bounce") > 0) {
                TalentMovementEvents.bounce(player);
            }
        }
    }

    /** Swimming holds its breath longer. */
    @SubscribeEvent
    public static void onBreathe(LivingBreatheEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.canBreathe()) {
            return;
        }
        double bonus = SkillService.bonus(player, Skill.SWIMMING);
        if (bonus <= 0) {
            return;
        }
        // Air is consumed one unit per tick, so the bonus has to buy whole skipped ticks.
        if (player.level().getRandom().nextDouble() < dev.amman.proficiency.skill.SkillPassives.airSkip(bonus)) {
            event.setConsumeAirAmount(0);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        TRACKERS.remove(event.getEntity().getUUID());
    }
}
