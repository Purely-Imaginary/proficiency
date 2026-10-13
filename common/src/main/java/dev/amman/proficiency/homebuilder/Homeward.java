package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.homebuilder.net.HomebuilderNet;
import dev.amman.proficiency.homebuilder.net.HomebuilderPayload;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.entity.living.LivingDamageEvent;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Homeward, Homebuilder's capstone (docs/HOMEBUILDER-SPEC.md): once per in-game day, a 10 s
 * channel takes you home to the Hearth you fed most. Damage or moving breaks it. For a minute after
 * a Hearth's bell called you home the channel is 5 s.
 *
 * <p>It shortens the walk home and keeps the risk: banked Tales are paid by the Tales lane when you
 * arrive in range, not when you start, and a death before the channel ends loses them as every
 * death does. Telemetry {@code homeward} counts uses and the distance saved, to see whether it makes
 * the trip back too cheap.
 *
 * <p>"The Hearth you fed most" reads the Hearth ledgers: while you are home, the per-player second
 * copies your lifetime total there into your Hearth data (a mark per Hearth, every 10 s), so the
 * choice works with every Hearth unloaded. A Hearth that is gone from the index is dropped.
 */
public final class Homeward {

    /** Mark prefix in {@code PlayerHearth.marks}: {@code homebuilder.fed|<hearth key>} = lifetime fed x 100. */
    public static final String FED_PREFIX = "homebuilder.fed|";
    /** Mark in {@code PlayerHearth.marks}: the game time of the last arrival (the day rule's fallback). */
    public static final String AT_MARK = "homebuilder.homeward_at";
    /** Copy the ledger total this often while home (seconds of the per-player loop). */
    private static final int FED_EVERY_SECONDS = 10;

    /** A channel in progress. */
    record Running(HomebuilderMath.Channel channel, String target, boolean bell) {
    }

    private static final Map<UUID, Running> RUNNING = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> HURT = new ConcurrentHashMap<>();

    private Homeward() {
    }

    // ---- Starting --------------------------------------------------------------------------------

    /** Starts the channel, or says why not. Returns null when it started. */
    @Nullable
    public static Component start(ServerPlayer player) {
        if (!ProficiencyConfig.enabled(Skill.HOMEBUILDER) || !ProficiencyConfig.hearthEnabled()) {
            return Component.translatable("proficiency.homebuilder.homeward.off");
        }
        if (HomebuilderMath.rank(ProficiencyAttachments.of(player), HomebuilderMath.HOMEWARD) <= 0) {
            return Component.translatable("proficiency.homebuilder.homeward.locked");
        }
        if (!player.isAlive() || player.isSpectator()) {
            return Component.translatable("proficiency.homebuilder.homeward.off");
        }
        if (RUNNING.containsKey(player.getUUID())) {
            return Component.translatable("proficiency.homebuilder.homeward.running");
        }
        PlayerHearth data = HearthAttachments.of(player);
        if (!ready(player)) {
            return Component.translatable("proficiency.homebuilder.homeward.used");
        }
        String target = target(player);
        if (target == null) {
            return Component.translatable("proficiency.homebuilder.homeward.none");
        }
        BlockPos home = HearthRange.homeOf(player);
        if (home != null && target.equals(dev.amman.proficiency.hearth.HearthBlockEntity.key(player.level(), home))) {
            return Component.translatable("proficiency.homebuilder.homeward.already");
        }
        long now = player.level().getGameTime();
        boolean bell = data.heardBell(now);
        int ticks = bell ? Math.max(20, (int) Math.round(Hearths.data().bell().homewardChannelSeconds() * 20))
                : HomebuilderMath.HOMEWARD_TICKS;
        RUNNING.put(player.getUUID(), new Running(
                new HomebuilderMath.Channel(player.getX(), player.getY(), player.getZ(), now, ticks), target, bell));
        HURT.remove(player.getUUID());
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.3f);
        player.displayClientMessage(Component.translatable(bell ? "proficiency.homebuilder.homeward.start_bell"
                : "proficiency.homebuilder.homeward.start", (ticks + 19) / 20).withStyle(ChatFormatting.GOLD), true);
        HomebuilderNet.send(player, new HomebuilderPayload(HomebuilderPayload.CHANNEL, now, ticks, ""));
        return null;
    }

    /** Whether a channel runs for this player. */
    public static boolean channelling(ServerPlayer player) {
        return RUNNING.containsKey(player.getUUID());
    }

    /** The in-game day Homeward counts in: the overworld's, so every dimension agrees. */
    public static long today(MinecraftServer server) {
        return HomebuilderMath.day(server.overworld().getDayTime());
    }

    /**
     * Whether this player's Homeward is ready: not used on this in-game day, or a full day of game
     * time ago (so a world with the daylight cycle off still gets it back once a day).
     */
    public static boolean ready(ServerPlayer player) {
        PlayerHearth data = HearthAttachments.of(player);
        return HomebuilderMath.homewardReady(data.homewardDay(), today(player.server),
                data.mark(AT_MARK, Long.MIN_VALUE), player.server.overworld().getGameTime());
    }

    // ---- The channel ------------------------------------------------------------------------------

    /** Every server tick: step each channel. */
    static void tickAll(MinecraftServer server) {
        if (RUNNING.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Running> entry : List.copyOf(RUNNING.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Logged out: the channel is gone.
                RUNNING.remove(entry.getKey());
                HURT.remove(entry.getKey());
                continue;
            }
            if (!player.isAlive()) {
                // Died (the death hook normally got here first): the channel breaks, and a death
                // loses the Tales as every death does.
                cancel(player, HomebuilderPayload.END_HURT);
                continue;
            }
            step(player, entry.getValue());
        }
    }

    private static void step(ServerPlayer player, Running running) {
        long now = player.level().getGameTime();
        boolean hurt = HURT.remove(player.getUUID()) != null;
        HomebuilderMath.Step step = running.channel().step(now, player.getX(), player.getY(), player.getZ(), hurt);
        switch (step) {
            case RUNNING -> {
                if ((now - running.channel().start()) % 10 == 0) {
                    player.serverLevel().sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 0.1,
                            player.getZ(), 3, 0.35, 0.05, 0.35, 0.01);
                }
            }
            case MOVED, HURT -> cancel(player, step == HomebuilderMath.Step.HURT
                    ? HomebuilderPayload.END_HURT : HomebuilderPayload.END_MOVED);
            case ARRIVED -> arrive(player, running);
        }
    }

    /** Breaks the channel with a reason ({@code HomebuilderPayload.END_*}). */
    static void cancel(ServerPlayer player, int reason) {
        if (RUNNING.remove(player.getUUID()) == null) {
            return;
        }
        HURT.remove(player.getUUID());
        player.displayClientMessage(Component.translatable(reason == HomebuilderPayload.END_HURT
                ? "proficiency.homebuilder.homeward.hurt" : "proficiency.homebuilder.homeward.moved")
                .withStyle(ChatFormatting.RED), true);
        player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4f, 1.4f);
        HomebuilderNet.send(player, new HomebuilderPayload(HomebuilderPayload.CHANNEL_END, 0L, reason, ""));
        TelemetryService.hearth(player, Telemetry.HOMEWARD, "cancel",
                reason == HomebuilderPayload.END_HURT ? "hurt" : "moved", 1.0);
    }

    /**
     * The shared {@code LivingDamageEvent.Post}: a hit that really landed breaks a running channel.
     * Post fires only after every handler had its say, so a hit something cancelled (Scaffolder's
     * soft fall, another mod), a shield took whole, or the hurt cooldown swallowed never gets here
     * or arrives as 0. A hit the absorption hearts took counts: it landed.
     */
    static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && RUNNING.containsKey(player.getUUID())
                && (event.getNewDamage() > 0 || event.getReduction(DamageContainer.Reduction.ABSORPTION) > 0)) {
            HURT.put(player.getUUID(), Boolean.TRUE);
        }
    }

    private static void arrive(ServerPlayer player, Running running) {
        RUNNING.remove(player.getUUID());
        Target target = resolve(player.server, running.target());
        if (target == null) {
            player.displayClientMessage(Component.translatable("proficiency.homebuilder.homeward.none")
                    .withStyle(ChatFormatting.RED), true);
            HomebuilderNet.send(player, new HomebuilderPayload(HomebuilderPayload.CHANNEL_END, 0L,
                    HomebuilderPayload.END_MOVED, ""));
            return;
        }
        BlockPos spot = landing(target.level(), target.pos());
        boolean sameLevel = target.level() == player.level();
        double saved = sameLevel ? Math.sqrt(Math.pow(player.getX() - spot.getX(), 2)
                + Math.pow(player.getZ() - spot.getZ(), 2)) : 0.0;
        player.serverLevel().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, player.getX(), player.getY() + 0.5,
                player.getZ(), 6, 0.3, 0.4, 0.3, 0.01);
        player.teleportTo(target.level(), spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot(),
                player.getXRot());
        player.resetFallDistance();
        PlayerHearth data = HearthAttachments.of(player);
        data.setHomewardDay(today(player.server));
        data.setMark(AT_MARK, player.server.overworld().getGameTime());
        target.level().playSound(null, spot, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0f, 0.8f);
        target.level().playSound(null, target.pos(), SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS, 1.5f, 1.0f);
        target.level().sendParticles(ParticleTypes.FLAME, spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5,
                12, 0.4, 0.1, 0.4, 0.02);
        player.displayClientMessage(Component.translatable("proficiency.homebuilder.homeward.arrived")
                .withStyle(ChatFormatting.GOLD), true);
        HomebuilderNet.send(player, new HomebuilderPayload(HomebuilderPayload.CHANNEL_END, 0L,
                HomebuilderPayload.END_ARRIVED, ""));
        HomebuilderNet.sendHomewardDay(player);
        TelemetryService.hearth(player, Telemetry.HOMEWARD, sameLevel ? "use" : "use_other_dimension",
                running.bell() ? "bell" : "channel", saved);
    }

    // ---- The Hearth you fed most ------------------------------------------------------------------

    /** A Hearth found from its key. */
    record Target(ServerLevel level, BlockPos pos) {
    }

    /** Once a second while home: copy this player's lifetime total at this Hearth (every 10 s). */
    static void noteFed(ServerPlayer player, long second) {
        if (second % FED_EVERY_SECONDS != 0) {
            return;
        }
        HearthBlockEntity home = HearthRange.homeEntity(player);
        if (home == null) {
            return;
        }
        double fed = home.state().ledger().total(player.getUUID(), null, HearthLedger.Window.LIFETIME);
        if (fed > 0) {
            HearthAttachments.of(player).setMark(FED_PREFIX + home.key(), Math.round(fed * 100.0));
        }
    }

    /**
     * The key of the Hearth this player fed most that still stands, or null. A loaded Hearth's
     * ledger is read fresh; an unloaded one uses the copy from the last visit.
     */
    @Nullable
    public static String target(ServerPlayer player) {
        PlayerHearth data = HearthAttachments.of(player);
        String best = null;
        double most = 0.0;
        for (String key : fedKeys(data)) {
            String hearthKey = key.substring(FED_PREFIX.length());
            Target target = resolve(player.server, hearthKey);
            if (target == null) {
                // Broken (and so out of the index): the ledger went with it.
                data.clearMark(key);
                continue;
            }
            double fed = data.mark(key, 0L) / 100.0;
            if (target.level().isLoaded(target.pos())
                    && target.level().getBlockEntity(target.pos()) instanceof HearthBlockEntity hearth) {
                fed = hearth.state().ledger().total(player.getUUID(), null, HearthLedger.Window.LIFETIME);
            }
            if (fed > most) {
                most = fed;
                best = hearthKey;
            }
        }
        return best;
    }

    /** Every Hearth this player has a fed mark for. */
    static List<String> fedKeys(PlayerHearth data) {
        List<String> keys = new ArrayList<>();
        for (String key : data.markKeys()) {
            if (key.startsWith(FED_PREFIX)) {
                keys.add(key);
            }
        }
        return keys;
    }

    /** The Hearth for a key ("dimension|x,y,z") when its level exists and the index still lists it. */
    @Nullable
    static Target resolve(MinecraftServer server, String key) {
        int bar = key.indexOf('|');
        if (bar <= 0) {
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(key.substring(0, bar));
        String[] xyz = key.substring(bar + 1).split(",");
        if (dimension == null || xyz.length != 3) {
            return null;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level == null) {
            return null;
        }
        BlockPos pos;
        try {
            pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
        } catch (NumberFormatException e) {
            return null;
        }
        if (!HearthIndexData.get(level).contains(pos)) {
            return null;
        }
        if (level.isLoaded(pos) && !level.getBlockState(pos).is(HearthBlocks.HEARTH.get())) {
            return null;
        }
        return new Target(level, pos);
    }

    /**
     * Where to put someone arriving at a Hearth: the nearest spot within 4 blocks of the campfire
     * with two free blocks of air over solid ground, never the fire itself. Loads the chunk.
     */
    static BlockPos landing(ServerLevel level, BlockPos hearth) {
        level.getChunk(hearth);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 3; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos feet = hearth.offset(dx, dy, dz);
                    double distance = dx * dx + dz * dz + dy * dy * 0.5;
                    if (distance < bestDistance && standable(level, feet)) {
                        best = feet;
                        bestDistance = distance;
                    }
                }
            }
        }
        if (best != null) {
            return best;
        }
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                hearth.offset(1, 0, 0));
    }

    private static boolean standable(Level level, BlockPos feet) {
        BlockState below = level.getBlockState(feet.below());
        if (!below.isFaceSturdy(level, feet.below(), Direction.UP) || below.is(HearthBlocks.HEARTH.get())) {
            return false;
        }
        BlockState at = level.getBlockState(feet);
        BlockState head = level.getBlockState(feet.above());
        if (!at.getFluidState().isEmpty() || !head.getFluidState().isEmpty()) {
            return false;
        }
        AABB body = new AABB(feet.getX() + 0.2, feet.getY(), feet.getZ() + 0.2,
                feet.getX() + 0.8, feet.getY() + 1.8, feet.getZ() + 0.8);
        return level.noCollision(body) && !at.is(net.minecraft.tags.BlockTags.FIRE)
                && !at.is(net.minecraft.tags.BlockTags.CAMPFIRES);
    }

    // ---- Life cycle -------------------------------------------------------------------------------

    static void forget(UUID player) {
        RUNNING.remove(player);
        HURT.remove(player);
    }
}
