import json, sys
# skill -> tier -> ([(id, count)], why)
O = {
 "maces": {
  2: ([("minecraft:iron_block", 4), ("minecraft:blaze_rod", 8)], "wind_charge and breeze_rod do not exist in 1.20.1; the port's mace is an iron block on a blaze rod"),
  3: ([("minecraft:copper_ingot", 64), ("proficiency:mace", 1)], "minecraft:mace does not exist in 1.20.1; the port adds proficiency:mace"),
  4: ([("minecraft:netherite_ingot", 1), ("proficiency:hunters_charm", 2)], "heavy_core does not exist in 1.20.1"),
 },
 "tridents": {
  1: ([("minecraft:rotten_flesh", 32), ("minecraft:cod", 32)], "no ocean monuments, prismarine only from the Natural Altar (quartz, mid game); drowned in dead oceans drop flesh"),
  3: ([("minecraft:nautilus_shell", 8), ("minecraft:sea_pickle", 16)], "sponge has no source (no elder guardians; the bee hydroregulator only dries/wets one)"),
 },
 "unarmed": {
  1: ([("minecraft:white_wool", 32), ("minecraft:rotten_flesh", 32)], "leather and feathers need animals (Nature's Aura birthing altar / Sentient Life, quest depth 33-35)"),
  2: ([("minecraft:leather", 16), ("minecraft:string", 32)], "rabbits only come back with the arid/icy biome bottles (end of the quest book)"),
 },
 "endurance": {
  1: ([("minecraft:cooked_cod", 32), ("minecraft:white_wool", 16)], "no cows or leather early; fishing works from day one"),
  3: ([("minecraft:golden_apple", 4), ("minecraft:scute", 8)], "1.20.1 calls it minecraft:scute (turtle_scute is 1.20.5+); turtles from the birthing altar"),
  4: ([("botania:life_essence", 1), ("proficiency:hunters_charm", 2)], "no evokers (no mansions, no raids), so no totem; Gaia Guardian's life essence instead"),
 },
 "archery": {
  1: ([("minecraft:arrow", 128), ("minecraft:bone", 64)], "no chickens until Sentient Life (quest depth 33); skeletons drop both arrows and bones"),
 },
 "mining": {
  1: ([("minecraft:cobblestone", 128), ("minecraft:charcoal", 64)], "no coal anywhere (no ore; first coal is Botania Orechid, late); dead logs smelt into charcoal"),
 },
 "woodcutting": {
  1: ([("kubejs:dead_log", 64), ("kubejs:scrap_wood", 64)], "oak and birch only after saplings (mutandis) grown on scarce dirt; dead trees are the wood of the early game"),
  2: ([("minecraft:spruce_log", 32), ("minecraft:jungle_log", 32), ("minecraft:acacia_log", 32), ("enchanted:rowan_log", 32)], "Biomes We've Gone is not in the pack; rowan is the pack's own witch tree"),
  3: ([("minecraft:dark_oak_log", 16), ("minecraft:cherry_log", 16), ("minecraft:mangrove_log", 16), ("botania:livingwood_log", 16), ("naturesaura:ancient_log", 16)], "BWG and the Aether are not in the pack; livingwood and ancient logs are the pack's magic woods"),
  4: ([("minecraft:crimson_stem", 8), ("minecraft:warped_stem", 8), ("ars_nouveau:red_archwood_log", 8), ("botania:dreamwood_log", 8), ("proficiency:masterwork_ingot", 2)], "Twilight Forest and Gardens of the Dead are not in the pack; archwood and Alfheim's dreamwood instead"),
 },
 "excavation": {
  1: ([("kubejs:dried_earth", 128), ("minecraft:sand", 128)], "the ground is dried earth; real dirt is the first thing the pack makes you craft"),
 },
 "running": {
  1: ([("minecraft:iron_boots", 2), ("minecraft:sugar", 64)], "no leather early"),
  2: ([("minecraft:ender_pearl", 8), ("minecraft:feather", 32)], "no rabbits until the arid/icy biome bottles"),
 },
 "sneaking": {
  1: ([("minecraft:white_wool", 32), ("minecraft:white_carpet", 32)], "black dye needs ink sacs or Botania petals, neither early"),
  3: ([("minecraft:ender_pearl", 16), ("minecraft:black_wool", 32)], "no deep dark, so no sculk"),
  4: ([("minecraft:ender_eye", 4), ("proficiency:wanderers_token", 2)], "no deep dark, so no warden and no echo shards"),
 },
 "jumping": {
  1: ([("complicated_bees:honey_droplet", 32), ("minecraft:scaffolding", 16)], "no slimes spawn and no chickens early; bees are an early quest"),
  2: ([("minecraft:slime_ball", 32), ("minecraft:honey_bottle", 8)], "no rabbits; slime from Botania (cactus) or Create, mid game"),
  3: ([("minecraft:phantom_membrane", 8), ("minecraft:feather", 32)], "chorus only grows in the End, the last chapter"),
 },
 "swimming": {
  1: ([("minecraft:clay_ball", 64), ("minecraft:cod", 32)], "no kelp in the dead oceans; underwater clay deposits are the sea's early find"),
  2: ([("minecraft:prismarine_shard", 32), ("minecraft:sea_pickle", 8)], "sponge has no source"),
  3: ([("minecraft:nautilus_shell", 4), ("minecraft:scute", 8)], "1.20.1 calls it minecraft:scute"),
 },
 "smithing": {
  1: ([("minecraft:iron_ingot", 64), ("minecraft:charcoal", 64)], "no coal; charcoal from dead logs"),
 },
 "cooking": {
  1: ([("minecraft:bread", 32), ("minecraft:baked_potato", 32)], "no cows until the birthing altar (quest depth 35)"),
  3: ([("minecraft:honey_bottle", 16), ("farmersdelight:cooking_pot", 1), ("croptopia:rice", 64)], "Farmer's Delight rice is filtered out of AgriCraft and has no wild rice; Croptopia rice grows"),
 },
 "spellcasting": {
  3: ([("ars_nouveau:magebloom_fiber", 16), ("ars_nouveau:wilden_wing", 4)], "no echo shards"),
 },
 "beastslaying": {
  3: ([("minecraft:ghast_tear", 8), ("ars_nouveau:wilden_horn", 8)], "Twilight Forest is not in the pack; Wilden hunters come from the Ars summoning ritual"),
 },
 "wayfaring": {
  1: ([("minecraft:paper", 32), ("exposure:camera", 1)], "a compass needs redstone (mid game); the pack's camera is a copper early quest"),
  2: ([("minecraft:ender_pearl", 16), ("minecraft:compass", 4)], "Waystones is not in the pack"),
  3: ([("minecraft:ender_eye", 16), ("minecraft:map", 8)], "no echo shards"),
 },
 "spelunking": {
  3: ([("minecraft:tuff", 64), ("minecraft:diamond", 4), ("minecraft:glow_berries", 32)], "no deep dark: no sculk, no echo shards"),
 },
 "masonry": {
  1: ([("minecraft:cobblestone", 256), ("kubejs:flimsy_planks", 32)], "no oak early; a flimsy plank is a whole dead log, so 32 is the 128 oak planks' 32 logs"),
 },
 "decorating": {
  2: ([("minecraft:painting", 8), ("minecraft:item_frame", 16), ("minecraft:lantern", 16), ("mcwfurnitures:oak_chair", 8)], "Handcrafted is not in the pack; Macaw's Furniture is"),
  4: ([("minecraft:beacon", 1), ("mcwfurnitures:oak_table", 4), ("proficiency:masterwork_ingot", 1)], "Handcrafted is not in the pack"),
 },
 "social": {
  1: ([("minecraft:mushroom_stew", 4), ("minecraft:bread", 32)], "cake needs milk and eggs (animals, quest depth 33-35)"),
  2: ([("xercamusic:tubular_bell", 1), ("minecraft:gold_ingot", 16)], "a bell cannot be crafted and there are no villages; no villagers to earn emeralds from"),
 },
 "courage": {
  1: ([("minecraft:gunpowder", 16), ("minecraft:bone", 32)], "goats only from the birthing altar (quest depth 35)"),
  2: ([("minecraft:goat_horn", 1), ("minecraft:gold_ingot", 16)], "ominous_bottle does not exist in 1.20.1; no villagers for emeralds"),
  3: ([("ars_nouveau:wilden_tribute", 1), ("minecraft:blaze_rod", 8)], "no evokers, so no totem; the Wilden Chimera's tribute is the pack's mid-game boss trophy"),
 },
 "nightwalker": {
  1: ([("minecraft:ender_pearl", 4), ("minecraft:string", 32)], "a clock needs gold and redstone, glow berries need Create haunting; endermen and cave spiders are the night's early drops"),
  3: ([("minecraft:clock", 1), ("minecraft:glow_berries", 16)], "no deep dark: no echo shards or sculk"),
  4: ([("minecraft:lodestone", 1), ("proficiency:hunters_charm", 2)], "a recovery compass needs echo shards"),
 },
 "charger": {
  1: ([("minecraft:white_banner", 1), ("minecraft:flint", 16)], "no leather early"),
 },
 "tactician": {
  1: ([("minecraft:copper_ingot", 8), ("minecraft:arrow", 32)], "a spyglass needs amethyst (quest depth 41)"),
 },
}
out = {"_about": "Reclamation (Forge 1.20.1) material lists for the Proficiency talent trees. Tier 1 = root node (level 10), 2 and 3 = level-60 nodes, 4 = level-90 keystone. A named tier replaces that whole list. Shipped in the pack's config/, read once at game start. Generated by tools/reclamation/gen_materials.py; the reasons are in _why.",
       "skills": {}, "_why": {}}
for skill, tiers in O.items():
    out["skills"][skill] = {}
    for t, (lst, why) in sorted(tiers.items()):
        out["skills"][skill][str(t)] = [{"item": i, "count": c} for i, c in lst]
        out["_why"][f"{skill}/{t}"] = why
print(json.dumps(out, indent=2, ensure_ascii=False))
