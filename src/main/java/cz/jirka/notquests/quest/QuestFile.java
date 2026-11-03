package cz.jirka.notquests.quest;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.task.ItemTask;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.util.IdUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The whole quest book: settings, chapter groups, chapters (with their quests) and reward tables.
 */
public class QuestFile extends QuestObjectBase {
    public static final long FILE_ID = 1L;

    public final List<ChapterGroup> chapterGroups = new ArrayList<>();
    public final List<Chapter> chapters = new ArrayList<>();
    public final List<RewardTable> rewardTables = new ArrayList<>();

    public QuestShape defaultQuestShape = QuestShape.CIRCLE;
    public boolean defaultTeamReward = false;
    public RewardAutoClaim defaultAutoClaim = RewardAutoClaim.DISABLED;
    public ProgressionMode progressionMode = ProgressionMode.LINEAR;
    public boolean giveBookOnFirstJoin = false;
    public double gridScale = 0.5D;
    public boolean pauseGame = false;
    public boolean showLockedQuestDetails = true;
    /** Set while loading when a chapter group got a new id, so data.snbt gets saved back. */
    public boolean dataNeedsSave;

    private final Long2ObjectOpenHashMap<QuestObjectBase> objects = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<List<Quest>> dependents = new Long2ObjectOpenHashMap<>();
    public final List<Task> allTasks = new ArrayList<>();
    public final List<ItemTask> detectItemTasks = new ArrayList<>();
    public final List<Task> tickTasks = new ArrayList<>();
    public final List<Quest> allQuests = new ArrayList<>();
    public final List<Reward> allRewards = new ArrayList<>();

    public QuestFile() {
        id = FILE_ID;
    }

    @Override
    public QuestObjectType getObjectType() {
        return QuestObjectType.FILE;
    }

    @Override
    public QuestFile getQuestFile() {
        return this;
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.quest_book");
    }

    @Override
    public ItemStack getAltIcon() {
        return new ItemStack(cz.jirka.notquests.registry.ModItems.BOOK.get());
    }

    /** Rebuilds id lookups and caches. Call after any structural change. */
    public void refresh() {
        objects.clear();
        dependents.clear();
        allTasks.clear();
        detectItemTasks.clear();
        tickTasks.clear();
        allQuests.clear();
        allRewards.clear();
        objects.put(FILE_ID, this);
        for (ChapterGroup g : chapterGroups) {
            objects.put(g.id, g);
        }
        for (Chapter c : chapters) {
            objects.put(c.id, c);
            for (Quest q : c.quests) {
                objects.put(q.id, q);
                allQuests.add(q);
                for (Task t : q.tasks) {
                    objects.put(t.id, t);
                    allTasks.add(t);
                    if (t instanceof ItemTask it && it.isDetectable()) {
                        detectItemTasks.add(it);
                    }
                    if (t.checkOnTick()) {
                        tickTasks.add(t);
                    }
                }
                for (Reward r : q.rewards) {
                    objects.put(r.id, r);
                    allRewards.add(r);
                }
            }
        }
        for (RewardTable t : rewardTables) {
            objects.put(t.id, t);
        }
        for (Quest q : allQuests) {
            for (long dep : q.dependencies) {
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(q);
            }
        }
    }

    @Nullable
    public QuestObjectBase get(long id) {
        return objects.get(id);
    }

    @Nullable
    public Chapter getChapter(long id) {
        return objects.get(id) instanceof Chapter c ? c : null;
    }

    @Nullable
    public ChapterGroup getGroup(long id) {
        return objects.get(id) instanceof ChapterGroup g ? g : null;
    }

    @Nullable
    public Quest getQuest(long id) {
        return objects.get(id) instanceof Quest q ? q : null;
    }

    @Nullable
    public Task getTask(long id) {
        return objects.get(id) instanceof Task t ? t : null;
    }

    @Nullable
    public Reward getReward(long id) {
        return objects.get(id) instanceof Reward r ? r : null;
    }

    @Nullable
    public RewardTable getRewardTable(long id) {
        return objects.get(id) instanceof RewardTable t ? t : null;
    }

    public List<Quest> getDependents(long questId) {
        List<Quest> l = dependents.get(questId);
        return l == null ? Collections.emptyList() : l;
    }

    public boolean contains(long id) {
        return objects.containsKey(id);
    }

    public long newId() {
        long id;
        do {
            id = IdUtil.random();
        } while (objects.containsKey(id));
        return id;
    }

    public List<Chapter> getChaptersInGroup(long groupId) {
        List<Chapter> list = new ArrayList<>();
        for (Chapter c : chapters) {
            if (c.group == groupId || groupId == 0L && getGroup(c.group) == null) {
                list.add(c);
            }
        }
        return list;
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.remove("id"); // always 0000000000000001, no need to write it
        tag.putString("default_quest_shape", defaultQuestShape.id());
        tag.putBoolean("default_team_reward", defaultTeamReward);
        tag.putString("default_autoclaim_rewards", defaultAutoClaim.id());
        tag.putString("progression_mode", progressionMode.id());
        tag.putBoolean("give_book_on_first_join", giveBookOnFirstJoin);
        tag.putDouble("grid_scale", gridScale);
        tag.putBoolean("pause_game", pauseGame);
        tag.putBoolean("show_locked_quest_details", showLockedQuestDetails);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        defaultQuestShape = QuestShape.byId(tag.getString("default_quest_shape"));
        if (defaultQuestShape == QuestShape.DEFAULT) {
            defaultQuestShape = QuestShape.CIRCLE;
        }
        defaultTeamReward = tag.getBoolean("default_team_reward");
        defaultAutoClaim = RewardAutoClaim.byId(tag.getString("default_autoclaim_rewards"));
        if (defaultAutoClaim == RewardAutoClaim.DEFAULT) {
            defaultAutoClaim = RewardAutoClaim.DISABLED;
        }
        progressionMode = ProgressionMode.byId(tag.getString("progression_mode"));
        if (progressionMode == ProgressionMode.DEFAULT) {
            progressionMode = ProgressionMode.LINEAR;
        }
        giveBookOnFirstJoin = tag.getBoolean("give_book_on_first_join");
        gridScale = tag.contains("grid_scale") ? tag.getDouble("grid_scale") : 0.5D;
        pauseGame = tag.getBoolean("pause_game");
        showLockedQuestDetails = !tag.contains("show_locked_quest_details") || tag.getBoolean("show_locked_quest_details");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addEnum("default_quest_shape", defaultQuestShape, QuestShape.CIRCLE, QuestShape.REAL, v -> defaultQuestShape = v);
        group.addBool("default_team_reward", defaultTeamReward, false, v -> defaultTeamReward = v);
        group.addEnum("default_autoclaim_rewards", defaultAutoClaim, RewardAutoClaim.DISABLED, RewardAutoClaim.REAL, v -> defaultAutoClaim = v);
        group.addEnum("progression_mode", progressionMode, ProgressionMode.LINEAR, ProgressionMode.REAL, v -> progressionMode = v);
        group.addBool("give_book_on_first_join", giveBookOnFirstJoin, false, v -> giveBookOnFirstJoin = v);
        group.addDouble("grid_scale", gridScale, 0.5D, 0.05D, 4D, v -> gridScale = v);
        group.addBool("pause_game", pauseGame, false, v -> pauseGame = v);
        group.addBool("show_locked_quest_details", showLockedQuestDetails, true, v -> showLockedQuestDetails = v);
    }
}
