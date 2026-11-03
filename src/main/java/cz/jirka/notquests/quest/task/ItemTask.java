package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.ItemUtil;
import cz.jirka.notquests.util.TagNames;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemTask extends Task {
    public ItemStack item = ItemStack.EMPTY;
    public String itemTag = "";
    public long count = 1L;
    public boolean consumeItems = false;
    public boolean onlyFromCrafting = false;
    public boolean matchComponents = false;

    private TagKey<Item> cachedTag;
    private String cachedTagId;

    public ItemTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.ITEM;
    }

    @Override
    public long getMaxProgress() {
        return count;
    }

    /** Detection mode: progress follows the items in the inventory, nothing is taken. */
    public boolean isDetectable() {
        return !consumeItems && !onlyFromCrafting;
    }

    @Override
    public boolean isSubmittable() {
        return consumeItems && !onlyFromCrafting;
    }

    private TagKey<Item> tagKey() {
        if (!itemTag.equals(cachedTagId)) {
            cachedTagId = itemTag;
            ResourceLocation rl = TextUtil.rl(itemTag);
            cachedTag = rl == null ? null : TagKey.create(Registries.ITEM, rl);
        }
        return cachedTag;
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!itemTag.isEmpty()) {
            TagKey<Item> key = tagKey();
            return key != null && stack.is(key);
        }
        if (item.isEmpty() || cz.jirka.notquests.util.ItemUtil.isMissing(item)) {
            return false;
        }
        return matchComponents ? ItemStack.isSameItemSameComponents(stack, item) : stack.is(item.getItem());
    }

    public long countItems(Player player) {
        long total = 0L;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (matches(s)) {
                total += s.getCount();
            }
        }
        return total;
    }

    @Override
    public void submit(ServerQuestManager manager, Team team, ServerPlayer player) {
        long needed = count - team.data.getProgress(this);
        if (needed <= 0L) {
            return;
        }
        long removed = 0L;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize() && removed < needed; i++) {
            ItemStack s = inv.getItem(i);
            if (matches(s)) {
                int take = (int) Math.min(s.getCount(), needed - removed);
                s.shrink(take);
                removed += take;
            }
        }
        if (removed > 0L) {
            inv.setChanged();
            manager.addTaskProgress(team, this, removed);
        }
    }

    @Override
    public Component getAltTitle() {
        if (!itemTag.isEmpty()) {
            // "16x Logs" / "Any Log" instead of "Any #minecraft:logs"
            return Component.literal(count > 1L
                    ? TextUtil.formatLong(count) + "x " + TagNames.plural(Registries.ITEM, itemTag)
                    : TagNames.anyOne(Registries.ITEM, itemTag));
        }
        MutableComponent name = item.isEmpty() ? Component.translatable("notquests.task.item.no_item") : item.getHoverName().copy();
        if (count > 1L) {
            return Component.literal(TextUtil.formatLong(count) + "x ").append(name);
        }
        return name;
    }

    @Override
    public Component getProgressTitle() {
        if (!title.isEmpty()) {
            return getTitle();
        }
        if (!itemTag.isEmpty()) {
            return Component.literal(TagNames.plural(Registries.ITEM, itemTag));
        }
        return !item.isEmpty() ? item.getHoverName() : getTitle();
    }

    @Override
    public ItemStack getAltIcon() {
        if (!itemTag.isEmpty()) {
            // the tag is what counts, so show all of its items one after another ("any log" -> every log)
            ItemStack current = ItemUtil.cycleItemTag(itemTag);
            if (!current.isEmpty()) {
                return current;
            }
        }
        if (!item.isEmpty()) {
            return item.copyWithCount(1);
        }
        return super.getAltIcon();
    }

    @Override
    public void addTooltip(List<Component> list, TeamData data, boolean editMode) {
        if (!itemTag.isEmpty()) {
            List<ItemStack> items = ItemUtil.itemsOfTag(itemTag);
            if (!items.isEmpty()) {
                // a few examples spread over the tag: "Any of: Oak Log, Birch Log, Crimson Stem +37 more"
                MutableComponent examples = Component.empty();
                int shown = Math.min(3, items.size());
                for (int i = 0; i < shown; i++) {
                    if (i > 0) {
                        examples.append(", ");
                    }
                    examples.append(items.get(i * items.size() / shown).getHoverName());
                }
                if (items.size() > shown) {
                    examples.append(Component.translatable("notquests.task.item.more", items.size() - shown));
                }
                list.add(Component.translatable("notquests.task.item.any_of", examples).withStyle(ChatFormatting.GRAY));
            }
            if (editMode) {
                list.add(Component.literal("#" + itemTag).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        if (onlyFromCrafting) {
            list.add(Component.translatable("notquests.task.item.only_crafting").withStyle(ChatFormatting.GRAY));
        } else if (consumeItems) {
            list.add(Component.translatable("notquests.task.item.consumed").withStyle(ChatFormatting.GRAY));
        } else {
            list.add(Component.translatable("notquests.task.item.detected").withStyle(ChatFormatting.GRAY));
        }
        super.addTooltip(list, data, editMode);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        ItemUtil.put(tag, "item", item, provider);
        if (!itemTag.isEmpty()) {
            tag.putString("item_tag", itemTag);
        }
        putNumber(tag, "count", count);
        if (consumeItems) {
            tag.putBoolean("consume_items", true);
        }
        if (onlyFromCrafting) {
            tag.putBoolean("only_from_crafting", true);
        }
        if (matchComponents) {
            tag.putBoolean("match_components", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        item = ItemUtil.get(tag, "item", provider);
        if (!item.isEmpty() && item.getCount() != 1) {
            item.setCount(1);
        }
        itemTag = tag.getString("item_tag");
        count = tag.contains("count") ? Math.max(1L, tag.getLong("count")) : 1L;
        consumeItems = tag.getBoolean("consume_items");
        onlyFromCrafting = tag.getBoolean("only_from_crafting");
        matchComponents = tag.getBoolean("match_components");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addItem("item", item, true, v -> item = v.isEmpty() ? v : v.copyWithCount(1));
        group.addId("item_tag", itemTag, "", IdKind.ITEM_TAG, v -> itemTag = v.startsWith("#") ? v.substring(1) : v);
        group.addLong("count", count, 1L, 1L, Long.MAX_VALUE, v -> count = v);
        group.addBool("consume_items", consumeItems, false, v -> consumeItems = v);
        group.addBool("only_from_crafting", onlyFromCrafting, false, v -> onlyFromCrafting = v);
        group.addBool("match_components", matchComponents, false, v -> matchComponents = v);
    }
}
