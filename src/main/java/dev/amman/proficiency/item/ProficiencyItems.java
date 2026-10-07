package dev.amman.proficiency.item;

import dev.amman.proficiency.Proficiency;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * The four specialist materials, the compass, and the station recipes' tools and results.
 *
 * <p>Each material drops only from one skill's signature proc, and each gates the bottom of other
 * skills' trees. That is the whole of the cooperation design: you can always earn your own by
 * levelling that skill yourself, and a friend who already has can simply hand you one.
 */
public final class ProficiencyItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Proficiency.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Proficiency.MOD_ID);

    /** Drops from a Smithing proc. Wanted by Blocking, Woodcutting, Masonry and the rest. */
    public static final DeferredItem<Item> MASTERWORK_INGOT =
            ITEMS.registerSimpleItem("masterwork_ingot");

    /** Drops from an Alchemy proc. Wanted by the gathering trees. */
    public static final DeferredItem<Item> PROSPECTORS_DRAUGHT =
            ITEMS.registerSimpleItem("prospectors_draught");

    /** Drops from a Beastslaying proc. Wanted by the weapon trees. */
    public static final DeferredItem<Item> HUNTERS_CHARM =
            ITEMS.registerSimpleItem("hunters_charm");

    /** Drops from a Wayfaring proc. Wanted by the movement trees. */
    public static final DeferredItem<Item> WANDERERS_TOKEN =
            ITEMS.registerSimpleItem("wanderers_token");

    public static final DeferredItem<Item> FORESTERS_COMPASS = ITEMS.register(
            "foresters_compass",
            () -> new ForestersCompassItem(new Item.Properties().stacksTo(1)));

    /** Points at one registered friend; see {@link FriendCompassItem}. Plain crafting recipe. */
    public static final DeferredItem<Item> FRIEND_COMPASS = ITEMS.register(
            "friend_compass",
            () -> new FriendCompassItem(new Item.Properties().stacksTo(1)));

    // ---- Station tools: plain crafting recipes, anyone can make them --------------------------

    public static final DeferredItem<Item> LADLE = ITEMS.register("ladle",
            () -> new DescribedItem(new Item.Properties().durability(64)));

    public static final DeferredItem<Item> SMITHING_HAMMER = ITEMS.register("smithing_hammer",
            () -> new DescribedItem(new Item.Properties().durability(250)));

    // ---- Station results: only a player who learned the recipe can make these ----------------

    public static final DeferredItem<Item> MINERS_STEW = ITEMS.register("miners_stew",
            () -> new DescribedItem(new Item.Properties().stacksTo(1).food(new FoodProperties.Builder()
                    .nutrition(8).saturationModifier(0.8f).usingConvertsTo(Items.BOWL)
                    .effect(new MobEffectInstance(MobEffects.DIG_SPEED, 20 * 240, 0), 1.0f).build())));

    public static final DeferredItem<Item> TRAIL_RATION = ITEMS.register("trail_ration",
            () -> new DescribedItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(4).saturationModifier(0.5f).fast()
                    .effect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 120, 0), 1.0f).build())));

    public static final DeferredItem<Item> WARRIORS_PIEROGI = ITEMS.register("warriors_pierogi",
            () -> new DescribedItem(new Item.Properties().food(new FoodProperties.Builder()
                    .nutrition(5).saturationModifier(0.6f)
                    .effect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 90, 0), 1.0f).build())));

    public static final DeferredItem<Item> FISHERMANS_CHOWDER = ITEMS.register("fishermans_chowder",
            () -> new DescribedItem(new Item.Properties().stacksTo(1).food(new FoodProperties.Builder()
                    .nutrition(8).saturationModifier(0.8f).usingConvertsTo(Items.BOWL)
                    .effect(new MobEffectInstance(MobEffects.LUCK, 20 * 300, 0), 1.0f)
                    .effect(new MobEffectInstance(MobEffects.WATER_BREATHING, 20 * 180, 0), 1.0f).build())));

    /** 7 damage, 1.0 speed. Animals it kills drop twice the meat: {@link SpecialItemEvents}. */
    public static final DeferredItem<Item> BUTCHERS_CLEAVER = ITEMS.register("butchers_cleaver",
            () -> new DescribedItem.Sword(Tiers.IRON, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 4, -3.0f))));

    /** 4 damage, 2.4 speed. Every third hit on the same target lands 50% harder. */
    public static final DeferredItem<Item> DUELISTS_RAPIER = ITEMS.register("duelists_rapier",
            () -> new DescribedItem.Sword(Tiers.IRON, new Item.Properties()
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 1, -1.6f))));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("proficiency.tab"))
                    .icon(() -> new ItemStack(FORESTERS_COMPASS.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(FORESTERS_COMPASS.get());
                        output.accept(FRIEND_COMPASS.get());
                        output.accept(MASTERWORK_INGOT.get());
                        output.accept(PROSPECTORS_DRAUGHT.get());
                        output.accept(HUNTERS_CHARM.get());
                        output.accept(WANDERERS_TOKEN.get());
                        output.accept(LADLE.get());
                        output.accept(SMITHING_HAMMER.get());
                        output.accept(MINERS_STEW.get());
                        output.accept(TRAIL_RATION.get());
                        output.accept(WARRIORS_PIEROGI.get());
                        output.accept(FISHERMANS_CHOWDER.get());
                        output.accept(BUTCHERS_CLEAVER.get());
                        output.accept(DUELISTS_RAPIER.get());
                    })
                    .build());

    private ProficiencyItems() {
    }
}
