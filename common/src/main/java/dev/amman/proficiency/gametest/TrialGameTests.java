package dev.amman.proficiency.gametest;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.event.ChargerEvents;
import dev.amman.proficiency.event.CourageEvents;
import dev.amman.proficiency.event.TrialEvents;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import dev.amman.proficiency.skill.XpFactors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Trial Chambers (1.21 only): the trial factor on a trial mob's Courage and Charger XP, the
 * spawner's own mob tag (through a real trial spawner block), the wave bonus at the real state change,
 * and the vault bonus, with each guard. The arithmetic is in TrialRulesTest.
 */
public final class TrialGameTests {

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public TrialGameTests() {
    }

    private static ServerPlayer fighter(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(helper.absoluteVec(new Vec3(0.5, 2, 0.5)));
        CourageEvents.forget(player.getUUID());
        return player;
    }

    private static Mob foe(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at, ServerPlayer target) {
        Mob mob = helper.spawnWithNoFreeWill(type, at);
        if (target != null) {
            mob.setTarget(target);
        }
        return mob;
    }

    /** The factors of the newest gain in this skill, or "" when there is none. */
    private static String newestFactors(ServerPlayer player, Skill skill) {
        var log = SkillService.xpLog(player);
        if (log == null || log.entries(skill.ordinal()).isEmpty()) {
            return "";
        }
        return log.entries(skill.ordinal()).get(0).factors();
    }

    private static float logged(ServerPlayer player, Skill skill, String source) {
        var log = SkillService.xpLog(player);
        float sum = 0f;
        if (log != null) {
            for (var entry : log.entries(skill.ordinal())) {
                if (entry.source().equals(source)) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static float loggedBase(ServerPlayer player, Skill skill, String source) {
        var log = SkillService.xpLog(player);
        if (log != null) {
            for (var entry : log.entries(skill.ordinal())) {
                if (entry.source().equals(source)) {
                    return entry.base();
                }
            }
        }
        return 0f;
    }

    private static boolean hasTrial(String factors, double value) {
        for (XpFactors.Factor factor : XpFactors.decode(factors)) {
            if (factor.id().equals(XpFactors.TRIAL) && Math.abs(factor.value() - value) < 0.001) {
                return true;
            }
        }
        return false;
    }

    /** Courage: a trial mob's hit carries the trial factor inside the odds rule; a plain one does not. */
    @GameTest(template = "proficiency:empty")
    public static void aTrialMobsCouragePaysTheTrialFactor(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob plain = foe(helper, EntityType.SILVERFISH, new BlockPos(0, 2, 3), player);
        Mob trial = foe(helper, EntityType.SILVERFISH, new BlockPos(2, 2, 3), player);
        Mob ominous = foe(helper, EntityType.SILVERFISH, new BlockPos(1, 2, 4), player);
        long pos = helper.absolutePos(new BlockPos(5, 2, 5)).asLong();
        SpawnOrigin.tagTrial(trial, false, false, pos);
        SpawnOrigin.tagTrial(ominous, true, false, pos);
        CourageEvents.noteHurtBy(player, plain, trial, ominous);

        helper.assertTrue(SpawnOrigin.trialFactor(plain) == 1.0, "a plain mob has a trial factor");
        helper.assertTrue(CourageEvents.payHit(player, plain, 2f) > 0f, "the plain hit paid nothing at 3 to 1");
        helper.assertFalse(hasTrial(newestFactors(player, Skill.COURAGE), 1.5), "a plain mob showed a trial factor");
        helper.assertTrue(CourageEvents.payHit(player, trial, 2f) > 0f, "the trial hit paid nothing");
        helper.assertTrue(hasTrial(newestFactors(player, Skill.COURAGE), ProficiencyConfig.trialMobFactor()),
                "a trial mob's Courage hit has no trial factor: " + newestFactors(player, Skill.COURAGE));
        helper.assertTrue(CourageEvents.payHit(player, ominous, 2f) > 0f, "the ominous hit paid nothing");
        helper.assertTrue(hasTrial(newestFactors(player, Skill.COURAGE), ProficiencyConfig.trialOminousMobFactor()),
                "an ominous trial mob's hit has no ominous factor: " + newestFactors(player, Skill.COURAGE));
        helper.succeed();
    }

    /** Charger: first blood on a trial mob carries the factor; the once-per-mob rule is unchanged. */
    @GameTest(template = "proficiency:empty")
    public static void aTrialMobsFirstBloodPaysTheTrialFactor(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob trial = foe(helper, EntityType.ZOMBIE, new BlockPos(2, 2, 3), player);
        SpawnOrigin.tagTrial(trial, false, false, helper.absolutePos(new BlockPos(5, 2, 5)).asLong());
        float paid = ChargerEvents.payFirstBlood(player, trial);
        helper.assertTrue(paid > 0f, "first blood on a trial mob paid nothing");
        helper.assertTrue(hasTrial(newestFactors(player, Skill.CHARGER), ProficiencyConfig.trialMobFactor()),
                "first blood has no trial factor: " + newestFactors(player, Skill.CHARGER));
        helper.assertTrue(ChargerEvents.payFirstBlood(player, trial) == 0f, "first blood paid twice on one mob");
        helper.succeed();
    }

    /** A spawner a player placed makes mobs that pay nothing and carry no trial mark. */
    @GameTest(template = "proficiency:empty")
    public static void aPlacedSpawnersMobsPayNothing(GameTestHelper helper) {
        BlockPos placedRel = new BlockPos(1, 2, 1);
        BlockPos naturalRel = new BlockPos(5, 2, 5);
        var level = helper.getLevel();
        PlacedBlocks.markPlacement(level, helper.absolutePos(placedRel), Blocks.TRIAL_SPAWNER.defaultBlockState());
        Mob fromPlaced = foe(helper, EntityType.ZOMBIE, new BlockPos(2, 2, 2), null);
        Mob fromNatural = foe(helper, EntityType.ZOMBIE, new BlockPos(3, 2, 2), null);
        TrialEvents.onSpawn(level, helper.absolutePos(placedRel), false, fromPlaced.getUUID());
        TrialEvents.onSpawn(level, helper.absolutePos(naturalRel), false, fromNatural.getUUID());
        helper.assertFalse(SpawnOrigin.isTrial(fromPlaced), "a placed spawner's mob is a trial mob");
        helper.assertTrue(SpawnOrigin.xpFactor(fromPlaced) == ProficiencyConfig.artificialMobXp(),
                "a placed spawner's mob pays the spawner share");
        helper.assertTrue(SpawnOrigin.isTrial(fromNatural), "a natural spawner's mob is not marked");
        helper.assertTrue(SpawnOrigin.xpFactor(fromNatural) == ProficiencyConfig.spawnerMobXp(),
                "a trial mob does not pay the spawner share");
        helper.succeed();
    }

    /**
     * The real trial spawner block: its own spawnMob call tags the mob (through the mixin), and the
     * wave bonus is paid at the real WAITING_FOR_REWARD_EJECTION to EJECTING_REWARD change.
     */
    @GameTest(template = "proficiency:empty", timeoutTicks = 200)
    public static void aRealSpawnerTagsItsMobsAndPaysTheWave(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = fighter(helper);
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(rel);
        // The template's floor is barrier, which no mob may spawn on. The template is 5 wide: stay inside it,
        // so the neighbouring tests' areas are never touched.
        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        helper.setBlock(rel, Blocks.TRIAL_SPAWNER);
        TrialSpawnerBlockEntity be = (TrialSpawnerBlockEntity) level.getBlockEntity(abs);
        helper.assertTrue(be != null, "no trial spawner block entity");
        CompoundTag config = new CompoundTag();
        ListTag potentials = new ListTag();
        CompoundTag entry = new CompoundTag();
        CompoundTag data = new CompoundTag();
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:zombie");
        data.put("entity", entity);
        entry.put("data", data);
        entry.putInt("weight", 1);
        potentials.add(entry);
        config.put("spawn_potentials", potentials);
        config.putInt("spawn_range", 1);
        CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
        tag.put("normal_config", config);
        be.loadWithComponents(tag, level.registryAccess());

        Mob made = null;
        for (int attempt = 0; attempt < 60 && made == null; attempt++) {
            Optional<UUID> id = be.getTrialSpawner().spawnMob(level, abs);
            if (id.isPresent() && level.getEntity(id.get()) instanceof Mob mob) {
                made = mob;
            }
        }
        helper.assertTrue(made != null, "the real trial spawner made no mob in 60 tries (difficulty " + level.getDifficulty()
                + ", potentials empty " + be.getTrialSpawner().getConfig().spawnPotentialsDefinition().isEmpty()
                + ", below " + level.getBlockState(abs.below()) + ", at " + level.getBlockState(abs)
                + ", range " + be.getTrialSpawner().getConfig().spawnRange() + ")");
        helper.assertTrue(SpawnOrigin.isTrial(made), "the mob of a real trial spawner is not tagged");
        helper.assertTrue(SpawnOrigin.trialSpawnerPos(made) == abs.asLong(), "the tag has the wrong spawner");
        helper.assertFalse(SpawnOrigin.isOminousTrial(made), "a normal trial's mob is ominous");

        // Six kills of that spawner's mobs, three of them with an axe.
        for (int i = 0; i < 6; i++) {
            TrialEvents.noteKill(made, player, i < 3 ? Skill.AXES : Skill.SWORDS);
        }
        // Put the spawner at the moment before its reward ejects, with this player tracked.
        helper.setBlock(rel, Blocks.TRIAL_SPAWNER.defaultBlockState()
                .setValue(TrialSpawnerBlock.STATE, TrialSpawnerState.WAITING_FOR_REWARD_EJECTION));
        be = (TrialSpawnerBlockEntity) level.getBlockEntity(abs);
        CompoundTag state = be.saveWithFullMetadata(level.registryAccess());
        ListTag tracked = new ListTag();
        tracked.add(new IntArrayTag(UUIDUtil.uuidToIntArray(player.getUUID())));
        state.put("registered_players", tracked);
        state.put("current_mobs", new ListTag());
        state.putLong("cooldown_ends_at", level.getGameTime() + be.getTrialSpawner().getTargetCooldownLength() - 100L);
        be.loadWithComponents(state, level.registryAccess());
        made.discard();

        final ServerPlayer who = player;
        helper.succeedWhen(() -> {
            float paid = logged(who, Skill.AXES, TrialEvents.SOURCE_WAVE)
                    + logged(who, Skill.SWORDS, TrialEvents.SOURCE_WAVE);
            helper.assertTrue(paid > 0f, "the cleared wave paid nothing");
            // Three axe kills and three sword kills tie; the lower skill ordinal wins, and only one is paid.
            helper.assertTrue((logged(who, Skill.AXES, TrialEvents.SOURCE_WAVE) > 0f)
                            != (logged(who, Skill.SWORDS, TrialEvents.SOURCE_WAVE) > 0f),
                    "the wave paid two skills");
            helper.assertTrue(Math.abs(loggedBase(who, Skill.AXES, TrialEvents.SOURCE_WAVE)
                            + loggedBase(who, Skill.SWORDS, TrialEvents.SOURCE_WAVE)
                            - (float) ProficiencyConfig.trialWaveXp()) < 0.01f,
                    "the wave did not pay the full base at six kills");
        });
    }

    /** The wave bonus goes only to tracked players who are there, fought, and for a spawner nobody placed. */
    @GameTest(template = "proficiency:empty")
    public static void theWaveBonusHasItsGuards(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer fought = fighter(helper);
        ServerPlayer idle = fighter(helper);
        ServerPlayer far = fighter(helper);
        ServerPlayer untracked = fighter(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 6));
        Mob mob = foe(helper, EntityType.ZOMBIE, new BlockPos(3, 2, 3), null);
        SpawnOrigin.tagTrial(mob, true, false, pos.asLong());
        for (ServerPlayer p : new ServerPlayer[] {fought, far, untracked}) {
            for (int i = 0; i < 6; i++) {
                TrialEvents.noteKill(mob, p, Skill.MACES);
            }
        }
        far.setPos(helper.absoluteVec(new Vec3(100.5, 2, 0.5)));
        Set<UUID> tracked = Set.of(fought.getUUID(), idle.getUUID(), far.getUUID());
        int paid = TrialEvents.onWaveCleared(level, pos, true, 14, tracked);
        helper.assertTrue(paid == 1, "the wave paid " + paid + " players, wanted only the one who fought there");
        helper.assertTrue(logged(fought, Skill.MACES, TrialEvents.SOURCE_WAVE_OMINOUS) > 0f, "the fighter was not paid");
        helper.assertTrue(Math.abs(loggedBase(fought, Skill.MACES, TrialEvents.SOURCE_WAVE_OMINOUS)
                        - ProficiencyConfig.trialWaveXp() * 2.0) < 0.01, "an ominous wave did not pay double");
        helper.assertTrue(logged(idle, Skill.MACES, TrialEvents.SOURCE_WAVE_OMINOUS) == 0f, "a bystander was paid");
        helper.assertTrue(logged(far, Skill.MACES, TrialEvents.SOURCE_WAVE_OMINOUS) == 0f, "a player out of range was paid");
        helper.assertTrue(logged(untracked, Skill.MACES, TrialEvents.SOURCE_WAVE_OMINOUS) == 0f,
                "a player the spawner never tracked was paid");
        // The record is spent: the same wave cannot be paid twice.
        helper.assertTrue(TrialEvents.onWaveCleared(level, pos, true, 14, tracked) == 0, "a wave paid twice");

        // A spawner a player placed pays no wave bonus even with kills on record.
        BlockPos placed = helper.absolutePos(new BlockPos(1, 2, 6));
        PlacedBlocks.markPlacement(level, placed, Blocks.TRIAL_SPAWNER.defaultBlockState());
        Mob other = foe(helper, EntityType.ZOMBIE, new BlockPos(4, 2, 3), null);
        SpawnOrigin.tagTrial(other, false, false, placed.asLong());
        for (int i = 0; i < 6; i++) {
            TrialEvents.noteKill(other, fought, Skill.SWORDS);
        }
        helper.assertTrue(TrialEvents.onWaveCleared(level, placed, false, 14, Set.of(fought.getUUID())) == 0,
                "a placed spawner paid a wave");
        helper.succeed();
    }

    /** A vault pays Wayfaring once per vault per player, double when ominous, nothing for a placed one. */
    @GameTest(template = "proficiency:empty")
    public static void aVaultPaysOncePerPlayer(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = fighter(helper);
        ServerPlayer other = fighter(helper);
        BlockPos vault = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockPos ominousVault = helper.absolutePos(new BlockPos(4, 2, 2));
        BlockPos placed = helper.absolutePos(new BlockPos(6, 2, 2));
        PlacedBlocks.markPlacement(level, placed, Blocks.VAULT.defaultBlockState());

        float first = TrialEvents.onVaultOpened(level, vault, false, player);
        helper.assertTrue(first > 0f, "a first vault paid nothing");
        helper.assertTrue(TrialEvents.onVaultOpened(level, vault, false, player) == 0f, "the same vault paid twice");
        helper.assertTrue(TrialEvents.onVaultOpened(level, vault, false, other) > 0f,
                "a second player was not paid for the same vault");
        helper.assertTrue(TrialEvents.onVaultOpened(level, ominousVault, true, player) > 0f, "an ominous vault paid nothing");
        helper.assertTrue(Math.abs(loggedBase(player, Skill.WAYFARING, TrialEvents.SOURCE_VAULT_OMINOUS)
                        - ProficiencyConfig.trialVaultXp() * 2.0) < 0.01, "an ominous vault did not pay double");
        helper.assertTrue(TrialEvents.onVaultOpened(level, placed, false, player) == 0f, "a placed vault paid");
        helper.assertTrue(ProficiencyAttachments.of(player).hasVisited("vault:" + level.dimension().location()
                + ":" + vault.asLong()), "the vault was not remembered");
        helper.succeed();
    }
}
