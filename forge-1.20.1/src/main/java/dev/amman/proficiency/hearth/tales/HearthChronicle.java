package dev.amman.proficiency.hearth.tales;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.ChronicleBook;
import dev.amman.proficiency.hearth.ChronicleText;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthKey;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.TalesMath;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Hearth Chronicle (docs/HEARTH-SPEC.md, "Telling the tale"): a writable (or signed) book on a
 * lectern in a Hearth's range. A page the Chronicle has not seen is filed: a line in
 * {@code <world>/proficiency/chronicle.jsonl} (page id, author uuid and name, game time, real time, the
 * Tales it went with, the text) and an entry in the world's {@link ChronicleBook}. The first page after
 * a homecoming also pays the flat bonus ({@code chronicle.writeBonus} of what the homecoming paid), once
 * per homecoming. {@code /mcjournal} rates the pages later and {@link #rate} pays the burst.
 *
 * <p>The lectern is found when someone uses it ({@code BLOCK_USE}); its book is read once a second
 * on the Hearth's tick, only when the book changed. What a lectern held before it was registered is
 * remembered as already filed, so an old shelf is never mistaken for new writing.
 */
public final class HearthChronicle {

    static final String LANE = "tales";
    private static final String LECTERNS = "lecterns";
    private static final String FILED = "filed";
    private static final String WINDOW_START = "windowStart";
    private static final String WINDOW_COUNT = "windowCount";

    static final int MAX_LECTERNS = 8;
    static final int MAX_FILED = 512;
    /** Pages one Hearth files per game hour: a book pasted full of pages is not a flood. */
    static final int PAGES_PER_HOUR = 12;
    /** How long a lectern's last user counts as its writer. */
    static final long TOUCH_TICKS = 12_000L;

    public static final String FILE_DIR = "proficiency";
    public static final String FILE = "chronicle.jsonl";
    public static final String QUEUE = "chronicle-ratings.jsonl";

    private record Touch(UUID player, long tick) {
    }

    private static final Map<String, Touch> TOUCH = new ConcurrentHashMap<>();
    private static final Map<String, Integer> SEEN = new ConcurrentHashMap<>();
    /**
     * Lectern to the game tick before which its book is not read again: a book with new pages that cannot be
     * filed yet (nobody home to write them, or the hour's pages are used up) would otherwise be hashed and its
     * pages cleaned and hashed every Hearth second. A use of the lectern clears it.
     */
    private static final Map<String, Long> RETRY_AT = new ConcurrentHashMap<>();
    /** How long new pages wait for someone to be home before the book is looked at again (10 s). */
    static final long NO_AUTHOR_RETRY_TICKS = 200L;

    private HearthChronicle() {
    }

    // ---- Finding the lectern ----------------------------------------------------------------------

    private static String keyOf(ServerLevel level, BlockPos pos) {
        return HearthBlockEntity.key(level, pos);
    }

    /** A right click on a lectern: it belongs to the Hearth whose range holds it, and the player is its writer for a while. */
    public static void onLecternUse(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        HearthBlockEntity hearth = HearthRange.houseEntityAt(level, pos);
        if (hearth == null) {
            return;
        }
        register(level, hearth, pos, state);
        TOUCH.put(keyOf(level, pos), new Touch(player.getUUID(), level.getGameTime()));
        RETRY_AT.remove(keyOf(level, pos));
    }

    /** Registers a lectern with its Hearth; an old book already on it is remembered as filed. Returns whether it was new. */
    public static boolean register(ServerLevel level, HearthBlockEntity hearth, BlockPos pos, BlockState state) {
        CompoundTag lane = hearth.laneData(LANE);
        long[] known = lane.getLongArray(LECTERNS);
        long packed = pos.asLong();
        for (long k : known) {
            if (k == packed) {
                return false;
            }
        }
        if (known.length >= MAX_LECTERNS) {
            return false;
        }
        long[] more = java.util.Arrays.copyOf(known, known.length + 1);
        more[known.length] = packed;
        lane.putLongArray(LECTERNS, more);
        if (state.hasProperty(LecternBlock.HAS_BOOK) && state.getValue(LecternBlock.HAS_BOOK)
                && level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) {
            ItemStack book = lectern.getBook();
            addFiled(lane, ChronicleText.hashes(pagesOf(book)));
            SEEN.put(keyOf(level, pos), signature(book));
        }
        hearth.setChanged();
        return true;
    }

    // ---- Reading a book ---------------------------------------------------------------------------

    /**
     * The pages of a writable or signed book as plain text; empty for anything else. 1.20.1 keeps them in
     * the stack's NBT ({@code pages}): plain strings in a book and quill, JSON text components in a signed
     * book (1.21.1's {@code writable_book_content} and {@code written_book_content}, unfiltered).
     */
    static List<String> pagesOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("pages", Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = tag.getList("pages", Tag.TAG_STRING);
        List<String> pages = new ArrayList<>(list.size());
        if (stack.is(Items.WRITABLE_BOOK)) {
            for (int i = 0; i < list.size(); i++) {
                pages.add(list.getString(i));
            }
            return pages;
        }
        if (stack.is(Items.WRITTEN_BOOK)) {
            for (int i = 0; i < list.size(); i++) {
                pages.add(pageText(list.getString(i)));
            }
            return pages;
        }
        return List.of();
    }

    /** A signed book's page: a JSON text component (a page that is not one reads as it is written). */
    private static String pageText(String json) {
        try {
            Component page = Component.Serializer.fromJson(json);
            return page == null ? "" : page.getString();
        } catch (RuntimeException e) {
            return json;
        }
    }

    /** A signed book's title, "" for a book and quill. */
    static String titleOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return !stack.is(Items.WRITTEN_BOOK) || tag == null ? "" : ChronicleText.clean(tag.getString("title"));
    }

    /** What the book holds now, as one number (1.21.1's {@code ItemStack.hashItemAndComponents}). */
    static int signature(ItemStack stack) {
        return 31 * stack.getItem().hashCode() + java.util.Objects.hashCode(stack.getTag());
    }

    private static Set<Long> filed(CompoundTag lane) {
        Set<Long> out = new LinkedHashSet<>();
        for (long hash : lane.getLongArray(FILED)) {
            out.add(hash);
        }
        return out;
    }

    private static void addFiled(CompoundTag lane, Set<Long> more) {
        Set<Long> all = filed(lane);
        all.addAll(more);
        long[] array = all.stream().mapToLong(Long::longValue).toArray();
        if (array.length > MAX_FILED) {
            array = java.util.Arrays.copyOfRange(array, array.length - MAX_FILED, array.length);
        }
        lane.putLongArray(FILED, array);
    }

    // ---- Filing new pages -------------------------------------------------------------------------

    /** The Hearth's second: read every registered lectern whose book changed. */
    static void onHearthSecond(ServerLevel level, BlockPos hearthPos, HearthBlockEntity hearth) {
        CompoundTag lane = hearth.laneData(LANE);
        long[] known = lane.getLongArray(LECTERNS);
        if (known.length == 0) {
            return;
        }
        List<Long> keep = new ArrayList<>(known.length);
        boolean dropped = false;
        for (long packed : known) {
            BlockPos lecternPos = BlockPos.of(packed);
            if (!level.isLoaded(lecternPos)) {
                keep.add(packed);
                continue;
            }
            if (!level.getBlockState(lecternPos).is(Blocks.LECTERN) || !HearthRange.inHouse(hearthPos, lecternPos)) {
                dropped = true;
                SEEN.remove(keyOf(level, lecternPos));
                TOUCH.remove(keyOf(level, lecternPos));
                RETRY_AT.remove(keyOf(level, lecternPos));
                continue;
            }
            keep.add(packed);
            scan(level, hearth, lecternPos);
        }
        if (dropped) {
            lane.putLongArray(LECTERNS, keep.stream().mapToLong(Long::longValue).toArray());
            hearth.setChanged();
        }
    }

    /** Reads one lectern's book and files what is new. Public to the lane so a test can drive it. */
    public static int scan(ServerLevel level, HearthBlockEntity hearth, BlockPos lecternPos) {
        if (!(level.getBlockEntity(lecternPos) instanceof LecternBlockEntity lectern) || !lectern.hasBook()) {
            return 0;
        }
        String key = keyOf(level, lecternPos);
        long now = level.getGameTime();
        Long retry = RETRY_AT.get(key);
        if (retry != null && now < retry) {
            return 0;
        }
        ItemStack book = lectern.getBook();
        int signature = signature(book);
        Integer last = SEEN.get(key);
        if (last != null && last == signature) {
            return 0;
        }
        CompoundTag lane = hearth.laneData(LANE);
        List<ChronicleText.Fresh> fresh = ChronicleText.fresh(filed(lane), pagesOf(book));
        if (fresh.isEmpty()) {
            SEEN.put(key, signature);
            return 0;
        }
        ServerPlayer author = authorFor(level, hearth, lecternPos);
        if (author == null) {
            RETRY_AT.put(key, now + NO_AUTHOR_RETRY_TICKS);
            return 0;
        }
        long start = lane.contains(WINDOW_START) ? lane.getLong(WINDOW_START) : Long.MIN_VALUE;
        int count = lane.getInt(WINDOW_COUNT);
        if (start == Long.MIN_VALUE || now < start || now - start >= HearthData.TICKS_PER_HOUR) {
            start = now;
            count = 0;
        }
        int room = Math.max(0, PAGES_PER_HOUR - count);
        if (room == 0) {
            // The hour's pages are used up: nothing new can be filed before the window turns over.
            RETRY_AT.put(key, start + HearthData.TICKS_PER_HOUR);
            return 0;
        }
        RETRY_AT.remove(key);
        boolean all = fresh.size() <= room;
        List<ChronicleText.Fresh> take = all ? fresh : fresh.subList(0, room);
        lane.putLong(WINDOW_START, start);
        lane.putInt(WINDOW_COUNT, count + take.size());
        file(level, hearth, author, lecternPos, titleOf(book), take);
        if (all) {
            SEEN.put(key, signature);
        }
        return take.size();
    }

    /** The lectern's writer: its last user while they are still here, else the one player at home (or the nearest). */
    private static ServerPlayer authorFor(ServerLevel level, HearthBlockEntity hearth, BlockPos lecternPos) {
        MinecraftServer server = level.getServer();
        BlockPos hearthPos = hearth.getBlockPos();
        Touch touch = TOUCH.get(keyOf(level, lecternPos));
        if (touch != null && level.getGameTime() - touch.tick() <= TOUCH_TICKS) {
            ServerPlayer toucher = server.getPlayerList().getPlayer(touch.player());
            if (toucher != null && toucher.level() == level && hearthPos.equals(HearthRange.homeOf(toucher))) {
                return toucher;
            }
        }
        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer player : level.players()) {
            if (!hearthPos.equals(HearthRange.homeOf(player))) {
                continue;
            }
            double distance = player.distanceToSqr(lecternPos.getX() + 0.5, lecternPos.getY(), lecternPos.getZ() + 0.5);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * Files pages written by {@code author}: the register, the jsonl, the flat bonus on the first one
     * (the Tales of the last homecoming, once), the effects. Returns the first page's id.
     */
    public static long file(ServerLevel level, HearthBlockEntity hearth, ServerPlayer author, BlockPos lecternPos,
            String bookTitle, List<ChronicleText.Fresh> pages) {
        MinecraftServer server = level.getServer();
        HearthData data = Hearths.data();
        HearthWorldData world = HearthWorldData.get(server);
        PlayerHearth player = HearthAttachments.of(author);
        double base = player.takeChronicleBase();
        long firstId = -1L;
        Set<Long> hashes = new LinkedHashSet<>();
        for (int i = 0; i < pages.size(); i++) {
            ChronicleText.Fresh fresh = pages.get(i);
            String title = bookTitle.isEmpty() ? ChronicleText.title(fresh.text()) : bookTitle;
            double tales = i == 0 ? base : 0.0;
            ChronicleBook.Page page = world.chronicle().write(author.getUUID(), HearthTales.name(author),
                    hearth.key(), tales, level.getGameTime(), title);
            if (firstId < 0) {
                firstId = page.id();
            }
            append(server, ChronicleText.jsonLine(page.id(), page.author(), page.name(), page.hearth(), page.gameTime(),
                    System.currentTimeMillis(), page.tales(), page.title(), fresh.text()));
            hashes.add(fresh.hash());
        }
        addFiled(hearth.laneData(LANE), hashes);
        world.setDirty();
        hearth.setChanged();
        TalesFx.page(author, lecternPos);
        double bonus = TalesMath.writeBonus(base, data);
        if (bonus > 0.0) {
            double total = burst(level, hearth, author.getUUID(), HearthTales.name(author), author, bonus, data);
            author.displayClientMessage(TalesText.chronicleFiled(total, TalesText.poolName(data.talesPool())), true);
            HearthTales.send(author, new HearthTalesPayload(HearthTalesPayload.CHRONICLE, data.talesPool().ordinal(),
                    (float) total, 0, -1, List.of(), ChronicleText.title(pages.get(0).text())));
            HearthNet.markHudDirty(author);
            HearthEvents.CHRONICLE_WRITTEN.fire(new HearthEvents.ChronicleWritten(level, hearth.getBlockPos(), author,
                    firstId, base));
        } else {
            author.displayClientMessage(TalesText.chronicleFiled(0.0, TalesText.poolName(data.talesPool())), true);
        }
        return firstId;
    }

    /**
     * Adds a Chronicle burst (the write bonus or a rated tale) outside the hourly cap, through
     * {@code HearthCharge.credit} (ledger source chronicle, telemetry, tracker dots, tier watch). Returns the total.
     */
    private static double burst(ServerLevel level, HearthBlockEntity hearth, UUID who, String name, ServerPlayer online,
            double amount, HearthData data) {
        double total = 0.0;
        for (double in : TalesMath.burst(amount, data, (pool, share) -> online != null
                ? HearthCharge.credit(level, hearth, online, pool, HearthLedger.CHRONICLE, share, false, null)
                : HearthCharge.credit(level, hearth, who, name, pool, HearthLedger.CHRONICLE, share, false, null, null))
                .values()) {
            total += in;
        }
        return total;
    }

    // ---- The file ---------------------------------------------------------------------------------

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_DIR).resolve(FILE);
    }

    public static Path queue(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_DIR).resolve(QUEUE);
    }

    private static void append(MinecraftServer server, String line) {
        Path file = file(server);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            Proficiency.LOG.error("Hearth Chronicle: could not append to {}", file, e);
        }
    }

    // ---- Rating -----------------------------------------------------------------------------------

    /** {@code DEFERRED}: the Hearth's chunk is not loaded and this batch has used up its loads; nothing was recorded. */
    public enum Status { OK, UNKNOWN, ALREADY, DEFERRED }

    /** What a rating did: its status, the page, the charge paid and whether the Hearth was there to take it. */
    public record Rated(Status status, ChronicleBook.Page page, double paid, boolean hearthFound) {
    }

    /** How many unloaded Hearth chunks one batch of queued ratings may load on the server thread. */
    static final class LoadBudget {
        private int left;

        LoadBudget(int left) {
            this.left = left;
        }

        boolean take() {
            if (left <= 0) {
                return false;
            }
            left--;
            return true;
        }
    }

    /** A page's Hearth: the block entity, nothing when it is gone, or "not now" when it is unloaded and the budget is spent. */
    private record Lookup(HearthBlockEntity hearth, boolean defer) {
        static final Lookup DEFER = new Lookup(null, true);
        static final Lookup GONE = new Lookup(null, false);
    }

    /**
     * Rates a page once and pays {@code stars x perStar} of its Tales into its Hearth, outside the
     * hourly cap. A page that is unknown or already rated pays nothing and says so. The author is told
     * at once when online, else at their next login. This is the command's path: a Hearth whose chunk is
     * not loaded is loaded for the one rating.
     */
    public static Rated rate(MinecraftServer server, long id, int stars) {
        return rate(server, id, stars, null);
    }

    /**
     * {@link #rate(MinecraftServer, long, int)} with a budget of chunk loads ({@code null} is unlimited): out
     * of budget, a page whose Hearth is not loaded is {@link Status#DEFERRED} and left exactly as it was, so the
     * rating can be applied later. The Hearth is looked up and paid before the page is marked rated: a rating
     * that could not be paid is not spent.
     */
    static Rated rate(MinecraftServer server, long id, int stars, LoadBudget budget) {
        HearthWorldData world = HearthWorldData.get(server);
        Optional<ChronicleBook.Page> existing = world.chronicle().page(id);
        if (existing.isEmpty()) {
            return new Rated(Status.UNKNOWN, null, 0.0, false);
        }
        ChronicleBook.Page before = existing.get();
        if (before.rated()) {
            return new Rated(Status.ALREADY, before, 0.0, false);
        }
        Lookup lookup = lookup(server, before.hearth(), budget);
        if (lookup.defer()) {
            return new Rated(Status.DEFERRED, before, 0.0, false);
        }
        HearthData data = Hearths.data();
        HearthBlockEntity hearth = lookup.hearth();
        ServerPlayer author = server.getPlayerList().getPlayer(before.author());
        double amount = TalesMath.ratedBurst(before.tales(), stars, data);
        double paid = 0.0;
        // Pay first, record after: if the payment throws, the page is still unrated and the rating can be sent again.
        if (hearth != null && amount > 0.0) {
            String name = before.name().isEmpty() && author != null ? HearthTales.name(author) : before.name();
            paid = burst((ServerLevel) hearth.getLevel(), hearth, before.author(), name, author, amount, data);
            hearth.setChanged();
            if (author != null) {
                HearthNet.markHudDirty(author);
            }
        }
        ChronicleBook.Page page = world.chronicle().rate(id, stars).orElseThrow();
        world.setDirty();
        TelemetryService.hearth(author, Telemetry.TALE_RATING, Integer.toString(page.stars()), "chronicle", paid);
        if (author != null) {
            announce(author, world, page, paid);
        }
        return new Rated(Status.OK, page, paid, hearth != null);
    }

    /** The Hearth a page was written at, loading its chunk when needed; null when it is gone. */
    public static HearthBlockEntity hearthOf(MinecraftServer server, String key) {
        return lookup(server, key, null).hearth();
    }

    private static Lookup lookup(MinecraftServer server, String key, LoadBudget budget) {
        HearthKey parsed = HearthKey.parse(key);
        ResourceLocation dimension = parsed == null ? null : ResourceLocation.tryParse(parsed.dimension());
        if (dimension == null) {
            return Lookup.GONE;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return Lookup.GONE;
        }
        BlockPos pos = BlockPos.of(parsed.packed());
        if (!level.isLoaded(pos)) {
            if (budget != null && !budget.take()) {
                return Lookup.DEFER;
            }
            level.getChunkAt(pos);
        }
        HearthBlockEntity hearth = HearthRange.entity(level, pos);
        return hearth == null ? Lookup.GONE : new Lookup(hearth, false);
    }

    /** Tells the author their tale was retold (chat, panel, the unlock pair of sounds), once. */
    private static void announce(ServerPlayer author, HearthWorldData world, ChronicleBook.Page page, double paid) {
        if (!world.chronicle().markTold(page.id())) {
            return;
        }
        world.setDirty();
        author.sendSystemMessage(TalesText.retold(page.title(), page.stars()));
        TalesFx.retold(author);
        HearthData data = Hearths.data();
        HearthTales.send(author, new HearthTalesPayload(HearthTalesPayload.RETOLD, data.talesPool().ordinal(),
                (float) paid, 0, page.stars(), List.of(), page.title()));
    }

    /** At login: every rated page of this player that they have not been told about. */
    public static void announcePending(ServerPlayer player) {
        HearthWorldData world = HearthWorldData.get(player.server);
        for (ChronicleBook.Page page : world.chronicle().unannouncedFor(player.getUUID())) {
            announce(player, world, page, 0.0);
        }
    }

    // ---- Ratings that arrived as a file -----------------------------------------------------------

    /** Unloaded Hearth chunks one drain of the ratings file may load (the rest wait for the next drain). */
    public static final int MAX_LOADS_PER_DRAIN = 4;
    public static final String READING = QUEUE + ".reading";
    public static final String APPLIED = "chronicle-ratings.applied.jsonl";

    /**
     * Applies the ratings {@code /mcjournal} left in {@code chronicle-ratings.jsonl} while the server was
     * down or RCON was off ({@code {"id":7,"stars":4}} per line). The file is renamed first, so a line
     * written during the read goes to a fresh file and is read next time. Returns how many were applied.
     */
    public static int drainQueue(MinecraftServer server) {
        return drainQueue(server, MAX_LOADS_PER_DRAIN);
    }

    /**
     * {@link #drainQueue(MinecraftServer)} with its own budget of chunk loads. A page whose Hearth is not loaded
     * once the budget is gone goes back into the file for the next drain. A {@code .reading} file left by a drain
     * that died (a crash, a full disk) is read first, before the queue is renamed over it: a page rates once, so
     * lines that were already applied simply read as already rated.
     */
    public static int drainQueue(MinecraftServer server, int maxLoads) {
        Path queue = queue(server);
        Path reading = queue.resolveSibling(READING);
        if (!Files.isRegularFile(reading) && !Files.isRegularFile(queue)) {
            return 0;
        }
        Path applied = queue.resolveSibling(APPLIED);
        LoadBudget budget = new LoadBudget(maxLoads);
        int count = 0;
        try {
            if (Files.isRegularFile(reading)) {
                count += consume(server, queue, reading, applied, budget);
            }
            if (Files.isRegularFile(queue) && !Files.exists(reading)) {
                Files.move(queue, reading);
                count += consume(server, queue, reading, applied, budget);
            }
        } catch (IOException e) {
            Proficiency.LOG.error("Hearth Chronicle: could not apply {}", queue, e);
        }
        return count;
    }

    /** Applies every line of {@code reading}, files what happened, puts deferred lines back in {@code queue} and removes it. */
    private static int consume(MinecraftServer server, Path queue, Path reading, Path applied, LoadBudget budget)
            throws IOException {
        int count = 0;
        List<String> deferred = new ArrayList<>();
        for (String line : Files.readAllLines(reading, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            long id;
            int stars;
            try {
                JsonObject json = JsonParser.parseString(line).getAsJsonObject();
                id = json.get("id").getAsLong();
                stars = json.get("stars").getAsInt();
            } catch (RuntimeException e) {
                note(applied, line, "BAD_LINE");
                continue;
            }
            String outcome;
            try {
                Rated rated = rate(server, id, stars, budget);
                if (rated.status() == Status.DEFERRED) {
                    deferred.add(line.strip());
                    continue;
                }
                outcome = rated.status().name();
                if (rated.status() == Status.OK) {
                    count++;
                }
            } catch (RuntimeException e) {
                // The page is still unrated (the payment came first), so the rating can be sent again.
                Proficiency.LOG.error("Hearth Chronicle: could not rate page {}", id, e);
                outcome = "ERROR";
            }
            note(applied, line, outcome);
        }
        // Deferred lines go back before the old file goes: a crash between the two reads them twice, never not at all.
        if (!deferred.isEmpty()) {
            Files.writeString(queue, String.join("\n", deferred) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        Files.deleteIfExists(reading);
        return count;
    }

    private static void note(Path applied, String line, String outcome) throws IOException {
        Files.writeString(applied, line.strip() + " " + outcome + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    static void forget() {
        TOUCH.clear();
        SEEN.clear();
        RETRY_AT.clear();
    }
}
