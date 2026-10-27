package dev.amman.proficiency.skill;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Codex on the server: awards a title when a skill's first-time kinds reach 10, 25, 50 or 100,
 * tells the player (toast and sound), tells everyone at 100, and backfills a veteran silently on
 * login. A title is cosmetic: nothing here touches XP, stats or the death rule.
 */
public final class CodexService {

    private CodexService() {
    }

    /** The first-time kind just marked visited for this skill: award any milestone it reached. */
    public static void onKindFound(ServerPlayer player, Skill skill) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        int count = skills.kindsFound(skill);
        for (int milestone : Codex.reached(count)) {
            Codex.Title title = new Codex.Title(skill, milestone);
            if (skills.markCodexSeen(title.id())) {
                announce(player, skills, title);
            }
        }
    }

    /** Veterans get the milestones they already earned, with no toast, no sound and no chat line. */
    public static void backfill(ServerPlayer player) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        for (Skill skill : Skill.VALUES) {
            int count = skills.kindsFound(skill);
            for (int milestone : Codex.reached(count)) {
                skills.markCodexSeen(new Codex.Title(skill, milestone).id());
            }
        }
    }

    private static void announce(ServerPlayer player, PlayerSkills skills, Codex.Title title) {
        // The toast: name = the title's key, xp = kinds found, so the client needs no new shape.
        ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.CODEX, titleKey(title), title.id(),
                title.skill(), title.kinds());
        if (title.kinds() >= Codex.ANNOUNCE_AT) {
            if (ProficiencyConfig.announceMilestones()) {
                player.server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("proficiency.announce.codex", player.getDisplayName(),
                                Component.translatable(title.skill().translationKey()), title.kinds(),
                                titleName(title)).withStyle(ChatFormatting.GOLD), false);
            }
        }
    }

    public static String titleKey(Codex.Title title) {
        return "proficiency.codex.title." + title.kinds();
    }

    /** "Mining Scholar": the title as text, skill name filled in. */
    public static Component titleName(Codex.Title title) {
        return Component.translatable(titleKey(title), Component.translatable(title.skill().translationKey()));
    }

    /** The picked title as text for the tab list, or null for none. */
    public static Component pickedName(PlayerSkills skills) {
        Codex.Title title = Codex.parse(skills.codexTitle());
        return title == null ? null : titleName(title);
    }

    /** Picks (or with "" clears) a title and refreshes the tab list. False when it is not earned. */
    public static boolean pick(ServerPlayer player, String id) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        if (!skills.setCodexTitle(id)) {
            return false;
        }
        ProficiencyNetwork.sendFullSync(player);
        refreshTabList(player);
        return true;
    }

    /** Re-sends the tab list name, after a pick or an admin reset. */
    public static void refreshTabList(ServerPlayer player) {
        player.refreshTabListName();
    }
}
