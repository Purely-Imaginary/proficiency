package dev.amman.proficiency.hearth;

import dev.amman.proficiency.platform.Services;
import net.minecraft.world.entity.player.Player;

/**
 * A player's Hearth state ({@link PlayerHearth}): the loader attachment {@code proficiency:hearth},
 * saved into the player's own NBT. Not copied by the loader on death: the Hearth's Clone handler
 * carries it across and clears what a death costs.
 */
public final class HearthAttachments {

    private HearthAttachments() {
    }

    public static PlayerHearth of(Player player) {
        return Services.platform().hearth(player);
    }
}
