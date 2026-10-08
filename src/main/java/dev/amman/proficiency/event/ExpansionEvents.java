package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.DiscoveryNames;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.compat.LivingIncomingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The skills that only exist because of what is in this pack, plus the two builder skills.
 *
 * <p>Nothing here imports a single class from another mod. Everything is decided from namespaces,
 * tags and vanilla types, so the mod loads and behaves identically whether Ars Nouveau and Create
 * are present or not. A skill whose content is absent simply never earns anything, which is the
 * correct behaviour and not a bug.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class ExpansionEvents {

    private static final Set<String> ARCANE_NAMESPACES = Set.of(
            "ars_nouveau", "ars_additions", "arseng");

    private static final Set<String> MACHINE_NAMESPACES = Set.of(
            "create", "createaddition", "create_dragons_plus", "create_enchantment_industry",
            "ae2", "arseng", "titanium", "functionalstorage", "merequester", "toms_storage");

    /** How far Mining and Masonry can push block reach at level 100, in blocks, each. */
    private static final double REACH_AT_MAX = dev.amman.proficiency.skill.SkillPassives.REACH_AT_MAX;

    private static final ResourceLocation MINING_REACH = Proficiency.id("mining_reach");
    private static final ResourceLocation MASONRY_REACH = Proficiency.id("masonry_reach");
    private static final ResourceLocation FRENZY_REACH = Proficiency.id("frenzy_reach");
    private static final double FRENZY_REACH_BONUS = 4.0;

    private static final int WAYFARING_CHECK_TICKS = 40;
    private static final int BIOME_XP = 25;
    private static final int DIMENSION_XP = 150;
    /** Grand structures pay {@code grandStructureXp} instead of {@code structureXp}. */
    private static final TagKey<Structure> GRAND_STRUCTURES = TagKey.create(
            Registries.STRUCTURE, Proficiency.id("grand_structures"));

    /** The re-entry banner's cooldown. Dropped on logout. */
    private static final EntryCooldown ENTRY_COOLDOWN = new EntryCooldown();

    /**
     * What counts as a boss.
     *
     * <p>This started as a bare {@code maxHealth >= 80} check, which was wrong for this pack: at
     * that threshold ordinary elites from Twilight Forest and the Aether qualify, and the
     * server-wide announcement becomes red-text spam inside a week. The tag is the real answer
     * because the pack's owner controls it and it does not drift as mobs are added; the health
     * figure survives only as a configurable backstop for mobs nobody has listed yet.
     */
    private static final TagKey<EntityType<?>> NOTABLE_BOSSES = TagKey.create(
            Registries.ENTITY_TYPE, Proficiency.id("notable_bosses"));

    static boolean isNotableBoss(LivingEntity victim) {
        return victim.getType().is(NOTABLE_BOSSES)
                || victim.getMaxHealth() >= dev.amman.proficiency.config.ProficiencyConfig.bossHealth();
    }

    /**
     * A room somebody actually decorated should be worth standing in. Decorating's only payoff was
     * a refund chance identical to Masonry's, which gave an interior designer nothing an interior
     * designer wants. No XP here on purpose: anything that paid out for standing still would be
     * farmed by leaving a client logged in overnight.
     */
    private static void cozyRoom(ServerPlayer player) {
        int decorating = ProficiencyAttachments.of(player).level(Skill.DECORATING);
        if (decorating < COZY_MIN_LEVEL) {
            return;
        }
        // Hygge: a smaller room of fewer, better things still counts.
        int threshold = TalentMath.cozyThreshold(COZY_THRESHOLD,
                TalentService.rank(player, Skill.DECORATING, "hygge"));
        BlockPos centre = player.blockPosition();
        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2 && found < threshold; dx++) {
            for (int dy = -1; dy <= 2 && found < threshold; dy++) {
                for (int dz = -2; dz <= 2 && found < threshold; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (dev.amman.proficiency.skill.BuildClassifier
                            .isDecorative(player.level().getBlockState(cursor))) {
                        found++;
                    }
                }
            }
        }
        if (found < threshold) {
            return;
        }
        // Tastemaker: the room is good enough to be worth more than a slow heal.
        boolean tastemaker = TalentService.rank(player, Skill.DECORATING, "tastemaker") > 0;
        warm(player, tastemaker);

        // The Host: guests standing in your room get what you get. Nothing is paid for it, so
        // there is nothing to farm.
        int host = TalentService.rank(player, Skill.DECORATING, "host");
        if (host > 0) {
            double radius = 2.0 * host;
            for (ServerPlayer guest : player.serverLevel().players()) {
                if (guest != player && !guest.isSpectator()
                        && guest.distanceToSqr(player) <= radius * radius) {
                    warm(guest, tastemaker);
                }
            }
        }
    }

    private static void warm(ServerPlayer player, boolean tastemaker) {
        player.addEffect(new MobEffectInstance(
                MobEffects.REGENERATION, COZY_TICKS * 2, tastemaker ? 1 : 0, true, false, true));
        if (tastemaker) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.LUCK, COZY_TICKS * 2, 0, true, false, true));
        }
    }

    /** Enough sparkles to read a vein by, and a hard stop on the scan. */
    private static final int MAX_ORE_MARKS = 48;

    private static final double CONVOY_RADIUS = 24.0;
    private static final int AURA_TICKS = 15;

    private static final int COZY_TICKS = 100;
    private static final int COZY_THRESHOLD = 10;
    private static final int COZY_MIN_LEVEL = 10;

    private static final long CAST_COOLDOWN_TICKS = 20;
    private static final Map<UUID, Long> LAST_CAST = new ConcurrentHashMap<>();

    /**
     * Positions this player has already been paid for, and until when.
     *
     * <p>A refund on placement is a dupe unless something stops you being paid twice for the same
     * spot: the block you placed is still standing, so you break it back and keep the refund.
     *
     * <p>The first attempt at this used a capped LinkedHashSet and also marked every block break.
     * Both were wrong. `add` on an element already present is a no-op and does not refresh recency,
     * so eviction was by first insertion rather than by use, and marking every break burned the
     * whole budget during ordinary mining. A player could push the block off with a piston (which
     * fires neither event, so the position was never re-marked), touch five hundred other blocks,
     * and the position would age out with the original block still in hand.
     *
     * <p>Expiry by time fixes all of it. A position stays paid for ten minutes of world time
     * whatever else happens, and only actual payouts record anything.
     */
    private static final long PLACEMENT_TTL_TICKS = 12_000;

    private static final int PLACEMENT_MEMORY = 4096;

    private static final Map<UUID, Map<Long, Long>> PAID_PLACEMENTS = new ConcurrentHashMap<>();

    /** Bulk Order's current run of identical blocks, per player. */
    private static final Map<UUID, TalentMath.Streak> STREAKS = new ConcurrentHashMap<>();

    /** Set while Echo Cast's second hit lands, so the echo neither echoes nor procs nor pays. */
    private static final ThreadLocal<Boolean> ECHOING = ThreadLocal.withInitial(() -> false);

    private static final double ECHO_RADIUS = 8.0;

    private static final ResourceLocation MASONRY_TALENT_REACH = Proficiency.id("masonry_talent_reach");

    /**
     * Vanilla's redstone components, for Redstone Savant. Vanilla has no tag for these, so the set
     * is written out: anything whose job is to make, carry or act on a signal.
     */
    private static final Set<Item> REDSTONE_PARTS = Set.of(
            Items.REPEATER, Items.COMPARATOR, Items.PISTON, Items.STICKY_PISTON, Items.OBSERVER,
            Items.DISPENSER, Items.DROPPER, Items.HOPPER, Items.REDSTONE_LAMP,
            Items.DAYLIGHT_DETECTOR, Items.TARGET, Items.LEVER, Items.NOTE_BLOCK,
            Items.REDSTONE_TORCH, Items.REDSTONE_BLOCK, Items.TRIPWIRE_HOOK, Items.TRAPPED_CHEST,
            Items.LIGHTNING_ROD, Items.SCULK_SENSOR, Items.CALIBRATED_SCULK_SENSOR,
            Items.STONE_BUTTON, Items.OAK_BUTTON, Items.STONE_PRESSURE_PLATE,
            Items.LIGHT_WEIGHTED_PRESSURE_PLATE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Items.RAIL, Items.POWERED_RAIL, Items.DETECTOR_RAIL, Items.ACTIVATOR_RAIL);

    /** Planting pays half of a harvest (1.0), so replanting is never the better loop. */
    private static final double PLANTING_XP = 0.5;

    private ExpansionEvents() {
    }

    private static boolean alreadyPaidFor(ServerPlayer player, BlockPos pos) {
        long now = player.level().getGameTime();
        Map<Long, Long> paid = PAID_PLACEMENTS.computeIfAbsent(
                player.getUUID(), id -> Collections.synchronizedMap(new LinkedHashMap<>()));
        long key = pos.asLong();

        synchronized (paid) {
            Long until = paid.get(key);
            if (until != null && until > now) {
                return true;
            }
            if (paid.size() >= PLACEMENT_MEMORY) {
                paid.values().removeIf(expiry -> expiry <= now);
                if (paid.size() >= PLACEMENT_MEMORY) {
                    Iterator<Map.Entry<Long, Long>> oldest = paid.entrySet().iterator();
                    oldest.next();
                    oldest.remove();
                }
            }
            paid.put(key, now + PLACEMENT_TTL_TICKS);
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PAID_PLACEMENTS.remove(event.getEntity().getUUID());
        ENTRY_COOLDOWN.forget(event.getEntity().getUUID());
        STREAKS.remove(event.getEntity().getUUID());
        LAST_CAST.remove(event.getEntity().getUUID());
        // Finding 8: this cache had a cleanup method that nothing ever called.
        dev.amman.proficiency.skill.CompanyBonus.forget(event.getEntity().getUUID());
        dev.amman.proficiency.skill.Tempo.forget(event.getEntity().getUUID());
        dev.amman.proficiency.event.CombatEvents.forget(event.getEntity().getUUID());
        ProcService.forget(event.getEntity().getUUID());
    }

    // ---- Construction ------------------------------------------------------------------------

    /**
     * Builders spend blocks, so the passive refunds them. Structural and decorative placement are
     * split because an interior designer and a castle builder are not doing the same job.
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Every player placement is remembered, creative and machines included, so breaking it again pays nothing.
        dev.amman.proficiency.skill.PlacedBlocks.markPlacement(player.serverLevel(), event.getPos(), event.getPlacedBlock());
        if (player.isCreative()) {
            return;
        }
        placed(player, event.getPlacedBlock(), event.getPos());
    }

    /**
     * What a survival player's placement pays. Split from the event so a GameTest can call it: the
     * mock player is always creative, and the event handler skips creative players.
     */
    public static void placed(ServerPlayer player, BlockState placed, BlockPos pos) {
        if (alreadyPaidFor(player, pos)) {
            return;
        }

        // Seeds, saplings and the like are farming. Half a harvest; no refund, proc or streak,
        // because a free seed is not what a farmer wants from the passive.
        if (dev.amman.proficiency.skill.BuildClassifier.isPlanting(placed)) {
            SkillService.grant(player, Skill.FARMING, PLANTING_XP, placed.getBlock().getDescriptionId());
            return;
        }

        // Setting up a contraption is the whole of Create; crafting the cogwheel is only half.
        if (isMachine(placed)) {
            SkillService.grant(player, Skill.ENGINEERING, 2.0, placed.getBlock().getDescriptionId());
            if (ProcService.fire(player, Skill.ENGINEERING, pos)) {
                // Overclock: a free machine can come as two.
                giveBack(player, placed, Math.max(1, ProcService.roundRandomly(
                        player, ProcService.power(player, Skill.ENGINEERING))));
            } else if (TalentService.rank(player, Skill.ENGINEERING, "blueprint") > 0
                    && player.getRandom().nextDouble() < 0.20) {
                giveBack(player, placed, 1);
            }
            return;
        }

        Skill skill = dev.amman.proficiency.skill.BuildClassifier.skillFor(placed);
        // Stairs, slabs and walls: half Masonry, half Decorating. Masonry alone rolls the proc, the
        // refund and the first-time bonus, so the block cannot refund twice.
        boolean split = dev.amman.proficiency.skill.BuildClassifier.isSplit(placed);
        SkillService.grant(player, skill, split ? 0.5 : 1.0, placed.getBlock().getDescriptionId());
        if (split) {
            SkillService.grantNoFirstTime(player, Skill.DECORATING, 0.5, placed.getBlock().getDescriptionId());
        }

        // Master Mason and Master Decorator build for free, which is what a builder actually wants
        // from a capstone.
        if (TalentService.hasSpecial(player, skill, "free_place")) {
            giveBack(player, placed, 1);
            ProcService.fire(player, skill, pos);
            return;
        }

        double refundChance = ProficiencyAttachments.of(player).bonus(skill);
        int refunds = 0;
        if (ProcService.fire(player, skill, pos)) {
            // Stonecutter and Feng Shui make the triple bigger.
            refunds = Math.max(1, ProcService.roundRandomly(
                    player, 3.0 * ProcService.power(player, skill)));
        } else if (refundChance > 0 && player.level().getRandom().nextDouble() < refundChance) {
            refunds = 1;
        }

        // Lamplighter: a light is the one decoration you place dozens of.
        int lamplighter = TalentService.rank(player, Skill.DECORATING, "lamplighter");
        if (refunds == 0 && lamplighter > 0 && placed.getLightEmission() > 0
                && player.getRandom().nextDouble() < 0.15 * lamplighter) {
            refunds = 1;
        }

        // Bulk Order: a wall is the same block a hundred times, so reward the run.
        int bulk = TalentService.rank(player, Skill.MASONRY, "bulk_order");
        if (bulk > 0 && skill == Skill.MASONRY && !split) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(placed.getBlock());
            if (STREAKS.computeIfAbsent(player.getUUID(), id -> new TalentMath.Streak())
                    .place(String.valueOf(blockId))) {
                refunds += bulk;
            }
        }

        if (refunds > 0) {
            giveBack(player, placed, refunds);
        }
    }

    /** Create, AE2 and the rest: blocks whose namespace says they are part of a machine. */
    static boolean isMachine(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return blockId != null && MACHINE_NAMESPACES.contains(blockId.getNamespace());
    }


    private static void giveBack(ServerPlayer player, BlockState placed, int count) {
        ItemStack refund = new ItemStack(placed.getBlock().asItem(), count);
        if (refund.getItem() instanceof BlockItem) {
            player.getInventory().placeItemBackInInventory(refund);
        }
    }

    // ---- Reach -------------------------------------------------------------------------------

    /** Mining reaches further into the rock, Masonry further out into the air. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        AttributeInstance reach = player.getAttribute(net.minecraftforge.common.ForgeMod.BLOCK_REACH.get());
        if (reach != null) {
            PlayerSkills skills = ProficiencyAttachments.of(player);
            setReach(reach, MINING_REACH, skills.level(Skill.MINING));
            setReach(reach, MASONRY_REACH, skills.level(Skill.MASONRY));

            // Haste does nothing for someone placing blocks, so a builder's frenzy buys reach.
            boolean building = ActiveService.isFrenzied(player, Skill.MASONRY)
                    || ActiveService.isFrenzied(player, Skill.DECORATING);
            setReachDirect(reach, FRENZY_REACH, building ? FRENZY_REACH_BONUS : 0.0);

            // Long Arm and Grand Architect. Both sides, like the rest of reach, or the client
            // would refuse to aim at a block the server would let you place.
            double talentReach = dev.amman.proficiency.skill.SkillPassives.masonryTalentReach(skills);
            setReachDirect(reach, MASONRY_TALENT_REACH, talentReach);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.tickCount % WAYFARING_CHECK_TICKS == 0) {
                checkWhereYouAre(serverPlayer);
            }
            if (serverPlayer.tickCount % AURA_TICKS == 0) {
                masteryAura(serverPlayer);
            }
            if (serverPlayer.tickCount % COZY_TICKS == 0) {
                cozyRoom(serverPlayer);
            }
        }
    }

    private static void setReach(AttributeInstance attribute, ResourceLocation id, int level) {
        setReachDirect(attribute, id, REACH_AT_MAX * (level / 100.0));
    }

    private static void setReachDirect(AttributeInstance attribute, ResourceLocation id, double amount) {
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
        dev.amman.proficiency.compat.Attr.setTransient(attribute, 
                dev.amman.proficiency.compat.Attr.mod(id, amount, AttributeModifier.Operation.ADDITION));
    }

    // ---- Wayfaring ---------------------------------------------------------------------------

    /** Public so the gametests can run the real check without waiting out the 40-tick timer. */
    public static void checkWhereYouAre(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        ResourceLocation dimension = player.level().dimension().location();
        // The place you spawn is not a discovery. A player who has seen nothing yet gets the
        // starting dimension, biome and any structure they stand in recorded silently: no XP, no
        // proc, nothing shared. Otherwise a first join paid 150 + 25 Wayfaring XP for standing still.
        if (!skills.hasAnyPlace()) {
            skills.markVisited("dim:" + dimension);
            player.level().getBiome(player.blockPosition()).unwrapKey()
                    .ifPresent(key -> skills.markVisited("biome:" + key.location()));
            checkStructure(player, skills, true, false);
            return;
        }
        boolean[] found = new boolean[1];
        if (skills.markVisited("dim:" + dimension)) {
            found[0] = true;
            String name = dimension.toLanguageKey("dimension");
            float xp = SkillService.grant(player, Skill.WAYFARING, DIMENSION_XP, name);
            ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.DIMENSION, name,
                    dimension.toString(), Skill.WAYFARING, xp);
            var shared = shareDiscovery(player, "dim:" + dimension, DiscoveryPayload.DIMENSION, name,
                    dimension.toString(), DIMENSION_XP, null);
            announce(player, shared, Component.translatableWithFallback("proficiency.announce.dimension",
                    "%s entered %s for the first time", player.getDisplayName(),
                    DiscoveryNames.component(name, dimension.toString())));
        }
        player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
            if (skills.markVisited("biome:" + key.location())) {
                found[0] = true;
                // Inspired goes first so the discovery itself is paid at the raised rate.
                TalentExpansionEvents.onNewBiome(player);
                String name = key.location().toLanguageKey("biome");
                float xp = SkillService.grant(player, Skill.WAYFARING, BIOME_XP, name);
                ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.BIOME, name,
                        key.location().toString(), Skill.WAYFARING, xp);
                ProcService.fire(player, Skill.WAYFARING, player.blockPosition());
                shareDiscovery(player, "biome:" + key.location(), DiscoveryPayload.BIOME, name,
                        key.location().toString(), BIOME_XP, null);
            }
        });
        checkStructure(player, skills, false, found[0]);
    }

    /**
     * Standing inside a structure you have never been in before. "Inside" means inside one of its
     * pieces, not its overall box: a mineshaft's box spans a whole hillside, and walking over one
     * is not finding it. Only the chunk's own structure references are looked at, which the chunk
     * already holds, so this costs nothing for the ordinary case of standing in a field.
     */
    /**
     * The structure start with a piece at the player's feet or at the block they stand on. A
     * village street piece is one block tall and holds only the path itself, so a player walking
     * down the street has their feet one block above every piece: checking only the feet meant a
     * village never counted until you went indoors. Verified in a real client on 2026-09-29.
     */
    private static net.minecraft.world.level.levelgen.structure.StructureStart pieceAt(ServerLevel level,
            BlockPos pos, net.minecraft.world.level.levelgen.structure.Structure structure) {
        var start = level.structureManager().getStructureWithPieceAt(pos, structure);
        return start.isValid() ? start : level.structureManager().getStructureWithPieceAt(pos.below(), structure);
    }

    private static void checkStructure(ServerPlayer player, PlayerSkills skills, boolean silent,
            boolean placeFound) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        var here = level.structureManager().getAllStructuresAt(pos);
        if (here.isEmpty()) {
            return;
        }
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        long now = level.getGameTime();
        String enterKey = null;
        String enterId = null;
        for (var structure : here.keySet()) {
            ResourceLocation raw = registry.getKey(structure);
            if (raw == null) {
                continue;
            }
            // Variants are one discovery: every village is a village.
            String canonical = StructureIds.canonical(raw.toString());
            String key = StructureIds.visitedKey(canonical);
            if (!silent && StructureIds.alreadySeen(canonical, skills::hasVisited)) {
                // The common case, every two seconds inside a village. Ask the cheap questions
                // first: is the banner even on, and is this start off cooldown? Only then walk
                // the pieces. getStructureAt looks at the start's box, not its pieces.
                if (enterKey != null || placeFound
                        || ProficiencyConfig.structureEntryCooldownMinutes() == 0) {
                    continue;
                }
                var box = level.structureManager().getStructureAt(pos, structure);
                if (!box.isValid()) {
                    continue;
                }
                String cooldownKey = EntryCooldown.key(canonical, box.getChunkPos().x,
                        box.getChunkPos().z);
                if (!ENTRY_COOLDOWN.ready(player.getUUID(), cooldownKey, now)) {
                    continue;
                }
                if (pieceAt(level, pos, structure).isValid()) {
                    enterKey = cooldownKey;
                    enterId = canonical;
                }
                continue;
            }
            var start = pieceAt(level, pos, structure);
            if (!start.isValid()) {
                continue;
            }
            if (silent) {
                skills.markVisited(key);
                // The spawn structure's banner must not fire two seconds after a silent join.
                recordEntry(player, EntryCooldown.key(canonical, start.getChunkPos().x,
                        start.getChunkPos().z), now);
                continue;
            }
            skills.markVisited(key);
            ResourceLocation canonicalLoc = new ResourceLocation(canonical);
            boolean grand = registry.wrapAsHolder(structure).is(GRAND_STRUCTURES);
            int xpBase = grand ? ProficiencyConfig.grandStructureXp() : ProficiencyConfig.structureXp();
            String name = canonicalLoc.toLanguageKey("structure");
            float xp = SkillService.grant(player, Skill.WAYFARING, xpBase, name);
            ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.STRUCTURE, name,
                    canonical, Skill.WAYFARING, xp);
            String startKey = EntryCooldown.key(canonical, start.getChunkPos().x,
                    start.getChunkPos().z);
            var shared = shareDiscovery(player, key, DiscoveryPayload.STRUCTURE, name, canonical,
                    xpBase, startKey);
            if (grand) {
                announce(player, shared, Component.translatableWithFallback("proficiency.announce.structure",
                        "%s discovered %s", player.getDisplayName(),
                        DiscoveryNames.component(name, canonical)));
            }
            // Later visits to this one start their cooldown now, so the discovery banner is not
            // followed by a re-entry banner ten seconds later.
            recordEntry(player, startKey, now);
            // One banner per check. A second structure overlapping this one waits two seconds.
            return;
        }
        if (enterKey != null && !placeFound) {
            recordEntry(player, enterKey, now);
            ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.ENTER,
                    new ResourceLocation(enterId).toLanguageKey("structure"), enterId,
                    Skill.WAYFARING, 0f);
        }
    }

    private static void recordEntry(ServerPlayer player, String cooldownKey, long now) {
        ENTRY_COOLDOWN.prune(player.getUUID(), now);
        ENTRY_COOLDOWN.record(player.getUUID(), cooldownKey, now,
                ProficiencyConfig.structureEntryCooldownMinutes() * 1200L);
    }

    /**
     * A quiet grey italic line to everyone but the discoverer and the players who were just paid
     * the same discovery through the convoy: they already saw it as a banner.
     */
    private static void announce(ServerPlayer discoverer, java.util.List<ServerPlayer> skip,
            Component line) {
        if (!ProficiencyConfig.announceGrandDiscoveries()) {
            return;
        }
        Component styled = line.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        for (ServerPlayer other : discoverer.server.getPlayerList().getPlayers()) {
            if (other != discoverer && !skip.contains(other)) {
                other.sendSystemMessage(styled);
            }
        }
    }

    /**
     * Anyone travelling with you who has not been here either gets it marked too, at half credit.
     * The person who actually led gets full XP and the proc, so there is a reason to be first and
     * a reason to bring people, which is the whole shape of the co-op design here.
     */
    private static java.util.List<ServerPlayer> shareDiscovery(ServerPlayer discoverer, String place,
            int kind, String name, String id, double xp, String entryKey) {
        java.util.List<ServerPlayer> paid = new java.util.ArrayList<>();
        for (ServerPlayer other : discoverer.serverLevel().players()) {
            if (other == discoverer || other.isSpectator()) {
                continue;
            }
            if (other.distanceToSqr(discoverer) > CONVOY_RADIUS * CONVOY_RADIUS) {
                continue;
            }
            PlayerSkills theirs = ProficiencyAttachments.of(other);
            // A village found before variants were collapsed under another key still counts.
            if (kind == DiscoveryPayload.STRUCTURE
                    && StructureIds.alreadySeen(id, theirs::hasVisited)) {
                continue;
            }
            if (theirs.markVisited(place)) {
                float got = SkillService.grant(other, Skill.WAYFARING, xp * 0.5,
                        "proficiency.xplog.source.convoy");
                ProficiencyNetwork.sendDiscovery(other, kind, name, id, Skill.WAYFARING, got,
                        discoverer.getGameProfile().getName());
                paid.add(other);
                if (entryKey != null) {
                    // They were told already: no re-entry banner two seconds later.
                    recordEntry(other, entryKey, other.serverLevel().getGameTime());
                }
            }
        }
        return paid;
    }

    /**
     * A maxed skill is worth seeing from across the base. Server-sent, so a vanilla client sees it
     * too, and throttled hard because it is purely decorative.
     */
    private static void masteryAura(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        for (Skill skill : Skill.VALUES) {
            if (skills.level(skill) < 100) {
                continue;
            }
            player.serverLevel().sendParticles(ProcService.particleFor(skill),
                    player.getX(), player.getY() + 0.15, player.getZ(),
                    2, 0.3, 0.05, 0.3, 0.0);
            return;
        }
    }

    // ---- Spelunking --------------------------------------------------------------------------

    /** Ore pulled out of the deep dark counts twice: once for Mining, once for getting down there. */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) {
            return;
        }
        BlockState state = event.getState();
        if (!state.is(Tags.Blocks.ORES)
                || dev.amman.proficiency.skill.PlacedBlocks.isUnpaid(player.level(), event.getPos(), state)) {
            return;
        }
        // Idea 31: no drop with this tool, no Spelunking XP either. Creative is unchanged.
        if (!GatheringEvents.dropsWithHeldTool(player, state)) {
            return;
        }
        double depthBonus = Math.max(0, (32 - event.getPos().getY())) / 32.0;
        SkillService.grant(player, Skill.SPELUNKING, 1.0 + depthBonus * 2.0, state.getBlock().getDescriptionId());

        if (ProcService.fire(player, Skill.SPELUNKING, event.getPos())
                && event.getLevel() instanceof ServerLevel level) {
            // Glowstone Heart: further and longer, both bounded in TalentMath.
            double power = ProcService.power(player, Skill.SPELUNKING);
            player.addEffect(new MobEffectInstance(
                    MobEffects.NIGHT_VISION, TalentMath.caveSenseTicks(power), 0));
            revealNearbyOre(level, event.getPos(), TalentMath.caveSenseRadius(power));
        }
    }

    /**
     * Cave Sense: every ore nearby briefly sparkles.
     *
     * <p>Radius 8 rather than 12, and it stops once it has marked enough to read. At 12 this was
     * 15,625 block lookups on the main thread with nothing to stop several miners triggering it in
     * the same tick; at 8 with an early exit it is a fraction of that and bounded either way. Proc
     * power can take it back up to 12, never past it, and the mark cap still stops it early.
     */
    static void revealNearbyOre(ServerLevel level, BlockPos centre, int radius) {
        int marked = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius && marked < MAX_ORE_MARKS; dx++) {
            for (int dy = -radius; dy <= radius && marked < MAX_ORE_MARKS; dy++) {
                for (int dz = -radius; dz <= radius && marked < MAX_ORE_MARKS; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.getBlockState(cursor).is(Tags.Blocks.ORES)) {
                        continue;
                    }
                    marked++;
                    level.sendParticles(ParticleTypes.GLOW,
                            cursor.getX() + 0.5, cursor.getY() + 0.5, cursor.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.0);
                }
            }
        }
    }

    // ---- Spellcasting and Beastslaying -------------------------------------------------------

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        // The echo's own hit was already worked out by the spell that caused it.
        if (ECHOING.get()) {
            return;
        }
        LivingEntity victim = event.getEntity();

        if (isArcane(event.getSource().getDirectEntity(), event.getSource().getMsgId())) {
            applySpell(event, player);
        } else if (isModdedCreature(victim)) {
            // Beastslaying rides on top of whatever weapon skill already applied.
            double bonus = ProficiencyAttachments.of(player).bonus(Skill.BEASTSLAYING);
            if (bonus > 0) {
                event.setAmount((float) (event.getAmount() * dev.amman.proficiency.skill.SkillPassives.more(bonus)));
            }
        }
        beastslayingTalents(event, player, victim);
    }

    /**
     * Bane, Giant Slayer and Boss Bane. These name what you are hitting, not how, so they apply to
     * a spell or an arrow as much as to a sword, and to vanilla mobs as well as modded ones.
     */
    private static void beastslayingTalents(LivingIncomingDamageEvent event, Player player,
            LivingEntity victim) {
        if (victim instanceof Player) {
            return;
        }
        double multiplier = 1.0;
        int bane = TalentService.rank(player, Skill.BEASTSLAYING, "bane");
        if (bane > 0 && (victim.getMobType() == net.minecraft.world.entity.MobType.UNDEAD
                || victim.getMobType() == net.minecraft.world.entity.MobType.ARTHROPOD)) {
            multiplier *= TalentMath.perRank(bane, 0.05);
        }
        int giant = TalentService.rank(player, Skill.BEASTSLAYING, "giant_slayer");
        if (giant > 0 && victim.getMaxHealth() >= 2.0f * player.getMaxHealth()) {
            multiplier *= TalentMath.perRank(giant, 0.05);
        }
        if (TalentService.rank(player, Skill.BEASTSLAYING, "boss_bane") > 0 && isNotableBoss(victim)) {
            multiplier *= 1.20;
        }
        if (multiplier != 1.0) {
            event.setAmount((float) (event.getAmount() * multiplier));
        }
    }

    private static void applySpell(LivingIncomingDamageEvent event, Player player) {
        Skill skill = Skill.SPELLCASTING;
        double bonus = ProficiencyAttachments.of(player).bonus(skill);
        float amount = event.getAmount();
        if (bonus > 0) {
            amount *= (float) dev.amman.proficiency.skill.SkillPassives.more(bonus);
        }
        boolean empowered = ProcService.fire(player, skill, event.getEntity());
        if (empowered) {
            amount *= (float) (2.0 * ProcService.power(player, skill));
        }
        event.setAmount(amount);
        SkillService.grantNoFirstTime(player, skill,
                dev.amman.proficiency.skill.SpawnOrigin.xpFactor(event.getEntity()),
                event.getEntity().getType().getDescriptionId());
        dev.amman.proficiency.skill.KillCredit.record(event.getEntity(), player, skill);

        if (empowered && TalentService.rank(player, skill, "echo_cast") > 0) {
            echo(event, player, amount * 0.5f);
        }
    }

    /**
     * Echo Cast: the nearest other hostile within eight blocks of the target takes half. The hit
     * goes in with the same source, under {@link #ECHOING}, so it is not boosted again, does not
     * roll a proc, pays no XP and cannot echo in turn.
     */
    private static void echo(LivingIncomingDamageEvent event, Player player, float amount) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !(amount > 0)) {
            return;
        }
        AABB area = target.getBoundingBox().inflate(ECHO_RADIUS);
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area,
                other -> other != target && other != player && other.isAlive()
                        && other instanceof Enemy)) {
            double distance = candidate.distanceToSqr(target);
            if (distance < best && distance <= ECHO_RADIUS * ECHO_RADIUS) {
                best = distance;
                nearest = candidate;
            }
        }
        if (nearest == null) {
            return;
        }
        ECHOING.set(true);
        try {
            nearest.hurt(event.getSource(), amount);
        } finally {
            ECHOING.set(false);
        }
        level.sendParticles(ParticleTypes.WITCH,
                nearest.getX(), nearest.getY() + nearest.getBbHeight() * 0.6, nearest.getZ(),
                10, 0.3, 0.4, 0.3, 0.02);
    }

    /** Public so the weapon handler can stand down rather than double-scaling the same hit. */
    public static boolean isArcaneSource(net.minecraft.world.damagesource.DamageSource source) {
        return isArcane(source.getDirectEntity(), source.getMsgId());
    }

    private static boolean isArcane(Entity direct, String messageId) {
        if (direct != null) {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(direct.getType());
            if (type != null && ARCANE_NAMESPACES.contains(type.getNamespace())) {
                return true;
            }
        }
        return messageId != null && messageId.startsWith("ars_");
    }

    private static boolean isModdedCreature(LivingEntity entity) {
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return type != null && !"minecraft".equals(type.getNamespace());
    }

    /** Killing something the pack added is what Beastslaying is actually for. */
    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof Player player)
                || player.level().isClientSide()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || !isModdedCreature(victim)) {
            return;
        }
        // Scaled by how much of a thing it was. A boss is worth a lot of chickens.
        double xp = Math.min(60.0, 1.0 + victim.getMaxHealth() / 4.0);
        SkillService.grant(player, Skill.BEASTSLAYING, xp * dev.amman.proficiency.skill.SpawnOrigin.xpFactor(victim),
                victim.getType().getDescriptionId());

        if (isNotableBoss(victim)) {
            // Killing a Cataclysm or Twilight Forest boss is not the same event as killing a
            // modded chicken, and it should not roll the same dice.
            ProcService.celebrate((ServerPlayer) player, Skill.BEASTSLAYING);
            if (TalentService.rank(player, Skill.BEASTSLAYING, "boss_bane") > 0) {
                player.getInventory().placeItemBackInInventory(
                        new ItemStack(ProficiencyItems.HUNTERS_CHARM.get(), 2));
            }
            if (TalentService.rank(player, Skill.BEASTSLAYING, "legend") > 0) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0));
            }
            player.getServer().getPlayerList().broadcastSystemMessage(
                    net.minecraft.network.chat.Component.translatable("proficiency.announce.boss",
                                    player.getDisplayName(), victim.getDisplayName())
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    false);
        } else {
            ProcService.fire(player, Skill.BEASTSLAYING, victim);
        }
    }

    /**
     * Casting, not just hitting. Spellcasting used to pay only when an arcane damage source landed,
     * which meant a utility caster running Break, Grow, Light or Summon glyphs levelled it never.
     * The mod's own cast cooldown is the throttle here; there is no need for another.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (id == null || !ARCANE_NAMESPACES.contains(id.getNamespace())) {
            return;
        }
        // This event fires on every click attempt, not on every spell that actually resolved, so
        // the mod's own cast cooldown is not the throttle the first version assumed it was. Without
        // this, spam-clicking an empty wand farmed XP at click rate, and Tempo then scaled it.
        long now = player.level().getGameTime();
        Long last = LAST_CAST.get(player.getUUID());
        if (last != null && now - last < CAST_COOLDOWN_TICKS) {
            return;
        }
        LAST_CAST.put(player.getUUID(), now);
        SkillService.grant(player, Skill.SPELLCASTING, 0.5);
    }

    // ---- Engineering -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        ItemStack result = event.getCrafting();
        if (result.isEmpty()) {
            return;
        }
        // Redstone Savant: vanilla's own machines count, but only for someone who took the node.
        int redstone = TalentService.rank(player, Skill.ENGINEERING, "redstone");
        if (redstone > 0 && REDSTONE_PARTS.contains(result.getItem())) {
            SkillService.grant(player, Skill.ENGINEERING, 1.0);
            if (player.getRandom().nextDouble() < 0.10 * redstone) {
                handBack(player, result, 1);
            }
            return;
        }

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(result.getItem());
        if (id == null || !MACHINE_NAMESPACES.contains(id.getNamespace())) {
            return;
        }
        // An item can be damageable or food AND machine-namespaced, in which case CraftingEvents
        // pays Smithing or Cooking for the same craft. That is two different skills from one
        // action, the same shape as ore paying both Mining and Spelunking, and it is intended.
        SkillService.grant(player, Skill.ENGINEERING, 2.0, result.getDescriptionId());
        if (ProcService.fire(player, Skill.ENGINEERING)) {
            // The whole craft again, and Overclock can make it twice.
            int copies = Math.max(1, ProcService.roundRandomly(
                    player, ProcService.power(player, Skill.ENGINEERING)));
            handBack(player, result, result.getCount() * copies);
        } else if (player.getRandom().nextDouble() < SkillService.bonus(player, Skill.ENGINEERING)) {
            // The passive: one of what you made, which is what Precision Parts always promised.
            handBack(player, result, 1);
        }

        // Cogsmith: Create's parts specifically, on top of whatever came back above.
        int cogsmith = TalentService.rank(player, Skill.ENGINEERING, "cogsmith");
        if (cogsmith > 0 && id.getNamespace().startsWith("create")
                && player.getRandom().nextDouble() < 0.15 * cogsmith) {
            handBack(player, result, 1);
        }
    }

    private static void handBack(Player player, ItemStack made, int count) {
        if (count > 0) {
            player.getInventory().placeItemBackInInventory(made.copyWithCount(count));
        }
    }
}
