package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.event.EntryCooldown;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EntryCooldownTest {

    private final UUID second = UUID.randomUUID();

    @Test
    void blocksUntilItRunsOut() {
        EntryCooldown cooldown = new EntryCooldown();
        String key = EntryCooldown.key("minecraft:village", 3, -4);
        assertTrue(cooldown.ready(second, key, 0));
        cooldown.record(second, key, 100, 1200);
        assertFalse(cooldown.ready(second, key, 1299));
        assertTrue(cooldown.ready(second, key, 1300));
    }

    @Test
    void eachStartAndPlayerIsSeparate() {
        EntryCooldown cooldown = new EntryCooldown();
        String a = EntryCooldown.key("minecraft:village", 0, 0);
        String b = EntryCooldown.key("minecraft:village", 9, 0);
        assertEquals("minecraft:village@9,0", b);
        cooldown.record(second, a, 0, 1200);
        assertTrue(cooldown.ready(second, b, 1));
        assertTrue(cooldown.ready(UUID.randomUUID(), a, 1));
    }

    @Test
    void zeroLengthIsOffAndForgetClears() {
        EntryCooldown cooldown = new EntryCooldown();
        String key = EntryCooldown.key("minecraft:igloo", 1, 1);
        cooldown.record(second, key, 0, 0);
        assertTrue(cooldown.ready(second, key, 1));
        cooldown.record(second, key, 0, 1200);
        cooldown.forget(second);
        assertTrue(cooldown.ready(second, key, 1));
    }

    @Test
    void pruneDropsExpiredOnly() {
        EntryCooldown cooldown = new EntryCooldown();
        String old = EntryCooldown.key("a:b", 0, 0);
        String live = EntryCooldown.key("a:c", 0, 0);
        cooldown.record(second, old, 0, 100);
        cooldown.record(second, live, 0, 1000);
        cooldown.prune(second, 500);
        assertTrue(cooldown.ready(second, old, 500));
        assertFalse(cooldown.ready(second, live, 500));
    }
}
