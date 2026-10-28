package dev.amman.proficiency;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.TempoChain;
import dev.amman.proficiency.skill.TempoRules;
import dev.amman.proficiency.skill.TempoRules.Live;
import dev.amman.proficiency.skill.TempoRules.Role;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempoRulesTest {

    @Test
    void everySkillHasTheRoleHeChose() {
        Set<Skill> weapons = Set.of(Skill.SWORDS, Skill.AXES, Skill.MACES, Skill.TRIDENTS, Skill.UNARMED,
                Skill.ARCHERY, Skill.CROSSBOWS, Skill.BEASTSLAYING);
        Set<Skill> riders = Set.of(Skill.COURAGE, Skill.CHARGER, Skill.GUARDIAN, Skill.TACTICIAN, Skill.BLOCKING);
        Set<Skill> exempt = Set.of(Skill.ENDURANCE, Skill.SOCIAL, Skill.NIGHTWALKER, Skill.HOMEBUILDER);
        for (Skill skill : Skill.VALUES) {
            Role want = weapons.contains(skill) ? Role.WEAPON : riders.contains(skill) ? Role.RIDE
                    : exempt.contains(skill) ? Role.EXEMPT : Role.OWN;
            assertEquals(want, TempoRules.role(skill), skill.id());
        }
        // Every COMBAT-category skill is a weapon, a rider or Endurance: none falls through to the normal rule.
        for (Skill skill : Skill.VALUES) {
            if (skill.category() == SkillCategory.COMBAT) {
                assertTrue(TempoRules.role(skill) != Role.OWN, skill.id());
            }
        }
        assertEquals(weapons, Set.of(TempoRules.WEAPONS));
    }

    @Test
    void weaponsTakeTheCombatWindowAndStepAndMiningKeepsItsOwn() {
        assertEquals(140, TempoRules.window(Skill.SWORDS, 60, 140));
        assertEquals(140, TempoRules.window(Skill.ARCHERY, 60, 140));
        assertEquals(60, TempoRules.window(Skill.MINING, 60, 140));
        assertEquals(0.05, TempoRules.perStep(Skill.AXES, 0.02, 0.05));
        assertEquals(0.02, TempoRules.perStep(Skill.MINING, 0.02, 0.05));
        assertEquals(10, TempoRules.maxSteps(0.50, 0.05));
        assertEquals(25, TempoRules.maxSteps(0.50, 0.02));
        assertEquals(0, TempoRules.maxSteps(0.50, 0.0));
    }

    @Test
    void aSevenSecondWindowHoldsAFiveSecondGapAndBreaksAfterEight() {
        TempoChain sword = new TempoChain();
        sword.advance(1000, 140, 10, 1200);
        sword.advance(1010, 140, 10, 1200);
        assertTrue(sword.alive(1010 + 100, 140, 1200));
        sword.advance(1010 + 100, 140, 10, 1200); // 5 s pause
        assertEquals(2, sword.steps);
        assertFalse(sword.alive(1110 + 160, 140, 1200)); // 8 s
        sword.advance(1110 + 160, 140, 10, 1200);
        assertEquals(0, sword.steps);

        TempoChain mining = new TempoChain();
        mining.advance(1000, 60, 25, 1200);
        mining.advance(1010, 60, 25, 1200);
        mining.advance(1010 + 80, 60, 25, 1200); // 4 s
        assertEquals(0, mining.steps);
    }

    @Test
    void aChainNobodySteppedIsNotAlive() {
        assertFalse(new TempoChain().alive(0, 140, 1200));
        assertFalse(new TempoChain().alive(50, 140, 1200));
    }

    @Test
    void theChainCapEndsALiveChainForTheRiderToo() {
        TempoChain chain = new TempoChain();
        for (long t = 100; t <= 1300; t += 20) {
            chain.advance(t, 140, 10, 1200);
        }
        assertTrue(chain.alive(1300, 140, 1200));
        assertFalse(chain.alive(1301, 140, 1200)); // started at 100, past 1200 ticks
    }

    @Test
    void aRiderReadsTheMostRecentlySteppedLiveWeaponChain() {
        Map<Skill, Live> state = new EnumMap<>(Skill.class);
        assertNull(TempoRules.ride(state::get));
        state.put(Skill.SWORDS, new Live(100, 6, true));
        assertEquals(Skill.SWORDS, TempoRules.ride(state::get));
        state.put(Skill.ARCHERY, new Live(120, 1, true));
        assertEquals(Skill.ARCHERY, TempoRules.ride(state::get)); // stepped later, even with fewer steps
        state.put(Skill.ARCHERY, new Live(120, 1, false));
        assertEquals(Skill.SWORDS, TempoRules.ride(state::get)); // a dead chain is skipped
        state.put(Skill.BEASTSLAYING, new Live(100, 2, true));
        assertEquals(Skill.SWORDS, TempoRules.ride(state::get)); // same tick: more steps wins
        state.put(Skill.SWORDS, new Live(100, 6, false));
        assertEquals(Skill.BEASTSLAYING, TempoRules.ride(state::get));
        // Riders and non-weapons are never a source.
        state.clear();
        state.put(Skill.COURAGE, new Live(500, 9, true));
        state.put(Skill.MINING, new Live(500, 9, true));
        state.put(Skill.ENDURANCE, new Live(500, 9, true));
        assertNull(TempoRules.ride(state::get));
    }

    @Test
    void aJumpCritDoublesTheNextGapOnly() {
        long grace = TempoRules.critWindow(140, 2.0);
        assertEquals(280, grace);
        assertEquals(0, TempoRules.critWindow(140, 1.0)); // the setting at 1.0 turns it off
        TempoChain sword = new TempoChain();
        sword.advance(1000, 140, 10, 1200);
        sword.advance(1010, 140, 10, 1200, grace); // a crit
        assertEquals(280, sword.window(140));
        assertTrue(sword.alive(1010 + 240, 140, 1200)); // 12 s later still alive
        sword.advance(1010 + 240, 140, 10, 1200); // an ordinary hit: the grace is spent
        assertEquals(2, sword.steps);
        assertEquals(140, sword.window(140));
        sword.advance(1250 + 160, 140, 10, 1200); // 8 s after an ordinary hit: broken
        assertEquals(0, sword.steps);
        // Crit after crit keeps the long window for each next gap.
        sword.advance(2000, 140, 10, 1200, grace);
        sword.advance(2240, 140, 10, 1200, grace);
        sword.advance(2480, 140, 10, 1200, grace);
        assertEquals(2, sword.steps);
        // A copy (the Jade preview) keeps the grace.
        assertEquals(280, sword.copy().window(140));
    }

    @Test
    void aCritIsAnnouncedEvenAtTheCapWithTheLongerWindow() {
        TempoChain chain = new TempoChain();
        for (long t = 100; t < 120; t++) {
            chain.advance(t, 140, 10, 1200);
            chain.takeAnnouncement(t, 140);
        }
        assertEquals(10, chain.steps);
        chain.advance(120, 140, 10, 1200);
        assertFalse(chain.takeAnnouncement(120, 140)); // capped, no change, before the heartbeat
        chain.advance(121, 140, 10, 1200, 280);
        assertTrue(chain.takeAnnouncement(121, chain.window(140))); // the grace changed
        chain.advance(122, 140, 10, 1200, 280);
        assertFalse(chain.takeAnnouncement(122, chain.window(140)));
        assertTrue(chain.takeAnnouncement(122, chain.window(140), true)); // a crit forces it
        chain.advance(123, 140, 10, 1200);
        assertTrue(chain.takeAnnouncement(123, chain.window(140))); // back to 7 s: the client must hear it
    }

    @Test
    void aRiderInheritsTheCritGrace() {
        TempoChain sword = new TempoChain();
        sword.advance(100, 140, 10, 1200);
        sword.advance(110, 140, 10, 1200, 280);
        Map<Skill, Live> state = new EnumMap<>(Skill.class);
        long now = 110 + 240;
        state.put(Skill.SWORDS, new Live(sword.lastTick, sword.steps, sword.alive(now, 140, 1200)));
        assertEquals(Skill.SWORDS, TempoRules.ride(state::get));
    }

    @Test
    void aCritFlaresWhiteAndTheLongerWindowKeepsTheFireBurning() {
        dev.amman.proficiency.client.TempoState hud = new dev.amman.proficiency.client.TempoState(Skill.VALUES.length);
        int swords = Skill.SWORDS.ordinal();
        hud.accept(swords, 3, 10, 1150, 14_000, 1000); // the crit's grace: 14 s
        hud.acceptCrit(swords, 1000);
        assertEquals(1f, hud.critFlare(swords, 1050));
        assertTrue(hud.critFlare(swords, 1500) > 0f && hud.critFlare(swords, 1500) < 1f);
        assertEquals(0f, hud.critFlare(swords, 1000 + dev.amman.proficiency.client.TempoFlames.CRIT_FLARE_MS));
        assertEquals(0f, hud.lapse(swords, 1000 + 12_000)); // 12 s on: still burning
        assertTrue(hud.lapse(swords, 1000 + 15_000) > 0f);
    }

    @Test
    void theHudLineOfASupportSkillShowsTheFireItRides() {
        dev.amman.proficiency.client.TempoState hud = new dev.amman.proficiency.client.TempoState(Skill.VALUES.length);
        int courage = Skill.COURAGE.ordinal();
        int swords = Skill.SWORDS.ordinal();
        int archery = Skill.ARCHERY.ordinal();
        assertEquals(-1, hud.source(courage, 0)); // no weapon chain: no flames
        assertEquals(Skill.MINING.ordinal(), hud.source(Skill.MINING.ordinal(), 0));
        assertEquals(-1, hud.source(Skill.ENDURANCE.ordinal(), 0));
        hud.accept(swords, 4, 10, 1200, 7000, 1000);
        assertEquals(swords, hud.source(courage, 2000));
        hud.accept(archery, 1, 10, 1050, 7000, 3000);
        assertEquals(archery, hud.source(courage, 4000)); // the newer chain
        // Archery lapses (7 s after 3000) while Swords was renewed: back to Swords.
        hud.accept(swords, 5, 10, 1250, 7000, 9000);
        assertEquals(swords, hud.source(courage, 11000));
        // Every chain lapsed: the newest still dying down keeps its smoke on the line, then nothing.
        assertEquals(swords, hud.source(courage, 16500));
        assertEquals(-1, hud.source(courage, 60000));
    }
}
