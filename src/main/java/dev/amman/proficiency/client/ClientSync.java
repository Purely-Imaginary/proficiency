package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.client.Minecraft;

/** The server's copy of this player's skills, laid over the client's. Client only. */
public final class ClientSync {

    private static volatile boolean synced;

    private ClientSync() {
    }

    /** Whether the server has sent this player's skills since joining. Tooltips wait for it. */
    public static boolean isSynced() {
        return synced;
    }

    public static void reset() {
        synced = false;
    }

    public static void apply(PlayerSkills skills) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            ProficiencyAttachments.of(player).copyFrom(skills);
            synced = true;
        }
    }
}
