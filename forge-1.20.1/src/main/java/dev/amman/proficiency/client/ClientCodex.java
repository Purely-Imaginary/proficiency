package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** The Codex as the client shows it: counts from the synced visited keys, the title from the sync. */
public final class ClientCodex {

    private ClientCodex() {
    }

    public static int kinds(Skill skill) {
        java.util.Collection<String> visited = ClientVisited.visited();
        // The picker asks for 35 counts a frame: rescan only when the synced set changed.
        if (visited != cacheSource || visited.size() != cacheSize) {
            cacheSource = visited;
            cacheSize = visited.size();
            java.util.Arrays.fill(cache, -1);
        }
        int i = skill.ordinal();
        if (cache[i] < 0) {
            cache[i] = Codex.count(visited, skill);
        }
        return cache[i];
    }

    private static java.util.Collection<String> cacheSource;
    private static int cacheSize = -1;
    private static final int[] cache = new int[Skill.VALUES.length];

    /** "Kinds found: 7, next milestone at 10", or the finished form once all four are earned. */
    public static Component line(Skill skill) {
        return line(kinds(skill));
    }

    public static Component line(int found) {
        int next = Codex.next(found);
        return next == 0 ? Component.translatable("proficiency.codex.kinds_found_done", found)
                : Component.translatable("proficiency.codex.kinds_found", found, next);
    }

    /** The id of the title the player has picked, "" for none. */
    public static String picked() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return "";
        }
        PlayerSkills skills = ProficiencyAttachments.of(minecraft.player);
        return skills.codexTitle();
    }

    /** Asks the server to pick (or with "" clear) a title: the same command a player can type. */
    public static void pick(String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && !id.equals(picked())) {
            minecraft.player.connection.sendCommand(id.isEmpty() ? "skills title clear" : "skills title set " + id);
        }
    }
}
