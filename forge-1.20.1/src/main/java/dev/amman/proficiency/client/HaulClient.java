package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.client.stone.HaulBedButton;
import dev.amman.proficiency.client.stone.HaulScreen;
import dev.amman.proficiency.client.stone.HaulToast;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.haul.HaulSummary;
import dev.amman.proficiency.net.HaulRequestPayload;
import dev.amman.proficiency.net.HaulNetwork;
import net.minecraft.Util;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;

/**
 * Today's Haul, client side (1.44.0, bed reworked in 1.48.0). The server's login hello says it has the card (only
 * then does this client ever ask for one, so an older server never gets an unknown packet). Lying down in a bed
 * never opens the card by itself: the client only peeks ("is there an unseen haul?"), and when the server says yes
 * the bed's chat screen shows a "Today's Haul" button ({@link HaulBedButton}) while {@code ui.haulCard} is on; a
 * click asks for the card. At login, an unseen haul becomes a toast, and the skills key opens the haul while it is up.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class HaulClient {

    private static boolean serverHas;
    private static boolean wasSleeping;
    private static long remindUntil;
    /** A peek is out and its answer (a hello-mode summary) is the bed button's haul, not the login toast. */
    private static boolean peeking;
    private static final int PEEK_EVERY_TICKS = 40;
    private static int sincePeek;
    /** The unseen haul the bed button offers; null when there is none (or the card was just asked for). */
    private static HaulSummary bedHaul;

    private HaulClient() {
    }

    /** Client thread: a card or the login hello. */
    public static void accept(HaulSummary summary) {
        serverHas = true;
        Minecraft mc = Minecraft.getInstance();
        if (summary.mode() == HaulSummary.LOGIN && peeking) {
            peeking = false;
            bedHaul = ProficiencyClientConfig.uiHaulCard() && mc.player != null && mc.player.isSleeping() ? summary : null;
            return;
        }
        if (summary.mode() == HaulSummary.LOGIN) {
            if (ProficiencyClientConfig.uiHaulCard() && summary.totalXp() >= 1 && mc.player != null) {
                HaulToast.show(mc, summary, ProficiencyClient.OPEN_SKILLS.getTranslatedKeyMessage());
                remindUntil = Util.getMillis() + HaulToast.DURATION_MS + 3000;
            }
            return;
        }
        remindUntil = 0;
        bedHaul = null;
        HaulScreen.open(mc, summary);
    }

    /** Every client tick: a bed just taken peeks for an unseen haul (nothing opens). */
    public static void tick(Minecraft mc) {
        if (mc.player == null) {
            serverHas = false;
            wasSleeping = false;
            remindUntil = 0;
            peeking = false;
            bedHaul = null;
            return;
        }
        boolean sleeping = mc.player.isSleeping();
        if (sleeping && serverHas && ProficiencyClientConfig.uiHaulCard() && bedHaul == null) {
            // On lying down, then every 2 s while there is nothing to offer: something can arrive in bed (Hearth charge).
            if (!wasSleeping || ++sincePeek >= PEEK_EVERY_TICKS) {
                sincePeek = 0;
                peeking = true;
                HaulNetwork.request(HaulRequestPayload.PEEK);
            }
        }
        if (!sleeping) {
            peeking = false;
            bedHaul = null;
            sincePeek = 0;
        }
        wasSleeping = sleeping;
    }

    /** The skills key was pressed in the world: true when the login reminder took it and asked for the haul. */
    public static boolean takeSkillsKey() {
        if (serverHas && remindUntil > Util.getMillis()) {
            remindUntil = 0;
            HaulNetwork.request(HaulRequestPayload.KEY);
            return true;
        }
        return false;
    }

    /** The unseen haul the bed button offers right now, or null. */
    public static HaulSummary bedHaul() {
        return bedHaul;
    }

    /** The bed button was clicked: ask for the card (it opens when it arrives). */
    private static void openFromBed() {
        bedHaul = null;
        HaulNetwork.request(HaulRequestPayload.OPEN);
    }

    /** The bed's chat screen came up: it gets the button (hidden until there is a haul to show). */
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InBedChatScreen screen) {
            event.addListener(new HaulBedButton(screen.width, screen.height, HaulClient::bedHaul, HaulClient::openFromBed));
        }
    }
}
