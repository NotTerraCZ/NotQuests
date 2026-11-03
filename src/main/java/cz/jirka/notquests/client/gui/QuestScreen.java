package cz.jirka.notquests.client.gui;

import cz.jirka.notquests.client.ClientEditor;
import cz.jirka.notquests.client.ClientQuestData;
import cz.jirka.notquests.client.NotQuestsClient;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Chapter;
import cz.jirka.notquests.quest.ChapterGroup;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.reward.ChoiceReward;
import cz.jirka.notquests.quest.reward.ItemReward;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.reward.RewardType;
import cz.jirka.notquests.quest.reward.RewardTypes;
import cz.jirka.notquests.client.compat.RecipeViewers;
import cz.jirka.notquests.quest.task.ItemTask;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.quest.task.TaskType;
import cz.jirka.notquests.quest.task.TaskTypes;
import net.minecraft.world.item.ItemStack;
import cz.jirka.notquests.util.TextUtil;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** The quest book: chapter list on the left, quest tree in the middle, quest details as an overlay. */
public class QuestScreen extends Screen {
    private static final int TOOLBAR_H = 24;
    private static final int CHAPTER_ROW = 18;
    private static final float UNIT = 24F;

    private static long lastChapterId;
    private static final Map<Long, double[]> CAMERA = new HashMap<>();
    private static final Set<Long> COLLAPSED = new HashSet<>();
    private static CompoundTag clipboard;

    private final long focusId;
    private QuestFile file;
    private int seenVersion = Integer.MIN_VALUE;
    private Chapter chapter;
    private double camX;
    private double camY;
    private double zoom = 1D;
    private long viewQuestId;
    private final LongLinkedOpenHashSet selected = new LongLinkedOpenHashSet();
    private ContextMenu menu;
    private final ViewPanel view = new ViewPanel();

    private int panelW = 130;
    private double chapterScroll;
    private final List<ChapterRow> chapterRows = new ArrayList<>();

    private int dragMode;
    private Quest pressedQuest;
    private double pressX;
    private double pressY;
    private double dragX;
    private double dragY;
    private double moveDX;
    private double moveDY;
    private boolean moved;

    private final List<ToolButton> tools = new ArrayList<>();
    private List<Component> tooltip;

    private record ChapterRow(@Nullable ChapterGroup group, @Nullable Chapter chapter) {
    }

    private record ToolButton(Icon icon, int color, Component tooltip, Runnable action, int x, int y, boolean enabled) {
        boolean isOver(double mx, double my) {
            return mx >= x && my >= y && mx < x + 18 && my < y + 18;
        }
    }

    public QuestScreen(long focusId) {
        super(Component.translatable("notquests.quest_book"));
        this.focusId = focusId;
    }

    // ================================================================== state

    private TeamData team() {
        return ClientQuestData.team;
    }

    private UUID self() {
        return ClientQuestData.self();
    }

    private boolean edit() {
        return ClientQuestData.canEdit();
    }

    private int gx() {
        return panelW;
    }

    private int gy() {
        return TOOLBAR_H;
    }

    private int gw() {
        return width - panelW;
    }

    private int gh() {
        return height - TOOLBAR_H;
    }

    private float unit() {
        return (float) (UNIT * zoom);
    }

    private float sx(double x) {
        return (float) (gx() + gw() / 2D + (x - camX) * unit());
    }

    private float sy(double y) {
        return (float) (gy() + gh() / 2D + (y - camY) * unit());
    }

    private double gridX(double mx) {
        return camX + (mx - gx() - gw() / 2D) / unit();
    }

    private double gridY(double my) {
        return camY + (my - gy() - gh() / 2D) / unit();
    }

    private double snap(double v) {
        double s = file == null ? 0.5D : file.gridScale;
        if (s <= 0D || hasShiftDown()) {
            return Math.round(v * 100D) / 100D;
        }
        return Math.round(v / s) * s;
    }

    private double qx(Quest q) {
        return q.x + (dragMode == 2 && moved && selected.contains(q.id) ? moveDX : 0D);
    }

    private double qy(Quest q) {
        return q.y + (dragMode == 2 && moved && selected.contains(q.id) ? moveDY : 0D);
    }

    private float qsize(Quest q) {
        return (float) (q.size * unit());
    }

    @Override
    protected void init() {
        refresh();
    }

    private void refresh() {
        file = ClientQuestData.file;
        seenVersion = ClientQuestData.version;
        if (file == null) {
            return;
        }
        if (chapter != null) {
            chapter = file.getChapter(chapter.id);
        }
        if (chapter == null && lastChapterId != 0L) {
            Chapter c = file.getChapter(lastChapterId);
            if (c != null) {
                selectChapter(c);
            }
        }
        if (chapter == null && focusId != 0L) {
            QuestObjectBase o = file.get(focusId);
            if (o instanceof Quest q) {
                selectChapter(q.chapter);
                viewQuestId = q.id;
            } else if (o instanceof Chapter c) {
                selectChapter(c);
            }
        }
        if (chapter == null || !edit() && !team().isVisible(chapter)) {
            Chapter first = null;
            for (Chapter c : file.chapters) {
                if (edit() || team().isVisible(c)) {
                    first = c;
                    break;
                }
            }
            if (first != null && first != chapter) {
                selectChapter(first);
            } else if (first == null) {
                chapter = null;
            }
        }
        if (viewQuestId != 0L && file.getQuest(viewQuestId) == null) {
            viewQuestId = 0L;
        }
        selected.removeIf((java.util.function.LongPredicate) id -> file.getQuest(id) == null);
        buildChapterRows();
        panelW = computePanelWidth();
    }

    private int computePanelWidth() {
        int w = 100;
        for (ChapterRow row : chapterRows) {
            QuestObjectBase o = row.chapter != null ? row.chapter : row.group;
            if (o != null) {
                w = Math.max(w, font.width(o.getTitle()) + (row.group != null && row.chapter != null ? 10 : 0) + 64);
            }
        }
        return Mth.clamp(w, 100, Math.max(100, Math.min(170, width / 3)));
    }

    private void buildChapterRows() {
        chapterRows.clear();
        boolean edit = edit();
        for (Chapter c : file.getChaptersInGroup(0L)) {
            if (edit || team().isVisible(c)) {
                chapterRows.add(new ChapterRow(null, c));
            }
        }
        for (ChapterGroup g : file.chapterGroups) {
            List<Chapter> list = new ArrayList<>();
            for (Chapter c : file.getChaptersInGroup(g.id)) {
                if (edit || team().isVisible(c)) {
                    list.add(c);
                }
            }
            if (list.isEmpty() && !edit) {
                continue;
            }
            chapterRows.add(new ChapterRow(g, null));
            if (!COLLAPSED.contains(g.id)) {
                for (Chapter c : list) {
                    chapterRows.add(new ChapterRow(g, c));
                }
            }
        }
    }

    private void selectChapter(Chapter c) {
        if (chapter != null) {
            CAMERA.put(chapter.id, new double[]{camX, camY, zoom});
        }
        chapter = c;
        lastChapterId = c.id;
        double[] cam = CAMERA.get(c.id);
        if (cam != null) {
            camX = cam[0];
            camY = cam[1];
            zoom = cam[2];
        } else {
            centerView();
        }
    }

    private List<Quest> visibleQuests() {
        List<Quest> list = new ArrayList<>();
        if (chapter == null) {
            return list;
        }
        boolean edit = edit();
        for (Quest q : chapter.quests) {
            if (edit || team().isVisible(q)) {
                list.add(q);
            }
        }
        return list;
    }

    private void centerView() {
        List<Quest> quests = visibleQuests();
        if (quests.isEmpty() || width == 0) {
            camX = 0D;
            camY = 0D;
            zoom = 1D;
            return;
        }
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (Quest q : quests) {
            minX = Math.min(minX, q.x - q.size / 2D);
            minY = Math.min(minY, q.y - q.size / 2D);
            maxX = Math.max(maxX, q.x + q.size / 2D);
            maxY = Math.max(maxY, q.y + q.size / 2D);
        }
        camX = (minX + maxX) / 2D;
        camY = (minY + maxY) / 2D;
        double fitX = (gw() - 40) / (UNIT * Math.max(1D, maxX - minX));
        double fitY = (gh() - 40) / (UNIT * Math.max(1D, maxY - minY));
        zoom = Mth.clamp(Math.min(fitX, fitY), 0.5D, 1.6D);
    }

    @Nullable
    private Quest viewQuest() {
        return viewQuestId == 0L || file == null ? null : file.getQuest(viewQuestId);
    }

    private void openView(Quest q) {
        viewQuestId = q.id;
        view.descScroll = 0D;
    }

    /** Tasks and rewards of a locked quest are shown as "?" - then the recipe viewer must not reveal them either. */
    private boolean detailsHidden(Quest q) {
        return (q.hideDetailsUntilStartable || !file.showLockedQuestDetails) && !team().canStartTasks(q) && !edit();
    }

    @Override
    public void tick() {
        if (ClientQuestData.file == null) {
            onClose();
            return;
        }
        if (seenVersion != ClientQuestData.version || file != ClientQuestData.file) {
            refresh();
        }
    }

    @Override
    public void removed() {
        if (chapter != null) {
            CAMERA.put(chapter.id, new double[]{camX, camY, zoom});
        }
    }

    @Override
    public boolean isPauseScreen() {
        return file != null && file.pauseGame;
    }

    // ================================================================== rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, Theme.BACKGROUND);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (file == null) {
            renderBackground(g, mouseX, mouseY, partialTick);
            return;
        }
        tooltip = null;
        renderBackground(g, mouseX, mouseY, partialTick);
        boolean overlay = menu != null || viewQuest() != null;
        Quest hovered = overlay || mouseX < gx() || mouseY < gy() ? null : questAt(mouseX, mouseY);

        renderGraph(g, mouseX, mouseY, hovered);
        renderChapterPanel(g, mouseX, mouseY, overlay);
        renderToolbar(g, mouseX, mouseY, overlay);

        if (hovered != null && dragMode != 2) {
            tooltip = questTooltip(hovered);
        }

        Quest vq = viewQuest();
        if (vq != null) {
            GuiUtil.clearDepth(g);
            g.fill(gx(), gy(), width, height, 0x80000000);
            view.render(g, vq, mouseX, mouseY);
        }
        if (menu != null) {
            GuiUtil.clearDepth(g);
            menu.render(g, mouseX, mouseY);
        } else if (tooltip != null && !tooltip.isEmpty()) {
            GuiUtil.clearDepth(g);
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private void renderGraph(GuiGraphics g, int mx, int my, @Nullable Quest hovered) {
        int gx = gx();
        int gy = gy();
        g.fill(gx, gy, width, height, Theme.GRAPH);
        if (chapter == null) {
            GuiUtil.centeredString(g, Component.translatable(file.chapters.isEmpty() ? "notquests.gui.no_chapters" : "notquests.gui.no_visible_chapters"), gx + gw() / 2F, gy + gh() / 2F - 4, Theme.TEXT_DIM, false);
            if (edit()) {
                GuiUtil.centeredString(g, Component.translatable("notquests.gui.add_chapter_hint"), gx + gw() / 2F, gy + gh() / 2F + 8, Theme.TEXT_DARK, false);
            }
            return;
        }
        g.enableScissor(gx, gy, width, height);
        boolean edit = edit();
        float u = unit();

        // grid
        if (u >= 8F) {
            double x0 = Math.floor(gridX(gx));
            double x1 = Math.ceil(gridX(width));
            double y0 = Math.floor(gridY(gy));
            double y1 = Math.ceil(gridY(height));
            if (edit) {
                for (double x = x0; x <= x1; x++) {
                    float px = sx(x);
                    g.fill((int) px, gy, (int) px + 1, height, x == 0 ? 0x30FFFFFF : 0x12FFFFFF);
                }
                for (double y = y0; y <= y1; y++) {
                    float py = sy(y);
                    g.fill(gx, (int) py, width, (int) py + 1, y == 0 ? 0x30FFFFFF : 0x12FFFFFF);
                }
            } else if ((x1 - x0) * (y1 - y0) < 4000) {
                for (double x = x0; x <= x1; x++) {
                    for (double y = y0; y <= y1; y++) {
                        int px = (int) sx(x);
                        int py = (int) sy(y);
                        g.fill(px, py, px + 1, py + 1, 0x1EFFFFFF);
                    }
                }
            }
        }

        TeamData d = team();
        List<Quest> quests = visibleQuests();

        // dependency lines
        float thick = Math.max(1.5F, u * 0.09F);
        for (Quest q : quests) {
            if (!edit && (q.hideDependencyLines || chapter.hideDependencyLines)) {
                continue;
            }
            for (int i = 0; i < q.dependencies.size(); i++) {
                Quest dep = file.getQuest(q.dependencies.getLong(i));
                if (dep == null || dep.chapter != chapter || !edit && !d.isVisible(dep)) {
                    continue;
                }
                float x1 = sx(qx(dep));
                float y1 = sy(qy(dep));
                float x2 = sx(qx(q));
                float y2 = sy(qy(q));
                float dx = x2 - x1;
                float dy = y2 - y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                float r1 = qsize(dep) * 0.42F;
                float r2 = qsize(q) * 0.42F;
                if (len <= r1 + r2) {
                    continue;
                }
                float ux = dx / len;
                float uy = dy / len;
                float sx1 = x1 + ux * r1;
                float sy1 = y1 + uy * r1;
                float sx2 = x2 - ux * r2;
                float sy2 = y2 - uy * r2;
                boolean highlight = edit && (selected.contains(q.id) || selected.contains(dep.id)) || hovered == q || hovered == dep;
                if (d.isCompleted(q)) {
                    GuiUtil.line(g, sx1, sy1, sx2, sy2, thick, highlight ? Theme.COMPLETE : Theme.LINE_DONE);
                } else if (d.isCompleted(dep)) {
                    dashedLine(g, sx1, sy1, sx2, sy2, thick, highlight ? 0xA0FFFFFF : 0x60FFFFFF, highlight ? Theme.SELECTION : 0xF0FFFFFF);
                } else {
                    GuiUtil.line(g, sx1, sy1, sx2, sy2, thick, highlight ? Theme.SELECTION : Theme.LINE_LOCKED);
                }
            }
        }

        // quests
        for (Quest q : quests) {
            drawQuest(g, q, d, edit, q == hovered);
        }

        if (quests.isEmpty()) {
            GuiUtil.centeredString(g, Component.translatable("notquests.gui.no_quests"), gx + gw() / 2F, gy + gh() / 2F - 4, Theme.TEXT_DIM, false);
            if (edit) {
                GuiUtil.centeredString(g, Component.translatable("notquests.gui.add_quest_hint"), gx + gw() / 2F, gy + gh() / 2F + 8, Theme.TEXT_DARK, false);
            }
        }

        if (dragMode == 3) {
            int bx1 = (int) Math.min(pressX, dragX);
            int by1 = (int) Math.min(pressY, dragY);
            int bx2 = (int) Math.max(pressX, dragX);
            int by2 = (int) Math.max(pressY, dragY);
            g.pose().pushPose();
            g.pose().translate(0F, 0F, 300F);
            g.fill(bx1, by1, bx2, by2, 0x305AA9FF);
            g.renderOutline(bx1, by1, bx2 - bx1, by2 - by1, Theme.SELECTION);
            g.pose().popPose();
        }
        if (edit) {
            // quests selected in another chapter can be linked from here: tell the editor how
            int elsewhere = 0;
            for (Quest s : selectedQuests()) {
                if (s.chapter != chapter) {
                    elsewhere++;
                }
            }
            if (elsewhere > 0) {
                Component hint = Component.translatable("notquests.gui.selected_elsewhere", elsewhere);
                int tw = font.width(hint);
                int hx = gx + (gw() - tw) / 2;
                int hy = gy + gh() - 18;
                g.pose().pushPose();
                g.pose().translate(0F, 0F, 300F);
                g.fill(hx - 6, hy - 4, hx + tw + 6, hy + 12, 0xE0101218);
                g.renderOutline(hx - 6, hy - 4, tw + 12, 16, Theme.SELECTION);
                g.drawString(font, hint, hx, hy, Theme.ACCENT, false);
                g.pose().popPose();
            }
        }
        g.disableScissor();
    }

    private void dashedLine(GuiGraphics g, float x1, float y1, float x2, float y2, float thick, int base, int dash) {
        GuiUtil.line(g, x1, y1, x2, y2, thick, base);
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        float seg = Math.max(3F, unit() * 0.3F);
        float period = seg * 2.2F;
        float offset = (float) ((System.currentTimeMillis() / 1000D * unit() * 1.1D) % period);
        float ux = dx / len;
        float uy = dy / len;
        for (float s = offset - period; s < len; s += period) {
            float a = Math.max(0F, s);
            float b = Math.min(len, s + seg);
            if (b > a) {
                GuiUtil.line(g, x1 + ux * a, y1 + uy * a, x1 + ux * b, y1 + uy * b, thick, dash);
            }
        }
    }

    private void drawQuest(GuiGraphics g, Quest q, TeamData d, boolean edit, boolean hovered) {
        float cx = sx(qx(q));
        float cy = sy(qy(q));
        float size = qsize(q) * (hovered ? 1.07F : 1F);
        if (cx + size < gx() || cy + size < gy() || cx - size > width || cy - size > height) {
            return;
        }
        boolean completed = d.isCompleted(q);
        boolean unclaimed = completed && d.hasUnclaimedRewards(self(), q);
        boolean locked = !d.canStartTasks(q);
        boolean started = d.isStarted(q);
        float alpha = edit && !d.isVisible(q) ? 0.45F : 1F;

        int fill;
        int outline;
        if (completed) {
            fill = unclaimed ? Theme.CLAIM_DARK : Theme.COMPLETE_DARK;
            outline = unclaimed ? GuiUtil.lerp(Theme.CLAIM, 0xFFFFFFFF, GuiUtil.pulse(1400L) * 0.5F) : Theme.COMPLETE;
        } else if (locked) {
            fill = Theme.LOCKED_FILL;
            outline = Theme.LOCKED;
        } else if (started) {
            fill = Theme.NODE_FILL;
            outline = Theme.PROGRESS;
        } else {
            fill = Theme.NODE_FILL;
            outline = Theme.NODE_OUTLINE;
        }
        if (hovered) {
            outline = GuiUtil.lerp(outline, 0xFFFFFFFF, 0.35F);
        }

        if (edit && selected.contains(q.id)) {
            GuiUtil.shape(g, q.getShape(), true, cx, cy, size * 1.22F, GuiUtil.withAlpha(Theme.SELECTION, 0.95F));
        }
        GuiUtil.shape(g, q.getShape(), false, cx, cy, size, GuiUtil.withAlpha(fill, alpha));
        GuiUtil.shape(g, q.getShape(), true, cx, cy, size, GuiUtil.withAlpha(outline, alpha));

        float iconSize = size * 0.6F;
        boolean hideDetails = (q.hideDetailsUntilStartable || !file.showLockedQuestDetails) && locked && !edit;
        if (hideDetails) {
            g.pose().pushPose();
            float scale = Math.max(0.5F, size / 24F);
            g.pose().translate(cx, cy, 0F);
            g.pose().scale(scale, scale, 1F);
            g.drawString(font, "?", -font.width("?") / 2, -4, Theme.TEXT_DIM, false);
            g.pose().popPose();
        } else {
            IconRenderer.render(g, q, cx - iconSize / 2F, cy - iconSize / 2F, iconSize);
        }

        g.pose().pushPose();
        g.pose().translate(0F, 0F, 260F);
        if (locked && !completed) {
            GuiUtil.shape(g, q.getShape(), false, cx, cy, size * 0.86F, 0x88000000);
        }
        if (alpha < 1F) {
            GuiUtil.shape(g, q.getShape(), false, cx, cy, size * 0.86F, 0x66000000);
        }
        float badge = Math.max(8F, size * 0.38F);
        if (unclaimed) {
            GuiUtil.icon(g, Icon.EXCLAMATION, cx + size * 0.22F, cy - size * 0.5F, badge, Theme.CLAIM);
        } else if (completed) {
            GuiUtil.icon(g, Icon.CHECK, cx + size * 0.2F, cy - size * 0.48F, badge, Theme.COMPLETE);
        }
        if (d.isPinned(self(), q.id)) {
            GuiUtil.icon(g, Icon.PIN, cx - size * 0.5F, cy - size * 0.5F, badge * 0.85F, Theme.ACCENT);
        }
        if (hasExternalRequirement(q)) {
            // needs a quest from another chapter (or a whole chapter) - no line can show that
            boolean met = d.areDependenciesComplete(q);
            GuiUtil.icon(g, Icon.LINK, cx - size * 0.52F, cy + size * 0.12F, badge * 0.85F, met ? Theme.ACCENT : Theme.CLAIM);
        }
        if (started && !completed && !q.tasks.isEmpty() && size >= 14F) {
            int pct = d.questProgressPercent(q);
            float bw = size * 0.7F;
            float by = cy + size * 0.5F + 1.5F;
            GuiUtil.rect(g, cx - bw / 2F, by, cx + bw / 2F, by + 2F, 0xC0000000);
            GuiUtil.rect(g, cx - bw / 2F, by, cx - bw / 2F + bw * pct / 100F, by + 2F, Theme.PROGRESS);
        }
        g.pose().popPose();
    }

    @Nullable
    private Quest questAt(double mx, double my) {
        if (chapter == null || mx < gx() || my < gy()) {
            return null;
        }
        List<Quest> quests = visibleQuests();
        for (int i = quests.size() - 1; i >= 0; i--) {
            Quest q = quests.get(i);
            float half = qsize(q) / 2F;
            float cx = sx(qx(q));
            float cy = sy(qy(q));
            if (mx >= cx - half && mx <= cx + half && my >= cy - half && my <= cy + half) {
                return q;
            }
        }
        return null;
    }

    /** True if the quest needs a quest from another chapter or a whole chapter. */
    private boolean hasExternalRequirement(Quest q) {
        for (long depId : q.dependencies) {
            QuestObjectBase dep = file.get(depId);
            if (dep instanceof Chapter || dep instanceof Quest dq && dq.chapter != q.chapter) {
                return true;
            }
        }
        return false;
    }

    /** " ✔ Iron Age (Main Story)" / " ✖ Chapter: The Beginning" */
    private Component requirementLine(Quest q, QuestObjectBase dep, TeamData d) {
        boolean done = d.isCompleted(dep);
        MutableComponent line = Component.literal(done ? " ✔ " : " ✖ ").withStyle(done ? ChatFormatting.GREEN : ChatFormatting.RED);
        if (dep instanceof Chapter c) {
            line.append(Component.translatable("notquests.gui.chapter_dep", c.getTitle()).withStyle(ChatFormatting.GRAY));
        } else {
            line.append(dep.getTitle().copy().withStyle(ChatFormatting.GRAY));
            if (dep instanceof Quest dq && dq.chapter != q.chapter) {
                line.append(Component.literal(" (").append(dq.chapter.getTitle()).append(")").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return line;
    }

    /** Selected quests of the chapter that is open (the selection can also hold quests of other chapters for linking). */
    private List<Quest> selectedHere() {
        List<Quest> list = new ArrayList<>();
        for (Quest q : selectedQuests()) {
            if (q.chapter == chapter) {
                list.add(q);
            }
        }
        return list;
    }

    private void toggleDependency(Quest q, long depId) {
        ClientEditor.action(q.dependencies.contains(depId) ? NQNetwork.Actions.REMOVE_DEPENDENCY : NQNetwork.Actions.ADD_DEPENDENCY, q.id, depId);
    }

    private static Component checked(boolean on, Component label) {
        return Component.literal(on ? "✔ " : "").append(label);
    }

    /** The item EMI / JEI should show for a task or reward: the task's item (the one shown right now for tags) or the reward item. */
    private static ItemStack recipeStack(@Nullable QuestObjectBase o) {
        if (o instanceof ItemTask t) {
            return t.getIcon();
        }
        if (o instanceof ItemReward r) {
            return r.item;
        }
        return ItemStack.EMPTY;
    }

    /** Quests of another chapter to require, split into pages so long chapters still fit on the screen. */
    private List<ContextMenu.Entry> questRequirementEntries(Quest q, Chapter c) {
        List<ContextMenu.Entry> all = new ArrayList<>();
        for (Quest other : c.quests) {
            all.add(ContextMenu.Entry.of(checked(q.dependencies.contains(other.id), other.getTitle()), () -> toggleDependency(q, other.id)));
        }
        int page = 14;
        if (all.size() <= page) {
            return all;
        }
        List<ContextMenu.Entry> pages = new ArrayList<>();
        for (int start = 0; start < all.size(); start += page) {
            int end = Math.min(all.size(), start + page);
            pages.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.quests_range", start + 1, end), new ArrayList<>(all.subList(start, end))));
        }
        return pages;
    }

    private List<Component> questTooltip(Quest q) {
        List<Component> list = new ArrayList<>();
        TeamData d = team();
        boolean edit = edit();
        boolean hideDetails = (q.hideDetailsUntilStartable || !file.showLockedQuestDetails) && !d.canStartTasks(q) && !edit;
        list.add(hideDetails ? Component.literal("???") : q.getTitle());
        if (!q.subtitle.isEmpty() && !hideDetails) {
            list.add(TextUtil.parse(q.subtitle).withStyle(ChatFormatting.GRAY));
        }
        if (d.isCompleted(q)) {
            list.add(Component.translatable("notquests.gui.completed").withStyle(ChatFormatting.GREEN));
            if (d.hasUnclaimedRewards(self(), q)) {
                list.add(Component.translatable("notquests.gui.rewards_available").withStyle(ChatFormatting.GOLD));
            }
        } else if (!d.areDependenciesComplete(q)) {
            list.add(Component.translatable("notquests.gui.locked").withStyle(ChatFormatting.RED));
        } else if (d.isStarted(q)) {
            list.add(Component.translatable("notquests.gui.progress_percent", d.questProgressPercent(q)).withStyle(ChatFormatting.AQUA));
        }
        if (q.optional) {
            list.add(Component.translatable("notquests.gui.optional").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (hasExternalRequirement(q) && (edit || !d.areDependenciesComplete(q))) {
            list.add(Component.translatable("notquests.gui.requires_elsewhere").withStyle(ChatFormatting.GRAY));
            for (long depId : q.dependencies) {
                QuestObjectBase dep = file.get(depId);
                if (dep instanceof Chapter || dep instanceof Quest dq && dq.chapter != q.chapter) {
                    list.add(requirementLine(q, dep, d));
                }
            }
        }
        if (edit) {
            list.add(Component.literal(q.getCodeString()).withStyle(ChatFormatting.DARK_GRAY));
            if (!d.isVisible(q)) {
                list.add(Component.translatable("notquests.gui.hidden_for_players").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return list;
    }

    private void renderChapterPanel(GuiGraphics g, int mx, int my, boolean overlay) {
        g.fill(0, 0, panelW, height, Theme.PANEL);
        g.fill(panelW - 1, 0, panelW, height, Theme.BORDER);
        // header
        GuiUtil.item(g, file.getIcon(), 5, 4, 16);
        g.drawString(font, GuiUtil.trim(file.getTitle(), panelW - 30), 25, 8, Theme.TEXT, false);
        g.fill(0, TOOLBAR_H - 1, panelW, TOOLBAR_H, Theme.BORDER);

        int footer = footerHeight();
        int top = TOOLBAR_H + 2;
        int bottom = height - footer;
        clampChapterScroll();
        g.enableScissor(0, top, panelW - 1, bottom);
        TeamData d = team();
        boolean edit = edit();
        for (int i = 0; i < chapterRows.size(); i++) {
            int y = (int) (top + i * CHAPTER_ROW - chapterScroll);
            if (y + CHAPTER_ROW < top || y > bottom) {
                continue;
            }
            ChapterRow row = chapterRows.get(i);
            boolean hover = !overlay && mx >= 0 && mx < panelW - 1 && my >= y && my < y + CHAPTER_ROW && my >= top && my < bottom;
            if (row.chapter == null) {
                ChapterGroup grp = row.group;
                if (hover) {
                    g.fill(0, y, panelW - 1, y + CHAPTER_ROW, Theme.PANEL_LIGHT);
                }
                GuiUtil.icon(g, COLLAPSED.contains(grp.id) ? Icon.COLLAPSED : Icon.EXPANDED, 4, y + 5, 8, Theme.TEXT_DIM);
                g.drawString(font, GuiUtil.trim(grp.getTitle(), panelW - 22), 15, y + 5, Theme.TEXT_DIM, false);
                continue;
            }
            Chapter c = row.chapter;
            boolean sel = c == chapter;
            if (sel) {
                g.fill(0, y, panelW - 1, y + CHAPTER_ROW, Theme.PANEL_HOVER);
                g.fill(0, y, 2, y + CHAPTER_ROW, Theme.ACCENT);
            } else if (hover) {
                g.fill(0, y, panelW - 1, y + CHAPTER_ROW, Theme.PANEL_LIGHT);
            }
            int x = row.group != null ? 12 : 5;
            IconRenderer.render(g, c, x, y + 2, 14);
            int right = panelW - 6;
            int textMax = right - (x + 18) - 22;
            boolean done = d.isCompleted(c);
            boolean unclaimed = d.hasUnclaimedRewards(self(), c);
            int color = !edit && !d.isVisible(c) ? Theme.TEXT_DARK : sel ? Theme.TEXT : Theme.TEXT_DIM;
            if (edit && c.alwaysInvisible) {
                color = Theme.TEXT_DARK;
            }
            g.drawString(font, GuiUtil.trim(c.getTitle(), textMax), x + 18, y + 5, color, false);
            g.pose().pushPose();
            g.pose().translate(0F, 0F, 200F);
            if (unclaimed) {
                GuiUtil.icon(g, Icon.EXCLAMATION, right - 10, y + 4, 10, Theme.CLAIM);
            } else if (done) {
                GuiUtil.icon(g, Icon.CHECK, right - 10, y + 4, 10, Theme.COMPLETE);
            } else {
                String pct = d.chapterProgressPercent(c) + "%";
                g.drawString(font, pct, right - font.width(pct), y + 5, Theme.TEXT_DARK, false);
            }
            g.pose().popPose();
        }
        g.disableScissor();

        // footer
        int fy = height - footer;
        g.fill(0, fy, panelW - 1, fy + 1, Theme.BORDER);
        if (edit) {
            int bw = (panelW - 12) / 2;
            drawPanelButton(g, 4, fy + 4, bw, Component.translatable("notquests.gui.add_chapter"), mx, my, overlay);
            drawPanelButton(g, 8 + bw, fy + 4, bw, Component.translatable("notquests.gui.add_group"), mx, my, overlay);
        }
        Component teamLine = ClientQuestData.teamPersonal
                ? Component.translatable("notquests.gui.solo")
                : Component.translatable("notquests.gui.team", ClientQuestData.teamName, ClientQuestData.members.size());
        g.drawString(font, GuiUtil.trim(teamLine, panelW - 10), 5, height - 12, Theme.TEXT_DARK, false);
        if (!overlay && mx >= 0 && mx < panelW && my >= height - 14 && !ClientQuestData.teamPersonal) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(ClientQuestData.teamName).withStyle(ChatFormatting.AQUA));
            for (var m : ClientQuestData.members) {
                lines.add(Component.literal((m.online() ? "● " : "○ ") + m.name()).withStyle(m.online() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
            tooltip = lines;
        }
    }

    private int footerHeight() {
        return edit() ? 38 : 16;
    }

    private void drawPanelButton(GuiGraphics g, int x, int y, int w, Component text, int mx, int my, boolean overlay) {
        boolean hover = !overlay && mx >= x && my >= y && mx < x + w && my < y + 16;
        GuiUtil.frame(g, x, y, w, 16, hover ? Theme.PANEL_HOVER : Theme.PANEL_LIGHT, Theme.BORDER);
        GuiUtil.centeredString(g, Component.literal("+ ").append(text), x + w / 2F, y + 4, Theme.COMPLETE, false);
    }

    private void clampChapterScroll() {
        int visible = height - footerHeight() - TOOLBAR_H - 2;
        double max = Math.max(0, chapterRows.size() * CHAPTER_ROW - visible);
        chapterScroll = Mth.clamp(chapterScroll, 0D, max);
    }

    private void renderToolbar(GuiGraphics g, int mx, int my, boolean overlay) {
        boolean edit = edit();
        g.fill(panelW, 0, width, TOOLBAR_H, Theme.PANEL);
        g.fill(panelW, TOOLBAR_H - 1, width, TOOLBAR_H, edit ? Theme.EDIT : Theme.BORDER);
        buildTools();
        int left = panelW + 8;
        int right = width - 6;
        for (ToolButton t : tools) {
            right = Math.min(right, t.x - 4);
        }
        if (chapter != null) {
            IconRenderer.render(g, chapter, left, 4, 16);
            int tx = left + 21;
            Component title = chapter.getTitle();
            int titleW = Math.min(font.width(title), right - tx - 4);
            g.drawString(font, GuiUtil.trim(title, right - tx - 4), tx, 8, Theme.TEXT, false);
            if (!chapter.subtitle.isEmpty()) {
                int sx = tx + titleW + 10;
                if (right - sx > 30) {
                    g.drawString(font, GuiUtil.trim(TextUtil.parse(chapter.subtitle.get(0)), right - sx), sx, 8, Theme.TEXT_DARK, false);
                }
            }
        }
        if (edit) {
            Component label = Component.translatable("notquests.gui.editing");
            int w = font.width(label);
            int x = right - w - 2;
            if (x > left + 120) {
                g.drawString(font, label, x, 8, Theme.EDIT, false);
            }
        }
        for (ToolButton t : tools) {
            boolean hover = !overlay && t.isOver(mx, my);
            if (hover && t.enabled) {
                g.fill(t.x, t.y, t.x + 18, t.y + 18, Theme.PANEL_HOVER);
            }
            GuiUtil.icon(g, t.icon, t.x + 3, t.y + 3, 12, t.enabled ? t.color : Theme.TEXT_DARK);
            if (hover) {
                tooltip = List.of(t.tooltip);
            }
        }
    }

    private void buildTools() {
        tools.clear();
        boolean edit = edit();
        int x = width - 22;
        int y = 3;
        tools.add(new ToolButton(Icon.CROSS, Theme.TEXT_DIM, Component.translatable("notquests.gui.close"), this::onClose, x, y, true));
        x -= 20;
        if (ClientQuestData.hasPermission()) {
            boolean on = ClientQuestData.editMode;
            tools.add(new ToolButton(Icon.PENCIL, on ? Theme.EDIT : Theme.TEXT_DIM,
                    Component.translatable(on ? "notquests.gui.edit_mode_on" : "notquests.gui.edit_mode_off"),
                    () -> ClientEditor.setEditMode(!ClientQuestData.editMode), x, y, true));
            x -= 20;
        }
        if (edit) {
            tools.add(new ToolButton(Icon.GEAR, Theme.TEXT_DIM, Component.translatable("notquests.gui.file_settings"),
                    () -> ClientEditor.openEditor(file, this), x, y, true));
            x -= 20;
            tools.add(new ToolButton(Icon.LIST, Theme.TEXT_DIM, Component.translatable("notquests.gui.reward_tables"),
                    () -> minecraft.setScreen(new RewardTablesScreen(this)), x, y, true));
            x -= 20;
        }
        boolean claimable = team().hasUnclaimedRewards(self(), file);
        tools.add(new ToolButton(Icon.GIFT, claimable ? GuiUtil.lerp(Theme.CLAIM, 0xFFFFFFFF, GuiUtil.pulse(1400L) * 0.5F) : Theme.TEXT_DARK,
                Component.translatable("notquests.gui.claim_all"), () -> {
            if (team().hasUnclaimedRewards(self(), file)) {
                PacketDistributor.sendToServer(new NQNetwork.ClaimAll(0L));
            }
        }, x, y, claimable));
        x -= 20;
        tools.add(new ToolButton(Icon.CENTER, Theme.TEXT_DIM, Component.translatable("notquests.gui.center_view"), this::centerView, x, y, chapter != null));
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (file == null) {
            return false;
        }
        if (menu != null) {
            ContextMenu.Result r = menu.mouseClicked(mx, my, button);
            if (r != ContextMenu.Result.KEEP_OPEN) {
                menu = null;
            }
            return true;
        }
        Quest vq = viewQuest();
        if (vq != null) {
            if (view.isOver(mx, my)) {
                view.mouseClicked(vq, mx, my, button);
            } else {
                viewQuestId = 0L;
            }
            return true;
        }
        if (my < TOOLBAR_H && mx >= panelW) {
            for (ToolButton t : tools) {
                if (t.isOver(mx, my) && t.enabled && button == 0) {
                    playClick();
                    t.action.run();
                    return true;
                }
            }
            return true;
        }
        if (mx < panelW) {
            chapterPanelClicked(mx, my, button);
            return true;
        }
        graphClicked(mx, my, button);
        return true;
    }

    private void playClick() {
        minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
    }

    private void chapterPanelClicked(double mx, double my, int button) {
        boolean edit = edit();
        int footer = footerHeight();
        int fy = height - footer;
        if (my >= fy) {
            if (edit && my >= fy + 4 && my < fy + 20) {
                int bw = (panelW - 12) / 2;
                if (mx >= 4 && mx < 4 + bw) {
                    ClientEditor.createChapter(file, 0L, this);
                } else if (mx >= 8 + bw && mx < 8 + bw * 2) {
                    ClientEditor.createGroup(file, this);
                }
            }
            return;
        }
        if (my < TOOLBAR_H) {
            if (button == 1 && edit) {
                openFileMenu(mx, my);
            }
            return;
        }
        int top = TOOLBAR_H + 2;
        int index = (int) ((my - top + chapterScroll) / CHAPTER_ROW);
        ChapterRow row = index >= 0 && index < chapterRows.size() && my >= top ? chapterRows.get(index) : null;
        if (row == null) {
            if (button == 1 && edit) {
                List<ContextMenu.Entry> e = new ArrayList<>();
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.add_chapter"), () -> ClientEditor.createChapter(file, 0L, this)).icon(Icon.PLUS));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.add_group"), () -> ClientEditor.createGroup(file, this)).icon(Icon.PLUS));
                menu = new ContextMenu(e, (int) mx, (int) my, width, height);
            }
            return;
        }
        if (row.chapter == null) {
            ChapterGroup grp = row.group;
            if (button == 0) {
                if (!COLLAPSED.remove(grp.id)) {
                    COLLAPSED.add(grp.id);
                }
                playClick();
                buildChapterRows();
            } else if (button == 1 && edit) {
                List<ContextMenu.Entry> e = new ArrayList<>();
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.edit"), () -> ClientEditor.openEditor(grp, this)).icon(Icon.PENCIL));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.add_chapter"), () -> ClientEditor.createChapter(file, grp.id, this)).icon(Icon.PLUS));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.move_up"), () -> ClientEditor.action(NQNetwork.Actions.MOVE_UP, grp.id, 0L)).icon(Icon.ARROW_UP));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.move_down"), () -> ClientEditor.action(NQNetwork.Actions.MOVE_DOWN, grp.id, 0L)).icon(Icon.ARROW_DOWN));
                e.add(ContextMenu.Entry.separator());
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.delete"), () -> ClientEditor.delete(List.of(grp), this)).danger().icon(Icon.TRASH));
                menu = new ContextMenu(e, (int) mx, (int) my, width, height);
            }
            return;
        }
        Chapter c = row.chapter;
        if (button == 0) {
            if (c != chapter) {
                playClick();
                selectChapter(c);
                viewQuestId = 0L;
            }
        } else if (button == 1 && edit) {
            List<ContextMenu.Entry> e = new ArrayList<>();
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.edit"), () -> ClientEditor.openEditor(c, this)).icon(Icon.PENCIL));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.move_up"), () -> ClientEditor.action(NQNetwork.Actions.MOVE_UP, c.id, 0L)).icon(Icon.ARROW_UP));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.move_down"), () -> ClientEditor.action(NQNetwork.Actions.MOVE_DOWN, c.id, 0L)).icon(Icon.ARROW_DOWN));
            e.add(ContextMenu.Entry.separator());
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.complete_instantly"), () -> ClientEditor.action(NQNetwork.Actions.COMPLETE, c.id, 0L)).color(Theme.COMPLETE));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.reset_progress"), () -> ClientEditor.action(NQNetwork.Actions.RESET, c.id, 0L)).color(Theme.CLAIM));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.copy_id"), () -> minecraft.keyboardHandler.setClipboard(c.getCodeString())));
            e.add(ContextMenu.Entry.separator());
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.delete"), () -> ClientEditor.delete(List.of(c), this)).danger().icon(Icon.TRASH));
            menu = new ContextMenu(e, (int) mx, (int) my, width, height);
        }
    }

    private void openFileMenu(double mx, double my) {
        List<ContextMenu.Entry> e = new ArrayList<>();
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.file_settings"), () -> ClientEditor.openEditor(file, this)).icon(Icon.GEAR));
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.reward_tables"), () -> minecraft.setScreen(new RewardTablesScreen(this))).icon(Icon.LIST));
        e.add(ContextMenu.Entry.separator());
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.reset_all_progress"), () -> ClientEditor.action(NQNetwork.Actions.RESET, QuestFile.FILE_ID, 0L)).color(Theme.CLAIM));
        menu = new ContextMenu(e, (int) mx, (int) my, width, height);
    }

    private void graphClicked(double mx, double my, int button) {
        if (chapter == null) {
            if (button == 1 && edit()) {
                openFileMenu(mx, my);
            }
            return;
        }
        Quest q = questAt(mx, my);
        boolean edit = edit();
        if (button == 0) {
            pressX = mx;
            pressY = my;
            dragX = mx;
            dragY = my;
            moved = false;
            pressedQuest = q;
            if (q != null && edit) {
                dragMode = 2;
            } else if (q == null && edit && hasShiftDown()) {
                dragMode = 3;
            } else {
                dragMode = 1;
            }
        } else if (button == 1) {
            if (edit) {
                if (q != null) {
                    openQuestMenu(q, mx, my);
                } else {
                    openGraphMenu(mx, my);
                }
            } else if (q != null) {
                boolean pinned = team().isPinned(self(), q.id);
                List<ContextMenu.Entry> e = new ArrayList<>();
                e.add(ContextMenu.Entry.of(Component.translatable(pinned ? "notquests.gui.unpin" : "notquests.gui.pin"),
                        () -> PacketDistributor.sendToServer(new NQNetwork.PinQuest(q.id, !pinned))).icon(Icon.PIN));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.open"), () -> openView(q)));
                menu = new ContextMenu(e, (int) mx, (int) my, width, height);
            }
        } else if (button == 2) {
            pressX = mx;
            pressY = my;
            moved = false;
            pressedQuest = null;
            dragMode = 1;
        }
    }

    private List<ContextMenu.Entry> taskTypeEntries(Consumer<TaskType> action) {
        List<ContextMenu.Entry> list = new ArrayList<>();
        for (TaskType t : TaskTypes.all()) {
            list.add(ContextMenu.Entry.of(t.displayName(), () -> action.accept(t)));
        }
        return list;
    }

    private List<ContextMenu.Entry> rewardTypeEntries(Consumer<RewardType> action) {
        List<ContextMenu.Entry> list = new ArrayList<>();
        for (RewardType t : RewardTypes.all()) {
            list.add(ContextMenu.Entry.of(t.displayName(), () -> action.accept(t)));
        }
        return list;
    }

    private List<Quest> selectedQuests() {
        List<Quest> list = new ArrayList<>();
        for (long id : selected) {
            Quest q = file.getQuest(id);
            if (q != null) {
                list.add(q);
            }
        }
        return list;
    }

    private void openQuestMenu(Quest q, double mx, double my) {
        List<ContextMenu.Entry> e = new ArrayList<>();
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.edit"), () -> ClientEditor.openEditor(q, this)).icon(Icon.PENCIL));
        boolean pinned = team().isPinned(self(), q.id);
        e.add(ContextMenu.Entry.of(Component.translatable(pinned ? "notquests.gui.unpin" : "notquests.gui.pin"),
                () -> PacketDistributor.sendToServer(new NQNetwork.PinQuest(q.id, !pinned))).icon(Icon.PIN));
        e.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.add_task"), taskTypeEntries(t -> ClientEditor.addTask(q, t, this))).icon(Icon.PLUS));
        e.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.add_reward"), rewardTypeEntries(t -> ClientEditor.addReward(q, t, this))).icon(Icon.GIFT));
        List<Quest> others = new ArrayList<>(selectedQuests());
        others.remove(q);
        if (!others.isEmpty()) {
            e.add(ContextMenu.Entry.separator());
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.add_deps", others.size()), () -> {
                for (Quest s : others) {
                    ClientEditor.action(NQNetwork.Actions.ADD_DEPENDENCY, q.id, s.id);
                }
            }).color(Theme.ACCENT));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.add_dependents", others.size()), () -> {
                for (Quest s : others) {
                    ClientEditor.action(NQNetwork.Actions.ADD_DEPENDENCY, s.id, q.id);
                }
            }).color(Theme.ACCENT));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.remove_links"), () -> {
                for (Quest s : others) {
                    ClientEditor.action(NQNetwork.Actions.REMOVE_DEPENDENCY, q.id, s.id);
                    ClientEditor.action(NQNetwork.Actions.REMOVE_DEPENDENCY, s.id, q.id);
                }
            }));
        }
        // requirements from other chapters: a whole chapter (so it can't be skipped) or single quests of it
        List<ContextMenu.Entry> chapterReqs = new ArrayList<>();
        List<ContextMenu.Entry> questReqs = new ArrayList<>();
        for (Chapter c : file.chapters) {
            if (c == q.chapter) {
                continue;
            }
            chapterReqs.add(ContextMenu.Entry.of(checked(q.dependencies.contains(c.id), c.getTitle()), () -> toggleDependency(q, c.id)));
            if (!c.quests.isEmpty()) {
                questReqs.add(ContextMenu.Entry.sub(c.getTitle(), questRequirementEntries(q, c)));
            }
        }
        if (!chapterReqs.isEmpty()) {
            if (others.isEmpty()) {
                e.add(ContextMenu.Entry.separator());
            }
            e.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.require_chapter"), chapterReqs).icon(Icon.LINK));
            if (!questReqs.isEmpty()) {
                e.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.require_quest_from"), questReqs).icon(Icon.LINK));
            }
        }
        if (!q.dependencies.isEmpty()) {
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.clear_deps"), () -> ClientEditor.action(NQNetwork.Actions.CLEAR_DEPENDENCIES, q.id, 0L)));
        }
        e.add(ContextMenu.Entry.separator());
        List<ContextMenu.Entry> chapters = new ArrayList<>();
        for (Chapter c : file.chapters) {
            if (c != q.chapter) {
                chapters.add(ContextMenu.Entry.of(c.getTitle(), () -> ClientEditor.action(NQNetwork.Actions.MOVE_QUEST_TO_CHAPTER, q.id, c.id)));
            }
        }
        if (!chapters.isEmpty()) {
            e.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.move_to_chapter"), chapters));
        }
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.duplicate"), () -> ClientEditor.pasteQuest(ClientEditor.copyQuest(q), chapter, q.x + 1D, q.y + 1D)));
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.copy"), () -> clipboard = ClientEditor.copyQuest(q)));
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.copy_id"), () -> minecraft.keyboardHandler.setClipboard(q.getCodeString())));
        e.add(ContextMenu.Entry.separator());
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.complete_instantly"), () -> ClientEditor.action(NQNetwork.Actions.COMPLETE, q.id, 0L)).color(Theme.COMPLETE));
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.reset_progress"), () -> ClientEditor.action(NQNetwork.Actions.RESET, q.id, 0L)).color(Theme.CLAIM));
        e.add(ContextMenu.Entry.separator());
        List<Quest> here = selectedHere();
        List<Quest> targets = here.contains(q) && here.size() > 1 ? here : List.of(q);
        e.add(ContextMenu.Entry.of(targets.size() > 1 ? Component.translatable("notquests.gui.delete_n", targets.size()) : Component.translatable("notquests.gui.delete"),
                () -> ClientEditor.delete(targets, this)).danger().icon(Icon.TRASH));
        menu = new ContextMenu(e, (int) mx, (int) my, width, height);
    }

    private void openGraphMenu(double mx, double my) {
        double x = snap(gridX(mx));
        double y = snap(gridY(my));
        List<ContextMenu.Entry> e = new ArrayList<>();
        e.add(ContextMenu.Entry.sub(Component.translatable("notquests.gui.add_quest"), taskTypeEntries(t -> ClientEditor.createQuest(chapter, x, y, t, this))).icon(Icon.PLUS));
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.add_empty_quest"), () -> ClientEditor.createQuest(chapter, x, y, null, this)));
        if (clipboard != null) {
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.paste"), () -> ClientEditor.pasteQuest(clipboard, chapter, x, y)));
        }
        e.add(ContextMenu.Entry.separator());
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.edit_chapter"), () -> ClientEditor.openEditor(chapter, this)).icon(Icon.PENCIL));
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.select_all"), () -> {
            for (Quest q : chapter.quests) {
                selected.add(q.id);
            }
        }));
        if (!selected.isEmpty()) {
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.deselect"), selected::clear));
        }
        e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.center_view"), this::centerView).icon(Icon.CENTER));
        menu = new ContextMenu(e, (int) mx, (int) my, width, height);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (file == null || menu != null || viewQuest() != null) {
            return false;
        }
        dragX = mx;
        dragY = my;
        if (dragMode == 1) {
            if (!moved && Math.abs(mx - pressX) + Math.abs(my - pressY) > 3D) {
                moved = true;
            }
            camX -= dx / unit();
            camY -= dy / unit();
            return true;
        } else if (dragMode == 2 && pressedQuest != null) {
            if (!moved && Math.abs(mx - pressX) + Math.abs(my - pressY) > 3D) {
                moved = true;
                if (!selected.contains(pressedQuest.id)) {
                    selected.clear();
                    selected.add(pressedQuest.id);
                }
            }
            if (moved) {
                double nx = snap(pressedQuest.x + (mx - pressX) / unit());
                double ny = snap(pressedQuest.y + (my - pressY) / unit());
                moveDX = nx - pressedQuest.x;
                moveDY = ny - pressedQuest.y;
            }
            return true;
        } else if (dragMode == 3) {
            moved = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (file == null) {
            return false;
        }
        int mode = dragMode;
        Quest pressed = pressedQuest;
        boolean wasMoved = moved;
        dragMode = 0;
        pressedQuest = null;
        if (mode == 2 && pressed != null) {
            if (wasMoved) {
                List<Quest> list = selectedHere();
                long[] ids = new long[list.size()];
                double[] xs = new double[list.size()];
                double[] ys = new double[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    Quest q = list.get(i);
                    ids[i] = q.id;
                    xs[i] = q.x + moveDX;
                    ys[i] = q.y + moveDY;
                    q.x = xs[i];
                    q.y = ys[i];
                }
                if (moveDX != 0D || moveDY != 0D) {
                    ClientEditor.moveQuests(ids, xs, ys);
                }
            } else if (hasControlDown()) {
                if (!selected.remove(pressed.id)) {
                    selected.add(pressed.id);
                }
            } else {
                selected.clear();
                selected.add(pressed.id);
                openView(pressed);
            }
        } else if (mode == 1 && !wasMoved && button == 0) {
            if (pressed != null) {
                openView(pressed);
            } else if (edit() && !hasControlDown()) {
                selected.clear();
            }
        } else if (mode == 3) {
            double x1 = Math.min(pressX, mx);
            double y1 = Math.min(pressY, my);
            double x2 = Math.max(pressX, mx);
            double y2 = Math.max(pressY, my);
            for (Quest q : visibleQuests()) {
                float cx = sx(q.x);
                float cy = sy(q.y);
                if (cx >= x1 && cx <= x2 && cy >= y1 && cy <= y2) {
                    selected.add(q.id);
                }
            }
        }
        moveDX = 0D;
        moveDY = 0D;
        moved = false;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (file == null || menu != null) {
            return true;
        }
        Quest vq = viewQuest();
        if (vq != null) {
            if (view.isOver(mx, my)) {
                view.descScroll = Mth.clamp(view.descScroll - scrollY * 10D, 0D, view.descMaxScroll);
            }
            return true;
        }
        if (mx < panelW) {
            chapterScroll -= scrollY * CHAPTER_ROW;
            clampChapterScroll();
            return true;
        }
        if (my < TOOLBAR_H) {
            return true;
        }
        double gxm = gridX(mx);
        double gym = gridY(my);
        zoom = Mth.clamp(zoom * Math.pow(1.15D, scrollY), 0.25D, 4D);
        camX = gxm - (mx - gx() - gw() / 2D) / unit();
        camY = gym - (my - gy() - gh() / 2D) / unit();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (menu != null) {
                menu = null;
            } else if (viewQuestId != 0L) {
                viewQuestId = 0L;
            } else {
                onClose();
            }
            return true;
        }
        if (NotQuestsClient.OPEN_KEY.matches(keyCode, scanCode) && menu == null) {
            onClose();
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_U) && menu == null && viewQuest() != null && !detailsHidden(viewQuest()) && RecipeViewers.available()) {
            // like in EMI / JEI: R = recipes, U = uses of the hovered task or reward item
            double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
            double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
            if (RecipeViewers.show(recipeStack(view.slotObjectAt(mx, my)), keyCode == GLFW.GLFW_KEY_U)) {
                return true;
            }
        }
        if (file == null) {
            return false;
        }
        double pan = 40D / unit();
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> camX -= pan;
            case GLFW.GLFW_KEY_RIGHT -> camX += pan;
            case GLFW.GLFW_KEY_UP -> camY -= pan;
            case GLFW.GLFW_KEY_DOWN -> camY += pan;
            default -> {
            }
        }
        if (edit() && chapter != null && menu == null && viewQuestId == 0L) {
            if (keyCode == GLFW.GLFW_KEY_DELETE && !selectedHere().isEmpty()) {
                ClientEditor.delete(selectedHere(), this);
                return true;
            }
            if (hasControlDown()) {
                if (keyCode == GLFW.GLFW_KEY_A) {
                    for (Quest q : chapter.quests) {
                        selected.add(q.id);
                    }
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_C && !selectedHere().isEmpty()) {
                    clipboard = ClientEditor.copyQuest(selectedHere().get(0));
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_V && clipboard != null) {
                    double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
                    double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
                    ClientEditor.pasteQuest(clipboard, chapter, snap(gridX(mx)), snap(gridY(my)));
                    return true;
                } else if (keyCode == GLFW.GLFW_KEY_D && !selectedHere().isEmpty()) {
                    for (Quest q : selectedHere()) {
                        ClientEditor.pasteQuest(ClientEditor.copyQuest(q), chapter, q.x + 1D, q.y + 1D);
                    }
                    return true;
                }
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ================================================================== test hooks (used by the dev auto test)

    /** Clicks a context menu entry by its label; returns false if there was no such entry. */
    public boolean testMenu(String label) {
        if (menu == null) {
            return false;
        }
        ContextMenu.Result r = menu.clickByLabel(label);
        if (r == ContextMenu.Result.CLOSE) {
            menu = null;
        }
        return r != ContextMenu.Result.OUTSIDE;
    }

    /** Adds a quest to the selection, like Ctrl+click. */
    public void testSelect(Quest q) {
        selected.add(q.id);
    }

    @Nullable
    public Quest testViewQuest() {
        return viewQuest();
    }

    public float[] testScreenPos(Quest q) {
        if (q.chapter != chapter) {
            selectChapter(q.chapter);
        }
        return new float[]{sx(q.x), sy(q.y)};
    }

    @Nullable
    public double[] testSlot(QuestObjectBase o) {
        for (ViewPanel.Slot s : view.slots) {
            if (s.object() == o) {
                return new double[]{s.x() + 9D, s.y() + 9D};
            }
        }
        return null;
    }

    // ================================================================== quest details

    /** Details of one quest: tasks on the left, rewards on the right, description below. */
    private class ViewPanel {
        private int x;
        private int y;
        private int w;
        private int h;
        double descScroll;
        double descMaxScroll;
        private final List<Slot> slots = new ArrayList<>();
        private final List<ToolButton> buttons = new ArrayList<>();

        private record Slot(@Nullable QuestObjectBase object, boolean isTask, int x, int y) {
            boolean isOver(double mx, double my) {
                return mx >= x && my >= y && mx < x + 18 && my < y + 18;
            }
        }

        boolean isOver(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        private List<FormattedCharSequence> descriptionLines(Quest q, int width) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (String s : q.description) {
                if (s.isEmpty()) {
                    lines.add(FormattedCharSequence.EMPTY);
                } else {
                    lines.addAll(GuiUtil.wrap(TextUtil.parse(s), width));
                }
            }
            return lines;
        }

        void render(GuiGraphics g, Quest q, int mx, int my) {
            TeamData d = team();
            boolean edit = edit();
            boolean hide = (q.hideDetailsUntilStartable || !file.showLockedQuestDetails) && !d.canStartTasks(q) && !edit;
            w = Math.min(gw() - 24, 340);
            int half = (w - 18) / 2;
            int perRow = Math.max(1, (half + 2) / 20);
            int taskCount = q.tasks.size() + (edit ? 1 : 0);
            int rewardCount = 0;
            for (Reward r : q.rewards) {
                if (edit || !r.isInvisible()) {
                    rewardCount++;
                }
            }
            if (edit) {
                rewardCount++;
            }
            int slotRows = Math.max(1, Math.max((taskCount + perRow - 1) / perRow, (rewardCount + perRow - 1) / perRow));
            int headerH = q.subtitle.isEmpty() ? 24 : 32;
            int sectionsH = 12 + slotRows * 21 + 4;
            List<FormattedCharSequence> desc = hide ? List.of() : descriptionLines(q, w - 20);
            List<Component> footer = footerLines(q, d);
            int footerH = footer.isEmpty() ? 0 : footer.size() * 10 + 6;
            int maxH = gh() - 16;
            int descFull = desc.isEmpty() ? 0 : desc.size() * 10 + 8;
            int fixed = headerH + sectionsH + footerH + 6;
            int descH = Math.min(descFull, Math.max(0, maxH - fixed));
            h = fixed + descH;
            x = gx() + (gw() - w) / 2;
            y = gy() + Math.max(8, (gh() - h) / 2);
            descMaxScroll = Math.max(0, descFull - descH);
            descScroll = Mth.clamp(descScroll, 0D, descMaxScroll);

            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x70000000);
            GuiUtil.frame(g, x, y, w, h, 0xFA15161C, d.isCompleted(q) ? Theme.COMPLETE : Theme.BORDER_LIGHT);

            // header
            if (!hide) {
                IconRenderer.render(g, q, x + 6, y + 4, 16);
            }
            buttons.clear();
            int bx = x + w - 20;
            buttons.add(new ToolButton(Icon.CROSS, Theme.TEXT_DIM, Component.translatable("notquests.gui.close"), () -> viewQuestId = 0L, bx, y + 3, true));
            bx -= 18;
            boolean pinned = d.isPinned(self(), q.id);
            buttons.add(new ToolButton(Icon.PIN, pinned ? Theme.ACCENT : Theme.TEXT_DARK,
                    Component.translatable(pinned ? "notquests.gui.unpin" : "notquests.gui.pin"),
                    () -> PacketDistributor.sendToServer(new NQNetwork.PinQuest(q.id, !pinned)), bx, y + 3, true));
            bx -= 18;
            if (edit) {
                buttons.add(new ToolButton(Icon.PENCIL, Theme.EDIT, Component.translatable("notquests.gui.edit"), () -> ClientEditor.openEditor(q, QuestScreen.this), bx, y + 3, true));
                bx -= 18;
            }
            int titleMax = bx - (x + 27);
            Component title = hide ? Component.literal("???") : q.getTitle();
            g.drawString(font, GuiUtil.trim(title, titleMax), x + 27, y + 8, d.isCompleted(q) ? Theme.COMPLETE : Theme.TEXT, false);
            if (!q.subtitle.isEmpty() && !hide) {
                g.drawString(font, GuiUtil.trim(TextUtil.parse(q.subtitle), w - 34), x + 27, y + 19, Theme.TEXT_DIM, false);
            }
            for (ToolButton b : buttons) {
                boolean hover = b.isOver(mx, my);
                if (hover) {
                    g.fill(b.x, b.y, b.x + 16, b.y + 16, Theme.PANEL_HOVER);
                    tooltip = List.of(b.tooltip);
                }
                GuiUtil.icon(g, b.icon, b.x + 2, b.y + 2, 12, b.color);
            }
            int sy = y + headerH;
            g.fill(x + 4, sy - 2, x + w - 4, sy - 1, Theme.BORDER);

            // sections
            int leftX = x + 8;
            int rightX = x + 10 + half;
            g.drawString(font, Component.translatable("notquests.gui.tasks"), leftX, sy + 2, Theme.TEXT_DIM, false);
            Component rewardsLabel = Component.translatable("notquests.gui.rewards");
            g.drawString(font, rewardsLabel, rightX, sy + 2, Theme.TEXT_DIM, false);
            g.fill(x + 9 + half, sy + 2, x + 10 + half, sy + sectionsH - 4, Theme.BORDER);
            slots.clear();
            int i = 0;
            for (Task t : q.tasks) {
                slots.add(new Slot(t, true, leftX + (i % perRow) * 20, sy + 13 + (i / perRow) * 21));
                i++;
            }
            if (edit) {
                slots.add(new Slot(null, true, leftX + (i % perRow) * 20, sy + 13 + (i / perRow) * 21));
            }
            i = 0;
            for (Reward r : q.rewards) {
                if (!edit && r.isInvisible()) {
                    continue;
                }
                slots.add(new Slot(r, false, rightX + (i % perRow) * 20, sy + 13 + (i / perRow) * 21));
                i++;
            }
            if (edit) {
                slots.add(new Slot(null, false, rightX + (i % perRow) * 20, sy + 13 + (i / perRow) * 21));
            }
            for (Slot s : slots) {
                renderSlot(g, q, d, s, mx, my, edit, hide);
            }

            // description
            int dy = sy + sectionsH;
            if (descH > 0) {
                g.fill(x + 4, dy - 2, x + w - 4, dy - 1, Theme.BORDER);
                g.enableScissor(x + 2, dy + 2, x + w - 2, dy + descH - 2);
                int ly = (int) (dy + 5 - descScroll);
                for (FormattedCharSequence line : desc) {
                    if (ly > dy - 10 && ly < dy + descH) {
                        g.drawString(font, line, x + 10, ly, Theme.TEXT, false);
                    }
                    ly += 10;
                }
                g.disableScissor();
                if (descMaxScroll > 0) {
                    int barH = Math.max(8, (int) (descH * descH / (double) descFull));
                    int barY = (int) (dy + (descH - barH) * (descScroll / descMaxScroll));
                    g.fill(x + w - 4, barY, x + w - 2, barY + barH, Theme.BORDER_LIGHT);
                }
            }
            int fy = dy + descH + 2;
            if (!footer.isEmpty()) {
                g.fill(x + 4, fy - 2, x + w - 4, fy - 1, Theme.BORDER);
                int ly = fy + 3;
                for (Component c : footer) {
                    g.drawString(font, GuiUtil.trim(c, w - 16), x + 8, ly, Theme.TEXT_DIM, false);
                    ly += 10;
                }
            }
        }

        private List<Component> footerLines(Quest q, TeamData d) {
            List<Component> list = new ArrayList<>();
            if (!d.areDependenciesComplete(q) && !q.dependencies.isEmpty()) {
                list.add(Component.translatable(q.dependencyRequirement.one ? "notquests.gui.requires_one" : "notquests.gui.requires").withStyle(ChatFormatting.RED));
                int shown = 0;
                for (int i = 0; i < q.dependencies.size() && shown < 4; i++) {
                    QuestObjectBase dep = file.get(q.dependencies.getLong(i));
                    if (dep == null) {
                        continue;
                    }
                    list.add(requirementLine(q, dep, d));
                    shown++;
                }
            } else if (d.isCompleted(q) && d.hasUnclaimedRewards(self(), q)) {
                list.add(Component.translatable("notquests.gui.click_reward_to_claim").withStyle(ChatFormatting.GOLD));
            }
            return list;
        }

        private void renderSlot(GuiGraphics g, Quest q, TeamData d, Slot s, int mx, int my, boolean edit, boolean hide) {
            boolean hover = s.isOver(mx, my);
            if (s.object == null) {
                GuiUtil.frame(g, s.x, s.y, 18, 18, hover ? Theme.PANEL_HOVER : Theme.PANEL, Theme.BORDER);
                GuiUtil.icon(g, Icon.PLUS, s.x + 4, s.y + 4, 10, Theme.COMPLETE);
                if (hover) {
                    tooltip = List.of(Component.translatable(s.isTask ? "notquests.gui.add_task" : "notquests.gui.add_reward"));
                }
                return;
            }
            int border = Theme.BORDER;
            boolean complete = false;
            boolean claimed = false;
            List<Component> tip = new ArrayList<>();
            if (s.object instanceof Task t) {
                complete = d.isComplete(t);
                boolean canSubmit = t.isSubmittable() && !complete && d.canStartTasks(q);
                if (complete) {
                    border = Theme.COMPLETE;
                } else if (canSubmit) {
                    border = GuiUtil.lerp(Theme.CLAIM, Theme.BORDER, GuiUtil.pulse(1400L));
                }
                tip.add(hide ? Component.literal("???") : t.getTitle());
                long progress = d.getProgress(t);
                if (complete) {
                    tip.add(Component.translatable("notquests.gui.completed").withStyle(ChatFormatting.GREEN));
                } else if (t.showProgressNumbers()) {
                    tip.add(Component.translatable("notquests.gui.progress", t.formatProgress(progress), t.formatProgress(t.getMaxProgress())).withStyle(ChatFormatting.AQUA));
                }
                if (t.optional) {
                    tip.add(Component.translatable("notquests.gui.optional_task").withStyle(ChatFormatting.DARK_GRAY));
                }
                if (!hide) {
                    t.addTooltip(tip, d, edit);
                }
                if (!d.canStartTasks(q) && !complete) {
                    tip.add(Component.translatable("notquests.gui.quest_locked").withStyle(ChatFormatting.RED));
                }
            } else if (s.object instanceof Reward r) {
                claimed = d.isRewardClaimed(self(), r);
                boolean canClaim = d.isCompleted(q) && !claimed;
                if (claimed) {
                    border = Theme.COMPLETE;
                } else if (canClaim) {
                    border = GuiUtil.lerp(Theme.CLAIM, 0xFFFFFFFF, GuiUtil.pulse(1200L) * 0.6F);
                }
                tip.add(r.getTitle());
                if (claimed) {
                    tip.add(Component.translatable("notquests.gui.claimed").withStyle(ChatFormatting.GREEN));
                } else if (canClaim) {
                    tip.add(Component.translatable(r instanceof ChoiceReward ? "notquests.gui.click_to_choose" : "notquests.gui.click_to_claim").withStyle(ChatFormatting.GOLD));
                } else {
                    tip.add(Component.translatable("notquests.gui.complete_to_claim").withStyle(ChatFormatting.DARK_GRAY));
                }
                r.addTooltip(tip, edit);
                if (edit && r.isInvisible()) {
                    tip.add(Component.translatable("notquests.gui.invisible_reward").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            if (!hide && RecipeViewers.available() && !recipeStack(s.object).isEmpty()) {
                // EMI / JEI: a click opens the recipes when it has nothing else to do (submit / claim / edit)
                boolean clickOpens = !edit && !(s.object instanceof Task t2 && t2.isSubmittable() && !complete && d.canStartTasks(q))
                        && !(s.object instanceof Reward && d.isCompleted(q) && !claimed);
                tip.add(Component.translatable(clickOpens ? "notquests.gui.recipe_hint_click" : "notquests.gui.recipe_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (edit) {
                tip.add(Component.translatable("notquests.gui.right_click_edit").withStyle(ChatFormatting.DARK_GRAY));
            }
            GuiUtil.frame(g, s.x, s.y, 18, 18, hover ? Theme.PANEL_HOVER : Theme.PANEL_LIGHT, border);
            if (!(hide && s.object instanceof Task)) {
                IconRenderer.render(g, s.object, s.x + 1, s.y + 1, 16);
            }
            g.pose().pushPose();
            g.pose().translate(0F, 0F, 220F);
            if (s.object instanceof Task t && t.showProgressNumbers() && !hide) {
                String count = TextUtil.formatLong(t.getMaxProgress());
                g.drawString(font, count, s.x + 18 - font.width(count), s.y + 10, 0xFFFFFFFF, true);
                if (!complete) {
                    float pct = Math.min(1F, d.getProgress(t) / (float) Math.max(1L, t.getMaxProgress()));
                    g.fill(s.x + 1, s.y + 16, s.x + 17, s.y + 17, 0xFF000000);
                    g.fill(s.x + 1, s.y + 16, s.x + 1 + (int) (16 * pct), s.y + 17, Theme.PROGRESS);
                }
            } else if (s.object instanceof ItemReward ir && ir.displayCount() > 1) {
                String count = TextUtil.formatLong(ir.displayCount());
                g.drawString(font, count, s.x + 18 - font.width(count), s.y + 10, 0xFFFFFFFF, true);
            }
            if (complete || claimed) {
                g.fill(s.x + 1, s.y + 1, s.x + 17, s.y + 17, 0x55000000);
                GuiUtil.icon(g, Icon.CHECK, s.x + 9, s.y + 1, 8, Theme.COMPLETE);
            }
            g.pose().popPose();
            if (hover) {
                tooltip = tip;
            }
        }

        void mouseClicked(Quest q, double mx, double my, int button) {
            for (ToolButton b : buttons) {
                if (b.isOver(mx, my) && button == 0) {
                    playClick();
                    b.action.run();
                    return;
                }
            }
            TeamData d = team();
            boolean edit = edit();
            for (Slot s : slots) {
                if (!s.isOver(mx, my)) {
                    continue;
                }
                if (s.object == null) {
                    if (edit) {
                        menu = new ContextMenu(s.isTask ? taskTypeEntries(t -> ClientEditor.addTask(q, t, QuestScreen.this))
                                : rewardTypeEntries(t -> ClientEditor.addReward(q, t, QuestScreen.this)), (int) mx, (int) my, width, height);
                    }
                    return;
                }
                if (button == 1 && edit) {
                    openSlotMenu(q, s.object, mx, my);
                    return;
                }
                if (button != 0) {
                    return;
                }
                if (s.object instanceof Task t) {
                    if (t.isSubmittable() && !d.isComplete(t) && d.canStartTasks(q)) {
                        playClick();
                        PacketDistributor.sendToServer(new NQNetwork.SubmitTask(t.id));
                    } else if (edit) {
                        ClientEditor.openEditor(t, QuestScreen.this);
                    } else if (!detailsHidden(q)) {
                        RecipeViewers.show(recipeStack(t), false);
                    }
                } else if (s.object instanceof Reward r) {
                    if (d.isCompleted(q) && !d.isRewardClaimed(self(), r)) {
                        playClick();
                        if (r instanceof ChoiceReward cr) {
                            minecraft.setScreen(new ChoiceScreen(QuestScreen.this, cr));
                        } else {
                            PacketDistributor.sendToServer(new NQNetwork.ClaimReward(r.id, -1));
                        }
                    } else if (edit) {
                        ClientEditor.openEditor(r, QuestScreen.this);
                    } else if (!detailsHidden(q)) {
                        RecipeViewers.show(recipeStack(r), false);
                    }
                }
                return;
            }
        }

        private void openSlotMenu(Quest q, QuestObjectBase o, double mx, double my) {
            List<ContextMenu.Entry> e = new ArrayList<>();
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.edit"), () -> ClientEditor.openEditor(o, QuestScreen.this)).icon(Icon.PENCIL));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.move_left"), () -> ClientEditor.action(NQNetwork.Actions.MOVE_UP, o.id, 0L)).icon(Icon.ARROW_UP));
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.move_right"), () -> ClientEditor.action(NQNetwork.Actions.MOVE_DOWN, o.id, 0L)).icon(Icon.ARROW_DOWN));
            if (o instanceof Task) {
                e.add(ContextMenu.Entry.separator());
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.complete_instantly"), () -> ClientEditor.action(NQNetwork.Actions.COMPLETE, o.id, 0L)).color(Theme.COMPLETE));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.reset_progress"), () -> ClientEditor.action(NQNetwork.Actions.RESET, o.id, 0L)).color(Theme.CLAIM));
            }
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.copy_id"), () -> minecraft.keyboardHandler.setClipboard(o.getCodeString())));
            ItemStack stack = recipeStack(o);
            if (RecipeViewers.available() && !stack.isEmpty()) {
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.show_recipes"), () -> RecipeViewers.show(stack, false)));
                e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.show_uses"), () -> RecipeViewers.show(stack, true)));
            }
            e.add(ContextMenu.Entry.separator());
            e.add(ContextMenu.Entry.of(Component.translatable("notquests.gui.delete"), () -> ClientEditor.delete(List.of(o), QuestScreen.this)).danger().icon(Icon.TRASH));
            menu = new ContextMenu(e, (int) mx, (int) my, width, height);
        }

        /** The task or reward under the mouse, if any. */
        @Nullable
        QuestObjectBase slotObjectAt(double mx, double my) {
            for (Slot s : slots) {
                if (s.object != null && s.isOver(mx, my)) {
                    return s.object;
                }
            }
            return null;
        }
    }
}
