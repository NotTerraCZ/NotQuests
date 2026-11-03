package cz.jirka.notquests.quest;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.util.IdUtil;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Quest extends QuestObjectBase {
    public Chapter chapter;
    public double x;
    public double y;
    public double size = 1D;
    public QuestShape shape = QuestShape.DEFAULT;
    public String subtitle = "";
    public List<String> description = new ArrayList<>();
    public LongArrayList dependencies = new LongArrayList();
    public DependencyRequirement dependencyRequirement = DependencyRequirement.ALL_COMPLETED;
    public int minRequiredDependencies = 0;
    public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
    public boolean optional = false;
    public boolean invisible = false;
    public boolean hideUntilDepsComplete = false;
    public boolean hideDetailsUntilStartable = false;
    public boolean hideDependencyLines = false;
    public boolean canRepeat = false;
    public boolean disableToast = false;
    public final List<Task> tasks = new ArrayList<>();
    public final List<Reward> rewards = new ArrayList<>();

    public Quest(Chapter chapter) {
        this.chapter = chapter;
    }

    @Override
    public QuestObjectType getObjectType() {
        return QuestObjectType.QUEST;
    }

    @Override
    public QuestFile getQuestFile() {
        return chapter.file;
    }

    @Override
    public Component getAltTitle() {
        if (!tasks.isEmpty()) {
            return tasks.get(0).getTitle();
        }
        return Component.translatable("notquests.unnamed_quest");
    }

    @Override
    public ItemStack getAltIcon() {
        if (tasks.size() > 1) {
            // a quest that wants several things goes through all of them (one per second), like FTB Quests
            ItemStack s = tasks.get(cz.jirka.notquests.util.ItemUtil.cycleIndex(tasks.size())).getIcon();
            if (!s.isEmpty()) {
                return s;
            }
        }
        for (Task t : tasks) {
            ItemStack s = t.getIcon();
            if (!s.isEmpty()) {
                return s;
            }
        }
        for (Reward r : rewards) {
            ItemStack s = r.getIcon();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return super.getAltIcon();
    }

    public QuestShape getShape() {
        return shape == QuestShape.DEFAULT ? chapter.getDefaultShape() : shape;
    }

    public ProgressionMode getProgressionMode() {
        return progressionMode == ProgressionMode.DEFAULT ? chapter.getProgressionMode() : progressionMode;
    }

    public boolean hasDependency(long id) {
        return dependencies.contains(id);
    }

    /** True if {@code other} is (transitively) required by this quest. */
    public boolean dependsOn(Quest other) {
        return dependsOn(other, new java.util.HashSet<>());
    }

    private boolean dependsOn(Quest other, java.util.Set<Long> visited) {
        if (!visited.add(id)) {
            return false;
        }
        for (long d : dependencies) {
            if (d == other.id) {
                return true;
            }
            QuestObjectBase o = chapter.file.get(d);
            if (o instanceof Quest q && q.dependsOn(other, visited)) {
                return true;
            }
            if (o instanceof Chapter c) {
                // requiring a whole chapter means requiring every quest that counts for it
                for (Quest cq : c.quests) {
                    if (!cq.optional && (cq.id == other.id || cq.dependsOn(other, visited))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True if completing chapter {@code c} needs this quest (so this quest must not require that chapter). */
    public boolean isNeededFor(Chapter c) {
        for (Quest cq : c.quests) {
            if (!cq.optional && (cq == this || cq.dependsOn(this))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putDouble("x", x);
        tag.putDouble("y", y);
        if (size != 1D) {
            tag.putDouble("size", size);
        }
        if (shape != QuestShape.DEFAULT) {
            tag.putString("shape", shape.id());
        }
        if (!subtitle.isEmpty()) {
            tag.putString("subtitle", subtitle);
        }
        if (!description.isEmpty()) {
            ListTag list = new ListTag();
            for (String s : description) {
                list.add(StringTag.valueOf(s));
            }
            tag.put("description", list);
        }
        if (!dependencies.isEmpty()) {
            ListTag list = new ListTag();
            for (long d : dependencies) {
                list.add(StringTag.valueOf(IdUtil.toFileId(d)));
            }
            tag.put("dependencies", list);
        }
        if (dependencyRequirement != DependencyRequirement.ALL_COMPLETED) {
            tag.putString("dependency_requirement", dependencyRequirement.id());
        }
        if (minRequiredDependencies > 0) {
            tag.putInt("min_required_dependencies", minRequiredDependencies);
        }
        if (progressionMode != ProgressionMode.DEFAULT) {
            tag.putString("progression_mode", progressionMode.id());
        }
        putFlag(tag, "optional", optional);
        putFlag(tag, "invisible", invisible);
        putFlag(tag, "hide_until_deps_complete", hideUntilDepsComplete);
        putFlag(tag, "hide_details_until_startable", hideDetailsUntilStartable);
        putFlag(tag, "hide_dependency_lines", hideDependencyLines);
        putFlag(tag, "can_repeat", canRepeat);
        putFlag(tag, "disable_toast", disableToast);
    }

    private static void putFlag(CompoundTag tag, String key, boolean value) {
        if (value) {
            tag.putBoolean(key, true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        x = tag.getDouble("x");
        y = tag.getDouble("y");
        size = tag.contains("size") ? tag.getDouble("size") : 1D;
        shape = QuestShape.byId(tag.getString("shape"));
        subtitle = tag.getString("subtitle");
        description = new ArrayList<>();
        if (tag.contains("description", Tag.TAG_STRING)) {
            description.addAll(java.util.Arrays.asList(tag.getString("description").split("\n", -1)));
        } else {
            ListTag desc = tag.getList("description", Tag.TAG_STRING);
            for (int i = 0; i < desc.size(); i++) {
                description.add(desc.getString(i));
            }
        }
        dependencies = new LongArrayList();
        List<String> depIds = new ArrayList<>();
        if (tag.contains("dependencies", Tag.TAG_STRING)) {
            depIds.add(tag.getString("dependencies"));
        } else {
            ListTag deps = tag.getList("dependencies", Tag.TAG_STRING);
            for (int i = 0; i < deps.size(); i++) {
                depIds.add(deps.getString(i));
            }
        }
        for (String s : depIds) {
            long d = IdUtil.parseFileId(s);
            if (d != 0L && d != id && !dependencies.contains(d)) {
                dependencies.add(d);
            }
        }
        dependencyRequirement = DependencyRequirement.byId(tag.getString("dependency_requirement"));
        minRequiredDependencies = tag.getInt("min_required_dependencies");
        progressionMode = ProgressionMode.byId(tag.getString("progression_mode"));
        optional = tag.getBoolean("optional");
        invisible = tag.getBoolean("invisible");
        hideUntilDepsComplete = tag.getBoolean("hide_until_deps_complete");
        hideDetailsUntilStartable = tag.getBoolean("hide_details_until_startable");
        hideDependencyLines = tag.getBoolean("hide_dependency_lines");
        canRepeat = tag.getBoolean("can_repeat");
        disableToast = tag.getBoolean("disable_toast");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addString("subtitle", subtitle, "", v -> subtitle = v);
        group.addTextList("description", description, v -> description = v);
        group.addDouble("x", x, 0D, -100000D, 100000D, v -> x = v);
        group.addDouble("y", y, 0D, -100000D, 100000D, v -> y = v);
        group.addDouble("size", size, 1D, 0.25D, 8D, v -> size = v);
        group.addEnum("shape", shape, QuestShape.DEFAULT, QuestShape.VALUES, v -> shape = v);
        group.addEnum("dependency_requirement", dependencyRequirement, DependencyRequirement.ALL_COMPLETED, DependencyRequirement.VALUES, v -> dependencyRequirement = v);
        group.addInt("min_required_dependencies", minRequiredDependencies, 0, 0, 1000, v -> minRequiredDependencies = v);
        group.addEnum("progression_mode", progressionMode, ProgressionMode.DEFAULT, ProgressionMode.VALUES, v -> progressionMode = v);
        group.addBool("optional", optional, false, v -> optional = v);
        group.addBool("invisible", invisible, false, v -> invisible = v);
        group.addBool("hide_until_deps_complete", hideUntilDepsComplete, false, v -> hideUntilDepsComplete = v);
        group.addBool("hide_details_until_startable", hideDetailsUntilStartable, false, v -> hideDetailsUntilStartable = v);
        group.addBool("hide_dependency_lines", hideDependencyLines, false, v -> hideDependencyLines = v);
        group.addBool("can_repeat", canRepeat, false, v -> canRepeat = v);
        group.addBool("disable_toast", disableToast, false, v -> disableToast = v);
    }
}
