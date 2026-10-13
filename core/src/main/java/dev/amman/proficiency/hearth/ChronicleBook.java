package dev.amman.proficiency.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The world's Chronicle register: the page counter and, per page, who wrote it, at which Hearth,
 * the Tales it went with and its rating. The page text itself goes to
 * {@code <world>/proficiency/chronicle.jsonl} (the Tales lane writes it); this is what the mod needs
 * to pay a rating once and to tell the author. Saved in the world's Hearth SavedData.
 */
public final class ChronicleBook {

    /**
     * One page. {@code stars} is -1 until {@code /mcjournal} rates it. {@code name} is the author's
     * name when it was written and {@code title} a short title taken from the text (both only for
     * the author's notice and the ledger); {@code told} is set once the author has been told the
     * rating, so an author who was offline hears it at the next login, once.
     */
    public record Page(long id, UUID author, String name, String hearth, double tales, long gameTime, int stars,
            String title, boolean told) {

        public static final Codec<Page> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("id").forGetter(Page::id),
                // A bad author reads as HearthState.NOBODY instead of throwing (UUID.fromString would
                // abort the whole world-data load); a page with no author still keeps its id.
                HearthState.UUID_CODEC.fieldOf("author").forGetter(Page::author),
                Codec.STRING.optionalFieldOf("name", "").forGetter(Page::name),
                Codec.STRING.optionalFieldOf("hearth", "").forGetter(Page::hearth),
                Codec.DOUBLE.optionalFieldOf("tales", 0.0).forGetter(Page::tales),
                Codec.LONG.optionalFieldOf("time", 0L).forGetter(Page::gameTime),
                Codec.INT.optionalFieldOf("stars", -1).forGetter(Page::stars),
                Codec.STRING.optionalFieldOf("title", "").forGetter(Page::title),
                // A page saved before the notice state existed has no "told" field. If it was already rated
                // its author was never going to be told by this code, so it reads as told: otherwise every
                // old rating would be retold (chat line, sound, panel) at the next login. An unrated page
                // has nothing to tell yet.
                Codec.BOOL.optionalFieldOf("told").forGetter(page -> Optional.of(page.told()))
        ).apply(i, (id, author, name, hearth, tales, time, stars, title, told) ->
                new Page(id, author, name, hearth, tales, time, stars, title, told.orElse(stars >= 0))));

        /** A page as the foundation made it, with no name, title or notice state. */
        public Page(long id, UUID author, String hearth, double tales, long gameTime, int stars) {
            this(id, author, "", hearth, tales, gameTime, stars, "", false);
        }

        public boolean rated() {
            return stars >= 0;
        }

        /** Rated and the author has not heard it yet. */
        public boolean unannounced() {
            return rated() && !told;
        }
    }

    public static final Codec<ChronicleBook> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.optionalFieldOf("next", 1L).forGetter(b -> b.next),
            Page.CODEC.listOf().optionalFieldOf("pages", List.of()).forGetter(b -> new ArrayList<>(b.pages.values()))
    ).apply(i, ChronicleBook::new));

    private long next;
    private final Map<Long, Page> pages = new LinkedHashMap<>();

    public ChronicleBook() {
        this(1L, List.of());
    }

    private ChronicleBook(long next, List<Page> saved) {
        this.next = Math.max(1L, next);
        for (Page page : saved) {
            pages.put(page.id(), page);
            this.next = Math.max(this.next, page.id() + 1);
        }
    }

    /** Files a new page and returns it with its id. */
    public Page write(UUID author, String hearth, double tales, long gameTime) {
        return write(author, "", hearth, tales, gameTime, "");
    }

    /** Files a new page with the author's name and a short title (both may be empty). */
    public Page write(UUID author, String name, String hearth, double tales, long gameTime, String title) {
        Page page = new Page(next++, author, name == null ? "" : name, hearth == null ? "" : hearth,
                tales > 0 && Double.isFinite(tales) ? tales : 0.0, gameTime, -1, title == null ? "" : title, false);
        pages.put(page.id(), page);
        return page;
    }

    public Optional<Page> page(long id) {
        return Optional.ofNullable(pages.get(id));
    }

    /**
     * Rates a page once. Empty when the page is unknown or already rated (a second rating of the same
     * page is refused); otherwise the rated page, whose Tales the caller pays out.
     */
    public Optional<Page> rate(long id, int stars) {
        Page page = pages.get(id);
        if (page == null || page.rated()) {
            return Optional.empty();
        }
        Page rated = new Page(page.id(), page.author(), page.name(), page.hearth(), page.tales(), page.gameTime(),
                Math.max(0, Math.min(TalesMath.MAX_STARS, stars)), page.title(), false);
        pages.put(id, rated);
        return Optional.of(rated);
    }

    /** Marks a rated page as announced to its author. False when the page is unknown or was already told. */
    public boolean markTold(long id) {
        Page page = pages.get(id);
        if (page == null || page.told()) {
            return false;
        }
        pages.put(id, new Page(page.id(), page.author(), page.name(), page.hearth(), page.tales(), page.gameTime(),
                page.stars(), page.title(), true));
        return true;
    }

    /** Pages of one author that are rated and not yet announced to them, oldest first. */
    public List<Page> unannouncedFor(UUID author) {
        List<Page> out = new ArrayList<>();
        for (Page page : pages.values()) {
            if (page.unannounced() && page.author().equals(author)) {
                out.add(page);
            }
        }
        return out;
    }

    /** The id the next page will get. */
    public long nextId() {
        return next;
    }

    public int size() {
        return pages.size();
    }
}
