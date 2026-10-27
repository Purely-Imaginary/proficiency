package dev.amman.proficiency.hearth.stages;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The Hearth's stage designs (docs/HEARTH-DESIGNS-SPEC.md): eight lanes of six stages, each a 9x9 grid per
 * layer from -1 (the floor under the fire) to 5, as offsets from the campfire (x east, y up, z south).
 * Pure: the data is a classpath resource read on the server and on every client, so the game's check and
 * the guide's previews read the same lines. A {@link Probe} answers what stands where.
 *
 * <p>Matching: a stage matches when every non-space cell holds its role ({@code '.'} is air, {@code 'F'} is
 * the fire and is not looked at). The built stage is the highest stage that any lane matches in full; lower
 * stages are not required, because a later stage may reshape a cell (S to s, P to w).
 *
 * <p>The guide's API: {@link #shipped()}, {@link #lanes()}, {@link Lane#stage(int)}, {@link Stage#cells()}
 * ({@code dx, dy, dz, role}) and {@link #defaultBlock(char)} for a concrete block id per role.
 */
public final class HearthDesigns {

    /** Where the data is, as a classpath resource (the same path in every jar). */
    public static final String RESOURCE = "/data/proficiency/hearth_designs/designs.json";
    public static final int SIZE = 9;
    public static final int RADIUS = 4;
    public static final int MIN_Y = -1;
    public static final int MAX_Y = 5;
    public static final int STAGES = 6;
    /** The most lanes a designs file may hold (the guide's picker is two rows of four; the preview cache is this wide). */
    public static final int MAX_LANES = 8;

    /** The roles of the legend, in legend order (without the air {@code '.'}, the fire and the blank). */
    public static final String ROLES = "SsWPwpNicgbhlauBrkvVfxCmGzInqYOL";

    /** Role letters of the legend with their default block: what the guide draws, what a test builds. */
    private static final Map<Character, String> DEFAULTS = Map.ofEntries(
            Map.entry('.', "minecraft:air"),
            Map.entry('F', "minecraft:campfire"),
            Map.entry('S', "minecraft:stone_bricks"),
            Map.entry('s', "minecraft:stone_brick_stairs"),
            Map.entry('W', "minecraft:oak_log"),
            Map.entry('P', "minecraft:oak_planks"),
            Map.entry('w', "minecraft:oak_slab"),
            Map.entry('p', "minecraft:oak_fence"),
            Map.entry('N', "minecraft:white_banner"),
            Map.entry('i', "minecraft:iron_block"),
            Map.entry('c', "minecraft:copper_block"),
            Map.entry('g', "minecraft:gold_block"),
            Map.entry('b', "minecraft:iron_bars"),
            Map.entry('h', "minecraft:chain"),
            Map.entry('l', "minecraft:lantern"),
            Map.entry('a', "minecraft:anvil"),
            Map.entry('u', "minecraft:cauldron"),
            Map.entry('B', "minecraft:bell"),
            Map.entry('r', "minecraft:lightning_rod"),
            Map.entry('k', "minecraft:lodestone"),
            Map.entry('v', "minecraft:water"),
            Map.entry('V', "minecraft:lava"),
            Map.entry('f', "minecraft:flower_pot"),
            Map.entry('x', "minecraft:chest"),
            Map.entry('C', "minecraft:candle"),
            Map.entry('m', "minecraft:white_carpet"),
            Map.entry('G', "minecraft:glass"),
            Map.entry('z', "minecraft:skeleton_skull"),
            Map.entry('I', "minecraft:packed_ice"),
            Map.entry('n', "minecraft:snow_block"),
            Map.entry('q', "minecraft:moss_block"),
            Map.entry('Y', "minecraft:magma_block"),
            Map.entry('O', "minecraft:obsidian"),
            Map.entry('L', "minecraft:oak_leaves"));

    /**
     * Whether a role is a fluid: {@code 'v'} a water source block, {@code 'V'} a lava source block (the fluid
     * state is looked at, not a block tag; a waterlogged block holds no water source of its own).
     */
    public static boolean isFluid(char role) {
        return role == 'v' || role == 'V';
    }

    /** One block of a design: an offset from the fire and the role it must hold. */
    public record Cell(int dx, int dy, int dz, char role) {
    }

    /** One stage of a lane: its whole structure, scan order (layer -1 up, north to south, west to east). */
    public static final class Stage {
        private final int stage;
        private final List<Cell> cells;
        private final Cell[] checked;

        Stage(int stage, List<Cell> cells) {
            this.stage = stage;
            this.cells = Collections.unmodifiableList(cells);
            this.checked = cells.stream().filter(c -> c.role() != 'F').toArray(Cell[]::new);
        }

        /** 1..6. */
        public int stage() {
            return stage;
        }

        /** Every non-blank cell, the fire ({@code 'F'}) and the air cells ({@code '.'}) included. */
        public List<Cell> cells() {
            return cells;
        }

        /** The cell checked at an offset, or null (blank, or outside the grid). */
        public Cell cellAt(int dx, int dy, int dz) {
            for (Cell cell : cells) {
                if (cell.dx() == dx && cell.dy() == dy && cell.dz() == dz) {
                    return cell;
                }
            }
            return null;
        }

        /** Whether every checked cell holds its role. Stops at the first miss. */
        boolean matches(Probe probe) {
            for (Cell cell : checked) {
                if (!probe.is(cell.role(), cell.dx(), cell.dy(), cell.dz())) {
                    return false;
                }
            }
            return true;
        }

        /** How many checked cells are wrong, and the first of them (scan order); null when none. */
        Miss misses(Probe probe) {
            Cell first = null;
            int wrong = 0;
            for (Cell cell : checked) {
                if (!probe.is(cell.role(), cell.dx(), cell.dy(), cell.dz())) {
                    wrong++;
                    if (first == null) {
                        first = cell;
                    }
                }
            }
            return new Miss(first, wrong);
        }
    }

    record Miss(Cell first, int wrong) {
    }

    /**
     * One lane: an id ({@code longhouse}, {@code forge}, ...), its six stages and its palette (role letter to the
     * block id the guide, the ghost and the tests use for that role; a role without an entry uses {@link #defaultBlock}).
     */
    public record Lane(String id, List<Stage> stages, Map<Character, String> palette) {

        public Lane(String id, List<Stage> stages) {
            this(id, stages, Map.of());
        }

        /** The block id this lane suggests for a role: its palette entry, else the role's default; null for an unknown role. */
        public String block(char role) {
            String own = palette.get(role);
            return own != null ? own : defaultBlock(role);
        }

        /** Stage {@code stage} (1..6). */
        public Stage stage(int stage) {
            return stages.get(stage - 1);
        }
    }

    /** What stands where, relative to the campfire; the game answers from block tags. */
    @FunctionalInterface
    public interface Probe {
        /** Whether the block at the offset holds this role ({@code '.'}: air; never asked for {@code 'F'}). */
        boolean is(char role, int dx, int dy, int dz);
    }

    /** The first wrong cell of the stage a lane is closest to, and how many are still wrong. */
    public record Problem(int stage, String lane, char role, int dx, int dy, int dz, int remaining) {
    }

    /** What a probe built: the stage, and the problem of the next one (null at VI). */
    public record Result(int built, Problem problem) {
    }

    private final List<Lane> lanes;

    private HearthDesigns(List<Lane> lanes) {
        this.lanes = Collections.unmodifiableList(lanes);
    }

    private static int index(int dx, int dy, int dz) {
        return ((dy - MIN_Y) * SIZE + (dz + RADIUS)) * SIZE + (dx + RADIUS);
    }

    /**
     * The positions of the design a Hearth stands as: the solid cells (not air, not blank) of the lane that
     * matches its built stage, and the fire. The Comfort scan skips these ("the Hearth structure's own blocks
     * never count"); a Hearth that stands as nothing has only its fire.
     */
    public static final class Footprint {
        static final Footprint FIRE_ONLY = new Footprint(new boolean[SIZE * SIZE * (MAX_Y - MIN_Y + 1)]);

        private final boolean[] cells;

        private Footprint(boolean[] cells) {
            this.cells = cells;
            cells[index(0, 0, 0)] = true;
        }

        /** Whether an offset from the fire is one of the structure's own positions. */
        public boolean contains(int dx, int dy, int dz) {
            return Math.abs(dx) <= RADIUS && Math.abs(dz) <= RADIUS && dy >= MIN_Y && dy <= MAX_Y
                    && cells[index(dx, dy, dz)];
        }
    }

    // ---- Loading ---------------------------------------------------------------------------------

    private static volatile HearthDesigns shipped;

    /** The designs of the mod's own jar, read once. */
    public static HearthDesigns shipped() {
        HearthDesigns known = shipped;
        if (known == null) {
            synchronized (HearthDesigns.class) {
                known = shipped;
                if (known == null) {
                    try (InputStream in = HearthDesigns.class.getResourceAsStream(RESOURCE)) {
                        if (in == null) {
                            throw new IllegalStateException("missing resource " + RESOURCE);
                        }
                        known = parse(new InputStreamReader(in, StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new IllegalStateException("cannot read " + RESOURCE, e);
                    }
                    shipped = known;
                }
            }
        }
        return known;
    }

    /** Parses a designs file. Throws {@link IllegalArgumentException} on a bad grid. */
    public static HearthDesigns parse(Reader json) {
        JsonObject root = JsonParser.parseReader(json).getAsJsonObject();
        List<Lane> lanes = new ArrayList<>();
        for (JsonElement laneElement : root.getAsJsonArray("lanes")) {
            JsonObject laneObject = laneElement.getAsJsonObject();
            String id = laneObject.get("id").getAsString();
            List<Stage> stages = new ArrayList<>();
            for (JsonElement stageElement : laneObject.getAsJsonArray("stages")) {
                JsonObject stageObject = stageElement.getAsJsonObject();
                int number = stageObject.get("stage").getAsInt();
                if (number != stages.size() + 1) {
                    throw new IllegalArgumentException(id + ": stage " + number + " out of order");
                }
                stages.add(new Stage(number, cells(id, number, stageObject.getAsJsonObject("layers"))));
            }
            if (stages.size() != STAGES) {
                throw new IllegalArgumentException(id + ": " + stages.size() + " stages, expected " + STAGES);
            }
            lanes.add(new Lane(id, Collections.unmodifiableList(stages), palette(id, laneObject)));
        }
        if (lanes.isEmpty() || lanes.size() > MAX_LANES) {
            throw new IllegalArgumentException(lanes.size() + " lanes, expected 1.." + MAX_LANES);
        }
        return new HearthDesigns(lanes);
    }

    /** The optional {@code "palette"} object: role letter to block id. Throws on a role that is not in the legend. */
    private static Map<Character, String> palette(String lane, JsonObject laneObject) {
        if (!laneObject.has("palette")) {
            return Map.of();
        }
        Map<Character, String> out = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : laneObject.getAsJsonObject("palette").entrySet()) {
            if (entry.getKey().length() != 1 || ROLES.indexOf(entry.getKey().charAt(0)) < 0) {
                throw new IllegalArgumentException(lane + ": palette role '" + entry.getKey() + "' is not in the legend");
            }
            out.put(entry.getKey().charAt(0), entry.getValue().getAsString());
        }
        return Collections.unmodifiableMap(out);
    }

    private static List<Cell> cells(String lane, int stage, JsonObject layers) {
        List<Integer> ys = new ArrayList<>();
        for (String key : layers.keySet()) {
            int y = Integer.parseInt(key);
            if (y < MIN_Y || y > MAX_Y) {
                throw new IllegalArgumentException(lane + " " + stage + ": layer " + y + " out of range");
            }
            ys.add(y);
        }
        Collections.sort(ys);
        List<Cell> out = new ArrayList<>();
        for (int y : ys) {
            var rows = layers.getAsJsonArray(Integer.toString(y));
            if (rows.size() != SIZE) {
                throw new IllegalArgumentException(lane + " " + stage + " layer " + y + ": " + rows.size() + " rows");
            }
            for (int row = 0; row < SIZE; row++) {
                String text = rows.get(row).getAsString();
                if (text.length() != SIZE) {
                    throw new IllegalArgumentException(lane + " " + stage + " layer " + y + " row " + row + ": width");
                }
                for (int col = 0; col < SIZE; col++) {
                    char role = text.charAt(col);
                    if (role != ' ') {
                        if (role != '.' && role != 'F' && ROLES.indexOf(role) < 0) {
                            throw new IllegalArgumentException(lane + " " + stage + ": unknown role '" + role + "'");
                        }
                        out.add(new Cell(col - RADIUS, y, row - RADIUS, role));
                    }
                }
            }
        }
        return out;
    }

    // ---- Reading ---------------------------------------------------------------------------------

    /** The lanes, in file order. */
    public List<Lane> lanes() {
        return lanes;
    }

    /** The lane called {@code id}, or null. */
    public Lane lane(String id) {
        for (Lane lane : lanes) {
            if (lane.id().equals(id)) {
                return lane;
            }
        }
        return null;
    }

    /** A concrete block id for a role ({@code minecraft:stone_bricks} for {@code 'S'}); null for an unknown one. */
    public static String defaultBlock(char role) {
        return DEFAULTS.get(role);
    }

    /** The footprint of the stage and lane a probe stands as (see {@link Footprint}). */
    public Footprint footprint(Probe probe) {
        for (int stage = STAGES; stage >= 1; stage--) {
            for (Lane lane : lanes) {
                if (lane.stage(stage).matches(probe)) {
                    boolean[] cells = new boolean[SIZE * SIZE * (MAX_Y - MIN_Y + 1)];
                    for (Cell cell : lane.stage(stage).cells()) {
                        if (cell.role() != '.') {
                            cells[index(cell.dx(), cell.dy(), cell.dz())] = true;
                        }
                    }
                    return new Footprint(cells);
                }
            }
        }
        return Footprint.FIRE_ONLY;
    }

    // ---- Matching --------------------------------------------------------------------------------

    /** The highest stage any lane matches in full (0 when none), and the problem of the stage after it. */
    public Result check(Probe probe) {
        int built = 0;
        for (int stage = STAGES; stage >= 1 && built == 0; stage--) {
            for (Lane lane : lanes) {
                if (lane.stage(stage).matches(probe)) {
                    built = stage;
                    break;
                }
            }
        }
        return new Result(built, built >= STAGES ? null : problem(probe, built + 1));
    }

    /** Whether some lane's stage {@code stage} matches in full. */
    public boolean matches(Probe probe, int stage) {
        for (Lane lane : lanes) {
            if (lane.stage(stage).matches(probe)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The problem of stage {@code stage}: the lane with the fewest wrong cells, its first wrong cell in scan
     * order, and the count still wrong. Null when some lane matches.
     */
    public Problem problem(Probe probe, int stage) {
        Problem best = null;
        for (Lane lane : lanes) {
            Miss miss = lane.stage(stage).misses(probe);
            if (miss.first() == null) {
                return null;
            }
            if (best == null || miss.wrong() < best.remaining()) {
                Cell cell = miss.first();
                best = new Problem(stage, lane.id(), cell.role(), cell.dx(), cell.dy(), cell.dz(), miss.wrong());
            }
        }
        return best;
    }
}
