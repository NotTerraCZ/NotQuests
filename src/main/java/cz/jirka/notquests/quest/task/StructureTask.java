package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.server.ServerQuestManager;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

public class StructureTask extends Task {
    public String structure = "#minecraft:village";

    public StructureTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.STRUCTURE;
    }

    @Override
    public boolean checkOnTick() {
        return true;
    }

    @Override
    public void tickCheck(ServerQuestManager manager, Team team, ServerPlayer player) {
        ResourceLocation rl = TextUtil.rl(structure);
        if (rl == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (!level.isLoaded(pos)) {
            return;
        }
        StructureStart start;
        if (structure.startsWith("#")) {
            start = level.structureManager().getStructureWithPieceAt(pos, TagKey.create(Registries.STRUCTURE, rl));
        } else {
            Structure s = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(rl);
            if (s == null) {
                return;
            }
            start = level.structureManager().getStructureWithPieceAt(pos, s);
        }
        if (start.isValid()) {
            manager.setTaskProgress(team, this, 1L);
        }
    }

    @Override
    public Component getAltTitle() {
        String name = structure.startsWith("#")
                ? cz.jirka.notquests.util.TagNames.plural(Registries.STRUCTURE, structure)
                : TextUtil.prettyId(structure);
        return Component.translatable("notquests.task.structure.title", Component.literal(name));
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("structure", structure);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        structure = tag.contains("structure") ? tag.getString("structure") : "#minecraft:village";
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("structure", structure, "#minecraft:village", IdKind.STRUCTURE, v -> structure = v);
    }
}
