package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.util.SidedHooks;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Grants (all remaining criteria of) an advancement. */
public class AdvancementReward extends Reward {
    public String advancement = "minecraft:story/root";

    public AdvancementReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.ADVANCEMENT;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        ResourceLocation rl = TextUtil.rl(advancement);
        if (rl == null) {
            return;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(rl);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        List<String> remaining = new ArrayList<>();
        for (String c : progress.getRemainingCriteria()) {
            remaining.add(c);
        }
        for (String c : remaining) {
            player.getAdvancements().award(holder, c);
        }
    }

    @Override
    public Component getAltTitle() {
        ResourceLocation rl = TextUtil.rl(advancement);
        Component name = rl == null ? Component.literal(advancement)
                : SidedHooks.advancementDisplay.apply(rl).map(SidedHooks.AdvancementDisplay::title).orElse(TextUtil.advancementName(advancement));
        return Component.translatable("notquests.reward.advancement.title", name);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("advancement", advancement);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        advancement = tag.contains("advancement") ? tag.getString("advancement") : "minecraft:story/root";
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("advancement", advancement, "minecraft:story/root", IdKind.ADVANCEMENT, v -> advancement = v);
    }
}
