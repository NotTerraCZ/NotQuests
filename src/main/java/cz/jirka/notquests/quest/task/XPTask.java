package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class XPTask extends Task {
    public long value = 5L;
    public boolean points = false;

    public XPTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.XP;
    }

    @Override
    public long getMaxProgress() {
        return value;
    }

    @Override
    public boolean isSubmittable() {
        return true;
    }

    public static int totalPointsForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) (2.5D * level * level - 40.5D * level + 360D);
        }
        return (int) (4.5D * level * level - 162.5D * level + 2220D);
    }

    public static int totalPoints(Player player) {
        return totalPointsForLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    @Override
    public void submit(ServerQuestManager manager, Team team, ServerPlayer player) {
        long needed = value - team.data.getProgress(this);
        if (needed <= 0L) {
            return;
        }
        long taken;
        if (points) {
            taken = Math.min(totalPoints(player), needed);
            if (taken > 0L) {
                player.giveExperiencePoints((int) -taken);
            }
        } else {
            taken = Math.min(player.experienceLevel, needed);
            if (taken > 0L) {
                player.giveExperienceLevels((int) -taken);
            }
        }
        if (taken > 0L) {
            manager.addTaskProgress(team, this, taken);
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable(points ? "notquests.task.xp.points" : "notquests.task.xp.levels", value);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        putNumber(tag, "value", value);
        if (points) {
            tag.putBoolean("points", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        value = tag.contains("value") ? Math.max(1L, tag.getLong("value")) : 5L;
        points = tag.getBoolean("points");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addLong("value", value, 5L, 1L, Integer.MAX_VALUE, v -> value = v);
        group.addBool("points", points, false, v -> points = v);
    }
}
