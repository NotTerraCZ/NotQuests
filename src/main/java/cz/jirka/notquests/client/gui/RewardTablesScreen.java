package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.client.ClientEditor;
import cz.jirka.notquests.client.ClientQuestData;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.RewardTable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Editor list of all reward tables. */
public class RewardTablesScreen extends Screen {
    private static final int ROW_H = 24;
    private final Screen parent;
    private int seenVersion;
    private double scroll;
    private int left;
    private int listW;
    private int listTop;
    private int listBottom;

    public RewardTablesScreen(Screen parent) {
        super(Component.translatable("notquests.gui.reward_tables"));
        this.parent = parent;
    }

    private List<RewardTable> tables() {
        QuestFile f = ClientQuestData.file;
        return f == null ? List.of() : f.rewardTables;
    }

    @Override
    protected void init() {
        seenVersion = ClientQuestData.version;
        listW = Math.min(360, width - 20);
        left = (width - listW) / 2;
        listTop = 30;
        listBottom = height - 36;
        List<RewardTable> tables = tables();
        double max = Math.max(0, tables.size() * ROW_H - (listBottom - listTop));
        scroll = Math.max(0, Math.min(scroll, max));
        for (int i = 0; i < tables.size(); i++) {
            RewardTable t = tables.get(i);
            int y = (int) (listTop + i * ROW_H - scroll);
            if (y < listTop - 1 || y + ROW_H > listBottom + 1) {
                continue;
            }
            addRenderableWidget(new FlatButton(left + listW - 64, y + 3, 30, 18, Component.empty(), b -> ClientEditor.openEditor(t, this)).icon(Icon.PENCIL, Theme.EDIT));
            addRenderableWidget(new FlatButton(left + listW - 32, y + 3, 30, 18, Component.empty(), b -> ClientEditor.delete(List.of(t), this)).icon(Icon.TRASH, Theme.DANGER));
        }
        QuestFile f = ClientQuestData.file;
        addRenderableWidget(new FlatButton(width / 2 - 104, height - 26, 100, 18, Component.translatable("notquests.gui.add_reward_table"), b -> {
            if (f != null) {
                ClientEditor.createRewardTable(f, this);
            }
        }).icon(Icon.PLUS, Theme.COMPLETE));
        addRenderableWidget(new FlatButton(width / 2 + 4, height - 26, 100, 18, Component.translatable("notquests.gui.back"), b -> onClose()));
    }

    @Override
    public void tick() {
        if (seenVersion != ClientQuestData.version) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= scrollY * ROW_H;
        rebuildWidgets();
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
        renderBackground(g, mouseX, mouseY, partialTick);
        GuiUtil.centeredString(g, title, width / 2F, 10, Theme.TEXT, false);
        List<RewardTable> tables = tables();
        if (tables.isEmpty()) {
            GuiUtil.centeredString(g, Component.translatable("notquests.gui.no_reward_tables"), width / 2F, listTop + 10, Theme.TEXT_DIM, false);
        }
        for (int i = 0; i < tables.size(); i++) {
            RewardTable t = tables.get(i);
            int y = (int) (listTop + i * ROW_H - scroll);
            if (y < listTop - 1 || y + ROW_H > listBottom + 1) {
                continue;
            }
            g.fill(left, y + 1, left + listW, y + ROW_H - 1, Theme.PANEL);
            IconRenderer.render(g, t, left + 4, y + 4, 16);
            g.drawString(font, GuiUtil.trim(t.getTitle(), listW - 120), left + 26, y + 4, Theme.TEXT, false);
            g.drawString(font, Component.translatable("notquests.gui.n_entries", t.entries.size()), left + 26, y + 13, Theme.TEXT_DARK, false);
        }
        for (var r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
