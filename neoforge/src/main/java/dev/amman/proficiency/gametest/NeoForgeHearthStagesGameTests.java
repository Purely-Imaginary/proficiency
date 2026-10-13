package dev.amman.proficiency.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stages lane against a claim mod on NeoForge's own bus: a listener at NORMAL priority (where most
 * mods listen; Architectury, which FTB Chunks uses, listens at HIGH), registered after this mod, that
 * refuses clicks on claimed positions. The shared body is
 * {@link HearthStagesGameTests#aClaimRefusesTheClickBeforeTheHearth}.
 */
public final class NeoForgeHearthStagesGameTests {

    private static final Set<BlockPos> CLAIMED = ConcurrentHashMap.newKeySet();
    private static boolean registered;

    private NeoForgeHearthStagesGameTests() {
    }

    private static synchronized void claimMod() {
        if (registered) {
            return;
        }
        registered = true;
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerInteractEvent.RightClickBlock.class, event -> {
            if (!event.getLevel().isClientSide() && CLAIMED.contains(event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
        });
    }

    @GameTest(template = "proficiency:empty", batch = "hearth_stages_claim_neoforge")
    public static void aClaimModRefusesTheClickBeforeTheHearth(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        claimMod();
        HearthStagesGameTests.aClaimRefusesTheClickBeforeTheHearth(helper, CLAIMED);
    }
}
