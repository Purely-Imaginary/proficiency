package dev.amman.proficiency.gametest;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionResult;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stages lane against a claim mod on Fabric: a {@code UseBlockCallback} in the default phase (where
 * Architectury, which FTB Chunks uses, registers), added after this mod's own callback, that refuses
 * clicks on claimed positions. The shared body is
 * {@link HearthStagesGameTests#aClaimRefusesTheClickBeforeTheHearth}.
 */
public final class FabricHearthStagesGameTests {

    private static final Set<BlockPos> CLAIMED = ConcurrentHashMap.newKeySet();
    private static boolean registered;

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public FabricHearthStagesGameTests() {
    }

    private static synchronized void claimMod() {
        if (registered) {
            return;
        }
        registered = true;
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                !level.isClientSide() && CLAIMED.contains(hit.getBlockPos()) ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    @GameTest(template = "proficiency:empty", batch = "hearth_stages_claim_fabric")
    public static void aClaimModRefusesTheClickBeforeTheHearth(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        claimMod();
        HearthStagesGameTests.aClaimRefusesTheClickBeforeTheHearth(helper, CLAIMED);
    }
}
