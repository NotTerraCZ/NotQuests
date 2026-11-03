package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.quest.WeightedItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Editor for the entries of a reward table: item, count and weight. */
public class WeightedItemsScreen extends Screen {
    private static final int ROW_H = 24;
    private final Screen parent;
    private final Consumer<List<WeightedItem>> callback;
    private final List<WeightedItem> entries;
    private final List<List<AbstractWidget>> rowWidgets = new ArrayList<>();
    private double scroll;
    private int left;
    private int listTop;
    private int listBottom;

    public WeightedItemsScreen(Screen parent, List<WeightedItem> entries, Consumer<List<WeightedItem>> callback) {
        super(Component.translatable("notquests.gui.reward_table_entries"));
        this.parent = parent;
        this.callback = callback;
        this.entries = new ArrayList<>();
        for (WeightedItem e : entries) {
            this.entries.add(e.copy());
        }
    }

    @Override
    protected void init() {
        rowWidgets.clear();
        int w = Math.min(360, width - 20);
        left = (width - w) / 2;
        listTop = 30;
        listBottom = height - 60;
        for (int i = 0; i < entries.size(); i++) {
            WeightedItem e = entries.get(i);
            List<AbstractWidget> ws = new ArrayList<>();
            ws.add(new FlatButton(left, 0, w - 150, 20, Component.empty(),
                    b -> minecraft.setScreen(new ItemSelectScreen(this, false, s -> {
                        if (!s.isEmpty()) {
                            e.stack = s.copyWithCount(Math.max(1, e.stack.getCount()));
                        }
                    }))).item(() -> e.stack).label(() -> e.stack.getHoverName()));
            EditBox count = new EditBox(font, left + w - 146, 0, 44, 18, Component.translatable("notquests.config.count"));
            count.setValue(Integer.toString(e.stack.getCount()));
            count.setResponder(s -> {
                try {
                    int n = Integer.parseInt(s.trim());
                    if (n >= 1 && n <= 99) {
                        e.stack.setCount(n);
                        count.setTextColor(0xFFE0E0E0);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                }
                count.setTextColor(0xFFFF6060);
            });
            ws.add(count);
            EditBox weight = new EditBox(font, left + w - 96, 0, 60, 18, Component.translatable("notquests.config.weight"));
            weight.setValue(Integer.toString(e.weight));
            weight.setResponder(s -> {
                try {
                    int n = Integer.parseInt(s.trim());
                    if (n >= 0) {
                        e.weight = n;
                        weight.setTextColor(0xFFE0E0E0);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                }
                weight.setTextColor(0xFFFF6060);
            });
            ws.add(weight);
            int index = i;
            ws.add(new FlatButton(left + w - 30, 0, 30, 18, Component.empty(), b -> {
                entries.remove(index);
                rebuildWidgets();
            }).icon(Icon.TRASH, Theme.DANGER));
            for (AbstractWidget aw : ws) {
                addRenderableWidget(aw);
            }
            rowWidgets.add(ws);
        }
        addRenderableWidget(new FlatButton(width / 2 - 75, height - 52, 150, 18, Component.translatable("notquests.gui.add_entry"),
                b -> minecraft.setScreen(new ItemSelectScreen(this, false, s -> {
                    if (!s.isEmpty()) {
                        entries.add(new WeightedItem(s.copyWithCount(1), 1));
                    }
                }))).icon(Icon.PLUS, Theme.COMPLETE));
        addRenderableWidget(new FlatButton(width / 2 - 104, height - 26, 100, 18, Component.translatable("notquests.gui.save"), b -> {
            List<WeightedItem> result = new ArrayList<>();
            for (WeightedItem e : entries) {
                if (!e.stack.isEmpty()) {
                    result.add(e);
                }
            }
            minecraft.setScreen(parent);
            callback.accept(result);
        }).textColor(() -> Theme.COMPLETE));
        addRenderableWidget(new FlatButton(width / 2 + 4, height - 26, 100, 18, Component.translatable("notquests.gui.cancel"), b -> onClose()));
        clampScroll();
        layout();
    }

    private void clampScroll() {
        double max = Math.max(0, entries.size() * ROW_H - (listBottom - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void layout() {
        for (int i = 0; i < rowWidgets.size(); i++) {
            int y = (int) (listTop + i * ROW_H - scroll);
            boolean visible = y >= listTop - 1 && y + ROW_H <= listBottom + 1;
            for (AbstractWidget w : rowWidgets.get(i)) {
                w.setY(y + (20 - w.getHeight()) / 2 + 1);
                w.visible = visible;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW_H;
        clampScroll();
        layout();
        return true;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Theme.SCREEN);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        GuiUtil.centeredString(g, title, width / 2F, 10, Theme.TEXT, false);
        int w = Math.min(360, width - 20);
        g.drawString(font, Component.translatable("notquests.config.count"), left + w - 146, listTop - 10, Theme.TEXT_DARK, false);
        g.drawString(font, Component.translatable("notquests.config.weight"), left + w - 96, listTop - 10, Theme.TEXT_DARK, false);
        if (entries.isEmpty()) {
            GuiUtil.centeredString(g, Component.translatable("notquests.gui.no_entries"), width / 2F, listTop + 10, Theme.TEXT_DIM, false);
        }
        int total = 0;
        for (WeightedItem e : entries) {
            total += e.weight;
        }
        for (int i = 0; i < entries.size(); i++) {
            int y = (int) (listTop + i * ROW_H - scroll);
            if (y >= listTop - 1 && y + ROW_H <= listBottom + 1 && total > 0) {
                String pct = String.format(java.util.Locale.ROOT, "%.0f%%", entries.get(i).weight * 100D / total);
                g.drawString(font, pct, left + w + 4, y + 7, Theme.TEXT_DARK, false);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
