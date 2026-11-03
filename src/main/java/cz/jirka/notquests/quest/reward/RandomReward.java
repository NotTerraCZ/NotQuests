package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.WeightedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Rolls the reward table {@code loot_size} times. */
public class RandomReward extends TableReward {
    public RandomReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.RANDOM;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        RewardTable t = getTable();
        if (t == null) {
            return;
        }
        for (int i = 0; i < t.lootSize; i++) {
            WeightedItem e = t.roll(player.getRandom());
            if (e != null && !e.stack.isEmpty()) {
                giveItem(player, e.stack, e.stack.getCount());
                if (notify) {
                    notifyItem(player, e.stack, e.stack.getCount());
                }
            }
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.random.title", tableName());
    }
}
