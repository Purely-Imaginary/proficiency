package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The stack of recent skills above the HUD skill line (ui.hudStack, 1.54.0), without any Minecraft in it.
 *
 * <p>The main line keeps showing the held or engaged skill ({@link dev.amman.proficiency.skill.HudSkillPick}).
 * Any other skill that earns XP gets a compact row above it. The rules:
 * <ul>
 * <li>At most {@link #MAX_ROWS} rows. The order is fixed by the first gain: a new row goes on top, and a row
 *     never changes place while it is up, so nothing flips.</li>
 * <li>A row fades {@code lingerMs} after its last gain (ui.hudStackLinger); the rows above it then slide down
 *     to close the gap.</li>
 * <li>The main line's skill never has a row: a gain on it is the main line's, and a row whose skill becomes
 *     the main line fades out.</li>
 * <li>A fourth skill replaces the row that has been idle longest: that row fades and the new one goes on top.</li>
 * <li>A level gained on a row pops on that row ({@link Row#pop}), it does not borrow the main line.</li>
 * </ul>
 *
 * <p>{@link #holdMain} is the main line's hysteresis for movement: with an empty hand the line no longer
 * flips between Running and Jumping while the player runs and jumps.
 */
public final class HudStack {

    /** Most rows that are not leaving. */
    public static final int MAX_ROWS = 3;
    /** Default time a row stays after its last gain (ui.hudStackLinger is in seconds). */
    public static final long DEFAULT_LINGER_MS = 4000;
    /** A row fades in this fast. */
    public static final long FADE_IN_MS = 220;
    /** A leaving row fades out over this, then it is gone and the rows above slide down. */
    public static final long FADE_OUT_MS = 380;
    /** Time constant of the slide when a row below has gone. */
    public static final double SLIDE_TAU_MS = 70;
    /** Most dots one row has in the air (each row has its own budget, so the whole stack stays small). */
    public static final int ROW_DOTS = 6;

    /**
     * Main-line hysteresis between movement skills: a movement skill on the main line stays there until it has
     * been on the line this long ...
     */
    public static final long MOVE_MIN_HOLD_MS = 3000;
    /** ... and has not earned XP for this long. */
    public static final long MOVE_IDLE_MS = 3000;

    /** One row. Times are on the caller's clock. */
    public static final class Row {
        private final Skill skill;
        private final long born;
        private long lastGain;
        private long leftAt = Long.MIN_VALUE;
        private float slot;
        /** The dots flying into this row's bar, and the value its bar shows. */
        public final XpGainDots dots = new XpGainDots();
        /** The whole part of the bar value last shown (a level, or 100 + stars), -1 before the first. */
        public int shownWhole = -1;
        private long popAt = Long.MIN_VALUE / 2;
        private int popOld;
        private int popNew;
        private boolean popStar;

        Row(Skill skill, long now, float slot) {
            this.skill = skill;
            this.born = now;
            this.lastGain = now;
            this.slot = slot;
        }

        public Skill skill() {
            return skill;
        }

        public long born() {
            return born;
        }

        public long lastGain() {
            return lastGain;
        }

        public boolean leaving() {
            return leftAt != Long.MIN_VALUE;
        }

        /** The slot the row is drawn at this frame, 0 = just above the main line; eases after a removal. */
        public float slot() {
            return slot;
        }

        /** 0 to 1: fading in after it appeared, out while it leaves. */
        public float alpha(long now) {
            float in = Math.min(1f, Math.max(0f, (now - born) / (float) FADE_IN_MS));
            if (!leaving()) {
                return in;
            }
            float out = 1f - Math.min(1f, Math.max(0f, (now - leftAt) / (float) FADE_OUT_MS));
            return Math.min(in, out);
        }

        /** The level-up (or Mastery star) pop on this row: from {@code oldWhole} to {@code newWhole}. */
        public void pop(int oldWhole, int newWhole, boolean star, long now) {
            popAt = now;
            popOld = oldWhole;
            popNew = newWhole;
            popStar = star;
            lastGain = Math.max(lastGain, now);
        }

        public boolean popStar() {
            return popStar;
        }

        public int popOld() {
            return popOld;
        }

        public int popNew() {
            return popNew;
        }

        /** Every tenth level is the gold pop, like the main line's; a star never is. */
        public boolean popGold() {
            return !popStar && LevelUpFx.isGold(popNew);
        }

        public long popMs() {
            return popStar ? LevelUpFx.STAR_MS : LevelUpFx.durationMs(popNew);
        }

        /** 0 to 1 through the pop, negative before it, above 1 after. */
        public float popT(long now) {
            return (now - popAt) / (float) popMs();
        }

        public boolean popping(long now) {
            float t = popT(now);
            return t >= 0f && t < 1f;
        }

        /** Brightness of the pop's flash, 1 falling to 0 (squared, so it snaps); a star at half. */
        public float popFlash(long now) {
            if (!popping(now)) {
                return 0f;
            }
            float left = 1f - popT(now);
            return popStar ? left * left * 0.5f : left * left;
        }

        /** The number's roll, 0 to 1 eased; 1 when it has landed or for a star. */
        public float popRoll(long now) {
            if (popStar || now < popAt) {
                return 1f;
            }
            float t = (now - popAt) / (float) LevelUpFx.ROLL_MS;
            return (float) XpGainDots.ease(Math.max(0f, Math.min(1f, t)));
        }
    }

    private final List<Row> rows = new ArrayList<>();

    /** The rows, bottom to top in their fixed order, leaving ones included. Read only. */
    public List<Row> rows() {
        return rows;
    }

    /** The row of {@code skill} that is not leaving, or {@code null}. */
    @Nullable
    public Row row(Skill skill) {
        for (Row row : rows) {
            if (row.skill == skill && !row.leaving()) {
                return row;
            }
        }
        return null;
    }

    /** How many rows are not leaving. */
    public int live() {
        int n = 0;
        for (Row row : rows) {
            if (!row.leaving()) {
                n++;
            }
        }
        return n;
    }

    /**
     * {@code skill} earned XP. Returns its row (new or old), or {@code null} when the gain is the main line's.
     * A new row goes on top; a fourth live row first sends the one idle longest away.
     */
    @Nullable
    public Row gain(Skill skill, @Nullable Skill main, long now) {
        if (skill == null || skill == main) {
            return null;
        }
        Row row = row(skill);
        if (row != null) {
            row.lastGain = Math.max(row.lastGain, now);
            return row;
        }
        if (live() >= MAX_ROWS) {
            Row idle = null;
            for (Row r : rows) {
                if (!r.leaving() && (idle == null || r.lastGain < idle.lastGain)) {
                    idle = r;
                }
            }
            if (idle != null) {
                idle.leftAt = now;
            }
        }
        // A churn of new skills fades one row at a time: an older fade still running is cut short, so the
        // stack never draws more than MAX_ROWS + 1 rows.
        while (rows.size() > MAX_ROWS) {
            Row gone = null;
            for (Row r : rows) {
                if (r.leaving()) {
                    gone = r;
                    break;
                }
            }
            if (gone == null) {
                break;
            }
            rows.remove(gone);
        }
        row = new Row(skill, now, rows.size());
        rows.add(row);
        return row;
    }

    /**
     * Called once a frame: a row whose skill is now the main line's leaves, a row idle for {@code lingerMs}
     * leaves, a row that has faded out is removed, and the rest ease to their places ({@code slide} false
     * snaps them, for Distortion Effects 0%).
     */
    public void tick(@Nullable Skill main, long now, long dtMs, long lingerMs, boolean slide) {
        for (Row row : rows) {
            if (row.leaving()) {
                continue;
            }
            // A pop keeps its row up until it is over and a moment to read the number has passed.
            long since = now - row.lastGain;
            if (row.skill == main || since >= lingerMs && !row.popping(now)) {
                row.leftAt = now;
            }
        }
        rows.removeIf(row -> row.leaving() && now - row.leftAt >= FADE_OUT_MS);
        double k = slide ? 1 - Math.exp(-Math.max(0, dtMs) / SLIDE_TAU_MS) : 1;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float target = i;
            row.slot += (float) ((target - row.slot) * k);
            if (Math.abs(target - row.slot) < 0.01f) {
                row.slot = target;
            }
        }
    }

    /** The highest slot any row reaches this frame, plus one: the stack's height in rows (0 when empty). */
    public float height() {
        float h = 0f;
        for (Row row : rows) {
            h = Math.max(h, row.slot + 1f);
        }
        return h;
    }

    public void clear() {
        rows.clear();
    }

    /** True for the skills whose main line the movement hysteresis holds. */
    public static boolean movement(@Nullable Skill skill) {
        return skill == Skill.RUNNING || skill == Skill.JUMPING;
    }

    /**
     * The main line's skill this frame. {@code picked} is {@link dev.amman.proficiency.skill.HudSkillPick}'s answer;
     * {@code current} the skill the main line holds, on it for {@code sinceCurrent} ms and last earning XP
     * {@code sinceCurrentGain} ms ago; {@code sinceRunGain} is the same for Running ({@code Long.MAX_VALUE} for never).
     *
     * <p>Rule: a change between two movement skills (Running, Jumping) waits until the current one has held the
     * line {@link #MOVE_MIN_HOLD_MS} and has been idle {@link #MOVE_IDLE_MS}. Coming onto movement from anything
     * else, Running is preferred over a picked Jumping while Running earned in the last {@link #MOVE_IDLE_MS}: a
     * sprint is the steady earner, the jumps are the side line. So while the player runs and jumps, Running stays on
     * the line and the jumps go to a row; when he only jumps, Running goes idle and Jumping takes the line after at
     * most 3 s. Every other change (an item in the hand, a punch, water, crouching) is immediate.
     */
    @Nullable
    public static Skill holdMain(@Nullable Skill picked, @Nullable Skill current, long sinceCurrent,
            long sinceCurrentGain, long sinceRunGain) {
        if (picked == current) {
            return picked;
        }
        if (!movement(current)) {
            return picked == Skill.JUMPING && sinceRunGain < MOVE_IDLE_MS ? Skill.RUNNING : picked;
        }
        if (!movement(picked)) {
            return picked;
        }
        if (sinceCurrent < MOVE_MIN_HOLD_MS || sinceCurrentGain < MOVE_IDLE_MS) {
            return current;
        }
        return picked;
    }

    /**
     * Horizontal place of the stack column, left edge {@code left} and {@code width} wide, that must clear the chat
     * box ({@code chatRight} wide from the left edge, {@code chatTop} down to {@code chatBottom}; {@code chatRight}
     * 0 for no chat) between {@code top} and {@code bottom}. Returns the shift to add to x, or
     * {@link Integer#MIN_VALUE} when the column cannot clear the chat on this screen and must hide while it shows.
     */
    public static int dodgeChat(int left, int width, int top, int bottom, int chatRight, int chatTop,
            int chatBottom, int guiWidth) {
        boolean overlapY = chatRight > 0 && chatTop < bottom && chatBottom > top;
        if (!overlapY || left >= chatRight + CHAT_GAP) {
            return 0;
        }
        int shift = chatRight + CHAT_GAP - left;
        if (left + shift + width > guiWidth - EDGE) {
            return Integer.MIN_VALUE;
        }
        return shift;
    }

    /** Gap kept between the chat box and the stack, and to the screen edge. */
    public static final int CHAT_GAP = 6;
    public static final int EDGE = 4;
}
