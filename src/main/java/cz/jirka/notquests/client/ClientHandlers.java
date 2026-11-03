package cz.jirka.notquests.client;

import com.mojang.logging.LogUtils;
import cz.jirka.notquests.client.gui.QuestToast;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Chapter;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestFileIO;
import cz.jirka.notquests.quest.QuestObjectBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Handles packets from the server. Only ever loaded on the client. */
public final class ClientHandlers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ClientHandlers() {
    }

    public static void syncQuests(NQNetwork.SyncQuests p) {
        try {
            CompoundTag tag = NQNetwork.decompress(p.data());
            ClientQuestData.file = QuestFileIO.fromNetwork(tag, ClientQuestData.registries());
            ClientQuestData.version++;
        } catch (Exception e) {
            LOGGER.error("Failed to read quest file from server", e);
        }
    }

    public static void syncTeam(NQNetwork.SyncTeam p) {
        CompoundTag tag = p.data();
        ClientQuestData.teamName = tag.getString("name");
        ClientQuestData.teamPersonal = tag.getBoolean("personal");
        if (tag.hasUUID("owner")) {
            ClientQuestData.teamOwner = tag.getUUID("owner");
        }
        List<ClientQuestData.Member> members = new ArrayList<>();
        ListTag list = tag.getList("members", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            members.add(new ClientQuestData.Member(m.getUUID("uuid"), m.getString("name"), m.getBoolean("online")));
        }
        ClientQuestData.members = members;
        ClientQuestData.team = TeamData.read(tag.getCompound("data"));
        ClientQuestData.version++;
    }

    public static void completed(NQNetwork.ObjectCompleted p) {
        QuestFile file = ClientQuestData.file;
        if (file == null) {
            return;
        }
        QuestObjectBase o = file.get(p.id());
        Minecraft mc = Minecraft.getInstance();
        if (o instanceof Quest q) {
            mc.getToasts().addToast(new QuestToast(Component.translatable("notquests.toast.quest_completed"), q.getTitle(), q, QuestToast.Kind.QUEST));
            if (ClientConfig.COMPLETION_SOUNDS.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6F, 0.6F));
            }
        } else if (o instanceof Chapter c) {
            mc.getToasts().addToast(new QuestToast(Component.translatable("notquests.toast.chapter_completed"), c.getTitle(), c, QuestToast.Kind.CHAPTER));
            if (ClientConfig.COMPLETION_SOUNDS.get()) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1F, 0.8F));
            }
        } else if (o instanceof QuestFile f) {
            mc.getToasts().addToast(new QuestToast(Component.translatable("notquests.toast.file_completed"), f.getTitle(), f, QuestToast.Kind.CHAPTER));
        }
    }

    public static void toast(NQNetwork.Toast p) {
        Minecraft.getInstance().getToasts().addToast(new QuestToast(p.title(), p.text(), p.icon()));
    }

    public static void editMode(NQNetwork.EditMode p) {
        ClientQuestData.editMode = p.editing();
        ClientQuestData.version++;
    }

    public static void openBook(NQNetwork.OpenBook p) {
        ClientQuestData.openQuestScreen(p.focus());
    }

    public static void registryList(NQNetwork.RegistryList p) {
        ClientQuestData.registryLists.put(p.kind(), p.ids());
        ClientQuestData.version++;
    }
}
