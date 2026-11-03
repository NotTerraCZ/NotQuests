package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.QuestObjectType;
import cz.jirka.notquests.quest.RewardAutoClaim;
import cz.jirka.notquests.quest.Tristate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.List;

public abstract class Reward extends QuestObjectBase {
    public final Quest quest;
    public Tristate teamReward = Tristate.DEFAULT;
    public RewardAutoClaim autoClaim = RewardAutoClaim.DEFAULT;
    public boolean excludeFromClaimAll = false;

    protected Reward(Quest quest) {
        this.quest = quest;
    }

    public abstract RewardType getType();

    /** Server side: give the reward to the player. {@code choice} is only used by choice rewards. */
    public abstract void claim(ServerPlayer player, boolean notify, int choice);

    @Override
    public final QuestObjectType getObjectType() {
        return QuestObjectType.REWARD;
    }

    @Override
    public QuestFile getQuestFile() {
        return quest.getQuestFile();
    }

    @Override
    public ItemStack getAltIcon() {
        return getType().icon().get();
    }

    public boolean isTeamReward() {
        return teamReward.get(getQuestFile().defaultTeamReward);
    }

    public RewardAutoClaim getAutoClaim() {
        return autoClaim == RewardAutoClaim.DEFAULT ? getQuestFile().defaultAutoClaim : autoClaim;
    }

    public boolean isInvisible() {
        return getAutoClaim() == RewardAutoClaim.INVISIBLE;
    }

    /** Choice rewards need input from the player, so they are skipped by "claim all" and auto claiming. */
    public boolean needsChoice() {
        return false;
    }

    public void addTooltip(List<Component> list, boolean editMode) {
        if (isTeamReward()) {
            list.add(Component.translatable("notquests.reward.team_reward").withStyle(ChatFormatting.BLUE));
        }
    }

    protected static void giveItem(ServerPlayer player, ItemStack stack, long count) {
        if (stack.isEmpty() || count <= 0L || cz.jirka.notquests.util.ItemUtil.isMissing(stack)) {
            return;
        }
        int max = Math.max(1, stack.getMaxStackSize());
        while (count > 0L) {
            int n = (int) Math.min(count, max);
            ItemHandlerHelper.giveItemToPlayer(player, stack.copyWithCount(n));
            count -= n;
        }
    }

    protected void notifyItem(ServerPlayer player, ItemStack stack, long count) {
        NQNetwork.sendToast(player, Component.translatable("notquests.toast.reward"),
                Component.literal(count > 1L ? count + "x " : "").append(stack.getHoverName()), stack.copyWithCount(1));
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("type", getType().id());
        if (teamReward != Tristate.DEFAULT) {
            tag.putBoolean("team_reward", teamReward == Tristate.TRUE);
        }
        if (autoClaim != RewardAutoClaim.DEFAULT) {
            tag.putString("auto", autoClaim.id());
        }
        if (excludeFromClaimAll) {
            tag.putBoolean("exclude_from_claim_all", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        if (tag.contains("team_reward", net.minecraft.nbt.Tag.TAG_BYTE)) {
            teamReward = tag.getBoolean("team_reward") ? Tristate.TRUE : Tristate.FALSE;
        } else {
            teamReward = Tristate.byId(tag.getString("team_reward"));
        }
        autoClaim = RewardAutoClaim.byId(tag.getString("auto"));
        excludeFromClaimAll = tag.getBoolean("exclude_from_claim_all");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addEnum("team_reward", teamReward, Tristate.DEFAULT, Tristate.VALUES, v -> teamReward = v);
        group.addEnum("auto", autoClaim, RewardAutoClaim.DEFAULT, RewardAutoClaim.VALUES, v -> autoClaim = v);
        group.addBool("exclude_from_claim_all", excludeFromClaimAll, false, v -> excludeFromClaimAll = v);
    }

    @Override
    public Component getTypeName() {
        return getType().displayName();
    }
}
