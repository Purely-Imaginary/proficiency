package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.NightwalkerMath;
import dev.amman.proficiency.skill.NightwalkerService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nightwalker in the world: Moonlit on a kill in the dark, Eclipse keeping mobs off you, and the
 * tree's mechanics (Night Owl, Darkborn Bane, Phantom Ward, Deep Calm, Sanctuary, Hunter's Moon,
 * and the Night Hunter synergy with Sneaking). The XP and the pot are in
 * {@link NightwalkerService}; the numbers in {@link NightwalkerMath}.
 *
 * <p>Nothing here hides you: stealth belongs to Sneaking. Eclipse only makes mobs in the dark lose
 * a target that is already far away, which is seeing them first, not being unseen.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class NightwalkerEvents {

    /** Set on a mob that spawned in the dark. Saved with the mob, so it survives a restart. */
    public static final String DARKBORN_TAG = "proficiency_darkborn";

    /** Spawn types that mean "the world made this mob here", not a player or a command. */
    private static final Set<MobSpawnType> WORLD_SPAWNS = Set.of(MobSpawnType.NATURAL,
            MobSpawnType.SPAWNER, MobSpawnType.STRUCTURE,
            MobSpawnType.REINFORCEMENT, MobSpawnType.JOCKEY, MobSpawnType.PATROL);

    private static final Map<UUID, Float> LAST_EXHAUSTION = new ConcurrentHashMap<>();
    /** Deep Calm re-adds a shorter Darkness; this stops that from being shortened again. */
    private static final ThreadLocal<Boolean> CALMING = ThreadLocal.withInitial(() -> false);

    private NightwalkerEvents() {
    }

    // ---- Moonlit, Hunter's Moon, Night Hunter --------------------------------------------------

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity() instanceof Enemy) || event.getEntity() instanceof Player
                || !NightwalkerService.isDark(player)) {
            return;
        }
        // Hunter's Moon: while Moonlit runs, a kill in the dark renews it without a roll.
        if (NightwalkerService.moonlitRunning(player)
                && TalentService.rank(player, Skill.NIGHTWALKER, "hunters_moon") > 0) {
            NightwalkerService.moonlit(player);
            return;
        }
        // Night Hunter (with Sneaking): a kill made from a crouch in the dark is always a Moonlit.
        if (player.isCrouching() && TalentService.hasSynergy(player, "night_hunter")) {
            ProcService.forceNext(player, Skill.NIGHTWALKER);
        }
        if (ProcService.fire(player, Skill.NIGHTWALKER, event.getEntity())) {
            NightwalkerService.moonlit(player);
        }
    }

    // ---- Eclipse ------------------------------------------------------------------------------

    /** While Eclipse runs, a hostile mob in the dark more than 8 blocks away cannot take you as a target. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player
                && event.getEntity() instanceof Mob mob
                && NightwalkerService.eclipseHides(player, mob)) {
            event.setCanceled(true);
        }
    }

    // ---- Night Owl ----------------------------------------------------------------------------

    /**
     * Night Owl. There is no exhaustion event, so this watches the number, like Lean Times: of
     * whatever it climbed since last tick, at night or in the dark, a share goes back. A drop
     * means the food bar just rolled over, and there is nothing to hand back.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide()) {
            return;
        }
        FoodData food = player.getFoodData();
        float now = food.getExhaustionLevel();
        Float before = LAST_EXHAUSTION.put(player.getUUID(), now);
        if (before == null || now <= before) {
            return;
        }
        int rank = TalentService.rank(player, Skill.NIGHTWALKER, "night_hunger");
        if (rank <= 0 || NightwalkerService.holdsLight(player)
                || !(player.level().isNight() || NightwalkerService.isDark(player))) {
            return;
        }
        float refunded = (float) Math.max(0.0, now - (now - before) * NightwalkerMath.nightOwlRefund(rank));
        food.setExhaustion(refunded);
        LAST_EXHAUSTION.put(player.getUUID(), refunded);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_EXHAUSTION.remove(event.getEntity().getUUID());
    }

    // ---- Darkborn Bane, Phantom Ward ----------------------------------------------------------

    /** Marks a mob the world spawned at block light 0-3. */
    @SubscribeEvent
    public static void onSpawn(MobSpawnEvent.FinalizeSpawn event) {
        // Also where every mob's origin is tagged (spawn egg, command, spawner...), for SpawnOrigin.
        dev.amman.proficiency.skill.SpawnOrigin.tag(event.getEntity(), event.getSpawnType());
        if (!WORLD_SPAWNS.contains(event.getSpawnType())) {
            return;
        }
        BlockPos pos = BlockPos.containing(event.getX(), event.getY(), event.getZ());
        if (event.getLevel().getBrightness(LightLayer.BLOCK, pos) <= NightwalkerMath.DARK_BLOCK_LIGHT) {
            markDarkborn(event.getEntity());
        }
    }

    public static void markDarkborn(Mob mob) {
        mob.getPersistentData().putBoolean(DARKBORN_TAG, true);
    }

    public static boolean isDarkborn(LivingEntity entity) {
        return entity.getPersistentData().getBoolean(DARKBORN_TAG);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide()) {
            return;
        }
        if (target instanceof Player victim) {
            if (event.getSource().getEntity() instanceof Phantom) {
                int ward = TalentService.rank(victim, Skill.NIGHTWALKER, "phantom_ward");
                if (ward > 0) {
                    event.setAmount((float) (event.getAmount() * NightwalkerMath.phantomWard(ward)));
                }
            }
            return;
        }
        if (event.getSource().getEntity() instanceof Player player && isDarkborn(target)) {
            int rank = TalentService.rank(player, Skill.NIGHTWALKER, "darkborn");
            if (rank > 0) {
                event.setAmount((float) (event.getAmount() * NightwalkerMath.darkbornMultiplier(rank)));
            }
        }
    }

    // ---- Deep Calm ----------------------------------------------------------------------------

    /**
     * Deep Calm. An effect's length cannot be changed once made, so the long Darkness is refused
     * and a shorter copy is added in its place.
     */
    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        MobEffectInstance effect = event.getEffectInstance();
        if (CALMING.get() || effect == null || !(effect.getEffect() == MobEffects.DARKNESS)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int rank = TalentService.rank(player, Skill.NIGHTWALKER, "darkness_ward");
        int shorter = NightwalkerMath.darknessTicks(effect.getDuration(), rank);
        if (shorter >= effect.getDuration()) {
            return;
        }
        event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        CALMING.set(true);
        try {
            player.addEffect(new MobEffectInstance(effect.getEffect(), shorter, effect.getAmplifier(),
                    effect.isAmbient(), effect.isVisible(), effect.showIcon()), dev.amman.proficiency.compat.EffectSource.current());
        } finally {
            CALMING.set(false);
        }
    }

    // ---- Sanctuary ----------------------------------------------------------------------------

    /**
     * Sanctuary: no monster spawns on its own within 16 blocks of the respawn point (bed or
     * anchor) of an online player who has it. Vanilla never spawns within 24 blocks of a player
     * anyway, so a radius around the player would do nothing; the respawn point is the home that
     * would otherwise need torches.
     */
    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL || !(event.getEntity() instanceof Enemy)) {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (sanctuaryCovers(level, event.getX(), event.getY(), event.getZ())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /** Whether any online player's Sanctuary covers this spot. */
    public static boolean sanctuaryCovers(ServerLevel level, double x, double y, double z) {
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            BlockPos home = player.getRespawnPosition();
            if (home == null || player.getRespawnDimension() != level.dimension()
                    || TalentService.rank(player, Skill.NIGHTWALKER, "sanctuary") <= 0) {
                continue;
            }
            if (NightwalkerMath.inSanctuary(home.distToCenterSqr(x, y, z))) {
                return true;
            }
        }
        return false;
    }
}
