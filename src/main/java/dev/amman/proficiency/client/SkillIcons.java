package dev.amman.proficiency.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * The 16x16 pixel icon of each skill ({@code textures/gui/skill/<id>.png}, drawn by
 * {@code tools/gen_skill_icons.py}), drawn next to the skill's name on the HUD line, in the skills
 * panel, the tree header, the XP feed, the banner XP line, the death recap and the ability wheel.
 *
 * <p>A GUI texture is sampled nearest-neighbour, so an icon drawn 8 GUI pixels wide is the 16
 * texels at 1:1 on a GUI scale of 2 and doubled at 4. The textures are looked up once and kept,
 * so a frame allocates nothing.
 */
public final class SkillIcons {

    /** The icon beside a line of text: as tall as the font's capitals plus one. */
    public static final int SMALL = 8;
    /** The icon where there is room for the full texture: tree header, wheel slots. */
    public static final int LARGE = 16;
    /** Space between an icon and the text after it. */
    public static final int GAP = 2;

    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Skill.VALUES.length];

    private SkillIcons() {
    }

    /** Whether to draw icons at all ({@code ui.skillIcons}). */
    public static boolean enabled() {
        return ProficiencyClientConfig.uiSkillIcons();
    }

    public static ResourceLocation texture(Skill skill) {
        ResourceLocation texture = TEXTURES[skill.ordinal()];
        if (texture == null) {
            texture = new ResourceLocation(Proficiency.MOD_ID, "textures/gui/skill/" + skill.id() + ".png");
            TEXTURES[skill.ordinal()] = texture;
        }
        return texture;
    }

    /**
     * The icon size that fits: {@code preferred} when it, a gap and {@code content} pixels of text
     * fit in {@code available}, else {@link #SMALL} when that fits, else 0 (draw none). The
     * caller moves its text right by {@link #advance} of the answer.
     */
    public static int fit(int available, int content, int preferred) {
        if (preferred >= LARGE && LARGE + GAP + content <= available) {
            return LARGE;
        }
        if (preferred >= SMALL && SMALL + GAP + content <= available) {
            return SMALL;
        }
        return 0;
    }

    /** How far text moves right for an icon of this size: the icon and its gap, or 0 for none. */
    public static int advance(int size) {
        return size <= 0 ? 0 : size + GAP;
    }

    /** Text beside a {@link #SMALL} icon at {@code textY}: the icon's top, so its foot sits on the baseline. */
    public static int smallTop(int textY) {
        return textY - 1;
    }

    /** Draws the icon at full strength, {@code size} GUI pixels square. */
    public static void draw(GuiGraphics graphics, Skill skill, int x, int y, int size) {
        draw(graphics, skill, x, y, size, 255);
    }

    /**
     * Draws the icon with an alpha of 0 to 255, so it fades with the text it sits beside. Leaves
     * the blend switch and the shader colour as it found them: GUI code around it relies on both.
     */
    public static void draw(GuiGraphics graphics, Skill skill, int x, int y, int size, int alpha) {
        if (size <= 0 || alpha <= 8) {
            return;
        }
        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        if (!blendWasOn) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        float[] colour = RenderSystem.getShaderColor();
        float r = colour[0];
        float g = colour[1];
        float b = colour[2];
        float a = colour[3];
        if (alpha < 255) {
            RenderSystem.setShaderColor(r, g, b, a * (alpha / 255f));
        }
        graphics.blit(texture(skill), x, y, size, size, 0f, 0f, 16, 16, 16, 16);
        if (alpha < 255) {
            RenderSystem.setShaderColor(r, g, b, a);
        }
        if (!blendWasOn) {
            RenderSystem.disableBlend();
        }
    }
}
