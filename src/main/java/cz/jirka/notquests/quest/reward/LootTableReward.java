package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Rolls a vanilla (datapack) loot table. */
public class LootTableReward extends Reward {
    public String lootTable = "minecraft:chests/simple_dungeon";

    public LootTableReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.LOOT_TABLE;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        ResourceLocation rl = TextUtil.rl(lootTable);
        if (rl == null) {
            return;
        }
        LootTable table = player.server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, rl));
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withLuck(player.getLuck())
                .create(LootContextParamSets.CHEST);
        for (ItemStack stack : table.getRandomItems(params)) {
            if (!stack.isEmpty()) {
                int count = stack.getCount();
                giveItem(player, stack, count);
                if (notify) {
                    notifyItem(player, stack, count);
                }
            }
        }
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.loot_table.title", TextUtil.prettyId(lootTable));
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("loot_table", lootTable);
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        lootTable = tag.contains("loot_table") ? tag.getString("loot_table") : "minecraft:chests/simple_dungeon";
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("loot_table", lootTable, "minecraft:chests/simple_dungeon", IdKind.LOOT_TABLE, v -> lootTable = v);
    }
}
