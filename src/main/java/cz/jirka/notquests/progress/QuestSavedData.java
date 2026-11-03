package cz.jirka.notquests.progress;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Teams, their progress and per-player flags, stored in the world's data folder. */
public class QuestSavedData extends SavedData {
    public static final String NAME = "notquests_progress";

    public final Map<UUID, Team> teams = new LinkedHashMap<>();
    public final Map<UUID, UUID> playerTeams = new HashMap<>();
    public final Map<UUID, String> playerNames = new HashMap<>();
    public final Set<UUID> editing = new HashSet<>();
    public final Set<UUID> bookGiven = new HashSet<>();

    public static Factory<QuestSavedData> factory() {
        return new Factory<>(QuestSavedData::new, QuestSavedData::load, null);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag teamList = new ListTag();
        for (Team t : teams.values()) {
            teamList.add(t.write());
        }
        tag.put("teams", teamList);
        ListTag players = new ListTag();
        for (Map.Entry<UUID, UUID> e : playerTeams.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("uuid", e.getKey());
            p.putUUID("team", e.getValue());
            p.putString("name", playerNames.getOrDefault(e.getKey(), ""));
            players.add(p);
        }
        tag.put("players", players);
        tag.put("editing", uuidList(editing));
        tag.put("book_given", uuidList(bookGiven));
        return tag;
    }

    private static ListTag uuidList(Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID u : set) {
            list.add(NbtUtils.createUUID(u));
        }
        return list;
    }

    public static QuestSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        QuestSavedData d = new QuestSavedData();
        ListTag teamList = tag.getList("teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < teamList.size(); i++) {
            Team t = Team.read(teamList.getCompound(i));
            d.teams.put(t.id, t);
        }
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag p = players.getCompound(i);
            UUID uuid = p.getUUID("uuid");
            d.playerTeams.put(uuid, p.getUUID("team"));
            if (!p.getString("name").isEmpty()) {
                d.playerNames.put(uuid, p.getString("name"));
            }
        }
        for (Tag u : tag.getList("editing", Tag.TAG_INT_ARRAY)) {
            d.editing.add(NbtUtils.loadUUID(u));
        }
        for (Tag u : tag.getList("book_given", Tag.TAG_INT_ARRAY)) {
            d.bookGiven.add(NbtUtils.loadUUID(u));
        }
        return d;
    }

    /** The personal team of a player, created on demand. */
    public Team personalTeam(UUID player, String name) {
        Team t = teams.get(player);
        if (t == null) {
            t = new Team(player, name, true, player);
            t.members.add(player);
            teams.put(player, t);
            setDirty();
        }
        return t;
    }

    /** The team the player currently belongs to (personal team if none). */
    public Team getTeam(UUID player, String name) {
        UUID teamId = playerTeams.get(player);
        Team t = teamId == null ? null : teams.get(teamId);
        if (t == null || !t.members.contains(player)) {
            t = personalTeam(player, name);
            t.members.add(player);
            playerTeams.put(player, t.id);
            setDirty();
        }
        return t;
    }

    @Nullable
    public Team findTeamByName(String name) {
        for (Team t : teams.values()) {
            if (!t.personal && t.name.equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    public String nameOf(UUID player) {
        return playerNames.getOrDefault(player, player.toString().substring(0, 8));
    }
}
