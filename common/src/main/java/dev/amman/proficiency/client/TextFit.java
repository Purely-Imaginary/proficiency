package dev.amman.proficiency.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Text that has to fit a space it does not control, because the language chooses how long it is.
 *
 * <p>Every screen of the mod used to cut text at a fixed pixel count or run it into its neighbour.
 * These helpers give each place the same three answers: clip with an ellipsis (and let the caller
 * show the whole text on hover), wrap onto more lines where there is height, or pack the pieces of
 * a "a · b · c" line so a piece moves whole to the next line and none is cut in the middle.
 *
 * <p>{@link #clips} counts every time text had to be shortened or wrapped, per site, so a test
 * client can report which places still overflow in a language.
 */
final class TextFit {

    static final String ELLIPSIS = "…";
    static final String DOT = " \u00B7 ";
    static final String DOT_RE = "\\s*\u00B7\\s*";

    /** Site name to how often it had to cut or wrap since the last {@link #resetClips}. */
    private static final java.util.Map<String, Integer> CLIPS = new java.util.TreeMap<>();

    private TextFit() {
    }

    static void note(String site) {
        if (LayoutAudit.active()) {
            CLIPS.merge(site, 1, Integer::sum);
        }
    }

    static java.util.Map<String, Integer> clips() {
        return CLIPS;
    }

    static void resetClips() {
        CLIPS.clear();
    }

    /** {@code text} cut to {@code width} pixels with an ellipsis, or whole when it already fits. */
    static String clip(Font font, String text, int width) {
        return TextBreak.clip(font::width, text, width);
    }

    /** The same for text that carries styles. */
    static FormattedCharSequence clip(Font font, FormattedText text, int width) {
        if (font.width(text) <= width) {
            return Language.reorder(text);
        }
        int room = width - font.width(ELLIPSIS);
        if (room <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        return FormattedCharSequence.composite(Language.reorder(font.substrByWidth(text, room)),
                FormattedCharSequence.forward(ELLIPSIS, Style.EMPTY));
    }

    /** Draws {@code text} cut to {@code width}; true when it had to be cut. */
    static boolean draw(GuiGraphics graphics, Font font, String site, String text, int x, int y, int width,
            int colour, boolean shadow) {
        String shown = clip(font, text, width);
        boolean cut = !shown.equals(text);
        if (cut) {
            note(site);
        }
        graphics.drawString(font, shown, x, y, colour, shadow);
        return cut;
    }

    /** Wraps styled text to {@code width}. A single-style text goes through {@link TextBreak}. */
    static List<FormattedCharSequence> wrap(Font font, FormattedText text, int width) {
        Style uniform = text instanceof Component component ? uniformStyle(component) : null;
        if (uniform == null) {
            return font.split(text, Math.max(8, width));
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String line : TextBreak.wrap(font::width, text.getString(), Math.max(8, width))) {
            out.add(FormattedCharSequence.forward(line, uniform));
        }
        return out;
    }

    /** The one style every part of the component is drawn in, or null when it mixes styles. */
    private static Style uniformStyle(Component component) {
        Style[] found = new Style[1];
        boolean[] mixed = new boolean[1];
        component.getVisualOrderText().accept((index, style, codePoint) -> {
            if (found[0] == null) {
                found[0] = style;
            } else if (!found[0].equals(style)) {
                mixed[0] = true;
                return false;
            }
            return true;
        });
        return mixed[0] || found[0] == null ? null : found[0];
    }

    /** The lines of a wrapped string as plain strings (for code that draws with a plain colour). */
    static List<String> wrapPlain(Font font, String text, int width) {
        return TextBreak.wrap(font::width, text, Math.max(8, width));
    }

    static List<String> pack(Font font, String text, int width, int maxLines, boolean[] overflow, String splitRegex,
            String join) {
        return TextBreak.pack(font::width, text, width, maxLines, overflow, splitRegex, join);
    }

    /** {@code text} wrapped to {@code width}, at most {@code maxLines} lines, the last one clipped. */
    static List<String> wrapLimited(Font font, String text, int width, int maxLines, boolean[] overflow) {
        return TextBreak.limit(font::width, wrapPlain(font, text, width), width, maxLines, overflow);
    }

    /** Areas of one frame whose text was cut, so the whole text can show on hover. */
    static final class Hovers {
        private final List<int[]> areas = new ArrayList<>();
        private final List<Component> texts = new ArrayList<>();

        void clear() {
            areas.clear();
            texts.clear();
        }

        void add(int x, int y, int w, int h, Component full) {
            areas.add(new int[] {x, y, w, h});
            texts.add(full);
        }

        /** The full text under the pointer, or null. */
        List<Component> at(int mouseX, int mouseY) {
            for (int i = areas.size() - 1; i >= 0; i--) {
                int[] a = areas.get(i);
                if (mouseX >= a[0] && mouseX < a[0] + a[2] && mouseY >= a[1] && mouseY < a[1] + a[3]) {
                    return List.of(texts.get(i));
                }
            }
            return null;
        }
    }

    /** Splits each component to {@code width} and returns components again (for a vanilla tooltip list). */
    static List<Component> wrapComponents(Font font, List<Component> lines, int width) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) {
            if (font.width(line) <= width) {
                out.add(line);
                continue;
            }
            note("tooltip.wrap");
            Style uniform = uniformStyle(line);
            if (uniform != null) {
                for (String piece : TextBreak.wrap(font::width, line.getString(), width)) {
                    out.add(Component.literal(piece).setStyle(uniform));
                }
                continue;
            }
            for (FormattedText piece : font.getSplitter().splitLines(line, width, Style.EMPTY)) {
                MutableComponent built = Component.empty();
                piece.visit((style, string) -> {
                    built.append(Component.literal(string).setStyle(style));
                    return Optional.empty();
                }, Style.EMPTY);
                out.add(built);
            }
        }
        return out;
    }

    /**
     * A tooltip that always fits the screen: lines wrapped to {@code maxWidth}, placed beside the
     * pointer like the vanilla one, and, when it is still taller than the screen, drawn smaller. The
     * vanilla list tooltip neither wraps nor shrinks, so a long translation ran off the edge.
     */
    static void tooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY,
            int maxWidth) {
        if (lines.isEmpty()) {
            return;
        }
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int limit = Math.max(40, Math.min(maxWidth, screenW - 16));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            List<FormattedCharSequence> pieces = wrap(font, line, limit);
            if (pieces.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(pieces);
            }
        }
        int width = 0;
        for (FormattedCharSequence line : wrapped) {
            width = Math.max(width, font.width(line));
        }
        int height = wrapped.size() * 10 - 1;
        float scale = Math.min(1f, Math.min((screenW - 12f) / (width + 8), (screenH - 12f) / (height + 8)));
        if (scale < 1f) {
            note("tooltip.shrink");
        }
        float realW = (width + 8) * scale;
        float realH = (height + 8) * scale;
        float x = mouseX + 12;
        if (x + realW > screenW - 2) {
            x = mouseX - 12 - realW;
        }
        x = Math.max(2, Math.min(x, screenW - realW - 2));
        float y = Math.max(2, Math.min(mouseY - 12, screenH - realH - 2));
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 400);
        graphics.pose().scale(scale, scale, 1f);
        graphics.fill(1, 0, width + 7, 1, 0xF0100010);
        graphics.fill(1, height + 7, width + 7, height + 8, 0xF0100010);
        graphics.fill(0, 1, width + 8, height + 7, 0xF0100010);
        graphics.fillGradient(1, 1, width + 7, 2, 0x505000FF, 0x505000FF);
        graphics.fillGradient(1, height + 6, width + 7, height + 7, 0x5028007F, 0x5028007F);
        graphics.fillGradient(1, 2, 2, height + 6, 0x505000FF, 0x5028007F);
        graphics.fillGradient(width + 6, 2, width + 7, height + 6, 0x505000FF, 0x5028007F);
        int ty = 4;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(font, line, 4, ty, 0xFFFFFFFF, true);
            ty += 10;
        }
        graphics.pose().popPose();
    }

    /** Whether the stack is one of this mod's items. */
    static boolean isOurs(net.minecraft.world.item.ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()
                .equals(dev.amman.proficiency.Proficiency.MOD_ID);
    }

    /** Short wrapper so {@link #clip(Font, FormattedText, int)} reads without an import clash. */
    private static final class Language {
        static FormattedCharSequence reorder(FormattedText text) {
            return net.minecraft.locale.Language.getInstance().getVisualOrder(text);
        }
    }
}
