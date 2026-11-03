package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;

public class StatTask extends Task {
    public String stat = "minecraft:jump";
    public long value = 100L;

    public StatTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.STAT;
    }

    @Override
    public long getMaxProgress() {
        return value;
    }

    private Stat<ResourceLocation> statObject() {
        ResourceLocation rl = TextUtil.rl(stat);
        if (rl == null) {
            return null;
        }
        ResourceLocation key = BuiltInRegistries.CUSTOM_STAT.get(rl);
        return key == null ? null : Stats.CUSTOM.get(key);
    }

    @Override
    public boolean checkOnTick() {
        return true;
    }

    @Override
    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
        Stat<ResourceLocation> s = statObject();
        if (s == null) {
            return;
        }
        long v = Math.min(player.getStats().getValue(s), value);
        if (v > team.data.getProgress(this)) {
            manager.setTaskProgress(team, this, v);
        }
    }

    @Override
    public String formatProgress(long progress) {
        Stat<ResourceLocation> s = statObject();
        return s == null ? super.formatProgress(progress) : s.format((int) Math.min(progress, Integer.MAX_VALUE));
    }

    @Override
    public Component getAltTitle() {
        ResourceLocation rl = TextUtil.rl(stat);
        Component name = rl == null ? Component.literal(stat)
                : Component.translatableWithFallback("stat." + rl.getNamespace() + "." + rl.getPath(), TextUtil.prettyId(stat));
        return Component.translatable("notquests.task.stat.title", name, formatProgress(value));
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("stat", stat);
        putNumber(tag, "value", value);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        stat = tag.contains("stat") ? tag.getString("stat") : "minecraft:jump";
        value = tag.contains("value") ? Math.max(1L, tag.getLong("value")) : 100L;
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("stat", stat, "minecraft:jump", IdKind.STAT, v -> stat = v);
        group.addLong("value", value, 100L, 1L, Integer.MAX_VALUE, v -> value = v);
    }
}
