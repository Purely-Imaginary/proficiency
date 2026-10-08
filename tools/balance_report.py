#!/usr/bin/env python3
"""Balance report from Proficiency telemetry (python3 stdlib only).

The mod appends one JSON line per bucket every five minutes to
<world>/proficiency/telemetry/YYYY-MM-DD.jsonl (see README, "Balance telemetry"). This reads
one or more such folders and writes report.md and a self-contained report.html:

  XP per active hour per skill (median over players, with the player count)
  source mix per skill, dead skills, runaway skills (more than 3x the median skill)
  time to level at the current rate against the XP curve
  proc rate against the configured chance, and what deaths cost

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
          "decorating social nightwalker courage guardian charger tactician").split()

MIN_ACTIVE_S = 300          # a player needs five minutes in a skill before their rate counts
RUNAWAY_FACTOR = 3.0
SLOW_FACTOR = 1 / 3
PROC_MIN_ROLLS = 30
EXCLUDED_KINDS = {"command"}  # an op's /skills addxp is not play
DEFAULT_CURVE = (8.0, 2.0, 1.35)
MAX_LEVEL = 100

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
    }


# ---------------------------------------------------------------- markdown

def markdown(d):
    L = []
    w = L.append
    t = d["table"]
    w("# Proficiency balance report")
    w("")
    w("Window %s to %s, %d day file(s), %d player(s) on %s. Generated %s.%s" % (
        d["since"], d["until"], d["files"], len(d["players"]),
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
    w("- Dead (no XP in the window): %s." % (", ".join(dead) or "none"))
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
    return "\n".join(L) + "\n"


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
    w("<p class=sub>%s to %s &middot; %d day file(s) &middot; %s%s &middot; generated %s</p>" % (
        h(d["since"]), h(d["until"]), d["files"], h(", ".join(d["worlds"]) or "no server"),
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
                       (len(run), "runaway skills"), (len(dead), "dead skills")):
        w("<div class=card><b>%s</b><span>%s</span></div>" % (h(big), h(small)))
    w("</div>")
    w("<div class='card list'><b style='font-size:1rem'>Findings</b>")
    w("<p>Runaway (over %gx the median): %s</p>" % (RUNAWAY_FACTOR, ", ".join(
        "<b>%s</b> %s" % (h(r["skill"]), h(fx(r["vs_median"]))) for r in run) or "none"))
    w("<p>Slow (under a third): %s</p>" % (", ".join(
        "%s %s" % (h(r["skill"]), h(fx(r["vs_median"]))) for r in slow) or "none"))
    w("<p>Dead (no XP in the window): %s</p>" % (h(", ".join(r["skill"] for r in dead)) or "none"))
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
    w("<h2>Dead skills</h2><p>%s</p>" % (
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
    w("</table></div></main></body></html>")
    return "".join(o)


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
