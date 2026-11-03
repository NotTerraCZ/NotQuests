package cz.jirka.notquests.client;

import cz.jirka.notquests.client.gui.GuiUtil;
import cz.jirka.notquests.client.gui.Icon;
import cz.jirka.notquests.client.gui.Theme;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.task.Task;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Pinned quests drawn on the in-game HUD. */
public final class PinnedHud {
    private record Line(Component text, Quest quest, int indent) {
    }

    private PinnedHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.getDebugOverlay().showDebugScreen() || !ClientConfig.SHOW_PINNED.get()) {
            return;
        }
        QuestFile file = ClientQuestData.file;
        if (file == null) {
            return;
        }
        TeamData d = ClientQuestData.team;
        LongArrayList pinned = d.getPinned(ClientQuestData.self());
        if (pinned.isEmpty()) {
            return;
        }
        Font font = mc.font;
        List<Line> lines = new ArrayList<>();
        int shown = 0;
        for (int i = 0; i < pinned.size() && shown < 6; i++) {
            Quest q = file.getQuest(pinned.getLong(i));
            if (q == null) {
                continue;
            }
            shown++;
            boolean done = d.isCompleted(q);
            net.minecraft.network.chat.MutableComponent title = q.getTitle().copy().withStyle(done ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            if (!done && q.tasks.size() > 1 && d.canStartTasks(q)) {
                // overall progress; single-task quests already show it on the task line
                title.append(Component.literal(" " + d.questProgressPercent(q) + "%").withStyle(ChatFormatting.GRAY));
            }
            lines.add(new Line(title, q, 0));
            if (done) {
                if (d.hasUnclaimedRewards(ClientQuestData.self(), q)) {
                    lines.add(new Line(Component.translatable("notquests.hud.rewards_ready").withStyle(ChatFormatting.GOLD), null, 1));
                } else {
                    lines.add(new Line(Component.translatable("notquests.gui.completed").withStyle(ChatFormatting.DARK_GREEN), null, 1));
                }
                continue;
            }
            if (!d.canStartTasks(q)) {
                lines.add(new Line(Component.translatable("notquests.gui.locked").withStyle(ChatFormatting.RED), null, 1));
                // what still has to be done before it unlocks (also quests / chapters elsewhere)
                int missing = 0;
                for (long depId : q.dependencies) {
                    cz.jirka.notquests.quest.QuestObjectBase dep = file.get(depId);
                    if (dep == null || d.isCompleted(dep)) {
                        continue;
                    }
                    if (missing++ >= 3) {
                        lines.add(new Line(Component.literal("...").withStyle(ChatFormatting.GRAY), null, 1));
                        break;
                    }
                    net.minecraft.network.chat.MutableComponent name = dep instanceof cz.jirka.notquests.quest.Chapter c
                            ? Component.translatable("notquests.gui.chapter_dep", c.getTitle())
                            : dep.getTitle().copy();
                    if (dep instanceof Quest dq && dq.chapter != q.chapter) {
                        name.append(Component.literal(" (").append(dq.chapter.getTitle()).append(")"));
                    }
                    lines.add(new Line(Component.literal("✖ ").withStyle(ChatFormatting.RED).append(name.withStyle(ChatFormatting.GRAY)), null, 1));
                }
                continue;
            }
            int tasks = 0;
            for (Task t : q.tasks) {
                if (d.isComplete(t)) {
                    continue;
                }
                if (tasks++ >= 4) {
                    lines.add(new Line(Component.literal("...").withStyle(ChatFormatting.GRAY), null, 1));
                    break;
                }
                Component text = t.showProgressNumbers()
                        ? Component.literal(t.formatProgress(d.getProgress(t)) + "/" + t.formatProgress(t.getMaxProgress()) + " ").withStyle(ChatFormatting.AQUA).append(t.getProgressTitle().copy().withStyle(ChatFormatting.GRAY))
                        : t.getTitle().copy().withStyle(ChatFormatting.GRAY);
                lines.add(new Line(text, null, 1));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        int w = 0;
        for (Line l : lines) {
            w = Math.max(w, font.width(l.text) + (l.indent > 0 ? 10 : 14));
        }
        w = Math.min(w, 220);
        int h = lines.size() * 10 + 6;
        float scale = ClientConfig.PINNED_SCALE.get().floatValue();
        int sw = (int) (g.guiWidth() / scale);
        int sh = (int) (g.guiHeight() / scale);
        int x;
        int y;
        switch (ClientConfig.PINNED_POSITION.get()) {
            case TOP_RIGHT -> {
                x = sw - w - 12;
                y = 4;
            }
            case MIDDLE_LEFT -> {
                x = 4;
                y = (sh - h) / 2;
            }
            case MIDDLE_RIGHT -> {
                x = sw - w - 12;
                y = (sh - h) / 2;
            }
            default -> {
                x = 4;
                y = 4;
            }
        }
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1F);
        g.fill(x, y, x + w + 8, y + h, 0x90000000);
        g.fill(x, y, x + 1, y + h, Theme.ACCENT);
        int ly = y + 4;
        for (Line l : lines) {
            int lx = x + 5;
            if (l.quest != null) {
                GuiUtil.icon(g, Icon.PIN, lx, ly, 8, Theme.ACCENT);
                lx += 11;
            } else {
                lx += 9;
            }
            g.drawString(font, GuiUtil.trim(l.text, w - (lx - x) + 4), lx, ly, 0xFFFFFFFF, true);
            ly += 10;
        }
        g.pose().popPose();
    }
}
