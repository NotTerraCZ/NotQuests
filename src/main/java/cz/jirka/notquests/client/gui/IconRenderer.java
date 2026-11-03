package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.task.CheckmarkTask;
import net.minecraft.client.gui.GuiGraphics;

/** Draws the icon of any quest object (items, plus a few special icons). */
public final class IconRenderer {
    private IconRenderer() {
    }

    public static void render(GuiGraphics g, QuestObjectBase o, float x, float y, float size) {
        if (o == null) {
            return;
        }
        if (!o.hasCustomIcon()) {
            if (o instanceof CheckmarkTask) {
                GuiUtil.icon(g, Icon.CHECK, x, y, size, 0xFFE9E9EC);
                return;
            }
            if (o instanceof Quest q && !q.tasks.isEmpty() && !q.tasks.get(0).hasCustomIcon() && q.tasks.get(0) instanceof CheckmarkTask) {
                GuiUtil.icon(g, Icon.CHECK, x, y, size, 0xFFE9E9EC);
                return;
            }
        }
        GuiUtil.item(g, o.getIcon(), x, y, size);
    }
}
