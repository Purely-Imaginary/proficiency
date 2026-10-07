package dev.amman.proficiency.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * 1.21 names an attribute modifier by a ResourceLocation; 1.20.1 by a UUID plus a display name.
 * Every modifier keeps master's id: its UUID is derived from that id (name-based, so it is the
 * same on every run and a saved permanent modifier is found again), and the id is its name.
 */
public final class Attr {

    private Attr() {
    }

    public static UUID uuid(ResourceLocation id) {
        return UUID.nameUUIDFromBytes(("proficiency-modifier:" + id).getBytes(StandardCharsets.UTF_8));
    }

    public static AttributeModifier mod(ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(uuid(id), id.toString(), amount, operation);
    }

    /** 1.21's addOrUpdateTransientModifier. */
    public static void setTransient(AttributeInstance instance, AttributeModifier modifier) {
        instance.removeModifier(modifier.getId());
        instance.addTransientModifier(modifier);
    }

    /** 1.21's addOrReplacePermanentModifier. */
    public static void setPermanent(AttributeInstance instance, AttributeModifier modifier) {
        instance.removeModifier(modifier.getId());
        instance.addPermanentModifier(modifier);
    }
}
