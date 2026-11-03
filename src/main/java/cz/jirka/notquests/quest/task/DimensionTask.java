package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DimensionTask extends Task {
    public String dimension = "minecraft:the_nether";

    public DimensionTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.DIMENSION;
    }

    @Override
    public boolean checkOnTick() {
        return true;
    }

    @Override
    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
        if (player.level().dimension().location().toString().equals(dimension)) {
            manager.setTaskProgress(team, this, 1L);
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.task.dimension.title", TextUtil.dimensionName(dimension));
    }

    @Override
    public ItemStack getAltIcon() {
        return switch (dimension) {
            case "minecraft:the_nether" -> new ItemStack(Items.NETHERRACK);
            case "minecraft:the_end" -> new ItemStack(Items.END_STONE);
            case "minecraft:overworld" -> new ItemStack(Items.GRASS_BLOCK);
            default -> super.getAltIcon();
        };
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("dimension", dimension);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        dimension = tag.contains("dimension") ? tag.getString("dimension") : "minecraft:the_nether";
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("dimension", dimension, "minecraft:the_nether", IdKind.DIMENSION, v -> dimension = v);
    }
}
