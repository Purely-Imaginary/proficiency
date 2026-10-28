package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudStackTest {

    private static final long LINGER = HudStack.DEFAULT_LINGER_MS;
    private static final long NEVER = Long.MAX_VALUE;

    private static List<Skill> order(HudStack stack) {
        return stack.rows().stream().filter(r -> !r.leaving()).map(HudStack.Row::skill).toList();
    }

    private static void tick(HudStack stack, Skill main, long now) {
        stack.tick(main, now, 16, LINGER, true);
    }

    @Test
    void aGainOnTheMainLineSkillMakesNoRow() {
        HudStack stack = new HudStack();
        assertNull(stack.gain(Skill.SWORDS, Skill.SWORDS, 0));
        assertTrue(stack.rows().isEmpty());
    }

    @Test
    void newRowsGoOnTopAndKeepTheirOrder() {
        HudStack stack = new HudStack();
        stack.gain(Skill.RUNNING, Skill.SWORDS, 0);
        stack.gain(Skill.JUMPING, Skill.SWORDS, 100);
        // Running earns again: it must not move.
        stack.gain(Skill.RUNNING, Skill.SWORDS, 200);
        stack.gain(Skill.COURAGE, Skill.SWORDS, 300);
        assertEquals(List.of(Skill.RUNNING, Skill.JUMPING, Skill.COURAGE), order(stack));
        for (int t = 300; t < 2000; t += 16) {
            stack.gain(Skill.JUMPING, Skill.SWORDS, t);
            stack.gain(Skill.RUNNING, Skill.SWORDS, t + 8);
            tick(stack, Skill.SWORDS, t);
            assertEquals(List.of(Skill.RUNNING, Skill.JUMPING, Skill.COURAGE), order(stack));
        }
    }

    @Test
    void aRowFadesAfterTheLingerAndTheRestSlideDown() {
        HudStack stack = new HudStack();
        stack.gain(Skill.RUNNING, null, 0);
        stack.gain(Skill.JUMPING, null, 0);
        tick(stack, null, 0);
        // Running stops, Jumping keeps earning.
        long t = 0;
        for (; t < LINGER + 50; t += 16) {
            stack.gain(Skill.JUMPING, null, t);
            tick(stack, null, t);
        }
        HudStack.Row running = stack.rows().get(0);
        assertTrue(running.leaving());
        assertTrue(running.alpha(t) < 1f);
        // Jumping holds slot 1 while Running fades, then slides down.
        assertEquals(1f, stack.row(Skill.JUMPING).slot(), 1e-6);
        for (long end = t + HudStack.FADE_OUT_MS + 600; t < end; t += 16) {
            stack.gain(Skill.JUMPING, null, t);
            tick(stack, null, t);
        }
        assertEquals(List.of(Skill.JUMPING), order(stack));
        assertEquals(1, stack.rows().size());
        assertEquals(0f, stack.row(Skill.JUMPING).slot(), 1e-6);
    }

    @Test
    void theSlideIsSmooth() {
        HudStack stack = new HudStack();
        stack.gain(Skill.RUNNING, null, 0);
        stack.gain(Skill.JUMPING, null, 0);
        tick(stack, null, 0);
        long t = 0;
        while (!stack.rows().get(0).leaving() || stack.rows().size() > 1) {
            t += 16;
            stack.gain(Skill.JUMPING, null, t);
            tick(stack, null, t);
            if (stack.rows().size() == 1) {
                break;
            }
        }
        float slot = stack.row(Skill.JUMPING).slot();
        assertTrue(slot > 0.5f && slot < 1f, "one frame after the removal it has only started to slide: " + slot);
        HudStack still = new HudStack();
        still.gain(Skill.RUNNING, null, 0);
        still.gain(Skill.JUMPING, null, 0);
        for (long u = 0; still.rows().size() > 1; u += 16) {
            still.gain(Skill.JUMPING, null, u);
            still.tick(null, u, 16, LINGER, false);
        }
        assertEquals(0f, still.row(Skill.JUMPING).slot(), 1e-6, "no motion: the row snaps");
    }

    @Test
    void aFourthSkillReplacesTheRowIdleLongest() {
        HudStack stack = new HudStack();
        stack.gain(Skill.RUNNING, Skill.SWORDS, 0);
        stack.gain(Skill.JUMPING, Skill.SWORDS, 100);
        stack.gain(Skill.COURAGE, Skill.SWORDS, 200);
        stack.gain(Skill.RUNNING, Skill.SWORDS, 900);
        // Jumping earned last at 100: it is the idle one, not Running (first in) or Courage.
        stack.gain(Skill.BLOCKING, Skill.SWORDS, 1000);
        assertEquals(List.of(Skill.RUNNING, Skill.COURAGE, Skill.BLOCKING), order(stack));
        assertTrue(stack.rows().get(1).leaving());
        assertSame(Skill.JUMPING, stack.rows().get(1).skill());
        assertEquals(3, stack.live());
        // The new row sits on top (slot 3) until the leaving row is gone, then all slide down.
        assertEquals(3f, stack.row(Skill.BLOCKING).slot(), 1e-6);
        for (long t = 1000; t < 1000 + HudStack.FADE_OUT_MS + 800; t += 16) {
            tick(stack, Skill.SWORDS, t);
        }
        assertEquals(3, stack.rows().size());
        assertEquals(2f, stack.row(Skill.BLOCKING).slot(), 1e-6);
    }

    @Test
    void theStackIsBounded() {
        HudStack stack = new HudStack();
        Skill[] many = {Skill.RUNNING, Skill.JUMPING, Skill.COURAGE, Skill.BLOCKING, Skill.MINING, Skill.FARMING,
                Skill.SWIMMING, Skill.SNEAKING};
        for (int i = 0; i < 200; i++) {
            stack.gain(many[i % many.length], Skill.SWORDS, i * 10L);
            tick(stack, Skill.SWORDS, i * 10L);
            assertTrue(stack.live() <= HudStack.MAX_ROWS);
            // One fade at a time: never more than one row over the cap on screen.
            assertTrue(stack.rows().size() <= HudStack.MAX_ROWS + 1);
        }
    }

    @Test
    void aRowWhoseSkillTakesTheMainLineLeaves() {
        HudStack stack = new HudStack();
        stack.gain(Skill.MINING, Skill.SWORDS, 0);
        tick(stack, Skill.SWORDS, 10);
        assertNotNull(stack.row(Skill.MINING));
        // The pickaxe comes out: Mining is the main line's now, never in the stack as well.
        tick(stack, Skill.MINING, 20);
        assertNull(stack.row(Skill.MINING));
        assertNull(stack.gain(Skill.MINING, Skill.MINING, 30));
    }

    @Test
    void aPopKeepsItsRowUpAndHasItsOwnTiming() {
        HudStack stack = new HudStack();
        HudStack.Row row = stack.gain(Skill.RUNNING, null, 0);
        row.pop(19, 20, false, LINGER - 100);
        assertTrue(row.popGold());
        assertEquals(LevelUpFx.GOLD_MS, row.popMs());
        tick(stack, null, LINGER + 200);
        assertFalse(row.leaving(), "a pop inside the linger keeps the row");
        assertTrue(row.popFlash(LINGER - 100) > 0.99f);
        assertEquals(1f, row.popRoll(LINGER - 100 + LevelUpFx.ROLL_MS), 1e-6);
        HudStack.Row star = new HudStack().gain(Skill.JUMPING, null, 0);
        star.pop(101, 102, true, 0);
        assertFalse(star.popGold());
        assertEquals(1f, star.popRoll(10), 1e-6);
        assertTrue(star.popFlash(0) <= 0.5f);
    }

    @Test
    void movementHoldsTheMainLineWhileItEarns() {
        // Running is on the line and earning; a jump picks Jumping: Running stays.
        assertEquals(Skill.RUNNING, HudStack.holdMain(Skill.JUMPING, Skill.RUNNING, 10_000, 200, NEVER));
        // Running has been on only a moment: it stays even when idle.
        assertEquals(Skill.RUNNING, HudStack.holdMain(Skill.JUMPING, Skill.RUNNING, 500, NEVER, NEVER));
        // Idle long enough and on long enough: Jumping takes over.
        assertEquals(Skill.JUMPING, HudStack.holdMain(Skill.JUMPING, Skill.RUNNING, HudStack.MOVE_MIN_HOLD_MS,
                HudStack.MOVE_IDLE_MS, NEVER));
        // The same the other way.
        assertEquals(Skill.JUMPING, HudStack.holdMain(Skill.RUNNING, Skill.JUMPING, 10_000, 100, NEVER));
    }

    @Test
    void comingOntoMovementPrefersARunningSprint() {
        // The sword is put away mid-sprint, just after a jump: Running (earning) takes the line, not Jumping.
        assertEquals(Skill.RUNNING, HudStack.holdMain(Skill.JUMPING, Skill.SWORDS, 10_000, 0, 200));
        // Not sprinting: the jump is what is happening.
        assertEquals(Skill.JUMPING, HudStack.holdMain(Skill.JUMPING, Skill.SWORDS, 10_000, 0, NEVER));
        assertEquals(Skill.JUMPING, HudStack.holdMain(Skill.JUMPING, null, 0, NEVER, HudStack.MOVE_IDLE_MS));
    }

    @Test
    void everythingElseChangesTheMainLineAtOnce() {
        assertEquals(Skill.SWORDS, HudStack.holdMain(Skill.SWORDS, Skill.RUNNING, 0, 0, NEVER));
        assertEquals(Skill.UNARMED, HudStack.holdMain(Skill.UNARMED, Skill.RUNNING, 0, 0, NEVER));
        assertEquals(Skill.RUNNING, HudStack.holdMain(Skill.RUNNING, Skill.UNARMED, 0, 0, NEVER));
        assertEquals(Skill.SWIMMING, HudStack.holdMain(Skill.SWIMMING, Skill.JUMPING, 0, 0, NEVER));
        assertEquals(Skill.RUNNING, HudStack.holdMain(Skill.RUNNING, null, 0, 0, NEVER));
        assertNull(HudStack.holdMain(null, Skill.RUNNING, 0, 0, NEVER));
    }

    @Test
    void runningAndJumpingNeverFlipTheMainLine() {
        // Replay: sprinting pays Running every 300 ms on the ground, a jump every 900 ms picks Jumping for 3 s.
        Skill main = null;
        long since = 0;
        long lastRun = NEVER;
        int changes = 0;
        long lastJump = Long.MIN_VALUE / 2;
        for (long t = 0; t < 20_000; t += 50) {
            if (t % 900 == 0) {
                lastJump = t;
            }
            if (t % 300 == 0) {
                lastRun = t;
            }
            Skill picked = t - lastJump < 3000 ? Skill.JUMPING : Skill.RUNNING;
            long sinceGain = main == Skill.RUNNING ? t - lastRun : t - lastJump;
            Skill next = HudStack.holdMain(picked, main, t - since, sinceGain, t - lastRun);
            if (next != main) {
                changes++;
                main = next;
                since = t;
            }
        }
        assertEquals(1, changes, "the first pick, then no flip at all");
        assertEquals(Skill.RUNNING, main, "the steady earner holds the line");
    }

    @Test
    void theColumnDodgesTheChat() {
        // No chat, or chat below the column: no shift.
        assertEquals(0, HudStack.dodgeChat(200, 120, 100, 140, 0, 0, 0, 480));
        assertEquals(0, HudStack.dodgeChat(200, 120, 100, 140, 330, 160, 200, 480));
        // Chat covers the column's rows: moved right of it.
        assertEquals(330 + HudStack.CHAT_GAP - 200, HudStack.dodgeChat(200, 120, 100, 140, 330, 90, 200, 640));
        // No room right of the chat: hide.
        assertEquals(Integer.MIN_VALUE, HudStack.dodgeChat(150, 120, 100, 140, 330, 90, 200, 427));
    }
}
