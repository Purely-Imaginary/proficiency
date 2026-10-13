package dev.amman.proficiency.hearth.stages;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dressing roles (pots, chests, candles, carpet, glass, skulls, ice, snow, moss, magma, obsidian, leaves) and the lane palette. */
class DressingRolesTest {

    private static final String NEW_ROLES = "fxCmGzInqYOL";

    private static String wide(String letters) {
        return "[\"         \",\"         \",\"         \",\"" + letters + "\",\"         \",\"         \",\"         \",\"         \",\"         \"]";
    }

    private static HearthDesigns two(String palette) {
        // Two stages' worth of letters on two layers, so all twelve roles are on the grid.
        return HearthDesigns.parse(new StringReader("{\"legend\": {}, \"lanes\": [{\"id\": \"dressed\"" + palette + ", \"stages\": ["
                + "{\"stage\": 1, \"layers\": {\"0\": " + wide("fxCmGz   ") + ", \"1\": " + wide("InqYOL   ") + "}},"
                + "{\"stage\": 2, \"layers\": {}}, {\"stage\": 3, \"layers\": {}}, {\"stage\": 4, \"layers\": {}},"
                + "{\"stage\": 5, \"layers\": {}}, {\"stage\": 6, \"layers\": {}}]}]}"));
    }

    @Test
    void everyNewRoleIsInTheLegendWithADefaultBlockAndAMaterial() {
        for (char role : NEW_ROLES.toCharArray()) {
            assertTrue(HearthDesigns.ROLES.indexOf(role) >= 0, "legend " + role);
            assertNotNull(HearthDesigns.defaultBlock(role), "default block " + role);
            assertTrue(HearthDesigns.defaultBlock(role).startsWith("minecraft:"));
            assertNotNull(StageMaterials.group(role), "material " + role);
        }
        assertEquals("minecraft:flower_pot", HearthDesigns.defaultBlock('f'));
        assertEquals("minecraft:chest", HearthDesigns.defaultBlock('x'));
        assertEquals("minecraft:candle", HearthDesigns.defaultBlock('C'));
        assertEquals("minecraft:obsidian", HearthDesigns.defaultBlock('O'));
    }

    @Test
    void everyRoleOfTheLegendHasAMaterialGroupThatTheGuideListsAndNoGroupIsShared() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (char role : HearthDesigns.ROLES.toCharArray()) {
            String group = StageMaterials.group(role);
            assertNotNull(group, "group of " + role);
            assertTrue(java.util.Arrays.asList(StageMaterials.GROUPS).contains(group), group + " is listed");
            seen.add(group);
        }
        for (String group : StageMaterials.GROUPS) {
            assertTrue(seen.contains(group), group + " is the group of some role");
        }
        for (char role : NEW_ROLES.toCharArray()) {
            assertTrue(seen.contains(StageMaterials.group(role)));
        }
    }

    @Test
    void theNewRolesParseAndCountAsTheirOwnMaterials() {
        HearthDesigns designs = two("");
        HearthDesigns.Stage stage = designs.lanes().get(0).stage(1);
        assertEquals(12, stage.cells().size());
        Map<String, Integer> counts = StageMaterials.count(stage);
        assertEquals(12, counts.size());
        counts.values().forEach(n -> assertEquals(1, n));
        assertEquals(java.util.List.of("flower_pot", "storage", "candle", "carpet", "glass", "skull", "ice", "snow", "moss",
                "magma", "obsidian", "leaves").toString(), counts.keySet().stream()
                .sorted(java.util.Comparator.comparingInt(g -> java.util.Arrays.asList(StageMaterials.GROUPS).indexOf(g)))
                .toList().toString());
    }

    @Test
    void aStageMatchesOnlyWhenTheNewRolesStandWhereTheyAreWanted() {
        HearthDesigns designs = two("");
        HearthDesigns.Stage stage = designs.lanes().get(0).stage(1);
        Map<String, Character> placed = new java.util.HashMap<>();
        for (HearthDesigns.Cell cell : stage.cells()) {
            placed.put(cell.dx() + "," + cell.dy() + "," + cell.dz(), cell.role());
        }
        HearthDesigns.Probe probe = (role, dx, dy, dz) -> {
            Character here = placed.get(dx + "," + dy + "," + dz);
            return role == '.' ? here == null : here != null && here == role;
        };
        assertTrue(designs.matches(probe, 1));
        placed.put("-4,0,-1", 'x');
        assertTrue(!designs.matches(probe, 1));
        assertEquals('f', designs.problem(probe, 1).role());
    }

    @Test
    void aPaletteIsOptionalAndFallsBackToTheDefaultBlock() {
        HearthDesigns.Lane plain = two("").lanes().get(0);
        assertTrue(plain.palette().isEmpty());
        assertEquals("minecraft:white_carpet", plain.block('m'));
        assertEquals("minecraft:stone_bricks", plain.block('S'));
        assertNull(plain.block('?'));
    }

    @Test
    void aPaletteNamesTheLanesBlockPerRoleAndKeepsTheDefaultsForTheRest() {
        HearthDesigns.Lane lane = two(", \"palette\": {\"m\": \"minecraft:red_carpet\", \"x\": \"minecraft:barrel\", \"S\": \"minecraft:deepslate_bricks\"}")
                .lanes().get(0);
        assertEquals(3, lane.palette().size());
        assertEquals("minecraft:red_carpet", lane.block('m'));
        assertEquals("minecraft:barrel", lane.block('x'));
        assertEquals("minecraft:deepslate_bricks", lane.block('S'));
        assertEquals("minecraft:candle", lane.block('C'));
    }

    @Test
    void aPaletteRoleOutsideTheLegendIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> two(", \"palette\": {\"Z\": \"minecraft:stone\"}"));
        assertThrows(IllegalArgumentException.class, () -> two(", \"palette\": {\"mm\": \"minecraft:stone\"}"));
    }

    @Test
    void aFileMayHoldUpToEightLanes() {
        StringBuilder text = new StringBuilder("{\"legend\": {}, \"lanes\": [");
        for (int lane = 0; lane < 9; lane++) {
            text.append(lane > 0 ? "," : "").append("{\"id\": \"l").append(lane).append("\", \"stages\": [");
            for (int stage = 1; stage <= 6; stage++) {
                text.append(stage > 1 ? "," : "").append("{\"stage\": ").append(stage).append(", \"layers\": {}}");
            }
            text.append("]}");
            if (lane == 7) {
                assertEquals(8, HearthDesigns.parse(new StringReader(text + "]}")).lanes().size());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> HearthDesigns.parse(new StringReader(text + "]}")));
    }

    @Test
    void theShippedDesignsKeepTheirPalettesToKnownRoles() {
        for (HearthDesigns.Lane lane : HearthDesigns.shipped().lanes()) {
            for (Map.Entry<Character, String> entry : lane.palette().entrySet()) {
                assertTrue(HearthDesigns.ROLES.indexOf(entry.getKey()) >= 0, lane.id() + " " + entry.getKey());
                assertTrue(entry.getValue().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"), lane.id() + " " + entry.getValue());
            }
            assertTrue(HearthDesigns.shipped().lanes().size() <= HearthDesigns.MAX_LANES);
        }
    }
}
