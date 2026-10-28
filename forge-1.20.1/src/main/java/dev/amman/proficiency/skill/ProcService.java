package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
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
import net.minecraftforge.common.MinecraftForge;
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
        return fire(player, skill, target, pos, 1.0);
    }

    /** As {@link #fire(Player, Skill, LivingEntity)}, with the chance scaled (a critical hit doubles it). */
    public static boolean fire(Player player, Skill skill, @Nullable LivingEntity target, double chanceScale) {
        return fire(player, skill, target, null, chanceScale);
    }

    /**
     * As {@link #fire(Player, Skill, BlockPos)}, with {@code bonus} added to the chance after every
     * other factor (Seam Sense's Motherlode charge). The chance never passes 1.
     */
    public static boolean fireWithBonus(Player player, Skill skill, @Nullable BlockPos pos, double bonus) {
        return fire(player, skill, null, pos, 1.0, bonus);
    }

    private static boolean fire(Player player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos, double chanceScale) {
        return fire(player, skill, target, pos, chanceScale, 0.0);
    }

    private static boolean fire(Player player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos, double chanceScale, double bonus) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()) {
            return false;
        }
        // A frenzy is not a better roll, it is no roll at all.
        if (ActiveService.isFrenzied(player, skill)) {
            celebrate(serverPlayer, skill, target, pos);
            MinecraftForge.EVENT_BUS.post(new SkillProcEvent(serverPlayer, skill, target, pos, false));
            return true;
        }
        if (forced(serverPlayer, skill)) {
            celebrate(serverPlayer, skill, target, pos);
            MinecraftForge.EVENT_BUS.post(new SkillProcEvent(serverPlayer, skill, target, pos, true));
            return true;
        }
        double chance = dev.amman.proficiency.skill.CritRules.scaledChance(
                ProficiencyAttachments.of(player).procChance(skill) * situational(serverPlayer, skill),
                chanceScale);
        if (chance > 0 && bonus > 0) {
            chance = Math.min(1.0, chance + bonus);
        }
        if (chance <= 0) {
            return false;
        }
        boolean landed = serverPlayer.serverLevel().getRandom().nextDouble() < chance;
        TelemetryService.roll(serverPlayer, skill, chance, landed);
        if (!landed) {
            return false;
        }
        celebrate(serverPlayer, skill, target, pos);
        ActiveService.onProc(serverPlayer, skill);
        MinecraftForge.EVENT_BUS.post(new SkillProcEvent(serverPlayer, skill, target, pos, true));
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

    /** Takes back a {@link #forceNext} that has gone stale (Fishing Trip's 60 s). */
    public static void unforceNext(Player player, Skill skill) {
        var set = FORCED.get(player.getUUID());
        if (set != null) {
            set.remove(skill);
        }
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
    public static double situational(ServerPlayer player, Skill skill) {
        double multiplier = 1.0;
        if ((skill == Skill.TRIDENTS || skill == Skill.FISHING)
                && player.isInWaterOrRain()
                && TalentService.hasSynergy(player, "tidecaller")) {
            multiplier *= 2.0;
        }
        // Moon Garden: Bountiful Harvest fires half again as often once the sun is down.
        if (skill == Skill.FARMING && NightwalkerService.isNight(player.level())
                && TalentService.hasSynergy(player, "moon_garden")) {
            multiplier *= 1.5;
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

    /**
     * The chance an ordinary roll of {@link #fire(Player, Skill, BlockPos)} lands, without the
     * frenzy or a forced proc. Masonry's free-block rule (RefundMath) needs it to split its total.
     */
    public static double chance(ServerPlayer player, Skill skill) {
        return Math.min(1.0, Math.max(0.0, dev.amman.proficiency.skill.CritRules.scaledChance(
                ProficiencyAttachments.of(player).procChance(skill) * situational(player, skill), 1.0)));
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
        TelemetryService.proc(player, skill);
        ProficiencyAdvancements.award(player, Achievements.SIGNATURE_PROC);
        // The name of the moment is drawn above the crosshair by the client (ProcCallout), from the
        // same packet as the particles: where you are looking, not down on the action bar.

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
                sound(skill), SoundSource.PLAYERS, skill == Skill.MINING ? 0.5f : 0.7f, pitch(skill));
        // The particles are the client's job (ProcFx has the recipe). Each client can switch them off.
        ProcFxSender.send(player, skill, target, pos);
        // Mastery stars: the skill's own rider on its own proc (nothing below level 100 with a star).
        MasteryRiders.onProc(player, skill, target, pos);
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

    private static SoundEvent sound(Skill skill) {
        if (skill == Skill.MINING) {
            // A deep stone crunch, not the XP ding: Motherlode fires often on a long dig.
            return SoundEvents.DEEPSLATE_BREAK;
        }
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
        if (skill == Skill.MINING) {
            return 0.75f;
        }
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
