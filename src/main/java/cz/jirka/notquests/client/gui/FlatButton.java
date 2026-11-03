package cz.jirka.notquests.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Dark themed button that also reacts to right clicks (button index is passed to the action). */
public class FlatButton extends AbstractWidget {
    private final IntConsumer action;
    private Supplier<Component> label;
    private Supplier<Integer> textColor = () -> Theme.TEXT;
    private Supplier<ItemStack> item;
    private Icon icon;
    private int iconColor = Theme.TEXT;
    private boolean centered = true;
    private int fill = Theme.PANEL_LIGHT;
    private int border = Theme.BORDER;

    public FlatButton(int x, int y, int w, int h, Component label, IntConsumer action) {
        super(x, y, w, h, label);
        this.label = () -> label;
        this.action = action;
    }

    public FlatButton label(Supplier<Component> label) {
        this.label = label;
        return this;
    }

    public FlatButton textColor(Supplier<Integer> color) {
        this.textColor = color;
        return this;
    }

    public FlatButton item(Supplier<ItemStack> item) {
        this.item = item;
        this.centered = false;
        return this;
    }

    public FlatButton icon(Icon icon, int color) {
        this.icon = icon;
        this.iconColor = color;
        return this;
    }

    public FlatButton left() {
        this.centered = false;
        return this;
    }

    public FlatButton colors(int fill, int border) {
        this.fill = fill;
        this.border = border;
        return this;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        int bg = !active ? Theme.PANEL : isHovered() ? Theme.PANEL_HOVER : fill;
        GuiUtil.frame(g, getX(), getY(), width, height, bg, isHovered() && active ? Theme.BORDER_LIGHT : border);
        int tx = getX() + 4;
        if (item != null) {
            ItemStack s = item.get();
            if (!s.isEmpty()) {
                g.renderItem(s, getX() + 2, getY() + (height - 16) / 2);
            }
            tx = getX() + 21;
        }
        if (icon != null) {
            float size = Math.min(12, height - 6);
            float ix = label.get().getString().isEmpty() ? getX() + (width - size) / 2F : getX() + 4;
            GuiUtil.icon(g, icon, ix, getY() + (height - size) / 2F, size, active ? iconColor : Theme.TEXT_DARK);
            tx = (int) (ix + size + 4);
        }
        Component text = label.get();
        if (!text.getString().isEmpty()) {
            int color = active ? textColor.get() : Theme.TEXT_DARK;
            int avail = getX() + width - 4 - tx;
            if (centered && item == null && icon == null) {
                var seq = GuiUtil.trim(text, width - 8);
                g.drawString(font, seq, getX() + (width - font.width(seq)) / 2, getY() + (height - 8) / 2, color, false);
            } else {
                g.drawString(font, GuiUtil.trim(text, avail), tx, getY() + (height - 8) / 2, color, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (active && visible && (button == 0 || button == 1) && isMouseOver(mouseX, mouseY)) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            action.accept(button);
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
