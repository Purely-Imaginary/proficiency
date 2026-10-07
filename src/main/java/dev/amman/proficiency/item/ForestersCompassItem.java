package dev.amman.proficiency.item;

import com.mojang.datafixers.util.Pair;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.event.StructureIds;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Tells you what you still owe and where to go looking.
 *
 * <p>There is no way to ask the game "which biome grows rainbow eucalyptus", so the compass does
 * not pretend to. It does the two things it genuinely can: it reads the next talent in your
 * Woodcutting tree and lists exactly which logs you are still short of, and it points at the
 * nearest biome you have never stood in. Unfamiliar wood is in unfamiliar places, so in practice
 * those two answers are the same answer.
 *
 * <p>Sneak-use toggles a second mode. In Structure mode the needle points at the nearest structure
 * type you have never been inside, using the same canonical types the discovery XP uses.
 */
public class ForestersCompassItem extends Item {

    private static final int SEARCH_RADIUS = 6400;
    private static final int HORIZONTAL_STEP = 32;
    /**
     * Larger than any world is tall, so the search samples ONE height: see {@link #searchFrom}.
     * It used to be 64, which searched every height down to bedrock, and the nearest unvisited
     * biome was almost always a cave biome straight under your feet. A compass only shows a
     * horizontal direction, so the needle pointed at the spot you clicked on (reported 2026-09-29).
     */
    private static final int VERTICAL_STEP = 4096;

    /** Cave biomes are never a destination for a compass: you cannot walk into them from above. */
    private static final TagKey<Biome> IS_CAVE =
            TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_cave"));
    private static final TagKey<Biome> IS_UNDERGROUND =
            TagKey.create(Registries.BIOME, new ResourceLocation("forge", "is_underground"));
    private static final String TRACK_KEY = "tracked_skill";
    /** Biome id the needle currently points toward, kept beside {@link #TRACK_KEY} in custom data. */
    static final String TARGET_KEY = "target_biome";
    private static final int ARRIVAL_CHECK_INTERVAL = 20;

    /** Custom-data key for the mode. Missing means Biome, so every old stack keeps working. */
    static final String MODE_KEY = "mode";
    private static final String MODE_STRUCTURE = "structure";
    /** Canonical structure id the needle points toward in Structure mode. */
    static final String TARGET_STRUCTURE_KEY = "target_structure";
    /**
     * Structure search radius. The API counts placement-spacing cells per random-spread placement,
     * not chunks, and each candidate chunk is generated on the server thread, so it stays small.
     */
    static final int STRUCTURE_SEARCH_CELLS = 8;
    private static final long STRUCTURE_COOLDOWN_TICKS = 600;
    /** Game time each player may next search for structures. Server thread only. */
    private static final java.util.Map<java.util.UUID, Long> NEXT_STRUCTURE_SEARCH = new java.util.HashMap<>();

    public ForestersCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.success(held);
        }

        // Sneak-use looking up at the sky toggles Structure mode; sneak-use otherwise steps the
        // tree, as it always did. Putting Structure at the end of the tree cycle meant up to
        // twenty-eight clicks to reach it, one per skill still owing materials.
        if (player.isShiftKeyDown() && player.getXRot() < LOOK_UP_PITCH) {
            toggleStructureMode(serverPlayer, held);
            return InteractionResultHolder.success(held);
        }
        if (player.isShiftKeyDown()) {
            cycleSelection(serverPlayer, held);
            return InteractionResultHolder.success(held);
        }

        reportOutstanding(serverPlayer, trackedSkill(held));
        if (structureMode(held)) {
            pointAtStructure(serverPlayer, serverLevel, held);
        } else {
            pointAtSomewhereNew(serverPlayer, serverLevel, held);
        }

        serverPlayer.playNotifySound(
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.1f);
        player.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(held);
    }

    static boolean structureMode(ItemStack stack) {
        return MODE_STRUCTURE.equals(ItemData.custom(stack).getString(MODE_KEY));
    }

    /** The drop-down entry the tooltip and action bar name: a tree, or "Mode: Structure". */
    private static Component selectionLine(ItemStack stack) {
        if (structureMode(stack)) {
            return Component.translatable("proficiency.compass.mode_structure");
        }
        return Component.translatable("proficiency.compass.tracking",
                Component.translatable(trackedSkill(stack).translationKey()));
    }

    @Override
    public void appendHoverText(ItemStack stack, @org.jetbrains.annotations.Nullable Level context, List<Component> lines, TooltipFlag flag) {
        lines.add(selectionLine(stack).copy().withStyle(ChatFormatting.AQUA));
        lines.add(Component.translatable("proficiency.compass.tooltip_use").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Which tree the compass is reading. Kept on the stack so two compasses can track two trees. */
    private static Skill trackedSkill(ItemStack stack) {
        if (ItemData.hasCustom(stack)) {
            Skill skill = Skill.byId(ItemData.custom(stack).getString(TRACK_KEY));
            if (skill != null) {
                return skill;
            }
        }
        return Skill.WOODCUTTING;
    }

    /** Merges into the stack's custom data so the tracked skill and the target biome coexist. */
    private static void editData(ItemStack stack, Consumer<CompoundTag> edit) {
        CompoundTag tag = ItemData.custom(stack);
        edit.accept(tag);
        ItemData.setCustom(stack, tag);
    }

    private static void setTarget(ItemStack stack, ServerLevel level, BlockPos target, ResourceLocation biome) {
        ItemData.setTarget(stack, GlobalPos.of(level.dimension(), target));
        editData(stack, tag -> tag.putString(TARGET_KEY, biome.toString()));
    }

    private static void clearTarget(ItemStack stack) {
        ItemData.clearTarget(stack);
        editData(stack, tag -> {
            tag.remove(TARGET_KEY);
            tag.remove(TARGET_STRUCTURE_KEY);
        });
    }

    /**
     * Cheap arrival check, at most once a second and only server side. It never searches for
     * biomes; it only asks whether the stored one has been visited by now.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide() || !(entity instanceof ServerPlayer player)
                || (level.getGameTime() + slot) % ARRIVAL_CHECK_INTERVAL != 0) {
            return;
        }
        if (!ItemData.hasCustom(stack)) {
            return;
        }
        CompoundTag tag = ItemData.custom(stack);
        String structure = tag.getString(TARGET_STRUCTURE_KEY);
        if (!structure.isEmpty()) {
            if (StructureIds.alreadySeen(structure, key -> ProficiencyAttachments.of(player).hasVisited(key))) {
                clearTarget(stack);
                player.displayClientMessage(Component.translatable("proficiency.compass.arrived",
                        structureName(structure)).withStyle(ChatFormatting.GREEN), true);
            }
            return;
        }
        String biome = tag.getString(TARGET_KEY);
        if (biome.isEmpty()) {
            return;
        }
        if (ProficiencyAttachments.of(player).hasVisited("biome:" + biome)) {
            clearTarget(stack);
            ResourceLocation id = ResourceLocation.tryParse(biome);
            Component name = id == null ? Component.literal(biome)
                    : Component.translatable("biome." + id.getNamespace() + "." + id.getPath());
            player.displayClientMessage(Component.translatable("proficiency.compass.arrived", name)
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    /**
     * Sneak-use, either hand, steps to the next unfinished tree, wrapping round. From Structure mode
     * it returns to the tracked tree. Structure mode itself is sneak-use while looking up.
     * The old target is dropped on every step: a needle from another entry means nothing.
     */
    /** Pitch below which the player counts as looking up; vanilla pitch is negative upwards. */
    private static final float LOOK_UP_PITCH = -45f;

    private void toggleStructureMode(ServerPlayer player, ItemStack stack) {
        clearTarget(stack);
        boolean toStructure = !structureMode(stack);
        editData(stack, tag -> {
            if (toStructure) {
                tag.putString(MODE_KEY, MODE_STRUCTURE);
            } else {
                tag.remove(MODE_KEY);
            }
        });
        player.displayClientMessage(selectionLine(stack).copy().withStyle(ChatFormatting.AQUA), true);
    }

    private void cycleSelection(ServerPlayer player, ItemStack stack) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        clearTarget(stack);
        Skill next = null;
        if (structureMode(stack)) {
            // Out of Structure mode, back to the tree it was tracking.
            next = trackedSkill(stack);
        } else {
            int from = trackedSkill(stack).ordinal();
            for (int step = 1; step <= Skill.VALUES.length; step++) {
                Skill candidate = Skill.VALUES[(from + step) % Skill.VALUES.length];
                if (!Talents.unpaidMaterialNodes(candidate, skills::hasPaid).isEmpty()) {
                    next = candidate;
                    break;
                }
            }
            if (next == null) {
                next = trackedSkill(stack);
            }
        }
        Skill chosen = next;
        editData(stack, tag -> {
            if (chosen == null) {
                tag.putString(MODE_KEY, MODE_STRUCTURE);
            } else {
                tag.remove(MODE_KEY);
                tag.putString(TRACK_KEY, chosen.id());
            }
        });
        player.displayClientMessage(selectionLine(stack).copy().withStyle(ChatFormatting.AQUA), true);
    }

    private void reportOutstanding(ServerPlayer player, Skill tracked) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        // The next node in this tree that still owes materials. Ranks cost points only; the
        // compass is about the things you have to go and fetch.
        List<Talent> owing = Talents.unpaidMaterialNodes(tracked, skills::hasPaid);
        Talent next = owing.isEmpty() ? null : owing.get(0);
        if (next == null) {
            player.sendSystemMessage(Component.translatable("proficiency.compass.tree_done")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        player.sendSystemMessage(Component.translatable("proficiency.compass.owing",
                next.displayName()).withStyle(ChatFormatting.GOLD));

        List<Requirement> materials = TalentService.payableMaterials(next);
        boolean anythingMissing = false;
        for (Requirement requirement : materials) {
            int held = requirement.countIn(player.getInventory());
            if (held >= requirement.count()) {
                continue;
            }
            anythingMissing = true;
            player.sendSystemMessage(Component.translatable("proficiency.perk.missing_line",
                    requirement.displayName(), held, requirement.count())
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!anythingMissing) {
            player.sendSystemMessage(Component.translatable("proficiency.compass.ready")
                    .withStyle(ChatFormatting.GREEN));
        }
    }

    /**
     * The one height the search samples: yours, but never below sea level, so a compass used down a
     * mine still looks for surface biomes rather than the cave you are standing in.
     */
    private static BlockPos searchFrom(ServerLevel level, BlockPos from) {
        return new BlockPos(from.getX(), Math.max(from.getY(), level.getSeaLevel()), from.getZ());
    }

    private void pointAtSomewhereNew(ServerPlayer player, ServerLevel level, ItemStack stack) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        BlockPos from = player.blockPosition();

        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(
                holder -> !holder.is(IS_CAVE) && !holder.is(IS_UNDERGROUND) && holder.unwrapKey()
                        .map(key -> !skills.hasVisited("biome:" + key.location()))
                        .orElse(false),
                searchFrom(level, from), SEARCH_RADIUS, HORIZONTAL_STEP, VERTICAL_STEP);

        if (found == null) {
            clearTarget(stack);
            player.sendSystemMessage(Component.translatable("proficiency.compass.nothing_new")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        BlockPos target = found.getFirst();
        found.getSecond().unwrapKey().ifPresent(key -> setTarget(stack, level, target, key.location()));
        int distance = (int) Math.sqrt(from.distSqr(target));
        Component biomeName = found.getSecond().unwrapKey()
                .map(key -> (Component) Component.translatable(
                        "biome." + key.location().getNamespace() + "." + key.location().getPath()))
                .orElse(Component.literal("somewhere"));

        player.sendSystemMessage(Component.translatable("proficiency.compass.heading",
                biomeName, distance, target.getX(), target.getZ())
                .withStyle(ChatFormatting.AQUA));
    }

    private static Component structureName(String canonicalId) {
        ResourceLocation id = ResourceLocation.tryParse(canonicalId);
        if (id == null) {
            return Component.literal(canonicalId);
        }
        return Component.translatableWithFallback("structure." + id.getNamespace() + "." + id.getPath(),
                dev.amman.proficiency.net.DiscoveryNames.fromId(canonicalId));
    }

    /**
     * Structure mode. One search per use, never per tick, and at most one per 30 s per player.
     * Only structures that can generate in this dimension are asked for: an unreachable wanted set
     * would otherwise generate chunks over the whole radius for nothing.
     */
    private void pointAtStructure(ServerPlayer player, ServerLevel level, ItemStack stack) {
        PlayerSkills skills = ProficiencyAttachments.of(player);
        BlockPos from = player.blockPosition();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var generator = level.getChunkSource().getGenerator();
        var possible = generator.getBiomeSource().possibleBiomes();

        // Every structure whose biomes meet this dimension's biomes.
        List<String> local = new ArrayList<>();
        for (var entry : registry.entrySet()) {
            HolderSet<net.minecraft.world.level.biome.Biome> biomes = entry.getValue().biomes();
            if (possible.stream().anyMatch(biomes::contains)) {
                local.add(entry.getKey().location().toString());
            }
        }
        List<Holder<Structure>> wanted = new ArrayList<>();
        for (String raw : StructureIds.unvisitedTargets(local, key -> skills.hasVisited(key))) {
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id != null) {
                registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE, id))
                        .ifPresent(h -> wanted.add((Holder<Structure>) h));
            }
        }
        clearTarget(stack);
        if (wanted.isEmpty()) {
            player.sendSystemMessage(Component.translatable("proficiency.compass.all_structures")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }

        long now = level.getGameTime();
        Long ready = NEXT_STRUCTURE_SEARCH.get(player.getUUID());
        if (ready != null && now < ready) {
            player.sendSystemMessage(Component.translatable("proficiency.compass.structure_wait",
                    (ready - now + 19) / 20).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        NEXT_STRUCTURE_SEARCH.put(player.getUUID(), now + STRUCTURE_COOLDOWN_TICKS);

        Pair<BlockPos, Holder<Structure>> found = generator.findNearestMapStructure(
                level, HolderSet.direct(wanted), from, STRUCTURE_SEARCH_CELLS, false);
        if (found == null) {
            player.sendSystemMessage(Component.translatable("proficiency.compass.no_structures")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        BlockPos target = found.getFirst();
        ResourceLocation rawKey = found.getSecond().unwrapKey().map(k -> k.location()).orElse(null);
        if (rawKey == null) {
            return;
        }
        String canonical = StructureIds.canonical(rawKey.toString());
        ItemData.setTarget(stack, GlobalPos.of(level.dimension(), target));
        editData(stack, tag -> tag.putString(TARGET_STRUCTURE_KEY, canonical));
        int distance = (int) Math.sqrt(from.distSqr(target));
        player.sendSystemMessage(Component.translatable("proficiency.compass.heading",
                structureName(canonical), distance, target.getX(), target.getZ())
                .withStyle(ChatFormatting.AQUA));
    }
}
