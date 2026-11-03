package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.WeightedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Gives every entry of the reward table. */
public class AllTableReward extends TableReward {
    public AllTableReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.ALL_TABLE;
    }

    @Override
    protected boolean showChances() {
        return false;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        RewardTable t = getTable();
        if (t == null) {
            return;
        }
        for (WeightedItem e : t.entries) {
            giveItem(player, e.stack, e.stack.getCount());
            if (notify) {
                notifyItem(player, e.stack, e.stack.getCount());
            }
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.all_table.title", tableName());
    }
}
