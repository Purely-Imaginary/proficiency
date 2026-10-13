package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.BoonType;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.skill.TelemetryService;
import dev.amman.proficiency.telemetry.Telemetry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Workshop (docs/HEARTH-SPEC.md, the boons table): stations in range run faster while a player is in
 * range ({@link #hearthSecond} publishes the speed for {@link HearthStations}), and a crafting table
 * used at home refunds a material now and then, never on a reversible recipe. Not scaled by Warmth:
 * furnaces are not people.
 */
public final class HearthWorkshop {

    private static volatile ReversibleRecipes.Result reversible = ReversibleRecipes.Result.NONE;
    private static RecipeManager reversibleFrom;

    /** Tests only: a fixed roll in [0, 1) instead of the player's random, or null. */
    private static volatile Double testRoll;

    private HearthWorkshop() {
    }

    // ---- Station speed --------------------------------------------------------------------------

    /** {@code HEARTH_TICK}: publishes this Hearth's station speed while someone is home, else nothing. */
    static void hearthSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        HearthStations.publish(level, hearth.packedPos(), stationSpeed(level, hearth), level.getGameTime());
    }

    /**
     * The station speed this Hearth gives right now: its Workshop value while a player (alive, not a
     * spectator) is in its range by the in-range flag, else 0 (abuse check 12: nobody home, normal speed).
     */
    public static double stationSpeed(ServerLevel level, HearthBlockEntity hearth) {
        HearthState state = hearth.state();
        if (state.smouldering()) {
            return 0.0;
        }
        HearthData d = Hearths.data();
        int tier = PlayerEffects.tier(state, BoonType.STATION_SPEED, d);
        if (tier <= 0 || !someoneHome(level, hearth.packedPos())) {
            return 0.0;
        }
        return PlayerEffects.value(BoonType.STATION_SPEED, tier, d);
    }

    private static boolean someoneHome(ServerLevel level, long hearth) {
        long now = level.getGameTime();
        String dimension = level.dimension().location().toString();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            PlayerHearth data = HearthAttachments.of(player);
            if (data.inRange(dimension, now) && data.inRangePos() == hearth) {
                return true;
            }
        }
        return false;
    }

    // ---- The crafting-table refund ----------------------------------------------------------------

    /** A refund chance and the effective tier of the pool that gives it (0 and 0: none). */
    public record Refund(double chance, int tier) {
        static final Refund NONE = new Refund(0.0, 0);
    }

    /** The refund chance at this Hearth: a {@code refund_chance} pool's value, or a pool's {@code refund} list. */
    public static double refundChance(HearthState state, HearthData d) {
        return refund(state, d).chance();
    }

    /**
     * The best refund at this Hearth and the tier of the pool it comes from (a pack may give it to any
     * pool, as a {@code refund_chance} boon or a {@code refund} list), so its telemetry row carries
     * that pool's tier.
     */
    public static Refund refund(HearthState state, HearthData d) {
        if (state.smouldering()) {
            return Refund.NONE;
        }
        Refund best = Refund.NONE;
        for (HearthPool pool : HearthPool.VALUES) {
            HearthData.PoolSpec spec = d.pool(pool);
            if (spec == null) {
                continue;
            }
            int tier = state.effectiveTier(pool, d);
            if (tier <= 0) {
                continue;
            }
            double chance = spec.boon() == BoonType.REFUND_CHANCE ? spec.value(tier) : spec.refundAt(tier);
            if (chance > best.chance()) {
                best = new Refund(Math.min(1.0, chance), tier);
            }
        }
        return best;
    }

    /**
     * A craft at a crafting table (not the 2x2 grid) by a player at home: each consumed ingredient
     * comes back with the refund chance, unless the recipe is reversible (abuse check 12) or the
     * ingredient leaves a container behind (a bucket). Called before vanilla takes the ingredients.
     */
    static void crafted(ServerPlayer player, ItemStack result, Container grid) {
        if (!ProficiencyConfig.hearthEnabled() || result.isEmpty()
                || !(player.containerMenu instanceof CraftingMenu menu)) {
            return;
        }
        HearthBlockEntity hearth = HearthRange.homeEntity(player);
        if (hearth == null) {
            return;
        }
        Refund refund = refund(hearth.state(), Hearths.data());
        double chance = refund.chance();
        if (!(chance > 0)) {
            return;
        }
        checkRecipes(player.server);
        if (isReversible(menu, result, grid)) {
            return;
        }
        int refunded = 0;
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack used = grid.getItem(slot);
            if (used.isEmpty() || used.getItem().hasCraftingRemainingItem()) {
                continue;
            }
            if (roll(player) < chance) {
                player.getInventory().placeItemBackInInventory(used.copyWithCount(1));
                refunded++;
            }
        }
        if (refunded > 0) {
            TelemetryService.hearth(player, Telemetry.HEARTH_BOON, BoonType.REFUND_CHANCE.id(),
                    HearthRules.roman(refund.tier()), refunded);
        }
    }

    private static double roll(ServerPlayer player) {
        Double fixed = testRoll;
        return fixed != null ? fixed : player.getRandom().nextDouble();
    }

    /** Tests: every refund roll returns {@code value} (null: back to random). */
    public static void setTestRoll(@Nullable Double value) {
        testRoll = value;
    }

    private static boolean isReversible(CraftingMenu menu, ItemStack result, Container grid) {
        ReversibleRecipes.Result known = reversible;
        RecipeHolder<?> used = menu.getSlot(0).container instanceof ResultContainer results
                ? results.getRecipeUsed() : null;
        if (used != null) {
            return known.isReversible(used.id().toString());
        }
        // Unknown recipe (a modded table path): judge by the result and the inputs.
        String out = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack in = grid.getItem(slot);
            if (!in.isEmpty() && known.isReversiblePair(out, BuiltInRegistries.ITEM.getKey(in.getItem()).toString())) {
                return true;
            }
        }
        return false;
    }

    // ---- Reversible recipes, once per datapack reload ---------------------------------------------

    /** The current set (tests and the panel). */
    public static ReversibleRecipes.Result reversible() {
        return reversible;
    }

    /** Recomputes the reversible set when the server's recipes changed (a {@code /reload} swaps them). */
    static void checkRecipes(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == reversibleFrom) {
            return;
        }
        reversibleFrom = manager;
        try {
            List<ReversibleRecipes.Shape> shapes = new ArrayList<>();
            var registries = server.registryAccess();
            for (RecipeHolder<CraftingRecipe> holder : manager.getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                if (recipe.isSpecial()) {
                    continue;
                }
                ItemStack out = recipe.getResultItem(registries);
                if (out.isEmpty()) {
                    continue;
                }
                List<Set<String>> inputs = new ArrayList<>();
                for (Ingredient ingredient : recipe.getIngredients()) {
                    if (ingredient.isEmpty()) {
                        continue;
                    }
                    Set<String> items = new LinkedHashSet<>();
                    for (ItemStack stack : ingredient.getItems()) {
                        items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                    }
                    inputs.add(items);
                }
                shapes.add(new ReversibleRecipes.Shape(holder.id().toString(),
                        BuiltInRegistries.ITEM.getKey(out.getItem()).toString(), inputs));
            }
            reversible = ReversibleRecipes.find(shapes);
            Proficiency.LOG.debug("Hearth Workshop: {} reversible crafting recipes of {}", reversible.ids().size(),
                    shapes.size());
        } catch (RuntimeException e) {
            Proficiency.LOG.error("Hearth Workshop: could not work out the reversible recipes", e);
        }
    }

    static void forget() {
        reversibleFrom = null;
        reversible = ReversibleRecipes.Result.NONE;
        HearthStations.clear();
    }
}
