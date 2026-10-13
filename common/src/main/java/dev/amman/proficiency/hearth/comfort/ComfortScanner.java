package dev.amman.proficiency.hearth.comfort;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.HearthStructure;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import dev.amman.proficiency.skill.PlacedBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One Comfort and Character scan of the Hearth at a position (docs/HEARTH-SPEC.md, "Comfort"). It
 * never loads a chunk and never runs per tick: {@link HearthComfort} decides when ({@link ScanPolicy}).
 *
 * <p>What it counts:
 * <ul>
 * <li><b>Placed blocks only.</b> The scan walks the comfort marks of the chunk columns the range
 * touches (every player placement, {@link PlacedBlocks#markComfort}; blocks from before that mark
 * existed still count through their gathering mark), not the world's blocks, so natural terrain,
 * villages and chest contents can never count. A mark whose block is no longer the one placed is
 * dropped.</li>
 * <li><b>Comfort</b>: the distinct kinds among those blocks that stand indoors (no sky above them),
 * from the kind tags; plus the entities of the one box query: paintings and item frames holding an
 * item (art), armour stands wearing armour (trophy), whatever a pack lists in the entity type tags.</li>
 * <li><b>Character</b>: the distinct block ids among them, inside or out, as the item that places
 * each ({@code block.asItem()}), minus {@code #proficiency:hearth/character_ignore}, with the optional
 * family folding.</li>
 * <li>The Hearth's own structure counts for neither: whatever stands at one of its fixed positions
 * (the fire, the platform, the pillars, the roof ring, the lantern and banner spots) and the bell.</li>
 * </ul>
 * "Indoors" is the sky-light test of the spec on the block's top face: a block (or an entity's block)
 * is indoors when {@code canSeeSky} is false for the position above it. The position of an opaque
 * block itself holds no sky light, which would make a barrel on a lawn "indoors".
 */
public final class ComfortScanner {

    private final ServerLevel level;
    private final BlockPos hearth;
    private final HearthData data;
    private final HearthGeometry.Range range;
    private final List<String> kinds;
    private final List<TagKey<Block>> kindTags;
    private final Map<Block, BlockInfo> cache = new IdentityHashMap<>();
    private final HearthDesigns.Footprint footprint;
    private final ComfortTally tally = new ComfortTally();
    private final LongArrayList stale = new LongArrayList();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos sky = new BlockPos.MutableBlockPos();

    private ComfortScanner(ServerLevel level, BlockPos hearth) {
        this.level = level;
        this.hearth = hearth;
        this.data = Hearths.data();
        this.range = HearthRange.range();
        this.kinds = ComfortTags.kinds(data);
        this.kindTags = ComfortTags.blockTags(data);
        this.footprint = HearthStructure.footprint(StageBlocks.probe(level, hearth, data));
    }

    /**
     * Scans the Hearth at {@code hearth}. Returns null when a chunk column of its range is not
     * loaded (nothing is read, nothing is loaded; try again later), else what the scan found.
     */
    @Nullable
    public static ComfortTally.Result scan(ServerLevel level, BlockPos hearth) {
        return new ComfortScanner(level, hearth).run();
    }

    /** What one placed block would be worth: the Comfort kinds it belongs to and its Character entry. */
    public static Worth worthOf(BlockState state) {
        HearthData data = Hearths.data();
        List<String> kinds = ComfortTags.kinds(data);
        List<TagKey<Block>> tags = ComfortTags.blockTags(data);
        List<String> found = new ArrayList<>();
        for (int i = 0; i < kinds.size(); i++) {
            if (state.is(tags.get(i))) {
                found.add(kinds.get(i));
            }
        }
        return new Worth(List.copyOf(found), characterEntry(state));
    }

    /** The Comfort kinds a block belongs to and the Character entry it makes (null: it counts for none). */
    public record Worth(List<String> kinds, @Nullable String entry) {

        /** Whether this is worth the placer-note key {@code kind:<kind>} or {@code id:<entry>}. */
        public boolean has(String key) {
            if (key.startsWith(KIND_NOTE)) {
                return kinds.contains(key.substring(KIND_NOTE.length()));
            }
            return entry != null && key.equals(ID_NOTE + entry);
        }
    }

    /** Prefix of a placer-note key for a Comfort kind. */
    public static final String KIND_NOTE = "kind:";

    /** Prefix of a placer-note key for a Character entry. */
    public static final String ID_NOTE = "id:";

    /**
     * Whether the block at {@code pos} still stands as something the Hearth at {@code hearth} would
     * count for {@code key} (a placer-note key): it is loaded, worth the key, not the Hearth's own
     * structure, and, for a kind, indoors. A placer is credited only through a block that passes.
     */
    public static boolean stillCounts(ServerLevel level, BlockPos hearth, BlockPos pos, String key) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!worthOf(state).has(key)
                || isStructure(state, HearthStructure.footprint(StageBlocks.probe(level, hearth, Hearths.data())),
                        pos.getX() - hearth.getX(), pos.getY() - hearth.getY(), pos.getZ() - hearth.getZ())) {
            return false;
        }
        return !key.startsWith(KIND_NOTE) || indoors(level, pos);
    }

    // ---- The scan ---------------------------------------------------------------------------------

    @Nullable
    private ComfortTally.Result run() {
        int radius = (int) Math.ceil(range.radius());
        int hx = hearth.getX();
        int hz = hearth.getZ();
        List<LevelChunk> chunks = new ArrayList<>();
        for (int cx = (hx - radius) >> 4; cx <= (hx + radius) >> 4; cx++) {
            for (int cz = (hz - radius) >> 4; cz <= (hz + radius) >> 4; cz++) {
                if (!touches(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    return null;
                }
                chunks.add(chunk);
            }
        }
        int minY = Math.max(level.getMinBuildHeight(), hearth.getY() - range.below());
        int maxY = Math.min(level.getMaxBuildHeight() - 1, hearth.getY() + range.above());
        for (LevelChunk chunk : chunks) {
            int cx = chunk.getPos().x;
            int cz = chunk.getPos().z;
            PlacedBlocks.forEachPlaced(level, cx, cz, minY, maxY,
                    (x, y, z, hash, comfortMark) -> visit(chunk, x, y, z, hash, comfortMark));
        }
        for (int i = 0; i < stale.size(); i++) {
            PlacedBlocks.clearComfort(level, BlockPos.of(stale.getLong(i)));
        }
        scanEntities();
        return tally.result(data);
    }

    /** Whether the chunk column's nearest point to the Hearth lies inside the cylinder's radius. */
    private boolean touches(int cx, int cz) {
        int x0 = cx << 4;
        int z0 = cz << 4;
        int dx = Math.max(0, Math.max(x0 - hearth.getX(), hearth.getX() - (x0 + 15)));
        int dz = Math.max(0, Math.max(z0 - hearth.getZ(), hearth.getZ() - (z0 + 15)));
        return (double) dx * dx + (double) dz * dz <= range.radius() * range.radius();
    }

    private void visit(LevelChunk chunk, int x, int y, int z, int storedHash, boolean comfortMark) {
        int dx = x - hearth.getX();
        int dy = y - hearth.getY();
        int dz = z - hearth.getZ();
        if (!range.contains(dx, dy, dz)) {
            return;
        }
        pos.set(x, y, z);
        BlockState state = chunk.getBlockState(pos);
        BlockInfo info = cache.computeIfAbsent(state.getBlock(), b -> describe(state));
        if (info.hash != storedHash) {
            // Whatever stands here now is not what was placed: the mark is stale (blown up, burnt,
            // washed away, replaced). The gathering mark is the skill lane's to clean up.
            if (comfortMark) {
                stale.add(pos.asLong());
            }
            return;
        }
        if (isStructure(state, footprint, dx, dy, dz)) {
            return;
        }
        if (info.entry != null) {
            tally.addEntry(info.entry);
        }
        if (!info.kinds.isEmpty() && anyNewKind(info.kinds) && indoors(x, y, z)) {
            for (int i = info.kinds.nextSetBit(0); i >= 0; i = info.kinds.nextSetBit(i + 1)) {
                tally.addKind(kinds.get(i));
            }
        }
    }

    /** Whether a block of these kinds could add one this scan has not found yet (so the sky test is worth doing). */
    private boolean anyNewKind(BitSet set) {
        for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
            if (!tally.hasKind(kinds.get(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The Hearth's own structure: whatever stands at a position of the design the Hearth stands as (its fire
     * included, {@link HearthStructure#footprint}), and the bell, which hangs anywhere in the 9x9x7 check
     * box. A chair next to the fire is furniture.
     */
    static boolean isStructure(BlockState state, HearthDesigns.Footprint footprint, int dx, int dy, int dz) {
        return footprint.contains(dx, dy, dz)
                || HearthStructure.inBox(dx, dy, dz) && state.is(StageBlocks.BELL);
    }

    /** Indoors: no sky light reaches the top face of the block (a roof, a ceiling, the ground above). */
    private boolean indoors(int x, int y, int z) {
        return !level.canSeeSky(sky.set(x, y + 1, z));
    }

    private static boolean indoors(ServerLevel level, BlockPos pos) {
        return !level.canSeeSky(pos.above());
    }

    private static final class BlockInfo {
        final int hash;
        final BitSet kinds;
        @Nullable
        final String entry;

        BlockInfo(int hash, BitSet kinds, @Nullable String entry) {
            this.hash = hash;
            this.kinds = kinds;
            this.entry = entry;
        }
    }

    private BlockInfo describe(BlockState state) {
        BitSet set = new BitSet(kinds.size());
        for (int i = 0; i < kindTags.size(); i++) {
            if (state.is(kindTags.get(i))) {
                set.set(i);
            }
        }
        return new BlockInfo(PlacedBlocks.hash(state), set, characterEntry(state));
    }

    /**
     * The Character entry of a block: the item that places it, folded by the family tags, or null when
     * it is ignored (filler, the Hearth itself) or has no item (fire, water, a portal).
     */
    @Nullable
    public static String characterEntry(BlockState state) {
        if (state.is(ComfortTags.CHARACTER_IGNORE)) {
            return null;
        }
        Item item = state.getBlock().asItem();
        if (item == Items.AIR) {
            return null;
        }
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        String family = state.getTags()
                .map(TagKey::location)
                .filter(tag -> tag.getNamespace().equals(Proficiency.MOD_ID)
                        && CharacterKeys.isFamilyTag(tag.getPath()))
                .map(tag -> CharacterKeys.familyEntry(tag.toString()))
                .sorted()
                .findFirst()
                .orElse(null);
        if (family != null) {
            return family;
        }
        return state.is(ComfortTags.CHARACTER_FAMILY) ? CharacterKeys.foldNumbered(id) : id;
    }

    // ---- Entities: one box query ---------------------------------------------------------------------

    private void scanEntities() {
        Map<EntityType<?>, BitSet> byType = entityKinds();
        if (byType.isEmpty()) {
            return;
        }
        double r = range.radius();
        AABB box = new AABB(hearth.getX() - r, hearth.getY() - range.below(), hearth.getZ() - r,
                hearth.getX() + r + 1, hearth.getY() + range.above() + 1, hearth.getZ() + r + 1);
        List<Entity> found = level.getEntitiesOfClass(Entity.class, box, e -> byType.containsKey(e.getType()));
        for (Entity entity : found) {
            if (entity.isRemoved()) {
                continue;
            }
            BlockPos at = entity.blockPosition();
            if (!range.contains(at.getX() - hearth.getX(), at.getY() - hearth.getY(), at.getZ() - hearth.getZ())
                    || level.canSeeSky(at)) {
                continue;
            }
            if (!countsNow(entity)) {
                // Nothing reports a frame being filled or a stand being dressed: look again soon.
                tally.markPending();
                continue;
            }
            BitSet set = byType.get(entity.getType());
            for (int i = set.nextSetBit(0); i >= 0; i = set.nextSetBit(i + 1)) {
                tally.addKind(kinds.get(i));
            }
        }
    }

    /**
     * The entity types of every kind's entity tag, with the kinds each is worth. Empty when no tag lists
     * one. It reads the members of the (few) tags, not every registered entity type.
     */
    private Map<EntityType<?>, BitSet> entityKinds() {
        Map<EntityType<?>, BitSet> byType = new IdentityHashMap<>();
        for (int i = 0; i < kinds.size(); i++) {
            int kind = i;
            BuiltInRegistries.ENTITY_TYPE.getTag(ComfortTags.entity(kinds.get(i))).ifPresent(members -> {
                for (Holder<EntityType<?>> member : members) {
                    byType.computeIfAbsent(member.value(), t -> new BitSet(kinds.size())).set(kind);
                }
            });
        }
        return byType;
    }

    /** An item frame counts while it holds an item, an armour stand while it wears armour; anything else listed counts as is. */
    private static boolean countsNow(Entity entity) {
        if (entity instanceof ItemFrame frame) {
            return !frame.getItem().isEmpty();
        }
        if (entity instanceof ArmorStand stand) {
            return wearsArmour(stand);
        }
        return true;
    }

    private static boolean wearsArmour(ArmorStand stand) {
        return !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !stand.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                || !stand.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || !stand.getItemBySlot(EquipmentSlot.FEET).isEmpty();
    }
}
