package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.net.ActivateAbilityPayload;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import dev.amman.proficiency.platform.net.ClientPacketDistributor;
import dev.amman.proficiency.platform.bus.Dist;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class ClientInputEvents {

    private ClientInputEvents() {
    }

    /**
     * Set PROFICIENCY_OPEN_TREE=mining in the client's environment and that tree opens once, as
     * soon as you are in a world. Exists because synthetic keys never reach a game on a headless
     * output, so without it the tree screen could not be looked at by anything but a person.
     */
    private static final String OPEN_TREE = System.getenv("PROFICIENCY_OPEN_TREE");
    private static boolean openedFromEnvironment;

    /** PROFICIENCY_OPEN_JOURNAL=1 opens the Discovery journal once, the same way, for screenshots. */
    private static final boolean OPEN_JOURNAL = "1".equals(System.getenv("PROFICIENCY_OPEN_JOURNAL"));
    private static boolean journalOpenedFromEnvironment;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // The 1.33.0 style migration runs here, on the title screen, before a config screen can be opened.
        dev.amman.proficiency.config.ProficiencyClientConfig.migrateUiStyle();
        SkillTooltip.tick();
        Minecraft minecraft = Minecraft.getInstance();
        LayoutDemo.tick(minecraft);
        if (OPEN_TREE != null && !openedFromEnvironment && minecraft.player != null
                && (minecraft.screen == null
                        || minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen)
                && minecraft.player.tickCount > 100) {
            Skill skill = Skill.byId(OPEN_TREE);
            if (skill != null) {
                openedFromEnvironment = true;
                minecraft.setScreen(new TalentTreeScreen(skill, dev.amman.proficiency.client.stone.SkillstoneScreen.skills()));
            }
        }
        if (OPEN_JOURNAL && !journalOpenedFromEnvironment && minecraft.player != null
                && (minecraft.screen == null
                        || minecraft.screen instanceof net.minecraft.client.gui.screens.PauseScreen)
                && minecraft.player.tickCount > 100) {
            journalOpenedFromEnvironment = true;
            minecraft.setScreen(new DiscoveryJournalScreen(dev.amman.proficiency.client.stone.SkillstoneScreen.skills()));
        }
        HaulClient.tick(minecraft);
        SpendableClient.tick(minecraft);
        while (ProficiencyClient.OPEN_SKILLS.consumeClick()) {
            if (minecraft.screen == null && minecraft.player != null && !HaulClient.takeSkillsKey()) {
                minecraft.setScreen(dev.amman.proficiency.client.stone.SkillstoneScreen.skills());
            }
        }
        // The Waywheel's own key (unbound by default): opens the stone wheel at once, in either style.
        while (ProficiencyClient.WAYWHEEL.consumeClick()) {
            dev.amman.proficiency.client.stone.WaywheelScreen.open(minecraft, ProficiencyClient.WAYWHEEL);
        }
        // A tap casts what the hands say; a hold opens the wheel. The press is only counted here,
        // and the tap is decided on release, so a hold never casts the held item's ability first.
        while (ProficiencyClient.USE_ABILITY.consumeClick()) {
            if (heldTicks < 0) {
                heldTicks = 0;
            }
        }
        if (heldTicks >= 0) {
            if (minecraft.player == null || minecraft.screen != null) {
                heldTicks = -1;
            } else if (ProficiencyClient.USE_ABILITY.isDown()) {
                heldTicks++;
                if (heldTicks >= WHEEL_HOLD_TICKS && dev.amman.proficiency.client.stone.SkillstoneScreen.modern()) {
                    // The MODERN style's wheel: the stone Waywheel (1.33.0), with every skill, not only the unlocked.
                    heldTicks = -1;
                    dev.amman.proficiency.client.stone.WaywheelScreen.open(minecraft, ProficiencyClient.USE_ABILITY);
                } else if (heldTicks >= WHEEL_HOLD_TICKS) {
                    heldTicks = -1;
                    AbilityWheelScreen wheel = AbilityWheelScreen.forPlayer(minecraft.player);
                    if (wheel == null) {
                        minecraft.player.displayClientMessage(Component.translatable(
                                "proficiency.wheel.none_root", ActiveService.unlockLevel()), true);
                    } else {
                        minecraft.setScreen(wheel);
                    }
                }
            } else {
                heldTicks = -1;
                Skill skill = AbilityContext.current(minecraft.player);
                if (skill == null) {
                    minecraft.player.displayClientMessage(
                            Component.translatable("proficiency.active.none"), true);
                } else {
                    cast(skill);
                }
            }
        }
    }

    /** How long the ability key must be held before the wheel opens: a quarter of a second. */
    private static final int WHEEL_HOLD_TICKS = 5;
    /** Ticks the ability key has been down, or -1 when no press is being followed. */
    private static int heldTicks = -1;

    /** Asks the server to start this skill's ability; it answers on the action bar if it cannot. */
    public static void cast(Skill skill) {
        ClientPacketDistributor.sendToServer(new ActivateAbilityPayload(skill.ordinal()));
    }
}
