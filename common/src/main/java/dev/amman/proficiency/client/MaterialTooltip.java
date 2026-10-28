package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.perk.MaterialNeeds;
import dev.amman.proficiency.perk.Requirements;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tells a player on any item tooltip what that item is still needed for: the unpaid talent nodes
 * that list it, or a tag it belongs to, as a material. The rules are core {@link MaterialNeeds}; this
 * is the game side. The index is rebuilt when a node is paid or the material lists change, never per
 * frame, and the lines only count what the player carries now (the pay path's count).
 *
 * <p>Nothing here asks the server for anything: the trees, the paid set and the inventory are all
 * on the client already. By default only nodes you can unlock right now show (level gate open, parents full); Shift adds the later ones, dimmed with their level.
 */
public final class MaterialTooltip {

    private static MaterialNeeds.Index index;
    private static int indexPaid;
    private static int indexGeneration = -1;
    private static boolean indexOwner;

    private MaterialTooltip() {
    }

    static void reset() {
        index = null;
    }

    /** The loaders' tooltip events reach this through {@link SkillTooltip#append}. */
    static void append(Player player, ItemStack stack, List<Component> tip) {
        if (!ProficiencyClientConfig.uiMaterialTooltips() || !ClientSync.isSynced()) {
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(player);
        MaterialNeeds.Index idx = index(skills);
        if (idx.isEmpty()) {
            return;
        }
        Set<String> tags = new HashSet<>();
        stack.getTags().forEach(tag -> tags.add(tag.location().toString()));
        List<MaterialNeeds.Entry> entries = idx.entriesFor(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), tags);
        if (entries.isEmpty()) {
            return;
        }
        List<MaterialNeeds.Line> lines = MaterialNeeds.lines(entries,
                requirement -> Requirements.countIn(requirement, player.getInventory()), skills::isUnlockableNow);
        tip.addAll(build(lines, TooltipDetail.detailed()));
    }

    /** The block itself, for the tooltip and for the layout demo. Empty when nothing shows. */
    static List<Component> build(List<MaterialNeeds.Line> lines, boolean detailed) {
        List<Component> out = new ArrayList<>();
        List<MaterialNeeds.Line> now = MaterialNeeds.immediate(lines);
        if (!now.isEmpty()) {
            int missing = MaterialNeeds.totalMissing(now);
            out.add((missing > 0 ? Component.translatable("proficiency.matneed.header", missing)
                    : Component.translatable("proficiency.matneed.ready"))
                    .withStyle(missing > 0 ? ChatFormatting.GOLD : ChatFormatting.GREEN));
        }
        for (MaterialNeeds.Line line : MaterialNeeds.shown(lines, detailed)) {
            Component skill = Component.translatable(line.entry().skill().translationKey())
                    .withStyle(style -> style.withColor(SkillPalette.accent(line.entry().skill().category())));
            Component node = Component.translatable(line.entry().talent().nameKey());
            if (line.unlocked()) {
                out.add(Component.translatable("proficiency.matneed.line", skill, node, line.required(), line.held())
                        .withStyle(line.missing() == 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            } else {
                out.add(Component.translatable("proficiency.matneed.line_later", skill, node, line.required(),
                        line.held(), line.entry().talent().requiredLevel()).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        int hidden = MaterialNeeds.hidden(lines, detailed);
        if (hidden > 0) {
            out.add(Component.translatable("proficiency.matneed.more", hidden)
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        return out;
    }

    private static MaterialNeeds.Index index(PlayerSkills skills) {
        int paid = skills.paidSignature();
        int generation = Talents.generation();
        if (index == null || paid != indexPaid || generation != indexGeneration) {
            index = MaterialNeeds.build(skills::hasPaid, Requirements::isAvailable);
            indexPaid = paid;
            indexGeneration = generation;
        }
        return index;
    }
}
