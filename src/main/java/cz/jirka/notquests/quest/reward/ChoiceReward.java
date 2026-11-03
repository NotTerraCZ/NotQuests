package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.WeightedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** The player picks one entry of the reward table. */
public class ChoiceReward extends TableReward {
    public ChoiceReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.CHOICE;
    }

    @Override
    public boolean needsChoice() {
        return true;
    }

    @Override
    protected boolean showChances() {
        return false;
    }

    public boolean isValidChoice(int choice) {
        RewardTable t = getTable();
        return t != null && choice >= 0 && choice < t.entries.size();
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        RewardTable t = getTable();
        if (t == null || choice < 0 || choice >= t.entries.size()) {
            return;
        }
        WeightedItem e = t.entries.get(choice);
        giveItem(player, e.stack, e.stack.getCount());
        if (notify) {
            notifyItem(player, e.stack, e.stack.getCount());
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.choice.title", tableName());
    }
}
