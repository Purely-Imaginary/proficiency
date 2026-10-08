package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillProcEvent;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The talent specials of the expansion and builder trees that do not belong to an existing
 * handler in {@link ExpansionEvents}: things that happen to you rather than things you do.
 *
 * <p>Like the rest of the expansion code, nothing here imports a class from another mod. Ars
 * Nouveau's mana is reached through its attributes by registry id, so without Ars installed the
 * two mana nodes simply do nothing.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class TalentExpansionEvents {

    private static final ResourceLocation WALK_SPEED = Proficiency.id("wayfaring_walk");
    private static final ResourceLocation TRAIL_SPEED = Proficiency.id("trail_speed");
    private static final ResourceLocation CONVOY_SPEED = Proficiency.id("convoy_speed");
    private static final ResourceLocation MANA_MAX = Proficiency.id("mana_font");
    private static final ResourceLocation MANA_REGEN = Proficiency.id("deep_well");

    /**
     * Ars Nouveau registers its perk attributes under dotted paths, read out of
     * {@code PerkAttributes} in the 5.10.5 jar. The lang keys ({@code ars_nouveau.max_mana}) are not
     * the registry ids.
     */
    private static final ResourceLocation ARS_MAX_MANA =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "ars_nouveau.perk.max_mana");
    private static final ResourceLocation ARS_MANA_REGEN =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "ars_nouveau.perk.mana_regen");

    private static final double CONVOY_RADIUS = 24.0;
    private static final int INSPIRED_TICKS = 1200;
    private static final int WORLD_WALKER_TICKS = 200;
    private static final int MAGUS_TICKS = 100;

    /** Cave Lore and the rest count as underground below this height with no sky overhead. */
    private static final int UNDERGROUND_BELOW_Y = 50;

    /** The best walking share a nearby Legend of the Road holds, refreshed once a second. */
    private static final Map<UUID, Double> CONVOY = new ConcurrentHashMap<>();

    /** Set while Warden's Whisper re-applies the shortened Darkness, so it is not shortened twice. */
    private static final ThreadLocal<Boolean> SHORTENING = ThreadLocal.withInitial(() -> false);

    private TalentExpansionEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CONVOY.remove(event.getEntity().getUUID());
        SkillService.forget(event.getEntity().getUUID());
    }

    // ---- Ticking -----------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        applyWalkModifiers(player);
        if (player.level().isClientSide()) {
            climb(player);
            return;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (serverPlayer.tickCount % 20 == 0) {
            manaModifiers(serverPlayer);
            refreshConvoy(serverPlayer);
        }
        if (serverPlayer.tickCount % 40 == 0) {
            campfire(serverPlayer);
            deepSight(serverPlayer);
        }
        if (serverPlayer.tickCount % 200 == 0) {
            echolocation(serverPlayer);
        }
    }

    /**
     * Trailwise (the Wayfaring passive), Pathfinder and Legend of the Road. The first two run on
     * both sides, the same way MovementEvents does its stances, so the client predicts the speed
     * the server will allow. The convoy share is server-only: the client has no copy of another
     * player's skills to work it out from, and the server's attribute sync carries it across.
     */
    private static void applyWalkModifiers(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean walking = player.onGround() && !player.isSprinting() && !player.isCrouching()
                && !player.isInWater() && !player.isPassenger();
        double walk = walking ? walkShare(player) : 0.0;
        setModifier(speed, WALK_SPEED, walk, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        int trail = TalentService.rank(player, Skill.WAYFARING, "trail_speed");
        boolean onPath = trail > 0 && player.onGround()
                && player.getBlockStateOn().is(Blocks.DIRT_PATH);
        setModifier(speed, TRAIL_SPEED, onPath ? 0.10 * trail : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        if (!player.level().isClientSide()) {
            double shared = walking ? CONVOY.getOrDefault(player.getUUID(), 0.0) : 0.0;
            // Shared, not stacked: you walk as fast as the fastest of you, no faster.
            setModifier(speed, CONVOY_SPEED, Math.max(0.0, shared - walk),
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
    }

    /** Half the Wayfaring passive, which is what the Trailwise description promises. */
    private static double walkShare(Player player) {
        return dev.amman.proficiency.skill.SkillPassives.walkShare(SkillService.bonus(player, Skill.WAYFARING));
    }

    /**
     * Who holds Legend of the Road right now, rebuilt once a second. Without it every player scanned
     * every other player every second for a capstone almost nobody has: quadratic in the player
     * count for nothing. Now each player looks only at the holders, usually none.
     */
    private static volatile java.util.List<ServerPlayer> convoyLeaders = java.util.List.of();

    @SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        java.util.List<ServerPlayer> leaders = new java.util.ArrayList<>();
        for (ServerPlayer candidate : event.getServer().getPlayerList().getPlayers()) {
            if (TalentService.rank(candidate, Skill.WAYFARING, "convoy_speed") > 0) {
                leaders.add(candidate);
            }
        }
        convoyLeaders = leaders;
    }

    private static void refreshConvoy(ServerPlayer player) {
        double best = 0.0;
        for (ServerPlayer leader : convoyLeaders) {
            if (leader.level() != player.level() || leader.isRemoved()) {
                continue;
            }
            if (leader == player || leader.isSpectator()
                    || leader.distanceToSqr(player) > CONVOY_RADIUS * CONVOY_RADIUS) {
                continue;
            }
            best = Math.max(best, walkShare(leader));
        }
        if (best > 0) {
            CONVOY.put(player.getUUID(), best);
        } else {
            CONVOY.remove(player.getUUID());
        }
    }

    /**
     * Mana Font and Deep Well.
     *
     * <p>Multiplied on the total, not the base, and that is deliberate: both Ars attributes have a
     * base of 0. Ars works out your mana from book tier and glyphs, writes it onto the attribute as
     * its own ADD_VALUE modifier and then reads the attribute's value. A multiplier on the base
     * would be a percentage of zero. On the total it is a percentage of the mana you actually have.
     */
    private static void manaModifiers(ServerPlayer player) {
        applyArs(player, ARS_MAX_MANA, MANA_MAX,
                0.15 * TalentService.rank(player, Skill.SPELLCASTING, "max_mana"));
        applyArs(player, ARS_MANA_REGEN, MANA_REGEN,
                0.15 * TalentService.rank(player, Skill.SPELLCASTING, "mana_regen"));
    }

    private static void applyArs(Player player, ResourceLocation attributeId,
            ResourceLocation modifierId, double amount) {
        Optional<Holder.Reference<Attribute>> attribute = BuiltInRegistries.ATTRIBUTE.getHolder(attributeId);
        if (attribute.isEmpty()) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute.get());
        if (instance == null) {
            return;
        }
        setModifier(instance, modifierId, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    private static void setModifier(AttributeInstance attribute, ResourceLocation id, double amount,
            AttributeModifier.Operation operation) {
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

    /**
     * Rope Climber. Player movement is decided on the client, so this runs there, for the local
     * player only. Vanilla sets an upward climb to a fixed speed each tick; doubling what is left
     * after that tick doubles the next tick's step. Going down is clamped by vanilla before the
     * move and cannot be sped up this way.
     */
    private static void climb(Player player) {
        if (!player.isLocalPlayer()
                || TalentService.rank(player, Skill.SPELUNKING, "climber") <= 0) {
            return;
        }
        if (!player.onClimbable() || player.isCrouching()
                || player.getInBlockState().is(Blocks.SCAFFOLDING)) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y > 0) {
            player.setDeltaMovement(motion.x, Math.min(motion.y * 2.0, 0.5), motion.z);
        }
    }

    /** Campfire Tales: a lit campfire within three blocks. 343 lookups, every two seconds. */
    private static void campfire(ServerPlayer player) {
        if (TalentService.rank(player, Skill.WAYFARING, "campfire") <= 0) {
            return;
        }
        BlockPos centre = player.blockPosition();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (CampfireBlock.isLitCampfire(player.level().getBlockState(cursor))) {
                        player.addEffect(new MobEffectInstance(
                                MobEffects.REGENERATION, 60, 0, true, false, true));
                        return;
                    }
                }
            }
        }
    }

    /**
     * Lord of the Deep. Refreshed at 300 ticks every 40, so it never drops under the 200 at which
     * night vision starts to flicker.
     */
    private static void deepSight(ServerPlayer player) {
        if (player.getY() < 0 && TalentService.rank(player, Skill.SPELUNKING, "deep_sight") > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, true));
        }
    }

    /** Echolocation: a small Cave Sense on a timer. At rank 3 that is 13 cubed lookups every 10 s. */
    private static void echolocation(ServerPlayer player) {
        int rank = TalentService.rank(player, Skill.SPELUNKING, "echolocation");
        if (rank > 0 && underground(player)) {
            ExpansionEvents.revealNearbyOre(player.serverLevel(), player.blockPosition(), 3 + rank);
        }
    }

    private static boolean underground(Player player) {
        return player.getY() < UNDERGROUND_BELOW_Y && !player.level().canSeeSky(player.blockPosition());
    }

    // ---- Discovery ---------------------------------------------------------------------------

    /** Called by the Wayfaring check the moment a biome is marked visited for the first time. */
    static void onNewBiome(ServerPlayer player) {
        int fresh = TalentService.rank(player, Skill.WAYFARING, "fresh_air");
        if (fresh > 0) {
            player.getFoodData().eat(2 * fresh, 0.0f);
        }
        int inspired = TalentService.rank(player, Skill.WAYFARING, "inspired");
        if (inspired > 0) {
            SkillService.inspire(player, TalentMath.perRank(inspired, 0.10), INSPIRED_TICKS);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || TalentService.rank(player, Skill.WAYFARING, "world_walker") <= 0) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, WORLD_WALKER_TICKS, 0));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, WORLD_WALKER_TICKS, 0));
    }

    // ---- Procs -------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onProc(SkillProcEvent event) {
        ServerPlayer player = event.player();
        switch (event.skill()) {
            case SPELLCASTING -> {
                if (TalentService.rank(player, Skill.SPELLCASTING, "magus") > 0) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MAGUS_TICKS, 0));
                    player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, MAGUS_TICKS, 0));
                }
            }
            case ENGINEERING -> {
                // Grand Engineer: Smithing's specialist material, from the other workshop.
                if (TalentService.rank(player, Skill.ENGINEERING, "tinkers_luck") > 0) {
                    player.getInventory().placeItemBackInInventory(
                            new ItemStack(ProficiencyItems.MASTERWORK_INGOT.get()));
                }
            }
            default -> {
            }
        }
    }

    // ---- Breaking ----------------------------------------------------------------------------

    /**
     * Wrench Wizard and Deep Dweller. Lowest priority and multiplying the new speed rather than the
     * original, so they stack on top of the gathering skills' own break speed instead of being
     * overwritten by it. Both sides, so the crack animation matches.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        double multiplier = 1.0;
        int quick = TalentService.rank(player, Skill.ENGINEERING, "quick_hands");
        if (quick > 0 && ExpansionEvents.isMachine(event.getState())) {
            multiplier *= TalentMath.perRank(quick, 0.30);
        }
        int deep = TalentService.rank(player, Skill.SPELUNKING, "deep_dweller");
        if (deep > 0 && player.getY() < 0) {
            multiplier *= TalentMath.perRank(deep, 0.10);
        }
        if (multiplier != 1.0) {
            event.setNewSpeed((float) (event.getNewSpeed() * multiplier));
        }
    }

    // ---- Taking damage -----------------------------------------------------------------------

    /** Everything that makes a hit on you smaller: Spell Ward, Safety Harness, Cave Lore, Cave-In. */
    @SubscribeEvent
    public static void onHurt(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide()) {
            return;
        }
        DamageSource source = event.getSource();
        if (TalentService.rank(player, Skill.SPELUNKING, "cave_in") > 0
                && (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FALLING_BLOCK)
                        || source.is(DamageTypes.FALLING_ANVIL)
                        || source.is(DamageTypes.FALLING_STALACTITE))) {
            event.setCanceled(true);
            return;
        }

        double multiplier = 1.0;
        int ward = TalentService.rank(player, Skill.SPELLCASTING, "spell_ward");
        if (ward > 0 && (source.is(Tags.DamageTypes.IS_MAGIC) || ExpansionEvents.isArcaneSource(source))) {
            multiplier *= TalentMath.lessPerRank(ward, 0.08);
        }
        int harness = TalentService.rank(player, Skill.MASONRY, "harness");
        if (harness > 0 && source.is(DamageTypeTags.IS_FALL)
                && player.getMainHandItem().getItem() instanceof BlockItem) {
            multiplier *= TalentMath.lessPerRank(harness, 0.15);
        }
        // Cave Lore, the Spelunking passive. /kill and the void are not the cave's doing.
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && underground(player)) {
            multiplier *= 1.0 - TalentMath.undergroundReduction(
                    SkillService.bonus(player, Skill.SPELUNKING));
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    /**
     * Warden's Whisper. The duration of an effect instance cannot be changed once built, so the
     * incoming Darkness is refused and a shorter copy applied in its place.
     */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (SHORTENING.get() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MobEffectInstance incoming = event.getEffectInstance();
        if (incoming == null || !incoming.is(MobEffects.DARKNESS) || incoming.isInfiniteDuration()) {
            return;
        }
        int rank = TalentService.rank(player, Skill.SPELUNKING, "sculk_silence");
        if (rank <= 0) {
            return;
        }
        event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        MobEffectInstance shorter = new MobEffectInstance(incoming.getEffect(),
                TalentMath.darknessDuration(incoming.getDuration(), rank), incoming.getAmplifier(),
                incoming.isAmbient(), incoming.isVisible(), incoming.showIcon());
        SHORTENING.set(true);
        try {
            player.addEffect(shorter, event.getEffectSource());
        } finally {
            SHORTENING.set(false);
        }
    }

    // ---- Loot --------------------------------------------------------------------------------

    /** Scavenger: the whole drop list again, for a modded creature. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        if (type == null || "minecraft".equals(type.getNamespace())) {
            return;
        }
        int rank = TalentService.rank(player, Skill.BEASTSLAYING, "scavenger");
        if (rank <= 0 || player.getRandom().nextDouble() >= 0.10 * rank) {
            return;
        }
        List<ItemEntity> copies = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemEntity copy = new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(),
                    drop.getItem().copy());
            copy.setDefaultPickUpDelay();
            copies.add(copy);
        }
        event.getDrops().addAll(copies);
    }
}
