package cz.jirka.notquests.client;

import com.mojang.logging.LogUtils;
import cz.jirka.notquests.client.gui.ChoiceScreen;
import cz.jirka.notquests.client.gui.ItemSelectScreen;
import cz.jirka.notquests.client.gui.QuestScreen;
import cz.jirka.notquests.client.gui.RewardTablesScreen;
import cz.jirka.notquests.client.gui.WeightedItemsScreen;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.task.TaskTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Development only: scripted walk through the quest book that takes screenshots.
 * Enabled with -Dnotquests.autotest=true, does nothing otherwise.
 */
public final class AutoTest {
    public static final boolean ENABLED = Boolean.getBoolean("notquests.autotest");
    /** NQ_AUTOTEST_MODE=chapters only takes one screenshot per chapter (used to check example quest files). */
    private static final boolean CHAPTER_SHOTS = "chapters".equals(System.getenv("NQ_AUTOTEST_MODE"));
    /** NQ_AUTOTEST_MODE=links tests requirements across chapters in the editor. */
    private static final boolean LINKS = "links".equals(System.getenv("NQ_AUTOTEST_MODE"));
    /** NQ_AUTOTEST_MODE=recipes tests the EMI / JEI buttons and the pinned quest HUD. */
    private static final boolean RECIPES = "recipes".equals(System.getenv("NQ_AUTOTEST_MODE"));
    /** NQ_AUTOTEST_MODE=icons takes the map three times, one second apart (icons that cycle), and the quest book item. */
    private static final boolean ICONS = "icons".equals(System.getenv("NQ_AUTOTEST_MODE"));
    private static final Logger LOGGER = LogUtils.getLogger();
    private static int step;
    private static int wait;

    private AutoTest() {
    }

    private static void chapterShots(Minecraft mc, QuestScreen qs) {
        var chapters = ClientQuestData.file.chapters;
        if (step == 0) {
            wait = 120;
            step = 1;
            return;
        }
        if (step == 1) {
            for (var ch : chapters) {
                for (Quest q : ch.quests) {
                    for (var t : q.tasks) {
                        java.util.List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
                        t.addTooltip(tip, ClientQuestData.team, false);
                        StringBuilder sb = new StringBuilder();
                        tip.forEach(c -> sb.append(" | ").append(c.getString()));
                        LOGGER.info("[AUTOTEST] task '{}' hud '{}'{}", t.getTitle().getString(), t.getProgressTitle().getString(), sb);
                    }
                }
            }
            command("notquests editing_mode false");
            GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2D, 2D); // no tooltip over the quests
            ClientQuestData.openQuestScreen();
            wait = 40;
            step = 2;
            return;
        }
        int i = (step - 2) / 5;
        if (i >= chapters.size()) {
            LOGGER.info("[AUTOTEST] DONE");
            mc.stop();
            return;
        }
        var c = chapters.get(i);
        switch ((step - 2) % 5) {
            case 0 -> {
                if (qs != null && !c.quests.isEmpty()) {
                    qs.testScreenPos(c.quests.get(0));
                }
                wait = 30;
            }
            case 1 -> {
                shot("chapter_" + c.filename + "_a");
                wait = 25; // a bit more than a second later, cycling tag icons show their next item
            }
            case 2 -> {
                shot("chapter_" + c.filename + "_b");
                if (qs != null && !c.quests.isEmpty()) {
                    click(qs, questToOpen(c), 0);
                }
                wait = 20;
            }
            case 3 -> {
                // hover the first task so its tooltip is in the screenshot
                Quest open = questToOpen(c);
                if (qs != null && !open.tasks.isEmpty()) {
                    double[] p = qs.testSlot(open.tasks.get(0));
                    if (p != null) {
                        moveMouse(mc, p[0], p[1]);
                    }
                }
                wait = 5;
            }
            default -> {
                shot("chapter_" + c.filename + "_view");
                if (qs != null) {
                    qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                wait = 10;
            }
        }
        step++;
    }

    private static String screenName(Minecraft mc) {
        return mc.screen == null ? "none" : mc.screen.getClass().getName();
    }

    private static void iconsTest(Minecraft mc) {
        switch (step++) {
            case 0 -> wait = 160;
            case 1 -> {
                command("notquests editing_mode false");
                GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 2D, 2D); // no tooltip over the quests
                ClientQuestData.openQuestScreen();
                wait = 40;
            }
            case 2, 3, 4 -> {
                LOGGER.info("[AUTOTEST] All Tasks icon now: {}", quest("All Tasks").getIcon().getItem());
                shot("icons_0" + (step - 2));
                wait = 20; // one second: the next icon
            }
            case 5 -> {
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                wait = 20;
            }
            case 6 -> {
                shot("icons_04_inventory");
                mc.setScreen(null);
                wait = 20;
            }
            case 7 -> {
                shot("icons_05_hotbar");
                wait = 5;
            }
            case 8 -> {
                LOGGER.info("[AUTOTEST] DONE");
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void recipesTest(Minecraft mc, QuestScreen qs) {
        switch (step++) {
            case 0 -> wait = 160;
            case 1 -> {
                command("notquests editing_mode false");
                ClientQuestData.openQuestScreen();
                wait = 40;
            }
            case 2 -> {
                click(qs, quest("Timber"), 0);
                wait = 20;
            }
            case 3 -> {
                double[] p = qs.testSlot(quest("Timber").tasks.get(0));
                moveMouse(mc, p[0], p[1]);
                wait = 10;
            }
            case 4 -> {
                shot("recipes_01_hint");
                double[] p = qs.testSlot(quest("Timber").tasks.get(0));
                qs.mouseClicked(p[0], p[1], 0); // detected item task: a click opens the recipes
                wait = 40;
            }
            case 5 -> {
                shot("recipes_02_viewer");
                LOGGER.info("[AUTOTEST] screen after clicking the task: {}", screenName(mc));
                if (!(mc.screen instanceof QuestScreen) && mc.screen != null) {
                    mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0); // like the player closing the viewer
                }
                wait = 20;
            }
            case 6 -> {
                LOGGER.info("[AUTOTEST] screen after closing: {} (quest still open: {})", screenName(mc), qs != null && qs.testViewQuest() != null);
                if (qs != null) {
                    qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    click(qs, quest("Welcome"), 0);
                }
                wait = 20;
            }
            case 7 -> {
                if (qs != null) {
                    double[] p = qs.testSlot(quest("Welcome").rewards.get(0));
                    moveMouse(mc, p[0], p[1]);
                    qs.keyPressed(GLFW.GLFW_KEY_U, 0, 0); // uses of the reward item
                }
                wait = 40;
            }
            case 8 -> {
                shot("recipes_03_uses");
                LOGGER.info("[AUTOTEST] screen after U: {}", screenName(mc));
                if (!(mc.screen instanceof QuestScreen) && mc.screen != null) {
                    mc.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                }
                wait = 20;
            }
            case 9 -> {
                LOGGER.info("[AUTOTEST] screen after closing: {}", screenName(mc));
                PacketDistributor.sendToServer(new NQNetwork.PinQuest(quest("Stone").id, true));
                cz.jirka.notquests.quest.Quest all = quest("All Tasks");
                if (all != null) {
                    PacketDistributor.sendToServer(new NQNetwork.PinQuest(all.id, true)); // unlocked, several tasks
                }
                mc.setScreen(null);
                wait = 40;
            }
            case 10 -> {
                shot("recipes_04_hud");
                wait = 5;
            }
            case 11 -> {
                LOGGER.info("[AUTOTEST] DONE");
                mc.stop();
            }
            default -> {
            }
        }
    }

    private static void linksTest(Minecraft mc, QuestScreen qs) {
        switch (step++) {
            case 0 -> wait = 120;
            case 1 -> {
                command("notquests editing_mode true");
                wait = 40;
            }
            case 2 -> {
                ClientQuestData.openQuestScreen();
                wait = 30;
            }
            case 3 -> {
                click(qs, quest("Tags"), 1);
                wait = 10;
            }
            case 4 -> {
                shot("links_01_menu");
                qs.testMenu("Require Whole Chapter");
                wait = 10;
            }
            case 5 -> {
                shot("links_02_chapters");
                LOGGER.info("[AUTOTEST] pick chapter: {}", qs.testMenu("The Beginning"));
                wait = 30;
            }
            case 6 -> {
                click(qs, quest("Tags"), 1);
                wait = 10;
            }
            case 7 -> {
                qs.testMenu("Require Quest From Chapter");
                wait = 5;
            }
            case 8 -> {
                qs.testMenu("Cheat Sheet Test");
                wait = 5;
            }
            case 9 -> {
                shot("links_03_quests");
                LOGGER.info("[AUTOTEST] pick quest: {}", qs.testMenu("All Types"));
                wait = 30;
            }
            case 10 -> {
                Quest tags = quest("Tags");
                LOGGER.info("[AUTOTEST] Tags dependencies: {}", tags.dependencies.longStream().mapToObj(cz.jirka.notquests.util.IdUtil::toFileId).toList());
                float[] p = qs.testScreenPos(tags);
                moveMouse(mc, p[0], p[1]);
                wait = 10;
            }
            case 11 -> {
                shot("links_04_badge_tooltip");
                moveMouse(mc, 2, 2);
                // would be a loop: the Tag Names chapter already needs The Beginning, which needs Welcome
                click(qs, quest("Welcome"), 1);
                wait = 10;
            }
            case 12 -> {
                qs.testMenu("Require Whole Chapter");
                wait = 5;
            }
            case 13 -> {
                LOGGER.info("[AUTOTEST] loop pick: {}", qs.testMenu("Tag Names Test"));
                wait = 30;
            }
            case 14 -> {
                LOGGER.info("[AUTOTEST] Welcome dependencies after loop attempt: {}", quest("Welcome").dependencies.size());
                qs.testSelect(quest("Welcome"));
                qs.testScreenPos(quest("Tags"));
                wait = 10;
            }
            case 15 -> {
                shot("links_05_selection_hint");
                wait = 10;
            }
            case 16 -> {
                LOGGER.info("[AUTOTEST] DONE");
                mc.stop();
            }
            default -> {
            }
        }
    }

    /** A quest whose first task uses a tag ("any log"), else one with loot rewards, else the first one. */
    private static Quest questToOpen(cz.jirka.notquests.quest.Chapter c) {
        for (Quest q : c.quests) {
            if (!q.tasks.isEmpty() && q.tasks.get(0) instanceof cz.jirka.notquests.quest.task.ItemTask it && !it.itemTag.isEmpty()) {
                return q;
            }
        }
        for (Quest q : c.quests) {
            if (q.rewards.stream().anyMatch(r -> r instanceof cz.jirka.notquests.quest.reward.TableReward
                    || r instanceof cz.jirka.notquests.quest.reward.LootTableReward)) {
                return q;
            }
        }
        return c.quests.get(0);
    }

    /** Puts the (headless) mouse at a GUI position, so hover tooltips show up in screenshots. */
    private static void moveMouse(Minecraft mc, double guiX, double guiY) {
        try {
            var w = mc.getWindow();
            var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, guiX * w.getScreenWidth() / w.getGuiScaledWidth());
            fy.setDouble(mc.mouseHandler, guiY * w.getScreenHeight() / w.getGuiScaledHeight());
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[AUTOTEST] could not move the mouse", e);
        }
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "notquests_" + name + ".png", mc.getMainRenderTarget(), c -> LOGGER.info("[AUTOTEST] screenshot {}", name));
    }

    private static Quest quest(String titleStart) {
        QuestFile f = ClientQuestData.file;
        for (Quest q : f.allQuests) {
            if (q.title.contains(titleStart)) {
                return q;
            }
        }
        LOGGER.warn("[AUTOTEST] quest {} not found", titleStart);
        return f.allQuests.get(0);
    }

    private static void command(String cmd) {
        LOGGER.info("[AUTOTEST] /{}", cmd);
        Minecraft.getInstance().player.connection.sendCommand(cmd);
    }

    private static void click(QuestScreen qs, Quest q, int button) {
        float[] p = qs.testScreenPos(q);
        qs.mouseClicked(p[0], p[1], button);
        if (button == 0) {
            qs.mouseReleased(p[0], p[1], button);
        }
    }

    private static void clickSlot(QuestScreen qs, Object o) {
        double[] p = qs.testSlot((cz.jirka.notquests.quest.QuestObjectBase) o);
        if (p != null) {
            qs.mouseClicked(p[0], p[1], 0);
        } else {
            LOGGER.warn("[AUTOTEST] slot not found");
        }
    }

    /** A quest with one task of every remaining type, set up to complete right where the player stands. */
    private static void createAllTasksQuest() {
        Minecraft mc = Minecraft.getInstance();
        var chapter = ClientQuestData.file.chapters.get(0);
        Quest q = new Quest(chapter);
        q.id = cz.jirka.notquests.util.IdUtil.random();
        q.x = 2.5D;
        q.y = 3.5D;
        q.title = "All Tasks";
        var pos = mc.player.blockPosition();
        var lt = new cz.jirka.notquests.quest.task.LocationTask(q);
        lt.x = pos.getX() - 5;
        lt.y = pos.getY() - 5;
        lt.z = pos.getZ() - 5;
        lt.w = 10;
        lt.h = 10;
        lt.d = 10;
        var dt = new cz.jirka.notquests.quest.task.DimensionTask(q);
        dt.dimension = "minecraft:overworld";
        var bt = new cz.jirka.notquests.quest.task.BiomeTask(q);
        bt.biome = "minecraft:plains";
        var at = new cz.jirka.notquests.quest.task.AdvancementTask(q);
        at.advancement = "minecraft:adventure/kill_a_mob";
        var st = new cz.jirka.notquests.quest.task.StatTask(q);
        st.stat = "minecraft:jump";
        st.value = 1L;
        var ot = new cz.jirka.notquests.quest.task.ObservationTask(q);
        ot.target = "minecraft:grass_block";
        ot.timer = 20L;
        var xt = new cz.jirka.notquests.quest.task.XPTask(q);
        xt.value = 5L;
        for (var t : java.util.List.of(lt, dt, bt, at, st, ot, xt)) {
            t.id = cz.jirka.notquests.util.IdUtil.random();
            q.tasks.add(t);
        }
        var r = new cz.jirka.notquests.quest.reward.XPLevelsReward(q);
        r.id = cz.jirka.notquests.util.IdUtil.random();
        q.rewards.add(r);
        PacketDistributor.sendToServer(new NQNetwork.CreateObject(cz.jirka.notquests.quest.QuestObjectType.QUEST.ordinal(), chapter.id, "",
                cz.jirka.notquests.quest.QuestFileIO.writeQuest(q, ClientQuestData.registries())));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ClientQuestData.file == null) {
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Screen screen = mc.screen;
        QuestScreen qs = screen instanceof QuestScreen s ? s : null;
        if (CHAPTER_SHOTS) {
            chapterShots(mc, qs);
            return;
        }
        if (LINKS) {
            linksTest(mc, qs);
            return;
        }
        if (RECIPES) {
            recipesTest(mc, qs);
            return;
        }
        if (ICONS) {
            iconsTest(mc);
            return;
        }
        LOGGER.info("[AUTOTEST] step {}", step);
        switch (step++) {
            case 0 -> {
                wait = 120;
                step = 100;
            }
            case 100 -> {
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                wait = 20;
            }
            case 101 -> {
                shot("00_inventory");
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true));
                wait = 20;
            }
            case 102 -> {
                shot("00_inventory_creative");
                mc.setScreen(null);
                step = 1;
                wait = 10;
            }
            case 1 -> {
                command("gamemode creative");
                command("time set day");
                command("gamerule doDaylightCycle false");
                command("notquests editing_mode false");
                command("difficulty easy");
                wait = 40;
            }
            case 2 -> {
                ClientQuestData.openQuestScreen();
                wait = 40;
            }
            case 3 -> shot("01_book");
            case 4 -> {
                click(qs, quest("Welcome"), 0);
                wait = 20;
            }
            case 5 -> shot("02_view");
            case 6 -> {
                clickSlot(qs, quest("Welcome").tasks.get(0));
                wait = 30;
            }
            case 7 -> shot("03_submitted");
            case 8 -> {
                clickSlot(qs, quest("Welcome").rewards.get(0));
                wait = 30;
            }
            case 9 -> {
                shot("04_claimed");
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                command("give @s minecraft:oak_log 16");
                command("give @s minecraft:birch_log 3");
                wait = 60;
            }
            case 10 -> {
                shot("05_detected");
                command("summon minecraft:zombie ~3 ~ ~ {NoAI:1b,PersistenceRequired:1b}");
                command("summon minecraft:zombie ~-3 ~ ~ {NoAI:1b,PersistenceRequired:1b}");
                wait = 10;
            }
            case 11 -> {
                command("damage @e[type=minecraft:zombie,limit=1,sort=nearest] 100 minecraft:player_attack by @s");
                wait = 5;
            }
            case 12 -> {
                command("damage @e[type=minecraft:zombie,limit=1,sort=nearest] 100 minecraft:player_attack by @s");
                wait = 30;
            }
            case 13 -> {
                click(qs, quest("Monster"), 0);
                wait = 20;
            }
            case 14 -> {
                shot("06_kill_progress");
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                command("difficulty peaceful");
                command("notquests editing_mode true");
                wait = 40;
            }
            case 15 -> shot("07_edit_mode");
            case 16 -> {
                click(qs, quest("Iron"), 1);
                wait = 20;
            }
            case 17 -> {
                shot("08_context_menu");
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                ClientEditor.action(NQNetwork.Actions.COMPLETE, quest("Stone").id, 0L);
                ClientEditor.action(NQNetwork.Actions.COMPLETE, quest("Monster").id, 0L);
                command("give @s minecraft:iron_ingot 8");
                wait = 40;
            }
            case 18 -> {
                click(qs, quest("Iron"), 0);
                wait = 20;
            }
            case 19 -> {
                shot("09_iron_unlocked");
                if (quest("Iron").tasks.get(0).isSubmittable()) {
                    clickSlot(qs, quest("Iron").tasks.get(0)); // detected items need no click (in edit mode a click opens the editor)
                }
                wait = 40;
            }
            case 20 -> {
                shot("10_iron_submitted");
                clickSlot(qs, quest("Iron").rewards.get(0));
                wait = 40;
            }
            case 21 -> {
                shot("11_random_claimed");
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                ClientEditor.action(NQNetwork.Actions.COMPLETE, quest("Nether").id, 0L);
                wait = 30;
            }
            case 22 -> {
                click(qs, quest("Nether"), 0);
                wait = 20;
            }
            case 23 -> {
                clickSlot(qs, quest("Nether").rewards.get(0));
                wait = 20;
            }
            case 24 -> {
                shot("12_choice");
                if (screen instanceof ChoiceScreen cs) {
                    double[] p = cs.cellCenter(0);
                    cs.mouseClicked(p[0], p[1], 0);
                }
                wait = 40;
            }
            case 25 -> {
                shot("13_choice_claimed");
                if (qs != null) {
                    qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                    ClientEditor.openEditor(quest("Iron"), qs);
                }
                wait = 20;
            }
            case 26 -> {
                shot("14_quest_config");
                screen.onClose();
                wait = 10;
            }
            case 27 -> {
                ClientEditor.createQuest(quest("Iron").chapter, 7.5D, 2.5D, TaskTypes.KILL, qs);
                wait = 20;
            }
            case 28 -> {
                shot("15_new_quest_config");
                screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                wait = 40;
            }
            case 29 -> {
                shot("16_new_quest_created");
                mc.setScreen(new ItemSelectScreen(qs, true, s -> {
                }));
                wait = 20;
            }
            case 30 -> {
                shot("17_item_select");
                mc.setScreen(new RewardTablesScreen(screen instanceof ItemSelectScreen ? new QuestScreen(0L) : screen));
                wait = 20;
            }
            case 31 -> {
                shot("18_reward_tables");
                RewardTable t = ClientQuestData.file.rewardTables.get(0);
                ClientEditor.openEditor(t, screen);
                wait = 20;
            }
            case 32 -> {
                shot("19_table_config");
                RewardTable t = ClientQuestData.file.rewardTables.get(0);
                mc.setScreen(new WeightedItemsScreen(screen, t.entries, l -> {
                }));
                wait = 20;
            }
            case 33 -> {
                shot("20_table_entries");
                mc.setScreen(null);
                PacketDistributor.sendToServer(new NQNetwork.PinQuest(quest("Timber").id, true));
                PacketDistributor.sendToServer(new NQNetwork.PinQuest(quest("Stone").id, true));
                PacketDistributor.sendToServer(new NQNetwork.PinQuest(quest("Monster").id, true));
                wait = 40;
            }
            case 34 -> {
                shot("21_hud");
                command("notquests team create Testers");
                ClientQuestData.openQuestScreen();
                wait = 30;
            }
            case 35 -> {
                qs.mouseClicked(4, 30, 1);
                wait = 10;
            }
            case 36 -> {
                shot("22_chapter_menu");
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                float[] p = qs.testScreenPos(quest("Timber"));
                qs.mouseClicked(p[0], p[1], 0);
                qs.mouseDragged(p[0] + 30, p[1] + 60, 0, 30, 60);
                qs.mouseReleased(p[0] + 30, p[1] + 60, 0);
                wait = 40;
            }
            case 37 -> {
                shot("23_moved");
                click(qs, quest("Stone"), 0);
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                click(qs, quest("Timber"), 1);
                wait = 10;
            }
            case 38 -> {
                shot("24_dependency_menu");
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                createAllTasksQuest();
                command("xp add @s 10 levels");
                command("tp @s ~ ~ ~ 0 90");
                mc.setScreen(null);
                wait = 30;
            }
            case 39 -> {
                mc.player.jumpFromGround();
                wait = 80;
            }
            case 40 -> {
                ClientQuestData.openQuestScreen();
                wait = 20;
            }
            case 41 -> {
                click(qs, quest("All Tasks"), 0);
                wait = 20;
            }
            case 42 -> {
                Quest q = quest("All Tasks");
                clickSlot(qs, q.tasks.get(q.tasks.size() - 1));
                wait = 40;
            }
            case 43 -> {
                shot("25_all_tasks");
                Quest q = quest("All Tasks");
                StringBuilder sb = new StringBuilder();
                for (var t : q.tasks) {
                    sb.append(t.getType().id()).append('=').append(ClientQuestData.team.getProgress(t)).append('/').append(t.getMaxProgress()).append(' ');
                }
                LOGGER.info("[AUTOTEST] all tasks quest: {} completed={}", sb, ClientQuestData.team.isCompleted(q));
                qs.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
                command("notquests editing_mode false");
                wait = 30;
            }
            case 44 -> {
                shot("26_final");
                LOGGER.info("[AUTOTEST] DONE");
                mc.setScreen(null);
                wait = 20;
            }
            case 45 -> mc.stop();
            default -> {
            }
        }
    }
}
