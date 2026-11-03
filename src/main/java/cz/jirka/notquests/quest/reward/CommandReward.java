package cz.jirka.notquests.quest.reward;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.quest.Quest;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Runs a command when claimed. Placeholders: {p} player name, {uuid}, {x} {y} {z} position,
 * {quest} quest id, {chapter} chapter id.
 */
public class CommandReward extends Reward {
    public String command = "/say Hello {p}!";
    public boolean elevatePerms = true;
    public boolean silent = false;

    public CommandReward(Quest quest) {
        super(quest);
    }

    @Override
    public RewardType getType() {
        return RewardTypes.COMMAND;
    }

    @Override
    public void claim(ServerPlayer player, boolean notify, int choice) {
        BlockPos pos = player.blockPosition();
        String cmd = command
                .replace("{p}", player.getGameProfile().getName())
                .replace("{uuid}", player.getStringUUID())
                .replace("{x}", Integer.toString(pos.getX()))
                .replace("{y}", Integer.toString(pos.getY()))
                .replace("{z}", Integer.toString(pos.getZ()))
                .replace("{quest}", quest.getCodeString())
                .replace("{chapter}", quest.chapter.getCodeString());
        if (cmd.startsWith("/")) {
            cmd = cmd.substring(1);
        }
        CommandSourceStack source = player.createCommandSourceStack();
        if (elevatePerms) {
            source = source.withPermission(4);
        }
        if (silent) {
            source = source.withSuppressedOutput();
        }
        player.server.getCommands().performPrefixedCommand(source, cmd);
    }

    @Override
    public Component getAltTitle() {
        return Component.translatable("notquests.reward.command.title");
    }

    @Override
    public void addTooltip(List<Component> list, boolean editMode) {
        if (editMode) {
            list.add(Component.literal(command).withStyle(ChatFormatting.DARK_GRAY));
        }
        super.addTooltip(list, editMode);
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("command", command);
        if (!elevatePerms) {
            tag.putBoolean("elevate_perms", false); // true is the default
        }
        if (silent) {
            tag.putBoolean("silent", true);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        command = tag.getString("command");
        elevatePerms = !tag.contains("elevate_perms") || tag.getBoolean("elevate_perms");
        silent = tag.getBoolean("silent");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addString("command", command, "/say Hello {p}!", v -> command = v);
        group.addBool("elevate_perms", elevatePerms, true, v -> elevatePerms = v);
        group.addBool("silent", silent, false, v -> silent = v);
    }
}
