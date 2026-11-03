package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.Quest;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class XPReward extends Reward {
    public int xp = 100;

    public XPReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.XP;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        player.giveExperiencePoints(xp);
        if (notify) {
            NQNetwork.sendToast(player, Component.translatable("notquests.toast.reward"), getAltTitle(), getIcon());
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.xp.title", xp);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putInt("xp", xp);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        xp = tag.contains("xp") ? tag.getInt("xp") : 100;
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addInt("xp", xp, 100, 1, Integer.MAX_VALUE, v -> xp = v);
    }
}
