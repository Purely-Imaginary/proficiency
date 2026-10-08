#!/usr/bin/env python3
"""python3 tools/tests/test_balance_report.py   (stdlib unittest, no network)"""
import datetime as dt
import json
import os
import re
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, ".."))
sys.path.insert(0, HERE)
import balance_report as B  # noqa: E402
import balance_fixture as F  # noqa: E402

END = dt.date(2026, 10, 8)


class Report(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        F.write(cls.tmp.name, END)
        cls.out = os.path.join(cls.tmp.name, "out")
        args = ["pandowo=" + os.path.join(cls.tmp.name, "pandowo"),
                "lesznica=" + os.path.join(cls.tmp.name, "lesznica"),
                "--out", cls.out, "--until", str(END)]
        B.main(args)
        cls.md = open(os.path.join(cls.out, "report.md"), encoding="utf-8").read()
        cls.html = open(os.path.join(cls.out, "report.html"), encoding="utf-8").read()
        rows, files, bad = B.load([("pandowo", os.path.join(cls.tmp.name, "pandowo")),
                                   ("lesznica", os.path.join(cls.tmp.name, "lesznica"))],
                                  END - dt.timedelta(days=6), END)
        cls.data = B.build(rows, files, bad, END - dt.timedelta(days=6), END, False,
                           B.skill_ids())
        cls.by = {r["skill"]: r for r in cls.data["table"]}

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_skill_list_matches_the_java_enum(self):
        java = B.skill_ids()
        self.assertEqual(34, len(java))
        self.assertEqual(sorted(B.SKILLS), sorted(java))

    def test_rate_is_the_median_over_players_with_their_count(self):
        mining = self.by["mining"]
        self.assertEqual(3, mining["rate_players"])
        self.assertAlmostEqual(600.0, mining["rate"], places=1)

    def test_runaway_dead_and_slow(self):
        self.assertTrue(self.by["mining"]["runaway"])
        self.assertFalse(self.by["swords"]["runaway"])
        self.assertTrue(self.by["courage"]["dead"])
        self.assertTrue(self.by["tactician"]["dead"])
        self.assertFalse(self.by["axes"]["dead"])
        self.assertTrue(self.by["running"]["slow"])

    def test_source_mix_and_proc_flag(self):
        self.assertAlmostEqual(0.8, self.by["mining"]["mix"]["block"] / sum(self.by["mining"]["mix"].values()), 3)
        self.assertTrue(self.by["mining"]["proc_off"])
        self.assertFalse(self.by["swords"]["proc_off"])
        self.assertAlmostEqual(0.2, self.by["swords"]["expect"], 3)

    def test_time_to_level_uses_the_curve(self):
        run = self.by["running"]
        self.assertGreater(run["h_total"], self.by["mining"]["h_total"] * 15)
        expect = sum(B.need_at(l, (8, 2, 1.35)) for l in range(100)) / run["rate"]
        self.assertAlmostEqual(expect, run["h_total"], places=3)

    def test_deaths(self):
        pandowo = self.data["deaths"]["pandowo"]
        self.assertEqual(2, pandowo["deaths"])
        self.assertEqual(5, pandowo["streak_lost"])
        self.assertAlmostEqual(40.5, pandowo["xp_lost"])

    def test_outputs_have_every_section(self):
        for heading in ("XP per active hour", "Source mix", "Dead skills", "Time to level",
                        "Proc rate", "What deaths cost"):
            self.assertIn(heading, self.md)
            self.assertIn(heading, self.html)
        self.assertIn("courage, tactician", self.md)
        self.assertNotIn("http://", self.html.replace("http://www.w3.org", ""))
        self.assertNotIn("<script src", self.html)

    def test_anon_hides_names_and_ids(self):
        out = os.path.join(self.tmp.name, "anon")
        B.main(["pandowo=" + os.path.join(self.tmp.name, "pandowo"), "--out", out, "--anon",
                "--until", str(END)])
        text = open(os.path.join(out, "report.md")).read() + open(os.path.join(out, "report.html")).read()
        for secret in ("Amman", "Rowan", "11111111", "22222222"):
            self.assertNotIn(secret, text)
        self.assertIn("Player A", text)

    def test_window_and_empty(self):
        out = os.path.join(self.tmp.name, "late")
        B.main(["pandowo=" + os.path.join(self.tmp.name, "pandowo"), "--out", out, "--since",
                "2026-12-01", "--until", "2026-12-07"])
        self.assertIn("No telemetry in the window", open(os.path.join(out, "report.md")).read())
        narrow = os.path.join(self.tmp.name, "one")
        B.main(["pandowo=" + os.path.join(self.tmp.name, "pandowo"), "--out", narrow,
                "--since", str(END), "--until", str(END)])
        self.assertIn("1 day file", open(os.path.join(narrow, "report.md")).read())

    def test_real_session_fixture_loads(self):
        # Written by a real throwaway NeoForge server with the mod, one player, 2026-10-08.
        folder = os.path.join(HERE, "fixtures", "balance", "real-session")
        out = os.path.join(self.tmp.name, "real")
        B.main(["skilltest=" + folder, "--out", out, "--until", "2026-10-08"])
        md = open(os.path.join(out, "report.md"), encoding="utf-8").read()
        self.assertIn("1 player(s) on skilltest", md)
        self.assertIn("What deaths cost", md)

    def test_committed_fixtures_match_the_generator(self):
        with tempfile.TemporaryDirectory() as fresh:
            F.write(fresh, END)
            for server in ("pandowo", "lesznica"):
                name = "%s/2026-10-08.jsonl" % server
                self.assertEqual(open(os.path.join(fresh, name)).read(),
                                 open(os.path.join(HERE, "fixtures", "balance", name)).read())

    def test_bad_lines_are_skipped(self):
        d = os.path.join(self.tmp.name, "broken")
        os.makedirs(d)
        with open(os.path.join(d, "2026-10-08.jsonl"), "w") as fh:
            fh.write("not json\n{\"v\":9}\n\n" + json.dumps(
                {"v": 1, "t": 1, "type": "player", "player": "X", "uuid": "u", "online_s": 10,
                 "active_s": 10, "deaths": 0, "streak_lost": 0}) + "\n")
        out = os.path.join(self.tmp.name, "broken-out")
        B.main(["s=" + d, "--out", out, "--until", str(END)])
        self.assertIn("2 unreadable", open(os.path.join(out, "report.md")).read())


if __name__ == "__main__":
    unittest.main()
