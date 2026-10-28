package dev.amman.proficiency.jade;

import dev.amman.proficiency.client.SkillIcons;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

/**
 * A small 8x8 pixel skull (drawn from a bitmap here, no texture) that stands for "kill" in a mob line, so
 * the line needs no word. It takes its gap as part of its width.
 */
final class SkullIconElement extends Element {

    private static final Vec2 SIZE = new Vec2(SkillIcons.SMALL + SkillIcons.GAP, SkillIcons.SMALL);
    private static final int BONE = 0xFFE9E4D4;
    private static final int SHADE = 0xFFB8B09A;
    /** '#' bone, '+' shade, '.' empty (the eye sockets and nose show the tooltip behind). */
    private static final String[] BITMAP = {
            "..####..",
            ".######.",
            "########",
            "##..#..#",
            "##..#..#",
            "########",
            ".######.",
            "..#+#+#.",
    };

    @Override
    public Vec2 getSize() {
        return SIZE;
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        int ox = Math.round(x);
        int oy = Math.round(y);
        for (int row = 0; row < BITMAP.length; row++) {
            for (int col = 0; col < BITMAP[row].length(); col++) {
                char c = BITMAP[row].charAt(col);
                if (c != '.') {
                    graphics.fill(ox + col, oy + row, ox + col + 1, oy + row + 1, c == '#' ? BONE : SHADE);
                }
            }
        }
    }

    @Override
    public String getMessage() {
        return "kill";
    }
}
