#!/usr/bin/env python3
"""Balance report from Proficiency telemetry (python3 stdlib only).

The mod appends one JSON line per bucket every five minutes to
<world>/proficiency/telemetry/YYYY-MM-DD.jsonl (see README, "Balance telemetry"). This reads
one or more such folders and writes report.md and a self-contained report.html:

  XP per active hour per skill (median over players, with the player count)
  source mix per skill, dead skills, runaway skills (more than 3x the median skill)
  time to level at the current rate against the XP curve
  proc rate against the configured chance, and what deaths cost
  the Hearth: charge per pool (and per source) per active hour and per hour at home, time to each
  tier, who feeds it, Warmth fills and bed share, temp HP spent away (boss hits), Tales and Chronicle
  ratings, sieges per 10 h with their outcomes, boon time, Homebuilder's share and Homeward use

  balance_report.py [LABEL=]DIR ... [--out DIR] [--days 7] [--since D] [--until D] [--anon]

DIR is a telemetry folder, or a folder that holds one (a world, or a pulled copy). LABEL names
the server in the report; without it the folder two levels up is used.
"""
import argparse
import datetime as dt
import html
import json
import math
import os
import re
import statistics
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SKILL_JAVA = os.path.join(HERE, "..", "core", "src", "main", "java", "dev", "amman",
                          "proficiency", "skill", "Skill.java")
# Fallback when the script runs away from the repo. tools/tests/test_balance_report.py checks it
# against Skill.java, so a new skill cannot be forgotten here.
SKILLS = ("swords axes maces tridents unarmed blocking endurance archery crossbows mining "
          "woodcutting excavation farming fishing running sneaking jumping swimming smithing "
          "cooking alchemy spellcasting engineering beastslaying wayfaring spelunking masonry "
          "decorating social nightwalker courage guardian charger tactician homebuilder").split()

MIN_ACTIVE_S = 300          # a player needs five minutes in a skill before their rate counts
DEAD_MIN_H = 10             # under this many active hours in total, "no XP" is too little data to call a skill dead
RUNAWAY_FACTOR = 3.0
SLOW_FACTOR = 1 / 3
PROC_MIN_ROLLS = 30
EXCLUDED_KINDS = {"command"}  # an op's /skills addxp is not play
DEFAULT_CURVE = (8.0, 2.0, 1.35)
MAX_LEVEL = 100

# The Hearth (docs/HEARTH-SPEC.md): pool ids in wire order and the default data's numbers. Charge
# the hourly cap turned away is filed under the source "capped" (the charge lane's telemetry).
HEARTH_POOLS = ("hearty", "regen", "fortified", "workshop", "homecoming")
HEARTH_THRESHOLDS = (200, 600, 1400, 2800, 5000, 8000)
HEARTH_HOURLY_CAP = 300.0
HEARTH_CAPPED = "capped"
HEARTH_ROMAN = ("I", "II", "III", "IV", "V", "VI")
HEARTH_COLORS = {"hearty": "#7fae63", "regen": "#6fb0a6", "fortified": "#8c98ab", "workshop": "#d2a249",
                 "homecoming": "#d98ab3"}

KIND_COLORS = {
    "block": "#c2813a", "mob": "#b5483f", "kill": "#8c2f39", "item": "#4f8f6b",
    "first-time": "#d6b33a", "discovery": "#3f86c4", "movement": "#6a8f3a",
    "damage": "#8a5aa8", "share": "#d77fa1", "role": "#3aa6a0", "action": "#7d7f8a",
    "rested": "#4f86ff", "teaching": "#a07fd7", "other": "#9a9a9a",
}


def skill_ids():
    try:
        with open(SKILL_JAVA, encoding="utf-8") as fh:
            found = re.findall(r'^\s+[A-Z_]+\("([a-z_]+)",\s*SkillCategory', fh.read(), re.M)
        if found:
            return found
    except OSError:
        pass
    return list(SKILLS)


# ---------------------------------------------------------------- loading

def find_dir(path):
    for cand in (path, os.path.join(path, "telemetry"),
                 os.path.join(path, "proficiency", "telemetry"),
                 os.path.join(path, "world", "proficiency", "telemetry")):
        if os.path.isdir(cand) and any(f.endswith(".jsonl") for f in os.listdir(cand)):
            return cand
    return path if os.path.isdir(path) else None


def default_label(path):
    parts = os.path.abspath(path).rstrip("/").split("/")
    for i, part in enumerate(parts):
        if part == "proficiency" and i > 0:
            return parts[i - 1] if parts[i - 1] != "world" or i < 2 else parts[i - 2]
    return parts[-1]


def load(sources, since, until):
    """Yield every row of the window, tagged with its server label."""
    rows, files, bad = [], 0, 0
    for label, path in sources:
        folder = find_dir(path)
        if not folder:
            print("balance_report: no telemetry folder in %s" % path, file=sys.stderr)
            continue
        for name in sorted(os.listdir(folder)):
            m = re.fullmatch(r"(\d{4}-\d{2}-\d{2})\.jsonl", name)
            if not m:
                continue
            day = dt.date.fromisoformat(m.group(1))
            if day < since or day > until:
                continue
            files += 1
            with open(os.path.join(folder, name), encoding="utf-8", errors="replace") as fh:
                for line in fh:
                    line = line.strip()
                    if not line:
                        continue
                    try:
                        row = json.loads(line)
                    except ValueError:
                        bad += 1
                        continue
                    if not isinstance(row, dict) or row.get("v") != 1:
                        bad += 1
                        continue
                    row["_world"] = label
                    row["_day"] = day
                    rows.append(row)
    return rows, files, bad


# ---------------------------------------------------------------- analysis

def median(values):
    return statistics.median(values) if values else None


def need_at(level, curve):
    return max(1.0, curve[0] + curve[1] * (level + 1) ** curve[2]) if level < MAX_LEVEL else float("inf")


def hours_between(level_from, level_to, curve, rate):
    total = sum(need_at(l, curve) for l in range(level_from, level_to))
    return total / rate if rate > 0 else None


def analyse(rows, skills):
    players = {}     # (world, uuid) -> dict
    ps = {}          # (world, uuid, skill) -> dict
    mix = {s: {} for s in skills}
    metas = {}       # world -> (t, curve): each server has its own curve settings
    for r in rows:
        t = r.get("t", 0)
        typ = r.get("type")
        if typ == "meta":
            if isinstance(r.get("curve"), list) and len(r["curve"]) == 3 \
                    and t >= metas.get(r["_world"], (-1, None))[0]:
                metas[r["_world"]] = (t, tuple(r["curve"]))
            continue
        if typ == "hearth":
            # Hearth events (charge, boons, sieges): their own section, not player XP rows.
            continue
        key = (r["_world"], r.get("uuid", r.get("player", "?")))
        p = players.setdefault(key, {"name": r.get("player", "?"), "world": r["_world"],
                                     "online_s": 0.0, "active_s": 0.0, "deaths": 0,
                                     "streak_lost": 0, "xp": 0.0})
        if typ == "player":
            p["online_s"] += r.get("online_s", 0)
            p["active_s"] += r.get("active_s", 0)
            p["deaths"] += r.get("deaths", 0)
            p["streak_lost"] += r.get("streak_lost", 0)
            p["name"] = r.get("player", p["name"])
            continue
        skill = r.get("skill")
        if skill not in mix:
            continue
        s = ps.setdefault(key + (skill,), {"xp": 0.0, "base": 0.0, "n": 0, "active_s": 0.0,
                                           "levelups": 0, "procs": 0, "rolls": 0, "roll_hits": 0,
                                           "chance_sum": 0.0, "deaths": 0, "xp_lost": 0.0,
                                           "level": 0, "need": 0.0, "lt": -1})
        if typ == "xp":
            kind = r.get("kind", "other")
            if kind in EXCLUDED_KINDS:
                continue
            s["xp"] += r.get("xp", 0)
            s["base"] += r.get("base", 0)
            s["n"] += r.get("n", 0)
            p["xp"] += r.get("xp", 0)
            mix[skill][kind] = mix[skill].get(kind, 0.0) + r.get("xp", 0)
        elif typ == "skill":
            for f in ("active_s", "levelups", "procs", "rolls", "roll_hits", "chance_sum",
                      "deaths", "xp_lost"):
                s[f] += r.get(f, 0)
            # A window with no grant carries no level; it must not overwrite the last known one.
            if "level" in r and t >= s["lt"]:
                s["lt"], s["level"], s["need"] = t, r["level"], r.get("need", 0.0)
    curves = {w: metas[w][1] if w in metas else DEFAULT_CURVE for w in {k[0] for k in players}}
    return players, ps, mix, curves


def curve_text(curves):
    """The curve as one formula when every server agrees, else one per server."""
    def one(c):
        return "%g + %g x (L+1)^%g" % tuple(c)
    if len(set(curves.values())) <= 1:
        return one(next(iter(curves.values()), DEFAULT_CURVE))
    return "; ".join("%s: %s" % (w, one(c)) for w, c in sorted(curves.items()))


def skill_table(skills, players, ps, mix, curves):
    out = []
    for skill in skills:
        mine = [(k, v) for k, v in ps.items() if k[2] == skill]
        xp = sum(v["xp"] for _, v in mine)
        rates, shares, levels = [], [], []
        for (world, uuid, _), v in mine:
            if v["xp"] > 0 and v["active_s"] >= MIN_ACTIVE_S:
                rates.append(v["xp"] / (v["active_s"] / 3600.0))
            pl = players.get((world, uuid))
            if pl and pl["active_s"] >= MIN_ACTIVE_S and v["xp"] > 0:
                shares.append(v["xp"] / (pl["active_s"] / 3600.0))
            if v["level"] or v["xp"] > 0:
                levels.append(v["level"])
        rate = median(rates)
        level = int(median(levels)) if levels else None
        rolls = sum(v["rolls"] for _, v in mine)
        hits = sum(v["roll_hits"] for _, v in mine)
        chance = sum(v["chance_sum"] for _, v in mine)
        row = {
            "skill": skill, "xp": xp, "players": len([1 for _, v in mine if v["xp"] > 0]),
            "rate_players": len(rates), "rate": rate, "per_play_hour": median(shares),
            "mix": mix[skill], "level": level,
            # XP the rested pool added to grants, and Social XP paid to teachers: kinds of their own.
            "rested": mix[skill].get("rested", 0.0), "teaching": mix[skill].get("teaching", 0.0),
            "levelups": sum(v["levelups"] for _, v in mine),
            "procs": sum(v["procs"] for _, v in mine), "rolls": rolls, "hits": hits,
            "expect": chance / rolls if rolls else None,
            "observed": hits / rolls if rolls else None,
            "xp_lost": sum(v["xp_lost"] for _, v in mine),
            "deaths": sum(v["deaths"] for _, v in mine),
            "h_next": None, "h_to_100": None, "h_total": None,
        }
        if rate:
            # Each server's own curve, its own median rate and level; the row is the median of those.
            totals, nexts, to100 = [], [], []
            for world in sorted({k[0] for k, _ in mine}):
                curve = curves.get(world, DEFAULT_CURVE)
                w_rates = [v["xp"] / (v["active_s"] / 3600.0) for (wd, _, _), v in mine
                           if wd == world and v["xp"] > 0 and v["active_s"] >= MIN_ACTIVE_S]
                if not w_rates:
                    continue
                w_rate = median(w_rates)
                w_levels = [v["level"] for (wd, _, _), v in mine
                            if wd == world and (v["level"] or v["xp"] > 0)]
                w_level = int(median(w_levels)) if w_levels else None
                totals.append(hours_between(0, MAX_LEVEL, curve, w_rate))
                if w_level is not None and w_level < MAX_LEVEL:
                    nexts.append(need_at(w_level, curve) / w_rate)
                    to100.append(hours_between(w_level, MAX_LEVEL, curve, w_rate))
            row["h_total"] = median(totals)
            row["h_next"] = median(nexts)
            row["h_to_100"] = median(to100)
        if row["rolls"] >= PROC_MIN_ROLLS:
            # A chance can be summed above 1 by older files; a roll is never surer than certain.
            p = min(1.0, max(0.0, row["expect"]))
            sigma = math.sqrt(max(p * (1 - p), 1e-9) / row["rolls"])
            row["proc_off"] = abs(row["observed"] - p) > 3 * sigma
        else:
            row["proc_off"] = False
        out.append(row)
    rated = [r["rate"] for r in out if r["rate"]]
    mid = median(rated)
    for r in out:
        r["dead"] = r["xp"] <= 0
        r["runaway"] = bool(mid and r["rate"] and r["rate"] > RUNAWAY_FACTOR * mid)
        r["slow"] = bool(mid and r["rate"] and r["rate"] < SLOW_FACTOR * mid)
        r["vs_median"] = (r["rate"] / mid) if mid and r["rate"] else None
    return out, mid


def death_summary(players, ps):
    per_world = {}
    for (world, _), p in players.items():
        w = per_world.setdefault(world, {"players": 0, "active_h": 0.0, "xp": 0.0, "deaths": 0,
                                          "streak_lost": 0, "xp_lost": 0.0})
        w["players"] += 1
        w["active_h"] += p["active_s"] / 3600.0
        w["xp"] += p["xp"]
        w["deaths"] += p["deaths"]
        w["streak_lost"] += p["streak_lost"]
    for (world, _, _), v in ps.items():
        per_world[world]["xp_lost"] += v["xp_lost"]
    return per_world


def hearth_summary(rows, players, mix=None):
    """The Hearth section from the "hearth" lines: charge per pool (every Hearth of a server summed),
    per active hour of play on the servers that have Hearth lines, what the hourly cap turned away,
    the hours to each tier at that rate, charge per pool and source, who fed it and from what, Warmth,
    temp HP spent away, Tales and the Chronicle, sieges, boon time, Homebuilder and Homeward, and
    every other Hearth event."""
    pools = {p: {"charge": 0.0, "capped": 0.0} for p in HEARTH_POOLS}
    feeders, sources, events, worlds = {}, {}, {}, set()
    by_source = {}   # (pool, source) -> charge
    for r in rows:
        if r.get("type") != "hearth":
            continue
        kind, label = r.get("kind") or "", r.get("label") or ""
        source = r.get("source") or ""
        value = r.get("value", 0)
        value = float(value) if isinstance(value, (int, float)) else 0.0
        worlds.add(r["_world"])
        if kind != "hearth_charge":
            e = events.setdefault((kind, label, source), [0, 0.0])
            e[0] += int(r.get("n", 0) or 0)
            e[1] += value
            continue
        pool = pools.setdefault(label, {"charge": 0.0, "capped": 0.0})
        if source == HEARTH_CAPPED:
            pool["capped"] += value
            continue
        pool["charge"] += value
        sources[source] = sources.get(source, 0.0) + value
        by_source[(label, source)] = by_source.get((label, source), 0.0) + value
        key = (r["_world"], r.get("uuid") or r.get("player") or "")
        f = feeders.setdefault(key, {"name": r.get("player") or "(world)", "world": r["_world"],
                                     "pools": {}, "sources": {}, "total": 0.0})
        f["pools"][label] = f["pools"].get(label, 0.0) + value
        f["sources"][source] = f["sources"].get(source, 0.0) + value
        f["total"] += value
    active_s = sum(p["active_s"] for (w, _), p in players.items() if w in worlds)
    active_h = active_s / 3600.0
    for p in pools.values():
        p["per_hour"] = p["charge"] / active_h if active_h > 0 else None
        rate = min(p["per_hour"], HEARTH_HOURLY_CAP) if p["per_hour"] else None
        p["tier_h"] = [t / rate if rate else None for t in HEARTH_THRESHOLDS]
    # Player-hours, like active_h: streak_paused is one line per player and carries no Hearth, so two
    # players at home together add up to two hours.
    home_s = sum(v[1] for (k, _, _), v in events.items() if k == "streak_paused")
    home_h = home_s / 3600.0
    for p in pools.values():
        p["per_home_hour"] = p["charge"] / home_h if home_h > 0 else None
    by_source_rows = []
    for (pool, source), value in by_source.items():
        total = pools.get(pool, {}).get("charge", 0.0)
        by_source_rows.append({"pool": pool, "source": source, "charge": value,
                               "per_hour": value / active_h if active_h > 0 else None,
                               "share": value / total if total > 0 else None})
    by_source_rows.sort(key=lambda r: (HEARTH_POOLS.index(r["pool"]) if r["pool"] in HEARTH_POOLS else 99,
                                       -r["charge"], r["source"]))
    return {"pools": pools, "feeders": feeders, "sources": sources, "events": events,
            "by_source": by_source_rows, "active_h": active_h, "home_h": home_h,
            "home_share": home_s / active_s if active_s > 0 and home_s > 0 else None,
            "sieges": siege_summary(events, active_h),
            "warmth": warmth_summary(events), "absorb": absorb_summary(events, active_h),
            "tales": tales_summary(events, sources), "boons": boon_summary(events),
            "homebuilder": homebuilder_summary(events, mix, players, worlds, active_h),
            "any": bool(feeders or events or any(p["capped"] > 0 for p in pools.values()))}


def _events(events, kind, label=None):
    """(n, sum) over the events of a kind (and label), every source."""
    n, total = 0, 0.0
    for (k, l, _), (count, value) in events.items():
        if k == kind and (label is None or l == label):
            n += count
            total += value
    return n, total


def warmth_summary(events):
    """hearth_warm: "rise" = seconds from the start of a rise to full (one event per fill), "bed" = a real
    night's sleep in range filled it at once."""
    rises, rise_s = _events(events, "hearth_warm", "rise")
    beds, _ = _events(events, "hearth_warm", "bed")
    if not (rises or beds):
        return None
    return {"rises": rises, "mean_rise_s": rise_s / rises if rises else None, "beds": beds,
            "bed_share": beds / (rises + beds)}


def absorb_summary(events, active_h):
    """hearth_absorb: temp HP spent away from home, by the hit's source ("boss" or "other")."""
    _, boss = _events(events, "hearth_absorb", "boss")
    n, total = _events(events, "hearth_absorb")
    if not n:
        return None
    return {"hits": n, "total": total, "boss": boss, "boss_share": boss / total if total > 0 else None,
            "per_hour": total / active_h if active_h > 0 else None}


def tales_summary(events, sources):
    """hearth_tales (banked by skill, paid, lost), tale_rating (label = stars, value = the charge paid)
    and the Chronicle's charge (ledger source "chronicle": the flat bonus and the rated bursts)."""
    banked_by = {}
    for (k, l, src), (_, value) in events.items():
        if k == "hearth_tales" and l == "banked":
            banked_by[src or "?"] = banked_by.get(src or "?", 0.0) + value
    banked = sum(banked_by.values())
    _, paid = _events(events, "hearth_tales", "paid")
    lost_n, lost = _events(events, "hearth_tales", "lost")
    stars, rated_charge = {}, 0.0
    for (k, l, _), (count, value) in events.items():
        if k == "tale_rating":
            stars[l] = stars.get(l, 0) + count
            rated_charge += value
    rated = sum(stars.values())
    chronicle = sources.get("chronicle", 0.0)
    if not (banked or paid or lost or rated or chronicle):
        return None
    # The mean is over the labels that are a star count: a label that is not a digit has no stars to add,
    # so it must not count in the divisor either.
    starred = sum(n for k, n in stars.items() if str(k).isdigit())
    mean = None
    if starred:
        mean = sum(int(k) * n for k, n in stars.items() if str(k).isdigit()) / starred
    return {"banked": banked, "banked_by": banked_by, "paid": paid, "lost": lost, "deaths": lost_n,
            "lost_share": lost / banked if banked > 0 else None, "rated": rated, "stars": stars,
            "mean_stars": mean, "rated_charge": rated_charge, "chronicle_charge": chronicle,
            "flat_charge": max(0.0, chronicle - rated_charge), "tales_charge": sources.get("tales", 0.0)}


def boon_summary(events):
    """hearth_boon: Warmth-weighted seconds each boon worked (1 a second for the unscaled ones), and
    Provisioned's Hearty away from home (source "provisioned")."""
    out = {}
    for (k, l, src), (_, value) in events.items():
        if k != "hearth_boon":
            continue
        b = out.setdefault(l, {"home_h": 0.0, "away_h": 0.0})
        if src == "provisioned":
            b["away_h"] += value / 3600.0
        else:
            b["home_h"] += value / 3600.0
    return out or None


def homebuilder_summary(events, mix, players, worlds, active_h):
    """Homebuilder's XP by kind (the at-home share is kind "home", milestones arrive as "action") and its
    share of all XP, plus Homeward: uses (by channel or after a come-home bell), distance saved, cancels."""
    hb = dict((mix or {}).get("homebuilder", {}))
    total = sum(hb.values())
    all_xp = sum(sum(m.values()) for m in (mix or {}).values())
    uses = {}
    saved_n, saved = 0, 0.0
    cancels = {}
    for (k, l, src), (count, value) in events.items():
        if k != "homeward":
            continue
        if l in ("use", "use_other_dimension"):
            uses[(l, src)] = uses.get((l, src), 0) + count
            if l == "use":
                saved_n += count
                saved += value
        elif l == "cancel":
            cancels[src] = cancels.get(src, 0) + count
    if not (total or uses or cancels):
        return None
    return {"xp": total, "share_xp": hb.get("home", 0.0), "milestone_xp": hb.get("action", 0.0),
            "first_xp": hb.get("first-time", 0.0), "of_all": total / all_xp if all_xp > 0 else None,
            "per_hour": total / active_h if active_h > 0 else None,
            "uses": sum(uses.values()), "bell_uses": sum(n for (l, src), n in uses.items() if src == "bell"),
            "other_dim": sum(n for (l, src), n in uses.items() if l == "use_other_dimension"),
            "mean_saved": saved / saved_n if saved_n else None, "cancels": cancels}


def siege_summary(events, active_h):
    """Sieges from the "hearth_siege" events (Sieges lane): announced sieges per 10 h of active play
    (rolled and called by a horn), hours the condition held, how they ended, the mean wave size."""
    def count(label):
        return sum(v[0] for (k, l, _), v in events.items() if k == "hearth_siege" and l == label)
    rolled, called = count("rolled"), count("called")
    if not (rolled or called or count("condition") or count("outcome")):
        return None
    waves = [v for (k, l, _), v in events.items() if k == "hearth_siege" and l == "wave"]
    wave_n, wave_mobs = sum(v[0] for v in waves), sum(v[1] for v in waves)
    outcomes = {s: v[0] for (k, l, s), v in events.items() if k == "hearth_siege" and l == "outcome"}
    return {"rolled": rolled, "called": called,
            "per_10h": (rolled + called) * 10.0 / active_h if active_h > 0 else None,
            "condition_h": count("condition") / 3600.0, "rolls": count("roll"),
            "outcomes": outcomes, "mean_wave": wave_mobs / wave_n if wave_n else None,
            "ended_per_10h": sum(outcomes.values()) * 10.0 / active_h if active_h > 0 else None,
            "kills": count("kill"), "unearned_falls": count("unearned"), "postponed": count("postpone"),
            "horns": count("horn")}


def siege_text(s):
    outcomes = ", ".join("%s %d" % (k, n) for k, n in sorted(s["outcomes"].items())) or "none yet"
    text = ("Sieges: %s per 10 h of active play (%d rolled, %d called by a horn); the condition held %.1f h "
            "(%d rolls); outcomes: %s; mean wave %s mobs." % (
                f1(s["per_10h"]), s["rolled"], s["called"], s["condition_h"], s["rolls"], outcomes,
                f1(s["mean_wave"])))
    if s.get("kills") or s.get("unearned_falls") or s.get("postponed"):
        text += (" Siege kills %d, siege mobs lost without a player's hand %d (unearned), nights postponed %d."
                 % (s.get("kills", 0), s.get("unearned_falls", 0), s.get("postponed", 0)))
    return text


def warmth_text(m):
    return ("Warmth: filled to full by standing at a Hearth %dx (mean %s s to full), by a night's sleep %dx "
            "(%s of fills)." % (m["rises"], f1(m["mean_rise_s"]), m["beds"], fp(m["bed_share"])))


def absorb_text(a):
    return ("Temp HP spent away from home: %.0f points over %d hits (%s per active hour); boss hits took "
            "%.0f of it (%s)." % (a["total"], a["hits"], f1(a["per_hour"]), a["boss"], fp(a["boss_share"])))


def tales_text(t):
    stars = ", ".join("%s stars %d" % (k, n) for k, n in sorted(t["stars"].items())) or "none"
    return ("Tales: %.0f banked away (%s), %.0f paid in at a Hearth, %.0f lost in %d death(s) (%s of banked). "
            "Chronicle: %.0f charge from the write bonus; %d pages rated (%s; mean %s), %.0f charge from ratings."
            % (t["banked"], top_sources(t["banked_by"]), t["paid"], t["lost"], t["deaths"], fp(t["lost_share"]),
               t["flat_charge"], t["rated"], stars, f1(t["mean_stars"]), t["rated_charge"]))


def boons_text(b):
    parts = []
    for boon in sorted(b):
        v = b[boon]
        part = "%s %.1f h" % (boon, v["home_h"])
        if v["away_h"] > 0:
            part += " (+%.1f h Provisioned away)" % v["away_h"]
        parts.append(part)
    return "Boon time, Warmth-weighted: %s." % ", ".join(parts)


def homebuilder_text(m):
    cancels = ", ".join("%s %d" % (k, n) for k, n in sorted(m["cancels"].items())) or "none"
    return ("Homebuilder: %.0f XP (%s per active hour, %s of all XP): the at-home share %.0f, milestones %.0f, "
            "first-time %.0f. Homeward: %d uses (%d after a come-home bell, %d to another dimension), mean %s "
            "blocks saved; cancelled: %s." % (
                m["xp"], f1(m["per_hour"]), fp(m["of_all"]), m["share_xp"], m["milestone_xp"], m["first_xp"],
                m["uses"], m["bell_uses"], m["other_dim"], f1(m["mean_saved"]), cancels))


def hearth_lines(hs):
    """The one-line summaries, in the order the report prints them (markdown and html share them)."""
    lines = []
    if hs["home_share"] is not None:
        lines.append("Share of active time at home (streak paused): %s (%.1f h)." % (fp(hs["home_share"]),
                                                                                     hs["home_h"]))
    for key, fn in (("warmth", warmth_text), ("absorb", absorb_text), ("tales", tales_text),
                    ("sieges", siege_text), ("boons", boons_text), ("homebuilder", homebuilder_text)):
        if hs.get(key):
            lines.append(fn(hs[key]))
    return lines


def hearth_name(d, key, f):
    if key in d["names"]:
        return d["names"][key]
    return "Player ?" if d["anon"] else f["name"]


def top_sources(sources, n=3):
    return ", ".join("%s %.0f" % (k, v) for k, v in sorted(sources.items(), key=lambda kv: -kv[1])[:n]) or "-"


# ---------------------------------------------------------------- formatting

def f1(v):
    return "-" if v is None else ("%.1f" % v)


def fh(v):
    if v is None:
        return "-"
    if v >= 1000:
        return "%.0f h" % v
    return "%.1f h" % v


def fx(v):
    return "-" if v is None else ("%.1fx" % v)


def fp(v):
    return "-" if v is None else ("%.0f%%" % (v * 100))


def rested_text(r):
    """Rested XP for a skill row: the XP and its share of the skill's XP, '-' when none."""
    if r["rested"] <= 0:
        return "-"
    share = r["rested"] / r["xp"] if r["xp"] > 0 else None
    return "%.0f (%s)" % (r["rested"], fp(share))


def teaching_text(r):
    return "-" if r["teaching"] <= 0 else "%.0f" % r["teaching"]


def mix_text(mix):
    total = sum(mix.values())
    if total <= 0:
        return "-"
    top = sorted(mix.items(), key=lambda kv: -kv[1])[:4]
    return ", ".join("%s %d%%" % (k, round(v * 100 / total)) for k, v in top)


def anon_names(players):
    order = sorted(players)
    names = {}
    for i, key in enumerate(order):
        label = ""
        n = i
        while True:
            label = chr(ord("A") + n % 26) + label
            n = n // 26 - 1
            if n < 0:
                break
        names[key] = "Player " + label
    return names


def build(rows, files, bad, since, until, anon, skills):
    players, ps, mix, curves = analyse(rows, skills)
    table, mid = skill_table(skills, players, ps, mix, curves)
    names = anon_names(players) if anon else {k: v["name"] for k, v in players.items()}
    deaths = death_summary(players, ps)
    return {
        "since": since, "until": until, "files": files, "bad": bad, "anon": anon,
        "players": players, "names": names, "table": table, "median_rate": mid,
        "curves": curves, "curve_text": curve_text(curves), "deaths": deaths, "worlds": sorted({k[0] for k in players}),
        "generated": dt.datetime.now().strftime("%Y-%m-%d %H:%M"),
        "span": data_span(rows), "active_h": sum(p["active_s"] for p in players.values()) / 3600,
        "hearth": hearth_summary(rows, players, mix),
    }


def data_span(rows):
    """First and last telemetry write in the window, local time: the window asked for can be far wider."""
    ts = [r["t"] for r in rows if isinstance(r.get("t"), (int, float))]
    if not ts:
        return None
    f = lambda ms: dt.datetime.fromtimestamp(ms / 1000).strftime("%Y-%m-%d %H:%M")
    return f(min(ts)), f(max(ts))


def dead_label(d):
    if d["active_h"] < DEAD_MIN_H:
        return "No XP yet (only %.1f active hours of data, too few to call a skill dead)" % d["active_h"]
    return "Dead (no XP in the window)"


def span_text(d):
    return "data %s to %s" % d["span"] if d["span"] else "no data"


# ---------------------------------------------------------------- markdown

def markdown(d):
    L = []
    w = L.append
    t = d["table"]
    w("# Proficiency balance report")
    w("")
    w("Window %s to %s (%s), %d day file(s), %d player(s) on %s. Generated %s.%s" % (
        d["since"], d["until"], span_text(d), d["files"], len(d["players"]),
        ", ".join(d["worlds"]) or "no server", d["generated"],
        " Names anonymised." if d["anon"] else ""))
    if d["bad"]:
        w("%d unreadable line(s) were skipped." % d["bad"])
    w("")
    if not d["players"]:
        w("No telemetry in the window. Telemetry needs the mod from this version on and a "
          "session in the window; check `telemetry.enabled` in `proficiency-server.toml`.")
        return "\n".join(L) + "\n"
    w("XP counts what players were paid (after every multiplier, survival bonus included). "
      "Operator grants (`/skills addxp`) are left out. A skill counts as in use for the minute "
      "after it last paid XP, and a player counts for a skill from %d minutes of such time." % (
          MIN_ACTIVE_S // 60))
    w("")
    w("## Summary")
    w("")
    dead = [r["skill"] for r in t if r["dead"]]
    run = [r for r in t if r["runaway"]]
    slow = [r for r in t if r["slow"]]
    off = [r for r in t if r["proc_off"]]
    w("- Median skill pays %s XP per active hour." % f1(d["median_rate"]) if d["median_rate"]
      else "- No skill has %d minutes of use from any player yet, so there are no rates." % (
          MIN_ACTIVE_S // 60))
    w("- Runaway (over %gx the median): %s." % (RUNAWAY_FACTOR, ", ".join(
        "%s (%s)" % (r["skill"], fx(r["vs_median"])) for r in run) or "none"))
    w("- Slow (under a third of the median): %s." % (", ".join(
        "%s (%s)" % (r["skill"], fx(r["vs_median"])) for r in slow) or "none"))
    w("- %s: %s." % (dead_label(d), ", ".join(dead) or "none"))
    w("- Proc rate off its configured chance: %s." % (", ".join(r["skill"] for r in off) or "none"))
    all_xp = sum(r["xp"] for r in t)
    w("- Rested XP: %.0f of %.0f XP (%s); teaching paid %.0f Social XP." % (
        sum(r["rested"] for r in t), all_xp,
        fp(sum(r["rested"] for r in t) / all_xp if all_xp > 0 else None),
        sum(r["teaching"] for r in t)))
    w("")
    w("## XP per active hour")
    w("")
    w("| Skill | Median XP/h | Players | vs median | Per play hour | Median level | XP total | Rested XP | Teaching XP | Level-ups |")
    w("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    for r in sorted(t, key=lambda r: -(r["rate"] or -1)):
        if r["dead"]:
            continue
        flag = " (runaway)" if r["runaway"] else (" (slow)" if r["slow"] else "")
        w("| %s%s | %s | %d of %d | %s | %s | %s | %.0f | %s | %s | %d |" % (
            r["skill"], flag, f1(r["rate"]), r["rate_players"], r["players"], fx(r["vs_median"]),
            f1(r["per_play_hour"]), r["level"] if r["level"] is not None else "-", r["xp"],
            rested_text(r), teaching_text(r), r["levelups"]))
    w("")
    w("Rested XP is the extra XP grants gained from the player's rested pool, with its share of the "
      "skill's XP; it is part of the XP total. Teaching XP is the Social XP teachers were paid for "
      "what their students spent.")
    w("")
    w("Median XP/h is XP over the hours the skill was in use, per player. Players with under "
      "%d minutes in a skill are left out of its median (the Players column says how many "
      "counted). Per play hour is XP over all active hours, so it shows what a skill adds to a "
      "typical hour of play." % (MIN_ACTIVE_S // 60))
    w("")
    w("## Source mix")
    w("")
    w("| Skill | Share of XP by source kind |")
    w("|---|---|")
    for r in t:
        if not r["dead"]:
            w("| %s | %s |" % (r["skill"], mix_text(r["mix"])))
    w("")
    w("## Dead skills")
    w("")
    if d["active_h"] < DEAD_MIN_H:
        w("%s." % dead_label(d))
        w("")
    w(", ".join(dead) if dead else "None: every skill paid XP in the window.")
    w("")
    w("## Time to level at the current rate")
    w("")
    w("Curve %s, from the server's own settings. Hours of use of that skill.\n" % d["curve_text"])
    w("| Skill | Median level | Next level | Level to 100 | 0 to 100 |")
    w("|---|---:|---:|---:|---:|")
    for r in sorted(t, key=lambda r: -(r["h_total"] or -1)):
        if r["h_total"] is not None:
            w("| %s | %s | %s | %s | %s |" % (r["skill"], r["level"], fh(r["h_next"]),
                                              fh(r["h_to_100"]), fh(r["h_total"])))
    w("")
    w("## Proc rate against the configured chance")
    w("")
    w("| Skill | Natural rolls | Landed | Observed | Configured (mean) | All procs |")
    w("|---|---:|---:|---:|---:|---:|")
    for r in t:
        if r["rolls"] or r["procs"]:
            w("| %s%s | %d | %d | %s | %s | %d |" % (
                r["skill"], " (off)" if r["proc_off"] else "", r["rolls"], r["hits"],
                fp(r["observed"]), fp(r["expect"]), r["procs"]))
    w("")
    w("Observed is flagged when it sits more than 3 standard errors from the mean chance rolled, "
      "from %d rolls. All procs also counts ability frenzy and forced procs." % PROC_MIN_ROLLS)
    w("")
    w("## What deaths cost")
    w("")
    w("| Server | Players | Active h | Deaths | Deaths per 10 active h | XP lost | Share of XP earned | Streak stacks lost |")
    w("|---|---:|---:|---:|---:|---:|---:|---:|")
    for world, v in sorted(d["deaths"].items()):
        per10 = v["deaths"] * 10 / v["active_h"] if v["active_h"] else None
        share = v["xp_lost"] / v["xp"] if v["xp"] else None
        w("| %s | %d | %.1f | %d | %s | %.0f | %s | %d |" % (
            world, v["players"], v["active_h"], v["deaths"], f1(per10), v["xp_lost"], fp(share),
            v["streak_lost"]))
    lost = sorted(((r["xp_lost"], r["skill"]) for r in t if r["xp_lost"] > 0), reverse=True)[:5]
    if lost:
        w("")
        w("Most XP lost by skill: " + ", ".join("%s %.0f" % (s, x) for x, s in lost) + ".")
    w("")
    w("## Players")
    w("")
    w("| Player | Server | Online h | Active h | XP | Deaths |")
    w("|---|---|---:|---:|---:|---:|")
    for key, p in sorted(d["players"].items(), key=lambda kv: -kv[1]["active_s"]):
        w("| %s | %s | %.1f | %.1f | %.0f | %d |" % (
            d["names"][key], p["world"], p["online_s"] / 3600, p["active_s"] / 3600, p["xp"],
            p["deaths"]))
    w("")
    hearth_markdown(d, w)
    return "\n".join(L) + "\n"


def hearth_markdown(d, w):
    hs = d["hearth"]
    w("## Hearth")
    w("")
    if not hs["any"]:
        w("No Hearth telemetry in the window.")
        w("")
        return
    w("Charge is what the Hearths gathered, every Hearth of a server summed, per hour of active play "
      "on the servers with Hearth lines (%.1f h). Hours are player-hours, here and at home: two players "
      "at home together count twice, since the telemetry has no Hearth to count a shared hour once. "
      "Time to a tier is from no charge at that rate, held to the default hourly cap of %.0f per pool, "
      "without decay." % (hs["active_h"], HEARTH_HOURLY_CAP))
    w("")
    w("| Pool | Charge | Per active hour | Per player-hour at home | Lost to the hourly cap | %s |"
      % " | ".join(HEARTH_ROMAN))
    w("|---|---:|---:|---:|---:|%s" % ("---:|" * len(HEARTH_ROMAN)))
    for name in sorted(hs["pools"], key=lambda n: HEARTH_POOLS.index(n) if n in HEARTH_POOLS else 99):
        p = hs["pools"][name]
        w("| %s | %.0f | %s | %s | %.0f | %s |" % (name, p["charge"], f1(p["per_hour"]), f1(p.get("per_home_hour")),
                                                   p["capped"], " | ".join(fh(t) for t in p["tier_h"])))
    for line in hearth_lines(hs):
        w("")
        w(line)
    if hs["by_source"]:
        w("")
        w("### Charge by pool and source")
        w("")
        w("| Pool | Source | Charge | Per active hour | Share of the pool |")
        w("|---|---|---:|---:|---:|")
        for r in hs["by_source"][:60]:
            w("| %s | %s | %.0f | %s | %s |" % (r["pool"], r["source"] or "-", r["charge"], f1(r["per_hour"]),
                                               fp(r["share"])))
    w("")
    w("### Who feeds the Hearth")
    w("")
    w("| Player | Server | Charge | %s | Top sources |" % " | ".join(HEARTH_POOLS))
    w("|---|---|---:|%s---|" % ("---:|" * len(HEARTH_POOLS)))
    for key, f in sorted(hs["feeders"].items(), key=lambda kv: -kv[1]["total"]):
        w("| %s | %s | %.0f | %s | %s |" % (hearth_name(d, key, f), f["world"], f["total"],
                                            " | ".join("%.0f" % f["pools"].get(p, 0.0) for p in HEARTH_POOLS),
                                            top_sources(f["sources"])))
    w("")
    w("Charge by source: %s." % top_sources(hs["sources"], 12))
    if hs["events"]:
        w("")
        w("### Other Hearth events")
        w("")
        w("| Kind | Label | Source | Events | Sum |")
        w("|---|---|---|---:|---:|")
        for (kind, label, source), (n, total) in sorted(hs["events"].items())[:60]:
            w("| %s | %s | %s | %d | %.1f |" % (kind, label or "-", source or "-", n, total))
    w("")


# ---------------------------------------------------------------- html

CSS = """
:root{--bg:#f6f4ef;--card:#fffdf8;--ink:#1d1b17;--mute:#6b665c;--line:#e2ddd0;--accent:#2f6f5e;
--warn:#b4442f;--ok:#2f6f5e;--bar:#c9d9d2}
@media (prefers-color-scheme:dark){:root:not([data-theme=light]){--bg:#15171a;--card:#1d2024;
--ink:#ecebe6;--mute:#9b9a92;--line:#2c3036;--accent:#6fc2a9;--warn:#ef8a73;--ok:#6fc2a9;--bar:#2e4a41}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:15px/1.5 system-ui,-apple-system,Segoe UI,sans-serif}
main{max-width:1100px;margin:0 auto;padding:24px 16px 64px}
h1{font-size:1.7rem;margin:0 0 4px}h2{font-size:1.15rem;margin:32px 0 8px}
.sub{color:var(--mute);margin:0 0 16px}
.cards{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:12px;margin:16px 0}
.card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}
.cards .card b{display:block;font-size:1.5rem}.card span{color:var(--mute);font-size:.85rem}
.tablewrap{overflow-x:auto;background:var(--card);border:1px solid var(--line);border-radius:10px}
table{border-collapse:collapse;width:100%;font-size:.9rem}
th,td{padding:6px 10px;text-align:right;border-bottom:1px solid var(--line);white-space:nowrap}
th:first-child,td:first-child{text-align:left}
th{color:var(--mute);font-weight:600;font-size:.8rem}
tr:last-child td{border-bottom:0}
.bar{display:inline-block;height:8px;border-radius:4px;background:var(--accent);vertical-align:middle;margin-left:8px}
.tag{font-size:.72rem;border-radius:4px;padding:1px 6px;margin-left:6px;color:#fff;background:var(--warn)}
.tag.slow{background:#8a6f2f}.mixbar{display:flex;height:12px;min-width:220px;border-radius:3px;overflow:hidden;background:var(--line)}
.mixbar i{display:block;height:100%}
.legend{display:flex;flex-wrap:wrap;gap:4px 14px;font-size:.8rem;color:var(--mute);margin:8px 0}
.legend i{display:inline-block;width:10px;height:10px;border-radius:2px;margin-right:4px}
p.note{color:var(--mute);font-size:.85rem;margin:6px 2px}
.list b{color:var(--warn);font-size:inherit;display:inline}
"""


def h(v):
    return html.escape(str(v))


def page(d):
    t = d["table"]
    o = []
    w = o.append
    w("<!doctype html><html lang=en><head><meta charset=utf-8>")
    w("<meta name=viewport content='width=device-width,initial-scale=1'>")
    w("<title>Proficiency balance</title><style>%s</style></head><body><main>" % CSS)
    w("<h1>Proficiency balance</h1>")
    w("<p class=sub>%s to %s &middot; %s &middot; %d day file(s) &middot; %s%s &middot; generated %s</p>" % (
        h(d["since"]), h(d["until"]), h(span_text(d)), d["files"], h(", ".join(d["worlds"]) or "no server"),
        " &middot; names anonymised" if d["anon"] else "", h(d["generated"])))
    if not d["players"]:
        w("<p>No telemetry in the window. It needs this version of the mod and a session in the "
          "window; check <code>telemetry.enabled</code> in <code>proficiency-server.toml</code>.</p>")
        w("</main></body></html>")
        return "".join(o)
    dead = [r for r in t if r["dead"]]
    run = [r for r in t if r["runaway"]]
    slow = [r for r in t if r["slow"]]
    off = [r for r in t if r["proc_off"]]
    active_h = sum(p["active_s"] for p in d["players"].values()) / 3600
    deaths = sum(p["deaths"] for p in d["players"].values())
    w("<div class=cards>")
    for big, small in ((len(d["players"]), "players"), ("%.1f" % active_h, "active hours"),
                       (f1(d["median_rate"]), "median XP per active hour"), (deaths, "deaths"),
                       (len(run), "runaway skills"),
                       (len(dead), "dead skills" if d["active_h"] >= DEAD_MIN_H else "skills with no XP yet")):
        w("<div class=card><b>%s</b><span>%s</span></div>" % (h(big), h(small)))
    w("</div>")
    w("<div class='card list'><b style='font-size:1rem'>Findings</b>")
    w("<p>Runaway (over %gx the median): %s</p>" % (RUNAWAY_FACTOR, ", ".join(
        "<b>%s</b> %s" % (h(r["skill"]), h(fx(r["vs_median"]))) for r in run) or "none"))
    w("<p>Slow (under a third): %s</p>" % (", ".join(
        "%s %s" % (h(r["skill"]), h(fx(r["vs_median"]))) for r in slow) or "none"))
    w("<p>%s: %s</p>" % (h(dead_label(d)), h(", ".join(r["skill"] for r in dead)) or "none"))
    w("<p>Proc rate off its chance: %s</p></div>" % (
        h(", ".join(r["skill"] for r in off)) or "none"))
    top = max([r["rate"] or 0 for r in t] + [1])
    w("<h2>XP per active hour</h2><div class=tablewrap><table><tr><th>Skill<th>Median XP/h"
      "<th>Players<th>vs median<th>Per play hour<th>Level<th>XP total<th>Rested XP<th>Teaching XP"
      "<th>Level-ups</tr>")
    for r in sorted(t, key=lambda r: -(r["rate"] or -1)):
        if r["dead"]:
            continue
        tag = "<span class=tag>runaway</span>" if r["runaway"] else (
            "<span class='tag slow'>slow</span>" if r["slow"] else "")
        width = int(110 * (r["rate"] or 0) / top)
        w("<tr><td>%s%s<td>%s<span class=bar style='width:%dpx'></span><td>%d of %d<td>%s<td>%s"
          "<td>%s<td>%.0f<td>%s<td>%s<td>%d</tr>" % (
              h(r["skill"]), tag, h(f1(r["rate"])), width, r["rate_players"], r["players"],
              h(fx(r["vs_median"])), h(f1(r["per_play_hour"])),
              h(r["level"] if r["level"] is not None else "-"), r["xp"],
              h(rested_text(r)), h(teaching_text(r)), r["levelups"]))
    w("</table></div><p class=note>Rested XP is the extra XP grants gained from the rested pool "
      "(part of the XP total, with its share); Teaching XP is the Social XP paid to teachers.</p>")
    w("<p class=note>Median over players of XP per hour the skill was in use "
      "(players under %d minutes in it are left out). Per play hour spreads the XP over all "
      "active hours instead.</p>" % (MIN_ACTIVE_S // 60))
    w("<h2>Source mix</h2><div class=legend>%s</div><div class=tablewrap><table>"
      "<tr><th>Skill<th>Share of XP by source kind<th>Top</tr>" % "".join(
          "<span><i style='background:%s'></i>%s</span>" % (c, h(k)) for k, c in KIND_COLORS.items()))
    for r in t:
        total = sum(r["mix"].values())
        if total <= 0:
            continue
        segs = "".join("<i style='width:%.2f%%;background:%s' title='%s %d%%'></i>" % (
            v * 100 / total, KIND_COLORS.get(k, "#999"), h(k), round(v * 100 / total))
            for k, v in sorted(r["mix"].items(), key=lambda kv: -kv[1]) if v > 0)
        w("<tr><td>%s<td><div class=mixbar>%s</div><td style='text-align:left'>%s</tr>" % (
            h(r["skill"]), segs, h(mix_text(r["mix"]))))
    w("</table></div>")
    w("<h2>Dead skills</h2>%s<p>%s</p>" % (
        "<p class=note>%s.</p>" % h(dead_label(d)) if d["active_h"] < DEAD_MIN_H else "",
        h(", ".join(r["skill"] for r in dead)) if dead else "None: every skill paid XP in the window."))
    w("<h2>Time to level at the current rate</h2><p class=note>Curve %s, from the "
      "server's own settings. Hours of use of that skill.</p><div class=tablewrap><table>"
      "<tr><th>Skill<th>Median level<th>Next level<th>Level to 100<th>0 to 100</tr>" % h(d["curve_text"]))
    for r in sorted(t, key=lambda r: -(r["h_total"] or -1)):
        if r["h_total"] is not None:
            w("<tr><td>%s<td>%s<td>%s<td>%s<td>%s</tr>" % (
                h(r["skill"]), h(r["level"]), h(fh(r["h_next"])), h(fh(r["h_to_100"])),
                h(fh(r["h_total"]))))
    w("</table></div>")
    w("<h2>Proc rate against the configured chance</h2><div class=tablewrap><table>"
      "<tr><th>Skill<th>Natural rolls<th>Landed<th>Observed<th>Configured (mean)<th>All procs</tr>")
    for r in t:
        if r["rolls"] or r["procs"]:
            tag = "<span class=tag>off</span>" if r["proc_off"] else ""
            w("<tr><td>%s%s<td>%d<td>%d<td>%s<td>%s<td>%d</tr>" % (
                h(r["skill"]), tag, r["rolls"], r["hits"], h(fp(r["observed"])), h(fp(r["expect"])),
                r["procs"]))
    w("</table></div><p class=note>Flagged when observed is more than 3 standard errors from the "
      "mean chance rolled, from %d rolls. All procs also counts ability frenzy and forced procs.</p>"
      % PROC_MIN_ROLLS)
    w("<h2>What deaths cost</h2><div class=tablewrap><table><tr><th>Server<th>Players<th>Active h"
      "<th>Deaths<th>Per 10 active h<th>XP lost<th>Share of XP earned<th>Streak stacks lost</tr>")
    for world, v in sorted(d["deaths"].items()):
        per10 = v["deaths"] * 10 / v["active_h"] if v["active_h"] else None
        share = v["xp_lost"] / v["xp"] if v["xp"] else None
        w("<tr><td>%s<td>%d<td>%.1f<td>%d<td>%s<td>%.0f<td>%s<td>%d</tr>" % (
            h(world), v["players"], v["active_h"], v["deaths"], h(f1(per10)), v["xp_lost"],
            h(fp(share)), v["streak_lost"]))
    w("</table></div>")
    w("<h2>Players</h2><div class=tablewrap><table><tr><th>Player<th>Server<th>Online h"
      "<th>Active h<th>XP<th>Deaths</tr>")
    for key, p in sorted(d["players"].items(), key=lambda kv: -kv[1]["active_s"]):
        w("<tr><td>%s<td>%s<td>%.1f<td>%.1f<td>%.0f<td>%d</tr>" % (
            h(d["names"][key]), h(p["world"]), p["online_s"] / 3600, p["active_s"] / 3600,
            p["xp"], p["deaths"]))
    w("</table></div>")
    hearth_page(d, w)
    w("</main></body></html>")
    return "".join(o)


def hearth_page(d, w):
    hs = d["hearth"]
    w("<h2>Hearth</h2>")
    if not hs["any"]:
        w("<p class=note>No Hearth telemetry in the window.</p>")
        return
    w("<p class=note>Charge the Hearths gathered, every Hearth of a server summed, per hour of active "
      "play on the servers with Hearth lines (%.1f h). Hours are player-hours, here and at home: two "
      "players at home together count twice, since the telemetry has no Hearth to count a shared hour "
      "once. Time to a tier is from no charge at that rate, held to the default hourly cap of %.0f per "
      "pool, without decay.</p>" % (hs["active_h"], HEARTH_HOURLY_CAP))
    w("<div class=tablewrap><table><tr><th>Pool<th>Charge<th>Per active hour<th>Per player-hour at home"
      "<th>Lost to the cap%s</tr>" % "".join("<th>%s" % r for r in HEARTH_ROMAN))
    for name in sorted(hs["pools"], key=lambda n: HEARTH_POOLS.index(n) if n in HEARTH_POOLS else 99):
        p = hs["pools"][name]
        w("<tr><td><span class=bar style='width:10px;margin:0 6px 0 0;background:%s'></span>%s<td>%.0f<td>%s<td>%s"
          "<td>%.0f%s</tr>" % (HEARTH_COLORS.get(name, "#999"), h(name), p["charge"], h(f1(p["per_hour"])),
                               h(f1(p.get("per_home_hour"))), p["capped"],
                               "".join("<td>%s" % h(fh(t)) for t in p["tier_h"])))
    w("</table></div>")
    for line in hearth_lines(hs):
        w("<p class=note>%s</p>" % h(line))
    if hs["by_source"]:
        w("<h2>Charge by pool and source</h2><div class=tablewrap><table><tr><th>Pool<th>Source<th>Charge"
          "<th>Per active hour<th>Share of the pool</tr>")
        for r in hs["by_source"][:60]:
            w("<tr><td>%s<td>%s<td>%.0f<td>%s<td>%s</tr>" % (h(r["pool"]), h(r["source"] or "-"), r["charge"],
                                                         h(f1(r["per_hour"])), h(fp(r["share"]))))
        w("</table></div>")
    w("<h2>Who feeds the Hearth</h2><div class=tablewrap><table><tr><th>Player<th>Server<th>Charge"
      "<th>By pool<th>Top sources</tr>")
    for key, f in sorted(hs["feeders"].items(), key=lambda kv: -kv[1]["total"]):
        segs = "".join("<i style='width:%.2f%%;background:%s' title='%s %.0f'></i>" % (
            f["pools"][p] * 100 / f["total"], HEARTH_COLORS[p], h(p), f["pools"][p])
            for p in HEARTH_POOLS if f["total"] > 0 and f["pools"].get(p, 0) > 0)
        w("<tr><td>%s<td>%s<td>%.0f<td><div class=mixbar>%s</div><td style='text-align:left'>%s</tr>" % (
            h(hearth_name(d, key, f)), h(f["world"]), f["total"], segs, h(top_sources(f["sources"]))))
    w("</table></div><p class=note>Charge by source: %s.</p>" % h(top_sources(hs["sources"], 12)))
    if hs["events"]:
        w("<h2>Other Hearth events</h2><div class=tablewrap><table><tr><th>Kind<th>Label<th>Source"
          "<th>Events<th>Sum</tr>")
        for (kind, label, source), (n, total) in sorted(hs["events"].items())[:60]:
            w("<tr><td>%s<td>%s<td>%s<td>%d<td>%.1f</tr>" % (h(kind), h(label or "-"), h(source or "-"), n, total))
        w("</table></div>")


# ---------------------------------------------------------------- main

def today():
    """Today in Warsaw: the mod names its day files by the server's local date, and this
    usually runs on a UTC host where the date flips two hours early."""
    try:
        from zoneinfo import ZoneInfo
        return dt.datetime.now(ZoneInfo("Europe/Warsaw")).date()
    except Exception:  # no tz database on this machine
        return dt.date.today()


def parse_day(text):
    return dt.date.fromisoformat(text)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("sources", nargs="+", help="[LABEL=]DIR, a telemetry folder or a world")
    ap.add_argument("--out", default=".", help="folder for report.html and report.md")
    ap.add_argument("--days", type=int, default=7, help="window length when --since is not given")
    ap.add_argument("--since", type=parse_day)
    ap.add_argument("--until", type=parse_day)
    ap.add_argument("--anon", action="store_true", help="replace player names with Player A, B ...")
    args = ap.parse_args(argv)
    until = args.until or today()
    since = args.since or (until - dt.timedelta(days=args.days - 1))
    if since > until:
        ap.error("--since is after --until")
    sources = []
    for item in args.sources:
        label, sep, path = item.partition("=")
        if not sep or "/" in label:
            label, path = None, item
        sources.append((label or default_label(path), path))
    rows, files, bad = load(sources, since, until)
    d = build(rows, files, bad, since, until, args.anon, skill_ids())
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "report.md"), "w", encoding="utf-8") as fh:
        fh.write(markdown(d))
    with open(os.path.join(args.out, "report.html"), "w", encoding="utf-8") as fh:
        fh.write(page(d))
    print("balance_report: %d players, %d files, wrote %s" % (
        len(d["players"]), files, os.path.join(args.out, "report.html")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
