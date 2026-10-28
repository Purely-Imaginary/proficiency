package dev.amman.proficiency.coop;

import dev.amman.proficiency.skill.SkillsWire;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One second of co-op XP for one player (1.45.0): the partners in company with them now (the pip row), one stream
 * per partner who earned XP, and the company XP their own grants carried (the shared plink). The wire layout is
 * here so the three lines share it; the payload classes only wrap it.
 *
 * @param partners the players in company with this one now, in a stable order
 * @param streams  one per partner who earned this second
 * @param ownBonus the XP company added to this player's own grants this second (0 for none)
 */
public record CoopBatch(List<Partner> partners, List<Stream> streams, float ownBonus) {

    /** Wire version; a reader that sees another one reads nothing. */
    public static final int VERSION = 1;

    public record Partner(UUID id, String name) {
    }

    /**
     * A partner's XP this second.
     *
     * @param skill the skill that earned most (its colour trails the dots)
     * @param xp    the XP they earned, every multiplier included
     * @param dots  how many dots to show, 1..{@link CoopRules#DOTS_PER_SECOND}
     * @param pays  true: company pays the watcher, dots fly into their bar; false: a faint trail at the earner
     * @param x     where the earner was, chest height, for a client that does not have them loaded
     */
    public record Stream(UUID id, String name, int skill, float xp, int dots, boolean pays, float x, float y, float z) {
    }

    public CoopBatch {
        partners = List.copyOf(partners);
        streams = List.copyOf(streams);
    }

    public static final CoopBatch EMPTY = new CoopBatch(List.of(), List.of(), 0f);

    public boolean hasNews() {
        return !streams.isEmpty() || ownBonus > 0f;
    }

    public void write(SkillsWire.Out out) {
        out.writeVarInt(VERSION);
        int np = Math.min(partners.size(), CoopRules.MAX_PARTNERS);
        out.writeVarInt(np);
        for (int i = 0; i < np; i++) {
            Partner partner = partners.get(i);
            writeId(out, partner.id());
            out.writeUtf(clip(partner.name()));
        }
        int ns = Math.min(streams.size(), CoopRules.MAX_STREAMS);
        out.writeVarInt(ns);
        for (int i = 0; i < ns; i++) {
            Stream s = streams.get(i);
            writeId(out, s.id());
            out.writeUtf(clip(s.name()));
            out.writeVarInt(s.skill());
            out.writeFloat(s.xp());
            out.writeVarInt(Math.max(0, Math.min(CoopRules.DOTS_PER_SECOND, s.dots())));
            out.writeVarInt(s.pays() ? 1 : 0);
            out.writeFloat(s.x());
            out.writeFloat(s.y());
            out.writeFloat(s.z());
        }
        out.writeFloat(ownBonus);
    }

    /** Reads what {@link #write} wrote; counts beyond the caps and dot counts beyond the limit are clamped. */
    public static CoopBatch read(SkillsWire.In in) {
        int version = in.readVarInt();
        if (version != VERSION) {
            return EMPTY;
        }
        int np = in.readVarInt();
        if (np < 0 || np > CoopRules.MAX_PARTNERS) {
            throw new IllegalArgumentException("coop: " + np + " partners");
        }
        List<Partner> partners = new ArrayList<>(np);
        for (int i = 0; i < np; i++) {
            partners.add(new Partner(readId(in), in.readUtf()));
        }
        int ns = in.readVarInt();
        if (ns < 0 || ns > CoopRules.MAX_STREAMS) {
            throw new IllegalArgumentException("coop: " + ns + " streams");
        }
        List<Stream> streams = new ArrayList<>(ns);
        for (int i = 0; i < ns; i++) {
            UUID id = readId(in);
            String name = in.readUtf();
            int skill = in.readVarInt();
            float xp = in.readFloat();
            int dots = Math.max(0, Math.min(CoopRules.DOTS_PER_SECOND, in.readVarInt()));
            boolean pays = in.readVarInt() != 0;
            streams.add(new Stream(id, name, skill, xp, dots, pays, in.readFloat(), in.readFloat(), in.readFloat()));
        }
        float own = in.readFloat();
        return new CoopBatch(partners, streams, Float.isFinite(own) ? Math.max(0f, own) : 0f);
    }

    private static String clip(String name) {
        return name == null ? "" : name.length() > 32 ? name.substring(0, 32) : name;
    }

    private static void writeId(SkillsWire.Out out, UUID id) {
        out.writeVarLong(id.getMostSignificantBits());
        out.writeVarLong(id.getLeastSignificantBits());
    }

    private static UUID readId(SkillsWire.In in) {
        long most = in.readVarLong();
        return new UUID(most, in.readVarLong());
    }

    /** True when two partner lists name the same players in the same order. */
    public static boolean samePartners(List<Partner> a, List<Partner> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).id().equals(b.get(i).id())) {
                return false;
            }
        }
        return true;
    }
}
