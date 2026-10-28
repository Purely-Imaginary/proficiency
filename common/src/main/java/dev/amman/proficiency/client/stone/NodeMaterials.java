package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Requirements;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentService;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What a talent node wants handed over when it fills, as the Skillstone shows it: the item, its
 * name and "have / need". It counts the same way the server does ({@link Requirements#countIn}, so
 * tags and the whole inventory), and lists the same requirements ({@link TalentService#payableMaterials},
 * so an item of an absent mod is not listed). Written once against APIs both Minecraft versions share.
 */
public final class NodeMaterials {

    /** One material of a node: what, how many are held, how many are needed. */
    record Row(Requirement requirement, int held, int need) {
        boolean enough() {
            return held >= need;
        }
    }

    private static final Map<String, List<ItemStack>> TAG_ITEMS = new HashMap<>();

    private NodeMaterials() {
    }

    /** The layout demo's node: it owes iron ingots, stone and any log, whatever its real data says. */
    private static String demoId;

    /** For the layout demo only: makes node {@code id} owe a three-material bill; null ends it. */
    public static void demo(String id) {
        demoId = id;
    }

    private static List<Requirement> payable(Talent t) {
        if (t.id().equals(demoId)) {
            return List.of(Requirement.of("minecraft:iron_ingot", 3), Requirement.of("minecraft:stone", 2),
                    Requirement.ofTag("minecraft:logs", 4));
        }
        return TalentService.payableMaterials(t);
    }

    /** What the node takes when it fills (the unlock slam flies these in). */
    static List<Requirement> wanted(Talent t) {
        return payable(t);
    }

    /** Whether the node still owes materials: it has some that can be met here, and they are not paid. */
    static boolean owes(PlayerSkills skills, Talent t) {
        return !skills.hasPaid(t) && !payable(t).isEmpty();
    }

    /** Whether a click on the node would charge them now: they are charged on the rank that fills it. */
    static boolean chargesNext(PlayerSkills skills, Talent t) {
        return owes(skills, t) && skills.rank(t) + 1 == t.maxRank();
    }

    static List<Row> rows(Talent t) {
        Player player = Minecraft.getInstance().player;
        List<Row> rows = new ArrayList<>();
        for (Requirement requirement : payable(t)) {
            int held = player == null ? 0 : Requirements.countIn(requirement, player.getInventory());
            rows.add(new Row(requirement, held, requirement.count()));
        }
        return rows;
    }

    static boolean allThere(List<Row> rows) {
        for (Row row : rows) {
            if (!row.enough()) {
                return false;
            }
        }
        return true;
    }

    /** The item to draw: the item itself, or for a tag one of its members, cycling with {@code now}. */
    static ItemStack icon(Requirement requirement, long now) {
        if (requirement.tag() == null) {
            Item item = Requirements.resolve(requirement);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        List<ItemStack> members = TAG_ITEMS.computeIfAbsent(requirement.tag(), id -> {
            List<ItemStack> found = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                ItemStack stack = new ItemStack(item);
                if (found.size() < 48 && Requirements.matches(requirement, stack)) {
                    found.add(stack);
                }
            }
            return found;
        });
        return members.isEmpty() ? ItemStack.EMPTY : members.get((int) ((now / 900L) % members.size()));
    }

    /** A tag's readable name: the game's own when it has one, else its path made into words. */
    static String name(Requirement requirement) {
        if (requirement.tag() == null) {
            return Requirements.displayName(requirement).getString();
        }
        String key = "tag.item." + requirement.namespace() + "." + requirement.path().replace('/', '.');
        if (Language.getInstance().has(key)) {
            return Component.translatable(key).getString();
        }
        String path = requirement.path();
        path = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        return path.isEmpty() ? path : Character.toUpperCase(path.charAt(0)) + path.substring(1);
    }

    /** "3 x Iron ingot", with the multiplication sign. */
    static String amount(Row row) {
        return SkillstoneScreen.tr("proficiency.stone.mat_amount", row.need(), name(row.requirement()));
    }

    /** The shorts only, joined: "3 x Iron ingot, 2 x Stone" (what is still needed, not what is wanted). */
    static String missing(List<Row> rows) {
        StringBuilder out = new StringBuilder();
        for (Row row : rows) {
            if (!row.enough()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(SkillstoneScreen.tr("proficiency.stone.mat_amount", row.need() - row.held(),
                        name(row.requirement())));
            }
        }
        return out.toString();
    }

    static String all(List<Row> rows) {
        StringBuilder out = new StringBuilder();
        for (Row row : rows) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(amount(row));
        }
        return out.toString();
    }

    /** The socket's tooltip: one have/need line per material, green when enough, red when short. */
    static List<Component> tooltip(PlayerSkills skills, Talent t) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(t.nameKey()).withStyle(ChatFormatting.GOLD));
        boolean last = skills.rank(t) + 1 == t.maxRank();
        lines.add(Component.literal(SkillstoneScreen.tr(last ? "proficiency.stone.materials_head"
                : "proficiency.stone.materials_head_last")).withStyle(ChatFormatting.GRAY));
        for (Row row : rows(t)) {
            lines.add(Component.literal("  " + name(row.requirement()) + "  " + Math.min(row.held(), row.need())
                            + " / " + row.need())
                    .withStyle(row.enough() ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        return lines;
    }

    /**
     * The Face's brazier hint: the first node that would take a point and still owes materials, with what it
     * wants and what is short, so the points waiting there do not hide the trip for the items.
     */
    static List<Component> brazierHint(PlayerSkills skills, dev.amman.proficiency.skill.Skill skill) {
        List<Component> lines = new ArrayList<>();
        for (Talent t : dev.amman.proficiency.perk.Talents.of(skill)) {
            if (skills.check(t) == dev.amman.proficiency.perk.TalentOutcome.OK && owes(skills, t)) {
                List<Row> rows = rows(t);
                lines.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.brazier_wants",
                        Component.translatable(t.nameKey()).getString(), all(rows))).withStyle(ChatFormatting.GOLD));
                if (!allThere(rows)) {
                    lines.add(Component.literal(SkillstoneScreen.tr("proficiency.stone.click_missing", missing(rows)))
                            .withStyle(ChatFormatting.RED));
                }
                break;
            }
        }
        return lines;
    }
}
