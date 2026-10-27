package dev.amman.proficiency.skill;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthEvents;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.world.level.GameRules;

/**
 * Awards the Proficiency advancement tab (docs/ADVANCEMENTS.md, rules in core {@link Achievements}).
 * Each advancement has one criterion, {@code event}, of the vanilla {@code minecraft:impossible}
 * trigger, and this class grants it by name, so datapacks and FTB Quests see ordinary advancements.
 * Nothing here may cost a player XP: every entry point swallows its own failures.
 */
public final class ProficiencyAdvancements {

    static final String CRITERION = "event";

    private ProficiencyAdvancements() {
    }

    /** Listens for the Hearth's milestones. Called once from {@code Proficiency.init}. */
    public static void init() {
        HearthEvents.STAGE_SEALED.add(e -> stageSealed(e.player(), e.stage()));
        HearthEvents.SIEGE_DEFENDED.add(e -> {
            // A level-N defence also stands for the lower horns, as stage N stands for 1..N.
            int top = Achievements.siegeLevelOf(e.siegeLevel());
            for (ServerPlayer player : e.present()) {
                for (int i = 1; i <= top; i++) {
                    award(player, Achievements.siege(i));
                }
            }
        });
        HearthEvents.FEAST.add(e -> e.eaters().forEach(p -> award(p, Achievements.FEAST)));
        HearthEvents.TALES_PAID.add(e -> award(e.player(), Achievements.TALES));
        HearthEvents.CHRONICLE_WRITTEN.add(e -> award(e.author(), Achievements.CHRONICLE));
    }

    /** Stage N sealed means stages 1..N stand, so a hearth built before the tab still counts. */
    private static void stageSealed(ServerPlayer player, int stage) {
        for (int i = 1; i <= Math.min(stage, Achievements.STAGES); i++) {
            award(player, Achievements.stage(i));
        }
    }

    /** Grants everything the saved state already earns, with a toast for each. */
    public static void check(ServerPlayer player) {
        try {
            for (String id : Achievements.earned(ProficiencyAttachments.of(player))) {
                grant(player, id);
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.warn("Proficiency advancement check failed", e);
        }
    }

    /** Only the places count can change on its own; one cheap test every discovery tick. */
    public static void checkPlaces(ServerPlayer player) {
        // The edge only: a veteran past 20 is backfilled on login, so no lookups on every later tick.
        if (ProficiencyAttachments.of(player).placesSeen() == Achievements.PLACES) {
            award(player, Achievements.PLACES_ID);
        }
    }

    /**
     * Login: veterans get what they already earned, silently (no toast, no chat line), from the
     * saved state and from the Hearth milestones that Homebuilder recorded. The chat line is held back
     * by switching the announce game rule off for the call, and the client shows no toast for the
     * first advancement packet of a session, which is the one that carries these.
     */
    public static void backfill(ServerPlayer player) {
        GameRules.BooleanValue announce = player.serverLevel().getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS);
        boolean announced = announce.get();
        try {
            // award() is what makes the tab and its visibility right; the chat line is a game rule and
            // the toast is skipped by the client for the first packet of a session, so only the rule needs a hold.
            announce.set(false, player.server);
            for (String id : Achievements.earned(ProficiencyAttachments.of(player))) {
                grant(player, id);
            }
            int topStage = 0;
            boolean feast = false;
            boolean tales = false;
            boolean chronicle = false;
            boolean siege = false;
            for (String key : HearthAttachments.of(player).milestoneKeys()) {
                topStage = Math.max(topStage, Achievements.stageOfMilestone(key));
                feast |= key.endsWith("|feast");
                tales |= key.endsWith("|tales");
                chronicle |= key.endsWith("|chronicle");
                siege |= Achievements.isSiegeMilestone(key);
            }
            for (int i = 1; i <= topStage; i++) {
                grant(player, Achievements.stage(i));
            }
            if (siege) {
                grant(player, Achievements.siege(1));
            }
            if (feast) {
                grant(player, Achievements.FEAST);
            }
            if (tales) {
                grant(player, Achievements.TALES);
            }
            if (chronicle) {
                grant(player, Achievements.CHRONICLE);
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.warn("Proficiency advancement backfill failed", e);
        } finally {
            announce.set(announced, player.server);
        }
    }

    /** "Only the Bars": the death emptied {@code barsLost} bars and no level fell. Call after the penalty ran. */
    public static void died(ServerPlayer player, int barsLost) {
        if (Achievements.onlyTheBars(ProficiencyAttachments.of(player).totalLevels(), barsLost)) {
            award(player, Achievements.DEATH);
        }
    }

    /** Awards one advancement now, with its toast. A no-op when it is held or the datapack lacks it. */
    public static void award(ServerPlayer player, String id) {
        try {
            grant(player, id);
        } catch (RuntimeException e) {
            Proficiency.LOG.warn("Proficiency advancement {} failed", id, e);
        }
    }

    /** Grants one advancement by name; backfill holds the announce rule around its calls. */
    private static void grant(ServerPlayer player, String id) {
        if (FakeActors.isFake(player) || player.server == null || player.getAdvancements() == null) {
            return;
        }
        Advancement holder = player.server.getAdvancements().getAdvancement(Proficiency.id(id));
        if (holder == null) {
            return;
        }
        PlayerAdvancements advancements = player.getAdvancements();
        AdvancementProgress progress = advancements.getOrStartProgress(holder);
        if (progress.isDone()) {
            return;
        }
        advancements.award(holder, CRITERION);
    }
}
