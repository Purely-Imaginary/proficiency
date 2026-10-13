package dev.amman.proficiency.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RightEdgeTest {

    @AfterEach
    void forget() {
        RightEdge.clear();
    }

    private static RightEdge.Place one(int top, int height, int panelTop, int panelBottom, float strength) {
        return RightEdge.place(top, height, 54, 510, new int[] {panelTop}, new int[] {panelBottom},
                new float[] {strength}, 1);
    }

    @Test
    void panelsStartAtThirtyPercentOrUnderABanner() {
        assertEquals(162, RightEdge.panelTop(540, 0), "GUI 2 at 1080p");
        assertEquals(81, RightEdge.panelTop(270, 0), "GUI 4 at 1080p");
        assertEquals(162, RightEdge.panelTop(540, 120), "a banner above the panel moves nothing");
        assertEquals(184, RightEdge.panelTop(540, 180), "a banner that would cover it pushes it under");
        assertEquals(8, RightEdge.panelTop(10, 0), "never above 8");
    }

    @Test
    void aBoxClearOfEveryPanelStaysPutAndWhole() {
        // The Tales panel 162..238 and a tracker that starts 4 px or more under it.
        RightEdge.Place place = one(242, 86, 162, 238, 1f);
        assertEquals(242, place.top());
        assertEquals(1f, place.alpha());
        assertFalse(place.squeezed());
        assertEquals(400, RightEdge.place(400, 60, 54, 510, new int[0], new int[0], new float[0], 0).top(),
                "no claims");
    }

    @Test
    void anOverlappedBoxStepsUnderThePanelWhenThereIsRoom() {
        // GUI 2 at 1080p, tracker at its default middle: 227..313 under a Tales panel 162..238.
        RightEdge.Place place = one(227, 86, 162, 238, 0.3f);
        assertEquals(242, place.top(), "whole, from the panel's first frame (the caller slides it)");
        assertEquals(1f, place.alpha(), "it stays fully drawn: a tier-up paid in plays on it");
        assertFalse(place.squeezed());
    }

    @Test
    void itGoesOverThePanelWhenThatIsTheShorterMove() {
        // A tracker set high (hearth.trackerY) whose bottom only grazes the panel's top.
        RightEdge.Place place = one(80, 80, 162, 238, 1f);
        assertEquals(78, place.top());
    }

    @Test
    void withNoRoomEitherSideItFadesWithThePanel() {
        // GUI 4 at 1080p: Tales 81..157, a five-row tracker 92..178, the hotbar's rows below 240.
        RightEdge.Place half = RightEdge.place(92, 86, 54, 240, new int[] {81}, new int[] {157}, new float[] {0.5f}, 1);
        assertTrue(half.squeezed());
        assertEquals(92, half.top(), "it stays where it is");
        assertEquals(0.5f, half.alpha(), 1e-6);
        RightEdge.Place full = RightEdge.place(92, 86, 54, 240, new int[] {81}, new int[] {157}, new float[] {1f}, 1);
        assertEquals(0f, full.alpha(), 1e-6);
    }

    @Test
    void twoPanelsThatTouchAreOneBand() {
        // The death recap 162..300 and a Tales panel 302..360 (rare, both at once): the box clears both.
        RightEdge.Place place = RightEdge.place(250, 60, 54, 510, new int[] {162, 302}, new int[] {300, 360},
                new float[] {1f, 1f}, 2);
        assertEquals(364, place.top());
    }

    @Test
    void faintAndEmptyClaimsMoveNothing() {
        assertEquals(227, one(227, 86, 162, 238, 0.01f).top(), "a panel's first frame");
        assertEquals(227, one(227, 86, 238, 238, 1f).top(), "no height");
    }

    @Test
    void claimsAreLiveOnlyWhileRenewed() {
        RightEdge.claim(RightEdge.TALES, 162, 238, 1f, 1000);
        assertEquals(242, RightEdge.place(227, 86, 54, 510, 1100).top());
        assertEquals(227, RightEdge.place(227, 86, 54, 510, 1000 + RightEdge.STALE_MS + 1).top(),
                "a panel that stopped drawing claims nothing");
        RightEdge.claim(RightEdge.TALES, 162, 238, 1f, 2000);
        RightEdge.release(RightEdge.TALES);
        assertEquals(227, RightEdge.place(227, 86, 54, 510, 2000).top(), "a released claim is gone at once");
        RightEdge.claim(RightEdge.DEATH_RECAP, 162, 238, 1f, 3000);
        assertEquals(242, RightEdge.place(227, 86, 54, 510, 3000).top(), "any slot counts");
    }

    @Test
    void theFollowerSlidesThenLandsAndSnapsAfterAPause() {
        RightEdge.Follower follower = new RightEdge.Follower();
        assertEquals(227, follower.follow(227, 0), "the first frame is where it is");
        int y = follower.follow(242, 16);
        assertTrue(y > 227 && y < 242, "one frame moves part of the way: " + y);
        long t = 16;
        for (int i = 0; i < 40; i++) {
            t += 16;
            y = follower.follow(242, t);
        }
        assertEquals(242, y, "it lands");
        assertEquals(227, follower.follow(227, t + 2000), "after a pause it snaps");
    }
}
