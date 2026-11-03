package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

public class BiomeTask extends Task {
    public String biome = "minecraft:plains";

    public BiomeTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.BIOME;
    }

    @Override
    public boolean checkOnTick() {
        return true;
    }

    @Override
    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
        ResourceLocation rl = TextUtil.rl(biome);
        if (rl == null) {
            return;
        }
        Holder<Biome> holder = player.level().getBiome(player.blockPosition());
        boolean ok = biome.startsWith("#") ? holder.is(TagKey.create(Registries.BIOME, rl)) : holder.is(ResourceKey.create(Registries.BIOME, rl));
        if (ok) {
            manager.setTaskProgress(team, this, 1L);
        }
    }

    public Component biomeName() {
        if (biome.startsWith("#")) {
            // "#minecraft:is_forest" -> "Forest"
            return Component.literal(cz.jirka.notquests.util.TagNames.plural(Registries.BIOME, biome));
        }
        ResourceLocation rl = TextUtil.rl(biome);
        if (rl == null) {
            return Component.literal(biome);
        }
        return Component.translatableWithFallback("biome." + rl.getNamespace() + "." + rl.getPath(), TextUtil.prettyId(biome));
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.task.biome.title", biomeName());
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("biome", biome);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        biome = tag.contains("biome") ? tag.getString("biome") : "minecraft:plains";
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("biome", biome, "minecraft:plains", IdKind.BIOME, v -> biome = v);
    }
}
