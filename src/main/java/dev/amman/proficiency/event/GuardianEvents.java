package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.item.FriendCompassItem;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.GuardianMath;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Holder;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import dev.amman.proficiency.compat.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guardian: protecting other players (idea 38). It owns the XP (five sources, all for protecting
 * someone else), the passive (players near you take less damage), Intercept, Shield Wall and the
 * tree's mechanics. The numbers are in {@link GuardianMath}.
 *
 * <p>Blocking keeps Bastion (players near a raised shield take less damage) and pays for your own
 * blocks. Endurance pays for damage you take and keeps your own survival. Guardian pays only when
 * the protection is for another player, with or without a shield.
 *
 * <p>Anti-farm rules, all here:
 * <ul>
 * <li>Every source needs another player (the ally): alive, not a spectator, not you. With no
 * player near, nothing pays.</li>
 * <li>Every source is scaled by the ally's danger: x0.25 at full health, x2 near death.</li>
 * <li>Damage from a player, or from someone's pet, never pays and never counts as danger: two
 * friends cannot farm each other. Hazards (falls, fire, cactus) do not count either: a heal
 * counts only if a mob hurt the ally in the last 30 s, and only a mob's hit starts a close call.</li>
 * <li>One mob pays cover and block XP for at most 40 damage in total (saved on the mob); one hit
 * counts at most 20.</li>
 * <li>A heal pays only for the health it really gives back, never the overheal.</li>
 * <li>A close call pays once per ally per guardian in 5 minutes.</li>
 * <li>Survival or adventure only.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class GuardianEvents {

    /** On a mob: the damage it already paid cover and block XP for. */
    public static final String PAID_TAG = "proficiency_guardian_paid";
    /** On a mob: the player it turned away from, who it turned to, when, and whether it was pulled. */
    private static final String TURN_ALLY = "proficiency_guardian_turn_ally";
    private static final String TURN_BY = "proficiency_guardian_turn_by";
    private static final String TURN_AT = "proficiency_guardian_turn_at";
    private static final String TURN_PULLED = "proficiency_guardian_turn_pulled";
    /** On a mob: the last player who hit it, and when. Set before vanilla's own memory is. */
    private static final String HIT_BY = "proficiency_guardian_hit_by";
    private static final String HIT_AT = "proficiency_guardian_hit_at";
    /** On a mob: the last player it hurt, and when. The avenger memory. */
    private static final String HURT_ALLY = "proficiency_guardian_hurt_ally";
    private static final String HURT_AT = "proficiency_guardian_hurt_at";
    /** On a mob: Taunt's lock. */
    private static final String TAUNT_BY = "proficiency_guardian_taunt_by";
    private static final String TAUNT_UNTIL = "proficiency_guardian_taunt_until";
    /** On a player: when their Sworn Shield last fired. */
    private static final String SWORN_AT = "proficiency_guardian_sworn_at";

    public static final String SOURCE_COVER = "proficiency.xplog.source.guardian_cover";
    public static final String SOURCE_BLOCK = "proficiency.xplog.source.guardian_block";
    public static final String SOURCE_AVENGER = "proficiency.xplog.source.guardian_avenger";
    public static final String SOURCE_HEAL = "proficiency.xplog.source.guardian_heal";
    public static final String SOURCE_REVIVE = "proficiency.xplog.source.guardian_revive";

    private static final ResourceLocation WALL_ARMOUR = Proficiency.id("guardian_shield_wall_armour");
    private static final ResourceLocation WALL_TOUGHNESS = Proficiency.id("guardian_shield_wall_toughness");

    /** Per player: the last time a mob (nobody's pet) hurt them. */
    private static final Map<UUID, Long> WORLD_HURT = new ConcurrentHashMap<>();
    /** Per player: who gave them their Regeneration, and until when. */
    private static final Map<UUID, Regen> REGEN_BY = new ConcurrentHashMap<>();
    /** Close calls being watched. Server thread only. */
    private static final List<Watch> WATCHES = new ArrayList<>();
    /** "guardian|ally" to the game time a close call last paid. */
    private static final Map<String, Long> REVIVE_PAID = new ConcurrentHashMap<>();
    /** Per guardian: the last Mending Guard heal. */
    private static final Map<UUID, Long> BLOCK_HEAL_AT = new ConcurrentHashMap<>();
    /** "watcher|friend" to the last compass pulse. */
    private static final Map<String, Long> PULSE_AT = new ConcurrentHashMap<>();
    /** Per player: until when Shield Wall's armour stays on them. */
    private static final Map<UUID, Long> WALLED = new ConcurrentHashMap<>();
    /** A cloud's potion, read once. Weak keys: a gone cloud drops out. Server thread only. */
    private static final Map<AreaEffectCloud, java.util.Optional<List<MobEffectInstance>>> CLOUD_CONTENTS =
            new java.util.WeakHashMap<>();

    private static final ThreadLocal<Boolean> INTERCEPTING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> SHARING = ThreadLocal.withInitial(() -> false);

    private record Regen(UUID healer, long until) {
    }

    private static final class Watch {
        final UUID ally;
        final UUID guardian;
        final long started;
        double lowest;

        Watch(UUID ally, UUID guardian, long started, double lowest) {
            this.ally = ally;
            this.guardian = guardian;
            this.started = started;
            this.lowest = lowest;
        }
    }

    private GuardianEvents() {
    }

    // ---- Who counts --------------------------------------------------------------------------

    private static boolean owned(@Nullable Entity entity) {
        return entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null;
    }

    /** A mob that is nobody's pet. Only these make danger. */
    public static boolean isThreat(@Nullable Entity culprit) {
        return culprit instanceof Mob && !owned(culprit);
    }

    /** Damage a player dealt, by hand, by arrow or by pet. Never pays and never counts as danger. */
    public static boolean fromPlayer(DamageSource source) {
        Entity culprit = source.getEntity();
        return culprit instanceof Player || owned(culprit) || source.getDirectEntity() instanceof Player;
    }

    /** Another player who can be protected: alive, not a spectator, not you, in your world. */
    public static boolean isAlly(ServerPlayer guardian, @Nullable Entity other) {
        return other instanceof ServerPlayer ally && ally != guardian && ally.isAlive()
                && !ally.isSpectator() && ally.level() == guardian.level();
    }

    private static boolean counts(ServerPlayer player) {
        // Read from the game mode itself: the GameTest mock claims creative.
        return player.gameMode.isSurvival() && ProficiencyConfig.enabled(Skill.GUARDIAN);
    }

    private static double danger(ServerPlayer ally) {
        return GuardianMath.danger(GuardianMath.share(ally.getHealth(), ally.getMaxHealth()));
    }

    @Nullable
    private static ServerPlayer online(MinecraftServer server, @Nullable UUID id) {
        return id == null ? null : server.getPlayerList().getPlayer(id);
    }

    /** The ally within {@code radius} with the lowest health share, or null. */
    @Nullable
    public static ServerPlayer mostHurtAlly(ServerPlayer guardian, double radius) {
        ServerPlayer best = null;
        double bestShare = 2.0;
        double range = radius * radius;
        for (ServerPlayer other : guardian.serverLevel().players()) {
            if (!isAlly(guardian, other) || other.distanceToSqr(guardian) > range) {
                continue;
            }
            double share = GuardianMath.share(other.getHealth(), other.getMaxHealth());
            if (share < bestShare) {
                bestShare = share;
                best = other;
            }
        }
        return best;
    }

    private static long now(Entity entity) {
        return entity.level().getGameTime();
    }

    // ---- XP source 1: cover ------------------------------------------------------------------

    /**
     * The ally this mob's hit on you protects, or null: the mob is after a player within 8 blocks
     * of you, or it turned from that player to you in the last 20 s (from 16 blocks if you pulled
     * it with a hit).
     */
    @Nullable
    public static ServerPlayer coveredAlly(ServerPlayer guardian, Mob mob) {
        if (!isThreat(mob)) {
            return null;
        }
        double near = GuardianMath.COVER_RADIUS * GuardianMath.COVER_RADIUS;
        if (isAlly(guardian, mob.getTarget()) && mob.getTarget().distanceToSqr(guardian) <= near) {
            return (ServerPlayer) mob.getTarget();
        }
        CompoundTag data = mob.getPersistentData();
        if (!data.hasUUID(TURN_BY) || !data.getUUID(TURN_BY).equals(guardian.getUUID())
                || !GuardianMath.remembered(data.getLong(TURN_AT), now(mob))) {
            return null;
        }
        ServerPlayer ally = data.hasUUID(TURN_ALLY) ? online(guardian.server, data.getUUID(TURN_ALLY)) : null;
        double radius = data.getBoolean(TURN_PULLED) ? GuardianMath.PULLED_RADIUS : GuardianMath.COVER_RADIUS;
        return isAlly(guardian, ally) && ally.distanceToSqr(guardian) <= radius * radius ? ally : null;
    }

    /** Pays one hit you took for an ally. Public so a GameTest can drive it with exact numbers. */
    public static float payCover(ServerPlayer guardian, Mob mob, float damage) {
        if (!counts(guardian)) {
            return 0f;
        }
        ServerPlayer ally = coveredAlly(guardian, mob);
        if (ally == null) {
            return 0f;
        }
        float paid = payDamage(guardian, mob, damage, ProficiencyConfig.guardianCoverXp(), danger(ally), SOURCE_COVER);
        if (paid > 0 && TalentService.rank(guardian, Skill.GUARDIAN, "sentinel") > 0) {
            // Sentinel: each hit taken for someone else brings Shield Wall back sooner.
            ProficiencyAttachments.of(guardian).shortenCooldown(Skill.GUARDIAN,
                    GuardianMath.SENTINEL_COOLDOWN_TICKS, now(guardian));
        }
        return paid;
    }

    private static float payDamage(ServerPlayer guardian, Mob mob, float damage, double rate, double danger,
            String source) {
        float already = mob.getPersistentData().getFloat(PAID_TAG);
        double counted = GuardianMath.countedDamage(damage, already);
        double xp = GuardianMath.damageXp(counted, rate, danger);
        if (!(xp > 0)) {
            return 0f;
        }
        mob.getPersistentData().putFloat(PAID_TAG, (float) (already + counted));
        return SkillService.grant(guardian, Skill.GUARDIAN, xp, source);
    }

    // ---- XP source 2: block and absorb -------------------------------------------------------

    /** Pays damage you blocked or absorbed from a mob with an ally within 4 blocks. */
    public static float payBlock(ServerPlayer guardian, @Nullable Entity attacker, float amount) {
        if (!counts(guardian) || !(attacker instanceof Mob mob) || !isThreat(mob) || !(amount > 0)) {
            return 0f;
        }
        ServerPlayer ally = mostHurtAlly(guardian, GuardianMath.BLOCK_RADIUS);
        if (ally == null) {
            return 0f;
        }
        return payDamage(guardian, mob, amount, ProficiencyConfig.guardianBlockXp(), danger(ally), SOURCE_BLOCK);
    }

    /** LOWEST, so it sees whether the block survived every other listener. Also Mending Guard. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer guardian) || event.isCanceled()) {
            return;
        }
        Entity attacker = event.getDamageSource().getEntity();
        payBlock(guardian, attacker, event.getBlockedDamage());
        if (isThreat(attacker)) {
            mendingGuard(guardian);
        }
    }

    /** Mending Guard: a blocked hit heals the most hurt ally within 8 blocks. */
    public static void mendingGuard(ServerPlayer guardian) {
        int rank = TalentService.rank(guardian, Skill.GUARDIAN, "block_heal");
        if (rank <= 0) {
            return;
        }
        long now = now(guardian);
        Long last = BLOCK_HEAL_AT.get(guardian.getUUID());
        if (last != null && now >= last && now - last < GuardianMath.BLOCK_HEAL_COOLDOWN_TICKS) {
            return;
        }
        ServerPlayer ally = mostHurtAlly(guardian, GuardianMath.ALLY_RADIUS);
        if (ally != null && ally.getHealth() < ally.getMaxHealth()) {
            ally.heal(GuardianMath.blockHeal(rank));
            BLOCK_HEAL_AT.put(guardian.getUUID(), now);
        }
    }

    /**
     * Pre, LOWEST: absorption is taken after this event, so the hearts about to soak the hit are
     * still there to read. A fully absorbed hit never reaches Post. Sworn Shield also lives here.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePreLate(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer guardian)) {
            return;
        }
        float absorbed = Math.min(event.getNewDamage(), guardian.getAbsorptionAmount());
        if (absorbed > 0 && !fromPlayer(event.getSource())) {
            payBlock(guardian, event.getSource().getEntity(), absorbed);
        }
    }

    // ---- The damage watcher: cover, the avenger memory, close calls, Taunt, the compass -------

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        Entity culprit = source.getEntity();
        long now = now(victim);
        if (!(victim instanceof Player)) {
            if (culprit instanceof ServerPlayer player && victim instanceof Mob mob) {
                mob.getPersistentData().putUUID(HIT_BY, player.getUUID());
                mob.getPersistentData().putLong(HIT_AT, now);
                if (event.getNewDamage() > 0) {
                    taunt(player, mob);
                }
            }
            return;
        }
        if (!(victim instanceof ServerPlayer hurt) || !(event.getNewDamage() > 0)) {
            return;
        }
        // Only a mob (nobody's pet) makes danger. Fall damage, fire, cactus and other hazards a
        // player can walk into on purpose do not: two friends could farm heals and close calls.
        if (isThreat(culprit)) {
            WORLD_HURT.put(hurt.getUUID(), now);
            Mob mob = (Mob) culprit;
            mob.getPersistentData().putUUID(HURT_ALLY, hurt.getUUID());
            mob.getPersistentData().putLong(HURT_AT, now);
            payCover(hurt, mob, event.getNewDamage());
            float after = hurt.getHealth();
            noteCloseCall(hurt, after + event.getNewDamage(), after);
        }
        pulseWatchers(hurt);
    }

    // ---- XP source 3: avenger ----------------------------------------------------------------

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) {
            return;
        }
        if (victim instanceof ServerPlayer dead) {
            forgetDanger(dead.getUUID());
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer killer && victim instanceof Mob mob) {
            avenge(killer, mob);
        }
    }

    /** An avenger kill: the mob hurt an ally within 5 s, and that ally is near. Returns the XP. */
    public static float avenge(ServerPlayer killer, Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (!isThreat(mob) || !data.hasUUID(HURT_ALLY) || !GuardianMath.avenges(data.getLong(HURT_AT), now(mob))) {
            return 0f;
        }
        ServerPlayer ally = online(killer.server, data.getUUID(HURT_ALLY));
        double range = GuardianMath.AVENGER_RADIUS * GuardianMath.AVENGER_RADIUS;
        if (!isAlly(killer, ally) || ally.distanceToSqr(killer) > range) {
            return 0f;
        }
        // Oathkeeper (with Courage): an avenger kill always rallies.
        if (TalentService.hasSynergy(killer, "oathkeeper")) {
            CourageEvents.rally(killer);
        }
        if (!counts(killer)) {
            return 0f;
        }
        double xp = ProficiencyConfig.guardianAvengerXp() * danger(ally);
        return xp > 0 ? SkillService.grant(killer, Skill.GUARDIAN, xp, SOURCE_AVENGER) : 0f;
    }

    // ---- XP source 4: healing an ally with thrown potions ------------------------------------

    /** Regeneration from someone else's potion or cloud: remember who, so its heals pay them. */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof ServerPlayer target)) {
            return;
        }
        MobEffectInstance effect = event.getEffectInstance();
        if (effect == null) {
            return;
        }
        if ((effect.getEffect() == MobEffects.REGENERATION)) {
            ServerPlayer healer = thrower(event.getEffectSource());
            if (healer != null && healer != target) {
                REGEN_BY.put(target.getUUID(), new Regen(healer.getUUID(), now(target) + effect.getDuration()));
            } else {
                // Their own Regeneration (a golden apple, a potion they drank) is not a friend's.
                REGEN_BY.remove(target.getUUID());
            }
        }
        if ((effect.getEffect() == MobEffects.ABSORPTION)) {
            heartshare(target, effect);
        }
    }

    /** The player behind an effect: the player, a thrown potion's owner, or a cloud's owner. */
    @Nullable
    private static ServerPlayer thrower(@Nullable Entity source) {
        if (source instanceof ServerPlayer player) {
            return player;
        }
        if (source instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (source instanceof AreaEffectCloud cloud && cloud.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        return null;
    }

    /** LOWEST: the amount is final. Only real, missing health counts. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHeal(LivingHealEvent event) {
        if (event.getEntity() instanceof ServerPlayer ally && event.getAmount() > 0
                && !ally.level().isClientSide()) {
            payHeal(ally, event.getAmount());
        }
    }

    /** Pays whoever healed this ally. Public so a GameTest can read the amount. */
    public static float payHeal(ServerPlayer ally, float amount) {
        long now = now(ally);
        if (ally.getHealth() >= ally.getMaxHealth()
                || !GuardianMath.healCounts(WORLD_HURT.getOrDefault(ally.getUUID(), -1L), now)) {
            return 0f;
        }
        ServerPlayer healer = healer(ally, now);
        if (healer == null || !counts(healer) || !isAlly(healer, ally)) {
            return 0f;
        }
        double xp = GuardianMath.healXp(amount, ally.getHealth(), ally.getMaxHealth(),
                ProficiencyConfig.guardianHealXp());
        return xp > 0 ? SkillService.grant(healer, Skill.GUARDIAN, xp, SOURCE_HEAL) : 0f;
    }

    /**
     * Who is healing this ally right now: the giver of a running Regeneration, else the owner of a
     * splash potion of Healing landing next to them, else the owner of a healing cloud they stand in.
     */
    @Nullable
    private static ServerPlayer healer(ServerPlayer ally, long now) {
        Regen regen = REGEN_BY.get(ally.getUUID());
        if (regen != null) {
            MobEffectInstance running = ally.getEffect(MobEffects.REGENERATION);
            if (regen.until() >= now && running != null) {
                ServerPlayer giver = online(ally.server, regen.healer());
                // Only Regeneration's own heal ticks pay the giver, not food or natural regen
                // healing on the other ticks while it runs. During the effect's tick the
                // instance still holds the duration it is checked with.
                if (giver != null && running.getEffect()
                        .isDurationEffectTick(running.getDuration(), running.getAmplifier())) {
                    return giver;
                }
            } else {
                REGEN_BY.remove(ally.getUUID(), regen);
            }
        }
        // A splash potion is still in the world while it applies itself.
        for (ThrownPotion potion : ally.level().getEntitiesOfClass(ThrownPotion.class,
                ally.getBoundingBox().inflate(GuardianMath.HEAL_SCAN_RADIUS))) {
            if (potion.getOwner() instanceof ServerPlayer owner && owner != ally
                    && heals(net.minecraft.world.item.alchemy.PotionUtils.getMobEffects(potion.getItem()), false)) {
                return owner;
            }
        }
        for (AreaEffectCloud cloud : ally.level().getEntitiesOfClass(AreaEffectCloud.class,
                ally.getBoundingBox())) {
            if (cloud.getOwner() instanceof ServerPlayer owner && owner != ally && heals(contents(cloud), true)) {
                return owner;
            }
        }
        return null;
    }

    private static boolean heals(@Nullable List<MobEffectInstance> contents, boolean regenToo) {
        if (contents == null) {
            return false;
        }
        for (MobEffectInstance effect : contents) {
            if (effect.getEffect() == MobEffects.HEAL
                    || (regenToo && effect.getEffect() == MobEffects.REGENERATION)) {
                return true;
            }
        }
        return false;
    }

    /** A cloud has no getter for its potion, so read it from what the cloud would save, once per cloud. */
    @Nullable
    private static List<MobEffectInstance> contents(AreaEffectCloud cloud) {
        return CLOUD_CONTENTS.computeIfAbsent(cloud, GuardianEvents::readContents).orElse(null);
    }

    private static java.util.Optional<List<MobEffectInstance>> readContents(AreaEffectCloud cloud) {
        return java.util.Optional.ofNullable(parseContents(cloud));
    }

    @Nullable
    private static List<MobEffectInstance> parseContents(AreaEffectCloud cloud) {
        try {
            // 1.20.1 saves a cloud's potion as "Potion" and its extra effects as an "Effects"
            // list (not a potion item's "CustomPotionEffects"), so the list is read by hand.
            CompoundTag tag = new CompoundTag();
            cloud.saveWithoutId(tag);
            if (!tag.contains("Potion") && !tag.contains("Effects")) {
                return null;
            }
            List<MobEffectInstance> all = new java.util.ArrayList<>(
                    net.minecraft.world.item.alchemy.PotionUtils.getPotion(tag).getEffects());
            net.minecraft.nbt.ListTag custom = tag.getList("Effects", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < custom.size(); i++) {
                MobEffectInstance effect = MobEffectInstance.load(custom.getCompound(i));
                if (effect != null) {
                    all.add(effect);
                }
            }
            return all;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- XP source 5: the close call ---------------------------------------------------------

    /** A hit took the ally from 30% or more to under 30%: every guardian next to them starts a watch. */
    public static void noteCloseCall(ServerPlayer ally, float before, float after) {
        double share = GuardianMath.share(after, ally.getMaxHealth());
        for (Watch watch : WATCHES) {
            if (watch.ally.equals(ally.getUUID())) {
                watch.lowest = Math.min(watch.lowest, share);
            }
        }
        if (!GuardianMath.closeCall(before, after, ally.getMaxHealth())) {
            return;
        }
        long now = now(ally);
        double range = GuardianMath.REVIVE_START_RADIUS * GuardianMath.REVIVE_START_RADIUS;
        for (ServerPlayer guardian : ally.serverLevel().players()) {
            if (!isAlly(guardian, ally) || guardian.distanceToSqr(ally) > range || !counts(guardian)) {
                continue;
            }
            Long paid = REVIVE_PAID.get(guardian.getUUID() + "|" + ally.getUUID());
            if (paid != null && now >= paid && now - paid < GuardianMath.REVIVE_COOLDOWN_TICKS) {
                continue;
            }
            boolean watching = WATCHES.stream().anyMatch(w -> w.ally.equals(ally.getUUID())
                    && w.guardian.equals(guardian.getUUID()));
            if (!watching) {
                WATCHES.add(new Watch(ally.getUUID(), guardian.getUUID(), now, share));
            }
        }
    }

    /** Every half second: close calls, Shield Wall, and Shield Wall's armour running out. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 0) {
            return;
        }
        tickWatches(server);
        Map<UUID, double[]> walls = new java.util.HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ActiveService.isFrenzied(player, Skill.GUARDIAN)) {
                shieldWall(player, walls);
            }
        }
        applyWalls(server, walls);
        expireWalls(server);
    }

    /** Pays and ends the watches that are done. Public so a GameTest can run it without waiting. */
    public static void tickWatches(MinecraftServer server) {
        double stay = GuardianMath.REVIVE_STAY_RADIUS * GuardianMath.REVIVE_STAY_RADIUS;
        Iterator<Watch> it = WATCHES.iterator();
        while (it.hasNext()) {
            Watch watch = it.next();
            ServerPlayer ally = online(server, watch.ally);
            ServerPlayer guardian = online(server, watch.guardian);
            if (ally == null || guardian == null || !isAlly(guardian, ally) || !guardian.isAlive()
                    || guardian.distanceToSqr(ally) > stay) {
                it.remove();
                continue;
            }
            long now = now(ally);
            if (!GuardianMath.reviveDone(watch.started, now)) {
                continue;
            }
            it.remove();
            if (!counts(guardian)) {
                continue;
            }
            REVIVE_PAID.put(watch.guardian + "|" + watch.ally, now);
            double xp = ProficiencyConfig.guardianReviveXp() * GuardianMath.danger(watch.lowest);
            if (xp > 0) {
                SkillService.grant(guardian, Skill.GUARDIAN, xp, SOURCE_REVIVE);
            }
        }
    }

    /** How many close calls this guardian is watching. For GameTests. */
    public static int watching(ServerPlayer guardian) {
        return (int) WATCHES.stream().filter(w -> w.guardian.equals(guardian.getUUID())).count();
    }

    // ---- The passive and Intercept -----------------------------------------------------------

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer ally) || fromPlayer(event.getSource())) {
            return;
        }
        double multiplier = GuardianMath.passiveMultiplier(bestPassive(ally));
        if (multiplier < 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
        Entity culprit = event.getSource().getEntity();
        if (!INTERCEPTING.get() && isThreat(culprit) && event.getAmount() > 0 && intercept(ally, event)) {
            event.setCanceled(true);
        }
    }

    /** The strongest Guardian passive among the other players within the company radius. */
    public static double bestPassive(ServerPlayer ally) {
        double radius = ProficiencyConfig.companyRadius();
        double range = radius * radius;
        double best = 0.0;
        for (ServerPlayer other : ally.serverLevel().players()) {
            if (other == ally || other.isSpectator() || !other.isAlive() || other.distanceToSqr(ally) > range) {
                continue;
            }
            best = Math.max(best, SkillService.bonus(other, Skill.GUARDIAN));
        }
        return best;
    }

    /**
     * Intercept: a guardian near the ally may take the hit instead, at half damage (less with proc
     * power). With Bulwark and a raised shield, the shield takes it. Returns whether it was taken.
     */
    private static boolean intercept(ServerPlayer ally, LivingIncomingDamageEvent event) {
        List<ServerPlayer> near = new ArrayList<>();
        for (ServerPlayer guardian : ally.serverLevel().players()) {
            if (!isAlly(guardian, ally) || !guardian.isAlive() || !guardian.gameMode.isSurvival()) {
                continue;
            }
            double radius = GuardianMath.interceptRadius(TalentService.rank(guardian, Skill.GUARDIAN, "guard_radius"));
            if (guardian.distanceToSqr(ally) <= radius * radius) {
                near.add(guardian);
            }
        }
        near.sort(Comparator.comparingDouble(guardian -> guardian.distanceToSqr(ally)));
        LivingEntity attacker = (LivingEntity) event.getSource().getEntity();
        for (ServerPlayer guardian : near) {
            if (!ProcService.fire(guardian, Skill.GUARDIAN, attacker)) {
                continue;
            }
            if (TalentService.hasSynergy(guardian, "bulwark") && guardian.isBlocking()) {
                // Bulwark: the raised shield takes the whole hit.
                ItemStack shield = guardian.getUseItem();
                EquipmentSlot slot = guardian.getUsedItemHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                        ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                shield.hurtAndBreak(Math.max(1, (int) Math.ceil(event.getAmount())), guardian,
                        broken -> broken.broadcastBreakEvent(slot));
                guardian.serverLevel().playSound(null, guardian.getX(), guardian.getY(), guardian.getZ(),
                        SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.0f);
                return true;
            }
            float damage = GuardianMath.interceptDamage(event.getAmount(), ProcService.power(guardian, Skill.GUARDIAN));
            INTERCEPTING.set(true);
            try {
                if (guardian.hurt(event.getSource(), damage)) {
                    return true;
                }
            } finally {
                INTERCEPTING.set(false);
            }
        }
        return false;
    }

    // ---- Sworn Shield ------------------------------------------------------------------------

    /**
     * Sworn Shield: a mob's killing blow on an ally within 8 blocks lands on you, at half damage.
     * Only a mob's blow (not /kill, the void or a hazard), and only if the half does not kill you:
     * it saves the ally, it does not trade one death for another.
     *
     * <p>The damage here is already after the ally's armour, so it is taken off your hearts
     * directly (absorption first). Going through {@code hurt} again would run armour, the passive
     * and Intercept a second time.
     */
    @SubscribeEvent
    public static void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer ally)) {
            return;
        }
        DamageSource source = event.getSource();
        if (!isThreat(source.getEntity()) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float damage = event.getNewDamage();
        if (!(damage > 0) || damage < ally.getHealth() + ally.getAbsorptionAmount()) {
            return;
        }
        float half = GuardianMath.swornDamage(damage);
        long now = now(ally);
        double range = GuardianMath.ALLY_RADIUS * GuardianMath.ALLY_RADIUS;
        for (ServerPlayer guardian : ally.serverLevel().players()) {
            if (!isAlly(guardian, ally) || !guardian.isAlive() || !guardian.gameMode.isSurvival()
                    || guardian.distanceToSqr(ally) > range
                    || !GuardianMath.swornSurvives(half, guardian.getHealth(), guardian.getAbsorptionAmount())
                    || TalentService.rank(guardian, Skill.GUARDIAN, "sworn_shield") <= 0
                    || !GuardianMath.swornReady(guardian.getPersistentData().contains(SWORN_AT)
                            ? guardian.getPersistentData().getLong(SWORN_AT) : -1L, now)) {
                continue;
            }
            event.setNewDamage(0f);
            guardian.getPersistentData().putLong(SWORN_AT, now);
            takeDirect(guardian, source, half);
            payCover(guardian, (Mob) source.getEntity(), half);
            guardian.displayClientMessage(Component.translatable("proficiency.guardian.sworn",
                    ally.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            ally.displayClientMessage(Component.translatable("proficiency.guardian.sworn_saved",
                    guardian.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            return;
        }
    }

    /** Takes already-reduced damage off a player's hearts, absorption first. Never kills. */
    private static void takeDirect(ServerPlayer player, DamageSource source, float amount) {
        float absorbed = Math.min(amount, player.getAbsorptionAmount());
        player.setAbsorptionAmount(player.getAbsorptionAmount() - absorbed);
        float rest = amount - absorbed;
        if (rest > 0) {
            player.getCombatTracker().recordDamage(source, rest);
            player.setHealth(Math.max(0.5f, player.getHealth() - rest));
        }
        player.level().broadcastDamageEvent(player, source);
    }

    // ---- Taunt -------------------------------------------------------------------------------

    /** Taunt: a mob you hit turns on you and cannot switch away for 5 s. */
    public static void taunt(ServerPlayer player, Mob mob) {
        if (TalentService.rank(player, Skill.GUARDIAN, "taunt") <= 0 || !mob.isAlive()) {
            return;
        }
        mob.getPersistentData().remove(TAUNT_UNTIL);
        mob.setTarget(player);
        mob.getPersistentData().putUUID(TAUNT_BY, player.getUUID());
        mob.getPersistentData().putLong(TAUNT_UNTIL, now(mob) + GuardianMath.TAUNT_TICKS);
    }

    /**
     * LOWEST, so it sees the switch that really happens. Two jobs: Taunt's lock, and remembering
     * the player a mob turned away from, for cover.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) {
            return;
        }
        LivingEntity next = event.getNewTarget();
        CompoundTag data = mob.getPersistentData();
        long now = now(mob);
        if (data.contains(TAUNT_UNTIL) && now < data.getLong(TAUNT_UNTIL) && data.hasUUID(TAUNT_BY)
                && (next == null || !next.getUUID().equals(data.getUUID(TAUNT_BY)))) {
            ServerPlayer taunter = mob.getServer() == null ? null : online(mob.getServer(), data.getUUID(TAUNT_BY));
            if (taunter != null && taunter.isAlive() && taunter.level() == mob.level()) {
                event.setCanceled(true);
                return;
            }
        }
        if (mob.getTarget() instanceof ServerPlayer from && next instanceof ServerPlayer to && from != to) {
            boolean pulled = data.hasUUID(HIT_BY) && data.getUUID(HIT_BY).equals(to.getUUID())
                    && GuardianMath.pulled(data.getLong(HIT_AT), now);
            data.putUUID(TURN_ALLY, from.getUUID());
            data.putUUID(TURN_BY, to.getUUID());
            data.putLong(TURN_AT, now);
            data.putBoolean(TURN_PULLED, pulled);
        }
    }

    // ---- Shield Wall -------------------------------------------------------------------------

    /**
     * Shield Wall, refreshed every half second while it runs: every ally in reach gets your armour
     * and toughness on top of theirs (the strongest wall counts, two do not stack), and hostile
     * mobs near you that are after another player turn on you. Sentinel adds Resistance I.
     * Public so a GameTest can run one refresh.
     */
    public static void shieldWall(ServerPlayer guardian) {
        Map<UUID, double[]> walls = new java.util.HashMap<>();
        shieldWall(guardian, walls);
        applyWalls(guardian.server, walls);
    }

    private static void shieldWall(ServerPlayer guardian, Map<UUID, double[]> walls) {
        double radius = GuardianMath.shieldWallRadius(TalentService.rank(guardian, Skill.GUARDIAN, "guard_radius"));
        double range = radius * radius;
        double armour = own(guardian, Attributes.ARMOR, WALL_ARMOUR);
        double toughness = own(guardian, Attributes.ARMOR_TOUGHNESS, WALL_TOUGHNESS);
        boolean sentinel = TalentService.rank(guardian, Skill.GUARDIAN, "sentinel") > 0;
        for (ServerPlayer ally : guardian.serverLevel().players()) {
            if (!isAlly(guardian, ally) || ally.distanceToSqr(guardian) > range) {
                continue;
            }
            double[] best = walls.computeIfAbsent(ally.getUUID(), id -> new double[2]);
            best[0] = Math.max(best[0], armour);
            best[1] = Math.max(best[1], toughness);
            if (sentinel) {
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, false, false, true));
            }
        }
        double pull = GuardianMath.SHIELD_WALL_RADIUS * GuardianMath.SHIELD_WALL_RADIUS;
        for (Mob mob : guardian.level().getEntitiesOfClass(Mob.class,
                guardian.getBoundingBox().inflate(GuardianMath.SHIELD_WALL_RADIUS))) {
            if (mob.isAlive() && isThreat(mob) && isAlly(guardian, mob.getTarget())
                    && mob.distanceToSqr(guardian) <= pull) {
                mob.setTarget(guardian);
            }
        }
    }

    private static void applyWalls(MinecraftServer server, Map<UUID, double[]> walls) {
        for (Map.Entry<UUID, double[]> entry : walls.entrySet()) {
            ServerPlayer ally = online(server, entry.getKey());
            if (ally == null) {
                continue;
            }
            set(ally, Attributes.ARMOR, WALL_ARMOUR, entry.getValue()[0]);
            set(ally, Attributes.ARMOR_TOUGHNESS, WALL_TOUGHNESS, entry.getValue()[1]);
            WALLED.put(ally.getUUID(), now(ally) + 20);
        }
    }

    /** Your own value of an attribute, without any Shield Wall someone else gave you. */
    private static double own(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attribute,
            ResourceLocation wall) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return 0.0;
        }
        AttributeModifier lent = instance.getModifier(dev.amman.proficiency.compat.Attr.uuid(wall));
        return Math.max(0.0, instance.getValue() - (lent == null ? 0.0 : lent.getAmount()));
    }

    private static void set(ServerPlayer ally, net.minecraft.world.entity.ai.attributes.Attribute attribute,
            ResourceLocation wall, double amount) {
        AttributeInstance instance = ally.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount > 0) {
            dev.amman.proficiency.compat.Attr.setTransient(instance, dev.amman.proficiency.compat.Attr.mod(wall, amount, AttributeModifier.Operation.ADDITION));
        } else {
            instance.removeModifier(dev.amman.proficiency.compat.Attr.uuid(wall));
        }
    }

    /** Takes Shield Wall's armour off players who left the wall or whose wall ended. */
    public static void expireWalls(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Long>> it = WALLED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            ServerPlayer player = online(server, entry.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            if (now(player) >= entry.getValue()) {
                unwall(player);
                it.remove();
            }
        }
    }

    private static void unwall(ServerPlayer player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(dev.amman.proficiency.compat.Attr.uuid(WALL_ARMOUR));
        }
        AttributeInstance toughness = player.getAttribute(Attributes.ARMOR_TOUGHNESS);
        if (toughness != null) {
            toughness.removeModifier(dev.amman.proficiency.compat.Attr.uuid(WALL_TOUGHNESS));
        }
    }

    // ---- Heartshare --------------------------------------------------------------------------

    /** Heartshare: your Absorption also goes to the most hurt ally within 8 blocks, for part of its time. */
    private static void heartshare(ServerPlayer guardian, MobEffectInstance effect) {
        if (SHARING.get()) {
            return;
        }
        int rank = TalentService.rank(guardian, Skill.GUARDIAN, "share_absorb");
        int ticks = GuardianMath.shareTicks(effect.getDuration(), rank);
        if (ticks <= 0) {
            return;
        }
        ServerPlayer ally = mostHurtAlly(guardian, GuardianMath.ALLY_RADIUS);
        if (ally == null) {
            return;
        }
        SHARING.set(true);
        try {
            ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, effect.getAmplifier()), guardian);
        } finally {
            SHARING.set(false);
        }
    }

    // ---- Watchful Compass --------------------------------------------------------------------

    /**
     * Watchful Compass: every player with the node and a Friend Compass for this friend in their
     * hotbar or off hand gets a pulse: the friend's health on the action bar and a heartbeat.
     * Returns how many were told.
     */
    public static int pulseWatchers(ServerPlayer friend) {
        int told = 0;
        long now = now(friend);
        for (ServerPlayer watcher : friend.server.getPlayerList().getPlayers()) {
            if (watcher == friend || TalentService.rank(watcher, Skill.GUARDIAN, "compass_watch") <= 0
                    || !carriesCompassFor(watcher, friend.getUUID())) {
                continue;
            }
            String key = watcher.getUUID() + "|" + friend.getUUID();
            Long last = PULSE_AT.get(key);
            if (last != null && now >= last && now - last < GuardianMath.PULSE_COOLDOWN_TICKS) {
                continue;
            }
            PULSE_AT.put(key, now);
            watcher.displayClientMessage(health("proficiency.guardian.friend_hurt", friend)
                    .withStyle(ChatFormatting.RED), true);
            watcher.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 1.0f);
            told++;
        }
        return told;
    }

    /** "Name: 7 / 20 health", for the compass pulse and for using the compass. */
    public static net.minecraft.network.chat.MutableComponent health(String key, ServerPlayer friend) {
        return Component.translatable(key, friend.getDisplayName(),
                (int) Math.ceil(friend.getHealth()), (int) Math.ceil(friend.getMaxHealth()));
    }

    private static boolean carriesCompassFor(ServerPlayer watcher, UUID friend) {
        if (compassFor(watcher.getOffhandItem(), friend)) {
            return true;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (compassFor(watcher.getInventory().getItem(slot), friend)) {
                return true;
            }
        }
        return false;
    }

    private static boolean compassFor(ItemStack stack, UUID friend) {
        return stack.getItem() instanceof FriendCompassItem && friend.equals(FriendCompassItem.friend(stack));
    }

    // ---- Forgetting --------------------------------------------------------------------------

    private static void forgetDanger(UUID player) {
        WORLD_HURT.remove(player);
        REGEN_BY.remove(player);
        WATCHES.removeIf(w -> w.ally.equals(player) || w.guardian.equals(player));
    }

    /** Drops everything kept for this player. On logout, and for GameTests. */
    public static void forget(UUID player) {
        forgetDanger(player);
        BLOCK_HEAL_AT.remove(player);
        WALLED.remove(player);
        String prefix = player + "|";
        String suffix = "|" + player;
        REVIVE_PAID.keySet().removeIf(key -> key.startsWith(prefix) || key.endsWith(suffix));
        PULSE_AT.keySet().removeIf(key -> key.startsWith(prefix) || key.endsWith(suffix));
    }

    /** Nothing carries into the next world (singleplayer and integrated servers reuse the JVM). */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        WORLD_HURT.clear();
        REGEN_BY.clear();
        WATCHES.clear();
        REVIVE_PAID.clear();
        BLOCK_HEAL_AT.clear();
        PULSE_AT.clear();
        WALLED.clear();
        CLOUD_CONTENTS.clear();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            unwall(player);
        }
        forget(event.getEntity().getUUID());
    }
}
