package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.perk.TalentService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pays the Nightwalker skill and runs its signature and ability. The numbers are in
 * {@link NightwalkerMath}.
 *
 * <p>The anti-farm rules are the design, and they are all here:
 * <ul>
 * <li>No source of its own. Every other skill's grant earned in the dark puts a share into a pot
 * ({@link SharePot}, the same code Social uses); nothing else does.</li>
 * <li>Nightwalker's own grants, Social's payouts and an op's {@code addxp} never feed it.</li>
 * <li>The outdoor trickle counts only seconds where you moved and looked around lately, and not
 * in bed, in water or riding: an AFK player or a water-stream farm earns nothing.</li>
 * <li>A death empties the pot and the partial minute, like every other XP bar.</li>
 * </ul>
 */
public final class NightwalkerService {

    /** The log and feed line for the share of grants earned in the dark. */
    public static final String DARK_SOURCE = "proficiency.xplog.source.darkness";
    /** The log and feed line for the outdoor night trickle. */
    public static final String NIGHT_SOURCE = "proficiency.xplog.source.night_out";
    private static final String COMMAND_SOURCE = "proficiency.xplog.source.command";

    private static final SharePot POT =
            new SharePot(Skill.NIGHTWALKER, DARK_SOURCE, NightwalkerMath.PAY_INTERVAL_TICKS);
    private static final Map<UUID, NightwalkerMath.Trickle> TRICKLES = new ConcurrentHashMap<>();
    /** Game time when each player's Moonlit ends, for Hunter's Moon. */
    private static final Map<UUID, Long> MOONLIT_UNTIL = new ConcurrentHashMap<>();

    private NightwalkerService() {
    }

    // ---- Darkness -----------------------------------------------------------------------------

    /** Whether this spot is dark: block light 0-3, and night or low sky light. */
    public static boolean isDark(Level level, BlockPos pos) {
        return NightwalkerMath.isDark(level.getBrightness(LightLayer.BLOCK, pos),
                level.getBrightness(LightLayer.SKY, pos), isNight(level));
    }

    /**
     * Whether it is night now, worked out from the time and weather. Not {@code level.isNight()}:
     * on a client that reads a sky-darkness value the client level computes once, when it is
     * created, so Dark Sight under the open sky stayed off all night for a player who joined by
     * day, until they rejoined (found in the real client 2026-10-07). The server recomputes the
     * same value every tick, so both sides now agree.
     */
    public static boolean isNight(Level level) {
        return NightwalkerMath.isNight(level.dimensionType().hasFixedTime(), level.getTimeOfDay(1.0f),
                level.getRainLevel(1.0f), level.getThunderLevel(1.0f));
    }

    /** Whether an entity stands in the dark, measured at its eyes (the feet can be inside a slab). */
    public static boolean isDark(Entity entity) {
        // A light in either hand switches Nightwalker off: you are not sneaking through the dark
        // with a torch. Every dark check for a player goes through here.
        return !holdsLight(entity) && isDark(entity.level(), BlockPos.containing(entity.getEyePosition()));
    }

    private static final net.minecraft.tags.TagKey<net.minecraft.world.item.Item> HELD_LIGHTS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    net.minecraft.resources.ResourceLocation.tryParse("proficiency:held_lights"));

    /** Whether a living entity holds a light in the main or off hand. */
    public static boolean holdsLight(Entity entity) {
        return entity instanceof net.minecraft.world.entity.LivingEntity living
                && (isLight(living.getMainHandItem()) || isLight(living.getOffhandItem()));
    }

    /** A block item whose block emits light (torches, lanterns, glowstone, modded), or the held_lights tag. */
    public static boolean isLight(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.is(HELD_LIGHTS)) {
            return true;
        }
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem block
                && block.getBlock().defaultBlockState().getLightEmission() > 0;
    }

    // ---- XP -----------------------------------------------------------------------------------

    /**
     * Called by {@link SkillService} for every grant of every skill.
     *
     * @param baseAmount the grant before any multiplier
     * @param tier       the first-time tier factor, 1.0 for an ordinary grant
     */
    static void onGrant(ServerPlayer player, Skill skill, @Nullable String source, double baseAmount,
            double tier) {
        if (skill == Skill.NIGHTWALKER || COMMAND_SOURCE.equals(source)
                || SocialService.SOURCE.equals(source)
                || !ProficiencyConfig.enabled(Skill.NIGHTWALKER) || !isDark(player)) {
            return;
        }
        POT.add(player.getUUID(), NightwalkerMath.share(baseAmount, ProficiencyConfig.xpRate(skill), tier,
                ProficiencyConfig.nightShare()));
    }

    /** Base Nightwalker XP collected from grants in the dark and not paid yet. */
    public static double pending(ServerPlayer player) {
        return POT.pending(player.getUUID());
    }

    /** Pays the pot now. The tick does it every 5 seconds; tests call it directly. */
    public static float flush(ServerPlayer player) {
        return POT.flush(player);
    }

    /** Once a second from the server tick: the pot, the outdoor trickle, and Eclipse. */
    public static void tick(ServerPlayer player) {
        if (POT.due(player)) {
            POT.flush(player);
        }
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        step(player, sample(player));
        if (ActiveService.isFrenzied(player, Skill.NIGHTWALKER)) {
            eclipseSweep(player);
        }
    }

    /** What the trickle needs to know about this second. */
    static NightwalkerMath.Second sample(ServerPlayer player) {
        Level level = player.level();
        BlockPos eyes = BlockPos.containing(player.getEyePosition());
        boolean outdoors = level.dimensionType().hasSkyLight() && level.canSeeSky(eyes);
        return new NightwalkerMath.Second(outdoors, level.isNight(), isDark(player),
                player.isSleeping(), player.isPassenger(), player.isInWater(),
                player.getX(), player.getZ(), player.getYRot(), player.getXRot());
    }

    /**
     * Feeds one second to the player's trickle counter and pays a full active minute. Returns the
     * XP paid, 0 most seconds. Public so a GameTest can drive it with made-up seconds.
     */
    public static float step(ServerPlayer player, NightwalkerMath.Second second) {
        double xp = ProficiencyConfig.nightTrickleXp();
        if (!(xp > 0) || !ProficiencyConfig.enabled(Skill.NIGHTWALKER)) {
            return 0f;
        }
        NightwalkerMath.Trickle trickle =
                TRICKLES.computeIfAbsent(player.getUUID(), id -> new NightwalkerMath.Trickle());
        if (!trickle.step(second)) {
            return 0f;
        }
        return SkillService.grant(player, Skill.NIGHTWALKER, xp, NIGHT_SOURCE);
    }

    /** Active seconds counted toward the next trickle payment. For tests. */
    public static int trickleSeconds(ServerPlayer player) {
        NightwalkerMath.Trickle trickle = TRICKLES.get(player.getUUID());
        return trickle == null ? 0 : trickle.active();
    }

    // ---- Moonlit (the signature) ---------------------------------------------------------------

    /**
     * Moonlit: Night Vision and Speed for 10 seconds, longer with proc power. Hunter's Moon adds
     * Strength and lets a kill in the dark renew it while it runs.
     */
    public static void moonlit(ServerPlayer player) {
        int ticks = NightwalkerMath.moonlitTicks(ProcService.power(player, Skill.NIGHTWALKER));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false, true));
        if (TalentService.rank(player, Skill.NIGHTWALKER, "hunters_moon") > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, false, true));
        }
        MOONLIT_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    /** Whether this player's Moonlit is still running. */
    public static boolean moonlitRunning(ServerPlayer player) {
        Long until = MOONLIT_UNTIL.get(player.getUUID());
        long now = player.level().getGameTime();
        return until != null && now < until && until - now <= 20L * 60 * 10;
    }

    // ---- Eclipse (the ability) -----------------------------------------------------------------

    /** Whether a hostile mob should lose track of this player now: Eclipse on, far, and in the dark. */
    public static boolean eclipseHides(ServerPlayer player, Mob mob) {
        return mob instanceof Enemy && ActiveService.isFrenzied(player, Skill.NIGHTWALKER)
                && NightwalkerMath.eclipseHides(mob.distanceToSqr(player), isDark(mob));
    }

    /** Once a second while Eclipse runs: hostile mobs in the dark beyond 8 blocks drop you. */
    public static void eclipseSweep(ServerPlayer player) {
        for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48.0),
                mob -> mob instanceof Enemy && mob.isAlive() && mob.getTarget() == player)) {
            if (eclipseHides(player, mob)) {
                loseTrack(mob);
            }
        }
    }

    /** Drops the mob's target, in the field and, for brain-driven mobs, in the brain. */
    public static void loseTrack(Mob mob) {
        mob.setTarget(null);
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    // ---- Lifecycle ----------------------------------------------------------------------------

    /** A death wipes the XP bars, so the unpaid pot and the partial minute go too. */
    public static void onDeath(UUID player) {
        POT.wipe(player);
        NightwalkerMath.Trickle trickle = TRICKLES.get(player);
        if (trickle != null) {
            trickle.reset();
        }
        MOONLIT_UNTIL.remove(player);
    }

    public static void forget(UUID player) {
        POT.forget(player);
        TRICKLES.remove(player);
        MOONLIT_UNTIL.remove(player);
    }
}
