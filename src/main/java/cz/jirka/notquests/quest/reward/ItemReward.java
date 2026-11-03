package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.util.ItemUtil;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemReward extends Reward {
    public ItemStack item = ItemStack.EMPTY;
    public int count = 1;
    public int randomBonus = 0;

    public ItemReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.ITEM;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        if (item.isEmpty()) {
            return;
        }
        long total = count + (randomBonus > 0 ? player.getRandom().nextInt(randomBonus + 1) : 0);
        giveItem(player, item, total);
        if (notify) {
            notifyItem(player, item, total);
        }
    }

    @Override
    public Component getAltTitle() {
        if (item.isEmpty()) {
            return Component.translatable("notquests.task.item.no_item");
        }
        String prefix = count > 1 ? TextUtil.formatLong(count) + (randomBonus > 0 ? "-" + (count + randomBonus) : "") + "x " : "";
        return Component.literal(prefix).append(item.getHoverName());
    }

    @Override
    public ItemStack getAltIcon() {
        return item.isEmpty() ? super.getAltIcon() : item.copyWithCount(1);
    }

    /** Count shown on the reward slot. */
    public int displayCount() {
        return count;
    }

    @Override
    public void addTooltip(List<Component> list, boolean editMode) {
        if (randomBonus > 0) {
            list.add(Component.translatable("notquests.reward.item.bonus", randomBonus).withStyle(ChatFormatting.GRAY));
        }
        super.addTooltip(list, editMode);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        ItemUtil.put(tag, "item", item, provider);
        if (count != 1) {
            tag.putInt("count", count);
        }
        if (randomBonus > 0) {
            tag.putInt("random_bonus", randomBonus);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        item = ItemUtil.get(tag, "item", provider);
        int stackCount = item.isEmpty() ? 1 : item.getCount();
        if (!item.isEmpty()) {
            item.setCount(1);
        }
        count = tag.contains("count") ? Math.max(1, tag.getInt("count")) : stackCount;
        randomBonus = Math.max(0, tag.getInt("random_bonus"));
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addItem("item", item, false, v -> item = v.isEmpty() ? v : v.copyWithCount(1));
        group.addInt("count", count, 1, 1, Integer.MAX_VALUE, v -> count = v);
        group.addInt("random_bonus", randomBonus, 0, 0, Integer.MAX_VALUE, v -> randomBonus = v);
    }
}
