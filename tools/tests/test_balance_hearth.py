#!/usr/bin/env python3
"""python3 tools/tests/test_balance_hearth.py   (stdlib unittest, no network)

The Hearth section of tools/balance_report.py against the committed fixture
tools/tests/fixtures/balance/hearthtown/ (written by tools/tests/hearth_fixture.py), number by number.
"""
import datetime as dt
import os
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
import balance_report as B  # noqa: E402
import hearth_fixture as F  # noqa: E402

DAY = dt.date(2026, 10, 8)
FOLDER = os.path.join(HERE, "fixtures", "balance", "hearthtown")


def read(path):
    with open(path, encoding="utf-8") as fh:
        return fh.read()


class HearthSection(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        rows, files, bad = B.load([("hearthtown", FOLDER)], DAY, DAY)
        cls.d = B.build(rows, files, bad, DAY, DAY, False, B.skill_ids())
        cls.hs = cls.d["hearth"]
        cls.md = B.markdown(cls.d)
        cls.page = B.page(cls.d)

    def test_the_committed_fixture_matches_the_generator(self):
        with tempfile.TemporaryDirectory() as fresh:
            folder = F.write(fresh)
            name = "%s.jsonl" % DAY
            self.assertEqual(read(os.path.join(folder, name)), read(os.path.join(FOLDER, name)))

    def test_charge_per_hour_per_pool_and_at_home(self):
        hs = self.hs
        self.assertAlmostEqual(5.0, hs["active_h"])
        self.assertAlmostEqual(2.0, hs["home_h"])
        hearty = hs["pools"]["hearty"]
        self.assertAlmostEqual(410.0, hearty["charge"], msg="mining 300 + farming 100 + siege 10")
        self.assertAlmostEqual(45.0, hearty["capped"])
        self.assertAlmostEqual(82.0, hearty["per_hour"])
        self.assertAlmostEqual(205.0, hearty["per_home_hour"])
        self.assertAlmostEqual(125.0, hs["pools"]["homecoming"]["charge"])
        self.assertIn("| hearty | 410 | 82.0 | 205.0 | 45 |", self.md)

    def test_time_to_each_tier_at_the_real_rate(self):
        tiers = self.hs["pools"]["hearty"]["tier_h"]
        self.assertAlmostEqual(200 / 82.0, tiers[0])
        self.assertAlmostEqual(8000 / 82.0, tiers[5])
        regen = self.hs["pools"]["regen"]["tier_h"]
        self.assertAlmostEqual(200 / 12.0, regen[0], msg="regen: 60 charge in 5 active hours")

    def test_charge_per_pool_and_source(self):
        rows = {(r["pool"], r["source"]): r for r in self.hs["by_source"]}
        mining = rows[("hearty", "mining")]
        self.assertAlmostEqual(300.0, mining["charge"])
        self.assertAlmostEqual(60.0, mining["per_hour"])
        self.assertAlmostEqual(300 / 410.0, mining["share"])
        self.assertNotIn(("hearty", "capped"), rows, "what the cap turned away is not a source")
        self.assertEqual("hearty", self.hs["by_source"][0]["pool"], "rows run in pool order")
        self.assertEqual("mining", self.hs["by_source"][0]["source"], "largest first within a pool")
        for text in (self.md, self.page):
            self.assertIn("Charge by pool and source", text)
        self.assertIn("| homecoming | feast | 40 | 8.0 | 32% |", self.md)

    def test_share_of_play_at_home(self):
        self.assertAlmostEqual(0.4, self.hs["home_share"])
        self.assertIn("Share of active time at home (streak paused): 40% (2.0 h).", self.md)

    def test_warmth_time_to_full_and_bed_share(self):
        w = self.hs["warmth"]
        self.assertEqual(4, w["rises"])
        self.assertAlmostEqual(50.0, w["mean_rise_s"])
        self.assertEqual(1, w["beds"])
        self.assertAlmostEqual(0.2, w["bed_share"])
        self.assertIn("Warmth: filled to full by standing at a Hearth 4x (mean 50.0 s to full), by a night's "
                      "sleep 1x (20% of fills).", self.md)

    def test_temp_hp_spent_away_with_boss_hits(self):
        a = self.hs["absorb"]
        self.assertAlmostEqual(20.0, a["total"])
        self.assertAlmostEqual(12.0, a["boss"])
        self.assertAlmostEqual(0.6, a["boss_share"])
        self.assertAlmostEqual(4.0, a["per_hour"])
        self.assertIn("Temp HP spent away from home: 20 points over 8 hits (4.0 per active hour); boss hits took "
                      "12 of it (60%).", self.md)

    def test_tales_banked_paid_lost_and_chronicle_ratings(self):
        t = self.hs["tales"]
        self.assertAlmostEqual(60.0, t["banked"])
        self.assertEqual({"running": 40.0, "wayfaring": 20.0}, t["banked_by"])
        self.assertAlmostEqual(45.0, t["paid"])
        self.assertAlmostEqual(15.0, t["lost"])
        self.assertAlmostEqual(0.25, t["lost_share"])
        self.assertEqual({"4": 1, "2": 1}, t["stars"])
        self.assertAlmostEqual(3.0, t["mean_stars"])
        self.assertAlmostEqual(9.0, t["rated_charge"])
        self.assertAlmostEqual(16.0, t["flat_charge"], msg="the Chronicle's 25 charge less the 9 from ratings")
        self.assertIn("Tales: 60 banked away (running 40, wayfaring 20), 45 paid in at a Hearth, 15 lost in 1 death(s) "
                      "(25% of banked). Chronicle: 16 charge from the write bonus; 2 pages rated (2 stars 1, "
                      "4 stars 1; mean 3.0), 9 charge from ratings.", self.md)

    def test_sieges_per_ten_hours_with_outcomes_and_unearned(self):
        s = self.hs["sieges"]
        self.assertAlmostEqual(4.0, s["per_10h"], msg="one rolled and one horn in 5 active hours")
        self.assertAlmostEqual(4.0, s["ended_per_10h"])
        self.assertEqual({"defended": 1, "unearned": 1}, s["outcomes"])
        self.assertAlmostEqual(2.0, s["condition_h"])
        self.assertAlmostEqual(5.0, s["mean_wave"])
        self.assertEqual(25, s["kills"])
        self.assertEqual(2, s["unearned_falls"])
        self.assertEqual(1, s["postponed"])
        self.assertIn("outcomes: defended 1, unearned 1; mean wave 5.0 mobs. Siege kills 25, siege mobs lost "
                      "without a player's hand 2 (unearned), nights postponed 1.", self.md)

    def test_boon_time(self):
        b = self.hs["boons"]
        self.assertAlmostEqual(1.0, b["hunger_slow"]["home_h"])
        self.assertAlmostEqual(0.5, b["hunger_slow"]["away_h"])
        self.assertAlmostEqual(0.5, b["regen_out_of_combat"]["home_h"])
        self.assertIn("Boon time, Warmth-weighted: hunger_slow 1.0 h (+0.5 h Provisioned away), "
                      "regen_out_of_combat 0.5 h.", self.md)

    def test_homebuilder_share_and_homeward(self):
        m = self.hs["homebuilder"]
        self.assertAlmostEqual(435.0, m["xp"])
        self.assertAlmostEqual(120.0, m["share_xp"])
        self.assertAlmostEqual(300.0, m["milestone_xp"])
        self.assertAlmostEqual(435.0 / 3435.0, m["of_all"])
        self.assertAlmostEqual(87.0, m["per_hour"])
        self.assertEqual(4, m["uses"])
        self.assertEqual(1, m["bell_uses"])
        self.assertEqual(1, m["other_dim"])
        self.assertAlmostEqual(200.0, m["mean_saved"])
        self.assertEqual({"hurt": 1, "moved": 2}, m["cancels"])
        self.assertIn("Homebuilder: 435 XP (87.0 per active hour, 13% of all XP): the at-home share 120, milestones "
                      "300, first-time 15. Homeward: 4 uses (1 after a come-home bell, 1 to another dimension), mean "
                      "200.0 blocks saved; cancelled: hurt 1, moved 2.", self.md)

    def test_a_label_that_is_not_a_star_count_is_not_in_the_mean(self):
        # tale_rating labels are the page's stars; a label that is no digit adds no stars and no rating.
        events = {("tale_rating", "5", "chronicle"): [2, 20.0], ("tale_rating", "skip", "chronicle"): [2, 0.0]}
        t = B.tales_summary(events, {"chronicle": 20.0})
        self.assertAlmostEqual(5.0, t["mean_stars"], msg="two five-star pages, two that carry no stars")
        only_odd = B.tales_summary({("tale_rating", "skip", "chronicle"): [3, 0.0]}, {"chronicle": 1.0})
        self.assertIsNone(only_odd["mean_stars"], "nothing to average")

    def test_hours_at_home_are_player_hours_and_the_report_says_so(self):
        # Two players at home together for one hour: the Hearth gained 240, the report shows 120 per
        # player-hour (2 h), the same convention as "per active hour".
        def line(who, kind, label, source, value, n=1):
            return {"v": 1, "t": 1, "type": "hearth", "player": who, "uuid": who, "kind": kind,
                    "label": label, "source": source, "n": n, "value": value, "_world": "w"}
        rows = [line("a", "hearth_charge", "hearty", "mining", 240.0),
                line("a", "streak_paused", "active", "", 3600.0), line("b", "streak_paused", "active", "", 3600.0)]
        players = {("w", "a"): {"active_s": 3600.0}, ("w", "b"): {"active_s": 3600.0}}
        hs = B.hearth_summary(rows, players, {})
        self.assertAlmostEqual(2.0, hs["home_h"])
        self.assertAlmostEqual(120.0, hs["pools"]["hearty"]["per_home_hour"])
        self.assertAlmostEqual(120.0, hs["pools"]["hearty"]["per_hour"], msg="active hours are player-hours too")
        for text in (self.md, self.page):
            self.assertIn("Per player-hour at home", text)
            self.assertIn("count twice", text)

    def test_every_line_is_on_the_page_too(self):
        for line in B.hearth_lines(self.hs):
            self.assertIn(B.h(line), self.page)

    def test_parts_without_lines_stay_quiet(self):
        empty = B.hearth_summary([], {}, {})
        for key in ("warmth", "absorb", "tales", "sieges", "boons", "homebuilder"):
            self.assertIsNone(empty[key], key)
        self.assertEqual([], B.hearth_lines(empty))
        self.assertFalse(empty["any"])


if __name__ == "__main__":
    unittest.main()
