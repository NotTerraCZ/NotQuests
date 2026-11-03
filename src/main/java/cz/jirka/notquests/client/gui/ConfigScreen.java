package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.ConfigValue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Generic property editor for quest objects. */
public class ConfigScreen extends Screen {
    private static final int ROW_H = 22;
    private static final int OK_COLOR = 0xFFE0E0E0;
    private static final int BAD_COLOR = 0xFFFF6060;

    private final ConfigGroup group;
    private final Screen parent;
    private final Consumer<Boolean> callback;
    private final List<Row> rows = new ArrayList<>();
    private double scroll;
    private int listTop;
    private int listBottom;
    private int left;
    private int contentWidth;
    private boolean closed;

    private record Row(ConfigValue<?> value, List<AbstractWidget> widgets) {
    }

    public ConfigScreen(ConfigGroup group, Screen parent, Consumer<Boolean> callback) {
        super(group.title);
        this.group = group;
        this.parent = parent;
        this.callback = callback;
    }

    @Override
    protected void init() {
        rows.clear();
        listTop = 30;
        listBottom = height - 34;
        contentWidth = Math.min(380, width - 20);
        left = (width - contentWidth) / 2;
        int labelW = (int) (contentWidth * 0.42F);
        int wx = left + labelW + 6;
        int ww = contentWidth - labelW - 6;

        for (ConfigValue<?> v : group.values) {
            List<AbstractWidget> widgets = new ArrayList<>();
            createWidgets(v, wx, ww, widgets);
            for (AbstractWidget w : widgets) {
                addRenderableWidget(w);
            }
            rows.add(new Row(v, widgets));
        }

        int bw = 100;
        addRenderableWidget(new FlatButton(width / 2 - bw - 4, height - 26, bw, 18, Component.translatable("notquests.gui.save"), b -> save())
                .textColor(() -> Theme.COMPLETE));
        addRenderableWidget(new FlatButton(width / 2 + 4, height - 26, bw, 18, Component.translatable("notquests.gui.cancel"), b -> cancel()));
        clampScroll();
        layoutRows();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void createWidgets(ConfigValue<?> v, int wx, int ww, List<AbstractWidget> widgets) {
        if (v instanceof ConfigValue.StringValue sv) {
            EditBox e = new EditBox(font, wx + 1, 0, ww - 2, 18, sv.getLabel());
            e.setMaxLength(32767);
            e.setValue(sv.value);
            e.moveCursorToStart(false);
            e.setResponder(s -> sv.value = s);
            widgets.add(e);
        } else if (v instanceof ConfigValue.IntValue iv) {
            EditBox e = new EditBox(font, wx + 1, 0, ww - 2, 18, iv.getLabel());
            e.setValue(Integer.toString(iv.value));
            e.setResponder(s -> {
                try {
                    int n = Integer.parseInt(s.trim());
                    boolean ok = n >= iv.min && n <= iv.max;
                    if (ok) {
                        iv.value = n;
                    }
                    e.setTextColor(ok ? OK_COLOR : BAD_COLOR);
                } catch (NumberFormatException ex) {
                    e.setTextColor(BAD_COLOR);
                }
            });
            widgets.add(e);
        } else if (v instanceof ConfigValue.LongValue lv) {
            EditBox e = new EditBox(font, wx + 1, 0, ww - 2, 18, lv.getLabel());
            e.setValue(Long.toString(lv.value));
            e.setResponder(s -> {
                try {
                    long n = Long.parseLong(s.trim());
                    boolean ok = n >= lv.min && n <= lv.max;
                    if (ok) {
                        lv.value = n;
                    }
                    e.setTextColor(ok ? OK_COLOR : BAD_COLOR);
                } catch (NumberFormatException ex) {
                    e.setTextColor(BAD_COLOR);
                }
            });
            widgets.add(e);
        } else if (v instanceof ConfigValue.DoubleValue dv) {
            EditBox e = new EditBox(font, wx + 1, 0, ww - 2, 18, dv.getLabel());
            e.setValue(formatDouble(dv.value));
            e.setResponder(s -> {
                try {
                    double n = Double.parseDouble(s.trim().replace(',', '.'));
                    boolean ok = n >= dv.min && n <= dv.max && Double.isFinite(n);
                    if (ok) {
                        dv.value = n;
                    }
                    e.setTextColor(ok ? OK_COLOR : BAD_COLOR);
                } catch (NumberFormatException ex) {
                    e.setTextColor(BAD_COLOR);
                }
            });
            widgets.add(e);
        } else if (v instanceof ConfigValue.BoolValue bv) {
            widgets.add(new FlatButton(wx, 0, ww, 18, Component.empty(), b -> bv.value = !bv.value)
                    .label(() -> Component.translatable(bv.value ? "notquests.gui.true" : "notquests.gui.false"))
                    .textColor(() -> bv.value ? Theme.COMPLETE : Theme.DANGER));
        } else if (v instanceof ConfigValue.EnumValue ev) {
            widgets.add(new FlatButton(wx, 0, ww, 18, Component.empty(), b -> ev.cycle(b == 1))
                    .label(() -> ((cz.jirka.notquests.quest.NamedEnum) ev.value).displayName()));
        } else if (v instanceof ConfigValue.ChoiceValue cv) {
            widgets.add(new FlatButton(wx, 0, ww, 18, Component.empty(), b -> cv.cycle(b == 1)).label(cv::currentName));
        } else if (v instanceof ConfigValue.ItemValue iv) {
            widgets.add(new FlatButton(wx, 0, ww, 20, Component.empty(), b -> {
                if (b == 1 && iv.allowEmpty) {
                    iv.value = net.minecraft.world.item.ItemStack.EMPTY;
                } else {
                    minecraft.setScreen(new ItemSelectScreen(this, iv.allowEmpty, s -> iv.value = s));
                }
            }).item(() -> iv.value.isEmpty() ? iv.emptyPreview.get() : iv.value)
                    .label(() -> iv.value.isEmpty() ? Component.translatable(iv.emptyLabel).withStyle(ChatFormatting.GRAY) : iv.value.getHoverName()));
        } else if (v instanceof ConfigValue.TextListValue tv) {
            widgets.add(new FlatButton(wx, 0, ww, 18, Component.empty(),
                    b -> minecraft.setScreen(new TextListScreen(this, tv.getLabel(), tv.value, lines -> tv.value = lines)))
                    .label(() -> Component.translatable("notquests.gui.edit_lines", tv.value.size())));
        } else if (v instanceof ConfigValue.IdValue idv) {
            EditBox e = new EditBox(font, wx + 1, 0, ww - 26, 18, idv.getLabel());
            e.setMaxLength(512);
            e.setValue(idv.value);
            e.moveCursorToStart(false);
            e.setResponder(s -> idv.value = s.trim());
            widgets.add(e);
            widgets.add(new FlatButton(wx + ww - 22, 0, 22, 18, Component.literal("..."),
                    b -> minecraft.setScreen(new IdSelectScreen(this, idv.kind, s -> idv.value = s))));
        } else if (v instanceof ConfigValue.WeightedItemsValue wv) {
            widgets.add(new FlatButton(wx, 0, ww, 18, Component.empty(),
                    b -> minecraft.setScreen(new WeightedItemsScreen(this, wv.value, list -> wv.value = list)))
                    .label(() -> Component.translatable("notquests.gui.edit_entries", wv.value.size())));
        }
    }

    private static String formatDouble(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e12) {
            return Long.toString((long) d);
        }
        return String.format(Locale.ROOT, "%s", d);
    }

    private int totalHeight() {
        return rows.size() * ROW_H;
    }

    private void clampScroll() {
        double max = Math.max(0, totalHeight() - (listBottom - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private void layoutRows() {
        for (int i = 0; i < rows.size(); i++) {
            int y = (int) (listTop + i * ROW_H - scroll);
            boolean visible = y >= listTop - 1 && y + ROW_H <= listBottom + 1;
            for (AbstractWidget w : rows.get(i).widgets) {
                w.setY(y + 2 + (18 - w.getHeight()) / 2 + (w.getHeight() > 18 ? 0 : 0));
                w.visible = visible;
            }
        }
    }

    private void save() {
        if (closed) {
            return;
        }
        closed = true;
        group.applyAll();
        minecraft.setScreen(parent);
        callback.accept(true);
    }

    private void cancel() {
        if (closed) {
            return;
        }
        closed = true;
        minecraft.setScreen(parent);
        callback.accept(false);
    }

    @Override
    public void onClose() {
        cancel();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW_H;
        clampScroll();
        layoutRows();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Theme.SCREEN);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(0, 0, width, 24, Theme.PANEL);
        g.fill(0, 24, width, 25, Theme.BORDER);
        GuiUtil.centeredString(g, title, width / 2F, 8, Theme.TEXT, false);
        g.fill(0, height - 32, width, height, Theme.PANEL);
        g.fill(0, height - 33, width, height - 32, Theme.BORDER);

        Component tooltip = null;
        for (int i = 0; i < rows.size(); i++) {
            int y = (int) (listTop + i * ROW_H - scroll);
            if (y < listTop - 1 || y + ROW_H > listBottom + 1) {
                continue;
            }
            if (i % 2 == 0) {
                g.fill(left - 4, y, left + contentWidth + 4, y + ROW_H, 0x18FFFFFF);
            }
            Row row = rows.get(i);
            Component label = row.value.getLabel();
            int labelW = (int) (contentWidth * 0.42F);
            g.drawString(font, GuiUtil.trim(label, labelW), left, y + 7, Theme.TEXT_DIM, false);
            if (mouseX >= left && mouseX < left + labelW && mouseY >= y && mouseY < y + ROW_H) {
                String key = row.value.tooltipKey();
                if (I18n.exists(key)) {
                    tooltip = Component.translatable(key);
                } else if (font.width(label) > labelW) {
                    tooltip = label;
                }
            }
        }
        if (rows.isEmpty()) {
            GuiUtil.centeredString(g, Component.translatable("notquests.gui.nothing_to_edit"), width / 2F, listTop + 10, Theme.TEXT_DIM, false);
        }
        for (var r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        if (tooltip != null) {
            g.renderTooltip(font, font.split(tooltip, 220), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
