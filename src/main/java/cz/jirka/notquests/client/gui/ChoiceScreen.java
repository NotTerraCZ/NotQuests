package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.WeightedItem;
import cz.jirka.notquests.quest.reward.ChoiceReward;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Lets the player pick one item of a choice reward. */
public class ChoiceScreen extends Screen {
    private static final int CELL = 28;
    private final Screen parent;
    private final ChoiceReward reward;
    private int cols;
    private int rows;
    private int gridX;
    private int gridY;

    public ChoiceScreen(Screen parent, ChoiceReward reward) {
        super(Component.translatable("notquests.gui.choose_reward"));
        this.parent = parent;
        this.reward = reward;
    }

    private RewardTable table() {
        return reward.getTable();
    }

    @Override
    protected void init() {
        RewardTable t = table();
        int n = t == null ? 0 : t.entries.size();
        cols = Math.max(1, Math.min(8, n));
        rows = Math.max(1, (n + cols - 1) / cols);
        gridX = (width - cols * CELL) / 2;
        gridY = (height - rows * CELL) / 2;
        addRenderableWidget(new FlatButton(width / 2 - 50, gridY + rows * CELL + 18, 100, 18, Component.translatable("notquests.gui.cancel"), b -> onClose()));
    }

    /** Center of the cell of an entry (also used by the dev auto test). */
    public double[] cellCenter(int index) {
        return new double[]{gridX + (index % cols) * CELL + CELL / 2D, gridY + (index / cols) * CELL + CELL / 2D};
    }

    private int indexAt(double mx, double my) {
        RewardTable t = table();
        if (t == null || mx < gridX || my < gridY || mx >= gridX + cols * CELL) {
            return -1;
        }
        int c = (int) ((mx - gridX) / CELL);
        int r = (int) ((my - gridY) / CELL);
        int i = r * cols + c;
        return i < t.entries.size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = indexAt(mx, my);
        if (i >= 0 && button == 0) {
            PacketDistributor.sendToServer(new NQNetwork.ClaimReward(reward.id, i));
            minecraft.setScreen(parent);
            return true;
        }
        return super.mouseClicked(mx, my, button);
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
        int pw = Math.max(cols * CELL + 24, font.width(title) + 30);
        int px = (width - pw) / 2;
        int py = gridY - 30;
        int ph = rows * CELL + 70;
        GuiUtil.frame(g, px, py, pw, ph, Theme.PANEL, Theme.CLAIM);
        GuiUtil.centeredString(g, title, width / 2F, py + 10, Theme.CLAIM, false);
        for (var r : renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        RewardTable t = table();
        if (t == null) {
            GuiUtil.centeredString(g, Component.translatable("notquests.reward.no_table"), width / 2F, gridY + 8, Theme.DANGER, false);
            return;
        }
        int hovered = indexAt(mouseX, mouseY);
        for (int i = 0; i < t.entries.size(); i++) {
            WeightedItem e = t.entries.get(i);
            int x = gridX + (i % cols) * CELL;
            int y = gridY + (i / cols) * CELL;
            GuiUtil.frame(g, x + 1, y + 1, CELL - 2, CELL - 2, i == hovered ? Theme.PANEL_HOVER : Theme.PANEL_LIGHT, i == hovered ? Theme.CLAIM : Theme.BORDER);
            g.renderItem(e.stack, x + (CELL - 16) / 2, y + (CELL - 16) / 2);
            g.renderItemDecorations(font, e.stack, x + (CELL - 16) / 2, y + (CELL - 16) / 2);
        }
        if (hovered >= 0) {
            g.renderTooltip(font, t.entries.get(hovered).stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
