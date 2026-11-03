package cz.jirka.notquests.util;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ItemUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Custom data key of the barrier placeholder that keeps an unknown item reference so saving never loses it. */
    private static final String MISSING = "notquests_missing";

    private ItemUtil() {
    }

    /** Plain items are written as "minecraft:diamond", stacks with components as full compounds. */
    public static Tag write(ItemStack stack, HolderLookup.Provider provider) {
        if (stack.isEmpty()) {
            return StringTag.valueOf("");
        }
        Tag missing = missingReference(stack);
        if (missing != null) {
            return missing.copy();
        }
        if (stack.getCount() == 1 && stack.getComponentsPatch().isEmpty()) {
            return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        ItemStack copy = stack.getCount() > 99 ? stack.copyWithCount(99) : stack;
        return copy.save(provider);
    }

    public static ItemStack read(Tag tag, HolderLookup.Provider provider) {
        if (tag instanceof StringTag s) {
            String id = s.getAsString().trim();
            if (id.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = fromId(id);
            return stack.isEmpty() && !isAir(id) ? missing(tag, id) : stack;
        } else if (tag instanceof CompoundTag c) {
            ItemStack stack = ItemStack.parseOptional(provider, c);
            String id = c.getString("id");
            return stack.isEmpty() && !id.isEmpty() && !isAir(id) ? missing(tag, id) : stack;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack fromId(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static boolean isAir(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.trim());
        return rl != null && rl.equals(BuiltInRegistries.ITEM.getKey(Items.AIR));
    }

    /** True for the barrier that stands in for an item id that does not exist (typo, or the mod is not installed). */
    public static boolean isMissing(ItemStack stack) {
        return missingReference(stack) != null;
    }

    @Nullable
    private static Tag missingReference(ItemStack stack) {
        if (!stack.is(Items.BARRIER)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        return tag.contains(MISSING) ? tag.get(MISSING) : null;
    }

    private static ItemStack missing(Tag raw, String id) {
        LOGGER.warn("Unknown item '{}' in the quest files - it is shown as a barrier and kept in the file as it is", id);
        ItemStack stack = new ItemStack(Items.BARRIER);
        CompoundTag data = new CompoundTag();
        data.put(MISSING, raw.copy());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Missing item: " + id)
                .withStyle(style -> style.withItalic(false).withColor(ChatFormatting.RED)));
        return stack;
    }

    public static void put(CompoundTag tag, String key, ItemStack stack, HolderLookup.Provider provider) {
        if (!stack.isEmpty()) {
            tag.put(key, write(stack, provider));
        }
    }

    public static ItemStack get(CompoundTag tag, String key, HolderLookup.Provider provider) {
        return tag.contains(key) ? read(tag.get(key), provider) : ItemStack.EMPTY;
    }

    public static String id(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ------------------------------------------------------------------ icons for tags ("any log" shows every log in turn)

    /** Position in a list of icons that moves on once per second. */
    public static int cycleIndex(int size) {
        return size <= 0 ? 0 : (int) ((System.currentTimeMillis() / 1000L) % size);
    }

    /** Every item of an item tag, in tag order. */
    public static List<ItemStack> itemsOfTag(String tagId) {
        ResourceLocation rl = TextUtil.rl(tagId);
        List<ItemStack> list = new ArrayList<>();
        if (rl != null) {
            BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl)).ifPresent(set -> {
                for (Holder<Item> h : set) {
                    list.add(new ItemStack(h.value()));
                }
            });
        }
        return list;
    }

    /** The item of an item tag to show right now; changes every second. */
    public static ItemStack cycleItemTag(String tagId) {
        ResourceLocation rl = TextUtil.rl(tagId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl));
        if (set.isEmpty() || set.get().size() == 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(set.get().get(cycleIndex(set.get().size())).value());
    }

    /** The block (as item) of a block tag to show right now; changes every second. */
    public static ItemStack cycleBlockTag(String tagId) {
        ResourceLocation rl = TextUtil.rl(tagId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> list = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, rl)).ifPresent(set -> {
            for (Holder<Block> h : set) {
                Item item = h.value().asItem();
                if (item != Items.AIR) {
                    list.add(new ItemStack(item));
                }
            }
        });
        return list.isEmpty() ? ItemStack.EMPTY : list.get(cycleIndex(list.size()));
    }

    /** The spawn egg of an entity type tag to show right now; changes every second. */
    public static ItemStack cycleEntityTag(String tagId) {
        ResourceLocation rl = TextUtil.rl(tagId);
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        List<ItemStack> list = new ArrayList<>();
        BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, rl)).ifPresent(set -> {
            for (Holder<EntityType<?>> h : set) {
                SpawnEggItem egg = SpawnEggItem.byId(h.value());
                if (egg != null) {
                    list.add(new ItemStack(egg));
                }
            }
        });
        return list.isEmpty() ? ItemStack.EMPTY : list.get(cycleIndex(list.size()));
    }
}
