# Dashboard export (1.22.0)

File: `<world>/proficiency/export/players.json`, version 1, UTF-8, written whole through `players.json.tmp` and a rename.
Beside it `deaths.json` (`{uuid: [epoch ms, ...]}`, last 7 days) feeds `deathsWeek`.

```
{ "version": 1, "generated": ISO-8601, "generatedMs": n, "world": name, "skills": [skill ids in order],
  "players": [ { "uuid", "name", "online", "seenMs", "totalLevels", "totalStars", "synergies",
                 "expertisePoints", "expertiseSpent", "deathsWeek", "top": [<= 3 skill ids],
                 "skills": { "<id>": { "level": 0-100, "bar": 0..1, "stars": 0-5 } } } ],
  "hearths": [ { "dimension", "x", "y", "z", "loaded", "owner": {"uuid","name"} | null, "stage": 0-6,
                 "smouldering", "lane", "tier", "radius", "reachXp", "contributors",
                 "pools": { "hearty|regen|fortified|workshop|homecoming": {"charge", "tier"} },
                 "build": { "on", "stage", "done", "total" } } ] }
```

- `bar` is the bar a player sees: progress to the next level, at level 100 progress to the next star.
- `owner` is the Hearth's top lifetime feeder (the Hearth stores no owner). `loaded: false` rows carry only place and radius.
- Every player with Proficiency data in the world's `playerdata/<uuid>.dat` is listed, online or not (`online` tells which). Offline players are read straight from the player file (the loader's attachment tag: `neoforge:attachments`, `fabric:attachments` or `ForgeCaps`, key `proficiency:skills`), never loaded as entities; a file is parsed again only when its mtime or size changed. `name` comes from the server's profile cache (`usercache.json`), the uuid when it has none; `seenMs` is the file's mtime. Shape unchanged, so `version` stays 1.
- A reader must ignore a `version` it does not know. Dashboard: `minecraft.proficiency()` in the homelab dashboard repo.
