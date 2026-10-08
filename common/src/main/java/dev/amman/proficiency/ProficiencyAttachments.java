package dev.amman.proficiency;

import dev.amman.proficiency.platform.Services;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.world.entity.player.Player;

/**
 * A player's skills. They live as the loader's data attachment {@code proficiency:skills}, saved
 * into the player's own NBT with {@link PlayerSkills#CODEC}. Not copied on death by the loader:
 * the Clone handler carries them across by hand, because that is where the death penalty is
 * charged.
 */
public final class ProficiencyAttachments {

    private ProficiencyAttachments() {
    }

    public static PlayerSkills of(Player player) {
        return Services.platform().skills(player);
    }
}
