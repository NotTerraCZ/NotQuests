package cz.jirka.notquests.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Right click menu used by the quest book and the editor. Supports one level of nesting per entry. */
public class ContextMenu {
    public enum Result {
        OUTSIDE,
        KEEP_OPEN,
        CLOSE
    }

    public static final class Entry {
        final Component label;
        final Runnable action;
        final List<Entry> children;
        boolean enabled = true;
        int color = Theme.TEXT;
        Icon icon;
        boolean separator;

        private Entry(Component label, Runnable action, List<Entry> children) {
            this.label = label;
            this.action = action;
            this.children = children;
        }

        public static Entry of(Component label, Runnable action) {
            return new Entry(label, action, null);
        }

        public static Entry sub(Component label, List<Entry> children) {
            return new Entry(label, null, children);
        }

        public static Entry separator() {
            Entry e = new Entry(Component.empty(), null, null);
            e.separator = true;
            e.enabled = false;
            return e;
        }

        public Entry icon(Icon icon) {
            this.icon = icon;
            return this;
        }

        public Entry danger() {
            this.color = Theme.DANGER;
            return this;
        }

        public Entry color(int color) {
            this.color = color;
            return this;
        }

        public Entry enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }

    private static final int ROW = 13;
    private final List<Entry> entries;
    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private ContextMenu child;
    private int childIndex = -1;
    private final int screenW;
    private final int screenH;
    private final boolean hasIcons;

    public ContextMenu(List<Entry> entries, int mouseX, int mouseY, int screenW, int screenH) {
        this.entries = new ArrayList<>(entries);
        this.screenW = screenW;
        this.screenH = screenH;
        Font font = GuiUtil.font();
        boolean icons = false;
        for (Entry e : this.entries) {
            icons |= e.icon != null;
        }
        this.hasIcons = icons;
        int width = 60;
        int height = 4;
        for (Entry e : this.entries) {
            if (e.separator) {
                height += 5;
            } else {
                width = Math.max(width, font.width(e.label) + (icons ? 12 : 0) + (e.children != null ? 12 : 0) + 12);
                height += ROW;
            }
        }
        this.w = width;
        this.h = height;
        this.x = Math.max(2, Math.min(mouseX, screenW - w - 2));
        this.y = Math.max(2, Math.min(mouseY, screenH - h - 2));
    }

    public boolean isOver(double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h || child != null && child.isOver(mx, my);
    }

    private int entryAt(double mx, double my) {
        if (mx < x || mx >= x + w) {
            return -1;
        }
        int yy = y + 2;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            int rh = e.separator ? 5 : ROW;
            if (my >= yy && my < yy + rh) {
                return e.separator ? -1 : i;
            }
            yy += rh;
        }
        return -1;
    }

    private int rowY(int index) {
        int yy = y + 2;
        for (int i = 0; i < index; i++) {
            yy += entries.get(i).separator ? 5 : ROW;
        }
        return yy;
    }

    public void render(GuiGraphics g, int mx, int my) {
        Font font = GuiUtil.font();
        g.pose().pushPose();
        g.pose().translate(0F, 0F, 600F);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0x60000000);
        GuiUtil.frame(g, x, y, w, h, 0xF8191A20, Theme.BORDER_LIGHT);
        int hovered = child != null && child.isOver(mx, my) ? -1 : entryAt(mx, my);
        int yy = y + 2;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.separator) {
                g.fill(x + 4, yy + 2, x + w - 4, yy + 3, Theme.BORDER);
                yy += 5;
                continue;
            }
            boolean hover = i == hovered || i == childIndex && child != null;
            if (hover && e.enabled) {
                g.fill(x + 1, yy, x + w - 1, yy + ROW, Theme.PANEL_HOVER);
            }
            int tx = x + 5;
            if (e.icon != null) {
                GuiUtil.icon(g, e.icon, tx, yy + 2.5F, 8, e.enabled ? e.color : Theme.TEXT_DARK);
            }
            if (hasIcons) {
                tx += 12;
            }
            g.drawString(font, e.label, tx, yy + 3, e.enabled ? e.color : Theme.TEXT_DARK, false);
            if (e.children != null) {
                g.drawString(font, ">", x + w - 9, yy + 3, Theme.TEXT_DIM, false);
            }
            yy += ROW;
        }
        g.pose().popPose();
        if (child != null) {
            child.render(g, mx, my);
        }
    }

    /** Test hook: "clicks" the entry whose label starts with the text (in the deepest open sub menu first). */
    public Result clickByLabel(String text) {
        if (child != null) {
            Result r = child.clickByLabel(text);
            if (r != Result.OUTSIDE) {
                return r;
            }
        }
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String label = net.minecraft.ChatFormatting.stripFormatting(e.label.getString());
            if (e.separator || !e.enabled || label == null || !label.replace("✔ ", "").startsWith(text)) {
                continue;
            }
            if (e.children != null) {
                child = new ContextMenu(e.children, x + w - 2, rowY(i) - 2, screenW, screenH);
                childIndex = i;
                return Result.KEEP_OPEN;
            }
            if (e.action != null) {
                e.action.run();
            }
            return Result.CLOSE;
        }
        return Result.OUTSIDE;
    }

    public Result mouseClicked(double mx, double my, int button) {
        if (child != null) {
            Result r = child.mouseClicked(mx, my, button);
            if (r != Result.OUTSIDE) {
                return r;
            }
        }
        int i = entryAt(mx, my);
        if (i < 0) {
            if (mx >= x && my >= y && mx < x + w && my < y + h) {
                return Result.KEEP_OPEN;
            }
            return Result.OUTSIDE;
        }
        Entry e = entries.get(i);
        if (!e.enabled) {
            return Result.KEEP_OPEN;
        }
        if (e.children != null) {
            if (childIndex == i && child != null) {
                child = null;
                childIndex = -1;
            } else {
                child = new ContextMenu(e.children, x + w - 2, rowY(i) - 2, screenW, screenH);
                childIndex = i;
            }
            return Result.KEEP_OPEN;
        }
        if (e.action != null) {
            e.action.run();
        }
        return Result.CLOSE;
    }
}
