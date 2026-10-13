#!/usr/bin/env python3
"""Writes a deterministic day of Hearth telemetry in the mod's line format (schema 1).

  hearth_fixture.py OUTDIR

OUTDIR/hearthtown/2026-10-08.jsonl: one server, two players (5 active hours together) at one Hearth,
with every Hearth kind the mod counts (docs/HEARTH-CONTRACT.md, "Telemetry"): charge per pool and
source (and what the hourly cap turned away), the streak held at home, Warmth fills, temp HP spent
away, Tales banked, paid and lost, two Chronicle ratings, a siege day (one rolled, one horn, one
defended, one unearned), boon time, Homebuilder's XP and Homeward. The numbers are round so
tools/tests/test_balance_hearth.py can check each line of the report's Hearth section by hand.
"""
import datetime as dt
import json
import os
import sys

DAY = dt.date(2026, 10, 8)
AMMAN = ("Amman", "11111111-1111-1111-1111-111111111111")
ROWAN = ("Rowan", "44444444-4444-4444-4444-444444444444")


def lines():
    # A fixed UTC instant (21:00 in Warsaw), so the file is the same on any machine.
    t = int(dt.datetime(2026, 10, 8, 19, 0, tzinfo=dt.timezone.utc).timestamp() * 1000)
    out = [{"v": 1, "t": t, "type": "meta", "curve": [8, 2, 1.35], "proc_unlock": 25}]

    def who(p):
        return {"v": 1, "t": t, "player": p[0], "uuid": p[1]}

    def hearth(p, kind, label, source, n, value):
        row = {"v": 1, "t": t} if p is None else who(p)
        out.append({**row, "type": "hearth", "kind": kind, "label": label, "source": source, "n": n,
                    "value": value})

    # Players: 3 + 2 active hours.
    for p, active in ((AMMAN, 10800), (ROWAN, 7200)):
        out.append({**who(p), "type": "player", "online_s": active + 600, "active_s": active, "deaths": 0,
                    "streak_lost": 0})
    # XP: Mining for both, Homebuilder's three kinds for Amman.
    for p, xp in ((AMMAN, 2000.0), (ROWAN, 1000.0)):
        out.append({**who(p), "type": "xp", "skill": "mining", "kind": "block", "n": 200, "base": xp / 2, "xp": xp})
        out.append({**who(p), "type": "skill", "skill": "mining", "level": 30, "need": 120, "active_s": 3600,
                    "levelups": 1, "procs": 0, "rolls": 0, "roll_hits": 0, "chance_sum": 0, "deaths": 0,
                    "xp_lost": 0})
    for kind, xp in (("home", 120.0), ("action", 300.0), ("first-time", 15.0)):
        out.append({**who(AMMAN), "type": "xp", "skill": "homebuilder", "kind": kind, "n": 3, "base": xp, "xp": xp})

    # Charge per pool and source (ledger sources), and the cap's refusals.
    for p, pool, source, n, value in ((AMMAN, "hearty", "mining", 40, 300.0), (ROWAN, "hearty", "farming", 20, 100.0),
                                      (AMMAN, "regen", "decorating", 5, 50.0), (AMMAN, "fortified", "masonry", 8, 80.0),
                                      (ROWAN, "workshop", "cooking", 6, 60.0), (ROWAN, "homecoming", "social", 4, 20.0),
                                      (AMMAN, "homecoming", "tales", 1, 30.0), (AMMAN, "homecoming", "chronicle", 3, 25.0),
                                      (ROWAN, "homecoming", "feast", 2, 40.0), (AMMAN, "hearty", "capped", 3, 45.0)):
        hearth(p, "hearth_charge", pool, source, n, value)
    for pool in ("hearty", "regen", "fortified", "workshop", "homecoming"):
        hearth(AMMAN, "hearth_charge", pool, "siege", 1, 10.0)

    # 2 h of 5 at home (the streak held).
    hearth(AMMAN, "streak_paused", "active", "", 90, 5400.0)
    hearth(ROWAN, "streak_paused", "idle", "", 30, 1800.0)
    # Warmth: four rises to full taking 200 s in all, one night's sleep.
    hearth(AMMAN, "hearth_warm", "rise", "", 4, 200.0)
    hearth(ROWAN, "hearth_warm", "bed", "", 1, 1.0)
    # Temp HP spent away: 12 points to bosses, 8 to the rest.
    hearth(AMMAN, "hearth_absorb", "boss", "", 3, 12.0)
    hearth(AMMAN, "hearth_absorb", "other", "", 5, 8.0)
    # Tales: 60 banked, 45 paid, 15 lost to one death; two pages rated 4 and 2 stars.
    hearth(AMMAN, "hearth_tales", "banked", "running", 50, 40.0)
    hearth(AMMAN, "hearth_tales", "banked", "wayfaring", 3, 20.0)
    hearth(AMMAN, "hearth_tales", "paid", "", 1, 45.0)
    hearth(ROWAN, "hearth_tales", "lost", "", 1, 15.0)
    hearth(AMMAN, "tale_rating", "4", "chronicle", 1, 6.0)
    hearth(AMMAN, "tale_rating", "2", "chronicle", 1, 3.0)
    # Sieges: the condition held 2 h, one rolled, one horn, waves of 4 and 6, one defended, one unearned.
    hearth(None, "hearth_siege", "condition", "", 7200, 7200.0)
    hearth(None, "hearth_siege", "roll", "", 120, 1.2)
    hearth(None, "hearth_siege", "hit", "", 1, 1.0)
    hearth(None, "hearth_siege", "rolled", "", 1, 1.0)
    hearth(AMMAN, "hearth_siege", "horn", "", 1, 1.0)
    hearth(None, "hearth_siege", "called", "", 1, 1.0)
    hearth(None, "hearth_siege", "postpone", "", 1, 1.0)
    hearth(None, "hearth_siege", "wave", "3", 3, 12.0)
    hearth(None, "hearth_siege", "wave", "4", 3, 18.0)
    hearth(AMMAN, "hearth_siege", "kill", "minecraft:zombie", 25, 25.0)
    hearth(None, "hearth_siege", "unearned", "minecraft:skeleton", 2, 2.0)
    hearth(None, "hearth_siege", "outcome", "defended", 1, 1.0)
    hearth(None, "hearth_siege", "outcome", "unearned", 1, 1.0)
    # Boon time: Hearty II an hour at home and half an hour Provisioned away, regen I half an hour.
    hearth(AMMAN, "hearth_boon", "hunger_slow", "II", 3600, 3600.0)
    hearth(AMMAN, "hearth_boon", "hunger_slow", "provisioned", 1800, 1800.0)
    hearth(ROWAN, "hearth_boon", "regen_out_of_combat", "I", 1800, 1800.0)
    # Homeward: three uses on foot or by the bell (600 blocks saved), one to another dimension, three cancels.
    hearth(AMMAN, "homeward", "use", "channel", 2, 500.0)
    hearth(AMMAN, "homeward", "use", "bell", 1, 100.0)
    hearth(AMMAN, "homeward", "use_other_dimension", "channel", 1, 0.0)
    hearth(AMMAN, "homeward", "cancel", "hurt", 1, 1.0)
    hearth(AMMAN, "homeward", "cancel", "moved", 2, 2.0)
    return out


def write(outdir):
    folder = os.path.join(outdir, "hearthtown")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "%s.jsonl" % DAY), "w", encoding="utf-8") as fh:
        fh.write("\n".join(json.dumps(line) for line in lines()) + "\n")
    return folder


if __name__ == "__main__":
    write(sys.argv[1])
