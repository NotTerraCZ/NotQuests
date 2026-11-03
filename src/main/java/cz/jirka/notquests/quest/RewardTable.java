package cz.jirka.notquests.quest;

import cz.jirka.notquests.config.ConfigGroup;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** Weighted list of items used by random, choice and "all items" rewards. */
public class RewardTable extends QuestObjectBase {
    public final QuestFile file;
    public String filename = "";
    public List<WeightedItem> entries = new ArrayList<>();
    public int emptyWeight = 0;
    public int lootSize = 1;
    public boolean hideTooltip = false;
    /** Set while loading when the table got a new id, so its file gets saved back. */
    public boolean needsSave;

    public RewardTable(QuestFile file) {
        this.file = file;
    }

    @Override
    public QuestObjectType getObjectType() {
        return QuestObjectType.REWARD_TABLE;
    }

    @Override
    public QuestFile getQuestFile() {
        return file;
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.unnamed_reward_table");
    }

    @Override
    public ItemStack getAltIcon() {
        return new ItemStack(cz.jirka.notquests.registry.ModItems.LOOT_TABLE_ICON.get());
    }

    public int totalWeight(boolean includeEmpty) {
        int w = includeEmpty ? emptyWeight : 0;
        for (WeightedItem e : entries) {
            w += e.weight;
        }
        return w;
    }

    /** One weighted roll; returns null when the "empty" slot was rolled. */
    public WeightedItem roll(RandomSource random) {
        int total = totalWeight(true);
        if (total <= 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (WeightedItem e : entries) {
            r -= e.weight;
            if (r < 0) {
                return e;
            }
        }
        return null;
    }

    public String chanceString(WeightedItem e) {
        int total = totalWeight(true);
        if (total <= 0) {
            return "0%";
        }
        double chance = e.weight * 100D / total;
        return chance >= 10D ? String.format(java.util.Locale.ROOT, "%.0f%%", chance) : String.format(java.util.Locale.ROOT, "%.1f%%", chance);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("filename", filename);
        ListTag list = new ListTag();
        for (WeightedItem e : entries) {
            list.add(e.write(provider));
        }
        tag.put("rewards", list);
        if (emptyWeight != 0) {
            tag.putInt("empty_weight", emptyWeight);
        }
        if (lootSize != 1) {
            tag.putInt("loot_size", lootSize);
        }
        if (hideTooltip) {
            tag.putBoolean("hide_tooltip", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        if (tag.contains("filename")) {
            filename = tag.getString("filename");
        }
        entries = new ArrayList<>();
        ListTag list = tag.getList("rewards", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            WeightedItem e = WeightedItem.read(list.getCompound(i), provider);
            if (!e.stack.isEmpty()) {
                entries.add(e);
            }
        }
        emptyWeight = tag.getInt("empty_weight");
        lootSize = tag.contains("loot_size") ? Math.max(1, tag.getInt("loot_size")) : 1;
        hideTooltip = tag.getBoolean("hide_tooltip");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addWeightedItems("entries", entries, v -> entries = v);
        group.addInt("empty_weight", emptyWeight, 0, 0, 100000, v -> emptyWeight = v);
        group.addInt("loot_size", lootSize, 1, 1, 100, v -> lootSize = v);
        group.addBool("hide_tooltip", hideTooltip, false, v -> hideTooltip = v);
    }
}
