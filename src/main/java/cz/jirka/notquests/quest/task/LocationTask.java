package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class LocationTask extends Task {
    public String dimension = "minecraft:overworld";
    public boolean ignoreDimension = false;
    public int x;
    public int y;
    public int z;
    public int w = 1;
    public int h = 1;
    public int d = 1;

    public LocationTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.LOCATION;
    }

    @Override
    public boolean checkOnTick() {
        return true;
    }

    @Override
    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
        if (!ignoreDimension && !player.level().dimension().location().toString().equals(dimension)) {
            return;
        }
        BlockPos pos = player.blockPosition();
        if (pos.getX() >= x && pos.getX() < x + w && pos.getY() >= y && pos.getY() < y + h && pos.getZ() >= z && pos.getZ() < z + d) {
            manager.setTaskProgress(team, this, 1L);
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.task.location.title", x, y, z);
    }

    @Override
    public void addTooltip(List<Component> list, TeamData data, boolean editMode) {
        // "In: Overworld" and "From 0, 60, 0 to 15, 75, 15" instead of ids and brackets
        if (!ignoreDimension) {
            list.add(Component.translatable("notquests.task.location.in", cz.jirka.notquests.util.TextUtil.dimensionName(dimension)).withStyle(ChatFormatting.GRAY));
        }
        list.add(Component.translatable("notquests.task.location.area", x, y, z, x + w - 1, y + h - 1, z + d - 1).withStyle(ChatFormatting.GRAY));
        super.addTooltip(list, data, editMode);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("dimension", dimension);
        if (ignoreDimension) {
            tag.putBoolean("ignore_dimension", true);
        }
        // a plain list reads nicer in files than an int array: [0, 60, 0, 16, 16, 16]
        net.minecraft.nbt.ListTag location = new net.minecraft.nbt.ListTag();
        for (int v : new int[]{x, y, z, w, h, d}) {
            location.add(net.minecraft.nbt.IntTag.valueOf(v));
        }
        tag.put("location", location);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        dimension = tag.contains("dimension") ? tag.getString("dimension") : "minecraft:overworld";
        ignoreDimension = tag.getBoolean("ignore_dimension");
        int[] a = tag.getIntArray("location");
        if (a.length == 0 && tag.contains("location", net.minecraft.nbt.Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag list = tag.getList("location", net.minecraft.nbt.Tag.TAG_INT);
            a = new int[list.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = list.getInt(i);
            }
        }
        if (a.length == 6) {
            x = a[0];
            y = a[1];
            z = a[2];
            w = Math.max(1, a[3]);
            h = Math.max(1, a[4]);
            d = Math.max(1, a[5]);
        }
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("dimension", dimension, "minecraft:overworld", IdKind.DIMENSION, v -> dimension = v);
        group.addBool("ignore_dimension", ignoreDimension, false, v -> ignoreDimension = v);
        group.addInt("x", x, 0, -30000000, 30000000, v -> x = v);
        group.addInt("y", y, 0, -30000000, 30000000, v -> y = v);
        group.addInt("z", z, 0, -30000000, 30000000, v -> z = v);
        group.addInt("w", w, 1, 1, 30000000, v -> w = v);
        group.addInt("h", h, 1, 1, 30000000, v -> h = v);
        group.addInt("d", d, 1, 1, 30000000, v -> d = v);
    }
}
