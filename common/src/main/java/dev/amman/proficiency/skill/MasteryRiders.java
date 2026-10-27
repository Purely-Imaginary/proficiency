package dev.amman.proficiency.skill;

import com.mojang.datafixers.util.Pair;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.StructureIds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mastery stars at work: one rider per skill, performed each time that skill's own signature proc
 * lands ({@link ProcService#celebrate}). The numbers are {@link MasteryEffects} and {@link MasteryMath};
 * the design of every row is docs/MASTERY-SPEC.md. A rider needs level 100 and at least one star, runs
 * on the server only, never for a machine's fake player, never starts a proc of its own (its damage is
 * magic with no weapon behind it, which no proc path reads, so it cannot chain) and never touches a
 * level, a talent point or the death penalty. It costs a handful of entity reads, once per proc.
 */
public final class MasteryRiders {

    /** The flourish (sparks, chime, action-bar name) plays at most this often per player. */
    private static final int FLOURISH_GAP_TICKS = 40;
    private static final java.util.Map<java.util.UUID, Long> LAST_FLOURISH = new java.util.concurrent.ConcurrentHashMap<>();
    /** A gift of one item stops once the player already carries this many, so no proc loop turns into supply. */
    private static final int GIFT_CARRY_CAP = 64;
    /** The proc's own blow lands this many ticks after the proc; a phantom hit waits for it. */
    private static final int ECHO_DELAY = 6;
    private static final Block[] FLOWERS = {Blocks.DANDELION, Blocks.POPPY, Blocks.AZURE_BLUET, Blocks.OXEYE_DAISY,
            Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY, Blocks.ALLIUM, Blocks.RED_TULIP};
    private static final net.minecraft.world.item.Item[] FISH = {Items.COD, Items.SALMON, Items.COD, Items.SALMON,
            Items.TROPICAL_FISH, Items.PUFFERFISH};
    private static final net.minecraft.world.item.Item[] BREADS = {Items.BREAD};
    /** New Horizon's structure search runs at most this often per player. */
    private static final int HORIZON_GAP_TICKS = 100;
    private static final java.util.Map<java.util.UUID, Long> LAST_HORIZON = new java.util.concurrent.ConcurrentHashMap<>();
    /** The structure ids each dimension can generate (biomes are fixed per world), worked out once per level. */
    private static final java.util.Map<ServerLevel, List<String>> LOCAL_STRUCTURES = new java.util.WeakHashMap<>();
    private static final int GOLD = 0xFFC400;
    private static final int RED = 0xFF3B30;

    private MasteryRiders() {
    }

    /** Called by the proc path, after the proc's own noise. */
    public static void onProc(ServerPlayer player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos) {
        if (FakeActors.isFake(player)) {
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int stars = MasteryEffects.stars(skills, skill);
        if (stars < 1) {
            return;
        }
        double v = MasteryEffects.value(skill, stars);
        boolean acted = switch (skill) {
            case SWORDS -> echoSlash(player, target, (float) v);
            case AXES -> whirl(player, v);
            case MACES -> tremor(player, v);
            case TRIDENTS -> arcingTide(player, target, (int) v);
            case UNARMED -> flurry(player, target, (int) v);
            case BLOCKING -> shieldBash(player, target, v);
            case ENDURANCE -> shrugItOff(player, (int) v);
            case ARCHERY -> ricochet(player, target, (int) v);
            case CROSSBOWS -> splinterBolt(player, target, v);
            case COURAGE -> warCry(player, v);
            case GUARDIAN -> wardingPulse(player, v);
            case CHARGER -> stampede(player, v);
            case TACTICIAN -> pinningFire(player, v);

            case MINING -> motherlodePing(player, (int) v);
            case WOODCUTTING -> replant(player, pos, (int) v);
            case EXCAVATION -> landslide(player, pos, (int) v);
            case FARMING -> greenThumb(player, pos, (int) v, stars);
            case FISHING -> secondCatch(player, pos, (int) v);
            case HUSBANDRY -> openBarn(player, v, stars);

            case RUNNING -> {
                Vec3 look = player.getLookAngle();
                yield dash(player, new Vec3(look.x, 0, look.z), v, MasteryMath.GROUND_DRAG, 0.0,
                        ParticleTypes.CLOUD, SoundEvents.ENDER_DRAGON_FLAP);
            }
            case SNEAKING -> smokeStep(player, v, stars);
            case JUMPING -> rebound(player, v);
            case SWIMMING -> glide(player, v);
            case RIDING -> restedMount(player, v);

            case SMITHING -> quench(player, v);
            case COOKING -> secondsServed(player, (int) v);
            case ALCHEMY -> {
                boolean any = extendHelpful(player, v);
                if (any) {
                    burst(player.serverLevel(), ParticleTypes.WITCH, player.getX(), player.getY() + 1.0, player.getZ(),
                            14, 0.5, 0.05);
                }
                yield any;
            }

            case SPELLCASTING -> magicMissiles(player, (int) v);
            case ENGINEERING -> tractorBeam(player, v);

            case BEASTSLAYING -> {
                feed(player, (int) v, (float) v);
                burst(player.serverLevel(), ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0,
                        player.getZ(), 6, 0.4, 0.0);
                yield true;
            }
            case WAYFARING -> newHorizon(player, (int) v);
            case SPELUNKING -> caveSense(player, v);

            case MASONRY -> repoint(player, (int) v);
            case DECORATING -> bloom(player, (int) v);
            case HOMEBUILDER -> hearthglow(player, v, stars);

            case SOCIAL -> toast(player, (float) v);
            case COMMERCE -> thrownIn(player, (int) v);
            case NIGHTWALKER -> moonGaze(player, v);
            default -> false;
        };
        if (acted) {
            flourish(player, skill, stars);
        }
    }

    // ---- Combat ---------------------------------------------------------------------------------

    /** Perfect Strike: a phantom second slash, a moment after the first, on the foe and the ones beside it. */
    private static boolean echoSlash(ServerPlayer player, @Nullable LivingEntity target, float damage) {
        LivingEntity first = focus(player, target, 4.5);
        if (first == null) {
            return false;
        }
        List<LivingEntity> caught = new ArrayList<>();
        caught.add(first);
        for (LivingEntity near : foes(player, first.position(), MasteryMath.ECHO_ARC)) {
            if (near != first) {
                caught.add(near);
            }
        }
        for (LivingEntity victim : caught) {
            strike(player, victim, damage, ECHO_DELAY, () -> {
                ServerLevel level = player.serverLevel();
                double y = victim.getY() + victim.getBbHeight() * 0.6;
                burst(level, ParticleTypes.SWEEP_ATTACK, victim.getX() - 0.35, y, victim.getZ(), 1, 0.0, 0.0);
                burst(level, ParticleTypes.SWEEP_ATTACK, victim.getX() + 0.35, y + 0.15, victim.getZ(), 1, 0.0, 0.0);
                burst(level, ParticleTypes.ENCHANTED_HIT, victim.getX(), y, victim.getZ(), 6, 0.3, 0.2);
            });
        }
        sound(player.serverLevel(), first.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.5f);
        return true;
    }

    /** Cleave: a spin that hits every hostile around you. */
    private static boolean whirl(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> victims = foes(player, player.position(), radius);
        if (victims.isEmpty()) {
            return false;
        }
        for (LivingEntity victim : victims) {
            strike(player, victim, MasteryMath.HIT_DAMAGE, 1, () -> {
                victim.knockback(0.7, player.getX() - victim.getX(), player.getZ() - victim.getZ());
            });
        }
        ring(level, ParticleTypes.SWEEP_ATTACK, player.position().add(0, 0.9, 0), Math.min(radius, 4.5), 8);
        ring(level, ParticleTypes.CRIT, player.position().add(0, 0.6, 0), radius, 14);
        sound(level, player.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.7f);
        return true;
    }

    /** Shockwave: the ground heaves and every hostile around you is thrown into the air. */
    private static boolean tremor(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> victims = foes(player, player.position(), radius);
        if (victims.isEmpty()) {
            return false;
        }
        for (LivingEntity victim : victims) {
            victim.setDeltaMovement(victim.getDeltaMovement().x, MasteryMath.TREMOR_LIFT, victim.getDeltaMovement().z);
            victim.hurtMarked = true;
        }
        ring(level, ParticleTypes.CLOUD, player.position().add(0, 0.2, 0), radius, 20);
        ring(level, ParticleTypes.POOF, player.position().add(0, 0.1, 0), radius * 0.6, 10);
        sound(level, player.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.5f);
        return true;
    }

    /** Riptide Strike: the charge arcs on from the foe to the nearest others. */
    private static boolean arcingTide(ServerPlayer player, @Nullable LivingEntity target, int hops) {
        LivingEntity from = focus(player, target, 8.0);
        if (from == null) {
            return false;
        }
        chain(player, from, hops, MasteryMath.HIT_DAMAGE, ParticleTypes.ELECTRIC_SPARK);
        sound(player.serverLevel(), from.position(), SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.6f);
        return true;
    }

    /** Stunning Blow: a flurry of jabs in the same blink. */
    private static boolean flurry(ServerPlayer player, @Nullable LivingEntity target, int jabs) {
        LivingEntity victim = focus(player, target, 4.5);
        if (victim == null) {
            return false;
        }
        strike(player, victim, MasteryMath.JAB_DAMAGE * jabs, ECHO_DELAY, () -> {
            ServerLevel level = player.serverLevel();
            for (int i = 0; i < jabs; i++) {
                burst(level, ParticleTypes.CRIT, victim.getX() + (level.random.nextDouble() - 0.5) * 0.8,
                        victim.getY() + victim.getBbHeight() * (0.3 + 0.5 * level.random.nextDouble()),
                        victim.getZ() + (level.random.nextDouble() - 0.5) * 0.8, 3, 0.1, 0.1);
            }
            sound(level, victim.position(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.7f, 1.4f);
        });
        return true;
    }

    /** Riposte: the attacker is bashed away. */
    private static boolean shieldBash(ServerPlayer player, @Nullable LivingEntity target, double blocks) {
        LivingEntity attacker = focus(player, target, 4.0);
        if (attacker == null) {
            return false;
        }
        Vec3 away = new Vec3(attacker.getX() - player.getX(), 0, attacker.getZ() - player.getZ());
        if (away.lengthSqr() < 1.0E-4) {
            away = player.getLookAngle().multiply(1, 0, 1);
        }
        double speed = MasteryMath.dashSpeed(blocks, MasteryMath.GROUND_DRAG);
        Vec3 push = away.normalize().scale(speed);
        attacker.setDeltaMovement(push.x, 0.0, push.z);
        attacker.hurtMarked = true;
        strike(player, attacker, MasteryMath.BASH_DAMAGE, ECHO_DELAY, () -> { });
        ServerLevel level = player.serverLevel();
        burst(level, ParticleTypes.CLOUD, attacker.getX(), attacker.getY() + 0.8, attacker.getZ(), 8, 0.3, 0.05);
        sound(level, attacker.position(), SoundEvents.SHIELD_BLOCK, 0.8f, 0.7f);
        return true;
    }

    /** Grit: the first N kinds of harmful effect wash off. */
    private static boolean shrugItOff(ServerPlayer player, int kinds) {
        int removed = 0;
        for (String name : MasteryMath.shrugKinds(kinds)) {
            if (removeEffect(player, name)) {
                removed++;
            }
        }
        if (removed == 0) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        burst(level, ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 8 + 2 * removed, 0.5, 0.0);
        sound(level, player.position(), SoundEvents.BREWING_STAND_BREW, 0.6f, 1.6f);
        return true;
    }

    /** Piercing Shot: the arrow glances on to a second target, and a third, as the stars allow. */
    private static boolean ricochet(ServerPlayer player, @Nullable LivingEntity target, int hops) {
        LivingEntity from = focus(player, target, 24.0);
        if (from == null) {
            return false;
        }
        chain(player, from, hops, MasteryMath.RICOCHET_DAMAGE, ParticleTypes.CRIT);
        sound(player.serverLevel(), from.position(), SoundEvents.ARROW_HIT, 0.8f, 1.4f);
        return true;
    }

    /** Double Load: the bolt splinters and the shrapnel hits everything around the target. */
    private static boolean splinterBolt(ServerPlayer player, @Nullable LivingEntity target, double radius) {
        LivingEntity center = focus(player, target, 16.0);
        if (center == null) {
            return false;
        }
        int i = 0;
        for (LivingEntity victim : foes(player, center.position(), radius)) {
            if (victim == center) {
                continue;
            }
            strike(player, victim, MasteryMath.HIT_DAMAGE, ECHO_DELAY + i++, () -> burst(player.serverLevel(),
                    ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() * 0.5, victim.getZ(),
                    6, 0.25, 0.2));
        }
        ServerLevel level = player.serverLevel();
        ring(level, ParticleTypes.SMOKE, center.position().add(0, 0.6, 0), radius * 0.7, 12);
        burst(level, ParticleTypes.CRIT, center.getX(), center.getY() + center.getBbHeight() * 0.5, center.getZ(), 12, 0.4, 0.3);
        sound(level, center.position(), SoundEvents.CROSSBOW_HIT, 0.9f, 0.8f);
        return true;
    }

    /** Rally: a cry that shoves every hostile back and takes the strength out of its arm. */
    private static boolean warCry(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> victims = foes(player, player.position(), radius);
        if (victims.isEmpty()) {
            return false;
        }
        for (LivingEntity victim : victims) {
            victim.knockback(0.6, player.getX() - victim.getX(), player.getZ() - victim.getZ());
            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MasteryMath.WAR_CRY_TICKS, 0));
        }
        Vec3 eye = player.getEyePosition().add(player.getLookAngle().scale(1.0));
        burst(level, ParticleTypes.SONIC_BOOM, eye.x, eye.y, eye.z, 1, 0.0, 0.0);
        sound(level, player.position(), SoundEvents.RAVAGER_ROAR, 0.5f, 1.3f);
        return true;
    }

    /** Intercept: a pulse that pushes every hostile off you. */
    private static boolean wardingPulse(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> victims = foes(player, player.position(), radius);
        if (victims.isEmpty()) {
            return false;
        }
        for (LivingEntity victim : victims) {
            victim.knockback(1.0, player.getX() - victim.getX(), player.getZ() - victim.getZ());
        }
        ring(level, ParticleTypes.GLOW, player.position().add(0, 0.9, 0), radius * 0.8, 16);
        sound(level, player.position(), SoundEvents.ANVIL_PLACE, 0.35f, 1.8f);
        return true;
    }

    /** Breach: the lane in front of you is cleared, the hostiles in it flung aside. */
    private static boolean stampede(ServerPlayer player, double length) {
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) {
            return false;
        }
        dir = dir.normalize();
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        ServerLevel level = player.serverLevel();
        Vec3 base = player.position();
        int flung = 0;
        for (LivingEntity victim : foes(player, base.add(dir.scale(length / 2)), length / 2 + 1.5)) {
            Vec3 rel = victim.position().subtract(base);
            double along = rel.dot(dir);
            double across = rel.dot(side);
            if (along < -0.5 || along > length || Math.abs(across) > 1.7) {
                continue;
            }
            double sign = across >= 0 ? 1.0 : -1.0;
            victim.setDeltaMovement(side.x * sign * 0.7, 0.45, side.z * sign * 0.7);
            victim.hurtMarked = true;
            strike(player, victim, MasteryMath.HIT_DAMAGE, 2, () -> { });
            flung++;
        }
        if (flung == 0) {
            return false;
        }
        for (int i = 1; i <= (int) length; i++) {
            Vec3 at = base.add(dir.scale(i));
            burst(level, ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 2, 0.3, 0.02);
        }
        sound(level, base, SoundEvents.HORSE_GALLOP, 0.9f, 0.8f);
        return true;
    }

    /** Called Shot: everything hostile around is pinned down and lit up. */
    private static boolean pinningFire(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        int n = 0;
        List<LivingEntity> victims = foes(player, player.position(), radius);
        if (victims.isEmpty()) {
            return false;
        }
        for (LivingEntity victim : victims) {
            victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, MasteryMath.PIN_TICKS, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, MasteryMath.PIN_TICKS, 0, false, false));
            n++;
        }
        ring(level, ParticleTypes.CRIT, player.position().add(0, 0.9, 0), radius * 0.7, 14);
        sound(level, player.position(), SoundEvents.SPYGLASS_USE, 0.7f, 1.2f);
        return true;
    }

    // ---- Gathering ------------------------------------------------------------------------------

    /** Motherlode: every ore around you glows through the rock for a few seconds. */
    private static boolean motherlodePing(ServerPlayer player, int radius) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition();
        List<BlockPos> found = new ArrayList<>();
        java.util.Set<Block> ores = oreBlocks();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        long limit = (long) radius * radius;
        // One look per chunk section first: a section whose palette holds no ore is skipped whole.
        for (int cx = (c.getX() - radius) >> 4; cx <= (c.getX() + radius) >> 4; cx++) {
            for (int cz = (c.getZ() - radius) >> 4; cz <= (c.getZ() + radius) >> 4; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (int sy = (c.getY() - radius) >> 4; sy <= (c.getY() + radius) >> 4; sy++) {
                    int index = chunk.getSectionIndexFromSectionY(sy);
                    if (index < 0 || index >= chunk.getSections().length) {
                        continue;
                    }
                    LevelChunkSection section = chunk.getSection(index);
                    if (section.hasOnlyAir() || !section.maybeHas(st -> ores.contains(st.getBlock()))) {
                        continue;
                    }
                    for (int lx = 0; lx < 16; lx++) {
                        for (int ly = 0; ly < 16; ly++) {
                            for (int lz = 0; lz < 16; lz++) {
                                int wx = (cx << 4) + lx;
                                int wy = (sy << 4) + ly;
                                int wz = (cz << 4) + lz;
                                long dx = wx - c.getX();
                                long dy = wy - c.getY();
                                long dz = wz - c.getZ();
                                if (dx * dx + dy * dy + dz * dz > limit) {
                                    continue;
                                }
                                if (ores.contains(section.getBlockState(lx, ly, lz).getBlock())) {
                                    found.add(new BlockPos(wx, wy, wz));
                                }
                            }
                        }
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        found.sort(Comparator.comparingDouble(b -> b.distSqr(c)));
        int marks = Math.min(found.size(), MasteryMath.MAX_MARKS);
        for (int i = 0; i < marks; i++) {
            BlockPos at = found.get(i);
            MasteryTasks.mark(level, at, level.getBlockState(at).getBlock(), 0.92, GOLD, MasteryMath.MARK_TICKS);
            burst(level, ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.0, at.getZ() + 0.5, 2, 0.1, 0.02);
        }
        ring(level, ParticleTypes.END_ROD, player.position().add(0, 1.0, 0), 1.5, 10);
        sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 1.2f);
        return true;
    }

    private static volatile java.util.Set<Block> oreCache;

    /** Every ore block (name ends in _ore, plus ancient debris), worked out once from the block registry. */
    private static java.util.Set<Block> oreBlocks() {
        java.util.Set<Block> cached = oreCache;
        if (cached == null) {
            cached = new java.util.HashSet<>();
            for (Block block : BuiltInRegistries.BLOCK) {
                String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
                if (path.endsWith("_ore") || path.equals("ancient_debris")) {
                    cached.add(block);
                }
            }
            oreCache = cached;
        }
        return cached;
    }

    /** Timber: saplings go in around the stump. */
    private static boolean replant(ServerPlayer player, @Nullable BlockPos pos, int count) {
        ServerLevel level = player.serverLevel();
        BlockPos center = pos != null ? pos : player.blockPosition();
        BlockState sapling = saplingNear(level, center);
        List<BlockPos> spots = new ArrayList<>();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 3; y >= -3; y--) {
                    BlockPos at = center.offset(x, y, z);
                    if (!level.hasChunkAt(at)) {
                        break;
                    }
                    if (level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(BlockTags.DIRT)
                            && sapling.canSurvive(level, at)) {
                        spots.add(at);
                        break;
                    }
                }
            }
        }
        java.util.Collections.shuffle(spots, new java.util.Random(level.random.nextLong()));
        List<BlockPos> planted = new ArrayList<>();
        for (BlockPos at : spots) {
            if (planted.size() >= count) {
                break;
            }
            boolean crowded = false;
            for (BlockPos other : planted) {
                if (Math.abs(other.getX() - at.getX()) < 2 && Math.abs(other.getZ() - at.getZ()) < 2) {
                    crowded = true;
                    break;
                }
            }
            if (!crowded) {
                level.setBlock(at, sapling, 3);
                planted.add(at);
                burst(level, ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 4, 0.25, 0.0);
            }
        }
        if (planted.isEmpty()) {
            return false;
        }
        sound(level, Vec3.atCenterOf(center), SoundEvents.GRASS_PLACE, 1.0f, 0.8f);
        return true;
    }

    /** The sapling of the tree that stood here: read from a log or leaf nearby, oak when there is none. */
    private static BlockState saplingNear(ServerLevel level, BlockPos center) {
        BlockState best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-5, -2, -5), center.offset(5, 8, 5))) {
            if (!level.hasChunkAt(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)) {
                continue;
            }
            double d = at.distSqr(center);
            if (d >= bestDist) {
                continue;
            }
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            String species = path.replace("stripped_", "").replace("_log", "").replace("_leaves", "")
                    .replace("_wood", "");
            Block sapling = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(species + "_sapling"));
            if (sapling != Blocks.AIR && sapling != null) {
                best = sapling.defaultBlockState();
                bestDist = d;
            }
        }
        return best != null ? best : Blocks.OAK_SAPLING.defaultBlockState();
    }

    private static boolean looseGround(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.MUD);
    }

    /** Landslide: loose ground around the spot gives way. */
    private static boolean landslide(ServerPlayer player, @Nullable BlockPos pos, int count) {
        ServerLevel level = player.serverLevel();
        BlockPos center = pos != null ? pos : player.blockPosition().below();
        BlockPos feet = player.blockPosition();
        List<BlockPos> loose = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-3, -3, -3), center.offset(3, 3, 3))) {
            if (!level.hasChunkAt(at) || !looseGround(level.getBlockState(at))
                    || PlacedBlocks.rawMarked(level, at)
                    || (at.getX() == feet.getX() && at.getZ() == feet.getZ() && at.getY() < feet.getY())) {
                continue;
            }
            boolean wet = false;
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                if (!level.getFluidState(at.relative(d)).isEmpty()) {
                    wet = true;
                    break;
                }
            }
            if (!wet) {
                loose.add(at.immutable());
            }
        }
        loose.sort(Comparator.comparingDouble(b -> b.distSqr(center)));
        int done = 0;
        for (BlockPos at : loose) {
            if (done >= count) {
                break;
            }
            level.destroyBlock(at, true, player);
            done++;
        }
        if (done > 0) {
            sound(level, Vec3.atCenterOf(center), SoundEvents.GRAVEL_BREAK, 1.0f, 0.6f);
        }
        return done > 0;
    }

    /** Bountiful Harvest: the crops around you are bone-mealed and the bare furrows replanted. */
    private static boolean greenThumb(ServerPlayer player, @Nullable BlockPos pos, int count, int stars) {
        ServerLevel level = player.serverLevel();
        BlockPos center = pos != null ? pos : player.blockPosition();
        List<BlockPos> growing = new ArrayList<>();
        List<BlockPos> bare = new ArrayList<>();
        BlockState kind = null;
        for (BlockPos at : BlockPos.betweenClosed(center.offset(-MasteryMath.SEARCH_RADIUS, -2, -MasteryMath.SEARCH_RADIUS),
                center.offset(MasteryMath.SEARCH_RADIUS, 2, MasteryMath.SEARCH_RADIUS))) {
            if (!level.hasChunkAt(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof CropBlock crop) {
                if (kind == null) {
                    kind = crop.defaultBlockState();
                }
                if (!crop.isMaxAge(state)) {
                    growing.add(at.immutable());
                }
            } else if (state.isAir() && level.getBlockState(at.below()).is(Blocks.FARMLAND)) {
                bare.add(at.immutable());
            }
        }
        growing.sort(Comparator.comparingDouble(b -> b.distSqr(center)));
        bare.sort(Comparator.comparingDouble(b -> b.distSqr(center)));
        int grown = 0;
        for (BlockPos at : growing) {
            if (grown >= count) {
                break;
            }
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof CropBlock crop) {
                crop.growCrops(level, at, state);
                level.levelEvent(1505, at, 15);
                grown++;
            }
        }
        int planted = 0;
        if (kind != null) {
            for (BlockPos at : bare) {
                if (planted >= stars) {
                    break;
                }
                level.setBlock(at, kind, 3);
                level.levelEvent(1505, at, 8);
                planted++;
            }
        }
        if (grown + planted == 0) {
            return false;
        }
        sound(level, Vec3.atCenterOf(center), SoundEvents.BONE_MEAL_USE, 1.0f, 1.0f);
        return true;
    }

    /** Treasure Haul: more fish come up on the line and fly to your hands. */
    private static boolean secondCatch(ServerPlayer player, @Nullable BlockPos pos, int fish) {
        ServerLevel level = player.serverLevel();
        int carried = 0;
        for (net.minecraft.world.item.Item kind : new net.minecraft.world.item.Item[] {Items.COD, Items.SALMON,
                Items.TROPICAL_FISH, Items.PUFFERFISH}) {
            carried += player.getInventory().countItem(kind);
        }
        if (carried >= GIFT_CARRY_CAP) {
            return false;
        }
        Vec3 from = pos != null ? Vec3.atCenterOf(pos)
                : player.getEyePosition().add(player.getLookAngle().scale(3.0));
        for (int i = 0; i < fish; i++) {
            ItemEntity item = new ItemEntity(level, from.x, from.y, from.z,
                    new ItemStack(FISH[level.random.nextInt(FISH.length)]));
            Vec3 to = player.position().add(0, 0.5, 0).subtract(from);
            item.setDeltaMovement(to.x * 0.1 + (level.random.nextDouble() - 0.5) * 0.1, 0.3 + to.y * 0.05,
                    to.z * 0.1 + (level.random.nextDouble() - 0.5) * 0.1);
            level.addFreshEntity(item);
        }
        burst(level, ParticleTypes.SPLASH, from.x, from.y + 0.2, from.z, 12 + 4 * fish, 0.4, 0.1);
        sound(level, from, SoundEvents.FISHING_BOBBER_SPLASH, 1.0f, 1.2f);
        return true;
    }

    /** Twins: the whole barn within reach perks up. Wool grows back, hens lay, calves grow. */
    private static boolean openBarn(ServerPlayer player, double radius, int stars) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(radius);
        boolean any = false;
        int eggs = player.getInventory().countItem(Items.EGG) >= GIFT_CARRY_CAP ? 8 : 0;
        for (net.minecraft.world.entity.animal.Animal animal : level.getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class, box,
                a -> a.isAlive() && a.distanceToSqr(player) <= radius * radius
                        && !(a instanceof TamableAnimal t && t.getOwnerUUID() != null
                                && !t.getOwnerUUID().equals(player.getUUID())))) {
            boolean touched = false;
            if (animal.isBaby()) {
                animal.ageUp(MasteryMath.calfGrowthSeconds(stars));
                touched = true;
            } else if (animal instanceof Sheep sheep && sheep.isSheared()) {
                sheep.setSheared(false);
                touched = true;
            } else if (animal instanceof Chicken chicken && eggs < 8) {
                chicken.spawnAtLocation(Items.EGG);
                eggs++;
                touched = true;
            }
            if (touched) {
                any = true;
                burst(level, ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight() + 0.2,
                        animal.getZ(), 1, 0.2, 0.0);
            }
        }
        if (any) {
            sound(level, player.position(), SoundEvents.CHICKEN_EGG, 0.8f, 1.2f);
        }
        return any;
    }

    // ---- Movement -------------------------------------------------------------------------------

    private static boolean dash(ServerPlayer player, Vec3 direction, double blocks, double drag, double lift,
            ParticleOptions trail, SoundEvent sound) {
        if (direction.lengthSqr() < 1.0E-4) {
            return false;
        }
        double speed = MasteryMath.dashSpeed(blocks, drag);
        Vec3 push = direction.normalize().scale(speed);
        Vec3 now = player.getDeltaMovement();
        player.setDeltaMovement(now.x + push.x, now.y + push.y + lift, now.z + push.z);
        player.hurtMarked = true;
        ServerLevel level = player.serverLevel();
        burst(level, trail, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.3, 0.05);
        sound(level, player.position(), sound, 0.5f, 1.4f);
        return true;
    }

    /** Shadowed: hunters lose the trail, a puff of smoke covers the step, you are gone for a moment. */
    private static boolean smokeStep(ServerPlayer player, double radius, int stars) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == player)) {
            mob.setTarget(null);
            // Brain-driven mobs (piglins, wardens, golems) keep their own memory of the target.
            mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
            mob.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ANGRY_AT);
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40 + 10 * stars, 0, false, false));
        burst(level, ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 10 + 2 * stars, 0.5, 0.02);
        sound(level, player.position(), SoundEvents.ENDERMAN_TELEPORT, 0.3f, 1.7f);
        return true;
    }

    /** Featherfall: a spring throws you up, and the air holds you on the way down. */
    private static boolean rebound(ServerPlayer player, double blocks) {
        Vec3 now = player.getDeltaMovement();
        player.setDeltaMovement(now.x, MasteryMath.launchSpeed(blocks), now.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, true));
        ServerLevel level = player.serverLevel();
        burst(level, ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 14, 0.35, 0.08);
        sound(level, player.position(), SoundEvents.SLIME_BLOCK_PLACE, 0.8f, 1.6f);
        return true;
    }

    /** Undertow: the current carries you on and fills your lungs. */
    private static boolean glide(ServerPlayer player, double blocks) {
        if (!player.isInWater()) {
            return false;
        }
        player.setAirSupply(player.getMaxAirSupply());
        return dash(player, player.getLookAngle(), blocks, MasteryMath.WATER_DRAG, 0.0,
                ParticleTypes.BUBBLE, SoundEvents.DOLPHIN_JUMP);
    }

    /** Tailwind: the mount you ride is mended and shakes off what ailed it. */
    private static boolean restedMount(ServerPlayer player, double health) {
        if (!(player.getVehicle() instanceof LivingEntity mount) || !mount.isAlive()) {
            return false;
        }
        mount.heal((float) health);
        for (MobEffectInstance effect : new ArrayList<>(mount.getActiveEffects())) {
            if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                mount.removeEffect(effect.getEffect());
            }
        }
        burst(player.serverLevel(), ParticleTypes.HEART, mount.getX(), mount.getY() + mount.getBbHeight() + 0.2,
                mount.getZ(), 4, 0.4, 0.0);
        sound(player.serverLevel(), mount.position(), SoundEvents.HORSE_BREATHE, 0.8f, 1.1f);
        return true;
    }

    // ---- Crafting -------------------------------------------------------------------------------

    /** Masterwork: heat and hammer on the armour you wear and the tool in your hand. */
    private static boolean quench(ServerPlayer player, double percent) {
        boolean any = false;
        for (ItemStack piece : player.getInventory().armor) {
            any |= repair(piece, MasteryMath.repairAmount(piece.getMaxDamage(), percent));
        }
        ItemStack held = player.getMainHandItem();
        any |= repair(held, MasteryMath.repairAmount(held.getMaxDamage(), percent));
        if (any) {
            ServerLevel level = player.serverLevel();
            burst(level, ParticleTypes.LAVA, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.0);
            burst(level, ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.4, 0.03);
            sound(level, player.position(), SoundEvents.ANVIL_USE, 0.5f, 1.4f);
        }
        return any;
    }

    /** Second Helping: a second plate for you and everyone at your table. */
    private static boolean secondsServed(ServerPlayer player, int food) {
        feed(player, food, food);
        ServerLevel level = player.serverLevel();
        for (ServerPlayer other : others(player, 8.0)) {
            feed(other, food, food);
            burst(level, ParticleTypes.HAPPY_VILLAGER, other.getX(), other.getY() + 1.0, other.getZ(), 5, 0.4, 0.0);
        }
        burst(level, ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.4, 0.0);
        sound(level, player.position(), SoundEvents.GENERIC_EAT, 0.8f, 1.1f);
        return true;
    }

    // ---- Mastery --------------------------------------------------------------------------------

    /** Arcane Surge: bolts fly at separate hostiles. */
    private static boolean magicMissiles(ServerPlayer player, int count) {
        ServerLevel level = player.serverLevel();
        List<LivingEntity> targets = foes(player, player.position(), MasteryMath.MISSILE_RANGE);
        if (targets.isEmpty()) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        for (int i = 0; i < Math.min(count, targets.size()); i++) {
            LivingEntity victim = targets.get(i);
            line(level, ParticleTypes.WITCH, eye, victim.position().add(0, victim.getBbHeight() * 0.5, 0), 8);
            strike(player, victim, MasteryMath.MISSILE_DAMAGE, 2 + 2 * i, () -> burst(level, ParticleTypes.ENCHANT,
                    victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 10, 0.3, 0.4));
        }
        sound(level, player.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.8f, 1.5f);
        return true;
    }

    /** Overengineered: everything dropped around you flies to your feet. */
    private static boolean tractorBeam(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        AABB box = player.getBoundingBox().inflate(radius);
        int pulled = 0;
        Vec3 to = player.position().add(0, 0.4, 0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box,
                i -> i.isAlive() && !i.getItem().isEmpty() && i.distanceToSqr(player) <= radius * radius)) {
            if (pulled >= MasteryMath.MAX_PULLED) {
                break;
            }
            if (item.getOwner() instanceof Player thrower && thrower != player) {
                continue;
            }
            if (item.distanceToSqr(player) < 1.5) {
                continue;
            }
            line(level, ParticleTypes.ELECTRIC_SPARK, item.position(), to, 5);
            item.setPos(to.x, to.y, to.z);
            item.setDeltaMovement(0, 0.1, 0);
            item.setNoPickUpDelay();
            pulled++;
        }
        if (pulled > 0) {
            ring(level, ParticleTypes.ELECTRIC_SPARK, player.position().add(0, 0.5, 0), 1.2, 8);
            sound(level, player.position(), SoundEvents.BEACON_ACTIVATE, 0.6f, 1.8f);
        }
        return pulled > 0;
    }

    // ---- Expedition -----------------------------------------------------------------------------

    /** New Horizon: the nearest structure you have not seen is marked, with the way to it. */
    private static boolean newHorizon(ServerPlayer player, int chunks) {
        ServerLevel level = player.serverLevel();
        // The search walks chunk after chunk on the server thread: one per player every five seconds at most.
        long now = level.getGameTime();
        Long lastSearch = LAST_HORIZON.get(player.getUUID());
        if (lastSearch != null && now >= lastSearch && now - lastSearch < HORIZON_GAP_TICKS) {
            return false;
        }
        LAST_HORIZON.put(player.getUUID(), now);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var generator = level.getChunkSource().getGenerator();
        List<String> local;
        synchronized (LOCAL_STRUCTURES) {
            local = LOCAL_STRUCTURES.get(level);
            if (local == null) {
                var possible = generator.getBiomeSource().possibleBiomes();
                local = new ArrayList<>();
                for (var entry : registry.entrySet()) {
                    HolderSet<net.minecraft.world.level.biome.Biome> biomes = entry.getValue().biomes();
                    if (possible.stream().anyMatch(biomes::contains)) {
                        local.add(entry.getKey().location().toString());
                    }
                }
                LOCAL_STRUCTURES.put(level, local);
            }
        }
        List<Holder<Structure>> wanted = new ArrayList<>();
        for (String raw : StructureIds.unvisitedTargets(local, skills.visitedKeys())) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) {
                registry.getHolder(id).ifPresent(h -> wanted.add((Holder<Structure>) h));
            }
        }
        if (wanted.isEmpty()) {
            return false;
        }
        BlockPos from = player.blockPosition();
        Pair<BlockPos, Holder<Structure>> found = generator.findNearestMapStructure(level,
                HolderSet.direct(wanted), from, chunks, false);
        if (found == null) {
            player.displayClientMessage(Component.translatable("proficiency.mastery.wayfaring.none", chunks)
                    .withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        BlockPos at = found.getFirst();
        ResourceLocation raw = found.getSecond().unwrapKey().map(k -> k.location()).orElse(null);
        if (raw == null) {
            return false;
        }
        String canonical = StructureIds.canonical(raw.toString());
        ResourceLocation id = ResourceLocation.tryParse(canonical);
        Component name = Component.translatableWithFallback(
                "structure." + (id == null ? "minecraft" : id.getNamespace()) + "." + (id == null ? canonical : id.getPath()),
                dev.amman.proficiency.net.DiscoveryNames.fromId(canonical));
        double dx = at.getX() - from.getX();
        double dz = at.getZ() - from.getZ();
        int distance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        player.sendSystemMessage(Component.translatable("proficiency.mastery.wayfaring.found", name, distance,
                compass(dx, dz)).withStyle(ChatFormatting.AQUA));
        // A short trail of sparks points the way.
        Vec3 dir = new Vec3(dx, 0, dz).normalize();
        for (int i = 2; i <= 10; i++) {
            Vec3 spot = player.position().add(dir.scale(i)).add(0, 1.0, 0);
            burst(level, ParticleTypes.END_ROD, spot.x, spot.y, spot.z, 1, 0.0, 0.0);
        }
        sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 0.8f);
        return true;
    }

    /** "NE", "S" and so on, from an east and south offset (north is negative Z). */
    public static String compass(double dx, double dz) {
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        double angle = Math.toDegrees(Math.atan2(dx, -dz));
        int index = (int) Math.floorMod(Math.round(angle / 45.0), 8L);
        return names[index];
    }

    /** Cave Sense: unlooted loot chests and monster spawners glow through the rock. */
    private static boolean caveSense(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition();
        int chunkRadius = (int) Math.ceil(radius / 16.0);
        record Find(BlockPos pos, boolean spawner) {
        }
        List<Find> found = new ArrayList<>();
        for (int cx = (c.getX() >> 4) - chunkRadius; cx <= (c.getX() >> 4) + chunkRadius; cx++) {
            for (int cz = (c.getZ() >> 4) - chunkRadius; cz <= (c.getZ() >> 4) + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockPos at = be.getBlockPos();
                    if (at.distSqr(c) > radius * radius) {
                        continue;
                    }
                    if (be instanceof SpawnerBlockEntity) {
                        found.add(new Find(at, true));
                    } else if (be instanceof RandomizableContainerBlockEntity container
                            && ((RandomizableContainer) container).getLootTable() != null) {
                        found.add(new Find(at, false));
                    }
                }
            }
        }
        if (found.isEmpty()) {
            return false;
        }
        found.sort(Comparator.comparingDouble(f -> f.pos().distSqr(c)));
        for (int i = 0; i < Math.min(found.size(), MasteryMath.MAX_MARKS); i++) {
            Find f = found.get(i);
            MasteryTasks.mark(level, f.pos(), f.spawner() ? Blocks.RED_CONCRETE : Blocks.GOLD_BLOCK, 0.5,
                    f.spawner() ? RED : GOLD, MasteryMath.MARK_TICKS);
            burst(level, ParticleTypes.END_ROD, f.pos().getX() + 0.5, f.pos().getY() + 1.0, f.pos().getZ() + 0.5, 2, 0.1, 0.02);
        }
        sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.8f);
        return true;
    }

    // ---- Construction ---------------------------------------------------------------------------

    private static Block restored(BlockState state) {
        Block b = state.getBlock();
        if (b == Blocks.MOSSY_COBBLESTONE) {
            return Blocks.COBBLESTONE;
        } else if (b == Blocks.MOSSY_STONE_BRICKS || b == Blocks.CRACKED_STONE_BRICKS) {
            return Blocks.STONE_BRICKS;
        } else if (b == Blocks.CRACKED_DEEPSLATE_BRICKS) {
            return Blocks.DEEPSLATE_BRICKS;
        } else if (b == Blocks.CRACKED_DEEPSLATE_TILES) {
            return Blocks.DEEPSLATE_TILES;
        } else if (b == Blocks.CRACKED_NETHER_BRICKS) {
            return Blocks.NETHER_BRICKS;
        } else if (b == Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) {
            return Blocks.POLISHED_BLACKSTONE_BRICKS;
        }
        return null;
    }

    /** Master Mason: weathered, cracked and mossy stonework nearby is repointed. */
    private static boolean repoint(ServerPlayer player, int count) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition();
        List<BlockPos> worn = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(c.offset(-5, -4, -5), c.offset(5, 4, 5))) {
            if (level.hasChunkAt(at) && restored(level.getBlockState(at)) != null && !PlacedBlocks.rawMarked(level, at)) {
                worn.add(at.immutable());
            }
        }
        worn.sort(Comparator.comparingDouble(b -> b.distSqr(c)));
        int done = 0;
        for (BlockPos at : worn) {
            if (done >= count) {
                break;
            }
            level.setBlock(at, restored(level.getBlockState(at)).defaultBlockState(), 3);
            burst(level, ParticleTypes.WAX_OFF, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 6, 0.4, 0.0);
            done++;
        }
        if (done > 0) {
            sound(level, player.position(), SoundEvents.STONE_PLACE, 1.0f, 1.3f);
        }
        return done > 0;
    }

    /** Inspired: flowers open on the grass around you. */
    private static boolean bloom(ServerPlayer player, int count) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition();
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos at : BlockPos.betweenClosed(c.offset(-4, -2, -4), c.offset(4, 2, 4))) {
            if (level.hasChunkAt(at) && level.getBlockState(at).isAir() && level.getBlockState(at.below()).is(Blocks.GRASS_BLOCK)
                    && !at.equals(c) && !at.equals(c.above())) {
                spots.add(at.immutable());
            }
        }
        java.util.Collections.shuffle(spots, new java.util.Random(level.random.nextLong()));
        int done = 0;
        for (BlockPos at : spots) {
            if (done >= count) {
                break;
            }
            BlockState flower = FLOWERS[level.random.nextInt(FLOWERS.length)].defaultBlockState();
            if (flower.canSurvive(level, at)) {
                level.setBlock(at, flower, 3);
                burst(level, ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 3, 0.25, 0.0);
                done++;
            }
        }
        if (done > 0) {
            sound(level, player.position(), SoundEvents.AZALEA_PLACE, 1.0f, 1.4f);
        }
        return done > 0;
    }

    /** Hearthglow: every campfire and candle in reach is lit again, and a warm bite is waiting. */
    private static boolean hearthglow(ServerPlayer player, double radius, int stars) {
        ServerLevel level = player.serverLevel();
        BlockPos c = player.blockPosition();
        int r = (int) radius;
        int lit = 0;
        for (BlockPos at : BlockPos.betweenClosed(c.offset(-r, -r / 2, -r), c.offset(r, r / 2, r))) {
            if (lit >= 24 || at.distSqr(c) > radius * radius || !level.hasChunkAt(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (isHearthLight(state) && !state.getValue(BlockStateProperties.LIT)
                    && !(state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))) {
                level.setBlock(at, state.setValue(BlockStateProperties.LIT, true), 3);
                burst(level, ParticleTypes.FLAME, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 6, 0.2, 0.02);
                lit++;
            }
        }
        if (lit == 0) {
            return false;
        }
        feed(player, 0, MasteryMath.warmBite(stars));
        burst(level, ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 5, 0.4, 0.0);
        sound(level, player.position(), SoundEvents.FIRECHARGE_USE, 0.6f, 1.2f);
        return true;
    }

    private static boolean isHearthLight(BlockState state) {
        return state.hasProperty(BlockStateProperties.LIT)
                && (state.is(BlockTags.CAMPFIRES) || state.is(BlockTags.CANDLES) || state.is(BlockTags.CANDLE_CAKES));
    }

    // ---- Social and Survival ---------------------------------------------------------------------

    /** Good Company: you and everyone in your company regain health. */
    private static boolean toast(ServerPlayer player, float health) {
        ServerLevel level = player.serverLevel();
        for (ServerPlayer other : CompanyBonus.companyNear(player)) {
            if (FakeActors.isFake(other) || !other.isAlive()) {
                continue;
            }
            other.heal(health);
            burst(level, ParticleTypes.HEART, other.getX(), other.getY() + 1.9, other.getZ(), 2, 0.3, 0.0);
        }
        sound(level, player.position(), SoundEvents.GLASS_BREAK, 0.4f, 1.8f);
        return true;
    }

    /** Good Deal: a villager passes you a few loaves, thrown in. */
    private static boolean thrownIn(ServerPlayer player, int loaves) {
        ServerLevel level = player.serverLevel();
        if (player.getInventory().countItem(Items.BREAD) >= GIFT_CARRY_CAP) {
            return false;
        }
        AbstractVillager from = null;
        double best = 64.0;
        for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class,
                player.getBoundingBox().inflate(8.0), AbstractVillager::isAlive)) {
            double d = villager.distanceToSqr(player);
            if (d < best) {
                best = d;
                from = villager;
            }
        }
        Vec3 start = from != null ? from.getEyePosition() : player.getEyePosition().add(player.getLookAngle());
        ItemEntity item = new ItemEntity(level, start.x, start.y, start.z, new ItemStack(BREADS[0], loaves));
        Vec3 to = player.position().add(0, 0.5, 0).subtract(start);
        item.setDeltaMovement(to.x * 0.1, 0.25 + to.y * 0.05, to.z * 0.1);
        level.addFreshEntity(item);
        burst(level, ParticleTypes.HAPPY_VILLAGER, start.x, start.y, start.z, 6, 0.3, 0.0);
        sound(level, start, SoundEvents.VILLAGER_YES, 0.8f, 1.2f);
        return true;
    }

    /** Moonlit: every hostile nearby is lit up in silver. */
    private static boolean moonGaze(ServerPlayer player, double radius) {
        ServerLevel level = player.serverLevel();
        int n = 0;
        List<LivingEntity> victims = foes(player, player.position(), radius);
        if (victims.isEmpty()) {
            return false;
        }
        for (LivingEntity victim : victims) {
            victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, MasteryMath.GAZE_TICKS, 0, false, false));
            burst(level, ParticleTypes.END_ROD, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3, victim.getZ(), 2, 0.1, 0.01);
            n++;
        }
        ring(level, ParticleTypes.END_ROD, player.position().add(0, 0.5, 0), radius * 0.4, 12);
        sound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 0.6f);
        return true;
    }

    // ---- Shared ---------------------------------------------------------------------------------

    /** Whether this entity is a fair target for a rider's damage or debuff. */
    static boolean isFoe(ServerPlayer player, Entity e) {
        if (!(e instanceof LivingEntity living) || e == player || !living.isAlive() || e instanceof Player
                || e instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return false;
        }
        if (NoCombatXp.looksLikeDummy(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath())) {
            return false;
        }
        if (e instanceof TamableAnimal tame && tame.getOwnerUUID() != null) {
            return false;
        }
        if (e instanceof Mob mob && mob.getTarget() == player) {
            return true;
        }
        // Neutral and provoke-only mobs (zombified piglins, piglins, endermen) are not fair game until they are after you.
        if (e instanceof net.minecraft.world.entity.NeutralMob
                || e instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin) {
            return false;
        }
        return e instanceof Enemy;
    }

    /** Foes within {@code radius} of a point, nearest first. */
    private static List<LivingEntity> foes(ServerPlayer player, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        List<LivingEntity> out = new ArrayList<>(player.serverLevel().getEntitiesOfClass(LivingEntity.class, box,
                e -> isFoe(player, e) && e.distanceToSqr(center) <= radius * radius));
        out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        // A mob grinder must not turn one proc into a mass kill or a server hitch.
        return out.size() > MasteryMath.MAX_VICTIMS ? new ArrayList<>(out.subList(0, MasteryMath.MAX_VICTIMS)) : out;
    }

    /** The foe the proc was about, or the nearest foe within range when it names none. */
    @Nullable
    private static LivingEntity focus(ServerPlayer player, @Nullable LivingEntity target, double range) {
        if (target != null && target.isAlive() && !(target instanceof Player)
                && !(target instanceof TamableAnimal tame && tame.getOwnerUUID() != null)
                && !(target instanceof net.minecraft.world.entity.decoration.ArmorStand)
                && !NoCombatXp.looksLikeDummy(BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath())) {
            return target;
        }
        List<LivingEntity> near = foes(player, player.position(), range);
        return near.isEmpty() ? null : near.get(0);
    }

    /**
     * Magic damage with the player as its cause and nothing as its weapon: it is credited to the player
     * but no proc path (they all look for a held weapon, an arrow or a trident) ever reads it, so a
     * rider can never start a proc. It lands {@code delay} ticks later, after the proc's own blow.
     */
    private static void strike(ServerPlayer player, LivingEntity victim, float damage, int delay, Runnable fx) {
        MasteryTasks.later(player.serverLevel().getServer(), delay, () -> {
            if (!victim.isAlive() || victim.level() != player.level()) {
                return;
            }
            victim.invulnerableTime = 0;
            victim.hurt(player.serverLevel().damageSources().indirectMagic(null, player), damage);
            fx.run();
        });
    }

    /** A chain through up to {@code hops} further foes, each the nearest unvisited one to the last. */
    private static void chain(ServerPlayer player, LivingEntity from, int hops, float damage, ParticleOptions trail) {
        ServerLevel level = player.serverLevel();
        Set<Entity> seen = new HashSet<>();
        seen.add(from);
        LivingEntity last = from;
        for (int i = 0; i < hops; i++) {
            LivingEntity next = null;
            for (LivingEntity candidate : foes(player, last.position(), MasteryMath.HOP_RANGE)) {
                if (!seen.contains(candidate)) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                break;
            }
            seen.add(next);
            Vec3 a = last.position().add(0, last.getBbHeight() * 0.5, 0);
            Vec3 b = next.position().add(0, next.getBbHeight() * 0.5, 0);
            LivingEntity victim = next;
            strike(player, victim, damage, ECHO_DELAY + 3 * i, () -> {
                line(level, trail, a, b, 8);
                burst(level, trail, b.x, b.y, b.z, 8, 0.25, 0.2);
            });
            last = next;
        }
    }

    private static boolean removeEffect(ServerPlayer player, String name) {
        ResourceLocation id = ResourceLocation.withDefaultNamespace(name);
        var holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
        return holder.isPresent() && player.removeEffect(holder.get());
    }

    private static void burst(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count,
            double spread, double speed) {
        level.sendParticles(particle, x, y, z, count, spread, spread, spread, speed);
    }

    private static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(particle, center.x + Math.cos(a) * radius, center.y, center.z + Math.sin(a) * radius,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, int steps) {
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.lerp(to, i / (double) steps);
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    /** Food points and saturation, added plainly (FoodData.eat scales saturation by the food points). */
    private static void feed(ServerPlayer player, int food, float saturation) {
        net.minecraft.world.food.FoodData data = player.getFoodData();
        float total = data.getSaturationLevel() + saturation;
        // Saturation cannot exceed the food level, so a hungry player is topped up just enough to hold it.
        data.setFoodLevel(Math.min(20, Math.max(data.getFoodLevel() + food, (int) Math.ceil(total))));
        data.setSaturation(Math.min(data.getFoodLevel(), total));
    }

    private static boolean repair(ItemStack stack, int amount) {
        if (!stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() > 0) {
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
            return true;
        }
        return false;
    }

    /** Players other than {@code player} within {@code radius} blocks, alive and playing. */
    private static List<ServerPlayer> others(ServerPlayer player, double radius) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class,
                player.getBoundingBox().inflate(radius))) {
            if (other != player && other.isAlive() && !other.isSpectator() && !FakeActors.isFake(other)
                    && other.distanceToSqr(player) <= radius * radius) {
                out.add(other);
            }
        }
        return out;
    }

    /**
     * Alchemy: stretches every helpful effect on the player by {@code percent} of what it has left
     * (at least 5 s, at most 2 min), never past 20 minutes in all.
     */
    private static boolean extendHelpful(ServerPlayer player, double percent) {
        List<MobEffectInstance> helpful = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL && !instance.isInfiniteDuration()
                    && instance.getDuration() < MasteryMath.ECHO_CAP_TICKS) {
                helpful.add(instance);
            }
        }
        boolean any = false;
        for (MobEffectInstance old : helpful) {
            int add = MasteryMath.echoAdd(old.getDuration(), percent);
            if (add <= 0) {
                continue;
            }
            player.addEffect(new MobEffectInstance(old.getEffect(), old.getDuration() + add, old.getAmplifier(),
                    old.isAmbient(), old.isVisible(), old.showIcon()));
            any = true;
        }
        return any;
    }

    /** A few gold sparks, a soft chime and the rider's name above the hotbar. */
    private static void flourish(ServerPlayer player, Skill skill, int stars) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_FLOURISH.get(player.getUUID());
        if (last != null && now - last < FLOURISH_GAP_TICKS && now >= last) {
            return;
        }
        LAST_FLOURISH.put(player.getUUID(), now);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.WAX_ON, player.getX(), player.getY() + 1.0, player.getZ(),
                3 + stars, 0.35, 0.5, 0.35, 0.0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.35f, 1.2f + 0.1f * stars);
        player.displayClientMessage(Component.literal("★".repeat(stars) + " ")
                .append(Component.translatable(MasteryEffects.of(skill).nameKey()))
                .withStyle(ChatFormatting.GOLD), true);
    }
}
