/**
 * A small stand-in for the NeoForge event bus, so the Fabric port keeps every handler exactly as
 * it was written for NeoForge. Handlers still say {@code @SubscribeEvent}, still take the same
 * event types with the same getters, and still get the same priority ordering and cancellation
 * rules. What changes is who posts the events: mixins and Fabric API callbacks in
 * {@code dev.amman.proficiency.platform.hooks}, each placed where NeoForge's own patch fires.
 */
package dev.amman.proficiency.platform.bus;
