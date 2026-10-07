package dev.amman.proficiency.item;

import dev.amman.proficiency.Proficiency;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What the station weapons and enchantments do. The enchantments are data
 * ({@code data/proficiency/enchantment}) with no effects of their own; this reads their level.
 * Neither is in any enchanting-table, loot or trade tag, so a Runescribe book is the only source.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class SpecialItemEvents {

    public static final ResourceKey<Enchantment> LIFEDRINKER =
            ResourceKey.create(Registries.ENCHANTMENT, Proficiency.id("lifedrinker"));
    public static final ResourceKey<Enchantment> MAGNETISM =
            ResourceKey.create(Registries.ENCHANTMENT, Proficiency.id("magnetism"));

    static final int RAPIER_CHAIN = 3;
    private static final int RAPIER_WINDOW_TICKS = 60;
    private static final float RAPIER_BONUS = 1.5f;
    private static final int LIFEDRINKER_COOLDOWN_TICKS = 10;

    private static final Map<UUID, Chain> CHAINS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_DRINK = new ConcurrentHashMap<>();

    private SpecialItemEvents() {
    }

    /** Butcher's Cleaver: an animal it kills drops its food twice. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide() || !(dead instanceof Animal)
                || !(event.getSource().getEntity() instanceof Player player)
                || !player.getMainHandItem().is(ProficiencyItems.BUTCHERS_CLEAVER.get())) {
            return;
        }
        List<ItemEntity> copies = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().has(DataComponents.FOOD)) {
                ItemEntity copy = new ItemEntity(dead.level(), drop.getX(), drop.getY(), drop.getZ(),
                        drop.getItem().copy());
                copy.setDefaultPickUpDelay();
                copies.add(copy);
            }
        }
        event.getDrops().addAll(copies);
    }

    /** Duelist's Rapier: every third hit on the same target inside three seconds is 50% harder. */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        Player player = melee(event.getSource());
        if (player == null || !player.getMainHandItem().is(ProficiencyItems.DUELISTS_RAPIER.get())) {
            return;
        }
        LivingEntity target = event.getEntity();
        long now = target.level().getGameTime();
        Chain before = CHAINS.get(player.getUUID());
        int hits = before != null && before.target() == target.getId() && now - before.tick() <= RAPIER_WINDOW_TICKS
                ? before.hits() + 1 : 1;
        if (hits >= RAPIER_CHAIN) {
            event.setAmount(event.getAmount() * RAPIER_BONUS);
            CHAINS.remove(player.getUUID());
            if (target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.5), target.getZ(),
                        12, 0.3, 0.3, 0.3, 0.2);
                level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT,
                        SoundSource.PLAYERS, 1.0f, 1.3f);
            }
        } else {
            CHAINS.put(player.getUUID(), new Chain(target.getId(), hits, now));
        }
    }

    /** Lifedrinker: a melee hit that lands heals you 1 health per level, twice a second at most. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        Player player = melee(event.getSource());
        if (player == null || event.getNewDamage() <= 0) {
            return;
        }
        int level = level(player.level(), player.getMainHandItem(), LIFEDRINKER);
        if (level <= 0) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_DRINK.get(player.getUUID());
        if (last != null && now - last < LIFEDRINKER_COOLDOWN_TICKS) {
            return;
        }
        LAST_DRINK.put(player.getUUID(), now);
        player.heal(level);
    }

    /** Magnetism: what a block drops goes straight into your inventory. Late, after every doubler. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)
                || level(event.getLevel(), event.getTool(), MAGNETISM) <= 0) {
            return;
        }
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack stack = drops.next().getItem();
            player.getInventory().add(stack);
            if (stack.isEmpty()) {
                drops.remove();
            }
        }
    }

    private static Player melee(DamageSource source) {
        if (source.getEntity() instanceof Player player && source.getDirectEntity() == player) {
            return player;
        }
        return null;
    }

    static int level(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) {
            return 0;
        }
        return level.registryAccess().registry(Registries.ENCHANTMENT)
                .flatMap(registry -> registry.getHolder(key))
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }

    private record Chain(int target, int hits, long tick) {
    }
}
