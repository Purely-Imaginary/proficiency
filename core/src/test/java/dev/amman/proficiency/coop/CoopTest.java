package dev.amman.proficiency.coop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.SkillsWire;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

class CoopTest {

    private static final UUID A = new UUID(1, 2);
    private static final UUID B = new UUID(3, 4);

    // ---- rules --------------------------------------------------------------------------------

    @Test
    void dotsGrowWithTheXpAndStopAtSix() {
        assertEquals(0, CoopRules.dotsFor(0));
        assertEquals(0, CoopRules.dotsFor(-3));
        assertEquals(0, CoopRules.dotsFor(Double.NaN));
        assertEquals(1, CoopRules.dotsFor(0.5));
        assertEquals(2, CoopRules.dotsFor(1));
        assertEquals(3, CoopRules.dotsFor(3));
        assertEquals(4, CoopRules.dotsFor(7));
        assertEquals(5, CoopRules.dotsFor(15));
        assertEquals(6, CoopRules.dotsFor(31));
        assertEquals(6, CoopRules.dotsFor(1e9));
        int last = 0;
        for (double xp = 0.01; xp < 1000; xp *= 1.3) {
            int dots = CoopRules.dotsFor(xp);
            assertTrue(dots >= last && dots <= CoopRules.DOTS_PER_SECOND);
            last = dots;
        }
    }

    @Test
    void aFarPlayerGetsNothingANearOneIsPaidOrFaint() {
        // Radius 16 both: within it and paid flies into the bar.
        assertEquals(CoopRules.Mode.PAYS, CoopRules.mode(5, 16, 16, true));
        assertEquals(CoopRules.Mode.PAYS, CoopRules.mode(16, 16, 16, true));
        // In range but company pays nothing (camaraderie 0, or Social itself): faint.
        assertEquals(CoopRules.Mode.FAINT, CoopRules.mode(5, 16, 16, false));
        // Beyond the watcher's radius but inside the earner's bigger one (Open Circle): faint.
        assertEquals(CoopRules.Mode.FAINT, CoopRules.mode(20, 16, 24, true));
        // Far: nothing.
        assertEquals(CoopRules.Mode.NONE, CoopRules.mode(16.01, 16, 16, true));
        assertEquals(CoopRules.Mode.NONE, CoopRules.mode(500, 16, 24, true));
        assertEquals(CoopRules.Mode.NONE, CoopRules.mode(Double.NaN, 16, 16, true));
        assertTrue(CoopRules.inCompany(10, 16));
        assertFalse(CoopRules.inCompany(17, 16));
    }

    @Test
    void theLedgerBatchesASecondPerEarnerAndNamesTheTopSkill() {
        CoopRules.Ledger ledger = new CoopRules.Ledger();
        ledger.add(A, 3, 2.0, 1.0);
        ledger.add(A, 5, 0.5, 1.0);
        ledger.add(A, 3, 1.0, 1.0);
        ledger.add(B, 7, 4.0, 1.15);
        ledger.add(B, 7, -1.0, 1.15);
        ledger.add(B, 7, Double.POSITIVE_INFINITY, 1.15);
        List<CoopRules.Earned> earned = ledger.drainEarned();
        assertEquals(2, earned.size());
        assertEquals(A, earned.get(0).earner());
        assertEquals(3.5, earned.get(0).xp(), 1e-9);
        assertEquals(3, earned.get(0).topSkill());
        assertEquals(B, earned.get(1).earner());
        assertEquals(7, earned.get(1).topSkill());
        assertTrue(ledger.drainEarned().isEmpty(), "drained");
        // B's own grant carried company: 4 * (1 - 1/1.15).
        assertEquals(4.0 * (1 - 1 / 1.15), ledger.takeOwn(B), 1e-9);
        assertEquals(0.0, ledger.takeOwn(B));
        assertEquals(0.0, ledger.takeOwn(A), "alone: no company XP");
    }

    // ---- wire ---------------------------------------------------------------------------------

    @Test
    void aBatchSurvivesTheWireAndClampsWhatItReads() {
        CoopBatch batch = new CoopBatch(
                List.of(new CoopBatch.Partner(A, "Amman"), new CoopBatch.Partner(B, "Rowan")),
                List.of(new CoopBatch.Stream(B, "Rowan", 4, 12.5f, 5, true, 1.5f, 64f, -3f),
                        new CoopBatch.Stream(A, "Amman", 0, 0.4f, 9, false, 0f, 0f, 0f)),
                0.75f);
        CoopBatch back = roundTrip(batch);
        assertEquals(2, back.partners().size());
        assertEquals("Rowan", back.partners().get(1).name());
        assertEquals(B, back.streams().get(0).id());
        assertEquals(12.5f, back.streams().get(0).xp());
        assertTrue(back.streams().get(0).pays());
        assertEquals(64f, back.streams().get(0).y());
        assertEquals(CoopRules.DOTS_PER_SECOND, back.streams().get(1).dots(), "nine dots is clamped to the cap");
        assertFalse(back.streams().get(1).pays());
        assertEquals(0.75f, back.ownBonus());
        assertTrue(back.hasNews());
        assertFalse(roundTrip(new CoopBatch(batch.partners(), List.of(), 0f)).hasNews());
        assertTrue(CoopBatch.samePartners(batch.partners(), back.partners()));
        assertFalse(CoopBatch.samePartners(batch.partners(), List.of(new CoopBatch.Partner(A, "Amman"))));
    }

    private static CoopBatch roundTrip(CoopBatch batch) {
        ArrayDeque<Object> q = new ArrayDeque<>();
        batch.write(new SkillsWire.Out() {
            public void writeVarInt(int v) {
                q.add(v);
            }

            public void writeFloat(float v) {
                q.add(v);
            }

            public void writeUtf(String v) {
                q.add(v);
            }

            public void writeVarLong(long v) {
                q.add(v);
            }
        });
        return CoopBatch.read(new SkillsWire.In() {
            public int readVarInt() {
                return (Integer) q.poll();
            }

            public float readFloat() {
                return (Float) q.poll();
            }

            public String readUtf() {
                return (String) q.poll();
            }

            public long readVarLong() {
                return (Long) q.poll();
            }
        });
    }

    // ---- pacing -------------------------------------------------------------------------------

    @Test
    void aBatchBecomesATrickleAtMostSixASecondPerPartner() {
        CoopTrickle trickle = new CoopTrickle();
        List<Long> first = trickle.schedule(A, 6, 1000);
        assertEquals(6, first.size());
        for (int i = 1; i < first.size(); i++) {
            assertEquals(CoopTrickle.SPACING_MS, first.get(i) - first.get(i - 1), "evenly spaced");
        }
        assertEquals(1000L, first.get(0));
        // A second batch 100 ms later from the same partner: the window is full until the first dots age out.
        List<Long> second = trickle.schedule(A, 6, 1100);
        List<Long> all = new ArrayList<>(first);
        all.addAll(second);
        for (long t : all) {
            int inWindow = 0;
            for (long u : all) {
                if (u > t - CoopTrickle.WINDOW_MS && u <= t) {
                    inWindow++;
                }
            }
            assertTrue(inWindow <= CoopRules.DOTS_PER_SECOND, "at most six in any second, got " + inWindow);
        }
        // Another partner has its own second.
        assertEquals(6, trickle.schedule(B, 6, 1100).size());
    }

    @Test
    void theSharedSkyIsBounded() {
        CoopTrickle trickle = new CoopTrickle();
        int total = 0;
        for (int p = 0; p < 8; p++) {
            total += trickle.schedule(new UUID(p, p), 6, 0).size();
        }
        assertTrue(total <= CoopTrickle.MAX_LIVE, "never more than the cap in the air: " + total);
        assertTrue(trickle.inAir(0) <= CoopTrickle.MAX_LIVE);
        // Once they land, room again.
        long later = 10_000;
        assertEquals(0, trickle.inAir(later));
        assertEquals(4, trickle.schedule(new UUID(9, 9), 4, later).size());
        assertTrue(trickle.schedule(A, 0, later).isEmpty());
        assertTrue(trickle.schedule(null, 3, later).isEmpty());
    }

    @Test
    void partnerColoursAreStableAndSpread() {
        assertEquals(CoopTrickle.colour(A), CoopTrickle.colour(new UUID(1, 2)));
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 64; i++) {
            int c = CoopTrickle.colour(UUID.nameUUIDFromBytes(("OfflinePlayer:p" + i).getBytes()));
            assertEquals(0xFF, c >>> 24, "opaque");
            seen.add(c);
        }
        assertTrue(seen.size() >= 6, "64 names should use most of the palette, used " + seen.size());
    }

    // ---- projection ---------------------------------------------------------------------------

    private static CoopProjection.Point at(float yaw, float pitch, double tx, double ty, double tz) {
        return CoopProjection.project(0, 0, 0, yaw, pitch, 70, 480, 270, tx, ty, tz, 12);
    }

    @Test
    void aPointStraightAheadIsTheCentre() {
        CoopProjection.Point p = at(0, 0, 0, 0, 10); // yaw 0 faces +Z
        assertTrue(p.onScreen());
        assertEquals(240, p.x(), 0.01);
        assertEquals(135, p.y(), 0.01);
        // Facing west (yaw 90 faces -X).
        CoopProjection.Point west = at(90, 0, -10, 0, 0);
        assertTrue(west.onScreen());
        assertEquals(240, west.x(), 0.01);
    }

    @Test
    void rightIsRightAndUpIsUp() {
        // Facing +Z (south), east (+X) is on the LEFT of the screen.
        CoopProjection.Point east = at(0, 0, 2, 0, 10);
        assertTrue(east.onScreen() && east.x() < 240, "east is left when facing south: " + east);
        CoopProjection.Point west = at(0, 0, -2, 0, 10);
        assertTrue(west.x() > 240, "west is right");
        CoopProjection.Point up = at(0, 0, 0, 2, 10);
        assertTrue(up.y() < 135, "higher is up the screen");
        // Looking down (positive pitch): a point at eye height ahead moves up the screen.
        CoopProjection.Point lookDown = at(0, 30, 0, 0, 10);
        assertTrue(lookDown.y() < 135);
        // The FOV is vertical: a point at the top edge of a 70 degree view.
        double edge = Math.tan(Math.toRadians(35)) * 10;
        CoopProjection.Point top = CoopProjection.project(0, 0, 0, 0, 0, 70, 480, 270, 0, edge * 0.999, 10, 0);
        assertEquals(0, top.y(), 0.5);
    }

    @Test
    void offScreenAndBehindClampToTheEdgeInItsDirection() {
        CoopProjection.Point farLeft = at(0, 0, 50, 0, 1); // east, almost beside: far off the left edge
        assertFalse(farLeft.onScreen());
        assertEquals(12, farLeft.x(), 0.01);
        assertTrue(farLeft.y() >= 12 && farLeft.y() <= 270 - 12);
        CoopProjection.Point behindRight = at(0, 0, -3, 0, -10); // behind, to the west: right edge
        assertFalse(behindRight.onScreen());
        assertEquals(480 - 12, behindRight.x(), 0.01);
        CoopProjection.Point dead = at(0, 0, 0, 0, -10); // straight behind: bottom middle
        assertFalse(dead.onScreen());
        assertEquals(240, dead.x(), 0.01);
        assertEquals(270 - 12, dead.y(), 0.01);
        CoopProjection.Point above = at(0, 0, 0, 100, 1);
        assertEquals(12, above.y(), 0.01);
        // Every clamped point is on the inset rectangle.
        for (int yaw = 0; yaw < 360; yaw += 15) {
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                CoopProjection.Point p = at(yaw, 0, Math.cos(a) * 30, (k - 4) * 3, Math.sin(a) * 30);
                assertTrue(p.x() >= 12 - 0.01 && p.x() <= 468.01 && p.y() >= 11.99 && p.y() <= 258.01,
                        "inside the inset: " + p);
                if (!p.onScreen()) {
                    boolean onEdge = Math.abs(p.x() - 12) < 0.02 || Math.abs(p.x() - 468) < 0.02
                            || Math.abs(p.y() - 12) < 0.02 || Math.abs(p.y() - 258) < 0.02;
                    assertTrue(onEdge, "a clamped point sits on the edge: " + p);
                }
            }
        }
        assertNotEquals(farLeft, behindRight);
    }
}
