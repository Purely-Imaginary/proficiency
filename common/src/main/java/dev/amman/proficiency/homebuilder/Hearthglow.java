package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.charge.ChargeRules;
import dev.amman.proficiency.hearth.charge.HearthCharge;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.ProcService;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTools;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hearthglow, Homebuilder's signature (docs/HOMEBUILDER-SPEC.md): a grant earned at home that fed
 * the Hearth rolls it, at most once per 10 s; a hit adds that grant's mirror to the same pool again,
 * times proc power (Ember Heart), through the Hearth's hourly cap. So it fills a pool faster and never
 * raises a ceiling. The usual proc moment plays: the name on the action bar, the Construction chime,
 * and the ember puff ({@code ProcFx}) on the Hearth itself.
 *
 * <ul>
 * <li>Only a grant the Hearth mirrors counts, by the charge lane's own rule ({@link ChargeRules#route}
 * is {@code POOL}): real play, not a first-time line, a skill a pool is fed by (combat feeds only
 * through siege kills, which this does not double).</li>
 * <li>The charge goes in through {@link HearthCharge#credit}, like every other charge: the hourly
 * cap, the ledger cell under the earning skill, {@code hearth_charge} telemetry, the tracker's dots
 * and the tier watch.</li>
 * <li>Masonry and Decorating are left out: their mirror is held 10 minutes against the shuffle farm,
 * and a second copy straight into the pool would skip that hold.</li>
 * <li>House Proud makes every eligible roll land, like every ability; the 10 s still holds.</li>
 * </ul>
 */
public final class Hearthglow {

    /** Game time of each player's last roll. */
    private static final Map<UUID, Long> LAST_ROLL = new ConcurrentHashMap<>();

    private Hearthglow() {
    }

    /** {@code HearthHooks.GRANT}, after the charge lane's mirror (registered after it). */
    static void onGrant(HearthHooks.Grant grant) {
        if (grant.skill() == Skill.HOMEBUILDER || SkillTools.isFakePlayer(grant.player())
                || ChargeRules.route(grant.skill(), grant.source(), grant.play(), grant.firstTime())
                        != ChargeRules.Route.POOL
                || !ProficiencyConfig.enabled(Skill.HOMEBUILDER) || !ProficiencyConfig.procsEnabled()) {
            return;
        }
        ServerPlayer player = grant.player();
        HearthBlockEntity home = HearthRange.homeEntity(player);
        if (home == null || home.state().smouldering()) {
            return;
        }
        HearthData data = Hearths.data();
        HearthPool pool = ChargeRules.pool(grant.skill(), data);
        if (pool == null || data.pool(pool) == null || data.pool(pool).holdSeconds() > 0) {
            return;
        }
        double mirror = ChargeRules.mirror(grant.beforeRested(), data);
        long now = player.level().getGameTime();
        if (!(mirror > 0) || home.state().cap(pool).room(now, data.hourlyCap()) <= 0) {
            // The hourly cap is full: this grant fed nothing, so there is nothing to glow about.
            return;
        }
        Long last = LAST_ROLL.get(player.getUUID());
        if (last != null && now >= last && now - last < HomebuilderMath.HEARTHGLOW_COOLDOWN_TICKS) {
            return;
        }
        // The gate counts attempts, not hits, as Social's Good Company does: a missed roll also waits
        // 10 s. So the rate is one roll per 10 s at home and Kindling's chance moves it in step
        // (20% to 30% at full Kindling: one Hearthglow per ~50 s to one per ~33 s). Gating only the hits would make
        // a busy player's grants (several a second) land one nearly every 10 s whatever the chance,
        // and Kindling would buy almost nothing.
        LAST_ROLL.put(player.getUUID(), now);
        if (!ProcService.fire(player, Skill.HOMEBUILDER, home.getBlockPos())) {
            return;
        }
        double added = HearthCharge.credit(player.serverLevel(), home, player, pool, grant.skill().id(),
                mirror * ProcService.power(player, Skill.HOMEBUILDER), true, grant.skill());
        if (added > 0) {
            // A soft crackle at the Hearth for everyone near it, on top of the proc's own chime.
            player.serverLevel().playSound(null, home.getBlockPos(), SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS, 1.0f, 1.2f);
        }
    }

    static void forget(UUID player) {
        LAST_ROLL.remove(player);
    }
}
