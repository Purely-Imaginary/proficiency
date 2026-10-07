package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.SkillCategory;

/** One colour per category, so the panel reads as four groups at a glance. */
public final class SkillPalette {

    public static final int PANEL = 0xE8121418;
    public static final int PANEL_BORDER = 0xFF2C333D;
    public static final int TRACK = 0xFF23282F;
    public static final int TEXT = 0xFFDDE2E8;
    public static final int TEXT_DIM = 0xFF7A828C;
    public static final int MAXED = 0xFFF2D98A;

    private SkillPalette() {
    }

    public static int accent(SkillCategory category) {
        return switch (category) {
            case COMBAT -> 0xFFD4695A;
            case GATHERING -> 0xFF7FAE63;
            case MOVEMENT -> 0xFF5F9EC4;
            case CRAFTING -> 0xFFD2A249;
            case MASTERY -> 0xFF9B7FC4;
            case EXPEDITION -> 0xFFC98A4B;
            case CONSTRUCTION -> 0xFF6FB0A6;
            case SOCIAL -> 0xFFD98AB3;
            case SURVIVAL -> 0xFF6F7FC9;
        };
    }
}
