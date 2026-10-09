package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.GatheringEvents;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillProcEvent;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.XpFactors;
import dev.amman.proficiency.platform.bus.EventPriority;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

/**
 * An area tool (a hammer, an excavator, a broadaxe, a paxel, a vein-mining mod) breaks the blocks
 * around the one you hit by breaking them through the player, so each one fires a block break of its
 * own. These tests stand in for such a tool: a listener that, inside the primary block's break
 * event, breaks eight neighbours through {@code gameMode.destroyBlock}, which is exactly what
 * reclamation_util's AreaBreakItemUsage does. The primary pays in full; the extras pay a share.
 *
 * <p>Excluded from the shipped jar by the {@code jar} task; they exist for the loaders' GameTest runs.
 */

public final class AoeGameTests {

    private static final BlockPos PRIMARY = new BlockPos(2, 2, 1);

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public AoeGameTests() {
    }

    // ---- a stand-in area tool ---------------------------------------------------------------

    private enum Mode { OFF, NORMAL, HIGH }

    private static volatile Mode mode = Mode.OFF;
    private static volatile ServerPlayer owner;
    private static volatile BlockPos primaryAbs;
    private static volatile List<BlockPos> neighboursAbs = List.of();
    private static volatile boolean breaking;
    private static boolean hooked;

    private static synchronized void hook() {
        if (hooked) {
            return;
        }
        hooked = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, BlockEvent.BreakEvent.class,
                event -> swing(event, Mode.NORMAL));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, false, BlockEvent.BreakEvent.class,
                event -> swing(event, Mode.HIGH));
    }

    private static void swing(BlockEvent.BreakEvent event, Mode wanted) {
        if (mode != wanted || breaking || event.getPlayer() != owner || !event.getPos().equals(primaryAbs)) {
            return;
        }
        breaking = true;
        try {
            for (BlockPos extra : neighboursAbs) {
                owner.gameMode.destroyBlock(extra);
            }
        } finally {
            breaking = false;
        }
    }

    /** A 3x3 face around the primary, in the x/y plane. */
    private static List<BlockPos> face(GameTestHelper helper, java.util.function.Function<BlockPos, net.minecraft.world.level.block.Block> fill) {
        List<BlockPos> extras = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos rel = PRIMARY.offset(dx, dy, 0);
                helper.setBlock(rel, rel.equals(PRIMARY) ? Blocks.STONE : fill.apply(rel));
                if (!rel.equals(PRIMARY)) {
                    extras.add(helper.absolutePos(rel));
                }
            }
        }
        return extras;
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        // The mock player may keep the creative ability after setGameMode; these tests need survival.
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        return player;
    }

    private static void swingAt(GameTestHelper helper, ServerPlayer player, Mode how, List<BlockPos> extras) {
        hook();
        owner = player;
        primaryAbs = helper.absolutePos(PRIMARY);
        neighboursAbs = extras;
        mode = how;
        try {
            player.gameMode.destroyBlock(primaryAbs);
        } finally {
            mode = Mode.OFF;
            owner = null;
        }
    }

    private static float paid(ServerPlayer player, Skill skill, String sourcePrefix) {
        float sum = 0f;
        var log = SkillService.xpLog(player);
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == skill.ordinal() && entry.source().startsWith(sourcePrefix)) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    private static String dump(ServerPlayer player, Skill skill) {
        StringBuilder out = new StringBuilder();
        var log = SkillService.xpLog(player);
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == skill.ordinal()) {
                    out.append("\n  ").append(entry.source()).append(" x").append(entry.count())
                            .append(" amount ").append(entry.amount()).append(" base ").append(entry.base())
                            .append(" [").append(entry.factors()).append("]");
                }
            }
        }
        return out.toString();
    }

    private static boolean hasAoeFactor(ServerPlayer player, Skill skill) {
        var log = SkillService.xpLog(player);
        if (log == null) {
            return false;
        }
        for (var entry : log.entries()) {
            if (entry.skill() == skill.ordinal()
                    && XpFactors.decode(entry.factors()).stream().anyMatch(f -> f.id().equals(XpFactors.AOE))) {
                return true;
            }
        }
        return false;
    }

    private static final String STONE = Blocks.STONE.getDescriptionId();

    // ---- the tests --------------------------------------------------------------------------

    /** One block on its own, for the number an extra is a share of. */
    private static float loneStone(GameTestHelper helper) {
        ServerPlayer lone = survivor(helper);
        helper.setBlock(PRIMARY, Blocks.STONE);
        Breaks.separately(lone, helper.absolutePos(PRIMARY));
        return paid(lone, Skill.MINING, STONE);
    }

    @GameTest(template = "proficiency:empty")
    public static void anAreaSwingPaysTheFirstBlockInFullAndEightExtrasAtAQuarter(GameTestHelper helper) {
        float one = loneStone(helper);
        helper.assertTrue(one > 0f, "a lone stone paid nothing");

        ServerPlayer player = survivor(helper);
        List<BlockPos> extras = face(helper, rel -> Blocks.STONE);
        swingAt(helper, player, Mode.NORMAL, extras);

        float total = paid(player, Skill.MINING, STONE);
        float expected = one * (1f + 8f * 0.25f);
        // Allow a few percent for the rested pool and rounding.
        helper.assertTrue(Math.abs(total - expected) < expected * 0.03f,
                "the swing paid " + total + " Mining XP, wanted " + expected + " (one block " + one + ")"
                        + dump(player, Skill.MINING));
        helper.assertTrue(hasAoeFactor(player, Skill.MINING), "no log line carries the aoe factor");
        for (BlockPos extra : extras) {
            helper.assertTrue(helper.getBlockState(extra.subtract(helper.absolutePos(BlockPos.ZERO))).isAir(),
                    "an extra block was not broken");
        }
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void extrasPayNoFirstTimeBonus(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        // Coal ore is a new kind for this player. As an extra it must not mark itself seen or pay the bonus.
        List<BlockPos> extras = face(helper, rel -> Blocks.COAL_ORE);
        swingAt(helper, player, Mode.NORMAL, extras);
        var skills = ProficiencyAttachments.of(player);
        helper.assertFalse(skills.hasVisited("first:" + Skill.MINING.id() + ":block.minecraft.coal_ore"),
                "an extra block paid or marked the first-time bonus");
        helper.assertTrue(paid(player, Skill.MINING, "first|block.minecraft.coal_ore") == 0f,
                "an extra coal ore paid a first-time line");
        helper.assertTrue(skills.hasVisited("first:" + Skill.MINING.id() + ":block.minecraft.stone"),
                "the primary block did not pay its first-time bonus");
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void extrasRollNoProcAndTheFirstBlockKeepsIt(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        List<BlockPos> extras = face(helper, rel -> Blocks.STONE);
        List<BlockPos> procs = new ArrayList<>();
        NeoForge.EVENT_BUS.addListener(SkillProcEvent.class, event -> {
            if (event.player() == player && event.skill() == Skill.MINING && event.pos() != null) {
                procs.add(event.pos());
            }
        });
        ProcService.forceNext(player, Skill.MINING);
        // The stand-in tool listens first, so the eight extras break before the primary's own
        // handlers run: if an extra rolled the proc it would take the forced one.
        swingAt(helper, player, Mode.HIGH, extras);
        helper.assertTrue(procs.size() == 1, "expected exactly one Mining proc, got " + procs.size());
        helper.assertTrue(procs.get(0).equals(helper.absolutePos(PRIMARY)),
                "the proc landed on " + procs.get(0) + ", not on the block the player broke");
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void placedBlocksAndWrongToolStillPayNothingAsExtras(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        List<BlockPos> extras = face(helper, rel -> Blocks.AIR);
        // One extra a player placed, one obsidian an iron pickaxe cannot harvest, one real stone.
        BlockPos placed = PRIMARY.east();
        BlockPos obsidian = PRIMARY.west();
        BlockPos natural = PRIMARY.above();
        helper.setBlock(placed, Blocks.STONE);
        PlacedBlocks.markPlacement(helper.getLevel(), helper.absolutePos(placed), Blocks.STONE.defaultBlockState());
        helper.setBlock(obsidian, Blocks.OBSIDIAN);
        helper.setBlock(natural, Blocks.STONE);
        List<BlockPos> three = List.of(helper.absolutePos(placed), helper.absolutePos(obsidian), helper.absolutePos(natural));
        float one = loneStone(helper);
        helper.setBlock(PRIMARY, Blocks.STONE);
        swingAt(helper, player, Mode.NORMAL, three);
        float total = paid(player, Skill.MINING, "block.");
        float expected = one * (1f + 0.25f);
        helper.assertTrue(Math.abs(total - expected) < expected * 0.01f,
                "wanted the primary plus one natural extra (" + expected + "), got " + total);
        helper.succeed();
    }

    /** An ore pays Mining and Spelunking; an extra ore of a swing pays a quarter of both, not a full Spelunking line each. */
    @GameTest(template = "proficiency:empty")
    public static void oreExtrasPayTheirSpelunkingAtAQuarterToo(GameTestHelper helper) {
        String iron = Blocks.IRON_ORE.getDescriptionId();
        ServerPlayer lone = survivor(helper);
        helper.setBlock(PRIMARY, Blocks.IRON_ORE);
        Breaks.separately(lone, helper.absolutePos(PRIMARY));
        float one = paid(lone, Skill.SPELUNKING, iron);
        helper.assertTrue(one > 0f, "a lone iron ore paid no Spelunking");

        ServerPlayer player = survivor(helper);
        List<BlockPos> extras = face(helper, rel -> Blocks.IRON_ORE);
        helper.setBlock(PRIMARY, Blocks.IRON_ORE);
        swingAt(helper, player, Mode.NORMAL, extras);

        float total = paid(player, Skill.SPELUNKING, iron);
        float expected = one * (1f + 8f * 0.25f);
        helper.assertTrue(Math.abs(total - expected) < expected * 0.03f,
                "the swing paid " + total + " Spelunking XP, wanted " + expected + " (one ore " + one + ")"
                        + dump(player, Skill.SPELUNKING));
        helper.assertTrue(hasAoeFactor(player, Skill.SPELUNKING), "no Spelunking line carries the aoe factor");
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void extrasAreStillSmeltedByTheTalent(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ProficiencyAttachments.of(player).setRank(Talents.get(Skill.MINING, "smelter"), 1);
        List<BlockPos> extras = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            BlockPos rel = PRIMARY.offset(dx, 0, 0);
            helper.setBlock(rel, Blocks.IRON_ORE);
            if (dx != 0) {
                extras.add(helper.absolutePos(rel));
            }
        }
        swingAt(helper, player, Mode.NORMAL, extras);
        int ingots = 0;
        int raw = 0;
        for (ItemEntity item : helper.getLevel().getEntities(EntityType.ITEM,
                new net.minecraft.world.phys.AABB(helper.absolutePos(PRIMARY)).inflate(6), e -> true)) {
            if (item.getItem().is(Items.IRON_INGOT)) {
                ingots += item.getItem().getCount();
            }
            if (item.getItem().is(Items.RAW_IRON)) {
                raw += item.getItem().getCount();
            }
        }
        helper.assertTrue(ingots >= 3 && raw == 0,
                "wanted three ingots and no raw iron (primary and both extras smelted), got " + ingots + " ingots, " + raw + " raw");
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void aSecondBreakInTheSameTickIsAnExtraAndANewTickIsNot(GameTestHelper helper) {
        float one = loneStone(helper);
        ServerPlayer player = survivor(helper);
        BlockPos a = PRIMARY;
        BlockPos b = PRIMARY.east();
        helper.setBlock(a, Blocks.STONE);
        helper.setBlock(b, Blocks.STONE);
        // Sequential breaks in one tick, no listener: the second is an extra.
        player.gameMode.destroyBlock(helper.absolutePos(a));
        player.gameMode.destroyBlock(helper.absolutePos(b));
        float together = paid(player, Skill.MINING, STONE);
        helper.assertTrue(Math.abs(together - one * 1.25f) < one * 1.25f * 0.01f,
                "two breaks in one tick paid " + together + ", wanted " + one * 1.25f);

        // A fresh tick: both are first blocks.
        ServerPlayer other = survivor(helper);
        helper.setBlock(a, Blocks.STONE);
        helper.setBlock(b, Blocks.STONE);
        Breaks.separately(other, helper.absolutePos(a));
        Breaks.separately(other, helper.absolutePos(b));
        float apart = paid(other, Skill.MINING, STONE);
        helper.assertTrue(apart > one * 1.9f,
                "two separate breaks paid " + apart + ", wanted about " + one * 2f);
        helper.succeed();
    }

    @SuppressWarnings("unused")
    private static void unused() {
        GatheringEvents.forgetAoe(java.util.UUID.randomUUID());
    }
}
