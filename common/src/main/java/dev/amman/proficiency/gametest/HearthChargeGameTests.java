package dev.amman.proficiency.gametest;

import com.mojang.authlib.GameProfile;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.ExpansionEvents;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.HoldQueue;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.charge.ChargeEvents;
import dev.amman.proficiency.hearth.charge.ChargeText;
import dev.amman.proficiency.hearth.charge.ChargeTick;
import dev.amman.proficiency.hearth.charge.ChargeTiers;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.hearth.charge.KeeperRows;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import dev.amman.proficiency.skill.TeachingService;
import dev.amman.proficiency.telemetry.TelemetryHub;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The charge lane in a real world, on both loaders (docs/HEARTH-SPEC.md, "Charge" and "Who feeds the
 * Hearth"; abuse checks 2, 3, 7, 14 and 17): what feeds which pool, what feeds nothing, the hold
 * queue, decay, the hourly cap, the ledger and its command, tier-ups and the siege API. The rules
 * themselves are unit tested in core ({@code ChargeRulesTest}).
 *
 * <p>Each test has its own batch: the {@code empty} template is tiny, and a neighbour's Hearth or
 * players would be "in range". Leftover mock players (never removed by vanilla) are moved far up
 * first, and each test removes its own at the end.
 */
public final class HearthChargeGameTests {

    private static final String T = "proficiency:empty";
    private static final BlockPos HEARTH = new BlockPos(1, 2, 1);
    private static final double EPS = 1e-4;

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HearthChargeGameTests() {
    }

    // ---- The stage ------------------------------------------------------------------------------

    /** One Hearth at relative (1, 2, 1) and the players of one test. */
    private static final class Home {
        final GameTestHelper helper;
        final ServerLevel level;
        final BlockPos pos;
        final HearthBlockEntity hearth;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<HearthHooks.Grant> grants = new ArrayList<>();
        private final List<BlockPos> placed = new ArrayList<>();
        private final HearthHooks.GrantListener listener = grants::add;

        Home(GameTestHelper helper, int stage) {
            this.helper = helper;
            this.level = helper.getLevel();
            Vec3 origin = helper.absoluteVec(Vec3.ZERO);
            for (ServerPlayer other : List.copyOf(level.players())) {
                if (other.position().distanceTo(origin) < 64) {
                    other.setPos(other.getX(), other.getY() + 400, other.getZ());
                }
            }
            helper.setBlock(HEARTH, HearthBlocks.HEARTH.get().defaultBlockState()
                    .setValue(HearthBlock.STAGE, Math.max(1, stage)).setValue(HearthBlock.SMOULDERING, false));
            this.pos = helper.absolutePos(HEARTH);
            if (!(level.getBlockEntity(pos) instanceof HearthBlockEntity entity)) {
                throw new IllegalStateException("a Hearth block should carry a Hearth block entity");
            }
            this.hearth = entity;
            hearth.state().setStage(stage);
            HearthHooks.GRANT.add(listener);
        }

        /** A survival player at relative (x, 2, z), at home. */
        ServerPlayer player(double x, double z) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(new Vec3(x, 2, z)));
            home(player);
            players.add(player);
            return player;
        }

        /** What the stages lane's tick does every second for a player in range. */
        void home(ServerPlayer player) {
            HearthAttachments.of(player).seenBy(pos.asLong(), level.dimension().location().toString(),
                    level.getGameTime());
        }

        HearthState state() {
            return hearth.state();
        }

        double charge(HearthPool pool) {
            return hearth.state().charge(pool);
        }

        double total() {
            double sum = 0;
            for (HearthPool pool : HearthPool.VALUES) {
                sum += charge(pool);
            }
            return sum;
        }

        /** The last grant this test saw with this source. */
        HearthHooks.Grant last(String source) {
            for (int i = grants.size() - 1; i >= 0; i--) {
                if (source.equals(grants.get(i).source())) {
                    return grants.get(i);
                }
            }
            throw new IllegalStateException("no grant with source " + source + " among " + grants.size());
        }

        /**
         * A spot this test placed a block on (with its placed and comfort marks). {@link #done} takes the
         * block and the marks away again: GameTest cells sit a few blocks apart, and a marked block left
         * standing counts for the Comfort and Character of the next test's Hearth in range.
         */
        void placedAt(BlockPos rel) {
            placed.add(rel);
        }

        void done() {
            HearthHooks.GRANT.remove(listener);
            for (ServerPlayer player : players) {
                player.server.getPlayerList().remove(player);
            }
            for (BlockPos rel : placed) {
                BlockPos abs = helper.absolutePos(rel);
                PlacedBlocks.clearComfort(level, abs);
                PlacedBlocks.consume(level, abs, level.getBlockState(abs));
                helper.setBlock(rel, Blocks.AIR);
            }
            helper.setBlock(HEARTH, Blocks.AIR);
            helper.succeed();
        }
    }

    private static double mirror(double beforeRested) {
        return beforeRested * Hearths.data().mirrorShare();
    }

    private static void near(GameTestHelper helper, double want, double got, String what) {
        helper.assertTrue(Math.abs(want - got) < EPS, what + ": wanted " + want + ", got " + got);
    }

    private static Mob mob(GameTestHelper helper, EntityType<? extends Mob> type, int x, int z) {
        return helper.spawnWithNoFreeWill(type, new BlockPos(x, 2, z));
    }

    private static void kill(ServerPlayer player, Mob target) {
        target.hurt(player.damageSources().playerAttack(player), 1000f);
    }

    // ---- What feeds which pool ------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_charge_mirror")
    public static void aGrantAtHomeMirrorsTenPercentIntoItsPool(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 2.5);
        try {
            SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
            double mining = home.last("block.minecraft.stone").beforeRested();
            near(helper, mirror(mining), home.charge(HearthPool.HEARTY), "Hearty from Mining");
            HearthLedger ledger = home.state().ledger();
            near(helper, home.charge(HearthPool.HEARTY),
                    ledger.total(player.getUUID(), HearthPool.HEARTY, HearthLedger.Window.LIFETIME), "the ledger cell");
            helper.assertTrue("mining".equals(ledger.sources(player.getUUID(), HearthPool.HEARTY,
                    HearthLedger.Window.LIFETIME).get(0).source()), "the ledger source is the skill");

            SkillService.grant(player, Skill.COOKING, 10.0, "item.minecraft.bread");
            near(helper, mirror(home.last("item.minecraft.bread").beforeRested()), home.charge(HearthPool.WORKSHOP),
                    "Workshop from Cooking");
            SkillService.grant(player, Skill.SOCIAL, 4.0, "proficiency.xplog.source.company");
            near(helper, mirror(home.last("proficiency.xplog.source.company").beforeRested()),
                    home.charge(HearthPool.HOMECOMING), "Homecoming from Social");

            // Movement, Expedition, Survival and Mastery feed nothing in range.
            double before = home.total();
            SkillService.grant(player, Skill.RUNNING, 5.0, "proficiency.xplog.source.sprinting");
            SkillService.grant(player, Skill.WAYFARING, 40.0, "biome.minecraft.plains");
            SkillService.grant(player, Skill.NIGHTWALKER, 5.0, "proficiency.xplog.source.dark");
            SkillService.grant(player, Skill.SPELLCASTING, 5.0, "proficiency.xplog.source.spell");
            near(helper, before, home.total(), "Movement, Expedition, Survival and Mastery");
            // Homebuilder's share feeds nothing either.
            int seenHome = home.grants.size();
            SkillService.grant(player, Skill.HOMEBUILDER, 5.0, "proficiency.xplog.source.home");
            helper.assertTrue(home.grants.size() > seenHome, "setup: the Homebuilder grant paid");
            near(helper, before, home.total(), "Homebuilder");

            // Masonry with no placement behind it holds nothing and pays nothing.
            SkillService.grant(player, Skill.MASONRY, 5.0, "proficiency.xplog.source.craft");
            near(helper, before, home.total(), "a build share with no block");
            helper.assertTrue(home.state().holds().isEmpty(), "nothing held without a placement");

            // Away from home, and at a smouldering Hearth: nothing.
            HearthAttachments.of(player).clearInRange();
            SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
            near(helper, before, home.total(), "away from home");
            home.home(player);
            home.state().setSmouldering(true);
            SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
            near(helper, before, home.total(), "a smouldering Hearth");
            home.state().setSmouldering(false);
        } finally {
            home.done();
        }
    }

    @GameTest(template = T, batch = "hearth_charge_rested")
    public static void restedFirstTimeCommandAndTeachingGrantsFeedNothing(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 2.5);
        try {
            PlayerSkills skills = ProficiencyAttachments.of(player);
            skills.setLevel(Skill.MINING, 40);
            skills.setRested(Skill.MINING, skills.restedCap(Skill.MINING));
            // A kind the player never met: the grant also pays a first-time line.
            SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.granite");
            HearthHooks.Grant grant = home.last("block.minecraft.granite");
            helper.assertTrue(grant.amount() > grant.beforeRested() + 0.01f,
                    "setup: the rested pool should have paid extra, " + grant.amount() + " vs " + grant.beforeRested());
            boolean firstPaid = home.grants.stream().anyMatch(g -> g.firstTime() && g.amount() > 0f);
            helper.assertTrue(firstPaid, "setup: a first-time line should have been paid");
            near(helper, mirror(grant.beforeRested()), home.charge(HearthPool.HEARTY),
                    "only beforeRested is mirrored: no rested extra, no first-time line");

            double before = home.total();
            SkillService.grant(player, Skill.MINING, 50.0, "proficiency.xplog.source.command");
            SkillService.grant(player, Skill.SOCIAL, 20.0, TeachingService.SOURCE);
            near(helper, before, home.total(), "an operator's grant and a teaching payout");
        } finally {
            home.done();
        }
    }

    @GameTest(template = T, batch = "hearth_charge_veteran")
    public static void aVeteranWithEveryStarStillFeeds(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 2.5);
        try {
            PlayerSkills skills = ProficiencyAttachments.of(player);
            skills.setLevel(Skill.WOODCUTTING, 100);
            skills.setStars(Skill.WOODCUTTING, Mastery.lastStar());
            SkillService.grant(player, Skill.WOODCUTTING, 10.0, "block.minecraft.oak_log");
            HearthHooks.Grant grant = home.last("block.minecraft.oak_log");
            helper.assertTrue(grant.banked() == 0f, "setup: a skill with every star banks nothing, banked " + grant.banked());
            helper.assertTrue(grant.beforeRested() > 0f, "setup: the grant paid");
            near(helper, mirror(grant.beforeRested()), home.charge(HearthPool.HEARTY), "a veteran feeds the Hearth");
            helper.assertTrue(home.charge(HearthPool.HEARTY) > 0, "Hearty grew");
        } finally {
            home.done();
        }
    }

    // ---- Abuse check 2: farms charge nothing; only siege and raid kills feed --------------------

    @GameTest(template = T, batch = "hearth_charge_combat")
    public static void farmsAndHitsChargeNothingOnlySiegeAndRaidKillsFeed(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(0.5, 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        String zombie = "kill|" + EntityType.ZOMBIE.getDescriptionId();
        try {
            // An ordinary kill, a caged-villager farm's zombie, a spawner's zombie.
            kill(player, mob(helper, EntityType.ZOMBIE, 2, 2));
            Mob baited = mob(helper, EntityType.ZOMBIE, 3, 2);
            Villager bait = (Villager) mob(helper, EntityType.VILLAGER, 4, 4);
            baited.setTarget(bait);
            kill(player, baited);
            Mob spawned = mob(helper, EntityType.ZOMBIE, 2, 3);
            SpawnOrigin.tag(spawned, MobSpawnType.SPAWNER);
            kill(player, spawned);
            helper.assertTrue(home.grants.stream().anyMatch(g -> zombie.equals(g.source())),
                    "setup: the kills should have paid kill XP");
            // A hit on a siege mob, blocking, a fall, role XP.
            Mob siege = mob(helper, EntityType.ZOMBIE, 3, 3);
            SpawnOrigin.tagSiege(siege);
            siege.hurt(player.damageSources().playerAttack(player), 1f);
            SkillService.grant(player, Skill.BLOCKING, 5.0, "entity.minecraft.zombie");
            SkillService.grant(player, Skill.ENDURANCE, 5.0, "proficiency.xplog.damage.fall");
            SkillService.grant(player, Skill.GUARDIAN, 5.0, "proficiency.xplog.source.guardian_cover");
            // A pillager with no raid is an ordinary kill.
            kill(player, mob(helper, EntityType.PILLAGER, 4, 2));
            near(helper, 0.0, home.total(), "farms, hits, blocking, falls, role XP and a patrol");

            // A siege mob feeds only the Hearth under siege it died at: not while this Hearth is at
            // peace (another Hearth's siege mob, a leftover), and not outside its range.
            Mob peace = mob(helper, EntityType.ZOMBIE, 2, 4);
            SpawnOrigin.tagSiege(peace);
            int seenPeace = home.grants.size();
            kill(player, peace);
            helper.assertTrue(home.grants.lastIndexOf(home.last(zombie)) >= seenPeace, "setup: the kill at peace paid XP");
            near(helper, 0.0, home.total(), "a siege mob killed while this Hearth is not under siege");
            home.state().setSiegePhase(SiegeRules.Phase.ACTIVE);
            Mob outside = mob(helper, EntityType.ZOMBIE, 4, 3);
            SpawnOrigin.tagSiege(outside);
            outside.setPos(outside.getX(), outside.getY(), outside.getZ() + HearthRange.range().radius() + 8);
            helper.assertFalse(HearthRange.inHouse(home.pos, outside.position()), "setup: out of range");
            int seenOutside = home.grants.size();
            kill(player, outside);
            helper.assertTrue(home.grants.lastIndexOf(home.last(zombie)) >= seenOutside, "setup: the kill outside paid XP");
            near(helper, 0.0, home.total(), "a siege mob killed outside this Hearth's range");

            // A siege mob killed in range while the siege runs: combatShare of the kill XP into every pool.
            int seen = home.grants.size();
            kill(player, siege);
            HearthHooks.Grant siegeKill = home.last(zombie);
            helper.assertTrue(home.grants.lastIndexOf(siegeKill) >= seen, "setup: the siege kill paid kill XP");
            double share = siegeKill.beforeRested() * Hearths.data().combatShare();
            for (HearthPool pool : HearthPool.VALUES) {
                near(helper, share, home.charge(pool), "siege kill into " + pool.id());
            }
            near(helper, share * HearthPool.COUNT, home.state().ledger().grandTotal(null, HearthLedger.Window.LIFETIME),
                    "the siege share in the ledger");
            helper.assertTrue(home.state().ledger().sources(player.getUUID(), null, HearthLedger.Window.LIFETIME)
                    .get(0).source().equals(HearthLedger.SIEGE), "filed under siege");
            home.state().setSiegePhase(SiegeRules.Phase.IDLE);

            // A raider of an active raid in range.
            double beforeRaid = home.charge(HearthPool.HEARTY);
            Pillager raider = (Pillager) mob(helper, EntityType.PILLAGER, 3, 4);
            Raid raid = new Raid(424_242, home.level, home.pos);
            raider.setCurrentRaid(raid);
            kill(player, raider);
            HearthHooks.Grant raidKill = home.last("kill|" + EntityType.PILLAGER.getDescriptionId());
            near(helper, beforeRaid + raidKill.beforeRested() * Hearths.data().combatShare(),
                    home.charge(HearthPool.HEARTY), "a raid kill feeds");

            // A raider of a real (registered) raid: Raider.die leaves the raid before the death event,
            // so the raid it belonged to is the one HearthRaiderMixin kept at the head of Raider.die.
            // This is the path every raid kill takes.
            Raid real = home.level.getRaids().createOrExtendRaid(player, home.pos);
            helper.assertTrue(real != null && real.isActive(), "setup: a raid at the Hearth");
            try {
                // A raider merely near the running raid (a witch farm next to a raid farm) is an
                // ordinary kill: membership counts, not the raid around it.
                Mob witch = mob(helper, EntityType.WITCH, 0, 4);
                helper.assertTrue(((Raider) witch).getCurrentRaid() == null, "setup: the witch is no raid's");
                double beforeWitch = home.total();
                int seenWitch = home.grants.size();
                kill(player, witch);
                HearthHooks.Grant witchKill = home.last("kill|" + EntityType.WITCH.getDescriptionId());
                helper.assertTrue(home.grants.lastIndexOf(witchKill) >= seenWitch, "setup: the witch's kill paid XP");
                near(helper, beforeWitch, home.total(), "a witch next to a running raid feeds nothing");

                Pillager joined = (Pillager) mob(helper, EntityType.PILLAGER, 2, 4);
                real.joinRaid(1, joined, null, false);
                helper.assertTrue(joined.getCurrentRaid() == real, "setup: the pillager joined the raid");
                double beforeJoined = home.charge(HearthPool.FORTIFIED);
                int seenJoined = home.grants.size();
                kill(player, joined);
                helper.assertTrue(joined.getCurrentRaid() == null, "setup: the raider left its raid as it died");
                helper.assertTrue(joined instanceof ChargeEvents.RaidMember member && member.proficiency$raidAtDeath() == real,
                        "HearthRaiderMixin kept the raid it died in");
                HearthHooks.Grant joinedKill = home.last("kill|" + EntityType.PILLAGER.getDescriptionId());
                helper.assertTrue(home.grants.lastIndexOf(joinedKill) >= seenJoined, "setup: the raider's kill paid XP");
                near(helper, beforeJoined + joinedKill.beforeRested() * Hearths.data().combatShare(),
                        home.charge(HearthPool.FORTIFIED), "a raider of a running raid feeds every pool");
            } finally {
                real.stop();
            }
        } finally {
            home.done();
        }
    }

    // ---- Abuse check 3: the hold queue ----------------------------------------------------------

    private static double place(Home home, ServerPlayer player, BlockPos rel, BlockState state) {
        home.helper.setBlock(rel, state);
        home.placedAt(rel);
        BlockPos abs = home.helper.absolutePos(rel);
        int seen = home.grants.size();
        ExpansionEvents.placed(player, state, abs);
        // The placement hook runs after the XP, in the same tick, as it does in the game.
        HearthHooks.onBlockPlaced(home.level, abs, state, player);
        double paid = 0;
        for (int i = seen; i < home.grants.size(); i++) {
            HearthHooks.Grant grant = home.grants.get(i);
            if (!grant.firstTime() && (grant.skill() == Skill.MASONRY || grant.skill() == Skill.DECORATING)) {
                paid += grant.beforeRested();
            }
        }
        return paid;
    }

    @GameTest(template = T, batch = "hearth_charge_hold")
    public static void aShuffledBlockChargesNothingAndAStandingOneOnce(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 0.5);
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        try {
            long now = home.level.getGameTime();
            // The shuffle: placed, broken, placed one over, broken.
            double first = place(home, player, new BlockPos(3, 2, 3), bricks);
            helper.assertTrue(first > 0, "setup: a stone brick pays Masonry");
            helper.assertTrue(home.state().holds().size() == 1, "the share waits: " + home.state().holds().size());
            near(helper, 0.0, home.charge(HearthPool.FORTIFIED), "nothing before the hold ends");
            player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 2, 3)));
            helper.assertTrue(home.state().holds().isEmpty(), "breaking drops what waits on the block");
            place(home, player, new BlockPos(3, 2, 4), bricks);
            player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 2, 4)));
            // One that stays, one swapped for another block without a break, and a slab (half each).
            double stays = place(home, player, new BlockPos(4, 2, 3), bricks);
            place(home, player, new BlockPos(4, 2, 4), bricks);
            helper.setBlock(new BlockPos(4, 2, 4), Blocks.OAK_PLANKS);
            double slab = place(home, player, new BlockPos(2, 2, 4), Blocks.STONE_BRICK_SLAB.defaultBlockState());
            helper.assertTrue(home.state().holds().size() == 4, "three blocks and a slab's two halves wait: "
                    + home.state().holds().size());

            // A restart keeps the pending credit: it is saved with the block entity.
            CompoundTag saved = home.hearth.saveWithFullMetadata(home.level.registryAccess());
            BlockEntity loaded = BlockEntity.loadStatic(home.pos, home.level.getBlockState(home.pos), saved,
                    home.level.registryAccess());
            helper.assertTrue(loaded instanceof HearthBlockEntity copy && copy.state().holds().size() == 4,
                    "the holds should load back with the Hearth");
            HoldQueue.Held held = home.state().holds().all().get(0).held();
            long due = now + Hearths.data().pool(HearthPool.FORTIFIED).holdSeconds() * 20L;
            helper.assertTrue(held.due() == due && "minecraft:stone_bricks".equals(held.block()),
                    "a hold remembers its block and its due time: " + held.block() + " " + held.due());

            // Not due yet: nothing.
            near(helper, 0.0, ChargeTick.settleHolds(home.level, home.hearth, due - 20), "before the ten minutes");
            // While the Hearth smoulders nothing settles: the holds wait for the relight.
            home.state().setSmouldering(true);
            near(helper, 0.0, ChargeTick.settleHolds(home.level, home.hearth, due + 1), "a smouldering Hearth");
            helper.assertTrue(home.state().holds().size() == 4, "the holds wait while it smoulders: "
                    + home.state().holds().size());
            near(helper, 0.0, home.total(), "nothing credited into a smouldering Hearth");
            home.state().setSmouldering(false);
            // Due: the standing brick and the slab pay, the swapped one does not.
            ChargeTick.settleHolds(home.level, home.hearth, due + 1);
            double slabMasonry = home.state().ledger().total(player.getUUID(), HearthPool.FORTIFIED,
                    HearthLedger.Window.LIFETIME) - mirror(stays);
            helper.assertTrue(slabMasonry > 0, "the slab's Masonry half paid");
            near(helper, mirror(stays + slab), home.charge(HearthPool.FORTIFIED) + home.charge(HearthPool.REGEN),
                    "the standing brick and both halves of the slab");
            helper.assertTrue(home.charge(HearthPool.REGEN) > 0, "the slab's Decorating half went to Regen");
            helper.assertTrue(home.state().holds().isEmpty(), "every due hold is settled");
            // Credited once.
            double after = home.total();
            ChargeTick.settleHolds(home.level, home.hearth, due + 400);
            near(helper, after, home.total(), "a hold pays once");
            helper.assertTrue(first > 0 && stays > 0, "setup");
        } finally {
            home.done();
        }
    }

    // ---- Abuse check 7: fake players ------------------------------------------------------------

    /** What Create's deployer looks like to us: a ServerPlayer with no connection, named ...FakePlayer. */
    private static final class MachineFakePlayer extends ServerPlayer {
        MachineFakePlayer(ServerLevel level) {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "machine"),
                    net.minecraft.server.level.ClientInformation.createDefault());
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    @GameTest(template = T, batch = "hearth_charge_fake")
    public static void fakePlayersFeedNothing(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        try {
            MachineFakePlayer fake = new MachineFakePlayer(home.level);
            fake.setPos(helper.absoluteVec(new Vec3(2.5, 2, 2.5)));
            home.home(fake);
            helper.assertTrue(SkillService.grant(fake, Skill.MINING, 10.0, "block.minecraft.stone") == 0f,
                    "a fake player is paid nothing");
            // Even if a grant ever reached the hook, the lane turns a machine away.
            HearthHooks.onGrant(fake, Skill.MINING, "block.minecraft.stone", 10.0, 10f, 10f, 10f, true, false);
            HearthHooks.onBlockPlaced(home.level, helper.absolutePos(new BlockPos(3, 2, 3)),
                    Blocks.STONE_BRICKS.defaultBlockState(), fake);
            home.placedAt(new BlockPos(3, 2, 3));
            near(helper, 0.0, home.total(), "a machine feeds nothing");
            helper.assertTrue(home.state().holds().isEmpty() && home.state().ledger().isEmpty(), "and is in no ledger");
        } finally {
            home.done();
        }
    }

    // ---- Abuse check 14: decay ------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_charge_decay_load", timeoutTicks = 80)
    public static void anUnloadedHearthCatchesUpOnItsFirstTick(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        long now = home.level.getGameTime();
        long hour = HearthData.TICKS_PER_HOUR;
        // As if its chunk was unloaded: last visit 30 h ago (past the grace day), decay last applied 10 h ago.
        home.state().setCharge(HearthPool.HEARTY, 1000);
        home.state().setLastVisited(now - 30 * hour);
        home.state().setLastDecay(now - 10 * hour);
        helper.runAfterDelay(30, () -> {
            try {
                double want = 1000 * Math.pow(1 - Hearths.data().decay().perHour(), 10);
                double got = home.charge(HearthPool.HEARTY);
                helper.assertTrue(Math.abs(got - want) < 0.5, "ten hours of decay at once: wanted ~" + want + ", got " + got);
                helper.assertTrue(home.state().lastVisited() == now - 30 * hour, "nobody visited");
            } finally {
                home.done();
            }
        });
    }

    @GameTest(template = T, batch = "hearth_charge_decay_afk", timeoutTicks = 80)
    public static void anAfkAltOrACreativeBuilderHoldsNoDecayOff(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer alt = home.player(2.5, 2.5);
        ServerPlayer builder = home.player(3.5, 2.5);
        builder.setGameMode(GameType.CREATIVE);
        ProficiencyAttachments.of(builder).noteActive(home.level.getGameTime());
        long now = home.level.getGameTime();
        home.state().setCharge(HearthPool.HEARTY, 1000);
        home.state().setLastVisited(now);
        home.state().setLastDecay(now);
        helper.runAfterDelay(30, () -> {
            try {
                helper.assertTrue(home.charge(HearthPool.HEARTY) < 1000 - 1e-6,
                        "an AFK alt and a creative builder must not hold decay off: " + home.charge(HearthPool.HEARTY));
                helper.assertTrue(home.state().lastVisited() == now, "nobody counted as home");
                helper.assertTrue(alt.isAlive(), "setup");
            } finally {
                home.done();
            }
        });
    }

    @GameTest(template = T, batch = "hearth_charge_decay_home", timeoutTicks = 80)
    public static void anActivePlayerHoldsDecayOff(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 2.5);
        long now = home.level.getGameTime();
        ProficiencyAttachments.of(player).noteActive(now);
        home.state().setCharge(HearthPool.HEARTY, 1000);
        home.state().setLastVisited(now);
        home.state().setLastDecay(now);
        helper.runAfterDelay(30, () -> {
            try {
                near(helper, 1000, home.charge(HearthPool.HEARTY), "someone home: no decay");
                helper.assertTrue(home.state().lastVisited() > now, "the visit clock moved");
                helper.assertTrue(ChargeTick.someoneHome(home.level, home.pos, home.level.getGameTime()),
                        "an active survival player in range is home");
            } finally {
                home.done();
            }
        });
    }

    // ---- The hourly cap -------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_charge_cap")
    public static void theHourlyCapHoldsAPoolToItsLimit(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 2.5);
        try {
            double cap = Hearths.data().hourlyCap();
            TelemetryHub.data().drain(0L, "");
            SkillService.grant(player, Skill.MINING, cap * 20, "block.minecraft.stone");
            helper.assertTrue(mirror(home.last("block.minecraft.stone").beforeRested()) > cap, "setup: the share is over the cap");
            near(helper, cap, home.charge(HearthPool.HEARTY), "the cap");
            near(helper, cap, home.state().ledger().total(player.getUUID(), HearthPool.HEARTY,
                    HearthLedger.Window.LIFETIME), "the ledger holds only what went in");
            SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
            near(helper, cap, home.charge(HearthPool.HEARTY), "full for the hour");
            // Telemetry hearth_charge names the player and the source; the cap's refusals are "capped".
            List<String> lines = TelemetryHub.data().drain(1L, "");
            String uuid = "\"uuid\":\"" + player.getUUID() + "\"";
            boolean fed = lines.stream().anyMatch(l -> l.contains("\"type\":\"hearth\"") && l.contains(uuid)
                    && l.contains("\"kind\":\"hearth_charge\"") && l.contains("\"label\":\"hearty\"")
                    && l.contains("\"source\":\"mining\""));
            boolean capped = lines.stream().anyMatch(l -> l.contains("\"type\":\"hearth\"") && l.contains(uuid)
                    && l.contains("\"kind\":\"hearth_charge\"") && l.contains("\"label\":\"hearty\"")
                    && l.contains("\"source\":\"capped\""));
            helper.assertTrue(fed && capped, "hearth_charge telemetry per player and source, and capped: " + lines);
            // Another pool has its own hour.
            SkillService.grant(player, Skill.COOKING, 10.0, "item.minecraft.bread");
            helper.assertTrue(home.charge(HearthPool.WORKSHOP) > 0, "Workshop is not capped by Hearty");
        } finally {
            home.done();
        }
    }

    // ---- Abuse check 17: the ledger and its command ---------------------------------------------

    /** Collects what a command prints. */
    private static final class Capture implements CommandSource {
        final List<Component> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component component) {
            lines.add(component);
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }

        String text() {
            StringBuilder out = new StringBuilder();
            for (Component line : lines) {
                flat(line, out);
                out.append('\n');
            }
            return out.toString();
        }
    }

    /** A component as text with its translation keys and arguments spelled out (the server may lack the lang file). */
    private static void flat(Object part, StringBuilder out) {
        if (part instanceof Component component) {
            if (component.getContents() instanceof TranslatableContents t) {
                out.append('[').append(t.getKey());
                for (Object arg : t.getArgs()) {
                    out.append(' ');
                    flat(arg, out);
                }
                out.append(']');
            } else if (component.getContents() instanceof PlainTextContents plain) {
                out.append(plain.text());
            }
            for (Component sibling : component.getSiblings()) {
                flat(sibling, out);
            }
        } else if (part != null) {
            out.append(part);
        }
    }

    /** How many player rows a printed table has. */
    private static int rows(String table) {
        int count = 0;
        for (int at = table.indexOf("ledger.line "); at >= 0; at = table.indexOf("ledger.line ", at + 1)) {
            count++;
        }
        return count;
    }

    private static String run(CommandSourceStack source, String command) {
        Capture capture = new Capture();
        source.getServer().getCommands().performPrefixedCommand(source.withSource(capture), command);
        return capture.text();
    }

    @GameTest(template = T, batch = "hearth_charge_ledger")
    public static void theLedgerAddsUpAndTheCommandPrintsIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer amman = home.player(2.5, 2.5);
        ServerPlayer rowan = home.player(3.5, 2.5);
        UUID old = UUID.fromString("00000000-0000-0000-0000-0000000000c3");
        try {
            HearthLedger ledger = home.state().ledger();
            // Someone who fed it ten days ago: in the lifetime, not in the week.
            ledger.add(old, "Oldtimer", HearthPool.HEARTY, "mining", 40, RestedMath.today() - 10);
            home.state().addUncapped(HearthPool.HEARTY, 40);
            SkillService.grant(amman, Skill.MINING, 10.0, "block.minecraft.stone");
            SkillService.grant(amman, Skill.MINING, 12.0, "block.minecraft.deepslate");
            SkillService.grant(amman, Skill.COOKING, 10.0, "item.minecraft.bread");
            // Farming now feeds REGEN (not HEARTY); use Fishing which stays in HEARTY's gathering category.
            SkillService.grant(rowan, Skill.FISHING, 10.0, "item.minecraft.cod");
            HearthCharge.credit(home.level, home.hearth, rowan, HearthPool.HOMECOMING, HearthLedger.TALES, 7.5, true, null);

            // Every point in exactly one cell: the cells sum to each pool's charge.
            for (HearthPool pool : HearthPool.VALUES) {
                near(helper, home.charge(pool), ledger.grandTotal(pool, HearthLedger.Window.LIFETIME),
                        "ledger against " + pool.id());
            }
            double week = ledger.grandTotal(HearthPool.HEARTY, HearthLedger.Window.WEEK);
            near(helper, home.charge(HearthPool.HEARTY) - 40, week, "the week leaves out day 10");

            // The table, for a player in range.
            CommandSourceStack asAmman = amman.createCommandSourceStack();
            // Mock players all share one name, so the rows are counted rather than named.
            String all = run(asAmman, "proficiency hearth ledger");
            helper.assertTrue(all.contains("proficiency.hearth.charge.ledger.header") && rows(all) == 2
                    && all.contains(amman.getGameProfile().getName()), "the week's table lists both: " + all);
            helper.assertTrue(!all.contains("Oldtimer"), "the week leaves the old feeder out: " + all);
            helper.assertTrue(all.contains("proficiency.hearth.charge.source.tales") || all.contains("proficiency.skill.farming"),
                    "sources are named: " + all);
            String lifetime = run(asAmman, "proficiency hearth ledger hearty lifetime");
            helper.assertTrue(lifetime.contains("Oldtimer") && rows(lifetime) == 3
                    && lifetime.contains("proficiency.hearth.charge.ledger.lifetime"), "the lifetime keeps them: " + lifetime);
            String workshop = run(asAmman, "proficiency hearth ledger workshop");
            helper.assertTrue(rows(workshop) == 1 && workshop.contains("proficiency.skill.cooking"),
                    "one pool ranks its own feeders: " + workshop);
            String bad = run(asAmman, "proficiency hearth ledger nonsense");
            helper.assertTrue(bad.contains("proficiency.hearth.charge.ledger.unknown_pool"), "an unknown pool: " + bad);

            // Out of range and no operator: refused. An operator anywhere, with a position: allowed.
            rowan.setPos(rowan.getX(), rowan.getY() + 300, rowan.getZ());
            HearthAttachments.of(rowan).clearInRange();
            String away = run(rowan.createCommandSourceStack(), "proficiency hearth ledger");
            helper.assertTrue(away.contains("proficiency.hearth.charge.ledger.not_home"), "away: " + away);
            CommandSourceStack op = home.level.getServer().createCommandSourceStack().withLevel(home.level);
            String atPos = run(op, "proficiency hearth ledger at " + home.pos.getX() + " " + home.pos.getY() + " "
                    + home.pos.getZ() + " all lifetime");
            helper.assertTrue(atPos.contains("Oldtimer") && rows(atPos) == 3, "an operator reads it by position: " + atPos);

            // The panel's packet carries both windows for everyone.
            var payload = HearthCharge.ledgerPayload(home.hearth);
            helper.assertTrue(payload.rows().size() == 3, "three feeders in the packet: " + payload.rows().size());
            helper.assertTrue(payload.pos() == home.pos.asLong(), "the packet names the Hearth");

            // Decay and a douse come off the pools, never off the ledger (abuse check 17, integration).
            double fedHearty = ledger.grandTotal(HearthPool.HEARTY, HearthLedger.Window.LIFETIME);
            double hearty = home.charge(HearthPool.HEARTY);
            HearthCharge.cut(home.level, home.hearth, 0.10);
            near(helper, hearty * 0.9, home.charge(HearthPool.HEARTY), "a douse takes 10% off the pool");
            near(helper, fedHearty, ledger.grandTotal(HearthPool.HEARTY, HearthLedger.Window.LIFETIME),
                    "and nothing off the ledger");
            long now = home.level.getGameTime();
            double beforeDecay = home.charge(HearthPool.HEARTY);
            home.state().tickDecay(now, true, Hearths.data());
            home.state().tickDecay(now + 30 * HearthData.TICKS_PER_HOUR, false, Hearths.data());
            helper.assertTrue(home.charge(HearthPool.HEARTY) < beforeDecay, "thirty hours with nobody home decay the pool: "
                    + home.charge(HearthPool.HEARTY) + " of " + beforeDecay);
            near(helper, fedHearty, ledger.grandTotal(HearthPool.HEARTY, HearthLedger.Window.LIFETIME),
                    "decay leaves the ledger alone");
        } finally {
            home.done();
        }
    }

    // ---- Tier-ups ---------------------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_charge_tier")
    public static void aTierUpFiresTheEventNamesTheFeederAndIsToldOnce(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 1);
        ServerPlayer player = home.player(2.5, 2.5);
        List<HearthEvents.TierUp> events = new ArrayList<>();
        java.util.function.Consumer<HearthEvents.TierUp> listener = event -> {
            if (event.hearth().equals(home.pos)) {
                events.add(event);
            }
        };
        HearthEvents.TIER_UP.add(listener);
        try {
            double first = Hearths.data().threshold(1);
            home.state().setCharge(HearthPool.HEARTY, first - 1);
            ChargeTiers.check(home.level, home.hearth);
            helper.assertTrue(events.isEmpty(), "priming tells nothing");
            SkillService.grant(player, Skill.MINING, 50.0, "block.minecraft.stone");
            helper.assertTrue(home.charge(HearthPool.HEARTY) >= first, "setup: the grant should cross I: "
                    + home.charge(HearthPool.HEARTY));
            helper.assertTrue(events.size() == 1, "one tier-up: " + events.size());
            HearthEvents.TierUp up = events.get(0);
            helper.assertTrue(up.pool() == HearthPool.HEARTY && up.from() == 0 && up.to() == 1, "Hearty 0 to I: " + up);
            helper.assertTrue(player.getUUID().equals(up.topFeeder()) && "mining".equals(up.topSource()),
                    "carried by the player's Mining: " + up.topFeeder() + " " + up.topSource());

            // The chat line names them.
            StringBuilder line = new StringBuilder();
            flat(ChargeText.tierUp(HearthPool.HEARTY, 1, KeeperRows.top(home.state().ledger(), HearthPool.HEARTY),
                    KeeperRows.of(home.state().ledger(), HearthPool.HEARTY, HearthLedger.Window.WEEK, 3)), line);
            helper.assertTrue(line.toString().contains("proficiency.hearth.charge.tier_up ")
                    && line.toString().contains(player.getGameProfile().getName())
                    && line.toString().contains("proficiency.skill.mining"), "the line: " + line);

            // A dip by the ceiling and back is not told again.
            home.state().setStage(0);
            ChargeTiers.check(home.level, home.hearth);
            home.state().setStage(1);
            ChargeTiers.check(home.level, home.hearth);
            // Nor a smoulder and a relight.
            home.state().setSmouldering(true);
            ChargeTiers.check(home.level, home.hearth);
            home.state().setSmouldering(false);
            ChargeTiers.check(home.level, home.hearth);
            helper.assertTrue(events.size() == 1, "the ceiling and a smoulder tell nothing new: " + events.size());

            // Charge lost (a doused siege) and won back is news again.
            HearthCharge.cut(home.level, home.hearth, 0.5);
            helper.assertTrue(home.state().chargeTier(HearthPool.HEARTY, Hearths.data()) == 0, "setup: back under I");
            HearthCharge.credit(home.level, home.hearth, player, HearthPool.HEARTY, "mining", first, false, Skill.MINING);
            helper.assertTrue(events.size() == 2 && events.get(1).to() == 1, "the re-climb is told: " + events.size());
        } finally {
            HearthEvents.TIER_UP.remove(listener);
            home.done();
        }
    }

    // ---- The siege lane's API -------------------------------------------------------------------

    @GameTest(template = T, batch = "hearth_charge_burst")
    public static void aSiegeVictoryBurstsOutsideTheCapSplitByKills(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Home home = new Home(helper, 3);
        ServerPlayer amman = home.player(2.5, 2.5);
        ServerPlayer rowan = home.player(3.5, 2.5);
        try {
            HearthData data = Hearths.data();
            // Hearty full for the hour, at tier I.
            SkillService.grant(amman, Skill.MINING, data.hourlyCap() * 20, "block.minecraft.stone");
            double hearty = home.charge(HearthPool.HEARTY);
            near(helper, data.hourlyCap(), hearty, "setup: Hearty at its cap");
            Map<UUID, Integer> kills = new LinkedHashMap<>();
            kills.put(amman.getUUID(), 3);
            kills.put(rowan.getUUID(), 1);
            HearthCharge.siegeKill(home.hearth, amman.getUUID(), amman.getGameProfile().getName());
            HearthCharge.siegeDefended(home.hearth, List.of(amman, rowan));
            double before = home.total();
            double added = HearthCharge.siegeBurst(home.level, home.hearth, kills, List.of(amman, rowan), 1);
            double share = data.siege().burst() + data.siege().streakBonus();
            near(helper, hearty + data.threshold(2) * share, home.charge(HearthPool.HEARTY),
                    "Hearty bursts past its hourly cap toward II");
            near(helper, data.threshold(1) * share, home.charge(HearthPool.WORKSHOP), "an empty pool bursts toward I");
            near(helper, before + added, home.total(), "what the burst says it added");
            HearthLedger ledger = home.state().ledger();
            double ammanSiege = ledger.sources(amman.getUUID(), HearthPool.WORKSHOP, HearthLedger.Window.LIFETIME).get(0).total();
            double rowanSiege = ledger.total(rowan.getUUID(), HearthPool.WORKSHOP, HearthLedger.Window.LIFETIME);
            near(helper, 3 * rowanSiege, ammanSiege, "split three to one by kills");
            for (HearthPool pool : HearthPool.VALUES) {
                near(helper, home.charge(pool), ledger.grandTotal(pool, HearthLedger.Window.LIFETIME), "ledger " + pool.id());
            }
            helper.assertTrue(ledger.get(amman.getUUID()).siegeKills() == 1
                    && ledger.get(rowan.getUUID()).siegesDefended() == 1, "siege counts");
            // A doused siege comes off the pools, never the ledger.
            double ledgerBefore = ledger.grandTotal(null, HearthLedger.Window.LIFETIME);
            double totalBefore = home.total();
            HearthCharge.cut(home.level, home.hearth, data.siege().dousedLoss());
            near(helper, totalBefore * (1 - data.siege().dousedLoss()), home.total(), "doused loss");
            near(helper, ledgerBefore, ledger.grandTotal(null, HearthLedger.Window.LIFETIME), "the ledger keeps it");
        } finally {
            home.done();
        }
    }
}
