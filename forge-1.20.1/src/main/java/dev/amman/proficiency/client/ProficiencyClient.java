package dev.amman.proficiency.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * The two keys. Master's defaults are K and G; on Forge 1.20.1 for the Reclamation pack they are
 * I and ` (grave accent), because in that pack K is Iris/Oculus shaders, Crafting Tweaks and Quark
 * rotation lock, and G is Curios, GuideME, Ars Nouveau, Building Gadgets and Mekanism. I and `
 * are bound by nothing in the pack. Both stay rebindable under Controls, Miscellaneous.
 */
public final class ProficiencyClient {

    public static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.proficiency.open_skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.categories.misc");

    public static final KeyMapping USE_ABILITY = new KeyMapping(
            "key.proficiency.use_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.misc");

    private ProficiencyClient() {
    }
}
