package dev.amman.proficiency.skill;

public enum SkillCategory {
    COMBAT("combat"),
    GATHERING("gathering"),
    MOVEMENT("movement"),
    CRAFTING("crafting"),
    /** Systems the pack adds that you learn rather than swing: spells and machines. */
    MASTERY("mastery"),
    /** What is out there: the things that kill you and the places you have not been. */
    EXPEDITION("expedition"),
    /** For the people who never fight anything and have the nicest base on the server. */
    CONSTRUCTION("construction"),
    /** Working next to other people. Only Social so far. */
    SOCIAL("social"),
    /** Holding out where it is dark. Only Nightwalker so far. */
    SURVIVAL("survival");

    private final String id;

    SkillCategory(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "proficiency.category." + id;
    }
}
