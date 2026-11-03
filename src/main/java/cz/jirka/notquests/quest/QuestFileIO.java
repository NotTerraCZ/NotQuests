package cz.jirka.notquests.quest;

import com.mojang.logging.LogUtils;
import cz.jirka.notquests.quest.reward.Reward;
import cz.jirka.notquests.quest.reward.RewardType;
import cz.jirka.notquests.quest.reward.RewardTypes;
import cz.jirka.notquests.quest.task.Task;
import cz.jirka.notquests.quest.task.TaskType;
import cz.jirka.notquests.quest.task.TaskTypes;
import cz.jirka.notquests.util.IdUtil;
import cz.jirka.notquests.util.SNBT;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reading and writing quest files. On disk the layout follows FTB Quests:
 * <pre>
 * config/notquests/quests/data.snbt
 * config/notquests/quests/chapters/&lt;chapter&gt;.snbt
 * config/notquests/quests/reward_tables/&lt;table&gt;.snbt
 * </pre>
 */
public final class QuestFileIO {
    private static final Logger LOGGER = LogUtils.getLogger();

    private QuestFileIO() {
    }

    // ------------------------------------------------------------------ objects with children

    // ------------------------------------------------------------------ ids of tasks and rewards
    //
    // Tasks and rewards written by hand usually have no id. Instead of inventing a random one (which would
    // force NotQuests to rewrite the author's file), they get an id derived from the quest id and their own
    // content. When a file is saved, ids that can be derived again are left out, so files stay clean.
    //
    // !!! NEVER CHANGE derivedId(), canonical(), canonicalValue(), TASK_META or REWARD_META (or IdUtil.hash) !!!
    // Player progress is saved under these ids. Any change in how they are computed makes every existing
    // world lose the progress and claimed rewards of all tasks and rewards that were written without an id.

    private static final Set<String> TASK_META = Set.of("id", "type", "title", "icon", "optional_task");
    private static final Set<String> REWARD_META = Set.of("id", "type", "title", "icon", "team_reward", "auto", "exclude_from_claim_all");

    private static long derivedId(long questId, String kind, String canonical, int occurrence) {
        return IdUtil.hash(IdUtil.toHex(questId) + "|" + kind + "|" + canonical + "|" + occurrence);
    }

    /**
     * A fixed text form of a task or reward compound: sorted keys, numbers without type suffixes, references as hex,
     * cosmetic keys left out. It only depends on what is written in the file, never on how this code writes it.
     */
    private static String canonical(CompoundTag tag, Set<String> skip, String typeId) {
        StringBuilder sb = new StringBuilder(typeId).append(':');
        List<String> keys = new ArrayList<>(tag.getAllKeys());
        keys.sort(null);
        for (String key : keys) {
            if (skip.contains(key)) {
                continue;
            }
            sb.append(key).append('=');
            Tag value = tag.get(key);
            if (key.equals("table_id") && value instanceof net.minecraft.nbt.StringTag s) {
                sb.append(IdUtil.toHex(IdUtil.parseFileId(s.getAsString())));
            } else {
                canonicalValue(sb, value);
            }
            sb.append(';');
        }
        return sb.toString();
    }

    private static void canonicalValue(StringBuilder sb, Tag tag) {
        if (tag instanceof CompoundTag c) {
            List<String> keys = new ArrayList<>(c.getAllKeys());
            keys.sort(null);
            sb.append('{');
            for (String key : keys) {
                sb.append(key).append('=');
                canonicalValue(sb, c.get(key));
                sb.append(';');
            }
            sb.append('}');
        } else if (tag instanceof net.minecraft.nbt.CollectionTag<?> list) {
            sb.append('[');
            for (Tag t : list) {
                canonicalValue(sb, t);
                sb.append(';');
            }
            sb.append(']');
        } else if (tag instanceof net.minecraft.nbt.FloatTag || tag instanceof net.minecraft.nbt.DoubleTag) {
            double d = ((net.minecraft.nbt.NumericTag) tag).getAsDouble();
            sb.append(d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : Double.toString(d));
        } else if (tag instanceof net.minecraft.nbt.NumericTag n) {
            sb.append(n.getAsLong());
        } else if (tag instanceof net.minecraft.nbt.StringTag s) {
            sb.append(net.minecraft.nbt.StringTag.quoteAndEscape(s.getAsString()));
        } else if (tag != null) {
            sb.append(tag.getAsString());
        }
    }

    // ------------------------------------------------------------------ objects with children

    /** Quest with its tasks and rewards; every id is written (network, editor, clipboard). */
    public static CompoundTag writeQuest(Quest q, HolderLookup.Provider p) {
        return writeQuest(q, p, false);
    }

    /** @param forFile leave out task / reward ids that are derived from their content anyway */
    public static CompoundTag writeQuest(Quest q, HolderLookup.Provider p, boolean forFile) {
        CompoundTag tag = new CompoundTag();
        q.writeData(tag, p);
        if (!q.tasks.isEmpty()) {
            ListTag tasks = new ListTag();
            Map<String, Integer> seen = new HashMap<>();
            for (Task t : q.tasks) {
                CompoundTag tt = new CompoundTag();
                t.writeData(tt, p);
                if (forFile) {
                    String canon = canonical(tt, TASK_META, t.getType().id());
                    int occurrence = seen.merge(canon, 1, Integer::sum) - 1;
                    if (derivedId(q.id, "task", canon, occurrence) == t.id) {
                        tt.remove("id");
                    }
                }
                tasks.add(tt);
            }
            tag.put("tasks", tasks);
        }
        if (!q.rewards.isEmpty()) {
            ListTag rewards = new ListTag();
            Map<String, Integer> seen = new HashMap<>();
            for (Reward r : q.rewards) {
                CompoundTag rt = new CompoundTag();
                r.writeData(rt, p);
                if (forFile) {
                    String canon = canonical(rt, REWARD_META, r.getType().id());
                    int occurrence = seen.merge(canon, 1, Integer::sum) - 1;
                    if (derivedId(q.id, "reward", canon, occurrence) == r.id) {
                        rt.remove("id");
                    }
                }
                rewards.add(rt);
            }
            tag.put("rewards", rewards);
        }
        return tag;
    }

    public static Quest readQuest(Chapter chapter, CompoundTag tag, HolderLookup.Provider p) {
        Quest q = new Quest(chapter);
        q.id = IdUtil.parseFileId(tag.getString("id"));
        if (q.id == 0L) {
            // quests are referenced by others, so they need a real id that is saved
            q.id = IdUtil.random();
            chapter.needsSave = true;
        }
        q.readData(tag, p);
        readQuestChildren(q, tag, p);
        return q;
    }

    public static void readQuestChildren(Quest q, CompoundTag tag, HolderLookup.Provider p) {
        q.tasks.clear();
        q.rewards.clear();
        ListTag tasks = tag.getList("tasks", Tag.TAG_COMPOUND);
        Map<String, Integer> seenTasks = new HashMap<>();
        for (int i = 0; i < tasks.size(); i++) {
            CompoundTag tt = tasks.getCompound(i);
            Task t = createTask(q, tt, p);
            if (t != null) {
                String canon = canonical(tt, TASK_META, t.getType().id());
                int occurrence = seenTasks.merge(canon, 1, Integer::sum) - 1;
                if (t.id == 0L) {
                    t.id = derivedId(q.id, "task", canon, occurrence);
                }
                q.tasks.add(t);
            }
        }
        ListTag rewards = tag.getList("rewards", Tag.TAG_COMPOUND);
        Map<String, Integer> seenRewards = new HashMap<>();
        for (int i = 0; i < rewards.size(); i++) {
            CompoundTag rt = rewards.getCompound(i);
            Reward r = createReward(q, rt, p);
            if (r != null) {
                String canon = canonical(rt, REWARD_META, r.getType().id());
                int occurrence = seenRewards.merge(canon, 1, Integer::sum) - 1;
                if (r.id == 0L) {
                    r.id = derivedId(q.id, "reward", canon, occurrence);
                }
                q.rewards.add(r);
            }
        }
    }

    /** Reads a single task; a missing id becomes a new random one. */
    public static Task readTask(Quest q, CompoundTag tag, HolderLookup.Provider p) {
        Task t = createTask(q, tag, p);
        if (t != null && t.id == 0L) {
            t.id = IdUtil.random();
        }
        return t;
    }

    /** Reads a single reward; a missing id becomes a new random one. */
    public static Reward readReward(Quest q, CompoundTag tag, HolderLookup.Provider p) {
        Reward r = createReward(q, tag, p);
        if (r != null && r.id == 0L) {
            r.id = IdUtil.random();
        }
        return r;
    }

    private static Task createTask(Quest q, CompoundTag tag, HolderLookup.Provider p) {
        TaskType type = TaskTypes.get(tag.getString("type"));
        if (type == null) {
            LOGGER.warn("Unknown task type '{}' in quest {}", tag.getString("type"), q.getCodeString());
            return null;
        }
        Task t = type.create(q);
        t.id = IdUtil.parseFileId(tag.getString("id"));
        t.readData(tag, p);
        return t;
    }

    private static Reward createReward(Quest q, CompoundTag tag, HolderLookup.Provider p) {
        RewardType type = RewardTypes.get(tag.getString("type"));
        if (type == null) {
            LOGGER.warn("Unknown reward type '{}' in quest {}", tag.getString("type"), q.getCodeString());
            return null;
        }
        Reward r = type.create(q);
        r.id = IdUtil.parseFileId(tag.getString("id"));
        r.readData(tag, p);
        return r;
    }

    /** Chapter with its quests; every id is written. */
    public static CompoundTag writeChapter(Chapter c, HolderLookup.Provider p) {
        return writeChapter(c, p, false);
    }

    public static CompoundTag writeChapter(Chapter c, HolderLookup.Provider p, boolean forFile) {
        CompoundTag tag = new CompoundTag();
        c.writeData(tag, p);
        tag.putInt("order_index", c.getIndex());
        ListTag quests = new ListTag();
        for (Quest q : c.quests) {
            quests.add(writeQuest(q, p, forFile));
        }
        tag.put("quests", quests);
        if (forFile) {
            tag.remove("filename"); // the name of the file itself is used
        }
        return tag;
    }

    public static Chapter readChapter(QuestFile file, CompoundTag tag, HolderLookup.Provider p) {
        Chapter c = new Chapter(file);
        c.id = IdUtil.parseFileId(tag.getString("id"));
        if (c.id == 0L) {
            c.id = IdUtil.random();
            c.needsSave = true;
        }
        c.readData(tag, p);
        ListTag quests = tag.getList("quests", Tag.TAG_COMPOUND);
        for (int i = 0; i < quests.size(); i++) {
            c.quests.add(readQuest(c, quests.getCompound(i), p));
        }
        return c;
    }

    public static CompoundTag writeFileData(QuestFile f, HolderLookup.Provider p) {
        CompoundTag tag = new CompoundTag();
        f.writeData(tag, p);
        ListTag groups = new ListTag();
        for (ChapterGroup g : f.chapterGroups) {
            CompoundTag gt = new CompoundTag();
            g.writeData(gt, p);
            groups.add(gt);
        }
        tag.put("chapter_groups", groups);
        return tag;
    }

    public static void readFileData(QuestFile f, CompoundTag tag, HolderLookup.Provider p) {
        f.readData(tag, p);
        f.chapterGroups.clear();
        ListTag groups = tag.getList("chapter_groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < groups.size(); i++) {
            CompoundTag gt = groups.getCompound(i);
            ChapterGroup g = new ChapterGroup(f);
            g.id = IdUtil.parseFileId(gt.getString("id"));
            if (g.id == 0L) {
                g.id = IdUtil.random();
                f.dataNeedsSave = true;
            }
            g.readData(gt, p);
            f.chapterGroups.add(g);
        }
    }

    public static CompoundTag writeRewardTable(RewardTable t, HolderLookup.Provider p) {
        return writeRewardTable(t, p, false);
    }

    public static CompoundTag writeRewardTable(RewardTable t, HolderLookup.Provider p, boolean forFile) {
        CompoundTag tag = new CompoundTag();
        t.writeData(tag, p);
        if (forFile) {
            tag.remove("filename");
        }
        return tag;
    }

    public static RewardTable readRewardTable(QuestFile f, CompoundTag tag, HolderLookup.Provider p) {
        RewardTable t = new RewardTable(f);
        t.id = IdUtil.parseFileId(tag.getString("id"));
        if (t.id == 0L) {
            t.id = IdUtil.random();
            t.needsSave = true;
        }
        t.readData(tag, p);
        return t;
    }

    // ------------------------------------------------------------------ network

    public static CompoundTag toNetwork(QuestFile f, HolderLookup.Provider p) {
        CompoundTag tag = new CompoundTag();
        tag.put("data", writeFileData(f, p));
        ListTag chapters = new ListTag();
        for (Chapter c : f.chapters) {
            chapters.add(writeChapter(c, p));
        }
        tag.put("chapters", chapters);
        ListTag tables = new ListTag();
        for (RewardTable t : f.rewardTables) {
            tables.add(writeRewardTable(t, p));
        }
        tag.put("reward_tables", tables);
        return tag;
    }

    public static QuestFile fromNetwork(CompoundTag tag, HolderLookup.Provider p) {
        QuestFile f = new QuestFile();
        readFileData(f, tag.getCompound("data"), p);
        ListTag chapters = tag.getList("chapters", Tag.TAG_COMPOUND);
        for (int i = 0; i < chapters.size(); i++) {
            f.chapters.add(readChapter(f, chapters.getCompound(i), p));
        }
        ListTag tables = tag.getList("reward_tables", Tag.TAG_COMPOUND);
        for (int i = 0; i < tables.size(); i++) {
            f.rewardTables.add(readRewardTable(f, tables.getCompound(i), p));
        }
        f.refresh();
        return f;
    }

    // ------------------------------------------------------------------ disk

    public static QuestFile load(Path folder, HolderLookup.Provider p) throws IOException {
        Path dataPath = folder.resolve("data.snbt");
        List<Path> chapterFiles = list(folder.resolve("chapters"));
        List<Path> tableFiles = list(folder.resolve("reward_tables"));
        if (!Files.exists(dataPath) && chapterFiles.isEmpty() && tableFiles.isEmpty()) {
            return null;
        }
        QuestFile f = new QuestFile();
        if (Files.exists(dataPath)) {
            readFileData(f, SNBT.read(dataPath), p);
        } else {
            LOGGER.warn("{} is missing, using the default quest book settings", dataPath);
        }

        List<CompoundTag> chapterTags = new ArrayList<>();
        for (Path path : chapterFiles) {
            try {
                CompoundTag tag = SNBT.read(path);
                // the file on disk always wins, so a copied file never overwrites the original when saved
                tag.putString("filename", stripExt(path));
                chapterTags.add(tag);
            } catch (IOException e) {
                LOGGER.error("Failed to load chapter {}: {}", path.getFileName(), e.getMessage());
            }
        }
        chapterTags.sort(Comparator.comparingInt(t -> t.getInt("order_index")));
        for (CompoundTag tag : chapterTags) {
            f.chapters.add(readChapter(f, tag, p));
        }

        for (Path path : tableFiles) {
            try {
                CompoundTag tag = SNBT.read(path);
                tag.putString("filename", stripExt(path));
                f.rewardTables.add(readRewardTable(f, tag, p));
            } catch (IOException e) {
                LOGGER.error("Failed to load reward table {}: {}", path.getFileName(), e.getMessage());
            }
        }
        f.rewardTables.sort(Comparator.comparing(t -> t.filename));
        fixDuplicateIds(f);
        f.refresh();
        return f;
    }

    /** Hand-copied quests often keep the id of the original; give every duplicate a fresh id (and save its file). */
    private static void fixDuplicateIds(QuestFile f) {
        Set<Long> seen = new HashSet<>();
        seen.add(QuestFile.FILE_ID);
        for (ChapterGroup g : f.chapterGroups) {
            if (reassignIfDuplicate(g, seen)) {
                f.dataNeedsSave = true;
            }
        }
        for (Chapter c : f.chapters) {
            boolean changed = reassignIfDuplicate(c, seen);
            for (Quest q : c.quests) {
                changed |= reassignIfDuplicate(q, seen);
                for (Task t : q.tasks) {
                    changed |= reassignIfDuplicate(t, seen);
                }
                for (Reward r : q.rewards) {
                    changed |= reassignIfDuplicate(r, seen);
                }
            }
            if (changed) {
                c.needsSave = true;
            }
        }
        for (RewardTable t : f.rewardTables) {
            if (reassignIfDuplicate(t, seen)) {
                t.needsSave = true;
            }
        }
    }

    private static boolean reassignIfDuplicate(QuestObjectBase o, Set<Long> seen) {
        boolean changed = false;
        while (o.id == 0L || !seen.add(o.id)) {
            LOGGER.warn("Duplicate quest object id {} ({}), assigning a new one", o.getCodeString(), o.getObjectType());
            o.id = IdUtil.random();
            changed = true;
        }
        return changed;
    }

    private static List<Path> list(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(path -> path.getFileName().toString().endsWith(".snbt")).sorted().toList();
        }
    }

    private static String stripExt(Path path) {
        String n = path.getFileName().toString();
        return n.endsWith(".snbt") ? n.substring(0, n.length() - 5) : n;
    }

    /** Writes everything. Files the quest file does not know about are never deleted. */
    public static void saveAll(QuestFile f, Path folder, HolderLookup.Provider p) throws IOException {
        Files.createDirectories(folder);
        saveData(f, folder, p);
        for (Chapter c : f.chapters) {
            saveChapter(c, folder, p);
        }
        for (RewardTable t : f.rewardTables) {
            saveRewardTable(t, folder, p);
        }
    }

    public static void saveData(QuestFile f, Path folder, HolderLookup.Provider p) throws IOException {
        SNBT.write(folder.resolve("data.snbt"), writeFileData(f, p));
    }

    public static void saveChapter(Chapter c, Path folder, HolderLookup.Provider p) throws IOException {
        ensureFilename(c.file, c, folder);
        SNBT.write(folder.resolve("chapters").resolve(c.filename + ".snbt"), writeChapter(c, p, true));
    }

    public static void deleteChapterFile(Chapter c, Path folder) throws IOException {
        if (!c.filename.isEmpty()) {
            Files.deleteIfExists(folder.resolve("chapters").resolve(c.filename + ".snbt"));
        }
    }

    public static void saveRewardTable(RewardTable t, Path folder, HolderLookup.Provider p) throws IOException {
        ensureFilename(t.file, t, folder);
        SNBT.write(folder.resolve("reward_tables").resolve(t.filename + ".snbt"), writeRewardTable(t, p, true));
    }

    public static void deleteRewardTableFile(RewardTable t, Path folder) throws IOException {
        if (!t.filename.isEmpty()) {
            Files.deleteIfExists(folder.resolve("reward_tables").resolve(t.filename + ".snbt"));
        }
    }

    /** Picks a file name for a new chapter that is not used by another chapter or by any file on disk (e.g. one that failed to load). */
    public static void ensureFilename(QuestFile f, Chapter c, Path folder) {
        if (!c.filename.isEmpty()) {
            return;
        }
        Set<String> used = existingNames(folder.resolve("chapters"));
        for (Chapter o : f.chapters) {
            if (o != c) {
                used.add(o.filename);
            }
        }
        c.filename = uniqueName(c.title.isEmpty() ? "chapter" : c.title, used);
    }

    public static void ensureFilename(QuestFile f, RewardTable t, Path folder) {
        if (!t.filename.isEmpty()) {
            return;
        }
        Set<String> used = existingNames(folder.resolve("reward_tables"));
        for (RewardTable o : f.rewardTables) {
            if (o != t) {
                used.add(o.filename);
            }
        }
        t.filename = uniqueName(t.title.isEmpty() ? "reward_table" : t.title, used);
    }

    private static Set<String> existingNames(Path dir) {
        Set<String> names = new HashSet<>();
        try {
            for (Path path : list(dir)) {
                names.add(stripExt(path));
            }
        } catch (IOException e) {
            LOGGER.warn("Could not list {}", dir, e);
        }
        return names;
    }

    public static String uniqueName(String title, Set<String> used) {
        String base = TextUtil.stripCodes(title).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_");
        base = base.replaceAll("^_|_$", "");
        if (base.isEmpty()) {
            base = "file";
        }
        if (base.length() > 48) {
            base = base.substring(0, 48);
        }
        String name = base;
        int i = 2;
        while (used.contains(name)) {
            name = base + "_" + i++;
        }
        return name;
    }
}
