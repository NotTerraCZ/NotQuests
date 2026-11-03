package cz.jirka.notquests.client;

import cz.jirka.notquests.client.gui.ConfigScreen;
import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.Chapter;
import cz.jirka.notquests.quest.ChapterGroup;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestFileIO;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.QuestObjectType;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.reward.RewardType;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.quest.task.TaskType;
import cz.jirka.notquests.util.IdUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Client side editor operations: open property editors and send changes to the server. */
public final class ClientEditor {
    private ClientEditor() {
    }

    private static HolderLookup.Provider reg() {
        return ClientQuestData.registries();
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    /** Fresh instance of the same object with the same properties (children are not copied). */
    public static QuestObjectBase copyOf(QuestObjectBase o) {
        CompoundTag tag = o.saveProps(reg());
        QuestObjectBase c;
        if (o instanceof QuestFile) {
            c = new QuestFile();
        } else if (o instanceof ChapterGroup g) {
            c = new ChapterGroup(g.file);
        } else if (o instanceof Chapter ch) {
            c = new Chapter(ch.file);
        } else if (o instanceof Quest q) {
            c = new Quest(q.chapter);
        } else if (o instanceof Task t) {
            c = t.getType().create(t.quest);
        } else if (o instanceof Reward r) {
            c = r.getType().create(r.quest);
        } else if (o instanceof RewardTable t) {
            c = new RewardTable(t.file);
        } else {
            throw new IllegalArgumentException("Unknown object " + o);
        }
        c.id = o.id;
        c.readData(tag, reg());
        return c;
    }

    private static Component editTitle(QuestObjectBase o) {
        return Component.translatable("notquests.gui.edit_title", o.getTypeName(), o.getTitle());
    }

    public static void openEditor(QuestObjectBase o, Screen parent) {
        QuestObjectBase copy = copyOf(o);
        ConfigGroup group = new ConfigGroup(editTitle(o));
        copy.fillConfig(group);
        for (var v : group.values) {
            if (v instanceof cz.jirka.notquests.config.ConfigValue.ItemValue iv && v.id.equals("icon")) {
                iv.emptyPreview = o::getAltIcon;
            }
        }
        mc().setScreen(new ConfigScreen(group, parent, saved -> {
            if (saved) {
                PacketDistributor.sendToServer(new NQNetwork.EditObject(o.id, copy.saveProps(reg())));
            }
        }));
    }

    private static void create(QuestObjectType type, long parent, String subType, CompoundTag data) {
        PacketDistributor.sendToServer(new NQNetwork.CreateObject(type.ordinal(), parent, subType, data));
    }

    public static void action(int action, long id, long arg) {
        PacketDistributor.sendToServer(new NQNetwork.EditAction(action, id, arg));
    }

    public static void createQuest(Chapter chapter, double x, double y, @Nullable TaskType taskType, Screen parent) {
        Quest q = new Quest(chapter);
        q.id = IdUtil.random();
        q.x = x;
        q.y = y;
        if (taskType == null) {
            create(QuestObjectType.QUEST, chapter.id, "", QuestFileIO.writeQuest(q, reg()));
            return;
        }
        Task t = taskType.create(q);
        t.id = IdUtil.random();
        q.tasks.add(t);
        ConfigGroup group = new ConfigGroup(Component.translatable("notquests.gui.new_quest", taskType.displayName()));
        t.fillConfig(group);
        mc().setScreen(new ConfigScreen(group, parent, saved -> {
            if (saved) {
                create(QuestObjectType.QUEST, chapter.id, "", QuestFileIO.writeQuest(q, reg()));
            }
        }));
    }

    public static void addTask(Quest quest, TaskType type, Screen parent) {
        Task t = type.create(quest);
        t.id = IdUtil.random();
        ConfigGroup group = new ConfigGroup(Component.translatable("notquests.gui.new_object", type.displayName()));
        t.fillConfig(group);
        mc().setScreen(new ConfigScreen(group, parent, saved -> {
            if (saved) {
                create(QuestObjectType.TASK, quest.id, type.id(), t.saveProps(reg()));
            }
        }));
    }

    public static void addReward(Quest quest, RewardType type, Screen parent) {
        Reward r = type.create(quest);
        r.id = IdUtil.random();
        ConfigGroup group = new ConfigGroup(Component.translatable("notquests.gui.new_object", type.displayName()));
        r.fillConfig(group);
        mc().setScreen(new ConfigScreen(group, parent, saved -> {
            if (saved) {
                create(QuestObjectType.REWARD, quest.id, type.id(), r.saveProps(reg()));
            }
        }));
    }

    public static void createChapter(QuestFile file, long group, Screen parent) {
        Chapter c = new Chapter(file);
        c.id = IdUtil.random();
        c.group = group;
        ConfigGroup config = new ConfigGroup(Component.translatable("notquests.gui.new_object", Component.translatable("notquests.object.chapter")));
        c.fillConfig(config);
        mc().setScreen(new ConfigScreen(config, parent, saved -> {
            if (saved) {
                create(QuestObjectType.CHAPTER, 0L, "", c.saveProps(reg()));
            }
        }));
    }

    public static void createGroup(QuestFile file, Screen parent) {
        ChapterGroup g = new ChapterGroup(file);
        g.id = IdUtil.random();
        ConfigGroup config = new ConfigGroup(Component.translatable("notquests.gui.new_object", Component.translatable("notquests.object.chapter_group")));
        g.fillConfig(config);
        mc().setScreen(new ConfigScreen(config, parent, saved -> {
            if (saved) {
                create(QuestObjectType.CHAPTER_GROUP, 0L, "", g.saveProps(reg()));
            }
        }));
    }

    public static void createRewardTable(QuestFile file, Screen parent) {
        RewardTable t = new RewardTable(file);
        t.id = IdUtil.random();
        ConfigGroup config = new ConfigGroup(Component.translatable("notquests.gui.new_object", Component.translatable("notquests.object.reward_table")));
        t.fillConfig(config);
        mc().setScreen(new ConfigScreen(config, parent, saved -> {
            if (saved) {
                create(QuestObjectType.REWARD_TABLE, 0L, "", t.saveProps(reg()));
            }
        }));
    }

    /** Copies a quest (with tasks and rewards, new ids) into a chapter at a position. */
    public static void pasteQuest(CompoundTag questData, Chapter target, double x, double y) {
        Quest copy = QuestFileIO.readQuest(target, questData, reg());
        copy.id = IdUtil.random();
        for (Task t : copy.tasks) {
            t.id = IdUtil.random();
        }
        for (Reward r : copy.rewards) {
            r.id = IdUtil.random();
        }
        copy.x = x;
        copy.y = y;
        if (copy.chapter != target) {
            copy.chapter = target;
        }
        create(QuestObjectType.QUEST, target.id, "", QuestFileIO.writeQuest(copy, reg()));
    }

    public static CompoundTag copyQuest(Quest q) {
        return QuestFileIO.writeQuest(q, reg());
    }

    public static void delete(List<? extends QuestObjectBase> objects, Screen parent) {
        if (objects.isEmpty()) {
            return;
        }
        Component what = objects.size() == 1 ? objects.get(0).getTitle() : Component.translatable("notquests.gui.n_objects", objects.size());
        mc().setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                for (QuestObjectBase o : objects) {
                    action(NQNetwork.Actions.DELETE, o.id, 0L);
                }
            }
            mc().setScreen(parent);
        }, Component.translatable("notquests.gui.delete_confirm_title"), Component.translatable("notquests.gui.delete_confirm", what)));
    }

    public static void moveQuests(long[] ids, double[] xs, double[] ys) {
        PacketDistributor.sendToServer(new NQNetwork.MoveQuests(ids, xs, ys));
    }

    public static void setEditMode(boolean edit) {
        action(NQNetwork.Actions.SET_EDIT_MODE, QuestFile.FILE_ID, edit ? 1L : 0L);
    }
}
