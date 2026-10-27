package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.net.HearthBuildPlanPayload;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Hearth builds its next stage by itself: one block per {@code hearth.buildSecondsPerBlock} (a minute by
 * default), taken from the home players' packs and the chests, barrels and shulker boxes in the Hearth's range. It pays no XP: the blocks
 * are set by the Hearth, not placed by a player, so Masonry, Decorating and the break skills never hear of them.
 * Sealing the stage stays the player's own offering. {@link AutoBuild} picks each step; this class reads the world,
 * finds the material, places it and keeps the job in the block entity (lane {@code "autobuild"}), so it survives a
 * restart. It runs on the Hearth's own second, so only while the Hearth's chunk is loaded.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class HearthAutoBuild {

    static final String KEY = "autobuild";
    private static final String ON = "on";
    private static final String LANE = "lane";
    private static final String STAGE = "stage";
    private static final String WAIT = "wait";
    private static final String TOTAL = "total";
    private static final String LEFT = "left";
    private static final String WHY = "why";
    private static final String MISSING = "missing";
    private static final String USE = "use.";
    /** Game time when the current wait began; 0 while the build is placing blocks (idea 16, the chunk ticket). */
    private static final String WAIT_SINCE = "waitSince";
    /** Seconds between two tries while the build waits, so a restocked chest is seen soon. */
    private static final int RETRY = 5;
    private static final String P = "proficiency.hearth.build.";

    /** Seconds per block for the GameTests; 0 means the config. */
    public static volatile int secondsOverride;

    private HearthAutoBuild() {
    }

    static int interval() {
        return secondsOverride > 0 ? secondsOverride : ProficiencyConfig.hearthBuildSeconds();
    }

    // ---- Requests -------------------------------------------------------------------------------------

    /** The panel's "Build it for me": checks, then starts the build of the next stage. */
    public static void start(ServerPlayer player, BlockPos pos, int laneIndex) {
        ServerLevel level = player.serverLevel();
        HearthBlockEntity hearth = HearthRange.entity(level, pos);
        if (hearth == null) {
            return;
        }
        int target = hearth.state().stage() + 1;
        if (target > HearthDesigns.STAGES) {
            StageText.chat(player, Component.translatable(P + "complete"));
            return;
        }
        HearthDesigns designs = HearthDesigns.shipped();
        HearthData data = Hearths.data();
        HearthDesigns.Probe probe = StageBlocks.probe(level, pos, data);
        int index = laneIndex >= 0 && laneIndex < designs.lanes().size() ? laneIndex
                : AutoBuild.builtLane(designs, probe, target);
        HearthDesigns.Lane lane = designs.lanes().get(index);
        List<StageGhost.Cell> diff = StageGhost.diff(lane.stage(target), probe);
        if (diff.isEmpty()) {
            StageText.chat(player, Component.translatable(P + "already", stage(target),
                    StageBlocks.offeringName(data, target)));
            return;
        }
        CompoundTag tag = hearth.laneData(KEY);
        Site site = new Site(level, pos, hearth, lane, target, probe, tag);
        List<StageGhost.Cell> foreign = AutoBuild.foreign(diff, site);
        if (!foreign.isEmpty()) {
            StageGhost.Cell first = foreign.get(0);
            StageText.chat(player, Component.translatable(P + "foreign", foreign.size(), stage(target),
                    level.getBlockState(pos.offset(first.dx(), first.dy(), first.dz())).getBlock().getName(),
                    StageText.where(first.dx(), first.dy(), first.dz())));
            for (StageGhost.Cell cell : foreign) {
                mark(level, pos.offset(cell.dx(), cell.dy(), cell.dz()));
            }
            return;
        }
        site.lockChoices(diff);
        tag.putBoolean(ON, true);
        tag.putString(LANE, lane.id());
        tag.putInt(STAGE, target);
        tag.putInt(TOTAL, diff.size());
        tag.putInt(LEFT, diff.size());
        tag.putInt(WAIT, 1);
        tag.putString(WHY, AutoBuild.Wait.NONE.name());
        tag.putString(MISSING, "");
        tag.remove(WAIT_SINCE);
        hearth.setChanged();
        refreshHold(level, pos, hearth);
        StageText.chat(player, Component.translatable(P + "started", stage(target),
                Component.translatable("proficiency.hearth.guide.lane." + lane.id()), diff.size(),
                duration((long) diff.size() * interval())));
        HearthPresence.update(level, pos, hearth);
    }

    /**
     * The plan screen's data: per role of the next stage (the running build's stage and lane while it runs), the
     * blocks needed, the block it takes and every block in range that fits, with counts and places.
     */
    public static void plan(ServerPlayer player, BlockPos pos, int laneIndex) {
        ServerLevel level = player.serverLevel();
        HearthBlockEntity hearth = HearthRange.entity(level, pos);
        if (hearth == null) {
            return;
        }
        CompoundTag tag = hearth.laneData(KEY);
        boolean running = tag.getBoolean(ON);
        HearthDesigns designs = HearthDesigns.shipped();
        int target = running ? tag.getInt(STAGE) : hearth.state().stage() + 1;
        if (target > HearthDesigns.STAGES) {
            HearthNet.sendBuildPlan(player, new HearthBuildPlanPayload(pos.asLong(), target, Math.max(0, laneIndex),
                    false, interval(), 0, 0, List.of()));
            return;
        }
        HearthDesigns.Probe probe = StageBlocks.probe(level, pos, Hearths.data());
        int index = -1;
        if (running) {
            for (int i = 0; i < designs.lanes().size(); i++) {
                if (designs.lanes().get(i).id().equals(tag.getString(LANE))) {
                    index = i;
                }
            }
        }
        if (index < 0) {
            index = laneIndex >= 0 && laneIndex < designs.lanes().size() ? laneIndex
                    : AutoBuild.builtLane(designs, probe, target);
        }
        HearthDesigns.Lane lane = designs.lanes().get(index);
        List<StageGhost.Cell> diff = StageGhost.diff(lane.stage(target), probe);
        Site site = new Site(level, pos, hearth, lane, target, probe, tag);
        List<HearthBuildPlanPayload.Row> rows = new ArrayList<>();
        // Rows in the stage's own order, so they do not move about as the build goes on.
        Map<Character, Integer> needs = BuildPlan.needs(diff);
        Map<Character, Integer> ordered = new java.util.LinkedHashMap<>();
        for (HearthDesigns.Cell cell : lane.stage(target).cells()) {
            Integer count = needs.get(cell.role());
            if (count != null) {
                ordered.putIfAbsent(cell.role(), count);
            }
        }
        for (Map.Entry<Character, Integer> need : ordered.entrySet()) {
            char role = need.getKey();
            List<Site.Stock> stock = site.stock(role);
            List<HearthBuildPlanPayload.Option> options = new ArrayList<>();
            for (Site.Stock entry : stock) {
                options.add(new HearthBuildPlanPayload.Option(optionId(role, entry.block), entry.count, places(pos, entry)));
            }
            String chosen;
            if (HearthDesigns.isFluid(role)) {
                chosen = optionId(role, null);
            } else {
                Block block = site.chosen(role);
                if (block == null) {
                    block = site.fallback(role, stock);
                }
                chosen = block == null ? "" : BuiltInRegistries.BLOCK.getKey(block).toString();
            }
            rows.add(new HearthBuildPlanPayload.Row(role, need.getValue(), chosen, List.copyOf(options)));
        }
        int clears = 0;
        for (StageGhost.Cell cell : diff) {
            if (cell.kind() == StageGhost.Kind.BLOCKED) {
                clears++;
            }
        }
        HearthNet.sendBuildPlan(player, new HearthBuildPlanPayload(pos.asLong(), target, index, running, interval(),
                clears, AutoBuild.foreign(diff, site).size(), List.copyOf(rows)));
    }

    /** The plan screen picks the block for a role ("" back to automatic). A block that does not fit is ignored. */
    public static void choose(ServerPlayer player, BlockPos pos, char role, String blockId) {
        HearthBlockEntity hearth = HearthRange.entity(player.serverLevel(), pos);
        if (hearth == null || HearthDesigns.isFluid(role) || HearthDesigns.ROLES.indexOf(role) < 0) {
            return;
        }
        CompoundTag tag = hearth.laneData(KEY);
        if (blockId.isEmpty()) {
            tag.remove(USE + role);
        } else {
            Block block = blockOf(blockId);
            var roleTag = StageBlocks.roleTag(role);
            if (block == null || roleTag == null || !block.defaultBlockState().is(roleTag)) {
                return;
            }
            tag.putString(USE + role, blockId);
        }
        hearth.setChanged();
    }

    /** The id the screen shows for an option: the block's, or the bucket's for water and lava. */
    private static String optionId(char role, @Nullable Block block) {
        if (role == 'v') {
            return "minecraft:water_bucket";
        }
        if (role == 'V') {
            return "minecraft:lava_bucket";
        }
        return block == null ? "" : BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** The pack first, then the containers holding most, as offsets from the fire. */
    private static List<HearthBuildPlanPayload.Where> places(BlockPos fire, Site.Stock entry) {
        List<HearthBuildPlanPayload.Where> out = new ArrayList<>();
        if (entry.pack > 0) {
            out.add(new HearthBuildPlanPayload.Where(true, 0, 0, 0, entry.pack));
        }
        List<Map.Entry<BlockPos, Integer>> places = new ArrayList<>(entry.places.entrySet());
        places.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<BlockPos, Integer> place : places) {
            if (out.size() >= HearthBuildPlanPayload.MAX_WHERE) {
                break;
            }
            BlockPos at = place.getKey();
            out.add(new HearthBuildPlanPayload.Where(false, at.getX() - fire.getX(), at.getY() - fire.getY(),
                    at.getZ() - fire.getZ(), place.getValue()));
        }
        return List.copyOf(out);
    }

    /** The panel's "Stop building". */
    public static void stop(ServerPlayer player, BlockPos pos) {
        HearthBlockEntity hearth = HearthRange.entity(player.serverLevel(), pos);
        if (hearth == null) {
            return;
        }
        CompoundTag tag = hearth.laneData(KEY);
        if (!tag.getBoolean(ON)) {
            return;
        }
        tag.putBoolean(ON, false);
        hearth.setChanged();
        refreshHold(player.serverLevel(), pos, hearth);
        int total = tag.getInt(TOTAL);
        StageText.chat(player, Component.translatable(P + "stopped", total - tag.getInt(LEFT), total));
        HearthPresence.update(player.serverLevel(), pos, hearth);
    }

    /** What the panel shows. */
    public static HearthStatePayload.Build status(HearthBlockEntity hearth) {
        CompoundTag tag = hearth.laneData(KEY);
        if (!tag.getBoolean(ON)) {
            return HearthStatePayload.Build.NONE;
        }
        int total = tag.getInt(TOTAL);
        int why = switch (whyOf(tag)) {
            case MATERIAL -> 1;
            case FOREIGN -> 2;
            case SUPPORT -> 3;
            default -> 0;
        };
        String missing = tag.getString(MISSING);
        // Bit 16 of "why": the build holds the chunk, so it goes on while the player is away (no new field: no protocol bump).
        if (hearth.getLevel() instanceof ServerLevel level && HearthChunks.isHeld(level, hearth.getBlockPos())) {
            why |= HearthStatePayload.Build.HELD;
        }
        return new HearthStatePayload.Build(true, tag.getInt(STAGE), Math.max(0, total - tag.getInt(LEFT)), total,
                Math.max(0, tag.getInt(WAIT)), why, missing.isEmpty() ? List.of() : List.of(missing.split(",")));
    }

    /**
     * The design lane this Hearth is built in, for the Jade tooltip: the running build's lane, else the lane its
     * built stages match best (what the Build button would pick). "" when the Hearth itself is not loaded; a neighbouring chunk that is not loaded reads as air, so a
     * tooltip never loads one. Reads the world, so callers cache it.
     */
    /** The lane the Hearth has marked as building, or null when it is not marked ON (no block is read). */
    public static String markedLane(HearthBlockEntity hearth) {
        CompoundTag tag = hearth.peekLane(KEY);
        return tag != null && tag.getBoolean(ON) && !tag.getString(LANE).isEmpty() ? tag.getString(LANE) : null;
    }

    public static String laneId(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        CompoundTag tag = hearth.peekLane(KEY);
        if (tag != null && tag.getBoolean(ON) && !tag.getString(LANE).isEmpty()) {
            return tag.getString(LANE);
        }
        if (!level.isLoaded(pos)) {
            return "";
        }
        HearthDesigns designs = HearthDesigns.shipped();
        int target = Math.min(HearthDesigns.STAGES, hearth.state().stage() + 1);
        int index = AutoBuild.builtLane(designs, StageBlocks.probeLoaded(level, pos), target);
        return index >= 0 && index < designs.lanes().size() ? designs.lanes().get(index).id() : "";
    }

    // ---- The Hearth's second ----------------------------------------------------------------------------

    /** Once a second per Hearth: counts down, then does one step. */
    public static void tick(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        step(level, pos, hearth);
        refreshHold(level, pos, hearth);
    }

    /**
     * Holds the Hearth's chunk while a job runs, lets it go when the job ends or has waited past the limit
     * ({@link ChunkHold}, {@link HearthChunks}). Safe to call at any time; it only compares and sets.
     */
    public static void refreshHold(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        CompoundTag tag = hearth.peekLane(KEY);
        if (tag == null || !tag.getBoolean(ON)) {
            // Idle Hearth: nothing to hold; only a ticket left from a build that just ended needs letting go.
            if (HearthChunks.release(level, pos)) {
                HearthPresence.update(level, pos, hearth);
            }
            return;
        }
        long since = tag.getLong(WAIT_SINCE);
        long waited = since <= 0 ? 0 : Math.max(1, (level.getGameTime() - since) / 20);
        if (ChunkHold.keep(ProficiencyConfig.hearthBuildKeepsChunk(), tag.getBoolean(ON), waited,
                ProficiencyConfig.hearthBuildChunkWaitMinutes())) {
            HearthChunks.hold(level, pos);
        } else if (HearthChunks.release(level, pos)) {
            // The panel's "goes on while you are away" line must go with the ticket.
            HearthPresence.update(level, pos, hearth);
        }
    }

    private static void step(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        CompoundTag tag = hearth.laneData(KEY);
        if (!tag.getBoolean(ON)) {
            return;
        }
        int wait = tag.getInt(WAIT) - 1;
        tag.putInt(WAIT, wait);
        hearth.setChanged();
        if (wait > 0) {
            return;
        }
        int target = tag.getInt(STAGE);
        HearthDesigns.Lane lane = HearthDesigns.shipped().lane(tag.getString(LANE));
        if (lane == null || hearth.state().stage() >= target) {
            tag.putBoolean(ON, false);
            HearthPresence.update(level, pos, hearth);
            return;
        }
        HearthDesigns.Probe probe = StageBlocks.probe(level, pos, Hearths.data());
        List<StageGhost.Cell> diff = StageGhost.diff(lane.stage(target), probe);
        if (diff.isEmpty()) {
            finish(level, pos, hearth, tag, target);
            return;
        }
        Site site = new Site(level, pos, hearth, lane, target, probe, tag);
        AutoBuild.Step step = AutoBuild.next(diff, site);
        if (step == null) {
            waitFor(level, pos, hearth, tag, diff, site);
            return;
        }
        site.run(step);
        HearthStageCheck.touched(level, pos.offset(step.cell().dx(), step.cell().dy(), step.cell().dz()));
        int left = StageGhost.wrong(lane.stage(target), probe);
        tag.putInt(LEFT, left);
        tag.putInt(WAIT, interval());
        tag.putString(WHY, AutoBuild.Wait.NONE.name());
        tag.putString(MISSING, "");
        tag.remove(WAIT_SINCE);
        if (left == 0) {
            finish(level, pos, hearth, tag, target);
        } else {
            HearthPresence.update(level, pos, hearth);
        }
    }

    private static void finish(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, CompoundTag tag, int target) {
        tag.putBoolean(ON, false);
        tag.putInt(LEFT, 0);
        hearth.setChanged();
        Component line = Component.translatable(P + "done", stage(target),
                StageBlocks.offeringName(Hearths.data(), target));
        for (ServerPlayer player : HearthPresence.inRange(level, pos)) {
            StageText.chat(player, line);
        }
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        HearthPresence.update(level, pos, hearth);
    }

    private static void waitFor(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, CompoundTag tag,
            List<StageGhost.Cell> diff, Site site) {
        AutoBuild.Wait why = AutoBuild.why(diff, site);
        StringBuilder missing = new StringBuilder();
        if (why == AutoBuild.Wait.MATERIAL) {
            for (Map.Entry<String, Integer> entry : AutoBuild.missing(diff, site).entrySet()) {
                if (!missing.isEmpty()) {
                    missing.append(',');
                }
                missing.append(entry.getKey()).append(':').append(entry.getValue());
            }
        }
        tag.putInt(LEFT, diff.size());
        tag.putInt(WAIT, RETRY);
        if (tag.getLong(WAIT_SINCE) <= 0) {
            tag.putLong(WAIT_SINCE, Math.max(1, level.getGameTime()));
        }
        boolean changed = whyOf(tag) != why || !tag.getString(MISSING).contentEquals(missing);
        tag.putString(WHY, why.name());
        tag.putString(MISSING, missing.toString());
        hearth.setChanged();
        if (!changed) {
            return;
        }
        Component line = switch (why) {
            case MATERIAL -> Component.translatable(P + "wait.material", materials(missing.toString()));
            case FOREIGN -> {
                StageGhost.Cell first = AutoBuild.foreign(diff, site).get(0);
                yield Component.translatable(P + "wait.foreign", StageText.where(first.dx(), first.dy(), first.dz()));
            }
            default -> Component.translatable(P + "wait.support");
        };
        for (ServerPlayer player : HearthPresence.inRange(level, pos)) {
            StageText.chat(player, line);
        }
        if (why == AutoBuild.Wait.FOREIGN) {
            for (StageGhost.Cell cell : AutoBuild.foreign(diff, site)) {
                mark(level, pos.offset(cell.dx(), cell.dy(), cell.dz()));
            }
        }
        HearthPresence.update(level, pos, hearth);
    }

    private static AutoBuild.Wait whyOf(CompoundTag tag) {
        try {
            return AutoBuild.Wait.valueOf(tag.getString(WHY));
        } catch (IllegalArgumentException e) {
            return AutoBuild.Wait.NONE;
        }
    }

    @Nullable
    static Block blockOf(@Nullable String id) {
        ResourceLocation location = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    /** "stage II, Hearth": the stage by number and name, as the panel names it. */
    static MutableComponent stage(int stage) {
        return Component.translatable(P + "stage", dev.amman.proficiency.hearth.HearthRules.roman(stage),
                StageText.stageName(stage));
    }

    /** "Stone ×12, Iron bars ×4" from "stone:12,iron_bars:4". */
    public static MutableComponent materials(String missing) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (String part : missing.split(",")) {
            int colon = part.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Component.translatable("proficiency.hearth.guide.mat." + part.substring(0, colon)))
                    .append(" ×" + part.substring(colon + 1));
        }
        return out;
    }

    /** "3 h 32 min", "12 min", "45 s". */
    public static String duration(long seconds) {
        if (seconds < 60) {
            return seconds + " s";
        }
        long minutes = (seconds + 59) / 60;
        return minutes < 60 ? minutes + " min" : minutes / 60 + " h " + minutes % 60 + " min";
    }

    /** A red puff on a player's block that stands in the way. */
    private static void mark(ServerLevel level, BlockPos at) {
        level.sendParticles(new DustParticleOptions(new Vector3f(1.0f, 0.15f, 0.1f), 1.4f),
                at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.35, 0.35, 0.35, 0.0);
    }

    // ---- The world around one Hearth, for one step ----------------------------------------------------------

    /** One step's view: what stands where, and the material the sources hold. */
    static final class Site implements AutoBuild.World {

        private final ServerLevel level;
        private final BlockPos fire;
        private final HearthDesigns.Lane lane;
        @Nullable
        private final HearthDesigns.Stage below;
        private final HearthDesigns.Probe probe;
        private final CompoundTag tag;
        private final List<Container> sources;
        /** Where each container stands; a player's pack has no entry. */
        private final Map<Container, BlockPos> where = new java.util.IdentityHashMap<>();
        private final Map<Character, Pick> picks = new HashMap<>();

        Site(ServerLevel level, BlockPos fire, HearthBlockEntity hearth, HearthDesigns.Lane lane, int target,
                HearthDesigns.Probe probe, CompoundTag tag) {
            this.level = level;
            this.fire = fire;
            this.lane = lane;
            this.below = target > 1 ? lane.stage(target - 1) : null;
            this.probe = probe;
            this.tag = tag;
            this.sources = sources(level, fire, where);
        }

        @Override
        public AutoBuild.Occupant at(int dx, int dy, int dz) {
            BlockPos at = fire.offset(dx, dy, dz);
            BlockState state = level.getBlockState(at);
            if (state.isAir()) {
                return AutoBuild.Occupant.EMPTY;
            }
            if (state.getBlock() instanceof LiquidBlock) {
                return state.getFluidState().isSource() ? AutoBuild.Occupant.FOREIGN : AutoBuild.Occupant.EMPTY;
            }
            if (below != null) {
                HearthDesigns.Cell was = below.cellAt(dx, dy, dz);
                if (was != null && was.role() != '.' && was.role() != 'F' && probe.is(was.role(), dx, dy, dz)) {
                    return AutoBuild.Occupant.OWN;
                }
            }
            if (state.canBeReplaced()) {
                return AutoBuild.Occupant.EMPTY;
            }
            return terrain(state) ? AutoBuild.Occupant.TERRAIN : AutoBuild.Occupant.FOREIGN;
        }

        @Override
        public boolean hasMaterial(char role) {
            return pick(role) != null;
        }

        @Override
        public boolean canStand(char role, int dx, int dy, int dz) {
            Pick pick = pick(role);
            if (pick == null) {
                return false;
            }
            BlockPos at = fire.offset(dx, dy, dz);
            return placed(pick.block, role, at).canSurvive(level, at);
        }

        void run(AutoBuild.Step step) {
            StageGhost.Cell cell = step.cell();
            BlockPos at = fire.offset(cell.dx(), cell.dy(), cell.dz());
            BlockState old = level.getBlockState(at);
            if (!old.isAir() && !(old.getBlock() instanceof LiquidBlock)) {
                stash(Block.getDrops(old, level, at, level.getBlockEntity(at)));
                level.levelEvent(2001, at, Block.getId(old));
            }
            if (step.action() == AutoBuild.Action.CLEAR) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return;
            }
            Pick pick = pick(cell.role());
            if (pick == null) {
                return;
            }
            take(pick);
            BlockState state = placed(pick.block, cell.role(), at);
            level.setBlock(at, state, Block.UPDATE_ALL);
            if (!HearthDesigns.isFluid(cell.role())) {
                tag.putString(USE + cell.role(), BuiltInRegistries.BLOCK.getKey(pick.block).toString());
            }
            fx(at, state);
        }

        // ---- Material ----

        private record Pick(Container container, int slot, Block block) {
        }

        /** The block for a role: the player's choice, else the design's own, else the most plentiful. */
        @Nullable
        private Pick pick(char role) {
            if (picks.containsKey(role)) {
                return picks.get(role);
            }
            Pick pick = find(role);
            picks.put(role, pick);
            return pick;
        }

        /** One block that fits a role: its count in all sources, its first slot and the count per place. */
        static final class Stock {
            final Block block;
            int count;
            Pick first;
            final Map<BlockPos, Integer> places = new java.util.LinkedHashMap<>();
            int pack;

            Stock(Block block) {
                this.block = block;
            }
        }

        /** Every block in the sources that fits {@code role} (buckets for water and lava), most first. */
        List<Stock> stock(char role) {
            Map<Block, Stock> out = new java.util.LinkedHashMap<>();
            Item bucket = role == 'v' ? Items.WATER_BUCKET : role == 'V' ? Items.LAVA_BUCKET : null;
            var roleTag = bucket == null ? StageBlocks.roleTag(role) : null;
            if (bucket == null && roleTag == null) {
                return List.of();
            }
            for (Container container : sources) {
                for (int i = 0; i < slots(container); i++) {
                    ItemStack stack = container.getItem(i);
                    Block block;
                    if (bucket != null) {
                        if (!stack.is(bucket)) {
                            continue;
                        }
                        block = role == 'v' ? Blocks.WATER : Blocks.LAVA;
                    } else {
                        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)
                                || !item.getBlock().defaultBlockState().is(roleTag)) {
                            continue;
                        }
                        block = item.getBlock();
                    }
                    Stock entry = out.computeIfAbsent(block, Stock::new);
                    entry.count += stack.getCount();
                    if (entry.first == null) {
                        entry.first = new Pick(container, i, block);
                    }
                    BlockPos at = where.get(container);
                    if (at == null) {
                        entry.pack += stack.getCount();
                    } else {
                        entry.places.merge(at, stack.getCount(), Integer::sum);
                    }
                }
            }
            List<Stock> sorted = new ArrayList<>(out.values());
            sorted.sort((a, b) -> Integer.compare(b.count, a.count));
            return sorted;
        }

        /** The block the player chose for a role, if it is a real block that fits it. */
        @Nullable
        Block chosen(char role) {
            Block block = blockOf(tag.getString(USE + role));
            if (block == null || HearthDesigns.isFluid(role)) {
                return null;
            }
            var roleTag = StageBlocks.roleTag(role);
            return roleTag != null && block.defaultBlockState().is(roleTag) ? block : null;
        }

        /** With no choice: the design's own block when the sources hold it, else the one they hold most of. */
        @Nullable
        Block fallback(char role, List<Stock> stock) {
            if (stock.isEmpty()) {
                return null;
            }
            Block own = blockOf(lane.block(role));
            for (Stock entry : stock) {
                if (entry.block == own) {
                    return own;
                }
            }
            return stock.get(0).block;
        }

        /** A chosen block is binding: when it runs out the build waits, it never takes another kind. */
        @Nullable
        private Pick find(char role) {
            List<Stock> stock = stock(role);
            if (HearthDesigns.isFluid(role)) {
                return stock.isEmpty() ? null : stock.get(0).first;
            }
            Block want = chosen(role);
            if (want == null) {
                want = fallback(role, stock);
            }
            for (Stock entry : stock) {
                if (entry.block == want) {
                    return entry.first;
                }
            }
            return null;
        }

        /** Every role the stage still needs without a choice gets the fallback, so the plan cannot change later. */
        void lockChoices(List<StageGhost.Cell> diff) {
            for (char role : BuildPlan.needs(diff).keySet()) {
                if (HearthDesigns.isFluid(role) || chosen(role) != null) {
                    continue;
                }
                Block block = fallback(role, stock(role));
                if (block != null) {
                    tag.putString(USE + role, BuiltInRegistries.BLOCK.getKey(block).toString());
                }
            }
        }

        /** A pack gives only its 36 main slots, never what the player wears or holds in the off hand. */
        private static int slots(Container container) {
            return container instanceof Inventory ? Inventory.INVENTORY_SIZE : container.getContainerSize();
        }

        private void take(Pick pick) {
            ItemStack stack = pick.container.getItem(pick.slot);
            if (stack.is(Items.WATER_BUCKET) || stack.is(Items.LAVA_BUCKET)) {
                if (stack.getCount() == 1) {
                    pick.container.setItem(pick.slot, new ItemStack(Items.BUCKET));
                } else {
                    stack.shrink(1);
                    stash(List.of(new ItemStack(Items.BUCKET)));
                }
            } else {
                stack.shrink(1);
                if (stack.isEmpty()) {
                    pick.container.setItem(pick.slot, ItemStack.EMPTY);
                }
            }
            pick.container.setChanged();
            picks.clear();
        }

        /** Drops and empty buckets go into the nearest home chest with room, else onto the fire's step. */
        private void stash(List<ItemStack> items) {
            for (ItemStack item : items) {
                ItemStack rest = item.copy();
                for (Container container : sources) {
                    if (container instanceof Inventory) {
                        continue;
                    }
                    rest = insert(container, rest);
                    if (rest.isEmpty()) {
                        break;
                    }
                }
                if (!rest.isEmpty()) {
                    Containers.dropItemStack(level, fire.getX() + 0.5, fire.getY() + 1.0, fire.getZ() + 0.5, rest);
                }
            }
        }

        private static ItemStack insert(Container container, ItemStack stack) {
            for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
                for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
                    if (!container.canPlaceItem(i, stack)) {
                        continue;
                    }
                    ItemStack there = container.getItem(i);
                    int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
                    if (pass == 0 && !there.isEmpty() && ItemStack.isSameItem(there, stack)
                            && ItemStack.matches(there.copyWithCount(1), stack.copyWithCount(1)) && there.getCount() < limit) {
                        int moved = Math.min(limit - there.getCount(), stack.getCount());
                        there.grow(moved);
                        stack.shrink(moved);
                        container.setChanged();
                    } else if (pass == 1 && there.isEmpty()) {
                        int moved = Math.min(limit, stack.getCount());
                        container.setItem(i, stack.copyWithCount(moved));
                        stack.shrink(moved);
                        container.setChanged();
                    }
                }
            }
            return stack;
        }

        // ---- Placing ----

        /** The block as the Hearth sets it: stairs climb toward the fire, leaves never decay, a lantern hangs if it must. */
        private BlockState placed(Block block, char role, BlockPos at) {
            BlockState state = block.defaultBlockState();
            int dx = at.getX() - fire.getX();
            int dz = at.getZ() - fire.getZ();
            if (block instanceof StairBlock) {
                Direction toFire = Math.abs(dx) >= Math.abs(dz)
                        ? (dx > 0 ? Direction.WEST : dx < 0 ? Direction.EAST : Direction.NORTH)
                        : (dz > 0 ? Direction.NORTH : Direction.SOUTH);
                state = state.setValue(StairBlock.FACING, toFire);
            }
            if (state.hasProperty(LeavesBlock.PERSISTENT)) {
                state = state.setValue(LeavesBlock.PERSISTENT, true);
            }
            if (state.hasProperty(LanternBlock.HANGING) && !state.canSurvive(level, at)) {
                state = state.setValue(LanternBlock.HANGING, true);
            }
            return HearthDesigns.isFluid(role) ? state : Block.updateFromNeighbourShapes(state, level, at);
        }

        /** A spark trail from the fire to the block, a shimmer on it and the block's own place sound. */
        private void fx(BlockPos at, BlockState state) {
            double fx = fire.getX() + 0.5;
            double fy = fire.getY() + 1.2;
            double fz = fire.getZ() + 0.5;
            double tx = at.getX() + 0.5;
            double ty = at.getY() + 0.5;
            double tz = at.getZ() + 0.5;
            for (int i = 1; i <= 8; i++) {
                double t = i / 9.0;
                level.sendParticles(ParticleTypes.END_ROD, fx + (tx - fx) * t, fy + (ty - fy) * t, fz + (tz - fz) * t,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
            level.sendParticles(ParticleTypes.ENCHANT, tx, ty, tz, 14, 0.4, 0.4, 0.4, 0.6);
            var sound = state.getSoundType();
            level.playSound(null, at, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1.0f) / 2.0f,
                    sound.getPitch() * 0.8f);
            level.playSound(null, fire, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.4f);
        }

        // ---- Sources and terrain ----

        /**
         * The players who call this Hearth home and stand in its range (their packs first), then every container
         * in its range, nearest first.
         */
        private static List<Container> sources(ServerLevel level, BlockPos fire, Map<Container, BlockPos> where) {
            List<Container> out = new ArrayList<>();
            for (ServerPlayer player : HearthPresence.inRange(level, fire)) {
                if (fire.equals(HearthRange.homeOf(player)) && !player.isSpectator()) {
                    out.add(player.getInventory());
                }
            }
            int radius = (int) Math.ceil(HearthRange.radius(level, fire));
            List<BlockEntity> found = new ArrayList<>();
            for (int cx = (fire.getX() - radius) >> 4; cx <= (fire.getX() + radius) >> 4; cx++) {
                for (int cz = (fire.getZ() - radius) >> 4; cz <= (fire.getZ() + radius) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        if (storage(entity) && HearthRange.inRange(level, fire, entity.getBlockPos())) {
                            found.add(entity);
                        }
                    }
                }
            }
            found.sort((a, b) -> Double.compare(a.getBlockPos().distSqr(fire), b.getBlockPos().distSqr(fire)));
            for (BlockEntity entity : found) {
                out.add((Container) entity);
                where.put((Container) entity, entity.getBlockPos());
            }
            return out;
        }

        /** Chests, barrels and shulker boxes (modded chests extend the chest): never a furnace's or a hopper's slots. */
        private static boolean storage(BlockEntity entity) {
            return entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity
                    || entity instanceof ShulkerBoxBlockEntity;
        }

        /** Natural ground the Hearth may dig out of its way. */
        private static boolean terrain(BlockState state) {
            if (state.is(BlockTags.LEAVES)) {
                return !state.hasProperty(LeavesBlock.PERSISTENT) || !state.getValue(LeavesBlock.PERSISTENT);
            }
            return state.is(BlockTags.DIRT) || state.is(BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(BlockTags.BASE_STONE_NETHER) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                    || state.is(Blocks.CLAY) || state.is(BlockTags.SNOW) || state.is(BlockTags.FLOWERS)
                    || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SNOW_BLOCK);
        }
    }
}
