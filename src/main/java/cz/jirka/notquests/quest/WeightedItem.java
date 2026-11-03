package cz.jirka.notquests.quest;

import cz.jirka.notquests.util.ItemUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** One entry of a reward table: an item stack (count = stack count) with a weight. */
public class WeightedItem {
    public ItemStack stack;
    public int weight;

    public WeightedItem(ItemStack stack, int weight) {
        this.stack = stack;
        this.weight = weight;
    }

    public WeightedItem copy() {
        return new WeightedItem(stack.copy(), weight);
    }

    public CompoundTag write(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put("item", ItemUtil.write(stack.copyWithCount(1), provider));
        if (stack.getCount() != 1) {
            tag.putInt("count", stack.getCount());
        }
        tag.putInt("weight", weight);
        return tag;
    }

    public static WeightedItem read(CompoundTag tag, HolderLookup.Provider provider) {
        ItemStack stack = ItemUtil.get(tag, "item", provider);
        int count = tag.contains("count") ? tag.getInt("count") : 1;
        if (!stack.isEmpty()) {
            stack.setCount(Math.max(1, Math.min(count, 99)));
        }
        int weight = tag.contains("weight") ? tag.getInt("weight") : 1;
        return new WeightedItem(stack, Math.max(0, weight));
    }
}
