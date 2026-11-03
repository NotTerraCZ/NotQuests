package cz.jirka.notquests.progress;

import cz.jirka.notquests.quest.Chapter;
import cz.jirka.notquests.quest.DependencyRequirement;
import cz.jirka.notquests.quest.ProgressionMode;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.util.IdUtil;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Quest progress of one team. Shared by server and client (the client receives a copy of its own team).
 */
public class TeamData {
    /** Key used for rewards that are claimed once for the whole team. */
    public static final UUID TEAM_KEY = Util.NIL_UUID;

    public final Long2LongOpenHashMap taskProgress = new Long2LongOpenHashMap();
    public final Long2LongOpenHashMap completed = new Long2LongOpenHashMap();
    public final Map<UUID, LongOpenHashSet> claimed = new HashMap<>();
    public final Map<UUID, LongArrayList> pinned = new HashMap<>();

    // ------------------------------------------------------------------ tasks

    public long getProgress(Task t) {
        return taskProgress.get(t.id);
    }

    public void setProgress(Task t, long value) {
        if (value <= 0L) {
            taskProgress.remove(t.id);
        } else {
            taskProgress.put(t.id, value);
        }
    }

    public boolean isComplete(Task t) {
        return getProgress(t) >= t.getMaxProgress();
    }

    // ------------------------------------------------------------------ completion

    public boolean isCompleted(QuestObjectBase o) {
        return completed.containsKey(o.id);
    }

    public boolean isCompleted(long id) {
        return completed.containsKey(id);
    }

    public void setCompleted(long id, long time) {
        completed.put(id, time);
    }

    public void removeCompleted(long id) {
        completed.remove(id);
    }

    public boolean isStarted(Quest q) {
        if (isCompleted(q)) {
            return true;
        }
        for (Task t : q.tasks) {
            if (getProgress(t) > 0L) {
                return true;
            }
        }
        return false;
    }

    public boolean areDependenciesComplete(Quest q) {
        if (q.dependencies.isEmpty()) {
            return true;
        }
        QuestFile file = q.getQuestFile();
        DependencyRequirement req = q.dependencyRequirement;
        int total = 0;
        int done = 0;
        for (int i = 0; i < q.dependencies.size(); i++) {
            QuestObjectBase o = file.get(q.dependencies.getLong(i));
            boolean ok;
            if (o instanceof Quest dep) {
                ok = req.completed ? isCompleted(dep) : isStarted(dep);
            } else if (o instanceof Chapter ch) {
                ok = isCompleted(ch);
            } else {
                continue;
            }
            total++;
            if (ok) {
                done++;
            }
        }
        if (total == 0) {
            return true;
        }
        int required = q.minRequiredDependencies > 0 ? Math.min(q.minRequiredDependencies, total) : (req.one ? 1 : total);
        return done >= required;
    }

    public boolean canStartTasks(Quest q) {
        return q.getProgressionMode() == ProgressionMode.FLEXIBLE || areDependenciesComplete(q);
    }

    /** Whether a non-editor player can see the quest at all. */
    public boolean isVisible(Quest q) {
        if (q.invisible && !isCompleted(q)) {
            return false;
        }
        if (q.hideUntilDepsComplete && !areDependenciesComplete(q)) {
            return false;
        }
        return true;
    }

    public boolean isVisible(Chapter c) {
        if (c.alwaysInvisible) {
            return false;
        }
        for (Quest q : c.quests) {
            if (isVisible(q)) {
                return true;
            }
        }
        return false;
    }

    public int questProgressPercent(Quest q) {
        if (isCompleted(q)) {
            return 100;
        }
        double sum = 0D;
        int count = 0;
        for (Task t : q.tasks) {
            if (t.optional) {
                continue;
            }
            count++;
            sum += Math.min(1D, getProgress(t) / (double) Math.max(1L, t.getMaxProgress()));
        }
        return count == 0 ? 0 : (int) Math.floor(sum * 100D / count);
    }

    public int chapterProgressPercent(Chapter c) {
        int total = 0;
        int done = 0;
        for (Quest q : c.quests) {
            if (q.optional) {
                continue;
            }
            total++;
            if (isCompleted(q)) {
                done++;
            }
        }
        return total == 0 ? 0 : done * 100 / total;
    }

    // ------------------------------------------------------------------ rewards

    public boolean isRewardClaimed(UUID player, Reward r) {
        LongOpenHashSet set = claimed.get(r.isTeamReward() ? TEAM_KEY : player);
        return set != null && set.contains(r.id);
    }

    public void setRewardClaimed(UUID player, Reward r) {
        claimed.computeIfAbsent(r.isTeamReward() ? TEAM_KEY : player, k -> new LongOpenHashSet()).add(r.id);
    }

    public void unclaim(long rewardId) {
        for (LongOpenHashSet set : claimed.values()) {
            set.remove(rewardId);
        }
    }

    public boolean canClaim(UUID player, Reward r) {
        return isCompleted(r.quest) && !isRewardClaimed(player, r);
    }

    public boolean hasUnclaimedRewards(UUID player, Quest q) {
        if (!isCompleted(q)) {
            return false;
        }
        for (Reward r : q.rewards) {
            if (!r.isInvisible() && !isRewardClaimed(player, r)) {
                return true;
            }
        }
        return false;
    }

    public boolean areAllRewardsClaimed(UUID player, Quest q) {
        for (Reward r : q.rewards) {
            if (!isRewardClaimed(player, r)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasUnclaimedRewards(UUID player, Chapter c) {
        for (Quest q : c.quests) {
            if (hasUnclaimedRewards(player, q)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasUnclaimedRewards(UUID player, QuestFile f) {
        for (Chapter c : f.chapters) {
            if (hasUnclaimedRewards(player, c)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ pinned quests

    public boolean isPinned(UUID player, long questId) {
        LongArrayList list = pinned.get(player);
        return list != null && list.contains(questId);
    }

    public void setPinned(UUID player, long questId, boolean pin) {
        LongArrayList list = pinned.computeIfAbsent(player, k -> new LongArrayList());
        list.rem(questId);
        if (pin) {
            list.add(questId);
        }
        if (list.isEmpty()) {
            pinned.remove(player);
        }
    }

    public LongArrayList getPinned(UUID player) {
        LongArrayList list = pinned.get(player);
        return list == null ? new LongArrayList() : list;
    }

    // ------------------------------------------------------------------ nbt

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        CompoundTag progress = new CompoundTag();
        for (Long2LongMap.Entry e : taskProgress.long2LongEntrySet()) {
            progress.putLong(IdUtil.toHex(e.getLongKey()), e.getLongValue());
        }
        tag.put("progress", progress);
        CompoundTag done = new CompoundTag();
        for (Long2LongMap.Entry e : completed.long2LongEntrySet()) {
            done.putLong(IdUtil.toHex(e.getLongKey()), e.getLongValue());
        }
        tag.put("completed", done);
        CompoundTag claimedTag = new CompoundTag();
        for (Map.Entry<UUID, LongOpenHashSet> e : claimed.entrySet()) {
            if (!e.getValue().isEmpty()) {
                claimedTag.put(e.getKey().toString(), new LongArrayTag(e.getValue().toLongArray()));
            }
        }
        tag.put("claimed", claimedTag);
        CompoundTag pinnedTag = new CompoundTag();
        for (Map.Entry<UUID, LongArrayList> e : pinned.entrySet()) {
            if (!e.getValue().isEmpty()) {
                pinnedTag.put(e.getKey().toString(), new LongArrayTag(e.getValue().toLongArray()));
            }
        }
        tag.put("pinned", pinnedTag);
        return tag;
    }

    public static TeamData read(CompoundTag tag) {
        TeamData d = new TeamData();
        CompoundTag progress = tag.getCompound("progress");
        for (String key : progress.getAllKeys()) {
            long id = IdUtil.parse(key);
            if (id != 0L) {
                d.taskProgress.put(id, progress.getLong(key));
            }
        }
        CompoundTag done = tag.getCompound("completed");
        for (String key : done.getAllKeys()) {
            long id = IdUtil.parse(key);
            if (id != 0L) {
                d.completed.put(id, done.getLong(key));
            }
        }
        CompoundTag claimedTag = tag.getCompound("claimed");
        for (String key : claimedTag.getAllKeys()) {
            try {
                d.claimed.put(UUID.fromString(key), new LongOpenHashSet(claimedTag.getLongArray(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        CompoundTag pinnedTag = tag.getCompound("pinned");
        for (String key : pinnedTag.getAllKeys()) {
            try {
                d.pinned.put(UUID.fromString(key), new LongArrayList(pinnedTag.getLongArray(key)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return d;
    }

    public TeamData copy() {
        return read(write());
    }
}
