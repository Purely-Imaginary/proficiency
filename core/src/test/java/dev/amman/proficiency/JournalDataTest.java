package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.net.VisitedDelta;
import dev.amman.proficiency.skill.JournalData;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JournalDataTest {

    private static String name(String id) {
        return id.substring(id.indexOf(':') + 1);
    }

    @Test
    void firstSendIsFullThenOnlyAdditions() {
        VisitedDelta delta = new VisitedDelta();
        Set<String> visited = new HashSet<>(Set.of("biome:a", "dim:b"));
        var first = delta.next(visited);
        assertTrue(first.full());
        assertEquals(List.of("biome:a", "dim:b"), first.keys());
        assertNull(delta.next(visited));
        visited.add("biome:c");
        var second = delta.next(visited);
        assertFalse(second.full());
        assertEquals(List.of("biome:c"), second.keys());
        assertNull(delta.next(visited));
    }

    @Test
    void emptySetStillGetsAFullFirstSend() {
        VisitedDelta delta = new VisitedDelta();
        var first = delta.next(Set.of());
        assertTrue(first.full());
        assertTrue(first.keys().isEmpty());
        assertNull(delta.next(Set.of()));
    }

    @Test
    void aRemovalForcesAFullResend() {
        VisitedDelta delta = new VisitedDelta();
        Set<String> visited = new HashSet<>(Set.of("a", "b", "c"));
        delta.next(visited);
        // One key swapped for another: same size, so only the superset check can see it.
        visited.remove("a");
        visited.add("d");
        var change = delta.next(visited);
        assertTrue(change.full());
        assertEquals(List.of("b", "c", "d"), change.keys());
        visited.clear();
        var wiped = delta.next(visited);
        assertTrue(wiped.full());
        assertTrue(wiped.keys().isEmpty());
    }

    @Test
    void countsAgainstUniverseAndAddsUnknownFinds() {
        var tab = JournalData.tab(List.of("minecraft:plains", "minecraft:desert", "minecraft:the_void"),
                Set.of("minecraft:plains", "gone:mod_biome"), id -> id, JournalDataTest::name);
        assertEquals(2, tab.found());
        assertEquals(3, tab.total());
    }

    @Test
    void groupsPutMinecraftFirstAndFoundBeforeUnfound() {
        var tab = JournalData.tab(
                List.of("zeta:b", "minecraft:swamp", "alpha:a", "minecraft:desert", "minecraft:beach"),
                Set.of("minecraft:swamp", "minecraft:beach"), id -> id, JournalDataTest::name);
        assertEquals(List.of("minecraft", "alpha", "zeta"),
                tab.groups().stream().map(JournalData.Group::namespace).toList());
        var minecraft = tab.groups().get(0);
        assertEquals(List.of("minecraft:beach", "minecraft:swamp", "minecraft:desert"),
                minecraft.entries().stream().map(JournalData.Entry::id).toList());
        assertEquals(2, minecraft.found());
        assertNull(minecraft.entries().get(2).label());
        assertEquals("beach", minecraft.entries().get(0).label());
    }

    @Test
    void structureVariantsCollapseOnBothSides() {
        var tab = JournalData.tab(
                List.of("minecraft:village_plains", "minecraft:village_taiga", "minecraft:mansion"),
                Set.of("minecraft:village_desert"),
                dev.amman.proficiency.event.StructureIds::canonical, JournalDataTest::name);
        assertEquals(2, tab.total());
        assertEquals(1, tab.found());
    }

    @Test
    void prefixStrippingKeepsColonsInTheId() {
        assertEquals(Set.of("minecraft:plains"),
                JournalData.withPrefix(List.of("biome:minecraft:plains", "dim:x", "biome:"), "biome:"));
    }

    @Test
    void firstKindsSplitOnTheFirstColonOnly() {
        Map<String, List<String>> kinds = JournalData.firstKinds(List.of(
                "first:mining:block.minecraft.iron_ore", "first:mining:block.minecraft.coal_ore",
                "first:combat:entity:zombie", "biome:x", "first:broken", "first::none", "first:x:"));
        assertEquals(List.of("block.minecraft.coal_ore", "block.minecraft.iron_ore"), kinds.get("mining"));
        assertEquals(List.of("entity:zombie"), kinds.get("combat"));
        assertEquals(2, kinds.size());
    }
}
