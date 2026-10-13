package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SpawnOrigin;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Night Watch, Homebuilder's row-4 keystone (docs/HOMEBUILDER-SPEC.md): siege mobs in range glow
 * for you, and you hear the siege horn an in-game hour before the siege comes.
 *
 * <ul>
 * <li><b>The glow is yours alone.</b> No effect is put on the mob (a Glowing effect would show to
 * everyone); this player is sent the mob's shared flags with the glowing bit set, once a second
 * while the siege runs and they are home. A mob that leaves range, or the end of the siege, gets its
 * real flags sent back. Vanilla resends the real flags whenever they change (a mob catching fire),
 * which hides the outline for up to a second.</li>
 * <li><b>The early horn</b> plays in the hour before the dusk the siege starts at, once per siege,
 * to Night Watch players at this Hearth or who fed it, wherever they are in its dimension.</li>
 * </ul>
 */
public final class NightWatch {

    /** Entity ids glowing for each player now. */
    private static final Map<UUID, IntSet> GLOWING = new ConcurrentHashMap<>();
    private static final String WARNED = "homebuilder.night_watch|";
    /** The vanilla shared-flags entry (the first data entry every entity defines) and its glowing bit. */
    private static final int SHARED_FLAGS_ID = 0;
    private static final byte GLOWING_BIT = 1 << 6;

    private NightWatch() {
    }

    /** {@code HearthHooks.HEARTH_TICK}, once a second per Hearth: the horn and the glow. */
    public static void onHearthSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        HearthState state = hearth.state();
        SiegeRules.Phase phase = state.siegePhase();
        if (phase == SiegeRules.Phase.IDLE || !ProficiencyConfig.enabled(Skill.HOMEBUILDER)) {
            return;
        }
        long now = level.getGameTime();
        // The area query runs only once a Night Watch player at this Hearth needs it, and once for all
        // of them: with nobody holding the talent a siege costs nothing here.
        List<Mob> mobs = null;
        for (ServerPlayer player : level.players()) {
            if (HomebuilderMath.rank(ProficiencyAttachments.of(player), HomebuilderMath.NIGHT_WATCH) <= 0) {
                continue;
            }
            boolean here = pos.equals(HearthRange.homeOf(player));
            if (phase == SiegeRules.Phase.COUNTDOWN && (here || fed(state, player))
                    && HomebuilderMath.nightWatchWarns(now, state.siegeDue(), level.getDayTime())) {
                warn(player, hearth.key(), state.siegeDue());
            }
            if (phase == SiegeRules.Phase.ACTIVE && here) {
                if (mobs == null) {
                    mobs = siegeMobs(level, pos);
                }
                glow(player, mobs);
            }
        }
    }

    private static boolean fed(HearthState state, ServerPlayer player) {
        return state.ledger().total(player.getUUID(), null, HearthLedger.Window.LIFETIME) > 0;
    }

    /** The early horn, once per siege (keyed by the Hearth and the siege's due time). */
    public static void warn(ServerPlayer player, String hearthKey, long due) {
        PlayerHearth data = HearthAttachments.of(player);
        String key = WARNED + hearthKey;
        if (data.mark(key, Long.MIN_VALUE) == due) {
            return;
        }
        data.setMark(key, due);
        player.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.5f, 0.85f);
        player.sendSystemMessage(Component.translatable("proficiency.homebuilder.night_watch.warning")
                .withStyle(ChatFormatting.GOLD));
    }

    /** Siege mobs alive inside this Hearth's range. */
    public static List<Mob> siegeMobs(ServerLevel level, BlockPos hearth) {
        HearthGeometry.Range range = HearthRange.range();
        AABB box = new AABB(hearth.getX() + 0.5 - range.radius(), hearth.getY() - range.below(),
                hearth.getZ() + 0.5 - range.radius(), hearth.getX() + 0.5 + range.radius(),
                hearth.getY() + range.above() + 1, hearth.getZ() + 0.5 + range.radius());
        return new ArrayList<>(level.getEntitiesOfClass(Mob.class, box,
                mob -> mob.isAlive() && SpawnOrigin.isSiege(mob) && HearthRange.inHouse(hearth, mob.position())));
    }

    /** Sends this player the glow for {@code mobs}, and the real flags back to mobs that left the set. */
    static void glow(ServerPlayer player, List<Mob> mobs) {
        IntSet before = GLOWING.computeIfAbsent(player.getUUID(), id -> new IntOpenHashSet());
        IntSet now = new IntOpenHashSet();
        for (Mob mob : mobs) {
            now.add(mob.getId());
            send(player, mob, true);
        }
        for (int id : before) {
            if (!now.contains(id)) {
                Entity gone = player.level().getEntity(id);
                if (gone != null) {
                    send(player, gone, false);
                }
            }
        }
        GLOWING.put(player.getUUID(), now);
    }

    /** Once a second for every player: a player who left the siege (or whose siege ended) loses the glow. */
    public static void tick(ServerPlayer player) {
        IntSet glowing = GLOWING.get(player.getUUID());
        if (glowing == null || glowing.isEmpty()) {
            return;
        }
        HearthBlockEntity home = HearthRange.homeEntity(player);
        if (home == null || home.state().siegePhase() != SiegeRules.Phase.ACTIVE
                || HomebuilderMath.rank(ProficiencyAttachments.of(player), HomebuilderMath.NIGHT_WATCH) <= 0) {
            glow(player, List.of());
        }
    }

    private static void send(ServerPlayer player, Entity entity, boolean glowing) {
        if (player.connection == null) {
            return;
        }
        byte flags = sharedFlags(entity);
        byte shown = glowing ? (byte) (flags | GLOWING_BIT) : flags;
        player.connection.send(new ClientboundSetEntityDataPacket(entity.getId(),
                List.of(new SynchedEntityData.DataValue<>(SHARED_FLAGS_ID, EntityDataSerializers.BYTE, shown))));
    }

    /** The entity's real shared flags (on fire, sneaking, invisible, glowing...). */
    static byte sharedFlags(Entity entity) {
        List<SynchedEntityData.DataValue<?>> values = entity.getEntityData().getNonDefaultValues();
        if (values != null) {
            for (SynchedEntityData.DataValue<?> value : values) {
                if (value.id() == SHARED_FLAGS_ID && value.value() instanceof Byte flags) {
                    return flags;
                }
            }
        }
        return 0;
    }

    /** What this player sees glowing now (GameTests). */
    public static IntSet glowingFor(ServerPlayer player) {
        IntSet set = GLOWING.get(player.getUUID());
        return set == null ? new IntOpenHashSet() : set;
    }

    static void forget(UUID player) {
        GLOWING.remove(player);
    }
}
