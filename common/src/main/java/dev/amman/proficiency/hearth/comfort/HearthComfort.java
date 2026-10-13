package dev.amman.proficiency.hearth.comfort;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthIndexStore;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.stages.HearthPresence;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.EntityJoinLevelEvent;
import dev.amman.proficiency.platform.event.entity.EntityLeaveLevelEvent;
import dev.amman.proficiency.platform.event.level.ChunkLoadEvent;
import dev.amman.proficiency.platform.event.level.ExplosionDetonateEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.SkillTools;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * The comfort lane (docs/HEARTH-CONTRACT.md, lane "comfort"): the Comfort and Character scan of every
 * Hearth, and the mark that makes it count placed blocks only.
 *
 * <ul>
 * <li><b>The mark.</b> Every block a player places gets a comfort mark whatever
 * {@code placedBlocksPayXp} says ({@link PlacedBlocks#markComfort}); a break clears it (and the far
 * half of a door or bed); pistons and falling blocks carry it; a mark whose block is gone is dropped by
 * the next scan of a Hearth that covers it, or when its chunk next loads ({@link PlacedBlocks#sweepComfort}).</li>
 * <li><b>The scan</b> ({@link ComfortScanner}) runs from the Hearth's second, never per tick: once for a
 * Hearth that never scanned; after a change in range (a placement, a break, an explosion) at most
 * every {@code comfort.scanCooldownSeconds} (30); and every {@code comfort.rescanSeconds} (300) while
 * someone is in range ({@link ScanPolicy}).</li>
 * <li><b>The cache.</b> The result is stored in the Hearth's state: {@link HearthState#comfort},
 * {@link HearthState#comfortKinds}, {@link HearthState#character}. A change goes to the players in range
 * as a {@code hearth_state} {@code UPDATE}; the charge and effects lanes read the state.</li>
 * <li><b>The milestones.</b> The first time a Comfort kind or a Character entry is seen at a Hearth,
 * {@link HearthEvents#COMFORT_KIND_SEEN} or {@link HearthEvents#CHARACTER_ID_SEEN} fires with the player
 * who placed it when this lane saw the placement (else null).</li>
 * </ul>
 *
 * <p>Called once from {@code Hearth.init}.
 */
public final class HearthComfort {

    private HearthComfort() {
    }

    public static void init() {
        HearthHooks.BLOCK_PLACED.add(HearthComfort::onPlaced);
        HearthHooks.BLOCK_BROKEN.add(HearthComfort::onBroken);
        HearthHooks.HEARTH_TICK.add(HearthComfort::onSecond);
        NeoForge.EVENT_BUS.addListener(ExplosionDetonateEvent.class, HearthComfort::onExplosion);
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, HearthComfort::onEntityJoin);
        NeoForge.EVENT_BUS.addListener(EntityLeaveLevelEvent.class, HearthComfort::onEntityLeave);
        NeoForge.EVENT_BUS.addListener(ChunkLoadEvent.class, HearthComfort::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> ComfortRequests.clear());
        NeoForge.EVENT_BUS.register(ComfortCommand.class);
    }

    // ---- API ------------------------------------------------------------------------------------------

    /**
     * Asks for a rescan of the Hearth at {@code hearthPos}: it happens on the Hearth's next second, once
     * the 30 s cooldown since its last scan is over.
     */
    public static void requestScan(ServerLevel level, BlockPos hearthPos) {
        ComfortRequests.touch(HearthBlockEntity.key(level, hearthPos));
    }

    /** The Hearth at {@code pos} is gone (broken, replaced): its pending flags and placer notes go with it. */
    public static void hearthGone(ServerLevel level, BlockPos pos) {
        ComfortRequests.drop(HearthBlockEntity.key(level, pos));
    }

    /**
     * Scans this Hearth now, ignoring the clock, and stores the result. Returns false (and changes
     * nothing) when a chunk of its range is not loaded.
     */
    public static boolean scanNow(ServerLevel level, HearthBlockEntity hearth) {
        BlockPos pos = hearth.getBlockPos();
        ComfortTally.Result result = ComfortScanner.scan(level, pos);
        if (result == null) {
            return false;
        }
        apply(level, pos, hearth, result);
        return true;
    }

    /** The kinds the data lists that this Hearth's last scan did not find, in the panel's order. */
    public static List<String> missingKinds(HearthState state) {
        return ComfortKinds.missing(state.comfortKinds(), Hearths.data().comfort().kinds());
    }

    // ---- The mark and the requests -----------------------------------------------------------------------

    private static void onPlaced(ServerLevel level, BlockPos pos, BlockState placed, @Nullable Entity placer) {
        ServerPlayer player = placer instanceof ServerPlayer p ? p : null;
        if (player != null) {
            PlacedBlocks.markComfort(level, pos, placed);
        }
        BlockPos home = HearthRange.houseAt(level, pos);
        if (home == null) {
            return;
        }
        ComfortRequests.Request request = ComfortRequests.touch(HearthBlockEntity.key(level, home));
        HearthBlockEntity hearth = HearthRange.entity(level, home);
        if (player != null && hearth != null && !SkillTools.isFakePlayer(player)) {
            // Only what this Hearth has not seen yet can still become a milestone worth a name.
            HearthState state = hearth.state();
            ComfortScanner.Worth worth = ComfortScanner.worthOf(placed);
            UUID id = player.getUUID();
            for (String kind : worth.kinds()) {
                if (!state.comfortSeen().contains(kind)) {
                    request.notePlacer(ComfortScanner.KIND_NOTE + kind, pos.asLong(), id);
                }
            }
            if (worth.entry() != null && !state.characterSeen().contains(worth.entry())) {
                request.notePlacer(ComfortScanner.ID_NOTE + worth.entry(), pos.asLong(), id);
            }
        }
    }

    private static void onBroken(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        PlacedBlocks.clearComfortPlacement(level, pos, state);
        BlockPos home = HearthRange.houseAt(level, pos);
        if (home != null) {
            // Whoever placed what stood here is not credited for what is placed here next.
            ComfortRequests.touch(HearthBlockEntity.key(level, home)).forgetAt(pos.asLong());
        }
    }

    /** A painting, an item frame or an armour stand was put up (not loaded from a chunk): it may be new Comfort. */
    private static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || !ProficiencyConfig.hearthEnabled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof HangingEntity) && !(entity instanceof ArmorStand)) {
            return;
        }
        BlockPos home = HearthRange.houseAt(level, entity.blockPosition());
        if (home != null) {
            ComfortRequests.touch(HearthBlockEntity.key(level, home));
        }
    }

    /**
     * A painting, a frame or an armour stand was taken down (killed, not saved away with its chunk): the
     * Comfort it was worth may be gone. Emptying a frame or undressing a stand reports nothing; the
     * 5-minute rescan finds those.
     */
    private static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ProficiencyConfig.hearthEnabled()) {
            return;
        }
        Entity entity = event.getEntity();
        if (!(entity instanceof HangingEntity) && !(entity instanceof ArmorStand)) {
            return;
        }
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason == null || reason.shouldSave()) {
            return;
        }
        BlockPos home = HearthRange.houseAt(level, entity.blockPosition());
        if (home != null) {
            ComfortRequests.touch(HearthBlockEntity.key(level, home));
        }
    }

    /** A chunk loaded: the comfort marks of blocks that were destroyed while it was not watched are dropped. */
    private static void onChunkLoad(ChunkLoadEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk
                && ProficiencyConfig.hearthEnabled()) {
            PlacedBlocks.sweepComfort(level, chunk);
        }
    }

    private static void onExplosion(ExplosionDetonateEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ProficiencyConfig.hearthEnabled()) {
            return;
        }
        HearthIndexStore index = HearthIndexData.get(level).store();
        if (index.size() == 0) {
            return;
        }
        LongOpenHashSet hit = new LongOpenHashSet();
        for (BlockPos blown : event.getAffectedBlocks()) {
            long home = index.houseAt(blown.getX(), blown.getY(), blown.getZ(), HearthRange.range());
            if (home != HearthIndexStore.NONE && hit.add(home)) {
                ComfortRequests.touch(HearthBlockEntity.key(level, BlockPos.of(home)));
            }
        }
        // Armour stands and frames die to the blast without being listed: the blast's own spot counts too.
        var center = event.getExplosion().center();
        long home = index.houseAt((int) Math.floor(center.x), (int) Math.floor(center.y), (int) Math.floor(center.z),
                HearthRange.range());
        if (home != HearthIndexStore.NONE && hit.add(home)) {
            ComfortRequests.touch(HearthBlockEntity.key(level, BlockPos.of(home)));
        }
    }

    // ---- The Hearth's second ------------------------------------------------------------------------------

    private static void onSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        String key = hearth.key();
        long now = level.getGameTime();
        if (ComfortRequests.deferred(key, now)) {
            return;
        }
        HearthState state = hearth.state();
        HearthData.Comfort config = Hearths.data().comfort();
        boolean changed = ComfortRequests.dirty(key);
        boolean due = ScanPolicy.due(state.lastScan(), now, changed, false, config)
                || (ScanPolicy.due(state.lastScan(), now, false, true, config) && !HearthPresence.inRange(level, pos).isEmpty());
        if (due && !scanNow(level, hearth)) {
            // A chunk of the range is not loaded: not again before the cooldown is over, or the scanner
            // would be built and dropped every second for as long as the chunk stays away.
            ComfortRequests.defer(key, now + Math.max(20L, ScanPolicy.ticks(config.scanCooldownSeconds())));
        }
    }

    // ---- Storing a result ----------------------------------------------------------------------------------

    private static void apply(ServerLevel level, BlockPos pos, HearthBlockEntity hearth, ComfortTally.Result result) {
        HearthState state = hearth.state();
        String key = hearth.key();
        ComfortRequests.Request request = ComfortRequests.get(key);
        boolean scannedBefore = state.lastScan() != HearthState.NEVER;
        List<String> kindsBefore = new ArrayList<>(state.comfortKinds());
        int characterBefore = state.character();
        state.setComfortKinds(new LinkedHashSet<>(result.kinds()));
        state.setCharacter(result.character());
        state.setLastScan(level.getGameTime());
        boolean changed = !kindsBefore.equals(result.kinds()) || characterBefore != result.character();

        List<String> newKinds = new ArrayList<>();
        for (String kind : result.kinds()) {
            if (state.markComfortSeen(kind)) {
                newKinds.add(kind);
            }
        }
        List<String> newEntries = new ArrayList<>();
        for (String entry : result.entries()) {
            if (state.markCharacterSeen(entry)) {
                newEntries.add(entry);
            }
        }
        hearth.setChanged();

        if (changed) {
            List<ServerPlayer> home = HearthPresence.inRange(level, pos);
            HearthStatePayload payload = HearthNet.stateOf(hearth, HearthStatePayload.UPDATE);
            for (ServerPlayer player : home) {
                HearthNet.sendState(player, payload);
            }
            // The first scan of a new Hearth is silent; after that a kind that starts to count gets its moment.
            List<String> gained = new ArrayList<>(result.kinds());
            gained.removeAll(kindsBefore);
            if (scannedBefore && !gained.isEmpty()) {
                ComfortCue.play(level, pos, home, result.comfort(), Hearths.data().comfort().max(), gained,
                        kindsBefore.size() < Hearths.data().comfort().max());
            }
        }
        for (String kind : newKinds) {
            String note = ComfortScanner.KIND_NOTE + kind;
            UUID placer = placerOf(level, pos, request, note);
            HearthEvents.COMFORT_KIND_SEEN.fire(new HearthEvents.ComfortKindSeen(level, pos, placer, kind));
            if (request != null) {
                request.forget(note);
            }
        }
        for (String entry : newEntries) {
            String note = ComfortScanner.ID_NOTE + entry;
            UUID placer = placerOf(level, pos, request, note);
            HearthEvents.CHARACTER_ID_SEEN.fire(new HearthEvents.CharacterIdSeen(level, pos, placer, entry));
            if (request != null) {
                request.forget(note);
            }
        }
        ComfortRequests.scanned(key);
        if (result.pending()) {
            ComfortRequests.watch(key, level.getGameTime());
        } else {
            ComfortRequests.unwatch(key);
        }
    }

    /**
     * Who placed what makes {@code note} count: the oldest placer of a block that still stands, still
     * counts for it (loaded, worth the note, indoors for a kind, not the Hearth's own structure). A note
     * of a block that is gone, or that never counted, credits nobody.
     */
    @Nullable
    private static UUID placerOf(ServerLevel level, BlockPos hearth, @Nullable ComfortRequests.Request request,
            String note) {
        if (request == null) {
            return null;
        }
        for (ComfortRequests.Note candidate : request.notes(note)) {
            if (ComfortScanner.stillCounts(level, hearth, BlockPos.of(candidate.pos()), note)) {
                return candidate.player();
            }
        }
        return null;
    }
}
