package dev.amman.proficiency.gametest;

import dev.amman.proficiency.hearth.HearthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Hearth's sleep hook from NeoForge's own wake-up event, through the bridge. Fabric posts the
 * shared event from a mixin (the Effects lane adds it); the shared HearthGameTests cover the rest.
 */
public final class NeoForgeHearthGameTests {

    private NeoForgeHearthGameTests() {
    }

    @GameTest(template = "proficiency:empty", batch = "hearth_neoforge_sleep")
    public static void aRealSleepReachesTheSleepHook(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        AtomicInteger slept = new AtomicInteger();
        HearthHooks.PlayerListener listener = who -> {
            if (who == player) {
                slept.incrementAndGet();
            }
        };
        HearthHooks.SLEEP.add(listener);
        try {
            CompoundTag tag = player.saveWithoutId(new CompoundTag());
            tag.putShort("SleepTimer", (short) 100);
            player.load(tag);
            player.setSleepingPos(helper.absolutePos(new BlockPos(1, 2, 1)));
            helper.assertTrue(player.isSleepingLongEnough(), "setup: the player should count as asleep");
            player.stopSleepInBed(false, false);
            helper.assertTrue(slept.get() == 1, "NeoForge's wake-up event should reach the sleep hook: " + slept.get());
        } finally {
            HearthHooks.SLEEP.remove(listener);
        }
        helper.succeed();
    }
}
