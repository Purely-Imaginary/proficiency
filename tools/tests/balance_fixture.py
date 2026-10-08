#!/usr/bin/env python3
"""Writes deterministic synthetic telemetry in the mod's line format (schema 1).

  balance_fixture.py OUTDIR [--end YYYY-MM-DD]

OUTDIR gets two servers, pandowo/ and lesznica/, each a telemetry folder with three day files.
Built so the report has something to say: mining runs away (5x the others), swords is a
runaway on one server only in the combined view, running and jumping are slow, courage and
tactician never pay (dead), mining's proc rate is far off its chance, and one player dies twice.
"""
import datetime as dt
import json
import os
import sys

SKILLS = ("swords axes maces tridents unarmed blocking endurance archery crossbows mining "
          "woodcutting excavation farming fishing running sneaking jumping swimming smithing "
          "cooking alchemy spellcasting engineering beastslaying wayfaring spelunking masonry "
          "decorating social nightwalker courage guardian charger tactician").split()
DEAD = {"courage", "tactician"}
RATES = {"mining": 600.0, "running": 30.0, "jumping": 25.0}   # XP per engaged hour, else 120
MIXES = {"mining": {"block": 0.8, "first-time": 0.1, "action": 0.1},
         "swords": {"mob": 0.5, "kill": 0.4, "first-time": 0.1},
         "running": {"movement": 1.0}}
CURVE = [8, 2, 1.35]


def need(level):
    return max(1.0, CURVE[0] + CURVE[1] * (level + 1) ** CURVE[2])


def write(outdir, end):
    servers = {"pandowo": [("Amman", "11111111-1111-1111-1111-111111111111", 1.0),
                           ("Rowan", "22222222-2222-2222-2222-222222222222", 0.7)],
               "lesznica": [("Tomek", "33333333-3333-3333-3333-333333333333", 0.9)]}
    for server, people in servers.items():
        folder = os.path.join(outdir, server)
        os.makedirs(folder, exist_ok=True)
        for back in (2, 1, 0):
            day = end - dt.timedelta(days=back)
            lines = []
            t = int(dt.datetime.combine(day, dt.time(20, 0)).timestamp() * 1000)
            lines.append(json.dumps({"v": 1, "t": t, "type": "meta", "curve": CURVE,
                                     "proc_unlock": 25}))
            for name, uuid, energy in people:
                head = {"v": 1, "t": t, "player": name, "uuid": uuid}
                active = 7200 * energy
                lines.append(json.dumps({**head, "type": "player", "online_s": active * 1.3,
                                         "active_s": active,
                                         "deaths": 2 if (name == "Amman" and back == 1) else 0,
                                         "streak_lost": 5 if (name == "Amman" and back == 1) else 0}))
                for i, skill in enumerate(SKILLS):
                    if skill in DEAD:
                        continue
                    engaged = active * (0.3 if skill in ("mining", "swords") else 0.08)
                    rate = RATES.get(skill, 120.0)
                    xp = rate * engaged / 3600
                    level = 10 + (i * 3) % 40 + (back == 0) * 2
                    for kind, share in MIXES.get(skill, {"block": 0.6, "mob": 0.4}).items():
                        lines.append(json.dumps({**head, "type": "xp", "skill": skill, "kind": kind,
                                                 "n": int(xp * share / 2) + 1,
                                                 "base": round(xp * share / 1.3, 4),
                                                 "xp": round(xp * share, 4)}))
                    rolls = 200 if skill in ("mining", "swords") else 40
                    chance = 0.2
                    hits = 5 if skill == "mining" else int(rolls * chance)
                    died = name == "Amman" and back == 1 and skill == "mining"
                    lines.append(json.dumps({**head, "type": "skill", "skill": skill, "level": level,
                                             "need": round(need(level), 4),
                                             "active_s": round(engaged, 2), "levelups": 1,
                                             "procs": hits + 3, "rolls": rolls, "roll_hits": hits,
                                             "chance_sum": rolls * chance, "deaths": int(died),
                                             "xp_lost": 40.5 if died else 0}))
            with open(os.path.join(folder, "%s.jsonl" % day), "w", encoding="utf-8") as fh:
                fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    out = sys.argv[1]
    end = dt.date.fromisoformat(sys.argv[sys.argv.index("--end") + 1]) if "--end" in sys.argv \
        else dt.date.today()
    write(out, end)
