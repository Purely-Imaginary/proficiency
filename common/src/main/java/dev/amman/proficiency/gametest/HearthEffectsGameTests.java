package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.event.ExhaustionWatch;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthDataLoader;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.effects.HearthStations;
import dev.amman.proficiency.hearth.effects.HearthStreak;
import dev.amman.proficiency.hearth.effects.HearthTempHp;
import dev.amman.proficiency.hearth.effects.HearthWorkshop;
import dev.amman.proficiency.hearth.effects.HomeRules;
import dev.amman.proficiency.hearth.effects.PlayerEffects;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.entity.living.LivingIncomingDamageEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SurvivalStreak;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The effects lane in a real world, on both loaders (docs/HEARTH-SPEC.md, abuse checks 5, 6, 8, 9,
 * 10, 11 and 12, plus Hearty, Provisioned and the "ends within 2 s" rule). The numbers themselves
 * are unit tested in core ({@code hearth/effects}).
 *
 * <p>Every test primes its Hearth's state and steps the per-player second by hand in the same tick
 * ({@link PlayerEffects#second}), so the other lanes' Hearth ticks (Comfort scans, the structure
 * check) cannot change the state between the priming and the check. Each test has its own batch:
 * the {@code empty} template is tiny and a neighbour's Hearth would be "in range".
 */
public final class HearthEffectsGameTests {

    private static final String T = "proficiency:empty";
    private static final BlockPos HEARTH = new BlockPos(1, 2, 1);

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HearthEffectsGameTests() {
    }

    // ---- Set-up ---------------------------------------------------------------------------------

    private static final class Cast {
        final GameTestHelper helper;
        final ServerLevel level;
        final HearthBlockEntity hearth;
        final List<ServerPlayer> players = new ArrayList<>();

        Cast(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            HearthWorkshop.setTestRoll(null);
            // Leftovers from earlier tests (mock players never leave) go far up and out of every range.
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other.position().distanceTo(origin) < 96) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            BlockState state = HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, 6);
            helper.setBlock(HEARTH, state);
            if (!(level.getBlockEntity(helper.absolutePos(HEARTH)) instanceof HearthBlockEntity entity)) {
                throw new IllegalStateException("no Hearth block entity");
            }
            hearth = entity;
        }

        /** A survival player beside the Hearth, no spawn protection, login grace over. */
        ServerPlayer player() {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
            clearSpawnProtection(player);
            PlayerEffects.endGrace(player);
            HearthAttachments.of(player).setLastDamage(PlayerHearth.NEVER);
            players.add(player);
            return player;
        }

        /**
         * The Hearth at a given build: stage, Character, the first {@code comfort} kinds, and each pool
         * charged to {@code tiers[pool]} (index = pool ordinal; a shorter array leaves the rest at 0).
         */
        HearthState prime(int stage, int character, int comfort, int... tiers) {
            HearthState state = hearth.state();
            HearthData d = Hearths.data();
            state.setSmouldering(false);
            state.setStage(stage);
            state.setHighestOffering(stage);
            state.setCharacter(character);
            Set<String> kinds = new HashSet<>(HearthData.DEFAULT_COMFORT_KINDS.subList(0,
                    Math.min(comfort, HearthData.DEFAULT_COMFORT_KINDS.size())));
            state.setComfortKinds(kinds);
            for (HearthPool pool : HearthPool.VALUES) {
                int tier = pool.ordinal() < tiers.length ? tiers[pool.ordinal()] : 0;
                state.setCharge(pool, tier <= 0 ? 0.0 : d.threshold(tier) + 1.0);
            }
            hearth.setChanged();
            return state;
        }

        /** Every pool at VI, a complete stage VI Hearth with all twelve Comfort kinds. */
        HearthState primeFull() {
            return prime(6, 50, 12, 6, 6, 6, 6, 6);
        }

        /** In range: next to the Hearth, and seen by it this tick. */
        void home(ServerPlayer player) {
            player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
            HearthAttachments.of(player).seenBy(hearth.packedPos(), level.dimension().location().toString(),
                    level.getGameTime());
        }

        /** Out of range, far away in the same dimension. */
        void away(ServerPlayer player, int slot) {
            player.setPos(1_000_000.0 + slot * 5_000.0, 80.0, 1_000_000.0 + 7_000.0);
            HearthAttachments.of(player).clearInRange();
        }

        void seconds(ServerPlayer player, int n, boolean atHome) {
            for (int i = 0; i < n; i++) {
                if (atHome) {
                    home(player);
                }
                PlayerEffects.second(player);
            }
        }

        void done() {
            HearthWorkshop.setTestRoll(null);
            for (ServerPlayer player : players) {
                player.server.getPlayerList().remove(player);
            }
            helper.setBlock(HEARTH, Blocks.AIR);
            helper.succeed();
        }
    }

    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not clear spawn protection", e);
        }
    }

    /** What a hit of {@code amount} becomes once the shared incoming-damage event ran. */
    private static float incoming(ServerPlayer player, DamageSource source, float amount) {
        return NeoForge.EVENT_BUS.post(new LivingIncomingDamageEvent(player, new DamageContainer(source, amount)))
                .getAmount();
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-3;
    }

    private static int count(ServerPlayer player, Item item) {
        int n = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    // ---- 5: Fortified never cuts players or their pets -------------------------------------------

    @GameTest(template = T, batch = "hearth_fx_fortified")
    public static void fortifiedCutsMobsButNeverPlayersOrTheirPets(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        ServerPlayer friend = cast.player();
        PlayerHearth data = HearthAttachments.of(player);
        cast.primeFull();
        cast.home(player);
        data.setWarmth(1.0);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        Wolf wolf = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(3, 2, 1));
        wolf.tame(friend);
        Wolf stray = helper.spawnWithNoFreeWill(EntityType.WOLF, new BlockPos(1, 2, 3));
        var sources = cast.level.damageSources();

        float mob = incoming(player, sources.mobAttack(zombie), 10f);
        helper.assertTrue(near(mob, 8f), "a zombie's 10 at Fortified VI and full Warmth should be 8, was " + mob);
        helper.assertTrue(near(incoming(player, sources.mobAttack(stray), 10f), 8f), "a wild wolf is a mob");
        float pvp = incoming(player, sources.playerAttack(friend), 10f);
        helper.assertTrue(near(pvp, 10f), "a player's hit is never cut, was " + pvp);
        float pet = incoming(player, sources.mobAttack(wolf), 10f);
        helper.assertTrue(near(pet, 10f), "a player's tamed wolf is never cut, was " + pet);
        helper.assertTrue(near(incoming(player, sources.fall(), 10f), 10f), "falls are never cut");
        helper.assertTrue(near(incoming(player, sources.lava(), 10f), 10f), "lava is never cut");

        // Scaled by Warmth.
        data.setWarmth(0.5);
        cast.home(player);
        helper.assertTrue(near(incoming(player, sources.mobAttack(zombie), 10f), 9f), "half Warmth, half the cut");
        // Away from home: nothing.
        data.setWarmth(1.0);
        cast.away(player, 51);
        helper.assertTrue(near(incoming(player, sources.mobAttack(zombie), 10f), 10f), "no Fortified away");

        // A real hit through the loader's damage path: the home hit loses 80% of the away hit.
        player.setHealth(20f);
        player.invulnerableTime = 0;
        player.hurt(sources.mobAttack(zombie), 6f);
        float lostAway = 20f - player.getHealth();
        cast.primeFull();
        cast.home(player);
        player.setHealth(20f);
        player.invulnerableTime = 0;
        player.hurt(sources.mobAttack(zombie), 6f);
        float lostHome = 20f - player.getHealth();
        helper.assertTrue(lostAway <= 0f || near(lostHome, lostAway * 0.8f),
                "a real hit at home: lost " + lostHome + ", away " + lostAway);
        // Every hit marked combat: Warmth does not rise for 5 s.
        helper.assertTrue(data.lastDamage() == cast.level.getGameTime(), "the hit should mark combat");
        zombie.discard();
        wolf.discard();
        stray.discard();
        cast.done();
    }

    // ---- 6: a death at home costs what death always costs ---------------------------------------

    @GameTest(template = T, batch = "hearth_fx_death")
    public static void aDeathAtHomeCostsWhatDeathAlwaysCosts(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer dead = cast.player();
        ServerPlayer fresh = cast.player();
        PlayerHearth data = HearthAttachments.of(dead);
        cast.primeFull();
        data.setWarmth(1.0);
        dead.setHealth(dead.getMaxHealth());
        cast.seconds(dead, 12, true);
        helper.assertTrue(dead.getAbsorptionAmount() > 0f && HearthTempHp.modifier(dead) > 0,
                "setup: temp HP should have filled, absorption " + dead.getAbsorptionAmount());
        PlayerSkills skills = ProficiencyAttachments.of(dead);
        skills.setStreakTicks(ProficiencyConfig.streakStepTicks() * 7);
        SkillService.grant(dead, Skill.MINING, 5.0, "block.minecraft.stone");
        skills.setRested(Skill.FARMING, skills.restedCap(Skill.FARMING));
        helper.assertTrue(skills.barProgress(Skill.MINING) > 0f, "setup: a partial Mining bar");
        data.addTales(30);
        long now = cast.level.getGameTime();
        data.provision(0.4, now, now + 20_000);
        // Abuse check 6 names "dying in range, or in a siege": this one dies while the Hearth's siege runs.
        cast.hearth.state().setSiegePhase(SiegeRules.Phase.ACTIVE);

        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
        cast.hearth.state().setSiegePhase(SiegeRules.Phase.IDLE);

        PlayerSkills after = ProficiencyAttachments.of(fresh);
        PlayerHearth carried = HearthAttachments.of(fresh);
        helper.assertTrue(after.streakTicks() == 0L, "the survival bonus is wiped at home too: " + after.streakTicks());
        helper.assertTrue(after.barProgress(Skill.MINING) == 0f, "the bars are wiped at home too");
        helper.assertTrue(after.rested(Skill.FARMING) == 0f, "rested XP is wiped at home too");
        helper.assertTrue(carried.warmth() == 0f && carried.tales() == 0.0, "Warmth and Tales go");
        helper.assertTrue(carried.provisionedAt(now) == 0.0, "Provisioned goes");
        helper.assertTrue(carried.absorbModifier() == 0f && HearthTempHp.modifier(fresh) == 0.0,
                "no temp HP after a death");
        helper.assertTrue(PlayerEffects.hungerFactor(fresh) == 1.0, "no Hearty carried past a death");
        cast.done();
    }

    // ---- 8: temp HP never stacks, lasts until spent ------------------------------------------------

    @GameTest(template = T, batch = "hearth_fx_temphp_stack")
    public static void tempHpNeverStacksAndLastsUntilSpent(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        PlayerHearth data = HearthAttachments.of(player);
        cast.primeFull();
        player.setHealth(player.getMaxHealth());
        data.setWarmth(1.0);
        // Charger's first blood: Absorption I, which raises the cap by 4 and fills 4.
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 0));
        player.setAbsorptionAmount(4f);
        helper.assertTrue(player.getAbsorptionAmount() == 4f, "setup: Charger's 4 points");
        cast.seconds(player, 40, true);
        helper.assertTrue(player.getAbsorptionAmount() == 12f,
                "max(Charger's 4, the Hearth's 12) is 12, not 16: " + player.getAbsorptionAmount());
        helper.assertTrue(HearthTempHp.modifier(player) == 12.0, "the modifier is the Hearth's cap");
        HomeRules.Hud hud = PlayerEffects.lastHud(player);
        helper.assertTrue(hud != null && (hud.flags() & HearthHudPayload.FLAG_TEMP_HP) != 0, "the HUD flags temp HP");

        // A relog keeps it: the absorption and the permanent modifier are both in the player's save.
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getFloat("AbsorptionAmount") == 12f, "absorption saved");
        boolean modifierSaved = false;
        ListTag attributes = saved.getList("attributes", Tag.TAG_COMPOUND);
        for (int i = 0; i < attributes.size(); i++) {
            if (attributes.getCompound(i).toString().contains(HearthTempHp.MODIFIER_ID.toString())) {
                modifierSaved = true;
            }
        }
        helper.assertTrue(modifierSaved, "the hearth_absorption modifier is saved with the player");

        // An hour away keeps it (no timer, leaving does not take it).
        cast.away(player, 52);
        cast.seconds(player, 120, false);
        helper.assertTrue(player.getAbsorptionAmount() == 12f && HearthTempHp.modifier(player) == 12.0,
                "temp HP lasts away from home");
        // Partly spent: the modifier never shrinks while any is left.
        player.setAbsorptionAmount(5f);
        cast.seconds(player, 3, false);
        helper.assertTrue(HearthTempHp.modifier(player) == 12.0, "the modifier stays while absorption lasts");
        // Spent: the loop removes it.
        player.setAbsorptionAmount(0f);
        cast.seconds(player, 1, false);
        helper.assertTrue(HearthTempHp.modifier(player) == 0.0 && data.absorbModifier() == 0f,
                "spent temp HP takes the modifier with it");

        // A bigger absorption from elsewhere is left alone.
        player.removeEffect(MobEffects.ABSORPTION);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, 3));
        player.setAbsorptionAmount(16f);
        cast.primeFull();
        data.setWarmth(1.0);
        cast.seconds(player, 12, true);
        helper.assertTrue(player.getAbsorptionAmount() == 16f && HearthTempHp.modifier(player) == 0.0,
                "a bigger absorption gets nothing on top: " + player.getAbsorptionAmount());
        player.removeEffect(MobEffects.ABSORPTION);
        cast.done();
    }

    // ---- 9: temp HP follows the Comfort-capped tier and max health -------------------------------

    @GameTest(template = T, batch = "hearth_fx_temphp_tier")
    public static void tempHpFollowsTheComfortCappedTierAndMaxHealth(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        PlayerHearth data = HearthAttachments.of(player);
        // Tier VI charge everywhere, but Comfort 7: regen's half cap is III, so 30% not 60%.
        cast.prime(6, 50, 7, 6, 6, 6, 6, 6);
        player.setHealth(player.getMaxHealth());
        data.setWarmth(1.0);
        for (int i = 0; i < 60; i++) {
            cast.prime(6, 50, 7, 6, 6, 6, 6, 6);
            cast.seconds(player, 1, true);
        }
        helper.assertTrue(player.getAbsorptionAmount() == 6f, "Comfort 7 gives 30% of 20 = 6, got "
                + player.getAbsorptionAmount());
        player.setAbsorptionAmount(0f);
        cast.seconds(player, 1, false);

        // All twelve kinds and 40 max health: 60% of 40 = 24.
        var maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
        var boost = Proficiency.id("test_hearth_health");
        maxHealth.addOrUpdateTransientModifier(new AttributeModifier(boost, 20.0, AttributeModifier.Operation.ADD_VALUE));
        player.setHealth(40f);
        data.setWarmth(1.0);
        for (int i = 0; i < 60; i++) {
            cast.primeFull();
            cast.seconds(player, 1, true);
        }
        helper.assertTrue(player.getAbsorptionAmount() == 24f, "60% of 40 max health = 24, got "
                + player.getAbsorptionAmount());
        // Back to 20 max health: what is left stays, the modifier does not shrink.
        maxHealth.removeModifier(boost);
        player.setHealth(20f);
        for (int i = 0; i < 8; i++) {
            cast.primeFull();
            cast.seconds(player, 1, true);
        }
        helper.assertTrue(player.getAbsorptionAmount() == 24f && HearthTempHp.modifier(player) == 24.0,
                "dropping to 20 max health keeps the 24 left, got " + player.getAbsorptionAmount());
        player.setAbsorptionAmount(0f);
        cast.seconds(player, 1, true);
        cast.done();
    }

    // ---- 10: the streak does not grow at home ----------------------------------------------------

    @GameTest(template = T, batch = "hearth_fx_streak")
    public static void theStreakDoesNotGrowAtHomeAndResumesOutside(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        // Any Hearth holds it, even a bare stage I with no charge at all.
        cast.prime(1, 0, 0);
        PlayerSkills skills = ProficiencyAttachments.of(player);
        long step = ProficiencyConfig.streakStepTicks();
        skills.setStreakTicks(step * 5 + 100);
        SkillService.grant(player, Skill.MINING, 5.0, "block.minecraft.stone");
        long before = skills.streakTicks();
        double multiplier = SurvivalStreak.multiplier(skills);

        cast.home(player);
        helper.assertTrue(HearthStreak.holds(player), "in range of a Hearth the streak is held");
        SurvivalStreak.tick(player, 12_000);
        helper.assertTrue(skills.streakTicks() == before, "ten minutes at home grew the streak: "
                + before + " -> " + skills.streakTicks());
        helper.assertTrue(SurvivalStreak.multiplier(skills) == multiplier && multiplier > 1.0,
                "the stacks still pay at home: x" + SurvivalStreak.multiplier(skills));
        PlayerEffects.second(player);
        HomeRules.Hud hud = PlayerEffects.lastHud(player);
        helper.assertTrue(hud != null && (hud.flags() & HearthHudPayload.FLAG_STREAK_SAFE) != 0,
                "the HUD says the stacks are safe");

        // Outside it resumes from the same value.
        cast.away(player, 53);
        helper.assertFalse(HearthStreak.holds(player), "away the streak is not held");
        SurvivalStreak.tick(player, 1_200);
        helper.assertTrue(skills.streakTicks() == before + 1_200, "outside it grows again from the same value: "
                + skills.streakTicks());

        // Breaking the Hearth while standing by it ends the hold at once (the flag outlives it by 2 s).
        cast.home(player);
        helper.assertTrue(HearthStreak.holds(player), "back home, held again");
        helper.setBlock(HEARTH, Blocks.AIR);
        helper.assertFalse(HearthStreak.holds(player), "a broken Hearth holds nothing");
        long now = skills.streakTicks();
        SurvivalStreak.tick(player, 20);
        helper.assertTrue(skills.streakTicks() == now + 20, "the streak grows again by the broken Hearth");
        cast.done();
    }

    // ---- 11: Warmth ------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_fx_warmth")
    public static void warmthRisesDrainsAndASleepFillsIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        PlayerHearth data = HearthAttachments.of(player);
        ServerLevel level = cast.level;
        // Homecoming at 0: the plain 60 s rise. Regen at VI for the temp HP check.
        cast.prime(6, 50, 12, 6, 6, 6, 6, 0);
        player.setHealth(player.getMaxHealth());

        data.setWarmth(0.0);
        cast.seconds(player, 30, true);
        helper.assertTrue(near(data.warmth(), 0.5), "half at 30 s, got " + data.warmth());
        // Temp HP waits for full Warmth.
        helper.assertTrue(player.getAbsorptionAmount() == 0f, "no temp HP below 100% Warmth: "
                + player.getAbsorptionAmount());
        cast.seconds(player, 29, true);
        helper.assertTrue(player.getAbsorptionAmount() == 0f, "still none at 59 s");
        cast.seconds(player, 1, true);
        helper.assertTrue(data.warmth() == 1f, "full at 60 s, got " + data.warmth());
        cast.seconds(player, 8, true);
        helper.assertTrue(player.getAbsorptionAmount() > 0f, "full Warmth fills temp HP");

        // Ten seconds outside cost twenty back.
        cast.away(player, 54);
        cast.seconds(player, 10, false);
        helper.assertTrue(near(data.warmth(), 1.0 - 10.0 / 30.0), "drains at twice the rate: " + data.warmth());
        cast.seconds(player, 19, true);
        helper.assertTrue(data.warmth() < 1f, "19 s is not enough back");
        cast.seconds(player, 1, true);
        helper.assertTrue(data.warmth() == 1f, "20 s back is: " + data.warmth());

        // No rise within 5 s of damage, and nothing lost.
        data.setWarmth(0.5);
        data.setLastDamage(level.getGameTime());
        cast.seconds(player, 3, true);
        helper.assertTrue(near(data.warmth(), 0.5), "no rise in combat: " + data.warmth());
        data.setLastDamage(PlayerHearth.NEVER);

        // Homebuilder's Warm Welcome III: 30% faster on top of readiness (60 s / 1.3).
        var welcome = dev.amman.proficiency.perk.Talents.get(Skill.HOMEBUILDER, "warm_welcome");
        ProficiencyAttachments.of(player).setRank(welcome, 3);
        try {
            data.setWarmth(0.0);
            cast.seconds(player, 30, true);
            helper.assertTrue(near(data.warmth(), 0.65), "Warm Welcome III fills 30% faster: " + data.warmth());
        } finally {
            ProficiencyAttachments.of(player).setRank(welcome, 0);
        }

        long dayTime = level.getDayTime();
        level.setRainLevel(0f);
        level.setThunderLevel(0f);
        BlockPos bed = helper.absolutePos(new BlockPos(3, 2, 3));
        try {
            // A day nap a mod allowed: the sleep timer reached 100, but it is day and nobody saw a night.
            level.setDayTime(6_000);
            level.updateSkyBrightness();
            helper.assertTrue(level.isDay(), "setup: day");
            HearthAttachments.of(player).setWarmth(0.0);
            cast.home(player);
            sleepAndWake(player, bed, 100, false);
            helper.assertTrue(warmth(player) == 0f, "a daytime bed does nothing: " + warmth(player));

            // A real night's sleep in range fills it at once (NeoForge's wake-up event; Fabric's mixin).
            level.setDayTime(18_000);
            level.updateSkyBrightness();
            helper.assertFalse(level.isDay(), "setup: night");
            HearthAttachments.of(player).setWarmth(0.0);
            cast.home(player);
            sleepAndWake(player, bed, 100, false);
            helper.assertTrue(warmth(player) == 1f, "a night's sleep fills Warmth: " + warmth(player));

            // Clicking a bed and getting up at once is no sleep, at night too.
            HearthAttachments.of(player).setWarmth(0.0);
            cast.home(player);
            sleepAndWake(player, bed, 0, true);
            helper.assertTrue(warmth(player) == 0f, "getting up at once does nothing: " + warmth(player));
        } finally {
            level.setDayTime(dayTime);
            level.updateSkyBrightness();
        }
        cast.done();
    }

    /**
     * In bed with the sleep timer at {@code timer} (100 = vanilla counts the player asleep), then up.
     * The timer is only reachable through the save, so the player is reloaded: read its attachments
     * again afterwards ({@code HearthAttachments.of}), the old objects are replaced.
     */
    private static void sleepAndWake(ServerPlayer player, BlockPos bed, int timer, boolean atOnce) {
        CompoundTag tag = player.saveWithoutId(new CompoundTag());
        tag.putShort("SleepTimer", (short) timer);
        player.load(tag);
        player.setSleepingPos(bed);
        player.stopSleepInBed(atOnce, true);
        player.clearSleepingPos();
    }

    private static float warmth(ServerPlayer player) {
        return HearthAttachments.of(player).warmth();
    }

    // ---- Hearty, Provisioned and "everything ends" -------------------------------------------------

    @GameTest(template = T, batch = "hearth_fx_hearty")
    public static void heartyScalesHungerAndSettingOutCarriesIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        PlayerHearth data = HearthAttachments.of(player);
        cast.primeFull();
        data.setWarmth(1.0);
        cast.seconds(player, 1, true);
        helper.assertTrue(near(PlayerEffects.hungerFactor(player), 0.6), "Hearty VI at full Warmth: hunger x0.6, got "
                + PlayerEffects.hungerFactor(player));
        var food = player.getFoodData();
        food.setExhaustion(0f);
        ExhaustionWatch.look(player, food);
        food.addExhaustion(1.0f);
        ExhaustionWatch.look(player, food);
        helper.assertTrue(near(food.getExhaustionLevel(), 0.6), "of 1.0 exhaustion 0.6 stays, got "
                + food.getExhaustionLevel());
        // The food tick: a climb that would pass 4.0 is scaled at its head, before vanilla takes a point
        // (a look after the tick alone would see the roll-over, a drop, and keep the whole climb).
        player.setHealth(player.getMaxHealth());
        food.setFoodLevel(20);
        food.setSaturation(5f);
        food.setExhaustion(0f);
        ExhaustionWatch.look(player, food);
        food.addExhaustion(5.0f);
        food.tick(player);
        helper.assertTrue(food.getSaturationLevel() == 5f && food.getFoodLevel() == 20,
                "5.0 x 0.6 = 3.0 stays under 4.0: no point taken, saturation " + food.getSaturationLevel()
                        + ", food " + food.getFoodLevel());
        helper.assertTrue(near(food.getExhaustionLevel(), 3.0), "3.0 kept, got " + food.getExhaustionLevel());
        food.setExhaustion(0f);
        ExhaustionWatch.look(player, food);

        // Leaving at full Warmth: Set out, and Provisioned carries Hearty for Homecoming VI's 30 min.
        long now = cast.level.getGameTime();
        cast.away(player, 55);
        cast.seconds(player, 1, false);
        helper.assertTrue(near(data.provisionedStrength(), 0.40), "Provisioned carries Hearty VI: "
                + data.provisionedStrength());
        helper.assertTrue(data.provisionedUntil() == now + 30 * 1200L, "for 30 minutes: "
                + (data.provisionedUntil() - now));
        helper.assertTrue(near(PlayerEffects.hungerFactor(player), 0.6), "it works away from home");
        helper.assertTrue(near(data.provisionedAt(now + 15 * 1200L), 0.20), "and fades linearly");

        // Leaving below full Warmth is no set-out.
        data.provision(0.0, PlayerHearth.NEVER, PlayerHearth.NEVER);
        data.setWarmth(0.5);
        cast.seconds(player, 1, true);
        cast.away(player, 55);
        cast.seconds(player, 1, false);
        helper.assertTrue(data.provisionedStrength() == 0f, "below full Warmth nothing is carried");

        // A smouldering Hearth: Warmth 0, no boons, within a second.
        data.setWarmth(1.0);
        cast.primeFull();
        cast.seconds(player, 1, true);
        cast.hearth.state().setSmouldering(true);
        cast.seconds(player, 1, true);
        helper.assertTrue(data.warmth() == 0f, "a smouldering Hearth takes Warmth: " + data.warmth());
        helper.assertTrue(PlayerEffects.hungerFactor(player) == 1.0, "no Hearty at a smouldering Hearth");
        helper.assertTrue(data.provisionedStrength() == 0f, "the Hearth went out under you: no set-out");
        HomeRules.Hud hud = PlayerEffects.lastHud(player);
        helper.assertTrue(hud != null && !hud.home() && (hud.flags() & HearthHudPayload.FLAG_SMOULDERING) != 0,
                "the HUD says it smoulders");

        // Switched off: everything ends at the next second, Provisioned included.
        cast.primeFull();
        data.setWarmth(1.0);
        cast.seconds(player, 1, true);
        cast.away(player, 55);
        cast.seconds(player, 1, false);
        helper.assertTrue(data.provisionedStrength() > 0f, "setup: Provisioned");
        cast.home(player);
        if (!ProficiencyConfig.SPEC.isLoaded()) {
            throw new IllegalStateException("the server config should be loaded in a GameTest server");
        }
        ProficiencyConfig.HEARTH_ENABLED.set(false);
        try {
            cast.seconds(player, 1, true);
            helper.assertTrue(PlayerEffects.hungerFactor(player) == 1.0 && data.provisionedStrength() == 0f,
                    "hearth.enabled = false ends Hearty and Provisioned");
            helper.assertFalse(HearthStreak.holds(player), "and the streak hold");
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
            float hit = incoming(player, cast.level.damageSources().mobAttack(zombie), 10f);
            zombie.discard();
            helper.assertTrue(near(hit, 10f), "and Fortified: a zombie's 10 stays 10, was " + hit);
        } finally {
            ProficiencyConfig.HEARTH_ENABLED.set(true);
        }

        // A broken Hearth: everything ends at the next second, and no set-out.
        cast.primeFull();
        data.setWarmth(1.0);
        cast.seconds(player, 2, true);
        helper.assertTrue(PlayerEffects.hungerFactor(player) < 1.0, "setup: Hearty back");
        cast.home(player);
        helper.setBlock(HEARTH, Blocks.AIR);
        PlayerEffects.second(player);
        helper.assertTrue(PlayerEffects.hungerFactor(player) == 1.0, "a broken Hearth ends Hearty");
        helper.assertTrue(data.provisionedStrength() == 0f, "and is no set-out");
        cast.done();
    }

    // ---- Edges: the server decides them, the HUD payload carries them ---------------------------------

    /** The edge flag the last payload carried since the last look (0: none). */
    private static int edge(ServerPlayer player) {
        return PlayerEffects.takeLastEdge(player);
    }

    @GameTest(template = T, batch = "hearth_fx_edges")
    public static void theServerDecidesEveryEdgeAndTheHudCarriesIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        PlayerHearth data = HearthAttachments.of(player);
        cast.primeFull();
        data.setWarmth(0.0);
        cast.away(player, 58);
        cast.seconds(player, 1, false);
        edge(player);

        cast.seconds(player, 1, true);
        helper.assertTrue(edge(player) == HearthHudPayload.FLAG_EDGE_HOME, "walking in: By the Hearth");
        cast.seconds(player, 1, true);
        helper.assertTrue(edge(player) == 0, "staying: no edge");

        // Below full Warmth: Left.
        data.setWarmth(0.5);
        cast.away(player, 58);
        cast.seconds(player, 1, false);
        helper.assertTrue(edge(player) == HearthHudPayload.FLAG_EDGE_LEFT, "leaving below full: Left");

        // At full Warmth with Hearty: Set out, and Provisioned.
        data.setWarmth(1.0);
        cast.seconds(player, 1, true);
        edge(player);
        cast.away(player, 58);
        cast.seconds(player, 1, false);
        helper.assertTrue(edge(player) == HearthHudPayload.FLAG_EDGE_SET_OUT && data.provisionedStrength() > 0f,
                "leaving at full with Hearty: Set out, Provisioned " + data.provisionedStrength());

        // At full Warmth with no Hearty tier: still Set out (the spec's cue), nothing to carry.
        data.provision(0.0, PlayerHearth.NEVER, PlayerHearth.NEVER);
        cast.prime(6, 50, 12, 0, 6, 6, 6, 6);
        data.setWarmth(1.0);
        cast.seconds(player, 1, true);
        edge(player);
        cast.away(player, 58);
        cast.seconds(player, 1, false);
        helper.assertTrue(edge(player) == HearthHudPayload.FLAG_EDGE_SET_OUT, "no Hearty, full Warmth: still Set out");
        helper.assertTrue(data.provisionedStrength() == 0f, "and nothing is carried");

        // A short visit below full with a Provisioned from an earlier trip: Left, not Set out.
        cast.primeFull();
        data.provision(0.4, cast.level.getGameTime(), cast.level.getGameTime() + 12_000);
        data.setWarmth(0.0);
        cast.seconds(player, 2, true);
        edge(player);
        cast.away(player, 58);
        cast.seconds(player, 1, false);
        helper.assertTrue(edge(player) == HearthHudPayload.FLAG_EDGE_LEFT, "a leftover Provisioned is no set-out");
        data.provision(0.0, PlayerHearth.NEVER, PlayerHearth.NEVER);

        // The Hearth smoulders, is switched off or breaks under the player: no edge at all.
        data.setWarmth(1.0);
        cast.seconds(player, 1, true);
        edge(player);
        cast.hearth.state().setSmouldering(true);
        cast.seconds(player, 1, true);
        HomeRules.Hud hud = PlayerEffects.lastHud(player);
        helper.assertTrue(hud != null && !hud.home() && edge(player) == 0, "smouldering under you: no edge");
        cast.hearth.state().setSmouldering(false);
        cast.seconds(player, 1, true);
        edge(player);
        ProficiencyConfig.HEARTH_ENABLED.set(false);
        try {
            cast.seconds(player, 1, true);
            helper.assertTrue(edge(player) == 0, "switched off under you: no edge");
        } finally {
            ProficiencyConfig.HEARTH_ENABLED.set(true);
        }
        cast.primeFull();
        data.setWarmth(1.0);
        cast.seconds(player, 1, true);
        edge(player);
        helper.setBlock(HEARTH, Blocks.AIR);
        PlayerEffects.second(player);
        helper.assertTrue(edge(player) == 0 && data.provisionedStrength() == 0f, "broken under you: no edge");

        // A login at home: no payload while the login holds, then home with no edge (no "By the Hearth").
        Cast again = new Cast(helper);
        again.primeFull();
        ServerPlayer back = again.player();
        PlayerHearth backData = HearthAttachments.of(back);
        backData.setWasHome(true);
        backData.setWarmth(0.8);
        PlayerEffects.loggedIn(back);
        backData.clearInRange();
        PlayerEffects.second(back);
        helper.assertTrue(PlayerEffects.lastHud(back) == null, "nothing is sent while the login holds");
        helper.assertTrue(near(backData.warmth(), 0.8), "and Warmth holds: " + backData.warmth());
        again.seconds(back, 1, true);
        HomeRules.Hud first = PlayerEffects.lastHud(back);
        helper.assertTrue(first != null && first.home() && edge(back) == 0, "then home, with no edge");
        cast.players.addAll(again.players);
        cast.done();
    }

    // ---- 12: Workshop ------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_fx_refund")
    public static void workshopNeverRefundsAReversibleRecipe(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        cast.primeFull();
        cast.home(player);
        HearthWorkshop.setTestRoll(0.0);
        helper.assertTrue(HearthWorkshop.reversible().isReversible("minecraft:iron_block"),
                "the reversible set should hold iron ingot -> block");
        helper.assertTrue(HearthWorkshop.reversible().isReversible("minecraft:iron_ingot_from_iron_block"),
                "and block -> ingots");
        helper.assertFalse(HearthWorkshop.reversible().isReversible("minecraft:chest"), "a chest is not reversible");
        helper.assertTrue(near(HearthWorkshop.refundChance(cast.hearth.state(), Hearths.data()), 0.08),
                "Workshop VI refunds 8%");

        // Nine ingots into a block: no refund.
        int ingots = craft(cast, player, Items.IRON_INGOT, 9, Items.IRON_BLOCK);
        helper.assertTrue(ingots == 0, "iron ingot -> block refunded " + ingots + " ingots");
        // The block back into ingots: no block comes back.
        player.getInventory().clearContent();
        int blocks = craft(cast, player, Items.IRON_BLOCK, 1, Items.IRON_INGOT);
        helper.assertTrue(blocks == 0, "iron block -> ingots refunded " + blocks + " blocks");
        // A chest: every plank comes back on a sure roll.
        player.getInventory().clearContent();
        int planks = craft(cast, player, Items.OAK_PLANKS, 8, Items.CHEST);
        helper.assertTrue(planks == 8, "a chest at home with a sure roll refunds its 8 planks, got " + planks);
        // Away from home: nothing.
        player.getInventory().clearContent();
        cast.away(player, 56);
        int awayPlanks = craft(cast, player, Items.OAK_PLANKS, 8, Items.CHEST);
        helper.assertTrue(awayPlanks == 0, "no refund away from home, got " + awayPlanks);
        player.closeContainer();

        // A pack may give the refund to another pool: the refund, and its telemetry row, carry that
        // pool's tier (Hearty II here), not Workshop's (VI).
        HearthData before = Hearths.data();
        Hearths.install(HearthDataLoader.parse("test",
                "{\"pools\":{\"hearty\":{\"refund\":[0.5,0.5,0.5,0.5,0.5,0.5]}}}").data());
        try {
            cast.prime(6, 50, 12, 2, 0, 0, 6, 0);
            HearthWorkshop.Refund refund = HearthWorkshop.refund(cast.hearth.state(), Hearths.data());
            helper.assertTrue(near(refund.chance(), 0.5) && refund.tier() == 2,
                    "the best refund is Hearty II's 50%: " + refund);
        } finally {
            Hearths.install(before);
        }
        cast.done();
    }

    /**
     * Crafts once at a crafting table through the real result slot: fills the grid with {@code n} of
     * {@code input} (a ring for 8, all nine for 9, one in the middle for 1), takes the result, and
     * returns how many {@code input} the player holds afterwards (the refund).
     */
    private static int craft(Cast cast, ServerPlayer player, Item input, int n, Item expected) {
        CraftingMenu menu = new CraftingMenu(77, player.getInventory(),
                ContainerLevelAccess.create(cast.level, cast.helper.absolutePos(new BlockPos(3, 2, 1))));
        player.containerMenu = menu;
        int[] slots = n == 1 ? new int[] {4} : n == 8 ? new int[] {0, 1, 2, 3, 5, 6, 7, 8}
                : new int[] {0, 1, 2, 3, 4, 5, 6, 7, 8};
        for (int slot : slots) {
            menu.getSlot(1 + slot).set(new ItemStack(input));
        }
        Slot result = menu.getSlot(0);
        ItemStack out = result.getItem();
        if (!out.is(expected)) {
            throw new IllegalStateException("the grid should make " + expected + ", made " + out);
        }
        int before = count(player, input);
        result.safeTake(out.getCount(), out.getCount(), player);
        int after = count(player, input);
        player.containerMenu = player.inventoryMenu;
        return after - before;
    }

    @GameTest(template = T, batch = "hearth_fx_stations", timeoutTicks = 200)
    public static void stationsRunFasterOnlyWhileSomeoneIsHome(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Cast cast = new Cast(helper);
        ServerPlayer player = cast.player();
        ServerLevel level = cast.level;
        // The Workshop's speed comes from the Hearth only while someone is in its range.
        cast.primeFull();
        cast.away(player, 57);
        helper.assertTrue(HearthWorkshop.stationSpeed(level, cast.hearth) == 0.0, "nobody home: normal speed");
        cast.home(player);
        helper.assertTrue(near(HearthWorkshop.stationSpeed(level, cast.hearth), 0.40), "someone home: 40% faster");
        cast.hearth.state().setSmouldering(true);
        helper.assertTrue(HearthWorkshop.stationSpeed(level, cast.hearth) == 0.0, "a smouldering Hearth: normal");
        cast.hearth.state().setSmouldering(false);
        cast.away(player, 57);

        BlockPos furnaceRel = new BlockPos(3, 2, 3);
        BlockPos standRel = new BlockPos(3, 2, 1);
        // Air first: a block left at the same spot by an earlier batch would keep its old block entity.
        helper.setBlock(furnaceRel, Blocks.AIR);
        helper.setBlock(standRel, Blocks.AIR);
        helper.setBlock(furnaceRel, Blocks.FURNACE);
        helper.setBlock(standRel, Blocks.BREWING_STAND);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) level.getBlockEntity(helper.absolutePos(furnaceRel));
        BrewingStandBlockEntity stand = (BrewingStandBlockEntity) level.getBlockEntity(helper.absolutePos(standRel));
        furnace.setItem(0, new ItemStack(Items.RAW_IRON, 16));
        furnace.setItem(1, new ItemStack(Items.COAL, 16));
        stand.setItem(0, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        stand.setItem(3, new ItemStack(Items.NETHER_WART));
        stand.setItem(4, new ItemStack(Items.BLAZE_POWDER));
        HearthStations.Furnace cook = (HearthStations.Furnace) furnace;
        HearthStations.Brewer brew = (HearthStations.Brewer) stand;

        // Measured against the game clock, not against runAfterDelay: a late test tick would run two
        // scheduled callbacks back to back. Each phase lasts at least 40 game ticks.
        long[] since = {-1L};
        int[] mark = new int[2];
        boolean[] fast = {false};
        BlockPos furnacePos = helper.absolutePos(furnaceRel);
        helper.onEachTick(() -> {
            if (cast.players.isEmpty()) {
                return;
            }
            long now = level.getGameTime();
            if (fast[0]) {
                // Someone home: the Hearth's speed, published every tick by hand, so the other lanes'
                // Hearth ticks cannot change the primed state under the measurement.
                cast.primeFull();
                cast.home(player);
                HearthStations.publishForTest(level, cast.hearth.getBlockPos(),
                        HearthWorkshop.stationSpeed(level, cast.hearth), 40);
            }
            if (since[0] < 0) {
                since[0] = now;
                mark[0] = cook.proficiency$cookingProgress();
                // A stand that has not started yet sets 400 on its first tick and counts down after.
                mark[1] = brew.proficiency$brewTime() == 0 ? 401 : brew.proficiency$brewTime();
                return;
            }
            long elapsed = now - since[0];
            if (elapsed < 40) {
                return;
            }
            int cooked = cook.proficiency$cookingProgress() - mark[0];
            int brewed = mark[1] - brew.proficiency$brewTime();
            String state = " in " + elapsed + " ticks (progress " + cook.proficiency$cookingProgress() + "/"
                    + cook.proficiency$cookingTotalTime() + ", lit " + cook.proficiency$litTime() + ", brew "
                    + brew.proficiency$brewTime() + ", speed " + HearthStations.speed(level, furnacePos) + ")";
            if (!fast[0]) {
                // Nobody home (the real Hearth publishes nothing): vanilla speed.
                helper.assertTrue(cooked == elapsed, "nobody home: a furnace cooks one step a tick, cooked " + cooked + state);
                helper.assertTrue(brewed == elapsed, "nobody home: a stand brews one step a tick, brewed " + brewed + state);
                fast[0] = true;
                cast.primeFull();
                cast.home(player);
                HearthStations.publishForTest(level, cast.hearth.getBlockPos(),
                        HearthWorkshop.stationSpeed(level, cast.hearth), 40);
                since[0] = now;
                mark[0] = cook.proficiency$cookingProgress();
                mark[1] = brew.proficiency$brewTime();
                return;
            }
            double expected = elapsed * 1.4;
            helper.assertTrue(Math.abs(cooked - expected) <= 2.0, "40% faster: about " + expected + " steps, cooked "
                    + cooked + state);
            helper.assertTrue(Math.abs(brewed - expected) <= 2.0, "40% faster brewing: about " + expected
                    + ", brewed " + brewed + state);
            helper.assertTrue(cook.proficiency$cookingProgress() < cook.proficiency$cookingTotalTime(),
                    "never past the total");
            BlockPos outside = cast.hearth.getBlockPos().offset(30, 0, 0);
            helper.assertTrue(HearthStations.speed(level, outside) == 0.0, "a station outside the range is never faster");
            // A publication that ran out (a Hearth whose chunk unloaded never publishes its 0) is
            // dropped by the once-a-second prune, even when it is the only one.
            int published = HearthStations.published();
            HearthStations.publishForTest(level, cast.hearth.getBlockPos(), 0.4, -1);
            HearthStations.prune(level.getServer());
            helper.assertTrue(published > 0 && HearthStations.published() <= published - 1,
                    "the run-out publication is pruned: " + published + " -> " + HearthStations.published());
            helper.setBlock(furnaceRel, Blocks.AIR);
            helper.setBlock(standRel, Blocks.AIR);
            cast.done();
            cast.players.clear();
        });
    }
}
