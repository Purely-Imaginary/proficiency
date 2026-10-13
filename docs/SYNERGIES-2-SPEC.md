# Synergies 2: about ten per tree (design spec)

Status: design only, nothing built. Written 2026-10-12 against baseline cea0189 (mod 1.5.6).
Sources read: `core/.../perk/Synergies.java`, `Synergy.java`, `PerkEffect.java`, `tools/talents_spec.py`
(the source of `docs/TREES.md` and `TalentTable.java`), the event handlers named per batch below,
`client/TalentTreeScreen.java`, `command/SkillCommand.java`, `SynergiesTest`, `SpecialsWiredTest`, `LangFilesTest`, `en_us.json`, `pl_pl.json`.

## What this adds

- **131 new synergies** on top of the 26 pairings that exist today (plus Renaissance, which needs no node).
  125 pairs, 6 triples.
- **102 numeric-only** (Batch A) and **29 bespoke** (Batches B1 to B4, each with a `special` tag read in event code).
- Every one of the 35 skills is a *need* in 9 or 10 synergies counting the existing ones (table at the end).

Why 131 and not 115: nine needs per skill for 35 skills is 315 need slots; the existing 26 fill 54, so 261 are new.
115 synergies only reach 261 with about 31 triples. The brief says mostly pairs, so this spec keeps 6 triples and
pays for it with more entries. If the owner prefers fewer, the seven skills at 10 (fishing, jumping, smithing, wayfaring,
courage, guardian, charger) can each give up one pair by folding it into a triple with a neighbour, which takes the
total to about 124 without dropping any skill below 9.

## Rules this spec followed

- Needs are single nodes at a rank, never a full tree, never a root node. Depth spread over all needs:
  level-10 nodes: 67, level-30 nodes: 108, level-60 nodes: 69, level-90 nodes: 24.
- No two synergies (new or old) share a need set; the checker compared all 157.
- Grants: 2 or 3 per synergy, x1.05 to x1.20, only to skills that are needs of that synergy, only on
  `XP_RATE`, `BONUS`, `PROC_CHANCE`, `PROC_POWER`, `ABILITY_DURATION`, `TEMPO`. No `DEATH_WARD` (owner rule),
  no `ABILITY_COOLDOWN` (kept out so the 0.25 floor never becomes the norm).
- Stacking: `Synergies.multiplier` multiplies every active grant. With every synergy of this spec and the old ones
  active at once, the largest product for any skill and effect is:
  sneaking XP_RATE x1.46, blocking XP_RATE x1.46, courage XP_RATE x1.46, farming XP_RATE x1.46, wayfaring XP_RATE x1.46, social XP_RATE x1.40.
  The largest passive (`BONUS`) product is guardian x1.33, beastslaying x1.33, charger x1.27, swimming x1.27.
  Nothing reaches x2, and those products need nine or ten other trees deep at once.
- Names checked case-insensitively against every value in `en_us.json` and `pl_pl.json` (skill, talent, proc,
  ability and synergy names) and against each other. No clashes. Descriptions are at most 90 characters in English,
  have no em dash, and none needs a `.short` key (`TooltipDetail.shortOr` falls back to `.desc`), so no new key has to
  exist in all 12 languages.

## Implementation order and file ownership

1. **Batch A first, alone.** It owns `core/.../perk/Synergies.java` and both lang files for *all* 131 entries,
   bespoke ones included, so the B batches never edit those shared files' structure. Static imports of
   `ABILITY_DURATION` and `TEMPO` must be added. Bespoke entries go in with `special` set to `null` and a unique
   marker comment, for example `null /* B1:turn_and_thrust */`. This keeps `SpecialsWiredTest` (every special tag
   must be read somewhere) green before the behaviour exists. Batch A also adds the core tests listed under Tests.
2. **B1 to B4 in parallel, two at a time** (B1 with B3, then B2 with B4, or any pairing: their handler files are
   disjoint). Each B agent replaces only its own `null /* Bn:id */` markers in `Synergies.java` with the tag
   (`"turn_and_thrust"`), which are one-line edits on distinct lines. Each B agent mirrors every common/ edit into
   the `forge-1.20.1/src/main/java` hand copy of the same file.
3. Every bespoke tag is read through `TalentService.hasSynergy(player, "<id>")`, the way `woodsman`, `ambush`,
   `juggernaut` and `tidecaller` are read today. The special tag equals the synergy id throughout this spec.

Strings per synergy, in `common/src/main/resources/assets/proficiency/lang/en_us.json` and `pl_pl.json`:
`proficiency.synergy.<id>` (name) and `proficiency.synergy.<id>.desc`. Other 10 languages: the orchestrator.

## Batch A: numeric-only synergies

102 entries. No event code. Files: `core/.../perk/Synergies.java`, `en_us.json`, `pl_pl.json`, core tests.
Batch A also adds the B entries (grants, names, `null` special), listed in each B section below.

| id | needs | grants | English | Polish |
|---|---|---|---|---|
| `cavalry_blade` | Swords `keen_edge` 5/5 (L10)<br>Charger `first_in` 5/5 (L10) | Swords passive x1.10<br>Charger XP x1.10 | **Cavalry Blade**: The first cut of a charge is the one that counts. | **Szabla kawalerzysty**: Liczy się pierwsze cięcie szarży. |
| `bloodied_steel` | Swords `vampiric_edge` 3/3 (L60)<br>Endurance `adrenal_mend` 3/3 (L30) | Swords XP x1.10<br>Endurance XP x1.10 | **Bloodied Steel**: Every wound you trade teaches the blade and the body. | **Zakrwawiona stal**: Każda wymieniona rana uczy i ostrze, i ciało. |
| `quiet_blade` | Sneaking `backstab` 3/3 (L30)<br>Swords `opening` 5/5 (L10) | Swords proc chance x1.10<br>Sneaking passive x1.05 | **Quiet Blade**: The opening you wait for in the shadows comes sooner. | **Ciche ostrze**: Okazja, na którą czekasz w cieniu, przychodzi szybciej. |
| `bladesmith` | Smithing `weaponwright` 1/1 (L60)<br>Swords `keen_edge` 3/5 (L10) | Swords passive x1.05<br>Smithing XP x1.05 | **Bladesmith**: You know which edge you ground yourself. | **Kowal mieczy**: Znasz ostrze, które sam naostrzyłeś. |
| `moonblade` | Swords `hemorrhage` 3/3 (L30)<br>Nightwalker `silver_light` 3/3 (L30) | Swords proc power x1.10<br>Nightwalker proc chance x1.10 | **Moonblade**: Silver light finds the wound and widens it. | **Księżycowe ostrze**: Srebrne światło znajduje ranę i ją pogłębia. |
| `armsmaster` | Swords `battle_trance` 2/3 (L60)<br>Axes `rampage` 2/3 (L60)<br>Maces `bell_ringer` 2/3 (L60) | Swords ability time x1.10<br>Axes ability time x1.10<br>Maces ability time x1.10 | **Armsmaster**: Blade, axe and mace: every frenzy runs a little longer. | **Mistrz oręża**: Miecz, topór i buzdygan: każdy szał trwa nieco dłużej. |
| `berserkergang` | Axes `berserker` 3/3 (L30)<br>Endurance `scar_tissue` 3/3 (L30) | Axes proc power x1.10<br>Endurance passive x1.05 | **Berserkergang**: Old scars, fresh rage. | **Berserkergang**: Stare blizny, świeża furia. |
| `red_mist` | Axes `bloodrush` 3/3 (L30)<br>Courage `hot_blood` 5/5 (L10) | Axes proc chance x1.10<br>Courage passive x1.10 | **Red Mist**: Outnumbered, the axe swings wider and the blood runs hotter. | **Czerwona mgła**: W przewadze wroga topór zatacza szersze łuki, a krew wrze. |
| `shieldbreaker` | Axes `heavy_blows` 5/5 (L10)<br>Blocking `shield_bash` 3/3 (L30) | Axes XP x1.10<br>Blocking proc chance x1.10 | **Shieldbreaker**: You know a shield from both sides. | **Łamacz tarcz**: Znasz tarczę z obu stron. |
| `headtaker` | Axes `headsman` 3/5 (L60)<br>Beastslaying `trophy_hunter` 5/5 (L10) | Axes proc power x1.10<br>Beastslaying proc chance x1.10 | **Headtaker**: The trophy comes off cleaner with a heavy axe. | **Łowca głów**: Ciężki topór odcina trofeum czyściej. |
| `wolfs_hour` | Axes `bloodlust` 5/5 (L10)<br>Nightwalker `moonrise` 5/5 (L10) | Axes proc chance x1.05<br>Nightwalker XP x1.10 | **Wolf's Hour**: When the moon is up, the axe gets hungry. | **Godzina wilka**: Gdy wschodzi księżyc, topór robi się głodny. |
| `raider` | Axes `bloodrush` 2/3 (L30)<br>Running `adrenaline` 3/3 (L30) | Axes XP x1.10<br>Running proc power x1.10 | **Raider**: Hit, run, hit again. | **Najeźdźca**: Uderz, uciekaj, uderz znowu. |
| `anvil_body` | Maces `crushing` 5/5 (L10)<br>Endurance `iron_constitution` 5/5 (L10) | Maces passive x1.05<br>Endurance ability time x1.10 | **Anvil Body**: Swing a heavy thing long enough and you become one. | **Ciało jak kowadło**: Wymachuj czymś ciężkim dość długo, a sam takim się staniesz. |
| `quarry_hammer` | Maces `tremor` 5/5 (L10)<br>Mining `efficiency` 3/5 (L10) | Maces proc chance x1.10<br>Mining XP x1.10 | **Quarry Hammer**: Rock and bone break the same way. | **Młot kamieniarza**: Skała i kość pękają tak samo. |
| `battering_ram` | Maces `concussion` 3/3 (L30)<br>Charger `crash_in` 3/3 (L30) | Maces XP x1.10<br>Charger proc power x1.10 | **Battering Ram**: The weight of the run goes into the head of the mace. | **Taran bojowy**: Cały impet biegu idzie w głowicę buzdygana. |
| `war_bell` | Maces `bell_ringer` 3/3 (L60)<br>Courage `war_drums` 3/3 (L30) | Maces proc power x1.05<br>Courage proc chance x1.10 | **War Bell**: Every ringing blow beats the drum for the next. | **Dzwon wojny**: Każdy dźwięczny cios wybija rytm następnego. |
| `smiths_maul` | Maces `aftershock` 3/3 (L30)<br>Smithing `temper` 3/3 (L30) | Maces XP x1.10<br>Smithing passive x1.10 | **Smith's Maul**: Anvil work and skull work, one swing. | **Kowalski młot**: Praca na kowadle i na czaszkach, jeden zamach. |
| `undercurrent` | Tridents `riptide_surge` 2/3 (L60)<br>Swimming `riptide_kick` 2/3 (L60) | Tridents ability time x1.10<br>Swimming ability time x1.10 | **Undercurrent**: Storm and tide hold you a little longer. | **Prąd głębinowy**: Sztorm i fala trzymają cię nieco dłużej. |
| `stormcaller` | Tridents `conductor` 1/1 (L90)<br>Spellcasting `overcharge` 3/3 (L30) | Tridents proc chance x1.10<br>Spellcasting proc power x1.10 | **Stormcaller**: The sky answers a spear and a spell alike. | **Wołający burzę**: Niebo odpowiada i na włócznię, i na zaklęcie. |
| `sea_wolf` | Tridents `impaler` 3/5 (L30)<br>Courage `unshaken` 3/3 (L30) | Tridents XP x1.10<br>Courage passive x1.05 | **Sea Wolf**: Waist-deep and outnumbered is where you fight best. | **Morski wilk**: Najlepiej walczysz po pas w wodzie i w mniejszości. |
| `deep_sea_slayer` | Tridents `skewer` 3/5 (L60)<br>Beastslaying `giant_slayer` 2/3 (L60) | Tridents proc power x1.05<br>Beastslaying passive x1.10 | **Deep-Sea Slayer**: The bigger the thing in the water, the deeper the spear. | **Pogromca głębin**: Im większe stworzenie w wodzie, tym głębiej wchodzi włócznia. |
| `boarding_party` | Tridents `true_aim` 5/5 (L10)<br>Charger `head_start` 3/3 (L30) | Tridents passive x1.05<br>Charger XP x1.10 | **Boarding Party**: First over the rail, spear first. | **Abordaż**: Pierwszy przez burtę, z włócznią przed sobą. |
| `spear_volley` | Tridents `tidal_edge` 3/5 (L10)<br>Tactician `spotter` 5/5 (L10) | Tactician passive x1.10<br>Tridents XP x1.10 | **Spear Volley**: A thrown trident is a shot from the back line too. | **Grad włóczni**: Rzucony trójząb to też strzał z tylnej linii. |
| `mariner` | Tridents `deep_breath` 1/1 (L30)<br>Wayfaring `trailwise` 5/5 (L10)<br>Fishing `rain_dancer` 2/3 (L60) | Tridents XP x1.05<br>Wayfaring passive x1.10<br>Fishing XP x1.05 | **Mariner**: Spear, rod and the open road over water. | **Żeglarz**: Włócznia, wędka i otwarta droga po wodzie. |
| `iron_monk` | Unarmed `iron_skin` 3/3 (L30)<br>Endurance `roll_with_it` 3/3 (L10) | Unarmed passive x1.05<br>Endurance proc chance x1.10 | **Iron Monk**: Fists and ribs hardened in the same school. | **Żelazny mnich**: Pięści i żebra hartowane w tej samej szkole. |
| `flying_kick` | Unarmed `haymaker` 5/5 (L10)<br>Jumping `leap` 3/3 (L30) | Unarmed proc chance x1.10<br>Jumping passive x1.05 | **Flying Kick**: The leap is half of the blow. | **Kopnięcie z wyskoku**: Skok to połowa ciosu. |
| `tavern_hero` | Unarmed `combo` 3/3 (L30)<br>Courage `battle_cry` 5/5 (L10) | Unarmed tempo x1.15<br>Courage XP x1.10 | **Tavern Hero**: Three against one and you are only warming up. | **Bohater karczmy**: Trzech na jednego, a ty dopiero się rozgrzewasz. |
| `kitchen_brawler` | Unarmed `iron_fist` 5/5 (L10)<br>Cooking `hearty` 3/3 (L30) | Unarmed XP x1.10<br>Cooking passive x1.05 | **Kitchen Brawler**: Kneading dough builds the same arm as a right hook. | **Kuchenny zabijaka**: Wyrabianie ciasta buduje to samo ramię co prawy sierpowy. |
| `drunken_fist` | Unarmed `stagger` 3/3 (L30)<br>Alchemy `strong_brew` 3/3 (L30) | Unarmed proc chance x1.05<br>Alchemy XP x1.10 | **Drunken Fist**: A strong brew and a looser stance. | **Pijana pięść**: Mocny wywar i luźniejsza postawa. |
| `open_palm` | Unarmed `dragon_palm` 3/5 (L60)<br>Guardian `mending_guard` 3/3 (L30) | Guardian passive x1.05<br>Unarmed proc power x1.10 | **Open Palm**: The hand that strikes is the hand that shields. | **Otwarta dłoń**: Dłoń, która uderza, to dłoń, która osłania. |
| `shoulder_charge` | Unarmed `iron_fist` 3/5 (L10)<br>Charger `crash_in` 2/3 (L30) | Unarmed passive x1.05<br>Charger XP x1.05 | **Shoulder Charge**: No weapon needed to arrive first. | **Szarża z barku**: Żeby dotrzeć pierwszym, broń nie jest potrzebna. |
| `unbroken_line` | Blocking `tenacity` 3/3 (L30)<br>Courage `unyielding` 2/3 (L60) | Blocking passive x1.05<br>Courage ability time x1.10 | **Unbroken Line**: Where you plant the shield, the line holds. | **Nieprzerwany szyk**: Gdzie wbijesz tarczę, tam szyk wytrzyma. |
| `shieldwright` | Blocking `spiked_rim` 2/3 (L60)<br>Smithing `whetstone` 3/3 (L10) | Blocking proc power x1.10<br>Smithing XP x1.10 | **Shieldwright**: You forged the rim you hit back with. | **Tarczownik**: Sam wykułeś obręcz, którą oddajesz ciosy. |
| `shield_rush` | Blocking `shield_bash` 2/3 (L30)<br>Charger `trust_the_line` 3/3 (L10) | Blocking proc chance x1.05<br>Charger passive x1.05 | **Shield Rush**: A raised shield is a battering ram with a handle. | **Natarcie tarczą**: Uniesiona tarcza to taran z uchwytem. |
| `hardtack` | Cooking `comfort_food` 3/3 (L30)<br>Endurance `lean_times` 2/3 (L60) | Endurance XP x1.05<br>Cooking proc chance x1.05 | **Hardtack**: Plain food, long marches, a stubborn body. | **Suchary**: Proste jedzenie, długie marsze, uparte ciało. |
| `marathoner` | Running `endurance` 3/3 (L10)<br>Endurance `deep_reserves` 3/3 (L30) | Running passive x1.05<br>Endurance proc power x1.10 | **Marathoner**: There is always one more mile in the legs. | **Maratończyk**: W nogach zawsze zostaje jeszcze jeden kilometr. |
| `moonshot` | Archery `eagle_eye` 5/5 (L10)<br>Nightwalker `far_sight` 3/3 (L30) | Archery proc chance x1.05<br>Nightwalker passive x1.10 | **Moonshot**: Eyes made for the dark, arrows that find it. | **Strzał w księżyc**: Oczy stworzone do ciemności i strzały, które ją przeszywają. |
| `big_game_hunter` | Archery `longshot` 3/3 (L30)<br>Beastslaying `monster_lore` 5/5 (L10) | Archery proc power x1.10<br>Beastslaying XP x1.10 | **Big Game Hunter**: Know the beast, then take it from a long way off. | **Łowca grubego zwierza**: Poznaj bestię, a potem ustrzel ją z daleka. |
| `twin_strings` | Archery `rain_of_arrows` 2/3 (L60)<br>Crossbows `hair_trigger` 5/5 (L10) | Archery ability time x1.10<br>Crossbows proc chance x1.05 | **Twin Strings**: Bow or crossbow, the hands already know. | **Dwie cięciwy**: Łuk czy kusza, ręce już wiedzą, co robić. |
| `arcane_arrow` | Archery `deadeye` 1/1 (L90)<br>Spellcasting `arcane_focus` 5/5 (L10) | Archery proc power x1.05<br>Spellcasting XP x1.10 | **Arcane Arrow**: A steady breath is half a spell. | **Tajemna strzała**: Spokojny oddech to pół zaklęcia. |
| `boltwright` | Smithing `masterwork` 5/5 (L10)<br>Crossbows `armor_piercer` 3/3 (L30) | Crossbows proc power x1.10<br>Smithing proc chance x1.05 | **Boltwright**: Bolts from your own anvil fly truer. | **Wytwórca bełtów**: Bełty z własnego kowadła lecą celniej. |
| `firing_line` | Crossbows `siege_crew` 2/3 (L60)<br>Tactician `suppression` 2/3 (L60) | Crossbows XP x1.10<br>Tactician ability time x1.10 | **Firing Line**: Load, call, loose, all on the same breath. | **Linia ognia**: Naciągnij, wskaż, strzel, wszystko na jednym oddechu. |
| `arrow_slit` | Crossbows `quickload` 2/3 (L60)<br>Masonry `cornerstone` 2/3 (L60)<br>Tactician `headshot` 3/3 (L30) | Crossbows passive x1.05<br>Masonry XP x1.10<br>Tactician proc power x1.10 | **Arrow Slit**: You built the wall, so you know where to shoot from. | **Strzelnica**: Sam zbudowałeś mur, więc wiesz, skąd strzelać. |
| `snipers_nest` | Sneaking `unseen` 3/3 (L30)<br>Crossbows `salvage` 3/3 (L30) | Crossbows XP x1.05<br>Sneaking proc chance x1.10 | **Sniper's Nest**: Unseen, unhurried, never out of bolts. | **Gniazdo strzelca**: Niewidoczny, bez pośpiechu, zawsze z bełtami. |
| `sentry_post` | Crossbows `armor_piercer` 2/3 (L30)<br>Guardian `taunt` 1/1 (L10) | Guardian proc chance x1.05<br>Crossbows passive x1.05 | **Sentry Post**: Someone has to stand on the wall and draw their eye. | **Posterunek**: Ktoś musi stać na murze i ściągać ich wzrok. |
| `tunneler` | Mining `vein_miner` 3/3 (L30)<br>Excavation `mole` 2/3 (L60) | Mining passive x1.05<br>Excavation XP x1.10 | **Tunneler**: Stone or soil, the tunnel goes on. | **Tunelarz**: Kamień czy ziemia, tunel idzie dalej. |
| `steam_drill` | Mining `rich_veins` 2/3 (L60)<br>Engineering `overclock` 3/3 (L30) | Mining proc power x1.10<br>Engineering XP x1.10 | **Steam Drill**: A tuned machine and a rich vein feed each other. | **Parowe wiertło**: Dostrojona maszyna i bogata żyła napędzają się nawzajem. |
| `quarryman` | Mining `prospector` 5/5 (L10)<br>Masonry `stonecutter` 3/3 (L30) | Mining XP x1.10<br>Masonry proc power x1.10 | **Quarryman**: You cut the stone you will build with. | **Łamacz kamienia**: Sam wydobywasz kamień, z którego będziesz budować. |
| `mineral_tincture` | Mining `gem_cutter` 3/3 (L30)<br>Alchemy `brewers_eye` 3/3 (L30) | Mining proc power x1.05<br>Alchemy passive x1.10 | **Mineral Tincture**: Redstone and glowstone go from the vein to the vial. | **Nalewka mineralna**: Czerwony kamień i jasnogłaz trafiają prosto z żyły do fiolki. |
| `lightless_seam` | Mining `dark_sight` 1/1 (L30)<br>Nightwalker `dark_adapted` 5/5 (L10) | Mining XP x1.05<br>Nightwalker XP x1.10 | **Lightless Seam**: Down where no torch reaches, you still see the ore. | **Żyła bez światła**: Tam, gdzie nie sięga pochodnia, wciąż widzisz rudę. |
| `joinery` | Woodcutting `heartwood` 3/3 (L30)<br>Decorating `thrift_shop` 5/5 (L10) | Woodcutting passive x1.05<br>Decorating XP x1.10 | **Joinery**: The heartwood you cut ends up as the table you set. | **Stolarka**: Rdzeń drzewa, który ścinasz, staje się stołem, który nakrywasz. |
| `greenway` | Woodcutting `woodland_stride` 2/3 (L60)<br>Wayfaring `fresh_air` 3/3 (L10) | Wayfaring proc chance x1.05<br>Woodcutting ability time x1.10 | **Greenway**: The forest is a road if you know it. | **Zielony szlak**: Las jest drogą, jeśli go znasz. |
| `full_woodpile` | Woodcutting `seasoned` 2/3 (L60)<br>Homebuilder `kindling` 5/5 (L10) | Homebuilder proc chance x1.10<br>Woodcutting XP x1.05 | **Full Woodpile**: A home is warm because someone chopped the wood. | **Pełna drewutnia**: W domu jest ciepło, bo ktoś narąbał drewna. |
| `smokehouse` | Woodcutting `forager` 3/3 (L30)<br>Cooking `camp_kitchen` 1/1 (L10) | Cooking XP x1.10<br>Woodcutting proc chance x1.05 | **Smokehouse**: Apples, alder smoke and a camp pot. | **Wędzarnia**: Jabłka, olchowy dym i kociołek przy obozie. |
| `timber_frame` | Woodcutting `canopy` 1/1 (L30)<br>Masonry `long_arm` 3/3 (L30) | Masonry passive x1.05<br>Woodcutting proc power x1.05 | **Timber Frame**: Beams and stone, raised by one pair of hands. | **Mur pruski**: Belki i kamień, wzniesione jedną parą rąk. |
| `topsoil` | Excavation `sifter` 3/3 (L30)<br>Farming `seed_saver` 3/3 (L30) | Excavation proc power x1.10<br>Farming XP x1.10 | **Topsoil**: Good dirt is half a harvest. | **Czarnoziem**: Dobra ziemia to pół plonu. |
| `potters_clay` | Excavation `quick_dig` 5/5 (L10)<br>Decorating `inspiration` 5/5 (L10) | Excavation XP x1.10<br>Decorating proc chance x1.10 | **Potter's Clay**: The riverbank you dig becomes the vase on the shelf. | **Glina garncarza**: Brzeg rzeki, który kopiesz, staje się wazonem na półce. |
| `groundwork` | Excavation `earthmover` 2/3 (L60)<br>Homebuilder `scaffolder` 3/3 (L30) | Excavation passive x1.05<br>Homebuilder XP x1.10 | **Groundwork**: Every home starts with a level patch of earth. | **Fundamenty**: Każdy dom zaczyna się od wyrównanego kawałka ziemi. |
| `deep_strata` | Excavation `fossil_hunter` 1/1 (L90)<br>Spelunking `deep_dweller` 3/3 (L60) | Excavation XP x1.10<br>Spelunking passive x1.10 | **Deep Strata**: The older the layer, the more it gives up. | **Głębokie warstwy**: Im starsza warstwa, tym więcej oddaje. |
| `earthworks` | Excavation `landslide_lore` 5/5 (L10)<br>Engineering `wrench_wizard` 3/3 (L10) | Excavation proc chance x1.10<br>Engineering passive x1.05 | **Earthworks**: Move enough dirt and it becomes engineering. | **Wały ziemne**: Przerzuć dość ziemi, a stanie się inżynierią. |
| `herbalist` | Farming `harvest_moon` 5/5 (L10)<br>Alchemy `catalyst` 3/3 (L10) | Alchemy XP x1.10<br>Farming proc chance x1.05 | **Herbalist**: You grow what goes into the brew. | **Zielarz**: Sam hodujesz to, co trafia do wywaru. |
| `barn_raising` | Farming `husbandry` 3/3 (L30)<br>Social `kinship` 2/2 (L10) | Farming XP x1.10<br>Social passive x1.05 | **Barn Raising**: The whole village turns out for a good farm. | **Tłoka**: Cała wieś zbiera się przy dobrym gospodarstwie. |
| `irrigation` | Farming `scythe` 1/2 (L60)<br>Engineering `redstone_savant` 3/3 (L30) | Farming ability time x1.10<br>Engineering proc chance x1.10 | **Irrigation**: Redstone does the watering, you do the reaping. | **Nawadnianie**: Czerwony kamień podlewa, ty zbierasz. |
| `flower_beds` | Farming `druid` 1/1 (L90)<br>Decorating `feng_shui` 3/3 (L30) | Decorating proc power x1.10<br>Farming XP x1.05 | **Flower Beds**: A garden is a room with the sky for a ceiling. | **Rabaty**: Ogród to pokój, którego sufitem jest niebo. |
| `riverborn` | Fishing `patient` 5/5 (L10)<br>Swimming `dolphin` 5/5 (L10) | Fishing passive x1.05<br>Swimming proc chance x1.10 | **Riverborn**: You know the river from above and below. | **Dziecię rzeki**: Znasz rzekę z góry i od spodu. |
| `wandering_angler` | Fishing `rain_dancer` 2/3 (L60)<br>Wayfaring `globetrotter` 2/3 (L60) | Fishing XP x1.10<br>Wayfaring ability time x1.10 | **Wandering Angler**: Every new river has a fish you have not met. | **Wędrowny wędkarz**: W każdej nowej rzece pływa ryba, której jeszcze nie znasz. |
| `night_line` | Fishing `old_salt` 2/3 (L60)<br>Nightwalker `long_night` 2/3 (L60) | Fishing ability time x1.10<br>Nightwalker ability time x1.10 | **Night Line**: The best bites come after dark. | **Nocna żyłka**: Najlepiej bierze po zmroku. |
| `brine_and_bottle` | Fishing `sunken_treasure` 1/1 (L90)<br>Alchemy `distillery` 1/1 (L60) | Alchemy proc chance x1.10<br>Fishing proc power x1.05 | **Brine and Bottle**: Pufferfish and salt water belong in the brewing stand. | **Solanka i flaszka**: Rozdymka i słona woda pasują do statywu alchemicznego. |
| `fish_story` | Fishing `lucky_line` 3/5 (L10)<br>Homebuilder `storyteller` 2/3 (L60) | Homebuilder passive x1.05<br>Fishing XP x1.05 | **Fish Story**: It was this big, and it grows every time you tell it. | **Rybackie opowieści**: Była taka duża, i rośnie przy każdym opowiadaniu. |
| `cutpurses_flight` | Running `escape_artist` 2/3 (L60)<br>Sneaking `cat_landing` 3/3 (L30) | Sneaking XP x1.10<br>Running proc chance x1.05 | **Cutpurse's Flight**: Over the wall, down the roof, gone. | **Ucieczka rzezimieszka**: Przez mur, po dachu, i już cię nie ma. |
| `running_start` | Running `roadrunner` 2/3 (L60)<br>Jumping `stomp` 2/3 (L30)<br>Charger `rolling_thunder` 3/3 (L30) | Charger proc power x1.05<br>Running XP x1.10<br>Jumping proc power x1.10 | **Running Start**: Sprint, leap, land on them. | **Z rozpędu**: Sprint, skok i lądowanie prosto na nich. |
| `running_mates` | Running `slipstream` 1/1 (L30)<br>Social `in_step` 1/1 (L90) | Social ability time x1.10<br>Running ability time x1.10 | **Running Mates**: Nobody falls behind on a good run. | **Ekipa biegowa**: Na dobrym biegu nikt nie zostaje z tyłu. |
| `hidden_spotter` | Sneaking `quiet_feet` 5/5 (L10)<br>Tactician `clear_line` 1/1 (L10) | Tactician XP x1.10<br>Sneaking passive x1.05 | **Hidden Spotter**: The best spotter is the one nobody sees. | **Ukryty obserwator**: Najlepszy obserwator to ten, którego nikt nie widzi. |
| `hush_of_the_deep` | Sneaking `assassin` 1/1 (L90)<br>Spelunking `warden_whisper` 2/3 (L60) | Spelunking proc chance x1.10<br>Sneaking proc power x1.05 | **Hush of the Deep**: Down there, silence is the only armour that works. | **Cisza głębin**: Tam na dole jedyną działającą zbroją jest cisza. |
| `pounce` | Jumping `stomp` 3/3 (L30)<br>Charger `unstoppable` 2/3 (L60) | Charger ability time x1.10<br>Jumping proc power x1.05 | **Pounce**: Coming from above counts as coming first. | **Skok na ofiarę**: Atak z góry liczy się jak atak jako pierwszy. |
| `chasm_leaper` | Spelunking `rope_climber` 1/1 (L30)<br>Jumping `skywalker` 1/1 (L90) | Spelunking passive x1.05<br>Jumping passive x1.05 | **Chasm Leaper**: Ravines are just long stairs. | **Skoczek nad przepaścią**: Wąwozy to po prostu długie schody. |
| `sump_diver` | Swimming `pearl_diver` 1/1 (L60)<br>Spelunking `cave_sense` 5/5 (L10) | Spelunking proc chance x1.05<br>Swimming proc power x1.10 | **Sump Diver**: The flooded passage is just another tunnel. | **Nurek jaskiniowy**: Zalany korytarz to po prostu kolejny tunel. |
| `island_hopper` | Swimming `leviathan_blood` 1/1 (L90)<br>Wayfaring `world_walker` 1/1 (L90) | Wayfaring passive x1.05<br>Swimming ability time x1.10 | **Island Hopper**: The sea is a road with no milestones. | **Wyspiarz**: Morze to droga bez kamieni milowych. |
| `salt_cure` | Swimming `current_rider` 2/3 (L30)<br>Alchemy `antidote` 3/3 (L30) | Alchemy ability time x1.10<br>Swimming passive x1.05 | **Salt Cure**: Sea water and the right tincture keep you sound. | **Solna kuracja**: Morska woda i właściwa nalewka trzymają cię w zdrowiu. |
| `gearwright` | Smithing `reforge` 3/3 (L30)<br>Engineering `cogsmith` 3/3 (L30) | Smithing proc power x1.10<br>Engineering XP x1.05 | **Gearwright**: A cog is a blade that goes round. | **Kowal trybów**: Zębatka to ostrze, które się kręci. |
| `runesmith` | Smithing `disenchanter` 1/1 (L60)<br>Alchemy `runescribe` 1/1 (L60) | Smithing ability time x1.10<br>Alchemy proc power x1.10 | **Runesmith**: What the grindstone takes off, the rune puts back. | **Kowal run**: Co zdejmie kamień szlifierski, to runa przywraca. |
| `guild_prices` | Smithing `haggler` 5/5 (L10)<br>Social `good_word` 2/2 (L10) | Smithing passive x1.05<br>Social proc chance x1.10 | **Guild Prices**: A good word at the anvil is worth a few levels. | **Cechowe ceny**: Dobre słowo przy kowadle jest warte kilku poziomów. |
| `wrought_iron` | Smithing `legendary` 1/1 (L90)<br>Decorating `curator` 3/3 (L60) | Decorating proc power x1.05<br>Smithing tempo x1.15 | **Wrought Iron**: The gate you forged is the finest thing in the room. | **Kute żelazo**: Wykuta przez ciebie brama to najpiękniejsza rzecz w pomieszczeniu. |
| `potluck` | Cooking `shared_meal` 2/3 (L60)<br>Social `old_friends` 3/3 (L30) | Cooking proc power x1.10<br>Social XP x1.05 | **Potluck**: Everyone brings a dish, and you bring two. | **Składkowa kolacja**: Każdy przynosi danie, a ty przynosisz dwa. |
| `trail_rations` | Wayfaring `campfire` 1/1 (L30)<br>Cooking `hearty` 3/3 (L30) | Wayfaring XP x1.05<br>Cooking proc chance x1.05 | **Trail Rations**: Food that keeps, for roads that do not end. | **Prowiant na szlak**: Jedzenie, które wytrzyma, na drogi, które się nie kończą. |
| `mana_tonic` | Alchemy `elixir_lore` 2/3 (L60)<br>Spellcasting `mana_font` 3/3 (L30) | Spellcasting passive x1.05<br>Alchemy tempo x1.15 | **Mana Tonic**: A brewer knows what the well is made of. | **Tonik many**: Alchemik wie, z czego jest zrobiona studnia mocy. |
| `starward` | Spellcasting `spell_ward` 3/3 (L30)<br>Nightwalker `phantom_ward` 2/3 (L60) | Spellcasting XP x1.10<br>Nightwalker passive x1.05 | **Starward**: Wards drawn by starlight hold through the night. | **Gwiezdna osłona**: Osłony kreślone w świetle gwiazd trzymają przez całą noc. |
| `ley_hall` | Spellcasting `deep_well` 2/3 (L60)<br>Decorating `lamplighter` 3/3 (L30) | Decorating passive x1.05<br>Spellcasting proc chance x1.05 | **Ley Hall**: Lamps placed along the ley lines burn with more than fire. | **Sala mocy**: Lampy na liniach mocy płoną czymś więcej niż ogniem. |
| `arcane_circle` | Spellcasting `arcane_rhythm` 2/3 (L60)<br>Social `teaching` 3/3 (L30) | Social passive x1.05<br>Spellcasting tempo x1.15 | **Arcane Circle**: Spells learned together are cast in rhythm. | **Krąg magów**: Zaklęcia uczone razem rzuca się w jednym rytmie. |
| `battlemage` | Spellcasting `echo_cast` 1/1 (L90)<br>Courage `giant_slayer` 2/3 (L30) | Courage proc power x1.10<br>Spellcasting proc power x1.05 | **Battlemage**: The bigger the foe, the louder the echo. | **Mag bojowy**: Im większy wróg, tym głośniejsze echo. |
| `hexbane` | Beastslaying `bane` 3/3 (L30)<br>Spellcasting `overcharge` 2/3 (L30) | Beastslaying proc power x1.10<br>Spellcasting passive x1.05 | **Hexbane**: The undead fear the word as much as the blade. | **Pogromca uroków**: Nieumarli boją się słowa tak samo jak ostrza. |
| `fortifier` | Engineering `blueprint` 1/1 (L60)<br>Masonry `bulk_order` 2/3 (L60) | Engineering passive x1.05<br>Masonry proc chance x1.10 | **Fortifier**: Walls with gears in them. | **Fortyfikator**: Mury z zębatkami w środku. |
| `clockwork_home` | Engineering `assembly_line` 2/3 (L60)<br>Homebuilder `long_pull` 3/3 (L30) | Engineering tempo x1.15<br>Homebuilder XP x1.05 | **Clockwork Home**: A house that does its own chores. | **Dom na sprężynach**: Dom, który sam wykonuje swoje obowiązki. |
| `field_engineer` | Engineering `chief_engineer` 1/1 (L90)<br>Tactician `quartermaster` 2/3 (L60) | Engineering proc power x1.10<br>Tactician XP x1.05 | **Field Engineer**: Supply lines are machines too. | **Inżynier polowy**: Linie zaopatrzenia to też maszyny. |
| `bounty_road` | Beastslaying `scavenger` 3/3 (L30)<br>Wayfaring `inspired` 2/3 (L60) | Beastslaying XP x1.10<br>Wayfaring proc power x1.10 | **Bounty Road**: Every road has a beast on it, and a reward. | **Szlak nagród**: Na każdej drodze czeka bestia i nagroda. |
| `witching_hour` | Beastslaying `hunters_instinct` 2/3 (L60)<br>Nightwalker `darkborn_bane` 2/3 (L30) | Beastslaying ability time x1.10<br>Nightwalker proc power x1.10 | **Witching Hour**: The night's worst things are the best trophies. | **Godzina wiedźm**: Najgorsze stwory nocy to najlepsze trofea. |
| `pathscout` | Wayfaring `seasoned_traveler` 3/3 (L30)<br>Tactician `quick_call` 5/5 (L10) | Tactician proc chance x1.05<br>Wayfaring XP x1.05 | **Pathscout**: You call the target before the others see the road. | **Zwiadowca**: Wskazujesz cel, zanim inni zobaczą drogę. |
| `glowworm_grotto` | Spelunking `glowstone_heart` 3/3 (L30)<br>Decorating `curator` 2/3 (L60) | Decorating tempo x1.15<br>Spelunking proc power x1.05 | **Glowworm Grotto**: A cave lit with care is a room worth keeping. | **Grota świetlików**: Starannie oświetlona jaskinia to pokój wart zatrzymania. |
| `shoring` | Spelunking `cave_in` 1/1 (L90)<br>Masonry `safety_harness` 2/3 (L30) | Spelunking ability time x1.10<br>Masonry proc power x1.05 | **Shoring**: You prop up the roof before you trust it. | **Obudowa chodnika**: Podpierasz strop, zanim mu zaufasz. |
| `root_cellar` | Spelunking `cave_lore` 3/5 (L10)<br>Homebuilder `night_watch` 1/1 (L90) | Homebuilder ability time x1.10<br>Spelunking XP x1.05 | **Root Cellar**: Every good home has a cave under it. | **Piwniczka**: Pod każdym dobrym domem jest jaskinia. |
| `hearthstone` | Masonry `rapid_build` 5/5 (L10)<br>Homebuilder `ember_heart` 3/3 (L30) | Homebuilder proc power x1.10<br>Masonry tempo x1.15 | **Hearthstone**: You laid the stone the fire sits on. | **Kamień paleniska**: Sam ułożyłeś kamień, na którym płonie ogień. |
| `salon` | Decorating `host` 2/3 (L60)<br>Social `strength_in_numbers` 2/3 (L60) | Social proc chance x1.05<br>Decorating ability time x1.10 | **Salon**: A fine room fills itself with good company. | **Salon**: Piękne wnętrze samo przyciąga dobre towarzystwo. |

## Batch B1: Melee and the fight (8 bespoke)

Files touched:
- common/.../event/TalentMeleeEvents.java (+ forge-1.20.1 copy)
- common/.../event/CombatEvents.java (+ forge copy)
- common/.../event/ChargerEvents.java (+ forge copy)
- common/.../event/CourageEvents.java (+ forge copy)
- common/.../event/GuardianEvents.java: ONE new public method `tauntFor(ServerPlayer, Mob, int ticks)` only (B2 owns the rest of the file; coordinate or let B1 run before B2)
- common/.../gametest/: a new `SynergyMeleeGameTests.java` (+ forge copy) registered where the other GameTest classes are

| id | needs | grants | English | Polish |
|---|---|---|---|---|
| `turn_and_thrust` | Swords `parry` 3/3 (L30)<br>Blocking `counter` 5/5 (L10) | Swords proc chance x1.10<br>Blocking XP x1.10 | **Turn and Thrust**: A sword Parry makes your next sword hit a Perfect Strike. | **Zwód i pchnięcie**: Parada mieczem sprawia, że następny cios mieczem to Idealne cięcie. |
| `single_combat` | Swords `duelist` 1/1 (L90)<br>Courage `challenge` 3/3 (L60) | Swords proc power x1.10<br>Courage XP x1.10 | **Single Combat**: Duelist ignores hostiles that are not after you. | **Pojedynek**: Mistrz pojedynków nie liczy wrogów, którzy nie celują w ciebie. |
| `reavers_rush` | Axes `sunder` 3/3 (L30)<br>Charger `breakthrough` 5/5 (L10) | Axes passive x1.05<br>Charger proc chance x1.10 | **Reaver's Rush**: A charge hit with an axe makes your next axe hit within 3 s a Cleave. | **Rajd łupieżcy**: Trafienie szarżą z toporem: następny cios toporem w 3 s to Rozpłatanie. |
| `falling_hammer` | Maces `meteor` 3/5 (L30)<br>Jumping `stomp` 3/3 (L30) | Maces proc power x1.10<br>Jumping XP x1.10 | **Falling Hammer**: A Shockwave from a 3+ block fall also lands a Stomp around you. | **Spadający młot**: Fala uderzeniowa po upadku z 3+ bloków daje też uderzenie Z buta. |
| `earthbound_oath` | Maces `cratermaker` 1/1 (L90)<br>Guardian `hold_fast` 2/3 (L60) | Guardian XP x1.10<br>Maces ability time x1.10 | **Earthbound Oath**: Every mob a Cratermaker smash hits turns on you for 4 s. | **Przysięga ziemi**: Każdy mob trafiony uderzeniem Twórcy kraterów rzuca się na ciebie na 4 s. |
| `nerve_strike` | Sneaking `backstab` 2/3 (L30)<br>Unarmed `one_inch_punch` 1/1 (L90) | Unarmed proc power x1.10<br>Sneaking XP x1.10 | **Nerve Strike**: A crouched punch from behind numbs: Slowness IV and Weakness for 2 s. | **Cios w nerw**: Cios z przykucnięcia w plecy paraliżuje: Spowolnienie IV i Osłabienie na 2 s. |
| `pavise` | Blocking `thorns` 3/5 (L30)<br>Crossbows `kickback` 3/3 (L30) | Blocking XP x1.10<br>Crossbows proc chance x1.10 | **Pavise**: A hit caught on your shield makes your next crossbow shot a Double Load. | **Pawęż**: Cios przyjęty na tarczę sprawia, że następny strzał z kuszy to Podwójny ładunek. |
| `colossus_bane` | Beastslaying `giant_slayer` 3/3 (L60)<br>Courage `giant_slayer` 3/3 (L30) | Courage XP x1.10<br>Beastslaying passive x1.05 | **Colossus Bane**: Killing a mob with 60+ max health always gives you Rally. | **Pogromca kolosów**: Zabicie moba z 60+ punktami zdrowia zawsze daje ci Zryw. |

### Turn and Thrust (`turn_and_thrust`)

- Trigger: A sword Parry fires (LivingIncomingDamageEvent cancelled by the parry roll).
- Effect: ProcService.forceNext(player, SWORDS): the next sword hit is a Perfect Strike. Not chained: Sword Saint's chain logic is untouched.
- Numbers: No window needed (forceNext lasts until the next sword roll); clear it on logout like CHAIN_UNTIL.
- Hook: TalentMeleeEvents.onPlayerHurt, the parry branch (common line ~302, after setCanceled).
- GameTest: GameTest: player with the synergy, Parry 3 forced (seed rng or call the branch with a stub), zombie hits; then a sword hit on a dummy: assert SkillProcEvent(SWORDS) posted.

### Single Combat (`single_combat`)

- Trigger: Duelist's damage check on a sword hit.
- Effect: hostilesNear() only counts Enemy mobs whose getTarget() is this player, so a duel survives a crowd that is busy with someone else.
- Numbers: Same 8-block radius, same x1.30 (DUELIST_MULTIPLIER).
- Hook: TalentMeleeEvents.damageMultiplier, SWORDS case, and hostilesNear (common line ~92 and ~128): pass a boolean `onlyHunters`.
- GameTest: GameTest: player + zombie A targeting player + zombie B targeting an armour stand/villager; assert damageMultiplier == 1.30 with the synergy and 1.0 without.

### Reaver's Rush (`reavers_rush`)

- Trigger: ChargerEvents.charged(): a charge hit, holding an axe (SkillTools.meleeSkill == AXES).
- Effect: Arm a 60-tick window; the next axe hit inside it calls ProcService.forceNext(AXES) before the roll (a Cleave).
- Numbers: 3 s window, once per charge.
- Hook: ChargerEvents.charged (common line ~533) to arm; TalentMeleeEvents.beforeRoll AXES branch to consume (same batch).
- GameTest: GameTest: call ChargerEvents.payCharge path via a scripted charge (or the public test seam used by ChargerGameTests), then an axe hit; assert a Cleave splash hit the second mob.

### Falling Hammer (`falling_hammer`)

- Trigger: A mace hit that procs (Shockwave) while player.fallDistance >= 3.
- Effect: Call TalentMovementEvents.stomp(player, fallDistance) at the same moment: Stomp's damage to the mob under you plus its particles.
- Numbers: Needs Stomp 3 (6 damage); no extra cooldown, the fall is the cooldown.
- Hook: TalentMeleeEvents.afterHit, MACES case (common line ~180); TalentMovementEvents.stomp is package-static, called not edited.
- GameTest: GameTest: player at fallDistance 4 with forced mace proc hits a zombie; assert the zombie took Shockwave + 6 extra.

### Earthbound Oath (`earthbound_oath`)

- Trigger: A Cratermaker-forced mace proc (beforeRoll forced it) and its splash hits.
- Effect: Every mob the splash touches gets GuardianEvents.taunt(player, mob) with a 4 s lock, so the Guardian-mace pulls a crowd off friends.
- Numbers: 4 s (80 ticks) lock instead of Taunt's 5 s; works without the Taunt node (pass a flag or a new GuardianEvents.tauntFor(player, mob, ticks)).
- Hook: TalentMeleeEvents.onSplashHit (common line ~247) checks a CRATER_HIT thread-local set in beforeRoll; GuardianEvents gets a small public tauntFor overload (B1 owns that one method).
- GameTest: GameTest: two zombies targeting a villager, player with synergy smashes from 6 blocks up; assert both zombies' target is the player.

### Nerve Strike (`nerve_strike`)

- Trigger: An unarmed main hit while crouching, from behind the target.
- Effect: Target gets Slowness IV and Weakness I for 2 s.
- Numbers: 40 ticks each; behind = TalentMovementEvents.behind(target look, attacker offset), the same test Backstab uses.
- Hook: TalentMeleeEvents.afterHit, UNARMED case (common line ~190).
- GameTest: GameTest: crouched player behind a zombie punches it; assert MobEffects.MOVEMENT_SLOWDOWN amplifier 3 on the zombie; from the front, none.

### Pavise (`pavise`)

- Trigger: A hit blocked by your shield (LivingShieldBlockEvent, getBlocked()).
- Effect: ProcService.forceNext(player, CROSSBOWS): the next crossbow shot is a Double Load.
- Numbers: Once per 5 s (map of last arm tick), so a mob tapping your shield cannot queue it forever.
- Hook: CombatEvents.onShieldBlock (common line ~330, next to the juggernaut charge).
- GameTest: GameTest: call onShieldBlock path via a real blocked skeleton arrow (or post the event), then fire a crossbow; assert SkillProcEvent(CROSSBOWS, rolled=true).

### Colossus Bane (`colossus_bane`)

- Trigger: A player kill of a mob with getMaxHealth() >= 60.
- Effect: CourageEvents.rally(player) regardless of odds.
- Numbers: 60 max health (Ravager 100, Iron Golem 100, Warden 500, Elder Guardian 80); no cooldown beyond the kill itself.
- Hook: CourageEvents.onDeath (common line ~271), before the outnumbered gate.
- GameTest: GameTest: spawn a ravager with 1 health left, player kills it alone; assert Rally's buff (the effect rally() applies) is on the player.

## Batch B2: Ranged and protecting (6 bespoke)

Files touched:
- common/.../event/TalentRangedEvents.java (+ forge copy)
- common/.../event/GuardianEvents.java (+ forge copy)
- reads only: TacticianEvents.mark (public), ExpansionEvents.isNotableBoss (package-static), TalentCraftingEvents panacea pick (copy the 5-line choice, do not edit that file)
- common/.../gametest/: new `SynergyRangedGameTests.java` (+ forge copy)

| id | needs | grants | English | Polish |
|---|---|---|---|---|
| `shared_quarry` | Archery `hunters_mark` 3/3 (L30)<br>Tactician `painted_target` 1/1 (L60) | Archery XP x1.10<br>Tactician proc chance x1.10 | **Shared Quarry**: Your Hunter's Mark also puts your Tactician mark on the target. | **Wspólna zwierzyna**: Znak łowcy nakłada też twój znak Taktyki na cel. |
| `hanging_shot` | Archery `skyhunter` 2/3 (L60)<br>Jumping `feather_weight` 3/3 (L30) | Archery passive x1.05<br>Jumping XP x1.10 | **Hanging Shot**: Loosing a full draw in mid-air gives Slow Falling for 1.5 s. | **Strzał w zawieszeniu**: Pełny strzał z łuku w powietrzu daje Powolne opadanie na 1,5 s. |
| `dragonbolt` | Crossbows `ballista` 1/1 (L90)<br>Beastslaying `boss_bane` 1/1 (L90) | Crossbows proc power x1.10<br>Beastslaying XP x1.15 | **Dragonbolt**: A Ballista blast on a boss hits it for 25% more and makes it glow 5 s. | **Smoczy bełt**: Wybuch Balisty na bossie zadaje mu 25% więcej i podświetla go na 5 s. |
| `first_responder` | Running `windwalker` 1/1 (L90)<br>Guardian `watchful_eye` 5/5 (L10) | Guardian XP x1.10<br>Running proc power x1.05 | **First Responder**: Sprinting while a friend within 24 blocks is under 30% health: Speed II. | **Pierwsza pomoc**: Biegniesz, a przyjaciel w promieniu 24 bloków ma poniżej 30% zdrowia: Szybkość II. |
| `lifeguard` | Swimming `current_rider` 3/3 (L30)<br>Guardian `warding` 5/5 (L10) | Swimming XP x1.10<br>Guardian passive x1.05 | **Lifeguard**: A friend within 8 blocks low on air gets Water Breathing 5 s. Once a minute. | **Ratownik**: Przyjaciel w promieniu 8 bloków z brakiem tchu dostaje Oddychanie pod wodą na 5 s. Raz na minutę. |
| `field_medic` | Alchemy `panacea` 1/1 (L90)<br>Guardian `heartshare` 2/3 (L60) | Guardian proc chance x1.10<br>Alchemy XP x1.10 | **Field Medic**: A healing splash you throw also cures one harmful effect on each friend hit. | **Sanitariusz**: Rzucona mikstura leczenia zdejmuje też jeden zły efekt z każdego trafionego przyjaciela. |

### Shared Quarry (`shared_quarry`)

- Trigger: Hunter's Mark lands (Archery proc with the hunters_mark rank, inside TalentRangedEvents.onProc).
- Effect: Also TacticianEvents.mark(serverPlayer, target): the red-team glow, the audience, Painted Target, Focus Fire all apply.
- Numbers: Uses the Tactician mark's own duration; no extra cooldown (the proc is the limiter).
- Hook: TalentRangedEvents.onProc ARCHERY branch (common line ~275).
- GameTest: GameTest: forced Archery proc on a zombie; assert TacticianEvents.liveMark(zombie) is non-null and its marker is the player.

### Hanging Shot (`hanging_shot`)

- Trigger: An arrow loosed from a bow at full draw (isCritArrow) while the player is not on the ground and not in water.
- Effect: Slow Falling for 1.5 s.
- Numbers: 30 ticks, once per airtime (reset on onGround), so it cannot be chained into flight.
- Hook: TalentRangedEvents.onArrowLoosed (common line ~145).
- GameTest: GameTest: player teleported 5 blocks up, spawn a crit arrow owned by them; assert MobEffects.SLOW_FALLING present; second arrow same airtime: duration not refreshed.

### Dragonbolt (`dragonbolt`)

- Trigger: Ballista fires on a target that ExpansionEvents.isNotableBoss() accepts.
- Effect: The target takes 25% of the triggering bolt's damage again as magic damage, and Glowing 5 s.
- Numbers: x0.25 of the bolt's last damage (store it in onArrowDamage), Glowing 100 ticks.
- Hook: TalentRangedEvents.onProc CROSSBOWS branch / ballista (common line ~279 and ~319); isNotableBoss is package-static in ExpansionEvents (called, not edited).
- GameTest: GameTest: forced Crossbows proc on a summoned wither (or a mob tagged notable in the test config); assert Glowing and health drop beyond the base blast.

### First Responder (`first_responder`)

- Trigger: Every 10 ticks: the player is sprinting and GuardianEvents.mostHurtAlly(player, 24) is under 30% health.
- Effect: Speed II for 2 s, refreshed while the conditions hold.
- Numbers: 40 ticks Speed amplifier 1; 24-block radius; 30% threshold.
- Hook: GuardianEvents.onServerTick (common line ~589), next to Lifeguard.
- GameTest: GameTest: ally at 4/20 health 15 blocks away, synergy player set sprinting; tick; assert MOVEMENT_SPEED amplifier 1; ally healed to full, assert it lapses.

### Lifeguard (`lifeguard`)

- Trigger: Every 10 ticks (GuardianEvents.onServerTick already runs at that rate): a friend within 8 blocks with air below a third.
- Effect: Water Breathing 5 s on that friend.
- Numbers: 100 ticks; once per 60 s per lifeguard (per-UUID cooldown map, cleared in forget()).
- Hook: GuardianEvents.onServerTick (common line ~589).
- GameTest: GameTest: two mock players, ally air set to 50 of 300 and submerged; tick 20; assert WATER_BREATHING on the ally; set air low again, assert no second grant inside 60 s.

### Field Medic (`field_medic`)

- Trigger: GuardianEvents.payHeal resolves a healer for an ally hurt by a splash/lingering healing potion.
- Effect: If that healer has the synergy, remove one harmful effect from the ally (same pick as TalentCraftingEvents.panacea: the longest harmful one).
- Numbers: One effect per heal event, at most once per 2 s per ally.
- Hook: GuardianEvents.payHeal (common line ~465), after `healer` is known.
- GameTest: GameTest: ally with Poison, synergy player throws a splash of Healing at them; assert Poison gone and payHeal paid.

## Batch B3: Gathering, food and the proc roll (8 bespoke)

Files touched:
- common/.../event/GatheringEvents.java (+ forge copy)
- common/.../event/TalentGatheringEvents.java (+ forge copy)
- common/.../event/TalentCraftingEvents.java (+ forge copy)
- common/.../skill/ProcService.java (+ forge copy), `situational()` only
- reads only: hearth/HearthRange.isHome (shared with forge via copySharedHearth)
- common/.../gametest/: new `SynergyGatheringGameTests.java` (+ forge copy)

| id | needs | grants | English | Polish |
|---|---|---|---|---|
| `harpooneer` | Tridents `undertow` 3/3 (L10)<br>Fishing `deep_waters` 3/3 (L30) | Tridents proc power x1.10<br>Fishing XP x1.10 | **Harpooneer**: A Riptide Strike on a mob in water can bring up a catch. | **Harpunnik**: Uderzenie fali w moba w wodzie może wyłowić zdobycz. |
| `orchard_keeper` | Woodcutting `sapling_keeper` 3/3 (L10)<br>Farming `fertile_soil` 3/3 (L10) | Woodcutting XP x1.10<br>Farming passive x1.05 | **Orchard Keeper**: A sapling you replant starts one growth stage ahead. | **Sadownik**: Posadzona przez ciebie sadzonka startuje o etap wzrostu dalej. |
| `charcoal_burner` | Woodcutting `sawmill` 1/1 (L90)<br>Smithing `forgefire` 2/3 (L60) | Smithing proc chance x1.05<br>Woodcutting proc power x1.05 | **Charcoal Burner**: A Timber feeds 1 log per 8 felled into a furnace within 4 blocks. | **Węglarz**: Powalenie wrzuca 1 kłodę na 8 ściętych do pieca w promieniu 4 bloków. |
| `dredger` | Excavation `wide_shovel` 1/1 (L30)<br>Swimming `aqua_eyes` 1/1 (L30) | Excavation ability time x1.10<br>Swimming XP x1.10 | **Dredger**: Shovels dig at full speed underwater and while swimming. | **Pogłębiarka**: Łopata kopie pod wodą i w trakcie pływania z pełną prędkością. |
| `moon_garden` | Farming `harvest_festival` 2/3 (L60)<br>Nightwalker `night_owl` 3/3 (L10) | Farming proc power x1.10<br>Nightwalker XP x1.05 | **Moon Garden**: At night, Bountiful Harvest fires half again as often. | **Księżycowy ogród**: Nocą Obfite plony trafiają się o połowę częściej. |
| `shore_supper` | Fishing `fresh_catch` 3/3 (L30)<br>Cooking `banquet` 3/3 (L30) | Fishing proc chance x1.05<br>Cooking XP x1.10 | **Shore Supper**: By a lit campfire, every raw fish comes up cooked and trains Cooking. | **Kolacja na brzegu**: Przy płonącym ognisku każda ryba wychodzi upieczona i uczy Gotowania. |
| `fishing_trip` | Fishing `double_hook` 3/3 (L30)<br>Social `stay_a_while` 3/3 (L30) | Fishing proc chance x1.05<br>Social XP x1.10 | **Fishing Trip**: A friend's Treasure Haul within 16 blocks makes your next one certain. | **Wyprawa na ryby**: Skarb z głębin przyjaciela w promieniu 16 bloków gwarantuje twój następny. |
| `sunday_roast` | Cooking `family_recipe` 1/1 (L60)<br>Homebuilder `warm_welcome` 2/3 (L60)<br>Farming `green_thumb` 1/1 (L30) | Cooking passive x1.05<br>Homebuilder proc chance x1.05<br>Farming passive x1.05 | **Sunday Roast**: Food eaten at home restores 2 more hunger and 2 more saturation. | **Niedzielny obiad**: Jedzenie zjedzone w domu przywraca 2 punkty głodu i 2 nasycenia więcej. |

### Harpooneer (`harpooneer`)

- Trigger: SkillProcEvent for TRIDENTS with a target that isInWater().
- Effect: Roll the vanilla fishing loot table (BuiltInLootTables.FISHING) once at the target's position and drop the result.
- Numbers: 25% chance per proc; one item.
- Hook: New @SubscribeEvent onProc(SkillProcEvent) in TalentGatheringEvents (already registered in platform/Subscribers).
- GameTest: GameTest: drowned in a water block, post SkillProcEvent(TRIDENTS, drowned) 40 times with the synergy; assert at least one ItemEntity from the fishing table nearby.

### Orchard Keeper (`orchard_keeper`)

- Trigger: Sapling Keeper replants a sapling (replantSapling succeeds).
- Effect: Advance the sapling one stage (SaplingBlock.advanceTree once: stage 0 to 1), so the next random tick or bone meal grows it.
- Numbers: One stage, every replant.
- Hook: TalentGatheringEvents.replantSapling (common line ~227).
- GameTest: GameTest: call replantSapling for an oak log position with the synergy (needs a Player arg added or a hasSynergy check by caller); assert the sapling's STAGE property is 1.

### Charcoal Burner (`charcoal_burner`)

- Trigger: A Timber fells a tree.
- Effect: Find a furnace/blast furnace/smoker within 4 blocks of the player; insert floor(logs / 8) logs of the felled type into its fuel slot (only if empty or the same item), taking them out of the drops.
- Numbers: 1 log per 8 felled, at most one stack; radius 4.
- Hook: GatheringEvents.timber (common line ~257; woodsman synergy at ~320 is the model).
- GameTest: GameTest: furnace next to the player, 16-log tree, forced Timber; assert furnace fuel slot holds 2 logs.

### Dredger (`dredger`)

- Trigger: BreakSpeed for a block whose harvest skill is EXCAVATION.
- Effect: Undo vanilla's underwater (x0.2 without Aqua Affinity) and not-on-ground (x0.2) penalties for shovels.
- Numbers: x5 each when the condition applies; never above the dry on-ground speed.
- Hook: GatheringEvents.onBreakSpeed (common line ~92).
- GameTest: GameTest: submerged player, sand block, compute speed via the event with and without the synergy; assert ratio 5 (or 25 when also floating).

### Moon Garden (`moon_garden`)

- Trigger: Farming's proc roll at night (level.isNight()).
- Effect: Proc chance x1.5, read in ProcService.situational like tidecaller and deep_delver.
- Numbers: x1.5 at night only.
- Hook: ProcService.situational (common line ~131).
- GameTest: Unit-ish GameTest: set dayTime 18000, assert ProcService.situational(player, FARMING) == 1.5; at noon 1.0.

### Shore Supper (`shore_supper`)

- Trigger: A catch while a lit campfire is within 3 blocks of the player.
- Effect: Fresh Catch always succeeds (every raw fish cooked), and the catch also grants 1 Cooking XP.
- Numbers: Radius 3 (same as Campfire Tales); 1.0 Cooking XP per catch with a cooked fish.
- Hook: TalentGatheringEvents.freshCatch (common line ~528) for the forced cook; GatheringEvents.onFished (~589) for the Cooking grant.
- GameTest: GameTest: lit campfire next to the player, call freshCatch with a raw cod list; assert cooked cod and Cooking XP > 0.

### Fishing Trip (`fishing_trip`)

- Trigger: SkillProcEvent for FISHING (Treasure Haul) by any player.
- Effect: Every other player within 16 blocks who has the synergy gets their next Fishing roll forced (ProcService.forceNext).
- Numbers: 16 blocks; the forced flag expires after 60 s (keep a until-tick map, drop it if stale before forcing).
- Hook: New onProc(SkillProcEvent) in TalentGatheringEvents (same listener as Harpooneer).
- GameTest: GameTest: two mock players 5 blocks apart, B has the synergy; post SkillProcEvent(FISHING) for A; then ProcService.fire(B, FISHING) returns true.

### Sunday Roast (`sunday_roast`)

- Trigger: Finishing eating a food item while HearthRange.isHome(player).
- Effect: player.getFoodData().eat(2, 0.5f) extra (2 hunger, +2 saturation).
- Numbers: +2 hunger, +2 saturation per meal; no cap beyond vanilla's.
- Hook: TalentCraftingEvents.onFinishUsing food path (common line ~305, next to comfort/shared_meal).
- GameTest: GameTest: place a Hearth (HearthGameTests has the setup), player at hunger 10 eats bread at home; assert hunger 17 instead of 15.

## Batch B4: Movement, discovery and building (7 bespoke)

Files touched:
- common/.../event/TalentEnduranceEvents.java (+ forge copy)
- common/.../event/MovementEvents.java (+ forge copy)
- common/.../event/TalentMovementEvents.java (+ forge copy)
- common/.../event/TalentExpansionEvents.java (+ forge copy)
- common/.../event/ExpansionEvents.java (+ forge copy)
- core/src/main/java/dev/amman/proficiency/event/TalentMath.java: the Seismic Survey interval/radius helper (B4 only)
- common/.../gametest/: new `SynergyMovementGameTests.java` (+ forge copy)

| id | needs | grants | English | Polish |
|---|---|---|---|---|
| `play_dead` | Sneaking `shadowmeld` 2/3 (L60)<br>Endurance `last_stand` 1/1 (L90) | Sneaking proc power x1.10<br>Endurance XP x1.10 | **Play Dead**: A Grit while crouching: Invisibility for 3 s and nearby mobs lose you. | **Udawanie trupa**: Zawziętość w przykucnięciu: 3 s niewidzialności, a moby w pobliżu cię gubią. |
| `ranger` | Archery `quiver` 3/3 (L10)<br>Wayfaring `pathfinder` 3/3 (L30) | Archery XP x1.05<br>Wayfaring proc chance x1.10 | **Ranger**: Each new biome you discover puts 4 arrows in your quiver. | **Strażnik dróg**: Każdy nowo odkryty biom dokłada 4 strzały do kołczanu. |
| `seismic_survey` | Mining `geologist` 3/3 (L10)<br>Spelunking `echolocation` 3/3 (L30)<br>Excavation `archaeologist` 3/3 (L30) | Mining proc chance x1.05<br>Spelunking XP x1.10<br>Excavation proc chance x1.05 | **Seismic Survey**: Echolocation pulses every 5 s instead of 10 and reaches 2 blocks further. | **Badanie sejsmiczne**: Echolokacja działa co 5 s zamiast co 10 i sięga 2 bloki dalej. |
| `freerunner` | Running `parkour` 1/1 (L30)<br>Jumping `bounce` 1/1 (L60) | Running passive x1.05<br>Jumping proc chance x1.10 | **Freerunner**: Landing a 4+ block fall while sprinting keeps the sprint and gives Speed II 2 s. | **Wolny biegacz**: Lądując w biegu po upadku z 4+ bloków, biegniesz dalej z Szybkością II na 2 s. |
| `high_diver` | Jumping `safe_landing` 5/5 (L10)<br>Swimming `deep_lungs` 3/3 (L30) | Jumping XP x1.05<br>Swimming passive x1.05 | **High Diver**: Falling 8+ blocks into water gives Dolphin's Grace for 6 s. | **Skoczek do wody**: Skok do wody z 8+ bloków daje Grację delfina na 6 s. |
| `steeplejack` | Jumping `cat_reflexes` 2/3 (L60)<br>Masonry `safety_harness` 3/3 (L30) | Masonry XP x1.10<br>Jumping ability time x1.10 | **Steeplejack**: A block you place while falling 3+ blocks stops the fall. Once per 5 s. | **Dekarz**: Blok postawiony w trakcie spadania z 3+ bloków przerywa upadek. Raz na 5 s. |
| `housewarming` | Decorating `hygge` 3/3 (L30)<br>Homebuilder `light_step` 5/5 (L10) | Decorating XP x1.05<br>Homebuilder passive x1.05 | **Housewarming**: A cozy room at home also feeds you: 1 hunger every 30 s. | **Parapetówka**: Przytulny pokój w domu także cię karmi: 1 punkt głodu co 30 s. |

### Play Dead (`play_dead`)

- Trigger: SkillProcEvent for ENDURANCE (Grit) while player.isCrouching().
- Effect: Invisibility 3 s, and every Mob within 12 blocks whose target is the player gets setTarget(null).
- Numbers: 60 ticks; 12 blocks; once per 30 s.
- Hook: New onProc(SkillProcEvent) in TalentEnduranceEvents (registered already).
- GameTest: GameTest: crouched player, zombie targeting them; post SkillProcEvent(ENDURANCE); assert INVISIBILITY and zombie.getTarget() == null.

### Ranger (`ranger`)

- Trigger: TalentExpansionEvents.onNewBiome (a biome visited for the first time).
- Effect: Give 4 arrows (placeItemBackInInventory, drops if full).
- Numbers: 4 arrows per new biome; nothing on revisits.
- Hook: TalentExpansionEvents.onNewBiome (common line ~310).
- GameTest: GameTest: call onNewBiome(player) with the synergy; assert inventory gained 4 Items.ARROW.

### Seismic Survey (`seismic_survey`)

- Trigger: Echolocation's timer.
- Effect: Interval 100 ticks instead of 200, and the reveal radius 3 + rank + 2.
- Numbers: 5 s; +2 blocks (13 cubed becomes 17 cubed at rank 3, still bounded by revealNearbyOre's cap).
- Hook: TalentExpansionEvents.onPlayerTick (common line ~126, the tickCount % 200 gate) and echolocation (~296).
- GameTest: Core unit test of the interval/radius helper (move both numbers into TalentMath); GameTest: underground player, ore 7 blocks away, tick 100; assert GLOW particles sent (or revealNearbyOre call count via a test hook).

### Freerunner (`freerunner`)

- Trigger: LivingFallEvent with fallen >= 4 while the player isSprinting().
- Effect: Keep sprinting (setSprinting(true)) and Speed II for 2 s.
- Numbers: 4 blocks; 40 ticks amplifier 1.
- Hook: MovementEvents.onFall (common line ~249).
- GameTest: GameTest: sprinting player dropped 5 blocks; assert MOVEMENT_SPEED amplifier 1 after landing and isSprinting().

### High Diver (`high_diver`)

- Trigger: Server tick: the player enters water while the previous tick's fallDistance was >= 8.
- Effect: Dolphin's Grace 6 s.
- Numbers: 8 blocks; 120 ticks; once per 10 s.
- Hook: TalentMovementEvents.onPlayerTick (common line ~111): remember last fallDistance per UUID, check in swimming() at ~197.
- GameTest: GameTest: player 10 blocks above a 3-deep pool, let it fall; assert DOLPHINS_GRACE.

### Steeplejack (`steeplejack`)

- Trigger: The player places a block while fallDistance >= 3.
- Effect: player.resetFallDistance() and a short scaffold sound.
- Numbers: 3 blocks; once per 5 s (per-UUID last tick).
- Hook: ExpansionEvents.onBlockPlaced (common line ~319), before the survival pay path.
- GameTest: GameTest: mock player with fallDistance 10 places a block via the event; assert fallDistance == 0; again within 5 s: unchanged.

### Housewarming (`housewarming`)

- Trigger: A cozy-room warm pulse (ExpansionEvents.cozyRoom reaches warm()) while HearthRange.isHome(player).
- Effect: Every 6th warm pulse, Saturation I for 1 tick (1 hunger, 2 saturation) on the warmed player only, not on guests.
- Numbers: COZY_TICKS is 100 (5 s, ExpansionEvents line ~215), so every 6th pulse is 30 s: 2 hunger a minute, below a walking player's drain. Per-UUID pulse counter.
- Hook: ExpansionEvents.cozyRoom (common line ~157; called from onPlayerTick every COZY_TICKS at ~499).
- GameTest: GameTest: Hearth + 5 decorative blocks around a player at hunger 10; run cozyRoom 6 times (package seam); assert hunger 11, and 10 after 5 runs.

## Per-skill count (as a need)

| skill | existing | new | total | of which bespoke (new) |
|---|---:|---:|---:|---:|
| swords | 1 | 8 | **9** | 2 |
| axes | 1 | 8 | **9** | 1 |
| maces | 1 | 8 | **9** | 2 |
| tridents | 1 | 8 | **9** | 1 |
| unarmed | 1 | 8 | **9** | 1 |
| blocking | 3 | 6 | **9** | 2 |
| endurance | 2 | 7 | **9** | 1 |
| archery | 2 | 7 | **9** | 3 |
| crossbows | 1 | 8 | **9** | 2 |
| mining | 2 | 7 | **9** | 1 |
| woodcutting | 2 | 7 | **9** | 2 |
| excavation | 1 | 8 | **9** | 2 |
| farming | 1 | 8 | **9** | 3 |
| fishing | 1 | 9 | **10** | 3 |
| running | 2 | 7 | **9** | 2 |
| sneaking | 2 | 7 | **9** | 2 |
| jumping | 1 | 9 | **10** | 5 |
| swimming | 1 | 8 | **9** | 3 |
| smithing | 1 | 9 | **10** | 1 |
| cooking | 2 | 7 | **9** | 2 |
| alchemy | 1 | 8 | **9** | 1 |
| spellcasting | 1 | 8 | **9** | 0 |
| engineering | 2 | 7 | **9** | 0 |
| beastslaying | 1 | 8 | **9** | 2 |
| wayfaring | 2 | 8 | **10** | 1 |
| spelunking | 1 | 8 | **9** | 1 |
| masonry | 2 | 7 | **9** | 1 |
| decorating | 1 | 8 | **9** | 1 |
| social | 2 | 7 | **9** | 1 |
| nightwalker | 1 | 8 | **9** | 1 |
| courage | 2 | 8 | **10** | 2 |
| guardian | 4 | 6 | **10** | 4 |
| charger | 2 | 8 | **10** | 1 |
| tactician | 2 | 7 | **9** | 1 |
| homebuilder | 1 | 8 | **9** | 2 |
| **all** | 54 | 268 | 322 | |

Spellcasting and Engineering have no bespoke synergy on purpose: their XP comes only from optional mods, so a
behaviour hung on them could not be GameTested in the plain test world. They get numeric ones only.

## UI impact (what the code does today)

`TalentTreeScreen` (common and the forge copy) lists `Synergies.involving(skill)` in the side column right of the
grid: every synergy that names the skill in a need or a grant, plus Renaissance (its grant has `skill == null`).
With this spec that is 10 or 11 lines per tree, up from 2 to 5.

- Layout: the column starts level with the first node row (`synergyTop()`), draws the "Synergies" header (11 px),
  the Discipline line (1 or 2 lines of 10 px) plus a 6 px gap, then one 12 px row per synergy (`SYNERGY_ROW = 12`).
  `drawSynergies` never clips at the bottom. The space it has is the grid height, `5*rowStep + node` from
  `GEOMETRIES`: 194, 172, 150 or 138 px. After the header that fits about 13, 12, 10 or 9 rows.
  At the two smaller geometries (picked on small windows or high GUI scale) 11 rows run past the grid into the
  footer text.
- The recent-XP list (`drawXpLog`) starts at `synergiesBottom + LOG_GAP` and returns early when there is no room
  for its header plus one 10 px row above the grid bottom. With 10 or more synergies it disappears on nearly every
  window size, so the "did that just pay" answer is lost.
- `layout()` widens the side column for the longest synergy name, up to `SIDE_WANTED_MAX = 230`, and squeezes it
  to `SIDE_MIN = 120` on narrow windows. Long Polish names such as "Skoczek nad przepaścią" will then go through
  `TextFit.clip`; the hover tooltip still shows the whole name.
- Node tooltips (`TalentTreeScreen` ~line 963) add one "feeds <synergy>" line per synergy the node is a need of,
  looping `Synergies.all()`. Several nodes now feed 2 or 3 synergies (for example `swords.parry`, `blocking.shield_bash`,
  `courage.giant_slayer`), so those tooltips grow by that many lines.
- `synergyPoint(index)` (LayoutDemo hover seam) assumes rows are at fixed 12 px offsets from the top; any scrolling
  must keep that seam meaningful or LayoutDemo's "tree synergy hover" and "tip synergy" shots break.
- `/skills synergies` (`SkillCommand.synergies`, ~line 335) prints every synergy with its needs: about 157 entries
  and 450+ chat lines after this spec. It needs a skill filter or an "active and partly met only" default.
- `UnlockFx` is sized by `synergies.size()` per tree, so it adapts; `SkillsScreen` only counts awake ones (~line 627).

Suggested UI change (its own task, needs a LayoutDemo pass at SETUPS 0 and 4 in en_us and pl_pl):

1. Compact rows: 10 px instead of 12, and sort awake first, then by parts met (`2/3` before `0/2`), then by name.
2. Clip the list to the rows that fit and scroll the column with the mouse wheel when hovered, with a dim
   "+N more" line as the last visible row. Keep `synergyPoint(i)` returning the on-screen row of the i-th *visible* entry.
3. Give the XP log a guaranteed 3 rows: cap the synergy list at `(gridH - header - 3 log rows)` rather than letting it
   push the log out.
4. In node tooltips, show at most 2 "feeds" lines and "and N more" after that (Shift shows all, as other details do).
5. `/skills synergies [skill]`, defaulting to awake and partly met only.

## Tests to add

Core (`core/src/test/.../SynergiesTest.java`, Batch A):
- every skill is a need in 9 to 11 synergies (replaces the weaker `everyTreeFeedsSomePairing`);
- no two synergies share a need set;
- no grant goes to a skill that is not a need of that synergy, and every multiplier is within 1.05 to 1.20;
- no synergy grants `DEATH_WARD`;
- with every synergy active, `Synergies.multiplier` for any skill and effect stays below 1.75 (today's max is about 1.46).

Each B batch: one GameTest per bespoke synergy as described in its section, each with a control case (same setup
without the synergy shows no effect). Update `theSpecialsTheEventCodeReadsExist` with the new tags as they land.

## Uncertain, check while building

- **Earthbound Oath** needs a small public `GuardianEvents.tauntFor` overload because `taunt()` returns early
  without the Taunt node. That one method touches B2's file; run B1 first or have B2 add it.
- **Reaver's Rush**: `charged()` runs after the hit's own axe roll, so the forced Cleave lands on the *next* axe hit.
  The 3 s window is new state; clear it in `ChargerEvents.forget`.
- **Harpooneer** rolls `BuiltInLootTables.FISHING` without a fishing hook entity; the loot context needs ORIGIN and
  TOOL params. On forge 1.20.1 the LootParams builder differs; copy what `sunkenTreasure` or `TREASURE` already do if
  the context is awkward and pick from that list instead.
- **High Diver** relies on the previous tick's `fallDistance`; vanilla resets it on water contact in the same tick,
  so the per-UUID memory must be written at the end of the tick handler.
- **Housewarming**: 2 hunger a minute is a guess at "you never starve at home, you still cook". Tune after a play test.
- **Dredger** must not exceed the dry, on-ground speed when the player already has Aqua Affinity (no double undo).
- **Single Combat** changes what `hostilesNear` counts only for players with the synergy; the Duelist talent
  text stays true for everyone else.
- Polish names were written for natural reading, not checked by a native player in game; `Skoczek nad przepaścią`
  and `Ucieczka rzezimieszka` are the longest and may clip in the side column.

## Appendix: Synergies.java lines (Batch A pastes these)

```java
add("turn_and_thrust", needs(SWORDS, "parry", 3, BLOCKING, "counter", 5),
        grants(SWORDS, PROC_CHANCE, 1.10, BLOCKING, XP_RATE, 1.10), null /* B1:turn_and_thrust */);
add("cavalry_blade", needs(SWORDS, "keen_edge", 5, CHARGER, "first_in", 5),
        grants(SWORDS, BONUS, 1.10, CHARGER, XP_RATE, 1.10), null);
add("bloodied_steel", needs(SWORDS, "vampiric_edge", 3, ENDURANCE, "adrenal_mend", 3),
        grants(SWORDS, XP_RATE, 1.10, ENDURANCE, XP_RATE, 1.10), null);
add("single_combat", needs(SWORDS, "duelist", 1, COURAGE, "challenge", 3),
        grants(SWORDS, PROC_POWER, 1.10, COURAGE, XP_RATE, 1.10), null /* B1:single_combat */);
add("quiet_blade", needs(SNEAKING, "backstab", 3, SWORDS, "opening", 5),
        grants(SWORDS, PROC_CHANCE, 1.10, SNEAKING, BONUS, 1.05), null);
add("bladesmith", needs(SMITHING, "weaponwright", 1, SWORDS, "keen_edge", 3),
        grants(SWORDS, BONUS, 1.05, SMITHING, XP_RATE, 1.05), null);
add("moonblade", needs(SWORDS, "hemorrhage", 3, NIGHTWALKER, "silver_light", 3),
        grants(SWORDS, PROC_POWER, 1.10, NIGHTWALKER, PROC_CHANCE, 1.10), null);
add("armsmaster", needs(SWORDS, "battle_trance", 2, AXES, "rampage", 2, MACES, "bell_ringer", 2),
        grants(SWORDS, ABILITY_DURATION, 1.10, AXES, ABILITY_DURATION, 1.10, MACES, ABILITY_DURATION, 1.10), null);
add("berserkergang", needs(AXES, "berserker", 3, ENDURANCE, "scar_tissue", 3),
        grants(AXES, PROC_POWER, 1.10, ENDURANCE, BONUS, 1.05), null);
add("red_mist", needs(AXES, "bloodrush", 3, COURAGE, "hot_blood", 5),
        grants(AXES, PROC_CHANCE, 1.10, COURAGE, BONUS, 1.10), null);
add("reavers_rush", needs(AXES, "sunder", 3, CHARGER, "breakthrough", 5),
        grants(AXES, BONUS, 1.05, CHARGER, PROC_CHANCE, 1.10), null /* B1:reavers_rush */);
add("shieldbreaker", needs(AXES, "heavy_blows", 5, BLOCKING, "shield_bash", 3),
        grants(AXES, XP_RATE, 1.10, BLOCKING, PROC_CHANCE, 1.10), null);
add("headtaker", needs(AXES, "headsman", 3, BEASTSLAYING, "trophy_hunter", 5),
        grants(AXES, PROC_POWER, 1.10, BEASTSLAYING, PROC_CHANCE, 1.10), null);
add("wolfs_hour", needs(AXES, "bloodlust", 5, NIGHTWALKER, "moonrise", 5),
        grants(AXES, PROC_CHANCE, 1.05, NIGHTWALKER, XP_RATE, 1.10), null);
add("raider", needs(AXES, "bloodrush", 2, RUNNING, "adrenaline", 3),
        grants(AXES, XP_RATE, 1.10, RUNNING, PROC_POWER, 1.10), null);
add("falling_hammer", needs(MACES, "meteor", 3, JUMPING, "stomp", 3),
        grants(MACES, PROC_POWER, 1.10, JUMPING, XP_RATE, 1.10), null /* B1:falling_hammer */);
add("anvil_body", needs(MACES, "crushing", 5, ENDURANCE, "iron_constitution", 5),
        grants(MACES, BONUS, 1.05, ENDURANCE, ABILITY_DURATION, 1.10), null);
add("quarry_hammer", needs(MACES, "tremor", 5, MINING, "efficiency", 3),
        grants(MACES, PROC_CHANCE, 1.10, MINING, XP_RATE, 1.10), null);
add("battering_ram", needs(MACES, "concussion", 3, CHARGER, "crash_in", 3),
        grants(MACES, XP_RATE, 1.10, CHARGER, PROC_POWER, 1.10), null);
add("war_bell", needs(MACES, "bell_ringer", 3, COURAGE, "war_drums", 3),
        grants(MACES, PROC_POWER, 1.05, COURAGE, PROC_CHANCE, 1.10), null);
add("smiths_maul", needs(MACES, "aftershock", 3, SMITHING, "temper", 3),
        grants(MACES, XP_RATE, 1.10, SMITHING, BONUS, 1.10), null);
add("earthbound_oath", needs(MACES, "cratermaker", 1, GUARDIAN, "hold_fast", 2),
        grants(GUARDIAN, XP_RATE, 1.10, MACES, ABILITY_DURATION, 1.10), null /* B1:earthbound_oath */);
add("harpooneer", needs(TRIDENTS, "undertow", 3, FISHING, "deep_waters", 3),
        grants(TRIDENTS, PROC_POWER, 1.10, FISHING, XP_RATE, 1.10), null /* B3:harpooneer */);
add("undercurrent", needs(TRIDENTS, "riptide_surge", 2, SWIMMING, "riptide_kick", 2),
        grants(TRIDENTS, ABILITY_DURATION, 1.10, SWIMMING, ABILITY_DURATION, 1.10), null);
add("stormcaller", needs(TRIDENTS, "conductor", 1, SPELLCASTING, "overcharge", 3),
        grants(TRIDENTS, PROC_CHANCE, 1.10, SPELLCASTING, PROC_POWER, 1.10), null);
add("sea_wolf", needs(TRIDENTS, "impaler", 3, COURAGE, "unshaken", 3),
        grants(TRIDENTS, XP_RATE, 1.10, COURAGE, BONUS, 1.05), null);
add("deep_sea_slayer", needs(TRIDENTS, "skewer", 3, BEASTSLAYING, "giant_slayer", 2),
        grants(TRIDENTS, PROC_POWER, 1.05, BEASTSLAYING, BONUS, 1.10), null);
add("boarding_party", needs(TRIDENTS, "true_aim", 5, CHARGER, "head_start", 3),
        grants(TRIDENTS, BONUS, 1.05, CHARGER, XP_RATE, 1.10), null);
add("spear_volley", needs(TRIDENTS, "tidal_edge", 3, TACTICIAN, "spotter", 5),
        grants(TACTICIAN, BONUS, 1.10, TRIDENTS, XP_RATE, 1.10), null);
add("mariner", needs(TRIDENTS, "deep_breath", 1, WAYFARING, "trailwise", 5, FISHING, "rain_dancer", 2),
        grants(TRIDENTS, XP_RATE, 1.05, WAYFARING, BONUS, 1.10, FISHING, XP_RATE, 1.05), null);
add("iron_monk", needs(UNARMED, "iron_skin", 3, ENDURANCE, "roll_with_it", 3),
        grants(UNARMED, BONUS, 1.05, ENDURANCE, PROC_CHANCE, 1.10), null);
add("nerve_strike", needs(SNEAKING, "backstab", 2, UNARMED, "one_inch_punch", 1),
        grants(UNARMED, PROC_POWER, 1.10, SNEAKING, XP_RATE, 1.10), null /* B1:nerve_strike */);
add("flying_kick", needs(UNARMED, "haymaker", 5, JUMPING, "leap", 3),
        grants(UNARMED, PROC_CHANCE, 1.10, JUMPING, BONUS, 1.05), null);
add("tavern_hero", needs(UNARMED, "combo", 3, COURAGE, "battle_cry", 5),
        grants(UNARMED, TEMPO, 1.15, COURAGE, XP_RATE, 1.10), null);
add("kitchen_brawler", needs(UNARMED, "iron_fist", 5, COOKING, "hearty", 3),
        grants(UNARMED, XP_RATE, 1.10, COOKING, BONUS, 1.05), null);
add("drunken_fist", needs(UNARMED, "stagger", 3, ALCHEMY, "strong_brew", 3),
        grants(UNARMED, PROC_CHANCE, 1.05, ALCHEMY, XP_RATE, 1.10), null);
add("open_palm", needs(UNARMED, "dragon_palm", 3, GUARDIAN, "mending_guard", 3),
        grants(GUARDIAN, BONUS, 1.05, UNARMED, PROC_POWER, 1.10), null);
add("shoulder_charge", needs(UNARMED, "iron_fist", 3, CHARGER, "crash_in", 2),
        grants(UNARMED, BONUS, 1.05, CHARGER, XP_RATE, 1.05), null);
add("pavise", needs(BLOCKING, "thorns", 3, CROSSBOWS, "kickback", 3),
        grants(BLOCKING, XP_RATE, 1.10, CROSSBOWS, PROC_CHANCE, 1.10), null /* B1:pavise */);
add("unbroken_line", needs(BLOCKING, "tenacity", 3, COURAGE, "unyielding", 2),
        grants(BLOCKING, BONUS, 1.05, COURAGE, ABILITY_DURATION, 1.10), null);
add("shieldwright", needs(BLOCKING, "spiked_rim", 2, SMITHING, "whetstone", 3),
        grants(BLOCKING, PROC_POWER, 1.10, SMITHING, XP_RATE, 1.10), null);
add("shield_rush", needs(BLOCKING, "shield_bash", 2, CHARGER, "trust_the_line", 3),
        grants(BLOCKING, PROC_CHANCE, 1.05, CHARGER, BONUS, 1.05), null);
add("hardtack", needs(COOKING, "comfort_food", 3, ENDURANCE, "lean_times", 2),
        grants(ENDURANCE, XP_RATE, 1.05, COOKING, PROC_CHANCE, 1.05), null);
add("marathoner", needs(RUNNING, "endurance", 3, ENDURANCE, "deep_reserves", 3),
        grants(RUNNING, BONUS, 1.05, ENDURANCE, PROC_POWER, 1.10), null);
add("play_dead", needs(SNEAKING, "shadowmeld", 2, ENDURANCE, "last_stand", 1),
        grants(SNEAKING, PROC_POWER, 1.10, ENDURANCE, XP_RATE, 1.10), null /* B4:play_dead */);
add("moonshot", needs(ARCHERY, "eagle_eye", 5, NIGHTWALKER, "far_sight", 3),
        grants(ARCHERY, PROC_CHANCE, 1.05, NIGHTWALKER, BONUS, 1.10), null);
add("shared_quarry", needs(ARCHERY, "hunters_mark", 3, TACTICIAN, "painted_target", 1),
        grants(ARCHERY, XP_RATE, 1.10, TACTICIAN, PROC_CHANCE, 1.10), null /* B2:shared_quarry */);
add("hanging_shot", needs(ARCHERY, "skyhunter", 2, JUMPING, "feather_weight", 3),
        grants(ARCHERY, BONUS, 1.05, JUMPING, XP_RATE, 1.10), null /* B2:hanging_shot */);
add("big_game_hunter", needs(ARCHERY, "longshot", 3, BEASTSLAYING, "monster_lore", 5),
        grants(ARCHERY, PROC_POWER, 1.10, BEASTSLAYING, XP_RATE, 1.10), null);
add("ranger", needs(ARCHERY, "quiver", 3, WAYFARING, "pathfinder", 3),
        grants(ARCHERY, XP_RATE, 1.05, WAYFARING, PROC_CHANCE, 1.10), null /* B4:ranger */);
add("twin_strings", needs(ARCHERY, "rain_of_arrows", 2, CROSSBOWS, "hair_trigger", 5),
        grants(ARCHERY, ABILITY_DURATION, 1.10, CROSSBOWS, PROC_CHANCE, 1.05), null);
add("arcane_arrow", needs(ARCHERY, "deadeye", 1, SPELLCASTING, "arcane_focus", 5),
        grants(ARCHERY, PROC_POWER, 1.05, SPELLCASTING, XP_RATE, 1.10), null);
add("boltwright", needs(SMITHING, "masterwork", 5, CROSSBOWS, "armor_piercer", 3),
        grants(CROSSBOWS, PROC_POWER, 1.10, SMITHING, PROC_CHANCE, 1.05), null);
add("firing_line", needs(CROSSBOWS, "siege_crew", 2, TACTICIAN, "suppression", 2),
        grants(CROSSBOWS, XP_RATE, 1.10, TACTICIAN, ABILITY_DURATION, 1.10), null);
add("arrow_slit", needs(CROSSBOWS, "quickload", 2, MASONRY, "cornerstone", 2, TACTICIAN, "headshot", 3),
        grants(CROSSBOWS, BONUS, 1.05, MASONRY, XP_RATE, 1.10, TACTICIAN, PROC_POWER, 1.10), null);
add("snipers_nest", needs(SNEAKING, "unseen", 3, CROSSBOWS, "salvage", 3),
        grants(CROSSBOWS, XP_RATE, 1.05, SNEAKING, PROC_CHANCE, 1.10), null);
add("sentry_post", needs(CROSSBOWS, "armor_piercer", 2, GUARDIAN, "taunt", 1),
        grants(GUARDIAN, PROC_CHANCE, 1.05, CROSSBOWS, BONUS, 1.05), null);
add("dragonbolt", needs(CROSSBOWS, "ballista", 1, BEASTSLAYING, "boss_bane", 1),
        grants(CROSSBOWS, PROC_POWER, 1.10, BEASTSLAYING, XP_RATE, 1.15), null /* B2:dragonbolt */);
add("tunneler", needs(MINING, "vein_miner", 3, EXCAVATION, "mole", 2),
        grants(MINING, BONUS, 1.05, EXCAVATION, XP_RATE, 1.10), null);
add("steam_drill", needs(MINING, "rich_veins", 2, ENGINEERING, "overclock", 3),
        grants(MINING, PROC_POWER, 1.10, ENGINEERING, XP_RATE, 1.10), null);
add("quarryman", needs(MINING, "prospector", 5, MASONRY, "stonecutter", 3),
        grants(MINING, XP_RATE, 1.10, MASONRY, PROC_POWER, 1.10), null);
add("mineral_tincture", needs(MINING, "gem_cutter", 3, ALCHEMY, "brewers_eye", 3),
        grants(MINING, PROC_POWER, 1.05, ALCHEMY, BONUS, 1.10), null);
add("lightless_seam", needs(MINING, "dark_sight", 1, NIGHTWALKER, "dark_adapted", 5),
        grants(MINING, XP_RATE, 1.05, NIGHTWALKER, XP_RATE, 1.10), null);
add("seismic_survey", needs(MINING, "geologist", 3, SPELUNKING, "echolocation", 3, EXCAVATION, "archaeologist", 3),
        grants(MINING, PROC_CHANCE, 1.05, SPELUNKING, XP_RATE, 1.10, EXCAVATION, PROC_CHANCE, 1.05), null /* B4:seismic_survey */);
add("orchard_keeper", needs(WOODCUTTING, "sapling_keeper", 3, FARMING, "fertile_soil", 3),
        grants(WOODCUTTING, XP_RATE, 1.10, FARMING, BONUS, 1.05), null /* B3:orchard_keeper */);
add("charcoal_burner", needs(WOODCUTTING, "sawmill", 1, SMITHING, "forgefire", 2),
        grants(SMITHING, PROC_CHANCE, 1.05, WOODCUTTING, PROC_POWER, 1.05), null /* B3:charcoal_burner */);
add("joinery", needs(WOODCUTTING, "heartwood", 3, DECORATING, "thrift_shop", 5),
        grants(WOODCUTTING, BONUS, 1.05, DECORATING, XP_RATE, 1.10), null);
add("greenway", needs(WOODCUTTING, "woodland_stride", 2, WAYFARING, "fresh_air", 3),
        grants(WAYFARING, PROC_CHANCE, 1.05, WOODCUTTING, ABILITY_DURATION, 1.10), null);
add("full_woodpile", needs(WOODCUTTING, "seasoned", 2, HOMEBUILDER, "kindling", 5),
        grants(HOMEBUILDER, PROC_CHANCE, 1.10, WOODCUTTING, XP_RATE, 1.05), null);
add("smokehouse", needs(WOODCUTTING, "forager", 3, COOKING, "camp_kitchen", 1),
        grants(COOKING, XP_RATE, 1.10, WOODCUTTING, PROC_CHANCE, 1.05), null);
add("timber_frame", needs(WOODCUTTING, "canopy", 1, MASONRY, "long_arm", 3),
        grants(MASONRY, BONUS, 1.05, WOODCUTTING, PROC_POWER, 1.05), null);
add("topsoil", needs(EXCAVATION, "sifter", 3, FARMING, "seed_saver", 3),
        grants(EXCAVATION, PROC_POWER, 1.10, FARMING, XP_RATE, 1.10), null);
add("potters_clay", needs(EXCAVATION, "quick_dig", 5, DECORATING, "inspiration", 5),
        grants(EXCAVATION, XP_RATE, 1.10, DECORATING, PROC_CHANCE, 1.10), null);
add("groundwork", needs(EXCAVATION, "earthmover", 2, HOMEBUILDER, "scaffolder", 3),
        grants(EXCAVATION, BONUS, 1.05, HOMEBUILDER, XP_RATE, 1.10), null);
add("deep_strata", needs(EXCAVATION, "fossil_hunter", 1, SPELUNKING, "deep_dweller", 3),
        grants(EXCAVATION, XP_RATE, 1.10, SPELUNKING, BONUS, 1.10), null);
add("earthworks", needs(EXCAVATION, "landslide_lore", 5, ENGINEERING, "wrench_wizard", 3),
        grants(EXCAVATION, PROC_CHANCE, 1.10, ENGINEERING, BONUS, 1.05), null);
add("dredger", needs(EXCAVATION, "wide_shovel", 1, SWIMMING, "aqua_eyes", 1),
        grants(EXCAVATION, ABILITY_DURATION, 1.10, SWIMMING, XP_RATE, 1.10), null /* B3:dredger */);
add("herbalist", needs(FARMING, "harvest_moon", 5, ALCHEMY, "catalyst", 3),
        grants(ALCHEMY, XP_RATE, 1.10, FARMING, PROC_CHANCE, 1.05), null);
add("barn_raising", needs(FARMING, "husbandry", 3, SOCIAL, "kinship", 2),
        grants(FARMING, XP_RATE, 1.10, SOCIAL, BONUS, 1.05), null);
add("moon_garden", needs(FARMING, "harvest_festival", 2, NIGHTWALKER, "night_owl", 3),
        grants(FARMING, PROC_POWER, 1.10, NIGHTWALKER, XP_RATE, 1.05), null /* B3:moon_garden */);
add("irrigation", needs(FARMING, "scythe", 1, ENGINEERING, "redstone_savant", 3),
        grants(FARMING, ABILITY_DURATION, 1.10, ENGINEERING, PROC_CHANCE, 1.10), null);
add("flower_beds", needs(FARMING, "druid", 1, DECORATING, "feng_shui", 3),
        grants(DECORATING, PROC_POWER, 1.10, FARMING, XP_RATE, 1.05), null);
add("shore_supper", needs(FISHING, "fresh_catch", 3, COOKING, "banquet", 3),
        grants(FISHING, PROC_CHANCE, 1.05, COOKING, XP_RATE, 1.10), null /* B3:shore_supper */);
add("riverborn", needs(FISHING, "patient", 5, SWIMMING, "dolphin", 5),
        grants(FISHING, BONUS, 1.05, SWIMMING, PROC_CHANCE, 1.10), null);
add("wandering_angler", needs(FISHING, "rain_dancer", 2, WAYFARING, "globetrotter", 2),
        grants(FISHING, XP_RATE, 1.10, WAYFARING, ABILITY_DURATION, 1.10), null);
add("fishing_trip", needs(FISHING, "double_hook", 3, SOCIAL, "stay_a_while", 3),
        grants(FISHING, PROC_CHANCE, 1.05, SOCIAL, XP_RATE, 1.10), null /* B3:fishing_trip */);
add("night_line", needs(FISHING, "old_salt", 2, NIGHTWALKER, "long_night", 2),
        grants(FISHING, ABILITY_DURATION, 1.10, NIGHTWALKER, ABILITY_DURATION, 1.10), null);
add("brine_and_bottle", needs(FISHING, "sunken_treasure", 1, ALCHEMY, "distillery", 1),
        grants(ALCHEMY, PROC_CHANCE, 1.10, FISHING, PROC_POWER, 1.05), null);
add("fish_story", needs(FISHING, "lucky_line", 3, HOMEBUILDER, "storyteller", 2),
        grants(HOMEBUILDER, BONUS, 1.05, FISHING, XP_RATE, 1.05), null);
add("cutpurses_flight", needs(RUNNING, "escape_artist", 2, SNEAKING, "cat_landing", 3),
        grants(SNEAKING, XP_RATE, 1.10, RUNNING, PROC_CHANCE, 1.05), null);
add("running_start", needs(RUNNING, "roadrunner", 2, JUMPING, "stomp", 2, CHARGER, "rolling_thunder", 3),
        grants(CHARGER, PROC_POWER, 1.05, RUNNING, XP_RATE, 1.10, JUMPING, PROC_POWER, 1.10), null);
add("freerunner", needs(RUNNING, "parkour", 1, JUMPING, "bounce", 1),
        grants(RUNNING, BONUS, 1.05, JUMPING, PROC_CHANCE, 1.10), null /* B4:freerunner */);
add("running_mates", needs(RUNNING, "slipstream", 1, SOCIAL, "in_step", 1),
        grants(SOCIAL, ABILITY_DURATION, 1.10, RUNNING, ABILITY_DURATION, 1.10), null);
add("first_responder", needs(RUNNING, "windwalker", 1, GUARDIAN, "watchful_eye", 5),
        grants(GUARDIAN, XP_RATE, 1.10, RUNNING, PROC_POWER, 1.05), null /* B2:first_responder */);
add("hidden_spotter", needs(SNEAKING, "quiet_feet", 5, TACTICIAN, "clear_line", 1),
        grants(TACTICIAN, XP_RATE, 1.10, SNEAKING, BONUS, 1.05), null);
add("hush_of_the_deep", needs(SNEAKING, "assassin", 1, SPELUNKING, "warden_whisper", 2),
        grants(SPELUNKING, PROC_CHANCE, 1.10, SNEAKING, PROC_POWER, 1.05), null);
add("high_diver", needs(JUMPING, "safe_landing", 5, SWIMMING, "deep_lungs", 3),
        grants(JUMPING, XP_RATE, 1.05, SWIMMING, BONUS, 1.05), null /* B4:high_diver */);
add("steeplejack", needs(JUMPING, "cat_reflexes", 2, MASONRY, "safety_harness", 3),
        grants(MASONRY, XP_RATE, 1.10, JUMPING, ABILITY_DURATION, 1.10), null /* B4:steeplejack */);
add("pounce", needs(JUMPING, "stomp", 3, CHARGER, "unstoppable", 2),
        grants(CHARGER, ABILITY_DURATION, 1.10, JUMPING, PROC_POWER, 1.05), null);
add("chasm_leaper", needs(SPELUNKING, "rope_climber", 1, JUMPING, "skywalker", 1),
        grants(SPELUNKING, BONUS, 1.05, JUMPING, BONUS, 1.05), null);
add("lifeguard", needs(SWIMMING, "current_rider", 3, GUARDIAN, "warding", 5),
        grants(SWIMMING, XP_RATE, 1.10, GUARDIAN, BONUS, 1.05), null /* B2:lifeguard */);
add("sump_diver", needs(SWIMMING, "pearl_diver", 1, SPELUNKING, "cave_sense", 5),
        grants(SPELUNKING, PROC_CHANCE, 1.05, SWIMMING, PROC_POWER, 1.10), null);
add("island_hopper", needs(SWIMMING, "leviathan_blood", 1, WAYFARING, "world_walker", 1),
        grants(WAYFARING, BONUS, 1.05, SWIMMING, ABILITY_DURATION, 1.10), null);
add("salt_cure", needs(SWIMMING, "current_rider", 2, ALCHEMY, "antidote", 3),
        grants(ALCHEMY, ABILITY_DURATION, 1.10, SWIMMING, BONUS, 1.05), null);
add("gearwright", needs(SMITHING, "reforge", 3, ENGINEERING, "cogsmith", 3),
        grants(SMITHING, PROC_POWER, 1.10, ENGINEERING, XP_RATE, 1.05), null);
add("runesmith", needs(SMITHING, "disenchanter", 1, ALCHEMY, "runescribe", 1),
        grants(SMITHING, ABILITY_DURATION, 1.10, ALCHEMY, PROC_POWER, 1.10), null);
add("guild_prices", needs(SMITHING, "haggler", 5, SOCIAL, "good_word", 2),
        grants(SMITHING, BONUS, 1.05, SOCIAL, PROC_CHANCE, 1.10), null);
add("wrought_iron", needs(SMITHING, "legendary", 1, DECORATING, "curator", 3),
        grants(DECORATING, PROC_POWER, 1.05, SMITHING, TEMPO, 1.15), null);
add("potluck", needs(COOKING, "shared_meal", 2, SOCIAL, "old_friends", 3),
        grants(COOKING, PROC_POWER, 1.10, SOCIAL, XP_RATE, 1.05), null);
add("sunday_roast", needs(COOKING, "family_recipe", 1, HOMEBUILDER, "warm_welcome", 2, FARMING, "green_thumb", 1),
        grants(COOKING, BONUS, 1.05, HOMEBUILDER, PROC_CHANCE, 1.05, FARMING, BONUS, 1.05), null /* B3:sunday_roast */);
add("trail_rations", needs(WAYFARING, "campfire", 1, COOKING, "hearty", 3),
        grants(WAYFARING, XP_RATE, 1.05, COOKING, PROC_CHANCE, 1.05), null);
add("mana_tonic", needs(ALCHEMY, "elixir_lore", 2, SPELLCASTING, "mana_font", 3),
        grants(SPELLCASTING, BONUS, 1.05, ALCHEMY, TEMPO, 1.15), null);
add("field_medic", needs(ALCHEMY, "panacea", 1, GUARDIAN, "heartshare", 2),
        grants(GUARDIAN, PROC_CHANCE, 1.10, ALCHEMY, XP_RATE, 1.10), null /* B2:field_medic */);
add("starward", needs(SPELLCASTING, "spell_ward", 3, NIGHTWALKER, "phantom_ward", 2),
        grants(SPELLCASTING, XP_RATE, 1.10, NIGHTWALKER, BONUS, 1.05), null);
add("ley_hall", needs(SPELLCASTING, "deep_well", 2, DECORATING, "lamplighter", 3),
        grants(DECORATING, BONUS, 1.05, SPELLCASTING, PROC_CHANCE, 1.05), null);
add("arcane_circle", needs(SPELLCASTING, "arcane_rhythm", 2, SOCIAL, "teaching", 3),
        grants(SOCIAL, BONUS, 1.05, SPELLCASTING, TEMPO, 1.15), null);
add("battlemage", needs(SPELLCASTING, "echo_cast", 1, COURAGE, "giant_slayer", 2),
        grants(COURAGE, PROC_POWER, 1.10, SPELLCASTING, PROC_POWER, 1.05), null);
add("hexbane", needs(BEASTSLAYING, "bane", 3, SPELLCASTING, "overcharge", 2),
        grants(BEASTSLAYING, PROC_POWER, 1.10, SPELLCASTING, BONUS, 1.05), null);
add("fortifier", needs(ENGINEERING, "blueprint", 1, MASONRY, "bulk_order", 2),
        grants(ENGINEERING, BONUS, 1.05, MASONRY, PROC_CHANCE, 1.10), null);
add("clockwork_home", needs(ENGINEERING, "assembly_line", 2, HOMEBUILDER, "long_pull", 3),
        grants(ENGINEERING, TEMPO, 1.15, HOMEBUILDER, XP_RATE, 1.05), null);
add("field_engineer", needs(ENGINEERING, "chief_engineer", 1, TACTICIAN, "quartermaster", 2),
        grants(ENGINEERING, PROC_POWER, 1.10, TACTICIAN, XP_RATE, 1.05), null);
add("bounty_road", needs(BEASTSLAYING, "scavenger", 3, WAYFARING, "inspired", 2),
        grants(BEASTSLAYING, XP_RATE, 1.10, WAYFARING, PROC_POWER, 1.10), null);
add("witching_hour", needs(BEASTSLAYING, "hunters_instinct", 2, NIGHTWALKER, "darkborn_bane", 2),
        grants(BEASTSLAYING, ABILITY_DURATION, 1.10, NIGHTWALKER, PROC_POWER, 1.10), null);
add("colossus_bane", needs(BEASTSLAYING, "giant_slayer", 3, COURAGE, "giant_slayer", 3),
        grants(COURAGE, XP_RATE, 1.10, BEASTSLAYING, BONUS, 1.05), null /* B1:colossus_bane */);
add("pathscout", needs(WAYFARING, "seasoned_traveler", 3, TACTICIAN, "quick_call", 5),
        grants(TACTICIAN, PROC_CHANCE, 1.05, WAYFARING, XP_RATE, 1.05), null);
add("glowworm_grotto", needs(SPELUNKING, "glowstone_heart", 3, DECORATING, "curator", 2),
        grants(DECORATING, TEMPO, 1.15, SPELUNKING, PROC_POWER, 1.05), null);
add("shoring", needs(SPELUNKING, "cave_in", 1, MASONRY, "safety_harness", 2),
        grants(SPELUNKING, ABILITY_DURATION, 1.10, MASONRY, PROC_POWER, 1.05), null);
add("root_cellar", needs(SPELUNKING, "cave_lore", 3, HOMEBUILDER, "night_watch", 1),
        grants(HOMEBUILDER, ABILITY_DURATION, 1.10, SPELUNKING, XP_RATE, 1.05), null);
add("hearthstone", needs(MASONRY, "rapid_build", 5, HOMEBUILDER, "ember_heart", 3),
        grants(HOMEBUILDER, PROC_POWER, 1.10, MASONRY, TEMPO, 1.15), null);
add("housewarming", needs(DECORATING, "hygge", 3, HOMEBUILDER, "light_step", 5),
        grants(DECORATING, XP_RATE, 1.05, HOMEBUILDER, BONUS, 1.05), null /* B4:housewarming */);
add("salon", needs(DECORATING, "host", 2, SOCIAL, "strength_in_numbers", 2),
        grants(SOCIAL, PROC_CHANCE, 1.05, DECORATING, ABILITY_DURATION, 1.10), null);
```

## Appendix: lang lines

en_us.json:

```json
  "proficiency.synergy.turn_and_thrust": "Turn and Thrust",
  "proficiency.synergy.turn_and_thrust.desc": "A sword Parry makes your next sword hit a Perfect Strike.",
  "proficiency.synergy.cavalry_blade": "Cavalry Blade",
  "proficiency.synergy.cavalry_blade.desc": "The first cut of a charge is the one that counts.",
  "proficiency.synergy.bloodied_steel": "Bloodied Steel",
  "proficiency.synergy.bloodied_steel.desc": "Every wound you trade teaches the blade and the body.",
  "proficiency.synergy.single_combat": "Single Combat",
  "proficiency.synergy.single_combat.desc": "Duelist ignores hostiles that are not after you.",
  "proficiency.synergy.quiet_blade": "Quiet Blade",
  "proficiency.synergy.quiet_blade.desc": "The opening you wait for in the shadows comes sooner.",
  "proficiency.synergy.bladesmith": "Bladesmith",
  "proficiency.synergy.bladesmith.desc": "You know which edge you ground yourself.",
  "proficiency.synergy.moonblade": "Moonblade",
  "proficiency.synergy.moonblade.desc": "Silver light finds the wound and widens it.",
  "proficiency.synergy.armsmaster": "Armsmaster",
  "proficiency.synergy.armsmaster.desc": "Blade, axe and mace: every frenzy runs a little longer.",
  "proficiency.synergy.berserkergang": "Berserkergang",
  "proficiency.synergy.berserkergang.desc": "Old scars, fresh rage.",
  "proficiency.synergy.red_mist": "Red Mist",
  "proficiency.synergy.red_mist.desc": "Outnumbered, the axe swings wider and the blood runs hotter.",
  "proficiency.synergy.reavers_rush": "Reaver's Rush",
  "proficiency.synergy.reavers_rush.desc": "A charge hit with an axe makes your next axe hit within 3 s a Cleave.",
  "proficiency.synergy.shieldbreaker": "Shieldbreaker",
  "proficiency.synergy.shieldbreaker.desc": "You know a shield from both sides.",
  "proficiency.synergy.headtaker": "Headtaker",
  "proficiency.synergy.headtaker.desc": "The trophy comes off cleaner with a heavy axe.",
  "proficiency.synergy.wolfs_hour": "Wolf's Hour",
  "proficiency.synergy.wolfs_hour.desc": "When the moon is up, the axe gets hungry.",
  "proficiency.synergy.raider": "Raider",
  "proficiency.synergy.raider.desc": "Hit, run, hit again.",
  "proficiency.synergy.falling_hammer": "Falling Hammer",
  "proficiency.synergy.falling_hammer.desc": "A Shockwave from a 3+ block fall also lands a Stomp around you.",
  "proficiency.synergy.anvil_body": "Anvil Body",
  "proficiency.synergy.anvil_body.desc": "Swing a heavy thing long enough and you become one.",
  "proficiency.synergy.quarry_hammer": "Quarry Hammer",
  "proficiency.synergy.quarry_hammer.desc": "Rock and bone break the same way.",
  "proficiency.synergy.battering_ram": "Battering Ram",
  "proficiency.synergy.battering_ram.desc": "The weight of the run goes into the head of the mace.",
  "proficiency.synergy.war_bell": "War Bell",
  "proficiency.synergy.war_bell.desc": "Every ringing blow beats the drum for the next.",
  "proficiency.synergy.smiths_maul": "Smith's Maul",
  "proficiency.synergy.smiths_maul.desc": "Anvil work and skull work, one swing.",
  "proficiency.synergy.earthbound_oath": "Earthbound Oath",
  "proficiency.synergy.earthbound_oath.desc": "Every mob a Cratermaker smash hits turns on you for 4 s.",
  "proficiency.synergy.harpooneer": "Harpooneer",
  "proficiency.synergy.harpooneer.desc": "A Riptide Strike on a mob in water can bring up a catch.",
  "proficiency.synergy.undercurrent": "Undercurrent",
  "proficiency.synergy.undercurrent.desc": "Storm and tide hold you a little longer.",
  "proficiency.synergy.stormcaller": "Stormcaller",
  "proficiency.synergy.stormcaller.desc": "The sky answers a spear and a spell alike.",
  "proficiency.synergy.sea_wolf": "Sea Wolf",
  "proficiency.synergy.sea_wolf.desc": "Waist-deep and outnumbered is where you fight best.",
  "proficiency.synergy.deep_sea_slayer": "Deep-Sea Slayer",
  "proficiency.synergy.deep_sea_slayer.desc": "The bigger the thing in the water, the deeper the spear.",
  "proficiency.synergy.boarding_party": "Boarding Party",
  "proficiency.synergy.boarding_party.desc": "First over the rail, spear first.",
  "proficiency.synergy.spear_volley": "Spear Volley",
  "proficiency.synergy.spear_volley.desc": "A thrown trident is a shot from the back line too.",
  "proficiency.synergy.mariner": "Mariner",
  "proficiency.synergy.mariner.desc": "Spear, rod and the open road over water.",
  "proficiency.synergy.iron_monk": "Iron Monk",
  "proficiency.synergy.iron_monk.desc": "Fists and ribs hardened in the same school.",
  "proficiency.synergy.nerve_strike": "Nerve Strike",
  "proficiency.synergy.nerve_strike.desc": "A crouched punch from behind numbs: Slowness IV and Weakness for 2 s.",
  "proficiency.synergy.flying_kick": "Flying Kick",
  "proficiency.synergy.flying_kick.desc": "The leap is half of the blow.",
  "proficiency.synergy.tavern_hero": "Tavern Hero",
  "proficiency.synergy.tavern_hero.desc": "Three against one and you are only warming up.",
  "proficiency.synergy.kitchen_brawler": "Kitchen Brawler",
  "proficiency.synergy.kitchen_brawler.desc": "Kneading dough builds the same arm as a right hook.",
  "proficiency.synergy.drunken_fist": "Drunken Fist",
  "proficiency.synergy.drunken_fist.desc": "A strong brew and a looser stance.",
  "proficiency.synergy.open_palm": "Open Palm",
  "proficiency.synergy.open_palm.desc": "The hand that strikes is the hand that shields.",
  "proficiency.synergy.shoulder_charge": "Shoulder Charge",
  "proficiency.synergy.shoulder_charge.desc": "No weapon needed to arrive first.",
  "proficiency.synergy.pavise": "Pavise",
  "proficiency.synergy.pavise.desc": "A hit caught on your shield makes your next crossbow shot a Double Load.",
  "proficiency.synergy.unbroken_line": "Unbroken Line",
  "proficiency.synergy.unbroken_line.desc": "Where you plant the shield, the line holds.",
  "proficiency.synergy.shieldwright": "Shieldwright",
  "proficiency.synergy.shieldwright.desc": "You forged the rim you hit back with.",
  "proficiency.synergy.shield_rush": "Shield Rush",
  "proficiency.synergy.shield_rush.desc": "A raised shield is a battering ram with a handle.",
  "proficiency.synergy.hardtack": "Hardtack",
  "proficiency.synergy.hardtack.desc": "Plain food, long marches, a stubborn body.",
  "proficiency.synergy.marathoner": "Marathoner",
  "proficiency.synergy.marathoner.desc": "There is always one more mile in the legs.",
  "proficiency.synergy.play_dead": "Play Dead",
  "proficiency.synergy.play_dead.desc": "A Grit while crouching: Invisibility for 3 s and nearby mobs lose you.",
  "proficiency.synergy.moonshot": "Moonshot",
  "proficiency.synergy.moonshot.desc": "Eyes made for the dark, arrows that find it.",
  "proficiency.synergy.shared_quarry": "Shared Quarry",
  "proficiency.synergy.shared_quarry.desc": "Your Hunter's Mark also puts your Tactician mark on the target.",
  "proficiency.synergy.hanging_shot": "Hanging Shot",
  "proficiency.synergy.hanging_shot.desc": "Loosing a full draw in mid-air gives Slow Falling for 1.5 s.",
  "proficiency.synergy.big_game_hunter": "Big Game Hunter",
  "proficiency.synergy.big_game_hunter.desc": "Know the beast, then take it from a long way off.",
  "proficiency.synergy.ranger": "Ranger",
  "proficiency.synergy.ranger.desc": "Each new biome you discover puts 4 arrows in your quiver.",
  "proficiency.synergy.twin_strings": "Twin Strings",
  "proficiency.synergy.twin_strings.desc": "Bow or crossbow, the hands already know.",
  "proficiency.synergy.arcane_arrow": "Arcane Arrow",
  "proficiency.synergy.arcane_arrow.desc": "A steady breath is half a spell.",
  "proficiency.synergy.boltwright": "Boltwright",
  "proficiency.synergy.boltwright.desc": "Bolts from your own anvil fly truer.",
  "proficiency.synergy.firing_line": "Firing Line",
  "proficiency.synergy.firing_line.desc": "Load, call, loose, all on the same breath.",
  "proficiency.synergy.arrow_slit": "Arrow Slit",
  "proficiency.synergy.arrow_slit.desc": "You built the wall, so you know where to shoot from.",
  "proficiency.synergy.snipers_nest": "Sniper's Nest",
  "proficiency.synergy.snipers_nest.desc": "Unseen, unhurried, never out of bolts.",
  "proficiency.synergy.sentry_post": "Sentry Post",
  "proficiency.synergy.sentry_post.desc": "Someone has to stand on the wall and draw their eye.",
  "proficiency.synergy.dragonbolt": "Dragonbolt",
  "proficiency.synergy.dragonbolt.desc": "A Ballista blast on a boss hits it for 25% more and makes it glow 5 s.",
  "proficiency.synergy.tunneler": "Tunneler",
  "proficiency.synergy.tunneler.desc": "Stone or soil, the tunnel goes on.",
  "proficiency.synergy.steam_drill": "Steam Drill",
  "proficiency.synergy.steam_drill.desc": "A tuned machine and a rich vein feed each other.",
  "proficiency.synergy.quarryman": "Quarryman",
  "proficiency.synergy.quarryman.desc": "You cut the stone you will build with.",
  "proficiency.synergy.mineral_tincture": "Mineral Tincture",
  "proficiency.synergy.mineral_tincture.desc": "Redstone and glowstone go from the vein to the vial.",
  "proficiency.synergy.lightless_seam": "Lightless Seam",
  "proficiency.synergy.lightless_seam.desc": "Down where no torch reaches, you still see the ore.",
  "proficiency.synergy.seismic_survey": "Seismic Survey",
  "proficiency.synergy.seismic_survey.desc": "Echolocation pulses every 5 s instead of 10 and reaches 2 blocks further.",
  "proficiency.synergy.orchard_keeper": "Orchard Keeper",
  "proficiency.synergy.orchard_keeper.desc": "A sapling you replant starts one growth stage ahead.",
  "proficiency.synergy.charcoal_burner": "Charcoal Burner",
  "proficiency.synergy.charcoal_burner.desc": "A Timber feeds 1 log per 8 felled into a furnace within 4 blocks.",
  "proficiency.synergy.joinery": "Joinery",
  "proficiency.synergy.joinery.desc": "The heartwood you cut ends up as the table you set.",
  "proficiency.synergy.greenway": "Greenway",
  "proficiency.synergy.greenway.desc": "The forest is a road if you know it.",
  "proficiency.synergy.full_woodpile": "Full Woodpile",
  "proficiency.synergy.full_woodpile.desc": "A home is warm because someone chopped the wood.",
  "proficiency.synergy.smokehouse": "Smokehouse",
  "proficiency.synergy.smokehouse.desc": "Apples, alder smoke and a camp pot.",
  "proficiency.synergy.timber_frame": "Timber Frame",
  "proficiency.synergy.timber_frame.desc": "Beams and stone, raised by one pair of hands.",
  "proficiency.synergy.topsoil": "Topsoil",
  "proficiency.synergy.topsoil.desc": "Good dirt is half a harvest.",
  "proficiency.synergy.potters_clay": "Potter's Clay",
  "proficiency.synergy.potters_clay.desc": "The riverbank you dig becomes the vase on the shelf.",
  "proficiency.synergy.groundwork": "Groundwork",
  "proficiency.synergy.groundwork.desc": "Every home starts with a level patch of earth.",
  "proficiency.synergy.deep_strata": "Deep Strata",
  "proficiency.synergy.deep_strata.desc": "The older the layer, the more it gives up.",
  "proficiency.synergy.earthworks": "Earthworks",
  "proficiency.synergy.earthworks.desc": "Move enough dirt and it becomes engineering.",
  "proficiency.synergy.dredger": "Dredger",
  "proficiency.synergy.dredger.desc": "Shovels dig at full speed underwater and while swimming.",
  "proficiency.synergy.herbalist": "Herbalist",
  "proficiency.synergy.herbalist.desc": "You grow what goes into the brew.",
  "proficiency.synergy.barn_raising": "Barn Raising",
  "proficiency.synergy.barn_raising.desc": "The whole village turns out for a good farm.",
  "proficiency.synergy.moon_garden": "Moon Garden",
  "proficiency.synergy.moon_garden.desc": "At night, Bountiful Harvest fires half again as often.",
  "proficiency.synergy.irrigation": "Irrigation",
  "proficiency.synergy.irrigation.desc": "Redstone does the watering, you do the reaping.",
  "proficiency.synergy.flower_beds": "Flower Beds",
  "proficiency.synergy.flower_beds.desc": "A garden is a room with the sky for a ceiling.",
  "proficiency.synergy.shore_supper": "Shore Supper",
  "proficiency.synergy.shore_supper.desc": "By a lit campfire, every raw fish comes up cooked and trains Cooking.",
  "proficiency.synergy.riverborn": "Riverborn",
  "proficiency.synergy.riverborn.desc": "You know the river from above and below.",
  "proficiency.synergy.wandering_angler": "Wandering Angler",
  "proficiency.synergy.wandering_angler.desc": "Every new river has a fish you have not met.",
  "proficiency.synergy.fishing_trip": "Fishing Trip",
  "proficiency.synergy.fishing_trip.desc": "A friend's Treasure Haul within 16 blocks makes your next one certain.",
  "proficiency.synergy.night_line": "Night Line",
  "proficiency.synergy.night_line.desc": "The best bites come after dark.",
  "proficiency.synergy.brine_and_bottle": "Brine and Bottle",
  "proficiency.synergy.brine_and_bottle.desc": "Pufferfish and salt water belong in the brewing stand.",
  "proficiency.synergy.fish_story": "Fish Story",
  "proficiency.synergy.fish_story.desc": "It was this big, and it grows every time you tell it.",
  "proficiency.synergy.cutpurses_flight": "Cutpurse's Flight",
  "proficiency.synergy.cutpurses_flight.desc": "Over the wall, down the roof, gone.",
  "proficiency.synergy.running_start": "Running Start",
  "proficiency.synergy.running_start.desc": "Sprint, leap, land on them.",
  "proficiency.synergy.freerunner": "Freerunner",
  "proficiency.synergy.freerunner.desc": "Landing a 4+ block fall while sprinting keeps the sprint and gives Speed II 2 s.",
  "proficiency.synergy.running_mates": "Running Mates",
  "proficiency.synergy.running_mates.desc": "Nobody falls behind on a good run.",
  "proficiency.synergy.first_responder": "First Responder",
  "proficiency.synergy.first_responder.desc": "Sprinting while a friend within 24 blocks is under 30% health: Speed II.",
  "proficiency.synergy.hidden_spotter": "Hidden Spotter",
  "proficiency.synergy.hidden_spotter.desc": "The best spotter is the one nobody sees.",
  "proficiency.synergy.hush_of_the_deep": "Hush of the Deep",
  "proficiency.synergy.hush_of_the_deep.desc": "Down there, silence is the only armour that works.",
  "proficiency.synergy.high_diver": "High Diver",
  "proficiency.synergy.high_diver.desc": "Falling 8+ blocks into water gives Dolphin's Grace for 6 s.",
  "proficiency.synergy.steeplejack": "Steeplejack",
  "proficiency.synergy.steeplejack.desc": "A block you place while falling 3+ blocks stops the fall. Once per 5 s.",
  "proficiency.synergy.pounce": "Pounce",
  "proficiency.synergy.pounce.desc": "Coming from above counts as coming first.",
  "proficiency.synergy.chasm_leaper": "Chasm Leaper",
  "proficiency.synergy.chasm_leaper.desc": "Ravines are just long stairs.",
  "proficiency.synergy.lifeguard": "Lifeguard",
  "proficiency.synergy.lifeguard.desc": "A friend within 8 blocks low on air gets Water Breathing 5 s. Once a minute.",
  "proficiency.synergy.sump_diver": "Sump Diver",
  "proficiency.synergy.sump_diver.desc": "The flooded passage is just another tunnel.",
  "proficiency.synergy.island_hopper": "Island Hopper",
  "proficiency.synergy.island_hopper.desc": "The sea is a road with no milestones.",
  "proficiency.synergy.salt_cure": "Salt Cure",
  "proficiency.synergy.salt_cure.desc": "Sea water and the right tincture keep you sound.",
  "proficiency.synergy.gearwright": "Gearwright",
  "proficiency.synergy.gearwright.desc": "A cog is a blade that goes round.",
  "proficiency.synergy.runesmith": "Runesmith",
  "proficiency.synergy.runesmith.desc": "What the grindstone takes off, the rune puts back.",
  "proficiency.synergy.guild_prices": "Guild Prices",
  "proficiency.synergy.guild_prices.desc": "A good word at the anvil is worth a few levels.",
  "proficiency.synergy.wrought_iron": "Wrought Iron",
  "proficiency.synergy.wrought_iron.desc": "The gate you forged is the finest thing in the room.",
  "proficiency.synergy.potluck": "Potluck",
  "proficiency.synergy.potluck.desc": "Everyone brings a dish, and you bring two.",
  "proficiency.synergy.sunday_roast": "Sunday Roast",
  "proficiency.synergy.sunday_roast.desc": "Food eaten at home restores 2 more hunger and 2 more saturation.",
  "proficiency.synergy.trail_rations": "Trail Rations",
  "proficiency.synergy.trail_rations.desc": "Food that keeps, for roads that do not end.",
  "proficiency.synergy.mana_tonic": "Mana Tonic",
  "proficiency.synergy.mana_tonic.desc": "A brewer knows what the well is made of.",
  "proficiency.synergy.field_medic": "Field Medic",
  "proficiency.synergy.field_medic.desc": "A healing splash you throw also cures one harmful effect on each friend hit.",
  "proficiency.synergy.starward": "Starward",
  "proficiency.synergy.starward.desc": "Wards drawn by starlight hold through the night.",
  "proficiency.synergy.ley_hall": "Ley Hall",
  "proficiency.synergy.ley_hall.desc": "Lamps placed along the ley lines burn with more than fire.",
  "proficiency.synergy.arcane_circle": "Arcane Circle",
  "proficiency.synergy.arcane_circle.desc": "Spells learned together are cast in rhythm.",
  "proficiency.synergy.battlemage": "Battlemage",
  "proficiency.synergy.battlemage.desc": "The bigger the foe, the louder the echo.",
  "proficiency.synergy.hexbane": "Hexbane",
  "proficiency.synergy.hexbane.desc": "The undead fear the word as much as the blade.",
  "proficiency.synergy.fortifier": "Fortifier",
  "proficiency.synergy.fortifier.desc": "Walls with gears in them.",
  "proficiency.synergy.clockwork_home": "Clockwork Home",
  "proficiency.synergy.clockwork_home.desc": "A house that does its own chores.",
  "proficiency.synergy.field_engineer": "Field Engineer",
  "proficiency.synergy.field_engineer.desc": "Supply lines are machines too.",
  "proficiency.synergy.bounty_road": "Bounty Road",
  "proficiency.synergy.bounty_road.desc": "Every road has a beast on it, and a reward.",
  "proficiency.synergy.witching_hour": "Witching Hour",
  "proficiency.synergy.witching_hour.desc": "The night's worst things are the best trophies.",
  "proficiency.synergy.colossus_bane": "Colossus Bane",
  "proficiency.synergy.colossus_bane.desc": "Killing a mob with 60+ max health always gives you Rally.",
  "proficiency.synergy.pathscout": "Pathscout",
  "proficiency.synergy.pathscout.desc": "You call the target before the others see the road.",
  "proficiency.synergy.glowworm_grotto": "Glowworm Grotto",
  "proficiency.synergy.glowworm_grotto.desc": "A cave lit with care is a room worth keeping.",
  "proficiency.synergy.shoring": "Shoring",
  "proficiency.synergy.shoring.desc": "You prop up the roof before you trust it.",
  "proficiency.synergy.root_cellar": "Root Cellar",
  "proficiency.synergy.root_cellar.desc": "Every good home has a cave under it.",
  "proficiency.synergy.hearthstone": "Hearthstone",
  "proficiency.synergy.hearthstone.desc": "You laid the stone the fire sits on.",
  "proficiency.synergy.housewarming": "Housewarming",
  "proficiency.synergy.housewarming.desc": "A cozy room at home also feeds you: 1 hunger every 30 s.",
  "proficiency.synergy.salon": "Salon",
  "proficiency.synergy.salon.desc": "A fine room fills itself with good company.",
```

pl_pl.json:

```json
  "proficiency.synergy.turn_and_thrust": "Zwód i pchnięcie",
  "proficiency.synergy.turn_and_thrust.desc": "Parada mieczem sprawia, że następny cios mieczem to Idealne cięcie.",
  "proficiency.synergy.cavalry_blade": "Szabla kawalerzysty",
  "proficiency.synergy.cavalry_blade.desc": "Liczy się pierwsze cięcie szarży.",
  "proficiency.synergy.bloodied_steel": "Zakrwawiona stal",
  "proficiency.synergy.bloodied_steel.desc": "Każda wymieniona rana uczy i ostrze, i ciało.",
  "proficiency.synergy.single_combat": "Pojedynek",
  "proficiency.synergy.single_combat.desc": "Mistrz pojedynków nie liczy wrogów, którzy nie celują w ciebie.",
  "proficiency.synergy.quiet_blade": "Ciche ostrze",
  "proficiency.synergy.quiet_blade.desc": "Okazja, na którą czekasz w cieniu, przychodzi szybciej.",
  "proficiency.synergy.bladesmith": "Kowal mieczy",
  "proficiency.synergy.bladesmith.desc": "Znasz ostrze, które sam naostrzyłeś.",
  "proficiency.synergy.moonblade": "Księżycowe ostrze",
  "proficiency.synergy.moonblade.desc": "Srebrne światło znajduje ranę i ją pogłębia.",
  "proficiency.synergy.armsmaster": "Mistrz oręża",
  "proficiency.synergy.armsmaster.desc": "Miecz, topór i buzdygan: każdy szał trwa nieco dłużej.",
  "proficiency.synergy.berserkergang": "Berserkergang",
  "proficiency.synergy.berserkergang.desc": "Stare blizny, świeża furia.",
  "proficiency.synergy.red_mist": "Czerwona mgła",
  "proficiency.synergy.red_mist.desc": "W przewadze wroga topór zatacza szersze łuki, a krew wrze.",
  "proficiency.synergy.reavers_rush": "Rajd łupieżcy",
  "proficiency.synergy.reavers_rush.desc": "Trafienie szarżą z toporem: następny cios toporem w 3 s to Rozpłatanie.",
  "proficiency.synergy.shieldbreaker": "Łamacz tarcz",
  "proficiency.synergy.shieldbreaker.desc": "Znasz tarczę z obu stron.",
  "proficiency.synergy.headtaker": "Łowca głów",
  "proficiency.synergy.headtaker.desc": "Ciężki topór odcina trofeum czyściej.",
  "proficiency.synergy.wolfs_hour": "Godzina wilka",
  "proficiency.synergy.wolfs_hour.desc": "Gdy wschodzi księżyc, topór robi się głodny.",
  "proficiency.synergy.raider": "Najeźdźca",
  "proficiency.synergy.raider.desc": "Uderz, uciekaj, uderz znowu.",
  "proficiency.synergy.falling_hammer": "Spadający młot",
  "proficiency.synergy.falling_hammer.desc": "Fala uderzeniowa po upadku z 3+ bloków daje też uderzenie Z buta.",
  "proficiency.synergy.anvil_body": "Ciało jak kowadło",
  "proficiency.synergy.anvil_body.desc": "Wymachuj czymś ciężkim dość długo, a sam takim się staniesz.",
  "proficiency.synergy.quarry_hammer": "Młot kamieniarza",
  "proficiency.synergy.quarry_hammer.desc": "Skała i kość pękają tak samo.",
  "proficiency.synergy.battering_ram": "Taran bojowy",
  "proficiency.synergy.battering_ram.desc": "Cały impet biegu idzie w głowicę buzdygana.",
  "proficiency.synergy.war_bell": "Dzwon wojny",
  "proficiency.synergy.war_bell.desc": "Każdy dźwięczny cios wybija rytm następnego.",
  "proficiency.synergy.smiths_maul": "Kowalski młot",
  "proficiency.synergy.smiths_maul.desc": "Praca na kowadle i na czaszkach, jeden zamach.",
  "proficiency.synergy.earthbound_oath": "Przysięga ziemi",
  "proficiency.synergy.earthbound_oath.desc": "Każdy mob trafiony uderzeniem Twórcy kraterów rzuca się na ciebie na 4 s.",
  "proficiency.synergy.harpooneer": "Harpunnik",
  "proficiency.synergy.harpooneer.desc": "Uderzenie fali w moba w wodzie może wyłowić zdobycz.",
  "proficiency.synergy.undercurrent": "Prąd głębinowy",
  "proficiency.synergy.undercurrent.desc": "Sztorm i fala trzymają cię nieco dłużej.",
  "proficiency.synergy.stormcaller": "Wołający burzę",
  "proficiency.synergy.stormcaller.desc": "Niebo odpowiada i na włócznię, i na zaklęcie.",
  "proficiency.synergy.sea_wolf": "Morski wilk",
  "proficiency.synergy.sea_wolf.desc": "Najlepiej walczysz po pas w wodzie i w mniejszości.",
  "proficiency.synergy.deep_sea_slayer": "Pogromca głębin",
  "proficiency.synergy.deep_sea_slayer.desc": "Im większe stworzenie w wodzie, tym głębiej wchodzi włócznia.",
  "proficiency.synergy.boarding_party": "Abordaż",
  "proficiency.synergy.boarding_party.desc": "Pierwszy przez burtę, z włócznią przed sobą.",
  "proficiency.synergy.spear_volley": "Grad włóczni",
  "proficiency.synergy.spear_volley.desc": "Rzucony trójząb to też strzał z tylnej linii.",
  "proficiency.synergy.mariner": "Żeglarz",
  "proficiency.synergy.mariner.desc": "Włócznia, wędka i otwarta droga po wodzie.",
  "proficiency.synergy.iron_monk": "Żelazny mnich",
  "proficiency.synergy.iron_monk.desc": "Pięści i żebra hartowane w tej samej szkole.",
  "proficiency.synergy.nerve_strike": "Cios w nerw",
  "proficiency.synergy.nerve_strike.desc": "Cios z przykucnięcia w plecy paraliżuje: Spowolnienie IV i Osłabienie na 2 s.",
  "proficiency.synergy.flying_kick": "Kopnięcie z wyskoku",
  "proficiency.synergy.flying_kick.desc": "Skok to połowa ciosu.",
  "proficiency.synergy.tavern_hero": "Bohater karczmy",
  "proficiency.synergy.tavern_hero.desc": "Trzech na jednego, a ty dopiero się rozgrzewasz.",
  "proficiency.synergy.kitchen_brawler": "Kuchenny zabijaka",
  "proficiency.synergy.kitchen_brawler.desc": "Wyrabianie ciasta buduje to samo ramię co prawy sierpowy.",
  "proficiency.synergy.drunken_fist": "Pijana pięść",
  "proficiency.synergy.drunken_fist.desc": "Mocny wywar i luźniejsza postawa.",
  "proficiency.synergy.open_palm": "Otwarta dłoń",
  "proficiency.synergy.open_palm.desc": "Dłoń, która uderza, to dłoń, która osłania.",
  "proficiency.synergy.shoulder_charge": "Szarża z barku",
  "proficiency.synergy.shoulder_charge.desc": "Żeby dotrzeć pierwszym, broń nie jest potrzebna.",
  "proficiency.synergy.pavise": "Pawęż",
  "proficiency.synergy.pavise.desc": "Cios przyjęty na tarczę sprawia, że następny strzał z kuszy to Podwójny ładunek.",
  "proficiency.synergy.unbroken_line": "Nieprzerwany szyk",
  "proficiency.synergy.unbroken_line.desc": "Gdzie wbijesz tarczę, tam szyk wytrzyma.",
  "proficiency.synergy.shieldwright": "Tarczownik",
  "proficiency.synergy.shieldwright.desc": "Sam wykułeś obręcz, którą oddajesz ciosy.",
  "proficiency.synergy.shield_rush": "Natarcie tarczą",
  "proficiency.synergy.shield_rush.desc": "Uniesiona tarcza to taran z uchwytem.",
  "proficiency.synergy.hardtack": "Suchary",
  "proficiency.synergy.hardtack.desc": "Proste jedzenie, długie marsze, uparte ciało.",
  "proficiency.synergy.marathoner": "Maratończyk",
  "proficiency.synergy.marathoner.desc": "W nogach zawsze zostaje jeszcze jeden kilometr.",
  "proficiency.synergy.play_dead": "Udawanie trupa",
  "proficiency.synergy.play_dead.desc": "Zawziętość w przykucnięciu: 3 s niewidzialności, a moby w pobliżu cię gubią.",
  "proficiency.synergy.moonshot": "Strzał w księżyc",
  "proficiency.synergy.moonshot.desc": "Oczy stworzone do ciemności i strzały, które ją przeszywają.",
  "proficiency.synergy.shared_quarry": "Wspólna zwierzyna",
  "proficiency.synergy.shared_quarry.desc": "Znak łowcy nakłada też twój znak Taktyki na cel.",
  "proficiency.synergy.hanging_shot": "Strzał w zawieszeniu",
  "proficiency.synergy.hanging_shot.desc": "Pełny strzał z łuku w powietrzu daje Powolne opadanie na 1,5 s.",
  "proficiency.synergy.big_game_hunter": "Łowca grubego zwierza",
  "proficiency.synergy.big_game_hunter.desc": "Poznaj bestię, a potem ustrzel ją z daleka.",
  "proficiency.synergy.ranger": "Strażnik dróg",
  "proficiency.synergy.ranger.desc": "Każdy nowo odkryty biom dokłada 4 strzały do kołczanu.",
  "proficiency.synergy.twin_strings": "Dwie cięciwy",
  "proficiency.synergy.twin_strings.desc": "Łuk czy kusza, ręce już wiedzą, co robić.",
  "proficiency.synergy.arcane_arrow": "Tajemna strzała",
  "proficiency.synergy.arcane_arrow.desc": "Spokojny oddech to pół zaklęcia.",
  "proficiency.synergy.boltwright": "Wytwórca bełtów",
  "proficiency.synergy.boltwright.desc": "Bełty z własnego kowadła lecą celniej.",
  "proficiency.synergy.firing_line": "Linia ognia",
  "proficiency.synergy.firing_line.desc": "Naciągnij, wskaż, strzel, wszystko na jednym oddechu.",
  "proficiency.synergy.arrow_slit": "Strzelnica",
  "proficiency.synergy.arrow_slit.desc": "Sam zbudowałeś mur, więc wiesz, skąd strzelać.",
  "proficiency.synergy.snipers_nest": "Gniazdo strzelca",
  "proficiency.synergy.snipers_nest.desc": "Niewidoczny, bez pośpiechu, zawsze z bełtami.",
  "proficiency.synergy.sentry_post": "Posterunek",
  "proficiency.synergy.sentry_post.desc": "Ktoś musi stać na murze i ściągać ich wzrok.",
  "proficiency.synergy.dragonbolt": "Smoczy bełt",
  "proficiency.synergy.dragonbolt.desc": "Wybuch Balisty na bossie zadaje mu 25% więcej i podświetla go na 5 s.",
  "proficiency.synergy.tunneler": "Tunelarz",
  "proficiency.synergy.tunneler.desc": "Kamień czy ziemia, tunel idzie dalej.",
  "proficiency.synergy.steam_drill": "Parowe wiertło",
  "proficiency.synergy.steam_drill.desc": "Dostrojona maszyna i bogata żyła napędzają się nawzajem.",
  "proficiency.synergy.quarryman": "Łamacz kamienia",
  "proficiency.synergy.quarryman.desc": "Sam wydobywasz kamień, z którego będziesz budować.",
  "proficiency.synergy.mineral_tincture": "Nalewka mineralna",
  "proficiency.synergy.mineral_tincture.desc": "Czerwony kamień i jasnogłaz trafiają prosto z żyły do fiolki.",
  "proficiency.synergy.lightless_seam": "Żyła bez światła",
  "proficiency.synergy.lightless_seam.desc": "Tam, gdzie nie sięga pochodnia, wciąż widzisz rudę.",
  "proficiency.synergy.seismic_survey": "Badanie sejsmiczne",
  "proficiency.synergy.seismic_survey.desc": "Echolokacja działa co 5 s zamiast co 10 i sięga 2 bloki dalej.",
  "proficiency.synergy.orchard_keeper": "Sadownik",
  "proficiency.synergy.orchard_keeper.desc": "Posadzona przez ciebie sadzonka startuje o etap wzrostu dalej.",
  "proficiency.synergy.charcoal_burner": "Węglarz",
  "proficiency.synergy.charcoal_burner.desc": "Powalenie wrzuca 1 kłodę na 8 ściętych do pieca w promieniu 4 bloków.",
  "proficiency.synergy.joinery": "Stolarka",
  "proficiency.synergy.joinery.desc": "Rdzeń drzewa, który ścinasz, staje się stołem, który nakrywasz.",
  "proficiency.synergy.greenway": "Zielony szlak",
  "proficiency.synergy.greenway.desc": "Las jest drogą, jeśli go znasz.",
  "proficiency.synergy.full_woodpile": "Pełna drewutnia",
  "proficiency.synergy.full_woodpile.desc": "W domu jest ciepło, bo ktoś narąbał drewna.",
  "proficiency.synergy.smokehouse": "Wędzarnia",
  "proficiency.synergy.smokehouse.desc": "Jabłka, olchowy dym i kociołek przy obozie.",
  "proficiency.synergy.timber_frame": "Mur pruski",
  "proficiency.synergy.timber_frame.desc": "Belki i kamień, wzniesione jedną parą rąk.",
  "proficiency.synergy.topsoil": "Czarnoziem",
  "proficiency.synergy.topsoil.desc": "Dobra ziemia to pół plonu.",
  "proficiency.synergy.potters_clay": "Glina garncarza",
  "proficiency.synergy.potters_clay.desc": "Brzeg rzeki, który kopiesz, staje się wazonem na półce.",
  "proficiency.synergy.groundwork": "Fundamenty",
  "proficiency.synergy.groundwork.desc": "Każdy dom zaczyna się od wyrównanego kawałka ziemi.",
  "proficiency.synergy.deep_strata": "Głębokie warstwy",
  "proficiency.synergy.deep_strata.desc": "Im starsza warstwa, tym więcej oddaje.",
  "proficiency.synergy.earthworks": "Wały ziemne",
  "proficiency.synergy.earthworks.desc": "Przerzuć dość ziemi, a stanie się inżynierią.",
  "proficiency.synergy.dredger": "Pogłębiarka",
  "proficiency.synergy.dredger.desc": "Łopata kopie pod wodą i w trakcie pływania z pełną prędkością.",
  "proficiency.synergy.herbalist": "Zielarz",
  "proficiency.synergy.herbalist.desc": "Sam hodujesz to, co trafia do wywaru.",
  "proficiency.synergy.barn_raising": "Tłoka",
  "proficiency.synergy.barn_raising.desc": "Cała wieś zbiera się przy dobrym gospodarstwie.",
  "proficiency.synergy.moon_garden": "Księżycowy ogród",
  "proficiency.synergy.moon_garden.desc": "Nocą Obfite plony trafiają się o połowę częściej.",
  "proficiency.synergy.irrigation": "Nawadnianie",
  "proficiency.synergy.irrigation.desc": "Czerwony kamień podlewa, ty zbierasz.",
  "proficiency.synergy.flower_beds": "Rabaty",
  "proficiency.synergy.flower_beds.desc": "Ogród to pokój, którego sufitem jest niebo.",
  "proficiency.synergy.shore_supper": "Kolacja na brzegu",
  "proficiency.synergy.shore_supper.desc": "Przy płonącym ognisku każda ryba wychodzi upieczona i uczy Gotowania.",
  "proficiency.synergy.riverborn": "Dziecię rzeki",
  "proficiency.synergy.riverborn.desc": "Znasz rzekę z góry i od spodu.",
  "proficiency.synergy.wandering_angler": "Wędrowny wędkarz",
  "proficiency.synergy.wandering_angler.desc": "W każdej nowej rzece pływa ryba, której jeszcze nie znasz.",
  "proficiency.synergy.fishing_trip": "Wyprawa na ryby",
  "proficiency.synergy.fishing_trip.desc": "Skarb z głębin przyjaciela w promieniu 16 bloków gwarantuje twój następny.",
  "proficiency.synergy.night_line": "Nocna żyłka",
  "proficiency.synergy.night_line.desc": "Najlepiej bierze po zmroku.",
  "proficiency.synergy.brine_and_bottle": "Solanka i flaszka",
  "proficiency.synergy.brine_and_bottle.desc": "Rozdymka i słona woda pasują do statywu alchemicznego.",
  "proficiency.synergy.fish_story": "Rybackie opowieści",
  "proficiency.synergy.fish_story.desc": "Była taka duża, i rośnie przy każdym opowiadaniu.",
  "proficiency.synergy.cutpurses_flight": "Ucieczka rzezimieszka",
  "proficiency.synergy.cutpurses_flight.desc": "Przez mur, po dachu, i już cię nie ma.",
  "proficiency.synergy.running_start": "Z rozpędu",
  "proficiency.synergy.running_start.desc": "Sprint, skok i lądowanie prosto na nich.",
  "proficiency.synergy.freerunner": "Wolny biegacz",
  "proficiency.synergy.freerunner.desc": "Lądując w biegu po upadku z 4+ bloków, biegniesz dalej z Szybkością II na 2 s.",
  "proficiency.synergy.running_mates": "Ekipa biegowa",
  "proficiency.synergy.running_mates.desc": "Na dobrym biegu nikt nie zostaje z tyłu.",
  "proficiency.synergy.first_responder": "Pierwsza pomoc",
  "proficiency.synergy.first_responder.desc": "Biegniesz, a przyjaciel w promieniu 24 bloków ma poniżej 30% zdrowia: Szybkość II.",
  "proficiency.synergy.hidden_spotter": "Ukryty obserwator",
  "proficiency.synergy.hidden_spotter.desc": "Najlepszy obserwator to ten, którego nikt nie widzi.",
  "proficiency.synergy.hush_of_the_deep": "Cisza głębin",
  "proficiency.synergy.hush_of_the_deep.desc": "Tam na dole jedyną działającą zbroją jest cisza.",
  "proficiency.synergy.high_diver": "Skoczek do wody",
  "proficiency.synergy.high_diver.desc": "Skok do wody z 8+ bloków daje Grację delfina na 6 s.",
  "proficiency.synergy.steeplejack": "Dekarz",
  "proficiency.synergy.steeplejack.desc": "Blok postawiony w trakcie spadania z 3+ bloków przerywa upadek. Raz na 5 s.",
  "proficiency.synergy.pounce": "Skok na ofiarę",
  "proficiency.synergy.pounce.desc": "Atak z góry liczy się jak atak jako pierwszy.",
  "proficiency.synergy.chasm_leaper": "Skoczek nad przepaścią",
  "proficiency.synergy.chasm_leaper.desc": "Wąwozy to po prostu długie schody.",
  "proficiency.synergy.lifeguard": "Ratownik",
  "proficiency.synergy.lifeguard.desc": "Przyjaciel w promieniu 8 bloków z brakiem tchu dostaje Oddychanie pod wodą na 5 s. Raz na minutę.",
  "proficiency.synergy.sump_diver": "Nurek jaskiniowy",
  "proficiency.synergy.sump_diver.desc": "Zalany korytarz to po prostu kolejny tunel.",
  "proficiency.synergy.island_hopper": "Wyspiarz",
  "proficiency.synergy.island_hopper.desc": "Morze to droga bez kamieni milowych.",
  "proficiency.synergy.salt_cure": "Solna kuracja",
  "proficiency.synergy.salt_cure.desc": "Morska woda i właściwa nalewka trzymają cię w zdrowiu.",
  "proficiency.synergy.gearwright": "Kowal trybów",
  "proficiency.synergy.gearwright.desc": "Zębatka to ostrze, które się kręci.",
  "proficiency.synergy.runesmith": "Kowal run",
  "proficiency.synergy.runesmith.desc": "Co zdejmie kamień szlifierski, to runa przywraca.",
  "proficiency.synergy.guild_prices": "Cechowe ceny",
  "proficiency.synergy.guild_prices.desc": "Dobre słowo przy kowadle jest warte kilku poziomów.",
  "proficiency.synergy.wrought_iron": "Kute żelazo",
  "proficiency.synergy.wrought_iron.desc": "Wykuta przez ciebie brama to najpiękniejsza rzecz w pomieszczeniu.",
  "proficiency.synergy.potluck": "Składkowa kolacja",
  "proficiency.synergy.potluck.desc": "Każdy przynosi danie, a ty przynosisz dwa.",
  "proficiency.synergy.sunday_roast": "Niedzielny obiad",
  "proficiency.synergy.sunday_roast.desc": "Jedzenie zjedzone w domu przywraca 2 punkty głodu i 2 nasycenia więcej.",
  "proficiency.synergy.trail_rations": "Prowiant na szlak",
  "proficiency.synergy.trail_rations.desc": "Jedzenie, które wytrzyma, na drogi, które się nie kończą.",
  "proficiency.synergy.mana_tonic": "Tonik many",
  "proficiency.synergy.mana_tonic.desc": "Alchemik wie, z czego jest zrobiona studnia mocy.",
  "proficiency.synergy.field_medic": "Sanitariusz",
  "proficiency.synergy.field_medic.desc": "Rzucona mikstura leczenia zdejmuje też jeden zły efekt z każdego trafionego przyjaciela.",
  "proficiency.synergy.starward": "Gwiezdna osłona",
  "proficiency.synergy.starward.desc": "Osłony kreślone w świetle gwiazd trzymają przez całą noc.",
  "proficiency.synergy.ley_hall": "Sala mocy",
  "proficiency.synergy.ley_hall.desc": "Lampy na liniach mocy płoną czymś więcej niż ogniem.",
  "proficiency.synergy.arcane_circle": "Krąg magów",
  "proficiency.synergy.arcane_circle.desc": "Zaklęcia uczone razem rzuca się w jednym rytmie.",
  "proficiency.synergy.battlemage": "Mag bojowy",
  "proficiency.synergy.battlemage.desc": "Im większy wróg, tym głośniejsze echo.",
  "proficiency.synergy.hexbane": "Pogromca uroków",
  "proficiency.synergy.hexbane.desc": "Nieumarli boją się słowa tak samo jak ostrza.",
  "proficiency.synergy.fortifier": "Fortyfikator",
  "proficiency.synergy.fortifier.desc": "Mury z zębatkami w środku.",
  "proficiency.synergy.clockwork_home": "Dom na sprężynach",
  "proficiency.synergy.clockwork_home.desc": "Dom, który sam wykonuje swoje obowiązki.",
  "proficiency.synergy.field_engineer": "Inżynier polowy",
  "proficiency.synergy.field_engineer.desc": "Linie zaopatrzenia to też maszyny.",
  "proficiency.synergy.bounty_road": "Szlak nagród",
  "proficiency.synergy.bounty_road.desc": "Na każdej drodze czeka bestia i nagroda.",
  "proficiency.synergy.witching_hour": "Godzina wiedźm",
  "proficiency.synergy.witching_hour.desc": "Najgorsze stwory nocy to najlepsze trofea.",
  "proficiency.synergy.colossus_bane": "Pogromca kolosów",
  "proficiency.synergy.colossus_bane.desc": "Zabicie moba z 60+ punktami zdrowia zawsze daje ci Zryw.",
  "proficiency.synergy.pathscout": "Zwiadowca",
  "proficiency.synergy.pathscout.desc": "Wskazujesz cel, zanim inni zobaczą drogę.",
  "proficiency.synergy.glowworm_grotto": "Grota świetlików",
  "proficiency.synergy.glowworm_grotto.desc": "Starannie oświetlona jaskinia to pokój wart zatrzymania.",
  "proficiency.synergy.shoring": "Obudowa chodnika",
  "proficiency.synergy.shoring.desc": "Podpierasz strop, zanim mu zaufasz.",
  "proficiency.synergy.root_cellar": "Piwniczka",
  "proficiency.synergy.root_cellar.desc": "Pod każdym dobrym domem jest jaskinia.",
  "proficiency.synergy.hearthstone": "Kamień paleniska",
  "proficiency.synergy.hearthstone.desc": "Sam ułożyłeś kamień, na którym płonie ogień.",
  "proficiency.synergy.housewarming": "Parapetówka",
  "proficiency.synergy.housewarming.desc": "Przytulny pokój w domu także cię karmi: 1 punkt głodu co 30 s.",
  "proficiency.synergy.salon": "Salon",
  "proficiency.synergy.salon.desc": "Piękne wnętrze samo przyciąga dobre towarzystwo.",
```
