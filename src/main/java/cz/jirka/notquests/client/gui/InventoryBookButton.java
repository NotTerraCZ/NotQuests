package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.client.ClientQuestData;
import cz.jirka.notquests.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Small quest book button in the corner of the inventory, like the FTB sidebar button. */
public class InventoryBookButton extends AbstractWidget {
    private final ItemStack icon = new ItemStack(ModItems.BOOK.get());

    public InventoryBookButton(int x, int y) {
        super(x, y, 20, 20, Component.translatable("notquests.quest_book"));
        setTooltip(Tooltip.create(Component.translatable("notquests.quest_book")));
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(getX(), getY(), getX() + width, getY() + height, isHovered() ? 0xC0303340 : 0x80000000);
        if (isHovered()) {
            g.renderOutline(getX(), getY(), width, height, Theme.ACCENT);
        }
        g.renderItem(icon, getX() + 2, getY() + 2);
        if (ClientQuestData.file != null && ClientQuestData.team.hasUnclaimedRewards(ClientQuestData.self(), ClientQuestData.file)) {
            g.pose().pushPose();
            g.pose().translate(0F, 0F, 200F);
            GuiUtil.icon(g, Icon.EXCLAMATION, getX() + 12, getY(), 9, Theme.CLAIM);
            g.pose().popPose();
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        Minecraft.getInstance().execute(ClientQuestData::openQuestScreen);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
