package cz.jirka.notquests.quest.task;

import cz.jirka.notquests.config.ConfigGroup;
import cz.jirka.notquests.config.IdKind;
import cz.jirka.notquests.quest.Quest;
import cz.jirka.notquests.util.TextUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.Optional;

public class KillTask extends Task {
    public String entity = "minecraft:zombie";
    public long value = 1L;
    public String customName = "";

    public KillTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return TaskTypes.KILL;
    }

    @Override
    public long getMaxProgress() {
        return value;
    }

    public boolean matches(Entity e) {
        ResourceLocation rl = TextUtil.rl(entity);
        if (rl == null) {
            return false;
        }
        boolean typeMatches;
        if (entity.startsWith("#")) {
            typeMatches = e.getType().is(TagKey.create(Registries.ENTITY_TYPE, rl));
        } else {
            typeMatches = rl.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        }
        if (!typeMatches) {
            return false;
        }
        if (!customName.isEmpty()) {
            return e.hasCustomName() && e.getCustomName() != null && customName.equals(e.getCustomName().getString());
        }
        return true;
    }

    public Optional<EntityType<?>> entityType() {
        if (entity.startsWith("#")) {
            return Optional.empty();
        }
        ResourceLocation rl = TextUtil.rl(entity);
        return rl == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(rl);
    }

    public Component entityName() {
        if (entity.startsWith("#")) {
            // "#minecraft:skeletons" -> "Skeletons"
            return Component.literal(cz.jirka.notquests.util.TagNames.plural(Registries.ENTITY_TYPE, entity));
        }
        return entityType().<Component>map(EntityType::getDescription).orElse(Component.literal(entity));
    }

    @Override
    public Component getAltTitle() {
        if (customName.isEmpty() && entity.startsWith("#") && value == 1L) {
            // "Kill Any Skeleton"
            return Component.translatable("notquests.task.kill.title_any",
                    cz.jirka.notquests.util.TagNames.anyOne(Registries.ENTITY_TYPE, entity));
        }
        Component name = customName.isEmpty() ? entityName() : Component.literal(customName);
        return Component.translatable("notquests.task.kill.title", TextUtil.formatLong(value), name);
    }

    @Override
    public Component getProgressTitle() {
        if (!title.isEmpty()) {
            return getTitle();
        }
        return customName.isEmpty() ? entityName() : Component.literal(customName);
    }

    @Override
    public ItemStack getAltIcon() {
        if (entity.startsWith("#")) {
            ItemStack current = cz.jirka.notquests.util.ItemUtil.cycleEntityTag(entity);
            if (!current.isEmpty()) {
                return current;
            }
        }
        Optional<EntityType<?>> type = entityType();
        if (type.isPresent()) {
            SpawnEggItem egg = SpawnEggItem.byId(type.get());
            if (egg != null) {
                return new ItemStack(egg);
            }
        }
        return super.getAltIcon();
    }

    @Override
    public void writeData(CompoundTag tag, HolderLookup.Provider provider) {
        super.writeData(tag, provider);
        tag.putString("entity", entity);
        putNumber(tag, "value", value);
        if (!customName.isEmpty()) {
            tag.putString("custom_name", customName);
        }
    }

    @Override
    public void readData(CompoundTag tag, HolderLookup.Provider provider) {
        super.readData(tag, provider);
        entity = tag.contains("entity") ? tag.getString("entity") : "minecraft:zombie";
        value = tag.contains("value") ? Math.max(1L, tag.getLong("value")) : 1L;
        customName = tag.getString("custom_name");
    }

    @Override
    public void fillConfig(ConfigGroup group) {
        super.fillConfig(group);
        group.addId("entity", entity, "minecraft:zombie", IdKind.ENTITY, v -> entity = v);
        group.addLong("value", value, 1L, 1L, Long.MAX_VALUE, v -> value = v);
        group.addString("custom_name", customName, "", v -> customName = v);
    }
}
