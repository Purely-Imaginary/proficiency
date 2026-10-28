package dev.amman.proficiency.coop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Co-op XP streams (1.45.0), the server's rules: who sees a partner's XP, how, and how much of it. Pure, no
 * Minecraft. The server adds every grant to a {@link Ledger} and drains it once a second; each player in company
 * range of an earner gets one stream per earner per second, never more than {@link #DOTS_PER_SECOND} dots.
 *
 * <p>Range, in blocks, the same numbers the XP code uses: a stream goes to a player within either one's company
 * radius ({@code company.radius} plus Open Circle). It {@link Mode#PAYS} when the player is within their OWN radius
 * and company actually adds XP for them (their company factor is above 1.0): then the dots fly into their bar.
 * Otherwise it is {@link Mode#FAINT}: a faint trail at the earner, nothing comes to you.
 */
public final class CoopRules {

    /** Most dots one partner's stream shows in a second. */
    public static final int DOTS_PER_SECOND = 6;
    /** How often the server sends, in ticks. */
    public static final int BATCH_TICKS = 20;
    /** Most streams (partners) in one batch, and most partners in the pip row. */
    public static final int MAX_STREAMS = 8;
    public static final int MAX_PARTNERS = 8;

    public enum Mode {
        NONE, FAINT, PAYS
    }

    private CoopRules() {
    }

    /**
     * Dots for a second's worth of a partner's XP: one for a sliver, one more each time the XP doubles, at most
     * {@link #DOTS_PER_SECOND}. 0.5 XP is 1 dot, 1 XP 2, 3 XP 3, 7 XP 4, 15 XP 5, 31 XP or more 6.
     */
    public static int dotsFor(double xp) {
        if (!(xp > 0) || !Double.isFinite(xp)) {
            return 0;
        }
        int dots = 1 + (int) Math.floor(Math.log(1.0 + xp) / Math.log(2.0));
        return Math.max(1, Math.min(DOTS_PER_SECOND, dots));
    }

    /**
     * How a player at {@code distance} from an earner sees the earner's XP.
     *
     * @param recipientRadius the watching player's company radius
     * @param earnerRadius    the earner's company radius
     * @param recipientPaid   true when company adds XP for the watcher right now (their factor is above 1.0)
     */
    public static Mode mode(double distance, double recipientRadius, double earnerRadius, boolean recipientPaid) {
        if (!(distance >= 0) || !Double.isFinite(distance)) {
            return Mode.NONE;
        }
        if (distance <= recipientRadius && recipientPaid) {
            return Mode.PAYS;
        }
        return distance <= Math.max(recipientRadius, earnerRadius) ? Mode.FAINT : Mode.NONE;
    }

    /** In company for the pip row: within the watching player's own company radius. */
    public static boolean inCompany(double distance, double recipientRadius) {
        return distance >= 0 && distance <= recipientRadius;
    }

    /** What one earner earned in the last second: the total and the skill that earned most. */
    public record Earned(UUID earner, double xp, int topSkill) {
    }

    /**
     * The XP each player earned since the last drain, and the company XP each one's own grants carried. Not thread
     * safe: the server thread only.
     */
    public static final class Ledger {
        private final Map<UUID, Map<Integer, Double>> earned = new LinkedHashMap<>();
        private final Map<UUID, Double> own = new HashMap<>();

        /** A grant of {@code xp} (after every multiplier) in {@code skill}, with {@code company} its company factor. */
        public void add(UUID earner, int skill, double xp, double company) {
            if (earner == null || !(xp > 0) || !Double.isFinite(xp)) {
                return;
            }
            earned.computeIfAbsent(earner, id -> new LinkedHashMap<>()).merge(skill, xp, Double::sum);
            if (company > 1.0 && Double.isFinite(company)) {
                own.merge(earner, xp * (1.0 - 1.0 / company), Double::sum);
            }
        }

        /** Everyone who earned since the last drain, in the order they first earned; empties the earned side. */
        public List<Earned> drainEarned() {
            List<Earned> out = new ArrayList<>(earned.size());
            for (Map.Entry<UUID, Map<Integer, Double>> entry : earned.entrySet()) {
                double total = 0;
                int top = -1;
                double best = -1;
                for (Map.Entry<Integer, Double> skill : entry.getValue().entrySet()) {
                    total += skill.getValue();
                    if (skill.getValue() > best) {
                        best = skill.getValue();
                        top = skill.getKey();
                    }
                }
                out.add(new Earned(entry.getKey(), total, top));
            }
            earned.clear();
            return out;
        }

        /** The company XP this player's own grants carried since the last call, and forget it. */
        public double takeOwn(UUID player) {
            Double value = own.remove(player);
            return value == null ? 0.0 : value;
        }

        /** Drop the own-bonus side for players who were not asked (left, or not sent to). */
        public void clearOwn() {
            own.clear();
        }

        public void forget(UUID player) {
            earned.remove(player);
            own.remove(player);
        }

        public boolean isEmpty() {
            return earned.isEmpty() && own.isEmpty();
        }
    }
}
