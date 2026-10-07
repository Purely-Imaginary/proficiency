package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RankBadges;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SurvivalStreak;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import dev.amman.proficiency.item.ProficiencyItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

import java.util.List;
import java.util.Map;

/** Keeping the client's copy honest, charging the death cost, and growing the survival streak. */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class PlayerLifecycleEvents {

    /** How often a changed skill set is pushed to its owner. Twice a second is plenty for a bar. */
    private static final int SYNC_INTERVAL_TICKS = 10;

    /** The streak is counted in whole seconds. */
    private static final int STREAK_INTERVAL_TICKS = 20;

    /** Death lines are capped: a progress wipe touches every skill with a partial bar. */
    private static final int DEATH_LINES_SHOWN = 5;

    private static int tickCounter;
    private static int streakCounter;

    private PlayerLifecycleEvents() {
    }

    /**
     * Your best skill, in the tab list.
     *
     * <p>The tab list and only the tab list. There was a PlayerEvent.NameFormat handler here too,
     * meant to put the badge over your head, and it never drew anything: NameFormat is called on
     * each VIEWING client against that client's own copy of the other player's attachment, and
     * attachments are synced to their owner alone. Every observer read zeroes, produced no badge,
     * and paid a 27-skill loop and a Component allocation per visible player per frame for it.
     *
     * <p>This one works because it runs server-side while the tab-list packet is built, so it reads
     * the real levels and ships already-rendered text to everyone, vanilla clients included.
     */
    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        event.setDisplayName(RankBadges.decorate(event.getEntity(), event.getDisplayName() != null
                ? event.getDisplayName()
                : event.getEntity().getName()));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ProficiencyNetwork.sendFullSync(player);
        // A new session has an empty journal on the client, even if the same UUID logged out a
        // moment ago on this very JVM (single player), so start the delta over.
        ProficiencyNetwork.forgetVisited(player.getUUID());
        ProficiencyNetwork.sendVisitedIfChanged(player);
        if (dev.amman.proficiency.config.ProficiencyConfig.xpFeedAtLogin(
                player.getGameProfile().getName())) {
            ProficiencyAttachments.of(player).setXpFeed(true);
        }

        // Until this, a new player had twenty-seven invisible percentages and no way to know the
        // system existed unless somebody told them. The compass is the one item that explains
        // itself by being used.
        if (ProficiencyAttachments.of(player).needsOnboarding()) {
            player.sendSystemMessage(Component.translatable("proficiency.welcome.title")
                    .withStyle(ChatFormatting.GOLD));
            // The keys as each client has them bound (Forge 1.20.1 defaults differ from master's).
            player.sendSystemMessage(Component.translatable("proficiency.welcome.body",
                            Component.keybind("key.proficiency.use_ability"),
                            Component.keybind("key.proficiency.open_skills"))
                    .withStyle(ChatFormatting.GRAY));
            player.getInventory().placeItemBackInInventory(
                    new ItemStack(ProficiencyItems.FORESTERS_COMPASS.get()));
        }
    }

    /** Closes any XP recording so the last lines reach the file. */
    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        dev.amman.proficiency.skill.XpFeedRecorder.stopAll();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ProficiencyNetwork.forgetVisited(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProficiencyNetwork.sendFullSync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ProficiencyNetwork.sendFullSync(player);
        }
    }

    /**
     * A respawned player is a brand new entity, so the skills have to be carried across by hand.
     * That is also where the death cost is charged — before the client is ever told the numbers,
     * so it never sees the pre-death values.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        // Forge invalidates the old body's capabilities before this event; bring them back long
        // enough to read, or every respawn would start from a blank skill sheet.
        event.getOriginal().reviveCaps();
        PlayerSkills carried;
        try {
            carried = event.getOriginal().getCapability(ProficiencyAttachments.SKILLS).orElse(null);
        } finally {
            event.getOriginal().invalidateCaps();
        }
        if (carried == null) {
            dev.amman.proficiency.Proficiency.LOG.error("No skills on the old body of {}; nothing carried",
                    event.getEntity().getGameProfile().getName());
            return;
        }
        PlayerSkills fresh = ProficiencyAttachments.of(event.getEntity());
        fresh.copyFrom(carried);

        if (!event.isWasDeath()) {
            return;
        }
        Map<Skill, Float> lost = fresh.applyDeathPenalty();
        int streakLost = SurvivalStreak.onDeath(fresh);
        // Unpaid Social XP is part of the bars a death wipes.
        dev.amman.proficiency.skill.SocialService.onDeath(event.getOriginal().getUUID());
        dev.amman.proficiency.skill.NightwalkerService.onDeath(event.getOriginal().getUUID());
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!lost.isEmpty()) {
            player.sendSystemMessage(Component.translatable("proficiency.death.penalty", lost.size())
                    .withStyle(ChatFormatting.RED));
            // Which skills, not just how many: the biggest bars first, and a count for the rest.
            List<Map.Entry<Skill, Float>> worst = lost.entrySet().stream()
                    .sorted(Map.Entry.<Skill, Float>comparingByValue().reversed())
                    .toList();
            worst.stream().limit(DEATH_LINES_SHOWN).forEach(entry -> player.sendSystemMessage(
                    Component.translatable("proficiency.death.line",
                                    Component.translatable(entry.getKey().translationKey()),
                                    Math.max(1, Math.round(entry.getValue() * 100f)))
                            .withStyle(ChatFormatting.DARK_RED)));
            if (worst.size() > DEATH_LINES_SHOWN) {
                player.sendSystemMessage(Component.translatable("proficiency.death.more",
                        worst.size() - DEATH_LINES_SHOWN).withStyle(ChatFormatting.DARK_RED));
            }
        }
        if (streakLost > 0) {
            player.sendSystemMessage(Component.translatable("proficiency.streak.lost",
                    streakLost, SurvivalStreak.percent(streakLost)).withStyle(ChatFormatting.RED));
        }
        if (!lost.isEmpty() || streakLost > 0) {
            player.playNotifySound(SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.35f, 1.6f);
        }
    }

    /** Pushes changed skill sets to their owners, batched rather than one packet per swing. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Belt and braces for the break context: it is matched on position so a stale one is
        // already harmless, but there is no reason to let one outlive the tick that made it.
        GatheringEvents.clearBreakContext();

        if (++streakCounter >= STREAK_INTERVAL_TICKS) {
            streakCounter = 0;
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                SurvivalStreak.tick(player, STREAK_INTERVAL_TICKS);
                // Social XP collected from shared work, paid in 5-second lumps.
                dev.amman.proficiency.skill.SocialService.tick(player);
                // Nightwalker: the dark-share pot, the outdoor night trickle, and Eclipse.
                dev.amman.proficiency.skill.NightwalkerService.tick(player);
            }
        }

        if (++tickCounter < SYNC_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;
        // Recording CSVs: at most one write a second, never per gain.
        dev.amman.proficiency.skill.XpFeedRecorder.flushDue();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PlayerSkills skills = ProficiencyAttachments.of(player);
            if (skills.isDirty()) {
                skills.clearDirty();
                ProficiencyNetwork.sendFullSync(player);
            }
            // Same cadence, its own small packet: the recent-XP list in the tree screen.
            ProficiencyNetwork.sendXpLogIfChanged(player);
            ProficiencyNetwork.sendXpFeed(player);
            ProficiencyNetwork.sendVisitedIfChanged(player);
        }
    }
}
