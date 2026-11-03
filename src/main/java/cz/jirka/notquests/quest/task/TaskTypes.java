package cz.jirka.notquests.quest.task;

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

public final class TaskTypes {
    private static final Map<String, TaskType> MAP = new LinkedHashMap<>();

    public static final TaskType ITEM = register("item", ItemTask::new, () -> new ItemStack(Items.CHEST));
    public static final TaskType KILL = register("kill", KillTask::new, () -> new ItemStack(Items.IRON_SWORD));
    public static final TaskType LOCATION = register("location", LocationTask::new, () -> new ItemStack(Items.COMPASS));
    public static final TaskType DIMENSION = register("dimension", DimensionTask::new, () -> new ItemStack(Items.OBSIDIAN));
    public static final TaskType BIOME = register("biome", BiomeTask::new, () -> new ItemStack(Items.OAK_SAPLING));
    public static final TaskType STRUCTURE = register("structure", StructureTask::new, () -> new ItemStack(Items.MOSSY_STONE_BRICKS));
    public static final TaskType ADVANCEMENT = register("advancement", AdvancementTask::new, () -> new ItemStack(Items.KNOWLEDGE_BOOK));
    public static final TaskType CHECKMARK = register("checkmark", CheckmarkTask::new, () -> new ItemStack(Items.LIME_DYE));
    public static final TaskType XP = register("xp", XPTask::new, () -> new ItemStack(Items.EXPERIENCE_BOTTLE));
    public static final TaskType STAT = register("stat", StatTask::new, () -> new ItemStack(Items.PAPER));
    public static final TaskType OBSERVATION = register("observation", ObservationTask::new, () -> new ItemStack(Items.SPYGLASS));

    private TaskTypes() {
    }

    private static TaskType register(String id, Function<Quest, Task> factory, Supplier<ItemStack> icon) {
        TaskType type = new TaskType(id, factory, icon);
        MAP.put(id, type);
        return type;
    }

    /** Lenient lookup for hand-written files: ignores case, surrounding spaces and a namespace ("notquests:item"). */
    @Nullable
    public static TaskType get(String id) {
        if (id == null) {
            return null;
        }
        String key = id.trim().toLowerCase(java.util.Locale.ROOT);
        int colon = key.indexOf(':');
        return MAP.get(colon >= 0 ? key.substring(colon + 1) : key);
    }

    public static Collection<TaskType> all() {
        return Collections.unmodifiableCollection(MAP.values());
    }
}
