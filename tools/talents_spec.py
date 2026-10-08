"""
The design of every talent tree, in one place. `gen_talents.py` turns this into
core/src/main/java/.../perk/TalentTable.java and the talent lines of en_us.json.

Edit here, then run:  python3 tools/gen_talents.py

Node: (id, name, row, col, ranks, cost, effects, special, parents, desc, mat)
  cost     ignored: price() below sets every node's cost per rank from where it sits
           (the last keyword, per_rank, overrides it for one node)
  effects  per rank: XP BON PC PP AD AC DW TMP (see PerkEffect)
  special  a tag the event code reads, or None. Its number per rank is in the desc.
  mat      1..4: which of the skill's old material lists this node charges when it fills
Level per row: 0, 10, 30, 60, 90, 100.

Points: one per skill level, 100 at level 100 (2026-09-29; it was one per two levels).
"""

LEVELS = [0, 10, 30, 60, 90, 100]
CAP = {"BON": 0.15, "AC": -0.20}

def price(n):
    """Cost per rank. Deeper costs more, and a one-rank mechanic is priced as one lump.

    The root is 5 x 2, so it fills exactly at level 10 when row 1 opens. Every tree lands
    between 80 and 100 points: a maxed skill fills all of it, a level-50 one gets about half.
    """
    row, ranks = n["row"], n["ranks"]
    if row == 0:
        return 2
    if row == 5:
        return 10
    if ranks == 1:
        return {1: 3, 2: 5, 3: 6, 4: 8}[row]
    return {1: 2, 2: 2, 3: 3, 4: 4}[row]


def N(id, name, row, col, ranks, cost, fx, special, parents, desc, mat=None, per_rank=None):
    n = dict(id=id, name=name, row=row, col=col, ranks=ranks, cost=cost, fx=fx,
             special=special, parents=parents, desc=desc, mat=mat)
    # per_rank overrides price() for the few nodes that have to fit a tree under 100 points.
    n["cost"] = price(n) if per_rank is None else per_rank
    return n

def root(id, name):
    return N(id, name, 0, 2, 5, 1, {"XP": 0.05}, None, [], "Where the tree starts. Fill it to open the branches.", 1)

def cap(id, name, special, desc, parents, **extra):
    return N(id, name, 5, 2, 1, 3, {**CAP, **extra}, special, parents, desc)

TREES = {}

TREES["swords"] = [
    root("swordplay", "Swordplay"),
    N("keen_edge", "Keen Edge", 1, 1, 5, 1, {"BON": 0.05}, None, ["swordplay"], "A sharper blade in a surer hand."),
    N("opening", "Find the Opening", 1, 3, 5, 1, {"PC": 0.10}, None, ["swordplay"], "Perfect Strike comes more often."),
    N("parry", "Parry", 2, 0, 3, 1, {}, "parry", ["keen_edge"], "Holding a sword, 5% chance per rank to turn aside a melee hit completely."),
    N("crosscut", "Crosscut", 2, 2, 3, 1, {"PP": 0.10}, None, ["keen_edge", "opening"], "Perfect Strike cuts deeper."),
    N("hemorrhage", "Hemorrhage", 2, 4, 3, 1, {}, "bleed", ["opening"], "Perfect Strike makes the target bleed (Wither) for 2 seconds per rank."),
    N("executioner", "Executioner", 3, 0, 3, 1, {}, "executioner", ["parry"], "+10% damage per rank to targets under 30% health.", 2),
    N("battle_trance", "Battle Trance", 3, 2, 3, 1, {"AD": 0.15}, None, ["crosscut"], "Berserk lasts longer."),
    N("vampiric_edge", "Vampiric Edge", 3, 4, 3, 1, {}, "lifesteal", ["hemorrhage"], "Perfect Strike heals you 1 health per rank.", 3),
    N("duelist", "Duelist", 4, 2, 1, 3, {"PP": 0.25}, "duelist", ["executioner", "vampiric_edge"], "+30% damage while only one hostile is within 8 blocks.", 4),
    cap("sword_saint", "Sword Saint", "chain_strike", "A Perfect Strike makes your next sword hit within 3 seconds a Perfect Strike too.", ["duelist", "battle_trance"]),
]

TREES["axes"] = [
    root("hewing", "Hewing"),
    N("heavy_blows", "Heavy Blows", 1, 0, 5, 1, {"BON": 0.05}, None, ["hewing"], "Every swing lands harder."),
    N("cleaving_arc", "Cleaving Arc", 1, 2, 3, 1, {}, "wide_cleave", ["hewing"], "Cleave reaches 1 block further per rank."),
    N("bloodlust", "Bloodlust", 1, 4, 5, 1, {"PC": 0.10}, None, ["hewing"], "Cleave comes more often."),
    N("berserker", "Berserker", 2, 0, 3, 1, {}, "berserker", ["heavy_blows"], "+3% damage per rank for every 10% of health you are missing."),
    N("sunder", "Sunder", 2, 2, 3, 1, {}, "sunder", ["cleaving_arc"], "Everything Cleave hits is Weakened for 2 seconds per rank."),
    N("bloodrush", "Bloodrush", 2, 4, 3, 1, {}, "bloodrush", ["bloodlust"], "An axe kill gives Speed and Strength for 2 seconds per rank."),
    N("rampage", "Rampage", 3, 1, 3, 1, {"AD": 0.10, "TMP": 0.20}, None, ["berserker", "sunder"], "Longer Berserk, and a streak that pays more.", 2),
    N("headsman", "Headsman", 3, 3, 5, 1, {"PP": 0.10}, None, ["bloodrush"], "Cleave hits harder.", 3),
    N("undying_rage", "Undying Rage", 4, 2, 1, 3, {}, "undying_rage", ["rampage", "headsman"], "Once every 5 minutes, a killing blow leaves you at 1 health while you hold an axe.", 4),
    cap("warlord", "Warlord", "warlord", "Every axe kill heals you 2 health.", ["undying_rage"]),
]

TREES["maces"] = [
    root("heft", "Heft"),
    N("crushing", "Crushing Weight", 1, 1, 5, 1, {"BON": 0.05}, None, ["heft"], "The mace falls heavier."),
    N("tremor", "Tremor", 1, 3, 5, 1, {"PC": 0.10}, None, ["heft"], "Shockwave comes more often."),
    N("meteor", "Meteor", 2, 0, 5, 1, {}, "meteor", ["crushing"], "+4% damage per rank for every block you fell before the hit, up to 20 blocks."),
    N("aftershock", "Aftershock", 2, 2, 3, 1, {"PP": 0.10}, "aftershock", ["crushing", "tremor"], "Shockwave reaches 1 block further per rank."),
    N("concussion", "Concussion", 2, 4, 3, 1, {}, "concussion", ["tremor"], "Shockwave's main target is slowed to a crawl for 1 second per rank."),
    N("wind_rider", "Wind Rider", 3, 0, 1, 2, {}, "wind_rider", ["meteor"], "For 3 seconds after a mace hit you take no fall damage.", 2),
    N("bell_ringer", "Bell Ringer", 3, 4, 3, 1, {"PP": 0.15}, None, ["concussion"], "Shockwave hits much harder.", 3),
    N("cratermaker", "Cratermaker", 4, 2, 1, 3, {}, "crater", ["wind_rider", "bell_ringer"], "A hit after falling 6 blocks or more is always a Shockwave.", 4),
    cap("titan", "Titan", "launch", "Shockwave throws everything it hits into the air.", ["cratermaker", "aftershock"]),
]

TREES["tridents"] = [
    root("spearcraft", "Spearcraft"),
    N("tidal_edge", "Tidal Edge", 1, 0, 5, 1, {}, "tidal_edge", ["spearcraft"], "+6% trident damage per rank while you stand in water or rain."),
    N("true_aim", "True Aim", 1, 2, 5, 1, {"BON": 0.05}, None, ["spearcraft"], "Tridents hit harder."),
    N("undertow", "Undertow", 1, 4, 3, 1, {}, "harpoon", ["spearcraft"], "A trident proc drags the target toward you, harder per rank."),
    N("deep_breath", "Deep Breath", 2, 1, 1, 2, {}, "trident_breath", ["tidal_edge", "true_aim"], "Holding a trident, you breathe underwater."),
    N("impaler", "Impaler", 2, 3, 5, 1, {"PC": 0.10}, None, ["true_aim", "undertow"], "The signature strike comes more often."),
    N("riptide_surge", "Riptide Surge", 3, 1, 3, 1, {"AD": 0.15}, None, ["deep_breath"], "The ability lasts longer.", 2),
    N("skewer", "Skewer", 3, 3, 5, 1, {"PP": 0.10}, None, ["impaler"], "The signature strike hits harder.", 3),
    N("conductor", "Conductor", 4, 2, 1, 3, {}, "conductor", ["riptide_surge", "skewer"], "A trident proc in a thunderstorm calls lightning down on the target.", 4),
    cap("sea_king", "Sea King", "sea_king", "Holding a trident in water gives you Conduit Power.", ["conductor"]),
]

TREES["unarmed"] = [
    root("knuckles", "Knuckles"),
    N("iron_fist", "Iron Fist", 1, 1, 5, 1, {"BON": 0.05}, None, ["knuckles"], "Bare hands hit harder."),
    N("haymaker", "Haymaker", 1, 3, 5, 1, {"PC": 0.10}, None, ["knuckles"], "The knockout blow comes more often."),
    N("combo", "Combo", 2, 0, 3, 1, {}, "combo", ["iron_fist"], "Each punch within 1.5 seconds of the last adds 5% per rank, up to 5 stacks."),
    N("stagger", "Stagger", 2, 2, 3, 1, {}, "stagger", ["iron_fist", "haymaker"], "The knockout blow Weakens the target for 2 seconds per rank."),
    N("iron_skin", "Iron Skin", 2, 4, 3, 1, {}, "iron_skin", ["haymaker"], "With an empty main hand you take 5% less damage per rank."),
    N("fury", "Fury", 3, 1, 3, 1, {"TMP": 0.30, "AD": 0.10}, None, ["combo", "stagger"], "A longer ability and a streak that pays more.", 2),
    N("dragon_palm", "Dragon Palm", 3, 4, 5, 1, {"PP": 0.10}, None, ["iron_skin"], "The knockout blow hits harder.", 3),
    N("one_inch_punch", "One-Inch Punch", 4, 2, 1, 3, {}, "one_inch", ["fury", "dragon_palm"], "The knockout blow sends its target flying three times as far.", 4),
    cap("the_unbroken", "The Unbroken", "fist_heal", "Every bare-handed hit heals you half a heart.", ["one_inch_punch"]),
]

TREES["blocking"] = [
    root("guard", "Guard"),
    N("reinforced", "Reinforced", 1, 0, 5, 1, {"BON": 0.05}, None, ["guard"], "The shield wears slower and you stand firmer."),
    N("counter", "Counter", 1, 4, 5, 1, {"PC": 0.10}, None, ["guard"], "Riposte comes more often."),
    N("tenacity", "Tenacity", 2, 0, 3, 1, {"DW": 0.20}, None, ["reinforced"], "Dying keeps more of this skill's progress."),
    N("shield_bash", "Shield Bash", 2, 2, 3, 1, {}, "shield_bash", ["reinforced", "counter"], "Blocking a melee hit shoves the attacker back, harder per rank."),
    N("thorns", "Thorned Guard", 2, 4, 5, 1, {"PP": 0.10}, None, ["counter"], "Riposte hits harder."),
    N("bastion", "Bastion", 3, 0, 3, 1, {}, "bastion", ["tenacity"], "While you block, players within 4 blocks take 5% less damage per rank.", 2),
    N("spiked_rim", "Spiked Rim", 3, 4, 3, 1, {}, "spiked_rim", ["thorns"], "Every blocked hit deals 1 damage per rank back to the attacker.", 3),
    N("aegis", "Aegis", 4, 2, 1, 3, {}, "aegis", ["bastion", "spiked_rim"], "Every blocked hit gives you a heart of absorption, up to four.", 4),
    cap("immovable", "Immovable", "immovable", "Holding your shield up, nothing can knock you back. This skill never loses progress to death.", ["aegis", "shield_bash"], DW=0.40),
]

TREES["endurance"] = [
    root("hardening", "Hardening"),
    N("iron_constitution", "Iron Constitution", 1, 0, 5, 1, {"BON": 0.05}, None, ["hardening"], "More maximum health."),
    N("stubborn", "Stubborn", 1, 2, 5, 1, {"PC": 0.10}, None, ["hardening"], "Grit comes more often."),
    N("roll_with_it", "Roll With It", 1, 4, 3, 1, {}, "roll_with_it", ["hardening"], "Knockback moves you 10% less per rank."),
    N("adrenal_mend", "Adrenal Mend", 2, 0, 3, 1, {}, "hurt_regen", ["iron_constitution"], "A hit of 4 damage or more gives Regeneration II for 2 seconds per rank. Once every 15 seconds."),
    N("deep_reserves", "Deep Reserves", 2, 2, 3, 1, {"PP": 0.15}, None, ["iron_constitution", "stubborn"], "Grit heals more."),
    N("scar_tissue", "Scar Tissue", 2, 4, 3, 1, {}, "scar_tissue", ["roll_with_it"], "Every hit leaves a scar, up to 5, and they fade 5 seconds after the last one. Each scar takes 2% per rank off the next hit."),
    N("lean_times", "Lean Times", 3, 1, 3, 1, {}, "lean_times", ["adrenal_mend", "deep_reserves"], "Below half health, hunger drains 15% slower per rank.", 2),
    N("stoneblood", "Stoneblood", 3, 3, 3, 1, {"AD": 0.15}, None, ["scar_tissue"], "Unbreakable lasts longer.", 3),
    N("last_stand", "Last Stand", 4, 2, 1, 3, {}, "last_stand", ["lean_times", "stoneblood"], "Once every 5 minutes, a blow that would kill you leaves you at 1 health with Resistance II for 3 seconds.", 4),
    cap("indomitable", "Indomitable", "indomitable", "Go 10 seconds without taking damage and you grow a heart of absorption every 5 seconds, up to four.", ["last_stand"]),
]

TREES["archery"] = [
    root("fletching", "Fletching"),
    N("steady_aim", "Steady Aim", 1, 0, 5, 1, {"BON": 0.05}, None, ["fletching"], "Arrows hit harder."),
    N("eagle_eye", "Eagle Eye", 1, 2, 5, 1, {"PC": 0.10}, None, ["fletching"], "The perfect shot comes more often."),
    N("quiver", "Deep Quiver", 1, 4, 3, 1, {}, "arrow_saver", ["fletching"], "10% chance per rank that an arrow that hits comes back to you."),
    N("longshot", "Longshot", 2, 0, 3, 1, {}, "longshot", ["steady_aim"], "+2% damage per rank for every 8 blocks of distance, up to 40 blocks."),
    N("piercing", "Piercing Shot", 2, 2, 3, 1, {"PP": 0.15}, None, ["eagle_eye"], "The perfect shot hits harder."),
    N("hunters_mark", "Hunter's Mark", 2, 4, 3, 1, {}, "hunters_mark", ["quiver"], "The perfect shot marks the target: it glows and takes 5% more from you per rank for 8 seconds."),
    N("rain_of_arrows", "Rain of Arrows", 3, 1, 3, 1, {"AD": 0.15}, None, ["longshot", "piercing"], "The ability lasts longer.", 2),
    N("skyhunter", "Skyhunter", 3, 3, 3, 1, {}, "skyhunter", ["hunters_mark"], "+10% damage per rank to anything off the ground.", 3),
    N("deadeye", "Deadeye", 4, 2, 1, 3, {}, "deadeye", ["rain_of_arrows", "skyhunter"], "A fully drawn arrow loosed from a crouch deals 50% more.", 4),
    cap("marksman", "Marksman", "marksman", "Every fifth arrow that hits is a perfect shot.", ["deadeye"]),
]

TREES["crossbows"] = [
    root("windlass", "Windlass"),
    N("heavy_bolts", "Heavy Bolts", 1, 1, 5, 1, {"BON": 0.05}, None, ["windlass"], "Bolts hit harder."),
    N("hair_trigger", "Hair Trigger", 1, 3, 5, 1, {"PC": 0.10}, None, ["windlass"], "The perfect bolt comes more often."),
    N("salvage", "Salvage", 2, 0, 3, 1, {}, "arrow_saver", ["heavy_bolts"], "10% chance per rank that a bolt that hits comes back to you."),
    N("armor_piercer", "Armor Piercer", 2, 2, 3, 1, {}, "armor_piercer", ["heavy_bolts", "hair_trigger"], "+8% damage per rank to targets wearing 10 or more armor."),
    N("kickback", "Kickback", 2, 4, 3, 1, {}, "kickback", ["hair_trigger"], "Bolts knock their target back, harder per rank."),
    N("siege_crew", "Siege Crew", 3, 1, 3, 1, {"PP": 0.15}, None, ["salvage", "armor_piercer"], "The perfect bolt hits harder.", 2),
    N("quickload", "Quickload", 3, 3, 3, 1, {"AD": 0.10, "AC": -0.05}, "surge", ["kickback"], "Each rolled perfect bolt takes 1 second per rank off the ability cooldown.", 3),
    N("ballista", "Ballista", 4, 2, 1, 3, {}, "ballista", ["siege_crew", "quickload"], "A perfect bolt bursts on impact: a small blast that breaks no blocks.", 4),
    cap("siege_master", "Siege Master", "siege_master", "+25% damage to targets more than 20 blocks away.", ["ballista"]),
]

TREES["mining"] = [
    root("pickwork", "Pickwork"),
    N("efficiency", "Efficiency", 1, 0, 5, 1, {"BON": 0.05}, None, ["pickwork"], "Faster mining and more second drops."),
    N("prospector", "Prospector", 1, 2, 5, 1, {"PC": 0.10}, None, ["pickwork"], "Motherlode comes more often."),
    N("geologist", "Geologist", 1, 4, 3, 1, {}, "ore_sense", ["pickwork"], "Mining ore: 5% chance per rank that nearby ore sparkles through the rock."),
    N("vein_miner", "Vein Miner", 2, 0, 3, 1, {}, "vein_miner", ["efficiency"], "Breaking ore also breaks up to 2 connected blocks of the same ore per rank."),
    N("gem_cutter", "Gem Cutter", 2, 2, 3, 1, {"PP": 0.10}, None, ["prospector"], "Motherlode gives more."),
    N("dark_sight", "Dark Sight", 2, 4, 1, 2, {}, "dark_sight", ["geologist"], "Below Y 30 with a pickaxe in hand, you see in the dark."),
    N("rich_veins", "Rich Veins", 3, 1, 3, 1, {"PP": 0.15}, None, ["vein_miner", "gem_cutter"], "Motherlode gives much more.", 2),
    N("hard_hat", "Hard Hat", 3, 4, 2, 1, {"DW": 0.25}, "hard_hat", ["dark_sight"], "Falling blocks and stalactites deal 50% less per rank. Dying keeps more of this skill's progress.", 3),
    N("smelter", "Smelter", 4, 2, 1, 3, {"PP": 0.25}, "auto_smelt", ["rich_veins"], "Ore comes up already smelted.", 4),
    cap("mountain_king", "Mountain King", "magnet", "Everything you mine goes straight into your inventory.", ["smelter", "hard_hat"]),
]

TREES["woodcutting"] = [
    root("lumberjack", "Lumberjack"),
    N("sharp_axe", "Sharp Axe", 1, 0, 5, 1, {"BON": 0.05}, None, ["lumberjack"], "Faster chopping and more second drops."),
    N("timber_call", "Timber!", 1, 2, 5, 1, {"PC": 0.10}, None, ["lumberjack"], "Timber comes more often."),
    N("sapling_keeper", "Sapling Keeper", 1, 4, 3, 1, {}, "replant_sapling", ["lumberjack"], "Chopping the bottom log of a tree: 33% chance per rank to plant its sapling."),
    N("canopy", "Canopy", 2, 0, 1, 2, {}, "timber_greedy", ["sharp_axe"], "Timber takes the leaves too, and fells much bigger trees."),
    N("heartwood", "Heartwood", 2, 2, 3, 1, {"PP": 0.15}, None, ["timber_call"], "Timber fells bigger trees and gives more."),
    N("forager", "Forager", 2, 4, 3, 1, {}, "forager", ["sapling_keeper"], "Breaking leaves: 10% chance per rank for an apple."),
    N("woodland_stride", "Woodland Stride", 3, 1, 3, 1, {}, "woodland_stride", ["canopy", "heartwood"], "Timber gives you Speed for 3 seconds per rank.", 2),
    N("seasoned", "Seasoned", 3, 3, 3, 1, {"AD": 0.15}, None, ["forager"], "Lumberfall lasts longer.", 3),
    N("sawmill", "Sawmill", 4, 2, 1, 3, {}, "sawmill", ["woodland_stride", "seasoned"], "Everything Timber fells goes straight into your inventory.", 4),
    cap("elder_of_the_grove", "Elder of the Grove", "full_replant", "Every tree you fell replants itself.", ["sawmill"]),
]

TREES["excavation"] = [
    root("spadework", "Spadework"),
    N("quick_dig", "Quick Dig", 1, 1, 5, 1, {"BON": 0.05}, None, ["spadework"], "Faster digging and more second drops."),
    N("landslide_lore", "Landslide Lore", 1, 3, 5, 1, {"PC": 0.10}, None, ["spadework"], "Landslide comes more often."),
    N("archaeologist", "Archaeologist", 2, 0, 3, 1, {}, "archaeology", ["quick_dig"], "Dirt, sand and gravel: 1% chance per rank to turn up a find (bone, nugget, clay, emerald...)."),
    N("wide_shovel", "Wide Shovel", 2, 2, 1, 2, {}, "landslide_wide", ["quick_dig", "landslide_lore"], "Landslide clears a 5x5 face instead of 3x3."),
    N("sifter", "Sifter", 2, 4, 3, 1, {}, "sifter", ["landslide_lore"], "Gravel: 15% chance per rank to also drop flint."),
    N("earthmover", "Earthmover", 3, 1, 3, 1, {"PP": 0.15}, None, ["archaeologist", "wide_shovel"], "Landslide gives more.", 2),
    N("mole", "Mole", 3, 3, 3, 1, {}, "mole", ["sifter"], "Each block you dig: 3% chance per rank to fill 1 hunger.", 3),
    N("fossil_hunter", "Fossil Hunter", 4, 2, 1, 3, {}, "fossil", ["earthmover", "mole"], "A Landslide rolls for a find once for every three blocks it clears.", 4),
    cap("shaper_of_earth", "Shaper of Earth", "gravity_well", "Digging sand or gravel also digs the column that would fall on you.", ["fossil_hunter"]),
]

TREES["farming"] = [
    root("tilling", "Tilling"),
    N("bountiful", "Bountiful", 1, 0, 5, 1, {"BON": 0.05}, None, ["tilling"], "More second harvests."),
    N("harvest_moon", "Harvest Moon", 1, 2, 5, 1, {"PC": 0.10}, None, ["tilling"], "Bountiful Harvest comes more often."),
    N("fertile_soil", "Fertile Soil", 1, 4, 3, 1, {}, "fertile", ["tilling"], "Every 5 seconds, crops within 3 blocks of you: 5% chance per rank to grow a stage."),
    N("seed_saver", "Seed Saver", 2, 0, 3, 1, {}, "seed_saver", ["bountiful"], "Harvesting a crop: 15% chance per rank for one extra of its seed."),
    N("green_thumb", "Green Thumb", 2, 2, 1, 2, {}, "always_replant", ["bountiful", "harvest_moon"], "Every crop you harvest replants itself."),
    N("husbandry", "Husbandry", 2, 4, 3, 1, {}, "twins", ["fertile_soil"], "Breeding animals trains Farming, with a 10% chance per rank of twins."),
    N("scythe", "Scythe Sweep", 3, 1, 2, 1, {}, "scythe", ["seed_saver", "green_thumb"], "Harvesting with a hoe also harvests ripe crops within 1 block per rank.", 2),
    N("harvest_festival", "Harvest Festival", 3, 4, 3, 1, {"PP": 0.15}, None, ["husbandry"], "Bountiful Harvest gives more.", 3),
    N("druid", "Druid", 4, 2, 1, 3, {}, "druid", ["scythe", "harvest_festival"], "Bone meal trains Farming and is not used up 25% of the time.", 4),
    cap("harvest_lord", "Harvest Lord", "golden_crop", "Each harvest: 2% chance for a golden carrot or a golden apple.", ["druid"]),
]

TREES["fishing"] = [
    root("casting", "Casting"),
    N("patient", "Patience", 1, 1, 5, 1, {"BON": 0.05}, None, ["casting"], "The rod lasts longer and catches double more often."),
    N("lucky_line", "Lucky Line", 1, 3, 5, 1, {"PC": 0.10}, None, ["casting"], "Treasure comes more often."),
    N("double_hook", "Double Hook", 2, 0, 3, 1, {}, "double_hook", ["patient"], "10% chance per rank that a catch brings a second fish."),
    N("deep_waters", "Deep Waters", 2, 2, 3, 1, {"PP": 0.15}, None, ["patient", "lucky_line"], "Treasure rolls more than once."),
    N("fresh_catch", "Fresh Catch", 2, 4, 3, 1, {}, "fresh_catch", ["lucky_line"], "20% chance per rank that raw fish come up already cooked."),
    N("rain_dancer", "Rain Dancer", 3, 1, 3, 1, {}, "rain_angler", ["double_hook", "deep_waters"], "While it rains, catches give 15% more XP per rank.", 2),
    N("old_salt", "Old Salt", 3, 3, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["fresh_catch"], "The ability lasts longer; a streak pays more.", 3),
    N("sunken_treasure", "Sunken Treasure", 4, 2, 1, 3, {}, "sunken_treasure", ["rain_dancer", "old_salt"], "Treasure also brings up an enchanted book.", 4),
    cap("master_angler", "Master Angler", "unbreakable_rod", "Fishing never costs rod durability.", ["sunken_treasure"]),
]

TREES["running"] = [
    # "endurance" keeps its id (saved ranks are keyed by it) but shows as Long Haul since the
    # Endurance skill arrived, so one word does not name two things.
    root("stride", "Stride"),
    N("long_legs", "Long Legs", 1, 0, 5, 1, {"BON": 0.05}, None, ["stride"], "Faster sprinting."),
    N("second_wind", "Second Wind", 1, 2, 5, 1, {"PC": 0.10}, None, ["stride"], "Burst of speed comes more often."),
    N("endurance", "Long Haul", 1, 4, 3, 1, {}, "endurance", ["stride"], "Sprinting uses 10% less hunger per rank."),
    N("parkour", "Parkour", 2, 0, 1, 2, {}, "parkour", ["long_legs"], "While sprinting you step up full blocks without jumping."),
    N("adrenaline", "Adrenaline", 2, 2, 3, 1, {"PP": 0.20}, None, ["second_wind"], "Burst of speed lasts longer."),
    N("slipstream", "Slipstream", 2, 4, 1, 2, {}, "slipstream", ["endurance"], "Players within 6 blocks of you get Speed while you sprint."),
    N("escape_artist", "Escape Artist", 3, 1, 3, 1, {}, "escape", ["parkour", "adrenaline"], "Hit below 30% health: Speed II for 2 seconds per rank. Once a minute.", 2),
    N("roadrunner", "Roadrunner", 3, 3, 3, 1, {"AD": 0.15}, None, ["slipstream"], "The ability lasts longer.", 3),
    N("windwalker", "Windwalker", 4, 2, 1, 3, {}, "windwalker", ["escape_artist", "roadrunner"], "Sprint for 8 seconds straight and you keep Speed for as long as you keep going.", 4),
    cap("fleetfoot", "Fleetfoot", "ghost_step", "While sprinting, 15% chance to dodge a melee hit.", ["windwalker"]),
]

TREES["sneaking"] = [
    root("soft_step", "Soft Step"),
    N("quiet_feet", "Quiet Feet", 1, 1, 5, 1, {"BON": 0.05}, None, ["soft_step"], "Faster while crouched."),
    N("vanish", "Vanish", 1, 3, 5, 1, {"PC": 0.10}, None, ["soft_step"], "Vanishing comes more often."),
    N("unseen", "Unseen", 2, 0, 3, 1, {}, "unseen", ["quiet_feet"], "Crouched, mobs notice you from 10% less far per rank."),
    N("backstab", "Backstab", 2, 2, 3, 1, {}, "backstab", ["quiet_feet", "vanish"], "+10% damage per rank to a mob hit from behind."),
    N("cat_landing", "Cat Landing", 2, 4, 3, 1, {}, "cat_landing", ["vanish"], "Crouched, you take 10% less fall damage per rank."),
    N("shadowmeld", "Shadowmeld", 3, 1, 3, 1, {"PP": 0.20}, None, ["unseen", "backstab"], "You stay vanished for longer.", 2),
    N("pickpocket", "Pickpocket", 3, 3, 3, 1, {}, "pickpocket", ["cat_landing"], "A mob you kill from a crouch: 10% chance per rank to drop its loot twice.", 3),
    N("assassin", "Assassin", 4, 2, 1, 3, {}, "assassinate", ["shadowmeld", "pickpocket"], "While invisible, a hit on a mob that is not targeting you deals double.", 4),
    cap("nightblade", "Nightblade", "still_shadow", "Crouch still for 3 seconds and you become invisible until you move.", ["assassin"]),
]

TREES["jumping"] = [
    root("spring", "Spring"),
    N("high_jump", "High Jump", 1, 0, 5, 1, {"BON": 0.05}, None, ["spring"], "Jump higher, land softer."),
    N("safe_landing", "Safe Landing", 1, 4, 5, 1, {"PC": 0.10}, None, ["spring"], "A landing that costs nothing comes more often."),
    N("leap", "Leap", 2, 0, 3, 1, {}, "leap", ["high_jump"], "Sprint-jumps carry you 5% further per rank."),
    N("feather_weight", "Feather Weight", 2, 2, 3, 1, {}, "feather", ["high_jump", "safe_landing"], "10% less fall damage per rank."),
    N("stomp", "Stomp", 2, 4, 3, 1, {}, "stomp", ["safe_landing"], "Landing on a mob from 3 blocks or more deals 2 damage per rank to it."),
    N("cat_reflexes", "Cat Reflexes", 3, 1, 3, 1, {"AD": 0.15}, None, ["leap", "feather_weight"], "The ability lasts longer.", 2),
    N("bounce", "Bounce", 3, 3, 1, 2, {}, "bounce", ["stomp"], "A landing that costs nothing bounces you back up.", 3),
    N("skywalker", "Skywalker", 4, 2, 1, 3, {}, "auto_slow_fall", ["cat_reflexes", "bounce"], "Falling more than 12 blocks gives you Slow Falling. Once every 30 seconds.", 4),
    cap("zephyr", "Zephyr", "zephyr", "No fall ever deals more than 4 damage.", ["skywalker"]),
]

TREES["swimming"] = [
    root("strokes", "Strokes"),
    N("streamline", "Streamline", 1, 1, 5, 1, {"BON": 0.05}, None, ["strokes"], "Faster swimming and longer breath."),
    N("dolphin", "Dolphin", 1, 3, 5, 1, {"PC": 0.10}, None, ["strokes"], "Dolphin's Grace comes more often."),
    N("deep_lungs", "Deep Lungs", 2, 0, 3, 1, {}, "deep_lungs", ["streamline"], "Drowning deals 25% less damage per rank."),
    N("current_rider", "Current Rider", 2, 2, 3, 1, {"PP": 0.20}, None, ["streamline", "dolphin"], "Dolphin's Grace lasts longer."),
    N("aqua_eyes", "Aquatic Eyes", 2, 4, 1, 2, {}, "aqua_vision", ["dolphin"], "You see clearly underwater (Night Vision while submerged)."),
    N("pearl_diver", "Pearl Diver", 3, 1, 1, 2, {}, "pearl_diver", ["deep_lungs", "current_rider"], "Underwater you break blocks at full speed.", 2),
    N("riptide_kick", "Riptide Kick", 3, 3, 3, 1, {"AD": 0.15}, None, ["aqua_eyes"], "The ability lasts longer.", 3),
    N("leviathan_blood", "Leviathan Blood", 4, 2, 1, 3, {}, "sea_healing", ["pearl_diver", "riptide_kick"], "In water, you slowly heal while hurt.", 4),
    cap("tidewalker", "Tidewalker", "tidewalker", "Dolphin's Grace whenever you are in water.", ["leviathan_blood"]),
]

TREES["smithing"] = [
    root("hammer", "Hammer and Tongs"),
    N("haggler", "Haggler", 1, 0, 5, 1, {"BON": 0.05}, None, ["hammer"], "Anvils cost less and break less."),
    N("masterwork", "Masterwork", 1, 2, 5, 1, {"PC": 0.10}, None, ["hammer"], "A second piece off the bench comes more often."),
    N("whetstone", "Whetstone", 1, 4, 3, 1, {}, "tough_tools", ["hammer"], "Crafted gear: 15% chance per rank to come with Unbreaking I."),
    N("reforge", "Reforge", 2, 0, 3, 1, {}, "reforge", ["haggler"], "An anvil repair: 10% chance per rank to restore the item completely."),
    N("fine_edge", "Fine Edge", 2, 2, 3, 1, {"PP": 0.15}, None, ["masterwork"], "Masterwork can turn out a third piece."),
    N("temper", "Temper", 2, 4, 3, 1, {}, "temper", ["whetstone"], "Crafted weapons: 10% chance per rank to come with Sharpness I."),
    N("weaponwright", "Weaponwright", 3, 0, 1, 1, {}, "weaponwright", ["reforge"], "Learn to forge a Butcher's Cleaver and a Duelist's Rapier. Lay the parts on an anvil and strike it with a Smithing Hammer."),
    N("forgefire", "Forgefire", 3, 1, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["reforge", "fine_edge"], "The ability lasts longer; a streak pays more.", 2),
    N("disenchanter", "Disenchanter", 3, 3, 1, 2, {}, "disenchanter", ["temper"], "The grindstone gives back twice the experience.", 3),
    N("legendary", "Legendary", 4, 2, 1, 3, {}, "legendary", ["forgefire", "disenchanter"], "A Masterwork on diamond or netherite gear adds a random enchantment.", 4),
    cap("grand_artisan", "Grand Artisan", "no_prior_work", "Anvil work never makes an item more expensive to work again.", ["legendary"]),
]

TREES["cooking"] = [
    root("knife_work", "Knife Work"),
    N("seasoning", "Seasoning", 1, 1, 5, 1, {"BON": 0.05}, None, ["knife_work"], "More second helpings from the furnace."),
    N("second_helping", "Second Helping", 1, 3, 5, 1, {"PC": 0.10}, None, ["knife_work"], "Extra portions come more often."),
    N("camp_kitchen", "Camp Kitchen", 1, 4, 1, 1, {}, "camp_kitchen", ["knife_work"], "Learn two cauldron dishes: Miner's Stew (Haste) and Trail Ration (Speed). Drop the ingredients into a water cauldron and stir it with a Ladle."),
    N("hearty", "Hearty", 2, 0, 3, 1, {}, "hearty", ["seasoning"], "Food you eat fills 1 more hunger per rank."),
    N("banquet", "Banquet", 2, 2, 3, 1, {"PP": 0.15}, None, ["seasoning", "second_helping"], "Extra portions are bigger."),
    N("comfort_food", "Comfort Food", 2, 4, 3, 1, {}, "comfort", ["second_helping"], "Eating gives Regeneration for 2 seconds per rank."),
    N("iron_stomach", "Iron Stomach", 3, 1, 1, 2, {}, "iron_stomach", ["hearty", "banquet"], "Raw, rotten or poisonous food never makes you sick.", 2),
    N("shared_meal", "Shared Meal", 3, 3, 3, 1, {}, "shared_meal", ["comfort_food"], "When you eat, players within 8 blocks get 1 hunger per rank.", 3),
    N("family_recipe", "Family Recipe", 3, 4, 1, 1, {}, "family_recipe", ["comfort_food"], "Learn two more cauldron dishes: Warrior's Pierogi (Strength) and Fisherman's Chowder (Luck, Water Breathing)."),
    N("gourmet", "Gourmet", 4, 2, 1, 3, {}, "gourmet", ["iron_stomach", "shared_meal"], "Eat five different foods in a row: Absorption II and Haste for a minute.", 4),
    cap("master_chef", "Master Chef", "double_saturation", "Everything you eat gives double saturation.", ["gourmet"]),
]

TREES["alchemy"] = [
    root("distilling", "Distilling"),
    N("potency", "Potency", 1, 0, 5, 1, {"BON": 0.05}, None, ["distilling"], "Potions you drink last longer."),
    N("efficient_draught", "Efficient Draught", 1, 2, 5, 1, {"PC": 0.10}, None, ["distilling"], "Keeping the potion you drank comes more often."),
    N("catalyst", "Catalyst", 1, 4, 3, 1, {}, "catalyst", ["distilling"], "A potion you drink: 10% chance per rank to work one level stronger."),
    N("antidote", "Antidote", 2, 0, 3, 1, {}, "antidote", ["potency"], "Harmful effects on you last 10% shorter per rank."),
    N("strong_brew", "Strong Brew", 2, 2, 3, 1, {"PP": 0.15}, None, ["efficient_draught"], "Efficient Draught can return two."),
    N("brewers_eye", "Brewer's Eye", 2, 4, 3, 1, {}, "brewers_eye", ["catalyst"], "Brewing a batch: 20% chance per rank to brew one extra potion."),
    N("runescribe", "Runescribe", 3, 0, 1, 1, {}, "runescribe", ["antidote"], "Learn two enchantments no table offers: Lifedrinker and Magnetism. Lay the reagents on an enchanting table and use a Book on it."),
    N("elixir_lore", "Elixir Lore", 3, 1, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["antidote", "strong_brew"], "The ability lasts longer; a streak pays more.", 2),
    N("distillery", "Distillery", 3, 3, 1, 2, {}, "overflow", ["brewers_eye"], "While the ability runs, Alchemy earns double XP.", 3),
    N("panacea", "Panacea", 4, 2, 1, 3, {}, "panacea", ["elixir_lore", "distillery"], "Drinking any potion also cures one harmful effect.", 4),
    cap("grand_alchemist", "Grand Alchemist", "shared_brew", "Potions you drink also reach players within 5 blocks.", ["panacea"]),
]

TREES["spellcasting"] = [
    root("cantrips", "Cantrips"),
    N("arcane_focus", "Arcane Focus", 1, 1, 5, 1, {"BON": 0.05}, None, ["cantrips"], "Spells hit harder."),
    N("spell_surge", "Spell Surge", 1, 3, 5, 1, {"PC": 0.10}, None, ["cantrips"], "Empowered spells come more often."),
    N("mana_font", "Mana Font", 2, 0, 3, 1, {}, "max_mana", ["arcane_focus"], "+15% maximum mana per rank (Ars Nouveau)."),
    N("overcharge", "Overcharge", 2, 2, 3, 1, {"PP": 0.15}, None, ["arcane_focus", "spell_surge"], "Empowered spells hit harder."),
    N("spell_ward", "Spell Ward", 2, 4, 3, 1, {}, "spell_ward", ["spell_surge"], "Magic damage against you is 8% lower per rank."),
    N("deep_well", "Deep Well", 3, 1, 3, 1, {}, "mana_regen", ["mana_font", "overcharge"], "+15% mana regeneration per rank (Ars Nouveau).", 2),
    N("arcane_rhythm", "Arcane Rhythm", 3, 3, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["spell_ward"], "The ability lasts longer; a streak pays more.", 3),
    N("echo_cast", "Echo Cast", 4, 2, 1, 3, {}, "echo_cast", ["deep_well", "arcane_rhythm"], "An empowered spell also hits the nearest other enemy for half.", 4),
    cap("magus", "Magus", "magus", "An empowered spell gives you Speed and Haste for 5 seconds.", ["echo_cast"]),
]

TREES["engineering"] = [
    root("tinkering", "Tinkering"),
    N("precision_parts", "Precision Parts", 1, 0, 5, 1, {"BON": 0.05}, None, ["tinkering"], "Machine crafts sometimes hand back one of what you made."),
    N("spare_parts", "Spare Parts", 1, 2, 5, 1, {"PC": 0.10}, None, ["tinkering"], "A free machine comes more often."),
    N("wrench_wizard", "Wrench Wizard", 1, 4, 3, 1, {}, "quick_hands", ["tinkering"], "Machine blocks break 30% faster per rank."),
    N("cogsmith", "Cogsmith", 2, 0, 3, 1, {}, "cogsmith", ["precision_parts"], "Crafting Create parts: 15% chance per rank for one extra."),
    N("overclock", "Overclock", 2, 2, 3, 1, {"PP": 0.15}, None, ["spare_parts"], "A free machine can come as two."),
    N("redstone_savant", "Redstone Savant", 2, 4, 3, 1, {}, "redstone", ["wrench_wizard"], "Crafting vanilla redstone parts trains Engineering, with a 10% chance per rank for one extra."),
    N("assembly_line", "Assembly Line", 3, 1, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["cogsmith", "overclock"], "The ability lasts longer; a streak pays more.", 2),
    N("blueprint", "Blueprint", 3, 3, 1, 2, {}, "blueprint", ["redstone_savant"], "Placing a machine block: 20% chance it costs nothing.", 3),
    N("chief_engineer", "Chief Engineer", 4, 2, 1, 3, {}, "overflow", ["assembly_line", "blueprint"], "While the ability runs, Engineering earns double XP.", 4),
    cap("grand_engineer", "Grand Engineer", "tinkers_luck", "Engineering procs also drop a Masterwork Ingot.", ["chief_engineer"]),
]

TREES["beastslaying"] = [
    root("tracking", "Tracking"),
    N("monster_lore", "Monster Lore", 1, 1, 5, 1, {"BON": 0.05}, None, ["tracking"], "More damage to the pack's creatures."),
    N("trophy_hunter", "Trophy Hunter", 1, 3, 5, 1, {"PC": 0.10}, None, ["tracking"], "Hunter's Charms come more often."),
    N("bane", "Bane", 2, 0, 3, 1, {}, "bane", ["monster_lore"], "+5% damage per rank to undead and arthropods."),
    N("trophy", "Trophy", 2, 2, 3, 1, {"PP": 0.15}, None, ["monster_lore", "trophy_hunter"], "A proc can drop two charms."),
    N("scavenger", "Scavenger", 2, 4, 3, 1, {}, "scavenger", ["trophy_hunter"], "A modded creature you kill: 10% chance per rank to drop its loot twice."),
    N("giant_slayer", "Giant Slayer", 3, 1, 3, 1, {}, "giant_slayer", ["bane", "trophy"], "+5% damage per rank to anything with twice your maximum health.", 2),
    N("hunters_instinct", "Hunter's Instinct", 3, 3, 3, 1, {"AD": 0.15}, None, ["scavenger"], "The ability lasts longer.", 3),
    N("boss_bane", "Boss Bane", 4, 2, 1, 3, {}, "boss_bane", ["giant_slayer", "hunters_instinct"], "+20% damage to bosses, and a boss kill drops two extra charms.", 4),
    cap("legend", "Legend", "legend", "A boss kill gives you Regeneration II and Strength for 30 seconds.", ["boss_bane"]),
]

TREES["wayfaring"] = [
    root("wanderlust", "Wanderlust"),
    N("trailwise", "Trailwise", 1, 0, 5, 1, {"BON": 0.05}, None, ["wanderlust"], "You walk faster (half your passive, while not sprinting)."),
    N("far_horizons", "Far Horizons", 1, 2, 5, 1, {"PC": 0.10}, None, ["wanderlust"], "Wanderer's Tokens come more often."),
    N("fresh_air", "Fresh Air", 1, 4, 3, 1, {}, "fresh_air", ["wanderlust"], "Each new biome fills 2 hunger per rank."),
    N("pathfinder", "Pathfinder", 2, 0, 3, 1, {}, "trail_speed", ["trailwise"], "On dirt paths you move 10% faster per rank."),
    N("seasoned_traveler", "Seasoned Traveler", 2, 2, 3, 1, {"PP": 0.15}, None, ["far_horizons"], "A proc can drop two tokens."),
    N("campfire", "Campfire Tales", 2, 4, 1, 2, {}, "campfire", ["fresh_air"], "Within 3 blocks of a lit campfire, you regenerate."),
    N("inspired", "Inspired", 3, 1, 3, 1, {}, "inspired", ["pathfinder", "seasoned_traveler"], "A new biome makes every skill earn 10% more XP per rank for 60 seconds.", 2),
    N("globetrotter", "Globetrotter", 3, 3, 3, 1, {"AD": 0.15}, None, ["campfire"], "The ability lasts longer.", 3),
    N("world_walker", "World Walker", 4, 2, 1, 3, {}, "world_walker", ["inspired", "globetrotter"], "Changing dimension gives Resistance and Slow Falling for 10 seconds.", 4),
    cap("legend_of_the_road", "Legend of the Road", "convoy_speed", "Players within 24 blocks share your walking speed.", ["world_walker"]),
]

TREES["spelunking"] = [
    root("torchbearer", "Torchbearer"),
    N("cave_lore", "Cave Lore", 1, 1, 5, 1, {"BON": 0.05}, None, ["torchbearer"], "Underground you take less damage (40% of your passive)."),
    N("cave_sense", "Cave Sense", 1, 3, 5, 1, {"PC": 0.10}, None, ["torchbearer"], "Cave Sense comes more often."),
    N("echolocation", "Echolocation", 2, 0, 3, 1, {}, "echolocation", ["cave_lore"], "Every 10 seconds underground, ore within 3 blocks plus 1 per rank sparkles."),
    N("glowstone_heart", "Glowstone Heart", 2, 2, 3, 1, {"PP": 0.15}, None, ["cave_lore", "cave_sense"], "Cave Sense reaches further and lasts longer."),
    N("rope_climber", "Rope Climber", 2, 4, 1, 2, {}, "climber", ["cave_sense"], "You climb ladders and vines twice as fast."),
    N("deep_dweller", "Deep Dweller", 3, 1, 3, 1, {}, "deep_dweller", ["echolocation", "glowstone_heart"], "Below Y 0, you break blocks 10% faster per rank.", 2),
    N("warden_whisper", "Warden's Whisper", 3, 3, 3, 1, {}, "sculk_silence", ["rope_climber"], "Darkness on you lasts 25% shorter per rank.", 3),
    N("cave_in", "Cave-In Survivor", 4, 2, 1, 3, {}, "cave_in", ["deep_dweller", "warden_whisper"], "Suffocation, falling blocks and stalactites cannot hurt you.", 4),
    cap("lord_of_the_deep", "Lord of the Deep", "deep_sight", "Below Y 0, you always see in the dark.", ["cave_in"]),
]

TREES["masonry"] = [
    root("mortar", "Mortar"),
    N("thrifty", "Thrifty", 1, 1, 5, 1, {"BON": 0.05}, None, ["mortar"], "Placed blocks come back more often."),
    N("rapid_build", "Rapid Build", 1, 3, 5, 1, {"PC": 0.10}, None, ["mortar"], "A triple refund comes more often."),
    N("long_arm", "Long Arm", 2, 0, 3, 1, {}, "long_arm", ["thrifty"], "Half a block more reach per rank."),
    N("stonecutter", "Stonecutter", 2, 2, 3, 1, {"PP": 0.15}, None, ["thrifty", "rapid_build"], "Triple refunds can be bigger."),
    N("safety_harness", "Safety Harness", 2, 4, 3, 1, {}, "harness", ["rapid_build"], "Holding a block, you take 15% less fall damage per rank."),
    N("cornerstone", "Cornerstone", 3, 1, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["long_arm", "stonecutter"], "The ability lasts longer; a streak pays more.", 2),
    N("bulk_order", "Bulk Order", 3, 3, 3, 1, {}, "bulk_order", ["safety_harness"], "Every 10th identical full block in a row refunds 1 per rank.", 3),
    N("master_mason", "Master Mason", 4, 2, 1, 3, {}, "free_place", ["cornerstone", "bulk_order"], "Some blocks you place cost nothing at all.", 4),
    cap("grand_architect", "Grand Architect", "architect_reach", "Two more blocks of reach, always.", ["master_mason"]),
]

TREES["decorating"] = [
    root("eye_for_detail", "Eye for Detail"),
    N("thrift_shop", "Thrift Shop", 1, 0, 5, 1, {"BON": 0.05}, None, ["eye_for_detail"], "Placed decorations come back more often."),
    N("inspiration", "Inspiration", 1, 4, 5, 1, {"PC": 0.10}, None, ["eye_for_detail"], "A triple refund comes more often."),
    N("hygge", "Hygge", 2, 0, 3, 1, {}, "hygge", ["thrift_shop"], "A cozy room needs 2 fewer decorations per rank."),
    N("feng_shui", "Feng Shui", 2, 2, 3, 1, {"PP": 0.15}, None, ["thrift_shop", "inspiration"], "Triple refunds can be bigger."),
    N("lamplighter", "Lamplighter", 2, 4, 3, 1, {}, "lamplighter", ["inspiration"], "Placing a light: 15% chance per rank it comes back."),
    N("host", "The Host", 3, 0, 3, 1, {}, "host", ["hygge"], "Your cozy room also warms players within 2 blocks per rank.", 2),
    N("curator", "Curator", 3, 4, 3, 1, {"AD": 0.15, "TMP": 0.20}, None, ["lamplighter"], "The ability lasts longer; a streak pays more.", 3),
    N("master_decorator", "Master Decorator", 4, 2, 1, 3, {}, "free_place", ["host", "curator"], "Some blocks you place cost nothing at all.", 4),
    cap("tastemaker", "Tastemaker", "tastemaker", "A cozy room gives Regeneration II and Luck.", ["master_decorator", "feng_shui"]),
]

# Social (idea 35, 2026-09-30): the tree mostly improves the company bonus itself. Numbers are
# in skill/SocialMath.java; keep the descriptions in step with them.
TREES["social"] = [
    root("fellowship", "Fellowship"),
    N("kinship", "Kinship", 1, 0, 2, 1, {"BON": 0.125}, None, ["fellowship"], "The company bonus grows bigger."),
    N("open_circle", "Open Circle", 1, 2, 3, 1, {}, "company_radius", ["fellowship"], "Company counts from 4 blocks further away per rank."),
    N("good_word", "A Good Word", 1, 4, 2, 1, {"PC": 0.25}, None, ["fellowship"], "Good Company comes more often."),
    N("old_friends", "Old Friends", 2, 0, 3, 1, {}, "camaraderie_up", ["kinship"], "Camaraderie grows by 5 points per rank: +15% becomes +30% at full."),
    N("stay_a_while", "Stay a While", 2, 2, 3, 1, {}, "company_linger", ["open_circle"], "The company bonus lasts 10 seconds per rank after the others walk away."),
    N("teaching", "Teaching", 2, 4, 3, 1, {}, "teaching", ["good_word"], "Players near you with a lower Social level earn 4% more XP per rank."),
    N("patient_mentor", "Patient Mentor", 3, 0, 3, 1, {}, "mentor_gap", ["old_friends"], "Someone counts as your mentor with 4 fewer levels of lead per rank.", 2),
    N("strength_in_numbers", "Strength in Numbers", 3, 2, 3, 1, {}, "company_crowd", ["stay_a_while"], "2% more company XP per rank for each extra player near you, up to three."),
    N("wise_counsel", "Wise Counsel", 3, 4, 3, 1, {}, "mentor_up", ["teaching"], "The mentor bonus grows by 5 points per rank: +25% becomes +40% at full.", 3),
    # Rested XP and teaching (docs/RESTED-AND-TEACHING.md). Three nodes that make the teacher better
    # at it; numbers are in skill/RestedMath.java. Priced 2 a rank so the tree stays at 100 points.
    N("quick_study", "Quick Study", 2, 3, 2, 1, {}, "teach_rate", ["good_word"], "The rested XP you teach fills 15% faster per rank.", None, 2),
    N("wide_classroom", "Wide Classroom", 3, 3, 2, 1, {}, "teach_radius", ["quick_study"], "Students count from 6 blocks further away per rank when you teach.", None, 2),
    N("teachers_pride", "Teacher's Pride", 4, 4, 2, 1, {}, "teacher_cut", ["wise_counsel", "wide_classroom"], "You earn 5 points more of the XP your students spend per rank: 25% becomes 35% at full.", None, 2),
    N("in_step", "In Step", 4, 2, 1, 3, {}, "in_step", ["patient_mentor", "wise_counsel"], "With company near, a tempo chain survives a pause twice as long, in every skill.", 4),
    cap("heart_of_the_group", "Heart of the Group", "heart_of_group", "Anyone near you who is at least as good as you at a skill counts as your mentor in it.", ["in_step", "strength_in_numbers"]),
]

# Nightwalker (idea 36, 2026-09-30): seeing and fighting in the dark. Sneaking owns hiding
# (Unseen, Nightblade, Backstab, Assassin), so nothing here hides you. Numbers are in
# skill/NightwalkerMath.java; keep the descriptions in step with them.
TREES["nightwalker"] = [
    root("after_dark", "After Dark"),
    N("dark_adapted", "Dark-Adapted Eyes", 1, 0, 5, 1, {"BON": 0.05}, None, ["after_dark"], "Dark Sight grows stronger."),
    N("night_owl", "Night Owl", 1, 2, 3, 1, {}, "night_hunger", ["after_dark"], "At night or in the dark you get hungry 10% slower per rank."),
    N("moonrise", "Moonrise", 1, 4, 5, 1, {"PC": 0.10}, None, ["after_dark"], "Moonlit comes more often."),
    N("far_sight", "Far Sight", 2, 0, 3, 1, {}, "dark_sight", ["dark_adapted"], "Dark Sight gets 2% more of Night Vision's light per rank."),
    N("darkborn_bane", "Darkborn Bane", 2, 2, 3, 1, {}, "darkborn", ["night_owl"], "+6% damage per rank to mobs that spawned in the dark."),
    N("silver_light", "Silver Light", 2, 4, 3, 1, {"PP": 0.10}, None, ["moonrise"], "Moonlit lasts longer."),
    N("phantom_ward", "Phantom Ward", 3, 0, 3, 1, {}, "phantom_ward", ["far_sight"], "Phantoms deal 20% less damage to you per rank.", 2),
    N("long_night", "Long Night", 3, 2, 3, 1, {"AD": 0.15}, None, ["darkborn_bane"], "Eclipse lasts longer."),
    N("deep_calm", "Deep Calm", 3, 4, 3, 1, {}, "darkness_ward", ["silver_light"], "The Darkness effect of Wardens and sculk shriekers is 25% shorter per rank.", 3),
    N("sanctuary", "Sanctuary", 4, 2, 1, 3, {}, "sanctuary", ["phantom_ward", "deep_calm"], "No monster spawns on its own within 16 blocks of your bed or respawn anchor, torches or not.", 4),
    cap("hunters_moon", "Hunter's Moon", "hunters_moon", "Moonlit also gives Strength, and a kill in the dark while it runs renews it.", ["sanctuary", "long_night"]),
]

# Courage (idea 37, 2026-09-30): fighting when the odds are against you. Endurance owns taking
# damage and surviving at low health (Grit, Last Stand, Indomitable, Adrenal Mend), so nothing
# here heals you on a hit taken or saves you from death. Numbers are in skill/CourageMath.java.
TREES["courage"] = [
    root("nerve", "Nerve"),
    N("hot_blood", "Hot Blood", 1, 0, 5, 1, {"BON": 0.05}, None, ["nerve"], "The more of them after you, the harder you hit."),
    N("hold_the_line", "Hold the Line", 1, 2, 3, 1, {}, "hold_the_line", ["nerve"], "With 3 or more mobs after you, you take 5% less damage from mobs per rank."),
    N("battle_cry", "Battle Cry", 1, 4, 5, 1, {"PC": 0.10}, None, ["nerve"], "Rally comes more often."),
    N("giant_slayer", "Giant Slayer", 2, 0, 3, 1, {}, "giant_slayer", ["hot_blood"], "+8% damage per rank to bosses and to mobs with 40 or more max health."),
    N("unshaken", "Unshaken", 2, 2, 3, 1, {}, "fearless", ["hold_the_line"], "Slowness and Weakness from mobs are a third shorter per rank. At rank 3 they never land."),
    N("war_drums", "War Drums", 2, 4, 3, 1, {"PP": 0.10}, None, ["battle_cry"], "Rally lasts longer."),
    N("spoils_of_valor", "Spoils of Valor", 3, 0, 3, 1, {}, "valor_heal", ["giant_slayer"], "Killing a boss or a mob stronger than you heals 2 health per rank.", 2),
    N("unyielding", "Unyielding", 3, 2, 3, 1, {"AD": 0.15}, None, ["unshaken"], "Stand Your Ground lasts longer."),
    N("challenge", "Challenge", 3, 4, 3, 1, {}, "challenge", ["war_drums"], "Hit a mob that is after another player within 4 blocks of you per rank, and it turns on you.", 3),
    N("rallying_cry", "Rallying Cry", 4, 2, 1, 3, {}, "rally_friends", ["spoils_of_valor", "challenge"], "Rally also gives its Strength and Speed to every player within 8 blocks.", 4),
    cap("lionheart", "Lionheart", "lionheart", "The passive counts up to 8 mobs after you, not 4, and a kill while outnumbered takes 5 seconds off Stand Your Ground's cooldown.", ["rallying_cry", "unyielding"]),
]

# Guardian (idea 38, 2026-09-30): protecting other players. Blocking keeps Bastion (shield
# shelter) and Endurance keeps your own survival (Grit, Last Stand), so every node here acts on
# or for another player. Numbers are in skill/GuardianMath.java.
TREES["guardian"] = [
    root("vigil", "Vigil"),
    N("warding", "Warding", 1, 0, 5, 1, {"BON": 0.05}, None, ["vigil"], "The players around you take a little less damage."),
    N("taunt", "Taunt", 1, 2, 1, 1, {}, "taunt", ["vigil"], "Hit a mob and it turns on you. For 5 seconds it cannot switch to anyone else."),
    N("watchful_eye", "Watchful Eye", 1, 4, 5, 1, {"PC": 0.10}, None, ["vigil"], "Intercept comes more often."),
    N("bodyguard", "Bodyguard", 2, 0, 3, 1, {}, "guard_radius", ["warding"], "Intercept and Shield Wall reach 2 blocks further per rank."),
    N("mending_guard", "Mending Guard", 2, 2, 3, 1, {}, "block_heal", ["taunt"], "Each hit you block with a shield heals the most hurt player within 8 blocks by 1 health per rank."),
    N("cover", "Cover", 2, 4, 3, 1, {"PP": 0.10}, None, ["watchful_eye"], "Intercept takes less of the hit."),
    N("heartshare", "Heartshare", 3, 0, 3, 1, {}, "share_absorb", ["bodyguard"], "When you get Absorption, the most hurt player within 8 blocks gets it too, for a third of its time per rank.", 2),
    N("hold_fast", "Hold Fast", 3, 2, 3, 1, {"AD": 0.15}, None, ["mending_guard"], "Shield Wall lasts longer."),
    N("watchful_compass", "Watchful Compass", 3, 4, 1, 1, {}, "compass_watch", ["cover"], "Your Friend Compass shows your friend's health, and pulses when they are hurt while it is in your hotbar.", 3),
    N("sworn_shield", "Sworn Shield", 4, 2, 1, 3, {}, "sworn_shield", ["heartshare", "watchful_compass"], "Once every 10 minutes, a blow that would kill a player within 8 blocks of you hits you instead, at half damage.", 4),
    cap("sentinel", "Sentinel", "sentinel", "Shield Wall also gives Resistance I to every player in it, and each hit you take for another player takes 2 seconds off Shield Wall's cooldown.", ["sworn_shield", "hold_fast"]),
]

# Charger (idea 39, 2026-09-30): first in, always moving forward. Courage keeps the odds and
# Guardian keeps protecting others, so every node here is about the charge, first blood, or
# leading a group from the front (Spearhead). Numbers are in skill/ChargerMath.java.
TREES["charger"] = [
    root("momentum", "Momentum"),
    N("first_in", "First In", 1, 0, 5, 1, {"BON": 0.05}, None, ["momentum"], "Your first blood on a mob hits harder."),
    N("trust_the_line", "Trust the Line", 1, 2, 3, 1, {}, "trust_line", ["momentum"], "Only in Spearhead: damage from friends is cut by 50%, 65% and 80% at rank 1, 2 and 3, so stray arrows and sweeps from the back line do not kill you."),
    N("breakthrough", "Breakthrough", 1, 4, 5, 1, {"PC": 0.10}, None, ["momentum"], "Breach comes more often."),
    N("crash_in", "Crash In", 2, 0, 3, 1, {}, "crash_absorb", ["first_in"], "First blood's Absorption lasts 4 seconds longer per rank, and is Absorption II at rank 3."),
    N("head_start", "Head Start", 2, 2, 3, 1, {}, "charge_short", ["trust_the_line"], "A charge needs half a block less per rank (5 blocks, down to 3.5)."),
    N("rolling_thunder", "Rolling Thunder", 2, 4, 3, 1, {"PP": 0.10}, None, ["breakthrough"], "Breach pushes harder and staggers longer."),
    N("red_harvest", "Red Harvest", 3, 0, 3, 1, {}, "charge_lifesteal", ["crash_in"], "In the 5 seconds after a charge hit, your hits heal 5% more of their damage per rank.", 2),
    N("unstoppable", "Unstoppable", 3, 2, 3, 1, {"AD": 0.15}, None, ["head_start"], "Charge! lasts longer."),
    N("spearpoint", "Spearpoint", 3, 4, 1, 1, {}, "spearhead_wide", ["rolling_thunder"], "Spearhead counts friends up to 24 blocks behind you, and its damage bonus is 10% higher.", 3),
    N("onslaught", "Onslaught", 4, 2, 1, 3, {}, "chain_first_blood", ["red_harvest", "spearpoint"], "A kill within 3 seconds of your first blood makes your next hit on another mob a first blood too.", 4),
    cap("warbringer", "Warbringer", "warbringer", "Each first blood takes 3 seconds off Charge!'s cooldown, and Charge!'s dash goes twice as far.", ["onslaught", "unstoppable"]),
]

# Tactician (idea 40, 2026-09-30): the back line, the mirror of Charger. Archery and Crossbows keep
# raw ranged damage and Sneaking keeps hits from behind a mob, so every node here is about the mark,
# the front line in front of you, or what you do for it. Numbers are in skill/TacticianMath.java.
TREES["tactician"] = [
    root("vantage", "Vantage Point"),
    N("spotter", "Spotter", 1, 0, 5, 1, {"BON": 0.05}, None, ["vantage"], "More ranged damage on a mob that is after someone else."),
    N("clear_line", "Clear Line", 1, 2, 1, 1, {}, "clear_line", ["vantage"], "Your projectiles fly through other players, and nothing you shoot or throw hurts them."),
    N("quick_call", "Quick Call", 1, 4, 5, 1, {"PC": 0.10}, None, ["vantage"], "Called Shot comes more often."),
    N("headshot", "Headshot", 2, 0, 3, 1, {}, "tactician_headshot", ["spotter"], "A projectile that hits a mob in the head deals 10% more damage per rank."),
    N("crossfire", "Crossfire", 2, 2, 3, 1, {}, "crossfire", ["clear_line"], "Each friend standing between you and the mob you hit (up to 3) adds 3% damage per rank."),
    N("opening", "Opening", 2, 4, 1, 1, {}, "mark_opens", ["quick_call"], "Your Called Shot also makes a Charger's next hit on that mob count as first blood."),
    N("quartermaster", "Quartermaster", 3, 0, 3, 1, {}, "mark_refund", ["headshot"], "A ranged kill on a mob you marked gives the arrow back, a 1 in 3 chance per rank.", 2),
    N("suppression", "Suppression", 3, 2, 3, 1, {"AD": 0.15}, None, ["crossfire"], "Suppressing Fire lasts longer."),
    N("painted_target", "Painted Target", 3, 4, 1, 1, {}, "mark_long", ["opening"], "Your mark lasts 4 seconds longer, and friends deal 5% more damage to it on top.", 3),
    N("focus_fire", "Focus Fire", 4, 2, 1, 3, {}, "mark_jump", ["quartermaster", "painted_target"], "When a mob you marked dies, the mark jumps to the nearest hostile mob within 8 blocks.", 4),
    cap("field_marshal", "Field Marshal", "field_marshal", "Your mark lasts 4 seconds longer, each kill a friend makes on it takes 3 seconds off Suppressing Fire's cooldown, and Suppressing Fire slows one level harder.", ["focus_fire", "suppression"]),
]
