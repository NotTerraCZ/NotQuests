package cz.jirka.notquests.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Item picker with search, like the creative search tab. */
public class ItemSelectScreen extends Screen {
    private static List<ItemStack> cachedAll;

    private final Screen parent;
    private final boolean allowEmpty;
    private final Consumer<ItemStack> callback;
    private EditBox search;
    private String query = "";
    private boolean inventoryMode;
    private List<ItemStack> filtered = new ArrayList<>();
    private int scrollRow;
    private int gridX;
    private int gridY;
    private int cols;
    private int rows;
    private static final int CELL = 18;

    public ItemSelectScreen(Screen parent, boolean allowEmpty, Consumer<ItemStack> callback) {
        super(Component.translatable("notquests.gui.select_item"));
        this.parent = parent;
        this.allowEmpty = allowEmpty;
        this.callback = callback;
    }

    private static List<ItemStack> allItems() {
        if (cachedAll == null) {
            List<ItemStack> list = new ArrayList<>();
            var mc = net.minecraft.client.Minecraft.getInstance();
            try {
                if (mc.player != null) {
                    CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.player.level().registryAccess());
                    Collection<ItemStack> items = CreativeModeTabs.searchTab().getSearchTabDisplayItems();
                    for (ItemStack s : items) {
                        list.add(s.copyWithCount(1));
                    }
                }
            } catch (Exception ignored) {
            }
            if (list.isEmpty()) {
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item != Items.AIR) {
                        list.add(new ItemStack(item));
                    }
                }
            }
            cachedAll = list;
        }
        return cachedAll;
    }

    @Override
    protected void init() {
        int w = Math.min(width - 20, 18 * 18 + 8);
        cols = Math.max(4, (w - 8) / CELL);
        gridX = (width - cols * CELL) / 2;
        gridY = 52;
        rows = Math.max(2, (height - gridY - 50) / CELL);

        search = new EditBox(font, gridX, 26, cols * CELL - 156, 18, Component.translatable("notquests.gui.search"));
        search.setHint(Component.translatable("notquests.gui.search_hint"));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scrollRow = 0;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        int bx = gridX + cols * CELL - 152;
        addRenderableWidget(new FlatButton(bx, 26, 74, 18, Component.empty(), b -> {
            inventoryMode = !inventoryMode;
            scrollRow = 0;
            refilter();
        }).label(() -> Component.translatable(inventoryMode ? "notquests.gui.inventory" : "notquests.gui.all_items")));
        addRenderableWidget(new FlatButton(bx + 78, 26, 74, 18, Component.translatable("notquests.gui.held_item"), b -> {
            ItemStack held = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
            if (!held.isEmpty()) {
                select(held.copyWithCount(1));
            }
        }));

        int by = height - 26;
        if (allowEmpty) {
            addRenderableWidget(new FlatButton(width / 2 - 104, by, 100, 18, Component.translatable("notquests.none"), b -> select(ItemStack.EMPTY)));
            addRenderableWidget(new FlatButton(width / 2 + 4, by, 100, 18, Component.translatable("notquests.gui.cancel"), b -> onClose()));
        } else {
            addRenderableWidget(new FlatButton(width / 2 - 50, by, 100, 18, Component.translatable("notquests.gui.cancel"), b -> onClose()));
        }
        refilter();
    }

    private void refilter() {
        filtered = new ArrayList<>();
        List<ItemStack> source;
        if (inventoryMode) {
            source = new ArrayList<>();
            if (minecraft.player != null) {
                var inv = minecraft.player.getInventory();
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    ItemStack s = inv.getItem(i);
                    if (!s.isEmpty()) {
                        boolean dup = false;
                        for (ItemStack o : source) {
                            if (ItemStack.isSameItemSameComponents(o, s)) {
                                dup = true;
                                break;
                            }
                        }
                        if (!dup) {
                            source.add(s.copyWithCount(1));
                        }
                    }
                }
            }
        } else {
            source = allItems();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (ItemStack s : source) {
            if (q.isEmpty()) {
                filtered.add(s);
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(s.getItem());
            if (q.startsWith("@")) {
                if (id.getNamespace().startsWith(q.substring(1))) {
                    filtered.add(s);
                }
            } else if (s.getHoverName().getString().toLowerCase(Locale.ROOT).contains(q) || id.toString().contains(q)) {
                filtered.add(s);
            }
        }
    }

    private void select(ItemStack stack) {
        minecraft.setScreen(parent);
        callback.accept(stack);
    }

    private int maxScroll() {
        int totalRows = (filtered.size() + cols - 1) / cols;
        return Math.max(0, totalRows - rows);
    }

    private int indexAt(double mx, double my) {
        if (mx < gridX || my < gridY || mx >= gridX + cols * CELL || my >= gridY + rows * CELL) {
            return -1;
        }
        int c = (int) ((mx - gridX) / CELL);
        int r = (int) ((my - gridY) / CELL);
        int i = (scrollRow + r) * cols + c;
        return i < filtered.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = indexAt(mx, my);
        if (i >= 0 && button == 0) {
            select(filtered.get(i).copyWithCount(1));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollRow = Math.max(0, Math.min(maxScroll(), scrollRow - (int) Math.signum(scrollY) * 2));
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
        GuiUtil.frame(g, gridX - 4, gridY - 4, cols * CELL + 8, rows * CELL + 8, Theme.PANEL, Theme.BORDER);
        int hovered = indexAt(mouseX, mouseY);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scrollRow + r) * cols + c;
                if (i >= filtered.size()) {
                    break;
                }
                int x = gridX + c * CELL;
                int y = gridY + r * CELL;
                if (i == hovered) {
                    g.fill(x, y, x + CELL, y + CELL, Theme.PANEL_HOVER);
                }
                g.renderItem(filtered.get(i), x + 1, y + 1);
            }
        }
        int max = maxScroll();
        if (max > 0) {
            int barH = Math.max(10, rows * CELL * rows / (rows + max));
            int barY = gridY + (rows * CELL - barH) * scrollRow / max;
            g.fill(gridX + cols * CELL + 1, barY, gridX + cols * CELL + 3, barY + barH, Theme.BORDER_LIGHT);
        }
        g.drawString(font, Component.translatable("notquests.gui.items_found", filtered.size()), gridX, gridY + rows * CELL + 6, Theme.TEXT_DARK, false);
        if (hovered >= 0) {
            g.renderTooltip(font, filtered.get(hovered), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
