package dev.amman.proficiency.skill;

import dev.amman.proficiency.Proficiency;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * What the Mastery riders need that a single tick cannot do: a little queue of things that happen a
 * few ticks later, and the glowing markers (a ping that shines through rock for a few seconds) that
 * Motherlode Ping and Cave Sense put in the world. A marker is a block-display entity with the glow
 * outline on, which vanilla draws through walls. It is never saved: a marker found on disk (a crash
 * or a chunk unloaded before it expired) is dropped the moment it loads.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class MasteryTasks {

    /** The entity tag that says "this is one of ours and short-lived". */
    public static final String MARK_TAG = "proficiency_mastery_mark";

    private record Task(long due, Runnable run, boolean cleanup) {
    }

    private static final List<Task> TASKS = new ArrayList<>();

    private MasteryTasks() {
    }

    /** Runs {@code run} on the server thread {@code ticks} ticks from now. */
    public static synchronized void later(MinecraftServer server, int ticks, Runnable run) {
        TASKS.add(new Task(server.getTickCount() + Math.max(1, ticks), run, false));
    }

    /** Like {@link #later}, but it is also run when the server stops: for tidying up, never for damage or loot. */
    public static synchronized void laterEvenOnStop(MinecraftServer server, int ticks, Runnable run) {
        TASKS.add(new Task(server.getTickCount() + Math.max(1, ticks), run, true));
    }

    /** Tasks still waiting (for the tests). */
    public static synchronized int pending() {
        return TASKS.size();
    }

    /** Spawns a glowing marker cube at a block that shines {@code ticks} ticks, then goes away. */
    public static void mark(ServerLevel level, BlockPos pos, Block look, double size, int color, int ticks) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        ListTag at = new ListTag();
        at.add(DoubleTag.valueOf(pos.getX() + 0.5));
        at.add(DoubleTag.valueOf(pos.getY() + 0.5));
        at.add(DoubleTag.valueOf(pos.getZ() + 0.5));
        tag.put("Pos", at);
        CompoundTag state = new CompoundTag();
        state.putString("Name", BuiltInRegistries.BLOCK.getKey(look).toString());
        tag.put("block_state", state);
        tag.putBoolean("Glowing", true);
        tag.putInt("glow_color_override", color);
        tag.putBoolean("Invulnerable", true);
        tag.putBoolean("NoGravity", true);
        tag.putBoolean("Silent", true);
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(MARK_TAG));
        tag.put("Tags", tags);
        // The cube is drawn around the entity's origin: shift it back by half its size.
        CompoundTag transform = new CompoundTag();
        transform.put("translation", floats((float) -size / 2f, (float) -size / 2f, (float) -size / 2f));
        transform.put("scale", floats((float) size, (float) size, (float) size));
        transform.put("left_rotation", floats(0f, 0f, 0f, 1f));
        transform.put("right_rotation", floats(0f, 0f, 0f, 1f));
        tag.put("transformation", transform);
        EntityType.create(tag, level).ifPresent(entity -> {
            level.addFreshEntity(entity);
            laterEvenOnStop(level.getServer(), ticks, entity::discard);
        });
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            run(event.getServer());
        }
    }

    /** Runs every task that has come due. Public so a test can drive it. */
    public static void run(MinecraftServer server) {
        List<Runnable> due = null;
        synchronized (MasteryTasks.class) {
            if (TASKS.isEmpty()) {
                return;
            }
            long now = server.getTickCount();
            for (Iterator<Task> it = TASKS.iterator(); it.hasNext();) {
                Task task = it.next();
                if (task.due() <= now) {
                    if (due == null) {
                        due = new ArrayList<>();
                    }
                    due.add(task.run());
                    it.remove();
                }
            }
        }
        if (due != null) {
            for (Runnable run : due) {
                try {
                    run.run();
                } catch (RuntimeException e) {
                    Proficiency.LOG.warn("A Mastery rider's delayed task failed", e);
                }
            }
        }
    }

    /** Nothing carries into the next world (singleplayer reuses the JVM). Markers go with their level. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        List<Task> left;
        synchronized (MasteryTasks.class) {
            left = new ArrayList<>(TASKS);
            TASKS.clear();
        }
        for (Task task : left) {
            if (!task.cleanup()) {
                continue; // a delayed hit or effect is dropped: the world is closing, nothing may die or drop now
            }
            try {
                task.run().run();
            } catch (RuntimeException ignored) {
                // The world is going away anyway.
            }
        }
    }

    /** A marker that came back off disk is a leak: it is cancelled before it ever joins the level. */
    @SubscribeEvent
    public static void onMarkLoaded(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.loadedFromDisk() && entity.getTags().contains(MARK_TAG)) {
            event.setCanceled(true);
        }
    }
}
