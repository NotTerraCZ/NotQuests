package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.Quest;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class XPLevelsReward extends Reward {
    public int xpLevels = 5;

    public XPLevelsReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.XP_LEVELS;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        player.giveExperienceLevels(xpLevels);
        if (notify) {
            NQNetwork.sendToast(player, Component.translatable("notquests.toast.reward"), getAltTitle(), getIcon());
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.xp_levels.title", xpLevels);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putInt("xp_levels", xpLevels);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        xpLevels = tag.contains("xp_levels") ? tag.getInt("xp_levels") : 5;
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addInt("xp_levels", xpLevels, 5, 1, Integer.MAX_VALUE, v -> xpLevels = v);
    }
}
