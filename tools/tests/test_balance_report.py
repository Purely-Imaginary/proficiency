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

    # -- fixes from the 1.3.0 review ---------------------------------------------------------

    def _server(self, name, rows):
        folder = os.path.join(self.tmp.name, name)
        os.makedirs(folder, exist_ok=True)
        with open(os.path.join(folder, "2026-10-08.jsonl"), "w") as fh:
            fh.write("\n".join(json.dumps(r) for r in rows) + "\n")
        return name + "=" + folder

    def _rows(self, curve, level=20, extra=()):
        head = {"v": 1, "t": 1000, "player": "P", "uuid": "u1"}
        return [{"v": 1, "t": 1000, "type": "meta", "curve": curve, "proc_unlock": 25},
                {**head, "type": "player", "online_s": 4000, "active_s": 3600, "deaths": 0,
                 "streak_lost": 0},
                {**head, "type": "xp", "skill": "mining", "kind": "block", "n": 10, "base": 100,
                 "xp": 600},
                {**head, "type": "skill", "skill": "mining", "level": level, "need": 50,
                 "active_s": 3600, "levelups": 0, "procs": 0, "rolls": 0, "roll_hits": 0,
                 "chance_sum": 0, "deaths": 0, "xp_lost": 0}, *extra]

    def test_each_server_uses_its_own_curve(self):
        a = self._server("slow", self._rows([8, 2, 1.35]))
        b = self._server("fast", self._rows([4, 1, 1.2]))
        rows, files, bad = B.load([("slow", a.split("=")[1]), ("fast", b.split("=")[1])],
                                  END, END)
        d = B.build(rows, files, bad, END, END, False, B.skill_ids())
        mining = {r["skill"]: r for r in d["table"]}["mining"]
        slow = B.hours_between(0, 100, (8, 2, 1.35), 600.0)
        fast = B.hours_between(0, 100, (4, 1, 1.2), 600.0)
        self.assertAlmostEqual(B.median([slow, fast]), mining["h_total"], places=3)
        self.assertNotAlmostEqual(slow, mining["h_total"], places=1)
        self.assertIn("slow: 8 + 2 x (L+1)^1.35", d["curve_text"])
        self.assertIn("fast: 4 + 1 x (L+1)^1.2", d["curve_text"])

    def test_a_row_without_a_level_keeps_the_real_level(self):
        head = {"v": 1, "t": 5000, "player": "P", "uuid": "u1"}
        quiet = {**head, "type": "skill", "skill": "mining", "active_s": 30, "levelups": 0,
                 "procs": 0, "rolls": 0, "roll_hits": 0, "chance_sum": 0, "deaths": 0,
                 "xp_lost": 0}
        rows, files, bad = B.load([("s", self._server("lvl", self._rows([8, 2, 1.35], 37,
                                                                     [quiet])).split("=")[1])],
                                  END, END)
        players, ps, mix, curves = B.analyse(rows, B.skill_ids())
        self.assertEqual(37, ps[("s", "u1", "mining")]["level"])
        self.assertAlmostEqual(3630, ps[("s", "u1", "mining")]["active_s"] , places=3)

    def test_a_chance_sum_above_one_is_not_a_proc_alarm(self):
        head = {"v": 1, "t": 1000, "player": "P", "uuid": "u1"}
        rolls = {**head, "type": "skill", "skill": "swords", "level": 30, "need": 50,
                 "active_s": 100, "levelups": 0, "procs": 40, "rolls": 40, "roll_hits": 40,
                 "chance_sum": 80.0, "deaths": 0, "xp_lost": 0}
        rows, files, bad = B.load([("s", self._server("proc", self._rows([8, 2, 1.35],
                                                                      extra=[rolls])).split("=")[1])],
                                  END, END)
        d = B.build(rows, files, bad, END, END, False, B.skill_ids())
        swords = {r["skill"]: r for r in d["table"]}["swords"]
        self.assertLessEqual(swords["expect"], 2.0)
        self.assertFalse(swords["proc_off"], "certain rolls that all landed are not off")

    def test_rested_and_teaching_xp_get_their_own_column(self):
        head = {"v": 1, "t": 1000, "player": "P", "uuid": "u1"}
        extra = [{**head, "type": "xp", "skill": "mining", "kind": "rested", "n": 0, "base": 0,
                  "xp": 150},
                 {**head, "type": "xp", "skill": "social", "kind": "teaching", "n": 3, "base": 4,
                  "xp": 5}]
        rows, files, bad = B.load([("s", self._server("rested", self._rows(
            [8, 2, 1.35], extra=extra)).split("=")[1])], END, END)
        d = B.build(rows, files, bad, END, END, False, B.skill_ids())
        by = {r["skill"]: r for r in d["table"]}
        self.assertEqual(150, by["mining"]["rested"])
        self.assertEqual(5, by["social"]["teaching"])
        self.assertEqual(0, by["swords"]["rested"])
        self.assertEqual("-", B.rested_text(by["swords"]))
        self.assertEqual("150 (20%)", B.rested_text(by["mining"]))  # 150 of 750 total
        md = B.markdown(d)
        self.assertIn("| Rested XP | Teaching XP |", md)
        self.assertIn("150 (20%)", md)
        self.assertIn("Rested XP: 150 of 755 XP", md)
        self.assertIn("<th>Rested XP<th>Teaching XP", B.page(d))

    def test_today_is_the_warsaw_date(self):
        from zoneinfo import ZoneInfo
        self.assertEqual(dt.datetime.now(ZoneInfo("Europe/Warsaw")).date(), B.today())


if __name__ == "__main__":
    unittest.main()
