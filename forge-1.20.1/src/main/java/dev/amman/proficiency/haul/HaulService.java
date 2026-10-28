package dev.amman.proficiency.haul;

import com.mojang.serialization.Dynamic;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.HaulRequestPayload;
import dev.amman.proficiency.net.HaulNetwork;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Today's Haul, server side (1.44.0). The Forge 1.20.1 copy: the player's free-form tag is Forge's own
 * {@code getPersistentData}, and the card goes on {@link HaulNetwork}. One {@link HaulLog} per online player, fed from
 * the XP grant, the Tempo announce, a synergy waking, a discovery and a Hearth credit, and saved in the
 * player's own data under {@link #TAG} after every change, so it survives a relog (and a death) until the card is shown. The card goes
 * out when the client asks from the bed screen's button ({@link HaulRequestPayload}) or on {@code /proficiency haul}, and the log
 * starts over then. Every hook is wrapped: the haul is a summary and must never cost a grant.
 */
public final class HaulService {

    /** The player's saved log, a compound in the free-form entity tag. */
    public static final String TAG = "proficiency_haul";

    private static final Map<UUID, HaulLog> LOGS = new HashMap<>();

    private HaulService() {
    }

    // ---- The log ---------------------------------------------------------------------------------

    /** The player's log, loaded from their data the first time it is asked for. */
    public static HaulLog log(ServerPlayer player) {
        return LOGS.computeIfAbsent(player.getUUID(), id -> load(player));
    }

    private static HaulLog load(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG, Tag.TAG_COMPOUND)) {
            return new HaulLog();
        }
        return HaulLog.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, data.getCompound(TAG)))
                .resultOrPartial(error -> Proficiency.LOG.warn("Could not read the haul of {}: {}",
                        player.getGameProfile().getName(), error))
                .orElseGet(HaulLog::new);
    }

    /** What the player's data holds right now, read fresh (for the GameTests: what a relog would load). */
    public static HaulLog readSaved(Player player) {
        return load(player);
    }

    private static void save(ServerPlayer player, HaulLog log) {
        CompoundTag data = player.getPersistentData();
        if (log.isEmpty()) {
            data.remove(TAG);
            return;
        }
        HaulLog.CODEC.encodeStart(NbtOps.INSTANCE, log)
                .resultOrPartial(error -> Proficiency.LOG.warn("Could not save the haul: {}", error))
                .ifPresent(tag -> data.put(TAG, tag));
    }

    private static boolean recording(ServerPlayer player) {
        return ProficiencyConfig.haulEnabled() && !SkillTools.isFakePlayer(player);
    }

    /** A skill's bar in level units, as the card draws it. */
    public static double units(PlayerSkills skills, Skill skill) {
        return HaulMath.units(skills.level(skill), skills.progress(skill), skills.stars(skill), Mastery.maxStars(),
                skills.starProgress(skill));
    }

    // ---- Hooks -----------------------------------------------------------------------------------

    /**
     * A paid grant, from {@code SkillService.grantOnce} after the XP went in: {@code unitsBefore} is where the bar
     * stood before, {@code levelBefore} the level before.
     */
    public static void onGrant(ServerPlayer player, Skill skill, float banked, double unitsBefore, int levelBefore,
            int levels, int stars) {
        try {
            if (!recording(player)) {
                return;
            }
            PlayerSkills skills = ProficiencyAttachments.of(player);
            int points = Math.max(0, skills.pointsEarned(skill)
                    - levelBefore / dev.amman.proficiency.perk.Talents.LEVELS_PER_POINT);
            HaulLog log = log(player);
            if (log.onGrant(skill, banked, (float) unitsBefore, levels, stars, points, System.currentTimeMillis())) {
                save(player, log);
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.debug("haul: grant not recorded", e);
        }
    }

    /** A Tempo chain the client was just told about. */
    public static void onTempo(ServerPlayer player, Skill skill, int steps, int permille) {
        try {
            if (recording(player)) {
                HaulLog log = log(player);
                if (log.onTempo(skill, steps, permille, System.currentTimeMillis())) {
                    save(player, log);
                }
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.debug("haul: tempo not recorded", e);
        }
    }

    /** A first-time discovery (a new kind, biome, dimension or structure), from the discovery banner's send. */
    public static void onFound(ServerPlayer player) {
        try {
            if (recording(player)) {
                HaulLog log = log(player);
                log.onFound(System.currentTimeMillis());
                save(player, log);
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.debug("haul: discovery not recorded", e);
        }
    }

    /** A synergy woke up for the player. */
    public static void onSynergy(ServerPlayer player, String id) {
        try {
            if (recording(player)) {
                HaulLog log = log(player);
                if (log.onSynergy(id, System.currentTimeMillis())) {
                    save(player, log);
                }
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.debug("haul: synergy not recorded", e);
        }
    }

    /** Hearth charge credited to {@code player} (online players only; a credit for someone away is skipped). */
    public static void onCharge(MinecraftServer server, UUID player, double amount) {
        try {
            if (server == null || player == null) {
                return;
            }
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            if (online != null && recording(online)) {
                HaulLog log = log(online);
                if (log.onCharge(amount, System.currentTimeMillis())) {
                    save(online, log);
                }
            }
        } catch (RuntimeException e) {
            Proficiency.LOG.debug("haul: charge not recorded", e);
        }
    }

    // ---- Lifecycle -------------------------------------------------------------------------------

    /** Login: load the saved log and say hello to a client that has the card (it may show a reminder). */
    public static void onLogin(ServerPlayer player) {
        LOGS.remove(player.getUUID());
        if (!ProficiencyConfig.haulEnabled()) {
            return;
        }
        HaulLog log = log(player);
        HaulNetwork.send(player, summary(player, log, dev.amman.proficiency.haul.HaulSummary.LOGIN));
    }

    public static void onLogout(UUID player) {
        LOGS.remove(player);
    }

    /** A respawn or a return from the End makes a new player entity: carry the saved log across. */
    public static void onClone(Player original, Player fresh) {
        CompoundTag from = original.getPersistentData();
        if (from.contains(TAG, Tag.TAG_COMPOUND)) {
            fresh.getPersistentData().put(TAG, from.getCompound(TAG).copy());
        }
    }

    // ---- Showing it ------------------------------------------------------------------------------

    private static HaulSummary summary(ServerPlayer player, HaulLog log, int mode) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        return log.summary(mode, skill -> units(skills, skill), System.currentTimeMillis());
    }

    /** The card, and the log starts over. Null when there is nothing to show. */
    public static HaulSummary take(ServerPlayer player) {
        HaulLog log = log(player);
        if (log.isEmpty()) {
            return null;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        HaulSummary summary = log.take(skill -> units(skills, skill), System.currentTimeMillis());
        save(player, log);
        return summary;
    }

    /**
     * The client asked. {@link HaulRequestPayload#PEEK} (in bed): a hello-mode summary when there is something unseen,
     * nothing otherwise, and the log is kept. {@link HaulRequestPayload#OPEN} (the bed button) and
     * {@link HaulRequestPayload#KEY} (the login reminder's key): the card, and the log starts over. The old
     * {@link HaulRequestPayload#BED} is ignored: lying down never opens a card by itself.
     */
    public static void onRequest(ServerPlayer player, int reason) {
        if (reason == HaulRequestPayload.BED) {
            return;
        }
        if (!ProficiencyConfig.haulEnabled()) {
            if (reason == HaulRequestPayload.KEY) {
                player.displayClientMessage(Component.translatable("proficiency.haul.off"), true);
            }
            return;
        }
        if (!HaulNetwork.canReceive(player)) {
            return;
        }
        if (reason == HaulRequestPayload.PEEK) {
            HaulLog log = log(player);
            if (!log.isEmpty()) {
                HaulNetwork.send(player, summary(player, log, dev.amman.proficiency.haul.HaulSummary.LOGIN));
            }
            return;
        }
        HaulSummary summary = take(player);
        if (summary == null) {
            if (reason == HaulRequestPayload.KEY) {
                player.displayClientMessage(Component.translatable("proficiency.haul.nothing"), true);
            }
            return;
        }
        HaulNetwork.send(player, summary);
    }

    /**
     * {@code /proficiency haul}: the card, or the totals in chat for a client without it. Returns the command's
     * result (1 when something was shown).
     */
    public static int command(ServerPlayer player) {
        if (!ProficiencyConfig.haulEnabled()) {
            player.sendSystemMessage(Component.translatable("proficiency.haul.off").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        HaulSummary summary = take(player);
        if (summary == null) {
            player.sendSystemMessage(Component.translatable("proficiency.haul.nothing").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        if (HaulNetwork.canReceive(player)) {
            HaulNetwork.send(player, summary);
        } else {
            player.sendSystemMessage(Component.translatable("proficiency.haul.text",
                    Math.round(summary.totalXp()), summary.totalLevels(), summary.points(),
                    Component.translatable(summary.rating().translationKey())).withStyle(ChatFormatting.GOLD));
        }
        return 1;
    }

    /**
     * Op test tool ({@code /proficiency haul seed}): adds a Tempo chain, discoveries, a synergy and Hearth charge to
     * the player's log, so a card can be looked at without a play session. XP comes from {@code addxp}.
     */
    public static void seed(ServerPlayer player, Skill tempoSkill) {
        HaulLog log = log(player);
        long now = System.currentTimeMillis();
        log.onTempo(tempoSkill, 18, 1360, now);
        log.onFound(now);
        log.onFound(now);
        log.onFound(now);
        log.onSynergy("haul_seed_a", now);
        log.onCharge(46.5, now);
        save(player, log);
    }
}
