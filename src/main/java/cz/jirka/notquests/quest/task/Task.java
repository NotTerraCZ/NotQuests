package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.QuestObjectType;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public abstract class Task extends QuestObjectBase {
    public final Quest quest;
    public boolean optional = false;

    protected Task(Quest quest) {
        this.quest = quest;
    }

    public abstract TaskType getType();

    @Override
    public final QuestObjectType getObjectType() {
        return QuestObjectType.TASK;
    }

    @Override
    public QuestFile getQuestFile() {
        return quest.getQuestFile();
    }

    @Override
    public ItemStack getAltIcon() {
        return getType().icon().get();
    }

    public long getMaxProgress() {
        return 1L;
    }

    /** Short title used next to progress numbers (e.g. "3/16 Oak Log" instead of "3/16 16x Oak Log"). */
    public Component getProgressTitle() {
        return getTitle();
    }

    public String formatProgress(long progress) {
        return TextUtil.formatLong(progress);
    }

    /** Whether the progress numbers (3/10) should be shown at all. */
    public boolean showProgressNumbers() {
        return getMaxProgress() > 1L;
    }

    /** Tasks that do something when clicked in the quest view (submit items, tick a checkmark...). */
    public boolean isSubmittable() {
        return false;
    }

    /** Server side: the player clicked the task. Only called when the team may work on the quest. */
    public void submit(ServerQuestManager manager, Team team, ServerPlayer player) {
    }

    /** Tasks that are checked every second for every online player (location, dimension...). */
    public boolean checkOnTick() {
        return false;
    }

    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
    }

    /** Extra tooltip lines shown in the quest view. */
    public void addTooltip(List<Component> list, TeamData data, boolean editMode) {
        if (isSubmittable() && !data.isComplete(this)) {
            list.add(Component.translatable("notquests.gui.click_to_submit").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("type", getType().id());
        if (optional) {
            tag.putBoolean("optional_task", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        optional = tag.getBoolean("optional_task");
    }

    /** Writes "16" instead of "16L" when the number fits; reading accepts both. */
    protected static void putNumber(CompoundTag tag, String key, long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            tag.putInt(key, (int) value);
        } else {
            tag.putLong(key, value);
        }
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addBool("optional_task", optional, false, v -> optional = v);
    }

    @Override
    public Component getTypeName() {
        return getType().displayName();
    }
}
