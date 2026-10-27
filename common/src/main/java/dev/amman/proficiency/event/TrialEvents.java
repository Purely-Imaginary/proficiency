package dev.amman.proficiency.event;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.net.DiscoveryPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.skill.PlacedBlocks;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.skill.SpawnOrigin;
import dev.amman.proficiency.skill.TrialRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Trial Chambers (1.21 only; the Forge 1.20.1 line has none). Three things, all fed by the trial
 * spawner and vault mixins:
 * <ul>
 * <li>Mobs a trial spawner makes are tagged ({@link SpawnOrigin#tagTrial}); Courage and Charger
 * multiply their XP by the {@code trial} factor, inside their own odds and first-blood rules.</li>
 * <li>A cleared wave (the spawner moving to its ejecting-reward state) pays each tracked player who
 * is still there and killed at least one of its mobs, in the combat skill they used most.</li>
 * <li>Opening a vault pays Wayfaring once per vault per player.</li>
 * </ul>
 * Guards: a spawner or vault a player placed pays nothing; a fake player pays nothing; a player who
 * stood by without a kill gets no wave bonus. The kill record lives in memory only: a restart in the
 * middle of a trial loses it and that wave pays nothing, which is the safe way to be wrong.
 */
public final class TrialEvents {

    public static final String SOURCE_WAVE = "proficiency.xplog.source.trial_wave";
    public static final String SOURCE_WAVE_OMINOUS = "proficiency.xplog.source.trial_wave_ominous";
    public static final String SOURCE_VAULT = "proficiency.xplog.source.trial_vault";
    public static final String SOURCE_VAULT_OMINOUS = "proficiency.xplog.source.trial_vault_ominous";

    /** A record older than this (game ticks, 30 minutes) is a wave that never finished. */
    private static final long STALE_TICKS = 36_000L;
    private static final int MAX_SPAWNERS = 64;

    private static final class Fight {
        long lastTick;
        /** Per player: kills of this spawner's mobs by skill ordinal. */
        final Map<UUID, int[]> kills = new HashMap<>();
    }

    private static final Map<String, Fight> FIGHTS = new HashMap<>();

    private TrialEvents() {
    }

    private static String key(ServerLevel level, long pos) {
        return level.dimension().location() + "|" + pos;
    }

    /** Whether a player placed the block here (the placed-block mark, or the Hearth's own mark). */
    private static boolean playerPlaced(ServerLevel level, BlockPos pos) {
        return PlacedBlocks.rawMarked(level, pos) || PlacedBlocks.rawComfortMarked(level, pos);
    }

    /** A trial spawner just made a mob. Called from the spawner mixin on the server thread. */
    public static void onSpawn(ServerLevel level, BlockPos spawnerPos, boolean ominous, UUID mobId) {
        Entity entity = level.getEntity(mobId);
        if (entity instanceof Mob mob) {
            SpawnOrigin.tagTrial(mob, ominous, playerPlaced(level, spawnerPos), spawnerPos.asLong());
        }
    }

    /** A player killed a trial mob with this skill: it counts toward that spawner's wave. */
    public static void noteKill(LivingEntity victim, Player player, Skill skill) {
        if (!SpawnOrigin.isTrial(victim) || !(victim.level() instanceof ServerLevel level)
                || SkillTools.isFakePlayer(player) || skill == null) {
            return;
        }
        long now = level.getGameTime();
        FIGHTS.values().removeIf(fight -> now - fight.lastTick > STALE_TICKS);
        String key = key(level, SpawnOrigin.trialSpawnerPos(victim));
        if (!FIGHTS.containsKey(key) && FIGHTS.size() >= MAX_SPAWNERS) {
            return;
        }
        Fight fight = FIGHTS.computeIfAbsent(key, k -> new Fight());
        fight.lastTick = now;
        fight.kills.computeIfAbsent(player.getUUID(), id -> new int[Skill.values().length])[skill.ordinal()]++;
    }

    /**
     * The spawner just moved to its ejecting-reward state with these players tracked. Pays the ones who
     * are present and fought. Returns how many were paid (for a GameTest).
     */
    public static int onWaveCleared(ServerLevel level, BlockPos pos, boolean ominous, int range,
            Set<UUID> tracked) {
        Fight fight = FIGHTS.remove(key(level, pos.asLong()));
        if (fight == null || playerPlaced(level, pos)) {
            return 0;
        }
        double base = ProficiencyConfig.trialWaveXp();
        int paid = 0;
        for (UUID id : tracked) {
            int[] counts = fight.kills.get(id);
            if (counts == null || !(level.getPlayerByUUID(id) instanceof ServerPlayer player)
                    || !player.isAlive() || player.isSpectator() || SkillTools.isFakePlayer(player)
                    || player.distanceToSqr(pos.getCenter()) > (double) range * range) {
                continue;
            }
            int best = TrialRules.mostUsed(counts);
            if (best < 0) {
                continue;
            }
            int kills = 0;
            for (int count : counts) {
                kills += count;
            }
            double xp = TrialRules.waveXp(base, kills, ominous);
            if (xp > 0 && SkillService.grant(player, Skill.values()[best], xp,
                    ominous ? SOURCE_WAVE_OMINOUS : SOURCE_WAVE) > 0f) {
                paid++;
            }
        }
        return paid;
    }

    /**
     * A player's key just opened a vault. Pays Wayfaring once per vault per player, and shows the
     * first-time banner. Returns the XP paid.
     */
    public static float onVaultOpened(ServerLevel level, BlockPos pos, boolean ominous, Player who) {
        if (!(who instanceof ServerPlayer player) || SkillTools.isFakePlayer(player) || player.isSpectator()
                || playerPlaced(level, pos)) {
            return 0f;
        }
        double xp = TrialRules.vaultXp(ProficiencyConfig.trialVaultXp(), ominous);
        if (!(xp > 0)
                || !ProficiencyAttachments.of(player).markVisited("vault:" + level.dimension().location()
                        + ":" + pos.asLong())) {
            return 0f;
        }
        float paid = SkillService.grant(player, Skill.WAYFARING, xp, ominous ? SOURCE_VAULT_OMINOUS : SOURCE_VAULT);
        ProficiencyNetwork.sendDiscovery(player, DiscoveryPayload.FIRST, "block.minecraft.vault",
                "block.minecraft.vault", Skill.WAYFARING, paid);
        return paid;
    }

    /** Drops what a player left behind. */
    public static void forget(UUID player) {
        for (Fight fight : FIGHTS.values()) {
            fight.kills.remove(player);
        }
    }
}
