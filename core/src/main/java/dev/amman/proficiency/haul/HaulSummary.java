package dev.amman.proficiency.haul;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillsWire;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * What the server sends for one Haul card: every skill that earned XP since the last haul and the session's
 * totals. Immutable; laid out on the wire by {@link #write} against {@link SkillsWire}, so the byte layout is the
 * same on every loader. The {@code haul} payload is optional (a client without it is never sent one), so this
 * layout is not part of the network protocol number.
 *
 * @param mode        {@link #CARD} (show the card) or {@link #LOGIN} (a hello at login: the client may toast it)
 * @param minutes     how long ago the session's first gain was, in whole minutes (0 for none)
 * @param rows        one per skill with XP, the most XP first (see {@link #sorted})
 * @param points      talent points earned (one per level)
 * @param tempoSkill  the skill of the best Tempo chain, or -1
 * @param tempoSteps  that chain's step
 * @param tempoPermille that chain's multiplier in thousandths (1000 = none)
 * @param found       first-time discoveries: new kinds, biomes, dimensions and structures
 * @param synergies   synergies that woke up
 * @param charge      Hearth charge this player's work credited
 */
public record HaulSummary(int mode, int minutes, List<Row> rows, int points, int tempoSkill, int tempoSteps,
        int tempoPermille, int found, int synergies, float charge) {

    public static final int CARD = 0;
    public static final int LOGIN = 1;

    /** Most rows the card lists; the rest are a "+N more" line. */
    public static final int MAX_ROWS = 8;

    /**
     * One skill's share. {@code from} and {@code to} are bar positions in level units ({@link HaulMath#units}): 23.4
     * is 40% into level 23, and past 100 a whole unit is a Mastery star.
     */
    public record Row(int skill, float xp, float from, float to, int levels, int stars) {

        public Skill skillValue() {
            return Skill.VALUES[skill];
        }
    }

    public HaulSummary {
        rows = List.copyOf(rows);
    }

    /** Most XP first; a tie keeps the skill order, so the card never shuffles between two looks. */
    public static List<Row> sorted(List<Row> rows) {
        List<Row> out = new ArrayList<>(rows);
        out.sort(Comparator.comparingDouble((Row r) -> -r.xp()).thenComparingInt(Row::skill));
        return out;
    }

    /** The rows the card lists: the first {@code max} (at least one when there are any). */
    public List<Row> shown(int max) {
        return rows.subList(0, Math.min(rows.size(), Math.max(1, max)));
    }

    /** How many rows {@link #shown} leaves out: the "+N more" count. */
    public int more(int max) {
        return rows.size() - shown(max).size();
    }

    public double totalXp() {
        double total = 0;
        for (Row row : rows) {
            total += row.xp();
        }
        return total;
    }

    public int totalLevels() {
        int total = 0;
        for (Row row : rows) {
            total += row.levels();
        }
        return total;
    }

    public int totalStars() {
        int total = 0;
        for (Row row : rows) {
            total += row.stars();
        }
        return total;
    }

    public HaulRating rating() {
        return HaulRating.of(totalXp());
    }

    /** Nothing to show: no XP and none of the counters moved. */
    public boolean isEmpty() {
        return rows.isEmpty() && points == 0 && found == 0 && synergies == 0 && !(charge > 0) && tempoSkill < 0;
    }

    public HaulSummary withMode(int newMode) {
        return new HaulSummary(newMode, minutes, rows, points, tempoSkill, tempoSteps, tempoPermille, found,
                synergies, charge);
    }

    // ---- Wire ------------------------------------------------------------------------------------

    public void write(SkillsWire.Out buf) {
        buf.writeVarInt(mode);
        buf.writeVarInt(Math.max(0, minutes));
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeVarInt(row.skill());
            buf.writeFloat(row.xp());
            buf.writeFloat(row.from());
            buf.writeFloat(row.to());
            buf.writeVarInt(row.levels());
            buf.writeVarInt(row.stars());
        }
        buf.writeVarInt(points);
        buf.writeVarInt(tempoSkill + 1);
        buf.writeVarInt(tempoSteps);
        buf.writeVarInt(tempoPermille);
        buf.writeVarInt(found);
        buf.writeVarInt(synergies);
        buf.writeFloat(charge);
    }

    /** Reads what {@link #write} wrote; a corrupt number is clamped, an unknown skill row is dropped. */
    public static HaulSummary read(SkillsWire.In buf) {
        int mode = buf.readVarInt();
        int minutes = Math.max(0, buf.readVarInt());
        int count = buf.readVarInt();
        if (count < 0 || count > Skill.VALUES.length) {
            throw new IllegalArgumentException("haul: " + count + " rows");
        }
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int skill = buf.readVarInt();
            float xp = sane(buf.readFloat());
            float from = sane(buf.readFloat());
            float to = sane(buf.readFloat());
            int levels = Math.max(0, buf.readVarInt());
            int stars = Math.max(0, buf.readVarInt());
            if (skill >= 0 && skill < Skill.VALUES.length) {
                rows.add(new Row(skill, xp, from, Math.max(from, to), levels, stars));
            }
        }
        int points = Math.max(0, buf.readVarInt());
        int tempoSkill = buf.readVarInt() - 1;
        if (tempoSkill >= Skill.VALUES.length) {
            tempoSkill = -1;
        }
        int tempoSteps = Math.max(0, buf.readVarInt());
        int tempoPermille = Math.max(0, buf.readVarInt());
        int found = Math.max(0, buf.readVarInt());
        int synergies = Math.max(0, buf.readVarInt());
        float charge = sane(buf.readFloat());
        return new HaulSummary(mode, minutes, sorted(rows), points, tempoSkill, tempoSteps, tempoPermille, found,
                synergies, charge);
    }

    private static float sane(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }
}
