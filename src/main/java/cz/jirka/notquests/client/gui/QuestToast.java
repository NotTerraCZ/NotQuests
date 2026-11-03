package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.quest.QuestObjectBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class QuestToast implements Toast {
    public enum Kind {
        QUEST(Theme.COMPLETE),
        CHAPTER(Theme.CLAIM),
        MESSAGE(Theme.ACCENT);

        final int color;

        Kind(int color) {
            this.color = color;
        }
    }

    private final Component title;
    private final Component text;
    private final QuestObjectBase object;
    private final ItemStack icon;
    private final Kind kind;
    private final int width;

    public QuestToast(Component title, Component text, QuestObjectBase object, Kind kind) {
        this.title = title;
        this.text = text;
        this.object = object;
        this.icon = ItemStack.EMPTY;
        this.kind = kind;
        this.width = computeWidth();
    }

    public QuestToast(Component title, Component text, ItemStack icon) {
        this.title = title;
        this.text = text;
        this.object = null;
        this.icon = icon;
        this.kind = Kind.MESSAGE;
        this.width = computeWidth();
    }

    private int computeWidth() {
        var font = Minecraft.getInstance().font;
        int w = 30 + Math.max(font.width(title), font.width(text)) + 10;
        return Math.max(160, Math.min(260, w));
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent component, long time) {
        var font = Minecraft.getInstance().font;
        int w = width();
        int h = height();
        g.fill(0, 0, w, h, kind.color);
        g.fill(1, 1, w - 1, h - 1, 0xF2121318);
        g.fill(1, 1, 3, h - 1, kind.color);
        if (object != null) {
            IconRenderer.render(g, object, 8, 8, 16);
        } else if (!icon.isEmpty()) {
            g.renderItem(icon, 8, 8);
        }
        g.drawString(font, title, 30, 7, kind.color, false);
        g.drawString(font, GuiUtil.trim(text, w - 36), 30, 18, Theme.TEXT, false);
        return time >= 5000L * component.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }
}
