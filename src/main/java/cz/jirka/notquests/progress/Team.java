package cz.jirka.notquests.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A group of players sharing quest progress. Every player has a personal team (id = player uuid);
 * players can create or join party teams with /notquests team.
 */
public class Team {
    public final UUID id;
    public String name;
    public boolean personal;
    public UUID owner;
    public final Set<UUID> members = new LinkedHashSet<>();
    public final Set<UUID> invites = new HashSet<>();
    public TeamData data = new TeamData();

    public Team(UUID id, String name, boolean personal, UUID owner) {
        this.id = id;
        this.name = name;
        this.personal = personal;
        this.owner = owner;
    }

    public boolean isOwner(UUID player) {
        return owner.equals(player);
    }

    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putBoolean("personal", personal);
        tag.putUUID("owner", owner);
        ListTag m = new ListTag();
        for (UUID u : members) {
            m.add(NbtUtils.createUUID(u));
        }
        tag.put("members", m);
        ListTag inv = new ListTag();
        for (UUID u : invites) {
            inv.add(NbtUtils.createUUID(u));
        }
        tag.put("invites", inv);
        tag.put("data", data.write());
        return tag;
    }

    public static Team read(CompoundTag tag) {
        Team t = new Team(tag.getUUID("id"), tag.getString("name"), tag.getBoolean("personal"), tag.getUUID("owner"));
        ListTag m = tag.getList("members", Tag.TAG_INT_ARRAY);
        for (Tag u : m) {
            t.members.add(NbtUtils.loadUUID(u));
        }
        ListTag inv = tag.getList("invites", Tag.TAG_INT_ARRAY);
        for (Tag u : inv) {
            t.invites.add(NbtUtils.loadUUID(u));
        }
        t.data = TeamData.read(tag.getCompound("data"));
        return t;
    }
}
