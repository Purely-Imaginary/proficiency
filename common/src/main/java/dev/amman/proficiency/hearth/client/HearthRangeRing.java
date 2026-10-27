package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows the Hearth's range (docs/HEARTH-SPEC.md, "Showing the range"): a ring of client particles
 * where the cylinder meets the terrain, about 75 points, one pulse every 2 s. It costs nothing unless
 * asked for: looking at a Hearth, holding a campfire or flint and steel while looking at a campfire or
 * hearth stone (then other Hearths' spacing shows as a red ring), or the panel's "Show range for
 * 30 s". Client switch {@code hearth.showRange} turns the first two off; the panel's button is an
 * explicit ask and works either way. The ring thins with the Particles video setting.
 */
public final class HearthRangeRing {

    /** The stages lane's platform tag: a 3x3 of it under a campfire makes a Firepit. */
    static final TagKey<Block> HEARTH_STONE = TagKey.create(Registries.BLOCK, Proficiency.id("hearth/stone"));

    /**
     * Big dust motes: a dust particle's size and life both grow with its scale, so at 3 a point reads from
     * 30 blocks away and lives 1 to 6 s, which joins the 2 s pulses into a ring that breathes instead of
     * blinking (the first screenshot round's 1.2 was specks).
     */
    private static final DustParticleOptions EMBER = new DustParticleOptions(new Vector3f(1.0f, 0.62f, 0.22f), 3.0f);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.92f, 0.16f, 0.12f), 3.0f);
    /** How far around the player other Hearths are looked for (chunks), only while planning. */
    private static final int SCAN_CHUNKS = 6;
    private static final long SCAN_MS = 2000;

    private static long shownPos = Long.MIN_VALUE;
    private static String shownDim = "";
    private static long shownUntil = Long.MIN_VALUE / 2;
    private static long lastPulse = Long.MIN_VALUE;
    private static int pulses;
    private static long lastScan = Long.MIN_VALUE / 2;
    private static final List<BlockPos> NEARBY = new ArrayList<>();

    private HearthRangeRing() {
    }

    /** The panel's button: keep the ring of the Hearth at {@code pos} up for 30 s. */
    public static void showFor(long pos, String dimension, long now) {
        shownPos = pos;
        shownDim = dimension == null ? "" : dimension;
        shownUntil = now + RangeRing.SHOW_MS;
        lastPulse = Long.MIN_VALUE;
    }

    static boolean showing(long pos, long now) {
        return shownPos == pos && now < shownUntil;
    }

    static void reset() {
        shownPos = Long.MIN_VALUE;
        shownUntil = Long.MIN_VALUE / 2;
        lastPulse = Long.MIN_VALUE;
        NEARBY.clear();
    }

    /** Once per client tick. */
    static void tick(Minecraft minecraft, long now) {
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        BlockPos own = null;
        boolean planning = false;
        String dimension = level.dimension().location().toString();
        if (now < shownUntil && shownPos != Long.MIN_VALUE && (shownDim.isEmpty() || shownDim.equals(dimension))) {
            own = BlockPos.of(shownPos);
        } else if (ProficiencyClientConfig.hearthShowRange()) {
            HitResult hit = minecraft.hitResult;
            if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos at = blockHit.getBlockPos();
                BlockState state = level.getBlockState(at);
                if (state.getBlock() instanceof HearthBlock) {
                    own = at;
                } else if (holdsLighter(player)) {
                    if (state.getBlock() instanceof CampfireBlock || state.is(BlockTags.CAMPFIRES)) {
                        own = at;
                        planning = true;
                    } else if (state.is(HEARTH_STONE) && blockHit.getDirection() == Direction.UP) {
                        own = at.above();
                        planning = true;
                    }
                }
            }
        }
        if (own == null) {
            return;
        }
        if (!RangeRing.pulseDue(lastPulse, now)) {
            return;
        }
        lastPulse = now;
        pulses++;
        RangeRing.Particles setting = switch (minecraft.options.particles().get()) {
            case ALL -> RangeRing.Particles.ALL;
            case DECREASED -> RangeRing.Particles.DECREASED;
            case MINIMAL -> RangeRing.Particles.MINIMAL;
        };
        int count = RangeRing.points(setting);
        double phase = (pulses * 0.6180339887) % 1.0;
        // The cylinder is one server-wide config (hearth.range, rangeBelow, rangeAbove: HearthRange.range()),
        // the same for every Hearth and every stage, so the last state packet's numbers fit whichever Hearth
        // is looked at. Before any state packet this session, the shipped defaults.
        float radius = 24f;
        int below = 8;
        int above = 16;
        HearthStatePayload state = HearthView.state();
        if (state != null) {
            radius = state.radius();
            below = state.below();
            above = state.above();
        }
        ring(level, own, radius, below, above, count, phase, EMBER, null, 0);
        if (planning) {
            if (now - lastScan >= SCAN_MS) {
                lastScan = now;
                scan(level, player.blockPosition());
            }
            int spacing = HearthView.spacing();
            for (BlockPos other : NEARBY) {
                if (other.equals(own)) {
                    continue;
                }
                // The spacing ring is big; only its stretch near the player gets particles.
                ring(level, other, spacing, below, above, Math.max(count, (int) Math.round(spacing * 2.2)), phase, RED,
                        player.blockPosition(), RangeRing.SPACING_VIEW);
            }
        }
    }

    private static boolean holdsLighter(LocalPlayer player) {
        return lighter(player.getMainHandItem()) || lighter(player.getOffhandItem());
    }

    private static boolean lighter(ItemStack stack) {
        return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.CAMPFIRE) || stack.is(Items.SOUL_CAMPFIRE);
    }

    /** Every Hearth block entity in the loaded chunks around the player (never loads one). */
    private static void scan(ClientLevel level, BlockPos centre) {
        NEARBY.clear();
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;
        for (int dx = -SCAN_CHUNKS; dx <= SCAN_CHUNKS; dx++) {
            for (int dz = -SCAN_CHUNKS; dz <= SCAN_CHUNKS; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx + dx, cz + dz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    if (entity instanceof HearthBlockEntity) {
                        NEARBY.add(entity.getBlockPos().immutable());
                    }
                }
            }
        }
        HearthStatePayload state = HearthView.state();
        if (state != null && level.dimension().location().toString().equals(state.dimension())) {
            BlockPos known = BlockPos.of(state.pos());
            if (!NEARBY.contains(known)) {
                NEARBY.add(known);
            }
        }
    }

    /**
     * One pulse of one ring: {@code count} points round {@code centre}, each set on the ground the
     * cylinder meets (within its height), drifting up a little. With {@code near} set only the points
     * within {@code reach} of it.
     */
    private static void ring(ClientLevel level, BlockPos centre, float radius, int below, int above, int count,
            double phase, DustParticleOptions dust, BlockPos near, double reach) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        // The mote's sprites, found once for the whole pulse.
        net.minecraft.client.renderer.texture.TextureAtlasSprite[] frames = EmberDust.frames();
        double cx = centre.getX() + 0.5;
        double cz = centre.getZ() + 0.5;
        int minY = centre.getY() - below;
        int maxY = centre.getY() + above;
        for (int i = 0; i < count; i++) {
            double[] p = RangeRing.point(cx, cz, radius, i, count, phase);
            if (near != null && !RangeRing.near(p[0], p[1], near.getX() + 0.5, near.getZ() + 0.5, reach)) {
                continue;
            }
            int bx = (int) Math.floor(p[0]);
            int bz = (int) Math.floor(p[1]);
            int y = RangeRing.surface(centre.getY(), minY, maxY, height -> {
                cursor.set(bx, height, bz);
                return !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
            });
            if (y == Integer.MIN_VALUE) {
                continue;
            }
            // Full-bright motes (EmberDust): plain dust takes the light where it is and was near invisible
            // at night; plain dust stays the fallback.
            if (!EmberDust.spawn(level, p[0], y + 0.12, p[1], 0.015, dust.getColor(), dust.getScale(), frames)) {
                level.addAlwaysVisibleParticle(dust, true, p[0], y + 0.12, p[1], 0.0, 0.015, 0.0);
            }
        }
    }
}
