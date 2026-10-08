package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.perk.TalentService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import dev.amman.proficiency.platform.bus.NeoForge;
import org.jetbrains.annotations.Nullable;

/**
 * The signature moments. A linear percentage bonus is invisible while you play: you cannot feel
 * 40% more damage, you only see it in a tooltip. A proc you feel, because the game stops and tells
 * you. Everything here exists to make the moment loud.
 */
public final class ProcService {

    private ProcService() {
    }

    /** Rolls a skill's proc and, if it lands, does the noise. Returns whether it landed. */
    public static boolean fire(Player player, Skill skill) {
        return fire(player, skill, null, null);
    }

    /** As {@link #fire(Player, Skill)}, telling {@link SkillProcEvent} listeners what it was about. */
    public static boolean fire(Player player, Skill skill, @Nullable LivingEntity target) {
        return fire(player, skill, target, null);
    }

    public static boolean fire(Player player, Skill skill, @Nullable BlockPos pos) {
        return fire(player, skill, null, pos);
    }

    public static boolean fire(Player player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return false;
        }
        // A frenzy is not a better roll, it is no roll at all.
        if (ActiveService.isFrenzied(player, skill)) {
            celebrate(serverPlayer, skill, target, pos);
            NeoForge.EVENT_BUS.post(new SkillProcEvent(serverPlayer, skill, target, pos, false));
            return true;
        }
        if (forced(serverPlayer, skill)) {
            celebrate(serverPlayer, skill, target, pos);
            NeoForge.EVENT_BUS.post(new SkillProcEvent(serverPlayer, skill, target, pos, true));
            return true;
        }
        double chance = ProficiencyAttachments.of(player).procChance(skill)
                * situational(serverPlayer, skill);
        if (chance <= 0 || serverPlayer.serverLevel().getRandom().nextDouble() >= chance) {
            return false;
        }
        celebrate(serverPlayer, skill, target, pos);
        ActiveService.onProc(serverPlayer, skill);
        NeoForge.EVENT_BUS.post(new SkillProcEvent(serverPlayer, skill, target, pos, true));
        return true;
    }

    /**
     * A talent can promise "the next one is certain" (Sword Saint, Marksman, Cratermaker). It says
     * so by calling {@link #forceNext}; the next roll for that skill then lands without rolling.
     */
    private static final java.util.Map<java.util.UUID, java.util.EnumSet<Skill>> FORCED =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void forceNext(Player player, Skill skill) {
        FORCED.computeIfAbsent(player.getUUID(), id -> java.util.EnumSet.noneOf(Skill.class)).add(skill);
    }

    private static boolean forced(ServerPlayer player, Skill skill) {
        var set = FORCED.get(player.getUUID());
        if (set != null && set.remove(skill)) {
            return true;
        }
        var test = TEST_FORCED.get(player.getUUID());
        if (test == null) {
            return false;
        }
        Long until = test.remove(skill);
        return until != null && System.currentTimeMillis() < until;
    }

    /** Op test tool: like {@link #forceNext}, but the flag lapses after {@code millis}. */
    private static final java.util.Map<java.util.UUID, java.util.Map<Skill, Long>> TEST_FORCED =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void forceNextFor(Player player, Skill skill, long millis) {
        TEST_FORCED.computeIfAbsent(player.getUUID(), id -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(skill, System.currentTimeMillis() + millis);
    }

    public static void forget(java.util.UUID player) {
        FORCED.remove(player);
        TEST_FORCED.remove(player);
    }

    /**
     * Turns a fractional amount into a whole one fairly: 1.4 is 1 sixty percent of the time and 2
     * the rest. Proc power is a multiplier like 1.45, and item counts cannot be.
     */
    public static int roundRandomly(Player player, double amount) {
        if (!(amount > 0)) {
            return 0;
        }
        int whole = (int) Math.floor(amount);
        return whole + (player.getRandom().nextDouble() < amount - whole ? 1 : 0);
    }

    /**
     * The synergies that care where you are rather than what you own. They cannot live in
     * {@link PlayerSkills#procChance}, which is pure data and has no world to look at.
     */
    static double situational(ServerPlayer player, Skill skill) {
        double multiplier = 1.0;
        if ((skill == Skill.TRIDENTS || skill == Skill.FISHING)
                && player.isInWaterOrRain()
                && TalentService.hasSynergy(player, "tidecaller")) {
            multiplier *= 2.0;
        }
        // Charger: a charge that ends in a sprint attack breaches more often.
        if (skill == Skill.CHARGER && dev.amman.proficiency.event.ChargerEvents.sprintRoll()) {
            multiplier *= ChargerMath.SPRINT_BREACH;
        }
        if (skill == Skill.MINING && player.getY() < 0
                && TalentService.hasSynergy(player, "deep_delver")) {
            multiplier *= 1.5;
        }
        return multiplier;
    }

    /** How hard a proc lands, raised by Precision and Mastery. */
    public static double power(Player player, Skill skill) {
        return ProficiencyAttachments.of(player).procPower(skill);
    }

    /** Announces a proc that some other code decided had happened. */
    public static void celebrate(ServerPlayer player, Skill skill) {
        celebrate(player, skill, null, null);
    }

    /** As above, with what the proc was about, so the particles can appear there. */
    public static void celebrate(ServerPlayer player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos) {
        player.displayClientMessage(
                Component.translatable(skill.procKey())
                        .withStyle(ChatFormatting.BOLD)
                        .withStyle(style -> style.withColor(accent(skill))),
                true);

        ItemStack spoils = specialistDrop(skill);
        if (!spoils.isEmpty()) {
            // Trophy, Seasoned Traveler and the rest: proc power buys more than one.
            spoils.setCount(Math.max(1, roundRandomly(player, power(player, skill))));
            player.getInventory().placeItemBackInInventory(spoils.copy());
            // Anyone learning from you gets one too. Costs the expert nothing, and it is the one
            // moment where standing next to a specialist hands you exactly what your tree wants.
            for (ServerPlayer student : CompanyBonus.studentsNear(player, skill)) {
                student.getInventory().placeItemBackInInventory(spoils.copy());
                student.displayClientMessage(Component.translatable("proficiency.company.shared",
                        player.getDisplayName(), spoils.getHoverName()), true);
            }
        }

        ServerLevel level = player.serverLevel();
        // Not playNotifySound: everyone nearby should hear that something happened to you.
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound(skill), SoundSource.PLAYERS, 0.7f, pitch(skill));
        // The particles are the client's job (ProcFx has the recipe). Each client can switch them off.
        ProcFxSender.send(player, skill, target, pos);
    }

    /**
     * The four materials another player's tree wants. They only ever come out of a proc, so having
     * a stack of them is proof you actually practise that skill.
     */
    private static ItemStack specialistDrop(Skill skill) {
        return switch (skill) {
            case SMITHING -> new ItemStack(ProficiencyItems.MASTERWORK_INGOT.get());
            case ALCHEMY -> new ItemStack(ProficiencyItems.PROSPECTORS_DRAUGHT.get());
            case BEASTSLAYING -> new ItemStack(ProficiencyItems.HUNTERS_CHARM.get());
            case WAYFARING -> new ItemStack(ProficiencyItems.WANDERERS_TOKEN.get());
            case ENGINEERING -> ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }

    private static int accent(Skill skill) {
        return switch (skill.category()) {
            case COMBAT -> 0xD4695A;
            case GATHERING -> 0x7FAE63;
            case MOVEMENT -> 0x5F9EC4;
            case CRAFTING -> 0xD2A249;
            case MASTERY -> 0x9B7FC4;
            case EXPEDITION -> 0xC98A4B;
            case CONSTRUCTION -> 0x6FB0A6;
            case SOCIAL -> 0xD98AB3;
            case SURVIVAL -> 0x6F7FC9;
        };
    }

    private static SoundEvent sound(Skill skill) {
        return switch (skill.category()) {
            case COMBAT -> SoundEvents.PLAYER_ATTACK_CRIT;
            case GATHERING -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case MOVEMENT -> SoundEvents.BEACON_ACTIVATE;
            case CRAFTING -> SoundEvents.ANVIL_USE;
            case MASTERY -> SoundEvents.AMETHYST_BLOCK_CHIME;
            case EXPEDITION -> SoundEvents.RAID_HORN.value();
            case CONSTRUCTION -> SoundEvents.STONE_PLACE;
            case SOCIAL -> SoundEvents.NOTE_BLOCK_BELL.value();
            case SURVIVAL -> SoundEvents.AMETHYST_BLOCK_RESONATE;
        };
    }

    private static float pitch(Skill skill) {
        return switch (skill.category()) {
            case COMBAT -> 0.9f;
            case GATHERING -> 1.4f;
            case MOVEMENT -> 1.6f;
            case CRAFTING -> 1.3f;
            case MASTERY -> 1.0f;
            case EXPEDITION -> 1.2f;
            case CONSTRUCTION -> 1.5f;
            case SOCIAL -> 1.2f;
            case SURVIVAL -> 0.8f;
        };
    }

    public static ParticleOptions particleFor(Skill skill) {
        return switch (skill.category()) {
            case COMBAT -> ParticleTypes.CRIT;
            case GATHERING -> ParticleTypes.HAPPY_VILLAGER;
            case MOVEMENT -> ParticleTypes.CLOUD;
            case CRAFTING -> ParticleTypes.ENCHANT;
            case MASTERY -> ParticleTypes.WITCH;
            case EXPEDITION -> ParticleTypes.FLAME;
            case CONSTRUCTION -> ParticleTypes.COMPOSTER;
            case SOCIAL -> ParticleTypes.HEART;
            case SURVIVAL -> ParticleTypes.END_ROD;
        };
    }
}
