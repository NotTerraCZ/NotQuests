package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.quest.Quest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public final class RewardTypes {
    private static final Map<String, RewardType> MAP = new LinkedHashMap<>();

    public static final RewardType ITEM = register("item", ItemReward::new, () -> new ItemStack(Items.CHEST));
    public static final RewardType XP = register("xp", XPReward::new, () -> new ItemStack(Items.EXPERIENCE_BOTTLE));
    public static final RewardType XP_LEVELS = register("xp_levels", XPLevelsReward::new, () -> new ItemStack(Items.EXPERIENCE_BOTTLE));
    public static final RewardType COMMAND = register("command", CommandReward::new, () -> new ItemStack(Items.COMMAND_BLOCK));
    public static final RewardType RANDOM = register("random", RandomReward::new, () -> new ItemStack(Items.ENDER_CHEST));
    public static final RewardType CHOICE = register("choice", ChoiceReward::new, () -> new ItemStack(Items.TRAPPED_CHEST));
    public static final RewardType ALL_TABLE = register("all_table", AllTableReward::new, () -> new ItemStack(Items.BARREL));
    public static final RewardType LOOT_TABLE = register("loot_table", LootTableReward::new, () -> new ItemStack(cz.jirka.notquests.registry.ModItems.LOOT_TABLE_ICON.get()));
    public static final RewardType ADVANCEMENT = register("advancement", AdvancementReward::new, () -> new ItemStack(Items.KNOWLEDGE_BOOK));
    public static final RewardType TOAST = register("toast", ToastReward::new, () -> new ItemStack(Items.PAPER));

    private RewardTypes() {
    }

    private static RewardType register(String id, Function<Quest, Reward> factory, Supplier<ItemStack> icon) {
        RewardType type = new RewardType(id, factory, icon);
        MAP.put(id, type);
        return type;
    }

    /** Lenient lookup for hand-written files: ignores case, surrounding spaces and a namespace ("notquests:item"). */
    @Nullable
    public static RewardType get(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = key.indexOf(':');
        return MAP.get(colon >= 0 ? key.substring(colon + 1) : key);
    }

    public static Collection<RewardType> all() {
        return Collections.unmodifiableCollection(MAP.values());
    }
}
