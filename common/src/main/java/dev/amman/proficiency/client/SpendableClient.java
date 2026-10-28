package dev.amman.proficiency.client;

import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Requirements;
import dev.amman.proficiency.perk.Spendable;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The client's copy of the 'spendable now' rule (1.55.0, core {@link Spendable}): what every screen reads for
 * "N ready", the item badge, the dial embers and the why-stuck tooltip, and the watcher behind the ready toast.
 * Worked out from data the client already has (its synced skills and its own inventory), so no packet.
 *
 * <p>Cheap: a client tick only hashes the skills and the inventory; the rule runs again when one of them changed
 * (the inventory debounced 400 ms, so a shift-click shuffle counts once), and every 2 s anyway. Written once
 * against APIs both Minecraft versions share (Forge copies it).
 */
public final class SpendableClient {

    private static final long INVENTORY_DEBOUNCE_MS = 400;
    private static final long REFRESH_MS = 2000;
    /** Ready ranks that were there when the world loaded are a baseline, never a toast. */
    private static final long BASELINE_MS = 8000;
    private static final long TOAST_GAP_MS = 5 * 60 * 1000L;

    /** The inventory as the pay path counts it. */
    public static Spendable.Materials materials(Player player) {
        Inventory inventory = player == null ? null : player.getInventory();
        return new Spendable.Materials() {
            @Override
            public int held(Requirement requirement) {
                return inventory == null ? 0 : Requirements.countIn(requirement, inventory);
            }

            @Override
            public boolean available(Requirement requirement) {
                return Requirements.isAvailable(requirement);
            }
        };
    }

    private static Spendable.Summary summary = Spendable.Summary.empty();
    private static Spendable.Summary demo;
    private static Player player;
    private static int skillsSig;
    private static int levelSum = -1;
    private static long levelsChangedAt = Long.MIN_VALUE / 2;
    private static int invSig;
    private static long invChangedAt = -1;
    private static long computedAt = Long.MIN_VALUE / 2;
    private static long joinedAt;
    private static boolean baselined;
    private static final Map<Skill, Long> TOASTED = new EnumMap<>(Skill.class);

    private SpendableClient() {
    }

    private static long clock() {
        return net.minecraft.Util.getMillis();
    }

    /** The last worked-out summary (the layout demo's while it runs). */
    public static Spendable.Summary summary() {
        if (demo != null) {
            return demo;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player != player) {
            // A screen opened before the first tick of a new world: work it out now.
            tick(mc);
        }
        return summary;
    }

    public static Spendable.Report report(Skill skill) {
        return summary().of(skill);
    }

    /** For the layout demo only: show this summary instead of the real one; null ends it. */
    public static void demo(Spendable.Summary shown) {
        demo = shown;
    }

    /** The layout demo: ready toasts fire only while its own scene asks for them (other scenes stay clean). */
    private static boolean demoToasts;

    static void demoToasts(boolean on) {
        demoToasts = on;
        TOASTED.clear();
    }

    /** Works it out again at once (the layout demo after it changed the player's tree). */
    public static void refresh() {
        computedAt = Long.MIN_VALUE / 2;
        skillsSig = 0;
    }

    private static int skillsSignature(PlayerSkills skills) {
        int sig = skills.paidSignature() * 31 + skills.talentRanks().hashCode();
        for (Skill s : Skill.VALUES) {
            sig = sig * 31 + skills.level(s);
        }
        return sig;
    }

    private static int inventorySignature(Inventory inventory) {
        int sig = 1;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                sig = sig * 31 + (System.identityHashCode(stack.getItem()) ^ stack.getCount() * 7919 ^ slot);
            }
        }
        return sig;
    }

    /** Every client tick (both lines' ClientInputEvents). */
    public static void tick(Minecraft mc) {
        Player p = mc.player;
        long now = clock();
        if (p == null) {
            player = null;
            summary = Spendable.Summary.empty();
            return;
        }
        if (p != player) {
            boolean sameWorld = player != null && joinedAt > 0 && now - joinedAt > BASELINE_MS;
            player = p;
            if (!sameWorld) {
                // A new world (or the first): the ranks ready now are the baseline.
                joinedAt = now;
                baselined = false;
            }
            computedAt = Long.MIN_VALUE / 2;
        }
        PlayerSkills skills = ProficiencyAttachments.of(p);
        if (skills == null) {
            return;
        }
        int ss = skillsSignature(skills);
        int is = inventorySignature(p.getInventory());
        boolean due = ss != skillsSig || now - computedAt > REFRESH_MS;
        if (is != invSig) {
            invSig = is;
            invChangedAt = now;
        }
        if (invChangedAt >= 0 && now - invChangedAt >= INVENTORY_DEBOUNCE_MS) {
            invChangedAt = -1;
            due = true;
        }
        if (!due) {
            return;
        }
        skillsSig = ss;
        computedAt = now;
        int levels = skills.totalLevels();
        if (levels != levelSum) {
            levelSum = levels;
            levelsChangedAt = now;
        }
        Spendable.Summary before = summary;
        summary = Spendable.all(skills, materials(p));
        watch(before, now);
    }

    private static void watch(Spendable.Summary before, long now) {
        if (!baselined) {
            if (now - joinedAt < BASELINE_MS) {
                return;
            }
            baselined = true;
            // At most one summary toast for what was already ready when the world loaded.
            int skillsReady = 0;
            for (Spendable.Report r : summary.reports().values()) {
                if (r.readyRanks() > 0) {
                    skillsReady++;
                    TOASTED.put(r.skill(), now);
                }
            }
            if (skillsReady > 0 && ProficiencyClientConfig.uiReadyToasts() && !LayoutAudit.active()) {
                SkillToasts.readySummary(skillsReady, summary.ready());
                chime();
            }
            return;
        }
        if (!ProficiencyClientConfig.uiReadyToasts() || LayoutAudit.active() && !demoToasts) {
            return;
        }
        boolean any = false;
        for (Skill skill : summary.newlyReady(before)) {
            Long last = TOASTED.get(skill);
            if (last != null && now - last < TOAST_GAP_MS) {
                continue;
            }
            TOASTED.put(skill, now);
            Talent node = firstReady(skill);
            SkillToasts.ready(skill, node == null ? Component.empty() : Component.translatable(node.nameKey()));
            any = true;
        }
        if (any) {
            chime();
        }
    }

    private static void chime() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6f, 0.55f));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.3f));
    }

    /** The first node (tree order) whose next rank can be bought now. */
    public static Talent firstReady(Skill skill) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        PlayerSkills skills = ProficiencyAttachments.of(mc.player);
        Spendable.Materials m = materials(mc.player);
        for (Talent t : Talents.of(skill)) {
            if (Spendable.nextReady(skills, t, m)) {
                return t;
            }
        }
        return null;
    }

    /** When the skills' levels last changed (a level-up is on its way to the HUD and its coin will land). */
    public static long levelsChangedAt() {
        return levelsChangedAt;
    }

    /** "3 ready". */
    public static String readyText(int ready) {
        return Component.translatable("proficiency.spend.ready", ready).getString();
    }

    /** The first material short anywhere (the HUD brazier's item badge), or null. */
    public static Spendable.Short firstShort(Spendable.Summary s, Skill only) {
        for (Spendable.Report r : s.reports().values()) {
            if ((only == null || r.skill() == only) && r.materialNodes() > 0 && !r.materials().isEmpty()) {
                return r.materials().get(0);
            }
        }
        return null;
    }

    // ---- Why stuck ------------------------------------------------------------------------------

    /** The reason lines of one skill, indented, in the player's words. */
    public static List<Component> reasons(Spendable.Report r) {
        List<Component> lines = new ArrayList<>();
        for (Spendable.Short s : r.materials()) {
            lines.add(Component.literal("  ").append(Component.translatable("proficiency.spend.why_items",
                    Component.translatable(s.talent().nameKey()), itemName(s.requirement()), s.need(), s.have()))
                    .withStyle(ChatFormatting.RED));
        }
        for (Spendable.Costly c : r.costs()) {
            lines.add(Component.literal("  ").append(Component.translatable("proficiency.spend.why_cost",
                    Component.translatable(c.talent().nameKey()), c.cost(), c.have())).withStyle(ChatFormatting.GOLD));
        }
        if (r.nextLevel() > 0) {
            lines.add(Component.literal("  ").append(Component.translatable("proficiency.spend.why_level", r.nextLevel()))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (r.materials().isEmpty() && r.costs().isEmpty() && r.nextLevel() <= 0) {
            for (Talent t : r.prerequisites()) {
                lines.add(Component.literal("  ").append(Component.translatable("proficiency.spend.why_parent",
                        Component.translatable(t.nameKey()))).withStyle(ChatFormatting.GRAY));
                break;
            }
        }
        return lines;
    }

    /** "Mining: 2 ready, 3 points wait". */
    public static Component head(Spendable.Report r) {
        return Component.translatable("proficiency.spend.why_skill", Component.translatable(r.skill().translationKey()),
                r.readyRanks(), r.leftover()).withStyle(r.readyRanks() > 0 ? ChatFormatting.YELLOW : ChatFormatting.GOLD);
    }

    /**
     * The why-stuck tooltip. {@code focus} is the skill the hover is about (the Face), or null (the dial: every
     * skill whose points wait). Without Shift the focus skill alone, or the first four waiting skills; Shift
     * lists them all. Ends with the total unspent.
     */
    public static List<Component> whyStuck(Skill focus, List<Skill> scope) {
        Spendable.Summary s = summary();
        boolean all = TooltipDetail.detailed();
        List<Component> lines = new ArrayList<>();
        List<Spendable.Report> waiting = new ArrayList<>();
        for (Spendable.Report r : s.waiting()) {
            if (all || focus == null && (scope == null || scope.contains(r.skill())) || r.skill() == focus) {
                waiting.add(r);
            }
        }
        if (focus != null && !all) {
            Spendable.Report r = s.of(focus);
            if (!r.waiting()) {
                lines.add(Component.translatable(r.readyRanks() > 0 ? "proficiency.spend.why_all_ready"
                        : "proficiency.spend.why_nothing", Component.translatable(focus.translationKey()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        int shown = 0;
        int cap = all ? Integer.MAX_VALUE : 4;
        for (Spendable.Report r : waiting) {
            if (shown >= cap) {
                break;
            }
            lines.add(head(r));
            List<Component> why = reasons(r);
            if (why.isEmpty()) {
                why = List.of(Component.literal("  ").append(Component.translatable("proficiency.spend.why_unknown"))
                        .withStyle(ChatFormatting.GRAY));
            }
            lines.addAll(all ? why : why.subList(0, Math.min(3, why.size())));
            shown++;
        }
        if (waiting.isEmpty() && (focus == null || all)) {
            lines.add(Component.translatable("proficiency.spend.why_none").withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.translatable("proficiency.spend.total", s.ready(), s.unspent())
                .withStyle(ChatFormatting.DARK_GRAY));
        int more = waiting.size() - shown;
        if (!all) {
            lines.add(Component.translatable(more > 0 ? "proficiency.spend.shift_more" : "proficiency.spend.shift_all",
                    more).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static Component itemName(Requirement requirement) {
        return Requirements.displayName(requirement);
    }

    // ---- Small shared marks ---------------------------------------------------------------------

    /** A 5x7 padlock of fills at ({@code x}, {@code y}): the shackle over a body with a keyhole. */
    public static void lock(GuiGraphics g, int x, int y, int argb) {
        g.fill(x + 1, y, x + 4, y + 1, argb);
        g.fill(x + 1, y + 1, x + 2, y + 3, argb);
        g.fill(x + 3, y + 1, x + 4, y + 3, argb);
        g.fill(x, y + 3, x + 5, y + 7, argb);
        g.fill(x + 2, y + 4, x + 3, y + 6, (argb & 0xFF000000) | 0x1A1413);
    }

    /**
     * The ready marker: a glowing ember dot with the count beside it ({@code count} 0 draws nothing). The dot is
     * at ({@code x}, {@code y}) and 4 px; the number sits right of it. Returns the width drawn. The glow breathes
     * slowly unless motion is off.
     */
    public static int ember(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int count, long now) {
        if (count <= 0) {
            return 0;
        }
        float pulse = ClickFx.motion() ? 0.5f + 0.5f * (float) Math.sin(now / 420.0) : 0.7f;
        int halo = Math.round(70 + 60 * pulse);
        g.fill(x - 2, y - 2, x + 6, y + 6, (Math.round(halo * 0.45f) << 24) | 0xE2571B);
        g.fill(x - 1, y - 1, x + 5, y + 5, (halo << 24) | 0xFF8A2A);
        g.fill(x, y, x + 4, y + 4, 0xFFFFB347);
        g.fill(x + 1, y + 1, x + 3, y + 3, 0xFFFFF0B0);
        String n = count > 99 ? "99+" : Integer.toString(count);
        g.drawString(font, n, x + 7, y - 2, 0xFFFFC247, true);
        return 7 + font.width(n);
    }

    public static int emberWidth(net.minecraft.client.gui.Font font, int count) {
        return count <= 0 ? 0 : 7 + font.width(count > 99 ? "99+" : Integer.toString(count));
    }
}
