package cz.jirka.notquests.quest;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.util.IdUtil;
import cz.jirka.notquests.util.ItemUtil;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Base of everything in a quest file. {@link #writeData}/{@link #readData} only handle the object's own
 * properties, never its children (those are written by {@link QuestFileIO}), so the editor can send
 * property changes without touching tasks, rewards or quests.
 */
public abstract class QuestObjectBase {
    public long id;
    public String title = "";
    public ItemStack icon = ItemStack.EMPTY;

    public abstract QuestObjectType getObjectType();

    public abstract QuestFile getQuestFile();

    /** The id as the quest files spell it: the readable name when it has one ("begin_welcome"), else 16 hex digits. */
    public String getCodeString() {
        return IdUtil.toFileId(id);
    }

    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putString("id", IdUtil.toFileId(id));
        if (!title.isEmpty()) {
            tag.putString("title", title);
        }
        ItemUtil.put(tag, "icon", icon, provider);
    }

    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        title = tag.getString("title");
        icon = ItemUtil.get(tag, "icon", provider);
    }

    /** Copy of the object's own properties, used by the editor. */
    public CompoundTag saveProps(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        writeData(tag, provider);
        return tag;
    }

    public final Component getTitle() {
        return title.isEmpty() ? getAltTitle() : TextUtil.parse(title);
    }

    public abstract Component getAltTitle();

    public final ItemStack getIcon() {
        return icon.isEmpty() ? getAltIcon() : icon;
    }

    public ItemStack getAltIcon() {
        return new ItemStack(Items.BOOK);
    }

    public boolean hasCustomIcon() {
        return !icon.isEmpty();
    }

    public void fillConfig(ConfigGroup group) {
        group.addString("title", title, "", v -> title = v);
        group.addItem("icon", icon, true, v -> icon = v).emptyLabel = "notquests.default";
    }

    public Component getTypeName() {
        return Component.translatable("notquests.object." + getObjectType().name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public String toString() {
        return getObjectType() + "#" + getCodeString();
    }
}
