package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.ProcFxPayload;
import dev.amman.proficiency.skill.ProcFx;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Plays a proc's particle recipe ({@link ProcFx}) on this client. Every particle goes through the
 * vanilla {@code addParticle}, so the Particles video setting thins them out by itself: Decreased
 * and Minimal drop most of them. Layers with a delay wait in a short queue and fire on later ticks.
 * The queue holds at most 24 particles per proc and is capped, so a burst of procs cannot pile up.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class ProcFxPlayer {

    /** Where a proc happened, frozen when the packet arrives. */
    private record Context(double px, double py, double pz, double lx, double lz, double lookY,
            boolean hasFocus, double fx, double fy, double fz, BlockState state, int height,
            String fallback) {
    }

    /** One proc being played: its context, its recipe, and the tick it started. */
    private record Play(Context context, ProcFx.Recipe recipe, long start) {
    }

    /**
     * Most procs playing at once, so at most 4 x 24 particles are ever scheduled. A recipe that does
     * not fit is skipped whole; it never plays half.
     */
    private static final int MAX_PLAYS = 4;

    private static final List<Play> PLAYS = new ArrayList<>(MAX_PLAYS);
    private static final java.util.Map<String, BlockState> BLOCKS = new java.util.HashMap<>();
    private static long now;
    private static Object lastLevel;
    private static Object lastPlayer;

    private ProcFxPlayer() {
    }

    public static void accept(ProcFxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !ProficiencyClientConfig.procFxEnabled() || PLAYS.size() >= MAX_PLAYS) {
            return;
        }
        Skill skill = Skill.VALUES[Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length)];
        ProcFx.Recipe recipe = ProcFx.recipe(skill);
        if (recipe == null) {
            return;
        }
        Context context = context(level, recipe, payload);
        if (context == null) {
            return;
        }
        Play play = new Play(context, recipe, now);
        PLAYS.add(play);
        step(level, play, 0);
    }

    private static Context context(ClientLevel level, ProcFx.Recipe recipe, ProcFxPayload payload) {
        Entity player = level.getEntity(payload.entityId());
        double px;
        double py;
        double pz;
        double lx = 0;
        double lz = 1;
        double ly = 0;
        if (player != null) {
            px = player.getX();
            py = player.getY();
            pz = player.getZ();
            var look = player.getLookAngle();
            double flat = Math.sqrt(look.x * look.x + look.z * look.z);
            if (flat > 1e-4) {
                lx = look.x / flat;
                lz = look.z / flat;
            }
            ly = look.y;
        } else if (payload.hasFocus()) {
            px = payload.fx();
            py = payload.fy();
            pz = payload.fz();
        } else {
            return null;
        }
        BlockState state = payload.stateId() > 0 ? Block.stateById(payload.stateId()) : Blocks.AIR.defaultBlockState();
        return new Context(px, py, pz, lx, lz, ly, payload.hasFocus(),
                payload.hasFocus() ? payload.fx() : px, payload.hasFocus() ? payload.fy() : py + 1.0,
                payload.hasFocus() ? payload.fz() : pz, state, payload.height(), recipe.fallbackBlock());
    }

    /** Emits every particle of the recipe that is due exactly this many ticks after the start. */
    private static void step(ClientLevel level, Play play, int elapsed) {
        for (ProcFx.Layer layer : play.recipe().layers()) {
            int count = layer.count();
            for (int i = 0; i < count; i++) {
                int at = layer.delay() + (count > 1 ? (int) Math.round((double) layer.over() * i / (count - 1)) : 0);
                if (at == elapsed) {
                    emit(level, layer, i, play.context());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        now++;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        // A new level (dimension change, logout) or a new player entity (respawn after death) ends
        // whatever was still waiting, so it cannot draw in the wrong place.
        if (level != lastLevel || minecraft.player != lastPlayer) {
            lastLevel = level;
            lastPlayer = minecraft.player;
            PLAYS.clear();
        }
        if (PLAYS.isEmpty() || level == null) {
            return;
        }
        for (int i = PLAYS.size() - 1; i >= 0; i--) {
            Play play = PLAYS.get(i);
            long elapsed = now - play.start();
            if (elapsed > play.recipe().ticks()) {
                PLAYS.remove(i);
            } else if (elapsed > 0) {
                step(level, play, (int) elapsed);
            }
        }
    }

    /** Drops whatever was still waiting. */
    public static void clear() {
        PLAYS.clear();
    }

    // ---- One particle --------------------------------------------------------------------------

    private static void emit(ClientLevel level, ProcFx.Layer layer, int index, Context c) {
        RandomSource r = level.random;
        int count = Math.max(1, layer.count());
        double t = count > 1 ? (double) index / (count - 1) : 0.5;
        float s = layer.spread();
        double x;
        double y;
        double z;
        double vx = (r.nextDouble() - 0.5) * 2 * layer.speed();
        double vy = (r.nextDouble() - 0.5) * 2 * layer.speed();
        double vz = (r.nextDouble() - 0.5) * 2 * layer.speed();
        switch (layer.anchor()) {
            case FOCUS -> {
                x = c.fx() + jitter(r, s);
                y = c.fy() + jitter(r, s);
                z = c.fz() + jitter(r, s);
            }
            case BODY -> {
                x = c.px() + jitter(r, s);
                y = c.py() + 1.0 + jitter(r, s);
                z = c.pz() + jitter(r, s);
            }
            case FEET -> {
                x = c.px() + jitter(r, s);
                y = c.py() + 0.1;
                z = c.pz() + jitter(r, s);
                vy = Math.abs(vy);
            }
            case FRONT -> {
                x = c.px() + c.lx() * 1.2 + jitter(r, s);
                y = c.py() + 1.1 + jitter(r, s);
                z = c.pz() + c.lz() * 1.2 + jitter(r, s);
            }
            case ARC -> {
                double angle = Math.toRadians(-65 + 130 * t);
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double dx = c.lx() * cos - c.lz() * sin;
                double dz = c.lx() * sin + c.lz() * cos;
                x = c.px() + dx * 2.3;
                y = c.py() + 1.0;
                z = c.pz() + dz * 2.3;
                vx = 0;
                vy = 0;
                vz = 0;
            }
            case LINE -> {
                double sx = c.px() + c.lx() * 0.8;
                double sy = c.py() + 1.4;
                double sz = c.pz() + c.lz() * 0.8;
                double ex = c.hasFocus() ? c.fx() : sx + c.lx() * 6;
                double ey = c.hasFocus() ? c.fy() : sy + c.lookY() * 6;
                double ez = c.hasFocus() ? c.fz() : sz + c.lz() * 6;
                x = sx + (ex - sx) * t + jitter(r, s);
                y = sy + (ey - sy) * t + jitter(r, s);
                z = sz + (ez - sz) * t + jitter(r, s);
            }
            case COLUMN -> {
                x = c.fx() + jitter(r, s);
                y = c.fy() - 0.5 + t * (c.height() + 1) + r.nextDouble() * 0.5;
                z = c.fz() + jitter(r, s);
            }
            case CROWN -> {
                x = c.fx() + jitter(r, s);
                y = c.fy() + c.height() + 0.7 + r.nextDouble() * 0.8;
                z = c.fz() + jitter(r, s);
            }
            case RING -> {
                double angle = Math.PI * 2 * t + r.nextDouble() * 0.3;
                double rx = Math.cos(angle);
                double rz = Math.sin(angle);
                x = c.fx() + rx * s;
                y = (c.hasFocus() ? c.fy() - 0.5 : c.py()) + 0.15;
                z = c.fz() + rz * s;
                vx = rx * layer.speed();
                vz = rz * layer.speed();
                vy = Math.abs(vy);
            }
            case SELF_COLUMN -> {
                x = c.px() + jitter(r, s);
                y = c.py() + t * 2.2;
                z = c.pz() + jitter(r, s);
                vy = Math.abs(vy) + layer.speed();
            }
            default -> {
                // TRAIL: from just ahead of the feet to behind them, further back for later particles.
                x = c.px() + c.lx() * (0.5 - t * 2.2) + jitter(r, s);
                y = c.py() + 0.15 + r.nextDouble() * 0.2;
                z = c.pz() + c.lz() * (0.5 - t * 2.2) + jitter(r, s);
                vy = Math.abs(vy);
            }
        }
        ParticleOptions options = options(level, layer, c, r);
        if (options == null) {
            return;
        }
        switch (layer.kind()) {
            case ENCHANT -> level.addParticle(options, x, y + 0.8, z, c.px() - x, c.py() + 1.0 - y, c.pz() - z);
            case NOTE -> level.addParticle(options, x, y, z, r.nextDouble(), 0, 0);
            case BUBBLE_COLUMN_UP -> level.addParticle(options, x, y, z, vx * 0.2, 0.15 + Math.abs(vy), vz * 0.2);
            case BUBBLE, SPLASH, FISHING -> level.addParticle(options, x, y, z, vx, Math.abs(vy) + 0.05, vz);
            case SWEEP -> level.addParticle(options, x, y, z, t * 2 - 1, 0, 0);
            default -> level.addParticle(options, x, y, z, vx, vy, vz);
        }
    }

    private static double jitter(RandomSource r, float spread) {
        return (r.nextDouble() - 0.5) * 2 * spread;
    }

    private static BlockState blockOf(ProcFx.Layer layer, Context c) {
        String id = layer.block();
        if (id == null && !c.state().isAir()) {
            return c.state();
        }
        String key = id != null ? id : c.fallback();
        BlockState cached = BLOCKS.get(key);
        if (cached == null) {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(key));
            cached = block == null || block == Blocks.AIR ? Blocks.STONE.defaultBlockState()
                    : block.defaultBlockState();
            BLOCKS.put(key, cached);
        }
        return cached;
    }

    private static ParticleOptions options(ClientLevel level, ProcFx.Layer layer, Context c,
            RandomSource r) {
        return switch (layer.kind()) {
            case CRIT -> ParticleTypes.CRIT;
            case ENCHANTED_HIT -> ParticleTypes.ENCHANTED_HIT;
            case SWEEP -> ParticleTypes.SWEEP_ATTACK;
            case DAMAGE_INDICATOR -> ParticleTypes.DAMAGE_INDICATOR;
            case ANGRY_VILLAGER -> ParticleTypes.ANGRY_VILLAGER;
            case WAX_ON -> ParticleTypes.WAX_ON;
            case WAX_OFF -> ParticleTypes.WAX_OFF;
            case ELECTRIC_SPARK -> ParticleTypes.ELECTRIC_SPARK;
            case GLOW -> ParticleTypes.GLOW;
            case HAPPY_VILLAGER -> ParticleTypes.HAPPY_VILLAGER;
            case COMPOSTER -> ParticleTypes.COMPOSTER;
            case HEART -> ParticleTypes.HEART;
            case NOTE -> ParticleTypes.NOTE;
            case SPLASH -> ParticleTypes.SPLASH;
            case BUBBLE -> ParticleTypes.BUBBLE;
            case BUBBLE_COLUMN_UP -> ParticleTypes.BUBBLE_COLUMN_UP;
            case FISHING -> ParticleTypes.FISHING;
            case CLOUD -> ParticleTypes.CLOUD;
            case POOF -> ParticleTypes.POOF;
            case SMOKE -> ParticleTypes.SMOKE;
            case LARGE_SMOKE -> ParticleTypes.LARGE_SMOKE;
            case CAMPFIRE_SMOKE -> ParticleTypes.CAMPFIRE_COSY_SMOKE;
            case ASH -> ParticleTypes.ASH;
            case SOUL -> ParticleTypes.SOUL;
            case SOUL_FIRE_FLAME -> ParticleTypes.SOUL_FIRE_FLAME;
            case FLAME -> ParticleTypes.FLAME;
            case SMALL_FLAME -> ParticleTypes.SMALL_FLAME;
            case LAVA -> ParticleTypes.LAVA;
            case END_ROD -> ParticleTypes.END_ROD;
            case ENCHANT -> ParticleTypes.ENCHANT;
            case WITCH -> ParticleTypes.WITCH;
            case EFFECT -> ParticleTypes.EFFECT;
            case DRAGON_BREATH -> ParticleTypes.DRAGON_BREATH;
            case FIREWORK -> ParticleTypes.FIREWORK;
            case BLOCK -> new BlockParticleOption(ParticleTypes.BLOCK, blockOf(layer, c));
            case BLOCK_DUST -> {
                BlockState state = blockOf(layer, c);
                int color = state.getMapColor(level, BlockPos.containing(c.fx(), c.fy(), c.fz())).col;
                yield new DustParticleOptions(new Vector3f(((color >> 16) & 0xFF) / 255f,
                        ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f), 1.0f);
            }
        };
    }
}
