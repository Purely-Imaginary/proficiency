package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.stages.HearthStructure.Problem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Every line the stages lane says, as translatable components ({@code proficiency.hearth.stages.*},
 * en_us and pl_pl), and the one place they are sent from.
 */
public final class StageText {

    private static final String P = "proficiency.hearth.stages.";

    /** Where the lines go. A GameTest swaps it to read them, then puts {@link #PLAYER} back. */
    @FunctionalInterface
    public interface Sink {
        void send(ServerPlayer player, Component message, boolean actionBar);
    }

    /** The real sink: the chat, or the action bar. */
    public static final Sink PLAYER = (player, message, actionBar) -> player.displayClientMessage(message, actionBar);

    public static volatile Sink sink = PLAYER;

    private StageText() {
    }

    public static void chat(ServerPlayer player, Component message) {
        sink.send(player, message, false);
    }

    public static void actionBar(ServerPlayer player, Component message) {
        sink.send(player, message, true);
    }

    // ---- Pieces ----------------------------------------------------------------------------------

    /** The stage's name: Firepit, Hearth, Homestead, Hall, Gathering Place, Heart of the Home. */
    public static MutableComponent stageName(int stage) {
        return Component.translatable(nameKey(stage));
    }

    public static String nameKey(int stage) {
        return P + "name." + Math.max(1, Math.min(6, stage));
    }

    /** A role's name for a chat line ("stone", "log", "air"): {@code proficiency.hearth.role.<letter>}. */
    public static MutableComponent role(char role) {
        return Component.translatable(roleKey(role));
    }

    public static String roleKey(char role) {
        return "proficiency.hearth.role." + (role == '.' ? "air" : String.valueOf(role));
    }

    /**
     * Where a cell is, in player terms, relative to the fire: "2 north, 1 east, 1 up" (north is -z). The
     * parts are {@code where.north}, {@code .south}, {@code .east}, {@code .west}, {@code .up}, {@code .down}.
     */
    public static MutableComponent where(int dx, int dy, int dz) {
        MutableComponent out = Component.empty();
        boolean first = true;
        int[] distance = {dz, dx, dy};
        String[][] names = {{"north", "south"}, {"west", "east"}, {"down", "up"}};
        for (int axis = 0; axis < 3; axis++) {
            int d = distance[axis];
            if (d == 0) {
                continue;
            }
            if (!first) {
                out.append(", ");
            }
            first = false;
            out.append(Component.translatable(P + "where." + names[axis][d < 0 ? 0 : 1], Math.abs(d)));
        }
        return out;
    }

    /** "23 blocks to go": {@code to_go.one}, {@code .few}, {@code .many} (Polish has three forms). */
    public static MutableComponent toGo(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        String form = count == 1 ? "one" : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "few" : "many";
        return Component.translatable(P + "to_go." + form, count);
    }

    /**
     * What is wrong in the next stage: "stone wanted 2 north, 1 east, 1 up of the fire (23 blocks to go)",
     * or for air "air wanted: smoke must rise above the fire (3 blocks to go)".
     */
    public static MutableComponent problem(Problem problem) {
        MutableComponent left = toGo(problem.remaining());
        if (problem.role() == '.') {
            if (problem.dx() == 0 && problem.dz() == 0 && problem.dy() > 0) {
                return Component.translatable(P + "problem.smoke", left);
            }
            return Component.translatable(P + "problem.air", where(problem.dx(), problem.dy(), problem.dz()), left);
        }
        return Component.translatable(P + "problem.cell", role(problem.role()),
                where(problem.dx(), problem.dy(), problem.dz()), left);
    }

    /** "Character 11/14". */
    public static MutableComponent characterShort(int character, int need) {
        return Component.translatable(P + "character", character, need);
    }

    private static String coords(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    // ---- Lines -----------------------------------------------------------------------------------

    /** Action bar, on placing a campfire on hearth stone the first time. */
    public static Component hint(Component offering, boolean flintAndSteel) {
        return (flintAndSteel ? Component.translatable(P + "hint")
                : Component.translatable(P + "hint.item", offering)).withStyle(ChatFormatting.GOLD);
    }

    /** Action bar, on lighting a campfire whose platform is not complete. */
    public static Component notAHearthYet(Problem problem) {
        return Component.translatable(P + "not_yet", problem(problem)).withStyle(ChatFormatting.YELLOW);
    }

    /** "Another Hearth burns 41 blocks away. Hearths need 64 blocks between them." */
    public static Component tooClose(int distance, int spacing) {
        return Component.translatable(P + "too_close", distance, spacing).withStyle(ChatFormatting.RED);
    }

    /** To everyone in range: who lit a Hearth. */
    public static Component lit(ServerPlayer by) {
        return Component.translatable(P + "lit", by.getDisplayName()).withStyle(ChatFormatting.GOLD);
    }

    /** To everyone in range: a stage sealed. */
    public static Component sealed(ServerPlayer by, int stage) {
        return Component.translatable(P + "sealed", by.getDisplayName(), stageName(stage).withStyle(ChatFormatting.BOLD),
                HearthRules.roman(stage)).withStyle(ChatFormatting.GOLD);
    }

    public static Component alreadyLit() {
        return Component.translatable(P + "already_lit").withStyle(ChatFormatting.GRAY);
    }

    public static Component alreadyOffered() {
        return Component.translatable(P + "offer.already").withStyle(ChatFormatting.GRAY);
    }

    public static Component rebuild(Problem problem) {
        return Component.translatable(P + "offer.rebuild", problem(problem)).withStyle(ChatFormatting.YELLOW);
    }

    public static Component wantsFirst(Component offering, int stage) {
        return Component.translatable(P + "offer.first", offering, stageName(stage), HearthRules.roman(stage))
                .withStyle(ChatFormatting.YELLOW);
    }

    public static Component notYet(Problem problem) {
        return Component.translatable(P + "offer.not_yet", problem(problem)).withStyle(ChatFormatting.YELLOW);
    }

    /** "Character 11/14: the Hall wants a home that looks lived in." */
    public static Component characterRefusal(int character, int need, int stage) {
        return Component.translatable(P + "offer.character", characterShort(character, need),
                Component.translatable(P + "character.why." + Math.max(3, Math.min(6, stage))))
                .withStyle(ChatFormatting.YELLOW);
    }

    /** To everyone in range: the next layer stands and only its offering is missing. */
    public static Component ready(int stage, Component offering) {
        return Component.translatable(P + "ready", stageName(stage), offering).withStyle(ChatFormatting.GOLD);
    }

    /** To everyone in range: the ceiling dropped, with why. */
    public static Component fell(int ceiling, Component why) {
        if (ceiling <= 0) {
            // Not even the Firepit stands: the platform under the fire is broken.
            return Component.translatable(P + "fell.footing", why).withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(P + "fell", stageName(ceiling), HearthRules.roman(ceiling), why)
                .withStyle(ChatFormatting.YELLOW);
    }

    /** To everyone in range: the ceiling is back up (a rebuild, or Character again). */
    public static Component rose(int ceiling) {
        return Component.translatable(P + "rose", stageName(ceiling), HearthRules.roman(ceiling))
                .withStyle(ChatFormatting.GOLD);
    }

    /** To everyone who fed the Hearth: who put it out, where. */
    public static Component putOut(ServerPlayer by, BlockPos pos, int stage) {
        return Component.translatable(P + "put_out", by.getDisplayName(), coords(pos), stageName(stage))
                .withStyle(ChatFormatting.RED);
    }

    public static Component putOutSelf() {
        return Component.translatable(P + "put_out.self").withStyle(ChatFormatting.RED);
    }

    /** Action bar, hitting the Hearth without sneaking. */
    public static Component sneakToBreak() {
        return Component.translatable(P + "sneak_to_break").withStyle(ChatFormatting.GRAY);
    }
}
