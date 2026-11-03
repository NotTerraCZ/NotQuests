package cz.jirka.notquests.quest;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.util.IdUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class Chapter extends QuestObjectBase {
    public final QuestFile file;
    public String filename = "";
    public long group = 0L;
    public List<String> subtitle = new ArrayList<>();
    public QuestShape defaultQuestShape = QuestShape.DEFAULT;
    public ProgressionMode progressionMode = ProgressionMode.DEFAULT;
    public boolean alwaysInvisible = false;
    public boolean hideDependencyLines = false;
    public final List<Quest> quests = new ArrayList<>();
    /** Set while loading when something in this chapter got a new id, so its file gets saved back. */
    public boolean needsSave;

    public Chapter(QuestFile file) {
        this.file = file;
    }

    @Override
    public QuestObjectType getObjectType() {
        return QuestObjectType.CHAPTER;
    }

    @Override
    public QuestFile getQuestFile() {
        return file;
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.unnamed_chapter");
    }

    @Override
    public ItemStack getAltIcon() {
        for (Quest q : quests) {
            ItemStack s = q.getIcon();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return super.getAltIcon();
    }

    public QuestShape getDefaultShape() {
        return defaultQuestShape == QuestShape.DEFAULT ? file.defaultQuestShape : defaultQuestShape;
    }

    public ProgressionMode getProgressionMode() {
        return progressionMode == ProgressionMode.DEFAULT ? file.progressionMode : progressionMode;
    }

    public int getIndex() {
        return file.chapters.indexOf(this);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("filename", filename);
        if (group != 0L) {
            tag.putString("group", IdUtil.toFileId(group));
        }
        if (!subtitle.isEmpty()) {
            ListTag list = new ListTag();
            for (String s : subtitle) {
                list.add(StringTag.valueOf(s));
            }
            tag.put("subtitle", list);
        }
        if (defaultQuestShape != QuestShape.DEFAULT) {
            tag.putString("default_quest_shape", defaultQuestShape.id());
        }
        if (progressionMode != ProgressionMode.DEFAULT) {
            tag.putString("progression_mode", progressionMode.id());
        }
        if (alwaysInvisible) {
            tag.putBoolean("always_invisible", true);
        }
        if (hideDependencyLines) {
            tag.putBoolean("hide_dependency_lines", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        if (tag.contains("filename")) {
            filename = tag.getString("filename");
        }
        group = IdUtil.parseFileId(tag.getString("group"));
        subtitle = new ArrayList<>();
        if (tag.contains("subtitle", Tag.TAG_STRING)) {
            subtitle.addAll(java.util.Arrays.asList(tag.getString("subtitle").split("\n", -1)));
        } else {
            ListTag list = tag.getList("subtitle", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                subtitle.add(list.getString(i));
            }
        }
        defaultQuestShape = QuestShape.byId(tag.getString("default_quest_shape"));
        progressionMode = ProgressionMode.byId(tag.getString("progression_mode"));
        alwaysInvisible = tag.getBoolean("always_invisible");
        hideDependencyLines = tag.getBoolean("hide_dependency_lines");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addTextList("subtitle", subtitle, v -> subtitle = v);
        List<Long> ids = new ArrayList<>();
        List<Component> names = new ArrayList<>();
        ids.add(0L);
        names.add(Component.translatable("notquests.none"));
        for (ChapterGroup g : file.chapterGroups) {
            ids.add(g.id);
            names.add(g.getTitle());
        }
        group.addChoice("group", this.group, ids, names, v -> this.group = v);
        group.addEnum("default_quest_shape", defaultQuestShape, QuestShape.DEFAULT, QuestShape.VALUES, v -> defaultQuestShape = v);
        group.addEnum("progression_mode", progressionMode, ProgressionMode.DEFAULT, ProgressionMode.VALUES, v -> progressionMode = v);
        group.addBool("always_invisible", alwaysInvisible, false, v -> alwaysInvisible = v);
        group.addBool("hide_dependency_lines", hideDependencyLines, false, v -> hideDependencyLines = v);
    }
}
