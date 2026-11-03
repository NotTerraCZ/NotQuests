package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.WeightedItem;
import cz.jirka.notquests.util.IdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Base for rewards that pull items from a reward table. */
public abstract class TableReward extends Reward {
    public long table = 0L;

    protected TableReward(Quest quest) {
        super(quest);
    }

    @Nullable
    public RewardTable getTable() {
        return getQuestFile().getRewardTable(table);
    }

    @Override
    public ItemStack getAltIcon() {
        // the table's own icon, or the NotQuests loot bag when the table has none
        RewardTable t = getTable();
        return t != null ? t.getIcon() : super.getAltIcon();
    }

    protected Component tableName() {
        RewardTable t = getTable();
        return t == null ? Component.translatable("notquests.reward.no_table").withStyle(ChatFormatting.RED) : t.getTitle();
    }

    protected boolean showChances() {
        return true;
    }

    @Override
    public void addTooltip(List<Component> list, boolean editMode) {
        RewardTable t = getTable();
        if (t != null && (!t.hideTooltip || editMode)) {
            int shown = 0;
            for (WeightedItem e : t.entries) {
                if (shown++ >= 12) {
                    list.add(Component.literal("...").withStyle(ChatFormatting.DARK_GRAY));
                    break;
                }
                Component line = Component.literal("- ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(e.stack.getCount() > 1 ? e.stack.getCount() + "x " : "").withStyle(ChatFormatting.GRAY))
                        .append(e.stack.getHoverName().copy().withStyle(ChatFormatting.GRAY));
                if (showChances()) {
                    line = line.copy().append(Component.literal(" (" + t.chanceString(e) + ")").withStyle(ChatFormatting.DARK_GRAY));
                }
                list.add(line);
            }
        }
        super.addTooltip(list, editMode);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        if (table != 0L) {
            tag.putString("table_id", IdUtil.toFileId(table));
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        table = IdUtil.parseFileId(tag.getString("table_id"));
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        List<Long> ids = new ArrayList<>();
        List<Component> names = new ArrayList<>();
        ids.add(0L);
        names.add(Component.translatable("notquests.none"));
        for (RewardTable t : getQuestFile().rewardTables) {
            ids.add(t.id);
            names.add(t.getTitle());
        }
        group.addChoice("table_id", table, ids, names, v -> table = v);
    }
}
