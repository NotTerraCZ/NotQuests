package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.SidedHooks;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class AdvancementTask extends Task {
    public String advancement = "minecraft:story/mine_stone";
    public String criterion = "";

    public AdvancementTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.ADVANCEMENT;
    }

    @Override
    public boolean checkOnTick() {
        return true;
    }

    @Override
    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
        ResourceLocation rl = TextUtil.rl(advancement);
        if (rl == null) {
            return;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(rl);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        boolean done;
        if (criterion.isEmpty()) {
            done = progress.isDone();
        } else {
            CriterionProgress c = progress.getCriterion(criterion);
            done = c != null && c.isDone();
        }
        if (done) {
            manager.setTaskProgress(team, this, 1L);
        }
    }

    private Optional<SidedHooks.AdvancementDisplay> display() {
        ResourceLocation rl = TextUtil.rl(advancement);
        return rl == null ? Optional.empty() : SidedHooks.advancementDisplay.apply(rl);
    }

    @Override
    public Component getAltTitle() {
        Component name = display().map(SidedHooks.AdvancementDisplay::title).orElse(TextUtil.advancementName(advancement));
        return Component.translatable("notquests.task.advancement.title", name);
    }

    @Override
    public ItemStack getAltIcon() {
        return display().map(SidedHooks.AdvancementDisplay::icon).filter(s -> !s.isEmpty()).orElseGet(super::getAltIcon);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("advancement", advancement);
        if (!criterion.isEmpty()) {
            tag.putString("criterion", criterion);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        advancement = tag.contains("advancement") ? tag.getString("advancement") : "minecraft:story/mine_stone";
        criterion = tag.getString("criterion");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("advancement", advancement, "minecraft:story/mine_stone", IdKind.ADVANCEMENT, v -> advancement = v);
        group.addString("criterion", criterion, "", v -> criterion = v);
    }
}
