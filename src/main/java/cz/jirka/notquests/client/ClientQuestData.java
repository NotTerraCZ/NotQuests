package cz.jirka.notquests.client;

import cz.jirka.notquests.client.gui.QuestScreen;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.QuestFile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client copy of the quest file and of the local player's team progress. */
public final class ClientQuestData {
    public record Member(UUID uuid, String name, boolean online) {
    }

    @Nullable
    public static QuestFile file;
    public static TeamData team = new TeamData();
    public static String teamName = "";
    public static boolean teamPersonal = true;
    public static UUID teamOwner = new UUID(0L, 0L);
    public static List<Member> members = new ArrayList<>();
    public static boolean editMode;
    /** Increased every time the quest file or team data changes, screens use it to refresh. */
    public static int version;
    public static final Map<String, List<String>> registryLists = new HashMap<>();

    private ClientQuestData() {
    }

    public static UUID self() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? new UUID(0L, 0L) : mc.player.getUUID();
    }

    public static boolean canEdit() {
        Minecraft mc = Minecraft.getInstance();
        return editMode && mc.player != null && mc.player.hasPermissions(2);
    }

    public static boolean hasPermission() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2);
    }

    public static HolderLookup.Provider registries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            return mc.getConnection().registryAccess();
        }
        return mc.level.registryAccess();
    }

    public static void openQuestScreen() {
        openQuestScreen(0L);
    }

    public static void openQuestScreen(long focus) {
        Minecraft mc = Minecraft.getInstance();
        if (file == null) {
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("notquests.message.not_loaded").withStyle(ChatFormatting.RED), false);
            }
            return;
        }
        mc.setScreen(new QuestScreen(focus));
    }

    public static void clear() {
        file = null;
        team = new TeamData();
        teamName = "";
        teamPersonal = true;
        members = new ArrayList<>();
        editMode = false;
        registryLists.clear();
        version++;
    }
}
