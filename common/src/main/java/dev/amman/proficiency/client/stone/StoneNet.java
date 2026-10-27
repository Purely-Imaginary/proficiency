package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.net.UnlockPerkPayload;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.platform.net.ClientPacketDistributor;

/**
 * The packets the Skillstone sends. The only class of the Skillstone that differs between the
 * 1.21.1 lines and Forge 1.20.1 (each line has its own copy); the screens are shared verbatim.
 */
final class StoneNet {

    private StoneNet() {
    }

    /** One rank into {@code talent}; the server answers with a sync, or refuses in chat. */
    static void unlock(Talent talent) {
        ClientPacketDistributor.sendToServer(new UnlockPerkPayload(talent.key()));
    }

    /** A Hearth request (the ledger, the build plan, start/stop, a block pick); a server without the channel ignores it. */
    static void hearth(dev.amman.proficiency.hearth.net.HearthRequestPayload payload) {
        try {
            ClientPacketDistributor.sendToServer(payload);
        } catch (RuntimeException e) {
            // No channel: the page keeps saying it is still asking.
        }
    }

    /** An Expertise rank or the Expertise respec; the server re-checks it and answers with a sync. */
    static void expertise(dev.amman.proficiency.net.ExpertisePayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    /** Homebuilder's wheel slots: the item magnet switch, or Homeward. */
    static void homebuilder(boolean magnet) {
        ClientPacketDistributor.sendToServer(new dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload(magnet
                ? dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.MAGNET
                : dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload.HOMEWARD));
    }
}
