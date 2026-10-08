/**
 * The event bus the shared 1.21.1 code listens on. It keeps NeoForge's shape, so every handler
 * reads as it was first written for NeoForge: {@code @SubscribeEvent}, the same event types with
 * the same getters, the same priority order and cancellation rules. Each loader posts the events:
 * on NeoForge {@code NeoForgeEventBridge} relays the real NeoForge events, one relay per
 * priority so other mods' listeners keep their place; on Fabric mixins and Fabric API callbacks
 * in {@code platform.hooks} post them where NeoForge's own patches fire.
 */
package dev.amman.proficiency.platform.bus;
