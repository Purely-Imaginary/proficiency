package dev.amman.proficiency.jade;

import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.ui.Element;

/**
 * A skill's 16x16 icon drawn small (the same size and the same sampling as beside a line on the HUD) at
 * the start of a Jade line. It takes the icon and its gap as its width, so the text appended after it
 * does not touch it.
 */
final class SkillIconElement extends Element {

    private static final Vec2 SIZE = new Vec2(SkillIcons.SMALL + SkillIcons.GAP, SkillIcons.SMALL);

    private final Skill skill;

    SkillIconElement(Skill skill) {
        this.skill = skill;
    }

    @Override
    public Vec2 getSize() {
        return SIZE;
    }

    @Override
    public void render(GuiGraphics graphics, float x, float y, float maxX, float maxY) {
        SkillIcons.draw(graphics, skill, Math.round(x), Math.round(y), SkillIcons.SMALL);
    }

    @Override
    public String getMessage() {
        return skill.id();
    }
}
