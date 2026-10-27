package dev.amman.proficiency.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ProficiencyClient {

    public static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.proficiency.open_skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.misc");

    public static final KeyMapping USE_ABILITY = new KeyMapping(
            "key.proficiency.use_ability",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.misc");

    /**
     * Holds the Waywheel open under the MODERN style (1.33.0). Unbound by default: under MODERN, holding the
     * ability key opens the same wheel; this key is for a player who wants the wheel on its own key.
     */
    public static final KeyMapping WAYWHEEL = new KeyMapping(
            "key.proficiency.waywheel",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.categories.misc");

    private ProficiencyClient() {
    }
}
