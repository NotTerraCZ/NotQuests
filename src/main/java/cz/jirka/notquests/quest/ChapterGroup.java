package cz.jirka.notquests.quest;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A collapsible header in the chapter list. */
public class ChapterGroup extends QuestObjectBase {
    public final QuestFile file;

    public ChapterGroup(QuestFile file) {
        this.file = file;
    }

    @Override
    public QuestObjectType getObjectType() {
        return QuestObjectType.CHAPTER_GROUP;
    }

    @Override
    public QuestFile getQuestFile() {
        return file;
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.unnamed_group");
    }

    @Override
    public ItemStack getAltIcon() {
        List<Chapter> list = file.getChaptersInGroup(id);
        return list.isEmpty() ? super.getAltIcon() : list.get(0).getIcon();
    }
}
