package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Just shows a custom toast (title + text) to the player. */
public class ToastReward extends Reward {
    public String description = "";

    public ToastReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.TOAST;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        NQNetwork.sendToast(player, getTitle(), TextUtil.parse(description), getIcon());
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.toast");
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        if (!description.isEmpty()) {
            tag.putString("description", description);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        description = tag.getString("description");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addString("toast_text", description, "", v -> description = v);
    }
}
