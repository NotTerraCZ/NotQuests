package cz.jirka.notquests.server;

import com.mojang.logging.LogUtils;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.net.NQNetwork;
import cz.jirka.notquests.progress.QuestSavedData;
import cz.jirka.notquests.progress.Team;
import cz.jirka.notquests.progress.TeamData;
import cz.jirka.notquests.quest.Chapter;
import cz.jirka.notquests.quest.ChapterGroup;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.quest.QuestFile;
import cz.jirka.notquests.quest.QuestFileIO;
import cz.jirka.notquests.quest.QuestObjectBase;
import cz.jirka.notquests.quest.QuestObjectType;
import cz.jirka.notquests.quest.RewardAutoClaim;
import cz.jirka.notquests.quest.RewardTable;
import cz.jirka.notquests.quest.reward.ChoiceReward;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.reward.RewardType;
import cz.jirka.notquests.quest.reward.RewardTypes;
import cz.jirka.notquests.quest.reward.TableReward;
import cz.jirka.notquests.quest.task.AdvancementTask;
import cz.jirka.notquests.quest.task.ItemTask;
import cz.jirka.notquests.quest.task.KillTask;
import cz.jirka.notquests.quest.task.ObservationTask;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.quest.task.TaskType;
import cz.jirka.notquests.quest.task.TaskTypes;
import cz.jirka.notquests.registry.ModItems;
import cz.jirka.notquests.util.IdUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Server side brain: owns the quest file and the progress data, checks tasks, gives rewards,
 * applies editor changes and keeps clients in sync.
 */
public class ServerQuestManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static ServerQuestManager instance;

    public final MinecraftServer server;
    public final Path folder;
    public QuestFile file = new QuestFile();
    public QuestSavedData data;
    private boolean readOnly;
    private byte[] cachedFileBytes;
    private final Set<UUID> dirtyTeams = new LinkedHashSet<>();
    private int ticks;

    private ServerQuestManager(MinecraftServer server) {
        this.server = server;
        this.folder = FMLPaths.CONFIGDIR.get().resolve("notquests").resolve("quests");
    }

    @Nullable
    public static ServerQuestManager get() {
        return instance;
    }

    public static void start(MinecraftServer server) {
        ServerQuestManager m = new ServerQuestManager(server);
        m.data = server.overworld().getDataStorage().computeIfAbsent(QuestSavedData.factory(), QuestSavedData.NAME);
        m.load();
        instance = m;
    }

    public static void stop() {
        instance = null;
    }

    public HolderLookup.Provider registries() {
        return server.registryAccess();
    }

    // ================================================================== loading & saving

    /** Loads the quest file from disk. Returns false if the files could not be read. */
    public boolean load() {
        readOnly = false;
        upgradeUntouchedDefaults();
        QuestFile f = null;
        try {
            f = QuestFileIO.load(folder, registries());
        } catch (Exception e) {
            LOGGER.error("Failed to load NotQuests quest file from {}", folder, e);
            if (Files.exists(folder.resolve("data.snbt"))) {
                readOnly = true;
            }
        }
        if (f == null) {
            if (readOnly) {
                f = new QuestFile();
                f.refresh();
            } else {
                LOGGER.info("No quest files found, writing the example quests to {}", folder);
                f = createDefaultQuests();
            }
        }
        file = f;
        cachedFileBytes = null;
        if (!readOnly && !Files.exists(folder.resolve("data.snbt"))) {
            // only chapter / reward table files were provided (e.g. copied from another instance)
            saveData();
        }
        if (!readOnly) {
            // only files where something needed a new id are written back, everything else stays as it was written
            List<String> saved = new ArrayList<>();
            if (f.dataNeedsSave) {
                f.dataNeedsSave = false;
                saveData();
                saved.add("data.snbt");
            }
            for (Chapter c : f.chapters) {
                if (c.needsSave) {
                    c.needsSave = false;
                    saveChapter(c);
                    saved.add("chapters/" + c.filename + ".snbt");
                }
            }
            for (RewardTable t : f.rewardTables) {
                if (t.needsSave) {
                    t.needsSave = false;
                    saveTable(t);
                    saved.add("reward_tables/" + t.filename + ".snbt");
                }
            }
            if (!saved.isEmpty()) {
                LOGGER.info("Gave ids to quest objects that had none (or a duplicate one) and saved {}", String.join(", ", saved));
            }
        }
        LOGGER.info("NotQuests loaded {} chapters, {} quests, {} reward tables", file.chapters.size(), file.allQuests.size(), file.rewardTables.size());
        return !readOnly;
    }

    /**
     * Example files written by older versions, by SHA-256 of their exact content. If such a file was never changed,
     * it is replaced with the current example; anything edited by the user is left alone.
     */
    private static final java.util.Map<String, String> OLD_DEFAULTS = java.util.Map.of(
            "reward_tables/starter_loot.snbt", "13e5b0bfd3a5909e7ee03dd97b9827e89b8755de7b3a69c6b302bceb26e9f28f" // 1.0.1, chest icon
    );

    private void upgradeUntouchedDefaults() {
        for (java.util.Map.Entry<String, String> e : OLD_DEFAULTS.entrySet()) {
            Path path = folder.resolve(e.getKey());
            try {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
                if (!e.getValue().equals(java.util.HexFormat.of().formatHex(digest))) {
                    continue;
                }
                try (java.io.InputStream in = ServerQuestManager.class.getResourceAsStream("/notquests/default_quests/" + e.getKey())) {
                    if (in != null) {
                        Files.copy(in, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        LOGGER.info("Updated the unchanged example file {} to the current version", e.getKey());
                    }
                }
            } catch (Exception ex) {
                LOGGER.warn("Could not update the example file {}", e.getKey(), ex);
            }
        }
    }

    /** The example quests from the jar - exactly the files shown on the mod page. */
    private static final String[] DEFAULT_FILES = {"data.snbt", "chapters/the_beginning.snbt", "reward_tables/starter_loot.snbt"};

    private QuestFile createDefaultQuests() {
        try {
            for (String name : DEFAULT_FILES) {
                try (java.io.InputStream in = ServerQuestManager.class.getResourceAsStream("/notquests/default_quests/" + name)) {
                    if (in == null) {
                        throw new IOException("Missing " + name + " in the NotQuests jar");
                    }
                    Path target = folder.resolve(name);
                    Files.createDirectories(target.getParent());
                    Files.copy(in, target);
                }
            }
            QuestFile f = QuestFileIO.load(folder, registries());
            if (f != null) {
                return f;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to write the example quests", e);
        }
        QuestFile f = new QuestFile();
        f.refresh();
        return f;
    }

    public void reload() {
        load();
        fileChanged();
        recheckOnline();
    }

    private void saveData() {
        if (readOnly) {
            return;
        }
        try {
            QuestFileIO.saveData(file, folder, registries());
        } catch (IOException e) {
            LOGGER.error("Failed to save quest file data", e);
        }
    }

    private void saveChapter(Chapter c) {
        if (readOnly) {
            return;
        }
        try {
            QuestFileIO.saveChapter(c, folder, registries());
        } catch (IOException e) {
            LOGGER.error("Failed to save chapter {}", c.filename, e);
        }
    }

    private void saveAllChapters() {
        for (Chapter c : file.chapters) {
            saveChapter(c);
        }
    }

    private void saveTable(RewardTable t) {
        if (readOnly) {
            return;
        }
        try {
            QuestFileIO.saveRewardTable(t, folder, registries());
        } catch (IOException e) {
            LOGGER.error("Failed to save reward table {}", t.filename, e);
        }
    }

    private void saveObject(QuestObjectBase o) {
        if (o instanceof QuestFile || o instanceof ChapterGroup) {
            saveData();
        } else if (o instanceof Chapter c) {
            saveChapter(c);
        } else if (o instanceof Quest q) {
            saveChapter(q.chapter);
        } else if (o instanceof Task t) {
            saveChapter(t.quest.chapter);
        } else if (o instanceof Reward r) {
            saveChapter(r.quest.chapter);
        } else if (o instanceof RewardTable t) {
            saveTable(t);
        }
    }

    // ================================================================== sync

    private byte[] fileBytes() {
        if (cachedFileBytes == null) {
            cachedFileBytes = NQNetwork.compress(QuestFileIO.toNetwork(file, registries()));
        }
        return cachedFileBytes;
    }

    public void sendFile(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new NQNetwork.SyncQuests(fileBytes()));
    }

    /** Call after any change to the quest file: rebuilds caches and resends it to everyone. */
    public void fileChanged() {
        file.refresh();
        cachedFileBytes = null;
        for (ServerPlayer p : players()) {
            sendFile(p);
        }
    }

    private CompoundTag teamTag(Team t) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", t.id);
        tag.putString("name", t.name);
        tag.putBoolean("personal", t.personal);
        tag.putUUID("owner", t.owner);
        ListTag members = new ListTag();
        for (UUID u : t.members) {
            CompoundTag m = new CompoundTag();
            m.putUUID("uuid", u);
            m.putString("name", data.nameOf(u));
            m.putBoolean("online", server.getPlayerList().getPlayer(u) != null);
            members.add(m);
        }
        tag.put("members", members);
        tag.put("data", t.data.write());
        return tag;
    }

    public void sendTeam(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new NQNetwork.SyncTeam(teamTag(getTeam(p))));
    }

    public void markDirty(Team t) {
        dirtyTeams.add(t.id);
        data.setDirty();
    }

    private void flushDirty() {
        if (dirtyTeams.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(dirtyTeams);
        dirtyTeams.clear();
        for (UUID id : ids) {
            Team t = data.teams.get(id);
            if (t == null) {
                continue;
            }
            List<ServerPlayer> online = onlineMembers(t);
            if (online.isEmpty()) {
                continue;
            }
            CompoundTag tag = teamTag(t);
            for (ServerPlayer p : online) {
                PacketDistributor.sendToPlayer(p, new NQNetwork.SyncTeam(tag));
            }
        }
    }

    // ================================================================== teams

    public Team getTeam(ServerPlayer p) {
        return data.getTeam(p.getUUID(), p.getGameProfile().getName());
    }

    public List<ServerPlayer> players() {
        return server.getPlayerList().getPlayers();
    }

    public List<ServerPlayer> onlineMembers(Team t) {
        List<ServerPlayer> list = new ArrayList<>();
        for (UUID u : t.members) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) {
                list.add(p);
            }
        }
        return list;
    }

    private Collection<Team> onlineTeams() {
        Set<Team> set = new LinkedHashSet<>();
        for (ServerPlayer p : players()) {
            set.add(getTeam(p));
        }
        return set;
    }

    public void onLogin(ServerPlayer p) {
        data.playerNames.put(p.getUUID(), p.getGameProfile().getName());
        data.setDirty();
        Team team = getTeam(p);
        if (team.personal) {
            team.name = p.getGameProfile().getName();
        }
        sendFile(p);
        PacketDistributor.sendToPlayer(p, new NQNetwork.EditMode(canEdit(p)));
        if (file.giveBookOnFirstJoin && data.bookGiven.add(p.getUUID())) {
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModItems.BOOK.get()));
            data.setDirty();
        }
        recheckTeam(team);
        for (Quest q : file.allQuests) {
            if (team.data.isCompleted(q)) {
                autoClaimFor(p, team, q);
            }
        }
        markDirty(team);
        flushDirty();
    }

    public void onLogout(ServerPlayer p) {
        Team team = getTeam(p);
        markDirty(team);
    }

    // ================================================================== progress

    public void setTaskProgress(Team team, Task task, long progress) {
        long max = task.getMaxProgress();
        long value = Math.max(0L, Math.min(progress, max));
        TeamData d = team.data;
        long old = d.getProgress(task);
        if (old == value) {
            return;
        }
        d.setProgress(task, value);
        markDirty(team);
        if (old <= 0L && value > 0L) {
            for (Quest dependent : new ArrayList<>(file.getDependents(task.quest.id))) {
                checkQuestCompletion(team, dependent);
            }
        }
        if (value >= max) {
            checkQuestCompletion(team, task.quest);
        }
    }

    public void addTaskProgress(Team team, Task task, long delta) {
        setTaskProgress(team, task, team.data.getProgress(task) + delta);
    }

    public void checkQuestCompletion(Team team, Quest quest) {
        TeamData d = team.data;
        if (d.isCompleted(quest) || !d.areDependenciesComplete(quest)) {
            return;
        }
        for (Task t : quest.tasks) {
            if (!t.optional && !d.isComplete(t)) {
                return;
            }
        }
        completeQuest(team, quest, true);
    }

    private void completeQuest(Team team, Quest quest, boolean notify) {
        TeamData d = team.data;
        if (d.isCompleted(quest)) {
            return;
        }
        d.setCompleted(quest.id, System.currentTimeMillis());
        markDirty(team);
        List<ServerPlayer> online = onlineMembers(team);
        if (notify && !quest.disableToast) {
            for (ServerPlayer p : online) {
                PacketDistributor.sendToPlayer(p, new NQNetwork.ObjectCompleted(quest.id));
            }
        }
        for (ServerPlayer p : online) {
            autoClaimFor(p, team, quest);
        }
        checkChapterCompletion(team, quest.chapter, notify);
        for (Quest dependent : new ArrayList<>(file.getDependents(quest.id))) {
            checkQuestCompletion(team, dependent);
        }
    }

    private void checkChapterCompletion(Team team, Chapter c, boolean notify) {
        TeamData d = team.data;
        if (d.isCompleted(c) || !isChapterDone(d, c)) {
            return;
        }
        long now = System.currentTimeMillis();
        d.setCompleted(c.id, now);
        markDirty(team);
        if (notify) {
            for (ServerPlayer p : onlineMembers(team)) {
                PacketDistributor.sendToPlayer(p, new NQNetwork.ObjectCompleted(c.id));
            }
        }
        for (Quest dependent : new ArrayList<>(file.getDependents(c.id))) {
            checkQuestCompletion(team, dependent);
        }
        if (!d.isCompleted(QuestFile.FILE_ID)) {
            boolean any = false;
            for (Chapter ch : file.chapters) {
                if (hasRequiredQuests(ch)) {
                    any = true;
                    if (!d.isCompleted(ch)) {
                        return;
                    }
                }
            }
            if (any) {
                d.setCompleted(QuestFile.FILE_ID, now);
                if (notify) {
                    for (ServerPlayer p : onlineMembers(team)) {
                        PacketDistributor.sendToPlayer(p, new NQNetwork.ObjectCompleted(QuestFile.FILE_ID));
                    }
                }
            }
        }
    }

    private static boolean hasRequiredQuests(Chapter c) {
        for (Quest q : c.quests) {
            if (!q.optional) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChapterDone(TeamData d, Chapter c) {
        boolean any = false;
        for (Quest q : c.quests) {
            if (q.optional) {
                continue;
            }
            any = true;
            if (!d.isCompleted(q)) {
                return false;
            }
        }
        return any;
    }

    public void recheckTeam(Team team) {
        for (Quest q : new ArrayList<>(file.allQuests)) {
            checkQuestCompletion(team, q);
        }
        for (Chapter c : file.chapters) {
            checkChapterCompletion(team, c, false);
        }
    }

    public void recheckOnline() {
        for (Team t : onlineTeams()) {
            recheckTeam(t);
        }
    }

    // ================================================================== rewards

    public void autoClaimFor(ServerPlayer p, Team team, Quest quest) {
        for (Reward r : new ArrayList<>(quest.rewards)) {
            RewardAutoClaim mode = r.getAutoClaim();
            if (!mode.isAuto() || r.needsChoice() || team.data.isRewardClaimed(p.getUUID(), r)) {
                continue;
            }
            claimReward(p, team, r, -1, mode == RewardAutoClaim.ENABLED);
        }
    }

    public boolean claimReward(ServerPlayer p, Team team, Reward r, int choice, boolean notify) {
        TeamData d = team.data;
        if (!d.isCompleted(r.quest) || d.isRewardClaimed(p.getUUID(), r)) {
            return false;
        }
        if (r instanceof ChoiceReward cr && !cr.isValidChoice(choice)) {
            return false;
        }
        d.setRewardClaimed(p.getUUID(), r);
        markDirty(team);
        try {
            r.claim(p, notify, choice);
        } catch (Exception e) {
            LOGGER.error("Failed to give reward {} to {}", r, p.getGameProfile().getName(), e);
        }
        if (r.quest.canRepeat && d.areAllRewardsClaimed(p.getUUID(), r.quest)) {
            resetQuest(d, r.quest);
            markDirty(team);
        }
        return true;
    }

    private static void resetQuest(TeamData d, Quest q) {
        for (Task t : q.tasks) {
            d.setProgress(t, 0L);
        }
        d.removeCompleted(q.id);
        for (Reward r : q.rewards) {
            d.unclaim(r.id);
        }
    }

    // ================================================================== forced progress (commands / editor)

    public void forceComplete(Team team, QuestObjectBase o) {
        TeamData d = team.data;
        if (o instanceof QuestFile f) {
            for (Chapter c : f.chapters) {
                forceComplete(team, c);
            }
        } else if (o instanceof Chapter c) {
            for (Quest q : c.quests) {
                forceComplete(team, q);
            }
        } else if (o instanceof Quest q) {
            for (Task t : q.tasks) {
                d.setProgress(t, t.getMaxProgress());
            }
            markDirty(team);
            completeQuest(team, q, true);
        } else if (o instanceof Task t) {
            setTaskProgress(team, t, t.getMaxProgress());
        }
        markDirty(team);
    }

    public void forceReset(Team team, QuestObjectBase o) {
        TeamData d = team.data;
        if (o instanceof QuestFile) {
            d.taskProgress.clear();
            d.completed.clear();
            d.claimed.clear();
        } else if (o instanceof Chapter c) {
            for (Quest q : c.quests) {
                resetQuest(d, q);
            }
            d.removeCompleted(c.id);
            d.removeCompleted(QuestFile.FILE_ID);
        } else if (o instanceof Quest q) {
            resetQuest(d, q);
            d.removeCompleted(q.chapter.id);
            d.removeCompleted(QuestFile.FILE_ID);
        } else if (o instanceof Task t) {
            d.setProgress(t, 0L);
            if (d.isCompleted(t.quest)) {
                d.removeCompleted(t.quest.id);
                for (Reward r : t.quest.rewards) {
                    d.unclaim(r.id);
                }
                d.removeCompleted(t.quest.chapter.id);
                d.removeCompleted(QuestFile.FILE_ID);
            }
        }
        markDirty(team);
    }

    // ================================================================== ticking & events

    public void tick() {
        ticks++;
        if (ticks % 10 == 0) {
            for (ServerPlayer p : players()) {
                if (p.isSpectator()) {
                    continue;
                }
                Team team = getTeam(p);
                detectItems(p, team);
                if (ticks % 20 == 0) {
                    tickTasks(p, team);
                }
            }
        }
        flushDirty();
    }

    private void detectItems(ServerPlayer p, Team team) {
        TeamData d = team.data;
        for (ItemTask t : file.detectItemTasks) {
            if (d.isComplete(t) || !d.canStartTasks(t.quest)) {
                continue;
            }
            long count = t.countItems(p);
            if (count > d.getProgress(t)) {
                setTaskProgress(team, t, Math.min(count, t.count));
            }
        }
    }

    private void tickTasks(ServerPlayer p, Team team) {
        TeamData d = team.data;
        for (Task t : file.tickTasks) {
            if (d.isComplete(t) || !d.canStartTasks(t.quest)) {
                continue;
            }
            t.tickCheck(this, team, p);
        }
    }

    public void onKill(ServerPlayer p, LivingEntity victim) {
        Team team = getTeam(p);
        TeamData d = team.data;
        for (Task t : file.allTasks) {
            if (t instanceof KillTask kt && !d.isComplete(kt) && d.canStartTasks(kt.quest) && kt.matches(victim)) {
                addTaskProgress(team, kt, 1L);
            }
        }
    }

    public void onCraft(ServerPlayer p, ItemStack crafted) {
        if (crafted.isEmpty()) {
            return;
        }
        Team team = getTeam(p);
        TeamData d = team.data;
        for (Task t : file.allTasks) {
            if (t instanceof ItemTask it && it.onlyFromCrafting && !d.isComplete(it) && d.canStartTasks(it.quest) && it.matches(crafted)) {
                addTaskProgress(team, it, crafted.getCount());
            }
        }
    }

    public void onAdvancement(ServerPlayer p) {
        Team team = getTeam(p);
        TeamData d = team.data;
        for (Task t : file.allTasks) {
            if (t instanceof AdvancementTask at && !d.isComplete(at) && d.canStartTasks(at.quest)) {
                at.tickCheck(this, team, p);
            }
        }
    }

    // ================================================================== player actions

    public void handleSubmit(ServerPlayer p, long taskId) {
        Task t = file.getTask(taskId);
        if (t == null || !t.isSubmittable()) {
            return;
        }
        Team team = getTeam(p);
        if (team.data.isComplete(t) || !team.data.canStartTasks(t.quest)) {
            return;
        }
        t.submit(this, team, p);
    }

    public void handleClaim(ServerPlayer p, long rewardId, int choice) {
        Reward r = file.getReward(rewardId);
        if (r != null) {
            claimReward(p, getTeam(p), r, choice, true);
        }
    }

    public void handleClaimAll(ServerPlayer p, long scope) {
        Team team = getTeam(p);
        QuestObjectBase scopeObj = scope == 0L ? file : file.get(scope);
        if (scopeObj == null) {
            return;
        }
        for (Reward r : new ArrayList<>(file.allRewards)) {
            if (r.needsChoice() || r.excludeFromClaimAll) {
                continue;
            }
            if (scopeObj instanceof Chapter c && r.quest.chapter != c || scopeObj instanceof Quest q && r.quest != q) {
                continue;
            }
            claimReward(p, team, r, -1, true);
        }
    }

    public void handleObserved(ServerPlayer p, long taskId) {
        if (!(file.getTask(taskId) instanceof ObservationTask t)) {
            return;
        }
        Team team = getTeam(p);
        if (!team.data.isComplete(t) && team.data.canStartTasks(t.quest)) {
            setTaskProgress(team, t, 1L);
        }
    }

    public void handlePin(ServerPlayer p, long questId, boolean pin) {
        if (pin && file.getQuest(questId) == null) {
            return;
        }
        Team team = getTeam(p);
        team.data.setPinned(p.getUUID(), questId, pin);
        markDirty(team);
    }

    // ================================================================== editing

    public boolean canEdit(ServerPlayer p) {
        return p.hasPermissions(2) && data.editing.contains(p.getUUID());
    }

    public void setEditMode(ServerPlayer p, boolean edit) {
        if (edit && !p.hasPermissions(2)) {
            p.sendSystemMessage(Component.translatable("notquests.message.no_permission").withStyle(ChatFormatting.RED));
            return;
        }
        if (edit) {
            data.editing.add(p.getUUID());
        } else {
            data.editing.remove(p.getUUID());
        }
        data.setDirty();
        PacketDistributor.sendToPlayer(p, new NQNetwork.EditMode(edit));
    }

    private boolean checkEdit(ServerPlayer p) {
        if (!canEdit(p)) {
            p.sendSystemMessage(Component.translatable("notquests.message.not_editing").withStyle(ChatFormatting.RED));
            return false;
        }
        if (readOnly) {
            p.sendSystemMessage(Component.translatable("notquests.message.read_only").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    public void handleEditObject(ServerPlayer p, long id, CompoundTag tag) {
        if (!checkEdit(p)) {
            return;
        }
        QuestObjectBase o = file.get(id);
        if (o == null) {
            return;
        }
        o.readData(tag, registries());
        o.id = id;
        if (o instanceof Quest q) {
            sanitizeDependencies(q);
        }
        saveObject(o);
        fileChanged();
        recheckOnline();
    }

    private void sanitizeDependencies(Quest q) {
        for (int i = q.dependencies.size() - 1; i >= 0; i--) {
            long d = q.dependencies.getLong(i);
            QuestObjectBase dep = file.get(d);
            if (d == q.id || dep == null || dep instanceof Quest dq && dq.dependsOn(q)) {
                q.dependencies.removeLong(i);
            }
        }
    }

    public void handleCreate(ServerPlayer p, int typeIndex, long parentId, String subType, CompoundTag tag) {
        if (!checkEdit(p)) {
            return;
        }
        HolderLookup.Provider reg = registries();
        long newId = IdUtil.parseFileId(tag.getString("id"));
        if (newId == 0L || newId == QuestFile.FILE_ID || file.contains(newId)) {
            newId = file.newId();
        }
        QuestObjectType type = QuestObjectType.byIndex(typeIndex);
        switch (type) {
            case CHAPTER_GROUP -> {
                ChapterGroup g = new ChapterGroup(file);
                g.id = newId;
                g.readData(tag, reg);
                file.chapterGroups.add(g);
                saveData();
            }
            case CHAPTER -> {
                Chapter c = new Chapter(file);
                c.id = newId;
                c.readData(tag, reg);
                c.filename = "";
                file.chapters.add(c);
                QuestFileIO.ensureFilename(file, c, folder);
                saveChapter(c);
            }
            case QUEST -> {
                Chapter c = file.getChapter(parentId);
                if (c == null) {
                    return;
                }
                Quest q = new Quest(c);
                q.id = newId;
                q.readData(tag, reg);
                QuestFileIO.readQuestChildren(q, tag, reg);
                Set<Long> used = new HashSet<>();
                used.add(newId);
                for (Task t : q.tasks) {
                    if (t.id == 0L || file.contains(t.id) || !used.add(t.id)) {
                        t.id = file.newId();
                        used.add(t.id);
                    }
                }
                for (Reward r : q.rewards) {
                    if (r.id == 0L || file.contains(r.id) || !used.add(r.id)) {
                        r.id = file.newId();
                        used.add(r.id);
                    }
                }
                c.quests.add(q);
                sanitizeDependencies(q);
                saveChapter(c);
            }
            case TASK -> {
                Quest q = file.getQuest(parentId);
                TaskType tt = TaskTypes.get(subType);
                if (q == null || tt == null) {
                    return;
                }
                Task t = tt.create(q);
                t.id = newId;
                t.readData(tag, reg);
                q.tasks.add(t);
                saveChapter(q.chapter);
            }
            case REWARD -> {
                Quest q = file.getQuest(parentId);
                RewardType rt = RewardTypes.get(subType);
                if (q == null || rt == null) {
                    return;
                }
                Reward r = rt.create(q);
                r.id = newId;
                r.readData(tag, reg);
                q.rewards.add(r);
                saveChapter(q.chapter);
            }
            case REWARD_TABLE -> {
                RewardTable t = new RewardTable(file);
                t.id = newId;
                t.readData(tag, reg);
                t.filename = "";
                file.rewardTables.add(t);
                QuestFileIO.ensureFilename(file, t, folder);
                saveTable(t);
            }
            default -> {
                return;
            }
        }
        fileChanged();
        recheckOnline();
    }

    public void handleMove(ServerPlayer p, long[] ids, double[] xs, double[] ys) {
        if (!checkEdit(p)) {
            return;
        }
        Set<Chapter> changed = new LinkedHashSet<>();
        for (int i = 0; i < ids.length; i++) {
            Quest q = file.getQuest(ids[i]);
            if (q != null && Double.isFinite(xs[i]) && Double.isFinite(ys[i])) {
                q.x = xs[i];
                q.y = ys[i];
                changed.add(q.chapter);
            }
        }
        for (Chapter c : changed) {
            saveChapter(c);
        }
        if (!changed.isEmpty()) {
            fileChanged();
        }
    }

    public void handleAction(ServerPlayer p, int action, long id, long arg) {
        if (action == NQNetwork.Actions.SET_EDIT_MODE) {
            setEditMode(p, arg != 0L);
            return;
        }
        if (!checkEdit(p)) {
            return;
        }
        QuestObjectBase o = file.get(id);
        if (o == null) {
            return;
        }
        switch (action) {
            case NQNetwork.Actions.DELETE -> delete(o);
            case NQNetwork.Actions.MOVE_UP -> move(o, true);
            case NQNetwork.Actions.MOVE_DOWN -> move(o, false);
            case NQNetwork.Actions.MOVE_QUEST_TO_CHAPTER -> {
                Chapter target = file.getChapter(arg);
                if (o instanceof Quest q && target != null && q.chapter != target) {
                    Chapter old = q.chapter;
                    old.quests.remove(q);
                    q.chapter = target;
                    target.quests.add(q);
                    saveChapter(old);
                    saveChapter(target);
                    fileChanged();
                }
            }
            case NQNetwork.Actions.COMPLETE -> forceComplete(getTeam(p), o);
            case NQNetwork.Actions.RESET -> forceReset(getTeam(p), o);
            case NQNetwork.Actions.ADD_DEPENDENCY -> {
                QuestObjectBase dep = file.get(arg);
                if (o instanceof Quest q && (dep instanceof Quest || dep instanceof Chapter) && arg != id && !q.dependencies.contains(arg)) {
                    if (dep instanceof Quest dq && dq.dependsOn(q) || dep instanceof Chapter dc && q.isNeededFor(dc)) {
                        p.sendSystemMessage(Component.translatable("notquests.message.dependency_loop").withStyle(ChatFormatting.RED));
                        return;
                    }
                    q.dependencies.add(arg);
                    saveChapter(q.chapter);
                    fileChanged();
                    recheckOnline();
                }
            }
            case NQNetwork.Actions.REMOVE_DEPENDENCY -> {
                if (o instanceof Quest q && q.dependencies.rem(arg)) {
                    saveChapter(q.chapter);
                    fileChanged();
                    recheckOnline();
                }
            }
            case NQNetwork.Actions.CLEAR_DEPENDENCIES -> {
                if (o instanceof Quest q && !q.dependencies.isEmpty()) {
                    q.dependencies.clear();
                    saveChapter(q.chapter);
                    fileChanged();
                    recheckOnline();
                }
            }
            default -> {
            }
        }
    }

    private void delete(QuestObjectBase o) {
        if (o instanceof QuestFile) {
            return;
        }
        if (o instanceof ChapterGroup g) {
            file.chapterGroups.remove(g);
            for (Chapter c : file.chapters) {
                if (c.group == g.id) {
                    c.group = 0L;
                    saveChapter(c);
                }
            }
            saveData();
        } else if (o instanceof Chapter c) {
            file.chapters.remove(c);
            try {
                QuestFileIO.deleteChapterFile(c, folder);
            } catch (IOException e) {
                LOGGER.error("Failed to delete chapter file", e);
            }
            Set<Long> removed = new HashSet<>();
            removed.add(c.id);
            for (Quest q : c.quests) {
                removed.add(q.id);
            }
            removeDependenciesTo(removed);
            saveAllChapters();
        } else if (o instanceof Quest q) {
            q.chapter.quests.remove(q);
            saveChapter(q.chapter);
            removeDependenciesTo(Set.of(q.id));
        } else if (o instanceof Task t) {
            t.quest.tasks.remove(t);
            saveChapter(t.quest.chapter);
        } else if (o instanceof Reward r) {
            r.quest.rewards.remove(r);
            saveChapter(r.quest.chapter);
        } else if (o instanceof RewardTable t) {
            file.rewardTables.remove(t);
            try {
                QuestFileIO.deleteRewardTableFile(t, folder);
            } catch (IOException e) {
                LOGGER.error("Failed to delete reward table file", e);
            }
            Set<Chapter> changed = new LinkedHashSet<>();
            for (Reward r : file.allRewards) {
                if (r instanceof TableReward tr && tr.table == t.id) {
                    tr.table = 0L;
                    changed.add(r.quest.chapter);
                }
            }
            for (Chapter c : changed) {
                saveChapter(c);
            }
        }
        fileChanged();
        recheckOnline();
    }

    private void removeDependenciesTo(Set<Long> ids) {
        for (Chapter c : file.chapters) {
            boolean changed = false;
            for (Quest q : c.quests) {
                for (int i = q.dependencies.size() - 1; i >= 0; i--) {
                    if (ids.contains(q.dependencies.getLong(i))) {
                        q.dependencies.removeLong(i);
                        changed = true;
                    }
                }
            }
            if (changed) {
                saveChapter(c);
            }
        }
    }

    private void move(QuestObjectBase o, boolean up) {
        if (o instanceof Chapter c) {
            List<Chapter> list = file.chapters;
            int i = list.indexOf(c);
            long group = file.getGroup(c.group) == null ? 0L : c.group;
            int j = i;
            do {
                j += up ? -1 : 1;
            } while (j >= 0 && j < list.size() && (file.getGroup(list.get(j).group) == null ? 0L : list.get(j).group) != group);
            if (j < 0 || j >= list.size()) {
                return;
            }
            Collections.swap(list, i, j);
            saveAllChapters();
        } else if (o instanceof ChapterGroup g) {
            if (!swap(file.chapterGroups, g, up)) {
                return;
            }
            saveData();
        } else if (o instanceof Task t) {
            if (!swap(t.quest.tasks, t, up)) {
                return;
            }
            saveChapter(t.quest.chapter);
        } else if (o instanceof Reward r) {
            if (!swap(r.quest.rewards, r, up)) {
                return;
            }
            saveChapter(r.quest.chapter);
        } else {
            return;
        }
        fileChanged();
    }

    private static <T> boolean swap(List<T> list, T element, boolean up) {
        int i = list.indexOf(element);
        int j = i + (up ? -1 : 1);
        if (i < 0 || j < 0 || j >= list.size()) {
            return false;
        }
        Collections.swap(list, i, j);
        return true;
    }

    public void handleRegistryRequest(ServerPlayer p, String kind) {
        if (!p.hasPermissions(2)) {
            return;
        }
        List<String> ids = new ArrayList<>();
        switch (IdKind.byName(kind)) {
            case STRUCTURE -> {
                Registry<Structure> reg = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
                for (ResourceLocation rl : reg.keySet()) {
                    ids.add(rl.toString());
                }
                reg.getTagNames().forEach(t -> ids.add("#" + t.location()));
            }
            case ADVANCEMENT -> {
                for (AdvancementHolder h : server.getAdvancements().getAllAdvancements()) {
                    if (!h.id().getPath().startsWith("recipes/")) {
                        ids.add(h.id().toString());
                    }
                }
            }
            case LOOT_TABLE -> {
                for (ResourceLocation rl : server.reloadableRegistries().getKeys(Registries.LOOT_TABLE)) {
                    ids.add(rl.toString());
                }
            }
            default -> {
            }
        }
        Collections.sort(ids);
        PacketDistributor.sendToPlayer(p, new NQNetwork.RegistryList(kind, ids));
    }

    // ================================================================== team commands

    public Component createTeam(ServerPlayer p, String name) {
        Team current = getTeam(p);
        if (!current.personal) {
            return err("notquests.team.already_in_team");
        }
        if (name.isBlank() || name.length() > 32) {
            return err("notquests.team.invalid_name");
        }
        if (data.findTeamByName(name) != null) {
            return err("notquests.team.name_taken", name);
        }
        Team t = new Team(UUID.randomUUID(), name, false, p.getUUID());
        t.members.add(p.getUUID());
        t.data = current.data.copy();
        data.teams.put(t.id, t);
        data.playerTeams.put(p.getUUID(), t.id);
        markDirty(t);
        return ok("notquests.team.created", name);
    }

    public Component invite(ServerPlayer p, ServerPlayer target) {
        Team t = getTeam(p);
        if (t.personal) {
            return err("notquests.team.not_in_team");
        }
        if (!t.isOwner(p.getUUID())) {
            return err("notquests.team.not_owner");
        }
        if (t.members.contains(target.getUUID())) {
            return err("notquests.team.already_member", target.getGameProfile().getName());
        }
        t.invites.add(target.getUUID());
        data.setDirty();
        Style click = Style.EMPTY.withColor(ChatFormatting.GREEN).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/notquests team join " + t.name))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/notquests team join " + t.name)));
        target.sendSystemMessage(Component.translatable("notquests.team.invited", p.getGameProfile().getName(), t.name)
                .append(" ").append(Component.translatable("notquests.team.click_to_join").withStyle(click)));
        return ok("notquests.team.invite_sent", target.getGameProfile().getName());
    }

    public Component join(ServerPlayer p, String name) {
        Team t = data.findTeamByName(name);
        if (t == null) {
            return err("notquests.team.not_found", name);
        }
        if (t.members.contains(p.getUUID())) {
            return err("notquests.team.already_member", p.getGameProfile().getName());
        }
        if (!t.invites.contains(p.getUUID())) {
            return err("notquests.team.not_invited", t.name);
        }
        Team current = getTeam(p);
        if (!current.personal) {
            return err("notquests.team.already_in_team");
        }
        t.invites.remove(p.getUUID());
        t.members.add(p.getUUID());
        data.playerTeams.put(p.getUUID(), t.id);
        markDirty(t);
        markDirty(current);
        for (ServerPlayer m : onlineMembers(t)) {
            if (m != p) {
                m.sendSystemMessage(Component.translatable("notquests.team.member_joined", p.getGameProfile().getName()).withStyle(ChatFormatting.GREEN));
            }
        }
        recheckTeam(t);
        return ok("notquests.team.joined", t.name);
    }

    public Component leave(ServerPlayer p) {
        Team t = getTeam(p);
        if (t.personal) {
            return err("notquests.team.not_in_team");
        }
        removeMember(t, p.getUUID());
        for (ServerPlayer m : onlineMembers(t)) {
            m.sendSystemMessage(Component.translatable("notquests.team.member_left", p.getGameProfile().getName()).withStyle(ChatFormatting.YELLOW));
        }
        return ok("notquests.team.left", t.name);
    }

    public Component kick(ServerPlayer p, UUID target, String targetName) {
        Team t = getTeam(p);
        if (t.personal) {
            return err("notquests.team.not_in_team");
        }
        if (!t.isOwner(p.getUUID())) {
            return err("notquests.team.not_owner");
        }
        if (!t.members.contains(target) || target.equals(p.getUUID())) {
            return err("notquests.team.not_member", targetName);
        }
        removeMember(t, target);
        ServerPlayer tp = server.getPlayerList().getPlayer(target);
        if (tp != null) {
            tp.sendSystemMessage(Component.translatable("notquests.team.kicked", t.name).withStyle(ChatFormatting.RED));
        }
        return ok("notquests.team.kicked_other", targetName);
    }

    private void removeMember(Team t, UUID player) {
        t.members.remove(player);
        Team personal = data.personalTeam(player, data.nameOf(player));
        personal.members.add(player);
        data.playerTeams.put(player, personal.id);
        if (t.members.isEmpty()) {
            data.teams.remove(t.id);
        } else if (t.isOwner(player)) {
            t.owner = t.members.iterator().next();
        }
        markDirty(t);
        markDirty(personal);
        data.setDirty();
    }

    public Component rename(ServerPlayer p, String name) {
        Team t = getTeam(p);
        if (t.personal) {
            return err("notquests.team.not_in_team");
        }
        if (!t.isOwner(p.getUUID())) {
            return err("notquests.team.not_owner");
        }
        if (name.isBlank() || name.length() > 32) {
            return err("notquests.team.invalid_name");
        }
        Team other = data.findTeamByName(name);
        if (other != null && other != t) {
            return err("notquests.team.name_taken", name);
        }
        t.name = name;
        markDirty(t);
        return ok("notquests.team.renamed", name);
    }

    public Component info(ServerPlayer p) {
        Team t = getTeam(p);
        int done = 0;
        for (Quest q : file.allQuests) {
            if (t.data.isCompleted(q)) {
                done++;
            }
        }
        StringBuilder members = new StringBuilder();
        for (UUID u : t.members) {
            if (!members.isEmpty()) {
                members.append(", ");
            }
            members.append(data.nameOf(u));
            if (t.isOwner(u) && !t.personal) {
                members.append(" (owner)");
            }
        }
        return Component.translatable("notquests.team.info", t.name, members.toString(), done, file.allQuests.size()).withStyle(ChatFormatting.AQUA);
    }

    public List<String> partyTeamNames() {
        List<String> list = new ArrayList<>();
        for (Team t : data.teams.values()) {
            if (!t.personal) {
                list.add(t.name);
            }
        }
        return list;
    }

    private static Component ok(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.GREEN);
    }

    private static Component err(String key, Object... args) {
        return Component.translatable(key, args).withStyle(ChatFormatting.RED);
    }

    public static boolean isError(Component c) {
        return c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == 0xFF5555;
    }
}
