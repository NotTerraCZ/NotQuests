package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.client.ClientQuestData;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.net.NQNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Searchable list of ids (entities, biomes, dimensions, structures, advancements...). */
public class IdSelectScreen extends Screen {
    private static final int ROW = 12;
    private final Screen parent;
    private final IdKind kind;
    private final Consumer<String> callback;
    private EditBox search;
    private String query = "";
    private List<String> all;
    private List<String> filtered = new ArrayList<>();
    private int scroll;
    private int listX;
    private int listY;
    private int listW;
    private int rows;

    public IdSelectScreen(Screen parent, IdKind kind, Consumer<String> callback) {
        super(Component.translatable("notquests.gui.select_id." + kind.name().toLowerCase(Locale.ROOT)));
        this.parent = parent;
        this.kind = kind;
        this.callback = callback;
    }

    @Override
    protected void init() {
        listW = Math.min(360, width - 20);
        listX = (width - listW) / 2;
        listY = 50;
        rows = Math.max(3, (height - listY - 36) / ROW);
        search = new EditBox(font, listX, 26, listW, 18, Component.translatable("notquests.gui.search"));
        search.setHint(Component.translatable("notquests.gui.search_hint"));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            scroll = 0;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(new FlatButton(width / 2 - 104, height - 26, 100, 18, Component.translatable("notquests.gui.use_typed"), b -> {
            if (!query.isBlank()) {
                select(query.trim());
            }
        }));
        addRenderableWidget(new FlatButton(width / 2 + 4, height - 26, 100, 18, Component.translatable("notquests.gui.cancel"), b -> onClose()));
        if (kind.serverSide && !ClientQuestData.registryLists.containsKey(kind.name())) {
            PacketDistributor.sendToServer(new NQNetwork.RequestRegistryList(kind.name()));
        }
        loadAll();
    }

    private <T> void addRegistry(List<String> list, Registry<T> reg) {
        if (kind.ids) {
            for (ResourceLocation rl : reg.keySet()) {
                list.add(rl.toString());
            }
        }
        if (kind.tags) {
            reg.getTagNames().forEach(t -> list.add("#" + t.location()));
        }
    }

    private void loadAll() {
        if (all != null) {
            return;
        }
        List<String> list = new ArrayList<>();
        var mc = minecraft;
        switch (kind) {
            case ENTITY -> addRegistry(list, BuiltInRegistries.ENTITY_TYPE);
            case BLOCK -> addRegistry(list, BuiltInRegistries.BLOCK);
            case ITEM_TAG -> addRegistry(list, BuiltInRegistries.ITEM);
            case STAT -> addRegistry(list, BuiltInRegistries.CUSTOM_STAT);
            case SOUND -> addRegistry(list, BuiltInRegistries.SOUND_EVENT);
            case BIOME -> {
                if (mc.level != null) {
                    addRegistry(list, mc.level.registryAccess().registryOrThrow(Registries.BIOME));
                }
            }
            case DIMENSION -> {
                if (mc.getConnection() != null) {
                    for (var key : mc.getConnection().levels()) {
                        list.add(key.location().toString());
                    }
                }
            }
            default -> {
                List<String> fromServer = ClientQuestData.registryLists.get(kind.name());
                if (fromServer == null) {
                    return;
                }
                list.addAll(fromServer);
            }
        }
        Collections.sort(list);
        all = list;
        refilter();
    }

    private void refilter() {
        filtered = new ArrayList<>();
        if (all == null) {
            return;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (String s : all) {
            if (q.isEmpty() || s.contains(q)) {
                filtered.add(s);
            }
        }
    }

    @Override
    public void tick() {
        if (all == null) {
            loadAll();
        }
    }

    private void select(String id) {
        minecraft.setScreen(parent);
        callback.accept(id);
    }

    private int indexAt(double mx, double my) {
        if (mx < listX || mx >= listX + listW || my < listY || my >= listY + rows * ROW) {
            return -1;
        }
        int i = scroll + (int) ((my - listY) / ROW);
        return i < filtered.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = indexAt(mx, my);
        if (i >= 0 && button == 0) {
            select(filtered.get(i));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Math.max(0, Math.min(Math.max(0, filtered.size() - rows), scroll - (int) Math.signum(scrollY) * 3));
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
        GuiUtil.frame(g, listX - 3, listY - 3, listW + 6, rows * ROW + 6, Theme.PANEL, Theme.BORDER);
        if (all == null) {
            GuiUtil.centeredString(g, Component.translatable("notquests.gui.loading"), width / 2F, listY + 6, Theme.TEXT_DIM, false);
            return;
        }
        int hovered = indexAt(mouseX, mouseY);
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            if (i >= filtered.size()) {
                break;
            }
            int y = listY + r * ROW;
            if (i == hovered) {
                g.fill(listX, y, listX + listW, y + ROW, Theme.PANEL_HOVER);
            }
            String s = filtered.get(i);
            g.drawString(font, GuiUtil.trim(s, listW - 6), listX + 3, y + 2, s.startsWith("#") ? Theme.CLAIM : Theme.TEXT, false);
        }
        if (filtered.isEmpty()) {
            GuiUtil.centeredString(g, Component.translatable("notquests.gui.no_results"), width / 2F, listY + 6, Theme.TEXT_DIM, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
